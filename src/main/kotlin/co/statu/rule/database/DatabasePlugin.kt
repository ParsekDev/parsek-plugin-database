package co.statu.rule.database

import co.statu.parsek.PluginEventManager
import co.statu.parsek.api.ParsekPlugin
import co.statu.parsek.api.config.PluginConfigManager
import co.statu.rule.database.event.DatabaseEventListener

class DatabasePlugin : ParsekPlugin() {
    companion object {
        internal lateinit var databaseManager: DatabaseManager
    }

    override suspend fun onStart() {
        val configManager = PluginConfigManager(this, DatabaseConfig::class.java)
        pluginBeanContext.beanFactory.registerSingleton(PluginConfigManager::class.java.name, configManager)

        logger.info("Initialized plugin config")

        databaseManager =
            pluginBeanContext.getBean(DatabaseManager::class.java)

        databaseManager.migrateNewPluginId(
            "database",
            this
        )

        databaseManager.initialize(this)

        registerSingletonGlobal(databaseManager)

        val handlers = PluginEventManager.getEventListeners<DatabaseEventListener>()

        handlers.forEach {
            it.onReady(databaseManager)
        }
    }
}
