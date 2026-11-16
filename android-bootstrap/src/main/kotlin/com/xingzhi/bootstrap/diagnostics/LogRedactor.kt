package com.xingzhi.bootstrap.diagnostics

/**
 * Redacts credentials from log lines.
 *
 * Bearer tokens become `Bearer [REDACTED]`. JSON, YAML, and query values for
 * [sensitiveFields] are replaced with `[REDACTED]`. SIP2 patron password (`AD`) and
 * terminal login password (`CO`) fields, delimited by `|`, are replaced in full.
 * Redaction is idempotent: a second pass does not change the result.
 */
object LogRedactor {
    private val sensitiveFields = listOf(
        "encryptedAccessToken",
        "authorization",
        "refreshToken",
        "accessToken",
        "privateKey",
        "password",
        "secret",
        "token",
    )

    private val fieldPattern = sensitiveFields
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

    private val bearer = Regex("""(?i)\bBearer\s+\S+""")

    private val jsonStringField = Regex(
        """(?i)("(?:$fieldPattern)"\s*:\s*")(?:\\.|[^"\\])*(")""",
    )

    private val jsonBareField = Regex(
        """(?i)("(?:$fieldPattern)"\s*:\s*)([^"\s,}\]]+)""",
    )

    private val assignmentField = Regex(
        """(?i)(^|[?&\s{,])($fieldPattern)(=)([^&\s#]+)""",
    )

    private val yamlField = Regex(
        """(?i)(^|[{\s,])($fieldPattern)(\s*:\s*)([^\n\r]*)""",
    )

    /** SIP2 `AD` / `CO` fields, including the surrounding pipe delimiters. */
    private val sipPasswordField = Regex("""\|(AD|CO)[^|]*\|""")

    fun sanitize(input: String): String {
        val withoutBearer = bearer.replace(input, "Bearer [REDACTED]")
        val withoutJsonStrings = jsonStringField.replace(withoutBearer) { match ->
            match.groupValues[1] + "[REDACTED]" + match.groupValues[2]
        }
        val withoutJsonBare = jsonBareField.replace(withoutJsonStrings) { match ->
            match.groupValues[1] + "[REDACTED]"
        }
        val withoutAssignments = assignmentField.replace(withoutJsonBare) { match ->
            match.groupValues[1] + match.groupValues[2] + match.groupValues[3] + "[REDACTED]"
        }
        val withoutYaml = yamlField.replace(withoutAssignments) { match ->
            match.groupValues[1] + match.groupValues[2] + ": [REDACTED]"
        }
        return sipPasswordField.replace(withoutYaml) { match ->
            "|${match.groupValues[1]}[REDACTED]|"
        }
    }
}
