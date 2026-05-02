package co.juneai.june.ui.screen.referral

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.juneai.june.R

@Composable
fun ReferralScreen(
    viewModel: ReferralViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Invite, 1 = Rewards

    // Theme-sourced colors
    val ReferralBg = MaterialTheme.colorScheme.background
    val CardBg = MaterialTheme.colorScheme.surface
    val AccentOrange = Color(0xFFE86B30) // Keep orange as distinctive brand color
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val AccentGreenBright = MaterialTheme.colorScheme.tertiaryContainer
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val CodeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val CodeBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val TierBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val PillContainerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val PillActiveBg = AccentGreen

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReferralBg)
    ) {
        // ── Top Bar ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = PrimaryText,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.kiwi_gallery),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        // ── Tab Toggle Pill ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(PillContainerBg)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Invite Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .then(
                            if (selectedTab == 0) Modifier.background(PillActiveBg)
                            else Modifier
                        )
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.invite),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedTab == 0) Color.White else SecondaryText
                    )
                }

                // Rewards Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .then(
                            if (selectedTab == 1) Modifier.background(AccentOrange)
                            else Modifier
                        )
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.rewards),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedTab == 1) Color.White else SecondaryText
                    )
                }
            }
        }

        // ── Content ────────────────────────────────────────
        if (selectedTab == 0) {
            InviteTab(state = state, viewModel = viewModel)
        } else {
            RewardsTab(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun InviteTab(
    state: ReferralState,
    viewModel: ReferralViewModel
) {
    val scrollState = rememberScrollState()
    val ReferralBg = MaterialTheme.colorScheme.background
    val CardBg = MaterialTheme.colorScheme.surface
    val AccentOrange = Color(0xFFE86B30)
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val AccentGreenBright = MaterialTheme.colorScheme.tertiaryContainer
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val CodeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val CodeBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // ── Hero Illustration ──────────────────────────
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AccentGreen.copy(alpha = 0.3f),
                            Color(0xFF1A2E16).copy(alpha = 0.6f),
                            ReferralBg
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Kiwi emoji
                Text(
                    text = "🥝",
                    fontSize = 48.sp
                )
                // Heart emoji
                Text(
                    text = "❤️",
                    fontSize = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Title ──────────────────────────────────────
        Text(
            text = stringResource(R.string.give_5gb_get_5gb),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.share_the_love_of_nature_invite_your_friends_and_you_both_get_5gb_bonus_storage),
            fontSize = 14.sp,
            color = SecondaryText,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── Referral Code Card ─────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.your_unique_code),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = SecondaryText,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Code display + copy
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CodeBg)
                    .border(1.dp, CodeBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.referralCode,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.copyCodeToClipboard() }
                        .border(1.dp, CodeBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = PrimaryText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Copy",
                        fontSize = 13.sp,
                        color = PrimaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Share Buttons ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ShareOption(
                    icon = Icons.AutoMirrored.Filled.Message,
                    label = stringResource(R.string.message),
                    bgColor = AccentGreen,
                    onClick = { viewModel.shareViaMessage() }
                )
                ShareOption(
                    icon = Icons.Default.Email,
                    label = stringResource(R.string.email),
                    bgColor = AccentGreen,
                    onClick = { viewModel.shareViaEmail() }
                )
                ShareOption(
                    icon = Icons.Default.Share,
                    label = stringResource(R.string.other),
                    bgColor = AccentGreen,
                    onClick = { viewModel.shareViaOther() }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Your Rewards section ──────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.your_rewards),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Text(
                text = stringResource(R.string.gb_earned, state.totalEarnedGb),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AccentGreenBright
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Progress Card ──────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.progress_to_next_tier),
                    fontSize = 13.sp,
                    color = SecondaryText
                )
                Text(
                    text = stringResource(
                        R.string.referrals,
                        state.referralCount,
                        state.referralsNeeded
                    ),
                    fontSize = 13.sp,
                    color = SecondaryText
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { state.referralCount.toFloat() / state.referralsNeeded },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = AccentOrange,
                trackColor = Color(0xFF2A3527)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Tier Dots ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                state.tiers.forEach { tier ->
                    TierDot(tier = tier)
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun RewardsTab(
    state: ReferralState,
    viewModel: ReferralViewModel
) {
    val scrollState = rememberScrollState()
    val CardBg = MaterialTheme.colorScheme.surface
    val AccentOrange = Color(0xFFE86B30)
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // ── Circular progress ring ─────────────────────
        val progress = state.referralCount.toFloat() / state.referralsNeeded
        val ringColor = AccentOrange
        val ringTrackColor = Color(0xFF2A3527)

        Box(
            modifier = Modifier
                .size(200.dp)
                .drawBehind {
                    // Track
                    drawCircle(
                        color = ringTrackColor,
                        radius = size.minDimension / 2 - 12.dp.toPx(),
                        style = Stroke(width = 10.dp.toPx())
                    )
                    // Progress arc
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 10.dp.toPx()),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            12.dp.toPx(), 12.dp.toPx()
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - 24.dp.toPx(),
                            size.height - 24.dp.toPx()
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🍃", fontSize = 32.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${state.referralCount}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = " of ${state.referralsNeeded}",
                        fontSize = 18.sp,
                        color = SecondaryText,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.friends_referred),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SecondaryText,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Growth Progress Card ──────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Growth Progress",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryText
                )
                Text(
                    text = "Next Tier: ${(state.referralCount / state.referralsNeeded + 1) * state.referralsNeeded * 2}GB",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentOrange
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = AccentOrange,
                trackColor = Color(0xFF2A3527)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Reach ${state.referralsNeeded} referrals to unlock an extra ${state.referralsNeeded * 2}GB of premium encrypted storage.",
                fontSize = 13.sp,
                color = SecondaryText,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Refer More Button ────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(AccentOrange)
                .clickable { viewModel.shareViaOther() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Refer More Friends",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Your Rewards List ─────────────────────────
        Text(
            text = "Your Rewards",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryText,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Reward items
        val rewards = listOf(
            Triple("+5GB Storage Bonus", "For inviting Sarah Thompson", true),
            Triple("+5GB Storage Bonus", "For inviting Mike Chen", true),
            Triple("+5GB Storage Bonus", "For inviting David Miller", true)
        )

        rewards.forEach { (title, subtitle, active) ->
            RewardItem(title = title, subtitle = subtitle, isActive = active)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun RewardItem(
    title: String,
    subtitle: String,
    isActive: Boolean
) {
    val CardBg = MaterialTheme.colorScheme.surface
    val AccentOrange = Color(0xFFE86B30)
    val AccentGreenBright = MaterialTheme.colorScheme.tertiaryContainer
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val CodeBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, CodeBorder, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Orange cloud icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentOrange.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text("☁️", fontSize = 22.sp)
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = SecondaryText
            )
        }

        Text(
            text = if (isActive) "Active" else "Locked",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) AccentGreenBright else SecondaryText
        )
    }
}

@Composable
private fun ShareOption(
    icon: ImageVector,
    label: String,
    bgColor: Color,
    onClick: () -> Unit
) {
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = bgColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = SecondaryText
        )
    }
}

@Composable
private fun TierDot(tier: ReferralTier) {
    val AccentOrange = Color(0xFFE86B30)
    val TierBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val CodeBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (tier.unlocked) AccentOrange else TierBg
                )
                .then(
                    if (!tier.unlocked) Modifier.border(1.dp, CodeBorder, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (tier.unlocked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Unlocked",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = SecondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${tier.gb}GB",
            fontSize = 11.sp,
            color = if (tier.unlocked) PrimaryText else SecondaryText
        )
    }
}
