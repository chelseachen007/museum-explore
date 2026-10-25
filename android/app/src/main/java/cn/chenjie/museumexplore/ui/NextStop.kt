package cn.chenjie.museumexplore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.chenjie.museumexplore.data.ExplorationOps
import cn.chenjie.museumexplore.data.MuseumRepository
import cn.chenjie.museumexplore.model.NextStopCandidate
import cn.chenjie.museumexplore.model.OpeningStatus

/**
 * 首页「今天去哪」区域。
 *
 * 推荐顺序由数据层决定；这个组件只负责城市选择、选择和展示推荐，
 * 不读取定位，也不自行打开官方入口。
 */
@Composable
fun NextStopSection(
    repository: MuseumRepository,
    viewModel: MuseumViewModel,
    revision: Int,
    onOpenMuseum: (city: String, museum: String) -> Unit,
) {
    var city by remember(repository, revision) { mutableStateOf(repository.selectedCity().trim()) }
    var showCityPicker by rememberSaveable { mutableStateOf(false) }
    val cities = remember(repository, revision) { repository.cities().map { it.name }.distinct() }

    val nextStops = remember(repository, revision, city) {
        if (city.isBlank()) {
            emptyList<Pair<NextStopCandidate, String?>>()
        } else {
            val candidates = repository.nextStopCandidates(city, System.currentTimeMillis())
            val openingDetails = candidates.associate { it.museumId to it.openingDetail }
            ExplorationOps.recommend(candidates, limit = 3).map { candidate ->
                candidate to openingDetails[candidate.museumId]
            }
        }
    }
    val recommendations = remember(nextStops) { nextStops.map { it.first } }
    val openingDetailByMuseum = remember(nextStops) {
        nextStops.associate { (candidate, detail) -> candidate.museumId to detail }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        if (city.isBlank()) {
            FirstVisitCard(onChooseCity = { showCityPicker = true })
        } else {
            NextStopHeader(
                city = city,
                onSwitchCity = { showCityPicker = true },
            )
            if (nextStops.isEmpty()) {
                EmptyCityCard(onSwitchCity = { showCityPicker = true })
            } else {
                Text(
                    text = "首选",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp),
                )
                NextStopCard(
                    candidate = recommendations.first(),
                    openingDetail = openingDetailByMuseum[recommendations.first().museumId],
                    entry = repository.officialEntry(recommendations.first().museumId),
                    featured = true,
                    onOpenMuseum = {
                        onOpenMuseum(recommendations.first().city, recommendations.first().museumId)
                    },
                    onWishChange = { wanted ->
                        viewModel.setMuseumWished(recommendations.first().museumId, wanted)
                    },
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 18.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
        )
    }

    if (showCityPicker) {
        CityPickerDialog(
            cities = cities,
            selected = city,
            onSelect = { selected ->
                repository.saveSelectedCity(selected)
                city = selected
                viewModel.refresh()
                showCityPicker = false
            },
            onDismiss = { showCityPicker = false },
        )
    }
}

@Composable
private fun NextStopHeader(
    city: String,
    onSwitchCity: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 20.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "今天去哪 · $city",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onSwitchCity),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onSwitchCity) {
            Text("切换城市")
        }
    }
}

@Composable
private fun FirstVisitCard(onChooseCity: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "今天去哪",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "先选择当前城市，我们只看当地已核验的博物馆，不使用你的位置。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            Button(
                onClick = onChooseCity,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(contentColor = Color.White),
            ) {
                Text("选择城市", color = Color.White)
            }
        }
    }
}

@Composable
private fun EmptyCityCard(onSwitchCity: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "这个城市暂无可推荐博物馆",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "可以切换城市，或者稍后再来看看。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 5.dp),
            )
            OutlinedButton(
                onClick = onSwitchCity,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("切换城市")
            }
        }
    }
}

@Composable
private fun NextStopCard(
    candidate: NextStopCandidate,
    openingDetail: String?,
    entry: cn.chenjie.museumexplore.model.OfficialEntry?,
    featured: Boolean,
    modifier: Modifier = Modifier,
    onOpenMuseum: () -> Unit,
    onWishChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val address = candidate.address.verifiedAddress()
    val hasMiniProgram = !entry?.landingUrl.isNullOrBlank()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (featured) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (featured) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            },
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = candidate.museumId,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = candidate.city,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 2.dp),
            )

            OpeningStatusLine(candidate, openingDetail)

            Text(
                text = relicSummary(candidate),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp),
            )

            Text(
                text = "推荐理由：${candidate.reason}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 7.dp),
            )

            if (hasMiniProgram) {
                Text(
                    text = "官方小程序入口已核验，详情页可直接打开。",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Button(
                onClick = onOpenMuseum,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(contentColor = Color.White),
            ) {
                Text(
                    text = if (hasMiniProgram) "打开官方小程序" else "查看详情",
                    color = Color.White,
                )
            }

            if (address != null) {
                OutlinedButton(
                    onClick = { MapNavigator.navigate(context, candidate.museumId, address) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("导航去这里")
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "地址待核验",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("博物馆名称", candidate.museumId),
                            )
                            Toast.makeText(
                                context,
                                "已复制「${candidate.museumId}」",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    ) {
                        Text("复制馆名")
                    }
                }
            }

            TextButton(
                onClick = { onWishChange(!candidate.isWished) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .semantics { role = Role.Checkbox },
            ) {
                Text(if (candidate.isWished) "不想去这家" else "加入想去")
            }
        }
    }
}

@Composable
private fun OpeningStatusLine(candidate: NextStopCandidate, openingDetail: String?) {
    val detail = openingDetail?.trim()?.takeIf { it.isNotEmpty() }
    Surface(
        modifier = Modifier.padding(top = 10.dp),
        shape = RoundedCornerShape(8.dp),
        color = when (candidate.openingStatus) {
            OpeningStatus.OPEN,
            OpeningStatus.CLOSING_SOON,
            OpeningStatus.BEFORE_OPEN,
            -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)

            OpeningStatus.CLOSED_TODAY,
            OpeningStatus.UNKNOWN,
            -> MaterialTheme.colorScheme.surface
        },
    ) {
        Text(
            text = if (detail == null) {
                candidate.openingStatus.label
            } else {
                "${candidate.openingStatus.label} · $detail"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

private fun relicSummary(candidate: NextStopCandidate): String = buildString {
    append(candidate.relicsTotal)
    append(" 件 195 永禁文物")
    if (candidate.relicsWished > 0) {
        append("，其中 ")
        append(candidate.relicsWished)
        append(" 件在你的想去清单")
    } else {
        append("，想去清单里暂无这家馆的文物")
    }
}

private fun String?.verifiedAddress(): String? {
    val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return value.takeUnless {
        it.contains("待核验") || it == "未知" || it == "暂无"
    }
}

@Composable
fun CityPickerDialog(
    cities: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val visibleCities = remember(cities) { cities.distinct() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择当前城市",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            if (visibleCities.isEmpty()) {
                Text("暂无可选城市")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(visibleCities, key = { it }) { city ->
                        val isSelected = city == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(city) }
                                .padding(horizontal = 4.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = city,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Text(
                                    text = "✓ 当前城市",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
