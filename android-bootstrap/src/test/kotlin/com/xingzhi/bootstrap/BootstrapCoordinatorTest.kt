package com.xingzhi.bootstrap

import com.xingzhi.license.License
import com.xingzhi.license.LicenseException
import com.xingzhi.remoteconfig.ConfigDecoder
import com.xingzhi.remoteconfig.ConfigSnapshot
import com.xingzhi.remoteconfig.ConfigSource
import com.xingzhi.remoteconfig.ConfigStore
import com.xingzhi.remoteconfig.ConfigValidator
import com.xingzhi.remoteconfig.MonotonicLongRevisionPolicy
import com.xingzhi.remoteconfig.RemoteConfigClient
import com.xingzhi.remoteconfig.RemoteConfigException
import com.xingzhi.trustedtime.TrustedTime
import com.xingzhi.trustedtime.TrustedTimeProvider
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class BootstrapCoordinatorTest {
    private val applied = mutableListOf<SampleConfig>()

    @Test
    fun `uses refreshed config when valid`(): Unit = runBlocking {
        val store = MemoryConfigStore()
        val source = FakeConfigSource(signed("remote"))
        val result = coordinator(source, store).bootstrap("device")

        assertTrue(result.refreshed)
        assertEquals("device", result.config.license?.sn)
        assertEquals(listOf(result.config), applied)
    }

    @Test
    fun `retains validated local config when refresh fails`(): Unit = runBlocking {
        val local = signed("local")
        val store = MemoryConfigStore(local)
        val source = FakeConfigSource(error = IllegalStateException("offline"))

        val result = coordinator(source, store).bootstrap("device")

        assertFalse(result.refreshed)
        assertEquals(local, store.snapshot)
        assertEquals(1, applied.size)
    }

    @Test
    fun `refreshNow does not fall back when fetch fails`(): Unit = runBlocking {
        val local = signed("local")
        val store = MemoryConfigStore(local)
        val source = FakeConfigSource(error = IllegalStateException("offline"))

        try {
            coordinator(source, store).refreshNow("device")
            fail("refreshNow should fail closed")
        } catch (error: Throwable) {
            assertTrue(error is RemoteConfigException)
        }
        assertEquals(local, store.snapshot)
        assertTrue(applied.isEmpty())
    }

    @Test
    fun `expired refreshed license is stored and not applied`(): Unit = runBlocking {
        val local = ConfigSnapshot("local-permanent".toByteArray(), "sig".toByteArray())
        val remote = ConfigSnapshot("remote-expired".toByteArray(), "sig".toByteArray())
        val store = MemoryConfigStore(local)
        val decoder = ConfigDecoder<SampleConfig> { bytes ->
            when (bytes.decodeToString()) {
                "local-permanent" -> SampleConfig(license = License.Permanent("device"))
                "remote-expired" -> SampleConfig(
                    revision = 2L,
                    license = License.Temporary("device", LocalDate.parse("2020-01-01")),
                )
                else -> error(bytes.decodeToString())
            }
        }
        val clock = FakeTrustedTime().also { it.succeedAt(1_777_766_400_000L) }

        try {
            coordinator(FakeConfigSource(remote), store, clock, decoder).bootstrap("device")
            fail("expired refreshed license must fail closed")
        } catch (error: LicenseException.Expired) {
            assertEquals(LocalDate.parse("2020-01-01"), error.expiresOn)
        }
        assertEquals(remote, store.snapshot)
        assertTrue(applied.isEmpty())
    }

    @Test
    fun `fails closed when unprovisioned`(): Unit = runBlocking {
        try {
            coordinator(
                FakeConfigSource(error = RemoteConfigException.NotFound()),
                MemoryConfigStore(),
            ).bootstrap("device")
            fail("unprovisioned device must fail closed")
        } catch (_: RemoteConfigException.NotFound) {
        }
        assertTrue(applied.isEmpty())
    }

    @Test
    fun `permanent license skips trusted time`(): Unit = runBlocking {
        val clock = FakeTrustedTime()
        coordinator(
            source = FakeConfigSource(signed("permanent")),
            store = MemoryConfigStore(),
            clock = clock,
            decoder = ConfigDecoder { SampleConfig(license = License.Permanent("device")) },
        ).bootstrap("device")

        assertEquals(0, clock.calls)
        assertEquals(1, applied.size)
    }

    private fun coordinator(
        source: FakeConfigSource,
        store: MemoryConfigStore,
        clock: FakeTrustedTime = FakeTrustedTime().also { it.succeedAt(1_777_766_400_000L) },
        decoder: ConfigDecoder<SampleConfig> = ConfigDecoder {
            SampleConfig(license = License.Permanent("device"))
        },
        validator: ConfigValidator<SampleConfig> = ConfigValidator { _, _ -> },
    ): BootstrapCoordinator<SampleConfig> {
        val client = RemoteConfigClient(
            source = source,
            store = store,
            decoder = decoder,
            validator = validator,
            revisionPolicy = MonotonicLongRevisionPolicy(SampleConfig::revision),
        )
        return BootstrapCoordinator(
            client = client,
            licensePolicy = BoundLicensePolicy(
                trustedTime = clock,
                serialNumber = { "device" },
                licenseOf = { it.license },
            ),
            applyConfig = { config -> applied += config },
            revisionOf = SampleConfig::revision,
        )
    }

    private fun signed(label: String): ConfigSnapshot =
        ConfigSnapshot(label.toByteArray(), "sig".toByteArray())

    private class MemoryConfigStore(
        var snapshot: ConfigSnapshot? = null,
    ) : ConfigStore {
        override suspend fun load(): ConfigSnapshot? = snapshot
        override suspend fun save(snapshot: ConfigSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeConfigSource(
        var snapshot: ConfigSnapshot? = null,
        var error: Throwable? = null,
    ) : ConfigSource {
        override suspend fun fetch(key: String): ConfigSnapshot {
            error?.let { throw it }
            return snapshot ?: error("No remote configuration for $key")
        }
    }

    private class FakeTrustedTime(
        var time: Result<TrustedTime> = Result.failure(IllegalStateException("offline")),
    ) : TrustedTimeProvider {
        var calls = 0
        override suspend fun now(): Result<TrustedTime> {
            calls += 1
            return time
        }
        override suspend fun refresh(): Result<TrustedTime> = now()
        fun succeedAt(epochMilli: Long) {
            time = Result.success(TrustedTime(Instant.ofEpochMilli(epochMilli)))
        }
    }
}

internal data class SampleConfig(
    val revision: Long = 1L,
    val license: License? = null,
)
