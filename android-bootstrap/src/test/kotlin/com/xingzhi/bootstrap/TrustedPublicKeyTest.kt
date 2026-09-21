package com.xingzhi.bootstrap

import com.xingzhi.license.PinnedPublicKeyResolver
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrustedPublicKeyTest {
    @Test
    fun `pins the Gitee identity document`() {
        assertEquals(URI("https://gitee.com/chuxubank.gpg"), TrustedPublicKey.uri)
        PinnedPublicKeyResolver(TrustedPublicKey.bytes) { error("fetch must not run") }
    }
}
