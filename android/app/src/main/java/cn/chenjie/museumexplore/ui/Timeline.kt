package cn.chenjie.museumexplore.ui

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import cn.chenjie.museumexplore.data.ExplorationOps
import cn.chenjie.museumexplore.data.MuseumRepository
import cn.chenjie.museumexplore.model.ExplorationState
import cn.chenjie.museumexplore.model.MuseumRecord
import cn.chenjie.museumexplore.model.Relic
import cn.chenjie.museumexplore.model.VISIT_ORDER
import cn.chenjie.museumexplore.model.Visit
import cn.chenjie.museumexplore.model.VisitPhoto
import cn.chenjie.museumexplore.model.YearFootprint
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

private const val FOOTPRINT_CARD_FILENAME = "boji_footprint"

private enum class TimelineTab(val label: String) {
    RECORDS("参观记录"),
    FOOTPRINT("年度足迹"),
}

private data class PendingDateTarget(
    val visitId: String?,
    val museum: String,
)

private data class AnnualPhoto(
    val photo: VisitPhoto,
    val museum: String,
)

/**
 * 参观时间线与年度足迹。
 *
 * 日期准确和日期待补充的记录始终分组展示；待补充记录不会显示推测日期，也不会进入年度统计。
 */
@Composable
fun TimelineScreen(
    repository: MuseumRepository,
    viewModel: MuseumViewModel,
    state: ExplorationState,
    unfiled: List<Pair<String, MuseumRecord>>,
    revision: Int,
    onBack: () -> Unit,
    onOpenMuseum: (city: String, museum: String) -> Unit,
    onOpenRelic: (relicId: Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val relics = remember(repository, revision) { repository.relics() }
    val relicById = remember(relics) { relics.associateBy(Relic::id) }
    var selectedTabKey by rememberSaveable { mutableStateOf(TimelineTab.RECORDS.name) }
    val selectedTab = runCatching { TimelineTab.valueOf(selectedTabKey) }.getOrDefault(TimelineTab.RECORDS)
    var editingVisit by remember { mutableStateOf<Visit?>(null) }
    var editingUnfiled by remember { mutableStateOf<Pair<String, MuseumRecord>?>(null) }
    var deletingVisit by remember { mutableStateOf<Visit?>(null) }
    var dateTarget by remember { mutableStateOf<PendingDateTarget?>(null) }
    var viewerImage by remember { mutableStateOf<GalleryImage?>(null) }
    var sharing by remember { mutableStateOf(false) }

    fun applyDate(target: PendingDateTarget, date: LocalDate) {
        val millis = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (target.visitId == null) {
            viewModel.addVisit(target.museum, millis)
            Toast.makeText(context, "已确认到访，记录已归入时间线", Toast.LENGTH_SHORT).show()
        } else if (viewModel.setVisitDate(target.visitId, millis)) {
            Toast.makeText(context, "参观日期已补充", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "这一天已有这家博物馆的参观记录，请选择其他日期", Toast.LENGTH_LONG).show()
        }
        dateTarget = null
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TimelineHeader(onBack)
        TimelineTabs(
            selectedTab = selectedTab,
            onSelect = { selectedTabKey = it.name },
        )
        when (selectedTab) {
            TimelineTab.RECORDS -> RecordsTimeline(
                state = state,
                unfiled = unfiled,
                relicById = relicById,
                cityOfMuseum = repository::cityOfMuseum,
                onOpenMuseum = onOpenMuseum,
                onOpenRelic = onOpenRelic,
                onViewPhoto = { photo, museum ->
                    viewerImage = GalleryImage(
                        model = File(photo.path),
                        title = "参观照片",
                        subtitle = museum,
                    )
                },
                onEditVisit = { editingVisit = it },
                onDeleteVisit = { deletingVisit = it },
                onPickDate = { visit ->
                    dateTarget = PendingDateTarget(visit.id, visit.museumId)
                },
                onConfirmVisit = { visit ->
                    viewModel.confirmVisit(visit.id, null)
                    Toast.makeText(context, "已确认到访，日期待补充", Toast.LENGTH_SHORT).show()
                },
                onConfirmUnfiled = { museum ->
                    dateTarget = PendingDateTarget(null, museum)
                },
                onEditUnfiled = { editingUnfiled = it },
            )

            TimelineTab.FOOTPRINT -> AnnualFootprint(
                state = state,
                sharing = sharing,
                onViewPhoto = { annualPhoto ->
                    viewerImage = GalleryImage(
                        model = File(annualPhoto.photo.path),
                        title = "年度照片",
                        subtitle = annualPhoto.museum,
                    )
                },
                onShare = { footprint ->
                    sharing = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            shareFootprintCard(context, footprint)
                        }
                        sharing = false
                        if (!ok) {
                            Toast.makeText(context, "分享图生成失败，请稍后再试", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }
    }

    editingVisit?.let { visit ->
        val visitRelics = remember(visit.museumId, relics) {
            relics.filter { relic ->
                relic.museum.split(Regex("[、,，;/；]"))
                    .map(String::trim)
                    .contains(visit.museumId)
            }
        }
        VisitRecordSheet(
            museum = visit.museumId,
            relics = visitRelics,
            initialRecord = MuseumRecord(
                note = visit.note,
                photos = visit.photos,
                updatedAt = visit.startedAt ?: visit.id.hashCode().toLong(),
            ),
            presetRelicId = null,
            repository = repository,
            otherRelics = remember(visitRelics, relics) {
                val own = visitRelics.mapTo(HashSet()) { it.id }
                relics.filterNot { it.id in own }
            },
            visitId = visit.id,
            // 时间线里改日期就是改这次参观的日期，和「确认到访」是同一条数据。
            lastDate = visit.startedAt,
            onSave = { note, photos, startedAt ->
                viewModel.saveRecordOnDate(
                    museum = visit.museumId,
                    note = note,
                    photos = photos,
                    startedAt = startedAt,
                    visitId = visit.id,
                )
            },
            onDismiss = { editingVisit = null },
        )
    }

    editingUnfiled?.let { (museum, record) ->
        val museumRelics = remember(museum, relics) {
            relics.filter { relic ->
                relic.museum.split(Regex("[、,，;/；]"))
                    .map(String::trim)
                    .contains(museum)
            }
        }
        VisitRecordSheet(
            museum = museum,
            relics = museumRelics,
            initialRecord = record,
            presetRelicId = null,
            repository = repository,
            otherRelics = remember(museumRelics, relics) {
                val own = museumRelics.mapTo(HashSet()) { it.id }
                relics.filterNot { it.id in own }
            },
            // 「待整理」的旧记录：日期留空就明确存成待补充，不硬塞进别的哪天。
            blankDateMeansUnknown = true,
            onSave = { note, photos, startedAt ->
                viewModel.saveRecordOnDate(
                    museum = museum,
                    note = note,
                    photos = photos,
                    startedAt = startedAt,
                    visitId = null,
                )
            },
            onDismiss = { editingUnfiled = null },
        )
    }

    deletingVisit?.let { visit ->
        AlertDialog(
            onDismissRequest = { deletingVisit = null },
            title = { Text("删除这次参观？") },
            text = {
                Text(
                    "这次参观的日期、感想和照片会删除。" +
                        "已看到的文物会保留，并继续计入你的已看到记录。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeVisit(visit.id)
                        deletingVisit = null
                        Toast.makeText(
                            context,
                            "参观已删除，已看到的文物记录已保留",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                ) {
                    Text("删除参观", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingVisit = null }) { Text("取消") }
            },
        )
    }

    dateTarget?.let { target ->
        DateChoiceDialog(
            target = target,
            onChooseDate = { initialDate ->
                showDatePicker(context, initialDate) { picked ->
                    applyDate(target, picked)
                }
            },
            onUseToday = { applyDate(target, LocalDate.now()) },
            onKeepUnknown = {
                if (target.visitId == null) {
                    viewModel.addVisit(target.museum, null)
                    Toast.makeText(context, "已确认到访，日期保留为待补充", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "记录仍保留在「待整理记录」中", Toast.LENGTH_SHORT).show()
                }
                dateTarget = null
            },
            onDismiss = { dateTarget = null },
        )
    }

    ImageViewerDialog(viewerImage) { viewerImage = null }
}

@Composable
private fun TimelineHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
        }
        Column(Modifier.padding(start = 2.dp)) {
            Text(
                "我的参观",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "真实日期记录，照片和感想只保存在本机",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun TimelineTabs(selectedTab: TimelineTab, onSelect: (TimelineTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TimelineTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val modifier = Modifier
                .weight(1f)
                .semantics { this.selected = isSelected }
            if (isSelected) {
                Button(
                    onClick = { onSelect(tab) },
                    modifier = modifier,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                ) {
                    Text(tab.label, color = Color.White)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(tab) },
                    modifier = modifier,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(tab.label)
                }
            }
        }
    }
}

@Composable
private fun RecordsTimeline(
    state: ExplorationState,
    unfiled: List<Pair<String, MuseumRecord>>,
    relicById: Map<Int, Relic>,
    cityOfMuseum: (String) -> String,
    onOpenMuseum: (city: String, museum: String) -> Unit,
    onOpenRelic: (relicId: Int) -> Unit,
    onViewPhoto: (VisitPhoto, String) -> Unit,
    onEditVisit: (Visit) -> Unit,
    onDeleteVisit: (Visit) -> Unit,
    onPickDate: (Visit) -> Unit,
    onConfirmVisit: (Visit) -> Unit,
    onConfirmUnfiled: (String) -> Unit,
    onEditUnfiled: (Pair<String, MuseumRecord>) -> Unit,
) {
    val ordered = state.visits.sortedWith(VISIT_ORDER)
    val dated = ordered.filter { it.hasExactDate }
    val undated = ordered.filterNot { it.hasExactDate }
    val datedDescription = dated.joinToString(separator = "；") { visit ->
        "${formatVisitDate(visit)}，${visit.museumId}"
    }
    val undatedDescription = undated.joinToString(separator = "；") { visit ->
        "${visit.museumId}，日期待补充"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            // 注意：这里不能用 item(Modifier) —— 那个重载会把 Modifier 当成 key，
            // key 必须能存进 Bundle，否则打开时间线会直接崩溃。
            item(key = "timeline_summary") {
                val summary = buildString {
                    append("参观时间线。")
                    if (dated.isNotEmpty()) {
                        append("准确日期记录 ${dated.size} 次：$datedDescription。")
                    }
                    if (undated.isNotEmpty()) {
                        append("待补充日期记录 ${undated.size} 次：$undatedDescription。")
                    }
                    if (dated.isEmpty() && undated.isEmpty()) {
                        append("还没有参观记录。")
                    }
                }
            Text(
                "准确日期按参观时间倒序；日期不详的记录单独整理，不进入错误年份。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .semantics { contentDescription = summary },
            )
        }

        if (state.visits.isEmpty() && unfiled.isEmpty()) {
            item {
                EmptyTimelineCard()
            }
        }

        if (dated.isNotEmpty()) {
            item {
                TimelineSectionTitle(
                    title = "参观记录",
                    detail = "${dated.size} 次准确日期参观",
                )
            }
            items(dated, key = { "dated_${it.id}" }) { visit ->
                VisitTimelineCard(
                    visit = visit,
                    seen = state.sightingsForVisit(visit.id),
                    relicById = relicById,
                    onOpenMuseum = onOpenMuseum,
                    onOpenRelic = onOpenRelic,
                    onViewPhoto = onViewPhoto,
                    onEdit = { onEditVisit(visit) },
                    onDelete = { onDeleteVisit(visit) },
                    onPickDate = null,
                    onConfirm = null,
                )
            }
        }

        if (undated.isNotEmpty()) {
            item {
                TimelineSectionTitle(
                    title = "待整理记录",
                    detail = "${undated.size} 次参观 · 日期待补充",
                    warning = true,
                )
            }
            items(undated, key = { "undated_${it.id}" }) { visit ->
                VisitTimelineCard(
                    visit = visit,
                    seen = state.sightingsForVisit(visit.id),
                    relicById = relicById,
                    onOpenMuseum = onOpenMuseum,
                    onOpenRelic = onOpenRelic,
                    onViewPhoto = onViewPhoto,
                    onEdit = { onEditVisit(visit) },
                    onDelete = { onDeleteVisit(visit) },
                    onPickDate = { onPickDate(visit) },
                    onConfirm = if (visit.pendingConfirmation) ({ onConfirmVisit(visit) }) else null,
                )
            }
        }

        if (unfiled.isNotEmpty()) {
            item {
                TimelineSectionTitle(
                    title = "尚未确认到访",
                    detail = "${unfiled.size} 条感想或照片",
                )
            }
            items(unfiled, key = { "unfiled_${it.first}" }) { item ->
                UnfiledRecordCard(
                    museum = item.first,
                    record = item.second,
                    city = cityOfMuseum(item.first),
                    onConfirm = { onConfirmUnfiled(item.first) },
                    onEdit = { onEditUnfiled(item) },
                    onViewPhoto = onViewPhoto,
                )
            }
        }
    }
}

@Composable
private fun EmptyTimelineCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.CollectionsBookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            )
            Text(
                "还没有参观记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "去博物馆页标记已到访，记录会自动出现在这里。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun TimelineSectionTitle(
    title: String,
    detail: String,
    warning: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (warning) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(
                        "不计入年份",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = .55f),
        )
    }
}

@Composable
private fun VisitTimelineCard(
    visit: Visit,
    seen: List<cn.chenjie.museumexplore.model.Sighting>,
    relicById: Map<Int, Relic>,
    onOpenMuseum: (city: String, museum: String) -> Unit,
    onOpenRelic: (relicId: Int) -> Unit,
    onViewPhoto: (VisitPhoto, String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPickDate: (() -> Unit)?,
    onConfirm: (() -> Unit)?,
) {
    val seenRelics = remember(seen, relicById) {
        seen.distinctBy { it.relicId }.mapNotNull { relicById[it.relicId] }
    }
    val firstPhoto = visit.photos.firstOrNull()
    val city = visit.city.ifBlank { "城市待补充" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (visit.pendingConfirmation) "待确认到访 · ${formatVisitDate(visit)}" else formatVisitDate(visit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Place,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            city,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 3.dp),
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "手动记录",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }

            Text(
                visit.museumId,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 13.dp),
            )

            if (seenRelics.isNotEmpty()) {
                Text(
                    "本次看到 ${seenRelics.size} 件永禁文物",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(end = 4.dp),
                ) {
                    items(seenRelics, key = { it.id }) { relic ->
                        RelicThumb(relic = relic, onClick = { onOpenRelic(relic.id) })
                    }
                }
            }

            firstPhoto?.let { photo ->
                Text(
                    "首张照片",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 15.dp, bottom = 7.dp),
                )
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(photo.path))
                        .crossfade(false)
                        .build(),
                    contentDescription = "查看 ${visit.museumId} 的首张参观照片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(178.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onViewPhoto(photo, visit.museumId) },
                )
            }

            if (visit.note.isNotBlank()) {
                Text(
                    visit.note,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onOpenMuseum(visit.city, visit.museumId) },
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text("查看博物馆")
                }
                if (onPickDate != null) {
                    TextButton(onClick = onPickDate) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("补录日期")
                    }
                }
                if (onConfirm != null) {
                    TextButton(onClick = onConfirm) {
                        Text("确认到访")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("编辑记录")
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun RelicThumb(relic: Relic, onClick: () -> Unit) {
    val model = relic.localImage?.let { "file:///android_asset/$it" } ?: relic.image
    Column(
        modifier = Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = "查看文物 ${relic.name}" }
            .clickable(onClick = onClick)
            .padding(bottom = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                relic.name.take(1),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!model.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(model)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            relic.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
private fun UnfiledRecordCard(
    museum: String,
    record: MuseumRecord,
    city: String,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onViewPhoto: (VisitPhoto, String) -> Unit,
) {
    val firstPhoto = record.photos.firstOrNull()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "日期待补充 · 尚未确认到访",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        museum,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "手动记录",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
            Text(
                city.ifBlank { "城市待补充" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (record.note.isNotBlank()) {
                Text(
                    record.note,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 13.dp),
                )
            }
            firstPhoto?.let { photo ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(photo.path))
                        .crossfade(false)
                        .build(),
                    contentDescription = "查看 $museum 的未归档照片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(top = 13.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onViewPhoto(photo, museum) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                ) {
                    Text("确认到访", color = Color.White)
                }
                TextButton(onClick = onEdit) { Text("编辑感想和照片") }
            }
        }
    }
}

@Composable
private fun AnnualFootprint(
    state: ExplorationState,
    sharing: Boolean,
    onViewPhoto: (AnnualPhoto) -> Unit,
    onShare: (YearFootprint) -> Unit,
) {
    val currentYear = LocalDate.now().year
    val availableYears = remember(state, currentYear) {
        (
            state.visits
                .filter(Visit::hasExactDate)
                .mapNotNull { visit -> visit.startedAt?.let(::localDateOf)?.year }
                .distinct() + currentYear
            )
            .distinct()
            .sortedDescending()
    }
    var selectedYear by rememberSaveable { mutableIntStateOf(currentYear) }
    if (selectedYear !in availableYears && state.visits.isNotEmpty()) {
        selectedYear = availableYears.firstOrNull() ?: currentYear
    }
    val footprint = remember(state, selectedYear) {
        ExplorationOps.footprint(state, selectedYear)
    }
    val yearVisits = remember(state, selectedYear) {
        state.visits
            .filter { visit -> visit.hasExactDate && localDateOf(visit.startedAt!!).year == selectedYear }
            .sortedWith(VISIT_ORDER)
    }
    val annualPhotos = remember(yearVisits) {
        yearVisits.flatMap { visit ->
            visit.photos.map { photo -> AnnualPhoto(photo, visit.museumId) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableYears.forEach { year ->
                    if (year == selectedYear) {
                        Button(
                            onClick = { selectedYear = year },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.semantics { selected = true },
                            colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                        ) {
                            Text("$year", color = Color.White)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { selectedYear = year },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.semantics { selected = false },
                        ) {
                            Text("$year")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "$selectedYear 年度足迹",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "统计来自真实参观和已看到文物记录，不以照片数量推算。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = .84f),
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FootprintMetric("博物馆", footprint.museumCount, Modifier.weight(1f))
                        FootprintMetric("文物", footprint.relicCount, Modifier.weight(1f))
                        FootprintMetric("城市", footprint.cityCount, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            MonthlyFootprintCard(footprint)
        }

        if (footprint.unknownDateVisits > 0) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "另有 ${footprint.unknownDateVisits} 条日期待补充记录，" +
                            "不会计入 $selectedYear 年统计。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }

        item {
            AnnualPhotoWall(annualPhotos, onViewPhoto)
        }

        item {
            Button(
                onClick = { onShare(footprint) },
                enabled = !sharing,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(contentColor = Color.White),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (sharing) "正在生成分享图…" else "生成并分享年度卡片", color = Color.White)
            }
        }

        item {
            Text(
                "分享图只包含年份和汇总数字，不含姓名、照片、本机路径或外部链接。" +
                    "保存和分享都在本机完成，不需要存储权限。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun FootprintMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = .13f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value.toString(),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .84f))
        }
    }
}

@Composable
private fun MonthlyFootprintCard(footprint: YearFootprint) {
    val maxCount = footprint.monthlyVisits.maxOrNull()?.coerceAtLeast(1) ?: 1
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "按月参观",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "柱上是当月参观次数",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                footprint.monthlyVisits.forEachIndexed { index, count ->
                    val month = index + 1
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "$month 月，$count 次参观"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier.height(72.dp).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp)
                                    .height((6 + 54 * count.toFloat() / maxCount).dp)
                                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                    .background(
                                        if (count == 0) MaterialTheme.colorScheme.surfaceVariant
                                        else MaterialTheme.colorScheme.primary,
                                    ),
                            )
                        }
                        Text(
                            month.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnualPhotoWall(
    photos: List<AnnualPhoto>,
    onViewPhoto: (AnnualPhoto) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "年度照片墙",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                " ${photos.size} 张",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (photos.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    "这一年还没有参观照片。编辑参观记录后，照片会出现在这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(18.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                photos.chunked(3).forEach { rowPhotos -> AnnualPhotoRow(rowPhotos, onViewPhoto) }
            }
        }
    }
}

@Composable
private fun AnnualPhotoRow(
    photos: List<AnnualPhoto>,
    onViewPhoto: (AnnualPhoto) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        photos.forEach { annualPhoto ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(annualPhoto.photo.path))
                    .crossfade(false)
                    .build(),
                contentDescription = "查看 ${annualPhoto.museum} 的年度照片",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onViewPhoto(annualPhoto) },
            )
        }
        repeat(3 - photos.size) {
            Spacer(Modifier.weight(1f).aspectRatio(1f))
        }
    }
}

