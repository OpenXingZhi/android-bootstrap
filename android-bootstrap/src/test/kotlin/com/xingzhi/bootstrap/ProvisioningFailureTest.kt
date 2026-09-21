package com.xingzhi.bootstrap

import com.xingzhi.license.LicenseException
import com.xingzhi.remoteconfig.RemoteConfigException
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProvisioningFailureTest {
    private val cause = IllegalStateException("technical detail")

    @Test
    fun `maps every library exception to one application failure model`() {
        val expiresOn = LocalDate(2026, 8, 12)
        val mappings = listOf(
            LicenseException.Expired(expiresOn) to ProvisioningFailure.Expired(expiresOn),
            LicenseException.TimeNotTrusted to ProvisioningFailure.TimeNotTrusted,
            LicenseException.MissingLicense to ProvisioningFailure.MissingLicense,
            LicenseException.SignatureVerificationFailed to
                ProvisioningFailure.LicenseSignatureInvalid,
            LicenseException.SerialNumberMismatch to ProvisioningFailure.SerialNumberMismatch,
            RemoteConfigException.FetchFailed(cause) to ProvisioningFailure.ConfigFetchFailed,
            RemoteConfigException.NotFound(cause) to ProvisioningFailure.ConfigNotFound,
            RemoteConfigException.SignatureNotFound(cause) to
                ProvisioningFailure.ConfigSignatureNotFound,
            RemoteConfigException.DecodeFailed(cause) to ProvisioningFailure.ConfigDecodeFailed,
            RemoteConfigException.ValidationFailed(cause) to
                ProvisioningFailure.ConfigValidationFailed,
            RemoteConfigException.StoreFailed(cause) to ProvisioningFailure.ConfigStoreFailed,
            RemoteConfigException.RollbackRejected(cause) to
                ProvisioningFailure.ConfigRollbackRejected,
        )

        mappings.forEach { (error, expected) ->
            assertEquals(expected, error.toProvisioningFailure())
        }
    }

    @Test
    fun `wrapped expiry keeps its date and takes priority over validation wrapper`() {
        val expiresOn = LocalDate(2026, 8, 12)
        val error = RemoteConfigException.ValidationFailed(
            IllegalStateException("policy", LicenseException.Expired(expiresOn)),
        )

        assertEquals(
            ProvisioningFailure.Expired(expiresOn),
            error.toProvisioningFailure(),
        )
    }

    @Test
    fun `unrecognized throwable maps to unexpected`() {
        assertEquals(
            ProvisioningFailure.Unexpected,
            IllegalStateException("unrelated").toProvisioningFailure(),
        )
    }
}
