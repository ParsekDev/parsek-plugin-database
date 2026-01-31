package co.statu.rule.database.dao

import co.statu.rule.database.Dao
import co.statu.rule.database.model.SchemeVersion
import io.vertx.jdbcclient.JDBCPool
import io.vertx.sqlclient.Pool

abstract class SchemeVersionDao : Dao<SchemeVersion>(SchemeVersion::class) {
    abstract suspend fun add(
        schemeVersion: SchemeVersion,
        jdbcPool: Pool
    )

    abstract suspend fun getLastSchemeVersion(
        pluginId: String,
        jdbcPool: Pool
    ): SchemeVersion?

    abstract suspend fun renamePluginId(
        exPluginId: String,
        newPluginId: String,
        jdbcPool: Pool
    )
}