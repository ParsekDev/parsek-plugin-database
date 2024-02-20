package co.statu.rule.database

import co.statu.parsek.api.config.PluginConfig

class DatabaseConfig(
    val host: String = "//localhost",
    val name: String = "default",
    val username: String = "default",
    val password: String = "",
    val prefix: String = "rule_"
) : PluginConfig()