package cn.chenjie.museumexplore.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.chenjie.museumexplore.data.MuseumRepository
import cn.chenjie.museumexplore.model.MuseumRecord
import cn.chenjie.museumexplore.model.MuseumSource

/**
 * 博物馆目录：内置清单 + 自己加的馆，都在这里管。
 *
 * 目标是「去了就能记」：不管这家馆在不在 195 件文物的名单里、在国内还是国外，
 * 用户都应该能两步把它加进来并开始记录，而不是被一份写死的清单挡住。
 */
@Composable
fun MuseumDirectoryScreen(
    repository: MuseumRepository,
    viewModel: MuseumViewModel,
    revision: Int,
    onBack: () -> Unit,
    onOpenMuseum: (String, String) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var showOnlyUser by rememberSaveable { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var merging by remember { mutableStateOf(false) }
    // 从清单直接写感想：不先进详情页、也不先确认到访。
    // 用户路过某家馆想起上次来过，应该两下就能把话记下来。
    var journalMuseum by remember { mutableStateOf<String?>(null) }
    var journalBranch by remember { mutableStateOf<String?>(null) }
    // 全量条目只在数据变更时算一次；搜索和筛选在缓存结果上过滤，
    // 输入框每敲一个字都不会再回仓库层重算。
    val allEntries = remember(revision) { repository.museumEntries() }
    val entries = remember(allEntries, query, showOnlyUser) {
        val keyword = query.trim()
        allEntries.filter { entry ->
            val matchesQuery = keyword.isEmpty() ||
                entry.name.contains(keyword, ignoreCase = true) ||
                entry.city.contains(keyword, ignoreCase = true)
            matchesQuery && (!showOnlyUser || entry.source == MuseumSource.USER)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        DirectoryHeader(
            count = allEntries.size,
            onBack = onBack,
        )
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { adding = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加博物馆", color = Color.White)
                }
                OutlinedButton(onClick = { merging = true }, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.Merge, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("合并馆区")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                placeholder = { Text("搜索馆名或城市") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showOnlyUser = !showOnlyUser }, shape = RoundedCornerShape(50)) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showOnlyUser) "只看我加的" else "全部馆")
                }
                Spacer(Modifier.weight(1f))
                Text("${entries.size} 家", style = MaterialTheme.typography.labelLarge)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.name }) { entry ->
                val branchCount = remember(entry.name, revision) { repository.branches(entry.name).size }
                MuseumDirectoryRow(
                    name = entry.name,
                    city = entry.city,
                    subtitle = when (entry.source) {
                        MuseumSource.USER -> "自己添加 · ${entry.city}"
                        MuseumSource.RELIC -> "${entry.city} · ${remember(entry.name, revision) { repository.relicCount(entry.name) }} 件永禁文物"
                        MuseumSource.BUILT_IN -> entry.city
                    },
                    badge = if (branchCount > 0) "$branchCount 个馆区" else null,
                    visited = remember(entry.name, revision) { repository.isVisited(entry.name) },
                    recordCount = remember(entry.name, revision) {
                        repository.museumRecords(entry.name).count { it.record.note.isNotBlank() || it.record.photos.isNotEmpty() }
                    },
                    onClick = { onOpenMuseum(entry.city, entry.name) },
                    onWriteJournal = {
                        // 有馆区的馆先打开一个「整馆 / 某个馆区」的选择，其余直接开面板。
                        journalMuseum = entry.name
                        journalBranch = null
                    },
                    onEdit = if (entry.source == MuseumSource.USER) ({ editing = entry.name }) else null,
                )
            }
        }
    }

    // 写感想：有馆区的馆先选目标，再开编辑面板。
    journalMuseum?.let { museum ->
        val branches = remember(museum, revision) { repository.branches(museum) }
        if (branches.isNotEmpty() && journalBranch == null) {
            JournalTargetDialog(
                museum = museum,
                branches = branches.map { it.name },
                onPick = { branch -> journalBranch = branch ?: "" },
                onDismiss = { journalMuseum = null },
            )
        } else {
            val branch = journalBranch.orEmpty()
            val targetId = if (branch.isBlank()) museum else "$museum · $branch"
            // 从清单进来一律是「写下这次」：面板必须是空白的新记录。
            // 回填上一次的内容会让用户分不清哪句是这次写的，写完还容易把旧记录冲掉。
            val initial = MuseumRecord()
            // 「上次那天」要的是最近一次真的去过的那天：日期待补充的记录排在
            // VISIT_ORDER 末尾，直接取 lastOrNull 会拿到它，等于没有日期可用。
            val lastDate = remember(museum, revision) {
                val state = repository.exploration()
                val visits = if (repository.hasBranches(museum)) {
                    state.confirmedVisitsForMuseumFamily(museum)
                } else {
                    state.confirmedVisitsForMuseum(museum)
                }
                visits.filter { it.hasExactDate }.maxByOrNull { it.startedAt!! }?.startedAt
            }
            VisitRecordSheet(
                museum = museum,
                relics = remember(museum, revision) { repository.relicsForMuseum(museum) },
                initialRecord = initial,
                presetRelicId = null,
                repository = repository,
                museumId = targetId,
                // 从清单进来总是「写下这次」：不指向任何一次既有到访，保存即新建。
                visitId = null,
                blankDateMeansUnknown = true,
                lastDate = lastDate,
                onSave = { note, photos, startedAt ->
                    viewModel.saveRecordOnDate(
                        museum = museum,
                        note = note,
                        photos = photos,
                        startedAt = startedAt,
                        visitId = null,
                        branch = branch,
                    )
                },
                onDismiss = { journalMuseum = null; journalBranch = null },
            )
        }
    }

    if (adding || editing != null) {
        val existing = editing?.let { repository.userMuseums().firstOrNull { user -> user.name == it } }
        UserMuseumDialog(
            initialName = existing?.name ?: "",
            initialCity = existing?.city ?: "",
            initialUrl = existing?.officialUrl ?: "",
            initialAddress = existing?.address ?: "",
            initialNotes = existing?.notes ?: "",
            title = if (existing == null) "添加博物馆" else "编辑博物馆",
            onSave = { name, city, url, address, notes ->
                val ok = if (existing == null) {
                    viewModel.addUserMuseum(name, city, url, address, notes)
                } else {
                    viewModel.updateUserMuseum(existing.name, city, url, address, notes)
                }
                if (ok) {
                    adding = false
                    editing = null
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "这个馆名已经存在了，换个名字或者直接搜索它", Toast.LENGTH_LONG).show()
                }
            },
            onDelete = existing?.let {
                {
                    viewModel.removeUserMuseum(it.name)
                    editing = null
                }
            },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }
    if (merging) {
        MergeBranchesSheet(
            repository = repository,
            onSubmit = { parent, city, members, suffixes ->
                val ok = viewModel.mergeMuseums(parent, city, members, suffixes)
                merging = false
                Toast.makeText(
                    context,
                    if (ok) "已合并，历史记录也跟着归到「$parent」" else "合并失败，检查一下馆名",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { merging = false },
        )
    }
}

/**
 * 写感想前先确定写哪一片。
 *
 * 「整馆」传回 null，代表内容落到父馆名下——详情页是整馆视角，照样看得到。
 * 只有真的去过某个馆区的人才选馆区，不强迫所有人先做这道题。
 */
@Composable
private fun JournalTargetDialog(
    museum: String,
    branches: List<String>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("写这次是去的哪一片？") },
        text = {
            Column {
                Text(
                    "选馆区会让记录挂到那片地址上；不确定就选整馆。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                listOf<String?>(null).plus(branches).forEach { branch ->
                    Text(
                        branch ?: "整馆（不细分馆区）",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(branch) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DirectoryHeader(count: Int, onBack: () -> Unit) {    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Text("‹ 返回", color = MaterialTheme.colorScheme.primary)
        }
        Text("MUSEUMS · $count", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("博物馆清单", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
        Text("内置清单和自己加的馆都在这里。名单外的馆也可以加进来记录。", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .7f))
    }
}

@Composable
private fun MuseumDirectoryRow(
    name: String,
    city: String,
    subtitle: String,
    badge: String?,
    visited: Boolean,
    recordCount: Int,
    onClick: () -> Unit,
    onWriteJournal: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Text("博", style = MaterialTheme.typography.titleMedium) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (badge != null) {
                        Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (visited) {
                    Text("已到访", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Tune, contentDescription = "编辑") }
                }
            }
            // 写感想是这里的第二个主操作：不用先进详情页，点开就能写。
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (recordCount > 0) "已写 $recordCount 条记录" else "还没写过记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onWriteJournal) {
                    Icon(Icons.Outlined.EditNote, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (recordCount > 0) "继续写" else "写感想")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserMuseumDialog(
    initialName: String,
    initialCity: String,
    initialUrl: String,
    initialAddress: String,
    initialNotes: String,
    title: String,
    onSave: (String, String, String?, String?, String?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var city by remember { mutableStateOf(initialCity) }
    var url by remember { mutableStateOf(initialUrl) }
    var address by remember { mutableStateOf(initialAddress) }
    var notes by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("馆名（必填）") },
                    placeholder = { Text("例如：浙江省博物馆 · 之江馆区") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("城市（必填，国外填城市名）") },
                    placeholder = { Text("例如：杭州 / 伦敦") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("官网（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("地址（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.padding(top = 6.dp)) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("删除这个馆")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), city.trim(), url.ifBlank { null }, address.ifBlank { null }, notes.ifBlank { null }) },
                enabled = name.isNotBlank() && city.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 合并馆区：把同一座馆的多种叫法收成一个父馆 + 若干馆区。
 *
 * 场景就是「浙江省博物馆」和「浙江省博物馆之江馆区」被当成两家馆记了两次。
 * 提交后历史参观记录也会跟着指到父馆，不需要用户自己去改时间线。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeBranchesSheet(
    repository: MuseumRepository,
    onSubmit: (String, String, List<Pair<String, String>>, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var parent by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    val names = remember { repository.allMuseumNames() }
    val selected = remember { mutableStateListOf<String>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 640.dp).imePadding(),
            contentPadding = PaddingValues(start = 22.dp, top = 4.dp, end = 22.dp, bottom = 28.dp),
        ) {
            item {
                Text("合并同一座博物馆", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "把「浙江省博物馆」「浙江省博物馆之江馆区」这种同一座馆的不同叫法收成一家。合并后历史记录也会跟着归到父馆，不会丢。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                OutlinedTextField(
                    value = parent,
                    onValueChange = { parent = it },
                    label = { Text("父馆名（统一后的名字）") },
                    placeholder = { Text("例如：浙江省博物馆") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("城市") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
                Text("选择要并进来的馆（下面按你点选的顺序成为馆区名）", style = MaterialTheme.typography.labelLarge)
            }
            items(names, key = { it }) { name ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (selected.contains(name)) selected.remove(name) else selected.add(name)
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected.contains(name), onClick = {
                        if (selected.contains(name)) selected.remove(name) else selected.add(name)
                    })
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(name, fontWeight = FontWeight.Bold)
                        Text(
                            repository.cityOfMuseum(name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        val parentName = parent.trim().ifBlank { selected.firstOrNull().orEmpty() }
                        val members = selected
                            .filter { it != parentName }
                            .map { member ->
                                val short = member.removePrefix(parentName).trim().trim('·', '-', ' ')
                                member to short.ifBlank { member }
                            }
                        onSubmit(parentName, city.trim(), members, emptyList())
                    },
                    enabled = selected.isNotEmpty() && (parent.isNotBlank() || selected.size > 1),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                ) { Text("合并这 ${selected.size} 家", color = Color.White) }
            }
        }
    }
}

/** 把「父馆 · 馆区」拆成父馆和馆区，供参观时选择。 */
fun splitVisitDisplayName(displayName: String): Pair<String, String?> {
    val parts = displayName.split(" · ", limit = 2)
    return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        parts[0] to parts[1]
    } else {
        displayName to null
    }
}
