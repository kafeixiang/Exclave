package io.nekohasekai.sagernet.ktx

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Return a List<String>? from a JSON array field, or null if field is missing.
 * Matches behavior of other getX helpers used in project (return null when missing).
 */
fun JsonObject.getStringList(name: String): List<String>? {
    val elem = this.get(name) ?: return null
    if (!elem.isJsonArray) return null
    val array = elem.asJsonArray
    val list = ArrayList<String>(array.size())
    for (item: JsonElement in array) {
        if (item.isJsonNull) continue
        if (item.isJsonPrimitive && item.asJsonPrimitive.isString) {
            list.add(item.asString)
        } else {
            // fallback: use JSON text representation for non-string primitives/objects
            list.add(item.toString())
        }
    }
    return list
}