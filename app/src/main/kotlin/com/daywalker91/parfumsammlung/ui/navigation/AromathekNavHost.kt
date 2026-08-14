package com.daywalker91.parfumsammlung.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.daywalker91.parfumsammlung.di.AppContainer
import com.daywalker91.parfumsammlung.ui.collection.CollectionScreen
import com.daywalker91.parfumsammlung.ui.detail.DetailScreen
import com.daywalker91.parfumsammlung.ui.editor.PerfumeEditorScreen

private const val ARG_PERFUME_ID = "perfumeId"
private const val NEUES_PARFUM = -1L

object AromathekRoutes {
    const val COLLECTION = "collection"
    const val DETAIL = "detail/{$ARG_PERFUME_ID}"
    const val EDITOR = "editor?$ARG_PERFUME_ID={$ARG_PERFUME_ID}"

    fun detail(perfumeId: Long) = "detail/$perfumeId"
    fun editor(perfumeId: Long? = null) = "editor?$ARG_PERFUME_ID=${perfumeId ?: NEUES_PARFUM}"
}

@Composable
fun AromathekNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = AromathekRoutes.COLLECTION) {
        composable(AromathekRoutes.COLLECTION) {
            CollectionScreen(
                repository = container.perfumeRepository,
                onPerfumeClick = { navController.navigate(AromathekRoutes.detail(it)) },
                onAddClick = { navController.navigate(AromathekRoutes.editor()) },
            )
        }
        composable(
            route = AromathekRoutes.DETAIL,
            arguments = listOf(navArgument(ARG_PERFUME_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val perfumeId = backStackEntry.arguments?.getLong(ARG_PERFUME_ID) ?: return@composable
            DetailScreen(
                perfumeId = perfumeId,
                repository = container.perfumeRepository,
                onEditClick = { navController.navigate(AromathekRoutes.editor(perfumeId)) },
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack(AromathekRoutes.COLLECTION, inclusive = false) },
            )
        }
        composable(
            route = AromathekRoutes.EDITOR,
            arguments = listOf(
                navArgument(ARG_PERFUME_ID) {
                    type = NavType.LongType
                    defaultValue = NEUES_PARFUM
                },
            ),
        ) { backStackEntry ->
            val perfumeId = backStackEntry.arguments?.getLong(ARG_PERFUME_ID) ?: NEUES_PARFUM
            PerfumeEditorScreen(
                perfumeId = perfumeId.takeIf { it != NEUES_PARFUM },
                repository = container.perfumeRepository,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
