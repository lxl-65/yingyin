package app.yingyin.player

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale

// ---------------------------------------------------------------------------
// 轻量片库：收藏 / 观看记录（只存本机，不上传）
// ---------------------------------------------------------------------------

data class HomeSectionV2(val key: String, val title: String, val items: List<Item>)

private val SECTION_TITLES = linkedMapOf(
    "movie" to "热门电影",
    "series" to "热播剧集",
    "anime" to "动漫新番",
    "variety" to "综艺现场",
    "doc" to "纪录片",
    "short" to "短剧精选"
)

/** 与网站 server.js 的 NSFW_RE 对齐：默认不显示，只有设置开关打开才放行。 */
private val PRIVATE_WORDS = listOf(
    "伦理", "三级", "情色", "色情", "成人", "福利", "写真", "里番", "肉番",
    "女优", "无码", "有码", "麻豆", "自拍", "偷拍", "激情", "艳舞", "擦边"
)

fun isPrivateItem(item: Item): Boolean {
    val t = "${item.type} ${item.remarks} ${item.name}".lowercase(Locale.getDefault())
    return PRIVATE_WORDS.any { t.contains(it) }
}

/** 与网站 server.js 的 NAME_RULES 对齐，顺序不能乱：短剧在电视剧之前。 */
fun categoryKeyOf(item: Item): String {
    if (isPrivateItem(item)) return "private"
    val t = "${item.type} ${item.remarks} ${item.name}".lowercase(Locale.getDefault())
    return when {
        listOf("纪录", "记录").any { t.contains(it) } -> "doc"
        listOf("短剧", "爽剧", "微短剧", "反转剧").any { t.contains(it) } -> "short"
        listOf("动漫", "动画", "番剧", "国漫", "日漫").any { t.contains(it) } -> "anime"
        listOf("综艺", "真人秀", "脱口秀").any { t.contains(it) } -> "variety"
        listOf(
            "连续剧", "电视剧", "国产剧", "港剧", "韩剧", "美剧", "日剧", "台剧",
            "海外剧", "泰剧", "欧美剧", "香港剧", "韩国剧", "台湾剧", "日本剧",
            "剧情剧", "悬疑剧", "都市剧"
        ).any { t.contains(it) } -> "series"
        listOf(
            "电影", "动作片", "喜剧片", "爱情片", "科幻片", "恐怖片", "剧情片",
            "战争片", "犯罪片", "悬疑片", "冒险片", "奇幻片", "武侠片",
            "枪战片", "灾难片"
        ).any { t.contains(it) } -> "movie"
        t.contains("剧") -> "series"
        t.endsWith("片") -> "movie"
        else -> "movie"
    }
}

fun categoryTitleOf(key: String): String = when (key) {
    "private" -> "私密"
    "all" -> "全部片单"
    else -> SECTION_TITLES[key] ?: "精选片单"
}

fun buildHomeSections(items: List<Item>): List<HomeSectionV2> {
    val buckets = linkedMapOf<String, MutableList<Item>>()
    for (item in items) {
        if (categoryKeyOf(item) == "private") continue
        buckets.getOrPut(categoryKeyOf(item)) { mutableListOf() }.add(item)
    }
    val out = ArrayList<HomeSectionV2>()
    for ((key, title) in SECTION_TITLES) {
        val list = buckets[key]?.sortedWith(compareByDescending<Item> { it.srcs }.thenByDescending { it.score })
        if (!list.isNullOrEmpty()) out.add(HomeSectionV2(key, title, list))
    }
    if (out.isEmpty() && items.isNotEmpty()) out.add(HomeSectionV2("all", "精选片单", items.take(20)))
    return out
}

fun pickHero(items: List<Item>): Item? =
    items.filterNot { isPrivateItem(it) }
        .filter { it.pic.isNotBlank() }
        .maxWithOrNull(compareBy<Item> { it.srcs }.thenBy { it.score })
        ?: items.firstOrNull { !isPrivateItem(it) }

// ---------------------------------------------------------------------------
// 根路由
// ---------------------------------------------------------------------------

sealed interface AppRouteV2 {
    data object Home : AppRouteV2
    data object Categories : AppRouteV2
    data object Search : AppRouteV2
    data object Me : AppRouteV2
    data object Sources : AppRouteV2
    data object History : AppRouteV2
    data object Favorites : AppRouteV2
    data class Category(val key: String, val title: String) : AppRouteV2
    data class Detail(
        val item: Item,
        val resumeEp: Int = 0,
        val resumeLine: String = "",
        val resumePositionMs: Long = 0L,
        val autoPlay: Boolean = false
    ) : AppRouteV2
    data class Play(
        val item: Item,
        val title: String,
        val lines: List<Line>,
        val epIdx: Int,
        val startMs: Long = 0L
    ) : AppRouteV2
}

