package co.statu.rule.database.api

import co.statu.rule.database.Dao
import co.statu.rule.database.DatabaseMigration

interface DatabaseHelper {
    val tables: List<Dao<*>>
    val migrations: List<DatabaseMigration>
}