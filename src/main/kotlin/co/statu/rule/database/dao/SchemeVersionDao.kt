package co.statu.rule.database.dao

import co.statu.rule.database.Dao
import co.statu.rule.database.model.SchemeVersion
import io.vertx.jdbcclient.JDBCPool

abstract class SchemeVersionDao : Dao<SchemeVersion>(SchemeVersion::class) {
    abstract suspend fun add(
        schemeVersion: SchemeVersion,
        jdbcPool: JDBCPool
    )

    abstract suspend fun getLastSchemeVersion(
        pluginId: String,
        jdbcPool: JDBCPool
    ): SchemeVersion?
}