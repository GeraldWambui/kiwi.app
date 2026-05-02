package co.juneai.june.ui.ext

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIconRes: Int,
    val unselectedIconRes: Int
)