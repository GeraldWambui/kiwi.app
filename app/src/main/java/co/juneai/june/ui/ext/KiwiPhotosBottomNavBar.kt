package co.juneai.june.ui.ext

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val backgroundColor = MaterialTheme.colorScheme.background.copy(alpha = if (isLight) 0.90f else 0.95f)
    val dividerColor = MaterialTheme.colorScheme.outline

    Column(modifier = modifier.background(backgroundColor)) {
        HorizontalDivider(
            thickness = 1.dp,
            color = dividerColor
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 4.dp),
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
        animationSpec = tween(250),
        label = "navTint"
    )

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(id = if (selected) item.selectedIconRes else item.unselectedIconRes),
            contentDescription = item.label,
            tint = tintColor,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = item.label,
            color = tintColor,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
