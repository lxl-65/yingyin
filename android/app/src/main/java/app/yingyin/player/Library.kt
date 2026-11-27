package app.yingyin.player

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** 只在手机本地保存：收藏、观看记录。不上传，不写账号信息。 */
data class WatchEntry(
    val item: Item,
    val lineName: String,
    val epIdx: Int,
    val epName: String,
    val at: Long,
    /** 最近一次播放位置/总时长，用于“继续观看”的真实进度和续播。 */
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

object Library {
    private var prefs: SharedPreferences? = null

    var favorites by mutableStateOf<List<Item>>(emptyList())
        private set

    var history by mutableStateOf<List<WatchEntry>>(emptyList())
        private set

    /** 与网站一致：私密分类默认关闭；关闭时首页/搜索/分类都不露出。 */
    var showPrivate by mutableStateOf(false)
        private set

    /** 关闭私密时，连历史/收藏也不展示；数据仍保留，重新打开开关可见。 */
    val visibleFavorites: List<Item>
        get() = if (showPrivate) favorites else favorites.filterNot { isPrivateItem(it) }

    val visibleHistory: List<WatchEntry>
        get() = if (showPrivate) history else history.filterNot { isPrivateItem(it.item) }

    fun init(ctx: Context) {
        if (prefs != null) return
        val sp = ctx.getSharedPreferences("yingyin_library", Context.MODE_PRIVATE)
        prefs = sp
        favorites = readItems(sp.getString("favorites", "[]"))
        history = readHistory(sp.getString("history", "[]"))
        showPrivate = sp.getBoolean("showPrivate", false)
    }

    fun isFavorite(item: Item): Boolean =
        favorites.any { norm(it.name) == norm(item.name) }

    fun toggleFavorite(item: Item) {
        favorites = if (isFavorite(item)) {
            favorites.filterNot { norm(it.name) == norm(item.name) }
        } else {
            listOf(item.copy()) + favorites.filterNot { norm(it.name) == norm(item.name) }
        }
        persist()
    }

    fun record(item: Item, lineName: String, epIdx: Int, epName: String) {
        val entry = WatchEntry(item, lineName, epIdx, epName, System.currentTimeMillis())
        history = (listOf(entry) + history.filterNot { norm(it.item.name) == norm(item.name) })
            .take(30)
        persist()
    }

    fun updateProgress(item: Item, positionMs: Long, durationMs: Long) {
        val idx = history.indexOfFirst { norm(it.item.name) == norm(item.name) }
        if (idx < 0) return
        val old = history[idx]
        history = history.toMutableList().also {
            it[idx] = old.copy(positionMs = positionMs.coerceAtLeast(0L), durationMs = durationMs.coerceAtLeast(0L))
        }
        persist()
    }

    fun clearHistory() {
        history = emptyList()
        persist()
    }

    fun updateShowPrivate(on: Boolean) {
        showPrivate = on
        persist()
    }

    private fun persist() {
        val sp = prefs ?: return
        sp.edit()
            .putString("favorites", itemsJson(favorites).toString())
            .putString("history", historyJson().toString())
            .putBoolean("showPrivate", showPrivate)
            .apply()
    }

    private fun readItems(raw: String?): List<Item> = try {
        val arr = JSONArray(raw ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { itemFromJson(it) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun readHistory(raw: String?): List<WatchEntry> = try {
        val arr = JSONArray(raw ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val j = arr.optJSONObject(i) ?: return@mapNotNull null
            val item = j.optJSONObject("item")?.let { itemFromJson(it) } ?: return@mapNotNull null
            WatchEntry(
                item = item,
                lineName = j.optString("line", ""),
                epIdx = j.optInt("epIdx", 0),
                epName = j.optString("epName", ""),
                at = j.optLong("at", 0L),
                positionMs = j.optLong("positionMs", 0L),
                durationMs = j.optLong("durationMs", 0L)
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun itemsJson(items: List<Item>): JSONArray {
        val arr = JSONArray()
        items.forEach { arr.put(itemJson(it)) }
        return arr
    }

    private fun historyJson(): JSONArray {
        val arr = JSONArray()
        history.forEach { entry ->
            arr.put(JSONObject().apply {
                put("item", itemJson(entry.item))
                put("line", entry.lineName)
                put("epIdx", entry.epIdx)
                put("epName", entry.epName)
                put("at", entry.at)
                put("positionMs", entry.positionMs)
                put("durationMs", entry.durationMs)
            })
        }
        return arr
    }

    private fun itemJson(item: Item): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("sid", item.sid)
        put("name", item.name)
        put("pic", item.pic)
        put("year", item.year)
        put("remarks", item.remarks)
        put("type", item.type)
        put("area", item.area)
        put("score", item.score)
        put("srcs", item.srcs)
        put("entries", JSONArray().apply {
            item.entries.forEach { e ->
                put(JSONObject().apply {
                    put("s", e.s)
                    put("id", e.id)
                    put("name", e.name)
                })
            }
        })
    }

    private fun itemFromJson(j: JSONObject): Item = Item(
        id = j.optString("id"),
        sid = j.optString("sid"),
        name = j.optString("name"),
        pic = j.optString("pic"),
        year = j.optString("year"),
        remarks = j.optString("remarks"),
        type = j.optString("type"),
        area = j.optString("area"),
        score = j.optDouble("score", 0.0),
        srcs = j.optInt("srcs", 1),
        entries = buildList {
            val arr = j.optJSONArray("entries") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                add(EntryRef(e.optInt("s"), e.optString("id"), e.optString("name")))
            }
        }
    )
}
