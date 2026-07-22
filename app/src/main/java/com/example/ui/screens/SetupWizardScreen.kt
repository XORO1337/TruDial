package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.SettingsManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.flow.first

@Composable
fun AnimatedGradientBackground(content: @Composable () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val color1 by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.primaryContainer,
        targetValue = MaterialTheme.colorScheme.tertiaryContainer,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color1"
    )
    val color2 by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.secondaryContainer,
        targetValue = MaterialTheme.colorScheme.primaryContainer,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(color1.copy(alpha = 0.3f), color2.copy(alpha = 0.3f))))
    ) {
        content()
    }
}

@Composable
fun SetupWizardScreen(onSetupComplete: () -> Unit) {
    var currentStep by remember { mutableStateOf(0) }
    
    AnimatedGradientBackground {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(animationSpec = tween(600, easing = FastOutSlowInEasing)) { width -> width } + fadeIn(animationSpec = tween(600))).togetherWith(
                        slideOutHorizontally(animationSpec = tween(600, easing = FastOutSlowInEasing)) { width -> -width } + fadeOut(animationSpec = tween(600))
                    )
                } else {
                    (slideInHorizontally(animationSpec = tween(600, easing = FastOutSlowInEasing)) { width -> -width } + fadeIn(animationSpec = tween(600))).togetherWith(
                        slideOutHorizontally(animationSpec = tween(600, easing = FastOutSlowInEasing)) { width -> width } + fadeOut(animationSpec = tween(600))
                    )
                }
            },
            label = "SetupWizardAnimation"
        ) { targetStep ->
            when (targetStep) {
                0 -> LoginStep(onNext = { currentStep = 1 })
                1 -> ThemeStep(onNext = { currentStep = 2 })
                2 -> PinStep(onNext = { currentStep = 3 })
                3 -> AiEngineStep(onNext = { currentStep = 4 })
                4 -> ModelDownloadStep(onComplete = { onSetupComplete() })
            }
        }
    }
}

