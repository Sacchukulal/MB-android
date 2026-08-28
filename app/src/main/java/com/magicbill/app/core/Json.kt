package com.magicbill.app.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One JSON configuration for the whole app. Unknown keys are the other side's business. */
val MbJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

// Readers that never throw on a missing or null key — the shape is the other side's, and a
// screen must not crash because a column was added.

fun JsonObject.str(key: String): String = strOrNull(key) ?: ""
fun JsonObject.strOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
fun JsonObject.long(key: String): Long = longOrNull(key) ?: 0L
fun JsonObject.longOrNull(key: String): Long? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.longOrNull ?: p.doubleOrNull?.toLong() ?: p.contentOrNull?.toLongOrNull()
}
fun JsonObject.int(key: String): Int = intOrNull(key) ?: 0
fun JsonObject.intOrNull(key: String): Int? = longOrNull(key)?.toInt()
fun JsonObject.bool(key: String): Boolean = boolOrNull(key) ?: false
fun JsonObject.boolOrNull(key: String): Boolean? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.booleanOrNull ?: p.contentOrNull?.let { it == "true" || it == "1" }
}
fun JsonObject.obj(key: String): JsonObject? = (this[key] as? JsonObject)
fun JsonObject.arr(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())
/** The raw JSON text of a nested value, kept as-is for a detail screen to parse later. */
fun JsonObject.raw(key: String): String = this[key]?.takeIf { it !is JsonNull }?.toString() ?: "null"
fun JsonObject.strings(key: String): List<String> = arr(key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
fun JsonElement.asArrayOrEmpty(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())
fun JsonElement.objects(): List<JsonObject> = asArrayOrEmpty().mapNotNull { it as? JsonObject }
fun JsonElement.primitiveText(): String? = (this as? JsonPrimitive)?.contentOrNull

fun parseJsonOrNull(text: String): JsonElement? = try {
    MbJson.parseToJsonElement(text)
} catch (e: Exception) {
    null
}

// Unused-import guards for the compiler: these are handy on call sites.
@Suppress("unused")
private fun keep(e: JsonElement) = e.jsonPrimitive to e.jsonObject to e.jsonArray
