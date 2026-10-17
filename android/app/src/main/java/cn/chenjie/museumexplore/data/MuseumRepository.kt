package cn.chenjie.museumexplore.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import cn.chenjie.museumexplore.model.CitySummary
import cn.chenjie.museumexplore.model.MuseumInfo
import cn.chenjie.museumexplore.model.MuseumRecord
import cn.chenjie.museumexplore.model.Relic
import cn.chenjie.museumexplore.model.VisitPhoto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MuseumRepository(private val context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("museum_records", Context.MODE_PRIVATE)
    private val relics: List<Relic> by lazy { readRelics() }
    private val museumInfo: Map<String, MuseumInfo> by lazy { readMuseums() }
    private val photosDir: File
        get() = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }

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

    fun museumRecord(museum: String): MuseumRecord {
        val raw = preferences.getString(recordKey(museum), "").orEmpty()
        if (raw.isBlank()) return MuseumRecord()
        return runCatching { parseRecord(JSONObject(raw)) }.getOrDefault(MuseumRecord())
    }

    fun saveMuseumRecord(museum: String, note: String, photos: List<VisitPhoto>) {
        val previous = museumRecord(museum)
        val photosJson = JSONArray()
        photos.forEach { photo ->
            photosJson.put(
                JSONObject()
                    .put("path", File(photo.path).name)
                    .put("relicId", photo.relicId ?: JSONObject.NULL),
            )
        }
        val record = JSONObject()
            .put("note", note.trim())
            .put("photos", photosJson)
            .put("updatedAt", System.currentTimeMillis())
        preferences.edit()
            .putString(recordKey(museum), record.toString())
            .putBoolean("visited:$museum", true)
            .apply()

        val retained = photos.mapTo(mutableSetOf()) { it.path }
        previous.photos.filterNot { it.path in retained }.forEach { deletePhoto(it.path) }
    }

    fun photosForRelic(relicId: Int): List<VisitPhoto> = preferences.all.keys
        .asSequence()
        .filter { it.startsWith(RECORD_PREFIX) }
        .flatMap { key -> museumRecordForKey(key).photos.asSequence() }
        .filter { it.relicId == relicId }
        .distinctBy { it.path }
        .toList()

    fun primaryMuseum(relic: Relic): String = splitMuseumNames(relic.museum).firstOrNull().orEmpty()

    fun createCameraPhoto(): String {
        val file = File(photosDir, "camera_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        file.parentFile?.mkdirs()
        file.createNewFile()
        return file.absolutePath
    }

    fun cameraUri(path: String): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(path),
    )

    fun importPhoto(uri: Uri): String? = runCatching {
        val mime = context.contentResolver.getType(uri)
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?.lowercase()
            ?.takeIf { it in PHOTO_EXTENSIONS }
            ?: uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()?.takeIf { it in PHOTO_EXTENSIONS }
            ?: "jpg"
        val file = File(photosDir, "photo_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use(input::copyTo)
        } ?: return@runCatching null
        file.takeIf { it.length() > 0L }?.absolutePath
    }.getOrNull()

    fun photoFile(path: String): File = File(path)

    fun deletePhoto(path: String) {
        val file = File(path)
        val root = photosDir.canonicalFile
        val target = runCatching { file.canonicalFile }.getOrNull() ?: return
        if (target.parentFile == root) target.delete()
    }

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
            photosDir.listFiles()?.filter(File::isFile)?.forEach { photo ->
                zip.putNextEntry(ZipEntry("photos/${photo.name}"))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write("博物馆探索本地备份：包含打卡、感想、照片与设置。".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun importBackup(input: InputStream) {
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry?
            while (zip.nextEntry.also { entry = it } != null) {
                when {
                    entry?.name == "records.json" -> {
                        val records = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        val editor = preferences.edit()
                        records.keys().forEach { key ->
                            val value = records.optString(key)
                            if (key.startsWith("relic:") || key.startsWith("visited:")) {
                                editor.putBoolean(key, value.toBoolean())
                            } else {
                                editor.putString(key, value)
                            }
                        }
                        editor.apply()
                    }
                    entry?.name?.startsWith("photos/") == true -> {
                        val name = entry?.name.orEmpty().removePrefix("photos/").substringAfterLast('/')
                        if (name.isNotBlank()) {
                            File(photosDir, name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun museumRecordForKey(key: String): MuseumRecord = runCatching {
        val raw = preferences.getString(key, "").orEmpty()
        if (raw.isBlank()) MuseumRecord() else parseRecord(JSONObject(raw))
    }.getOrDefault(MuseumRecord())

    private fun parseRecord(json: JSONObject): MuseumRecord {
        val photosJson = json.optJSONArray("photos") ?: JSONArray()
        val photos = buildList {
            for (index in 0 until photosJson.length()) {
                val item = photosJson.optJSONObject(index) ?: continue
                val storedPath = item.optString("path").trim()
                if (storedPath.isBlank()) continue
                val path = File(photosDir, File(storedPath).name).absolutePath
                add(VisitPhoto(path, item.optInt("relicId", 0).takeIf { it > 0 }))
            }
        }
        return MuseumRecord(
            note = json.optString("note", ""),
            photos = photos,
            updatedAt = json.optLong("updatedAt", 0L),
        )
    }

    private fun readRelics(): List<Relic> = runCatching {
        val localImages = runCatching {
            context.assets.list("relic_images").orEmpty()
                .mapNotNull { name ->
                    val id = name.substringBefore('.').toIntOrNull() ?: return@mapNotNull null
                    id to "relic_images/$name"
                }
                .toMap()
        }.getOrDefault(emptyMap())
        val json = JSONObject(readAsset("195件永禁文物.json"))
        val items = json.getJSONArray("items")
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val id = item.optInt("id")
                add(
                    Relic(
                        id = id,
                        name = cleanDisplay(item.optString("name")),
                        era = cleanDisplay(item.optString("era")),
                        category = cleanDisplay(item.optString("cat")),
                        museum = cleanDisplay(item.optString("museum")),
                        city = cleanDisplay(item.optString("city", "未分城市")),
                        reason = cleanDisplay(item.optString("reason", "暂无说明")),
                        story = cleanDisplay(item.optString("story", "暂无完整故事")),
                        image = item.optionalString("image"),
                        localImage = localImages[id],
                        museumUrl = item.optionalString("museumUrl"),
                        museumWechat = cleanDisplay(item.optString("museumWechat")).takeIf { it.isNotBlank() },
                    ),
                )
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
                    name = cleanDisplay(item.optString("name")),
                    city = cleanDisplay(item.optString("city")),
                    officialUrl = item.optionalString("officialUrl"),
                    wechat = cleanDisplay(item.optString("wechat")).takeIf { it.isNotBlank() },
                    address = cleanDisplay(item.optString("address")).takeIf { it.isNotBlank() },
                    intro = cleanDisplay(item.optString("intro")).takeIf { it.isNotBlank() },
                    openingHours = cleanDisplay(item.optString("openingHours")).takeIf { it.isNotBlank() },
                    reservation = cleanDisplay(item.optString("reservation")).takeIf { it.isNotBlank() },
                    wikiUrl = item.optionalString("wikiUrl"),
                    miniProgram = cleanDisplay(item.optString("miniProgram")).takeIf { it.isNotBlank() },
                )
                put(museum.name, museum)
            }
        }
    }.getOrDefault(emptyMap())

    private fun readAsset(name: String): String = context.assets.open(name).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun cleanDisplay(raw: String?): String {
        if (raw == null || raw.equals("null", ignoreCase = true)) return ""
        return TextCleaner.clean(raw)
    }
    private fun JSONObject.optionalString(key: String): String? = optString(key).trim()
        .takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    private fun recordKey(museum: String): String = "$RECORD_PREFIX$museum"
    private fun splitMuseumNames(raw: String): List<String> = raw.split(Regex("[、,，;/；]"))
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .ifEmpty { listOf(raw) }

    private companion object {
        const val RECORD_PREFIX = "museum_record:"
        const val PHOTO_DIR = "visit_photos"
        val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
    }
}
