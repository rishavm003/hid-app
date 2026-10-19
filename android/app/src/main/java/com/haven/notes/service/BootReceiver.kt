package com.haven.notes.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.haven.notes.data.Storage

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val storage = Storage(context)
            val session = storage.familySession
            if (session != null && session.enabled) {
                NotesLocationService.start(context)
            }
        }
    }
}
