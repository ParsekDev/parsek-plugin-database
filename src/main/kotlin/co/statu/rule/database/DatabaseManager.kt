package co.statu.rule.database

import co.statu.parsek.api.ParsekPlugin
import co.statu.parsek.api.config.PluginConfigManager
import co.statu.rule.database.impl.SchemeVersionDaoImpl
import co.statu.rule.database.model.SchemeVersion
import io.vertx.core.Vertx
import io.vertx.jdbcclient.JDBCConnectOptions
import io.vertx.jdbcclient.JDBCPool
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import org.slf4j.Logger
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.sql.BatchUpdateException
import kotlin.system.exitProcess

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
class DatabaseManager(
    private val vertx: Vertx,
    private val logger: Logger,
    private val databasePlugin: DatabasePlugin
) {
    private val pluginConfigManager by lazy {
        databasePlugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<DatabaseConfig>
    }

    private lateinit var pool: Pool

    private val tables = mutableMapOf<ParsekPlugin, MutableList<Dao<*>>>()
    private val migrations = mutableMapOf<ParsekPlugin, MutableList<DatabaseMigration>>()

    private val schemeVersionDaoImpl = SchemeVersionDaoImpl()

    fun getTablePrefix(): String = pluginConfigManager.config.prefix

    fun getConnectionPool(): Pool {
        if (!::pool.isInitialized) {
            val databaseConfig = pluginConfigManager.config

            val host = databaseConfig.host
            val name = databaseConfig.name
            val username = databaseConfig.username
            val password = databaseConfig.password

            try {
                Class.forName("com.clickhouse.jdbc.ClickHouseDriver")
            } catch (e: Exception) {
                logger.error("Failed to load ClickHouse driver", e)
            }

            val options = JDBCConnectOptions()
                .setJdbcUrl("jdbc:clickhouse:$host/$name")
                .setUser(username)
                .setPassword(password)

            val poolOptions = PoolOptions()
                .setShared(true)
                .setName("parsek")
                .setMaxSize(100)

            pool = JDBCPool.pool(vertx, options, poolOptions)
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

    private suspend fun initSchemeVersion(plugin: ParsekPlugin, jdbcPool: Pool) {
        val lastSchemeVersion = schemeVersionDaoImpl.getLastSchemeVersion(plugin.pluginId, jdbcPool)

        val latestMigration = getLatestMigration(plugin)

        if (lastSchemeVersion != null) {
            return
        }

        if (latestMigration == null) {
            schemeVersionDaoImpl.add(
                SchemeVersion(
                    pluginId = plugin.pluginId,
                    version = 1,
                    extra = "Init ${plugin.pluginId}"
                ),
                jdbcPool
            )

            return
        }

        schemeVersionDaoImpl.add(
            SchemeVersion(
                pluginId = plugin.pluginId,
                version = latestMigration.SCHEME_VERSION,
                extra = latestMigration.SCHEME_VERSION_INFO
            ),
            jdbcPool
        )
    }

    private suspend fun initPluginDB(plugin: ParsekPlugin, jdbcPool: Pool) {
        initSchemeVersion(plugin, jdbcPool)

        initTables(plugin)

        logger.info("\"${plugin.pluginId}\"'s database has been initialized")
    }

    suspend fun initialize(
        plugin: ParsekPlugin,
    ) {
        if (this.tables[plugin] == null) {
            this.tables[plugin] = mutableListOf()
        }

        if (this.migrations[plugin] == null) {
            this.migrations[plugin] = mutableListOf()
        }

        this.tables[plugin] = plugin.pluginBeanContext.getBeansOfType(Dao::class.java).values.map { it as Dao }.toMutableList()
        this.migrations[plugin] = plugin.pluginBeanContext.getBeansOfType(DatabaseMigration::class.java).values.map { it as DatabaseMigration }.toMutableList()

        val jdbcPool: Pool

        try {
            jdbcPool = getConnectionPool()
        } catch (e: Exception) {
            e.printStackTrace()
            logger.info("Connection to database failed! Shutting down...")

            exitProcess(1)
        }

        val lastSchemeVersion: SchemeVersion?

        try {
            lastSchemeVersion = schemeVersionDaoImpl.getLastSchemeVersion(plugin.pluginId, jdbcPool)
        } catch (_: BatchUpdateException) {
            try {
                if (plugin is DatabasePlugin) {
                    logger.warn("First time installing scheme version (first time app installation)")

                    schemeVersionDaoImpl.init(jdbcPool, plugin)
                }

                initPluginDB(plugin, jdbcPool)

                return
            } catch (e: Exception) {
                logger.error(e.message)
                logger.error("Database Error: Could not install plugin \"${plugin.pluginId}\". Shutting down...")

                exitProcess(1)
            }
        }

        if (lastSchemeVersion == null) {
            initPluginDB(plugin, jdbcPool)

            return
        }

        checkMigration(plugin, jdbcPool, lastSchemeVersion)
    }

    suspend fun migrateNewPluginId(exPluginId: String, plugin: ParsekPlugin) {
        try {
            schemeVersionDaoImpl.renamePluginId(exPluginId, plugin.pluginId, getConnectionPool())
        } catch (e: Exception) {
            if (plugin !is DatabasePlugin) {
                logger.error(e.message)
                exitProcess(1)
            }
        }
    }

    internal fun getLatestMigration(plugin: ParsekPlugin) = migrations[plugin]?.maxByOrNull { it.SCHEME_VERSION }

    suspend fun checkMigration(plugin: ParsekPlugin, jdbcPool: Pool, lastSchemeVersion: SchemeVersion?) {
        logger.info("Checking available database migrations for \"${plugin.pluginId}\"")

        val databaseVersion = lastSchemeVersion?.version ?: 0

        if (databaseVersion == 0) {
            logger.error("Database Error: Database scheme is not correct, please reinstall platform")

            return
        }

        migrate(plugin, jdbcPool, databaseVersion)
    }

    private suspend fun updateSchemeVersion(version: Int, info: String, plugin: ParsekPlugin, jdbcPool: Pool) {
        schemeVersionDaoImpl.add(
            SchemeVersion(
                pluginId = plugin.pluginId,
                version = version,
                extra = info
            ),
            jdbcPool
        )
    }

    private suspend fun migrate(plugin: ParsekPlugin, jdbcPool: Pool, databaseVersion: Int) {
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