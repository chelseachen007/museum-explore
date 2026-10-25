package cn.chenjie.museumexplore.data

import cn.chenjie.museumexplore.model.DatePrecision
import cn.chenjie.museumexplore.model.ExplorationState
import cn.chenjie.museumexplore.model.MuseumCandidate
import cn.chenjie.museumexplore.model.MuseumRecord
import cn.chenjie.museumexplore.model.NextStopCandidate
import cn.chenjie.museumexplore.model.OpeningStatus
import cn.chenjie.museumexplore.model.RecordSource
import cn.chenjie.museumexplore.model.Sighting
import cn.chenjie.museumexplore.model.Visit
import cn.chenjie.museumexplore.model.WishEntry
import cn.chenjie.museumexplore.model.YearFootprint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 「想去 / 已到访 / 已看到」的状态机。
 *
 * 全部函数都是纯函数（除了调用方传入的 id 生成器），方便单元测试覆盖迁移、幂等和时间统计。
 * 关键规则来自 需求文档-官方入口与参观闭环-v0.6.md 第 6 节：
 * - 重复点击必须幂等，不生成重复参观或重复看到记录。
 * - 日期无法确认时用 UNKNOWN，绝不用「今天」冒充参观日期。
 * - 取消看到只影响看到记录，不删除到访记录。
 */
object ExplorationOps {

