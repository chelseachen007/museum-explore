package cn.chenjie.museumexplore

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.util.Log
// 高德地图暂时不用（v0.7）：相关代码保留但注释，恢复时把这一段 import 和
// MapScreen 里的 AmapView / addMuseumMarkers / mapCenter 一起放开即可。
// import com.amap.api.maps.AMap
// import com.amap.api.maps.CameraUpdateFactory
// import com.amap.api.maps.MapView
// import com.amap.api.maps.MapsInitializer
// import com.amap.api.maps.model.BitmapDescriptorFactory
// import com.amap.api.maps.model.LatLng
// import com.amap.api.maps.model.MarkerOptions
import okhttp3.OkHttpClient
import cn.chenjie.museumexplore.data.MuseumRepository
import cn.chenjie.museumexplore.model.CitySummary
import cn.chenjie.museumexplore.model.DatePrecision
import cn.chenjie.museumexplore.model.MuseumBranch
import cn.chenjie.museumexplore.model.MuseumInfo
import cn.chenjie.museumexplore.model.OfficialEntry
import cn.chenjie.museumexplore.model.Relic
import cn.chenjie.museumexplore.model.VISIT_ORDER
import cn.chenjie.museumexplore.model.Visit
import cn.chenjie.museumexplore.model.VisitTarget
import cn.chenjie.museumexplore.model.MuseumRecord
import cn.chenjie.museumexplore.ui.GalleryImage
import cn.chenjie.museumexplore.ui.ImageViewerDialog
import cn.chenjie.museumexplore.ui.MapNavigator
import cn.chenjie.museumexplore.ui.MuseumDirectoryScreen
import cn.chenjie.museumexplore.ui.MuseumTheme
import cn.chenjie.museumexplore.ui.MuseumViewModel
import cn.chenjie.museumexplore.ui.MuseumVisitRecordSection
import cn.chenjie.museumexplore.ui.NextStopSection
import cn.chenjie.museumexplore.ui.RelicImageCarousel
import cn.chenjie.museumexplore.ui.TimelineScreen
import cn.chenjie.museumexplore.ui.VisitRecordSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MuseumTheme { MuseumApp() } }
    }
}

private sealed class AppDestination(val route: String, val label: String) {
    data object Collection : AppDestination("collection", "文物")
    data object Map : AppDestination("map", "去哪")
    data object More : AppDestination("more", "更多")
}

internal object MuseumImageLoaderHolder {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader.Builder(context.applicationContext)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("User-Agent", "BoJi/0.7.8 (Android museum exploration app)")
                                .build(),
                        )
                    }
                    .build()
            }
            .build()
            .also { instance = it }
    }
}

