package com.xingzhi.bootstrap

import com.xingzhi.license.License
import com.xingzhi.license.LicenseException
import com.xingzhi.license.LicenseVerifier
import com.xingzhi.remoteconfig.ConfigSnapshot
import com.xingzhi.remoteconfig.ConfigValidator
import com.xingzhi.trustedtime.TrustedTime
import com.xingzhi.trustedtime.TrustedTimeProvider
import java.time.ZoneOffset
import kotlinx.datetime.LocalDate

/**
 * License eligibility run after detached signature verification and serial binding.
 *
 * Permanent licenses never consult trusted time. Temporary licenses fail closed with
 * [LicenseException.TimeNotTrusted] when trusted time is unavailable, then apply
 * [LicenseVerifier.verifyBoundLicense].
 */
class BoundLicensePolicy<T>(
    private val trustedTime: TrustedTimeProvider,
    private val serialNumber: suspend () -> String,
    private val licenseOf: (T) -> License?,
    private val licenseVerifier: LicenseVerifier = LicenseVerifier(),
) : ConfigValidator<T> {
    override suspend fun validate(snapshot: ConfigSnapshot, decoded: T) {
        val license = licenseOf(decoded) ?: throw LicenseException.MissingLicense
        licenseVerifier.verifyBoundLicense(
            license = license,
            actualSerialNumber = serialNumber(),
            trustedUtcDate = {
                trustedTime.now().getOrElse { throw LicenseException.TimeNotTrusted }.toUtcDate()
            },
        ).getOrThrow()
    }

    private fun TrustedTime.toUtcDate(): LocalDate {
        val javaDate = instant.atZone(ZoneOffset.UTC).toLocalDate()
        return LocalDate.parse(javaDate.toString())
    }
}
