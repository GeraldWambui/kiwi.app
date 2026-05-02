package co.juneai.june.ui.screen.referral

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