package app.yingyin.player

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration

sealed interface Route {
    data object Home : Route
    data object Search : Route
    data object Me : Route
    data class Detail(val item: Item) : Route
    data class Play(val item: Item, val title: String, val lines: List<Line>, val epIdx: Int) : Route
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        setContent { App() }
    }
}

@Composable
fun App() {
    YingyinTheme {
        val cfg = LocalConfiguration.current
        val isTv = (cfg.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
                Configuration.UI_MODE_TYPE_TELEVISION
        var route by remember { mutableStateOf<Route>(Route.Home) }

        BackHandler(enabled = route !is Route.Home) {
            route = when (val r = route) {
                is Route.Play -> Route.Detail(r.item)
                else -> Route.Home
            }
        }

        when (val r = route) {
            Route.Home -> HomeScreen(
                isTv = isTv,
                onSearch = { route = Route.Search },
                onMe = { route = Route.Me },
                onOpen = { route = Route.Detail(it) }
            )

            Route.Search -> SearchScreen(
                isTv = isTv,
                onBack = { route = Route.Home },
                onOpen = { route = Route.Detail(it) }
            )

            Route.Me -> MeScreen(onBack = { route = Route.Home })

            is Route.Detail -> DetailScreen(
                item = r.item,
                onBack = { route = Route.Home },
                onPlay = { title, lines, idx -> route = Route.Play(r.item, title, lines, idx) }
            )

            is Route.Play -> PlayerScreen(
                title = r.title,
                lines = r.lines,
                epIdx = r.epIdx,
                onBack = { route = Route.Detail(r.item) }
            )
        }
    }
}
