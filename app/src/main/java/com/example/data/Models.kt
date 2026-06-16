package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ExecutionStep(
    val agent: String,      // "FILE", "WEB", "SYSTEM", "MEMORY"
    val action: String,     // e.g. "Create folder"
    val parameter: String,  // details
    val command: String     // simulated terminal command
)

@JsonClass(generateAdapter = true)
data class CriticResponse(
    val isOverallSafe: Boolean,
    val reasoning: String,
    val approvedSteps: List<ExecutionStep>
)

data class HostPcStatus(
    val computerName: String = "DESKTOP-MELYNDA-ULTRA",
    val operatingSystem: String = "Windows 11 Enterprise (23H2)",
    val cpuUsage: Int = 12,
    val cpuTemperature: Int = 42,
    val ramUsage: Int = 31, // %
    val diskUsage: Int = 54, // %
    val networkLatency: Int = 14, // ms
    val connectionStatus: String = "CONNECTED" // "CONNECTED", "SECURE_SANDBOX", "DISCONNECTED"
)

data class WebBrowserState(
    val url: String = "https://melynda.os/welcome",
    val title: String = "Melynda OS Operator Gateway",
    val htmlContent: String = "<h1>System Ready</h1><p>Melynda Ultra OS Autonomous Web Agent is idle. Waiting for coordinate inputs...</p>",
    val isLoading: Boolean = false
)
