package co.juneai.june.ui.ext

import co.juneai.june.R
import co.juneai.june.navigation.Screens

val bottomNavItems = listOf(
    BottomNavItem(
        route = Screens.Library.route,
        label = "Photos",
        selectedIconRes = R.drawable.ic_ios_home_selected,
        unselectedIconRes = R.drawable.ic_ios_home
    ),
    BottomNavItem(
        route = Screens.ForYou.route,
        label = "Memories",
        selectedIconRes = R.drawable.ic_stitch_heart_outline,
        unselectedIconRes = R.drawable.ic_stitch_heart_outline
    ),
    BottomNavItem(
        route = Screens.Search.route,
        label = "Search",
        selectedIconRes = R.drawable.ic_ios_search,
        unselectedIconRes = R.drawable.ic_ios_search
    ),
    BottomNavItem(
        route = Screens.Albums.route,
        label = "Collections",
        selectedIconRes = R.drawable.ic_stitch_albums_filled,
        unselectedIconRes = R.drawable.ic_stitch_albums_outline
    )
)