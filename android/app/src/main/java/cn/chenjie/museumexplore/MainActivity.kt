package cn.chenjie.museumexplore

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import okhttp3.OkHttpClient
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import cn.chenjie.museumexplore.data.MuseumRepository
import cn.chenjie.museumexplore.model.CitySummary
import cn.chenjie.museumexplore.model.MuseumInfo
import cn.chenjie.museumexplore.model.Relic
import cn.chenjie.museumexplore.ui.GalleryImage
import cn.chenjie.museumexplore.ui.ImageViewerDialog
import cn.chenjie.museumexplore.ui.MuseumTheme
import cn.chenjie.museumexplore.ui.MuseumViewModel
import cn.chenjie.museumexplore.ui.MuseumVisitRecordSection
import cn.chenjie.museumexplore.ui.RelicImageCarousel
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
    data object Collection : AppDestination("collection", "国宝")
    data object Map : AppDestination("map", "地图")
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
                                .header("User-Agent", "BoJi/0.5.2 (Android museum exploration app)")
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
    var mapKeyDialog by remember { mutableStateOf(false) }
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
    val swipeTabsEnabled = currentRoute == "collection" || currentRoute == "map" || currentRoute == "more"
    val activeTab = when {
        currentRoute.startsWith("map") || currentRoute.startsWith("city") || currentRoute.startsWith("museum") -> 1
        currentRoute == "more" -> 2
        else -> 0
    }
    val destinations = listOf(AppDestination.Collection, AppDestination.Map, AppDestination.More)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = activeTab == index,
                        onClick = { navigateTab(navController, destination.route) },
                        icon = { Icon(destinationIcon(index), contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .then(
                    if (swipeTabsEnabled) {
                        Modifier.pointerInput(activeTab) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount < -72) navigateTab(navController, destinations[(activeTab + 1).coerceAtMost(2)].route)
                                if (dragAmount > 72) navigateTab(navController, destinations[(activeTab - 1).coerceAtLeast(0)].route)
                            }
                        }
                    } else Modifier,
                ),
        ) {
            NavHost(
                navController = navController,
                startDestination = AppDestination.Collection.route,
                enterTransition = { fadeIn(tween(120)) },
                exitTransition = { fadeOut(tween(80)) },
                popEnterTransition = { fadeIn(tween(100)) },
                popExitTransition = { fadeOut(tween(70)) },
            ) {
                composable("collection") { CollectionScreen(repository, revision, { navController.navigate("relic/${it.id}") }) }
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
                            onMap = { navController.navigate("map") },
                        )
                    }
                }
                composable("map") { MapScreen(repository, viewModel, navController) }
                composable("city/{city}") { entry ->
                    val city = Uri.decode(entry.arguments?.getString("city").orEmpty())
                    CityMuseumsScreen(repository, city, navController)
                }
                composable("museum/{city}/{museum}") { entry ->
                    val city = Uri.decode(entry.arguments?.getString("city").orEmpty())
                    val museum = Uri.decode(entry.arguments?.getString("museum").orEmpty())
                    MuseumDetailScreen(repository, viewModel, city, museum, revision, navController)
                }
                composable("more") {
                    MoreScreen(
                        repository = repository,
                        onMapKey = { mapKeyDialog = true },
                        onOpenMapConsole = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.amap.com/dev/key/app")))
                        },
                        onExport = { exportLauncher.launch("museum-explore-backup.zip") },
                        onImport = { importLauncher.launch(arrayOf("application/zip")) },
                        onAbout = { aboutDialog = true },
                        onProtocol = { protocolDialog = true },
                    )
                }
            }
        }
    }

    if (mapKeyDialog) MapKeyDialog(repository.mapKey(), { viewModel.saveMapKey(it); mapKeyDialog = false }, { mapKeyDialog = false }, {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.amap.com/dev/key/app")))
    })
    if (aboutDialog) InfoDialog("博物馆探索", "195 件永禁出境文物\n本地记录版\n版本 0.5.2\n\n城市、照片、感想和收集记录默认保存在本机。地图服务需要用户自行配置高德 Key。") { aboutDialog = false }
    if (protocolDialog) InfoDialog("隐私与协议", "本应用的打卡、照片和感想默认只保存在本机。高德地图仅在用户配置 Key 并打开地图时使用网络服务，具体数据处理以高德地图服务协议和隐私政策为准。\n\nWikimedia Commons 图片按数据中的许可信息使用。") { protocolDialog = false }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("博物馆探索", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("正在整理你的国宝清单…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun CollectionScreen(repository: MuseumRepository, revision: Int, onRelicClick: (Relic) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CollectionFilter.All) }
    var filterMenu by remember { mutableStateOf(false) }
    val relics = remember(repository, revision, query, filter) {
        repository.relics().filter { relic ->
            val matchesQuery = query.isBlank() || relic.name.contains(query) || relic.museum.contains(query)
            val matchesFilter = when (filter) {
                CollectionFilter.All -> true
                CollectionFilter.Collected -> repository.isCollected(relic.id)
                CollectionFilter.Uncollected -> !repository.isCollected(relic.id)
            }
            matchesQuery && matchesFilter
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PageHeader("THE COLLECTION · 195", "国宝总览", "把 195 件永禁出境文物，放进自己的清单里。")
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("搜索国宝") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
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
        Text("${filter.label}    ${relics.size} 件 · ${repository.collectedCount()} / 195 已收集", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp, 18.dp, 20.dp, 8.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(14.dp, 0.dp, 14.dp, 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(relics, key = { it.id }) { relic -> RelicCard(relic, repository.isCollected(relic.id), onRelicClick) }
        }
    }
}

private enum class CollectionFilter(val label: String, val shortLabel: String) {
    All("全部国宝", "全部"),
    Collected("已收集国宝", "已收集"),
    Uncollected("未收集国宝", "未收集"),
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
private fun RelicCard(relic: Relic, collected: Boolean, onClick: (Relic) -> Unit) {
    Card(onClick = { onClick(relic) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) {
        Column(Modifier.padding(9.dp)) {
            RelicImage(relic, Modifier.fillMaxWidth().height(112.dp))
            Text(if (collected) "已收集" else "未收集", style = MaterialTheme.typography.labelSmall, color = if (collected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
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
    onMap: () -> Unit,
) {
    val collected = repository.isCollected(relic.id)
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
        PageHeader("‹ 返回国宝总览", relic.name, "${relic.era} · ${relic.category}", onBack)
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
        LocationCard(relic, repository, onMap)
        Button(
            onClick = onMap,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(contentColor = Color.White),
        ) { Text("查看地图", color = Color.White) }
        SectionLabel("完整故事")
        Text(relic.story, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 22.dp))
        Row(Modifier.fillMaxWidth().padding(22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { viewModel.setCollected(relic.id, !collected) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(if (collected) "取消收集" else "收集") }
            Button(
                onClick = { onOpenMuseumRecord(relic.city, primaryMuseum) },
                enabled = primaryMuseum.isNotBlank(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text("去博物馆记录", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.height(24.dp))
    }
    ImageViewerDialog(viewerImage) { viewerImage = null }
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
        PageHeader("CITY / $city", city, "已去过的馆优先 · 未去馆按国宝数量排序") { navController.popBackStack() }
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
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(placeholderBrush(museum.hashCode())), contentAlignment = Alignment.Center) { Text("博", color = Color.White, style = MaterialTheme.typography.titleLarge) }
            Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(museum, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("$city · ${repository.relicCount(museum)} 件永禁文物", style = MaterialTheme.typography.bodyMedium) }
            Text(if (visited) "已去" else "待去", color = if (visited) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
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
    val relics = remember(city, museum) { repository.relicsForMuseum(city, museum) }
    val record = remember(revision, museum) { repository.museumRecord(museum) }
    val relicNames = remember(relics) { relics.associate { it.id to it.name } }
    val visited = repository.isVisited(museum)
    val hero = remember(relics) { relics.firstOrNull { !it.localImage.isNullOrBlank() || !it.image.isNullOrBlank() } ?: relics.firstOrNull() }
    var showRecordSheet by rememberSaveable(museum) { mutableStateOf(false) }
    var viewerImage by remember { mutableStateOf<GalleryImage?>(null) }
    val heroImage = hero?.let {
        GalleryImage(it.localImage?.let { local -> "file:///android_asset/$local" } ?: it.image ?: return@let null, "官方资料图", it.name)
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        item {
            PageHeader("${city.uppercase(Locale.getDefault())} · VISIT", museum, "$city · ${repository.relicCount(museum)} 件永禁文物 · ${if (visited) "已去过" else "准备出发"}") { navController.popBackStack() }
        }
        if (heroImage != null) {
            item {
                RelicImageCarousel(listOf(heroImage), Modifier.padding(horizontal = 14.dp)) { viewerImage = it }
            }
        }
        item { InfoCard(info, city) }
        item { SectionLabel("关于这座博物馆") }
        item {
            Text(
                info?.intro ?: "$museum 收藏了本次主线中的 ${repository.relicCount(museum)} 件永禁出境文物。建议出发前查看官方开放公告，并预留足够时间参观。",
                modifier = Modifier.padding(horizontal = 22.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item {
            Button(
                onClick = { viewModel.setVisited(museum, !visited) },
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { Text(if (visited) "取消打卡" else "标记已去", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        item { SectionLabel("我的记录") }
        item {
            MuseumVisitRecordSection(
                record = record,
                relicNames = relicNames,
                onEdit = { showRecordSheet = true },
                onPhotoClick = { photo ->
                    val relicName = photo.relicId?.let(relicNames::get)
                    viewerImage = GalleryImage(
                        model = File(photo.path),
                        title = "我的照片",
                        subtitle = relicName?.let { "关联：$it" } ?: "未关联国宝",
                    )
                },
            )
        }
        item { SectionLabel("馆藏国宝") }
        items(relics, key = { it.id }) { relic ->
            RelicCard(relic, repository.isCollected(relic.id)) { navController.navigate("relic/${relic.id}") }
            Spacer(Modifier.height(10.dp))
        }
        item { Spacer(Modifier.height(20.dp)) }
    }

    if (showRecordSheet) {
        VisitRecordSheet(
            museum = museum,
            relics = relics,
            initialRecord = record,
            presetRelicId = null,
            repository = repository,
            onSave = { note, photos -> viewModel.saveMuseumRecord(museum, note, photos) },
            onDismiss = { showRecordSheet = false },
        )
    }
    ImageViewerDialog(viewerImage) { viewerImage = null }
}

@Composable
private fun InfoCard(info: MuseumInfo?, city: String) {
    Card(Modifier.fillMaxWidth().padding(14.dp, 10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("时间   ${info?.openingHours ?: "每天 9:00—17:00 · 以馆方公告为准"}")
            Text("地址   ${info?.address ?: "$city · 具体地址待补充"}")
            Text("预约   ${info?.reservation ?: "以馆方当日公告为准"}")
            info?.miniProgram?.let { Text("小程序   $it") }
            info?.wikiUrl?.let { Text("资料   Wikipedia") }
        }
    }
}

@Composable
private fun MapScreen(repository: MuseumRepository, viewModel: MuseumViewModel, navController: NavHostController) {
    var selectedCity by rememberSaveable { mutableStateOf("") }
    val key = repository.mapKey()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PageHeader("EXPLORE / MAP", "去哪里看国宝", if (selectedCity.isEmpty()) "绿色是走过的地方，朱砂色是下一站。" else "$selectedCity · 博物馆地图")
        if (key.isBlank()) {
            Card(Modifier.fillMaxWidth().padding(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("地图需要高德 Android Key", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Key 只保存在本机，不会上传。请到“更多”里申请和保存。", modifier = Modifier.padding(top = 8.dp))
                    Button(
                        onClick = { navController.navigate("more") },
                        modifier = Modifier.padding(top = 12.dp),
                        colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                    ) { Text("去更多设置", color = Color.White) }
                }
            }
        } else {
            AmapView(repository, selectedCity, key, Modifier.fillMaxWidth().height(280.dp))
        }
        if (selectedCity.isEmpty()) {
            LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(repository.cities(), key = { it.name }) { city -> CityMapCard(city) { selectedCity = city.name } }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { selectedCity = "" }) { Text("‹ 返回城市") }; Text(selectedCity, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(repository.museumNames(selectedCity)) { museum -> MuseumRow(repository, selectedCity, museum) { navController.navigate("museum/${Uri.encode(selectedCity)}/${Uri.encode(museum)}") } }
            }
        }
    }
}

@Composable
private fun CityMapCard(city: CitySummary, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(city.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${city.relicCount} 件国宝", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
            Text("${city.visitedCount} / ${city.museumCount} 家博物馆已去", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).padding(top = 0.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { Box(Modifier.fillMaxWidth(if (city.museumCount == 0) 0f else city.visitedCount.toFloat() / city.museumCount).height(5.dp).background(MaterialTheme.colorScheme.secondary)) }
        }
    }
}

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
    val coords = mapOf("北京" to LatLng(39.9042, 116.4074), "西安" to LatLng(34.3416, 108.9398), "上海" to LatLng(31.2304, 121.4737), "杭州" to LatLng(30.2741, 120.1551), "武汉" to LatLng(30.5928, 114.3055), "长沙" to LatLng(28.2282, 112.9388), "成都" to LatLng(30.5728, 104.0668), "沈阳" to LatLng(41.8057, 123.4315))
    coords.filter { city.isBlank() || it.key == city }.forEach { (name, center) -> repository.museumNames(name).forEachIndexed { index, museum -> map.addMarker(MarkerOptions().position(LatLng(center.latitude + index * .035, center.longitude + index * .045)).title(museum).snippet("${repository.relicCount(museum)} 件永禁文物").icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_museum_pin))) } }
}

private fun mapCenter(city: String): LatLng = mapOf("北京" to LatLng(39.9042, 116.4074), "上海" to LatLng(31.2304, 121.4737), "杭州" to LatLng(30.2741, 120.1551), "西安" to LatLng(34.3416, 108.9398), "武汉" to LatLng(30.5928, 114.3055), "长沙" to LatLng(28.2282, 112.9388), "成都" to LatLng(30.5728, 104.0668), "沈阳" to LatLng(41.8057, 123.4315))[city] ?: LatLng(35.86, 104.2)

@Composable
private fun MoreScreen(repository: MuseumRepository, onMapKey: () -> Unit, onOpenMapConsole: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onAbout: () -> Unit, onProtocol: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.background)) {
        PageHeader("MUSEUM EXPLORER · 01", "更多", "保持简单，所有设置都在本机。")
        SectionLabel("地图与数据")
        SettingsRow("地图服务", "${if (repository.mapKey().isBlank()) "设置" else "已设置"}高德地图 Key", Icons.Outlined.Settings, onMapKey)
        TextButton(onClick = onOpenMapConsole, modifier = Modifier.padding(horizontal = 18.dp)) { Text("去高德控制台申请 Key  ↗") }
        SettingsRow("导出备份", "保存照片、打卡和感想", Icons.Outlined.UploadFile, onExport)
        SettingsRow("导入备份", "从 ZIP 恢复本机记录", Icons.Outlined.CollectionsBookmark, onImport)
        SectionLabel("关于")
        SettingsRow("关于与更新", "版本 0.5.2 · 检查更新", Icons.Outlined.Info, onAbout)
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

@Composable
private fun MapKeyDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit, onOpenConsole: () -> Unit) {
    var key by remember { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("地图服务") }, text = { Column { Text("Key 仅保存在本机，不会上传。申请时绑定 Android 平台、包名和 SHA1。"); OutlinedTextField(key, { key = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), placeholder = { Text("粘贴高德 Android SDK Key") }); TextButton(onClick = onOpenConsole, modifier = Modifier.padding(top = 4.dp)) { Text("打开高德申请页") } } }, confirmButton = { TextButton(onClick = { onSave(key.trim()) }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }) }

private fun navigateTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId) { saveState = false }
        launchSingleTop = true
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