@Composable
private fun DateChoiceDialog(
    target: PendingDateTarget,
    onChooseDate: (LocalDate) -> Unit,
    onUseToday: () -> Unit,
    onKeepUnknown: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isUnfiled = target.visitId == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isUnfiled) "确认到访日期" else "补录参观日期")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${target.museum} · " +
                        if (isUnfiled) {
                            "选择本次参观的日期。确认后会归入时间线。"
                        } else {
                            "可以选择今天或过去的日期。日期不详时继续保留在待整理记录。"
                        },
                )
                OutlinedButton(
                    onClick = { onChooseDate(LocalDate.now()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("从日历选择日期")
                }
                OutlinedButton(
                    onClick = onUseToday,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text("使用今天")
                }
                TextButton(
                    onClick = onKeepUnknown,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isUnfiled) "日期记不清了，保存为待补充" else "日期记不清了，保持待补充")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun showDatePicker(
    context: Context,
    initialDate: LocalDate,
    onPicked: (LocalDate) -> Unit,
) {
    val initial = Calendar.getInstance().apply {
        set(Calendar.YEAR, initialDate.year)
        set(Calendar.MONTH, initialDate.monthValue - 1)
        set(Calendar.DAY_OF_MONTH, initialDate.dayOfMonth)
    }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            onPicked(LocalDate.of(year, month + 1, day))
        },
        initial.get(Calendar.YEAR),
        initial.get(Calendar.MONTH),
        initial.get(Calendar.DAY_OF_MONTH),
    ).apply {
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}