    fun addVisit(
        state: ExplorationState,
        museumId: String,
        city: String,
        startedAt: Long?,
        precision: DatePrecision,
        source: RecordSource,
        idFactory: (String) -> String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<ExplorationState, Visit> {
        val effectivePrecision = if (startedAt == null) DatePrecision.UNKNOWN else precision
        if (effectivePrecision == DatePrecision.EXACT && startedAt != null) {
            val existing = state.visits.firstOrNull { visit ->
                visit.museumId == museumId &&
                    visit.hasExactDate &&
                    sameLocalDay(visit.startedAt!!, startedAt, zone)
            }
            if (existing != null) return state to existing
        }
        val visit = Visit(
            id = idFactory("visit"),
            museumId = museumId,
            city = city,
            startedAt = startedAt,
            datePrecision = effectivePrecision,
            source = source,
        )
        return state.copy(visits = state.visits + visit) to visit
    }

    /** 删除参观。国宝的「已看到」记录保留，只解除关联（返回被删除的参观，便于保留照片和感想）。 */
    fun removeVisit(state: ExplorationState, visitId: String): Pair<ExplorationState, Visit?> {
        val removed = state.visit(visitId) ?: return state to null
        val next = state.copy(
            visits = state.visits.filterNot { it.id == visitId },
            sightings = state.sightings.map { sighting ->
                if (sighting.visitId == visitId) sighting.copy(visitId = null) else sighting
            },
        )
        return next to removed
    }

    fun recordSighting(
        state: ExplorationState,
        relicId: Int,
        museumId: String?,
        visitId: String?,
        seenAt: Long?,
        source: RecordSource,
        idFactory: (String) -> String,
    ): Pair<ExplorationState, Sighting> {
        val resolvedVisitId = visitId ?: museumId?.let { state.latestVisitForMuseum(it)?.id }
        val existing = state.sightings.firstOrNull { it.relicId == relicId && it.visitId == resolvedVisitId }
        if (existing != null) return state to existing
        val sighting = Sighting(
            id = idFactory("sighting"),
            relicId = relicId,
            museumId = museumId,
            visitId = resolvedVisitId,
            seenAt = seenAt ?: resolvedVisitId?.let { state.visit(it)?.startedAt },
            source = source,
        )
        return state.copy(sightings = state.sightings + sighting) to sighting
    }

    /** 取消看到：撤销该件国宝最近的一次看到记录，不影响博物馆到访。 */
    fun removeSighting(state: ExplorationState, relicId: Int): ExplorationState {
        val target = state.sightingsForRelic(relicId)
            .maxWithOrNull(compareBy<Sighting> { it.seenAt ?: Long.MIN_VALUE }.thenBy { it.id })
            ?: return state
        return state.copy(sightings = state.sightings.filterNot { it.id == target.id })
    }

    fun setWish(entries: List<WishEntry>, target: String, wanted: Boolean, now: Long): List<WishEntry> {
        if (target.isBlank()) return entries
        val exists = entries.any { it.target == target }
        return when {
            wanted && !exists -> entries + WishEntry(target, now)
            !wanted && exists -> entries.filterNot { it.target == target }
            else -> entries
        }
    }

    /** 年度足迹。日期未知的历史记录不计入具体年份，单独统计。 */
    fun footprint(state: ExplorationState, year: Int, zone: ZoneId = ZoneId.systemDefault()): YearFootprint {
        val monthly = MutableList(12) { 0 }
        val museumIds = mutableSetOf<String>()
        val cities = mutableSetOf<String>()
        state.visits.forEach { visit ->
            if (visit.pendingConfirmation) return@forEach
            if (!visit.hasExactDate) return@forEach
            val date = Instant.ofEpochMilli(visit.startedAt!!).atZone(zone).toLocalDate()
            if (date.year != year) return@forEach
            museumIds.add(visit.museumId)
            if (visit.city.isNotBlank()) cities.add(visit.city)
            monthly[date.monthValue - 1] = monthly[date.monthValue - 1] + 1
        }
        val visitIdsInYear = state.visits.filter { visit ->
            !visit.pendingConfirmation && visit.hasExactDate &&
                Instant.ofEpochMilli(visit.startedAt!!).atZone(zone).toLocalDate().year == year
        }.mapTo(mutableSetOf()) { it.id }
        val relicCount = state.sightings.count { sighting ->
            val seenAt = sighting.seenAt
            when {
                seenAt != null -> Instant.ofEpochMilli(seenAt).atZone(zone).toLocalDate().year == year
                sighting.visitId != null -> sighting.visitId in visitIdsInYear
                else -> false
            }
        }
        return YearFootprint(
            year = year,
            museumCount = museumIds.size,
            relicCount = relicCount,
            cityCount = cities.size,
            monthlyVisits = monthly,
            unknownDateVisits = state.visits.count { !it.pendingConfirmation && !it.hasExactDate },
        )
    }

    /** 下一站推荐，排序完全可解释。 */
    fun recommend(candidates: List<MuseumCandidate>, limit: Int = 3): List<NextStopCandidate> {
        return candidates.map { candidate ->
            val unseenWished = candidate.wishedRelicIds.count { it in candidate.unseenRelicIds }
            var score = 0
            score += when (candidate.openingStatus) {
                OpeningStatus.OPEN -> 40
                OpeningStatus.CLOSING_SOON -> 18
                OpeningStatus.BEFORE_OPEN -> 12
                OpeningStatus.CLOSED_TODAY -> -80
                OpeningStatus.UNKNOWN -> 0
            }
            if (candidate.isWished) score += 45
            score += unseenWished * 9
            score += (candidate.unseenRelicIds.size - unseenWished).coerceAtLeast(0) * 2
            score += if (candidate.visitCount == 0) 12 else -6
            if (candidate.address != null) score += 4

            NextStopCandidate(
                museumId = candidate.museumId,
                city = candidate.city,
                address = candidate.address,
                relicsTotal = candidate.relicsTotal,
                relicsWished = candidate.wishedRelicIds.size,
                relicsUnseen = candidate.unseenRelicIds.size,
                isWished = candidate.isWished,
                visitedCount = candidate.visitCount,
                openingStatus = candidate.openingStatus,
                reason = buildReason(candidate, unseenWished),
                score = score,
            )
        }.sortedWith(
            compareByDescending<NextStopCandidate> { it.score }
                .thenByDescending { it.relicsWished }
                .thenByDescending { it.relicsUnseen }
                .thenBy { it.museumId },
        ).take(limit)
    }

    private fun buildReason(candidate: MuseumCandidate, unseenWished: Int): String = when {
        candidate.openingStatus == OpeningStatus.CLOSED_TODAY -> "今天闭馆，可以先看看其他馆"
        candidate.isWished && unseenWished > 0 -> "在你的想去清单里，还有 $unseenWished 件你想看的国宝"
        candidate.isWished -> "在你的想去清单里"
        unseenWished > 0 -> "有 $unseenWished 件你想看的国宝"
        candidate.unseenRelicIds.isNotEmpty() -> "还有 ${candidate.unseenRelicIds.size} 件 195 永禁文物没看过"
        candidate.visitCount > 0 -> "已经去过，可以再看一次"
        else -> "本城还没去过的馆"
    }

    /**
     * 旧数据迁移。只把用户确实点过的状态翻译过来：
     * `visited:X = true` → 一次日期待补充的到访；`relic:ID = true` → 一条已看到记录。
     */
    fun migrate(
        snapshot: LegacySnapshot,
        cityOf: (String) -> String?,
    ): ExplorationState {
        val migrateId = { museum: String -> "visit_migrated_" + kotlin.math.abs(museum.hashCode().toLong()).toString(36) }
        val visits = snapshot.visitedMuseums.distinct().map { museum ->
            val journal = snapshot.journals[museum]
            Visit(
                id = migrateId(museum),
                museumId = museum,
                city = cityOf(museum).orEmpty(),
                startedAt = null,
                datePrecision = DatePrecision.UNKNOWN,
                source = RecordSource.MIGRATION,
                note = journal?.note.orEmpty(),
                photos = journal?.photos.orEmpty(),
            )
        }
        val visitIdByMuseum = visits.associate { it.museumId to it.id }
        val sightings = snapshot.seenRelics.map { legacy ->
            // 旧版「收集」没有时间。关联只用来归档到那次（同样没有日期的）到访，
            // seenAt 保持 null：不制造「看这件国宝的那一天」。
            Sighting(
                id = "sighting_migrated_${legacy.relicId}",
                relicId = legacy.relicId,
                museumId = legacy.museumId,
                visitId = legacy.museumId?.let { visitIdByMuseum[it] },
                seenAt = null,
                source = RecordSource.MIGRATION,
            )
        }
        return ExplorationState(
            wishedMuseums = emptyList(),
            wishedRelics = emptyList(),
            visits = visits,
            sightings = sightings,
        )
    }

    /**
     * 用户给一个「占位记录」补了日期，就代表确认到访。
     * 如果同馆同一天已有确认记录，则把占位记录的感想和照片合并过去，避免重复参观。
     */
    fun confirmVisit(
        state: ExplorationState,
        visitId: String,
        startedAt: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ExplorationState {
        val visit = state.visit(visitId) ?: return state
        val duplicate = startedAt?.let { timestamp ->
            state.visits.firstOrNull { other ->
                other.id != visitId &&
                    other.museumId == visit.museumId &&
                    !other.pendingConfirmation &&
                    other.hasExactDate &&
                    sameLocalDay(other.startedAt!!, timestamp, zone)
            }
        }
        if (duplicate == null) {
            return state.copy(
                visits = state.visits.map {
                    if (it.id != visitId) it
                    else it.copy(
                        startedAt = startedAt,
                        datePrecision = if (startedAt == null) DatePrecision.UNKNOWN else DatePrecision.EXACT,
                        pendingConfirmation = false,
                    )
                },
            )
        }
        val merged = duplicate.copy(
            note = listOf(duplicate.note, visit.note).filter(String::isNotBlank).joinToString("\n\n"),
            photos = (duplicate.photos + visit.photos).distinctBy { it.path },
        )
        return state.copy(
            visits = state.visits
                .filterNot { it.id == visitId }
                .map { if (it.id == duplicate.id) merged else it },
            sightings = state.sightings.map {
                if (it.visitId == visitId) it.copy(visitId = duplicate.id) else it
            },
        )
    }

    private fun sameLocalDay(first: Long, second: Long, zone: ZoneId): Boolean =
        LocalDate.ofInstant(Instant.ofEpochMilli(first), zone) == LocalDate.ofInstant(Instant.ofEpochMilli(second), zone)
}

data class LegacySighting(val relicId: Int, val museumId: String?)

data class LegacySnapshot(
    val visitedMuseums: List<String>,
    val seenRelics: List<LegacySighting>,
    val journals: Map<String, MuseumRecord> = emptyMap(),
)
