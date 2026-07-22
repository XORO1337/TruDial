package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telecom.TelecomManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import com.example.data.SettingsManager
import com.example.telecom.CallController
import com.example.telecom.GroqTranscriber
import kotlinx.coroutines.channels.Channel
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.example.BuildConfig
import android.telecom.Call
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.os.VibrationEffect
import android.util.Log

// Unified log tag for the whole live-call flow. Filter with: adb logcat -s TruDialCall
private const val CALL_TAG = "TruDialCall"

private const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
private const val GROQ_LLAMA_8B_MODEL = "llama-3.1-8b-instant"

/**
 * Calls an OpenAI-compatible /chat/completions endpoint (OpenRouter, Groq, ...) and returns the
 * assistant's message content. Returns "Low" on any failure so the caller degrades gracefully.
 * Must be invoked from a background dispatcher.
 */
private fun callChatCompletion(
    endpoint: String,
    apiKey: String,
    model: String,
    prompt: String,
    temperature: Double = 0.0
): String {
    Log.d(CALL_TAG, "Sending analysis request → endpoint=$endpoint model=$model")
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("HTTP-Referer", "https://github.com/aistudio")
        setRequestProperty("X-Title", "TruDial")
        doOutput = true
    }
    return try {
        val jsonPayload = JSONObject().apply {
            put("model", model)
            put("temperature", temperature) // deterministic classification; avoids flaky verdicts
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }
        connection.outputStream.use { os ->
            val input = jsonPayload.toString().toByteArray(Charsets.UTF_8)
            os.write(input, 0, input.size)
        }

        val code = connection.responseCode
        Log.d(CALL_TAG, "Analysis response code=$code from $endpoint")
        if (code == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val choices = JSONObject(response).optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                message?.optString("content", "Low") ?: "Low"
            } else {
                Log.w(CALL_TAG, "Analysis response had no choices: $response")
                "Low"
            }
        } else {
            val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
            Log.e(CALL_TAG, "Analysis request failed code=$code body=$err")
            "Low"
        }
    } catch (e: Exception) {
        Log.e(CALL_TAG, "Analysis request threw", e)
        "Low"
    } finally {
        connection.disconnect()
    }
}

/** Structured result of a scam analysis. */
private data class RiskDecision(val level: String, val indicators: List<Int>, val reason: String)

/**
 * Builds the scam-analysis prompt. The model judges the WHOLE conversation so far (not one
 * fragment) against a fixed digital-arrest checklist, and must see MULTIPLE indicators before
 * raising risk — a single ambiguous line should stay "Safe".
 */
private fun buildScamAnalysisPrompt(transcript: String): String {
    val convo = transcript.trim().takeLast(4000).ifEmpty { "(no speech yet)" }
    return """
You are a fraud-detection assistant analysing a live phone call in India (the transcript may be in Hindi or English). Judge ONLY from evidence in the transcript. Focus on "digital arrest" and authority-impersonation scams.

Scam indicators:
1. Falsely claiming to represent the police, CBI, ED, telecom authority (TRAI/DoT), customs, or a court.
2. Creating extreme urgency or threatening immediate arrest (e.g. "आपको अभी गिरफ्तार किया जाएगा").
3. Isolating the victim — forbidding them from contacting family or a lawyer.
4. Insisting the call be kept secret or confidential.
5. Keeping the victim on a long continuous video/audio call to block outside advice.
6. Using official-sounding language, fake FIR/case numbers, or claims of seized parcels/forged documents to appear legitimate.
7. Demanding money, bank details, OTP, or a UPI transfer for "verification" or a "safe/government account".

Judgement rules:
- Count an indicator ONLY when a participant in THIS call actually says or does it. Base every indicator strictly on the spoken words in the transcript. NEVER invent an indicator that the words do not clearly support.
- Do NOT flag speech that merely discusses, describes, quotes, explains, or tests scams, fraud detection, checklists, risk levels, or this app (e.g. someone reading documentation or a log aloud). That is meta-commentary, not a scam — treat it as "Safe".
- Not every call mentioning the police is a scam. Genuine officials never demand money/OTP, never forbid contacting a lawyer, and never keep you on a secret continuous call. Ordinary conversation is "Safe".
- Require MULTIPLE distinct indicators before raising risk. Do NOT raise risk on a single ambiguous line. When uncertain, choose "Safe".
- "Safe": zero or one indicator.
- "High": two or three distinct indicators.
- "Extreme": four or more indicators, OR any demand for money/OTP (indicator 7) combined with impersonation of authority (indicator 1).

Respond with ONLY a compact JSON object and nothing else:
{"risk":"Safe|High|Extreme","indicators":[matched indicator numbers],"reason":"short reason"}

Transcript:
$convo
""".trim()
}

