package co.statu.rule.database

import io.vertx.jdbcclient.JDBCPool

abstract class DatabaseMigration {
    abstract val handlers: List<suspend (jdbcPool: JDBCPool, tablePrefix: String) -> Unit>

    abstract val FROM_SCHEME_VERSION: Int
    abstract val SCHEME_VERSION: Int
    abstract val SCHEME_VERSION_INFO: String

    fun isMigratable(version: Int) = version == FROM_SCHEME_VERSION

    suspend fun migrate(jdbcPool: JDBCPool, tablePrefix: String) {
        handlers.forEach {
            it.invoke(jdbcPool, tablePrefix)
        }
    }
}
