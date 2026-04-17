package co.juneai.june.navigation

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object ForYou : Screen("for_you")
    object Albums : Screen("albums")
    object Search : Screen("search")
    object ImageDetail : Screen("image_detail/{imageId}")
    object Trash : Screen("trash")
    object Settings : Screen("settings")
    object StorageDetails : Screen("storage_details")
    object Subscription : Screen("subscription")
    object Passcode : Screen("passcode")
    object VaultSetup : Screen("vault_setup")
    object ForgotPassword : Screen("forgot_password")
    object SecureVault : Screen("secure_vault")
    object Referral : Screen("referral")
    // Keep legacy route alias
    object Photos : Screen("library")
}