package co.juneai.june.ui.screen.vault

sealed class VaultAuthState {
    object Locked : VaultAuthState()
    object AuthenticatingBiometric : VaultAuthState()
    object RequirePin : VaultAuthState()
    object Unlocked : VaultAuthState()
    data class Error(val message: String) : VaultAuthState()
}