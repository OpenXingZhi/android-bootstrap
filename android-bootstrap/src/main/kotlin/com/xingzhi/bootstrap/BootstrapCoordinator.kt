package com.xingzhi.bootstrap

import com.xingzhi.license.findLicenseException
import com.xingzhi.remoteconfig.ConfigValidator
import com.xingzhi.remoteconfig.RemoteConfigClient
import com.xingzhi.remoteconfig.RemoteConfigException
import com.xingzhi.remoteconfig.StoredConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BootstrapResult<T>(
    @JvmField val config: T,
    @JvmField val refreshed: Boolean,
)

/**
 * Loads last-known-good config and attempts a signed refresh.
 *
 * Cryptographic, schema, and revision validity decide whether a refresh is authoritative.
 * [licensePolicy] then gates startup and preference application. A trusted signed
 * revocation or expiry is never replaced by an older local license.
 */
class BootstrapCoordinator<T>(
    private val client: RemoteConfigClient<T>,
    private val licensePolicy: ConfigValidator<T>,
    private val applyConfig: suspend (T) -> Unit,
    private val revisionOf: (T) -> Long,
) {
    suspend fun bootstrap(serialNumber: String): BootstrapResult<T> = withContext(Dispatchers.IO) {
        val local = client.load()
        val localConfig = local.getOrNull()
        val refreshed = client.refresh(serialNumber)
        val chosen: StoredConfig<T> = when {
            refreshed.isSuccess -> refreshed.getOrThrow()
            localConfig != null -> localConfig
            else -> throw unwrap(refreshed.exceptionOrNull() ?: local.exceptionOrNull())
        }

        licensePolicy.validate(chosen.snapshot, chosen.value)
        applyConfig(chosen.value)
        BootstrapResult(
            config = chosen.value,
            refreshed = refreshed.isSuccess,
        )
    }

    /**
     * Fetches and applies a signed snapshot without falling back to local on failure.
     * [BootstrapResult.refreshed] is true when the committed revision changed.
     */
    suspend fun refreshNow(serialNumber: String): BootstrapResult<T> = withContext(Dispatchers.IO) {
        val localRevision = client.load().getOrNull()?.value?.let(revisionOf)
        val stored = client.refresh(serialNumber).getOrThrow()
        licensePolicy.validate(stored.snapshot, stored.value)
        applyConfig(stored.value)
        BootstrapResult(
            config = stored.value,
            refreshed = localRevision != revisionOf(stored.value),
        )
    }

    private fun unwrap(error: Throwable?): Throwable =
        error?.findLicenseException()
            ?: error
            ?: RemoteConfigException.NotFound()
}
