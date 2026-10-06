package app.lanceur.builtin

import app.lanceur.i18n.tr
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.lanceur.builtin.battery.BatteryBarCard
import app.lanceur.builtin.battery.BatteryInfo
import app.lanceur.builtin.battery.BatteryRingCard
import app.lanceur.builtin.battery.ChargeStatus
import app.lanceur.builtin.calendar.CalendarCardActions
import app.lanceur.builtin.calendar.CalendarLoad
import app.lanceur.builtin.calendar.MonthCard
import app.lanceur.builtin.calendar.WeekCard
import app.lanceur.builtin.calendar.WeekStrip
import app.lanceur.builtin.clocks.Cities
import app.lanceur.builtin.clocks.WorldClock
import app.lanceur.builtin.clocks.WorldClocksCard
import app.lanceur.builtin.contacts.FavoriteContact
import app.lanceur.builtin.contacts.FavoritesActions
import app.lanceur.builtin.contacts.FavoritesCard
import app.lanceur.builtin.contacts.FavoritesState
import app.lanceur.builtin.countdown.CountdownCard
import app.lanceur.builtin.countdown.CountdownConfig
import app.lanceur.builtin.media.MediaSnapshot
import app.lanceur.builtin.media.NowPlayingActions
import app.lanceur.builtin.media.NowPlayingCard
import app.lanceur.builtin.media.NowPlayingState
import app.lanceur.builtin.note.NoteCard
import app.lanceur.builtin.note.NoteData
import app.lanceur.builtin.rss.Article
import app.lanceur.builtin.rss.RssCard
import app.lanceur.builtin.rss.RssViewState
import app.lanceur.builtin.shortcuts.ShortcutActions
import app.lanceur.builtin.shortcuts.ShortcutsCard
import app.lanceur.builtin.shortcuts.TorchState
import app.lanceur.builtin.storage.Gauge
import app.lanceur.builtin.storage.StorageCard
import app.lanceur.builtin.storage.StorageReading
import app.lanceur.builtin.timer.StopwatchState
import app.lanceur.builtin.timer.TimerCard
import app.lanceur.builtin.todo.TodoCard
import app.lanceur.builtin.todo.TodoList
import app.lanceur.builtin.weather.Forecast
import app.lanceur.builtin.weather.HourForecast
import app.lanceur.builtin.weather.WeatherCard
import app.lanceur.builtin.weather.WeatherViewState
import app.lanceur.summary.SummaryEvent
import app.lanceur.ui.blockTouchesBelow
import java.time.LocalDate
import java.time.ZoneId

/** Largeur de référence d'une carte : la miniature est la vraie carte, réduite pour tenir dans l'aperçu. */
private val CARD_WIDTH = 360.dp

/** Miniature du sélecteur : le widget dessiné avec des données d'exemple, sans réaction au toucher. */
@Composable
fun BuiltinPreview(kind: BuiltinKind, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val height = kind.heightDp(kind.defaultSize).dp
        val scale = minOf(maxWidth / CARD_WIDTH, maxHeight / height)
        Box(
            Modifier
                .requiredSize(CARD_WIDTH, height)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(24.dp)),
        ) {
            SampleCard(kind, Modifier.fillMaxSize())
            Box(Modifier.matchParentSize().blockTouchesBelow())
        }
    }
}

