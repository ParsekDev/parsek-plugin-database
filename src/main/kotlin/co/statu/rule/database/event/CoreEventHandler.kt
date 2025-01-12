package co.statu.rule.database.event

import co.statu.parsek.PluginEventManager
import co.statu.parsek.api.annotation.EventListener
import co.statu.parsek.api.config.PluginConfigManager
import co.statu.parsek.api.event.CoreEventListener
import co.statu.parsek.config.ConfigManager
import co.statu.rule.database.DatabaseConfig
import co.statu.rule.database.DatabaseManager
import co.statu.rule.database.DatabasePlugin
import org.slf4j.Logger

@EventListener
class CoreEventHandler(
    private val logger: Logger,
    private val databasePlugin: DatabasePlugin,
) : CoreEventListener {
    private val databaseManager by lazy {
        databasePlugin.pluginBeanContext.getBean(DatabaseManager::class.java)
    }

    override suspend fun onConfigManagerReady(configManager: ConfigManager) {
        val pluginConfigManager = PluginConfigManager(
            databasePlugin,
            DatabaseConfig::class.java,
        )
        databasePlugin.pluginBeanContext.beanFactory.registerSingleton(
            pluginConfigManager.javaClass.name,
            pluginConfigManager
        )

        logger.info("Initialized plugin config")

        DatabasePlugin.databaseManager = databaseManager

        databaseManager.migrateNewPluginId(
            "database",
            databasePlugin.pluginId,
            databasePlugin
        )

        databaseManager.initialize(databasePlugin)

        databasePlugin.registerSingletonGlobal(databaseManager)

        val handlers = PluginEventManager.getEventListeners<DatabaseEventListener>()

        handlers.forEach {
            it.onReady(databaseManager)
        }
    }
}