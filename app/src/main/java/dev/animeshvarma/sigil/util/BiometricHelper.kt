package dev.animeshvarma.sigil.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object BiometricHelper {

    private const val BIO_KEY_ALIAS = "SIGIL_BIO_AUTH_KEY"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    // --- CHECKS ---
    fun hasBiometricChanged(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

            if (!keyStore.containsAlias(BIO_KEY_ALIAS)) return false

            val key = keyStore.getKey(BIO_KEY_ALIAS, null) as? SecretKey ?: return false
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")

            cipher.init(Cipher.ENCRYPT_MODE, key)

            false
        } catch (_: KeyPermanentlyInvalidatedException) {
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isDeviceSecure(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    @Suppress("unused")
    fun isBiometricAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // --- CRYPTO LOGIC ---
    private fun getBiometricCipher(): Cipher? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

            if (!keyStore.containsAlias(BIO_KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val builder = KeyGenParameterSpec.Builder(
                    BIO_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)

                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
            }

            val key = keyStore.getKey(BIO_KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher

        } catch (_: KeyPermanentlyInvalidatedException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun resetBiometrics() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.deleteEntry(BIO_KEY_ALIAS)
        } catch (_: Exception) { }
    }

    // --- UI ---
    fun showPrompt(
        activity: FragmentActivity,
        onSuccess: (BiometricPrompt.CryptoObject?) -> Unit,
        onFailure: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Sigil Security")
            .setSubtitle("Identity Verification Required")
            .setNegativeButtonText("Use Sigil PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(true)
            .build()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess(result.cryptoObject)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onFailure()
                    BiometricPrompt.ERROR_LOCKOUT -> onError("Too many attempts. Use PIN.")
                    else -> onError(errString.toString())
                }
            }
        }

        try {
            val cipher = getBiometricCipher()
            if (cipher != null) {
                val cryptoObject = BiometricPrompt.CryptoObject(cipher)
                BiometricPrompt(activity, executor, callback).authenticate(promptInfo, cryptoObject)
            } else {
                if (hasBiometricChanged()) {
                    onFailure()
                } else {
                    onError("Biometric Key Initialization Failed")
                }
            }
        } catch (_: Exception) {
            onError("Biometric System Unavailable")
        }
    }
}