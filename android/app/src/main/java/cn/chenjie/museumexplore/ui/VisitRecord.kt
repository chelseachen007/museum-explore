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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
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
) {
    if (record.isEmpty) {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("我的参观记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "写下这次参观的感想，拍下展厅里打动你的国宝。照片可以选择关联到对应国宝。",
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
                    if (record.updatedAt > 0L) {
                        Text(
                            SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(record.updatedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onEdit) { Text("编辑") }
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
                record.photos.chunked(2).forEach { rowPhotos ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowPhotos.forEach { photo ->
                            val relicName = photo.relicId?.let(relicNames::get) ?: "未关联国宝"
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF0EBE3))
                                    .clickable { onPhotoClick(photo) },
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(File(photo.path))
                                        .crossfade(false)
                                        .build(),
                                    contentDescription = relicName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(3.dp),
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
                        if (rowPhotos.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitRecordSheet(
    museum: String,
    relics: List<Relic>,
    initialRecord: MuseumRecord,
    presetRelicId: Int?,
    repository: MuseumRepository,
    onSave: (String, List<VisitPhoto>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by rememberSaveable(museum, initialRecord.updatedAt) { mutableStateOf(initialRecord.note) }
    var photos by remember(museum, initialRecord.updatedAt) {
        mutableStateOf(initialRecord.photos.map { DraftPhoto(it.path, it.relicId) })
    }
    var importing by remember { mutableStateOf(false) }
    var associatingIndex by remember { mutableStateOf<Int?>(null) }
    var cameraPath by remember { mutableStateOf<String?>(null) }
    val relicNames = remember(relics) { relics.associate { it.id to it.name } }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val imported = withContext(Dispatchers.IO) { uris.mapNotNull { repository.importPhoto(it) } }
            photos = photos + imported.map { DraftPhoto(it, presetRelicId) }
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
            photos = photos + DraftPhoto(path, presetRelicId)
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
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(if (photos.isEmpty()) .68f else .92f).imePadding(),
            contentPadding = PaddingValues(start = 22.dp, top = 4.dp, end = 22.dp, bottom = 28.dp),
        ) {
            item {
                Text("记录这次参观", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    museum,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 5.dp, bottom = 12.dp),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp),
                    placeholder = { Text("写下此刻的感想、展厅里印象最深的一件国宝……") },
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
                        "关联后，照片会出现在对应国宝详情的轮播图里。",
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
                                    Text(photo.relicId?.let(relicNames::get) ?: "选择关联国宝", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            IconButton(onClick = {
                                photos = photos.filterIndexed { photoIndex, _ -> photoIndex != index }
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
                        val savedPhotos = photos.map { VisitPhoto(it.path, it.relicId) }
                        onSave(note, savedPhotos)
                        Toast.makeText(
                            context,
                            if (note.isBlank() && savedPhotos.isEmpty()) "记录已清空" else "参观记录已保存",
                            Toast.LENGTH_SHORT,
                        ).show()
                        onDismiss()
                    },
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                ) { Text("保存记录", color = Color.White) }
            }
        }
    }

    val index = associatingIndex
    if (index != null && index in photos.indices) {
        PhotoAssociationDialog(
            currentRelicId = photos[index].relicId,
            relics = relics,
            onSelect = { relicId ->
                photos = photos.toMutableList().also { list ->
                    list[index] = list[index].copy(relicId = relicId)
                }
                associatingIndex = null
            },
            onDismiss = { associatingIndex = null },
        )
    }
}

@Composable
private fun PhotoAssociationDialog(
    currentRelicId: Int?,
    relics: List<Relic>,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关联到哪件国宝？") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = currentRelicId == null, onClick = { onSelect(null) })
                        Text("不关联国宝", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                itemsIndexed(relics, key = { _, relic -> relic.id }) { _, relic ->
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
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
