package co.statu.rule.database

import co.statu.parsek.PluginEventManager
import co.statu.rule.database.deserializer.JsonObjectDeserializer
import co.statu.rule.database.event.DatabaseEventListener
import com.google.gson.GsonBuilder
import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import kotlin.reflect.KClass

abstract class DBEntity {
    companion object {
        val gson by lazy {
            val gsonBuilder = GsonBuilder()

            gsonBuilder.registerTypeAdapter(JsonObject::class.java, JsonObjectDeserializer())

            val databaseEventHandlers = PluginEventManager.getEventListeners<DatabaseEventListener>()

            databaseEventHandlers.forEach { it.onGsonBuild(gsonBuilder) }

            gsonBuilder
                .create()
        }

        inline fun <reified T : DBEntity> KClass<T>.from(row: Row): T =
            gson.fromJson(row.toJson().encode(), this.java)

        inline fun <reified T : DBEntity> KClass<T>.from(rowSet: RowSet<Row>) = rowSet.map { this.from(it) }
    }

    fun toJson(): String = gson.toJson(this)
}