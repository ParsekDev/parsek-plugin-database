package co.statu.rule.database

import co.statu.parsek.api.config.PluginConfigManager
import co.statu.parsek.api.event.ParsekEventListener
import co.statu.parsek.config.ConfigManager
import co.statu.rule.database.DatabasePlugin.Companion.databaseManager
import co.statu.rule.database.DatabasePlugin.Companion.logger
import co.statu.rule.database.DatabasePlugin.Companion.migrations
import co.statu.rule.database.DatabasePlugin.Companion.tables
import co.statu.rule.database.event.DatabaseEventListener

class ParsekEventHandler : ParsekEventListener {
    override suspend fun onConfigManagerReady(configManager: ConfigManager) {
        DatabasePlugin.pluginConfigManager = PluginConfigManager(
            configManager,
            DatabasePlugin.INSTANCE,
            DatabaseConfig::class.java,
            logger
        )

        logger.info("Initialized plugin config")

        databaseManager = DatabaseManager(
            DatabasePlugin.INSTANCE.context.vertx,
            DatabasePlugin.pluginConfigManager,
            logger
        )

        databaseManager.initialize(DatabasePlugin.INSTANCE, tables, migrations)

        val handlers = DatabasePlugin.INSTANCE.context.pluginEventManager.getEventHandlers<DatabaseEventListener>()

        handlers.forEach {
            it.onReady(databaseManager)
        }
    }
}