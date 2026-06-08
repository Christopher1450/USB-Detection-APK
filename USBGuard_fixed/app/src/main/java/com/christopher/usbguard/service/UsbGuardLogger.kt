package com.christopher.usbguard.service

import android.content.Context
import com.christopher.usbguard.model.LogIntegrityState
import com.christopher.usbguard.model.LogIntegrityStatus
import com.christopher.usbguard.model.RiskLevel
import com.christopher.usbguard.model.UsbSecurityEvent
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object UsbGuardLogger {

    private const val PRIMARY_FILE_NAME = "usb_guard_events_protected.jsonl"
    private const val MIRROR_FILE_NAME = "usb_guard_events_mirror.jsonl"
    private const val LEGACY_FILE_NAME = "usb_guard_events.jsonl"
    private const val RETENTION_LOCK_MS = 3_600_000L
    private const val GENESIS_HASH = "USB_GUARD_GENESIS_V1"

    fun log(context: Context, event: UsbSecurityEvent): UsbSecurityEvent {
        val now = System.currentTimeMillis()
        val previousHash = readEvents(context).maxByOrNull { it.timestamp }?.hash?.takeIf { it.isNotBlank() } ?: GENESIS_HASH

        val protectedEvent = event.copy(
            id = event.id.ifBlank { UUID.randomUUID().toString() },
            lockedUntilMillis = event.lockedUntilMillis.coerceAtLeast(now + RETENTION_LOCK_MS),
            previousHash = previousHash,
            hash = ""
        )

        val finalEvent = protectedEvent.copy(
            hash = sha256(canonicalString(protectedEvent))
        )

        val line = toJson(finalEvent).toString()
        primaryLogFile(context).appendText(line + "\n")
        mirrorLogFile(context).appendText(line + "\n")
        return finalEvent
    }

    fun readEvents(context: Context): List<UsbSecurityEvent> {
        val source = when {
            primaryLogFile(context).exists() -> primaryLogFile(context)
            mirrorLogFile(context).exists() -> mirrorLogFile(context)
            legacyLogFile(context).exists() -> legacyLogFile(context)
            else -> return emptyList()
        }

        return source.readLines()
            .mapNotNull { line ->
                runCatching { fromJson(JSONObject(line)) }.getOrNull()
            }
            .sortedByDescending { it.timestamp }
    }

    fun integrityStatus(context: Context): LogIntegrityStatus {
        val eventsAscending = readEvents(context).sortedBy { it.timestamp }
        if (eventsAscending.isEmpty()) {
            return LogIntegrityStatus(
                state = LogIntegrityState.EMPTY,
                totalEvents = 0,
                lockedEvents = 0,
                deletableEvents = 0,
                detail = "No protected incident log has been created yet."
            )
        }

        var expectedPrevious = GENESIS_HASH
        var tampered = false
        var lastHash = ""

        eventsAscending.forEach { event ->
            val recalculated = sha256(canonicalString(event.copy(hash = "")))
            if (event.previousHash != expectedPrevious || event.hash != recalculated) {
                tampered = true
            }
            expectedPrevious = event.hash
            lastHash = event.hash
        }

        val now = System.currentTimeMillis()
        val locked = eventsAscending.count { it.lockedUntilMillis > now }
        val deletable = eventsAscending.count { it.lockedUntilMillis <= now }

        return LogIntegrityStatus(
            state = if (tampered) LogIntegrityState.TAMPERED else LogIntegrityState.VERIFIED,
            totalEvents = eventsAscending.size,
            lockedEvents = locked,
            deletableEvents = deletable,
            lastVerifiedHash = lastHash.take(16),
            detail = if (tampered) {
                "Hash chain mismatch detected. Logs may have been modified, deleted, or rewritten outside the app."
            } else {
                "Append-only hash chain verified. Events are UI-locked for 1 hour before in-app deletion is allowed."
            }
        )
    }

    fun clearEligibleLogs(context: Context): Int {
        val now = System.currentTimeMillis()
        val all = readEvents(context).sortedBy { it.timestamp }
        val remaining = all.filter { it.lockedUntilMillis > now }
        val removed = all.size - remaining.size

        rewriteLogs(context, remaining)
        return removed
    }

    fun readLogs(context: Context): String {
        val file = primaryLogFile(context)
        return if (file.exists()) file.readText() else ""
    }

    private fun rewriteLogs(context: Context, eventsAscending: List<UsbSecurityEvent>) {
        var previousHash = GENESIS_HASH
        val rebuilt = eventsAscending.map { event ->
            val rebuiltEvent = event.copy(previousHash = previousHash, hash = "")
            val finalEvent = rebuiltEvent.copy(hash = sha256(canonicalString(rebuiltEvent)))
            previousHash = finalEvent.hash
            finalEvent
        }

        val text = rebuilt.joinToString(separator = "\n", postfix = if (rebuilt.isNotEmpty()) "\n" else "") { event ->
            toJson(event).toString()
        }

        primaryLogFile(context).writeText(text)
        mirrorLogFile(context).writeText(text)
    }

    private fun primaryLogFile(context: Context): File {
        return File(context.noBackupFilesDir, PRIMARY_FILE_NAME)
    }

    private fun mirrorLogFile(context: Context): File {
        return File(context.filesDir, MIRROR_FILE_NAME)
    }

    private fun legacyLogFile(context: Context): File {
        return File(context.filesDir, LEGACY_FILE_NAME)
    }

    private fun toJson(event: UsbSecurityEvent): JSONObject {
        return JSONObject()
            .put("id", event.id)
            .put("title", event.title)
            .put("detail", event.detail)
            .put("riskLevel", event.riskLevel.name)
            .put("timestamp", event.timestamp)
            .put("lockedUntilMillis", event.lockedUntilMillis)
            .put("previousHash", event.previousHash)
            .put("hash", event.hash)
    }

    private fun fromJson(json: JSONObject): UsbSecurityEvent {
        val timestamp = json.optLong("timestamp", System.currentTimeMillis())
        return UsbSecurityEvent(
            id = json.optString("id", UUID.randomUUID().toString()),
            title = json.optString("title", "USB Security Event"),
            detail = json.optString("detail", "No detail"),
            riskLevel = runCatching { RiskLevel.valueOf(json.optString("riskLevel", "WARNING")) }.getOrDefault(RiskLevel.WARNING),
            timestamp = timestamp,
            lockedUntilMillis = json.optLong("lockedUntilMillis", timestamp + RETENTION_LOCK_MS),
            previousHash = json.optString("previousHash", ""),
            hash = json.optString("hash", "")
        )
    }

    private fun canonicalString(event: UsbSecurityEvent): String {
        return listOf(
            event.id,
            event.title,
            event.detail,
            event.riskLevel.name,
            event.timestamp.toString(),
            event.lockedUntilMillis.toString(),
            event.previousHash
        ).joinToString(separator = "|")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
