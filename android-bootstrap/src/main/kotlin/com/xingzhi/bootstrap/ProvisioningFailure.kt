package com.xingzhi.bootstrap

import com.xingzhi.license.LicenseException
import com.xingzhi.license.findLicenseException
import com.xingzhi.remoteconfig.RemoteConfigException
import com.xingzhi.remoteconfig.findRemoteConfigException
import kotlinx.datetime.LocalDate

/** Application-level provisioning failures consumed by UI state and localized at render time. */
sealed interface ProvisioningFailure {
    data class Expired(val expiresOn: LocalDate) : ProvisioningFailure
    data object TimeNotTrusted : ProvisioningFailure
    data object MissingLicense : ProvisioningFailure
    data object LicenseSignatureInvalid : ProvisioningFailure
    data object SerialNumberMismatch : ProvisioningFailure
    data object ConfigFetchFailed : ProvisioningFailure
    data object ConfigNotFound : ProvisioningFailure
    data object ConfigSignatureNotFound : ProvisioningFailure
    data object ConfigDecodeFailed : ProvisioningFailure
    data object ConfigValidationFailed : ProvisioningFailure
    data object ConfigStoreFailed : ProvisioningFailure
    data object ConfigRollbackRejected : ProvisioningFailure
    data object Unexpected : ProvisioningFailure
}

fun Throwable.toProvisioningFailure(): ProvisioningFailure =
    when (val licenseError = findLicenseException()) {
        is LicenseException.Expired -> ProvisioningFailure.Expired(licenseError.expiresOn)
        LicenseException.TimeNotTrusted -> ProvisioningFailure.TimeNotTrusted
        LicenseException.MissingLicense -> ProvisioningFailure.MissingLicense
        LicenseException.SignatureVerificationFailed -> ProvisioningFailure.LicenseSignatureInvalid
        LicenseException.SerialNumberMismatch -> ProvisioningFailure.SerialNumberMismatch
        null -> when (findRemoteConfigException()) {
            is RemoteConfigException.FetchFailed -> ProvisioningFailure.ConfigFetchFailed
            is RemoteConfigException.NotFound -> ProvisioningFailure.ConfigNotFound
            is RemoteConfigException.SignatureNotFound ->
                ProvisioningFailure.ConfigSignatureNotFound
            is RemoteConfigException.DecodeFailed -> ProvisioningFailure.ConfigDecodeFailed
            is RemoteConfigException.ValidationFailed -> ProvisioningFailure.ConfigValidationFailed
            is RemoteConfigException.StoreFailed -> ProvisioningFailure.ConfigStoreFailed
            is RemoteConfigException.RollbackRejected -> ProvisioningFailure.ConfigRollbackRejected
            null -> ProvisioningFailure.Unexpected
        }
    }