@Composable
fun YingyinAppV2() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        Repo.init(ctx.applicationContext)
        val subs = BuildConfig.SUB_URLS.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (subs.isNotEmpty()) Repo.sync(subs)
        Repo.checkUpdate(BuildConfig.VERSION_URL.split(",").map { it.trim() }.filter { it.isNotBlank() })
    }

    // 用路由栈保存来路：分类/搜索/我的 → 详情 → 播放，返回时逐层退回，不再一律回首页。
    var stack by remember { mutableStateOf<List<AppRouteV2>>(listOf(AppRouteV2.Home)) }
    fun go(next: AppRouteV2) { stack = stack + next }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }
    fun tab(next: AppRouteV2) { stack = listOf(next) }
    val route = stack.last()
    val routeKey = when (val r = route) {
        is AppRouteV2.Category -> "category:${r.key}"
        is AppRouteV2.Detail -> "detail:${r.item.sid}:${r.item.id}:${r.resumeEp}:${r.resumeLine}"
        is AppRouteV2.Play -> "play:${r.item.sid}:${r.item.id}"
        else -> route.toString()
    }
    val saveableStates = rememberSaveableStateHolder()

    BackHandler(enabled = stack.size > 1) { back() }

    val updateInfo = Repo.update
    var dismissedUpdate by rememberSaveable {
        mutableStateOf(ctx.getSharedPreferences("yingyin", 0).getInt("dismissedUpdate", 0))
    }
    fun dismissUpdate(versionCode: Int) {
        dismissedUpdate = versionCode
        ctx.getSharedPreferences("yingyin", 0).edit().putInt("dismissedUpdate", versionCode).apply()
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        AppBackdropV2()
        // 保留每个页面的 rememberSaveable 状态：搜索页从详情返回后还能看到原查询和结果。
        saveableStates.SaveableStateProvider(routeKey) {
            when (val r = route) {
            AppRouteV2.Home -> HomeScreenV2(
                onSearch = { go(AppRouteV2.Search) },
                onMe = { go(AppRouteV2.Me) },
                onOpen = { go(AppRouteV2.Detail(it)) },
                onResume = { entry ->
                    go(AppRouteV2.Detail(
                        entry.item,
                        entry.epIdx,
                        entry.lineName,
                        entry.positionMs,
                        autoPlay = true
                    ))
                },
                onCategory = { key, title -> go(AppRouteV2.Category(key, title)) }
            )

            AppRouteV2.Categories -> CategoriesScreenV2(
                onCategory = { key, title -> go(AppRouteV2.Category(key, title)) },
                onBack = { back() }
            )

            AppRouteV2.Search -> SearchScreenV2(
                onOpen = { go(AppRouteV2.Detail(it)) },
                onBack = { if (stack.size > 1) back() else tab(AppRouteV2.Home) }
            )

            AppRouteV2.Me -> MeScreenV2(
                onSources = { go(AppRouteV2.Sources) },
                onHistory = { go(AppRouteV2.History) },
                onFavorites = { go(AppRouteV2.Favorites) }
            )

            AppRouteV2.Sources -> SourcesScreenV2(onBack = { back() })

            AppRouteV2.History -> HistoryScreenV2(
                onBack = { back() },
                onResume = { entry ->
                    go(AppRouteV2.Detail(
                        entry.item,
                        entry.epIdx,
                        entry.lineName,
                        entry.positionMs,
                        autoPlay = true
                    ))
                }
            )

            AppRouteV2.Favorites -> FavoritesScreenV2(
                onBack = { back() },
                onOpen = { go(AppRouteV2.Detail(it)) }
            )

            is AppRouteV2.Category -> {
                var unlocked by remember(r.key) { mutableStateOf(false) }
                if (r.key == "private" && !unlocked) {
                    PrivateGateV2(onBack = { back() }, onUnlock = { unlocked = true })
                } else {
                    CategoryScreenServerV2(
                        key = r.key,
                        title = r.title,
                        onOpen = { go(AppRouteV2.Detail(it)) },
                        onBack = { back() }
                    )
                }
            }

            is AppRouteV2.Detail -> DetailScreenV2(
                item = r.item,
                resumeEp = r.resumeEp,
                resumeLine = r.resumeLine,
                resumePositionMs = r.resumePositionMs,
                autoPlay = r.autoPlay,
                onBack = { back() },
                onPlay = { lines, idx, line, startMs ->
                    Library.record(
                        r.item,
                        line.from,
                        idx,
                        line.eps.getOrNull(idx)?.name ?: "第${idx + 1}集"
                    )
                    go(AppRouteV2.Play(r.item, r.item.name, lines, idx, startMs))
                }
            )

            is AppRouteV2.Play -> PlayerScreen(
                title = r.title,
                lines = r.lines,
                epIdx = r.epIdx,
                startMs = r.startMs,
                onBack = {
                    // 从播放器返回详情时带回当前集，避免详情页又跳回第 1 集。
                    stack = stack.dropLast(2) + AppRouteV2.Detail(r.item, r.epIdx)
                },
                onEpChange = { nextEp ->
                    stack = stack.dropLast(1) + AppRouteV2.Play(r.item, r.title, r.lines, nextEp, 0L)
                },
                onProgress = { pos, dur -> Library.updateProgress(r.item, pos, dur) }
            )
        }
        }

        if (route is AppRouteV2.Home || route is AppRouteV2.Categories ||
            route is AppRouteV2.Search || route is AppRouteV2.Me) {
            BottomBarV2(
                active = when (route) {
                    AppRouteV2.Categories -> "categories"
                    AppRouteV2.Search -> "search"
                    AppRouteV2.Me -> "me"
                    else -> "home"
                },
                onHome = { tab(AppRouteV2.Home) },
                onCategories = { tab(AppRouteV2.Categories) },
                onSearch = { tab(AppRouteV2.Search) },
                onMe = { tab(AppRouteV2.Me) }
            )
        }

        if (updateInfo != null && updateInfo.versionCode > dismissedUpdate) {
            UpdateDialogV2(
                info = updateInfo,
                onDismiss = { dismissUpdate(updateInfo.versionCode) }
            )
        }
    }
}

@Composable
private fun AppBackdropV2() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Bg)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Accent.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.02f),
                radius = size.width * 0.95f
            ),
            radius = size.width * 0.95f,
            center = Offset(size.width * 0.1f, size.height * 0.02f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF23405B).copy(alpha = 0.13f), Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 0.58f),
                radius = size.width * 0.8f
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width * 0.95f, size.height * 0.58f)
        )
    }
}

@Composable
private fun BottomBarV2(
    active: String,
    onHome: () -> Unit,
    onCategories: () -> Unit,
    onSearch: () -> Unit,
    onMe: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Panel.copy(alpha = 0.97f))
                .border(1.dp, Stroke.copy(alpha = 0.8f), RoundedCornerShape(26.dp))
                .padding(vertical = 7.dp, horizontal = 8.dp)
        ) {
            TabItemV2("首页", Icons.Outlined.Home, active == "home", Modifier.weight(1f), onHome)
            TabItemV2("分类", Icons.Outlined.GridView, active == "categories", Modifier.weight(1f), onCategories)
            TabItemV2("搜索", Icons.Outlined.Search, active == "search", Modifier.weight(1f), onSearch)
            TabItemV2("我的", Icons.Outlined.Person, active == "me", Modifier.weight(1f), onMe)
        }
    }
}

