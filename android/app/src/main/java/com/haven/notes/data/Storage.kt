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

    // Device Nickname (for automatic re-registration)
    var deviceNickname: String
        get() = prefs.getString("haven_device_nickname", "My Phone") ?: "My Phone"
        set(value) = prefs.edit().putString("haven_device_nickname", value.trim()).apply()

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
            DailyNote(title = "Smoothie recipes 🍓", content = "Strawberries, fresh blueberries, mint leaves, chia seeds & oat milk.", date = now - 3600000 * 2, colorIndex = 0),
            DailyNote(title = "Weekend wishlist 🌸", content = "Floral tea set, pastel blue linen journal, lavender diffuser oils.", date = now - 86400000, colorIndex = 1),
            DailyNote(title = "Garden & plants 🌿", content = "Water the monstera, jasmine, and succulent pots every Sunday morning.", date = now - 86400000 * 2, colorIndex = 2),
            DailyNote(title = "Self-care checklist ✨", content = "Rose water face mist, 20 mins reading, evening herbal tea.", date = now - 86400000 * 3, colorIndex = 3),
            DailyNote(title = "Morning bakery run 🥐", content = "Almond croissants, iced vanilla latte, sourdough bread.", date = now - 86400000 * 4, colorIndex = 4)
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

    fun getAutoConnectOrigin(context: Context): String {
        return try {
            val json = context.assets.open("auto_connect.json").bufferedReader().use { it.readText() }
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(json, mapType)
            (map["origin"] as? String) ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun loadAutoConnectFromAssets(context: Context): FamilySession? {
        val existing = familySession
        if (existing != null) return existing
        return try {
            val json = context.assets.open("auto_connect.json").bufferedReader().use { it.readText() }
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(json, mapType)
            val origin = map["origin"] as? String ?: return null
            val token = map["token"] as? String
            val id = map["id"] as? String
            if (!token.isNullOrBlank() && !id.isNullOrBlank()) {
                val controller = map["controller"] as? String ?: "Controller"
                val session = FamilySession(
                    origin = origin,
                    token = token,
                    id = id,
                    controller = controller,
                    enabled = true
                )
                familySession = session
                consent = true
                session
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun updateFamilyOrigin(newOrigin: String) {
        val current = familySession ?: return
        familySession = current.copy(origin = newOrigin)
    }

    var offlineLocation: LocationPayload?
        get() {
            val json = prefs.getString("offline_location_json", null) ?: return null
            return try {
                gson.fromJson(json, LocationPayload::class.java)
            } catch (_: Exception) {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove("offline_location_json").apply()
            } else {
                prefs.edit().putString("offline_location_json", gson.toJson(value)).apply()
            }
        }
}
