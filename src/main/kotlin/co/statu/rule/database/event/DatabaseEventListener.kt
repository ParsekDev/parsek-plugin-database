package co.statu.rule.database.event

import co.statu.parsek.api.PluginEvent
import co.statu.rule.database.DatabaseManager
import com.google.gson.GsonBuilder

/**
 * ParsekEventListener is an extension point for listening Parsek related events
 * such as when config manager has been initialized.
 */
interface DatabaseEventListener : PluginEvent {

    suspend fun onReady(databaseManager: DatabaseManager) {}

    fun onGsonBuild(gsonBuilder: GsonBuilder) {}
}