private fun formatVisitDate(visit: Visit): String {
    if (!visit.hasExactDate) return "日期待补充"
    val startedAt = visit.startedAt ?: return "日期待补充"
    val date = localDateOf(startedAt)
    return "${date.year}年${date.monthValue}月${date.dayOfMonth}日 · ${weekdayLabel(date.dayOfWeek)}"
}

private fun localDateOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun weekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "星期一"
    DayOfWeek.TUESDAY -> "星期二"
    DayOfWeek.WEDNESDAY -> "星期三"
    DayOfWeek.THURSDAY -> "星期四"
    DayOfWeek.FRIDAY -> "星期五"
    DayOfWeek.SATURDAY -> "星期六"
    DayOfWeek.SUNDAY -> "星期日"
}

/**
 * 生成只含汇总信息的本地分享图。
 *
 * file_paths.xml 目前只暴露 filesDir/visit_photos，因此先按要求写 cacheDir；
 * 若 FileProvider 拒绝缓存目录，就复制到已配置目录再分享，全程不需要存储权限。
 */
private fun shareFootprintCard(context: Context, footprint: YearFootprint): Boolean {
    return runCatching {
        val cacheFile = File(
            context.cacheDir,
            "${FOOTPRINT_CARD_FILENAME}_${footprint.year}.png",
        )
        cacheFile.parentFile?.mkdirs()
        FileOutputStream(cacheFile).use { output ->
            createFootprintBitmap(footprint).compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = runCatching {
            FileProvider.getUriForFile(context, authority, cacheFile)
        }.getOrElse {
            val shareDir = File(context.filesDir, "visit_photos/shares").apply { mkdirs() }
            val shareFile = File(shareDir, "${FOOTPRINT_CARD_FILENAME}_${footprint.year}.png")
            cacheFile.copyTo(shareFile, overwrite = true)
            FileProvider.getUriForFile(context, authority, shareFile)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "我的 ${footprint.year} 年度博物馆足迹")
            clipData = ClipData.newRawUri("年度足迹", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "分享年度足迹")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(chooser)
        }.getOrElse {
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/png")
                clipData = ClipData.newRawUri("年度足迹", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(view)
        }
        true
    }.getOrDefault(false)
}

private fun createFootprintBitmap(footprint: YearFootprint): Bitmap {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.rgb(245, 242, 236))

    val primary = AndroidColor.rgb(182, 75, 59)
    val ink = AndroidColor.rgb(41, 39, 34)
    val muted = AndroidColor.rgb(129, 122, 112)
    val sand = AndroidColor.rgb(234, 226, 214)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = primary
    paint.textSize = 34f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("博迹 · 年度足迹", 80f, 105f, paint)

    paint.color = ink
    paint.textSize = 150f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText(footprint.year.toString(), 80f, 285f, paint)

    paint.textSize = 40f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.color = muted
    canvas.drawText("这一年的博物馆记忆", 84f, 348f, paint)

    drawMetric(canvas, paint, 180f, 515f, footprint.museumCount, "到访博物馆", ink, muted)
    drawMetric(canvas, paint, 540f, 515f, footprint.relicCount, "看到文物", ink, muted)
    drawMetric(canvas, paint, 900f, 515f, footprint.cityCount, "去过城市", ink, muted)

    paint.color = sand
    canvas.drawRoundRect(RectF(80f, 590f, 1000f, 598f), 4f, 4f, paint)

    paint.color = ink
    paint.textSize = 38f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("按月参观", 80f, 680f, paint)

    val maxCount = (footprint.monthlyVisits.maxOrNull() ?: 0).coerceAtLeast(1)
    val chartLeft = 80f
    val chartTop = 770f
    val chartBottom = 1115f
    val chartWidth = 920f
    val slot = chartWidth / 12f
    footprint.monthlyVisits.forEachIndexed { index, count ->
        val left = chartLeft + index * slot + 9f
        val right = chartLeft + (index + 1) * slot - 9f
        val barHeight = 18f + (chartBottom - chartTop - 18f) * count.toFloat() / maxCount
        paint.color = if (count == 0) sand else primary
        canvas.drawRoundRect(
            RectF(left, chartBottom - barHeight, right, chartBottom),
            12f,
            12f,
            paint,
        )
        paint.color = muted
        paint.textSize = 26f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(count.toString(), (left + right) / 2f, chartBottom - barHeight - 18f, paint)
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText((index + 1).toString(), (left + right) / 2f, chartBottom + 38f, paint)
    }
    paint.textAlign = Paint.Align.LEFT

    paint.color = muted
    paint.textSize = 28f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    if (footprint.unknownDateVisits > 0) {
        canvas.drawText(
            "另有 ${footprint.unknownDateVisits} 条日期待补充记录，未计入本年度。",
            80f,
            1222f,
            paint,
        )
    }
    canvas.drawText("仅包含博物馆、文物与城市汇总数据", 80f, 1284f, paint)
    return bitmap
}

private fun drawMetric(
    canvas: AndroidCanvas,
    paint: Paint,
    centerX: Float,
    top: Float,
    value: Int,
    label: String,
    ink: Int,
    muted: Int,
) {
    paint.textAlign = Paint.Align.CENTER
    paint.color = ink
    paint.textSize = 94f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText(value.toString(), centerX, top, paint)
    paint.color = muted
    paint.textSize = 28f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    canvas.drawText(label, centerX, top + 48f, paint)
    paint.textAlign = Paint.Align.LEFT
}
