package com.haven.notes.data

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class Contact(
    val name: String,
    val phone: String
)

data class CheckIn(
    val id: String,
    val deadline: Double,
    val state: String
)

data class Delivery(
    val phone: String,
    val state: String,
    val attempts: Int = 0
)

data class AlertRecord(
    val id: String,
    val kind: String,
    val created: Double,
    val deliveries: List<Delivery> = emptyList()
)

data class ServerStatus(
    val mode: String = "demo",
    val contacts: List<Contact> = emptyList(),
    val timer: CheckIn? = null,
    val alerts: List<AlertRecord> = emptyList()
)

data class CommandResult(
    val id: String? = null,
    val state: String? = null,
    val deadline: Double? = null
)

data class LocationPayload(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val timestamp: Double,
    val battery: Int? = null,
    val charging: Boolean? = null,
    val trigger: String? = null
)

data class PendingRequest(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val bodyJson: String
)

data class FamilySession(
    val origin: String,
    val token: String,
    val id: String,
    val controller: String,
    var enabled: Boolean = false
)

data class DailyNote(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val date: Long = System.currentTimeMillis(),
    val colorIndex: Int = 0
)

data class PreviewResponse(
    @SerializedName("controller_name") val controllerName: String? = null,
    val expires: Double? = null
)

data class JoinResponse(
    val id: String? = null,
    val token: String? = null,
    @SerializedName("controller_name") val controllerName: String? = null
)

data class MeResponse(
    val id: String? = null,
    val name: String? = null,
    val sharing: Boolean = false,
    @SerializedName("controller_name") val controllerName: String? = null,
    @SerializedName("last_location") val lastLocation: LocationPayload? = null
)

data class CallRecord(
    val id: String,
    val number: String,
    val name: String? = null,
    val type: String,
    val timestamp: Double,
    val duration: Long
)

