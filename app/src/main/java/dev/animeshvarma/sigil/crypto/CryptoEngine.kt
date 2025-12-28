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
import org.bouncycastle.crypto.engines.*
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PKCS7Padding
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.Arrays
import java.util.zip.Deflater
import java.util.zip.Inflater

object CryptoEngine {

    private val secureRandom = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    // Safety Limits
    private const val MAX_DATA_LIMIT = 10 * 1024 * 1024 // 10MB
    private const val MAX_METADATA_SIZE = 4096 // 4KB

    // Legacy Delimiter as Bytes
    private val LEGACY_DELIMITER_BYTES = "::SIGIL_CS::".toByteArray(StandardCharsets.UTF_8)

    enum class Algorithm {
        AES_GCM, AES_CBC, TWOFISH_CBC, SERPENT_CBC, CAMELLIA_CBC,
        CAST6_CBC, RC6_CBC, SM4_CBC, SEED_CBC,
        BLOWFISH_CBC, IDEA_CBC, CAST5_CBC, TEA_CBC, XTEA_CBC, GOST_CBC
    }

    private fun getBlockSize(algo: Algorithm): Int {
        return when (algo) {
            Algorithm.BLOWFISH_CBC, Algorithm.IDEA_CBC, Algorithm.CAST5_CBC,
            Algorithm.TEA_CBC, Algorithm.XTEA_CBC, Algorithm.GOST_CBC -> 8
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
        compress: Boolean = true,
        logCallback: (String) -> Unit = {}
    ): String {
        if (data.size > MAX_DATA_LIMIT) throw IllegalArgumentException("Input exceeds 10MB safety limit.")

        val startTime = System.currentTimeMillis()
        logCallback("Initializing Secure Chain...")

        // 1. Salt
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)

        // 2. Root Secret
        val passBytes = toBytes(password)
        val rootSecret = deriveRootSecret(passBytes, salt)
        Arrays.fill(passBytes, 0.toByte())

        logCallback("Root Secret derived (Argon2id).")

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
            val iv = ByteArray(getBlockSize(algo)).apply { secureRandom.nextBytes(this) }
            ivListBytes.add(iv)

            val layerKey = deriveSubKey(rootSecret, "SIGIL_LAYER_$layerId", getKeySize(algo))

            logCallback("Layer $layerId: Encrypting with ${algo.name}...")
            currentBytes = processCipher(true, algo, currentBytes, layerKey, iv)
            Arrays.fill(layerKey, 0.toByte())
        }

        // 5. Header
        val headerKey = deriveSubKey(rootSecret, "SIGIL_HEADER", 32)
        val flags = if (compress) "C" else "N"
        val ivStringList = ivListBytes.joinToString(",") { encoder.encodeToString(it) }

        val metadataString = "$algoNames|$ivStringList|$flags"
        val metadataBytes = metadataString.toByteArray(StandardCharsets.UTF_8)
        val headerIv = ByteArray(12).apply { secureRandom.nextBytes(this) }

        val encryptedMetadataBytes = encryptAesGcm(metadataBytes, headerKey, headerIv, metadataBytes)
        Arrays.fill(headerKey, 0.toByte())

        logCallback("Header encrypted & sealed (AES-GCM + AAD).")

        // 6. Pack
        val packBuffer = ByteBuffer.allocate(16 + 12 + 4 + encryptedMetadataBytes.size + currentBytes.size)
        packBuffer.put(salt)
        packBuffer.put(headerIv)
        packBuffer.putInt(encryptedMetadataBytes.size)
        packBuffer.put(encryptedMetadataBytes)
        packBuffer.put(currentBytes)
        val packedBytes = packBuffer.array()

