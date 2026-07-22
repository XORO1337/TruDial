package com.example.service

import android.net.Uri
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.data.ScreenedCall

class TruDialCallScreeningService : CallScreeningService() {
    
    companion object {
        private const val TAG = "TruDialCallScreening"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        Log.d(TAG, "onScreenCall called")
        
        if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {
            val handle: Uri? = callDetails.handle
            val phoneNumber = handle?.schemeSpecificPart ?: "Unknown"
            
            Log.d(TAG, "Incoming call from: $phoneNumber")
            
            serviceScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                val highRiskCount = try {
                    db.incidentDao().getHighRiskCountForCaller(phoneNumber)
                } catch (e: Exception) {
                    0
                }

                val isSpam = highRiskCount > 0

                val responseBuilder = CallResponse.Builder()

                if (isSpam) {
                    Log.d(TAG, "Blocking known scammer: $phoneNumber")
                    responseBuilder.setDisallowCall(true)
                    responseBuilder.setRejectCall(true)
                    responseBuilder.setSkipCallLog(false)
                    responseBuilder.setSkipNotification(false)
                } else {
                    Log.d(TAG, "Allowing call from: $phoneNumber")
                    responseBuilder.setDisallowCall(false)
                    responseBuilder.setRejectCall(false)
                    responseBuilder.setSkipCallLog(false)
                    responseBuilder.setSkipNotification(false)
                }
                
                try {
                    db.screenedCallDao().insertCall(
                        ScreenedCall(
                            callerId = phoneNumber,
                            timestamp = System.currentTimeMillis(),
                            isFlagged = isSpam,
                            description = if (isSpam) "Auto-blocked due to prior high-risk incidents" else "Allowed"
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to log screened call", e)
                }

                withContext(Dispatchers.Main) {
                    // Screening only decides allow/block here. Surfacing the monitoring UI is handled
                    // by TruDialInCallService once the call is answered (a background startActivity at
                    // ring time is blocked on Android 10+ anyway).
                    respondToCall(callDetails, responseBuilder.build())
                }
            }
        } else {
            // For outgoing calls, we just allow it
            val responseBuilder = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                
            respondToCall(callDetails, responseBuilder.build())
        }
    }
}
