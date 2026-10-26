package cn.chenjie.museumexplore.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.chenjie.museumexplore.data.MuseumRepository
import cn.chenjie.museumexplore.data.VisitDateInput
import cn.chenjie.museumexplore.model.MuseumRecord
import cn.chenjie.museumexplore.model.Relic
import cn.chenjie.museumexplore.model.VisitPhoto
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DraftPhoto(
    val path: String,
    val relicId: Int?,
)

@Composable
fun MuseumVisitRecordSection(
    record: MuseumRecord,
    relicNames: Map<Int, String>,
    onEdit: () -> Unit,
    onPhotoClick: (VisitPhoto) -> Unit,
    /** 删除这条记录。为空时不显示删除入口。 */
    onDelete: (() -> Unit)? = null,
    /** 记录更新时刻为空时（例如刚补的一次到访），用这次到访的日期顶上。 */
    fallbackTime: Long = 0L,
) {
    // 空记录也照样出卡片：一次到访对应用一张卡片，卡片数必须和「已到访 N 次」对得上。
    val updatedAt = record.updatedAt.takeIf { it > 0L } ?: fallbackTime
    if (record.isEmpty) {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("我的参观记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (updatedAt > 0L) {
                    Text(
                        SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(updatedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "写下这次参观的感想，拍下展厅里打动你的永禁文物。照片可以选择关联到对应文物。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Button(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                ) { Text("写感想 / 添加照片", color = Color.White) }
            }
        }
        return
    }

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("我的参观记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (updatedAt > 0L) {
                        Text(
                            SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(updatedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (record.note.isNotBlank() || record.photos.isNotEmpty()) {
                    TextButton(onClick = onEdit) { Text("编辑") }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (record.note.isNotBlank()) {
                Text(record.note, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp))
            }
            if (record.photos.isNotEmpty()) {
                Text(
                    "${record.photos.size} 张照片",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 14.dp, bottom = 7.dp),
                )
                // 横向单行滚动：拍十几张也不用把详情页拉成一条长龙。
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 4.dp),
                ) {
                    items(record.photos, key = { it.path }) { photo ->
                        VisitPhotoThumb(
                            photo = photo,
                            relicName = photo.relicId?.let(relicNames::get) ?: "未关联文物",
                            onClick = { onPhotoClick(photo) },
                        )
                    }
                }
            }
        }
    }
}

