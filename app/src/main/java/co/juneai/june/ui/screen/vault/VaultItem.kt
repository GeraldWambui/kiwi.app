package co.juneai.june.ui.screen.vault

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import co.juneai.june.data.model.VaultMediaType

data class VaultItem(
    val mediaId: String,
    val id: Long = mediaId.hashCode().toLong(),
    val uri: Uri,
    val fileName: String,
    val isVerified: Boolean = false,
    val fileSize: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val type: VaultMediaType,
    val vaultPath: String? = null,
    val timestamp: Long = 0L,
    val isEncrypted: Boolean = false
)


@Preview
@Composable
fun Greet() {
    Column(){
        Text(
            text = "Hi",
            lineHeight = 1.5.sp
        )
    }
}