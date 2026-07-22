package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.SettingsManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import com.example.BuildConfig
import android.util.Log

@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    
    val pinEnabled by settingsManager.pinEnabledFlow.collectAsState(initial = null)
    val savedPin by settingsManager.userPinFlow.collectAsState(initial = "")
    val userSignedIn by settingsManager.userSignedInFlow.collectAsState(initial = false)
    
    var enteredPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    // Firebase Auth State
    var isUserSignedIn by remember { mutableStateOf(false) }

    LaunchedEffect(userSignedIn) {
        if (userSignedIn) {
            isUserSignedIn = true
        } else {
            try {
                val auth = FirebaseAuth.getInstance()
                isUserSignedIn = auth.currentUser != null
            } catch (e: Exception) {
                // Firebase not initialized
                isUserSignedIn = false
            }
        }
    }

    val scope = rememberCoroutineScope()
    
    // Auto-advance if signed in and PIN is disabled
    LaunchedEffect(isUserSignedIn, pinEnabled) {
        if (isUserSignedIn && pinEnabled == false) {
            onAuthenticated()
        }
    }

    if (pinEnabled == null) {
        return // Loading datastore
    }

    AnimatedGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Lock",
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "TruDial Security",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(48.dp))

            AnimatedContent(
                targetState = !isUserSignedIn,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                },
                label = "auth_content"
            ) { needsSignIn ->
                if (needsSignIn) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Sign in to securely backup your call data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val webClientId = BuildConfig.WEB_CLIENT_ID
                                    if (webClientId.isEmpty() || webClientId == "YOUR_WEB_CLIENT_ID" || webClientId == "\"YOUR_WEB_CLIENT_ID\"") {
                                        Toast.makeText(context, "Google Sign-In is not configured. Please add WEB_CLIENT_ID to Secrets.", Toast.LENGTH_LONG).show()
                                        return@launch
                                    }
                                    try {
                                        val credentialManager = CredentialManager.create(context)
                                        val googleIdOption = GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId(webClientId)
                                            .setAutoSelectEnabled(true)
                                            .build()
                                            
                                        val request = GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()
                                            
                                        val result = credentialManager.getCredential(context, request)
                                        val credential = result.credential
                                        
                                        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                            val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                                            val authResult = FirebaseAuth.getInstance().signInWithCredential(authCredential).await()
                                            if (authResult.user != null) {
                                                isUserSignedIn = true
                                                settingsManager.setUserSignedIn(true)
                                            } else {
                                                Toast.makeText(context, "Authentication failed.", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Unexpected credential type.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AuthScreen", "Google Sign-In failed", e)
                                        Toast.makeText(context, "Sign-In failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .shadow(8.dp, RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sign in with Google", fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (pinEnabled == true) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(16.dp, RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Enter App PIN",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedTextField(
                                value = enteredPin,
                                onValueChange = { 
                                    if (it.length <= 4) enteredPin = it 
                                    error = false
                                },
                                label = { Text("4-digit PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                isError = error,
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            if (error) {
                                Text(
                                    text = "Incorrect PIN. Try again.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Button(
                                onClick = {
                                    if (enteredPin == savedPin) {
                                        onAuthenticated()
                                    } else {
                                        error = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                enabled = enteredPin.length == 4,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Unlock App", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
