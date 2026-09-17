package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Inbox : Screen("inbox")
    object Saved : Screen("saved")
    object More : Screen("more")
    object Detail : Screen("detail/{screenshotId}") {
        fun createRoute(screenshotId: Long) = "detail/$screenshotId"
    }
}

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    object Inbox : BottomNavItem(
        route = Screen.Inbox.route,
        title = "Inbox",
        selectedIcon = Icons.Filled.Inbox,
        unselectedIcon = Icons.Outlined.Inbox,
        testTag = "nav_inbox_tab"
    )

    object Saved : BottomNavItem(
        route = Screen.Saved.route,
        title = "Saved",
        selectedIcon = Icons.Filled.Bookmark,
        unselectedIcon = Icons.Outlined.BookmarkBorder,
        testTag = "nav_saved_tab"
    )

    object More : BottomNavItem(
        route = Screen.More.route,
        title = "More",
        selectedIcon = Icons.Filled.MoreHoriz,
        unselectedIcon = Icons.Outlined.MoreHoriz,
        testTag = "nav_more_tab"
    )
}

val bottomNavItems = listOf(
    BottomNavItem.Inbox,
    BottomNavItem.Saved,
    BottomNavItem.More
)
