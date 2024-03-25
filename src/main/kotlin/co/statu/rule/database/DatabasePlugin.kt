package co.statu.rule.database

import co.statu.parsek.api.ParsekPlugin

class DatabasePlugin : ParsekPlugin() {
    companion object {
        internal lateinit var databaseManager: DatabaseManager
    }
}
