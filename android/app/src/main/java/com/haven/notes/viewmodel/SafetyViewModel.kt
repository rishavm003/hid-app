package com.haven.notes.viewmodel

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import com.haven.notes.data.ApiClient
import com.haven.notes.data.CheckIn
import com.haven.notes.data.CommandResult
import com.haven.notes.data.LocationPayload
import com.haven.notes.data.PendingRequest
import com.haven.notes.data.ServerStatus
import com.haven.notes.data.Storage
import com.haven.notes.service.ReminderReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class SafetyViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = Storage(application)
    private val apiClient = ApiClient()
    private val gson = Gson()

    private val _status = MutableStateFlow<ServerStatus?>(null)
    val status: StateFlow<ServerStatus?> = _status.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow("Connect your server to get started.")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _locationText = MutableStateFlow("Location sharing is off")
    val locationText: StateFlow<String> = _locationText.asStateFlow()

    private val _sharingLocation = MutableStateFlow(false)
    val sharingLocation: StateFlow<Boolean> = _sharingLocation.asStateFlow()

    private val _pending = MutableStateFlow<PendingRequest?>(null)
    val pending: StateFlow<PendingRequest?> = _pending.asStateFlow()

    var endpoint: String
        get() = storage.endpoint
        set(value) { storage.endpoint = value }

    var token: String
        get() = storage.token
        set(value) { storage.token = value }

    var consent: Boolean
        get() = storage.consent
        set(value) { storage.consent = value }

    var decoyPin: String
        get() = storage.decoyPin
        set(value) { storage.decoyPin = value }

    private var lastLocation: Location? = null

    val activeTimer: CheckIn?
        get() {
            val timer = _status.value?.timer
            return if (timer?.state == "active") timer else null
        }

    init {
        _pending.value = storage.pendingRequest
        if (storage.endpoint.isNotBlank() && storage.token.isNotBlank()) {
            refresh()
        }
    }

    fun saveSettings(newEndpoint: String, newToken: String, newConsent: Boolean) {
        if (_pending.value != null) {
            _message.value = "Resolve the pending request before changing servers."
            return
        }
        try {
            val cleanUrl = apiClient.validateHttpsOrigin(newEndpoint)
            if (newToken.trim().length < 32) {
                _message.value = "Enter the server's API token (at least 32 characters)."
                return
            }
            storage.endpoint = cleanUrl
            storage.token = newToken.trim()
            storage.consent = newConsent
            _status.value = null
            refresh()
        } catch (e: Exception) {
            _message.value = e.message ?: "Invalid settings."
        }
    }

    fun refresh() {
        if (storage.endpoint.isBlank() || storage.token.isBlank()) return
        viewModelScope.launch {
            try {
                val json = apiClient.get(storage.endpoint, "/v1/status", storage.token)
                val newStatus = gson.fromJson(json, ServerStatus::class.java)
                _status.value = newStatus
                if (_pending.value == null) {
                    _message.value = if (newStatus.mode == "demo") {
                        "Demo connected. No SMS messages will be sent."
                    } else {
                        "Server connected. SMS delivery is not guaranteed."
                    }
                }
            } catch (e: Exception) {
                _message.value = "Cannot reach server: ${e.message}"
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun setLocation(enabled: Boolean) {
        _sharingLocation.value = enabled
        if (enabled) {
            _locationText.value = "Requesting location…"
            try {
                val fused = LocationServices.getFusedLocationProviderClient(getApplication<Application>())
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            lastLocation = loc
                            _locationText.value = "Location available; shared only when you start an alert or timer."
                        } else {
                            fallbackSystemLocation()
                        }
                    }
                    .addOnFailureListener {
                        fallbackSystemLocation()
                    }
            } catch (e: Exception) {
                fallbackSystemLocation()
            }
        } else {
            lastLocation = null
            _locationText.value = "Location sharing is off"
        }
    }

    @SuppressLint("MissingPermission")
    private fun fallbackSystemLocation() {
        try {
            val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
                lastLocation = loc
                _locationText.value = "Location available; shared only when you start an alert or timer."
            } else {
                _locationText.value = "Location unavailable. Alerts can still be sent."
            }
        } catch (e: Exception) {
            _locationText.value = "Location unavailable. Alerts can still be sent."
        }
    }

    private fun locationPayload(): LocationPayload? {
        val loc = lastLocation ?: return null
        if (!_sharingLocation.value || loc.accuracy < 0) return null
        val ageSeconds = (System.currentTimeMillis() - loc.time) / 1000.0
        if (Math.abs(ageSeconds) > 90) return null
        return LocationPayload(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracy = loc.accuracy.toDouble(),
            timestamp = (loc.time / 1000.0)
        )
    }

    fun send(path: String, minutes: Int? = null) {
        if (!storage.consent) {
            _message.value = "First agree to sharing alerts with your chosen contacts in Setup."
            return
        }
        if (_busy.value || _pending.value != null) return

        val body = mutableMapOf<String, Any>()
        if (path == "/v1/checkins/complete") {
            body["id"] = activeTimer?.id ?: ""
        } else {
            locationPayload()?.let { body["location"] = it }
        }
        if (minutes != null) {
            body["minutes"] = minutes
        }

        val pending = PendingRequest(
            id = UUID.randomUUID().toString(),
            path = path,
            bodyJson = gson.toJson(body)
        )
        storage.pendingRequest = pending
        _pending.value = pending
        retry()
    }

    fun retry() {
        val current = _pending.value ?: return
        if (_busy.value) return
        _busy.value = true

        viewModelScope.launch {
            try {
                val json = apiClient.post(
                    origin = storage.endpoint,
                    path = current.path,
                    token = storage.token,
                    bodyJson = current.bodyJson,
                    idempotencyKey = current.id
                )
                val result = gson.fromJson(json, CommandResult::class.java)
                storage.pendingRequest = null
                _pending.value = null
                refresh()

                when (result.state) {
                    "active" -> {
                        val deadline = result.deadline ?: 0.0
                        if (deadline <= System.currentTimeMillis() / 1000.0) {
                            _message.value = "This saved request was accepted earlier, but its deadline has passed. Review Circle for alerts."
                        } else {
                            _message.value = if (_status.value?.mode == "demo") {
                                "Demo timer started. Missed check-ins will NOT send SMS."
                            } else {
                                "Server confirmed your check-in. It will alert contacts if you miss the deadline."
                            }
                            scheduleReminder(deadline)
                        }
                    }
                    "expired" -> {
                        _message.value = "The deadline already passed. The alert cannot be recalled. Contact your trusted people directly."
                    }
                    "complete" -> {
                        cancelReminder()
                        _message.value = "Server confirmed your check-in is complete."
                    }
                    "demo" -> {
                        _message.value = "Demo alert recorded. Nobody was contacted."
                    }
                    else -> {
                        _message.value = "Alert saved on server. Check activity for SMS submission status."
                    }
                }
            } catch (e: Exception) {
                _message.value = "Request not confirmed. It may have reached the server. Retry this request or refresh status. ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearPending() {
        storage.pendingRequest = null
        _pending.value = null
        _message.value = "Local retry cleared. This does not cancel anything already received by the server. Refresh status."
    }

    private fun scheduleReminder(deadlineSeconds: Double) {
        try {
            val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(getApplication(), ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                getApplication(),
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerMs = Math.max(
                System.currentTimeMillis() + 1000L,
                (deadlineSeconds * 1000.0 - 30000.0).toLong()
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
            }
        } catch (_: Exception) {}
    }

    private fun cancelReminder() {
        try {
            val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(getApplication(), ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                getApplication(),
                1001,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (_: Exception) {}
    }
}
