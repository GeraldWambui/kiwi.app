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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.juneai.june.R
import co.juneai.june.navigation.Screen
import co.juneai.june.ui.theme.DarkNavActive
import co.juneai.june.ui.theme.DarkNavUnselected
import co.juneai.june.ui.theme.KiwiDark
import co.juneai.june.ui.theme.NavUnselected

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIconRes: Int,
    val unselectedIconRes: Int
)

val bottomNavItems = listOf(
    BottomNavItem(
        route = Screen.Library.route,
        label = "Library",
        selectedIconRes = R.drawable.ic_stitch_library_filled,
        unselectedIconRes = R.drawable.ic_stitch_library_outline
    ),
    BottomNavItem(
        route = Screen.ForYou.route,
        label = "For You",
        selectedIconRes = R.drawable.ic_stitch_heart_outline,
        unselectedIconRes = R.drawable.ic_stitch_heart_outline
    ),
    BottomNavItem(
        route = Screen.Search.route,
        label = "Search",
        selectedIconRes = R.drawable.ic_ios_search,
        unselectedIconRes = R.drawable.ic_ios_search
    ),
    BottomNavItem(
        route = Screen.Albums.route,
        label = "Albums",
        selectedIconRes = R.drawable.ic_stitch_albums_filled,
        unselectedIconRes = R.drawable.ic_stitch_albums_outline
    ),
    BottomNavItem(
        route = Screen.Settings.route,
        label = "Settings",
        selectedIconRes = R.drawable.ic_stitch_settings_filled,
        unselectedIconRes = R.drawable.ic_stitch_settings_outline
    )
)

@Composable
fun JuneBottomNavBar(
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
    // Light: active = 2D3E27 (KiwiDark), inactive = 9CA3AF (NavUnselected)
    // Dark:  active = A3E635 (DarkNavActive), inactive = FFFFFF (DarkNavUnselected)
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
