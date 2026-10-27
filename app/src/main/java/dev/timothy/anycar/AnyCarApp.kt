package dev.timothy.anycar

import android.app.Application
import android.content.Context
import dev.timothy.anycar.actions.CommandExecutor
import dev.timothy.anycar.actions.CommandRouter
import dev.timothy.anycar.actions.ContactResolver
import dev.timothy.anycar.actions.ExternalLauncher
import dev.timothy.anycar.ai.DashboardScanner
import dev.timothy.anycar.ai.MetaApi
import dev.timothy.anycar.ai.MuseAgent
import dev.timothy.anycar.car.CarSession
import dev.timothy.anycar.car.Notifications
import dev.timothy.anycar.data.AppDatabase
import dev.timothy.anycar.data.SettingsStore
import dev.timothy.anycar.data.VehicleRepository
import dev.timothy.anycar.voice.MetaSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dev.timothy.anycar.capability.CapabilityChecker
import dev.timothy.anycar.car.WheelButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AnyCarApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Notifications.createChannels(this)
    }
}

/** Process-wide singletons. */
class AppGraph(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vehicles = VehicleRepository(AppDatabase.create(app).vehicles())
    val settings = SettingsStore(app)

    // Hackathon edition: the key comes from local.properties at build time (see app/build.gradle.kts).
    val meta = MetaApi(BuildConfig.META_API_KEY)
    val scanner = DashboardScanner(meta)
    @Volatile var metaAiOn = false
        private set

    val session = CarSession(app, settings, appScope, MetaSpeech(meta))
    val executor = CommandExecutor(app, ExternalLauncher(app, session), ContactResolver(app)) { metaAiOn && meta.configured }
    private val router = CommandRouter(executor, MuseAgent(meta)) { metaAiOn }
    val checker = CapabilityChecker(app, vehicles, settings) { meta.configured && settings.metaSpeechEnabled.first() }

    init {
        appScope.launch { settings.metaAiEnabled.collect { metaAiOn = it } }
        appScope.launch { WheelButton.sync(app, vehicles.all()) }
        appScope.launch { vehicles.observeAll().collect { session.refresh(it) } }
        session.handler = router::handle
        session.onExit = executor::clear
    }
}

val Context.graph: AppGraph get() = (applicationContext as AnyCarApp).graph
