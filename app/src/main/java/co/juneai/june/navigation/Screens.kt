package co.juneai.june.navigation

sealed class Screens(val route: String) {
    object Library : Screens("library")
    object ForYou : Screens("for_you")
    object Albums : Screens("albums")
    object Search : Screens("search")
    object ImageDetail : Screens("image_detail/{imageId}")
    object Trash : Screens("trash")
    object Settings : Screens("settings")
    object StorageDetails : Screens("storage_details")
    object Subscription : Screens("subscription")
    object Passcode : Screens("passcode")
    object VaultSetup : Screens("vault_setup")
    object ForgotPassword : Screens("forgot_password")
    object SecureVault : Screens("secure_vault")
    object Referral : Screens("referral")
    object Photos : Screens("library")
}