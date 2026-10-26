package cn.chenjie.museumexplore.data

import cn.chenjie.museumexplore.model.UserRelic
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户自建国宝的 JSON 编解码。
 *
 * 与 [UserMuseumsCodec] 同一套容错规则：单条坏数据只跳过那一条，不能让整份清单读不出来。
 * 自建国宝的 id 一律为负数，读回来若发现是正数，说明与 195 件名单撞号，直接丢弃。
 */
object UserRelicsCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(relics: List<UserRelic>): String {
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put(
                "relics",
                JSONArray().apply {
                    relics.forEach { relic ->
                        put(
                            JSONObject()
                                .put("id", relic.id)
                                .put("name", relic.name)
                                .put("museum", relic.museum)
                                .put("city", relic.city)
                                .put("era", relic.era ?: JSONObject.NULL)
                                .put("category", relic.category ?: JSONObject.NULL)
                                .put("reason", relic.reason ?: JSONObject.NULL)
                                .put("story", relic.story ?: JSONObject.NULL)
                                .put("photo", relic.photo ?: JSONObject.NULL)
                                .put("museumUrl", relic.museumUrl ?: JSONObject.NULL)
                                .put("createdAt", relic.createdAt),
                        )
                    }
                },
            )
        return root.toString()
    }

    fun decode(raw: String?): List<UserRelic> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val items = root.optJSONArray("relics") ?: return emptyList()
        val result = mutableListOf<UserRelic>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val relic = fromJson(item) ?: continue
            if (result.any { it.id == relic.id }) continue
            result += relic
        }
        return result
    }

    private fun fromJson(item: JSONObject): UserRelic? {
        val id = item.optIntOrNull("id") ?: return null
        if (id >= 0) return null
        val name = TextCleaner.clean(item.optString("name")).trim()
        if (name.isBlank()) return null
        return UserRelic(
            id = id,
            name = name,
            museum = TextCleaner.clean(item.optString("museum")).trim(),
            city = TextCleaner.clean(item.optString("city")).trim(),
            era = item.optionalString("era"),
            category = item.optionalString("category"),
            reason = item.optionalString("reason"),
            story = item.optionalString("story"),
            photo = item.optionalString("photo"),
            museumUrl = item.optionalString("museumUrl"),
            createdAt = item.optLong("createdAt", 0L),
        )
    }

    private fun JSONObject.optionalString(key: String): String? = TextCleaner.clean(optString(key)).trim()
        .takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}
