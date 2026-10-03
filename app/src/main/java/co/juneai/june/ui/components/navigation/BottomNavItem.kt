package co.juneai.june.ui.components.navigation

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIconRes: Int,
    val unselectedIconRes: Int
)
