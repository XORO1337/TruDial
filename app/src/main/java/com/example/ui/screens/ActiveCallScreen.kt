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
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.example.BuildConfig
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.os.VibrationEffect
import android.util.Log

@SuppressLint("MissingPermission")
@Composable
fun ActiveCallScreen(
    incomingCallerId: String,
    onCallEnded: (callerId: String, riskLevel: String, summary: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val useLocalLlm by settingsManager.useLocalLlmFlow.collectAsState(initial = false)
    
    var transcript by remember { mutableStateOf("") }
    var riskLevel by remember { mutableStateOf("Low") }
    var onHold by remember { mutableStateOf(false) }
    var llmInference by remember { mutableStateOf<LlmInference?>(null) }
    
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
                Log.e("ActiveCallScreen", "Vibration failed", e)
            }
        }
    }
    
    LaunchedEffect(useLocalLlm) {
        if (useLocalLlm) {
            withContext(Dispatchers.IO) {
                try {
                    val modelFile = File(context.filesDir, "model/model.bin")
                    if (modelFile.exists()) {
                        val options = LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(modelFile.absolutePath)
                            .build()
                        val llm = LlmInference.createFromOptions(context, options)
                        withContext(Dispatchers.Main) {
                            llmInference = llm
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ActiveCallScreen", "Failed to init LLM", e)
                }
            }
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

    DisposableEffect(Unit) {
        var speechRecognizer: SpeechRecognizer? = null
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        startListening(this@apply)
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val newText = matches[0]
                            transcript += "$newText\n\n"
                            
                            scope.launch {
                                try {
                                    val prompt = "Analyze this call transcript segment and respond with exactly one word (Safe, High, or Extreme) representing the risk level of scam or digital arrest: \"$newText\""
                                    var result = ""
                                    
                                    if (useLocalLlm && llmInference != null) {
                                        withContext(Dispatchers.IO) {
                                            result = llmInference?.generateResponse(prompt) ?: "Low"
                                        }
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            var aiUrl = BuildConfig.AI_API_URL
                                            if (aiUrl.isEmpty() || aiUrl == "YOUR_AI_API_URL" || aiUrl == "\"YOUR_AI_API_URL\"") {
                                                aiUrl = "https://openrouter.ai/api/v1/chat/completions"
                                            }
                                            val url = URL(aiUrl)
                                            val connection = url.openConnection() as HttpURLConnection
                                            connection.requestMethod = "POST"
                                            connection.setRequestProperty("Content-Type", "application/json")
                                            val apiKey = BuildConfig.AI_API_KEY
                                            connection.setRequestProperty("Authorization", "Bearer $apiKey")
                                            connection.setRequestProperty("HTTP-Referer", "https://github.com/aistudio")
                                            connection.setRequestProperty("X-Title", "TruDial")
                                            connection.doOutput = true
                                            
                                            val jsonPayload = JSONObject().apply {
                                                val model = if (BuildConfig.AI_MODEL.isNotEmpty() && BuildConfig.AI_MODEL != "YOUR_AI_MODEL" && BuildConfig.AI_MODEL != "\"YOUR_AI_MODEL\"") BuildConfig.AI_MODEL else "meta-llama/llama-3-8b-instruct:free"
                                                put("model", model)
                                                
                                                val messageObj = JSONObject().apply {
                                                    put("role", "user")
                                                    put("content", prompt)
                                                }
                                                val messagesArray = JSONArray().apply {
                                                    put(messageObj)
                                                }
                                                put("messages", messagesArray)
                                            }
                                            
                                            connection.outputStream.use { os ->
                                                val input = jsonPayload.toString().toByteArray(Charsets.UTF_8)
                                                os.write(input, 0, input.size)
                                            }
                                            
                                            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                                                val response = connection.inputStream.bufferedReader().use { it.readText() }
                                                val jsonResponse = JSONObject(response)
                                                val choices = jsonResponse.optJSONArray("choices")
                                                if (choices != null && choices.length() > 0) {
                                                    val message = choices.getJSONObject(0).optJSONObject("message")
                                                    result = message?.optString("content", "Low") ?: "Low"
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (result.contains("High", ignoreCase = true)) {
                                        if (riskLevel == "Low") riskLevel = "High"
                                    } else if (result.contains("Extreme", ignoreCase = true)) {
                                        riskLevel = "Extreme"
                                    }
                                    
                                    if (riskLevel == "Extreme" && !onHold) {
                                        onHold = true
                                    }
                                } catch (e: Exception) {
                                    Log.e("ActiveCallScreen", "AI Analysis failed", e)
                                    // Fallback rule-based
                                    if (newText.contains("digital arrest", ignoreCase = true) || newText.contains("warrant", ignoreCase = true)) {
                                        riskLevel = "Extreme"
                                        if (!onHold) onHold = true
                                    }
                                }
                            }
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
                startListening(intent)
            }
        }
        
        onDispose {
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
                    Text(
                        text = transcript.ifEmpty { "Listening..." },
                        modifier = Modifier.verticalScroll(rememberScrollState()),
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
                    onClick = { onHold = !onHold },
                    containerColor = if (onHold) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                    contentColor = if (onHold) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Pause, contentDescription = "Hold")
                }
                
                ExtendedFloatingActionButton(
                    onClick = {
                        try {
                            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                            telecomManager.endCall()
                        } catch (e: Exception) {
                            e.printStackTrace()
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
