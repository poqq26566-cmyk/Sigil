package dev.animeshvarma.sigil.util

import kotlin.math.log2

object SecureMemory {


    fun wipe(data: ByteArray?) {
        data?.fill(0.toByte())
    }


    fun wipe(data: CharArray?) {
        data?.fill('\u0000')
    }

    data class EntropyResult(
        val score: Int,
        val label: String,
        val colorHex: Long // ARGB Long
    )

    fun calculateEntropy(password: CharArray): EntropyResult {
        if (password.isEmpty()) return EntropyResult(0, "Empty", 0xFFB00020)

        var hasLower = false
        var hasUpper = false
        var hasDigit = false
        var hasSpecial = false

        // Zero-allocation iteration
        for (i in password.indices) {
            val c = password[i]
            if (c.isLowerCase()) hasLower = true
            else if (c.isUpperCase()) hasUpper = true
            else if (c.isDigit()) hasDigit = true
            else hasSpecial = true
        }

        var poolSize = 0
        if (hasLower) poolSize += 26
        if (hasUpper) poolSize += 26
        if (hasDigit) poolSize += 10
        if (hasSpecial) poolSize += 32

        if (poolSize == 0) return EntropyResult(0, "Weak", 0xFFCF6679)

        val entropy = password.size * log2(poolSize.toDouble())

        val score = entropy.coerceAtMost(100.0).toInt()

        return when {
            score < 40 -> EntropyResult(score, "Weak", 0xFFCF6679)      // Red
            score < 60 -> EntropyResult(score, "Fair", 0xFFFFD54F)      // Orange
            score < 80 -> EntropyResult(score, "Strong", 0xFF81C784)    // Light Green
            else -> EntropyResult(score, "Unbreakable", 0xFF00E676)     // Bright Green
        }
    }

    fun calculateEntropy(password: String): EntropyResult {
        val chars = password.toCharArray()
        val result = calculateEntropy(chars)
        wipe(chars)
        return result
    }
}