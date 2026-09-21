package com.xingzhi.bootstrap

import com.xingzhi.trustedtime.DeviceTime
import java.time.Duration

/**
 * [DeviceTime] from a boot identifier and a monotonic millisecond clock.
 *
 * Android applications pass
 * `"${applicationInfo.uid}:${Settings.Global.BOOT_COUNT}"` and
 * [android.os.SystemClock.elapsedRealtime]. Storage adapters stay in the app.
 */
class MonotonicDeviceTime(
    private val bootId: String,
    private val monotonicMillis: () -> Long,
) : DeviceTime {
    constructor(uid: Int, bootCount: Int, monotonicMillis: () -> Long) : this(
        bootId = "$uid:$bootCount",
        monotonicMillis = monotonicMillis,
    )

    override fun monotonic(): Duration = Duration.ofMillis(monotonicMillis())

    override fun bootId(): String = bootId
}
