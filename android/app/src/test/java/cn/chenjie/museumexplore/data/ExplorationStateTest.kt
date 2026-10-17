package cn.chenjie.museumexplore.data

import cn.chenjie.museumexplore.model.DatePrecision
import cn.chenjie.museumexplore.model.ExplorationState
import cn.chenjie.museumexplore.model.MuseumCandidate
import cn.chenjie.museumexplore.model.MuseumRecord
import cn.chenjie.museumexplore.model.OpeningStatus
import cn.chenjie.museumexplore.model.RecordSource
import cn.chenjie.museumexplore.model.VisitPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ExplorationStateTest {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private var sequence = 0
    private val ids: (String) -> String = { prefix -> "${prefix}_${++sequence}" }

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 10): Long =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun migrationTurnsLegacyFlagsIntoDatedUnknownVisitAndSighting() {
        val journal = MuseumRecord(note = "看了越王勾践剑", photos = listOf(VisitPhoto("a.jpg", 45)))
        val snapshot = LegacySnapshot(
            visitedMuseums = listOf("湖北省博物馆"),
            seenRelics = listOf(LegacySighting(45, "湖北省博物馆")),
            journals = mapOf("湖北省博物馆" to journal),
        )

        val state = ExplorationOps.migrate(snapshot) { museum -> if (museum == "湖北省博物馆") "武汉" else null }

        val visit = state.visits.single()
        assertEquals(DatePrecision.UNKNOWN, visit.datePrecision)
        assertNull(visit.startedAt)
        assertEquals(RecordSource.MIGRATION, visit.source)
        assertEquals("看了越王勾践剑", visit.note)
        assertEquals("武汉", visit.city)
        val sighting = state.sightings.single()
        assertEquals(45, sighting.relicId)
        assertEquals(visit.id, sighting.visitId)
        assertNull(sighting.seenAt)
    }

    @Test
    fun sameMuseumSameDayVisitIsIdempotentButAnotherDayIsANewVisit() {
        val first = millis(2026, 10, 17)
        val withVisit = ExplorationOps.addVisit(
            ExplorationState(), "故宫博物院", "北京", first, DatePrecision.EXACT, RecordSource.MANUAL, ids,
        ).first
        val sameAgain = ExplorationOps.addVisit(
            withVisit, "故宫博物院", "北京", first + 60_000, DatePrecision.EXACT, RecordSource.MANUAL, ids,
        ).first
        assertEquals(1, sameAgain.visits.size)

        val nextDay = ExplorationOps.addVisit(
            sameAgain, "故宫博物院", "北京", millis(2026, 10, 18), DatePrecision.EXACT, RecordSource.MANUAL, ids,
        ).first
        assertEquals(2, nextDay.visits.size)
    }

    @Test
    fun cancellingASightingKeepsTheVisit() {
        val visitState = ExplorationOps.addVisit(
            ExplorationState(), "上海博物馆", "上海", millis(2026, 5, 1), DatePrecision.EXACT, RecordSource.MANUAL, ids,
        ).first
        val seenState = ExplorationOps.recordSighting(
            visitState, relicId = 12, museumId = "上海博物馆", visitId = null, seenAt = null,
            source = RecordSource.MANUAL, idFactory = ids,
        ).first
        assertTrue(seenState.isSeen(12))

        val cancelled = ExplorationOps.removeSighting(seenState, 12)
        assertFalse(cancelled.isSeen(12))
        assertEquals(1, cancelled.visits.size)
        assertTrue(cancelled.isVisited("上海博物馆"))
    }

    @Test
    fun removingAVisitKeepsSightingsAsUnlinkedRecords() {
        val visitState = ExplorationOps.addVisit(
            ExplorationState(), "南京博物院", "南京", millis(2026, 6, 6), DatePrecision.EXACT, RecordSource.MANUAL, ids,
        ).first
        val seenState = ExplorationOps.recordSighting(
            visitState, relicId = 7, museumId = "南京博物院", visitId = null, seenAt = null,
            source = RecordSource.MANUAL, idFactory = ids,
        ).first
        val sightingId = seenState.sightings.single().id

        val (afterRemoval, removed) = ExplorationOps.removeVisit(seenState, visitState.visits.single().id)

        assertEquals(visitState.visits.single().id, removed?.id)
        assertTrue(afterRemoval.visits.isEmpty())
        assertEquals(sightingId, afterRemoval.sightings.single().id)
        assertNull(afterRemoval.sightings.single().visitId)
    }

    @Test
    fun footprintIgnoresUnknownDatesAndCountsSeenRelics() {
        val year = 2026
        var state = ExplorationOps.addVisit(
            ExplorationState(), "湖北省博物馆", "武汉", millis(year, 3, 4), DatePrecision.EXACT, RecordSource.MANUAL, ids,
        ).first
        state = ExplorationOps.addVisit(
            state, "湖北省博物馆", "武汉", millis(year, 3, 5), DatePrecision.EXACT, RecordSource.MANUAL, ids,
        ).first
        state = ExplorationOps.addVisit(
            state, "湖南博物院", "长沙", millis(year, 4, 9), DatePrecision.EXACT, RecordSource.MANUAL, ids,
        ).first
        state = ExplorationOps.addVisit(
            state, "故宫博物院", "北京", null, DatePrecision.UNKNOWN, RecordSource.MANUAL, ids,
        ).first
        state = ExplorationOps.recordSighting(
            state, relicId = 45, museumId = "湖北省博物馆", visitId = state.visits.first().id, seenAt = millis(year, 3, 4),
            source = RecordSource.MANUAL, idFactory = ids,
        ).first
        state = ExplorationOps.recordSighting(
            state, relicId = 46, museumId = "湖南博物院", visitId = null, seenAt = millis(2025, 12, 30),
            source = RecordSource.MANUAL, idFactory = ids,
        ).first

        val footprint = ExplorationOps.footprint(state, year)

        assertEquals(2, footprint.museumCount)
        assertEquals(2, footprint.cityCount)
        assertEquals(1, footprint.relicCount)
        assertEquals(2, footprint.monthlyVisits[2])
        assertEquals(1, footprint.monthlyVisits[3])
        assertEquals(1, footprint.unknownDateVisits)
    }

    @Test
    fun recommendPutsWishedOpenMuseumFirstAndExplainsWhy() {
        val closed = MuseumCandidate(
            museumId = "闭馆馆", city = "北京", address = "某路 1 号", relicsTotal = 3,
            wishedRelicIds = listOf(1), unseenRelicIds = listOf(1, 2, 3), isWished = true, visitCount = 0,
            openingStatus = OpeningStatus.CLOSED_TODAY, openingDetail = "今天闭馆",
        )
        val open = MuseumCandidate(
            museumId = "开放馆", city = "北京", address = "某路 2 号", relicsTotal = 5,
            wishedRelicIds = listOf(9, 10), unseenRelicIds = listOf(9, 10, 11), isWished = true, visitCount = 0,
            openingStatus = OpeningStatus.OPEN, openingDetail = "17:00 停止入馆",
        )

        val ranked = ExplorationOps.recommend(listOf(closed, open))

        assertEquals("开放馆", ranked.first().museumId)
        assertTrue(ranked.first().reason.contains("想去清单"))
        assertTrue(ranked.first().reason.contains("2 件"))
    }

    @Test
    fun openingHoursAreUnknownWithoutReliableRange() {
        assertEquals(
            OpeningStatus.UNKNOWN,
            OpeningHours.status("具体开放时段和闭馆日以官方公告、预约页面为准。", null, millis(2026, 10, 17, 10)),
        )
        assertEquals(OpeningStatus.UNKNOWN, OpeningHours.status(null, null, millis(2026, 10, 17, 10)))
    }

    @Test
    fun openingHoursDetectClosedDayBeforeOpenAndClosingSoon() {
        val text = "9:00—17:30，16:00停止入馆；周一闭馆（法定节假日安排另行公告）。"
        // 2026-10-19 是周一，2026-10-17 是周六
        assertEquals(OpeningStatus.CLOSED_TODAY, OpeningHours.status(text, null, millis(2026, 10, 19, 10)))
        assertEquals(OpeningStatus.BEFORE_OPEN, OpeningHours.status(text, null, millis(2026, 10, 17, 8)))
        assertEquals(OpeningStatus.OPEN, OpeningHours.status(text, null, millis(2026, 10, 17, 12)))
        assertEquals(OpeningStatus.CLOSING_SOON, OpeningHours.status(text, null, millis(2026, 10, 17, 15)))
    }

    @Test
    fun codecRoundTripKeepsWishesVisitsAndSightings() {
        var state = ExplorationState()
        state = state.copy(wishedMuseums = ExplorationOps.setWish(state.wishedMuseums, "三星堆博物馆", true, 1L))
        state = state.copy(wishedRelics = ExplorationOps.setWish(state.wishedRelics, "12", true, 2L))
        state = ExplorationOps.addVisit(
            state, "三星堆博物馆", "广汉", millis(2026, 10, 2), DatePrecision.EXACT, RecordSource.MANUAL, ids,
        ).first
        state = ExplorationOps.recordSighting(
            state, 12, "三星堆博物馆", null, null, RecordSource.MANUAL, ids,
        ).first
        state = state.copy(
            visits = state.visits.map { it.copy(note = "青铜神树很震撼", photos = listOf(VisitPhoto("photo_1.jpg", 12))) },
        )

        val decoded = ExplorationCodec.decode(ExplorationCodec.encode(state))

        assertEquals(state.wishedMuseums, decoded.wishedMuseums)
        assertEquals(state.wishedRelics, decoded.wishedRelics)
        assertEquals(state.visits, decoded.visits)
        assertEquals(state.sightings, decoded.sightings)
        assertTrue(decoded.isSeen(12))
        assertTrue(decoded.isVisited("三星堆博物馆"))
    }

    @Test
    fun codecSurvivesBrokenPayload() {
        val decoded = ExplorationCodec.decode("{not json")
        assertTrue(decoded.visits.isEmpty())
        assertTrue(decoded.sightings.isEmpty())
    }

    @Test
    fun confirmingDraftVisitMergesIntoExistingVisitSameDay() {
        val day = millis(2026, 10, 17)
        var state = ExplorationOps.addVisit(
            ExplorationState(), "河北博物院", "石家庄", day, DatePrecision.EXACT, RecordSource.MANUAL, ids,
        ).first
        val draft = ExplorationOps.addVisit(
            state, "河北博物院", "石家庄", null, DatePrecision.UNKNOWN, RecordSource.MANUAL, ids,
        ).second
        state = state.copy(
            visits = (state.visits + draft.copy(pendingConfirmation = true, note = "长信宫灯很震撼", photos = listOf(VisitPhoto("p.jpg", 3)))),
        )
        state = ExplorationOps.recordSighting(
            state, 3, "河北博物院", draft.id, null, RecordSource.MANUAL, ids,
        ).first

        val confirmed = ExplorationOps.confirmVisit(state, draft.id, day)

        assertEquals(1, confirmed.visits.size)
        assertFalse(confirmed.visits.single().pendingConfirmation)
        assertTrue(confirmed.visits.single().note.contains("长信宫灯"))
        assertEquals(confirmed.visits.single().id, confirmed.sightings.single().visitId)
    }

    @Test
    fun pendingVisitIsNotCountedInFootprint() {
        var state = ExplorationState()
        val draft = ExplorationOps.addVisit(
            state, "河北博物院", "石家庄", null, DatePrecision.UNKNOWN, RecordSource.MANUAL, ids,
        ).second
        state = state.copy(visits = listOf(draft.copy(pendingConfirmation = true)))

        val footprint = ExplorationOps.footprint(state, 2026)

        assertEquals(0, footprint.museumCount)
        assertEquals(0, footprint.unknownDateVisits)
    }
}
