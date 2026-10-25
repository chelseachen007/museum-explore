package cn.chenjie.museumexplore.data

import cn.chenjie.museumexplore.model.UserMuseum
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户自建博物馆的 JSON 编解码。
 *
 * 解析必须是容错的：单条坏数据只跳过那一条，不能让整份自建清单读不出来。
 * 名字为空或为 null 字面量的条目直接丢弃，否则会污染按馆名索引的整条链路。
 */
object UserMuseumsCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(museums: List<UserMuseum>): String {
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put(
                "museums",
                JSONArray().apply {
                    museums.forEach { museum ->
                        put(
                            JSONObject()
                                .put("name", museum.name)
                                .put("city", museum.city)
                                .put("officialUrl", museum.officialUrl ?: JSONObject.NULL)
                                .put("address", museum.address ?: JSONObject.NULL)
                                .put("notes", museum.notes ?: JSONObject.NULL)
                                .put("createdAt", museum.createdAt)
                                .put("mergedInto", museum.mergedInto ?: JSONObject.NULL),
                        )
                    }
                },
            )
        return root.toString()
    }

    fun decode(raw: String?): List<UserMuseum> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val items = root.optJSONArray("museums") ?: return emptyList()
        val result = mutableListOf<UserMuseum>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val museum = fromJson(item) ?: continue
            if (result.any { it.name == museum.name }) continue
            result += museum
        }
        return result
    }

    private fun fromJson(item: JSONObject): UserMuseum? {
        val name = TextCleaner.clean(item.optString("name")).trim()
        if (name.isBlank()) return null
        return UserMuseum(
            name = name,
            city = TextCleaner.clean(item.optString("city")).trim(),
            officialUrl = item.optionalString("officialUrl"),
            address = item.optionalString("address"),
            notes = item.optionalString("notes"),
            createdAt = item.optLong("createdAt", 0L),
            mergedInto = item.optionalString("mergedInto"),
        )
    }

    private fun JSONObject.optionalString(key: String): String? = TextCleaner.clean(optString(key)).trim()
        .takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}
