package com.xingzhi.bootstrap

import com.xingzhi.license.License
import com.xingzhi.license.LicenseException
import com.xingzhi.remoteconfig.ConfigSnapshot
import com.xingzhi.trustedtime.TrustedTime
import com.xingzhi.trustedtime.TrustedTimeProvider
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class BoundLicensePolicyTest {
    private val snapshot = ConfigSnapshot("unused".toByteArray(), "sig".toByteArray())

    @Test
    fun `permanent license does not consult trusted time`(): Unit = runBlocking {
        val trustedTime = FakeTrustedTime(fail = true)
        val policy = BoundLicensePolicy<SampleConfig>(
            trustedTime = trustedTime,
            serialNumber = { "device" },
            licenseOf = { it.license },
        )

        policy.validate(snapshot, SampleConfig(license = License.Permanent("device")))

        assertEquals(0, trustedTime.calls)
    }

    @Test
    fun `temporary license fails closed when time is untrusted`(): Unit = runBlocking {
        val policy = BoundLicensePolicy<SampleConfig>(
            trustedTime = FakeTrustedTime(fail = true),
            serialNumber = { "device" },
            licenseOf = { it.license },
        )

        try {
            policy.validate(
                snapshot,
                SampleConfig(license = License.Temporary("device", LocalDate(2026, 8, 13))),
            )
            fail("expected TimeNotTrusted")
        } catch (_: LicenseException.TimeNotTrusted) {
        }
    }

    @Test
    fun `temporary license fails after the inclusive expiry date`(): Unit = runBlocking {
        val policy = BoundLicensePolicy<SampleConfig>(
            trustedTime = FakeTrustedTime(Instant.parse("2026-08-14T00:00:00Z")),
            serialNumber = { "device" },
            licenseOf = { it.license },
        )

        try {
            policy.validate(
                snapshot,
                SampleConfig(license = License.Temporary("device", LocalDate(2026, 8, 13))),
            )
            fail("expected Expired")
        } catch (error: LicenseException.Expired) {
            assertEquals(LocalDate(2026, 8, 13), error.expiresOn)
        }
    }

    @Test
    fun `missing license fails closed`(): Unit = runBlocking {
        val policy = BoundLicensePolicy<SampleConfig>(
            trustedTime = FakeTrustedTime(),
            serialNumber = { "device" },
            licenseOf = { it.license },
        )

        try {
            policy.validate(snapshot, SampleConfig(license = null))
            fail("expected MissingLicense")
        } catch (_: LicenseException.MissingLicense) {
        }
    }

    private class FakeTrustedTime(
        private val instant: Instant = Instant.EPOCH,
        private val fail: Boolean = false,
    ) : TrustedTimeProvider {
        var calls = 0
        override suspend fun now(): Result<TrustedTime> {
            calls++
            return if (fail) Result.failure(IllegalStateException("offline"))
            else Result.success(TrustedTime(instant))
        }

        override suspend fun refresh(): Result<TrustedTime> = now()
    }
}