@Composable
private fun TabItemV2(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Accent else TextDim,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (selected) Accent else TextDim,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ---------------------------------------------------------------------------
// 通用组件
// ---------------------------------------------------------------------------

@Composable
private fun WordmarkV2() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("影视仓", color = TextMain, fontFamily = Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.width(7.dp))
        Text(
            "BETA ${BuildConfig.VERSION_NAME}",
            color = AccentSoft,
            fontSize = 8.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun CoverImage(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    fallbackSize: Int = 24
) {
    Box(modifier.background(CardBg)) {
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.linearGradient(listOf(Accent.copy(alpha = 0.20f), Panel, CardBg))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.trim().take(2).ifBlank { "影" },
                color = TextDim.copy(alpha = 0.42f),
                fontFamily = Serif,
                fontSize = fallbackSize.sp,
                fontWeight = FontWeight.Bold
            )
        }
        val ctx = LocalContext.current
        val request = remember(url) {
            if (url.isNullOrBlank()) null else ImageRequest.Builder(ctx)
                .data(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0 Safari/537.36")
                .addHeader("Referer", runCatching { val u = java.net.URL(url); "${u.protocol}://${u.host}/" }.getOrDefault(""))
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
private fun RoundIconButtonV2(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Panel.copy(alpha = 0.86f))
            .border(1.dp, Stroke.copy(alpha = 0.8f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = TextMain, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun TopBarV2(onSearch: () -> Unit, onMe: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WordmarkV2()
        Spacer(Modifier.weight(1f))
        RoundIconButtonV2(Icons.Outlined.Search, "搜索", onSearch, Modifier.padding(end = 9.dp))
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Accent, Color(0xFFD94D66))))
                .clickable(onClick = onMe),
            contentAlignment = Alignment.Center
        ) {
            Text("影", color = Color(0xFF1D0C08), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SectionTitleV2(
    title: String,
    count: Int? = null,
    onMore: (() -> Unit)? = null,
    unit: String = "部"
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, color = TextMain, fontFamily = Serif, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        if (count != null && count > 0) {
            Spacer(Modifier.width(7.dp))
            Text("$count $unit", color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Spacer(Modifier.weight(1f))
        if (onMore != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onMore)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("查看全部", color = TextSub, fontSize = 10.sp)
                Icon(Icons.Outlined.ChevronRight, null, tint = TextSub, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun BadgeV2(
    text: String,
    accent: Boolean = true,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    Text(
        text,
        color = color ?: if (accent) AccentSoft else TextSub,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color?.copy(alpha = 0.14f) ?: if (accent) AccentDark.copy(alpha = 0.9f) else Panel)
            .border(
                1.dp,
                color?.copy(alpha = 0.35f) ?: if (accent) Accent.copy(alpha = 0.25f) else Stroke,
                RoundedCornerShape(5.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun PosterCardV2(
    item: Item,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .width(118.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(13.dp))
                .background(CardBg)
        ) {
            CoverImage(
                url = item.pic,
                name = item.name,
                modifier = Modifier.fillMaxSize(),
                fallbackSize = 20
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.62f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.78f)
                        )
                    )
            )
            if (item.srcs > 1) {
                BadgeV2("多源", modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                if (item.score > 0) {
                    Text("★ ${"%.1f".format(Locale.US, item.score)}", color = AccentSoft, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
                if (item.remarks.isNotBlank()) {
                    Text(item.remarks, color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            item.name,
            color = TextMain,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            listOf(item.year, item.type).filter { it.isNotBlank() }.joinToString(" · "),
            color = TextDim,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CategoryChipsV2(onCategory: (String, String) -> Unit) {
    val visible = CATEGORY_INFOS.filter { info -> info.key != "private" || Library.showPrivate }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(visible) { info ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Panel.copy(alpha = 0.9f))
                    .border(1.dp, info.accent.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .clickable { onCategory(info.key, info.title) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(info.icon, null, tint = info.accent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(info.title, color = TextSub, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun PosterRowV2(items: List<Item>, onOpen: (Item) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) { item ->
            PosterCardV2(item, onClick = { onOpen(item) })
        }
    }
}

// ---------------------------------------------------------------------------
// 首页
// ---------------------------------------------------------------------------

@Composable
fun HomeScreenV2(
    onSearch: () -> Unit,
    onMe: () -> Unit,
    onOpen: (Item) -> Unit,
    onResume: (WatchEntry) -> Unit,
    onCategory: (String, String) -> Unit
) {
    val items = Repo.homeItems.toList()
    val serverGroups = Repo.serverGroups
    val sections = remember(items, serverGroups, Repo.serverFeed) {
        if (serverGroups.isNotEmpty()) {
            // 首页只留一条「最新上线」；电影/电视剧/动漫这些整排分类交给底部「分类」页，
            // 不再在首页重复占掉两屏。
            val feed = Repo.serverFeed.ifEmpty { serverGroups.firstOrNull()?.items.orEmpty() }
            if (feed.isNotEmpty()) listOf(HomeSectionV2("home", "最新上线", feed)) else emptyList()
        } else if (items.isNotEmpty()) {
            listOf(HomeSectionV2("home", "最近更新", items.take(30)))
        } else emptyList()
    }
    val hero = remember(items) { pickHero(items) }
    val history = Library.visibleHistory.firstOrNull()
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var booting by remember { mutableStateOf(items.isEmpty()) }

    LaunchedEffect(Unit) {
        // 有缓存就先出画面；网络刷新放到后台，不再让首页等片源。
        if (Repo.homeItems.isNotEmpty()) booting = false
        if (Repo.homeItems.isEmpty()) {
            // 只显示一次启动骨架，之后即使源全挂也进入空态。
            booting = true
        }
        val serverOk = Repo.loadServerHome()
        if (!serverOk) Repo.home()
        booting = false
    }

    if (booting && items.isEmpty()) {
        HomeLoadingV2()
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 110.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item { TopBarV2(onSearch, onMe) }
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 13.dp)) {
                Text(
                    "今晚，看点什么。",
                    color = TextMain,
                    fontFamily = Serif,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "${if (Repo.serverSourceCount > 0) Repo.serverSourceCount else Repo.sources.size} 个源已接入 · 先给你最好的线路",
                    color = TextSub,
                    fontSize = 10.sp,
                    letterSpacing = 0.3.sp
                )
            }
        }

        hero?.let {
            item { HeroCardV2(it, onOpen = { onOpen(it) }) }
        }

        if (history != null) {
            item { SectionTitleV2("继续观看", onMore = { onResume(history) }) }
            item { ContinueCardV2(history, onOpen = { onResume(history) }) }
        }

        if (sections.isEmpty() && items.isEmpty()) {
            item { EmptyHomeV2(onRefresh = { scope.launch { syncing = true; Repo.home(); syncing = false } }, syncing = syncing) }
        }

        sections.forEach { section ->
            item(key = "t-${section.key}") {
                SectionTitleV2(
                    title = section.title,
                    count = null,
                    onMore = { onCategory(if (section.key == "home") "home" else section.key, section.title) }
                )
            }
            item(key = "r-${section.key}") {
                PosterRowV2(section.items.take(12), onOpen)
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("— 片源会变，好片不会 —", color = TextDim, fontSize = 10.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Text("影视仓 · 你的私人片库", color = TextDim.copy(alpha = 0.7f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun HeroCardV2(item: Item, onOpen: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(194.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(CardBg, Panel)
                )
            )
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 竖版海报保持 2:3，不再把横图拉满整块背景——这是首页显得“廉价”的主因。
        CoverImage(
            url = item.pic,
            name = item.name,
            modifier = Modifier
                .width(104.dp)
                .height(156.dp)
                .clip(RoundedCornerShape(12.dp)),
            fallbackSize = 34
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            BadgeV2("本周精选")
            Spacer(Modifier.height(7.dp))
            Text(
                item.name,
                color = TextMain,
                fontFamily = Serif,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 24.sp
            )
            Spacer(Modifier.height(5.dp))
            Text(
                listOf(item.year, item.type, item.area).filter { it.isNotBlank() }.joinToString("  ·  "),
                color = TextSub,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Accent)
                        .clickable(onClick = onOpen)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Color(0xFF1B0B05), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("立即播放", color = Color(0xFF1B0B05), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(13.dp))
                Text("详情 ↗", color = AccentSoft, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ContinueCardV2(entry: WatchEntry, onOpen: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(1.dp, Stroke.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(58.dp)
                .height(82.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CardBg)
        ) {
            CoverImage(entry.item.pic, entry.item.name, Modifier.fillMaxSize(), fallbackSize = 22)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.item.name, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(
                historySubV2(entry),
                color = TextSub,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(9.dp))
            val progress = if (entry.durationMs > 0L) {
                (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.height(2.dp).weight(1f).clip(CircleShape).background(Stroke)) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(Accent))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(35.dp).clip(CircleShape).background(Accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.PlayArrow, null, tint = Color(0xFF1B0B05), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun HomeLoadingV2() {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        WordmarkV2()
        Spacer(Modifier.height(50.dp))
        Text("今晚，看点什么。", color = TextMain, fontFamily = Serif, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(24.dp)).background(Panel))
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Box(Modifier.width(98.dp).height(146.dp).clip(RoundedCornerShape(13.dp)).background(Panel))
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
            Text("正在同步 12 个片源…", color = TextSub, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmptyHomeV2(onRefresh: () -> Unit, syncing: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("片源暂时没有响应", color = TextMain, fontFamily = Serif, fontSize = 19.sp)
        Spacer(Modifier.height(7.dp))
        Text("检查一下网络，再试一次", color = TextSub, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Accent).clickable(onClick = onRefresh).padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Refresh, null, tint = Color(0xFF1B0B05), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(if (syncing) "同步中…" else "重新同步", color = Color(0xFF1B0B05), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------------------
// 搜索
// ---------------------------------------------------------------------------

@Composable
fun SearchScreenV2(onOpen: (Item) -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Item>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchSeq by remember { mutableStateOf(0) }
    var lastQuery by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun doSearch() {
        val q = query.trim()
        if (q.isBlank()) return
        if (loading && q == lastQuery) return
        lastQuery = q
        searchJob?.cancel()
        val seq = searchSeq + 1
        searchSeq = seq
        results = emptyList()
        searchError = null
        searched = true
        loading = true
        searchJob = scope.launch {
            try {
                val found = Repo.search(q) { partial ->
                    if (seq == searchSeq) {
                        results = if (Library.showPrivate) partial
                        else partial.filterNot { categoryKeyOf(it) == "private" }
                    }
                }
                if (seq == searchSeq) {
                    results = if (Library.showPrivate) found
                    else found.filterNot { categoryKeyOf(it) == "private" }
                }
            } catch (e: Exception) {
                if (seq == searchSeq) {
                    results = emptyList()
                    searchError = "搜索失败，检查网络后重试"
                }
            } finally {
                if (seq == searchSeq) loading = false
            }
        }
    }

    // 从详情返回搜索时，页面会被重新组合；用保存的 query 自动回填缓存结果，避免搜索页变空。
    LaunchedEffect(Unit) {
        if (query.isNotBlank() && results.isEmpty()) doSearch()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 110.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("搜索", color = TextMain, fontFamily = Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Close, "关闭搜索", tint = TextSub, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("取消", color = TextSub, fontSize = 10.sp)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("找到想看的。", color = TextMain, fontFamily = Serif, fontSize = 33.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("片名、演员、类型，一次搜 ${Repo.sources.size} 个源", color = TextSub, fontSize = 11.sp)
            Spacer(Modifier.height(17.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(Panel)
                    .border(1.dp, if (query.isNotEmpty()) Accent.copy(alpha = 0.55f) else Stroke, RoundedCornerShape(15.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = TextSub, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextMain, fontSize = 13.sp),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text("片名、演员、类型", color = TextDim, fontSize = 13.sp)
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close,
                        "清空",
                        tint = TextSub,
                        modifier = Modifier.size(17.dp).clickable {
                            searchJob?.cancel()
                            searchSeq += 1
                            query = ""
                            results = emptyList()
                            searched = false
                            loading = false
                            lastQuery = ""
                            searchError = null
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier.clip(RoundedCornerShape(9.dp)).background(Accent).clickable { doSearch() }.padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Text("搜索", color = Color(0xFF1B0B05), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 160.dp)) {
            if (query.isBlank()) {
                item {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp)) {
                        Text("热门搜索", color = TextMain, fontFamily = Serif, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        val hot = listOf("庆余年", "斗破苍穹", "漫长的季节", "狂飙", "三体", "繁花", "流浪地球", "甄嬛传")
                        hot.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                row.forEach { name ->
                                    Text(
                                        name,
                                        color = TextMain,
                                        fontSize = 11.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Panel)
                                            .border(1.dp, Stroke, RoundedCornerShape(50))
                                            .clickable { query = name; doSearch() }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    SectionTitleV2("最近热播", count = Repo.homeItems.filterNot { categoryKeyOf(it) == "private" }.take(8).size)
                }
                items(Repo.homeItems.filterNot { categoryKeyOf(it) == "private" }.take(8)) { item ->
                    SearchResultRowV2(item, onOpen)
                }
            } else {
                if (loading) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = Accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(9.dp))
                            Text(
                                if (results.isEmpty()) "正在搜 ${Repo.sources.size} 个源…"
                                else "已到 ${results.size} 条，仍在补后面的源…",
                                color = TextSub,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                if (results.isNotEmpty()) {
                    item { SectionTitleV2("搜索结果", count = results.size) }
                    items(results) { item -> SearchResultRowV2(item, onOpen) }
                } else if (searched && !loading) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (searchError != null) "搜索没有完成" else "没有找到「$query」",
                                color = TextMain, fontFamily = Serif, fontSize = 18.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                searchError ?: "换个片名或减少关键词试试",
                                color = TextSub, fontSize = 11.sp
                            )
                            if (searchError != null) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "重试",
                                    color = Accent,
                                    fontSize = 11.sp,
                                    modifier = Modifier.clickable { doSearch() }.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRowV2(item: Item, onOpen: (Item) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(item) }
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(54.dp).height(76.dp).clip(RoundedCornerShape(9.dp)).background(CardBg)
        ) {
            CoverImage(item.pic, item.name, Modifier.fillMaxSize(), fallbackSize = 20)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(item.year, item.type, item.area).filter { it.isNotBlank() }.joinToString(" · "),
                color = TextSub,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgeV2(if (item.srcs > 1) "多源" else "单源")
                if (item.score > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("★ ${"%.1f".format(Locale.US, item.score)}", color = AccentSoft, fontSize = 9.sp)
                }
            }
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = TextDim, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------------------
// 分类总览
// ---------------------------------------------------------------------------

private data class CategoryInfoV2(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color
)

private val CATEGORY_INFOS = listOf(
    CategoryInfoV2("movie", "电影", "动作 / 剧情 / 科幻", Icons.Outlined.Movie, Color(0xFFFF8A5B)),
    CategoryInfoV2("series", "电视剧", "国产 / 日韩 / 欧美", Icons.Outlined.Tv, Color(0xFF7FB4FF)),
    CategoryInfoV2("anime", "动漫", "番剧 / 动画电影", Icons.Outlined.AutoAwesome, Color(0xFFB49BFF)),
    CategoryInfoV2("variety", "综艺", "真人秀 / 脱口秀", Icons.Outlined.Mic, Color(0xFFFFC46B)),
    CategoryInfoV2("doc", "纪录片", "纪实 / 自然 / 人文", Icons.Outlined.Public, Color(0xFF71D7B2)),
    CategoryInfoV2("short", "短剧", "爽剧 / 微短剧 / 反转", Icons.Outlined.Bolt, Color(0xFFFF7E9D)),
    CategoryInfoV2("private", "私密", "默认隐藏 · 设置里开启", Icons.Outlined.Lock, Color(0xFF9A7BFF))
)

@Composable
fun CategoriesScreenV2(onCategory: (String, String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 110.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("分类", color = TextMain, fontFamily = Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${if (Repo.serverSourceCount > 0) Repo.serverSourceCount else Repo.sources.size} 个源 · 与网页端同款分类", color = TextDim, fontSize = 10.sp, letterSpacing = 0.3.sp)
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("按类型找片。", color = TextMain, fontFamily = Serif, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("电影 / 电视剧 / 动漫 / 综艺 / 纪录片 / 短剧", color = TextSub, fontSize = 11.sp)
            Spacer(Modifier.height(18.dp))
        }
        val visible = CATEGORY_INFOS.filter { info ->
            info.key != "private" || Library.showPrivate
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(visible) { info ->
                CategoryTileV2(
                    info = info,
                    count = 0,
                    onClick = { onCategory(info.key, info.title) }
                )
            }
        }
    }
}

@Composable
private fun CategoryTileV2(info: CategoryInfoV2, count: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(info.accent.copy(alpha = 0.18f), Panel.copy(alpha = 0.94f))
                )
            )
            .border(1.dp, info.accent.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(info.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(info.icon, null, tint = info.accent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.weight(1f))
            if (info.key == "private") BadgeV2("18+", color = info.accent)
        }
        Spacer(Modifier.weight(1f))
        Text(info.title, color = TextMain, fontFamily = Serif, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(info.subtitle, color = TextSub, fontSize = 9.sp)
    }
}

// ---------------------------------------------------------------------------
// 单个分类
// ---------------------------------------------------------------------------

@Composable
fun CategoryScreenV2(
    key: String,
    title: String,
    onOpen: (Item) -> Unit,
    onBack: () -> Unit
) {
    val items = remember(key, Repo.homeItems.size) {
        if (key == "all") Repo.homeItems.toList() else Repo.homeItems.filter { categoryKeyOf(it) == key }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButtonV2(Icons.Outlined.ArrowBack, "返回", onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = TextMain, fontFamily = Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${items.size} 部 · 全库", color = TextDim, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items) { item ->
                PosterCardV2(item, modifier = Modifier.fillMaxWidth(), onClick = { onOpen(item) })
            }
        }
    }
}

private val ALL_GENRES_V2 = listOf(
    "动作", "爱情", "剧情", "喜剧", "犯罪", "悬疑", "科幻", "奇幻", "恐怖", "惊悚",
    "冒险", "战争", "历史", "武侠", "灾难", "家庭", "儿童", "古装", "运动", "音乐"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRowV2(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    if (options.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            color = TextDim,
            fontSize = 9.sp,
            modifier = Modifier.width(34.dp).padding(top = 8.dp)
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChipV2("全部", selected.isEmpty()) { onSelect("") }
            options.forEach { option ->
                FilterChipV2(option, selected == option) { onSelect(option) }
            }
        }
    }
}

@Composable
private fun FilterChipV2(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(27.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) Accent else Panel)
            .border(1.dp, if (active) Accent else Stroke, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (active) Color(0xFF1A0D08) else TextSub, fontSize = 9.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun CategoryScreenServerV2(
    key: String,
    title: String,
    onOpen: (Item) -> Unit,
    onBack: () -> Unit
) {
    var items by remember(key) { mutableStateOf<List<Item>>(emptyList()) }
    var page by remember(key) { mutableStateOf(1) }
    var alive by remember(key) { mutableStateOf(0) }
    var sources by remember(key) { mutableStateOf(0) }
    var loading by remember(key) { mutableStateOf(true) }
    var loadingMore by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var genre by remember(key) { mutableStateOf("") }
    var area by remember(key) { mutableStateOf("") }
    var year by remember(key) { mutableStateOf("") }
    var sort by remember(key) { mutableStateOf("new") }
    var facets by remember(key) { mutableStateOf(ServerFacets()) }
    var loadSeq by remember(key) { mutableStateOf(0) }
    var endReached by remember(key) { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    fun sortLocal(list: List<Item>): List<Item> = when (sort) {
        "score" -> list.sortedByDescending { it.score }
        "score_asc" -> list.sortedBy { it.score }
        "hot" -> list.sortedByDescending { it.srcs }
        else -> list
    }

    suspend fun load(p: Int, append: Boolean) {
        if (append && (loadingMore || loading)) return
        val my = ++loadSeq
        val cacheKey = Repo.categoryCacheKey(key, genre, area, year, sort)
        if (!append) {
            // 先出 5 分钟内的旧页，网络请求在后台继续；用户不再盯着空白网格。
            Repo.cachedCategory(cacheKey)?.let { hit ->
                items = groupLanguageVariants(hit.items)
                facets = hit.facets
                alive = hit.alive
                sources = hit.sources
            }
        }
        if (append) loadingMore = true else loading = true
        if (!append) endReached = false
        try {
            val cat = if (key == "all") "home" else key
            val online = ServerApi.probe()
            val res = if (online) ServerApi.list(cat, p, genre, area, year, sort) else null
            if (my != loadSeq) return
            if (res != null) {
                items = if (append) groupLanguageVariants(items + res.items) else groupLanguageVariants(res.items)
                if (res.items.isEmpty() && append) endReached = true
                alive = res.alive
                sources = res.sources
                facets = res.facets
                page = p
                error = null
                Repo.saveCategory(cacheKey, items, facets, alive, sources)
            } else {
                // PC 不在线：手机直接按订阅里的 cats 分类拉源，朋友不需要开电脑。
                val local = Repo.category(cat, p, genre, area, year)
                if (my != loadSeq) return
                val localItems = local.items.ifEmpty {
                    Repo.homeItems.filter { key == "all" || categoryKeyOf(it) == key }
                }
                items = sortLocal(groupLanguageVariants(if (append) items + localItems else localItems))
                if (localItems.isEmpty() && append) endReached = true
                alive = if (local.alive > 0) local.alive else Repo.sources.size
                sources = if (local.sources > 0) local.sources else Repo.sources.size
                facets = if (local.facets.genres.isNotEmpty() || local.facets.areas.isNotEmpty() || local.facets.years.isNotEmpty()) {
                    local.facets
                } else {
                    Repo.facetsFor(Repo.homeItems.filter { key == "all" || categoryKeyOf(it) == key })
                }
                page = p
                error = if (items.isEmpty()) "这个分类暂时没有加载到内容，换一个分类或稍后重试" else null
                Repo.saveCategory(cacheKey, items, facets, alive, sources)
            }
        } catch (e: Exception) {
            if (my == loadSeq && !append && items.isEmpty()) error = "分类加载失败，检查网络后重试"
        } finally {
            if (my == loadSeq) {
                loading = false
                loadingMore = false
            }
        }
    }

    LaunchedEffect(key) {
        Repo.cachedCategory(Repo.categoryCacheKey(key, genre, area, year, sort))?.let { hit ->
            items = groupLanguageVariants(hit.items)
            facets = hit.facets
            alive = hit.alive
            sources = hit.sources
            loading = false
        }
        load(1, false)
    }

    // 滑到距底部约 8 张时自动续拉，不把“加载更多”当成用户的活儿。
    LaunchedEffect(gridState, page, loading, loadingMore, endReached, items.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (items.size >= 30 && last >= items.lastIndex - 8 && !endReached && !loading && !loadingMore) {
                    scope.launch { load(page + 1, true) }
                }
            }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButtonV2(Icons.Outlined.ArrowBack, "返回", onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextMain, fontFamily = Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (loading) "正在汇总各源…"
                    else if (error != null) error!!
                    else "${items.size} 部已加载 · ${alive}/${sources} 个源有回应",
                    color = if (error != null) Color(0xFFFF8A7A) else TextDim,
                    fontSize = 9.sp,
                    maxLines = 2
                )
            }
            if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(8.dp))
        if (facets.genres.isNotEmpty() || facets.areas.isNotEmpty() || facets.years.isNotEmpty()) {
            // 采集站的 facets 偶尔只回前几个；悬疑/科幻/奇幻/儿童这些必须能点到，
            // 所以用内置全集兜底，再与服务器返回的并集。
            val genreOptions = (facets.genres + ALL_GENRES_V2).distinct()
            FilterRowV2("类型", genreOptions, genre) { genre = it; scope.launch { load(1, false) } }
            FilterRowV2("地区", facets.areas.take(10), area) { area = it; scope.launch { load(1, false) } }
            FilterRowV2("年份", facets.years.take(10), year) { year = it; scope.launch { load(1, false) } }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("排序", color = TextDim, fontSize = 9.sp, modifier = Modifier.width(34.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChipV2("最新", sort == "new") { sort = "new"; scope.launch { load(1, false) } }
                }
                item {
                    FilterChipV2("评分 ↓", sort == "score") { sort = "score"; scope.launch { load(1, false) } }
                }
                item {
                    FilterChipV2("评分 ↑", sort == "score_asc") { sort = "score_asc"; scope.launch { load(1, false) } }
                }
                item {
                    FilterChipV2("最热", sort == "hot") { sort = "hot"; scope.launch { load(1, false) } }
                }
            }
        }
        if (items.isEmpty() && !loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "这个分类暂时没有内容", color = TextSub, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("重试", color = Accent, fontSize = 11.sp, modifier = Modifier.clickable { scope.launch { load(1, false) } })
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(items) { item ->
                    PosterCardV2(item, modifier = Modifier.fillMaxWidth(), onClick = { onOpen(item) })
                }
                if (loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateGateV2(onBack: () -> Unit, onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButtonV2(Icons.Outlined.ArrowBack, "返回", onBack)
            Spacer(Modifier.width(12.dp))
            Text("私密片单", color = TextMain, fontFamily = Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF9A7BFF).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Lock, null, tint = Color(0xFFB9A5FF), modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("只放在这一个地方", color = TextMain, fontFamily = Serif, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Text(
                "这类内容不会混进首页、普通分类和搜索结果。\n仅限成年人，且只保存在本机。",
                color = TextSub, fontSize = 11.sp, lineHeight = 19.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF9A7BFF)).clickable(onClick = onUnlock),
                contentAlignment = Alignment.Center
            ) {
                Text("我已满 18 岁，进入", color = Color(0xFF120B24), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "返回",
                color = TextSub,
                fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(10.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 详情
// ---------------------------------------------------------------------------

/** 兼容旧记录：以前存的是「线路 3」，新记录存采集站真实来源名。 */
private fun resumeLineIndex(lines: List<Line>, resumeLine: String): Int {
    if (lines.isEmpty() || resumeLine.isBlank()) return 0
    val exact = lines.indexOfFirst { it.from == resumeLine }
    if (exact >= 0) return exact
    val byName = lines.indexOfFirst { it.from.contains(resumeLine, true) || resumeLine.contains(it.from, true) }
    if (byName >= 0) return byName
    val n = Regex("""线路\s*(\d+)""").find(resumeLine)?.groupValues?.getOrNull(1)?.toIntOrNull()
    return if (n != null && n in 1..lines.size) n - 1 else 0
}

@Composable
fun DetailScreenV2(
    item: Item,
    onBack: () -> Unit,
    onPlay: (List<Line>, Int, Line, Long) -> Unit,
    resumeEp: Int = 0,
    resumeLine: String = "",
    resumePositionMs: Long = 0L,
    autoPlay: Boolean = false
) {
    val cached = remember(item.sid, item.id) { Repo.cachedDetail(item) }
    var detail by remember(item.sid, item.id) { mutableStateOf(cached) }
    var loading by remember(item.sid, item.id) { mutableStateOf(cached == null) }
    var loadError by remember(item.sid, item.id) { mutableStateOf<String?>(null) }
    var reloadTick by remember(item.sid, item.id) { mutableStateOf(0) }
    var selectedLine by remember(item.sid, item.id) { mutableStateOf(0) }
    var selectedEp by remember(item.sid, item.id) { mutableStateOf(0) }
    var showAllEpisodes by remember(item.sid, item.id) { mutableStateOf(false) }
    var descOpen by remember(item.sid, item.id) { mutableStateOf(false) }
    var favorite by remember(item.sid, item.id) { mutableStateOf(Library.isFavorite(item)) }
    var autoPlayed by remember(item.sid, item.id) { mutableStateOf(false) }

    LaunchedEffect(item.sid, item.id, reloadTick) {
        val hadDetail = detail != null
        if (!hadDetail) loading = true
        loadError = null
        val online = try { ServerApi.probe() } catch (e: Exception) { false }
        val fresh = try {
            if (online && item.entries.isNotEmpty()) {
                ServerApi.detail(item)?.takeIf { it.lines.isNotEmpty() } ?: Repo.detail(item)
            } else {
                Repo.detail(item)
            }
        } catch (e: Exception) { null }
        if (fresh != null && fresh.lines.isNotEmpty()) {
            detail = fresh
            Repo.saveDetail(item, fresh)
            selectedLine = resumeLineIndex(fresh.lines, resumeLine)
            selectedEp = resumeEp.coerceIn(0, maxOf(0, (fresh.lines.getOrNull(selectedLine)?.eps?.size ?: 1) - 1))
            loadError = null
        } else if (!hadDetail) {
            loadError = "线路加载失败，检查网络后重试"
        }
        loading = false
    }

    val shown = detail?.item ?: item
    val lines = detail?.lines.orEmpty()
    val line = lines.getOrNull(selectedLine)
    val episodes = line?.eps.orEmpty()
    val maxEpisodes = lines.maxOfOrNull { it.eps.size } ?: 0
    val has4k = lines.any { it.from.contains("4K", true) || it.quality.contains("4K", true) }
    val score = shown.score
    val metaParts = buildList {
        shown.year.take(4).takeIf { it.length == 4 }?.let(::add)
        shown.area.takeIf { it.isNotBlank() }?.let(::add)
        shown.type.split(',', '，', '/').map { it.trim() }.filter { it.isNotBlank() }
            .distinct().take(3).forEach(::add)
        (detail?.lang ?: shown.lang).takeIf { it.isNotBlank() }?.let(::add)
    }
    val meta = metaParts.joinToString(" · ")
    val visibleEpisodes = if (showAllEpisodes) episodes else episodes.take(60)

    // 从「最近观看」点进来时直接续播；缓存里的线路也可立即起播，不再等 8 秒汇总。
    LaunchedEffect(item.sid, item.id, loading, detail, autoPlay) {
        if (autoPlay && !autoPlayed && !loading && line != null && episodes.isNotEmpty()) {
            autoPlayed = true
            val idx = selectedEp.coerceIn(0, episodes.lastIndex)
            val start = if (idx == resumeEp) resumePositionMs else 0L
            onPlay(lines, idx, line, start)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item(key = "detail-head") {
            Box(Modifier.fillMaxWidth().height(292.dp)) {
                if (shown.pic.isNotBlank()) {
                    CoverImage(
                        url = shown.pic,
                        name = shown.name,
                        modifier = Modifier.fillMaxSize().alpha(0.26f),
                        fallbackSize = 54
                    )
                }
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            0f to Bg.copy(alpha = 0.12f),
                            0.42f to Bg.copy(alpha = 0.30f),
                            0.72f to Bg.copy(alpha = 0.86f),
                            1f to Bg
                        )
                    )
                )
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundIconButtonV2(Icons.Outlined.ArrowBack, "返回", onBack)
                    Spacer(Modifier.weight(1f))
                    RoundIconButtonV2(
                        if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        "收藏",
                        {
                            Library.toggleFavorite(item)
                            favorite = Library.isFavorite(item)
                        }
                    )
                }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        Modifier
                            .width(112.dp)
                            .height(166.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardBg)
                            .border(1.dp, Stroke, RoundedCornerShape(14.dp))
                    ) {
                        CoverImage(shown.pic, shown.name, Modifier.fillMaxSize(), fallbackSize = 34)
                        if (shown.remarks.isNotBlank()) {
                            Box(
                                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f)))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    shown.remarks.take(14),
                                    color = TextMain,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(15.dp))
                    Column(Modifier.weight(1f).padding(bottom = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BadgeV2(if (has4k) "4K 可选" else if (lines.isNotEmpty()) "高清" else "整理中")
                            if (score > 0.0) {
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    "★ ${String.format(Locale.US, "%.1f", score)}",
                                    color = AccentSoft,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            shown.name,
                            color = TextMain,
                            fontFamily = Serif,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 29.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(7.dp))
                        if (meta.isNotBlank()) {
                            Text(
                                meta,
                                color = TextSub,
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            if (lines.isNotEmpty()) "${lines.size} 条线路 · ${maxEpisodes} 集" else "线路整理中",
                            color = TextDim,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        item(key = "detail-body") {
            Column(Modifier.padding(horizontal = 20.dp)) {
                val d = detail
                if (d == null) {
                    Spacer(Modifier.height(10.dp))
                    DetailLoadingCardV2(loading, loadError) { reloadTick++ }
                } else {
                    val dLines = d.lines
                    val lineIdx = selectedLine.coerceIn(0, maxOf(0, dLines.lastIndex))
                    val currentLine = dLines.getOrNull(lineIdx)
                    val eps = currentLine?.eps.orEmpty()
                    val playIdx = selectedEp.coerceIn(0, maxOf(0, eps.lastIndex))
                    val lineResumeIdx = resumeLineIndex(dLines, resumeLine)
                    val playStart = if (lineIdx == lineResumeIdx && playIdx == resumeEp) resumePositionMs else 0L

                    if (currentLine != null && eps.isNotEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Accent)
                                .clickable { onPlay(dLines, playIdx, currentLine, playStart) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "▶  立即播放 · ${episodeChipLabel(eps.getOrNull(playIdx)?.name ?: "第${playIdx + 1}集")}",
                                color = Color(0xFF1A0D08),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(13.dp))
                    }

                    if (d.desc.isNotBlank()) {
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Panel)
                                .border(1.dp, Stroke.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("简介", color = TextMain, fontFamily = Serif, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                if (d.desc.length > 90) {
                                    Text(
                                        if (descOpen) "收起" else "展开",
                                        color = AccentSoft,
                                        fontSize = 10.sp,
                                        modifier = Modifier.clickable { descOpen = !descOpen }
                                    )
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(
                                d.desc,
                                color = TextSub,
                                fontSize = 11.sp,
                                lineHeight = 18.sp,
                                maxLines = if (descOpen) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    val infoRows = buildList {
                        shown.year.take(4).takeIf { it.length == 4 }?.let { add("年份" to it) }
                        shown.area.takeIf { it.isNotBlank() }?.let { add("地区" to it) }
                        shown.type.takeIf { it.isNotBlank() }?.let { add("类型" to it) }
                        (detail?.lang ?: shown.lang).takeIf { it.isNotBlank() }?.let { add("语言" to it) }
                        detail?.director?.takeIf { it.isNotBlank() }?.let { add("导演" to it) }
                        detail?.actors?.takeIf { it.isNotBlank() }?.let { add("主演" to it) }
                    }
                    if (infoRows.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Panel)
                                .border(1.dp, Stroke.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            infoRows.forEachIndexed { i, (k, v) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                                    Text(k, color = TextDim, fontSize = 10.sp, modifier = Modifier.width(44.dp))
                                    Text(
                                        v,
                                        color = TextSub,
                                        fontSize = 10.5.sp,
                                        lineHeight = 16.sp,
                                        maxLines = if (k == "主演") 3 else 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (i < infoRows.lastIndex) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(Stroke.copy(alpha = 0.45f)))
                                }
                            }
                        }
                    }

                    if (dLines.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        DetailLoadingCardV2(false, "这部暂时没有可用线路，换一部或稍后重试", onRetry = { reloadTick++ })
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text("播放线路", color = TextMain, fontFamily = Serif, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(7.dp))
                            Text("${dLines.size} 条", color = TextDim, fontSize = 9.sp, modifier = Modifier.padding(bottom = 2.dp))
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            items(dLines.size) { idx ->
                                val l = dLines[idx]
                                val active = idx == lineIdx
                                Column(
                                    Modifier
                                        .width(132.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (active) AccentDark else Panel)
                                        .border(1.dp, if (active) Accent.copy(alpha = 0.55f) else Stroke.copy(alpha = 0.75f), RoundedCornerShape(11.dp))
                                        .clickable {
                                            selectedLine = idx
                                            selectedEp = if (idx == lineResumeIdx) {
                                                resumeEp.coerceIn(0, maxOf(0, l.eps.lastIndex))
                                            } else 0
                                        }
                                        .padding(horizontal = 11.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            lineLangV2(l).ifBlank { "线路 ${idx + 1}" },
                                            color = if (active) AccentSoft else TextMain,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (l.from.contains("4K", true) || l.quality.contains("4K", true)) {
                                            Spacer(Modifier.width(5.dp))
                                            BadgeV2("4K")
                                        }
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    val lineMeta = buildList {
                                        l.quality.substringBefore("·").trim()
                                            .takeIf { it.isNotBlank() && it.length <= 10 && !it.contains("未测") }
                                            ?.let(::add)
                                        add("${l.eps.size} 集")
                                    }.joinToString(" · ")
                                    Text(
                                        lineMeta,
                                        color = if (active) AccentSoft.copy(alpha = 0.8f) else TextDim,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("选集", color = TextMain, fontFamily = Serif, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(7.dp))
                            Text("${eps.size} 集", color = TextDim, fontSize = 9.sp)
                            Spacer(Modifier.weight(1f))
                            if (eps.size > 60) {
                                Text(
                                    if (showAllEpisodes) "收起" else "展开全部",
                                    color = AccentSoft,
                                    fontSize = 10.sp,
                                    modifier = Modifier.clickable { showAllEpisodes = !showAllEpisodes }
                                )
                            }
                        }
                        if (eps.isEmpty()) {
                            Text("这条线路暂时没有选集，换一条线路试试", color = TextDim, fontSize = 10.sp)
                        } else {
                            visibleEpisodes.chunked(4).forEachIndexed { rowIdx, row ->
                                Row(
                                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    row.forEachIndexed { i, ep ->
                                        val idx = rowIdx * 4 + i
                                        val selected = idx == playIdx
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .height(39.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selected) AccentDark else Panel)
                                                .border(1.dp, if (selected) Accent else Stroke, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    selectedEp = idx
                                                    onPlay(dLines, idx, currentLine!!, if (idx == resumeEp) resumePositionMs else 0L)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                episodeChipLabel(ep.name),
                                                color = if (selected) AccentSoft else TextSub,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLoadingCardV2(
    loading: Boolean,
    message: String?,
    onRetry: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .border(1.dp, Stroke.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(20.dp),
                color = Accent,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.height(10.dp))
            Text("正在汇总线路，通常几秒", color = TextSub, fontSize = 10.sp)
        } else {
            Text(message ?: "线路加载失败", color = TextSub, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "重新加载",
                color = AccentSoft,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRetry).padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private fun episodeChipLabel(name: String): String {
    val raw = name.trim()
    val date = Regex("^第?(\\d{4})(\\d{2})(\\d{2})期").find(raw)
    if (date != null) return "${date.groupValues[2]}-${date.groupValues[3]}期"
    return if (raw.length > 8) raw.take(8) else raw
}

@Composable
private fun DetailStatV2(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Panel)
            .border(1.dp, Stroke.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = TextMain, fontFamily = Serif, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = TextDim, fontSize = 9.sp)
    }
}

private fun lineLangV2(line: Line): String {
    val hay = "${line.from} ${line.quality} ${line.eps.firstOrNull()?.name.orEmpty()}".lowercase()
    return when {
        hay.contains("粤语") || hay.contains("canto") -> "粤语"
        hay.contains("国语") || hay.contains("国配") || hay.contains("mandarin") -> "国语"
        hay.contains("英语") || hay.contains("english") -> "英语"
        hay.contains("日语") || hay.contains("japanese") -> "日语"
        hay.contains("韩语") || hay.contains("korean") -> "韩语"
        else -> ""
    }
}

private fun historySubV2(entry: WatchEntry): String {
    val pct = if (entry.durationMs > 0L) {
        ((entry.positionMs * 100L / entry.durationMs).coerceIn(0L, 100L)).toInt()
    } else -1
    return buildList {
        if (entry.lineName.isNotBlank()) add(prettyLineNameV2(entry.lineName))
        if (entry.epName.isNotBlank()) add(entry.epName)
        if (pct >= 0) add("已看 $pct%")
    }.joinToString(" · ").ifBlank { "继续观看" }
}

/** 播放记录里存的是线路域名，列表里别把 jinyingm3u8 这种原始值直接给用户看。 */
private fun prettyLineNameV2(raw: String): String {
    val hay = raw.lowercase()
    return when {
        hay.contains("粤语") || hay.contains("canto") -> "粤语线路"
        hay.contains("国语") || hay.contains("国配") || hay.contains("mandarin") -> "国语线路"
        hay.contains("英语") || hay.contains("english") -> "英语线路"
        hay.contains("日语") || hay.contains("japanese") -> "日语线路"
        hay.contains("韩语") || hay.contains("korean") -> "韩语线路"
        hay.contains("m3u8") || hay.contains("http") || hay.contains(".com") || hay.contains(".net") -> "默认线路"
        else -> raw
    }
}

// ---------------------------------------------------------------------------
// 我的
// ---------------------------------------------------------------------------

@Composable
private fun MeScreenV2(
    onSources: () -> Unit,
    onHistory: () -> Unit,
    onFavorites: () -> Unit
) {
    val history = Library.visibleHistory
    val favorites = Library.visibleFavorites
    val update = Repo.update
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 110.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("我的", color = TextMain, fontFamily = Serif, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("本机资料", color = TextDim, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(62.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Accent, Color(0xFFD94D66)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("影", color = Color(0xFF1D0C08), fontFamily = Serif, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("影视仓用户", color = TextMain, fontFamily = Serif, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("${Repo.sources.size} 个源 · 手机直连片库", color = TextSub, fontSize = 10.sp)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeStatV2("${favorites.size}", "收藏", Modifier.weight(1f))
                MeStatV2("${history.size}", "看过", Modifier.weight(1f))
                MeStatV2("${Repo.sources.size}", "片源", Modifier.weight(1f))
            }
        }

        update?.let { up -> item { UpdateCardV2(up) } }

        item { SectionTitleV2("我的片单") }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Panel)
                    .border(1.dp, Stroke.copy(alpha = 0.7f), RoundedCornerShape(15.dp))
            ) {
                MeEntryRowV2(
                    icon = Icons.Outlined.PlayArrow,
                    title = "最近观看",
                    value = if (history.isEmpty()) "还没有记录" else "${history.size} 部",
                    hint = if (history.isEmpty()) "点开一部片，播放记录会留在这里" else "点进去选上次看到的位置",
                    onClick = onHistory
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Stroke.copy(alpha = 0.5f)))
                MeEntryRowV2(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = "我的收藏",
                    value = if (favorites.isEmpty()) "还没有收藏" else "${favorites.size} 部",
                    hint = if (favorites.isEmpty()) "详情页点右上角的心，就会留在这里" else "都在里面，慢慢挑",
                    onClick = onFavorites
                )
            }
        }

        item { SectionTitleV2("播放源", count = Repo.sources.size, unit = "个") }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSources() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Mint))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${Repo.sources.size} 个源 · 手机直连片库",
                        color = TextMain,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "内置源只读；自定义源可添加、编辑、测试和删除。",
                        color = TextDim,
                        fontSize = 9.sp,
                        lineHeight = 14.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("管理", color = AccentSoft, fontSize = 10.sp)
                Icon(Icons.Outlined.ChevronRight, null, tint = TextDim, modifier = Modifier.size(17.dp))
            }
        }

        item { SectionTitleV2("设置") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("显示「私密」分类", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("默认关闭。关闭时这类内容不出现在首页、分类页和搜索结果里；打开后分类页才多一个入口。", color = TextDim, fontSize = 9.sp, lineHeight = 14.sp)
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = Library.showPrivate,
                    onCheckedChange = { Library.updateShowPrivate(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF1D0C08),
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = TextDim,
                        uncheckedTrackColor = Panel
                    )
                )
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("影视仓 ${BuildConfig.VERSION_NAME} · 本地片库", color = TextDim, fontSize = 9.sp)
                Spacer(Modifier.height(4.dp))
                Text("不登录、不上传、不刷积分", color = TextDim.copy(alpha = 0.7f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun MeEntryRowV2(
    icon: ImageVector,
    title: String,
    value: String,
    hint: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = AccentSoft, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(7.dp))
                Text(value, color = TextSub, fontSize = 10.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(hint, color = TextDim, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = TextDim, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun HistoryScreenV2(
    onBack: () -> Unit,
    onResume: (WatchEntry) -> Unit
) {
    val history = Library.visibleHistory
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item { SubPageBarV2("最近观看", if (history.isEmpty()) "还没有观看记录" else "${history.size} 部 · 按最近看的排", onBack) }
        if (history.isEmpty()) {
            item { MeEmptyV2("还没有观看记录", "点开一部片，播放记录会留在这里") }
        } else {
            items(history) { entry ->
                WatchRowV2(entry.item, historySubV2(entry)) { onResume(entry) }
            }
        }
    }
}

@Composable
private fun FavoritesScreenV2(
    onBack: () -> Unit,
    onOpen: (Item) -> Unit
) {
    val favorites = Library.visibleFavorites
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item { SubPageBarV2("我的收藏", if (favorites.isEmpty()) "还没有收藏" else "${favorites.size} 部", onBack) }
        if (favorites.isEmpty()) {
            item { MeEmptyV2("还没有收藏", "在详情页点右上角的心，就会留在这里") }
        } else {
            items(favorites) { item ->
                WatchRowV2(item, item.type.ifBlank { "收藏" }) { onOpen(item) }
            }
        }
    }
}

@Composable
private fun SubPageBarV2(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButtonV2(Icons.Outlined.ArrowBack, "返回", onBack)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = TextMain, fontFamily = Serif, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextDim, fontSize = 9.sp)
        }
    }
}

@Composable
private fun UpdateDialogV2(info: UpdateInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1B1E27), Panel)))
                .border(1.dp, Accent.copy(alpha = 0.42f), RoundedCornerShape(22.dp))
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Color(0xFF1D0C08), modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("发现新版本", color = AccentSoft, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "影视仓 ${info.versionName}",
                        color = TextMain,
                        fontFamily = Serif,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                info.notes.ifBlank { "修复已知问题，优化播放体验。" },
                color = TextSub,
                fontSize = 12.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(12.dp))
            Text("下载后覆盖安装即可，收藏和观看记录会保留。", color = TextDim, fontSize = 10.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "稍后",
                    color = TextSub,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(Accent)
                        .clickable {
                            openUpdate(ctx, info)
                            onDismiss()
                        }
                        .padding(horizontal = 18.dp, vertical = 11.dp)
                ) {
                    Text("去下载", color = Color(0xFF1D0C08), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun UpdateCardV2(info: UpdateInfo) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF321B13), Panel)))
            .border(1.dp, Accent.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
            .clickable { openUpdate(ctx, info) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = AccentSoft, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("发现新版本 ${info.versionName}", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text("点这里下载覆盖安装，记录会保留", color = TextSub, fontSize = 9.sp)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = AccentSoft, modifier = Modifier.size(17.dp))
    }
}

private fun openUpdate(ctx: android.content.Context, info: UpdateInfo) {
    val url = info.apk.ifBlank { "https://github.com/lxl-65/yingyin/releases/latest" }
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun MeStatV2(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(13.dp)).background(Panel).border(1.dp, Stroke.copy(alpha = 0.7f), RoundedCornerShape(13.dp)).padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = TextMain, fontFamily = Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(label, color = TextDim, fontSize = 9.sp)
    }
}

@Composable
private fun MeEmptyV2(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, color = TextSub, fontSize = 11.sp)
        Spacer(Modifier.height(3.dp))
        Text(sub, color = TextDim, fontSize = 9.sp)
    }
}

@Composable
private fun WatchRowV2(item: Item, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(48.dp).height(67.dp).clip(RoundedCornerShape(8.dp)).background(CardBg)) {
            CoverImage(item.pic, item.name, Modifier.fillMaxSize(), fallbackSize = 20)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(sub, color = TextSub, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = TextDim, modifier = Modifier.size(17.dp))
    }
}
