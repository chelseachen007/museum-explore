package cn.chenjie.museumexplore.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import cn.chenjie.museumexplore.model.CitySummary
import cn.chenjie.museumexplore.model.DatePrecision
import cn.chenjie.museumexplore.model.ExplorationState
import cn.chenjie.museumexplore.model.MuseumCandidate
import cn.chenjie.museumexplore.model.MuseumInfo
import cn.chenjie.museumexplore.model.MuseumRecord
import cn.chenjie.museumexplore.model.OfficialEntry
import cn.chenjie.museumexplore.model.RecordSource
import cn.chenjie.museumexplore.model.Relic
import cn.chenjie.museumexplore.model.Visit
import cn.chenjie.museumexplore.model.VisitPhoto
import cn.chenjie.museumexplore.model.VISIT_ORDER
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MuseumRepository(private val context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("museum_records", Context.MODE_PRIVATE)
    private val relics: List<Relic> by lazy { readRelics() }
    private val museumInfo: Map<String, MuseumInfo> by lazy { readMuseums() }
    private val officialEntryMap: Map<String, OfficialEntry> by lazy { readOfficialEntries() }
    private val photosDir: File
        get() = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }
    private var cachedState: ExplorationState? = null

    fun relics(): List<Relic> = relics
    fun museums(): Map<String, MuseumInfo> = museumInfo
    fun officialEntry(museum: String): OfficialEntry? = officialEntryMap[museum]
    fun note(id: Int): String = preferences.getString("note:$id", "").orEmpty()
    fun lastPhoto(): String = preferences.getString("lastPhoto", "").orEmpty()
    fun mapKey(): String = preferences.getString("amap_key", "").orEmpty()
    fun selectedCity(): String = preferences.getString("selected_city", "").orEmpty()
    fun saveSelectedCity(city: String) = preferences.edit().putString("selected_city", city.trim()).apply()

    // ---------------------------------------------------------------------
    // 探索状态：想去 / 已到访 / 已看到
    // ---------------------------------------------------------------------

    fun exploration(): ExplorationState {
        cachedState?.let { return it }
        val stored = preferences.getString(EXPLORATION_KEY, null)
        val state = if (stored.isNullOrBlank()) {
            migrateLegacyStateIfNeeded()
        } else {
            toState(ExplorationCodec.decode(stored))
        }
        cachedState = state
        return state
    }

    fun isCollected(id: Int): Boolean = exploration().isSeen(id)
    fun isVisited(name: String): Boolean = exploration().isVisited(name)
    fun isWishedMuseum(name: String): Boolean = exploration().isMuseumWished(name)
    fun isWishedRelic(id: Int): Boolean = exploration().isRelicWished(id)

    fun setCollected(id: Int, value: Boolean) = setSeen(id, value)

    /** 手动到访。true 表示「记录今天到访」；已有今天的记录时幂等。 */
    fun setVisited(name: String, value: Boolean) {
        val state = exploration()
        val next = if (value) {
            val today = System.currentTimeMillis()
            if (state.visitsForMuseum(name).any { it.hasExactDate && isSameDay(it.startedAt!!, today) }) {
                state
            } else {
                ExplorationOps.addVisit(
                    state,
                    museumId = name,
                    city = cityOfMuseum(name),
                    startedAt = today,
                    precision = DatePrecision.EXACT,
                    source = RecordSource.MANUAL,
                    idFactory = ::newId,
                ).first
            }
        } else {
            state.copy(visits = state.visits.filterNot { it.museumId == name })
        }
        saveExploration(next)
    }

    /** 补录一次参观；日期不确定时传 null。 */
    fun addVisit(museum: String, startedAt: Long?): Visit {
        val result = ExplorationOps.addVisit(
            exploration(),
            museumId = museum,
            city = cityOfMuseum(museum),
            startedAt = startedAt,
            precision = if (startedAt == null) DatePrecision.UNKNOWN else DatePrecision.EXACT,
            source = RecordSource.MANUAL,
            idFactory = ::newId,
        )
        // 用户之前只写过感想、还没确认到访时，第一次确认到访把这条记录带进时间线。
        val pending = legacyRecord(museum)
        val next = if (!pending.isEmpty) {
            result.first.copy(
                visits = result.first.visits.map { visit ->
                    if (visit.id == result.second.id) visit.copy(note = pending.note, photos = pending.photos) else visit
                },
            ).also { preferences.edit().remove(recordKey(museum)).apply() }
        } else {
            result.first
        }
        saveExploration(next)
        return result.second
    }

    fun setVisitDate(visitId: String, startedAt: Long): Boolean {
        val state = exploration()
        val visit = state.visit(visitId) ?: return false
        if (state.visits.any { it.id != visitId && it.museumId == visit.museumId && it.hasExactDate && isSameDay(it.startedAt!!, startedAt) }) {
            return false
        }
        if (visit.pendingConfirmation) {
            saveExploration(ExplorationOps.confirmVisit(state, visitId, startedAt))
        } else {
            val updated = visit.copy(startedAt = startedAt, datePrecision = DatePrecision.EXACT)
            saveExploration(state.copy(visits = state.visits.map { if (it.id == visitId) updated else it }))
        }
        return true
    }

    /** 从时间线确认一条「待确认到访」记录；日期可以为空。 */
    fun confirmVisit(visitId: String, startedAt: Long?) {
        saveExploration(ExplorationOps.confirmVisit(exploration(), visitId, startedAt))
    }

    fun updateVisitJournal(visitId: String, note: String, photos: List<VisitPhoto>): Boolean {
        val state = exploration()
        val visit = state.visit(visitId) ?: return false
        val retained = photos.mapTo(mutableSetOf()) { File(it.path).name }
        visit.photos.map { File(it.path).name }
            .filterNot { it in retained }
            .forEach { name -> deletePhoto(name) }
        saveExploration(
            state.copy(visits = state.visits.map {
                if (it.id == visitId) visit.copy(note = note.trim(), photos = photos) else it
            }),
        )
        return true
    }

    fun removeVisit(visitId: String) {
        val removed = ExplorationOps.removeVisit(exploration(), visitId)
        saveExploration(removed.first)
        removed.second?.photos?.forEach { deletePhoto(it.path) }
    }

    fun setSeen(id: Int, value: Boolean, museumId: String? = null) {
        val state = exploration()
        val next = if (value) {
            if (state.isSeen(id)) {
                state
            } else {
                ExplorationOps.recordSighting(
                    state = state,
                    relicId = id,
                    museumId = museumId ?: relics.firstOrNull { it.id == id }?.let(::primaryMuseum),
                    visitId = null,
                    seenAt = System.currentTimeMillis(),
                    source = RecordSource.MANUAL,
                    idFactory = ::newId,
                ).first
            }
        } else {
            ExplorationOps.removeSighting(state, id)
        }
        saveExploration(next)
    }

    fun setMuseumWished(name: String, value: Boolean) {
        val state = exploration()
        saveExploration(
            state.copy(wishedMuseums = ExplorationOps.setWish(state.wishedMuseums, name, value, System.currentTimeMillis())),
        )
    }

    fun setRelicWished(id: Int, value: Boolean) {
        val state = exploration()
        saveExploration(
            state.copy(wishedRelics = ExplorationOps.setWish(state.wishedRelics, id.toString(), value, System.currentTimeMillis())),
        )
    }

    // ---------------------------------------------------------------------
    // 照片与感想（与 visit 共用同一份照片目录）
    // ---------------------------------------------------------------------

    fun museumRecord(museum: String): MuseumRecord {
        val visit = exploration().confirmedVisitsForMuseum(museum).sortedWith(VISIT_ORDER).firstOrNull()
        return if (visit != null && visit.hasJournal) {
            MuseumRecord(visit.note, visit.photos, visit.startedAt ?: 0L)
        } else {
            legacyRecord(museum)
        }
    }

    fun saveMuseumRecord(museum: String, note: String, photos: List<VisitPhoto>) {
        val state = exploration()
        val visit = state.confirmedVisitsForMuseum(museum).sortedWith(VISIT_ORDER).firstOrNull()
        if (visit != null && (visit.hasJournal || note.isNotBlank() || photos.isNotEmpty())) {
            updateVisitJournal(visit.id, note, photos)
        } else {
            // 写感想不等于到访：创建一条「待确认到访」的记录。用户确认后才计入到访和年度足迹。
            val existingPending = state.visitsForMuseum(museum).firstOrNull { it.pendingConfirmation }
            if (existingPending != null) {
                updateVisitJournal(existingPending.id, note, photos)
            } else {
                val draft = ExplorationOps.addVisit(
                    state,
                    museumId = museum,
                    city = cityOfMuseum(museum),
                    startedAt = null,
                    precision = DatePrecision.UNKNOWN,
                    source = RecordSource.MANUAL,
                    idFactory = ::newId,
                ).second
                saveExploration(
                    state.copy(
                        visits = (state.visits + draft).map {
                            if (it.id == draft.id) it.copy(pendingConfirmation = true, note = note.trim(), photos = photos) else it
                        },
                    ),
                )
            }
        }
    }

    fun photosForRelic(relicId: Int): List<VisitPhoto> = exploration().visits
        .flatMap { it.photos.asSequence() }
        .filter { it.relicId == relicId }
        .distinctBy { File(it.path).name }
        .toList()

    /** 有感想或照片、但还没有到访记录的博物馆，时间线里单列「待整理」。 */
    fun unfiledJournals(): List<Pair<String, MuseumRecord>> = preferences.all.keys
        .filter { it.startsWith(RECORD_PREFIX) }
        .map { key -> key.removePrefix(RECORD_PREFIX) to legacyRecord(key.removePrefix(RECORD_PREFIX)) }
        .filter { (museum, record) -> !record.isEmpty && exploration().visitsForMuseum(museum).none { it.pendingConfirmation } }
        .sortedBy { it.first }

    fun saveNote(id: Int, value: String) = preferences.edit().putString("note:$id", value).apply()
    fun saveMapKey(value: String) = preferences.edit().putString("amap_key", value).apply()
    fun saveLastPhoto(value: String) = preferences.edit().putString("lastPhoto", value).apply()

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

    fun photoFile(path: String): File = absolutePhotoFile(path)

    fun deletePhoto(path: String) {
        val target = runCatching { absolutePhotoFile(path).canonicalFile }.getOrNull() ?: return
        if (target.parentFile == photosDir.canonicalFile) target.delete()
    }

    // ---------------------------------------------------------------------
    // 浏览与统计
    // ---------------------------------------------------------------------

    fun collectedCount(): Int = relics.count { isCollected(it.id) }

    fun museumNames(city: String): List<String> = relics
        .filter { it.city == city }
        .flatMap { splitMuseumNames(it.museum) }
        .distinct()
        .sortedWith(compareByDescending<String> { relicCount(it) }.thenBy { it })

    fun allMuseumNames(): List<String> = museumInfo.keys.sorted()

    fun cities(): List<CitySummary> = relics.groupBy { it.city }.map { (city, cityRelics) ->
        val museums = museumNames(city)
        CitySummary(city, museums.size, cityRelics.size, museums.count(::isVisited))
    }.sortedByDescending { it.relicCount }

    fun relicsForMuseum(city: String, museum: String): List<Relic> = relics.filter {
        it.city == city && splitMuseumNames(it.museum).contains(museum)
    }

    fun relicsForMuseum(museum: String): List<Relic> = relics.filter {
        splitMuseumNames(it.museum).contains(museum)
    }

    fun relicCount(museum: String): Int = relics.count { splitMuseumNames(it.museum).contains(museum) }
    fun relicCountForLocations(raw: String): Int = splitMuseumNames(raw).sumOf(::relicCount)
    fun museumInfo(name: String): MuseumInfo? = museumInfo[name]
    fun cityOfMuseum(name: String): String = museumInfo[name]?.city
        ?: relics.firstOrNull { splitMuseumNames(it.museum).contains(name) }?.city
        ?: ""

    /** 下一站候选。开放状态只从已核验文本推断，推断不出就是待确认。 */
    fun nextStopCandidates(city: String, nowMillis: Long = System.currentTimeMillis()): List<MuseumCandidate> {
        val state = exploration()
        return museumNames(city).mapNotNull { museum ->
            val info = museumInfo[museum] ?: return@mapNotNull null
            if (info.openToPublic == false) return@mapNotNull null
            // 高校、研究院这类收藏机构不在「今天去哪」里推荐：它们通常不对公众开放。
            if (info.type == "collection_institution" && info.openToPublic != true) return@mapNotNull null
            val museumRelics = relicsForMuseum(city, museum)
            val plan = OpeningHours.plan(info.openingHours, info.closedDays, nowMillis)
            MuseumCandidate(
                museumId = museum,
                city = city,
                address = info.address,
                relicsTotal = museumRelics.size,
                wishedRelicIds = museumRelics.map { it.id }.filter { state.isRelicWished(it) },
                unseenRelicIds = museumRelics.map { it.id }.filterNot { state.isSeen(it) },
                isWished = state.isMuseumWished(museum),
                visitCount = state.confirmedVisitsForMuseum(museum).size,
                openingStatus = plan.status,
                openingDetail = plan.detail,
            )
        }
    }

    // ---------------------------------------------------------------------
    // 备份
    // ---------------------------------------------------------------------

    fun exportBackup(output: OutputStream) {
        val records = JSONObject()
        preferences.all.forEach { (key, value) -> records.put(key, value.toString()) }
        records.put(EXPLORATION_KEY, ExplorationCodec.encode(exploration()))
        val manifest = JSONObject()
            .put("schemaVersion", 2)
            .put("appVersion", "0.6.0")
            .put("exportedAt", System.currentTimeMillis())
            .put("description", "包含想去、到访、看到、参观时间线、照片、感想和设置。")
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("records.json"))
            zip.write(records.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            photosDir.listFiles()?.filter(File::isFile)?.forEach { photo ->
                zip.putNextEntry(ZipEntry("photos/${photo.name}"))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("README.txt"))
            val readme = "博物馆探索本地备份（v0.6）。包含想去、已到访、已看到、参观时间线、照片、感想和设置。\n" +
                "兼容 v0.5.1 备份导入；新增数据以 schemaVersion=2 标记。\n"
            zip.write(readme.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun importBackup(input: InputStream) {
        val staged = mutableMapOf<String, ByteArray>()
        var importedState: ExplorationState? = null
        var containsState = false
        val previousState = preferences.getString(EXPLORATION_KEY, null)
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry?
            while (zip.nextEntry.also { entry = it } != null) {
                val name = entry?.name.orEmpty()
                if (name == "records.json") {
                    val records = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    val editor = preferences.edit()
                    records.keys().forEach { key ->
                        val raw = records.opt(key)
                        if (key == EXPLORATION_KEY) {
                            importedState = ExplorationCodec.decode(raw?.toString())
                            containsState = true
                        } else if (key.startsWith("relic:") || key.startsWith("visited:")) {
                            editor.putBoolean(key, raw?.toString()?.toBoolean() ?: false)
                        } else if (raw != null) {
                            editor.putString(key, raw.toString())
                        }
                    }
                    editor.apply()
                } else if (name.startsWith("photos/")) {
                    val fileName = name.removePrefix("photos/").substringAfterLast('/')
                    if (fileName.isNotBlank()) staged[fileName] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        staged.forEach { (name, bytes) -> File(photosDir, name).writeBytes(bytes) }
        cachedState = null
        if (containsState && importedState != null) {
            saveExploration(toState(importedState!!))
        } else {
            // v0.5.1 备份里没有 v2 状态，按旧打卡数据重新迁移一次，不沿用本机已有状态。
            // 覆盖前先把本机原状态留一份，导入出错也不会丢数据。
            previousState?.let { writeRecoveryFile("before_import", it) }
            preferences.edit().remove(EXPLORATION_KEY).remove(LEGACY_BACKUP_KEY).apply()
            cachedState = migrateLegacyStateIfNeeded()
        }
    }

    // ---------------------------------------------------------------------
    // 内部
    // ---------------------------------------------------------------------

    private fun saveExploration(state: ExplorationState) {
        cachedState = state
        preferences.edit().putString(EXPLORATION_KEY, ExplorationCodec.encode(toStored(state))).apply()
    }

    /** 内存里照片统一用绝对路径，存储和导出统一用文件名，兼容 v0.5.1 备份。 */
    private fun toState(stored: ExplorationState): ExplorationState = stored.copy(
        visits = stored.visits.map { visit ->
            visit.copy(photos = visit.photos.map { VisitPhoto(absolutePhotoPath(it.path), it.relicId) })
        },
    )

    private fun toStored(state: ExplorationState): ExplorationState = state.copy(
        visits = state.visits.map { visit ->
            visit.copy(photos = visit.photos.map { VisitPhoto(File(it.path).name, it.relicId) })
        },
    )

    private fun legacyRecord(museum: String): MuseumRecord {
        val raw = preferences.getString(recordKey(museum), "").orEmpty()
        if (raw.isBlank()) return MuseumRecord()
        return runCatching { toStateRecord(parseRecord(JSONObject(raw))) }.getOrDefault(MuseumRecord())
    }

    private fun toStateRecord(record: MuseumRecord): MuseumRecord = record.copy(
        photos = record.photos.map { VisitPhoto(absolutePhotoPath(it.path), it.relicId) },
    )

    private fun writeLegacyRecord(museum: String, note: String, photos: List<VisitPhoto>) {
        val photosJson = JSONArray()
        photos.forEach { photo ->
            photosJson.put(JSONObject().put("path", File(photo.path).name).put("relicId", photo.relicId ?: JSONObject.NULL))
        }
        val record = JSONObject()
            .put("note", note.trim())
            .put("photos", photosJson)
            .put("updatedAt", System.currentTimeMillis())
        preferences.edit().putString(recordKey(museum), record.toString()).apply()
        val retained = photos.mapTo(mutableSetOf()) { File(it.path).name }
        legacyRecord(museum).photos.map { File(it.path).name }
            .filterNot { it in retained }
            .forEach { name -> deletePhoto(name) }
    }

    /**
     * 首次启动迁移。迁移前先生成一次备份到本地（不是用户可见的导出文件）。
     * 旧 visited/relic 布尔值只在第一次迁移时读取；已有 v2 状态后永远以 v2 为准。
     */
    private fun migrateLegacyStateIfNeeded(): ExplorationState {
        // 迁移前先落一份本机备份文件（只保留一份），迁移出问题时用户仍有原始记录。
        val hasExploration = preferences.getString(EXPLORATION_KEY, null) != null
        if (!hasExploration && preferences.all.isNotEmpty()) {
            writeRecoveryFile("migration_v051", JSONObject(preferences.all.mapValues { it.value.toString() }).toString(2))
        }
        val visited = preferences.all
            .filterKeys { it.startsWith("visited:") }
            .filterValues { it == true }
            .keys
            .map { it.removePrefix("visited:") }
        val seenIds = preferences.all
            .filterKeys { it.startsWith("relic:") }
            .filterValues { it == true }
            .keys
            .mapNotNull { it.removePrefix("relic:").toIntOrNull() }
        val journals = preferences.all.keys
            .filter { it.startsWith(RECORD_PREFIX) }
            .associate { key ->
                val museum = key.removePrefix(RECORD_PREFIX)
                museum to runCatching { parseRecord(JSONObject(preferences.getString(key, "") ?: "{}")) }.getOrDefault(MuseumRecord())
            }
        val snapshot = LegacySnapshot(
            // 只把用户确认过的到访翻译成参观记录；只有感想、没确认到访的记录留在「待整理」。
            visitedMuseums = visited.distinct(),
            seenRelics = seenIds.map { LegacySighting(it, relics.firstOrNull { relic -> relic.id == it }?.let(::primaryMuseum)) },
            journals = journals,
        )
        val migrated = ExplorationOps.migrate(snapshot, ::cityOfMuseum)
        preferences.edit().putString(EXPLORATION_KEY, ExplorationCodec.encode(migrated)).apply()
        return migrated
    }

    private fun writeRecoveryFile(tag: String, payload: String) {
        runCatching {
            File(context.filesDir, "backups").apply { mkdirs() }
            File(File(context.filesDir, "backups"), "backup_${tag}_${System.currentTimeMillis()}.json").writeText(payload)
        }
    }

    private fun parseRecord(json: JSONObject): MuseumRecord {
        val photosJson = json.optJSONArray("photos") ?: JSONArray()
        val photos = buildList {
            for (index in 0 until photosJson.length()) {
                val item = photosJson.optJSONObject(index) ?: continue
                val storedPath = item.optString("path").trim()
                if (storedPath.isBlank()) continue
                add(VisitPhoto(File(storedPath).name, item.optIntOrNull("relicId")))
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
                    closedDays = cleanDisplay(item.optString("closedDays")).takeIf { it.isNotBlank() },
                    reservation = cleanDisplay(item.optString("reservation")).takeIf { it.isNotBlank() },
                    wikiUrl = item.optionalString("wikiUrl"),
                    miniProgram = cleanDisplay(item.optString("miniProgram")).takeIf { it.isNotBlank() },
                    openToPublic = if (item.has("openToPublic")) item.optBoolean("openToPublic", true) else null,
                    type = item.optionalString("type"),
                    verifiedAt = item.optionalString("verifiedAt"),
                    status = item.optionalString("status"),
                )
                put(museum.name, museum)
            }
        }
    }.getOrDefault(emptyMap())

    private fun readOfficialEntries(): Map<String, OfficialEntry> = runCatching {
        val json = JSONObject(readAsset("official_entries.json"))
        val items = json.getJSONArray("museums")
        buildMap {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val entry = OfficialEntry(
                    museum = cleanDisplay(item.optString("museum")),
                    appName = cleanDisplay(item.optString("appName")),
                    appId = item.optionalString("appId"),
                    landingUrl = item.optionalString("landingUrl"),
                    schemeUrl = item.optionalString("schemeUrl"),
                    officialUrl = item.optionalString("officialUrl") ?: item.optionalString("sourceUrl"),
                    reservationUrl = item.optionalString("reservationUrl"),
                    verifiedAt = item.optString("verifiedAt").trim(),
                    wechatSearchName = item.optionalString("wechatSearchName"),
                    guideUrl = item.optionalString("guideUrl"),
                    exhibitionUrl = item.optionalString("exhibitionUrl"),
                    status = item.optionalString("status"),
                )
                if (entry.museum.isNotBlank()) put(entry.museum, entry)
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
    private fun absolutePhotoFile(path: String): File = if (File(path).isAbsolute) File(path) else File(photosDir, File(path).name)
    private fun absolutePhotoPath(path: String): String = absolutePhotoFile(path).absolutePath
    private fun newId(prefix: String): String = "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
    private fun isSameDay(first: Long, second: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        LocalDate.ofInstant(Instant.ofEpochMilli(first), zone) == LocalDate.ofInstant(Instant.ofEpochMilli(second), zone)

    private companion object {
        const val RECORD_PREFIX = "museum_record:"
        const val EXPLORATION_KEY = "exploration_v2"
        const val LEGACY_BACKUP_KEY = "legacy_backup_v051"
        const val PHOTO_DIR = "visit_photos"
        val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
    }
}