        // 7. MAC
        val macKey = deriveSubKey(rootSecret, "SIGIL_GLOBAL_MAC", 32)
        val hmac = calculateHMAC(packedBytes, macKey)
        Arrays.fill(macKey, 0.toByte())
        Arrays.fill(rootSecret, 0.toByte())

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
        logCallback: (String) -> Unit = {}
    ): ByteArray {
        val startTime = System.currentTimeMillis()
        logCallback("Reading Secure Container...")

        try {
            val cleanData = encryptedData.filter { !it.isWhitespace() }
            val totalBytes = decoder.decode(restorePadding(cleanData))

            if (totalBytes.size < 64) throw IllegalArgumentException("Data corrupted (Size too small).")
            if (totalBytes.size > MAX_DATA_LIMIT) throw IllegalArgumentException("Input exceeds 10MB limit.")

            // 1. HMAC Check
            val payloadSize = totalBytes.size - 32
            val payloadBytes = ByteArray(payloadSize)
            val storedMac = ByteArray(32)
            System.arraycopy(totalBytes, 0, payloadBytes, 0, payloadSize)
            System.arraycopy(totalBytes, payloadSize, storedMac, 0, 32)

            val salt = ByteArray(16)
            System.arraycopy(payloadBytes, 0, salt, 0, 16)

            // Reconstruct Root
            val passBytes = toBytes(password)
            val rootSecret = deriveRootSecret(passBytes, salt)
            Arrays.fill(passBytes, 0.toByte())
            logCallback("Root Secret reconstructed.")

            val macKey = deriveSubKey(rootSecret, "SIGIL_GLOBAL_MAC", 32)
            val calculatedMac = calculateHMAC(payloadBytes, macKey)

            if (!constantTimeEquals(storedMac, calculatedMac)) {
                Arrays.fill(rootSecret, 0.toByte())
                throw IllegalArgumentException("HMAC Verification Failed.")
            }
            Arrays.fill(macKey, 0.toByte())
            logCallback("Global HMAC Verified. Container is authentic.")

            // 2. Unpack
            val buffer = ByteBuffer.wrap(payloadBytes)
            buffer.position(16)
            val headerIv = ByteArray(12); buffer.get(headerIv)
            val headerLen = buffer.int

            if (headerLen !in 0..MAX_METADATA_SIZE) {
                Arrays.fill(rootSecret, 0.toByte())
                throw IllegalArgumentException("Invalid Header Size (Potential DoS Attack).")
            }

            val encryptedMeta = ByteArray(headerLen); buffer.get(encryptedMeta)
            val bodyBytes = ByteArray(buffer.remaining()); buffer.get(bodyBytes)

            // 3. Header
            val headerKey = deriveSubKey(rootSecret, "SIGIL_HEADER", 32)
            val metaBytes = decryptAesGcm(encryptedMeta, headerKey, headerIv)
            Arrays.fill(headerKey, 0.toByte())

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
                val layerKey = deriveSubKey(rootSecret, "SIGIL_LAYER_$layerId", keySize)

                logCallback("Layer $layerId: Decrypting with ${algo.name}...")
                currentBytes = processCipher(false, algo, currentBytes, layerKey, iv)
                Arrays.fill(layerKey, 0.toByte())
            }
            Arrays.fill(rootSecret, 0.toByte())

            // 5. Decompress
            if (isCompressed) {
                val compressedSize = currentBytes.size
                currentBytes = safeDecompress(currentBytes)
                logCallback("Decompressed: $compressedSize -> ${currentBytes.size} bytes")
            }

            // 6. Secure Legacy Checksum Logic
            val delimiterIndex = findSequenceIndex(currentBytes, LEGACY_DELIMITER_BYTES)
            if (delimiterIndex != -1) {

                val payload = currentBytes.copyOfRange(0, delimiterIndex)

                val calculatedHashStr = hashSHA256(payload)

                val storedHashBytes = currentBytes.copyOfRange(delimiterIndex + LEGACY_DELIMITER_BYTES.size, currentBytes.size)
                val storedHashStr = String(storedHashBytes, StandardCharsets.UTF_8)

                if (calculatedHashStr == storedHashStr) {
                    logCallback("Integrity Verified (Internal Checksum).")
                    logCallback("Decryption complete in ${System.currentTimeMillis() - startTime}ms.")
                    return payload // Return only the payload
                } else {
                    logCallback("[WARNING] Internal Checksum mismatch.")
                    return currentBytes // Return raw to let user decide
                }
            }

            logCallback("Decryption complete in ${System.currentTimeMillis() - startTime}ms.")
            return currentBytes

        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Container Corrupted or Invalid Format.")
        }
    }

    // --- SECURE HELPERS ---
    private fun findSequenceIndex(source: ByteArray, pattern: ByteArray): Int {
        if (pattern.isEmpty() || source.size < pattern.size) return -1
        for (i in 0..source.size - pattern.size) {
            var match = true
            for (j in pattern.indices) {
                if (source[i + j] != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
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
        Arrays.fill(byteBuffer.array(), 0.toByte())
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
            if (totalBytes > MAX_DATA_LIMIT) throw IllegalArgumentException("Decompression Limit (10MB) Exceeded.")
            outputStream.write(buffer, 0, count)
        }
        inflater.end()
        return outputStream.toByteArray()
    }

    private fun stripPadding(i: String) = i.trimEnd('=')
    private fun restorePadding(i: String): String { val m = i.length % 4; return if (m > 0) i + "=".repeat(4 - m) else i }

    private fun hashSHA256(i: ByteArray): String {
        val d = SHA256Digest()
        val o = ByteArray(d.digestSize)
        d.update(i, 0, i.size)
        d.doFinal(o, 0)
        return o.joinToString("") { "%02x".format(it) }
    }

    private fun deriveRootSecret(p: ByteArray, s: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(16)
            .withMemoryPowOfTwo(16)
            .withParallelism(4)
            .withSalt(s)
            .build()
        val g = Argon2BytesGenerator()
        g.init(params)
        val r = ByteArray(32)
        g.generateBytes(p, r, 0, 32)
        return r
    }

    private fun deriveSubKey(r: ByteArray, c: String, l: Int): ByteArray { val h = HKDFBytesGenerator(SHA512Digest()); h.init(HKDFParameters(r, null, c.toByteArray(StandardCharsets.UTF_8))); val k = ByteArray(l); h.generateBytes(k, 0, l); return k }
    private fun calculateHMAC(d: ByteArray, k: ByteArray): ByteArray { val h = HMac(SHA256Digest()); h.init(KeyParameter(k)); val o = ByteArray(h.macSize); h.update(d, 0, d.size); h.doFinal(o, 0); return o }

    private fun processCipher(encrypt: Boolean, algo: Algorithm, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return when (algo) {
            Algorithm.AES_GCM -> if (encrypt) encryptAesGcm(data, key, iv, null) else decryptAesGcm(data, key, iv)
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

    private fun processBlockCipher(encrypt: Boolean, engine: org.bouncycastle.crypto.BlockCipher, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(engine), PKCS7Padding())
        cipher.init(encrypt, ParametersWithIV(KeyParameter(key), iv))
        val out = ByteArray(cipher.getOutputSize(data.size))
        val l = cipher.processBytes(data, 0, data.size, out, 0)
        val f = cipher.doFinal(out, l)
        return out.copyOf(l + f)
    }

    private fun encryptAesGcm(data: ByteArray, key: ByteArray, iv: ByteArray, aad: ByteArray?): ByteArray {
        val c = GCMBlockCipher.newInstance(AESEngine.newInstance())
        c.init(true, ParametersWithIV(KeyParameter(key), iv))
        if (aad != null) c.processAADBytes(aad, 0, aad.size)
        val o = ByteArray(c.getOutputSize(data.size))
        val l = c.processBytes(data, 0, data.size, o, 0)
        val f = c.doFinal(o, l)
        return o.copyOf(l + f)
    }

    private fun decryptAesGcm(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val c = GCMBlockCipher.newInstance(AESEngine.newInstance())
        c.init(false, ParametersWithIV(KeyParameter(key), iv))
        val o = ByteArray(c.getOutputSize(data.size))
        val l = c.processBytes(data, 0, data.size, o, 0)
        val f = c.doFinal(o, l)
        return o.copyOf(l + f)
    }
}