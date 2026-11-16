package com.xingzhi.bootstrap.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogRedactorTest {
    @Test
    fun `redacts bearer tokens without swallowing nearby words`() {
        assertEquals(
            "Bearer [REDACTED]",
            LogRedactor.sanitize("bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"),
        )
        assertEquals(
            "prefix Bearer [REDACTED] suffix",
            LogRedactor.sanitize("prefix Bearer abcDEF123._-~+/ suffix"),
        )
        assertEquals(
            "Bearer [REDACTED] and Bearer [REDACTED]",
            LogRedactor.sanitize("Bearer one and bearer two"),
        )
    }

    @Test
    fun `redacts json credential fields and keeps other values`() {
        assertEquals(
            """{"password":"[REDACTED]","accessToken":"[REDACTED]","encryptedAccessToken":"[REDACTED]","refreshToken":"[REDACTED]","token":"[REDACTED]","authorization":"[REDACTED]","secret":"[REDACTED]","privateKey":"[REDACTED]","name":"ada"}""",
            LogRedactor.sanitize(
                """{"password":"hunter2","accessToken":"aaa","encryptedAccessToken":"bbb","refreshToken":"ccc","token":"ddd","authorization":"eee","secret":"fff","privateKey":"ggg","name":"ada"}""",
            ),
        )
        assertEquals(
            """{"Password":"[REDACTED]","AccessToken":"[REDACTED]"}""",
            LogRedactor.sanitize("""{"Password":"hunter2","AccessToken":"aaa"}"""),
        )
        assertEquals(
            """{"token" : "[REDACTED]", "ok": true}""",
            LogRedactor.sanitize("""{"token" : "abc", "ok": true}"""),
        )
        assertEquals(
            """{"password":"[REDACTED]"}""",
            LogRedactor.sanitize("""{"password":"a\"b"}"""),
        )
    }

    @Test
    fun `redacts yaml passwords and authorization values`() {
        assertEquals("password: [REDACTED]", LogRedactor.sanitize("password: hunter2"))
        assertEquals("Password: [REDACTED]", LogRedactor.sanitize("Password:   \"hunter2\""))
        assertEquals(
            "authorization: [REDACTED]",
            LogRedactor.sanitize("authorization: Bearer secret-value"),
        )
        assertEquals(
            "password: [REDACTED]\nname: ada\n|AD[REDACTED]|\n",
            LogRedactor.sanitize("password: one\nname: ada\n|ADtwo|\n"),
        )
    }

    @Test
    fun `redacts query credential fields`() {
        assertEquals(
            "a=1&accessToken=[REDACTED]&refreshToken=[REDACTED]&token=[REDACTED]&ok=1",
            LogRedactor.sanitize("a=1&accessToken=abc&refreshToken=def&token=ghi&ok=1"),
        )
        assertEquals(
            "https://host/callback?encryptedAccessToken=[REDACTED]&name=ada",
            LogRedactor.sanitize("https://host/callback?encryptedAccessToken=bbb&name=ada"),
        )
        assertEquals(
            "AccessToken=[REDACTED]",
            LogRedactor.sanitize("AccessToken=zzz"),
        )
    }

    @Test
    fun `redacts sip2 patron and login password fields only`() {
        assertEquals("|AD[REDACTED]|", LogRedactor.sanitize("|ADsecret|"))
        assertEquals("|CO[REDACTED]|", LogRedactor.sanitize("|COsecret|"))
        assertEquals(
            "64|AA203|AD[REDACTED]|AO|CO[REDACTED]|AY1",
            LogRedactor.sanitize("64|AA203|ADpatron-secret|AO|COlogin-secret|AY1"),
        )
    }

    @Test
    fun `leaves benign strings intact`() {
        val input = "passwordless login tokenizes BearerToken safely |AO12345| |ADpartial café"
        assertEquals(input, LogRedactor.sanitize(input))
        assertEquals("ok", LogRedactor.sanitize("ok"))
    }

    @Test
    fun `second pass is stable`() {
        val input = listOf(
            "Bearer raw-token",
            """{"password":"hunter2","token":"abc"}""",
            "password: hunter2",
            "accessToken=abc&ok=1",
            "64|AD[secret]|COlogin|",
        ).joinToString("\n")
        val once = LogRedactor.sanitize(input)
        assertEquals(once, LogRedactor.sanitize(once))
        assertFalse(once.contains("hunter2"))
        assertFalse(once.contains("raw-token"))
        assertTrue(once.contains("[REDACTED]"))
    }
}
