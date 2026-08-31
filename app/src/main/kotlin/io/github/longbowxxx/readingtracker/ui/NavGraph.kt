package io.github.longbowxxx.readingtracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.longbowxxx.readingtracker.ui.link.LinkWorkScreen
import io.github.longbowxxx.readingtracker.ui.record.RecordScreen
import io.github.longbowxxx.readingtracker.ui.visit.VisitScreen

/** 画面の宛先。 */
object Destinations {
    /** 記録する（User Story 1）。個室で本を手に持った状態で使う。 */
    const val RECORD = "record"

    /** 来店時に読める続きを見る（User Story 2）。 */
    const val VISIT = "visit"

    /** 暫定記録を正式な作品へ紐づける（User Story 3）。 */
    const val LINK = "link"
}

private data class TabItem(val route: String, val label: String)

private val tabs =
    listOf(
        TabItem(Destinations.RECORD, "記録する"),
        TabItem(Destinations.VISIT, "この店で読む"),
        TabItem(Destinations.LINK, "暫定記録"),
    )

@Composable
fun ReadingTrackerNavGraph(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(Destinations.RECORD) { inclusive = tab.route == Destinations.RECORD }
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = {},
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.RECORD,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destinations.RECORD) { RecordScreen() }
            composable(Destinations.VISIT) { VisitScreen() }
            composable(Destinations.LINK) { LinkWorkScreen() }
        }
    }
}
