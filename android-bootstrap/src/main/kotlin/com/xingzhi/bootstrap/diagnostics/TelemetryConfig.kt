package com.xingzhi.bootstrap.diagnostics

/**
 * Optional crash/telemetry destination.
 *
 * [isActive] is the gate callers should use: disabled configs and blank DSNs stay off
 * so a missing secret does not turn telemetry on.
 */
data class TelemetryConfig(
    val dsn: String? = null,
    val enabled: Boolean = true,
    val environment: String? = null,
) {
    val isActive: Boolean
        get() = enabled && !dsn.isNullOrBlank()
}
