package co.juneai.june.ui.components.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.juneai.june.ui.theme.DarkNavActive
import co.juneai.june.ui.theme.DarkNavUnselected
import co.juneai.june.ui.theme.KiwiDark
import co.juneai.june.ui.theme.NavUnselected

@Composable
fun KiwiPhotosBottomNavBar(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f
    val backgroundColor = MaterialTheme.colorScheme.background.copy(alpha = if (isLight) 0.92f else 0.96f)
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Column(modifier = modifier.background(backgroundColor)) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = dividerColor
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route
                NavBarTab(
                    item = item,
                    selected = isSelected,
                    isLight = isLight,
                    onClick = { onTabSelected(item.route) }
                )
            }
        }
    }
}

@Composable
private fun NavBarTab(
    item: BottomNavItem,
    selected: Boolean,
    isLight: Boolean,
    onClick: () -> Unit
) {
    val activeColor = if (isLight) KiwiDark else DarkNavActive
    val inactiveColor = if (isLight) NavUnselected else DarkNavUnselected

    val tintColor by animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(220),
        label = "navTint"
    )

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navScale"
    )

    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 0.12f else 0f,
        animationSpec = tween(200),
        label = "pillAlpha"
    )

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(activeColor.copy(alpha = pillAlpha))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = if (selected) item.selectedIconRes else item.unselectedIconRes),
                contentDescription = item.label,
                tint = tintColor,
                modifier = Modifier
                    .size(24.dp)
                    .scale(scale)
            )
        }
        Text(
            text = item.label,
            color = tintColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
