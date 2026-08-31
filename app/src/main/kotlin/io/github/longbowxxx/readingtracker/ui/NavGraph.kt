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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.longbowxxx.readingtracker.ui.link.LinkWorkScreen
import io.github.longbowxxx.readingtracker.ui.record.RecordDetailScreen
import io.github.longbowxxx.readingtracker.ui.record.RecordDetailViewModel
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

    /** 保存済みの記録を直す（User Story 4）。来店時の一覧から開く。 */
    const val RECORD_DETAIL = "record_detail/{volumeId}/{storeId}"

    fun recordDetail(volumeId: Long, storeId: Long): String = "record_detail/$volumeId/$storeId"
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
            // 詳細画面ではタブを出さない。編集中に別の画面へ飛ばさないため
            if (currentRoute in tabs.map { it.route }) {
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
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.RECORD,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destinations.RECORD) { RecordScreen() }

            composable(Destinations.VISIT) {
                VisitScreen(
                    onOpenRecord = { volumeId, storeId ->
                        navController.navigate(Destinations.recordDetail(volumeId, storeId))
                    },
                )
            }

            composable(Destinations.LINK) { LinkWorkScreen() }

            composable(
                route = Destinations.RECORD_DETAIL,
                arguments =
                listOf(
                    navArgument(RecordDetailViewModel.ARG_VOLUME_ID) { type = NavType.LongType },
                    navArgument(RecordDetailViewModel.ARG_STORE_ID) { type = NavType.LongType },
                ),
            ) {
                RecordDetailScreen(onClose = { navController.popBackStack() })
            }
        }
    }
}
