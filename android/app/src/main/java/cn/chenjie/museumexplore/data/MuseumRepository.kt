package cn.chenjie.museumexplore.data

import android.content.Context
import android.content.SharedPreferences
import cn.chenjie.museumexplore.model.MuseumInfo
import cn.chenjie.museumexplore.model.Relic
import cn.chenjie.museumexplore.model.CitySummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MuseumRepository(private val context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("museum_records", Context.MODE_PRIVATE)
    private val relics: List<Relic> by lazy { readRelics() }
    private val museumInfo: Map<String, MuseumInfo> by lazy { readMuseums() }

    fun relics(): List<Relic> = relics
    fun museums(): Map<String, MuseumInfo> = museumInfo
    fun isCollected(id: Int): Boolean = preferences.getBoolean("relic:$id", false)
    fun isVisited(name: String): Boolean = preferences.getBoolean("visited:$name", false)
    fun note(id: Int): String = preferences.getString("note:$id", "").orEmpty()
    fun lastPhoto(): String = preferences.getString("lastPhoto", "").orEmpty()
    fun mapKey(): String = preferences.getString("amap_key", "").orEmpty()

    fun setCollected(id: Int, value: Boolean) = preferences.edit().putBoolean("relic:$id", value).apply()
    fun setVisited(name: String, value: Boolean) = preferences.edit().putBoolean("visited:$name", value).apply()
    fun saveNote(id: Int, value: String) = preferences.edit().putString("note:$id", value).apply()
    fun saveMapKey(value: String) = preferences.edit().putString("amap_key", value).apply()
    fun saveLastPhoto(value: String) = preferences.edit().putString("lastPhoto", value).apply()

    fun collectedCount(): Int = relics.count { isCollected(it.id) }
    fun museumNames(city: String): List<String> = relics
        .filter { it.city == city }
        .flatMap { splitMuseumNames(it.museum) }
        .distinct()
        .sortedWith(compareByDescending<String> { relicCount(it) }.thenBy { it })

    fun cities(): List<CitySummary> = relics.groupBy { it.city }.map { (city, cityRelics) ->
        val museums = museumNames(city)
        CitySummary(city, museums.size, cityRelics.size, museums.count(::isVisited))
    }.sortedByDescending { it.relicCount }

    fun relicsForMuseum(city: String, museum: String): List<Relic> = relics.filter {
        it.city == city && splitMuseumNames(it.museum).contains(museum)
    }

    fun relicCount(museum: String): Int = relics.count { splitMuseumNames(it.museum).contains(museum) }
    fun relicCountForLocations(raw: String): Int = splitMuseumNames(raw).sumOf(::relicCount)

    fun exportBackup(output: OutputStream) {
        val records = JSONObject()
        preferences.all.forEach { (key, value) -> records.put(key, value.toString()) }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("records.json"))
            zip.write(records.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write("博物馆探索本地备份".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun importBackup(input: InputStream) {
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry?
            while (zip.nextEntry.also { entry = it } != null) {
                if (entry?.name != "records.json") continue
                val records = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                val editor = preferences.edit()
                records.keys().forEach { key -> editor.putString(key, records.optString(key)) }
                editor.apply()
            }
        }
    }

    private fun readRelics(): List<Relic> = runCatching {
        val json = JSONObject(readAsset("195件永禁文物.json"))
        val items = json.getJSONArray("items")
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(Relic(
                    id = item.optInt("id"),
                    name = item.optString("name"),
                    era = item.optString("era"),
                    category = item.optString("cat"),
                    museum = item.optString("museum"),
                    city = item.optString("city", "未分城市"),
                    reason = item.optString("reason", "暂无说明"),
                    story = item.optString("story", "暂无完整故事"),
                    image = item.optString("image").takeIf { it.isNotBlank() },
                    museumUrl = item.optString("museumUrl").takeIf { it.isNotBlank() },
                    museumWechat = item.optString("museumWechat").takeIf { it.isNotBlank() },
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun readMuseums(): Map<String, MuseumInfo> = runCatching {
        val json = JSONObject(readAsset("museums.json"))
        val items = json.getJSONArray("museums")
        buildMap {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val museum = MuseumInfo(
                    name = item.optString("name"),
                    city = item.optString("city"),
                    officialUrl = item.optString("officialUrl").takeIf { it.isNotBlank() },
                    wechat = item.optString("wechat").takeIf { it.isNotBlank() },
                    address = item.optString("address").takeIf { it.isNotBlank() && it != "null" },
                    intro = item.optString("intro").takeIf { it.isNotBlank() && it != "null" },
                    openingHours = item.optString("openingHours").takeIf { it.isNotBlank() && it != "null" },
                    reservation = item.optString("reservation").takeIf { it.isNotBlank() && it != "null" },
                )
                put(museum.name, museum)
            }
        }
    }.getOrDefault(emptyMap())

    private fun readAsset(name: String): String = context.assets.open(name).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun splitMuseumNames(raw: String): List<String> = raw.split(Regex("[、,，;/；]"))
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .ifEmpty { listOf(raw) }
}
