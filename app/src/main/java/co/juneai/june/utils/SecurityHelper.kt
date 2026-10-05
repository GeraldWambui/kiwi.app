package co.juneai.june.utils// SecurityHelper.kt
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64
import co.juneai.june.data.model.PinHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecurityHelper {
    companion object {
        private const val PBKDF2_ITERATIONS = 10000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
    }
        
        fun hashPin(pin: String): PinHash {
            val salt = ByteArray(SALT_LENGTH)
            SecureRandom().nextBytes(salt)
            val hash = pbkdf2(pin.toCharArray(), salt)
            return PinHash(
                hash = Base64.encodeToString(hash, Base64.NO_WRAP),
                salt = Base64.encodeToString(salt, Base64.NO_WRAP)
            )
        }
        
        fun verifyPin(pin: String, storedHash: String, storedSalt: String? = null): Boolean {
            return try {
                if (storedSalt.isNullOrEmpty()) {
                    // Check legacy SHA-256 hash
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
                    val legacyHash = hashBytes.joinToString("") { "%02x".format(it) }
                    return constantTimeEquals(legacyHash, storedHash)
                }
                val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
                val hash = pbkdf2(pin.toCharArray(), salt)
                val computedHash = Base64.encodeToString(hash, Base64.NO_WRAP)
                constantTimeEquals(computedHash, storedHash)
            } catch (e: Exception) {
                false
            }
        }
        
        private fun pbkdf2(password: CharArray, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return factory.generateSecret(spec).encoded
        }
        
        private fun constantTimeEquals(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            var result = 0
            for (i in a.indices) {
                result = result or (a[i].code xor b[i].code)
            }
            return result == 0
        }
}