package com.example.ui.screens

import android.telecom.Call
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.telecom.CallController

private const val INCOMING_TAG = "TruDialCall"

/**
 * Incoming-call UI. Because TruDial is the default phone app, the system no longer shows its own
 * ringing screen — this replaces it. Answer/Decline act on the live telecom [Call] via
 * [CallController]. Navigation is driven by the real call state so the screen also reacts when the
 * call is answered/ended from elsewhere (e.g. a headset).
 */
@Composable
fun IncomingCallScreen(
    callerId: String,
    onAnswered: () -> Unit,
    onDeclined: () -> Unit
) {
    val callState by CallController.state.collectAsState()

    LaunchedEffect(callState) {
        when (callState) {
            Call.STATE_ACTIVE -> {
                Log.i(INCOMING_TAG, "Incoming call became ACTIVE → opening monitor")
                onAnswered()
            }
            Call.STATE_DISCONNECTED -> {
                Log.i(INCOMING_TAG, "Incoming call ended before/while ringing → dismissing")
                onDeclined()
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "incoming_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(72.dp))
            Text(
                "Incoming call",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                callerId,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Screened by TruDial",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = {
                            Log.i(INCOMING_TAG, "User declined incoming call")
                            CallController.disconnect()
                        },
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Decline", modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Decline", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Answer
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = {
                            Log.i(INCOMING_TAG, "User answered incoming call")
                            CallController.answer()
                        },
                        containerColor = Color(0xFF16A34A),
                        contentColor = Color.White,
                        modifier = Modifier.size(72.dp).scale(pulseScale)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Answer", modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Answer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