@Composable
private fun SampleCard(kind: BuiltinKind, modifier: Modifier) {
    val today = remember { LocalDate.now() }
    val zone = ZoneId.systemDefault()
    val sampleEvents = remember(today) {
        fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        listOf(
            SummaryEvent(1, tr("Dentiste", "Dentist"), at(today, 14), at(today, 15), false, 0xFF4285F4.toInt()),
            SummaryEvent(2, "Sport", at(today.plusDays(2), 18), at(today.plusDays(2), 19), false, 0xFF0B8043.toInt()),
        )
    }
    val battery = BatteryInfo(78, ChargeStatus.CHARGING, 42 * 60_000L, 310, powerSave = false)
    when (kind) {
        BuiltinKind.BATTERY_RING -> BatteryRingCard(battery, onClick = {}, modifier = modifier)
        BuiltinKind.BATTERY_BAR -> BatteryBarCard(battery, onClick = {}, modifier = modifier)
        BuiltinKind.NOW_PLAYING -> NowPlayingCard(
            NowPlayingState.Active(
                MediaSnapshot(
                    "app.exemple", "Musique", "Bohemian Rhapsody", "Queen", null, durationMs = 355_000, positionMs = 102_000,
                    updatedAtElapsed = 0, speed = 0f, isPlaying = true, canPrevious = true, canNext = true, canSeek = true,
                ),
            ),
            kind.defaultSize,
            NowPlayingActions(),
            modifier,
            clock = { 0L },
        )
        BuiltinKind.CALENDAR_MONTH -> MonthCard(today, kind.defaultSize, CalendarLoad(true, sampleEvents), {}, CalendarCardActions(), modifier, zone)
        BuiltinKind.CALENDAR_WEEK ->
            WeekCard(today, WeekStrip.weekStart(today), kind.defaultSize, CalendarLoad(true, sampleEvents), {}, CalendarCardActions(), modifier, zone)
        BuiltinKind.NOTE -> NoteCard(tr("Pain, lait, œufs\nAppeler le garage", "Bread, milk, eggs\nCall the garage"), onSave = {}, modifier = modifier)
        BuiltinKind.TODO -> TodoCard(TodoList().add(tr("Pain", "Bread")).add(tr("Rendre le livre", "Return the book")).add(tr("Réserver le train", "Book the train")).toggle(2), onChange = {}, modifier = modifier)
        BuiltinKind.COUNTDOWN -> CountdownCard(
            CountdownConfig(tr("Vacances", "Holidays"), today.plusDays(12), null, 0),
            kind.defaultSize,
            onSetUp = {},
            modifier = modifier,
        )
        BuiltinKind.WORLD_CLOCKS -> {
            val now = java.time.Instant.now()
            WorldClocksCard(listOf("paris", "new-york", "tokyo", "sydney").mapNotNull(Cities::byId).map { WorldClock.of(it, now, zone) }, kind.defaultSize, modifier)
        }
        BuiltinKind.TIMER -> TimerCard(StopwatchState(), onStopwatch = {}, onTimer = { false }, onOtherTimer = {}, modifier = modifier)
        BuiltinKind.FAVORITE_CONTACTS -> FavoritesCard(
            FavoritesState.Loaded(listOf(tr("Maman", "Mom"), "Léa", "Hugo", "Inès").map { FavoriteContact(it, it, "0", null) }),
            kind.defaultSize,
            FavoritesActions(),
            modifier,
        )
        BuiltinKind.SHORTCUTS -> ShortcutsCard(TorchState(available = true, on = true), ShortcutActions(), modifier)
        BuiltinKind.STORAGE -> StorageCard(
            StorageReading(Gauge(87_000_000_000, 128_000_000_000), Gauge(7_100_000_000, 12_000_000_000)),
            onClick = {},
            modifier = modifier,
        )
        BuiltinKind.WEATHER -> WeatherCard(
            WeatherViewState.Ready(
                "Nantes",
                Forecast(16, 15, "⛅", tr("Éclaircies", "Partly cloudy"), 12, 20, 9, 17, (15..20).map { HourForecast(tr("$it h", "$it:00"), if (it < 18) "⛅" else "🌧", it) }, emptyList()),
                tr("Mis à jour à 14:05", "Updated at 14:05"),
            ),
            kind.defaultSize,
            onOpen = {},
            onChooseCity = {},
            modifier = modifier,
        )
        BuiltinKind.RSS -> {
            val now = System.currentTimeMillis()
            RssCard(
                RssViewState.Ready(
                    listOf(
                        Article(tr("Élections : les premiers résultats", "Elections: first results"), "a", "Le Monde", now - 25 * 60_000),
                        Article(tr("Une astuce Linux pour gagner du temps", "A Linux tip to save time"), "b", "Korben", now - 3 * 3_600_000),
                        Article(tr("Le budget 2027 présenté", "The 2027 budget unveiled"), "c", "France Info", now - 5 * 3_600_000),
                    ),
                    now,
                    failed = false,
                ),
                kind.defaultSize,
                now,
                onOpen = {},
                modifier = modifier,
            )
        }
    }
}
