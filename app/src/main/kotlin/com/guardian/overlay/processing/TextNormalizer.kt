package com.guardian.overlay.processing

object TextNormalizer {
    private val slangMap = mapOf(
        "asap" to "urgent",
        "lah" to "",
        "pls" to "please",
        "rm" to "ringgit",
        "duit" to "money",
        "gcash" to "wallet",
        "otp" to "one time password",
        "gov" to "government"
    )

    fun normalize(input: String): String {
        var text = input.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

        slangMap.forEach { (k, v) ->
            text = text.replace(Regex("\\b${Regex.escape(k)}\\b"), v)
        }

        return text.replace(Regex("\\s+"), " ").trim()
    }
}
