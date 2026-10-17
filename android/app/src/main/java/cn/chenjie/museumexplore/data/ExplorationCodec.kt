package cn.chenjie.museumexplore.data

import cn.chenjie.museumexplore.model.DatePrecision
import cn.chenjie.museumexplore.model.EXPLORATION_SCHEMA_VERSION
import cn.chenjie.museumexplore.model.ExplorationState
import cn.chenjie.museumexplore.model.RecordSource
import cn.chenjie.museumexplore.model.Sighting
import cn.chenjie.museumexplore.model.Visit
import cn.chenjie.museumexplore.model.VisitPhoto
import cn.chenjie.museumexplore.model.WishEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * 探索状态 v2 的 JSON 编解码。
 *
 * 解析必须是容错的：任何一条坏数据都不能让整份用户记录读不出来。
 * 照片这里只保存文件名（和 v0.5.1 的 museum_record 一致），绝对路径由 Repository 还原，
 * 这样换设备导入备份后照片仍能找到。
 */
object ExplorationCodec {
    fun encode(state: ExplorationState): String {
        val root = JSONObject()
            .put("schemaVersion", EXPLORATION_SCHEMA_VERSION)
            .put("wishedMuseums", encodeWishes(state.wishedMuseums))
            .put("wishedRelics", encodeWishes(state.wishedRelics))
            .put("visits", JSONArray().apply {
                state.visits.forEach { visit ->
                    put(
                        JSONObject()
                            .put("id", visit.id)
                            .put("museumId", visit.museumId)
                            .put("city", visit.city)
                            .put("startedAt", visit.startedAt ?: JSONObject.NULL)
                            .put("datePrecision", visit.datePrecision.name.lowercase())
                            .put("source", visit.source.name.lowercase())
                            .put("pendingConfirmation", visit.pendingConfirmation)
                            .put("note", visit.note)
                            .put("photos", JSONArray().apply {
                                visit.photos.forEach { photo ->
                                    put(
                                        JSONObject()
                                            .put("path", photo.path)
                                            .put("relicId", photo.relicId ?: JSONObject.NULL),
                                    )
                                }
                            }),
                    )
                }
            })
            .put("sightings", JSONArray().apply {
                state.sightings.forEach { sighting ->
                    put(
                        JSONObject()
                            .put("id", sighting.id)
                            .put("relicId", sighting.relicId)
                            .put("museumId", sighting.museumId ?: JSONObject.NULL)
                            .put("visitId", sighting.visitId ?: JSONObject.NULL)
                            .put("seenAt", sighting.seenAt ?: JSONObject.NULL)
                            .put("source", sighting.source.name.lowercase()),
                    )
                }
            })
        return root.toString()
    }

    fun decode(raw: String?): ExplorationState {
        if (raw.isNullOrBlank()) return ExplorationState()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return ExplorationState()
        return ExplorationState(
            schemaVersion = root.optInt("schemaVersion", EXPLORATION_SCHEMA_VERSION),
            wishedMuseums = decodeWishes(root.optJSONArray("wishedMuseums")),
            wishedRelics = decodeWishes(root.optJSONArray("wishedRelics")),
            visits = decodeVisits(root.optJSONArray("visits")),
            sightings = decodeSightings(root.optJSONArray("sightings")),
        )
    }

    private fun encodeWishes(entries: List<WishEntry>) = JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject().put("target", entry.target).put("createdAt", entry.createdAt))
        }
    }

    private fun decodeWishes(array: JSONArray?): List<WishEntry> {
        if (array == null) return emptyList()
        val result = mutableListOf<WishEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val target = item.optString("target").trim()
            if (target.isEmpty()) continue
            if (result.any { it.target == target }) continue
            result.add(WishEntry(target, item.optLong("createdAt", 0L)))
        }
        return result
    }

    private fun decodeVisits(array: JSONArray?): List<Visit> {
        if (array == null) return emptyList()
        val result = mutableListOf<Visit>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val museumId = item.optString("museumId").trim()
            if (id.isEmpty() || museumId.isEmpty()) continue
            val startedAt = item.optLongOrNull("startedAt")
            val precision = if (startedAt == null) DatePrecision.UNKNOWN else parsePrecision(item.optString("datePrecision"))
            result.add(
                Visit(
                    id = id,
                    museumId = museumId,
                    city = item.optString("city").trim(),
                    startedAt = startedAt,
                    datePrecision = precision,
                    source = parseSource(item.optString("source")),
                    pendingConfirmation = item.optBoolean("pendingConfirmation", false),
                    note = item.optString("note").trim(),
                    photos = decodePhotos(item.optJSONArray("photos")),
                ),
            )
        }
        return result
    }

    private fun decodePhotos(array: JSONArray?): List<VisitPhoto> {
        if (array == null) return emptyList()
        val result = mutableListOf<VisitPhoto>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val path = item.optString("path").trim()
            if (path.isEmpty()) continue
            result.add(VisitPhoto(path, item.optIntOrNull("relicId")?.takeIf { it > 0 }))
        }
        return result
    }

    private fun decodeSightings(array: JSONArray?): List<Sighting> {
        if (array == null) return emptyList()
        val result = mutableListOf<Sighting>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val relicId = item.optIntOrNull("relicId") ?: continue
            if (id.isEmpty() || relicId <= 0) continue
            result.add(
                Sighting(
                    id = id,
                    relicId = relicId,
                    museumId = item.optStringOrNull("museumId"),
                    visitId = item.optStringOrNull("visitId"),
                    seenAt = item.optLongOrNull("seenAt"),
                    source = parseSource(item.optString("source")),
                ),
            )
        }
        return result
    }

    private fun parsePrecision(raw: String): DatePrecision =
        if (raw.trim().lowercase() == "exact") DatePrecision.EXACT else DatePrecision.UNKNOWN

    private fun parseSource(raw: String): RecordSource =
        if (raw.trim().lowercase() == "migration") RecordSource.MIGRATION else RecordSource.MANUAL
}

internal fun JSONObject.optIntOrNull(key: String): Int? {
    if (isNull(key)) return null
    val raw = optString(key).trim()
    if (raw.isEmpty() || raw == "null") return null
    return raw.toIntOrNull()
}

internal fun JSONObject.optLongOrNull(key: String): Long? {
    if (isNull(key)) return null
    val value = opt(key)
    val long = when (value) {
        is Number -> value.toLong()
        else -> optString(key).trim().toLongOrNull()
    } ?: return null
    return long.takeIf { it > 0L }
}

internal fun JSONObject.optStringOrNull(key: String): String? {
    if (isNull(key)) return null
    return optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
}
