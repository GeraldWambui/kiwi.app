package co.juneai.june.ui.screen.referral

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ReferralState(
    val referralCode: String = "KIWI-5G-GROW",
    val referralCount: Int = 3,
    val referralsNeeded: Int = 5,
    val totalEarnedGb: Int = 15,
    val tiers: List<ReferralTier> = listOf(
        ReferralTier(5, true),
        ReferralTier(10, true),
        ReferralTier(15, true),
        ReferralTier(20, false),
        ReferralTier(25, false)
    )
)

data class ReferralTier(
    val gb: Int,
    val unlocked: Boolean
)

@HiltViewModel
class ReferralViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    private val _state = MutableStateFlow(ReferralState())
    val state: StateFlow<ReferralState> = _state.asStateFlow()

    fun copyCodeToClipboard() {
        val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Referral Code", _state.value.referralCode)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(application, "Code copied!", Toast.LENGTH_SHORT).show()
    }

    fun shareViaMessage() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildShareText())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(Intent.createChooser(intent, "Share via Message").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareViaEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:")
            putExtra(Intent.EXTRA_SUBJECT, "Join Kiwi Gallery — Get 5GB Free!")
            putExtra(Intent.EXTRA_TEXT, buildShareText())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            application.startActivity(intent)
        } catch (_: Exception) {
            // Fall back to generic share
            shareViaMessage()
        }
    }

    fun shareViaOther() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildShareText())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun buildShareText(): String {
        return "Join Kiwi Gallery and we both get 5GB free cloud storage! " +
                "Use my referral code: ${_state.value.referralCode}\n\n" +
                "Download: https://kiwigallery.app/invite/${_state.value.referralCode}"
    }
}
