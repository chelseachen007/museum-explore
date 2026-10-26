package cn.chenjie.museumexplore.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import cn.chenjie.museumexplore.model.CitySummary
import cn.chenjie.museumexplore.model.DatePrecision
import cn.chenjie.museumexplore.model.ExplorationState
import cn.chenjie.museumexplore.model.GuideGlossaryGroup
import cn.chenjie.museumexplore.model.GuideGlossaryItem
import cn.chenjie.museumexplore.model.GuideSection
import cn.chenjie.museumexplore.model.MuseumGuide
import cn.chenjie.museumexplore.model.MuseumBranch
import cn.chenjie.museumexplore.model.MuseumCandidate
import cn.chenjie.museumexplore.model.MuseumEntry
import cn.chenjie.museumexplore.model.MuseumInfo
import cn.chenjie.museumexplore.model.MuseumRecord
import cn.chenjie.museumexplore.model.MuseumRecordWithDate
import cn.chenjie.museumexplore.model.MuseumSource
import cn.chenjie.museumexplore.model.OfficialEntry
import cn.chenjie.museumexplore.model.RecordSource
import cn.chenjie.museumexplore.model.Relic
import cn.chenjie.museumexplore.model.UserMuseum
import cn.chenjie.museumexplore.model.UserRelic
import cn.chenjie.museumexplore.model.Visit
import cn.chenjie.museumexplore.model.VisitPhoto
import cn.chenjie.museumexplore.model.VisitTarget
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
    private val officialEntryMap: Map<String, OfficialEntry> by lazy { readOfficialEntries() }
    private val baseMuseumInfo: Map<String, MuseumInfo> by lazy { readMuseums() }
    private val builtInBranches: List<MuseumBranch> by lazy { readBranches() }
    /**
     * 「博物馆怎么逛」指南。
     *
     * 和 [relics] 一样是懒加载的静态资产，但它**不参与**探索状态的编码/备份：
     * 内容更新走 App 版本，不走用户数据迁移，混进备份反而会让老备份覆盖掉新指南。
     */
    private val guide: MuseumGuide by lazy { readGuide() }
    private val photosDir: File
        get() = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }
    private var cachedState: ExplorationState? = null
    private var cachedUserMuseums: List<UserMuseum>? = null
    private var cachedUserRelics: List<UserRelic>? = null
    private var cachedDirectory: MuseumDirectory? = null
    private var cachedRelicCounts: Map<String, Int>? = null

    /** 内置馆 + 用户自建馆 + 参观过但两边都没有的馆，三者合成的最终视图。 */
    private data class MuseumDirectory(
        val info: Map<String, MuseumInfo>,
        val branches: Map<String, List<MuseumBranch>>,
        /** 被并入某个父馆的老记录馆名 → 父馆名。老记录仍然能正确归位。 */
        val aliases: Map<String, String>,
        /** 被并进父馆的馆区名。它们不是独立条目，列表和城市计数都要排除。 */
        val branchNames: Set<String>,
        /** 用户自建馆名，用于列表排序和来源标记。 */
        val userNames: Set<String>,
        val branchParents: Set<String>,
    ) {
        fun canonical(name: String): String = aliases[name] ?: name
    }

    fun relics(): List<Relic> = relics
    fun guide(): MuseumGuide = guide
    fun guideSection(id: String): GuideSection? = guide.sections.firstOrNull { it.id == id }
    fun museums(): Map<String, MuseumInfo> = directory().info
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

    /** 写一条感想要落到哪一天。返回「应该写进哪一次参观」+「要不要新建」。 */
    data class JournalTarget(val visitId: String?, val create: Boolean)

    /**
     * 从一个日期起算的记录落点。
     *
     * 规则简单到用户能自己解释：
     * - 那天已经有一条到访 → 写进那一条，一次参观只有一张卡片。
     * - 那天没有 → 新建一次到访（用户写了感想又说了是哪天，就是去过了）。
     * - 日期留空 → 新建一条「日期待补充」的参观。清空日期通常意味着「记不清哪天」，是另一趟，
     *   不能塞回上一趟里；用户之后可以在时间线上补日期。
     *
     * 这样录旧账不会覆盖上一次的记录，也不会把「又去了一次」硬塞进第一张卡片。
     */
    fun journalTargetOn(museum: String, startedAt: Long?): JournalTarget {
        if (startedAt == null) return JournalTarget(null, create = true)
        val sameDay = exploration().confirmedVisitsForMuseumFamily(museum)
            .sortedWith(VISIT_ORDER)
            .firstOrNull { it.hasExactDate && isSameDay(it.startedAt!!, startedAt) }
        return if (sameDay != null) JournalTarget(sameDay.id, create = false) else JournalTarget(null, create = true)
    }

    /**
     * 保存一条感想，同时落定它的参观日期。
     *
     * 落点全部交给 [journalTargetOn] 决定：界面只管「写了什么、哪天」，
     * 「写进哪一次」只有一处规则，不会出现写入和读取各算一遍的错位。
     *
     * [visitId] 非空表示用户正在编辑某一次既有的参观，此时只改那一条。
     */
    fun saveRecordOnDate(
        museum: String,
        note: String,
        photos: List<VisitPhoto>,
        startedAt: Long?,
        visitId: String? = null,
        branch: String? = null,
    ): String? {
        val targetId = if (branch.isNullOrBlank()) museum else "$museum · $branch"
        if (visitId != null && updateVisitJournal(visitId, note, photos)) {
            if (startedAt != null) applyVisitDate(visitId, startedAt)
            return visitId
        }
        if (note.isBlank() && photos.isEmpty()) return null
        val target = journalTargetOn(targetId, startedAt)
        if (!target.create && target.visitId != null) {
            updateVisitJournal(target.visitId, note, photos)
            return target.visitId
        }
        val state = exploration()
        val draft = ExplorationOps.addVisit(
            state,
            museumId = targetId,
            city = cityOfMuseum(museum),
            startedAt = startedAt,
            precision = if (startedAt == null) DatePrecision.UNKNOWN else DatePrecision.EXACT,
            source = RecordSource.MANUAL,
            idFactory = ::newId,
        ).second
        saveExploration(
            state.copy(
                visits = (state.visits + draft).map {
                    if (it.id == draft.id) {
                        it.copy(
                            // 只有没写日期的才挂「待确认到访」，写了日期就算真的去过。
                            pendingConfirmation = startedAt == null,
                            note = note.trim(),
                            photos = photos,
                        )
                    } else {
                        it
                    }
                },
            ),
        )
        return draft.id
    }

    /** 改日期。待确认的记录直接转为已确认；同一天已有那次时不改，保持一次到访一张卡片。 */
    private fun applyVisitDate(visitId: String, startedAt: Long) {
        val visit = exploration().visit(visitId) ?: return
        if (visit.hasExactDate && isSameDay(visit.startedAt!!, startedAt)) return
        setVisitDate(visitId, startedAt)
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

    /**
     * 整馆视角下写记录的目标 visit。
     *
     * 「再记一次参观」这类操作只加了一个空壳到访，感想和照片必须落到
     * 已经有内容的那条上——否则新写的会追加到空壳里，页面上却读旧的，
     * 看起来就是「记了但没记上」。
     */
    fun journalTargetVisitId(museum: String, branch: String? = null): String? {
        val targetId = if (branch.isNullOrBlank()) museum else "$museum · $branch"
        val state = exploration()
        val journal = state.confirmedVisitsForMuseum(targetId)
            .sortedWith(VISIT_ORDER)
            .firstOrNull { it.hasJournal }
        if (journal != null) return journal.id
        return state.confirmedVisitsForMuseum(targetId)
            .sortedWith(VISIT_ORDER)
            .firstOrNull()
            ?.id
    }

    /**
     * 这座馆的所有参观记录，一次到访一条，最近的在前面。
     *
     * 每次「再记一次参观」都是独立一条，详情页会并列成多张卡片——
     * 只展示「有内容的」会让刚补的空到访看起来丢了，和上面的「已到访 N 次」对不上。
     * 所以这里包含还没写感想/照片的到访，卡片自己负责空态。
     */
    fun museumRecords(museum: String): List<MuseumRecordWithDate> {
        val state = exploration()
        val visits = if (hasBranches(museum)) state.confirmedVisitsForMuseumFamily(museum) else state.confirmedVisitsForMuseum(museum)
        return visits.sortedWith(VISIT_ORDER)
            .map { visit ->
                MuseumRecordWithDate(
                    record = MuseumRecord(visit.note, visit.photos, visit.startedAt ?: 0L),
                    visitId = visit.id,
                )
            }
    }

    fun museumRecord(museum: String): MuseumRecord {
        val state = exploration()
        // 整馆视角：父馆和它所有馆区里最近一条有内容的记录，都会出现在这一页。
        val visits = if (hasBranches(museum)) state.confirmedVisitsForMuseumFamily(museum) else state.confirmedVisitsForMuseum(museum)
        val visit = visits.filter { it.hasJournal }.sortedWith(VISIT_ORDER).firstOrNull()
        return if (visit != null) {
            MuseumRecord(visit.note, visit.photos, visit.startedAt ?: 0L)
        } else {
            legacyRecord(museum)
        }
    }

    /**
     * 保存某一馆区的记录。
     *
     * 父馆本身不写 journal（它就是「整馆」这个虚拟目标），内容一律落到具体馆区；
     * 没配馆区的馆照旧用馆名当 museumId。
     */
    fun saveBranchRecord(
        museum: String,
        branch: String? = null,
        note: String,
        photos: List<VisitPhoto>,
        targetId: String = if (branch.isNullOrBlank()) museum else "$museum · $branch",
    ) {
        val state = exploration()
        // 优先写回已有内容的那条，避免第二次记参观把内容追加到空壳上、页面却还是旧的。
        val existing = state.confirmedVisitsForMuseum(targetId)
            .sortedWith(VISIT_ORDER)
            .firstOrNull { it.hasJournal }
            ?: state.confirmedVisitsForMuseum(targetId).sortedWith(VISIT_ORDER).firstOrNull()
            ?: state.visitsForMuseum(targetId).firstOrNull { it.pendingConfirmation }
        if (existing != null) {
            updateVisitJournal(existing.id, note, photos)
            return
        }
        val draft = ExplorationOps.addVisit(
            state,
            museumId = targetId,
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

    /**
     * 删除这一次参观：连到访本身一起删，不是只清空感想和照片。
     *
     * 用户理解的「删除我的参观记录」就是这条记录从此不存在——
     * 只清内容的话卡片还在、已到访次数也不变，看起来像没删掉。
     * 已看到的文物记录不受影响（ExplorationOps.removeVisit 自己会保留）。
     */
    fun deleteMuseumRecord(museum: String, record: MuseumRecord) {
        deleteMuseumRecord(museum, record, visitId = null)
    }

    /** 有 visitId 时按 id 精确删，避免两条内容完全相同的记录删错。 */
    fun deleteMuseumRecord(museum: String, record: MuseumRecord, visitId: String?) {
        val state = exploration()
        val visits = if (hasBranches(museum)) state.confirmedVisitsForMuseumFamily(museum) else state.confirmedVisitsForMuseum(museum)
        val target = visitId?.let { id -> visits.firstOrNull { it.id == id } }
            ?: visits.firstOrNull { visit ->
                visit.note == record.note &&
                    visit.photos.map { it.path }.toSet() == record.photos.map { it.path }.toSet()
            } ?: return
        record.photos.forEach { path -> runCatching { deletePhoto(path.path) } }
        removeVisit(target.id)
    }

    fun saveMuseumRecord(museum: String, note: String, photos: List<VisitPhoto>) {
        val state = exploration()
        // 同样优先写回已有内容的那条。
        val visit = state.confirmedVisitsForMuseum(museum)
            .sortedWith(VISIT_ORDER)
            .firstOrNull { it.hasJournal }
            ?: state.confirmedVisitsForMuseum(museum).sortedWith(VISIT_ORDER).firstOrNull()
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

    /**
     * 某座城市里可以去的博物馆名。
     *
     * 三个来源合并：195 件文物名单派生的馆、内置馆清单、用户自己新增的馆。
     * 参观记录里的馆名也会进来——否则用户自己建的馆在改名或删数据后会「消失」，
     * 但记录还在时间线里，点进去就是一篇空白。
     */
    fun museumNames(city: String): List<String> {
        val directory = directory()
        val cityKeys = cityKeysOf(city)
        // 热点城市只列「本批线索 + 有国宝的馆 + 我记过的馆」。
        // 全量名录是几百家海外国立馆，塞进单个城市列表会把它变成通讯录，想去哪儿反而找不到。
        val fromDirectory = directory.info.values
            .filter { cityKeys.contains(it.city) }
            .filter { info ->
                info.type == "user_added" ||
                    directory.userNames.contains(info.name) ||
                    relicCount(info.name) > 0 ||
                    exploration().visits.any { it.museumId == info.name }
            }
            .map { it.name }
        val fromVisits = exploration().visits
            .map { directory.canonical(it.museumId) }
            .filter { cityKeys.contains(cityOfMuseum(it)) }
        return (fromDirectory + fromVisits)
            .distinct()
            .filterNot { directory.branchNames.contains(it) }
            .sortedWith(compareByDescending<String> { relicCount(it) }.thenBy { it })
    }

    fun allMuseumNames(): List<String> = directory().info.keys
        .filterNot { directory().branchNames.contains(it) }
        .sorted()

    /**
     * 目录页一次性取全量条目。
     *
     * 之前目录页对每个馆依次调 museumInfo / isUserMuseum / relicCount，
     * 每个都是一次 map 查找加一次 canonicalKeys（内部还会再取一次目录），
     * 几百个馆叠起来就是首屏可见的卡顿。这里统一在一个循环里算完。
     */
    fun museumEntries(): List<MuseumEntry> {
        val directory = directory()
        val counts = relicCounts()
        val userNames = directory.userNames
        return directory.info.values
            .filterNot { directory.branchNames.contains(it.name) }
            .map { info ->
                val source = when {
                    info.name in userNames || info.type == "user_added" -> MuseumSource.USER
                    counts[info.name] ?: 0 > 0 -> MuseumSource.RELIC
                    else -> MuseumSource.BUILT_IN
                }
                MuseumEntry(
                    name = info.name,
                    city = info.city,
                    source = source,
                    // 目录页不判断今天是否闭馆（那要看星期），闭馆与否在详情页展示。
                    closed = false,
                )
            }
            .sortedWith(compareBy({ it.city }, { it.name }))
    }

    fun cities(): List<CitySummary> {
        val directory = directory()
        val cityMuseums = linkedMapOf<String, MutableSet<String>>()
        relics.groupBy { it.city }.forEach { (city, cityRelics) -> cityMuseums.getOrPut(city) { linkedSetOf() } }
        directory.info.values.filterNot { directory.branchNames.contains(it.name) }.forEach { info ->
            cityMuseums.getOrPut(info.city) { linkedSetOf() }.add(info.name)
        }
        return cityMuseums.map { (city, museums) ->
            CitySummary(
                name = city,
                museumCount = museums.size,
                relicCount = relics.count { it.city == city },
                visitedCount = museums.count(::isVisited),
            )
        }.sortedWith(compareByDescending<CitySummary> { it.relicCount }.thenByDescending { it.museumCount })
    }

    fun relicsForMuseum(city: String, museum: String): List<Relic> {
        val keys = canonicalKeys(museum)
        return relics.filter {
            it.city == city && splitMuseumNames(it.museum).any(keys::contains)
        }
    }

    fun relicsForMuseum(museum: String): List<Relic> {
        val keys = canonicalKeys(museum)
        return relics.filter {
            splitMuseumNames(it.museum).any(keys::contains)
        }
    }

    fun relicCount(museum: String): Int = relicCounts()[museum] ?: 0
    fun relicCountForLocations(raw: String): Int = splitMuseumNames(raw).sumOf(::relicCount)
    fun museumInfo(name: String): MuseumInfo? = directory().info[name]

    fun cityOfMuseum(name: String): String {
        val directory = directory()
        val keys = canonicalKeys(name)
        // 先看名字本身，父馆名不能直接折到第一个馆区去（否则「浙江省博物馆」会变成之江馆的城市）。
        return directory.info[name]?.city
            ?: directory.branches[directory.canonical(name)]?.firstOrNull()?.city
            ?: directory.info[directory.canonical(name)]?.city
            ?: relics.firstOrNull { relic ->
                relic.city.isNotBlank() && splitMuseumNames(relic.museum).any(keys::contains)
            }?.city
            ?: exploration().visits.firstOrNull { keys.contains(directory.canonical(it.museumId)) }?.city
            ?: ""
    }

    // ---------------------------------------------------------------------
    // 博物馆目录：分馆、自建馆
    // ---------------------------------------------------------------------

    /** 某座馆下的馆区（分馆）。整馆本身可作为「不限馆区」的选项由界面自行补上。 */
    fun branches(museum: String): List<MuseumBranch> {
        val directory = directory()
        val parent = directory.canonical(museum)
        return directory.branches[parent].orEmpty()
    }

    /** 这座馆是否拆成了多个馆区——拆过的馆，记录参观时必须选清楚去的是哪一片。 */
    fun hasBranches(museum: String): Boolean = branches(museum).isNotEmpty()

    /** 记录参观时可选的目标：整馆 + 每个馆区。 */
    fun visitTargets(museum: String): List<VisitTarget> {
        val directory = directory()
        val parent = directory.canonical(museum)
        val city = cityOfMuseum(parent)
        // 同一地址只留一行：数据里「本馆」和某个馆区可能指向同一个地方，重复列出没意义。
        val branches = directory.branches[parent].orEmpty()
            .distinctBy { it.address?.replace(Regex("\\s+"), "")?.takeIf { addr -> addr.isNotBlank() } ?: it.name }
        return buildList {
            if (branches.isEmpty()) {
                add(VisitTarget(parent, null, city, directory.info[parent]?.address))
            } else {
                // 每个馆区各占一行，父馆本身不是「一个」地方，不再单列一行。
                branches.forEach { branch ->
                    val isParentRow = branch.name == parent
                    add(
                        VisitTarget(
                            parent = parent,
                            // 整馆那一行不写馆区后缀，避免出现「浙江省博物馆 · 浙江省博物馆」。
                            branch = if (isParentRow) null else branch.name,
                            city = city,
                            address = branch.address,
                            label = branch.label ?: if (isParentRow) "本馆" else branch.name.substringAfter(" · ", branch.name),
                        ),
                    )
                }
            }
        }
    }

    fun userMuseums(): List<UserMuseum> {
        cachedUserMuseums?.let { return it }
        val loaded = UserMuseumsCodec.decode(preferences.getString(USER_MUSEUMS_KEY, null))
        cachedUserMuseums = loaded
        return loaded
    }

    fun isUserMuseum(name: String): Boolean = userMuseums().any { it.name == name && !it.isMerged }

    // ---------------------------------------------------------------------
    // 用户自建国宝（195 名单之外，现场拍到就能记）
    // ---------------------------------------------------------------------

    fun userRelics(): List<UserRelic> {
        cachedUserRelics?.let { return it }
        val loaded = UserRelicsCodec.decode(preferences.getString(USER_RELICS_KEY, null))
        cachedUserRelics = loaded
        return loaded
    }

    /** 自建国宝按录入时间倒序，最后拍到的排在最前。 */
    fun userRelicsNewestFirst(): List<UserRelic> = userRelics()
        .sortedByDescending { it.createdAt }
        // 库里只存文件名；换设备导入备份后目录变了，这里还原成当前设备的绝对路径。
        .map { relic -> relic.photo?.let { relic.copy(photo = absolutePhotoPath(it)) } ?: relic }

    fun userRelic(id: Int): UserRelic? = userRelicsNewestFirst().firstOrNull { it.id == id }

    /**
     * 新增一件自建国宝。
     *
     * id 取当前最小值的再减一，保证永不为正数——正数是 195 件名单的编号，
     * 一旦撞号，「已看到」标记就会挂到错误的对象上。
     * [photo] 传的是照片文件名（不是绝对路径），换设备导入备份后仍能还原。
     */
    fun addUserRelic(
        name: String,
        museum: String,
        city: String,
        era: String? = null,
        category: String? = null,
        reason: String? = null,
        story: String? = null,
        photo: String? = null,
        museumUrl: String? = null,
    ): UserRelic? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null
        val existing = userRelics()
        val relic = UserRelic(
            id = (existing.minOfOrNull { it.id } ?: 0) - 1,
            name = cleanName,
            museum = museum.trim(),
            city = city.trim(),
            era = era?.trim()?.takeIf { it.isNotEmpty() },
            category = category?.trim()?.takeIf { it.isNotEmpty() },
            reason = reason?.trim()?.takeIf { it.isNotEmpty() },
            story = story?.trim()?.takeIf { it.isNotEmpty() },
            photo = photo?.let { File(it).name }?.takeIf { it.isNotEmpty() },
            museumUrl = museumUrl?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = System.currentTimeMillis(),
        )
        saveUserRelics(existing + relic)
        return relic
    }

    fun updateUserRelic(
        id: Int,
        name: String,
        museum: String,
        city: String,
        era: String? = null,
        category: String? = null,
        reason: String? = null,
        story: String? = null,
        photo: String? = null,
        museumUrl: String? = null,
    ): Boolean {
        val current = userRelics()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return false
        val cleanName = name.trim()
        if (cleanName.isBlank()) return false
        val old = current[index]
        val newPhoto = photo?.let { File(it).name }?.takeIf { it.isNotEmpty() }
        // 换了照片就把旧文件删掉，否则备份里会一直带着用户已经放弃的那张。
        if (old.photo != null && newPhoto != old.photo) deletePhoto(old.photo)
        val updated = old.copy(
            name = cleanName,
            museum = museum.trim(),
            city = city.trim(),
            era = era?.trim()?.takeIf { it.isNotEmpty() },
            category = category?.trim()?.takeIf { it.isNotEmpty() },
            reason = reason?.trim()?.takeIf { it.isNotEmpty() },
            story = story?.trim()?.takeIf { it.isNotEmpty() },
            photo = newPhoto,
            museumUrl = museumUrl?.trim()?.takeIf { it.isNotEmpty() },
        )
        saveUserRelics(current.toMutableList().also { it[index] = updated })
        return true
    }

    fun removeUserRelic(id: Int) {
        val target = userRelic(id) ?: return
        saveUserRelics(userRelics().filterNot { it.id == id })
        target.photo?.let(::deletePhoto)
    }

    /** 自建国宝所在馆的馆藏计数：详情页「N 件永禁文物」不把它算进去，单列一段。 */
    fun userRelicCountForMuseum(museum: String): Int = userRelics().count { it.museum == museum }

    /** 新增一座博物馆。同名内置馆已存在时直接返回 false，交给界面提示用户。 */
    fun addUserMuseum(name: String, city: String, officialUrl: String? = null, address: String? = null, notes: String? = null): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return false
        if (directory().info.containsKey(cleanName)) return false
        val added = UserMuseum(
            name = cleanName,
            city = city.trim(),
            officialUrl = officialUrl?.trim()?.takeIf { it.isNotEmpty() },
            address = address?.trim()?.takeIf { it.isNotEmpty() },
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = System.currentTimeMillis(),
        )
        saveUserMuseums(userMuseums() + added)
        return true
    }

    fun updateUserMuseum(name: String, city: String, officialUrl: String?, address: String?, notes: String?): Boolean {
        val current = userMuseums()
        val index = current.indexOfFirst { it.name == name && !it.isMerged }
        if (index < 0) return false
        val updated = current[index].copy(
            city = city.trim(),
            officialUrl = officialUrl?.trim()?.takeIf { it.isNotEmpty() },
            address = address?.trim()?.takeIf { it.isNotEmpty() },
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        )
        saveUserMuseums(current.toMutableList().also { it[index] = updated })
        return true
    }

    fun removeUserMuseum(name: String) {
        saveUserMuseums(userMuseums().filterNot { it.name == name })
    }

    /** 改过一次名之后，老记录还挂在旧名上，这里把旧名指回新名字。 */
    fun renameUserMuseum(oldName: String, newName: String): Boolean {
        val target = newName.trim()
        if (target.isBlank() || target == oldName) return false
        val current = userMuseums()
        val index = current.indexOfFirst { it.name == oldName && !it.isMerged }
        if (index < 0) return false
        if (current.any { it.name == target } || baseMuseumInfo.containsKey(target)) return false
        saveUserMuseums(current.toMutableList().also { it[index] = it[index].copy(name = target) })
        remapVisitMuseumNames(mapOf(oldName to target))
        return true
    }

    /**
     * 把名字相近的馆合并成一个「父馆 + 馆区」结构。
     *
     * 用户实际会说出「浙江省博物馆之江馆区」这种带馆区的名字；如果不合并，
     * 同一座馆会散成好几条记录，年度足迹和到访次数全乱。合并后：
     * - [selected] 这两个馆名不再作为独立馆出现；
     * - 它们的历史参观记录按 [trimSuffixes] 规则翻译成「父馆 · 馆区」；
     * - 用户自建的那条馆会被标记 mergedInto，不再重复出现在列表里。
     */
    fun mergeMuseums(
        parentName: String,
        parentCity: String,
        members: List<Pair<String, String>>,
        trimSuffixes: List<String>,
    ): Boolean {
        val parent = parentName.trim()
        if (parent.isBlank() || members.isEmpty()) return false
        val cleaned = members
            .map { (name, branch) -> name.trim() to branch.trim() }
            .filter { (name, branch) -> name.isNotEmpty() && branch.isNotEmpty() }
            .filter { (name, _) -> name != parent }
        if (cleaned.isEmpty()) return false
        val parentCityName = parentCity.trim().ifBlank { locationCity(parent) }
        val overrides = JSONObject()
        readMergeOverrides().forEach { (key, value) -> overrides.put(key, value) }
        overrides.put(
            parent,
            JSONObject()
                .put("city", parentCityName)
                .put("trimSuffixes", JSONArray(trimSuffixes.filter { it.isNotBlank() }))
                .put(
                    "branches",
                    JSONArray().apply {
                        cleaned.forEach { (name, branch) ->
                            put(
                                JSONObject()
                                    .put("name", name)
                                    .put("branch", branch)
                                    .put("parent", parent),
                            )
                        }
                    },
                ),
        )
        preferences.edit().putString(MERGE_OVERRIDES_KEY, overrides.toString()).apply()
        // 用户自建的同名馆并进父馆，避免列表里出现两个「浙江省博物馆」。
        saveUserMuseums(
            userMuseums().map { museum ->
                if (cleaned.any { it.first == museum.name } || museum.name == parent) museum.copy(mergedInto = parent) else museum
            },
        )
        val mergedNames = buildMap {
            put(parent, parent)
            cleaned.forEach { (name, _) -> put(name, parent) }
        }
        remapVisitMuseumNames(mergedNames)
        invalidateDirectory()
        return true
    }

    /** 下一站候选。开放状态只从已核验文本推断，推断不出就是待确认。 */
    fun nextStopCandidates(city: String, nowMillis: Long = System.currentTimeMillis()): List<MuseumCandidate> {
        val state = exploration()
        return museumNames(city).mapNotNull { museum ->
            val info = directory().info[museum] ?: return@mapNotNull null
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
            .put("appVersion", "0.7.0")
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
        // 自建馆和自建国宝也是内存缓存，不清掉的话导入完界面还是旧数据——
        // 用户会以为备份没导进去，或者干脆看不到别人那台设备上的记录。
        cachedState = null
        cachedUserMuseums = null
        cachedUserRelics = null
        invalidateDirectory()
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

    private fun directory(): MuseumDirectory {
        cachedDirectory?.let { return it }
        val userMuseums = userMuseums().filterNot { it.isMerged }
        val merged = HashMap(baseMuseumInfo)
        userMuseums.forEach { user ->
            merged[user.name] = MuseumInfo(
                name = user.name,
                city = user.city,
                officialUrl = user.officialUrl,
                wechat = null,
                address = user.address,
                intro = user.notes,
                openingHours = null,
                closedDays = null,
                reservation = null,
                wikiUrl = null,
                miniProgram = null,
                openToPublic = null,
                type = "user_added",
                verifiedAt = null,
                status = "user",
            )
        }
        val branches = builtInBranches.groupBy { it.parent }
        val folding = foldBranchRecords(merged, builtInBranches)
        val directory = MuseumDirectory(
            info = folding.info,
            branches = branches,
            aliases = folding.aliases,
            branchNames = folding.branchNames,
            userNames = userMuseums.map { it.name }.toSet(),
            branchParents = folding.branchParents,
        )
        cachedDirectory = directory
        return directory
    }

    internal data class BranchFolding(
        val info: Map<String, MuseumInfo>,
        val aliases: Map<String, String>,
        val branchNames: Set<String>,
        val branchParents: Set<String>,
    )

    private fun invalidateDirectory() {
        cachedDirectory = null
        cachedRelicCounts = null
    }

    private fun saveUserMuseums(list: List<UserMuseum>) {
        cachedUserMuseums = list
        preferences.edit().putString(USER_MUSEUMS_KEY, UserMuseumsCodec.encode(list)).apply()
        invalidateDirectory()
    }

    private fun saveUserRelics(list: List<UserRelic>) {
        cachedUserRelics = list
        preferences.edit().putString(USER_RELICS_KEY, UserRelicsCodec.encode(list)).apply()
    }

    /**
     * 内置清单 + 用户自建馆以外的补充清单一起读进来。
     *
     * 三个资产文件字段一致，缺 [city] 的条目按省份/国家兜底，避免它们挤在空字符串城市里。
     */
    private fun readMuseums(): Map<String, MuseumInfo> = runCatching {
        val result = LinkedHashMap<String, MuseumInfo>()
        ASSET_FILES.forEach { asset ->
            runCatching {
                val json = JSONObject(readAsset(asset))
                val items = json.optJSONArray("museums") ?: json.optJSONArray("muweums") ?: JSONArray()
                for (index in 0 until items.length()) {
                    val info = parseMuseumInfo(items.getJSONObject(index)) ?: continue
                    result.putIfAbsent(info.name, info)
                }
            }
        }
        result
    }.getOrDefault(emptyMap())

    private fun parseMuseumInfo(item: JSONObject): MuseumInfo? {
        val name = cleanDisplay(item.optString("name"))
        if (name.isBlank()) return null
        val city = cleanDisplay(item.optString("city"))
            .ifBlank { cleanDisplay(item.optString("province")) }
            .ifBlank { cleanDisplay(item.optString("country")) }
            .ifBlank { "未分城市" }
        return MuseumInfo(
            name = name,
            city = city,
            officialUrl = item.optionalString("officialUrl"),
            wechat = cleanDisplay(item.optString("wechat")).takeIf { it.isNotBlank() },
            address = cleanDisplay(item.optString("address")).takeIf { it.isNotBlank() },
            intro = cleanDisplay(item.optString("intro")).takeIf { it.isNotBlank() },
            openingHours = cleanDisplay(item.optString("openingHours")).takeIf { it.isNotBlank() },
            closedDays = cleanDisplay(item.optString("closedDays")).takeIf { it.isNotBlank() },
            reservation = cleanDisplay(item.optString("reservation")).takeIf { it.isNotBlank() },
            wikiUrl = item.optionalString("wikiUrl"),
            miniProgram = cleanDisplay(item.optString("miniProgram")).takeIf { it.isNotBlank() },
            // 没有明确标注开放性的馆，宁可标成「待确认」也不要默认开放。
            openToPublic = if (item.has("openToPublic") && !item.isNull("openToPublic")) {
                item.optBoolean("openToPublic", true)
            } else {
                null
            },
            type = item.optionalString("type"),
            verifiedAt = item.optionalString("verifiedAt"),
            status = item.optionalString("status"),
        )
    }

    /**
     * 馆区配置来自两处：内置资产 + 用户手动合并的覆盖。
     *
     * 用户覆盖优先——内置配置可能不全，但用户亲手合并过的一定是他真实去过的那几片。
     */
    private fun readBranches(): List<MuseumBranch> = runCatching {
        val result = LinkedHashMap<String, MuseumBranch>()
        fun add(parent: String, city: String, branch: MuseumBranch) {
            result.putIfAbsent("$parent|${branch.name}", branch)
        }
        runCatching {
            val json = JSONObject(readAsset(BRANCHES_ASSET))
            val items = json.optJSONArray("museums") ?: JSONArray()
            for (index in 0 until items.length()) {
                val museum = items.getJSONObject(index)
                val parent = cleanDisplay(museum.optString("parent"))
                if (parent.isBlank()) continue
                val city = cleanDisplay(museum.optString("city")).ifBlank { locationCity(parent) }
                val branches = museum.optJSONArray("branches") ?: JSONArray()
                for (branchIndex in 0 until branches.length()) {
                    val branch = branches.getJSONObject(branchIndex)
                    val name = cleanDisplay(branch.optString("name")).ifBlank {
                        cleanDisplay(branch.optString("branch"))
                    }
                    if (name.isBlank()) continue
                    add(
                        parent,
                        city,
                        MuseumBranch(
                            parent = parent,
                            name = name,
                            city = city,
                            address = cleanDisplay(branch.optString("address")).takeIf { it.isNotBlank() },
                            label = cleanDisplay(branch.optString("label")).takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }
        readMergeOverrides().forEach { (parent, override) ->
            val city = cleanDisplay(override.optString("city")).ifBlank { locationCity(parent) }
            val branches = override.optJSONArray("branches") ?: JSONArray()
            for (index in 0 until branches.length()) {
                val branch = branches.getJSONObject(index)
                val name = cleanDisplay(branch.optString("branch"))
                    .ifBlank { cleanDisplay(branch.optString("name")) }
                if (name.isBlank()) continue
                add(
                    parent,
                    city,
                    MuseumBranch(
                        parent = parent,
                        name = name,
                        city = city,
                        address = cleanDisplay(branch.optString("address")).takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        result.values.toList()
    }.getOrDefault(emptyList())

    private fun readMergeOverrides(): Map<String, JSONObject> = runCatching {
        val raw = preferences.getString(MERGE_OVERRIDES_KEY, null) ?: return emptyMap()
        val json = JSONObject(raw)
        buildMap {
            json.keys().forEach { key ->
                json.optJSONObject(key)?.let { put(key, it) }
            }
        }
    }.getOrDefault(emptyMap())

    /** 合并后把历史参观记录里的旧馆名翻译成「父馆」，否则老记录会挂在已经不存在的馆名下。 */
    private fun remapVisitMuseumNames(mapping: Map<String, String>) {
        if (mapping.isEmpty()) return
        val state = exploration()
        val updated = state.visits.map { visit ->
            val target = mapping[visit.museumId] ?: return@map visit
            if (target == visit.museumId) visit else visit.copy(museumId = target)
        }
        if (updated != state.visits) saveExploration(state.copy(visits = updated))
    }

    /** 城市比较用去空格、去「市/省」后缀的形式，避免「杭州市」和「杭州」分裂成两座城。 */
    private fun cityKeysOf(city: String): Set<String> {
        val trimmed = city.trim()
        if (trimmed.isEmpty()) return emptySet()
        return setOf(trimmed, trimmed.removeSuffix("市"), trimmed.removeSuffix("省"))
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** 一个馆名可能由多条记录共用（「A、B」），统一展开成可比较的键集合。 */
    /**
     * 馆名 → 该馆涉及的文物数。
     *
     * 目录页一次要渲染几百个馆，之前每个馆都 `relics.filter {...}` 一次（195 件 × N 馆），
     * 滚动和首屏都会明显发卡。这里只扫一遍 relics，按馆名分桶存起来。
     */
    private fun relicCounts(): Map<String, Int> = cachedRelicCounts ?: run {
        val counts = HashMap<String, Int>()
        relics.forEach { relic ->
            splitMuseumNames(relic.museum).forEach { name ->
                counts[name] = (counts[name] ?: 0) + 1
            }
        }
        // 父馆名要把馆区的数量并进来，否则「浙江省博物馆」显示 0 件。
        directory().aliases.forEach { (alias, canonical) ->
            counts[canonical]?.let { counts[alias] = (counts[alias] ?: 0) + it }
        }
        cachedRelicCounts = counts
        counts
    }

    private fun canonicalKeys(museum: String): Set<String> {
        val directory = directory()
        return splitMuseumNames(museum)
            .flatMap { name -> listOf(name, directory.canonical(name)) }
            .toSet()
    }

    /**
     * 只从资产文件推断馆所在城市，不碰目录缓存。
     *
     * 供 readMuseums / readBranches 自己用：那两个方法正是目录缓存的构建过程，
     * 在里面回调 cityOfMuseum 会无限递归。
     */
    private fun locationCity(name: String): String {
        baseMuseumInfo[name]?.city?.takeIf { it.isNotBlank() }?.let { return it }
        val keys = splitMuseumNames(name).toSet()
        return relics.firstOrNull { relic ->
            relic.city.isNotBlank() && splitMuseumNames(relic.museum).any(keys::contains)
        }?.city.orEmpty()
    }

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
                        detail = item.optString("detail").trim(),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())


    /**
     * 读「博物馆怎么逛」。
     *
     * 解析失败一律返回 [MuseumGuide.empty]，界面据此隐藏入口——指南是锦上添花，
     * 不能因为一个资产文件坏了就让整个 App 打不开。
     *
     * 空 [id] 的节会被丢掉：id 是深链和阅读进度的锚点，没 id 就没法定位。
     */
    private fun readGuide(): MuseumGuide = runCatching {
        val json = JSONObject(readAsset(GUIDE_ASSET))
        val sectionItems = json.optJSONArray("sections") ?: JSONArray()
        val sections = buildList {
            for (index in 0 until sectionItems.length()) {
                val item = sectionItems.getJSONObject(index)
                val id = item.optString("id").trim()
                val title = cleanDisplay(item.optString("title"))
                // body 走 cleanMarkdown 而不是 cleanDisplay：TextCleaner 是为维基抄来的脏文本设计的，
                // 它会把 `**粗体**` 前后空白吃掉，正文的 markdown 结构会被它悄悄改写。
                val body = cleanMarkdown(item.optString("body"))
                if (id.isBlank() || title.isBlank() || body.isBlank()) continue
                add(
                    GuideSection(
                        id = id,
                        title = title,
                        summary = cleanDisplay(item.optString("summary")),
                        body = body,
                    ),
                )
            }
        }
        val groups = buildList {
            val groupItems = json.optJSONArray("glossary") ?: JSONArray()
            for (index in 0 until groupItems.length()) {
                val group = groupItems.getJSONObject(index)
                val name = cleanDisplay(group.optString("group"))
                if (name.isBlank()) continue
                val items = buildList {
                    val entries = group.optJSONArray("items") ?: JSONArray()
                    for (entryIndex in 0 until entries.length()) {
                        val entry = entries.getJSONObject(entryIndex)
                        val char = cleanDisplay(entry.optString("char"))
                        if (char.isBlank()) continue
                        add(
                            GuideGlossaryItem(
                                char = char,
                                pinyin = cleanDisplay(entry.optString("pinyin")),
                                // 地名类条目允许没有释义，这里不要用占位符补，否则界面上全是重复的「—」。
                                meaning = cleanDisplay(entry.optString("meaning")),
                            ),
                        )
                    }
                }
                if (items.isNotEmpty()) add(GuideGlossaryGroup(group = name, items = items))
            }
        }
        val sources = buildList {
            val sourceItems = json.optJSONArray("sources") ?: JSONArray()
            for (index in 0 until sourceItems.length()) {
                val text = cleanDisplay(sourceItems.optString(index))
                if (text.isNotBlank()) add(text)
            }
        }
        MuseumGuide(
            title = cleanDisplay(json.optString("title")).ifBlank { "博物馆怎么逛" },
            subtitle = cleanDisplay(json.optString("subtitle")),
            intro = cleanDisplay(json.optString("intro")),
            updatedAt = json.optString("updatedAt").trim(),
            sections = sections,
            glossary = groups,
            sources = sources,
        )
    }.getOrDefault(MuseumGuide.empty)

    private fun readOfficialEntries(): Map<String, OfficialEntry> = runCatching {        val json = JSONObject(readAsset("official_entries.json"))
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
    /**
     * 清洗 markdown 正文：只做「行级」规整，不动行内标记。
     *
     * 与 [cleanDisplay] 的区别是不逐行 trim 之前的空行压缩——正文有空行分段，
     * 连续多个空行压成一个就够了，但不能把空行整个吃掉，否则所有段落会粘成一坨。
     */
    private fun cleanMarkdown(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

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

    companion object {
        /**
         * 把「父馆 · 馆区」的独立记录折进父馆。
         *
         * 一个馆在列表里只能占一行，馆区只作为地址栏里的一行地址存在。
         * 老版本的名录里留下过「浙江省博物馆 · 之江馆区」这种独立条目，
         * 后来 curator 又往名录里补了分馆，两处都命中就会重复出现，所以这里统一收口。
         *
         * 纯函数，便于单测覆盖。
         */
        internal fun foldBranchRecords(
            info: Map<String, MuseumInfo>,
            branches: List<MuseumBranch>,
        ): BranchFolding {
            val aliases = HashMap<String, String>()
            val branchNames = HashSet<String>()
            val branchParents = HashSet<String>()
            branches.forEach { branch ->
                branchParents.add(branch.parent)
                // 父馆那一行代表整馆，不该算作「子馆」被排除掉。
                if (branch.name != branch.parent) {
                    aliases[branch.name] = branch.parent
                    branchNames.add(branch.name)
                }
            }
            val folded = info.filterNot { (name, _) -> name in branchNames }
            return BranchFolding(
                info = folded,
                aliases = aliases,
                branchNames = branchNames,
                branchParents = branchParents,
            )
        }

        const val RECORD_PREFIX = "museum_record:"
        const val EXPLORATION_KEY = "exploration_v2"
        const val LEGACY_BACKUP_KEY = "legacy_backup_v051"
        const val PHOTO_DIR = "visit_photos"
        val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
        const val USER_MUSEUMS_KEY = "user_museums_v1"
        const val USER_RELICS_KEY = "user_relics_v1"
        const val MERGE_OVERRIDES_KEY = "museum_merge_overrides_v1"
        const val BRANCHES_ASSET = "museum_branches.json"
        const val GUIDE_ASSET = "museum_guide.json"

        /** 依次读取，同名馆以先读到的为准。 */
        val ASSET_FILES = listOf("museums.json", "museums_cn.json", "museums_overseas.json")
    }
}
