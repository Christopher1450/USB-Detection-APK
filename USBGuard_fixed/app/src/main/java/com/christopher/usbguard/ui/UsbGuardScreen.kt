package com.christopher.usbguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.christopher.usbguard.model.LogIntegrityState
import com.christopher.usbguard.model.LogIntegrityStatus
import com.christopher.usbguard.model.PowerSample
import com.christopher.usbguard.model.RiskLevel
import com.christopher.usbguard.model.UsbMode
import com.christopher.usbguard.model.UsbSnapshot
import com.christopher.usbguard.repository.UsbGuardRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UsbGuardScreen(
    onStartGuard: () -> Unit,
    onStopGuard: () -> Unit,
    onClearEligibleLogs: () -> Unit
) {
    val snapshot by UsbGuardRepository.snapshot.collectAsState()
    val events by UsbGuardRepository.events.collectAsState()
    val powerSamples by UsbGuardRepository.powerSamples.collectAsState()
    val logIntegrity by UsbGuardRepository.logIntegrity.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HeaderSection()
                HeroStatusCard(snapshot)
                ControlButtons(snapshot.isMonitoring, onStartGuard, onStopGuard)
                QuickTelemetryCard(snapshot)

                ExpandableSection(
                    title = "Power & USB Details",
                    subtitle = "Charging source, USB data mode, voltage, current, and watt estimate",
                    defaultExpanded = true
                ) {
                    CurrentDetails(snapshot)
                }

                ExpandableSection(
                    title = "Power Graphs",
                    subtitle = "Power In, Device Draw, and Battery Net Power",
                    defaultExpanded = false
                ) {
                    PowerGraphContent(powerSamples)
                }

                ExpandableSection(
                    title = "Connected Device Identity",
                    subtitle = "Host/accessory/input identity when Android exposes it",
                    defaultExpanded = false
                ) {
                    DeviceIdentityContent(snapshot)
                }

                ExpandableSection(
                    title = "Protected Logs",
                    subtitle = "1-hour retention lock and hash-chain integrity state",
                    defaultExpanded = false
                ) {
                    EnterpriseLogContent(logIntegrity, onClearEligibleLogs)
                }

                ExpandableSection(
                    title = "Recent Security Events",
                    subtitle = "Current session only; protected log count is shown separately",
                    defaultExpanded = false
                ) {
                    EventListContent(events, logIntegrity)
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "USB Guard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Real-time USB risk monitoring for Android",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HeroStatusCard(snapshot: UsbSnapshot) {
    val status = statusInfo(snapshot)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        colors = CardDefaults.cardColors(containerColor = status.containerColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = status.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = status.contentColor
                    )
                    Text(
                        text = status.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = status.contentColor
                    )
                }
                RiskBadge(snapshot.riskLevel)
            }

            LinearProgressIndicator(
                progress = { snapshot.riskScore / 100f },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = status.userHint,
                style = MaterialTheme.typography.bodySmall,
                color = status.contentColor
            )
        }
    }
}

@Composable
private fun QuickTelemetryCard(snapshot: UsbSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickMetric("Risk", "${snapshot.riskScore}/100")
                QuickMetric("Power", if (snapshot.powerConnected) "Online" else "Offline")
                QuickMetric("USB Data", if (snapshot.usbDataConnected || snapshot.configured || snapshot.mode.isDataMode()) "Online" else "Offline")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickMetric("Mode", snapshot.mode.name)
                QuickMetric("Source", snapshot.chargingPlugged)
                QuickMetric("Battery", "${"%.0f".format(snapshot.batteryPercent)}%")
            }
        }
    }
}

