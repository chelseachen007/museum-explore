package cn.chenjie.museumexplore.model

/**
 * 探索状态 v2：把「想去 / 已到访 / 已看到」三件事拆成互相独立的数据。
 *
 * 设计约束（见 需求文档-官方入口与参观闭环-v0.6.md 第 6 节）：
 * - 三个状态不是同一条进度的三步，不能压成一个布尔值。
 * - 同一家博物馆允许多次参观。
 * - 无法确认日期时用 [DatePrecision.UNKNOWN]，不允许用迁移当天冒充参观日期。
 */
const val EXPLORATION_SCHEMA_VERSION = 2

enum class DatePrecision {
    /** 用户明确选择了日期。 */
    EXACT,

    /** 历史数据或用户选择「日期待补充」，[Visit.startedAt] 为 null。 */
    UNKNOWN,
}

enum class RecordSource {
    /** 用户本次主动操作产生。 */
    MANUAL,

    /** 由 v0.5.1 的旧打卡数据迁移而来，未确认日期。 */
    MIGRATION,
}

/** 「想去」对象：博物馆名或国宝 id。 */
data class WishEntry(
    val target: String,
    val createdAt: Long,
)

/** 一次参观。同一 museumId 可以有多次。 */
data class Visit(
    val id: String,
    val museumId: String,
    val city: String,
    val startedAt: Long?,
    val datePrecision: DatePrecision,
    val source: RecordSource,
    /**
     * true 只用于「写感想时顺带创建」的占位参观。
     * 它出现在时间线里，但顶部明确显示「待确认到访」，不计入到访次数和年度足迹。
     */
    val pendingConfirmation: Boolean = false,
    /** 这次参观的感想。空串表示用户没写，不代表「没有感想」。 */
    val note: String = "",
    /** 这次参观的照片，照片可以关联到具体国宝。 */
    val photos: List<VisitPhoto> = emptyList(),
) {
    /** 历史迁移记录只计入总进度，不计入具体年份。 */
    val hasExactDate: Boolean
        get() = datePrecision == DatePrecision.EXACT && startedAt != null

    val hasJournal: Boolean
        get() = note.isNotBlank() || photos.isNotEmpty()
}

/** 一次「看到某件国宝」的记录。 */
data class Sighting(
    val id: String,
    val relicId: Int,
    val museumId: String?,
    val visitId: String?,
    val seenAt: Long?,
    val source: RecordSource,
)

data class ExplorationState(
    val schemaVersion: Int = EXPLORATION_SCHEMA_VERSION,
    val wishedMuseums: List<WishEntry> = emptyList(),
    val wishedRelics: List<WishEntry> = emptyList(),
    val visits: List<Visit> = emptyList(),
    val sightings: List<Sighting> = emptyList(),
) {
    fun visit(id: String): Visit? = visits.firstOrNull { it.id == id }

    fun visitsForMuseum(museum: String): List<Visit> = visits.filter { it.museumId == museum }

    /** 最近一次到访：有准确日期的排在前面，同组内按时间倒序。 */
    fun latestVisitForMuseum(museum: String): Visit? = visitsForMuseum(museum)
        .sortedWith(VISIT_ORDER)
        .firstOrNull()

    fun sightingsForVisit(visitId: String): List<Sighting> = sightings.filter { it.visitId == visitId }

    fun sightingsForMuseum(museum: String): List<Sighting> = sightings.filter { it.museumId == museum }

    fun sightingsForRelic(relicId: Int): List<Sighting> = sightings.filter { it.relicId == relicId }

    fun isSeen(relicId: Int): Boolean = sightings.any { it.relicId == relicId }

    fun isVisited(museum: String): Boolean = visits.any { it.museumId == museum && !it.pendingConfirmation }

    /** 已确认的到访才计入统计。 */
    fun confirmedVisitsForMuseum(museum: String): List<Visit> =
        visitsForMuseum(museum).filterNot { it.pendingConfirmation }

    fun isMuseumWished(museum: String): Boolean = wishedMuseums.any { it.target == museum }

    fun isRelicWished(relicId: Int): Boolean = wishedRelics.any { it.target == relicId.toString() }
}

/** 时间线排序：准确日期倒序在前，日期未知的记录沉底。 */
val VISIT_ORDER: Comparator<Visit> = compareByDescending<Visit> { it.hasExactDate }
    .thenByDescending { it.startedAt ?: Long.MIN_VALUE }
    .thenByDescending { it.id }

data class YearFootprint(
    val year: Int,
    val museumCount: Int,
    val relicCount: Int,
    val cityCount: Int,
    val monthlyVisits: List<Int>,
    val unknownDateVisits: Int,
)

data class NextStopCandidate(
    val museumId: String,
    val city: String,
    val address: String?,
    val relicsTotal: Int,
    val relicsWished: Int,
    val relicsUnseen: Int,
    val isWished: Boolean,
    val visitedCount: Int,
    val openingStatus: OpeningStatus,
    val reason: String,
    val score: Int,
)

/**
 * 开放状态。数据不足以判断时必须是 [UNKNOWN]，不允许默认显示「开放中」。
 */
enum class OpeningStatus(val label: String) {
    OPEN("开放中"),
    CLOSING_SOON("即将闭馆"),
    BEFORE_OPEN("未开馆"),
    CLOSED_TODAY("今日闭馆"),
    UNKNOWN("待官方确认"),
}

/**
 * 下一站推荐的输入：只包含已核验的馆级事实。
 * [address] 为 null 表示地址未核验，此时不提供导航，只在卡片上说明。
 */
data class MuseumCandidate(
    val museumId: String,
    val city: String,
    val address: String?,
    val relicsTotal: Int,
    val wishedRelicIds: List<Int>,
    val unseenRelicIds: List<Int>,
    val isWished: Boolean,
    val visitCount: Int,
    val openingStatus: OpeningStatus,
    val openingDetail: String?,
)
