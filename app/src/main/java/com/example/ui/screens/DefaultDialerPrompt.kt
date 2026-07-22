package com.example.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** True when TruDial currently holds the default phone/dialer role. */
private fun isDefaultDialer(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    } else {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telecomManager.defaultDialerPackage == context.packageName
    }
}

/** Intent that opens the system prompt to make this app the default phone app. */
private fun requestDefaultDialerIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    } else {
        Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
    }
}

/**
 * Shows a one-time dialog asking the user to make TruDial the default phone app when it isn't
 * already. Without the dialer role the InCallService never activates, so real-time screening, the
 * incoming-call screen, and call hold cannot work. Drop this into any screen (e.g. the dashboard).
 */
@Composable
fun DefaultDialerPrompt() {
    val context = LocalContext.current
    // Assume held initially to avoid a dialog flash before the first check completes.
    var isDefault by remember { mutableStateOf(true) }
    var dismissed by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = isDefaultDialer(context)
        Log.i("TruDialCall", "Default dialer request returned; isDefault=$isDefault")
    }

    LaunchedEffect(Unit) {
        isDefault = isDefaultDialer(context)
    }

    if (!isDefault && !dismissed) {
        AlertDialog(
            onDismissRequest = { dismissed = true },
            icon = { Icon(Icons.Default.Phone, contentDescription = null) },
            title = { Text("Set TruDial as your phone app", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "TruDial needs to be your default phone app to screen calls in real time, show " +
                        "its own incoming-call screen, and place suspected scam calls on hold. Until then, " +
                        "your regular dialer handles calls and these protections stay off."
                )
            },
            confirmButton = {
                Button(
                    onClick = { launcher.launch(requestDefaultDialerIntent(context)) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Set as default")
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissed = true }) { Text("Not now") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}
