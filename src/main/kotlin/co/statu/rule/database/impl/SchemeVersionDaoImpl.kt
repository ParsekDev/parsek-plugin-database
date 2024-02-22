package co.statu.rule.database.impl

import co.statu.parsek.api.ParsekPlugin
import co.statu.rule.database.dao.SchemeVersionDao
import co.statu.rule.database.model.SchemeVersion
import io.vertx.jdbcclient.JDBCPool
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple

class SchemeVersionDaoImpl : SchemeVersionDao() {

    override suspend fun init(jdbcPool: JDBCPool, plugin: ParsekPlugin) {
        jdbcPool
            .query(
                """
                        CREATE TABLE `${getTablePrefix() + tableName}` (
                            `pluginId` String NOT NULL,
                            `when` Date NOT NULL,
                            `version` Int32 NOT NULL,
                            `extra` String
                        ) ENGINE = MergeTree() order by `when`;
                        """
            )
            .execute()
            .await()
    }

    override suspend fun add(
        schemeVersion: SchemeVersion,
        jdbcPool: JDBCPool
    ) {
        jdbcPool
            .preparedQuery("INSERT INTO `${getTablePrefix() + tableName}` (`pluginId`, `when`, `version`, `extra`) VALUES (?, now(), ?, ?)")
            .execute(
                Tuple.of(
                    schemeVersion.pluginId,
                    schemeVersion.version,
                    schemeVersion.extra
                )
            )
            .await()
    }

    override suspend fun getLastSchemeVersion(
        pluginId: String,
        jdbcPool: JDBCPool
    ): SchemeVersion? {
        val query =
            "SELECT * FROM `${getTablePrefix() + tableName}` WHERE `pluginId` = ?  ORDER BY `version` DESC LIMIT 1"

        val rows: RowSet<Row> = jdbcPool
            .preparedQuery(query)
            .execute(Tuple.of(pluginId))
            .await()

        if (rows.size() == 0) {
            return null
        }

        val row = rows.toList()[0]

        return row.toEntity()
    }

    override suspend fun renamePluginId(
        exPluginId: String,
        newPluginId: String,
        jdbcPool: JDBCPool
    ) {
        val query =
            "ALTER TABLE `${getTablePrefix() + tableName}` UPDATE `pluginId` = ? WHERE `pluginId` = ?;"

        jdbcPool
            .preparedQuery(query)
            .execute(Tuple.of(newPluginId, exPluginId))
            .await()
    }
}