@Composable
private fun MuseumApp(viewModel: MuseumViewModel = viewModel()) {
    val navController = rememberNavController()
    val repository = viewModel.repository
    val dataReady by produceState(initialValue = false, repository) {
        withContext(Dispatchers.IO) {
            repository.relics()
            repository.museums()
        }
        value = true
    }
    if (!dataReady) {
        LoadingScreen()
        return
    }
    val revision by viewModel.revision.collectAsState()
    val context = LocalContext.current
    var aboutDialog by remember { mutableStateOf(false) }
    var protocolDialog by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { repository.exportBackup(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) context.contentResolver.openInputStream(uri)?.use(repository::importBackup)?.also { viewModel.refresh() }
    }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route.orEmpty()
    // 底部 tab 由状态直接切换，不经过 NavHost 入栈/出栈。
    // 之前每次切 tab 都 navigate + popUpTo + restoreState，栈一旦清空就会白屏；
    // 而三个主页本来就不需要回退栈，用 selectedTab 切换既不会白屏也不会重建。
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val activeTab = when {
        currentRoute.startsWith("map") || currentRoute.startsWith("city") || currentRoute.startsWith("museum") -> 1
        currentRoute == "more" || currentRoute == "timeline" || currentRoute == "museums" -> 2
        currentRoute.isEmpty() -> selectedTab
        else -> selectedTab
    }
    val destinations = remember { listOf(AppDestination.Collection, AppDestination.Map, AppDestination.More) }
    // 底部图标是 vector，每次重组重建会白白吃掉切 tab 的那几帧。
    val tabIcons = remember { destinations.indices.map(::destinationIcon) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = activeTab == index,
                        onClick = {
                            selectedTab = index
                            // 从二级页回到某个 tab 时，把栈上的详情页清掉，避免返回键又退回详情。
                            if (currentRoute.isNotEmpty() && currentRoute != "collection" &&
                                currentRoute != "map" && currentRoute != "more") {
                                navController.popBackStack(navController.graph.startDestinationId, false)
                            }
                        },
                        icon = { Icon(tabIcons[index], contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // 主页面不在导航栈上，所以返回键不会被 NavHost 拦截。
            // 在「去哪 / 更多」按返回先回到「文物」，再按才退出应用。
            BackHandler(enabled = selectedTab != 0 && (currentRoute.isEmpty() || currentRoute == "home")) {
                selectedTab = 0
            }
            // 二级页优先；栈上只有 home 占位时，按 selectedTab 画主页面。
            val onSecondaryPage = currentRoute.isNotEmpty() && currentRoute != "home"
            if (!onSecondaryPage) {
                when (selectedTab) {
                    0 -> CollectionTab(
                        repository = repository,
                        revision = revision,
                        onRelicClick = { navController.navigate("relic/${it.id}") },
                    )
                    1 -> MapTab(repository, viewModel, revision, navController)
                    else -> MoreTab(
                        repository = repository,
                        onTimeline = { navController.navigate("timeline") },
                        onMuseums = { navController.navigate("museums") },
                        onExport = { exportLauncher.launch("museum-explore-backup.zip") },
                        onImport = { importLauncher.launch(arrayOf("application/zip")) },
                        onAbout = { aboutDialog = true },
                        onProtocol = { protocolDialog = true },
                    )
                }
            }
            // 左右滑动切 tab 已去掉：它和 LazyVerticalGrid / LazyColumn 的横向手势抢事件，
            // 表现就是「滑动时页面卡一下，然后跳到别的 tab」。底部导航已经够用。
            //
            // 三个主页面（文物 / 去哪 / 更多）不再进 NavHost 栈，由 selectedTab 切换；
            // 这里只放二级页，退出即回到当前 tab，不会因为栈被清空而白屏。
            NavHost(
                navController = navController,
                startDestination = "home",
                enterTransition = { fadeIn(tween(90)) },
                exitTransition = { fadeOut(tween(60)) },
                popEnterTransition = { fadeIn(tween(90)) },
                popExitTransition = { fadeOut(tween(60)) },
            ) {
                // 空目的地：栈底占位，保证任何二级页被弹出后都有地方可回。
                composable("home") {}
                composable("relic/{id}") { entry ->
                    val relic = repository.relics().firstOrNull { it.id == entry.arguments?.getString("id")?.toIntOrNull() }
                    if (relic != null) {
                        RelicDetailScreen(
                            repository = repository,
                            viewModel = viewModel,
                            relic = relic,
                            revision = revision,
                            onBack = { navController.popBackStack() },
                            onOpenMuseumRecord = { city, museum ->
                                navController.navigate("museum/${Uri.encode(city)}/${Uri.encode(museum)}")
                            },
                            onOpenMuseum = { city, museum ->
                                navController.navigate("museum/${Uri.encode(city)}/${Uri.encode(museum)}")
                            },
                        )
                    }
                }
                composable("city/{city}") { entry ->
                    val city = Uri.decode(entry.arguments?.getString("city").orEmpty())
                    CityMuseumsScreen(repository, city, navController)
                }
                composable("museum/{city}/{museum}") { entry ->
                    val city = Uri.decode(entry.arguments?.getString("city").orEmpty())
                    val museum = Uri.decode(entry.arguments?.getString("museum").orEmpty())
                    MuseumDetailScreen(repository, viewModel, city, museum, revision, navController)
                }
                composable("museums") {
                    MuseumDirectoryScreen(
                        repository = repository,
                        viewModel = viewModel,
                        revision = revision,
                        onBack = { navController.popBackStack() },
                        onOpenMuseum = { city, museum ->
                            navController.navigate("museum/${Uri.encode(city)}/${Uri.encode(museum)}")
                        },
                    )
                }
                composable("timeline") {
                    TimelineScreen(
                        repository = repository,
                        viewModel = viewModel,
                        state = repository.exploration(),
                        unfiled = remember(revision) { repository.unfiledJournals() },
                        revision = revision,
                        onBack = { navController.popBackStack() },
                        onOpenMuseum = { city, museum ->
                            navController.navigate("museum/${Uri.encode(city)}/${Uri.encode(museum)}")
                        },
                        onOpenRelic = { relicId -> navController.navigate("relic/$relicId") },
                    )
                }
            }
        }
    }

    if (aboutDialog) InfoDialog("博迹", "195 件永禁出境文物\n本地记录版\n版本 0.7.8\n\n想去、已到访、已看到、照片、感想和参观时间线默认保存在本机。官方入口交给微信或系统浏览器打开，应用不读取你的位置。") { aboutDialog = false }
    if (protocolDialog) InfoDialog("隐私与协议", "本应用不申请定位权限，不读取或保存位置信息。到访记录只来自你的手动确认。\n\n想去、已到访、已看到、照片、感想和时间线默认只保存在本机，不上传第三方。打开官方小程序或网站时，由微信或系统浏览器处理页面内容，适用对应服务的隐私政策。\n\nWikimedia Commons 图片按数据中的许可信息使用。") { protocolDialog = false }
}

@Composable
private fun CollectionTab(
    repository: MuseumRepository,
    revision: Int,
    onRelicClick: (Relic) -> Unit,
) {
    CollectionScreen(
        repository = repository,
        revision = revision,
        onRelicClick = onRelicClick,
    )
}

@Composable
private fun MapTab(
    repository: MuseumRepository,
    viewModel: MuseumViewModel,
    revision: Int,
    navController: NavHostController,
) {
    MapScreen(repository, viewModel, revision, navController)
}

@Composable
private fun MoreTab(
    repository: MuseumRepository,
    onTimeline: () -> Unit,
    onMuseums: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAbout: () -> Unit,
    onProtocol: () -> Unit,
) {
    MoreScreen(
        repository = repository,
        onTimeline = onTimeline,
        onMuseums = onMuseums,
        onExport = onExport,
        onImport = onImport,
        onAbout = onAbout,
        onProtocol = onProtocol,
    )
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("博物馆探索", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("正在整理你的永禁文物清单…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun CollectionScreen(
    repository: MuseumRepository,
    revision: Int,
    onRelicClick: (Relic) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CollectionFilter.All) }
    var filterMenu by remember { mutableStateOf(false) }
    // 只算一遍状态快照，避免每张卡片各自回读一次全量 exploration 导致滚动掉帧。
    val state = remember(revision) { repository.exploration() }
    // 195 张卡片的「已看到 / 想看」预先算成 Set，卡片只做 O(1) 查表。
    val seenIds = remember(state) { state.sightings.mapTo(HashSet()) { it.relicId } }
    val wishedIds = remember(state) { state.wishedRelics.mapNotNullTo(HashSet()) { it.target.toIntOrNull() } }
    val allRelics = remember(repository) { repository.relics() }
    val relics = remember(allRelics, seenIds, wishedIds, query, filter) {
        allRelics.filter { relic ->
            val matchesQuery = query.isBlank() || relic.name.contains(query) || relic.museum.contains(query)
            val matchesFilter = when (filter) {
                CollectionFilter.All -> true
                CollectionFilter.Seen -> relic.id in seenIds
                CollectionFilter.Unseen -> relic.id !in seenIds
                CollectionFilter.Wished -> relic.id in wishedIds
            }
            matchesQuery && matchesFilter
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PageHeader("THE COLLECTION · 195", "永禁文物总览", "标记真正看到过的永禁文物，也把想看的留给下一次。")
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("搜索永禁文物") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.width(8.dp))
            Box {
                Button(
                    onClick = { filterMenu = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 13.dp),
                ) { Text(if (filter == CollectionFilter.All) "筛选" else filter.shortLabel, color = Color.White, maxLines = 1) }
                DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                    CollectionFilter.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                filter = option
                                filterMenu = false
                            },
                        )
                    }
                }
            }
        }
        Text(
            "${filter.label}    ${relics.size} 件 · ${seenIds.size} / ${allRelics.size} 已看到",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(20.dp, 18.dp, 20.dp, 8.dp),
        )
        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(14.dp, 0.dp, 14.dp, 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(relics, key = { it.id }) { relic ->
                RelicCard(
                    relic = relic,
                    collected = relic.id in seenIds,
                    wished = relic.id in wishedIds,
                    onClick = onRelicClick,
                )
            }
        }
    }
}

