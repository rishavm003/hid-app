package com.haven.notes.viewmodel

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.haven.notes.data.ApiClient
import com.haven.notes.data.FamilySession
import com.haven.notes.data.JoinResponse
import com.haven.notes.data.MeResponse
import com.haven.notes.data.PreviewResponse
import com.haven.notes.data.Storage
import com.haven.notes.service.NotesLocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

class FamilyViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = Storage(application)
    private val apiClient = ApiClient()
    private val gson = Gson()

    private val _session = MutableStateFlow<FamilySession?>(null)
    val session: StateFlow<FamilySession?> = _session.asStateFlow()

    private val _message = MutableStateFlow("Join only a controller you know and trust.")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _reviewedController = MutableStateFlow<String?>(null)
    val reviewedController: StateFlow<String?> = _reviewedController.asStateFlow()

    private val _lastUpload = MutableStateFlow<Long?>(null)
    val lastUpload: StateFlow<Long?> = _lastUpload.asStateFlow()

    private var reviewedOrigin = ""
    private var reviewedCode = ""

    init {
        _session.value = storage.familySession
        _session.value?.id?.let { id ->
            val last = storage.getLastFamilyUpload(id)
            if (last > 0) _lastUpload.value = last
        }
    }

    fun parseInvitation(rawInput: String): Pair<String, String>? {
        val trimmed = rawInput.trim()
        val prefix = when {
            trimmed.startsWith("NOTES1.") -> "NOTES1."
            trimmed.startsWith("HAVEN1.") -> "HAVEN1."
            else -> ""
        }
        if (prefix.isNotEmpty()) {
            return try {
                val b64 = trimmed.substring(prefix.length)
                val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                val mapType = object : TypeToken<Map<String, String>>() {}.type
                val map: Map<String, String> = gson.fromJson(decoded, mapType)
                val origin = map["origin"] ?: return null
                val code = map["code"] ?: return null
                Pair(origin, code)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    fun preview(origin: String, code: String) {
        if (_busy.value) return
        _busy.value = true
        _reviewedController.value = null

        viewModelScope.launch {
            try {
                val cleanOrigin = apiClient.validateHttpsOrigin(origin)
                val body = gson.toJson(mapOf("code" to code.trim()))
                val resp = apiClient.post(cleanOrigin, "/v1/family/preview", bodyJson = body)
                val result = gson.fromJson(resp, PreviewResponse::class.java)
                _reviewedController.value = result.controllerName
                reviewedOrigin = cleanOrigin
                reviewedCode = code.trim()
                _message.value = "Review the controller identity below before joining. Location stays off until you enable it."
            } catch (e: Exception) {
                _message.value = e.message ?: "Could not preview invitation."
            } finally {
                _busy.value = false
            }
        }
    }

    fun join(origin: String, code: String, name: String) {
        if (_busy.value || _session.value != null || _reviewedController.value == null) {
            _message.value = "Review this invitation first."
            return
        }
        _busy.value = true

        viewModelScope.launch {
            try {
                val cleanOrigin = apiClient.validateHttpsOrigin(origin)
                val body = gson.toJson(mapOf(
                    "code" to code.trim(),
                    "name" to name.trim(),
                    "consent" to true
                ))
                val resp = apiClient.post(cleanOrigin, "/v1/family/join", bodyJson = body)
                val result = gson.fromJson(resp, JoinResponse::class.java)

                if (result.token.isNullOrEmpty() || result.id.isNullOrEmpty()) {
                    throw Exception("Invalid join response from server.")
                }

                val newSession = FamilySession(
                    origin = cleanOrigin,
                    token = result.token,
                    id = result.id,
                    controller = result.controllerName ?: "Controller",
                    enabled = true
                )
                storage.familySession = newSession
                _session.value = newSession

                // Automatically enable sharing on server and start background service
                try {
                    val shareBody = gson.toJson(mapOf("enabled" to true))
                    apiClient.post(cleanOrigin, "/v1/family/sharing", token = result.token, bodyJson = shareBody)
                } catch (_: Exception) {}

                NotesLocationService.start(getApplication())
                _message.value = "Joined ${newSession.controller}. Hourly location sharing is ACTIVE."
            } catch (e: Exception) {
                _message.value = "Join not confirmed: ${e.message}. A used invitation may need replacing."
            } finally {
                _busy.value = false
            }
        }
    }

    fun enable(context: Context) {
        val current = _session.value ?: return
        if (_busy.value) return
        _busy.value = true

        viewModelScope.launch {
            try {
                val body = gson.toJson(mapOf("enabled" to true))
                apiClient.post(current.origin, "/v1/family/sharing", token = current.token, bodyJson = body)
                val updated = current.copy(enabled = true)
                storage.familySession = updated
                _session.value = updated

                NotesLocationService.start(context)
                _message.value = "Sharing enabled with ${current.controller}."
            } catch (e: Exception) {
                _message.value = "Could not enable sharing: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun stop(context: Context) {
        NotesLocationService.stop(context)
        val current = _session.value ?: return
        val updated = current.copy(enabled = false)
        storage.familySession = updated
        _session.value = updated
        _message.value = "Stopped on this phone. Confirming pause with the server…"

        viewModelScope.launch {
            try {
                val body = gson.toJson(mapOf("enabled" to false))
                apiClient.post(current.origin, "/v1/family/sharing", token = current.token, bodyJson = body)
                _message.value = "Sharing paused. Your controller can still see your last shared location and notes until you erase them."
            } catch (e: Exception) {
                _message.value = "Stopped locally. Server pause is unconfirmed; last shared data remains visible."
            }
        }
    }

    fun reconcile() {
        val current = _session.value ?: return
        if (_busy.value) return

        viewModelScope.launch {
            try {
                val resp = apiClient.get(current.origin, "/v1/family/me", token = current.token)
                if (current.enabled) {
                    if (!result.sharing) {
                        try {
                            val shareBody = gson.toJson(mapOf("enabled" to true))
                            apiClient.post(current.origin, "/v1/family/sharing", token = current.token, bodyJson = shareBody)
                        } catch (_: Exception) {}
                    }
                    NotesLocationService.start(getApplication())
                }
                val last = storage.getLastFamilyUpload(current.id)
                if (last > 0) _lastUpload.value = last
            } catch (e: Exception) {
                _message.value = "Cannot verify family access: ${e.message}"
            }
        }
    }

    fun erase(context: Context) {
        stop(context)
        val current = _session.value ?: return
        _busy.value = true

        viewModelScope.launch {
            try {
                apiClient.post(current.origin, "/v1/family/erase", token = current.token, bodyJson = "{}")
                storage.clearLastFamilyUpload(current.id)
                _lastUpload.value = null
                _message.value = "Shared family location and manual notes erased."
            } catch (e: Exception) {
                _message.value = "Erase not confirmed: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun shareNote(text: String, occurredDate: Date, onComplete: (Boolean) -> Unit) {
        val current = _session.value ?: return
        if (_busy.value) return
        _busy.value = true

        viewModelScope.launch {
            try {
                val body = gson.toJson(mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "text" to text.trim(),
                    "occurred" to (occurredDate.time / 1000.0),
                    "consent" to true
                ))
                apiClient.post(current.origin, "/v1/family/notes", token = current.token, bodyJson = body)
                _message.value = "Your manual note was shared with ${current.controller}."
                onComplete(true)
            } catch (e: Exception) {
                _message.value = "Note not confirmed: ${e.message}"
                onComplete(false)
            } finally {
                _busy.value = false
            }
        }
    }

    fun shareNow(context: Context) {
        val current = _session.value ?: return
        if (!current.enabled) {
            _message.value = "Enable family sharing first."
            return
        }
        NotesLocationService.shareNow(context)
        _message.value = "Requesting a fresh location. Waiting for server confirmation…"
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _lastUpload.value = storage.getLastFamilyUpload(current.id)
        }
    }

    fun reconnect(newOrigin: String) {
        val current = _session.value ?: return
        if (current.enabled) {
            _message.value = "Stop sharing locally before reconnecting to a new server address."
            return
        }
        _busy.value = true

        viewModelScope.launch {
            try {
                val clean = apiClient.validateHttpsOrigin(newOrigin)
                val resp = apiClient.get(clean, "/v1/family/me", token = current.token)
                val result = gson.fromJson(resp, MeResponse::class.java)

                if (result.id != current.id || result.controllerName != current.controller) {
                    throw Exception("This server does not match your family account.")
                }

                val body = gson.toJson(mapOf("enabled" to false))
                apiClient.post(clean, "/v1/family/sharing", token = current.token, bodyJson = body)

                val updated = current.copy(origin = clean, enabled = false)
                storage.familySession = updated
                _session.value = updated
                _message.value = "Reconnected. Enable sharing again, then reconnect Home and Circle below if you use SOS."
            } catch (e: Exception) {
                _message.value = "Reconnect failed: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }
}
