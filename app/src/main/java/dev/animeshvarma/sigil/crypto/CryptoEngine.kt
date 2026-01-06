package dev.animeshvarma.sigil.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.engines.*
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.paddings.PKCS7Padding
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {

    private val secureRandom = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    // Safety Limits
    private const val MAX_DATA_LIMIT = 10 * 1024 * 1024 // 10MB
    private const val MAX_METADATA_SIZE = 4096 // 4KB

    data class KdfConfig(
        val iterations: Int,
        val memoryPow2: Int,
        val parallelism: Int
    )

    enum class Algorithm {
        AES_GCM, CHACHA20_POLY1305,
        AES_CBC, TWOFISH_CBC, SERPENT_CBC, CAMELLIA_CBC,
        CAST6_CBC, RC6_CBC, SM4_CBC, SEED_CBC,
        BLOWFISH_CBC, IDEA_CBC, CAST5_CBC, TEA_CBC, XTEA_CBC, GOST_CBC
    }

    private fun getBlockSize(algo: Algorithm): Int {
        return when (algo) {
            Algorithm.AES_GCM, Algorithm.CHACHA20_POLY1305 -> 12

            // 64-bit Block Ciphers (Weak)
            Algorithm.BLOWFISH_CBC, Algorithm.IDEA_CBC, Algorithm.CAST5_CBC,
            Algorithm.TEA_CBC, Algorithm.XTEA_CBC, Algorithm.GOST_CBC -> 8

            // Standard 128-bit Block Ciphers
            else -> 16
        }
    }

    private fun getKeySize(algo: Algorithm): Int {
        return when (algo) {
            Algorithm.SM4_CBC, Algorithm.SEED_CBC, Algorithm.CAST5_CBC,
            Algorithm.IDEA_CBC, Algorithm.TEA_CBC, Algorithm.XTEA_CBC, Algorithm.BLOWFISH_CBC -> 16

            else -> 32
        }
    }

    fun encrypt(
        data: ByteArray,
        password: CharArray,
        algorithms: List<Algorithm> = listOf(Algorithm.AES_GCM),
        kdfConfig: KdfConfig,
        compress: Boolean = true,
        logCallback: (String) -> Unit = {}
    ): String {
        if (data.size > MAX_DATA_LIMIT) throw IllegalArgumentException("Input exceeds 10MB safety limit.")

        val startTime = System.currentTimeMillis()
        logCallback("Initializing Secure Chain...")

        // 1. Salt
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)

        // 2. Root Secret (Argon2id)
        val passBytes = toBytes(password)
        val rootSecret = deriveRootSecret(passBytes, salt, kdfConfig)
        passBytes.fill(0.toByte())

        logCallback("Root Secret derived (Argon2id: ${kdfConfig.memoryPow2} pow2 memory, ${kdfConfig.iterations} iterations).")

        // 3. Payload
        var currentBytes = data.copyOf()

        if (compress) {
            val originalSize = currentBytes.size
            currentBytes = compressData(currentBytes)
            logCallback("Compression: $originalSize -> ${currentBytes.size} bytes")
        }

        // 4. Chain Encryption
        val ivListBytes = mutableListOf<ByteArray>()
        val algoNames = algorithms.joinToString(",") { it.name }

        logCallback("Chain Sequence: $algoNames")

        algorithms.forEachIndexed { index, algo ->
            val layerId = index + 1
            if (getBlockSize(algo) == 8) {
                logCallback("[WARNING] Layer $layerId uses ${algo.name} (64-bit block - Weak).")
            }

            val ivSize = getBlockSize(algo)
            val iv = ByteArray(ivSize).apply { secureRandom.nextBytes(this) }
            ivListBytes.add(iv)

            val layerKey = deriveSubKey(rootSecret, salt, "SIGIL_LAYER_$layerId", getKeySize(algo))

            logCallback("Layer $layerId: Encrypting with ${algo.name}...")
            currentBytes = processCipher(true, algo, currentBytes, layerKey, iv)
            layerKey.fill(0.toByte())
        }

        // 5. Header
        val headerKey = deriveSubKey(rootSecret, salt, "SIGIL_HEADER", 32)
        val flags = if (compress) "C" else "N"
        val ivStringList = ivListBytes.joinToString(",") { encoder.encodeToString(it) }

        val metadataString = "$algoNames|$ivStringList|$flags"
        val metadataBytes = metadataString.toByteArray(StandardCharsets.UTF_8)
        val headerIv = ByteArray(12).apply { secureRandom.nextBytes(this) }

        val encryptedMetadataBytes = encryptAesGcm(metadataBytes, headerKey, headerIv, salt)
        headerKey.fill(0.toByte())

        logCallback("Header encrypted (AES-GCM + AAD).")

        // 6. Pack
        val totalSize = 16 + 12 + 4 + encryptedMetadataBytes.size + currentBytes.size
        if (totalSize > MAX_DATA_LIMIT) throw IllegalArgumentException("Pack size exceeds limit.")

        val packBuffer = ByteBuffer.allocate(totalSize)
        packBuffer.put(salt)
        packBuffer.put(headerIv)
        packBuffer.putInt(encryptedMetadataBytes.size)
        packBuffer.put(encryptedMetadataBytes)
        packBuffer.put(currentBytes)
        val packedBytes = packBuffer.array()

        // 7. MAC
        val macKey = deriveSubKey(rootSecret, salt, "SIGIL_GLOBAL_MAC", 32)
        val hmac = calculateHMAC(packedBytes, macKey)
        macKey.fill(0.toByte())
        rootSecret.fill(0.toByte())

        val finalBytes = ByteBuffer.allocate(packedBytes.size + hmac.size)
            .put(packedBytes).put(hmac).array()

        if (finalBytes.size > MAX_DATA_LIMIT) throw IllegalArgumentException("Output exceeds limit.")

        logCallback("Global HMAC Signature applied.")
        logCallback("Operation complete in ${System.currentTimeMillis() - startTime}ms.")

        return stripPadding(encoder.encodeToString(finalBytes))
    }

    fun decrypt(
        encryptedData: String,
        password: CharArray,
        kdfConfig: KdfConfig,
        logCallback: (String) -> Unit = {}
    ): ByteArray {
        val startTime = System.currentTimeMillis()
        logCallback("Reading Secure Container...")

        val genericError = "Decryption failed. Verify password and data integrity."

        var rootSecret: ByteArray? = null

        try {
            val cleanData = encryptedData.filter { !it.isWhitespace() }
            val totalBytes = decoder.decode(restorePadding(cleanData))

            if (totalBytes.size < 64) throw Exception()
            if (totalBytes.size > MAX_DATA_LIMIT) throw Exception()

            // 1. HMAC Check
            val payloadSize = totalBytes.size - 32
            val payloadBytes = ByteArray(payloadSize)
            val storedMac = ByteArray(32)

            totalBytes.copyInto(payloadBytes, 0, 0, payloadSize)
            totalBytes.copyInto(storedMac, 0, payloadSize, totalBytes.size)

            val salt = ByteArray(16)
            payloadBytes.copyInto(salt, 0, 0, 16)

            // Reconstruct Root
            val passBytes = toBytes(password)
            rootSecret = deriveRootSecret(passBytes, salt, kdfConfig)
            passBytes.fill(0.toByte())
            logCallback("Root Secret reconstructed.")

            val macKey = deriveSubKey(rootSecret, salt, "SIGIL_GLOBAL_MAC", 32)
            val calculatedMac = calculateHMAC(payloadBytes, macKey)

            val validMac = constantTimeEquals(storedMac, calculatedMac)
            macKey.fill(0.toByte())

            if (!validMac) throw Exception()
            logCallback("Global HMAC Verified.")

            // 2. Unpack
            val buffer = ByteBuffer.wrap(payloadBytes)
            buffer.position(16) // Skip Salt
            val headerIv = ByteArray(12); buffer.get(headerIv)
            val headerLen = buffer.int

            if (headerLen !in 0..MAX_METADATA_SIZE) throw Exception()

            val encryptedMeta = ByteArray(headerLen); buffer.get(encryptedMeta)
            val bodyBytes = ByteArray(buffer.remaining()); buffer.get(bodyBytes)

            // 3. Header
            val headerKey = deriveSubKey(rootSecret, salt, "SIGIL_HEADER", 32)
            val metaBytes = decryptAesGcm(encryptedMeta, headerKey, headerIv, salt)
            headerKey.fill(0.toByte())

            val metaString = String(metaBytes, StandardCharsets.UTF_8)
            val parts = metaString.split("|")
            val algoNames = parts[0].split(",")
            val ivStrings = parts[1].split(",")
            val isCompressed = parts.getOrNull(2) == "C"

            logCallback("Layers detected: ${parts[0]}")

            // 4. Decrypt Chain
            var currentBytes = bodyBytes
            for (i in algoNames.indices.reversed()) {
                val layerId = i + 1
                val algo = Algorithm.valueOf(algoNames[i])
                val iv = decoder.decode(ivStrings[i])
                val keySize = getKeySize(algo)

                val layerKey = deriveSubKey(rootSecret, salt, "SIGIL_LAYER_$layerId", keySize)
                currentBytes = processCipher(false, algo, currentBytes, layerKey, iv)
                layerKey.fill(0.toByte())
            }

            // 5. Decompress
            if (isCompressed) {
                val compressedSize = currentBytes.size
                currentBytes = safeDecompress(currentBytes)
                logCallback("Decompressed: $compressedSize -> ${currentBytes.size} bytes")
            }

            logCallback("Decryption complete in ${System.currentTimeMillis() - startTime}ms.")
            return currentBytes

        } catch (_: Exception) {
            rootSecret?.fill(0.toByte())
            throw IllegalArgumentException(genericError)
        } finally {
            rootSecret?.fill(0.toByte())
        }
    }

    // --- SECURE HELPERS ---
    fun hashPin(pin: CharArray, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(32)
            .withMemoryPowOfTwo(16)
            .withParallelism(2)
            .withSalt(salt)
            .build()

        val g = Argon2BytesGenerator()
        g.init(params)
        val pinBytes = toBytes(pin)
        val hash = ByteArray(32)
        try {
            g.generateBytes(pinBytes, hash, 0, hash.size)
            return hash
        } finally {
            pinBytes.fill(0.toByte())
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }

    private fun toBytes(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = byteBuffer.array().copyOfRange(byteBuffer.position(), byteBuffer.limit())
        byteBuffer.array().fill(0.toByte())
        return bytes
    }

    private fun compressData(data: ByteArray): ByteArray {
        if (data.size > MAX_DATA_LIMIT) throw IllegalArgumentException("Input too large.")
        val d = Deflater(Deflater.BEST_COMPRESSION)
        d.setInput(data)
        d.finish()
        val o = ByteArrayOutputStream(data.size)
        val b = ByteArray(1024)
        while (!d.finished()) {
            val c = d.deflate(b)
            o.write(b, 0, c)
            if (o.size() > MAX_DATA_LIMIT) throw IllegalArgumentException("Compression overflow.")
        }
        d.end()
        return o.toByteArray()
    }

    private fun safeDecompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)
        var totalBytes = 0
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && !inflater.finished()) break
            totalBytes += count
            if (totalBytes > MAX_DATA_LIMIT) throw IllegalArgumentException("Decompression Limit Exceeded.")
            outputStream.write(buffer, 0, count)
        }
        inflater.end()
        return outputStream.toByteArray()
    }

    private fun stripPadding(i: String) = i.trimEnd('=')
    private fun restorePadding(i: String): String { val m = i.length % 4; return if (m > 0) i + "=".repeat(4 - m) else i }

    private fun deriveRootSecret(p: ByteArray, s: ByteArray, config: KdfConfig): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(config.iterations)
            .withMemoryPowOfTwo(config.memoryPow2)
            .withParallelism(config.parallelism)
            .withSalt(s)
            .build()
        val g = Argon2BytesGenerator()
        g.init(params)
        val r = ByteArray(32)
        g.generateBytes(p, r, 0, 32)
        return r
    }

    private fun deriveSubKey(r: ByteArray, salt: ByteArray, c: String, l: Int): ByteArray {
        val h = HKDFBytesGenerator(SHA512Digest())
        h.init(HKDFParameters(r, salt, c.toByteArray(StandardCharsets.UTF_8)))
        val k = ByteArray(l)
        h.generateBytes(k, 0, l)
        return k
    }

    private fun calculateHMAC(d: ByteArray, k: ByteArray): ByteArray { val h = HMac(SHA256Digest()); h.init(KeyParameter(k)); val o = ByteArray(h.macSize); h.update(d, 0, d.size); h.doFinal(o, 0); return o }

    private fun processCipher(encrypt: Boolean, algo: Algorithm, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return when (algo) {
            Algorithm.AES_GCM -> if (encrypt) encryptAesGcm(data, key, iv, null) else decryptAesGcm(data, key, iv, null)
            Algorithm.CHACHA20_POLY1305 -> processChaCha20Poly1305(encrypt, data, key, iv)
            Algorithm.AES_CBC -> processBlockCipher(encrypt, AESEngine.newInstance(), data, key, iv)
            Algorithm.TWOFISH_CBC -> processBlockCipher(encrypt, TwofishEngine(), data, key, iv)
            Algorithm.SERPENT_CBC -> processBlockCipher(encrypt, SerpentEngine(), data, key, iv)
            Algorithm.CAMELLIA_CBC -> processBlockCipher(encrypt, CamelliaEngine(), data, key, iv)
            Algorithm.CAST6_CBC -> processBlockCipher(encrypt, CAST6Engine(), data, key, iv)
            Algorithm.RC6_CBC -> processBlockCipher(encrypt, RC6Engine(), data, key, iv)
            Algorithm.SM4_CBC -> processBlockCipher(encrypt, SM4Engine(), data, key, iv)
            Algorithm.SEED_CBC -> processBlockCipher(encrypt, SEEDEngine(), data, key, iv)
            Algorithm.BLOWFISH_CBC -> processBlockCipher(encrypt, BlowfishEngine(), data, key, iv)
            Algorithm.IDEA_CBC -> processBlockCipher(encrypt, IDEAEngine(), data, key, iv)
            Algorithm.CAST5_CBC -> processBlockCipher(encrypt, CAST5Engine(), data, key, iv)
            Algorithm.GOST_CBC -> processBlockCipher(encrypt, GOST28147Engine(), data, key, iv)
            Algorithm.TEA_CBC -> processBlockCipher(encrypt, TEAEngine(), data, key, iv)
            Algorithm.XTEA_CBC -> processBlockCipher(encrypt, XTEAEngine(), data, key, iv)
        }
    }

    // --- LOW LEVEL CIPHER IMPLEMENTATIONS ---
    private fun processChaCha20Poly1305(encrypt: Boolean, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        // ChaCha20-Poly1305 takes a 128-bit MAC size (16 bytes) and a 96-bit Nonce (12 bytes)
        val params = AEADParameters(KeyParameter(key), 128, iv, null)
        cipher.init(encrypt, params)
        val out = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, out, 0)
        val finalLen = cipher.doFinal(out, len)
        return out.copyOf(len + finalLen)
    }

    private fun processBlockCipher(encrypt: Boolean, engine: org.bouncycastle.crypto.BlockCipher, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(engine), PKCS7Padding())
        cipher.init(encrypt, ParametersWithIV(KeyParameter(key), iv))
        val out = ByteArray(cipher.getOutputSize(data.size))
        val l = cipher.processBytes(data, 0, data.size, out, 0)
        val f = cipher.doFinal(out, l)
        return out.copyOf(l + f)
    }

    private fun encryptAesGcm(data: ByteArray, key: ByteArray, iv: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(data)
    }

    private fun decryptAesGcm(data: ByteArray, key: ByteArray, iv: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(data)
    }
}