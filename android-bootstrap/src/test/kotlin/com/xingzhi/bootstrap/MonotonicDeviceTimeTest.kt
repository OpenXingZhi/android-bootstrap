package com.xingzhi.bootstrap

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MonotonicDeviceTimeTest {
    @Test
    fun `joins uid and boot count into the Android boot id`() {
        val deviceTime = MonotonicDeviceTime(uid = 10123, bootCount = 7) { 1_500L }

        assertEquals("10123:7", deviceTime.bootId())
        assertEquals(Duration.ofMillis(1_500L), deviceTime.monotonic())
    }
}
