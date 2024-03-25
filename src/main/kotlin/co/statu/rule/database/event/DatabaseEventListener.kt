package co.statu.rule.database.event

import co.statu.parsek.api.event.PluginEventListener
import co.statu.rule.database.DatabaseManager
import com.google.gson.GsonBuilder

/**
 * ParsekEventListener is an extension point for listening Parsek related events
 * such as when config manager has been initialized.
 */
interface DatabaseEventListener : PluginEventListener {

    suspend fun onReady(databaseManager: DatabaseManager) {}

    fun onGsonBuild(gsonBuilder: GsonBuilder) {}
}