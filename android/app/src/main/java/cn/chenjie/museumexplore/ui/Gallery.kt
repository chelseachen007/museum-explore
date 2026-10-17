package cn.chenjie.museumexplore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import cn.chenjie.museumexplore.MuseumImageLoaderHolder
import kotlin.math.roundToInt

data class GalleryImage(
    val model: Any,
    val title: String,
    val subtitle: String? = null,
)

@Composable
fun RelicImageCarousel(
    images: List<GalleryImage>,
    modifier: Modifier = Modifier,
    onOpen: (GalleryImage) -> Unit,
) {
    if (images.isEmpty()) return
    val context = LocalContext.current
    val imageLoader = remember(context) { MuseumImageLoaderHolder.get(context) }
    val pagerState = rememberPagerState(pageCount = { images.size })
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(268.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFF0EBE3)),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val image = images[page]
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable { onOpen(image) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "图片加载中…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(image.model)
                            .crossfade(false)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = image.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                    Surface(
                        color = Color.Black.copy(alpha = .62f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    ) {
                        Text(
                            image.title,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                    Icon(
                        Icons.Outlined.ZoomIn,
                        contentDescription = "放大查看",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(26.dp),
                    )
                }
            }
            if (images.size > 1) {
                Surface(
                    color = Color.Black.copy(alpha = .58f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${images.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
        }
        if (images.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(images.size) { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            ),
                    )
                }
            }
            Text(
                "左右滑动切换 · 点击图片放大",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun ImageViewerDialog(image: GalleryImage?, onDismiss: () -> Unit) {
    val current = image ?: return
    val context = LocalContext.current
    val imageLoader = remember(context) { MuseumImageLoaderHolder.get(context) }
    var scale by remember(current.model) { mutableFloatStateOf(1f) }
    var offset by remember(current.model) { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Text(
                "图片加载中…",
                color = Color.White.copy(alpha = .7f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center),
            )
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(current.model)
                    .crossfade(false)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = current.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(current.model) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 6f)
                            if (nextScale <= 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val maxX = size.width * (nextScale - 1f) / 2f
                                val maxY = size.height * (nextScale - 1f) / 2f
                                scale = nextScale
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY),
                                )
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = .55f))
                        .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(current.title, color = Color.White, fontWeight = FontWeight.Bold)
                        current.subtitle?.let {
                            Text(it, color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Color.White)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(Color.Black.copy(alpha = .55f))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (scale > 1.01f) "拖动查看 · ${(scale * 100).roundToInt()}%" else "双指或 + 放大 · 拖动查看长图",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "−",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                scale = (scale / 1.5f).coerceAtLeast(1f)
                                if (scale <= 1.01f) offset = Offset.Zero
                            }
                            .padding(horizontal = 9.dp, vertical = 2.dp),
                    )
                    Text(
                        "+",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { scale = (scale * 1.5f).coerceAtMost(6f) }
                            .padding(horizontal = 9.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "还原",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                scale = 1f
                                offset = Offset.Zero
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Spacer(Modifier.height(56.dp))
            }
        }
    }
}