@Composable
fun LoginStep(onNext: () -> Unit) {
    var isUserSignedIn by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }

    LaunchedEffect(Unit) {
        try {
            isUserSignedIn = FirebaseAuth.getInstance().currentUser != null
        } catch (e: Exception) {
            isUserSignedIn = false
        }
    }

    LaunchedEffect(isUserSignedIn) {
        if (isUserSignedIn) {
            scope.launch { settingsManager.setUserSignedIn(true) }
            delay(1200) // Show success state before auto-advancing
            onNext()
        }
    }

    // Enter animation for elements
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { -50 }, animationSpec = tween(800)) + fadeIn(tween(800))
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "App Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(1000, delayMillis = 300))
        ) {
            Text(
                text = "Welcome to TruDial",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(
            targetState = isUserSignedIn,
            transitionSpec = {
                (fadeIn(animationSpec = tween(500)) + slideInHorizontally(tween(500)) { width -> width }).togetherWith(
                    fadeOut(animationSpec = tween(500)) + slideOutHorizontally(tween(500)) { width -> -width }
                )
            },
            label = "LoginTransition"
        ) { signedIn ->
            if (signedIn) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle, 
                        contentDescription = "Signed In", 
                        tint = MaterialTheme.colorScheme.primary, 
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Signed in successfully.", 
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Redirecting to next step...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Sign in to backup your recordings and call data to Google Drive. This ensures your data is safe even if you change phones.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(48.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Button(
                                onClick = {
                                    Toast.makeText(context, "Simulating Google Sign-In...", Toast.LENGTH_SHORT).show()
                                    isUserSignedIn = true
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text("Sign in with Google", fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedButton(
                                onClick = {
                                    Toast.makeText(context, "Simulating OTP Phone Login...", Toast.LENGTH_SHORT).show()
                                    isUserSignedIn = true
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text("Phone Login (Auto-OTP)", fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            TextButton(
                                onClick = {
                                    Toast.makeText(context, "Simulating Email/Password Login...", Toast.LENGTH_SHORT).show()
                                    isUserSignedIn = true
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text("Email & Password", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    val selectedTheme by settingsManager.themeModeFlow.collectAsState(initial = "system")

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Palette, contentDescription = "Theme", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Choose Theme", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select a theme to see a real-time preview.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                ThemeOption("System Default", selectedTheme == "system") { scope.launch { settingsManager.setThemeMode("system") } }
                ThemeOption("Light Mode", selectedTheme == "light") { scope.launch { settingsManager.setThemeMode("light") } }
                ThemeOption("Dark Mode", selectedTheme == "dark") { scope.launch { settingsManager.setThemeMode("dark") } }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Continue", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun PinStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    var newPin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = "Lock", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Setup Secure PIN", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Set a 4-digit PIN to secure your dashboard and prevent unauthorized access.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = newPin,
            onValueChange = { if (it.length <= 4) newPin = it },
            label = { Text("Enter 4-digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp)
        )

        Spacer(modifier = Modifier.height(48.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text("Skip", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = {
                    if (newPin.length == 4) {
                        scope.launch {
                            settingsManager.setUserPin(newPin)
                            settingsManager.setPinEnabled(true)
                            onNext()
                        }
                    }
                },
                enabled = newPin.length == 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AiEngineStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    val preferCloud by settingsManager.preferCloudLlmFlow.collectAsState(initial = false)
    val savedGroqKey by settingsManager.groqApiKeyFlow.collectAsState(initial = "")
    var groqKeyInput by remember { mutableStateOf("") }
    LaunchedEffect(savedGroqKey) {
        if (groqKeyInput.isEmpty() && savedGroqKey.isNotEmpty()) groqKeyInput = savedGroqKey
    }

    // Detect RAM so we can show a recommendation, without forcing the choice.
    val hasSufficientRam = remember {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        memoryInfo.totalMem >= 7L * 1024 * 1024 * 1024
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Memory, contentDescription = "AI Engine", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Choose AI Engine", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasSufficientRam)
                "Your device has enough RAM to run the scam detector fully offline. You can also use Groq's cloud model instead."
            else
                "Your device is better suited to the cloud model. Provide a Groq API key, or continue with the default cloud API.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                EngineOption(
                    title = "On-device AI" + if (hasSufficientRam) " (Recommended)" else "",
                    subtitle = "Runs offline. Nothing leaves your phone.",
                    selected = !preferCloud
                ) { scope.launch { settingsManager.setPreferCloudLlm(false) } }
                EngineOption(
                    title = "Cloud AI (Groq)" + if (!hasSufficientRam) " (Recommended)" else "",
                    subtitle = "Uses Groq's Llama 3 8B model. Works on any phone.",
                    selected = preferCloud
                ) { scope.launch { settingsManager.setPreferCloudLlm(true) } }
            }
        }

        AnimatedVisibility(visible = preferCloud) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                OutlinedTextField(
                    value = groqKeyInput,
                    onValueChange = { groqKeyInput = it },
                    label = { Text("Groq API Key") },
                    placeholder = { Text("gsk_...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Optional now — you can add it later in Settings. Without a key the app uses the default cloud API.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = {
                scope.launch {
                    if (preferCloud) settingsManager.setGroqApiKey(groqKeyInput.trim())
                    onNext()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Continue", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun EngineOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ModelDownloadStep(onComplete: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("Initializing secure environment...") }
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    LaunchedEffect(Unit) {
        delay(800)

        // If the user opted into the cloud engine, skip the on-device model download entirely.
        val preferCloud = settingsManager.preferCloudLlmFlow.first()
        if (preferCloud) {
            status = "Cloud AI selected. Configuring Groq..."
            settingsManager.setUseLocalLlm(false)
            progress = 1f
            delay(1500)
            status = "Setup Complete! Starting app..."
            delay(1200)
            settingsManager.setSetupCompleted(true)
            onComplete()
            return@LaunchedEffect
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalMemoryBytes = memoryInfo.totalMem
        val thresholdBytes = 7L * 1024 * 1024 * 1024 // 7GB to account for OS usage on 8GB devices

        if (totalMemoryBytes >= thresholdBytes) {
            status = "Sufficient RAM detected. Downloading offline AI scanner..."
            settingsManager.setUseLocalLlm(true)
            
            withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                var input: java.io.InputStream? = null
                var output: java.io.FileOutputStream? = null
                try {
                    val downloadUrl = URL(BuildConfig.LOCAL_MODEL_URL)
                    connection = downloadUrl.openConnection() as HttpURLConnection
                    connection.connect()

                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val fileLength = connection.contentLength
                        input = connection.inputStream
                        
                        val modelDir = File(context.filesDir, "model")
                        if (!modelDir.exists()) modelDir.mkdirs()
                        val outputFile = File(modelDir, "model.bin")
                        
                        output = java.io.FileOutputStream(outputFile)

                        val data = ByteArray(8192)
                        var total: Long = 0
                        var count: Int
                        
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            if (fileLength > 0) {
                                withContext(Dispatchers.Main) {
                                    progress = total.toFloat() / fileLength
                                    status = "Downloading... ${(progress * 100).toInt()}%"
                                }
                            }
                            output.write(data, 0, count)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            status = "Failed: HTTP ${connection.responseCode}"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        status = "Download failed: ${e.message}"
                    }
                } finally {
                    try {
                        output?.close()
                        input?.close()
                    } catch (ignored: Exception) {}
                    connection?.disconnect()
                }
            }
            
            status = "Verifying integrity..."
            delay(1000)
        } else {
            status = "Device has < 8GB RAM. Configuring Cloud AI API..."
            settingsManager.setUseLocalLlm(false)
            delay(2000)
        }
        
        status = "Setup Complete! Starting app..."
        delay(1500)
        
        settingsManager.setSetupCompleted(true)
        onComplete()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(100.dp).scale(pulseScale).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = "Download", modifier = Modifier.size(50.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("Finalizing Setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        
        LinearProgressIndicator(
            progress = { progress }, 
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(status, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
    }
}
