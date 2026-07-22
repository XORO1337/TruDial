package com.example.telecom

import android.telecom.Call
import android.telecom.VideoProfile
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide bridge between the telecom [Call] owned by the [com.example.service.TruDialInCallService]
 * and the Compose UI (ActiveCallScreen).
 *
 * The service pushes the current call in via [attach] / [detach]; the UI observes [state] / [hasCall]
 * and issues [toggleHold] / [disconnect] actions. This is the only way to actually hold a call —
 * TelecomManager has no hold API, so it requires the live Call object from an InCallService.
 */
object CallController {
    private const val TAG = "TruDialCall"

    private var call: Call? = null

    private val _hasCall = MutableStateFlow(false)
    val hasCall: StateFlow<Boolean> = _hasCall.asStateFlow()

    private val _callerNumber = MutableStateFlow("")
    val callerNumber: StateFlow<String> = _callerNumber.asStateFlow()

    private val _state = MutableStateFlow(Call.STATE_NEW)
    val state: StateFlow<Int> = _state.asStateFlow()

    private val _canHold = MutableStateFlow(false)
    val canHold: StateFlow<Boolean> = _canHold.asStateFlow()

    private val _speakerOn = MutableStateFlow(false)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    /**
     * Set by TruDialInCallService so the UI can route call audio. Speakerphone is the only way the
     * mic (and thus SpeechRecognizer) can pick up the remote party's voice — normal apps cannot
     * access call audio directly on modern Android.
     */
    var speakerController: ((Boolean) -> Unit)? = null

    val hasActiveCall: Boolean get() = call != null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(c: Call, newState: Int) {
            Log.d(TAG, "CallController: state changed → $newState")
            _state.value = newState
        }

        override fun onDetailsChanged(c: Call, details: Call.Details) {
            _canHold.value = details.can(Call.Details.CAPABILITY_HOLD)
        }
    }

    @Suppress("DEPRECATION") // Call.getState() supports minSdk 24; Call.Details.getState() is API 31+.
    fun attach(newCall: Call) {
        call?.unregisterCallback(callback)
        call = newCall
        newCall.registerCallback(callback)
        _callerNumber.value = newCall.details?.handle?.schemeSpecificPart ?: "Unknown"
        _state.value = newCall.state
        _canHold.value = newCall.details?.can(Call.Details.CAPABILITY_HOLD) ?: false
        _hasCall.value = true
        Log.i(TAG, "CallController: attached call from ${_callerNumber.value}")
    }

    fun detach(removedCall: Call) {
        if (call == removedCall || call == null) {
            call?.unregisterCallback(callback)
            call = null
            _state.value = Call.STATE_DISCONNECTED
            _hasCall.value = false
            _speakerOn.value = false
            Log.i(TAG, "CallController: detached call")
        }
    }

    /** Route call audio to the speaker (or back to earpiece/headset). No-op without a live call. */
    fun setSpeaker(on: Boolean) {
        Log.i(TAG, "CallController: setSpeaker($on)")
        speakerController?.invoke(on)
    }

    /** Called by the service when the actual audio route changes, to keep the UI in sync. */
    fun updateSpeakerState(on: Boolean) {
        _speakerOn.value = on
    }

    fun answer() {
        val c = call
        if (c == null) {
            Log.w(TAG, "CallController: answer with no active call")
            return
        }
        Log.i(TAG, "CallController: answering call")
        c.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun toggleHold() {
        val c = call
        if (c == null) {
            Log.w(TAG, "CallController: toggleHold with no active call")
            return
        }
        if (_state.value == Call.STATE_HOLDING) {
            Log.i(TAG, "CallController: unholding call")
            c.unhold()
        } else {
            Log.i(TAG, "CallController: holding call")
            c.hold()
        }
    }

    fun disconnect() {
        val c = call
        if (c == null) {
            Log.w(TAG, "CallController: disconnect with no active call")
            return
        }
        Log.i(TAG, "CallController: disconnecting call")
        c.disconnect()
    }
}
