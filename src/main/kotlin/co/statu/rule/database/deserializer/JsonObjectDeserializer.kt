package co.statu.rule.database.deserializer

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import io.vertx.core.json.JsonObject
import java.lang.reflect.Type

class JsonObjectDeserializer : JsonDeserializer<JsonObject> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): JsonObject {
        val jsonAsString = json.asString

        return JsonObject(jsonAsString)
    }
}