package co.statu.rule.database

import co.statu.parsek.api.ParsekPlugin
import co.statu.parsek.api.PluginContext
import co.statu.parsek.api.config.PluginConfigManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DatabasePlugin(pluginContext: PluginContext) : ParsekPlugin(pluginContext) {
    companion object {
        internal val logger: Logger = LoggerFactory.getLogger(DatabasePlugin::class.java)

        internal lateinit var pluginConfigManager: PluginConfigManager<DatabaseConfig>

        internal lateinit var INSTANCE: DatabasePlugin

        internal val tables = listOf<Dao<*>>()
        internal val migrations = listOf<DatabaseMigration>()

        internal lateinit var databaseManager: DatabaseManager
    }

    init {
        INSTANCE = this

        logger.info("Initialized instance")

        context.pluginEventManager.register(this, ParsekEventHandler())

        logger.info("Registered event")
    }
}

