package com.haven.notes.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.haven.notes.data.Storage

class BatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BATTERY_LOW || action == Intent.ACTION_SHUTDOWN || action == "android.intent.action.ACTION_SHUTDOWN") {
            val storage = Storage(context)
            if (storage.familySession?.enabled == true) {
                NotesLocationService.emergencyBatteryFix(context)
            }
        }
    }
}
