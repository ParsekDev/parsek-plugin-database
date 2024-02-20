package co.statu.rule.database.model

import co.statu.rule.database.DBEntity
import java.util.*

data class SchemeVersion(
    val pluginId: String,
    val `when`: Date = Date(),
    val version: Int,
    val extra: String? = null
) : DBEntity()