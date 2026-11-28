package app.yingyin.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

/**
 * 播放源管理。
 *
 * 内置源随订阅更新，只读；用户自己加的源存在本机，可改可删。
 * 这里只改「App 用哪个片库」，不会去改片源站里的片库内容——那是对方服务器上的数据。
 */
@Composable
fun SourcesScreenV2(onBack: () -> Unit) {
    var selected by remember { mutableStateOf<Source?>(null) }
    var adding by remember { mutableStateOf(false) }
    val sources = Repo.sources
    val customCount = sources.count { it.custom }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 20.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clipCircleClickable(onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.ArrowBack, "返回", tint = TextMain, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(2.dp))
                Column(Modifier.weight(1f)) {
                    Text("播放源", color = TextMain, fontFamily = Serif, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("内置源只读，自定义源可改", color = TextDim, fontSize = 9.sp)
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SourceStat("${sources.size}", "全部源", Modifier.weight(1f))
                SourceStat("$customCount", "自定义", Modifier.weight(1f))
                SourceStat("${sources.count { !it.custom }}", "内置", Modifier.weight(1f))
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Accent)
                    .clickable { adding = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, null, tint = Color(0xFF1B0B05), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("添加自定义片源", color = Color(0xFF1B0B05), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Apple CMS 接口", color = Color(0xFF1B0B05).copy(alpha = 0.72f), fontSize = 9.sp)
            }
        }

        item {
            Text(
                "点任意一行查看详情、测试连接；自定义源还能编辑或删除。",
                color = TextDim,
                fontSize = 9.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        items(sources, key = { it.key + "|" + it.api }) { source ->
            SourceRowV2(source) { selected = source }
        }

        item {
            Text(
                "说明：这里只管理 App 使用的片库地址，不能修改片源站自己的影片内容。内置源会随着订阅地址更新；你自己加的源不会被订阅覆盖。",
                color = TextDim.copy(alpha = 0.82f),
                fontSize = 9.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
    }

    if (selected != null || adding) {
        SourceEditDialogV2(
            source = selected,
            onDismiss = { selected = null; adding = false }
        )
    }
}

@Composable
private fun SourceRowV2(source: Source, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(7.dp).clipCircle(source.custom),
        ) {
            Box(Modifier.fillMaxSize().background(if (source.custom) Accent else Mint))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    source.name,
                    color = TextMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(7.dp))
                SourceBadge(if (source.custom) "自定义" else "内置")
            }
            Spacer(Modifier.height(3.dp))
            Text(
                source.api,
                color = TextDim,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (source.ms > 0) {
                Spacer(Modifier.height(2.dp))
                Text("上次响应 ${source.ms}ms", color = TextDim.copy(alpha = 0.7f), fontSize = 8.sp)
            }
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = TextDim, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun SourceEditDialogV2(source: Source?, onDismiss: () -> Unit) {
    val editable = source == null || source.custom
    var name by remember(source?.key) { mutableStateOf(source?.name ?: "") }
    var api by remember(source?.key) { mutableStateOf(source?.api ?: "") }
    var testing by remember { mutableStateOf(false) }
    var testText by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1B1E27), Panel)))
                .border(1.dp, Accent.copy(alpha = 0.42f), RoundedCornerShape(22.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                if (source == null) "添加自定义片源" else if (editable) "编辑自定义片源" else "内置片源",
                color = TextMain,
                fontFamily = Serif,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                if (editable) "填 Apple CMS 的采集接口；保存后会在搜索、分类和详情里参与。" else "内置源随订阅更新，不能在 App 里修改。",
                color = TextDim,
                fontSize = 9.sp,
                lineHeight = 14.sp
            )
            Spacer(Modifier.height(16.dp))

            if (editable) {
                SourceFieldLabel("名称")
                SourceTextField(name, { name = it }, "例如：我的备用源")
                Spacer(Modifier.height(12.dp))
                SourceFieldLabel("接口地址")
                SourceTextField(api, { api = it }, "https://example.com/api.php/provide/vod")
            } else {
                SourceFieldLabel("名称")
                Text(source?.name.orEmpty(), color = TextMain, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                SourceFieldLabel("接口地址")
                Text(source?.api.orEmpty(), color = TextSub, fontSize = 10.sp, lineHeight = 15.sp)
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Panel)
                        .border(1.dp, Stroke, RoundedCornerShape(50))
                        .clickable {
                            if (testing) return@clickable
                            if (api.isBlank()) {
                                error = "先填接口地址"
                            } else {
                                testing = true
                                testText = null
                                error = null
                                scope.launch {
                                    val r = Repo.testSource(api)
                                    testOk = r.ok
                                    testText = r.message
                                    testing = false
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (testing) {
                        CircularProgressIndicator(color = Accent, modifier = Modifier.size(13.dp), strokeWidth = 1.5.dp)
                    } else {
                        Text("测试连接", color = AccentSoft, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.width(10.dp))
                testText?.let {
                    Text(
                        it,
                        color = if (testOk) Mint else Color(0xFFE28A75),
                        fontSize = 9.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(9.dp))
                Text(it, color = Color(0xFFE28A75), fontSize = 9.sp, lineHeight = 14.sp)
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (source?.custom == true) {
                    Text(
                        if (confirmDelete) "确认删除" else "删除这个源",
                        color = if (confirmDelete) Color(0xFFE28A75) else TextDim,
                        fontSize = 10.sp,
                        modifier = Modifier.clickable {
                            if (confirmDelete) {
                                Repo.removeCustomSource(source.key)
                                onDismiss()
                            } else {
                                confirmDelete = true
                            }
                        }
                    )
                    if (confirmDelete) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "取消",
                            color = TextSub,
                            fontSize = 10.sp,
                            modifier = Modifier.clickable { confirmDelete = false }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "关闭",
                    color = TextSub,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                if (editable) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "保存",
                        color = Color(0xFF1B0B05),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Accent)
                            .clickable {
                                val err = Repo.saveCustomSource(source?.key, name, api)
                                if (err == null) onDismiss() else error = err
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceFieldLabel(text: String) {
    Text(text, color = TextDim, fontSize = 9.sp, letterSpacing = 0.5.sp)
    Spacer(Modifier.height(5.dp))
}

@Composable
private fun SourceTextField(value: String, onValueChange: (String) -> Unit, hint: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(1.dp, Stroke, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        if (value.isBlank()) {
            Text(hint, color = TextDim.copy(alpha = 0.62f), fontSize = 10.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = TextMain, fontSize = 11.sp),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SourceBadge(text: String) {
    Text(
        text,
        color = if (text == "自定义") AccentSoft else TextSub,
        fontSize = 8.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (text == "自定义") Accent.copy(alpha = 0.15f) else Panel)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun SourceStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Panel)
            .border(1.dp, Stroke.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = TextMain, fontFamily = Serif, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = TextDim, fontSize = 9.sp)
    }
}

private fun Modifier.clipCircleClickable(onClick: () -> Unit): Modifier =
    this.clip(androidx.compose.foundation.shape.CircleShape).clickable { onClick() }

private fun Modifier.clipCircle(custom: Boolean): Modifier =
    this.clip(androidx.compose.foundation.shape.CircleShape)
