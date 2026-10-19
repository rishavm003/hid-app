package com.haven.notes.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Storage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("haven_notes_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // PIN
    var decoyPin: String
        get() = prefs.getString("haven_decoy_pin", "1998") ?: "1998"
        set(value) = prefs.edit().putString("haven_decoy_pin", value).apply()

    // Daily notes
    fun getDailyNotes(): List<DailyNote> {
        val json = prefs.getString("notes_decoy_notes_json", null) ?: return defaultNotes()
        return try {
            val type = object : TypeToken<List<DailyNote>>() {}.type
            gson.fromJson<List<DailyNote>>(json, type) ?: defaultNotes()
        } catch (e: Exception) {
            defaultNotes()
        }
    }

    fun saveDailyNotes(notes: List<DailyNote>) {
        val json = gson.toJson(notes)
        prefs.edit().putString("notes_decoy_notes_json", json).apply()
    }

    private fun defaultNotes(): List<DailyNote> {
        val now = System.currentTimeMillis()
        return listOf(
            DailyNote(title = "Grocery list", content = "Oat milk, coffee beans, olive oil, spinach, lemons", date = now - 3600000 * 2),
            DailyNote(title = "Ideas for weekend", content = "Check out the new farmer's market on Saturday morning.", date = now - 86400000),
            DailyNote(title = "Meeting summary", content = "Q3 targets look achievable. Follow up with team by Wednesday.", date = now - 86400000 * 3)
        )
    }

    // Safety endpoint & token
    var endpoint: String
        get() = prefs.getString("endpoint", "") ?: ""
        set(value) = prefs.edit().putString("endpoint", value.trim()).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(value) = prefs.edit().putString("token", value.trim()).apply()

    var consent: Boolean
        get() = prefs.getBoolean("consent", false)
        set(value) = prefs.edit().putBoolean("consent", value).apply()

    // Pending request
    var pendingRequest: PendingRequest?
        get() {
            val json = prefs.getString("pending_request", null) ?: return null
            return try {
                gson.fromJson(json, PendingRequest::class.java)
            } catch (e: Exception) {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove("pending_request").apply()
            } else {
                prefs.edit().putString("pending_request", gson.toJson(value)).apply()
            }
        }

    // Family session
    var familySession: FamilySession?
        get() {
            val json = prefs.getString("family_session", null) ?: return null
            return try {
                gson.fromJson(json, FamilySession::class.java)
            } catch (e: Exception) {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove("family_session").apply()
            } else {
                prefs.edit().putString("family_session", gson.toJson(value)).apply()
            }
        }

    fun getLastFamilyUpload(memberId: String): Long {
        return prefs.getLong("family_upload_$memberId", 0L)
    }

    fun setLastFamilyUpload(memberId: String, timestampMs: Long) {
        prefs.edit().putLong("family_upload_$memberId", timestampMs).apply()
    }

    fun clearLastFamilyUpload(memberId: String) {
        prefs.edit().remove("family_upload_$memberId").apply()
    }
}
