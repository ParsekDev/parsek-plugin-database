package co.statu.rule.database

import co.statu.parsek.api.ParsekPlugin
import co.statu.parsek.api.config.PluginConfigManager
import co.statu.rule.database.impl.SchemeVersionDaoImpl
import co.statu.rule.database.model.SchemeVersion
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.jdbcclient.JDBCPool
import org.slf4j.Logger
import java.sql.BatchUpdateException
import kotlin.system.exitProcess

class DatabaseManager(
    private val vertx: Vertx,
    private val pluginConfigManager: PluginConfigManager<DatabaseConfig>,
    private val logger: Logger,
) {
    private lateinit var pool: JDBCPool

    private val tables = mutableMapOf<ParsekPlugin, MutableList<Dao<*>>>()
    private val migrations = mutableMapOf<ParsekPlugin, MutableList<DatabaseMigration>>()

    private val schemeVersionDaoImpl = SchemeVersionDaoImpl()

    fun getTablePrefix(): String = pluginConfigManager.config.prefix

    fun getConnectionPool(): JDBCPool {
        if (!::pool.isInitialized) {
            val databaseConfig = pluginConfigManager.config

            val host = databaseConfig.host
            val name = databaseConfig.name
            val username = databaseConfig.username
            val password = databaseConfig.password

            val config: JsonObject = JsonObject()
                .put("url", "jdbc:clickhouse:$host/$name")
                .put("driver_class", "com.clickhouse.jdbc.ClickHouseDriver")
                .put("datasourceName", "parsek")
                .put("username", username)
                .put("max_pool_size", 100)

            if (password != "") {
                config
                    .put("password", password)
            }

            pool = JDBCPool.pool(vertx, config)
        }

        try {
            return pool
        } catch (e: Exception) {
            logger.error("Failed to connect database! Please check your configuration! Error is: $e")

            throw e
        }
    }

    private suspend fun initTables(plugin: ParsekPlugin) {
        try {
            tables[plugin]?.forEach { it.init(pool, plugin) }
        } catch (e: Exception) {
            logger.error(e.toString())

            throw e
        }
    }

    private suspend fun initSchemeVersion(plugin: ParsekPlugin, jdbcPool: JDBCPool) {
        val lastSchemeVersion = schemeVersionDaoImpl.getLastSchemeVersion(plugin.context.pluginId, jdbcPool)

        val latestMigration = getLatestMigration(plugin)

        if (lastSchemeVersion != null) {
            return
        }

        if (latestMigration == null) {
            schemeVersionDaoImpl.add(
                SchemeVersion(
                    pluginId = plugin.context.pluginId,
                    version = 1,
                    extra = "Init ${plugin.context.pluginId}"
                ),
                jdbcPool
            )

            return
        }

        schemeVersionDaoImpl.add(
            SchemeVersion(
                pluginId = plugin.context.pluginId,
                version = latestMigration.SCHEME_VERSION,
                extra = latestMigration.SCHEME_VERSION_INFO
            ),
            jdbcPool
        )
    }

    private suspend fun initPluginDB(plugin: ParsekPlugin, jdbcPool: JDBCPool) {
        initSchemeVersion(plugin, jdbcPool)

        initTables(plugin)

        logger.info("\"${plugin.context.pluginId}\"'s database has been initialized")
    }

    suspend fun initialize(
        plugin: ParsekPlugin,
        tables: List<Dao<*>>,
        migrations: List<DatabaseMigration>
    ) {
        if (this.tables[plugin] == null) {
            this.tables[plugin] = mutableListOf()
        }

        if (this.migrations[plugin] == null) {
            this.migrations[plugin] = mutableListOf()
        }

        this.tables[plugin]!!.addAll(tables)
        this.migrations[plugin]!!.addAll(migrations)

        val jdbcPool: JDBCPool

        try {
            jdbcPool = getConnectionPool()
        } catch (e: Exception) {
            logger.info("Connection to database failed! Shutting down...")

            exitProcess(1)
        }

        val lastSchemeVersion: SchemeVersion?

        try {
            lastSchemeVersion = schemeVersionDaoImpl.getLastSchemeVersion(plugin.context.pluginId, jdbcPool)
        } catch (e: BatchUpdateException) {
            try {
                if (plugin is DatabasePlugin) {
                    logger.warn("First time installing scheme version (first time app installation)")

                    schemeVersionDaoImpl.init(jdbcPool, plugin)
                }

                initPluginDB(plugin, jdbcPool)

                return
            } catch (e: Exception) {
                logger.error(e.message)
                logger.error("Database Error: Could not install plugin \"${plugin.context.pluginId}\". Shutting down...")

                exitProcess(1)
            }
        }

        if (lastSchemeVersion == null) {
            initPluginDB(plugin, jdbcPool)

            return
        }

        checkMigration(plugin, jdbcPool, lastSchemeVersion)
    }

    suspend fun migrateNewPluginId(exPluginId: String, newPluginId: String, plugin: ParsekPlugin) {
        try {
            schemeVersionDaoImpl.renamePluginId(exPluginId, newPluginId, getConnectionPool())
        } catch (e: Exception) {
            if (plugin !is DatabasePlugin) {
                logger.error(e.message)
                exitProcess(1)
            }
        }
    }

    internal fun getLatestMigration(plugin: ParsekPlugin) = migrations[plugin]?.maxByOrNull { it.SCHEME_VERSION }

    suspend fun checkMigration(plugin: ParsekPlugin, jdbcPool: JDBCPool, lastSchemeVersion: SchemeVersion?) {
        logger.info("Checking available database migrations for \"${plugin.context.pluginId}\"")

        val databaseVersion = lastSchemeVersion?.version ?: 0

        if (databaseVersion == 0) {
            logger.error("Database Error: Database scheme is not correct, please reinstall platform")

            return
        }

        migrate(plugin, jdbcPool, databaseVersion)
    }

    private suspend fun updateSchemeVersion(version: Int, info: String, plugin: ParsekPlugin, jdbcPool: JDBCPool) {
        schemeVersionDaoImpl.add(
            SchemeVersion(
                pluginId = plugin.context.pluginId,
                version = version,
                extra = info
            ),
            jdbcPool
        )
    }

    private suspend fun migrate(plugin: ParsekPlugin, jdbcPool: JDBCPool, databaseVersion: Int) {
        migrations[plugin]!!
            .find { it.isMigratable(databaseVersion) }
            ?.let {
                logger.info("Migration Found! Migrating database from version ${it.FROM_SCHEME_VERSION} to ${it.SCHEME_VERSION}: ${it.SCHEME_VERSION_INFO}")

                try {
                    it.migrate(jdbcPool, getTablePrefix())

                    updateSchemeVersion(it.SCHEME_VERSION, it.SCHEME_VERSION_INFO, plugin, jdbcPool)
                } catch (e: Exception) {
                    logger.error("Database Error: Migration failed from version ${it.FROM_SCHEME_VERSION} to ${it.SCHEME_VERSION}, error: " + e)

                    logger.error("Shutting down...")

                    exitProcess(1)
                }

                migrate(plugin, jdbcPool, it.SCHEME_VERSION)
            }
    }
}