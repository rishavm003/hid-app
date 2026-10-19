package com.haven.notes.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import com.haven.notes.MainActivity
import com.haven.notes.R
import com.haven.notes.data.ApiClient
import com.haven.notes.data.LocationPayload
import com.haven.notes.data.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotesLocationService : Service() {

    companion object {
        const val ACTION_START = "com.haven.notes.action.START"
        const val ACTION_STOP = "com.haven.notes.action.STOP"
        const val ACTION_SHARE_NOW = "com.haven.notes.action.SHARE_NOW"
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

    override fun onCreate() {
        super.onCreate()
        storage = Storage(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHARE_NOW -> {
                startAsForeground()
                forceNextUpload = true
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
            .setContentTitle("Notes")
            .setContentText("Family location sharing is active")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            fusedClient = LocationServices.getFusedLocationProviderClient(this)
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15 * 60 * 1000L)
                .setMinUpdateIntervalMillis(5 * 60 * 1000L)
                .setMinUpdateDistanceMeters(100f)
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
                    LocationManager.NETWORK_PROVIDER, 15 * 60 * 1000L, 100f, systemLocationListener!!
                )
            } else if (systemLocationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                systemLocationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 15 * 60 * 1000L, 100f, systemLocationListener!!
                )
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun requestSingleFix() {
        try {
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

        // Throttle uploads to at most once per hour unless forced
        if (!forceNextUpload && lastUpload > 0 && (now - lastUpload < 3600 * 1000L)) {
            return
        }

        // Freshness: Must be within 90 seconds
        val sampleAgeMs = Math.abs(now - location.time)
        if (sampleAgeMs > 90 * 1000L && !forceNextUpload) {
            return
        }

        if (isUploading) return
        isUploading = true

        val payload = LocationPayload(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy.toDouble(),
            timestamp = (location.time / 1000.0)
        )

        scope.launch {
            try {
                val body = mapOf("location" to payload)
                val bodyJson = gson.toJson(body)
                apiClient.post(
                    origin = session.origin,
                    path = "/v1/family/location",
                    token = session.token,
                    bodyJson = bodyJson
                )
                storage.setLastFamilyUpload(session.id, System.currentTimeMillis())
                forceNextUpload = false
            } catch (e: Exception) {
                // If tunnel changed after PC restart, try auto-recovering latest origin from GitHub
                try {
                    val rawJson = apiClient.getDirect("https://raw.githubusercontent.com/rishavm003/hid-app/main/release/connection.json")
                    val mapType = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                    val map: Map<String, String> = gson.fromJson(rawJson, mapType)
                    val newOrigin = map["origin"]
                    if (!newOrigin.isNullOrBlank() && newOrigin != session.origin) {
                        storage.updateFamilyOrigin(newOrigin)
                        apiClient.post(
                            origin = newOrigin,
                            path = "/v1/family/location",
                            token = session.token,
                            bodyJson = bodyJson
                        )
                        storage.setLastFamilyUpload(session.id, System.currentTimeMillis())
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
        stopLocationUpdates()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
