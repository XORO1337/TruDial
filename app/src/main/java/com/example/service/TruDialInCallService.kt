package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.telecom.CallController

/**
 * Makes TruDial a real default phone app. Without this, the system dialer owns the in-call UI and
 * the app can neither reliably show its screen when a call is answered nor hold the call.
 *
 * When a call becomes ACTIVE we surface the monitoring UI via a full-screen-intent notification —
 * the sanctioned way to launch UI from the background on Android 10+ (a plain startActivity from
 * the background is silently blocked, which is why the app previously had to be opened by hand).
 */
class TruDialInCallService : InCallService() {

    companion object {
        private const val TAG = "TruDialCall"
        private const val CHANNEL_ID = "trudial_active_call"
        private const val NOTIF_ID = 42
    }

    // Ensures we only force speaker on the first activation, so a later manual toggle sticks.
    private var autoSpeakerApplied = false

    override fun onCreate() {
        super.onCreate()
        CallController.speakerController = { on -> setSpeaker(on) }
    }

    override fun onDestroy() {
        CallController.speakerController = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION") // onCallAudioStateChanged is superseded by endpoints on API 34 but still fired.
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallController.updateSpeakerState(audioState.route == CallAudioState.ROUTE_SPEAKER)
    }

    private fun setSpeaker(on: Boolean) {
        Log.i(TAG, "InCallService: routing audio to ${if (on) "SPEAKER" else "earpiece/headset"}")
        setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.i(TAG, "InCallService: onCallAdded state=${stateName(callState(call))}")
        CallController.attach(call)

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                Log.d(TAG, "InCallService: onStateChanged → ${stateName(state)}")
                when (state) {
                    Call.STATE_RINGING -> surfaceCallUi(c, incoming = true)
                    Call.STATE_ACTIVE -> {
                        applyAutoSpeaker()
                        surfaceCallUi(c, incoming = false)
                    }
                    Call.STATE_DISCONNECTED -> cancelNotification()
                }
            }
        })

        // Surface immediately for the state the call is already in when handed to us.
        when (callState(call)) {
            Call.STATE_RINGING -> surfaceCallUi(call, incoming = true)
            Call.STATE_ACTIVE, Call.STATE_DIALING -> {
                applyAutoSpeaker()
                surfaceCallUi(call, incoming = false)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.i(TAG, "InCallService: onCallRemoved")
        CallController.detach(call)
        cancelNotification()
        autoSpeakerApplied = false
    }

    /**
     * Force speakerphone the first time a call goes active so the mic can pick up the remote party's
     * voice for transcription. Only applied once, so a later manual toggle by the user is respected.
     */
    private fun applyAutoSpeaker() {
        if (!autoSpeakerApplied) {
            autoSpeakerApplied = true
            setSpeaker(true)
        }
    }

    @Suppress("DEPRECATION") // Call.getState() supports minSdk 24; Call.Details.getState() is API 31+.
    private fun callState(call: Call): Int = call.state

    private fun surfaceCallUi(call: Call, incoming: Boolean) {
        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        Log.i(TAG, "InCallService: surfacing ${if (incoming) "incoming-call" else "monitoring"} UI for $number")

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // Only one of these is set so MainActivity knows which screen to show.
            if (incoming) putExtra("incoming_call", true) else putExtra("start_monitoring", true)
            putExtra("caller_id", number)
        }
        val pending = PendingIntent.getActivity(
            this, if (incoming) 1 else 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(if (incoming) "Incoming call" else "TruDial is monitoring your call")
            .setContentText(number)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, notification)

        // Belt-and-suspenders: a direct launch works when the app is foregrounded / on older OSes.
        // The full-screen intent above covers the background-launch-restricted case.
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Direct startActivity blocked; relying on full-screen intent", e)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Active Call Monitoring",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
        }
    }

    private fun cancelNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
    }

    private fun stateName(state: Int): String = when (state) {
        Call.STATE_NEW -> "NEW"
        Call.STATE_RINGING -> "RINGING"
        Call.STATE_DIALING -> "DIALING"
        Call.STATE_CONNECTING -> "CONNECTING"
        Call.STATE_ACTIVE -> "ACTIVE"
        Call.STATE_HOLDING -> "HOLDING"
        Call.STATE_DISCONNECTED -> "DISCONNECTED"
        else -> state.toString()
    }
}