/** Parses the model's JSON verdict; conservatively defaults to Safe if it can't be understood. */
private fun parseRiskDecision(raw: String): RiskDecision {
    val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    if (start >= 0 && end > start) {
        try {
            val obj = JSONObject(cleaned.substring(start, end + 1))
            val level = when (obj.optString("risk", "Safe").trim().lowercase()) {
                "extreme" -> "Extreme"
                "high" -> "High"
                else -> "Safe"
            }
            val indicators = mutableListOf<Int>()
            obj.optJSONArray("indicators")?.let { arr ->
                for (i in 0 until arr.length()) indicators.add(arr.optInt(i))
            }
            return RiskDecision(level, indicators, obj.optString("reason", ""))
        } catch (_: Exception) { /* fall through to lenient parse */ }
    }
    // Lenient fallback for models that answer with a bare word (e.g. small on-device model).
    val short = cleaned.length < 25
    val level = when {
        short && cleaned.contains("extreme", ignoreCase = true) -> "Extreme"
        short && cleaned.contains("high", ignoreCase = true) -> "High"
        else -> "Safe"
    }
    return RiskDecision(level, emptyList(), "unparsed")
}

@SuppressLint("MissingPermission")
@Composable
fun ActiveCallScreen(
    incomingCallerId: String,
    simulateScam: Boolean = false,
    onCallEnded: (callerId: String, riskLevel: String, summary: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val useLocalLlm by settingsManager.useLocalLlmFlow.collectAsState(initial = false)
    val preferCloud by settingsManager.preferCloudLlmFlow.collectAsState(initial = false)
    val groqApiKey by settingsManager.groqApiKeyFlow.collectAsState(initial = "")

    // Cloud override wins even on high-RAM phones, but only when a Groq key is actually present.
    val useGroq = preferCloud && groqApiKey.isNotBlank()

    LaunchedEffect(incomingCallerId) {
        Log.i(CALL_TAG, "Active call started with caller=$incomingCallerId")
    }
    LaunchedEffect(useGroq, useLocalLlm) {
        val engine = when {
            useGroq -> "Groq cloud ($GROQ_LLAMA_8B_MODEL)"
            useLocalLlm -> "on-device model"
            else -> "OpenRouter cloud (default)"
        }
        Log.i(CALL_TAG, "Analysis engine selected: $engine (preferCloud=$preferCloud, groqKeySet=${groqApiKey.isNotBlank()}, useLocalLlm=$useLocalLlm)")
    }

    // Real telecom call (from TruDialInCallService), if this screen is backing an actual call
    // rather than the dashboard "Simulate Call" demo.
    val hasRealCall by CallController.hasCall.collectAsState()
    val telecomCallState by CallController.state.collectAsState()
    val speakerOn by CallController.speakerOn.collectAsState()

    var transcript by remember { mutableStateOf("") }
    var riskLevel by remember { mutableStateOf("Low") }
    // Fallback hold flag used only for the simulated (no real Call) demo path.
    var simulatedHold by remember { mutableStateOf(false) }
    var llmInference by remember { mutableStateOf<LlmInference?>(null) }

    // Single source of truth for "is the call on hold", real or simulated.
    val onHold = if (hasRealCall) telecomCallState == Call.STATE_HOLDING else simulatedHold
    
    LaunchedEffect(riskLevel) {
        if (riskLevel != "Low") {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                
                if (vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val timings = longArrayOf(0, 500, 200, 500, 200, 500)
                        val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
                    }
                }
            } catch (e: Exception) {
                Log.e(CALL_TAG, "Vibration failed", e)
            }
        }
    }
    
    LaunchedEffect(useLocalLlm, useGroq) {
        // Skip loading the heavy on-device model entirely when the cloud override is active.
        if (useLocalLlm && !useGroq) {
            withContext(Dispatchers.IO) {
                try {
                    val modelFile = File(context.filesDir, "model/model.bin")
                    if (modelFile.exists()) {
                        Log.i(CALL_TAG, "Loading on-device model from ${modelFile.absolutePath}")
                        val options = LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(modelFile.absolutePath)
                            .build()
                        val llm = LlmInference.createFromOptions(context, options)
                        withContext(Dispatchers.Main) {
                            llmInference = llm
                        }
                        Log.i(CALL_TAG, "On-device model loaded successfully")
                    } else {
                        Log.w(CALL_TAG, "On-device model file missing; will fall back to cloud analysis")
                    }
                } catch (e: Exception) {
                    Log.e(CALL_TAG, "Failed to init on-device LLM", e)
                }
            }
        } else if (useGroq) {
            Log.d(CALL_TAG, "Cloud override active; skipping on-device model load")
        }
    }

    // Pulse animation for recording
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Play an alert sound when the call is put on hold (to notify user if phone is at their ear)
    LaunchedEffect(onHold) {
        if (onHold) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 2000)
                delay(2000)
                toneGen.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Analyse each recognised transcript segment sequentially with the current engine settings.
    // Producers (Whisper STT or the platform recogniser) push text onto this channel.
    val segmentChannel = remember { Channel<String>(Channel.UNLIMITED) }
    LaunchedEffect(Unit) {
        for (newText in segmentChannel) {
            try {
                // Judge the whole conversation so far, so "multiple indicators" can be assessed
                // cumulatively rather than from a single (possibly innocuous) fragment.
                val prompt = buildScamAnalysisPrompt(transcript)
                val groqActive = preferCloud && groqApiKey.isNotBlank()
                var result = ""
                when {
                    groqActive -> withContext(Dispatchers.IO) {
                        result = callChatCompletion(GROQ_ENDPOINT, groqApiKey, GROQ_LLAMA_8B_MODEL, prompt)
                    }
                    useLocalLlm && llmInference != null -> withContext(Dispatchers.IO) {
                        result = llmInference?.generateResponse(prompt) ?: "Safe"
                    }
                    else -> {
                        var aiUrl = BuildConfig.AI_API_URL
                        if (aiUrl.isEmpty() || aiUrl == "YOUR_AI_API_URL" || aiUrl == "\"YOUR_AI_API_URL\"") {
                            aiUrl = "https://openrouter.ai/api/v1/chat/completions"
                        }
                        val model = if (BuildConfig.AI_MODEL.isNotEmpty() && BuildConfig.AI_MODEL != "YOUR_AI_MODEL" && BuildConfig.AI_MODEL != "\"YOUR_AI_MODEL\"") BuildConfig.AI_MODEL else "meta-llama/llama-3-8b-instruct:free"
                        withContext(Dispatchers.IO) {
                            result = callChatCompletion(aiUrl, BuildConfig.AI_API_KEY, model, prompt)
                        }
                    }
                }

                val decision = parseRiskDecision(result)
                Log.i(CALL_TAG, "Analysis risk=${decision.level} indicators=${decision.indicators} reason=\"${decision.reason}\" (risk before update=$riskLevel)")

                // Escalate only; never downgrade within a call.
                when (decision.level) {
                    "Extreme" -> {
                        if (riskLevel != "Extreme") {
                            riskLevel = "Extreme"
                            Log.w(CALL_TAG, "Risk level escalated to Extreme (indicators=${decision.indicators})")
                        }
                    }
                    "High" -> {
                        if (riskLevel == "Low") {
                            riskLevel = "High"
                            Log.w(CALL_TAG, "Risk level escalated to High (indicators=${decision.indicators})")
                        }
                    }
                }

                val currentlyHeld = if (hasRealCall) telecomCallState == Call.STATE_HOLDING else simulatedHold
                if (riskLevel == "Extreme" && !currentlyHeld) {
                    Log.w(CALL_TAG, "Auto-holding call due to Extreme risk")
                    if (hasRealCall) CallController.toggleHold() else simulatedHold = true
                }
            } catch (e: Exception) {
                Log.e(CALL_TAG, "AI analysis failed; applying rule-based fallback", e)
                if (newText.contains("digital arrest", ignoreCase = true) || newText.contains("warrant", ignoreCase = true)) {
                    Log.w(CALL_TAG, "Rule-based fallback matched scam keywords → Extreme")
                    riskLevel = "Extreme"
                    val held = if (hasRealCall) telecomCallState == Call.STATE_HOLDING else simulatedHold
                    if (!held) {
                        if (hasRealCall) CallController.toggleHold() else simulatedHold = true
                    }
                }
            }
        }
    }

    // Demo mode: feed a scripted Hindi "digital arrest" scam through the same analysis pipeline,
    // bypassing the mic entirely. Lets you verify transcription→Groq→risk end-to-end on any device.
    if (simulateScam) {
        LaunchedEffect(Unit) {
            Log.i(CALL_TAG, "Simulate scam call: feeding scripted Hindi segments")
            val script = listOf(
                "नमस्ते सर, मैं मुंबई क्राइम ब्रांच से इंस्पेक्टर वर्मा बोल रहा हूँ।",
                "आपके नाम से एक कूरियर पार्सल पकड़ा गया है जिसमें अवैध ड्रग्स और नकली पासपोर्ट मिले हैं।",
                "आपके आधार कार्ड का इस्तेमाल मनी लॉन्ड्रिंग में हुआ है, आपके खिलाफ केस दर्ज हो चुका है।",
                "यह एक डिजिटल अरेस्ट है, अगली सूचना तक आप कैमरे के सामने रहेंगे और कॉल नहीं काटेंगे।",
                "गिरफ्तारी से बचना है तो अपनी सारी बैंक डिटेल और ओटीपी अभी बताइए।",
                "जांच के लिए यह पैसा तुरंत इस सुरक्षित सरकारी खाते में ट्रांसफर करना होगा।"
            )
            for (line in script) {
                delay(3000)
                Log.i(CALL_TAG, "Simulated scam segment: \"$line\"")
                transcript += "$line\n\n"
                segmentChannel.trySend(line)
            }
        }
    }

    // A Groq key lets us transcribe with Whisper over raw mic audio. This is the reliable path:
    // the platform SpeechRecognizer refuses call audio on many devices (endless ERROR_NO_MATCH).
    // Skipped in demo mode, where segments are scripted rather than captured from the mic.
    val useGroqStt = groqApiKey.isNotBlank()

    DisposableEffect(useGroqStt, simulateScam) {
        if (useGroqStt && !simulateScam) {
            Log.i(CALL_TAG, "Transcription source: Groq Whisper STT")
            val transcriber = GroqTranscriber(groqApiKey)
            transcriber.start(scope) { text ->
                Log.i(CALL_TAG, "Transcript segment recognized: \"$text\"")
                transcript += "$text\n\n"
                segmentChannel.trySend(text)
            }
            onDispose {
                Log.i(CALL_TAG, "Stopping Groq Whisper STT")
                transcriber.stop()
            }
        } else {
            onDispose { }
        }
    }

    // Fallback transcription via the platform SpeechRecognizer (only when no Groq key is set,
    // and never in demo mode).
    DisposableEffect(useGroqStt, simulateScam) {
        if (useGroqStt || simulateScam) {
            return@DisposableEffect onDispose { }
        }
        var speechRecognizer: SpeechRecognizer? = null
        val recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        Log.i(CALL_TAG, "Speech recognition available=$recognitionAvailable")
        if (recognitionAvailable) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        Log.d(CALL_TAG, "Speech recognizer: end of speech")
                    }
                    override fun onError(error: Int) {
                        // ERROR_NO_MATCH (7) and ERROR_SPEECH_TIMEOUT (6) fire constantly during quiet
                        // stretches of a call. Back off briefly before restarting so we don't hammer the
                        // recognizer (which was spinning several times a second) or flood the log.
                        Log.d(CALL_TAG, "Speech recognizer error code=$error; restarting after backoff")
                        val self = this@apply
                        scope.launch {
                            delay(1000)
                            startListening(self)
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val newText = matches[0]
                            Log.i(CALL_TAG, "Transcript segment recognized: \"$newText\"")
                            transcript += "$newText\n\n"
                            segmentChannel.trySend(newText)
                        }
                        startListening(this@apply)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                    
                    private fun startListening(recognizer: SpeechRecognizer) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        }
                        recognizer.startListening(intent)
                    }
                })
                
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                Log.i(CALL_TAG, "Starting speech recognition listener")
                startListening(intent)
            }
        }

        onDispose {
            Log.i(CALL_TAG, "Disposing speech recognizer for caller=$incomingCallerId")
            speechRecognizer?.destroy()
        }
    }

    val overlayColor by animateColorAsState(
        targetValue = when (riskLevel) {
            "Low" -> MaterialTheme.colorScheme.surface
            "High" -> Color(0xFFB45309) // Amber/Orange
            "Extreme" -> Color(0xFF991B1B) // Deep Red
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(1000),
        label = "bg_color"
    )

    val contentColor = if (riskLevel == "Low") MaterialTheme.colorScheme.onSurface else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = contentColor
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Active Call",
                style = MaterialTheme.typography.titleMedium,
                color = contentColor.copy(alpha = 0.7f),
            )
            
            Text(
                incomingCallerId,
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor,
                fontWeight = FontWeight.ExtraBold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            AnimatedVisibility(visible = riskLevel == "Low") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).scale(pulseScale).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Securely analyzing call locally...", color = contentColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (riskLevel == "Low") 1f else 0.95f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = "Mic", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Live Local Transcript", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    val transcriptScroll = rememberScrollState()
                    // Keep the newest line in view as segments arrive.
                    LaunchedEffect(transcript) {
                        transcriptScroll.animateScrollTo(transcriptScroll.maxValue)
                    }
                    Text(
                        text = transcript.ifEmpty {
                            if (hasRealCall && !speakerOn)
                                "Turn on speaker (top-left button) so TruDial can hear the call and transcribe it."
                            else
                                "Listening..."
                        },
                        modifier = Modifier.verticalScroll(transcriptScroll),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            AnimatedVisibility(visible = onHold) {
                Text(
                    "Call put on HOLD automatically for your safety.",
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 24.dp),
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingActionButton(
                    onClick = {
                        Log.i(CALL_TAG, "Speaker toggle pressed. Now=${!speakerOn}")
                        CallController.setSpeaker(!speakerOn)
                    },
                    containerColor = if (speakerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (speakerOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ) {
                    Icon(
                        if (speakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Speaker"
                    )
                }

                FloatingActionButton(
                    onClick = {
                        if (hasRealCall) {
                            Log.i(CALL_TAG, "Hold button pressed (real call). Currently held=$onHold")
                            CallController.toggleHold()
                        } else {
                            Log.i(CALL_TAG, "Hold button pressed (simulated call)")
                            simulatedHold = !simulatedHold
                        }
                    },
                    containerColor = if (onHold) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                    contentColor = if (onHold) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Pause, contentDescription = "Hold")
                }
                
                ExtendedFloatingActionButton(
                    onClick = {
                        Log.i(CALL_TAG, "User hung up. Final risk=$riskLevel, transcript length=${transcript.length}, realCall=$hasRealCall")
                        if (hasRealCall) {
                            CallController.disconnect()
                        } else {
                            try {
                                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                                telecomManager.endCall()
                            } catch (e: Exception) {
                                Log.e(CALL_TAG, "Failed to end call via TelecomManager", e)
                            }
                        }
                        onCallEnded(incomingCallerId, riskLevel, transcript.ifEmpty { "Call dropped quickly" })
                    },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier.height(64.dp).weight(1f).padding(start = 24.dp)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Hang up", modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Hang Up", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Non-intrusive Scam Alert Overlay
        AnimatedVisibility(
            visible = riskLevel != "Low",
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning, 
                        contentDescription = "Warning", 
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(32.dp).scale(pulseScale)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "SCAM DETECTED: $riskLevel",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Digital Arrest Pattern Identified",
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
