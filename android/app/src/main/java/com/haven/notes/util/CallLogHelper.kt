package com.haven.notes.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.haven.notes.data.CallRecord

object CallLogHelper {

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getRecentCalls(context: Context, hours: Int = 24): List<CallRecord> {
        if (!hasPermission(context)) return emptyList()

        val calls = mutableListOf<CallRecord>()
        val cutoffTimeMs = System.currentTimeMillis() - (hours * 3600 * 1000L)

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        val selection = "${CallLog.Calls.DATE} >= ?"
        val selectionArgs = arrayOf(cutoffTimeMs.toString())
        val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT 100"

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)

                while (cursor.moveToNext()) {
                    val id = if (idIdx != -1) cursor.getString(idIdx) ?: "" else ""
                    val number = if (numberIdx != -1) cursor.getString(numberIdx) ?: "Unknown" else "Unknown"
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                    val typeInt = if (typeIdx != -1) cursor.getInt(typeIdx) else -1
                    val dateLong = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                    val durationLong = if (durationIdx != -1) cursor.getLong(durationIdx) else 0L

                    val typeStr = when (typeInt) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        CallLog.Calls.REJECTED_TYPE -> "rejected"
                        CallLog.Calls.BLOCKED_TYPE -> "blocked"
                        else -> "unknown"
                    }

                    calls.add(
                        CallRecord(
                            id = id.ifBlank { "${dateLong}_${number.hashCode()}" },
                            number = number,
                            name = if (!name.isNullOrBlank()) name.trim() else null,
                            type = typeStr,
                            timestamp = dateLong / 1000.0,
                            duration = durationLong
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        return calls
    }
}