/** 记录里的单张照片：固定尺寸，横向滚动时宽度一致。 */
@Composable
private fun VisitPhotoThumb(photo: VisitPhoto, relicName: String, onClick: () -> Unit) {
    Box(
        Modifier
            .width(112.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF0EBE3))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(photo.path))
                .crossfade(false)
                .build(),
            contentDescription = relicName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            color = Color.Black.copy(alpha = .62f),
            shape = RoundedCornerShape(50),
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
        ) {
            Text(
                relicName,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * 参观记录编辑面板。
 *
 * 交互约定：拍照或选完相册照片后立即落盘，不需要用户再点一次保存——
 * 现场拍十几张照片还要滑到底部点按钮，是这个界面最容易让人放弃的地方。
 * 顶部保留「存一份」给只写文字的人，底部最后一行也留一个保存，防止长列表里找不到入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitRecordSheet(
    museum: String,
    relics: List<Relic>,
    initialRecord: MuseumRecord,
    presetRelicId: Int?,
    repository: MuseumRepository,
    museumId: String = museum,
    /** 其他馆的文物。默认只列本馆馆藏，出差看到别家的文物再展开这里。 */
    otherRelics: List<Relic> = emptyList(),
    /** 正在编辑的到访；null 表示这次保存等于新建一次参观。 */
    visitId: String? = null,
    /**
     * 日期留空时怎么落账：
     * - false（默认，沿用旧行为）：写进这座馆现有的记录，一条都没有就建「日期待补充」。
     * - true：日期留空就明确当作「日期待补充」，新建一次到访，不碰已有记录。
     * 用户在时间线上点「记一次以前的参观」时要的是后者。
     */
    blankDateMeansUnknown: Boolean = false,
    /** 上次用过的日期，给「再去了一趟」提供一键复用。 */
    lastDate: Long? = null,
    /** 交回 (感想, 照片, 参观日期)。日期为 null 表示用户留空/填了看不懂的内容。 */
    onSave: (String, List<VisitPhoto>, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by rememberSaveable(museum, initialRecord.updatedAt, visitId) { mutableStateOf(initialRecord.note) }
    // 新建时默认就是今天：绝大多数人是逛完当场记，填日期只为了改掉它。
    var dateText by rememberSaveable(museum, initialRecord.updatedAt, visitId) {
        mutableStateOf(
            when {
                visitId == null -> VisitDateInput.format(System.currentTimeMillis())
                initialRecord.updatedAt > 0L -> VisitDateInput.format(initialRecord.updatedAt)
                else -> ""
            },
        )
    }
    var photos by remember(museum, initialRecord.updatedAt) {
        mutableStateOf(initialRecord.photos.map { DraftPhoto(it.path, it.relicId) })
    }
    var importing by remember { mutableStateOf(false) }
    var associatingIndex by remember { mutableStateOf<Int?>(null) }
    var cameraPath by remember { mutableStateOf<String?>(null) }
    val relicNames = remember(relics) { relics.associate { it.id to it.name } }

    val dateRaw = dateText.trim()
    val parsedDate = remember(dateRaw) { if (dateRaw.isEmpty()) null else VisitDateInput.parse(dateRaw) }
    val dateInvalid = dateRaw.isNotEmpty() && parsedDate == null
    // 日期只决定「算哪一天」，不阻塞保存：解析不了就按留空处理，并在上面说清楚。
    val startedAt = parsedDate

    fun commit(silent: Boolean) {
        val savedPhotos = photos.map { VisitPhoto(it.path, it.relicId) }
        onSave(note, savedPhotos, startedAt)
        if (!silent) {
            Toast.makeText(
                context,
                if (note.isBlank() && savedPhotos.isEmpty()) "记录已清空" else "参观记录已保存",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** 照片一进列表就保存，用户关掉面板也不会丢。 */
    fun autoSave(newPhotos: List<DraftPhoto>) {
        photos = photos + newPhotos
        runCatching { commit(silent = false) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val imported = withContext(Dispatchers.IO) { uris.mapNotNull { repository.importPhoto(it) } }
            autoSave(imported.map { DraftPhoto(it, presetRelicId) })
            importing = false
            if (imported.size < uris.size) {
                Toast.makeText(context, "有 ${uris.size - imported.size} 张图片导入失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = cameraPath
        cameraPath = null
        if (success && path != null && repository.photoFile(path).length() > 0L) {
            autoSave(listOf(DraftPhoto(path, presetRelicId)))
        } else if (path != null) {
            repository.deletePhoto(path)
        }
    }

    fun openCamera() {
        val path = runCatching { repository.createCameraPhoto() }.getOrNull()
        if (path == null) {
            Toast.makeText(context, "无法创建照片文件", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            cameraPath = path
            cameraLauncher.launch(repository.cameraUri(path))
        }.onFailure {
            cameraPath = null
            repository.deletePhoto(path)
            Toast.makeText(context, "没有找到可用的相机应用", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        // 关掉面板前兜底保存一次：用户很容易拍完直接下滑关掉，不能指望他记得点保存。
        // 但内容没变化时不写，否则只是打开看一眼就会把上一次的记录日期刷成今天。
        onDismissRequest = {
            val savedPhotos = photos.map { VisitPhoto(it.path, it.relicId) }
            val dirty = note != initialRecord.note ||
                savedPhotos != initialRecord.photos ||
                // 只改了日期也算改动：用户专门把日期改对，不能因为没动正文就丢掉。
                (visitId != null && parsedDate != null && !sameDayAs(initialRecord.updatedAt, parsedDate))
            if (dirty) runCatching { commit(silent = true) }
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(if (photos.isEmpty()) .68f else .92f).imePadding(),
            contentPadding = PaddingValues(start = 22.dp, top = 4.dp, end = 22.dp, bottom = 28.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("记录这次参观", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            museumId,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                    // 保存按钮提到顶部：照片再多也不用往下滑才能保存。
                    Button(
                        onClick = {
                            commit(silent = false)
                            onDismiss()
                        },
                        enabled = !importing,
                        modifier = Modifier.padding(start = 10.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                    ) { Text("保存", color = Color.White) }
                }
                Spacer(Modifier.height(12.dp))
                VisitDateField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    parsed = startedAt,
                    invalid = dateInvalid,
                    editing = visitId != null,
                    // 保存过一次的馆，一键回到上个月那天，比翻日历快。
                    canReuseLast = visitId == null && lastDate != null &&
                        VisitDateInput.format(lastDate) != VisitDateInput.format(System.currentTimeMillis()),
                    onReuseLast = { lastDate?.let { dateText = VisitDateInput.format(it) } },
                    onClear = { dateText = "" },
                    // 日期留空时用户要知道这条会落到哪，不能留空就算今天。
                    blankHint = if (blankDateMeansUnknown) {
                        "留空会存成一次「日期待补充」的参观，之后可以在时间线里补上。"
                    } else {
                        "留空会写进这座馆已有的一次参观。"
                    },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp),
                    placeholder = { Text("写下此刻的感想、展厅里印象最深的一件文物……") },
                    shape = RoundedCornerShape(14.dp),
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = ::openCamera, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Outlined.PhotoCamera, null)
                        Spacer(Modifier.width(6.dp))
                        Text("拍照")
                    }
                    OutlinedButton(
                        onClick = { galleryLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, null)
                        Spacer(Modifier.width(6.dp))
                        Text("从相册添加")
                    }
                }
            }
            if (importing) {
                item {
                    Column(Modifier.padding(top = 12.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("正在导入照片…", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
            if (photos.isNotEmpty()) {
                item {
                    Text(
                        "照片关联（可选）",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 18.dp, bottom = 3.dp),
                    )
                    Text(
                        "关联后，照片会出现在对应文物详情的轮播图里。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(photos, key = { _, photo -> photo.path }) { index, photo ->
                    Card(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF0EBE3)),
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(File(photo.path)).crossfade(false).build(),
                                    contentDescription = "已选照片 ${index + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(2.dp),
                                )
                            }
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Text("照片 ${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                TextButton(
                                    onClick = { associatingIndex = index },
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                ) {
                                    Icon(Icons.Outlined.Link, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(photo.relicId?.let(relicNames::get) ?: "选择关联文物", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            IconButton(onClick = {
                                // 自动保存已经落盘，删照片要同步删掉，否则关掉面板它又会回来。
                                val removed = photos.getOrNull(index)
                                photos = photos.filterIndexed { photoIndex, _ -> photoIndex != index }
                                removed?.let { repository.deletePhoto(it.path) }
                                runCatching { commit(silent = true) }
                            }) {
                                Icon(Icons.Outlined.Close, contentDescription = "移除照片")
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        commit(silent = false)
                        onDismiss()
                    },
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                ) { Text("保存记录", color = Color.White) }
                Text(
                    "拍照或加完照片会自动保存，直接关掉也不会丢。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    val index = associatingIndex
    if (index != null && index in photos.indices) {
        PhotoAssociationDialog(
            currentRelicId = photos[index].relicId,
            relics = relics,
            otherRelics = otherRelics,
            onSelect = { relicId ->
                photos = photos.toMutableList().also { list ->
                    list[index] = list[index].copy(relicId = relicId)
                }
                associatingIndex = null
                runCatching { commit(silent = true) }
            },
            onDismiss = { associatingIndex = null },
        )
    }
}

/**
 * 「去的那天」。
 *
 * 不弹日期选择器，直接让人打字——回忆里的日期本来就是「上个月十几号」这种模糊说法，
 * 翻日历比敲 8 个数字慢。输入合法就显示归一化后的日期，非法就把原因写在下面，不偷偷改成今天。
 */
@Composable
private fun VisitDateField(
    value: String,
    onValueChange: (String) -> Unit,
    parsed: Long?,
    invalid: Boolean,
    editing: Boolean,
    canReuseLast: Boolean,
    onReuseLast: () -> Unit,
    onClear: () -> Unit,
    blankHint: String,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("去的那天", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (editing) {
                Text(
                    "改日期会同时更新到访记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("例如 2026-10-03，留空则不确定") },
                singleLine = true,
                isError = invalid,
                trailingIcon = if (value.isNotEmpty()) {
                    {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Outlined.Close, contentDescription = "清空日期", modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    null
                },
                shape = RoundedCornerShape(14.dp),
            )
            if (canReuseLast) {
                TextButton(onClick = onReuseLast, modifier = Modifier.padding(start = 6.dp)) { Text("上次那天") }
            }
        }
        Text(
            when {
                invalid -> "没看懂这个日期。可以写 2026-10-03、2026年10月3日，或者只写 10-03。"
                parsed != null -> {
                    val label = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(parsed))
                    val today = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date())
                    if (label == today) "$label · 今天" else label
                }
                else -> blankHint
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp, start = 2.dp),
        )
    }
}

/** 两个时间戳是不是同一天。用来判断「只改了日期」也算脏数据。 */
private fun sameDayAs(first: Long, second: Long): Boolean {
    if (first <= 0L) return false
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
    return fmt.format(Date(first)) == fmt.format(Date(second))
}

@Composable
private fun PhotoAssociationDialog(
    currentRelicId: Int?,
    relics: List<Relic>,
    otherRelics: List<Relic> = emptyList(),
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 默认收起，只露本馆馆藏；出差在外再去展开其他馆。
    var showOthers by rememberSaveable { mutableStateOf(false) }
    var otherQuery by rememberSaveable { mutableStateOf("") }
    val visibleOthers = remember(otherRelics, otherQuery) {
        val keyword = otherQuery.trim()
        if (keyword.isEmpty()) otherRelics
        else otherRelics.filter { it.name.contains(keyword) || it.museum.contains(keyword) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关联到哪件文物？") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = currentRelicId == null, onClick = { onSelect(null) })
                        Text("不关联文物", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                itemsIndexed(relics, key = { _, relic -> relic.id }) { _, relic ->
                    RelicChoiceRow(relic, currentRelicId, onSelect)
                }
                if (otherRelics.isNotEmpty()) {
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        TextButton(
                            onClick = { showOthers = !showOthers },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (showOthers) "收起其他馆的文物"
                                else "在其他馆看到的文物（${otherRelics.size}）",
                            )
                        }
                    }
                }
                if (showOthers) {
                    item {
                        OutlinedTextField(
                            value = otherQuery,
                            onValueChange = { otherQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            placeholder = { Text("搜文物名或博物馆") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                    itemsIndexed(visibleOthers, key = { _, relic -> relic.id }) { _, relic ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(relic.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = currentRelicId == relic.id, onClick = { onSelect(relic.id) })
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(relic.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${relic.museum} · ${relic.era}",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (visibleOthers.isEmpty()) {
                        item {
                            Text(
                                "没找到匹配的文物",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RelicChoiceRow(relic: Relic, currentRelicId: Int?, onSelect: (Int?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(relic.id) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = currentRelicId == relic.id, onClick = { onSelect(relic.id) })
        Column(Modifier.padding(start = 4.dp)) {
            Text(relic.name, fontWeight = FontWeight.Bold)
            Text(relic.era, style = MaterialTheme.typography.labelSmall)
        }
    }
}
