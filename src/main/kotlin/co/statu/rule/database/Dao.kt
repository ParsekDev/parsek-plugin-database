package co.statu.rule.database

import co.statu.parsek.api.ParsekPlugin
import co.statu.parsek.util.TextUtil.convertToSnakeCase
import co.statu.rule.database.DBEntity.Companion.gson
import co.statu.rule.database.DatabasePlugin.Companion.databaseManager
import io.vertx.jdbcclient.JDBCPool
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor

abstract class Dao<T : DBEntity>(private val entityClass: KClass<T>) {
    companion object {
        inline fun <reified T : Dao<*>> get(tableList: List<Dao<*>>): T = tableList.find { it is T } as T
    }

    fun Row.toEntity(): T = gson.fromJson(this.toJson().encode(), entityClass.java)

    fun RowSet<Row>.toEntities() = this.map { it.toEntity() }

    val tableName = entityClass.simpleName!!.convertToSnakeCase().lowercase()

    val fields by lazy {
        entityClass.primaryConstructor!!.parameters
            .filterNot { it.name in entityClass.companionObject?.declaredMemberProperties.orEmpty().map { it.name } }
            .map { it.name!! }
    }

    fun List<String>.toTableQuery() = this.joinToString(", ") { "`$it`" }

    abstract suspend fun init(jdbcPool: JDBCPool, plugin: ParsekPlugin)

    fun getTablePrefix(): String = databaseManager.getTablePrefix()

    suspend fun count(
        jdbcPool: JDBCPool
    ): Long {
        val query =
            "SELECT COUNT(*) FROM `${getTablePrefix() + tableName}`"

        val rows: RowSet<Row> = jdbcPool
            .preparedQuery(query)
            .execute()
            .await()

        return rows.toList()[0].getLong(0)
    }

    suspend fun byId(
        id: UUID,
        jdbcPool: JDBCPool
    ): T? {
        val query =
            "SELECT ${fields.toTableQuery()} FROM `${getTablePrefix() + tableName}` WHERE `id` = ?"

        val rows: RowSet<Row> = jdbcPool
            .preparedQuery(query)
            .execute(Tuple.of(id))
            .await()

        if (rows.size() == 0) {
            return null
        }

        val row = rows.toList()[0]

        return row.toEntity()
    }
}