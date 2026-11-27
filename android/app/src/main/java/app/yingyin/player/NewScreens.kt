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
    data class Category(val key: String, val title: String) : AppRouteV2
    data class Detail(val item: Item, val resumeEp: Int = 0) : AppRouteV2
    data class Play(
        val item: Item,
        val title: String,
        val lines: List<Line>,
        val epIdx: Int
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
        is AppRouteV2.Detail -> "detail:${r.item.sid}:${r.item.id}"
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
                onResume = { item, ep -> go(AppRouteV2.Detail(item, ep)) },
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
                onOpen = { go(AppRouteV2.Detail(it)) },
                onBack = { back() }
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
                onBack = { back() },
                onPlay = { lines, idx, line ->
                    Library.record(
                        r.item,
                        "线路 ${lines.indexOf(line) + 1}",
                        idx,
                        line.eps.getOrNull(idx)?.name ?: "第${idx + 1}集"
                    )
                    go(AppRouteV2.Play(r.item, r.item.name, lines, idx))
                }
            )

            is AppRouteV2.Play -> PlayerScreen(
                title = r.title,
                lines = r.lines,
                epIdx = r.epIdx,
                onBack = {
                    // 从播放器返回详情时带回当前集，避免详情页又跳回第 1 集。
                    stack = stack.dropLast(2) + AppRouteV2.Detail(r.item, r.epIdx)
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
        AsyncImage(
            model = url,
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
    onMore: (() -> Unit)? = null
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
            Text("$count 部", color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
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
    onResume: (Item, Int) -> Unit,
    onCategory: (String, String) -> Unit
) {
    val items = Repo.homeItems.toList()
    val serverGroups = Repo.serverGroups
    val sections = remember(items, serverGroups, Repo.serverFeed) {
        if (serverGroups.isNotEmpty()) {
            buildList {
                if (Repo.serverFeed.isNotEmpty()) add(HomeSectionV2("home", "最新上线", Repo.serverFeed))
                addAll(serverGroups.map { HomeSectionV2(it.cat, it.name, it.items) })
            }
        } else buildHomeSections(items)
    }
    val hero = remember(items) { pickHero(items) }
    val history = Library.visibleHistory.firstOrNull()
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var firstLoad by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (Repo.homeItems.isEmpty()) {
            val serverOk = Repo.loadServerHome()
            if (!serverOk) Repo.home()
        }
        firstLoad = false
    }

    if (firstLoad && items.isEmpty()) {
        HomeLoadingV2()
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 110.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item { TopBarV2(onSearch, onMe) }
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 15.dp)) {
                Text("今晚，", color = TextMain, fontFamily = Serif, fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 39.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("看点什么。", color = AccentSoft, fontFamily = Serif, fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 39.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${if (Repo.serverSourceCount > 0) Repo.serverSourceCount else Repo.sources.size} 个源已接入 · 先给你最好的线路",
                    color = TextSub,
                    fontSize = 11.sp,
                    letterSpacing = 0.3.sp
                )
            }
        }

        hero?.let {
            item { HeroCardV2(it, onOpen = { onOpen(it) }) }
        }

        item(key = "home-categories") {
            CategoryChipsV2(onCategory = onCategory)
        }

        if (history != null) {
            item { SectionTitleV2("继续观看", onMore = { onResume(history.item, history.epIdx) }) }
            item { ContinueCardV2(history, onOpen = { onResume(history.item, history.epIdx) }) }
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
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(286.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CardBg)
    ) {
        CoverImage(
            url = item.pic,
            name = item.name,
            modifier = Modifier.fillMaxSize(),
            fallbackSize = 64
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.05f),
                        0.42f to Color.Black.copy(alpha = 0.12f),
                        0.72f to Color.Black.copy(alpha = 0.72f),
                        1f to Color.Black.copy(alpha = 0.95f)
                    )
                )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgeV2("本周精选")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                item.name,
                color = Color.White,
                fontFamily = Serif,
                fontSize = 29.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 32.sp
            )
            Spacer(Modifier.height(5.dp))
            Text(
                listOf(item.year, item.type, item.area).filter { it.isNotBlank() }.joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Accent)
                        .clickable(onClick = onOpen)
                        .padding(horizontal = 15.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Color(0xFF1B0B05), modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("立即播放", color = Color(0xFF1B0B05), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(17.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpen)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("查看详情", color = Color.White.copy(alpha = 0.86f), fontSize = 11.sp)
                    Spacer(Modifier.width(3.dp))
                    Text("↗", color = AccentSoft, fontSize = 15.sp)
                }
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
                "${entry.lineName} · ${entry.epName}",
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDim, fontSize = 9.sp, modifier = Modifier.width(34.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChipV2("全部", selected.isEmpty()) { onSelect("") }
            }
            items(options) { option ->
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
    var facets by remember(key) { mutableStateOf(ServerFacets()) }
    var loadSeq by remember(key) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun load(p: Int, append: Boolean) {
        val my = ++loadSeq
        if (append) loadingMore = true else loading = true
        try {
            val cat = if (key == "all") "home" else key
            val online = ServerApi.probe()
            val res = if (online) ServerApi.list(cat, p, genre, area, year) else null
            if (my != loadSeq) return
            if (res != null) {
                items = if (append) (items + res.items).distinctBy { norm(it.name) } else res.items
                alive = res.alive
                sources = res.sources
                facets = res.facets
                page = p
                error = null
            } else {
                // PC 不在线：手机直接按订阅里的 cats 分类拉源，朋友不需要开电脑。
                val local = Repo.category(cat, p, genre, area, year)
                if (my != loadSeq) return
                val localItems = local.items.ifEmpty {
                    Repo.homeItems.filter { key == "all" || categoryKeyOf(it) == key }
                }
                items = if (append) (items + localItems).distinctBy { norm(it.name) } else localItems
                alive = if (local.alive > 0) local.alive else Repo.sources.size
                sources = if (local.sources > 0) local.sources else Repo.sources.size
                facets = if (local.facets.genres.isNotEmpty() || local.facets.areas.isNotEmpty() || local.facets.years.isNotEmpty()) {
                    local.facets
                } else {
                    Repo.facetsFor(Repo.homeItems.filter { key == "all" || categoryKeyOf(it) == key })
                }
                page = p
                error = if (items.isEmpty()) "这个分类暂时没有加载到内容，换一个分类或稍后重试" else null
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

    LaunchedEffect(key) { load(1, false) }

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
            FilterRowV2("类型", facets.genres, genre) { genre = it; scope.launch { load(1, false) } }
            FilterRowV2("地区", facets.areas, area) { area = it; scope.launch { load(1, false) } }
            FilterRowV2("年份", facets.years, year) { year = it; scope.launch { load(1, false) } }
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(items) { item ->
                    PosterCardV2(item, modifier = Modifier.fillMaxWidth(), onClick = { onOpen(item) })
                }
            }
            if (items.size >= 30) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = { scope.launch { load(page + 1, true) } },
                        enabled = !loadingMore
                    ) {
                        if (loadingMore) CircularProgressIndicator(Modifier.size(15.dp), color = Accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(if (loadingMore) "正在加载…" else "加载更多", color = Accent, fontSize = 11.sp)
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

@Composable
fun DetailScreenV2(
    item: Item,
    onBack: () -> Unit,
    onPlay: (List<Line>, Int, Line) -> Unit,
    resumeEp: Int = 0
) {
    var detail by remember(item.sid, item.id) { mutableStateOf<Detail?>(null) }
    var loading by remember(item.sid, item.id) { mutableStateOf(true) }
    var loadError by remember(item.sid, item.id) { mutableStateOf<String?>(null) }
    var reloadTick by remember(item.sid, item.id) { mutableStateOf(0) }
    var selectedLine by remember(item.sid, item.id) { mutableStateOf(0) }
    var selectedEp by remember(item.sid, item.id) { mutableStateOf(0) }
    var showAllEpisodes by remember(item.sid, item.id) { mutableStateOf(false) }
    var descOpen by remember(item.sid, item.id) { mutableStateOf(false) }
    var favorite by remember(item.sid, item.id) { mutableStateOf(Library.isFavorite(item)) }

    LaunchedEffect(item.sid, item.id, reloadTick) {
        loading = true
        loadError = null
        detail = null
        val online = try { ServerApi.probe() } catch (e: Exception) { false }
        detail = try {
            if (online && item.entries.isNotEmpty()) {
                ServerApi.detail(item)?.takeIf { it.lines.isNotEmpty() } ?: Repo.detail(item)
            } else {
                Repo.detail(item)
            }
        } catch (e: Exception) { null }
        if (detail?.lines.isNullOrEmpty()) loadError = "线路加载失败，检查网络后重试"
        selectedLine = 0
        selectedEp = resumeEp.coerceIn(0, maxOf(0, (detail?.lines?.firstOrNull()?.eps?.size ?: 1) - 1))
        loading = false
    }

    val lines = detail?.lines.orEmpty()
    val line = lines.getOrNull(selectedLine)
    val episodes = line?.eps.orEmpty()
    val maxEpisodes = lines.maxOfOrNull { it.eps.size } ?: 0
    val sourceCount = lines.map { it.from }.distinct().count()
    val visibleEpisodes = if (showAllEpisodes) episodes else episodes.take(60)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 35.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(310.dp)) {
                CoverImage(
                    url = item.pic,
                    name = item.name,
                    modifier = Modifier.fillMaxSize(),
                    fallbackSize = 64
                )
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.12f),
                            0.38f to Color.Black.copy(alpha = 0.16f),
                            0.70f to Color.Black.copy(alpha = 0.62f),
                            0.88f to Bg.copy(alpha = 0.94f),
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
                Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BadgeV2(if (lines.any { it.from.contains("4K", true) }) "4K 可选" else "高清")
                        Spacer(Modifier.width(7.dp))
                        Text(
                            when {
                                loading -> "线路同步中…"
                                sourceCount > 0 -> "$sourceCount 个来源"
                                else -> "暂无可用来源"
                            },
                            color = TextSub,
                            fontSize = 9.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.name,
                        color = TextMain,
                        fontFamily = Serif,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 35.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        listOf(item.year, item.type, item.area).filter { it.isNotBlank() }.joinToString("  ·  "),
                        color = TextSub,
                        fontSize = 10.sp
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                if (loading) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 26.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = Accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(9.dp))
                        Text("正在展开线路…", color = TextSub, fontSize = 11.sp)
                    }
                } else if (lines.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("线路没加载出来", color = TextMain, fontFamily = Serif, fontSize = 17.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(loadError ?: "检查网络后再试一次", color = TextSub, fontSize = 10.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Panel)
                                .border(1.dp, Stroke, RoundedCornerShape(50))
                                .clickable { reloadTick++ }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Refresh, null, tint = AccentSoft, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("重新加载", color = AccentSoft, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Accent)
                            .clickable {
                                if (episodes.isNotEmpty()) {
                                    val playIdx = selectedEp.coerceIn(0, episodes.lastIndex)
                                    selectedEp = playIdx
                                    onPlay(lines, playIdx, line!!)
                                }
                            }
                            .padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.PlayArrow, null, tint = Color(0xFF1B0B05), modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (episodes.isNotEmpty()) "立即播放 · ${episodes.getOrNull(selectedEp.coerceIn(0, episodes.lastIndex))?.name ?: "第${selectedEp + 1}集"}" else "立即播放",
                            color = Color(0xFF1B0B05),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailStatV2("${lines.size}", "线路", Modifier.weight(1f))
                        DetailStatV2("${sourceCount}", "来源", Modifier.weight(1f))
                        DetailStatV2(if (maxEpisodes > 0) "$maxEpisodes" else "—", "集数", Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(22.dp))
                    Text("线路", color = TextMain, fontFamily = Serif, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text("点一条线路切换；播放失败会自动换下一条", color = TextDim, fontSize = 9.sp)
                }
            }
        }

        if (lines.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 11.dp, bottom = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(lines.size) { index ->
                        val l = lines[index]
                        val selected = index == selectedLine
                        Column(
                            Modifier
                                .width(139.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (selected) AccentDark else Panel)
                                .border(1.dp, if (selected) Accent else Stroke, RoundedCornerShape(11.dp))
                                .clickable {
                                    selectedLine = index
                                    selectedEp = 0
                                    showAllEpisodes = false
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "线路 ${index + 1}",
                                    color = if (selected) AccentSoft else TextMain,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (l.from.contains("4K", true) || l.quality.contains("4K", true)) {
                                    Spacer(Modifier.width(5.dp))
                                    BadgeV2("4K")
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            val lineMeta = buildList {
                                if (l.quality.isNotBlank()) add(l.quality)
                                if (l.latency > 0) add("${l.latency}ms")
                                add("${l.eps.size} 集")
                            }.joinToString(" · ")
                            Text(
                                lineMeta,
                                color = if (selected) AccentSoft.copy(alpha = 0.8f) else TextDim,
                                fontSize = 8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("选集", color = TextMain, fontFamily = Serif, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(7.dp))
                        Text("${episodes.size} 集", color = TextDim, fontSize = 9.sp)
                        Spacer(Modifier.weight(1f))
                        if (episodes.size > 60) {
                            Text(
                                if (showAllEpisodes) "收起" else "展开全部",
                                color = AccentSoft,
                                fontSize = 10.sp,
                                modifier = Modifier.clickable { showAllEpisodes = !showAllEpisodes }
                            )
                        }
                    }
                    Spacer(Modifier.height(11.dp))
                    visibleEpisodes.chunked(4).forEachIndexed { rowIdx, row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEachIndexed { i, ep ->
                                val idx = rowIdx * 4 + i
                                val selected = idx == selectedEp
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(39.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) AccentDark else Panel)
                                        .border(1.dp, if (selected) Accent else Stroke, RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedEp = idx
                                            onPlay(lines, idx, line!!)
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

            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("简介", color = TextMain, fontFamily = Serif, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    val desc = detail?.desc.orEmpty().ifBlank { "暂无简介" }
                    Text(
                        desc,
                        color = TextSub,
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                        maxLines = if (descOpen) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (desc.length > 80) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (descOpen) "收起" else "展开",
                            color = AccentSoft,
                            fontSize = 10.sp,
                            modifier = Modifier.clickable { descOpen = !descOpen }
                        )
                    }
                    if (!detail?.actors.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("主演 · ${detail?.actors}", color = TextDim, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
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

// ---------------------------------------------------------------------------
// 我的
// ---------------------------------------------------------------------------

@Composable
private fun MeScreenV2(onOpen: (Item) -> Unit, onBack: () -> Unit) {
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
                MeStatV2("${if (Repo.serverSourceCount > 0) Repo.serverSourceCount else Repo.sources.size}", "片源", Modifier.weight(1f))
            }
        }

        update?.let { up -> item { UpdateCardV2(up) } }

        item { SectionTitleV2("最近观看", count = history.size) }
        if (history.isEmpty()) {
            item { MeEmptyV2("还没有观看记录", "点开一部片，播放记录会留在这里") }
        } else {
            items(history.take(3)) { entry ->
                WatchRowV2(entry.item, entry.lineName + " · " + entry.epName, onOpen)
            }
        }

        item { SectionTitleV2("我的收藏", count = favorites.size) }
        if (favorites.isEmpty()) {
            item { MeEmptyV2("还没有收藏", "在详情页点右上角的心，就会留在这里") }
        } else {
            items(favorites.take(5)) { item -> WatchRowV2(item, item.type.ifBlank { "收藏" }, onOpen) }
        }

        item { SectionTitleV2("播放源", count = if (Repo.serverSourceCount > 0) Repo.serverSourceCount else Repo.sources.size) }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Mint))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "手机直连 ${Repo.sources.size} 个源；电脑在线时自动增强",
                        color = TextSub,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(if (ServerApi.isOnline()) "家庭增强" else "独立模式", color = Mint, fontSize = 9.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text("不依赖电脑；电脑不在线时也能正常搜索、分类和播放。", color = TextDim, fontSize = 9.sp, lineHeight = 14.sp)
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
private fun WatchRowV2(item: Item, sub: String, onOpen: (Item) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(horizontal = 20.dp, vertical = 7.dp),
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
