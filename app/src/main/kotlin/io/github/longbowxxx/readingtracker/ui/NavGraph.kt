package io.github.longbowxxx.readingtracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.longbowxxx.readingtracker.ui.record.RecordScreen

/** 画面の宛先。来店時の参照（US2）は Phase 4 で追加する。 */
object Destinations {
    const val RECORD = "record"
}

@Composable
fun ReadingTrackerNavGraph(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Destinations.RECORD,
        modifier = modifier,
    ) {
        composable(Destinations.RECORD) {
            RecordScreen()
        }
    }
}