private enum class CollectionFilter(val label: String, val shortLabel: String) {
    All("全部永禁文物", "全部"),
    Seen("已看到的永禁文物", "已看到"),
    Unseen("还没看到的永禁文物", "未看到"),
    Wished("想看的永禁文物", "想看"),
}

@Composable
private fun PageHeader(eyebrow: String, title: String, subtitle: String, back: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
        if (back != null) TextButton(onClick = back, contentPadding = PaddingValues(0.dp)) { Text("‹ 返回", color = MaterialTheme.colorScheme.primary) }
        Text(eyebrow, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .7f))
    }
}

@Composable
private fun RelicCard(relic: Relic, collected: Boolean, wished: Boolean = false, onClick: (Relic) -> Unit) {
    Card(onClick = { onClick(relic) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) {
        Column(Modifier.padding(9.dp)) {
            RelicImage(relic, Modifier.fillMaxWidth().height(112.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (collected) "已看到" else "未看到",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (collected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (wished) {
                    Text(
                        "· 想看",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            Text(relic.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Text(relic.museum, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun RelicImage(relic: Relic, modifier: Modifier) {
    val context = LocalContext.current
    val imageLoader = remember(context) { MuseumImageLoaderHolder.get(context) }
    val model = relic.localImage?.let { "file:///android_asset/$it" } ?: relic.image
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(placeholderBrush(relic.id)), contentAlignment = Alignment.Center) {
        Text("实物照片", color = Color.White, style = MaterialTheme.typography.titleMedium)
        if (!model.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(false)
                    .listener(onError = { _, result -> Log.w("MuseumImage", "Failed to load $model", result.throwable) })
                    .build(),
                imageLoader = imageLoader,
                contentDescription = relic.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RelicDetailScreen(
    repository: MuseumRepository,
    viewModel: MuseumViewModel,
    relic: Relic,
    revision: Int,
    onBack: () -> Unit,
    onOpenMuseumRecord: (String, String) -> Unit,
    onOpenMuseum: (String, String) -> Unit,
) {
    val collected = repository.isCollected(relic.id)
    val wished = repository.isWishedRelic(relic.id)
    val recordedPhotos = remember(revision, relic.id) { repository.photosForRelic(relic.id) }
    val primaryMuseum = remember(relic.id) { repository.primaryMuseum(relic) }
    val galleryImages = remember(relic.localImage, relic.image, recordedPhotos) {
        buildList {
            (relic.localImage?.let { "file:///android_asset/$it" } ?: relic.image)?.let { model ->
                add(GalleryImage(model, "官方资料图", relic.name))
            }
            recordedPhotos.forEach { photo ->
                add(GalleryImage(File(photo.path), "我的照片", relic.name))
            }
        }
    }
    var viewerImage by remember { mutableStateOf<GalleryImage?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.background)) {
        PageHeader("‹ 返回永禁文物总览", relic.name, "${relic.era} · ${relic.category}", onBack)
        if (galleryImages.isNotEmpty()) {
            RelicImageCarousel(galleryImages, Modifier.padding(horizontal = 22.dp)) { viewerImage = it }
        } else {
            RelicImage(relic, Modifier.fillMaxWidth().height(230.dp).padding(horizontal = 22.dp))
        }
        if (recordedPhotos.isNotEmpty()) {
            Text(
                "你的 ${recordedPhotos.size} 张照片已加入上方轮播",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }
        SectionLabel("为什么不能出境")
        Text(relic.reason, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 22.dp))
        val openMuseum = { onOpenMuseum(relic.city, primaryMuseum) }
        LocationCard(relic, repository, openMuseum)
        Button(
            onClick = { openMuseum() },
            enabled = primaryMuseum.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(contentColor = Color.White),
        ) { Text("查看博物馆", color = Color.White) }
        SectionLabel("完整故事")
        Text(relic.story, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 22.dp))
        Button(
            onClick = { viewModel.setCollected(relic.id, !collected) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(contentColor = Color.White),
        ) { Text(if (collected) "取消看到" else "标记已看到", color = Color.White) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { viewModel.setRelicWished(relic.id, !wished) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (wished) "取消想看" else "加入想看") }
            OutlinedButton(
                onClick = { onOpenMuseumRecord(relic.city, primaryMuseum) },
                enabled = primaryMuseum.isNotBlank(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text("去博物馆记录", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.height(24.dp))
    }
    ImageViewerDialog(viewerImage) { viewerImage = null }
}

/** 还没有任何记录时的引导卡片。 */
@Composable
private fun EmptyVisitRecordCard(onAdd: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("我的参观记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "写下这次参观的感想，拍下展厅里打动你的永禁文物。照片可以选择关联到对应文物。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(contentColor = Color.White),
            ) { Text("写感想 / 添加照片", color = Color.White) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) { Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(22.dp, 18.dp, 22.dp, 8.dp)) }

@Composable
private fun LocationCard(relic: Relic, repository: MuseumRepository, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(18.dp, 16.dp, 18.dp, 9.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("现在在哪", style = MaterialTheme.typography.labelLarge)
                Text(relic.museum, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${relic.city} · ${repository.relicCountForLocations(relic.museum)} 件永禁文物", style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun CityMuseumsScreen(repository: MuseumRepository, city: String, navController: NavHostController) {
    val museums = repository.museumNames(city)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PageHeader("CITY / $city", city, "已去过的馆优先 · 未去馆按永禁文物数量排序") { navController.popBackStack() }
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(museums) { museum ->
                MuseumRow(repository, city, museum) { navController.navigate("museum/${Uri.encode(city)}/${Uri.encode(museum)}") }
            }
        }
    }
}

@Composable
private fun MuseumRow(repository: MuseumRepository, city: String, museum: String, onClick: () -> Unit) {
    val visited = repository.isVisited(museum)
    val wished = repository.isWishedMuseum(museum)
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(placeholderBrush(museum.hashCode())), contentAlignment = Alignment.Center) { Text("博", color = Color.White, style = MaterialTheme.typography.titleLarge) }
            Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(museum, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("$city · ${repository.relicCount(museum)} 件永禁文物", style = MaterialTheme.typography.bodyMedium) }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (visited) "已到访" else "未到访",
                    color = if (visited) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (wished) {
                    Text("想去", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun MuseumDetailScreen(
    repository: MuseumRepository,
    viewModel: MuseumViewModel,
    city: String,
    museum: String,
    revision: Int,
    navController: NavHostController,
) {
    val info = repository.museums()[museum]
    val officialEntry = repository.officialEntry(museum)
    val relics = remember(city, museum) { repository.relicsForMuseum(city, museum) }
    val record = remember(revision, museum) { repository.museumRecord(museum) }
    // 每次「再记一次参观」都是独立一条，详情页并列展示，不覆盖之前的。
    val records = remember(revision, museum) { repository.museumRecords(museum) }
    val relicNames = remember(relics) { relics.associate { it.id to it.name } }
    val visitTargets = remember(museum, revision) { repository.visitTargets(museum) }
    val hasBranches = visitTargets.size > 1
    val visited = repository.isVisited(museum)
    val wished = repository.isWishedMuseum(museum)
    // 有馆区的馆，整馆视角把各馆区的记录一起算上——用户眼里这还是一家馆。
    val visits = remember(revision, museum, hasBranches) {
        val state = repository.exploration()
        (if (hasBranches) state.visitsForMuseumFamily(museum) else state.visitsForMuseum(museum)).sortedWith(VISIT_ORDER)
    }
    val hero = remember(relics) { relics.firstOrNull { !it.localImage.isNullOrBlank() || !it.image.isNullOrBlank() } ?: relics.firstOrNull() }
    var showRecordSheet by rememberSaveable(museum) { mutableStateOf(false) }
    var recordTarget by rememberSaveable(museum) { mutableStateOf<String?>(null) }
    // 正在编辑哪一条到访；null 表示「新增」或者退回最近一条的旧行为。
    var editingVisitId by rememberSaveable(museum) { mutableStateOf<String?>(null) }
    var showBranchPicker by rememberSaveable(museum) { mutableStateOf(false) }
    var showVisitDateDialog by rememberSaveable(museum) { mutableStateOf(false) }
    var pendingDeleteRecord by remember(museum) { mutableStateOf<MuseumRecord?>(null) }
    var pendingOpenSheetAfterVisit by remember(museum) { mutableStateOf(false) }
    var viewerImage by remember { mutableStateOf<GalleryImage?>(null) }
    val heroImage = hero?.let {
        GalleryImage(it.localImage?.let { local -> "file:///android_asset/$local" } ?: it.image ?: return@let null, "官方资料图", it.name)
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        item {
            val status = when {
                visits.isEmpty() -> "还没到访"
                visits.any { !it.hasExactDate } -> "已到访 · 有记录待补日期"
                else -> "已到访 ${visits.size} 次"
            }
            PageHeader("${city.uppercase(Locale.getDefault())} · VISIT", museum, "$city · ${repository.relicCount(museum)} 件永禁文物 · $status") { navController.popBackStack() }
        }
        if (heroImage != null) {
            item {
                RelicImageCarousel(listOf(heroImage), Modifier.padding(horizontal = 14.dp)) { viewerImage = it }
            }
        }
        item {
            OfficialEntryCard(
                museum = museum,
                info = info,
                entry = officialEntry,
            )
        }
        item { InfoCard(museum, info, city, visitTargets) }
        item { SectionLabel("关于这座博物馆") }
        item {
            Text(
                info?.intro ?: "$museum 收藏了本次主线中的 ${repository.relicCount(museum)} 件永禁出境文物。建议出发前查看官方开放公告，并预留足够时间参观。",
                modifier = Modifier.padding(horizontal = 22.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item {
            MuseumVisitStatusCard(
                visited = visited,
                visitCount = visits.size,
                wished = wished,
                latestLabel = visits.firstOrNull()?.let(::visitDateLabel),
                onMarkVisit = {
                    if (visited) {
                        // 再记一次：先补一条到访，再直接进编辑面板写这一次的感想和照片。
                        // 之前的实现只补到访、不开面板，用户以为记录没写进去。
                        showVisitDateDialog = true
                        if (!hasBranches) pendingOpenSheetAfterVisit = true
                    } else {
                        showVisitDateDialog = true
                    }
                },
                onToggleWish = { viewModel.setMuseumWished(museum, !wished) },
            )
        }
        item { SectionLabel("我的记录") }
        if (visits.isEmpty()) {
            item {
                Text(
                    "写感想不会自动算作到访。点上面的「标记已到访」，这次记录就会出现在时间线里。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
            }
        }
        records.forEachIndexed { index, entry ->
            item(key = "record_${entry.visitId ?: index}") {
                MuseumVisitRecordSection(
                    record = entry.record,
                    relicNames = relicNames,
                    fallbackTime = visits.firstOrNull { it.id == entry.visitId }?.startedAt ?: 0L,
                    onEdit = {
                        // 编辑要落到卡片对应的那一次到访上，不能总是写最近一条。
                        recordTarget = null
                        editingVisitId = entry.visitId
                        showRecordSheet = true
                    },
                    onPhotoClick = { photo ->
                        val relicName = photo.relicId?.let(relicNames::get)
                        viewerImage = GalleryImage(
                            model = File(photo.path),
                            title = "我的照片",
                            subtitle = relicName?.let { "关联：$it" } ?: "未关联文物",
                        )
                    },
                    onDelete = if (entry.record.note.isNotBlank() || entry.record.photos.isNotEmpty()) {
                        { pendingDeleteRecord = entry.record }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        if (records.isEmpty()) {
            item { EmptyVisitRecordCard(onAdd = { if (hasBranches) showBranchPicker = true else { recordTarget = null; editingVisitId = null; showRecordSheet = true } }) }
        }
        // 没有馆藏文物的馆（自建馆、纯收藏机构）不显示这一栏，免得空着一片。
        if (relics.isNotEmpty()) {
            item { SectionLabel("馆藏永禁文物") }
            items(relics, key = { it.id }) { relic ->
                RelicCard(relic, repository.isCollected(relic.id)) { navController.navigate("relic/${relic.id}") }
                Spacer(Modifier.height(10.dp))
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }

    if (showBranchPicker) {
        BranchPickerDialog(
            museum = museum,
            targets = visitTargets,
            onPick = { branch ->
                showBranchPicker = false
                recordTarget = branch
                showRecordSheet = true
            },
            onDismiss = { showBranchPicker = false },
        )
    }
    if (showRecordSheet) {
        val branch = recordTarget
        val editingId = editingVisitId
        // 选到具体馆区时，只回填那一片的记录；否则用整馆视角。
        val sheetRecord = remember(revision, museum, branch, editingId) {
            when {
                // 从某张卡片进来的：就回填那一次到访，不能回填成最近一条。
                editingId != null -> {
                    val visit = repository.exploration().visit(editingId)
                    if (visit != null) MuseumRecord(visit.note, visit.photos, visit.startedAt ?: 0L) else MuseumRecord()
                }
                branch.isNullOrBlank() -> record
                else -> {
                    val state = repository.exploration()
                    val target = "$museum · $branch"
                    val visit = state.confirmedVisitsForMuseum(target).sortedWith(VISIT_ORDER).firstOrNull()
                    if (visit != null) MuseumRecord(visit.note, visit.photos, visit.startedAt ?: 0L) else MuseumRecord()
                }
            }
        }
        VisitRecordSheet(
            museum = museum,
            relics = relics,
            initialRecord = sheetRecord,
            presetRelicId = null,
            repository = repository,
            museumId = if (branch.isNullOrBlank()) museum else "$museum · $branch",
            // 出差时在别的馆看到文物很常见：默认只给本馆几条，需要时再展开全量。
            otherRelics = remember(relics) {
                val own = relics.mapTo(HashSet()) { it.id }
                repository.relics().filterNot { it.id in own }
            },
            onSave = { note, photos ->
                if (editingId != null) {
                    viewModel.updateVisitJournal(editingId, note, photos)
                } else {
                    viewModel.saveBranchRecord(museum, branch, note, photos)
                }
            },
            onDismiss = { showRecordSheet = false; editingVisitId = null },
        )
    }
    if (showVisitDateDialog) {
        VisitDateDialog(
            onConfirm = { startedAt ->
                val created = viewModel.addVisit(museum, startedAt)
                showVisitDateDialog = false
                if (pendingOpenSheetAfterVisit) {
                    pendingOpenSheetAfterVisit = false
                    recordTarget = null
                    // 补的这次到访还没内容，直接把面板指向它，写完就落在这张卡片上。
                    editingVisitId = created.id
                    showRecordSheet = true
                }
            },
            onDismiss = { showVisitDateDialog = false },
        )
    }
    pendingDeleteRecord?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRecord = null },
            title = { Text("删除这条记录？") },
            text = { Text("会删掉这条的感想和照片，已看到的文物和到访次数都保留。") },
            confirmButton = {
                TextButton(onClick = {
                    target.photos.forEach { runCatching { repository.deletePhoto(it.path) } }
                    viewModel.deleteMuseumRecord(museum, target)
                    pendingDeleteRecord = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteRecord = null }) { Text("保留") } },
        )
    }
    ImageViewerDialog(viewerImage) { viewerImage = null }
}

/** 记录参观前先问「去的哪一片」——同一座馆的不同馆区其实是两个地方。 */
@Composable
private fun BranchPickerDialog(
    museum: String,
    targets: List<VisitTarget>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("这次去的是哪个馆区？") },
        text = {
            LazyColumn {
                items(targets, key = { it.displayName }) { target ->
                    Text(
                        target.label ?: target.branch ?: "整个 $museum",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(target.branch ?: "") }
                            .padding(vertical = 14.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick("") }) { Text("整个 $museum") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 博物馆清单入口。
 */

private fun visitDateLabel(visit: Visit): String =
    if (visit.hasExactDate) {
        java.text.SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(java.util.Date(visit.startedAt!!))
    } else {
        "日期待补充"
    }

@Composable
private fun MuseumVisitStatusCard(
    visited: Boolean,
    visitCount: Int,
    wished: Boolean,
    latestLabel: String?,
    onMarkVisit: () -> Unit,
    onToggleWish: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(18.dp, 14.dp, 18.dp, 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                when {
                    visitCount == 0 -> "还没到访"
                    visitCount == 1 -> "已到访 1 次${latestLabel?.let { " · $it" } ?: ""}"
                    else -> "已到访 $visitCount 次 · 最近 $latestLabel"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "到访由你自己确认，不使用定位打卡。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onMarkVisit,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(contentColor = Color.White),
            ) { Text(if (visited) "再记一次参观" else "标记已到访", color = Color.White) }
            OutlinedButton(
                onClick = onToggleWish,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(if (wished) "取消想去" else "加入想去")
            }
        }
    }
}

/** 手动到访的日期选择：默认今天，可改成过去的日期，也可以明确「日期记不清了」。 */
@Composable
private fun VisitDateDialog(onConfirm: (Long?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("这次是什么时候去的？") },
        text = {
            Column {
                Text("默认是今天；补录过去的参观请选择日期。", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "不确定日期就先记为「日期待补充」，之后可以在时间线里补录。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(System.currentTimeMillis()) }) { Text("就是今天") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onConfirm(null) }) { Text("日期记不清了") }
                TextButton(onClick = {
                    val calendar = java.util.Calendar.getInstance()
                    android.app.DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val picked = java.util.Calendar.getInstance().apply {
                                set(year, month, day, 12, 0, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }
                            onConfirm(picked.timeInMillis)
                        },
                        calendar.get(java.util.Calendar.YEAR),
                        calendar.get(java.util.Calendar.MONTH),
                        calendar.get(java.util.Calendar.DAY_OF_MONTH),
                    ).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
                }) { Text("选择日期") }
            }
        },
    )
}

@Composable
private fun OfficialEntryCard(museum: String, info: MuseumInfo?, entry: OfficialEntry?) {
    val context = LocalContext.current
    var showAlternatives by remember(museum) { mutableStateOf(false) }
    val wechatAvailable = remember(context) {
        val candidates = listOf("com.tencent.mm", "com.tencent.mm.huawei")
        candidates.any { packageName ->
            runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
        }
    }

    fun openUrl(url: String?, failure: String) {
        if (url.isNullOrBlank()) {
            Toast.makeText(context, failure, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, "没有可打开该页面的应用", Toast.LENGTH_SHORT).show()
        }
    }

    fun copySearchName() {
        val name = entry?.wechatSearchName?.takeIf { it.isNotBlank() }
            ?: entry?.appName?.takeIf { it.isNotBlank() }
            ?: info?.miniProgram?.takeIf { it.isNotBlank() }
            ?: museum
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("博物馆官方名称", name))
        Toast.makeText(context, "已复制「$name」，去微信搜索", Toast.LENGTH_LONG).show()
    }

    Card(
        Modifier.fillMaxWidth().padding(14.dp, 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("官方入口", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                when {
                    entry?.landingUrl != null -> entry.appName
                    info?.officialUrl != null -> "$museum · 官方网站"
                    else -> museum
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 7.dp),
            )
            if (entry?.verifiedAt?.isNotBlank() == true) {
                Text(
                    "已核验 ${entry.verifiedAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val landingUrl = entry?.landingUrl
            val reservationUrl = entry?.reservationUrl
            val officialUrl = entry?.officialUrl ?: info?.officialUrl
            when {
                landingUrl != null -> {
                    Button(
                        onClick = {
                            if (wechatAvailable) openUrl(landingUrl, "无法打开微信小程序")
                            else {
                                Toast.makeText(context, "未安装微信，已为你保留官网入口", Toast.LENGTH_LONG).show()
                                showAlternatives = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                    ) { Text("打开官方小程序", color = Color.White) }
                    Text(
                        if (wechatAvailable) "将打开微信小程序，可能需要确认跳转" else "本机未检测到微信，可改用预约或官网",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                reservationUrl != null -> {
                    Button(
                        onClick = { openUrl(reservationUrl, "暂无官方预约链接") },
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                    ) { Text("立即预约", color = Color.White) }
                }
                officialUrl != null -> {
                    Button(
                        onClick = { openUrl(officialUrl, "暂无官方网站") },
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                    ) { Text("打开官方网站", color = Color.White) }
                }
                else -> {
                    Text(
                        info?.miniProgram?.let { "暂未接入可稳定直达的小程序。可以在微信里搜索官方名称「$it」。" }
                            ?: "暂未找到可稳定直接打开的官方入口，可以先复制馆名去微信搜索。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                    OutlinedButton(
                        onClick = { copySearchName() },
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("复制官方名称去微信搜索") }
                }
            }
            if (landingUrl != null) {
                Text(
                    "找不到小程序时，可改用官网或复制名称搜索。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (reservationUrl != null || officialUrl != null) {
                TextButton(onClick = { showAlternatives = !showAlternatives }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(if (showAlternatives) "收起其他入口" else "其他官方入口")
                }
            }
            if (showAlternatives) {
                if (landingUrl == null) reservationUrl?.let { url ->
                    OutlinedButton(
                        onClick = { openUrl(url, "暂无官方预约链接") },
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("打开官方预约页") }
                }
                if (landingUrl == null || reservationUrl == null) officialUrl?.let { url ->
                    OutlinedButton(
                        onClick = { openUrl(url, "暂无官方网站") },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("打开官方网站") }
                }
                TextButton(onClick = { copySearchName() }, modifier = Modifier.fillMaxWidth()) {
                    Text("复制官方名称去微信搜索")
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    museum: String,
    info: MuseumInfo?,
    city: String,
    branches: List<VisitTarget> = emptyList(),
) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(14.dp, 10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("时间   ${info?.openingHours ?: "待官方确认"}")
            if (branches.isEmpty()) {
                val address = info?.address
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("地址   ${address ?: "$city · 具体地址待补充"}", modifier = Modifier.weight(1f))
                    if (address != null) {
                        TextButton(onClick = { MapNavigator.navigate(context, museum, address) }) {
                            Text("导航")
                        }
                    }
                }
            } else {
                // 多馆区的馆只占一条记录，各馆区地址并排列在地址一栏里，各自可导航。
                Text("地址")
                branches.forEach { branch ->
                    val label = branch.label ?: branch.branch ?: "本馆"
                    val address = branch.address
                    Row(
                        modifier = Modifier.padding(start = 30.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "· $label：${address ?: "具体地址待补充"}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (address != null) {
                            TextButton(onClick = { MapNavigator.navigate(context, museum, address) }) {
                                Text("导航")
                            }
                        }
                    }
                }
            }
            Text("预约   ${info?.reservation ?: "待官方确认"}")
        }
    }
}

@Composable
private fun MapScreen(
    repository: MuseumRepository,
    viewModel: MuseumViewModel,
    revision: Int,
    navController: NavHostController,
) {
    val selectedCity = remember(revision) { repository.selectedCity() }
    val cities = remember(revision) { repository.cities() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PageHeader(
            "EXPLORE / NEXT STOP",
            "今天去哪",
            if (selectedCity.isBlank()) "先选城市，再决定下一站。不读取你的位置，也不显示距离。"
            else "$selectedCity · 推荐与博物馆列表",
        )
        // 地图暂时停用（高德 SDK 已注释），这里不占空间，等地图回来再换回 AmapView。
        LazyColumn(contentPadding = PaddingValues(0.dp, 0.dp, 0.dp, 24.dp)) {
            item {
                NextStopSection(
                    repository = repository,
                    viewModel = viewModel,
                    revision = revision,
                    onOpenMuseum = { city, museum ->
                        navController.navigate("museum/${Uri.encode(city)}/${Uri.encode(museum)}")
                    },
                )
            }
            item {
                SectionLabel(if (selectedCity.isBlank()) "按城市浏览" else "$selectedCity · 博物馆")
            }
            if (selectedCity.isBlank()) {
                item {
                    Text(
                        "选好城市后，这里会列出该城市的博物馆；也可以直接看上面的推荐。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                }
                items(cities, key = { it.name }) { city ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        CityMapCard(city) {
                            repository.saveSelectedCity(city.name)
                            viewModel.refresh()
                        }
                    }
                }
            } else {
                items(repository.museumNames(selectedCity)) { museum ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        MuseumRow(repository, selectedCity, museum) {
                            navController.navigate("museum/${Uri.encode(selectedCity)}/${Uri.encode(museum)}")
                        }
                    }
                }
            }
        }
    }
}

/*
 * 高德地图整块暂时停用（v0.7）。地图不是当前主线，先保列表和推荐可用；
 * 要恢复时：解开这一段、解开文件顶部的 amap import，并在 MapScreen 里把
 * 「地图暂未启用」提示卡换回 AmapView(...) 那三行。
@Composable
private fun AmapView(repository: MuseumRepository, city: String, key: String, modifier: Modifier) {
    AndroidView(factory = { context ->
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)
        MapsInitializer.setApiKey(key)
        MapView(context).also { mapView ->
            mapView.onCreate(null)
            mapView.onResume()
            val map = mapView.map
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(mapCenter(city), if (city.isBlank()) 4.3f else 11.2f))
            addMuseumMarkers(map, repository, city)
        }
    }, modifier = modifier.clip(RoundedCornerShape(18.dp)), update = {}, onRelease = { mapView ->
        mapView.onPause()
        mapView.onDestroy()
    })
}

private fun addMuseumMarkers(map: AMap, repository: MuseumRepository, city: String) {
    val coords = mapOf(
        "北京" to LatLng(39.9042, 116.4074),
        "西安" to LatLng(34.3416, 108.9398),
        "上海" to LatLng(31.2304, 121.4737),
        "杭州" to LatLng(30.2741, 120.1551),
        "武汉" to LatLng(30.5928, 114.3055),
        "长沙" to LatLng(28.2282, 112.9388),
        "成都" to LatLng(30.5728, 104.0668),
        "沈阳" to LatLng(41.8057, 123.4315),
    )
    coords.filter { city.isBlank() || it.key == city }.forEach { (name, center) ->
        repository.museumNames(name).forEachIndexed { index, museum ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(center.latitude + index * .035, center.longitude + index * .045))
                    .title(museum)
                    .snippet("${repository.relicCount(museum)} 件永禁文物")
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_museum_pin)),
            )
        }
    }
}

private fun mapCenter(city: String): LatLng = mapOf(
    "北京" to LatLng(39.9042, 116.4074),
    "上海" to LatLng(31.2304, 121.4737),
    "杭州" to LatLng(30.2741, 120.1551),
    "西安" to LatLng(34.3416, 108.9398),
    "武汉" to LatLng(30.5928, 114.3055),
    "长沙" to LatLng(28.2282, 112.9388),
    "成都" to LatLng(30.5728, 104.0668),
    "沈阳" to LatLng(41.8057, 123.4315),
)[city] ?: LatLng(35.86, 104.2)
*/

@Composable
private fun CityMapCard(city: CitySummary, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(city.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${city.relicCount} 件永禁文物", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
            Text("${city.visitedCount} / ${city.museumCount} 家博物馆已去", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).padding(top = 0.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { Box(Modifier.fillMaxWidth(if (city.museumCount == 0) 0f else city.visitedCount.toFloat() / city.museumCount).height(5.dp).background(MaterialTheme.colorScheme.secondary)) }
        }
    }
}

@Composable
private fun MoreScreen(
    repository: MuseumRepository,
    onTimeline: () -> Unit,
    onMuseums: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAbout: () -> Unit,
    onProtocol: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.background)) {
        PageHeader("MUSEUM EXPLORER · 01", "更多", "保持简单，所有设置都在本机。")
        SectionLabel("我的足迹")
        SettingsRow("我的参观", "时间线、年度足迹和待整理记录", Icons.Outlined.CalendarMonth, onTimeline)
        SettingsRow("博物馆清单", "国内 + 国外全量名录，也可以自己添加", Icons.Outlined.Museum, onMuseums)
        SectionLabel("地图与数据")
        // 地图服务设置暂时隐藏：高德 SDK 已注释，留着入口只会让用户以为地图坏了。
        SettingsRow("导出备份", "保存状态、时间线、照片和感想", Icons.Outlined.UploadFile, onExport)
        SettingsRow("导入备份", "从 ZIP 恢复本机记录", Icons.Outlined.CollectionsBookmark, onImport)
        SectionLabel("关于")
        SettingsRow("关于与更新", "版本 0.7.8 · 检查更新", Icons.Outlined.Info, onAbout)
        SettingsRow("隐私与协议", "本地存储说明、第三方服务协议", Icons.Outlined.Policy, onProtocol)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodyMedium) }; Icon(Icons.Outlined.KeyboardArrowRight, null) }
    }
}

// 高德 Key 设置面板暂时停用，恢复地图时一并放开。
/*
@Composable
private fun MapKeyDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit, onOpenConsole: () -> Unit) {
    var key by remember { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("地图服务") }, text = { Column { Text("Key 仅保存在本机，不会上传。申请时绑定 Android 平台、包名和 SHA1。"); OutlinedTextField(key, { key = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), placeholder = { Text("粘贴高德 Android SDK Key") }); TextButton(onClick = onOpenConsole, modifier = Modifier.padding(top = 4.dp)) { Text("打开高德申请页") } } }, confirmButton = { TextButton(onClick = { onSave(key.trim()) }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
*/

@Composable
private fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }) }

/**
 * 切底部 tab。
 *
 * saveState / restoreState 必须打开：原来的写法每次切 tab 都把上一个页面的组合状态整个丢掉，
 * 回到「文物」时重新构建两百多张卡片，这就是切换时那一顿的来源。
 */
private fun navigateTab(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route == route) return
    navController.navigate(route) {
        // 永远保留起始页不入栈弹出：inclusive=true 会把 collection 自己也弹掉，
        // 栈清空后 popping 回 collection 时没有目的地可画，表现就是切回来白屏。
        popUpTo(navController.graph.startDestinationId) {
            saveState = true
            inclusive = false
        }
        launchSingleTop = true
        restoreState = true
    }
}
private fun destinationIcon(index: Int) = when (index) { 0 -> Icons.Outlined.CollectionsBookmark; 1 -> Icons.Outlined.Map; else -> Icons.Outlined.MoreHoriz }
private fun placeholderBrush(id: Int): Brush {
    val palettes = listOf(
        Color(0xFF6A523D) to Color(0xFFAA8755),
        Color(0xFF5F756D) to Color(0xFFB5C3B8),
        Color(0xFF8A6549) to Color(0xFFC79B67),
        Color(0xFF76524C) to Color(0xFFB68172),
    )
    val (start, end) = palettes[kotlin.math.abs(id) % palettes.size]
    return Brush.linearGradient(listOf(start, end))
}
