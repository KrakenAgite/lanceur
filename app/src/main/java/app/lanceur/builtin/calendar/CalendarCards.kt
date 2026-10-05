package app.lanceur.builtin.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.summary.SummaryEvent
import app.lanceur.widgets.TimelineRow
import app.lanceur.widgets.WidgetSize
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.launch

class CalendarCardActions(
    val openEvent: (SummaryEvent) -> Unit = {},
    val openDay: (LocalDate) -> Unit = {},
    val requestCalendar: () -> Unit = {},
)

private const val MONTH_PAGES = 2401
private const val CENTER_PAGE = MONTH_PAGES / 2
private val CELL_HEIGHT = 34.dp
private val WEEKDAYS = listOf("L", "M", "M", "J", "V", "S", "D")

@Composable
private fun CardHeader(title: String, previous: String, next: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = previous) }
        Text(
            title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = next) }
    }
}

/**
 * Grille du mois. Glisser change de mois : le pager interne consomme le geste, la page de widgets ne bouge pas.
 * `onMonthShown` : mois arrêté, pour charger ses événements.
 */
@Composable
fun MonthCard(
    today: LocalDate,
    size: WidgetSize,
    load: CalendarLoad?,
    onMonthShown: (YearMonth) -> Unit,
    actions: CalendarCardActions,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = MaterialTheme.colorScheme
    val current = YearMonth.from(today)
    val pager = rememberPagerState(initialPage = CENTER_PAGE) { MONTH_PAGES }
    val scope = rememberCoroutineScope()
    val latestShown by rememberUpdatedState(onMonthShown)
    var selected by remember { mutableStateOf(today) }
    fun monthAt(page: Int): YearMonth = current.plusMonths((page - CENTER_PAGE).toLong())
    LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { latestShown(monthAt(it)) }
    }
    val events = load?.events.orEmpty()
    Column(modifier.fillMaxSize().background(colors.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 6.dp)) {
        CardHeader(
            title = MonthGrid.title(monthAt(pager.currentPage)),
            previous = "Mois précédent",
            next = "Mois suivant",
            onPrevious = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
            onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
        )
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        // Hauteur fixe de 6 semaines : la carte ne saute pas d'un mois à l'autre
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT * 6)) { page ->
            val grid = MonthGrid.build(monthAt(page), today, events, zone)
            Column {
                grid.weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth().height(CELL_HEIGHT)) {
                        week.forEach { day -> DayCell(day, selected = day.date == selected) { selected = day.date } }
                    }
                }
            }
        }
        if (load != null && !load.granted) {
            TextButton(onClick = actions.requestCalendar) { Text("Autoriser l'agenda") }
        } else if (size != WidgetSize.SMALL) {
            val lines = MonthGrid.dayLines(selected, events, zone).let { if (size == WidgetSize.MEDIUM) it.take(3) else it }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 8.dp, top = 6.dp, end = 8.dp)) {
                if (lines.isEmpty()) {
                    Text("Rien de prévu", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                lines.forEachIndexed { index, line ->
                    TimelineRow(line, isLast = index == lines.lastIndex) { actions.openEvent(line.event) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(day: GridDay, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("day-${day.date}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (day.isToday) colors.primary else Color.Transparent)
                .then(if (selected && !day.isToday) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    day.isToday -> colors.onPrimary
                    day.inMonth -> colors.onSurface
                    else -> colors.onSurfaceVariant.copy(alpha = 0.45f)
                },
            )
        }
        Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (day.birthday) Text("🎁", style = MaterialTheme.typography.labelSmall)
            day.colors.forEach { color ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(color?.let { Color(it) } ?: colors.primary))
            }
        }
    }
}

/** La semaine en 7 colonnes ; ‹ › change de semaine, toucher un jour ouvre l'agenda à cette date. */
@Composable
fun WeekCard(
    today: LocalDate,
    weekStart: LocalDate,
    size: WidgetSize,
    load: CalendarLoad?,
    onWeekChange: (LocalDate) -> Unit,
    actions: CalendarCardActions,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = MaterialTheme.colorScheme
    val strip = WeekStrip.build(WeekStrip.weekStart(weekStart), today, load?.events.orEmpty(), zone, WeekStrip.perDay(size))
    Column(modifier.fillMaxSize().background(colors.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 6.dp)) {
        CardHeader(
            title = strip.title,
            previous = "Semaine précédente",
            next = "Semaine suivante",
            onPrevious = { onWeekChange(WeekStrip.weekStart(weekStart).minusWeeks(1)) },
            onNext = { onWeekChange(WeekStrip.weekStart(weekStart).plusWeeks(1)) },
        )
        if (load != null && !load.granted) {
            TextButton(onClick = actions.requestCalendar) { Text("Autoriser l'agenda") }
            return@Column
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            strip.days.forEach { day ->
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (day.isToday) colors.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
                        .clickable { actions.openDay(day.date) }
                        .testTag("week-${day.date}")
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(day.label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Text(
                        day.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (day.isToday) colors.primary else colors.onSurface,
                    )
                    if (day.birthdays.isNotEmpty()) Text("🎁", style = MaterialTheme.typography.labelSmall)
                    day.items.forEach { item ->
                        Text(
                            item.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background((item.color?.let { Color(it) } ?: colors.primary).copy(alpha = 0.85f))
                                .padding(horizontal = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    if (day.more > 0) Text("+${day.more}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
