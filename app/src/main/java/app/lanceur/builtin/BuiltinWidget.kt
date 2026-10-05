package app.lanceur.builtin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lanceur.builtin.battery.BatteryBarCard
import app.lanceur.builtin.battery.BatteryRingCard
import app.lanceur.builtin.battery.BatterySource
import app.lanceur.builtin.calendar.CalendarCardActions
import app.lanceur.builtin.calendar.CalendarLoad
import app.lanceur.builtin.calendar.CalendarRangeSource
import app.lanceur.builtin.calendar.MonthCard
import app.lanceur.builtin.calendar.WeekCard
import app.lanceur.builtin.calendar.WeekStrip
import app.lanceur.builtin.clocks.Cities
import app.lanceur.builtin.clocks.WorldClock
import app.lanceur.builtin.clocks.WorldClocksCard
import app.lanceur.builtin.clocks.WorldClocksConfig
import app.lanceur.builtin.countdown.CountdownCard
import app.lanceur.builtin.countdown.CountdownConfig
import app.lanceur.builtin.media.NowPlayingActions
import app.lanceur.builtin.media.NowPlayingCard
import app.lanceur.builtin.media.NowPlayingSource
import app.lanceur.builtin.media.NowPlayingState
import app.lanceur.builtin.note.NoteCard
import app.lanceur.builtin.note.NoteData
import app.lanceur.builtin.todo.TodoCard
import app.lanceur.builtin.todo.TodoList
import app.lanceur.widgets.WidgetLayout
import app.lanceur.widgets.WidgetSlot
import java.time.LocalDate
import java.time.YearMonth

/** Ce dont les widgets intégrés ont besoin. `refresh` change à chaque retour sur la page (nouveau jour, nouvelle autorisation). */
class BuiltinServices(
    val battery: BatterySource,
    val nowPlaying: NowPlayingSource,
    val calendar: CalendarRangeSource,
    val calendarActions: CalendarCardActions,
    val openBatterySettings: () -> Unit,
    val openPlayer: () -> Unit,
    val grantMediaAccess: () -> Unit,
    val data: (Int) -> String? = { null },
    val saveData: (Int, String) -> Unit = { _, _ -> },
    val openSettings: (WidgetSlot) -> Unit = {},
    val refresh: Int,
)

/** Les flux ne sont collectés que tant que Lanceur est au premier plan (`collectAsStateWithLifecycle`). */
@Composable
fun BuiltinWidget(slot: WidgetSlot, services: BuiltinServices, modifier: Modifier) {
    val kind = BuiltinSlots.kindOf(slot) ?: return
    val size = WidgetLayout.displaySize(slot)
    val today = remember(services.refresh) { LocalDate.now() }
    when (kind) {
        BuiltinKind.BATTERY_RING, BuiltinKind.BATTERY_BAR -> {
            val initial = remember { services.battery.read() }
            val info by services.battery.info.collectAsStateWithLifecycle(initial)
            if (kind == BuiltinKind.BATTERY_RING) BatteryRingCard(info, services.openBatterySettings, modifier)
            else BatteryBarCard(info, services.openBatterySettings, modifier)
        }
        BuiltinKind.NOW_PLAYING -> {
            val state by services.nowPlaying.state.collectAsStateWithLifecycle(NowPlayingState.Idle)
            NowPlayingCard(
                state,
                size,
                NowPlayingActions(
                    playPause = services.nowPlaying::playPause,
                    next = services.nowPlaying::next,
                    previous = services.nowPlaying::previous,
                    seekTo = services.nowPlaying::seekTo,
                    open = services.openPlayer,
                    grantAccess = services.grantMediaAccess,
                ),
                modifier,
            )
        }
        BuiltinKind.CALENDAR_MONTH -> {
            var month by remember { mutableStateOf(YearMonth.from(today)) }
            // Mois affiché ± 1 : les mois voisins ont déjà leurs pastilles pendant le glissement
            val load by produceState<CalendarLoad?>(null, month, services.refresh) {
                value = services.calendar.load(month.minusMonths(1).atDay(1), month.plusMonths(2).atDay(1))
            }
            MonthCard(today, size, load, onMonthShown = { month = it }, actions = services.calendarActions, modifier = modifier)
        }
        BuiltinKind.CALENDAR_WEEK -> {
            var weekStart by remember(today) { mutableStateOf(WeekStrip.weekStart(today)) }
            val load by produceState<CalendarLoad?>(null, weekStart, services.refresh) {
                value = services.calendar.load(weekStart, weekStart.plusDays(7))
            }
            WeekCard(today, weekStart, size, load, onWeekChange = { weekStart = it }, actions = services.calendarActions, modifier = modifier)
        }
        BuiltinKind.NOTE -> NoteCard(
            initial = NoteData.text(services.data(slot.appWidgetId)),
            onSave = { services.saveData(slot.appWidgetId, NoteData.of(it)) },
            modifier = modifier,
        )
        BuiltinKind.TODO -> {
            var list by remember(slot.appWidgetId) { mutableStateOf(TodoList.fromData(services.data(slot.appWidgetId))) }
            TodoCard(list, onChange = { list = it; services.saveData(slot.appWidgetId, it.toData()) }, modifier = modifier)
        }
        BuiltinKind.COUNTDOWN -> {
            val now = rememberMinuteClock()
            CountdownCard(
                config = CountdownConfig.fromData(services.data(slot.appWidgetId)),
                size = size,
                onSetUp = { services.openSettings(slot) },
                modifier = modifier,
                now = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()),
            )
        }
        BuiltinKind.WORLD_CLOCKS -> {
            val now = java.time.Instant.ofEpochMilli(rememberMinuteClock())
            val home = java.time.ZoneId.systemDefault()
            val cities = WorldClocksConfig.fromData(services.data(slot.appWidgetId)).cityIds.mapNotNull(Cities::byId)
                .ifEmpty { listOf(Cities.home(home, now)) }
            WorldClocksCard(cities.map { WorldClock.of(it, now, home) }, size, modifier)
        }
    }
}
