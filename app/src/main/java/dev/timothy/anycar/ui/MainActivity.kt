package dev.timothy.anycar.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.timothy.anycar.ui.screens.MetaSettings
import dev.timothy.anycar.ui.screens.SystemsReport
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.timothy.anycar.BuildConfig
import dev.timothy.anycar.graph
import dev.timothy.anycar.ui.screens.AddCarFlow
import dev.timothy.anycar.ui.screens.HomeScreen
import dev.timothy.anycar.ui.theme.CarTheme

object Routes {
    const val HOME = "home"
    const val FIRST_RUN = "first_run"
    const val ADD_CAR = "add_car"
    const val DEBUG = "debug"
    const val SYSTEMS = "systems/{id}"

    fun systems(id: Long) = "systems/$id"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CarTheme { AnyCarNavHost() } }
    }
}

@Composable
fun AnyCarNavHost(nav: NavHostController = rememberNavController()) {
    val ctx = LocalContext.current
    val firstRunDone by ctx.graph.settings.firstRunDone.collectAsStateWithLifecycle(initialValue = null)
    val done = firstRunDone ?: return  // wait for DataStore before choosing the start screen

    NavHost(navController = nav, startDestination = if (done) Routes.HOME else Routes.FIRST_RUN) {
        composable(Routes.HOME) {
            val graph = ctx.graph
            val vehicles by remember { graph.vehicles.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
            val metaAi by graph.settings.metaAiEnabled.collectAsStateWithLifecycle(initialValue = false)
            val metaSpeech by graph.settings.metaSpeechEnabled.collectAsStateWithLifecycle(initialValue = false)
            val viaCarCall by graph.settings.voiceViaCarCall.collectAsStateWithLifecycle(initialValue = true)
            val spoken by graph.settings.ttsEnabled.collectAsStateWithLifecycle(initialValue = true)
            val scope = rememberCoroutineScope()
            HomeScreen(
                vehicles = vehicles,
                onEnterCarMode = { v -> ctx.startActivity(CarModeActivity.intent(ctx, v.id, manual = true)) },
                onAddCar = { nav.navigate(Routes.ADD_CAR) },
                onOpenDebug = if (BuildConfig.DEBUG) ({ nav.navigate(Routes.DEBUG) }) else null,
                meta = MetaSettings(graph.meta.configured, metaAi, metaSpeech),
                onMetaAi = { on -> scope.launch { graph.settings.setMetaAiEnabled(on) } },
                onMetaSpeech = { on -> scope.launch { graph.settings.setMetaSpeechEnabled(on) } },
                voiceViaCarCall = viaCarCall,
                onVoiceViaCarCall = { on -> scope.launch { graph.settings.setVoiceViaCarCall(on) } },
                spokenReplies = spoken,
                onSpokenReplies = { on -> scope.launch { graph.settings.setTtsEnabled(on) } },
                onSystemsReport = { v -> nav.navigate(Routes.systems(v.id)) },
            )
        }
        composable(Routes.SYSTEMS) { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            Scaffold { insets ->
                Column(Modifier.padding(insets).padding(24.dp)) {
                    SystemsReport(id, large = false) { nav.popBackStack() }
                }
            }
        }
        composable(Routes.FIRST_RUN) {
            AddCarFlow(firstRun = true) {
                nav.navigate(Routes.HOME) { popUpTo(Routes.FIRST_RUN) { inclusive = true } }
            }
        }
        composable(Routes.ADD_CAR) { AddCarFlow(firstRun = false) { nav.popBackStack() } }
        debugRoutes()
    }
}
