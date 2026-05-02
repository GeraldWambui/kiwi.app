package co.juneai.june.utils// EncryptionManager.kt
import android.content.Context
import android.os.Build
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class EncryptionManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    fun encryptAndMoveFile(sourceFile: File, destinationFile: File) {
        val encryptedFile = EncryptedFile.Builder(
            context,
            destinationFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        
        encryptedFile.openFileOutput().use { outputStream ->
            sourceFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        sourceFile.delete() // Delete original file
    }
    
    fun decryptAndRestore(sourceFile: File, destinationFile: File) {
        val encryptedFile = EncryptedFile.Builder(
            context,
            sourceFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        
        encryptedFile.openFileInput().use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
}