@Composable
private fun QuickMetric(label: String, value: String) {
    Column(
        modifier = Modifier.padding(end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ControlButtons(
    isMonitoring: Boolean,
    onStartGuard: () -> Unit,
    onStopGuard: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            modifier = Modifier.weight(1f),
            enabled = !isMonitoring,
            onClick = onStartGuard
        ) {
            Text(if (isMonitoring) "Guard Running" else "Start Guard")
        }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            enabled = isMonitoring,
            onClick = onStopGuard
        ) {
            Text("Stop")
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    subtitle: String,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
private fun CurrentDetails(snapshot: UsbSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailRow("Monitoring", if (snapshot.isMonitoring) "Active (${snapshot.monitorTick}s)" else "Stopped")
        DetailRow("Connection", statusInfo(snapshot).title)
        DetailRow("Power connected", snapshot.powerConnected.toYesNo())
        DetailRow("USB data connected", snapshot.usbDataConnected.toYesNo())
        DetailRow("Configured", snapshot.configured.toYesNo())
        DetailRow("USB mode", snapshot.mode.name)
        DetailRow("Power source", snapshot.chargingPlugged)
        DetailRow("Battery voltage", "${"%.2f".format(snapshot.voltageVolt)} V")
        DetailRow("Battery net current", "${"%.3f".format(snapshot.batteryNetCurrentAmpere)} A")
        DetailRow("Battery net power", "${"%.2f".format(snapshot.batteryNetPowerWatt)} W")
        DetailRow("Power going in", "${"%.2f".format(snapshot.estimatedPowerGoingInWatt)} W")
        DetailRow("Device draw", if (snapshot.powerConnected) "N/A while charging" else "${"%.2f".format(snapshot.estimatedDeviceDrawWatt)} W")
        DetailRow("USB descriptors", "${snapshot.usbDeviceCount}")
        DetailRow("External inputs", "${snapshot.externalInputCount}")
        DetailRow("Host identity", snapshot.hostIdentityLabel)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.9f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1.1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PowerGraphContent(samples: List<PowerSample>) {
    if (samples.size < 2) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Press Start Guard to collect power samples.")
        }
        return
    }

    val latest = samples.last()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricPill("Power In", "${"%.2f".format(latest.estimatedPowerGoingInWatt)} W")
        MetricPill("Device Draw", if (latest.estimatedDeviceDrawWatt > 0f) "${"%.2f".format(latest.estimatedDeviceDrawWatt)} W" else "N/A")
        MetricPill("Battery Net", "${"%.2f".format(latest.batteryNetPowerWatt)} W")
        MetricPill("Voltage", "${"%.2f".format(latest.voltageVolt)} V")
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text("Power Going In", fontWeight = FontWeight.Bold)
    SimpleLineChart(samples.map { it.estimatedPowerGoingInWatt }, "Power In W")

    Spacer(modifier = Modifier.height(12.dp))
    Text("Battery Net Power", fontWeight = FontWeight.Bold)
    SimpleLineChart(samples.map { it.batteryNetPowerWatt }, "Battery Net W")
}

@Composable
private fun DeviceIdentityContent(snapshot: UsbSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = snapshot.hostIdentityLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (snapshot.usbAttachedDevices.isNotEmpty()) {
            Text("OTG USB Devices", fontWeight = FontWeight.Bold)
            snapshot.usbAttachedDevices.forEach { device ->
                IdentityBlock(
                    title = "${device.manufacturer ?: "Unknown"} - ${device.product ?: "Unknown device"}",
                    body = "Name: ${device.deviceName}\nVID: ${device.vendorId}, PID: ${device.productId}\nClass: ${device.deviceClass}, Subclass: ${device.deviceSubclass}, Protocol: ${device.deviceProtocol}"
                )
            }
        }

        if (snapshot.usbAccessories.isNotEmpty()) {
            Text("Android Accessories", fontWeight = FontWeight.Bold)
            snapshot.usbAccessories.forEach { accessory ->
                IdentityBlock(
                    title = "${accessory.manufacturer ?: "Unknown"} - ${accessory.model ?: "Unknown"}",
                    body = "Description: ${accessory.description ?: "-"}\nVersion: ${accessory.version ?: "-"}\nSerial: ${accessory.serial ?: "-"}"
                )
            }
        }

        if (snapshot.externalInputs.isNotEmpty()) {
            Text("External Input Devices", fontWeight = FontWeight.Bold)
            snapshot.externalInputs.forEach { input ->
                IdentityBlock(
                    title = input.name,
                    body = "VID: ${input.vendorId}, PID: ${input.productId}, Sources: ${input.sources}"
                )
            }
        }

        if (snapshot.usbAttachedDevices.isEmpty() && snapshot.usbAccessories.isEmpty() && snapshot.externalInputs.isEmpty()) {
            Text(
                text = "No readable USB identity is exposed in this role. Host names such as laptop or Raspberry Pi are not available to normal unrooted APKs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EnterpriseLogContent(
    logIntegrity: LogIntegrityStatus,
    onClearEligibleLogs: () -> Unit
) {
    val label = when (logIntegrity.state) {
        LogIntegrityState.EMPTY -> "No log yet"
        LogIntegrityState.VERIFIED -> "Hash chain verified"
        LogIntegrityState.TAMPERED -> "Tamper warning"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricPill("Integrity", label)
            MetricPill("Stored", "${logIntegrity.totalEvents}")
            MetricPill("Locked", "${logIntegrity.lockedEvents}")
            MetricPill("Clearable", "${logIntegrity.deletableEvents}")
        }

        Text(
            text = logIntegrity.detail,
            style = MaterialTheme.typography.bodySmall,
            color = if (logIntegrity.state == LogIntegrityState.TAMPERED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Security events are protected from in-app deletion for 1 hour. Local logs are tamper-evident, not malware-proof against root or privileged compromise.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedButton(
            enabled = logIntegrity.deletableEvents > 0,
            onClick = onClearEligibleLogs
        ) {
            Text("Clear eligible logs")
        }
    }
}

@Composable
private fun EventListContent(
    events: List<com.christopher.usbguard.model.UsbSecurityEvent>,
    logIntegrity: LogIntegrityStatus
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "${events.size} current-session event(s) • ${logIntegrity.totalEvents} protected log(s)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No suspicious event yet")
            }
        } else {
            events.take(5).forEach { event ->
                val remainingMs = (event.lockedUntilMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                IdentityBlock(
                    title = "${event.title} • ${formatTime(event.timestamp)}",
                    body = buildString {
                        append(event.detail)
                        append("\nRetention: ")
                        append(if (remainingMs > 0L) "locked for ${formatDuration(remainingMs)}" else "eligible for in-app deletion")
                    }
                )
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RiskBadge(level: RiskLevel) {
    val label = when (level) {
        RiskLevel.SAFE -> "SAFE"
        RiskLevel.LOW -> "LOW"
        RiskLevel.WARNING -> "WARN"
        RiskLevel.DANGER -> "DANGER"
    }

    Card(
        shape = RoundedCornerShape(100.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IdentityBlock(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SimpleLineChart(samples: List<Float>, label: String) {
    val cleanSamples = samples.map { if (it.isFinite()) it else 0f }
    val maxValue = (cleanSamples.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val minValue = (cleanSamples.minOrNull() ?: 0f).coerceAtMost(0f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Max ${"%.2f".format(maxValue)}")
            Text("Min ${"%.2f".format(minValue)}")
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val width = size.width
            val height = size.height
            val range = (maxValue - minValue).coerceAtLeast(1f)
            val stepX = if (cleanSamples.size > 1) width / (cleanSamples.size - 1) else width
            val path = Path()

            cleanSamples.forEachIndexed { index, value ->
                val x = index * stepX
                val normalized = (value - minValue) / range
                val y = height - (normalized * height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawLine(axisColor, Offset(0f, height), Offset(width, height), strokeWidth = 2f)
            drawLine(axisColor, Offset(0f, 0f), Offset(0f, height), strokeWidth = 2f)
            drawPath(path, primaryColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
    }
}

private data class StatusInfo(
    val title: String,
    val subtitle: String,
    val userHint: String,
    val containerColor: Color,
    val contentColor: Color
)

@Composable
private fun statusInfo(snapshot: UsbSnapshot): StatusInfo {
    return when {
        !snapshot.isMonitoring -> StatusInfo(
            title = "Guard Stopped",
            subtitle = "Monitoring is off.",
            userHint = "Start Guard to check for tampered charging dock",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        snapshot.riskLevel == RiskLevel.DANGER -> StatusInfo(
            title = "High Risk USB Activity",
            subtitle = "Data or HID behavior looks unsafe.",
            userHint = "Unplug the cable and do not approve file transfer or debugging prompts.",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
        snapshot.riskLevel == RiskLevel.WARNING -> StatusInfo(
            title = "Suspicious USB Activity",
            subtitle = "USB data behavior needs attention.",
            userHint = "Only continue if the USB source is trusted.",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
        snapshot.usbDataConnected || snapshot.configured || snapshot.mode.isDataMode() -> StatusInfo(
            title = "USB Data Online",
            subtitle = "A data-capable USB connection is active.",
            userHint = "Normal for trusted laptop/ADB. Risky for public or unknown USB sources.",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        snapshot.powerConnected -> StatusInfo(
            title = "Charging Only",
            subtitle = "Power is active, USB data is offline.",
            userHint = "Expected state for a normal wall charger.",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        else -> StatusInfo(
            title = "Offline / No Cable",
            subtitle = "No active charging or USB connection.",
            userHint = "Connect a charger or USB source to begin monitoring.",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun UsbMode.isDataMode(): Boolean {
    return this == UsbMode.MTP ||
        this == UsbMode.PTP ||
        this == UsbMode.MIDI ||
        this == UsbMode.RNDIS ||
        this == UsbMode.ACCESSORY ||
        this == UsbMode.ADB ||
        this == UsbMode.UNKNOWN_DATA
}

private fun Boolean.toYesNo(): String = if (this) "Yes" else "No"

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
        .format(Date(timestamp))
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "${minutes}m ${seconds}s"
}
