package com.xingzhi.bootstrap.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TelemetryConfigTest {
    @Test
    fun `is active only when enabled with a non-blank dsn`() {
        assertFalse(TelemetryConfig().isActive)
        assertFalse(TelemetryConfig(dsn = "  ").isActive)
        assertFalse(TelemetryConfig(dsn = "", enabled = true).isActive)
        assertFalse(TelemetryConfig(dsn = "https://example.invalid/1", enabled = false).isActive)
        assertTrue(TelemetryConfig(dsn = "https://example.invalid/1").isActive)
        assertTrue(
            TelemetryConfig(
                dsn = "https://example.invalid/1",
                environment = "debug",
            ).isActive,
        )
        assertEquals("debug", TelemetryConfig(environment = "debug").environment)
    }
}
