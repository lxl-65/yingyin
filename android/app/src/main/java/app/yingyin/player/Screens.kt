package app.yingyin.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, right: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Text(
                "‹", color = Accent, fontSize = 26.sp,
                modifier = Modifier.clickable { onBack() }.padding(horizontal = 8.dp)
            )
        }
        Text(title, color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        right()
    }
}

@Composable
fun PosterCard(item: Item, isTv: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .then(
                if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(CardBg)
        ) {
            if (item.pic.isNotBlank()) {
                AsyncImage(
                    model = item.pic,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (item.srcs > 1) {
                Text(
                    "${item.srcs} 源",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            item.name,
            color = TextMain,
            fontSize = if (isTv) 13.5.sp else 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        val meta = listOfNotNull(
            item.year.takeIf { it.isNotBlank() },
            item.type.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, color = TextSub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun HomeScreen(
    isTv: Boolean,
    onSearch: () -> Unit,
    onMe: () -> Unit,
    onOpen: (Item) -> Unit
) {
    val list = Repo.homeItems
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = Repo.homeItems.isEmpty()
        Repo.home()
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar("影视仓") {
            Text(
                "搜索", color = TextSub, fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onSearch).padding(8.dp)
            )
            Text(
                "我的", color = TextSub, fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onMe).padding(8.dp)
            )
        }
        Text(
            "${Repo.subState} · 共 ${list.size} 部",
            color = TextSub,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 15.dp, bottom = 6.dp)
        )
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }

            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有取到内容\n检查网络后下拉重试", color = TextSub, fontSize = 13.sp)
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(if (isTv) 5 else 3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(list, key = { it.sid + ":" + it.id }) { item ->
                    PosterCard(item, isTv) { onOpen(item) }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(isTv: Boolean, onBack: () -> Unit, onOpen: (Item) -> Unit) {
    var kw by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<Item>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        if (kw.isBlank()) return
        scope.launch {
            busy = true
            searched = true
            list = Repo.search(kw.trim())
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar("搜索", onBack)
        TextField(
            value = kw,
            onValueChange = { kw = it },
            placeholder = { Text("片名、演员", color = TextSub, fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                focusedTextColor = TextMain,
                unfocusedTextColor = TextMain,
                cursorColor = Accent
            )
        )
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(Accent, RoundedCornerShape(9.dp))
                    .clickable { runSearch() }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(if (busy) "搜索中…" else "搜索", color = Color.White, fontSize = 13.sp)
            }
            Spacer(Modifier.width(10.dp))
            if (searched && !busy) {
                Text("${list.size} 个结果", color = TextSub, fontSize = 11.sp)
            }
        }
        if (busy) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.sid + ":" + it.id }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(width = 58.dp, height = 82.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardBg)
                        ) {
                            if (item.pic.isNotBlank()) {
                                AsyncImage(
                                    model = item.pic,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = TextMain, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    item.year.takeIf { it.isNotBlank() },
                                    item.type.takeIf { it.isNotBlank() },
                                    item.remarks.takeIf { it.isNotBlank() }
                                ).joinToString(" · "),
                                color = TextSub, fontSize = 11.sp, maxLines = 2,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text("${item.srcs} 个源 · 评分 ${item.score}", color = Accent, fontSize = 10.5.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailScreen(item: Item, onBack: () -> Unit, onPlay: (String, List<Line>, Int) -> Unit) {
    var detail by remember { mutableStateOf<Detail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var lineIdx by remember { mutableStateOf(0) }

    LaunchedEffect(item.sid + ":" + item.id) {
        loading = true
        detail = Repo.detail(item)
        lineIdx = 0
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar(item.name, onBack)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }

            detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("这个源打不开这部片\n换个源或稍后再试", color = TextSub, fontSize = 13.sp)
            }

            else -> {
                val d = detail!!
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
                ) {
                    Text(
                        listOfNotNull(
                            d.item.year.takeIf { it.isNotBlank() },
                            d.item.type.takeIf { it.isNotBlank() },
                            d.item.area.takeIf { it.isNotBlank() },
                            d.item.remarks.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        color = TextSub, fontSize = 11.5.sp
                    )
                    if (d.actors.isNotBlank()) {
                        Text("主演：${d.actors}", color = TextSub, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (d.desc.isNotBlank()) {
                        Text(
                            d.desc, color = Color(0xFFA8ADB9), fontSize = 12.sp, lineHeight = 19.sp,
                            maxLines = 6, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Text(
                        "线路", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        d.lines.forEachIndexed { i, line ->
                            val on = i == lineIdx
                            Text(
                                "${line.from} · ${line.eps.size}集",
                                color = if (on) Accent else TextSub,
                                fontSize = 11.5.sp,
                                modifier = Modifier
                                    .background(
                                        if (on) Color(0x22FF5D3B) else CardBg,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        if (on) 1.dp else 0.dp,
                                        if (on) Accent else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { lineIdx = i }
                                    .padding(horizontal = 11.dp, vertical = 7.dp)
                            )
                        }
                    }
                    val eps = d.lines.getOrNull(lineIdx)?.eps ?: emptyList()
                    Column(Modifier.padding(top = 14.dp)) {
                        eps.withIndex().chunked(4).forEach { rowEps ->
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowEps.forEach { pair ->
                                    val epIndex = pair.index
                                    val ep = pair.value
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .background(CardBg, RoundedCornerShape(9.dp))
                                            .clickable { onPlay(ep.name, d.lines, epIndex) }
                                            .padding(vertical = 11.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(ep.name, color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                repeat(4 - rowEps.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun MeScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val state = Repo.subState
    val sources = Repo.sources
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val subUrls = remember {
        BuildConfig.SUB_URLS.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar("我的", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Text("订阅", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(state, color = TextSub, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            Repo.update?.let { up ->
                Column(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .background(CardBg, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text("发现新版本 ${up.versionName}", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (up.notes.isNotBlank()) {
                        Text(up.notes, color = TextSub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Box(
                        Modifier
                            .padding(top = 10.dp)
                            .background(Accent, RoundedCornerShape(9.dp))
                            .clickable {
                                try {
                                    ctx.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(up.apk)
                                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (e: Exception) { }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text("去下载", color = Color.White, fontSize = 12.sp) }
                }
            }
            if (subUrls.isEmpty()) {
                Text(
                    "当前为内置源，未配置订阅地址。\n改 BuildConfig.SUB_URLS 即可远程更新源。",
                    color = TextSub, fontSize = 11.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .background(Accent, RoundedCornerShape(10.dp))
                        .clickable {
                            if (!busy) {
                                busy = true
                                scope.launch {
                                    Repo.sync(subUrls)
                                    busy = false
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(if (busy) "同步中…" else "同步订阅", color = Color.White, fontSize = 12.5.sp)
                }
            }

            Text(
                "当前源（${sources.size}）", color = TextMain, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp)
            )
            sources.forEach { s ->
                Column(Modifier.padding(vertical = 7.dp)) {
                    Text(s.name, color = TextMain, fontSize = 12.5.sp)
                    Text(s.api, color = TextSub, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                "影视仓 Android ${BuildConfig.VERSION_NAME} · 直连源站",
                color = TextSub, fontSize = 11.sp, modifier = Modifier.padding(top = 22.dp, bottom = 24.dp)
            )
        }
    }
}
