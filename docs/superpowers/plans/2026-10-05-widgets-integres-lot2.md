# Widgets intégrés, lot 2 — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter huit widgets intégrés locaux (Note, To-do, Compte à rebours, Horloges du monde, Minuteur / chrono, Contacts favoris, Raccourcis, Stockage et mémoire), avec des données par widget et une feuille de réglages.

**Architecture:** Les données d'un widget sont un texte `clé=valeur` (`WidgetData`) stocké sous la clé DataStore `widget_data_<id>`, et retiré en même temps que le widget. Chaque widget a son modèle pur (`toData` / `fromData`) testé sur la JVM, une source Android fine quand il en faut une (contacts, lampe, stockage) et une carte Compose sans état métier. Une feuille Material 3 règle les deux widgets configurables : elle s'ouvre à l'ajout, puis par ⚙ en mode édition. Chaque tâche de widget ajoute son entrée `BuiltinKind`, sa carte, sa branche dans `BuiltinWidget` et sa miniature dans `BuiltinPreview`.

**Tech Stack:** Kotlin 2.4.20, Compose BOM 2026.09.00, Material 3 (`ModalBottomSheet`, `DatePicker`, `TimeInput`), DataStore 1.2.1, `ContactsContract`, `CameraManager`, `StorageStatsManager`, `AlarmClock`. Tests : JUnit 4, coroutines-test, Compose UI test v2.

**Spec:** `docs/superpowers/specs/2026-10-05-widgets-integres-lot2-design.md`

## Global Constraints

- minSdk 35, compile/targetSdk 37, namespace `app.lanceur` ; aucune permission nouvelle ; aucune permission Internet.
- Les noms des entrées de `BuiltinKind` sont enregistrés : ne jamais les renommer. Les nouvelles entrées s'ajoutent **à la fin** de l'enum.
- Hauteurs (dp) : NOTE 140/220/340 (M) ; TODO 160/260/380 (M) ; COUNTDOWN 120/200 (S, configurable) ; WORLD_CLOCKS 120/200 (M, configurable) ; TIMER M 200 seul ; FAVORITE_CONTACTS 120/220 (S) ; SHORTCUTS S 120 seul ; STORAGE S 140 seul.
- Clé de données : `widget_data_<id>` ; retirer un widget retire ses données ; des données illisibles donnent un widget vide, sans plantage.
- Style : `cardBackground()`, `CardShape`, `CardLabel` (`app.lanceur.ui`), comme le résumé du jour.
- Textes en français. Orange d'alerte : `0xFFF9AB00`.
- Commandes : `JAVA_HOME=/opt/android-studio/jbr ./gradlew …` ; adb `~/Android/Sdk/platform-tools/adb`.
- Tests JVM : `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`.
- Tests d'interface d'une classe : `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<classe>` (téléphone déverrouillé).
- Ne jamais afficher ni committer `keystore.properties` ou `*.jks` ; mettre à jour le téléphone avec `adb install -r`.

## Review Focus

1. **Écriture concurrente des données.** Une note tapée vite et un autre widget modifié en même temps ne doivent rien perdre. `update` de `PrefsRepo` est transactionnel. La note écrit seulement 500 ms après la dernière frappe, puis la valeur finale. À vérifier à la lecture du code ; le test JVM de `PrefsRepo` couvre l'écriture par identifiant.
2. **Annuler la feuille à l'ajout.** Rien ne doit être ajouté. L'ajout n'a lieu que dans `onSave` (Tâche 4) ; à vérifier à la main.
3. **Chrono et horloge du téléphone.** Le chrono utilise l'horloge murale pour survivre au redémarrage. Si l'utilisateur recule l'heure, `elapsed` ne doit pas devenir négatif : `coerceAtLeast(0)` et le test `StopwatchTest.clock_going_back_never_gives_negative_time` le couvrent (Tâche 6).
4. **Glissements dans la page de widgets.** Supprimer une tâche par un glissement entrerait en conflit avec le pager de la page. C'est pourquoi la spec a été réglée sur un bouton ✕ (Tâche 3, ruling).
5. **Contacts autorisés après coup.** Après le dialogue d'autorisation, le widget doit se remplir sans redémarrage. Le flux est recréé à chaque `refresh`, qui augmente au retour au premier plan (Tâche 7). À vérifier à la main.

---

### Task 1: Données par widget

**Files:**
- Modify: `app/src/main/java/app/lanceur/builtin/BuiltinKind.kt` (paramètre `configurable`)
- Create: `app/src/main/java/app/lanceur/builtin/WidgetData.kt`
- Modify: `app/src/main/java/app/lanceur/prefs/LauncherPrefs.kt`, `app/src/main/java/app/lanceur/prefs/PrefsRepo.kt`, `app/src/main/java/app/lanceur/home/LauncherViewModel.kt`
- Modify: `app/src/main/java/app/lanceur/builtin/BuiltinWidget.kt`, `app/src/main/java/app/lanceur/AppRoot.kt` (`data` / `saveData`)
- Test: `app/src/test/java/app/lanceur/builtin/WidgetDataTest.kt`, `app/src/test/java/app/lanceur/prefs/PrefsRepoTest.kt`, `app/src/test/java/app/lanceur/widgets/PickerCatalogTest.kt`

**Interfaces:**
- Produces: `BuiltinKind.configurable: Boolean` ; `WidgetData.encode(Map<String,String>)`, `decode(String?)`, `list(List<String>)`, `unlist(String?)` ; `LauncherPrefs.widgetData: Map<Int, String>` ; `PrefsRepo.setWidgetData(id, data)`, `PrefsRepo.addBuiltinWidget(kind, data: String? = null)` ; `LauncherViewModel.setWidgetData(id, data)`, `addBuiltinWidget(kind, data: String? = null)` ; `BuiltinServices.data: (Int) -> String?`, `BuiltinServices.saveData: (Int, String) -> Unit`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/app/lanceur/builtin/WidgetDataTest.kt` :

```kotlin
package app.lanceur.builtin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataTest {
    @Test
    fun round_trip_keeps_special_characters() {
        val values = mapOf("text" to "a=b\nc\\d\\n", "vide" to "", "clé=bizarre" to "ok")
        assertEquals(values, WidgetData.decode(WidgetData.encode(values)))
    }

    @Test
    fun unreadable_lines_are_ignored() {
        assertEquals(mapOf("ok" to "1"), WidgetData.decode("ok=1\nsans séparateur"))
        assertTrue(WidgetData.decode(null).isEmpty())
        assertTrue(WidgetData.decode("").isEmpty())
    }

    @Test
    fun lists_round_trip() {
        val items = listOf("pain", "lait=2", "a\nb")
        assertEquals(items, WidgetData.unlist(WidgetData.decode(WidgetData.encode(mapOf("l" to WidgetData.list(items))))["l"]))
        assertTrue(WidgetData.unlist(null).isEmpty())
        assertTrue(WidgetData.unlist("").isEmpty())
    }
}
```

Ajouter à `PrefsRepoTest` :

```kotlin
    @Test
    fun widget_data_is_kept_per_widget_and_removed_with_it() = runTest {
        val repo = PrefsRepo(store())
        repo.addBuiltinWidget(app.lanceur.builtin.BuiltinKind.BATTERY_BAR, data = "a=1")
        repo.addBuiltinWidget(app.lanceur.builtin.BuiltinKind.BATTERY_BAR)
        repo.setWidgetData(-2, "b=2")
        assertEquals(mapOf(-1 to "a=1", -2 to "b=2"), repo.prefs.first().widgetData)
        repo.removeWidget(-1)
        assertEquals(mapOf(-2 to "b=2"), repo.prefs.first().widgetData)
        assertEquals(listOf(-2), repo.prefs.first().widgets.map { it.appWidgetId })
    }
```

Dans `PickerCatalogTest.lanceur_group_comes_first_in_enum_order`, remplacer la liste attendue des libellés par
`app.lanceur.builtin.BuiltinKind.entries.map { it.label }` (le groupe grandit à chaque tâche de ce plan).

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*WidgetDataTest*' --tests '*PrefsRepoTest*' --tests '*PickerCatalogTest*'`
Expected: FAIL at compilation: "Unresolved reference 'WidgetData'", "No parameter with name 'data'", "'setWidgetData'".

- [ ] **Step 3: Write `WidgetData.kt`**

```kotlin
package app.lanceur.builtin

/**
 * Données d'un widget intégré : une paire `clé=valeur` par ligne. `\`, `=` et le retour à la ligne sont échappés,
 * donc le premier `=` d'une ligne sépare toujours la clé de la valeur. Une ligne sans `=` est ignorée.
 */
object WidgetData {
    /** Séparateur d'éléments dans une valeur-liste : caractère de contrôle qu'on ne tape jamais. */
    private const val UNIT = '\u001F'

    fun encode(values: Map<String, String>): String =
        values.entries.joinToString("\n") { (key, value) -> escape(key) + "=" + escape(value) }

    fun decode(text: String?): Map<String, String> {
        if (text.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        text.split('\n').forEach { line ->
            val separator = line.indexOf('=')
            if (separator >= 0) out[unescape(line.substring(0, separator))] = unescape(line.substring(separator + 1))
        }
        return out
    }

    fun list(values: List<String>): String = values.joinToString(UNIT.toString())

    fun unlist(value: String?): List<String> = if (value.isNullOrEmpty()) emptyList() else value.split(UNIT)

    private fun escape(text: String): String = buildString {
        text.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '=' -> append("\\e")
                '\n' -> append("\\n")
                else -> append(c)
            }
        }
    }

    private fun unescape(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (val next = text[i + 1]) {
                    'n' -> append('\n')
                    'e' -> append('=')
                    else -> append(next)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
```

- [ ] **Step 4: Storage, ViewModel, `configurable`**

`LauncherPrefs.kt` : ajouter à la fin du constructeur

```kotlin
    /** Données des widgets intégrés (note, tâches, réglages…), par identifiant. */
    val widgetData: Map<Int, String> = emptyMap(),
```

`PrefsRepo.kt` :
- remplacer `addBuiltinWidget` par

```kotlin
    /** L'identifiant est choisi dans la même transaction que l'ajout : deux ajouts rapides n'ont jamais le même. */
    suspend fun addBuiltinWidget(kind: BuiltinKind, data: String? = null) = update { prefs ->
        val slot = BuiltinSlots.create(kind, prefs.widgets)
        prefs.copy(
            widgets = prefs.widgets + slot,
            widgetData = if (data == null) prefs.widgetData else prefs.widgetData + (slot.appWidgetId to data),
        )
    }

    suspend fun setWidgetData(appWidgetId: Int, data: String) = update { it.copy(widgetData = it.widgetData + (appWidgetId to data)) }
```

- dans `removeWidget`, remplacer le corps par
  `prefs.copy(widgets = prefs.widgets.filterNot { it.appWidgetId == appWidgetId }, widgetData = prefs.widgetData - appWidgetId)` ;
- dans le `companion object`, ajouter `const val DATA_PREFIX = "widget_data_"` ; dans `decode`, ajouter l'argument

```kotlin
            widgetData = stored.asMap().mapNotNull { (key, value) ->
                val id = key.name.takeIf { it.startsWith(DATA_PREFIX) }?.removePrefix(DATA_PREFIX)?.toIntOrNull()
                if (id != null && value is String) id to value else null
            }.toMap(),
```

- dans `encode`, à la fin :

```kotlin
            // Les données d'un widget retiré disparaissent avec lui
            out.asMap().keys.filter { it.name.startsWith(DATA_PREFIX) }.toList().forEach { out.remove(it) }
            prefs.widgetData.forEach { (id, text) -> out[stringPreferencesKey(DATA_PREFIX + id)] = text }
```

`LauncherViewModel.kt` : remplacer `addBuiltinWidget` et ajouter `setWidgetData` :

```kotlin
    fun addBuiltinWidget(kind: BuiltinKind, data: String? = null) {
        viewModelScope.launch { prefsRepo.addBuiltinWidget(kind, data) }
    }

    fun setWidgetData(appWidgetId: Int, data: String) {
        viewModelScope.launch { prefsRepo.setWidgetData(appWidgetId, data) }
    }
```

`BuiltinKind.kt` : ajouter le paramètre de constructeur `val configurable: Boolean = false,` après `defaultSize`, avec le
commentaire « Réglé par une feuille, à l'ajout et par ⚙ en mode édition. ».

`BuiltinWidget.kt` : ajouter à `BuiltinServices`, avant `refresh` :

```kotlin
    val data: (Int) -> String? = { null },
    val saveData: (Int, String) -> Unit = { _, _ -> },
```

`AppRoot.kt`, dans la construction de `BuiltinServices` : ajouter
`data = { prefs.widgetData[it] },` et `saveData = vm::setWidgetData,`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`
Expected: PASS, toute la suite JVM.

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "Widgets intégrés : données par widget, retirées avec lui"
```

---

### Task 2: Note rapide

**Files:**
- Modify: `BuiltinKind.kt` (entrée `NOTE`), `BuiltinWidget.kt`, `BuiltinPreview.kt`, `app/src/main/java/app/lanceur/widgets/WidgetPage.kt` (`imePadding`)
- Create: `app/src/main/java/app/lanceur/builtin/note/NoteData.kt`, `app/src/main/java/app/lanceur/builtin/note/NoteCard.kt`
- Test: `app/src/test/java/app/lanceur/builtin/note/NoteDataTest.kt`, `app/src/androidTest/java/app/lanceur/builtin/note/NoteCardTest.kt`

**Interfaces:**
- Consumes: `WidgetData`, `BuiltinServices.data/saveData` (Tâche 1).
- Produces: `NoteData.text(data: String?): String`, `NoteData.of(text): String` ; `@Composable NoteCard(initial: String, onSave: (String) -> Unit, modifier, saveDelayMs: Long = 500)`.

- [ ] **Step 1: Write the failing tests**

`NoteDataTest.kt` :

```kotlin
package app.lanceur.builtin.note

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteDataTest {
    @Test
    fun text_round_trip_and_empty_by_default() {
        val text = "Courses :\n- pain = 2\n- lait"
        assertEquals(text, NoteData.text(NoteData.of(text)))
        assertEquals("", NoteData.text(null))
        assertEquals("", NoteData.text("n'importe quoi"))
    }
}
```

`NoteCardTest.kt` :

```kotlin
package app.lanceur.builtin.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class NoteCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun typing_saves_the_text() {
        var saved = ""
        rule.setContent { MaterialTheme { Box(Modifier.height(220.dp)) { NoteCard("", onSave = { saved = it }, saveDelayMs = 0) } } }
        rule.onNodeWithText("Touche pour écrire…").assertIsDisplayed()
        rule.onNodeWithTag("note-field").performTextInput("Acheter du pain")
        rule.waitUntil(2_000) { saved == "Acheter du pain" }
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*NoteDataTest*'`
Expected: FAIL at compilation, "Unresolved reference 'NoteData'".

- [ ] **Step 3: Write `NoteData.kt` and the `NOTE` kind**

```kotlin
package app.lanceur.builtin.note

import app.lanceur.builtin.WidgetData

object NoteData {
    fun text(data: String?): String = WidgetData.decode(data)["text"].orEmpty()

    fun of(text: String): String = WidgetData.encode(mapOf("text" to text))
}
```

Dans `BuiltinKind`, après `BATTERY_BAR(...)`, ajouter :

```kotlin
    NOTE("Note rapide", "📝", mapOf(SMALL to 140, MEDIUM to 220, LARGE to 340), MEDIUM),
```

- [ ] **Step 4: Run the JVM test to verify it passes**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*NoteDataTest*'`
Expected: FAIL at compilation of `BuiltinWidget.kt` / `BuiltinPreview.kt` ("'when' expression must be exhaustive"). Faire l'étape 5 puis relancer : PASS.

- [ ] **Step 5: Write `NoteCard.kt`, wire it**

```kotlin
package app.lanceur.builtin.note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import kotlinx.coroutines.delay

/** Note modifiable sur place ; enregistrée `saveDelayMs` après la dernière frappe. */
@Composable
fun NoteCard(initial: String, onSave: (String) -> Unit, modifier: Modifier = Modifier, saveDelayMs: Long = 500) {
    var text by remember { mutableStateOf(initial) }
    val latestSave by rememberUpdatedState(onSave)
    LaunchedEffect(text) {
        if (text != initial) {
            delay(saveDelayMs)
            latestSave(text)
        }
    }
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 22.dp, vertical = 18.dp)) {
        CardLabel("Note")
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxSize().testTag("note-field"),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
            )
            if (text.isEmpty()) {
                Text("Touche pour écrire…", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
        }
    }
}
```

Dans `BuiltinWidget`, ajouter au `when (kind)` :

```kotlin
        BuiltinKind.NOTE -> NoteCard(
            initial = NoteData.text(services.data(slot.appWidgetId)),
            onSave = { services.saveData(slot.appWidgetId, NoteData.of(it)) },
            modifier = modifier,
        )
```

Dans `BuiltinPreview.SampleCard`, ajouter :

```kotlin
        BuiltinKind.NOTE -> NoteCard("Pain, lait, œufs\nAppeler le garage", onSave = {}, modifier = modifier)
```

(imports `app.lanceur.builtin.note.NoteCard` et `NoteData`). Dans `WidgetPage.kt`, ajouter `.imePadding()` juste après
`.systemBarsPadding()` sur la colonne de la page (import `androidx.compose.foundation.layout.imePadding`) : le champ reste
visible au-dessus du clavier.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest` puis
`JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.builtin.note.NoteCardTest`
Expected: PASS (suite JVM ; 1 test d'interface).

- [ ] **Step 7: Commit**

```bash
git add app/src
git commit -m "Widget Note rapide"
```

---

### Task 3: To-do

**Files:**
- Modify: `BuiltinKind.kt` (`TODO`), `BuiltinWidget.kt`, `BuiltinPreview.kt`
- Create: `app/src/main/java/app/lanceur/builtin/todo/TodoList.kt`, `app/src/main/java/app/lanceur/builtin/todo/TodoCard.kt`
- Test: `app/src/test/java/app/lanceur/builtin/todo/TodoListTest.kt`, `app/src/androidTest/java/app/lanceur/builtin/todo/TodoCardTest.kt`

**Ruling de conception :** la spec prévoyait « glisser pour supprimer ». Un glissement horizontal sur une carte entrerait en conflit avec le pager de la page de widgets (voir l'agenda du mois). Chaque tâche a donc un bouton ✕ « Supprimer ».

**Interfaces:**
- Produces: `data class TodoItem(id: Long, text: String, done: Boolean)` ; `data class TodoList(items)` avec `add`, `toggle`, `remove`, `clearDone`, `visible`, `hasDone`, `toData()`, `TodoList.fromData(String?)` ; `@Composable TodoCard(list, onChange: (TodoList) -> Unit, modifier)`.

- [ ] **Step 1: Write the failing tests**

`TodoListTest.kt` :

```kotlin
package app.lanceur.builtin.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoListTest {
    @Test
    fun add_ignores_blank_text_and_numbers_items() {
        val list = TodoList().add("  pain ").add("   ").add("lait")
        assertEquals(listOf(TodoItem(1, "pain", false), TodoItem(2, "lait", false)), list.items)
    }

    @Test
    fun done_items_move_to_the_end_and_can_be_cleared() {
        val list = TodoList().add("a").add("b").add("c").toggle(1)
        assertEquals(listOf("b", "c", "a"), list.visible.map { it.text })
        assertTrue(list.hasDone)
        assertEquals(listOf("b", "c"), list.clearDone().items.map { it.text })
        assertFalse(list.clearDone().hasDone)
        assertEquals(listOf("a", "c"), list.remove(2).items.map { it.text })
    }

    @Test
    fun round_trip_and_garbage() {
        val list = TodoList().add("a|b=c").add("d").toggle(2)
        assertEquals(list, TodoList.fromData(list.toData()))
        assertEquals(TodoList(), TodoList.fromData(null))
        assertEquals(TodoList(), TodoList.fromData("items=pas|un|nombre"))
        assertEquals(3L, list.add("e").items.last().id)
    }
}
```

`TodoCardTest.kt` :

```kotlin
package app.lanceur.builtin.todo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodoCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun add_check_and_clear() {
        var list by mutableStateOf(TodoList())
        rule.setContent { MaterialTheme { Box(Modifier.height(260.dp)) { TodoCard(list, onChange = { list = it }) } } }
        rule.onNodeWithTag("todo-new").performTextInput("Pain")
        rule.onNodeWithTag("todo-new").performImeAction()
        assertEquals(listOf("Pain"), list.items.map { it.text })
        rule.onNodeWithText("Pain").performClick()
        assertTrue(list.items.single().done)
        rule.onNodeWithText("Effacer les tâches faites").performClick()
        assertTrue(list.items.isEmpty())
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*TodoListTest*'`
Expected: FAIL at compilation, "Unresolved reference 'TodoList'".

- [ ] **Step 3: Write `TodoList.kt`, kind, card, wiring**

```kotlin
package app.lanceur.builtin.todo

import app.lanceur.builtin.WidgetData

data class TodoItem(val id: Long, val text: String, val done: Boolean)

data class TodoList(val items: List<TodoItem> = emptyList()) {
    /** À faire dans l'ordre d'ajout, puis les tâches faites. */
    val visible: List<TodoItem> get() = items.filterNot { it.done } + items.filter { it.done }

    val hasDone: Boolean get() = items.any { it.done }

    fun add(text: String): TodoList {
        val clean = text.trim()
        if (clean.isEmpty()) return this
        return copy(items = items + TodoItem((items.maxOfOrNull { it.id } ?: 0) + 1, clean, done = false))
    }

    fun toggle(id: Long) = copy(items = items.map { if (it.id == id) it.copy(done = !it.done) else it })

    fun remove(id: Long) = copy(items = items.filterNot { it.id == id })

    fun clearDone() = copy(items = items.filterNot { it.done })

    /** Chaque tâche : `id|0 ou 1|texte` (le texte peut contenir `|`). */
    fun toData(): String =
        WidgetData.encode(mapOf("items" to WidgetData.list(items.map { "${it.id}|${if (it.done) 1 else 0}|${it.text}" })))

    companion object {
        fun fromData(data: String?): TodoList = TodoList(
            WidgetData.unlist(WidgetData.decode(data)["items"]).mapNotNull { entry ->
                val parts = entry.split('|', limit = 3)
                val id = parts.getOrNull(0)?.toLongOrNull()
                if (parts.size == 3 && id != null) TodoItem(id, parts[2], parts[1] == "1") else null
            },
        )
    }
}
```

Dans `BuiltinKind`, après `NOTE(...)` :

```kotlin
    TODO("To-do", "✅", mapOf(SMALL to 160, MEDIUM to 260, LARGE to 380), MEDIUM),
```

`TodoCard.kt` :

```kotlin
package app.lanceur.builtin.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground

@Composable
fun TodoCard(list: TodoList, onChange: (TodoList) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().background(cardBackground()).padding(start = 22.dp, end = 10.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel("À faire", Modifier.weight(1f))
            if (list.hasDone) {
                TextButton(onClick = { onChange(list.clearDone()) }) {
                    Text("Effacer les tâches faites", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            list.visible.forEach { item ->
                key(item.id) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(list.toggle(item.id)) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = item.done, onCheckedChange = { onChange(list.toggle(item.id)) })
                        Text(
                            item.text,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (item.done) colors.onSurfaceVariant else colors.onSurface,
                            textDecoration = if (item.done) TextDecoration.LineThrough else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { onChange(list.remove(item.id)) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Supprimer ${item.text}", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth().testTag("todo-new"),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onChange(list.add(draft))
                            draft = ""
                        }),
                    )
                    if (draft.isEmpty()) Text("Ajouter une tâche…", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
```

Dans `BuiltinWidget`, au `when (kind)` :

```kotlin
        BuiltinKind.TODO -> {
            var list by remember(slot.appWidgetId) { mutableStateOf(TodoList.fromData(services.data(slot.appWidgetId))) }
            TodoCard(list, onChange = { list = it; services.saveData(slot.appWidgetId, it.toData()) }, modifier = modifier)
        }
```

Dans `BuiltinPreview.SampleCard` :

```kotlin
        BuiltinKind.TODO -> TodoCard(TodoList().add("Pain").add("Rendre le livre").add("Réserver le train").toggle(2), onChange = {}, modifier = modifier)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest` puis la classe `app.lanceur.builtin.todo.TodoCardTest` sur le téléphone.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Widget To-do"
```

---

### Task 4: Compte à rebours et feuille de réglages

**Files:**
- Modify: `BuiltinKind.kt` (`COUNTDOWN`, configurable), `BuiltinWidget.kt` (`openSettings`), `BuiltinPreview.kt`, `AppRoot.kt`
- Create: `app/src/main/java/app/lanceur/builtin/Ticks.kt`, `app/src/main/java/app/lanceur/builtin/BuiltinSettings.kt`
- Create: `app/src/main/java/app/lanceur/builtin/countdown/Countdown.kt`, `CountdownCard.kt`, `CountdownSettings.kt`
- Test: `app/src/test/java/app/lanceur/builtin/countdown/CountdownTest.kt`, `app/src/androidTest/java/app/lanceur/builtin/countdown/CountdownCardsTest.kt`

**Interfaces:**
- Consumes: `WidgetData`, `PrefsRepo`/VM (Tâche 1).
- Produces: `CountdownConfig(title, date: LocalDate, time: LocalTime?, createdAt: Long)` + `toData`/`fromData` ; `Countdown.of(config, now: ZonedDateTime): CountdownView(big, dateText, progress)` ; `@Composable rememberMinuteClock(): Long` ; `@Composable BuiltinSettingsSheet(kind, initial: String?, onSave: (String) -> Unit, onDismiss)` ; `@Composable CountdownSettings(initial, onSave, now)` ; `@Composable CountdownCard(config, size, onSetUp, modifier, now)` ; `BuiltinServices.openSettings: (WidgetSlot) -> Unit`.

- [ ] **Step 1: Write the failing tests**

`CountdownTest.kt` :

```kotlin
package app.lanceur.builtin.countdown

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val now = LocalDateTime.of(2026, 10, 5, 10, 0).atZone(paris)
    private fun config(day: Int, time: LocalTime? = null, month: Int = 10) =
        CountdownConfig("Vacances", LocalDate.of(2026, month, day), time, createdAt = 0)

    @Test
    fun days_left_today_and_past() {
        assertEquals("J-12", Countdown.of(config(17), now).big)
        assertEquals("12 j 4 h", Countdown.of(config(17, LocalTime.of(14, 0)), now).big)
        assertEquals("4 h 30 min", Countdown.of(config(5, LocalTime.of(14, 30)), now).big)
        assertEquals("C'est aujourd'hui 🎉", Countdown.of(config(5), now).big)
        assertEquals("C'est aujourd'hui 🎉", Countdown.of(config(5, LocalTime.of(8, 0)), now).big)
        assertEquals("Il y a 1 jour", Countdown.of(config(4), now).big)
        assertEquals("Il y a 3 jours", Countdown.of(config(2), now).big)
    }

    @Test
    fun date_text_and_progress() {
        assertEquals("Samedi 17 octobre 2026", Countdown.of(config(17), now).dateText)
        assertEquals("Samedi 17 octobre 2026 à 14:00", Countdown.of(config(17, LocalTime.of(14, 0)), now).dateText)
        val created = LocalDateTime.of(2026, 10, 1, 0, 0).atZone(paris).toInstant().toEpochMilli()
        val progress = Countdown.of(config(9).copy(createdAt = created), now).progress
        assertEquals(0.552f, progress, 0.01f)
        assertEquals(1f, Countdown.of(config(2).copy(createdAt = created), now).progress, 0f)
    }

    @Test
    fun data_round_trip_and_garbage() {
        val c = config(17, LocalTime.of(14, 0)).copy(createdAt = 42)
        assertEquals(c, CountdownConfig.fromData(c.toData()))
        assertEquals(config(17), CountdownConfig.fromData(config(17).toData()))
        assertNull(CountdownConfig.fromData(null))
        assertNull(CountdownConfig.fromData("date=pas une date"))
    }
}
```

`CountdownCardsTest.kt` :

```kotlin
package app.lanceur.builtin.countdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.lanceur.widgets.WidgetSize
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CountdownCardsTest {
    @get:Rule val rule = createComposeRule()
    private val now = LocalDateTime.of(2026, 10, 5, 10, 0).atZone(ZoneId.of("Europe/Paris"))

    @Test
    fun card_shows_days_left_and_title() {
        val config = CountdownConfig("Vacances", LocalDate.of(2026, 10, 17), null, 0)
        rule.setContent { MaterialTheme { Box(Modifier.height(200.dp)) { CountdownCard(config, WidgetSize.MEDIUM, onSetUp = {}, now = now) } } }
        rule.onNodeWithText("J-12").assertIsDisplayed()
        rule.onNodeWithText("VACANCES").assertIsDisplayed()
    }

    @Test
    fun unset_card_asks_to_set_up() {
        var asked = false
        rule.setContent { MaterialTheme { Box(Modifier.height(120.dp)) { CountdownCard(null, WidgetSize.SMALL, onSetUp = { asked = true }, now = now) } } }
        rule.onNodeWithText("Régler le compte à rebours").performClick()
        assertTrue(asked)
    }

    @Test
    fun settings_save_the_title_and_default_date() {
        var saved: String? = null
        rule.setContent { MaterialTheme { CountdownSettings(initial = null, onSave = { saved = it }, now = { 7L }) } }
        rule.onNodeWithTag("countdown-title").performTextInput("Vacances")
        rule.onNodeWithText("Enregistrer").performClick()
        val config = CountdownConfig.fromData(saved)!!
        assertEquals("Vacances", config.title)
        assertEquals(LocalDate.now().plusDays(7), config.date)
        assertEquals(7L, config.createdAt)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*CountdownTest*'`
Expected: FAIL at compilation, "Unresolved reference 'CountdownConfig'".

- [ ] **Step 3: Write `Countdown.kt`**

```kotlin
package app.lanceur.builtin.countdown

import app.lanceur.builtin.WidgetData
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** `createdAt` : moment du réglage (ms), départ de la barre de progression. */
data class CountdownConfig(val title: String, val date: LocalDate, val time: LocalTime?, val createdAt: Long) {
    fun toData(): String = WidgetData.encode(
        buildMap {
            put("title", title)
            put("date", date.toString())
            time?.let { put("time", it.toString()) }
            put("created", createdAt.toString())
        },
    )

    companion object {
        /** `null` sans date lisible : le widget propose alors de le régler. */
        fun fromData(data: String?): CountdownConfig? {
            val values = WidgetData.decode(data)
            val date = values["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            return CountdownConfig(
                title = values["title"].orEmpty(),
                date = date,
                time = values["time"]?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                createdAt = values["created"]?.toLongOrNull() ?: 0L,
            )
        }
    }
}

data class CountdownView(val big: String, val dateText: String, val progress: Float)

object Countdown {
    private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
    private val DATE_TIME = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm", Locale.FRENCH)

    fun of(config: CountdownConfig, now: ZonedDateTime): CountdownView {
        val target = config.time?.let { config.date.atTime(it).atZone(now.zone) }
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), config.date)
        val big = when {
            target != null && now.isBefore(target) -> {
                val minutes = Duration.between(now, target).toMinutes()
                val d = minutes / (24 * 60)
                val h = minutes % (24 * 60) / 60
                when {
                    d > 0 -> "$d j $h h"
                    h > 0 -> "$h h ${minutes % 60} min"
                    else -> "${maxOf(1, minutes)} min"
                }
            }
            days > 0 -> "J-$days"
            days == 0L -> "C'est aujourd'hui 🎉"
            days == -1L -> "Il y a 1 jour"
            else -> "Il y a ${-days} jours"
        }
        val end = (target ?: config.date.atStartOfDay(now.zone)).toInstant().toEpochMilli()
        val nowMs = now.toInstant().toEpochMilli()
        val progress = if (end <= config.createdAt) 1f
        else ((nowMs - config.createdAt).toFloat() / (end - config.createdAt)).coerceIn(0f, 1f)
        val dateText = (if (target != null) DATE_TIME.format(target) else DATE.format(config.date))
            .replaceFirstChar { it.titlecase(Locale.FRENCH) }
        return CountdownView(big, dateText, progress)
    }
}
```

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*CountdownTest*'`
Expected: PASS (3 tests).

- [ ] **Step 4: Ticks, kind, settings sheet, card**

`Ticks.kt` :

```kotlin
package app.lanceur.builtin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** Heure murale (ms), rafraîchie au début de chaque minute : horloges, compte à rebours. */
@Composable
fun rememberMinuteClock(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}
```

`BuiltinKind`, après `TODO(...)` :

```kotlin
    COUNTDOWN("Compte à rebours", "⏳", mapOf(SMALL to 120, MEDIUM to 200), SMALL, configurable = true),
```

`BuiltinSettings.kt` :

```kotlin
package app.lanceur.builtin

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import app.lanceur.builtin.countdown.CountdownConfig
import app.lanceur.builtin.countdown.CountdownSettings

/** Feuille de réglages d'un widget configurable ; `initial` vaut `null` à l'ajout. Annuler n'enregistre rien. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuiltinSettingsSheet(kind: BuiltinKind, initial: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        when (kind) {
            BuiltinKind.COUNTDOWN -> CountdownSettings(CountdownConfig.fromData(initial), onSave)
            else -> Unit
        }
    }
}
```

`CountdownSettings.kt` :

```kotlin
package app.lanceur.builtin.countdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Titre, date (par défaut dans une semaine) et heure facultative. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownSettings(initial: CountdownConfig?, onSave: (String) -> Unit, now: () -> Long = System::currentTimeMillis) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    // Le sélecteur de date travaille en UTC, à minuit
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = (initial?.date ?: LocalDate.now().plusDays(7)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    var withTime by remember { mutableStateOf(initial?.time != null) }
    val timeState = rememberTimePickerState(initialHour = initial?.time?.hour ?: 9, initialMinute = initial?.time?.minute ?: 0, is24Hour = true)
    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Compte à rebours", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Titre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("countdown-title"),
        )
        DatePicker(state = dateState, title = null, headline = null, showModeToggle = false)
        Row(Modifier.fillMaxWidth().clickable { withTime = !withTime }, verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = withTime, onCheckedChange = { withTime = it })
            Spacer(Modifier.width(12.dp))
            Text("Heure précise")
        }
        if (withTime) TimeInput(state = timeState)
        Button(
            onClick = {
                val millis = dateState.selectedDateMillis ?: return@Button
                val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                val time = if (withTime) LocalTime.of(timeState.hour, timeState.minute) else null
                onSave(CountdownConfig(title.trim().ifEmpty { "Compte à rebours" }, date, time, initial?.createdAt ?: now()).toData())
            },
            enabled = dateState.selectedDateMillis != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Enregistrer") }
    }
}
```

`CountdownCard.kt` :

```kotlin
package app.lanceur.builtin.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import app.lanceur.widgets.WidgetSize
import java.time.ZonedDateTime

@Composable
fun CountdownCard(
    config: CountdownConfig?,
    size: WidgetSize,
    onSetUp: () -> Unit,
    modifier: Modifier = Modifier,
    now: ZonedDateTime = ZonedDateTime.now(),
) {
    val colors = MaterialTheme.colorScheme
    if (config == null) {
        Column(
            modifier.fillMaxSize().background(cardBackground()).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardLabel("Compte à rebours")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSetUp) { Text("Régler le compte à rebours") }
            Spacer(Modifier.weight(1f))
        }
        return
    }
    val view = Countdown.of(config, now)
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 22.dp, vertical = 16.dp)) {
        CardLabel(config.title)
        Text(
            view.big,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(view.dateText, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (size == WidgetSize.MEDIUM) {
            Spacer(Modifier.weight(1f))
            LinearProgressIndicator(progress = { view.progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
        }
    }
}
```

- [ ] **Step 5: Wire the sheet and the card**

`BuiltinServices` : ajouter `val openSettings: (WidgetSlot) -> Unit = {},`.

`BuiltinWidget`, au `when (kind)` :

```kotlin
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
```

`BuiltinPreview.SampleCard` :

```kotlin
        BuiltinKind.COUNTDOWN -> CountdownCard(
            CountdownConfig("Vacances", today.plusDays(12), null, 0),
            kind.defaultSize,
            onSetUp = {},
            modifier = modifier,
        )
```

`AppRoot.kt` :
- en tête du composable, après `pickerQuery` :

```kotlin
    // Feuille de réglages d'un widget intégré : à l'ajout (`appWidgetId` nul) ou par ⚙
    var settingsRequest by remember { mutableStateOf<Pair<BuiltinKind, Int?>?>(null) }
```

- dans `BuiltinServices(...)` : `openSettings = { slot -> BuiltinSlots.kindOf(slot)?.let { settingsRequest = it to slot.appWidgetId } },`
- `isReconfigurable = { if (BuiltinSlots.isBuiltin(it)) BuiltinSlots.kindOf(it)?.configurable == true else container.widgetHost.isReconfigurable(it.appWidgetId) },`
- dans `WidgetPageActions`, `reconfigure = { slot -> BuiltinSlots.kindOf(slot)?.let { settingsRequest = it to slot.appWidgetId } ?: widgetHostActions.reconfigure(slot) },`
- `onPick` : `if (kind != null) { if (kind.configurable) settingsRequest = kind to null else vm.addBuiltinWidget(kind) } else widgetHostActions.add(entry)`
- à la fin du `Box(Modifier.fillMaxSize())` principal :

```kotlin
        settingsRequest?.let { (kind, id) ->
            BuiltinSettingsSheet(
                kind = kind,
                initial = id?.let { prefs.widgetData[it] },
                onSave = { data ->
                    if (id == null) vm.addBuiltinWidget(kind, data) else vm.setWidgetData(id, data)
                    settingsRequest = null
                },
                onDismiss = { settingsRequest = null },
            )
        }
```

(imports `app.lanceur.builtin.BuiltinKind`, `app.lanceur.builtin.BuiltinSettingsSheet`).

- [ ] **Step 6: Run the tests**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest` puis la classe `app.lanceur.builtin.countdown.CountdownCardsTest` sur le téléphone.
Expected: PASS (suite JVM ; 3 tests d'interface).

- [ ] **Step 7: Commit**

```bash
git add app/src
git commit -m "Widget Compte à rebours et feuille de réglages des widgets intégrés"
```

---

### Task 5: Horloges du monde

**Files:**
- Modify: `BuiltinKind.kt` (`WORLD_CLOCKS`), `BuiltinWidget.kt`, `BuiltinPreview.kt`, `BuiltinSettings.kt`
- Create: `app/src/main/java/app/lanceur/builtin/clocks/Cities.kt`, `WorldClock.kt`, `WorldClocksCard.kt`, `WorldClocksSettings.kt`
- Test: `app/src/test/java/app/lanceur/builtin/clocks/WorldClockTest.kt`, `app/src/androidTest/java/app/lanceur/builtin/clocks/WorldClocksTest.kt`

**Interfaces:**
- Produces: `data class City(id, name, zone: ZoneId)` ; `Cities.all`, `byId(id)`, `search(query)`, `home(zone, now)` ; `ClockFace(city, time, offset, dayHint: String?, isDay)` ; `WorldClock.of(city, now: Instant, home: ZoneId)` ; `WorldClocksConfig(cityIds)` + `toData`/`fromData` ; `@Composable WorldClocksCard(faces, size, modifier)` ; `@Composable WorldClocksSettings(initial, home: City, onSave)`.

- [ ] **Step 1: Write the failing tests**

`WorldClockTest.kt` :

```kotlin
package app.lanceur.builtin.clocks

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldClockTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun city(id: String) = Cities.byId(id)!!
    private val noon = Instant.parse("2026-10-05T12:00:00Z")

    @Test
    fun offsets_seen_from_paris() {
        assertEquals("+7 h", WorldClock.of(city("tokyo"), noon, paris).offset)
        assertEquals("21:00", WorldClock.of(city("tokyo"), noon, paris).time)
        assertEquals("−6 h", WorldClock.of(city("new-york"), noon, paris).offset)
        assertEquals("+3 h 30", WorldClock.of(city("new-delhi"), noon, paris).offset)
        assertEquals("même heure", WorldClock.of(city("paris"), noon, paris).offset)
    }

    @Test
    fun relative_day_and_daylight() {
        val evening = Instant.parse("2026-10-05T20:00:00Z")
        val tokyo = WorldClock.of(city("tokyo"), evening, paris)
        assertEquals("demain", tokyo.dayHint)
        assertFalse(tokyo.isDay)
        assertEquals("hier", WorldClock.of(city("honolulu"), Instant.parse("2026-10-05T05:00:00Z"), paris).dayHint)
        assertNull(WorldClock.of(city("tokyo"), noon, paris).dayHint)
        assertTrue(WorldClock.of(city("paris"), noon, paris).isDay)
    }

    @Test
    fun cities_search_without_accents_and_home() {
        assertEquals("São Paulo", Cities.search("sao").first().name)
        assertEquals("Montréal", Cities.search("MONTREAL").single().name)
        assertEquals(Cities.all, Cities.search(""))
        assertEquals(Cities.all.size, Cities.all.map { it.id }.toSet().size)
        assertEquals("paris", Cities.home(paris, noon).id)
        assertEquals("paris", Cities.home(ZoneId.of("Europe/Andorra"), noon).id)
    }

    @Test
    fun config_round_trip() {
        val config = WorldClocksConfig(listOf("paris", "tokyo"))
        assertEquals(config, WorldClocksConfig.fromData(config.toData()))
        assertEquals(WorldClocksConfig(emptyList()), WorldClocksConfig.fromData(null))
    }
}
```

`WorldClocksTest.kt` :

```kotlin
package app.lanceur.builtin.clocks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.lanceur.widgets.WidgetSize
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorldClocksTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun card_shows_time_and_offset() {
        val face = WorldClock.of(Cities.byId("tokyo")!!, Instant.parse("2026-10-05T12:00:00Z"), ZoneId.of("Europe/Paris"))
        rule.setContent { MaterialTheme { Box(Modifier.height(200.dp)) { WorldClocksCard(listOf(face), WidgetSize.MEDIUM) } } }
        rule.onNodeWithText("Tokyo").assertIsDisplayed()
        rule.onNodeWithText("21:00").assertIsDisplayed()
        rule.onNodeWithText("☾ +7 h").assertIsDisplayed()
    }

    @Test
    fun settings_add_a_city_to_home() {
        var saved: String? = null
        rule.setContent { MaterialTheme { WorldClocksSettings(initial = null, home = Cities.byId("paris")!!, onSave = { saved = it }) } }
        rule.onNodeWithTag("city-search").performTextInput("tokyo")
        rule.onNodeWithText("Tokyo").performClick()
        rule.onNodeWithText("Enregistrer").performClick()
        assertEquals(listOf("paris", "tokyo"), WorldClocksConfig.fromData(saved).cityIds)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*WorldClockTest*'`
Expected: FAIL at compilation, "Unresolved reference 'Cities'".

- [ ] **Step 3: Write `Cities.kt`, `WorldClock.kt`, the kind**

```kotlin
package app.lanceur.builtin.clocks

import app.lanceur.text.TextNormalizer
import java.time.Instant
import java.time.ZoneId

data class City(val id: String, val name: String, val zone: ZoneId)

/** Grandes villes, dont l'outre-mer français. L'identifiant est enregistré : ne jamais le changer. */
object Cities {
    private fun c(id: String, name: String, zone: String) = City(id, name, ZoneId.of(zone))

    val all: List<City> = listOf(
        c("paris", "Paris", "Europe/Paris"),
        c("londres", "Londres", "Europe/London"),
        c("lisbonne", "Lisbonne", "Europe/Lisbon"),
        c("madrid", "Madrid", "Europe/Madrid"),
        c("bruxelles", "Bruxelles", "Europe/Brussels"),
        c("amsterdam", "Amsterdam", "Europe/Amsterdam"),
        c("berlin", "Berlin", "Europe/Berlin"),
        c("rome", "Rome", "Europe/Rome"),
        c("zurich", "Zurich", "Europe/Zurich"),
        c("vienne", "Vienne", "Europe/Vienna"),
        c("stockholm", "Stockholm", "Europe/Stockholm"),
        c("oslo", "Oslo", "Europe/Oslo"),
        c("copenhague", "Copenhague", "Europe/Copenhagen"),
        c("helsinki", "Helsinki", "Europe/Helsinki"),
        c("varsovie", "Varsovie", "Europe/Warsaw"),
        c("prague", "Prague", "Europe/Prague"),
        c("athenes", "Athènes", "Europe/Athens"),
        c("istanbul", "Istanbul", "Europe/Istanbul"),
        c("kiev", "Kiev", "Europe/Kiev"),
        c("moscou", "Moscou", "Europe/Moscow"),
        c("reykjavik", "Reykjavik", "Atlantic/Reykjavik"),
        c("casablanca", "Casablanca", "Africa/Casablanca"),
        c("alger", "Alger", "Africa/Algiers"),
        c("tunis", "Tunis", "Africa/Tunis"),
        c("dakar", "Dakar", "Africa/Dakar"),
        c("abidjan", "Abidjan", "Africa/Abidjan"),
        c("lagos", "Lagos", "Africa/Lagos"),
        c("le-caire", "Le Caire", "Africa/Cairo"),
        c("nairobi", "Nairobi", "Africa/Nairobi"),
        c("johannesburg", "Johannesburg", "Africa/Johannesburg"),
        c("saint-denis", "Saint-Denis (La Réunion)", "Indian/Reunion"),
        c("dubai", "Dubaï", "Asia/Dubai"),
        c("riyad", "Riyad", "Asia/Riyadh"),
        c("teheran", "Téhéran", "Asia/Tehran"),
        c("karachi", "Karachi", "Asia/Karachi"),
        c("new-delhi", "New Delhi", "Asia/Kolkata"),
        c("mumbai", "Mumbai", "Asia/Kolkata"),
        c("katmandou", "Katmandou", "Asia/Kathmandu"),
        c("dacca", "Dacca", "Asia/Dhaka"),
        c("bangkok", "Bangkok", "Asia/Bangkok"),
        c("hanoi", "Hanoï", "Asia/Ho_Chi_Minh"),
        c("jakarta", "Jakarta", "Asia/Jakarta"),
        c("singapour", "Singapour", "Asia/Singapore"),
        c("hong-kong", "Hong Kong", "Asia/Hong_Kong"),
        c("pekin", "Pékin", "Asia/Shanghai"),
        c("shanghai", "Shanghai", "Asia/Shanghai"),
        c("taipei", "Taipei", "Asia/Taipei"),
        c("manille", "Manille", "Asia/Manila"),
        c("seoul", "Séoul", "Asia/Seoul"),
        c("tokyo", "Tokyo", "Asia/Tokyo"),
        c("perth", "Perth", "Australia/Perth"),
        c("brisbane", "Brisbane", "Australia/Brisbane"),
        c("sydney", "Sydney", "Australia/Sydney"),
        c("melbourne", "Melbourne", "Australia/Melbourne"),
        c("noumea", "Nouméa", "Pacific/Noumea"),
        c("auckland", "Auckland", "Pacific/Auckland"),
        c("papeete", "Papeete", "Pacific/Tahiti"),
        c("honolulu", "Honolulu", "Pacific/Honolulu"),
        c("anchorage", "Anchorage", "America/Anchorage"),
        c("vancouver", "Vancouver", "America/Vancouver"),
        c("los-angeles", "Los Angeles", "America/Los_Angeles"),
        c("san-francisco", "San Francisco", "America/Los_Angeles"),
        c("phoenix", "Phoenix", "America/Phoenix"),
        c("denver", "Denver", "America/Denver"),
        c("mexico", "Mexico", "America/Mexico_City"),
        c("chicago", "Chicago", "America/Chicago"),
        c("houston", "Houston", "America/Chicago"),
        c("toronto", "Toronto", "America/Toronto"),
        c("montreal", "Montréal", "America/Toronto"),
        c("new-york", "New York", "America/New_York"),
        c("miami", "Miami", "America/New_York"),
        c("la-havane", "La Havane", "America/Havana"),
        c("bogota", "Bogota", "America/Bogota"),
        c("lima", "Lima", "America/Lima"),
        c("fort-de-france", "Fort-de-France", "America/Martinique"),
        c("pointe-a-pitre", "Pointe-à-Pitre", "America/Guadeloupe"),
        c("cayenne", "Cayenne", "America/Cayenne"),
        c("santiago", "Santiago", "America/Santiago"),
        c("buenos-aires", "Buenos Aires", "America/Argentina/Buenos_Aires"),
        c("sao-paulo", "São Paulo", "America/Sao_Paulo"),
        c("rio", "Rio de Janeiro", "America/Sao_Paulo"),
    )

    private val byIdMap = all.associateBy { it.id }

    fun byId(id: String): City? = byIdMap[id]

    fun search(query: String): List<City> {
        val q = TextNormalizer.fold(query.trim())
        return if (q.isEmpty()) all else all.filter { TextNormalizer.fold(it.name).contains(q) }
    }

    /** Ville du téléphone : même fuseau, sinon même décalage en ce moment, sinon Paris. */
    fun home(zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): City =
        all.firstOrNull { it.zone == zone }
            ?: all.firstOrNull { it.zone.rules.getOffset(now) == zone.rules.getOffset(now) }
            ?: all.first()
}
```

`WorldClock.kt` :

```kotlin
package app.lanceur.builtin.clocks

import app.lanceur.builtin.WidgetData
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

data class ClockFace(val city: City, val time: String, val offset: String, val dayHint: String?, val isDay: Boolean)

data class WorldClocksConfig(val cityIds: List<String>) {
    fun toData(): String = WidgetData.encode(mapOf("cities" to WidgetData.list(cityIds)))

    companion object {
        const val MAX = 4

        fun fromData(data: String?) = WorldClocksConfig(WidgetData.unlist(WidgetData.decode(data)["cities"]).filter { it.isNotBlank() })
    }
}

object WorldClock {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)

    /** `home` : fuseau du téléphone, référence du décalage et du jour relatif. */
    fun of(city: City, now: Instant, home: ZoneId): ClockFace {
        val there = now.atZone(city.zone)
        val here = now.atZone(home)
        val seconds = there.offset.totalSeconds - here.offset.totalSeconds
        val offset = if (seconds == 0) "même heure" else {
            val minutes = abs(seconds) / 60
            val sign = if (seconds > 0) "+" else "−"
            if (minutes % 60 == 0) "$sign${minutes / 60} h" else "$sign${minutes / 60} h ${String.format(Locale.ROOT, "%02d", minutes % 60)}"
        }
        val days = ChronoUnit.DAYS.between(here.toLocalDate(), there.toLocalDate())
        val hint = when {
            days > 0 -> "demain"
            days < 0 -> "hier"
            else -> null
        }
        return ClockFace(city, TIME.format(there), offset, hint, there.hour in 7..18)
    }
}
```

`BuiltinKind`, après `COUNTDOWN(...)` :

```kotlin
    WORLD_CLOCKS("Horloges du monde", "🌍", mapOf(SMALL to 120, MEDIUM to 200), MEDIUM, configurable = true),
```

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*WorldClockTest*'` après l'étape 4 (le `when` de `BuiltinWidget` doit d'abord être complet).
Expected: PASS (4 tests).

- [ ] **Step 4: Card, settings, wiring**

`WorldClocksCard.kt` :

```kotlin
package app.lanceur.builtin.clocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import app.lanceur.widgets.WidgetSize

@Composable
fun WorldClocksCard(faces: List<ClockFace>, size: WidgetSize, modifier: Modifier = Modifier) {
    val shown = if (size == WidgetSize.SMALL) faces.take(2) else faces.take(WorldClocksConfig.MAX)
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 22.dp, vertical = 14.dp)) {
        CardLabel("Horloges")
        Spacer(Modifier.height(6.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
            shown.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { FaceCell(it, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FaceCell(face: ClockFace, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(face.city.name, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(face.time, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        Text(
            listOfNotNull("${if (face.isDay) "☀" else "☾"} ${face.offset}", face.dayHint).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
```

`WorldClocksSettings.kt` :

```kotlin
package app.lanceur.builtin.clocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** 1 à 4 villes ; à l'ajout, la ville du téléphone est cochée. */
@Composable
fun WorldClocksSettings(initial: WorldClocksConfig?, home: City, onSave: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initial?.cityIds?.takeIf { it.isNotEmpty() } ?: listOf(home.id)) }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Horloges du monde", style = MaterialTheme.typography.headlineSmall)
        Text("${selected.size} / ${WorldClocksConfig.MAX} villes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Chercher une ville") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("city-search"),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            items(Cities.search(query), key = { it.id }) { city ->
                val checked = city.id in selected
                val enabled = checked || selected.size < WorldClocksConfig.MAX
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = enabled) { selected = if (checked) selected - city.id else selected + city.id },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                    Text(city.name, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        Button(
            onClick = { onSave(WorldClocksConfig(selected).toData()) },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Enregistrer") }
    }
}
```

`BuiltinSettings.kt` : ajouter au `when` :

```kotlin
            BuiltinKind.WORLD_CLOCKS -> WorldClocksSettings(WorldClocksConfig.fromData(initial), Cities.home(), onSave)
```

`BuiltinWidget`, au `when (kind)` :

```kotlin
        BuiltinKind.WORLD_CLOCKS -> {
            val now = java.time.Instant.ofEpochMilli(rememberMinuteClock())
            val home = java.time.ZoneId.systemDefault()
            val cities = WorldClocksConfig.fromData(services.data(slot.appWidgetId)).cityIds.mapNotNull(Cities::byId)
                .ifEmpty { listOf(Cities.home(home, now)) }
            WorldClocksCard(cities.map { WorldClock.of(it, now, home) }, size, modifier)
        }
```

`BuiltinPreview.SampleCard` :

```kotlin
        BuiltinKind.WORLD_CLOCKS -> {
            val now = java.time.Instant.now()
            WorldClocksCard(listOf("paris", "new-york", "tokyo", "sydney").mapNotNull(Cities::byId).map { WorldClock.of(it, now, zone) }, kind.defaultSize, modifier)
        }
```

- [ ] **Step 5: Run the tests**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest` puis la classe `app.lanceur.builtin.clocks.WorldClocksTest` sur le téléphone.
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "Widget Horloges du monde"
```

---

### Task 6: Minuteur / chrono

**Files:**
- Modify: `BuiltinKind.kt` (`TIMER`), `BuiltinWidget.kt` (`startTimer`, `openTimers`), `BuiltinPreview.kt`, `AppRoot.kt`
- Create: `app/src/main/java/app/lanceur/builtin/timer/Stopwatch.kt`, `TimerCard.kt`
- Test: `app/src/test/java/app/lanceur/builtin/timer/StopwatchTest.kt`, `app/src/androidTest/java/app/lanceur/builtin/timer/TimerCardTest.kt`

**Interfaces:**
- Produces: `StopwatchState(startedAt: Long?, accumulated: Long, laps: List<Long>)` avec `running`, `elapsed(now)`, `start`, `pause`, `reset`, `lap`, `toData`, `fromData`, `StopwatchState.format(ms)` ; `TimerPresets.minutes`, `seconds(min)`, `label(min)` ; `@Composable TimerCard(stopwatch, onStopwatch, onTimer: (Int) -> Boolean, onOtherTimer, modifier, clock)` ; `BuiltinServices.startTimer: (Int) -> Boolean`, `openTimers: () -> Unit`.

- [ ] **Step 1: Write the failing tests**

`StopwatchTest.kt` :

```kotlin
package app.lanceur.builtin.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchTest {
    @Test
    fun start_pause_resume() {
        val s = StopwatchState().start(1_000)
        assertTrue(s.running)
        assertEquals(500, s.elapsed(1_500))
        val paused = s.pause(3_000)
        assertFalse(paused.running)
        assertEquals(2_000, paused.elapsed(99_000))
        assertEquals(2_500, paused.start(10_000).elapsed(10_500))
        assertEquals(StopwatchState(), paused.reset())
    }

    @Test
    fun laps_keep_the_last_three() {
        var s = StopwatchState().start(0)
        listOf(1_000L, 2_000, 3_000, 4_000).forEach { s = s.lap(it) }
        assertEquals(listOf(2_000L, 3_000, 4_000), s.laps)
    }

    @Test
    fun clock_going_back_never_gives_negative_time() {
        assertEquals(0, StopwatchState().start(10_000).elapsed(5_000))
    }

    @Test
    fun format_and_round_trip() {
        assertEquals("00:42,3", StopwatchState.format(42_345))
        assertEquals("1:02:03,4", StopwatchState.format(3_723_456))
        val s = StopwatchState(startedAt = 5, accumulated = 7, laps = listOf(1, 2))
        assertEquals(s, StopwatchState.fromData(s.toData()))
        assertEquals(StopwatchState(), StopwatchState.fromData("n'importe quoi"))
        assertEquals(listOf("1 min", "3 min", "5 min", "10 min", "15 min", "30 min"), TimerPresets.minutes.map(TimerPresets::label))
        assertEquals(300, TimerPresets.seconds(5))
    }
}
```

`TimerCardTest.kt` :

```kotlin
package app.lanceur.builtin.timer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimerCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun preset_starts_a_timer_and_stopwatch_runs() {
        var seconds = 0
        var stopwatch by mutableStateOf(StopwatchState())
        rule.setContent {
            MaterialTheme {
                Box(Modifier.height(200.dp)) {
                    TimerCard(stopwatch, onStopwatch = { stopwatch = it }, onTimer = { seconds = it; true }, onOtherTimer = {}, clock = { 1_000L })
                }
            }
        }
        rule.onNodeWithText("5 min").performClick()
        assertEquals(300, seconds)
        rule.onNodeWithText("Minuteur de 5 min lancé").assertIsDisplayed()
        rule.onNodeWithText("Chrono").performClick()
        rule.onNodeWithText("Démarrer").performClick()
        assertTrue(stopwatch.running)
        rule.onNodeWithText("Pause").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*StopwatchTest*'`
Expected: FAIL at compilation, "Unresolved reference 'StopwatchState'".

- [ ] **Step 3: Write `Stopwatch.kt` and the kind**

```kotlin
package app.lanceur.builtin.timer

import app.lanceur.builtin.WidgetData
import java.util.Locale

/** Chrono sur l'horloge murale : `startedAt` survit au redémarrage de Lanceur ou du téléphone. */
data class StopwatchState(val startedAt: Long? = null, val accumulated: Long = 0, val laps: List<Long> = emptyList()) {
    val running: Boolean get() = startedAt != null

    /** Horloge reculée par l'utilisateur : jamais de temps négatif. */
    fun elapsed(now: Long): Long = accumulated + (startedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)

    fun start(now: Long) = if (running) this else copy(startedAt = now)

    fun pause(now: Long) = if (!running) this else copy(startedAt = null, accumulated = elapsed(now))

    fun reset() = StopwatchState()

    fun lap(now: Long) = copy(laps = (laps + elapsed(now)).takeLast(3))

    fun toData(): String = WidgetData.encode(
        buildMap {
            startedAt?.let { put("started", it.toString()) }
            put("accumulated", accumulated.toString())
            put("laps", WidgetData.list(laps.map { it.toString() }))
        },
    )

    companion object {
        fun fromData(data: String?): StopwatchState {
            val values = WidgetData.decode(data)
            return StopwatchState(
                startedAt = values["started"]?.toLongOrNull(),
                accumulated = values["accumulated"]?.toLongOrNull() ?: 0,
                laps = WidgetData.unlist(values["laps"]).mapNotNull { it.toLongOrNull() },
            )
        }

        /** « 00:42,3 », ou « 1:02:03,4 » au-delà d'une heure. */
        fun format(ms: Long): String {
            val total = ms.coerceAtLeast(0)
            val tenths = total / 100 % 10
            val seconds = total / 1000
            val h = seconds / 3600
            val m = seconds % 3600 / 60
            val s = seconds % 60
            return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d,%d", h, m, s, tenths)
            else String.format(Locale.ROOT, "%02d:%02d,%d", m, s, tenths)
        }
    }
}

object TimerPresets {
    val minutes = listOf(1, 3, 5, 10, 15, 30)

    fun seconds(minutes: Int) = minutes * 60

    fun label(minutes: Int) = "$minutes min"
}
```

`BuiltinKind`, après `WORLD_CLOCKS(...)` :

```kotlin
    TIMER("Minuteur / chrono", "⏱", mapOf(MEDIUM to 200), MEDIUM),
```

- [ ] **Step 4: Card and wiring**

`TimerCard.kt` :

```kotlin
package app.lanceur.builtin.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lanceur.ui.cardBackground
import kotlinx.coroutines.delay

/** `onTimer` lance un minuteur de l'Horloge (secondes) ; `false` : l'Horloge a seulement été ouverte. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerCard(
    stopwatch: StopwatchState,
    onStopwatch: (StopwatchState) -> Unit,
    onTimer: (Int) -> Boolean,
    onOtherTimer: () -> Unit,
    modifier: Modifier = Modifier,
    clock: () -> Long = System::currentTimeMillis,
) {
    var chrono by remember { mutableStateOf(stopwatch.running) }
    var message by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(stopwatch.running) {
        now = clock()
        while (stopwatch.running) {
            delay(100)
            now = clock()
        }
    }
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Minuteur", "Chrono").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = chrono == (index == 1),
                    onClick = { chrono = index == 1 },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    icon = {},
                    label = { Text(label) },
                )
            }
        }
        if (!chrono) {
            Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.Center) {
                (TimerPresets.minutes.map { it as Int? } + null).chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { minutes ->
                            AssistChip(
                                onClick = {
                                    if (minutes == null) onOtherTimer()
                                    else message = if (onTimer(TimerPresets.seconds(minutes))) "Minuteur de ${TimerPresets.label(minutes)} lancé" else null
                                },
                                label = { Text(minutes?.let(TimerPresets::label) ?: "Autre") },
                            )
                        }
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        } else {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(StopwatchState.format(stopwatch.elapsed(now)), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                if (stopwatch.laps.isNotEmpty()) {
                    Text(
                        stopwatch.laps.joinToString("  ·  ") { StopwatchState.format(it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        val t = clock()
                        onStopwatch(if (stopwatch.running) stopwatch.pause(t) else stopwatch.start(t))
                    }) { Text(if (stopwatch.running) "Pause" else "Démarrer") }
                    OutlinedButton(onClick = { onStopwatch(stopwatch.lap(clock())) }, enabled = stopwatch.running) { Text("Tour") }
                    TextButton(onClick = { onStopwatch(stopwatch.reset()) }, enabled = !stopwatch.running && stopwatch.accumulated > 0) {
                        Text("Réinitialiser")
                    }
                }
            }
        }
    }
}
```

`BuiltinServices` : ajouter `val startTimer: (Int) -> Boolean = { false },` et `val openTimers: () -> Unit = {},`.

`BuiltinWidget`, au `when (kind)` :

```kotlin
        BuiltinKind.TIMER -> {
            var stopwatch by remember(slot.appWidgetId) { mutableStateOf(StopwatchState.fromData(services.data(slot.appWidgetId))) }
            TimerCard(
                stopwatch,
                onStopwatch = { stopwatch = it; services.saveData(slot.appWidgetId, it.toData()) },
                onTimer = services.startTimer,
                onOtherTimer = services.openTimers,
                modifier = modifier,
            )
        }
```

`BuiltinPreview.SampleCard` :

```kotlin
        BuiltinKind.TIMER -> TimerCard(StopwatchState(), onStopwatch = {}, onTimer = { false }, onOtherTimer = {}, modifier = modifier)
```

`AppRoot`, dans `BuiltinServices(...)` (import `android.provider.AlarmClock`) :

```kotlin
        startTimer = { seconds ->
            val direct = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            container.appLauncher.startSafely(direct).also { started ->
                if (!started) container.appLauncher.startSafely(Intent(AlarmClock.ACTION_SHOW_TIMERS))
            }
        },
        openTimers = { container.appLauncher.startSafely(Intent(AlarmClock.ACTION_SHOW_TIMERS)) },
```

- [ ] **Step 5: Run the tests**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest` puis la classe `app.lanceur.builtin.timer.TimerCardTest` sur le téléphone.
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "Widget Minuteur / chrono"
```

---

### Task 7: Contacts favoris

**Files:**
- Modify: `BuiltinKind.kt` (`FAVORITE_CONTACTS`), `BuiltinWidget.kt`, `BuiltinPreview.kt`, `AppContainer.kt`, `AppRoot.kt`
- Create: `app/src/main/java/app/lanceur/builtin/contacts/FavoriteContact.kt`, `FavoriteContactsSource.kt`, `FavoritesCard.kt`
- Test: `app/src/test/java/app/lanceur/builtin/contacts/FavoriteContactTest.kt`, `app/src/androidTest/java/app/lanceur/builtin/contacts/FavoritesCardTest.kt`

**Interfaces:**
- Produces: `data class FavoriteContact(lookupUri, name, phone: String?, photo: ImageBitmap?)` avec `firstName`, `initial` ; `sealed interface FavoritesState { NoPermission; Loaded(contacts) }` ; `FavoriteContactsSource(context).favorites(): Flow<FavoritesState>` ; `class FavoritesActions(call, sms, open, requestPermission)` ; `@Composable FavoritesCard(state, size, actions, modifier)` ; `BuiltinServices.contacts: FavoriteContactsSource?`, `favoritesActions: FavoritesActions`.

- [ ] **Step 1: Write the failing tests**

`FavoriteContactTest.kt` :

```kotlin
package app.lanceur.builtin.contacts

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteContactTest {
    private fun contact(name: String) = FavoriteContact("uri", name, null, null)

    @Test
    fun first_name_and_initial() {
        assertEquals("Jean-Côme", contact("Jean-Côme Chopin").firstName)
        assertEquals("Maman", contact("Maman").firstName)
        assertEquals("É", contact("élodie Martin").initial)
        assertEquals("?", contact("  ").initial)
        assertEquals("?", contact("  ").firstName)
    }
}
```

`FavoritesCardTest.kt` :

```kotlin
package app.lanceur.builtin.contacts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.lanceur.widgets.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FavoritesCardTest {
    @get:Rule val rule = createComposeRule()

    private val alice = FavoriteContact("uri-a", "Alice Durand", "0601020304", null)
    private val bob = FavoriteContact("uri-b", "Bob", null, null)

    @Test
    fun tap_calls_long_press_texts_and_no_number_opens() {
        val calls = mutableListOf<String>()
        val actions = FavoritesActions(call = { calls += "call:$it" }, sms = { calls += "sms:$it" }, open = { calls += "open:$it" })
        rule.setContent { MaterialTheme { Box(Modifier.height(120.dp)) { FavoritesCard(FavoritesState.Loaded(listOf(alice, bob)), WidgetSize.SMALL, actions) } } }
        rule.onNodeWithText("Alice").performClick()
        rule.onNodeWithText("Alice").performTouchInput { longClick() }
        rule.onNodeWithText("Bob").performClick()
        assertEquals(listOf("call:0601020304", "sms:0601020304", "open:uri-b"), calls)
    }

    @Test
    fun empty_and_no_permission() {
        var asked = false
        rule.setContent { MaterialTheme { FavoritesCard(FavoritesState.NoPermission, WidgetSize.SMALL, FavoritesActions(requestPermission = { asked = true })) } }
        rule.onNodeWithText("Autoriser les contacts").performClick()
        assertEquals(true, asked)
    }

    @Test
    fun no_favorites_explains_how_to_add_them() {
        rule.setContent { MaterialTheme { FavoritesCard(FavoritesState.Loaded(emptyList()), WidgetSize.SMALL, FavoritesActions()) } }
        rule.onNodeWithText("Ajoute des favoris ⭐ dans Contacts").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*FavoriteContactTest*'`
Expected: FAIL at compilation, "Unresolved reference 'FavoriteContact'".

- [ ] **Step 3: Model, source, kind**

`FavoriteContact.kt` :

```kotlin
package app.lanceur.builtin.contacts

import androidx.compose.ui.graphics.ImageBitmap
import java.util.Locale

data class FavoriteContact(val lookupUri: String, val name: String, val phone: String?, val photo: ImageBitmap?) {
    val firstName: String get() = name.trim().substringBefore(' ').ifEmpty { "?" }

    val initial: String get() = name.trim().firstOrNull()?.toString()?.uppercase(Locale.FRENCH) ?: "?"
}

sealed interface FavoritesState {
    data object NoPermission : FavoritesState
    data class Loaded(val contacts: List<FavoriteContact>) : FavoritesState
}
```

`FavoriteContactsSource.kt` :

```kotlin
package app.lanceur.builtin.contacts

import android.Manifest
import android.content.Context
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import androidx.compose.ui.graphics.asImageBitmap
import app.lanceur.search.SearchPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Contacts marqués d'une étoile dans l'appli Contacts, suivis en direct. */
class FavoriteContactsSource(private val context: Context) {
    /** Un nouveau flux à chaque appel : la permission est revérifiée (accordée entre-temps). */
    fun favorites(): Flow<FavoritesState> = callbackFlow {
        if (!SearchPermissions.granted(context, Manifest.permission.READ_CONTACTS)) {
            trySend(FavoritesState.NoPermission)
            awaitClose { }
            return@callbackFlow
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch { send(load()) }
            }
        }
        context.contentResolver.registerContentObserver(Contacts.CONTENT_URI, true, observer)
        send(load())
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    private suspend fun load(): FavoritesState = withContext(Dispatchers.IO) {
        FavoritesState.Loaded(runCatching { query() }.getOrDefault(emptyList()))
    }

    private fun query(): List<FavoriteContact> {
        val resolver = context.contentResolver
        val projection = arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_THUMBNAIL_URI, Contacts.HAS_PHONE_NUMBER)
        val out = mutableListOf<FavoriteContact>()
        resolver.query(Contacts.CONTENT_URI, projection, "${Contacts.STARRED} = 1", null, "${Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")?.use { c ->
            while (c.moveToNext() && out.size < MAX) {
                val id = c.getLong(0)
                val phone = if (c.getInt(4) == 1) firstPhone(id) else null
                out += FavoriteContact(
                    lookupUri = Contacts.getLookupUri(id, c.getString(1)).toString(),
                    name = c.getString(2).orEmpty(),
                    phone = phone,
                    photo = c.getString(3)?.let { photo(Uri.parse(it)) },
                )
            }
        }
        return out
    }

    /** Le numéro principal s'il y en a un, sinon le premier. */
    private fun firstPhone(contactId: Long): String? =
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.NUMBER),
            "${Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${Phone.IS_SUPER_PRIMARY} DESC, ${Phone.IS_PRIMARY} DESC",
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    private fun photo(uri: Uri) = runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    }.getOrNull()

    private companion object {
        const val MAX = 8
    }
}
```

`BuiltinKind`, après `TIMER(...)` :

```kotlin
    FAVORITE_CONTACTS("Contacts favoris", "⭐", mapOf(SMALL to 120, MEDIUM to 220), SMALL),
```

- [ ] **Step 4: Card and wiring**

`FavoritesCard.kt` :

```kotlin
package app.lanceur.builtin.contacts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import app.lanceur.widgets.WidgetSize

class FavoritesActions(
    val call: (String) -> Unit = {},
    val sms: (String) -> Unit = {},
    val open: (String) -> Unit = {},
    val requestPermission: () -> Unit = {},
)

private const val PER_ROW = 4

@Composable
fun FavoritesCard(state: FavoritesState, size: WidgetSize, actions: FavoritesActions, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        CardLabel("Favoris", Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(6.dp))
        when (state) {
            FavoritesState.NoPermission -> TextButton(onClick = actions.requestPermission) { Text("Autoriser les contacts") }
            is FavoritesState.Loaded -> if (state.contacts.isEmpty()) {
                Text("Ajoute des favoris ⭐ dans Contacts", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            } else {
                val rows = if (size == WidgetSize.SMALL) 1 else 2
                state.contacts.take(rows * PER_ROW).chunked(PER_ROW).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { Bubble(it, actions, Modifier.weight(1f)) }
                        repeat(PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(contact: FavoriteContact, actions: FavoritesActions, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { contact.phone?.let(actions.call) ?: actions.open(contact.lookupUri) },
                onLongClick = { contact.phone?.let(actions.sms) ?: actions.open(contact.lookupUri) },
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
            if (contact.photo != null) Image(contact.photo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(contact.initial, style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
        }
        Text(contact.firstName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
```

`AppContainer` : `val favoriteContacts = FavoriteContactsSource(appContext)` (import).

`BuiltinServices` : ajouter `val contacts: FavoriteContactsSource? = null,` et `val favoritesActions: FavoritesActions = FavoritesActions(),`.

`BuiltinWidget`, au `when (kind)` :

```kotlin
        BuiltinKind.FAVORITE_CONTACTS -> {
            val source = services.contacts
            // Nouveau flux à chaque retour au premier plan : une permission accordée entre-temps est prise en compte
            val flow = remember(source, services.refresh) { source?.favorites() ?: kotlinx.coroutines.flow.flowOf(FavoritesState.Loaded(emptyList())) }
            val state by flow.collectAsStateWithLifecycle(FavoritesState.Loaded(emptyList()))
            FavoritesCard(state, size, services.favoritesActions, modifier)
        }
```

`BuiltinPreview.SampleCard` :

```kotlin
        BuiltinKind.FAVORITE_CONTACTS -> FavoritesCard(
            FavoritesState.Loaded(listOf("Maman", "Léa", "Hugo", "Inès").map { FavoriteContact(it, it, "0", null) }),
            kind.defaultSize,
            FavoritesActions(),
            modifier,
        )
```

`AppRoot`, dans `BuiltinServices(...)` :

```kotlin
        contacts = container.favoriteContacts,
        favoritesActions = FavoritesActions(
            call = container.resultActions::call,
            sms = container.resultActions::sms,
            open = { uri -> container.resultActions.open(SearchResult.Contact(uri, "", null)) },
            requestPermission = { permissionLauncher.launch(SearchPermissions.ALL) },
        ),
```

- [ ] **Step 5: Run the tests**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest` puis la classe `app.lanceur.builtin.contacts.FavoritesCardTest` sur le téléphone.
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "Widget Contacts favoris"
```

---

### Task 8: Raccourcis rapides

**Files:**
- Modify: `BuiltinKind.kt` (`SHORTCUTS`), `BuiltinWidget.kt`, `BuiltinPreview.kt`, `AppContainer.kt`, `AppRoot.kt`
- Create: `app/src/main/java/app/lanceur/builtin/shortcuts/TorchController.kt`, `ShortcutsCard.kt`
- Test: `app/src/androidTest/java/app/lanceur/builtin/shortcuts/ShortcutsCardTest.kt`

**Ruling de conception :** la spec cite Wi-Fi et Internet à part, mais elle annonce cinq boutons. Le panneau « Internet » d'Android regroupe le Wi-Fi et les données mobiles. Les cinq boutons sont donc : Lampe, Internet, Bluetooth, Son, Ne pas déranger.

**Interfaces:**
- Produces: `data class TorchState(available, on)` ; `TorchController(context) { val state: Flow<TorchState>; fun set(on): Boolean }` ; `class ShortcutActions(toggleTorch: (Boolean) -> Unit, internet, bluetooth, sound, doNotDisturb)` ; `@Composable ShortcutsCard(torch, actions, modifier)` ; `BuiltinServices.torch: TorchController?`, `shortcutActions: ShortcutActions`.

- [ ] **Step 1: Write the failing UI test**

```kotlin
package app.lanceur.builtin.shortcuts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShortcutsCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun buttons_trigger_their_action() {
        val calls = mutableListOf<String>()
        val actions = ShortcutActions(
            toggleTorch = { calls += "torch:$it" },
            internet = { calls += "internet" },
            bluetooth = { calls += "bluetooth" },
            sound = { calls += "sound" },
            doNotDisturb = { calls += "dnd" },
        )
        rule.setContent { MaterialTheme { Box(Modifier.height(120.dp)) { ShortcutsCard(TorchState(available = true, on = false), actions) } } }
        listOf("Lampe", "Internet", "Bluetooth", "Son", "Ne pas déranger").forEach { rule.onNodeWithText(it).performClick() }
        assertEquals(listOf("torch:true", "internet", "bluetooth", "sound", "dnd"), calls)
    }

    @Test
    fun unavailable_torch_is_disabled() {
        rule.setContent { MaterialTheme { ShortcutsCard(TorchState(available = false, on = false), ShortcutActions()) } }
        rule.onNodeWithText("Lampe").assertIsNotEnabled()
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.builtin.shortcuts.ShortcutsCardTest`
Expected: FAIL at compilation, "Unresolved reference 'ShortcutActions'".

- [ ] **Step 3: Controller, card, kind, wiring**

`TorchController.kt` :

```kotlin
package app.lanceur.builtin.shortcuts

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

data class TorchState(val available: Boolean, val on: Boolean)

/** Lampe torche de la première caméra qui a un flash. Aucune permission n'est nécessaire. */
class TorchController(context: Context) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val cameraId: String? by lazy {
        runCatching {
            manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
    }

    val state: Flow<TorchState> = callbackFlow {
        val id = cameraId
        if (id == null) {
            trySend(TorchState(available = false, on = false))
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) trySend(TorchState(available = true, on = enabled))
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == id) trySend(TorchState(available = false, on = false))
            }
        }
        // L'enregistrement rappelle aussitôt avec l'état actuel
        manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { manager.unregisterTorchCallback(callback) }
    }.conflate()

    /** `false` si la lampe n'a pas pu changer (caméra occupée, pas de flash). */
    fun set(on: Boolean): Boolean {
        val id = cameraId ?: return false
        return runCatching { manager.setTorchMode(id, on) }.isSuccess
    }
}
```

`ShortcutsCard.kt` :

```kotlin
package app.lanceur.builtin.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground

class ShortcutActions(
    val toggleTorch: (Boolean) -> Unit = {},
    val internet: () -> Unit = {},
    val bluetooth: () -> Unit = {},
    val sound: () -> Unit = {},
    val doNotDisturb: () -> Unit = {},
)

/** Seule la lampe change d'état ici ; les autres ouvrent le panneau d'Android (une appli ne peut pas les changer). */
@Composable
fun ShortcutsCard(torch: TorchState, actions: ShortcutActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 12.dp, vertical = 12.dp)) {
        CardLabel("Raccourcis", Modifier.padding(start = 10.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Shortcut("🔦", "Lampe", active = torch.on, enabled = torch.available) { actions.toggleTorch(!torch.on) }
            Shortcut("🌐", "Internet", onClick = actions.internet)
            Shortcut("🔵", "Bluetooth", onClick = actions.bluetooth)
            Shortcut("🔊", "Son", onClick = actions.sound)
            Shortcut("🌙", "Ne pas déranger", onClick = actions.doNotDisturb)
        }
    }
}

@Composable
private fun Shortcut(emoji: String, label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .size(width = 64.dp, height = 76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (active) colors.primary else colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 20.sp) }
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 12.sp)
    }
}
```

`BuiltinKind`, après `FAVORITE_CONTACTS(...)` :

```kotlin
    SHORTCUTS("Raccourcis rapides", "⚡", mapOf(SMALL to 120), SMALL),
```

`AppContainer` : `val torch = TorchController(appContext)`.

`BuiltinServices` : `val torch: TorchController? = null,` et `val shortcutActions: ShortcutActions = ShortcutActions(),`.

`BuiltinWidget`, au `when (kind)` :

```kotlin
        BuiltinKind.SHORTCUTS -> {
            val torchFlow = remember(services.torch) { services.torch?.state ?: kotlinx.coroutines.flow.flowOf(TorchState(false, false)) }
            val torch by torchFlow.collectAsStateWithLifecycle(TorchState(available = true, on = false))
            ShortcutsCard(torch, services.shortcutActions, modifier)
        }
```

`BuiltinPreview.SampleCard` :

```kotlin
        BuiltinKind.SHORTCUTS -> ShortcutsCard(TorchState(available = true, on = true), ShortcutActions(), modifier)
```

`AppRoot`, dans `BuiltinServices(...)` (imports `android.provider.Settings` déjà présent) :

```kotlin
        torch = container.torch,
        shortcutActions = ShortcutActions(
            toggleTorch = { on -> if (!container.torch.set(on)) toast("Lampe indisponible") },
            internet = { container.appLauncher.startSafely(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
            bluetooth = { container.appLauncher.startSafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            sound = { container.appLauncher.startSafely(Intent(Settings.Panel.ACTION_VOLUME)) },
            doNotDisturb = {
                container.appLauncher.startSafely(Intent("android.settings.ZEN_MODE_SETTINGS")) ||
                    container.appLauncher.startSafely(Intent(Settings.ACTION_SOUND_SETTINGS))
            },
        ),
```

`toast` est défini plus bas dans `AppRoot` : déplacer la construction de `BuiltinServices` après `fun toast(...)` si le compilateur le demande (elle est déjà après, juste avant `Box`).

- [ ] **Step 4: Run the tests**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest` puis la classe `app.lanceur.builtin.shortcuts.ShortcutsCardTest` sur le téléphone.
Expected: PASS (2 tests d'interface).

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Widget Raccourcis rapides (lampe torche et panneaux)"
```

---

### Task 9: Stockage et mémoire

**Files:**
- Modify: `BuiltinKind.kt` (`STORAGE`), `BuiltinWidget.kt`, `BuiltinPreview.kt`, `AppContainer.kt`, `AppRoot.kt`
- Create: `app/src/main/java/app/lanceur/builtin/storage/StorageInfo.kt`, `StorageSource.kt`, `StorageCard.kt`
- Test: `app/src/test/java/app/lanceur/builtin/storage/StorageInfoTest.kt`, `app/src/androidTest/java/app/lanceur/builtin/storage/StorageCardTest.kt`

**Interfaces:**
- Produces: `data class Gauge(usedBytes, totalBytes)` avec `fraction`, `warning` ; `data class StorageReading(storage: Gauge, memory: Gauge)` ; `StorageInfo.text(gauge, decimals)` ; `StorageSource(context) { fun read(): StorageReading; val readings: Flow<StorageReading> }` ; `@Composable StorageCard(reading, onClick, modifier)` ; `BuiltinServices.storage: StorageSource?`, `openStorageSettings`.

- [ ] **Step 1: Write the failing tests**

`StorageInfoTest.kt` :

```kotlin
package app.lanceur.builtin.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageInfoTest {
    @Test
    fun texts_in_gigabytes() {
        assertEquals("87 / 128 Go", StorageInfo.text(Gauge(87_000_000_000, 128_000_000_000), decimals = 0))
        assertEquals("7,1 / 12,0 Go", StorageInfo.text(Gauge(7_100_000_000, 12_000_000_000), decimals = 1))
    }

    @Test
    fun warning_above_ninety_percent() {
        assertTrue(Gauge(91, 100).warning)
        assertFalse(Gauge(90, 100).warning)
        assertEquals(0f, Gauge(5, 0).fraction, 0f)
    }
}
```

`StorageCardTest.kt` :

```kotlin
package app.lanceur.builtin.storage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StorageCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun shows_both_gauges_and_opens_settings() {
        var opened = false
        val reading = StorageReading(Gauge(87_000_000_000, 128_000_000_000), Gauge(7_100_000_000, 12_000_000_000))
        rule.setContent { MaterialTheme { Box(Modifier.height(140.dp)) { StorageCard(reading, onClick = { opened = true }) } } }
        rule.onNodeWithText("87 / 128 Go").assertIsDisplayed()
        rule.onNodeWithText("7,1 / 12,0 Go").assertIsDisplayed()
        rule.onNodeWithText("Mémoire vive").performClick()
        assertTrue(opened)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*StorageInfoTest*'`
Expected: FAIL at compilation, "Unresolved reference 'Gauge'".

- [ ] **Step 3: Model, source, card, kind, wiring**

`StorageInfo.kt` :

```kotlin
package app.lanceur.builtin.storage

import java.util.Locale

data class Gauge(val usedBytes: Long, val totalBytes: Long) {
    val fraction: Float get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val warning: Boolean get() = fraction > 0.9f
}

data class StorageReading(val storage: Gauge, val memory: Gauge)

object StorageInfo {
    /** En gigaoctets décimaux, comme les réglages d'Android : « 87 / 128 Go », « 7,1 / 12,0 Go ». */
    fun text(gauge: Gauge, decimals: Int): String = "${gb(gauge.usedBytes, decimals)} / ${gb(gauge.totalBytes, decimals)} Go"

    private fun gb(bytes: Long, decimals: Int) = String.format(Locale.FRENCH, "%.${decimals}f", bytes / 1_000_000_000.0)
}
```

`StorageSource.kt` :

```kotlin
package app.lanceur.builtin.storage

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Stockage interne (taille arrondie comme dans les réglages) et mémoire vive. Aucune permission. */
class StorageSource(private val context: Context) {
    /** Relu à l'affichage, puis toutes les 5 s tant qu'une carte collecte. */
    val readings: Flow<StorageReading> = flow {
        while (true) {
            emit(read())
            delay(5_000)
        }
    }.flowOn(Dispatchers.IO)

    fun read(): StorageReading {
        val stats = context.getSystemService(StorageStatsManager::class.java)
        val statFs by lazy { StatFs(Environment.getDataDirectory().path) }
        val total = runCatching { stats.getTotalBytes(StorageManager.UUID_DEFAULT) }.getOrElse { statFs.totalBytes }
        val free = runCatching { stats.getFreeBytes(StorageManager.UUID_DEFAULT) }.getOrElse { statFs.availableBytes }
        val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        return StorageReading(
            storage = Gauge(total - free, total),
            memory = Gauge(memory.totalMem - memory.availMem, memory.totalMem),
        )
    }
}
```

`StorageCard.kt` :

```kotlin
package app.lanceur.builtin.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground

@Composable
fun StorageCard(reading: StorageReading, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(cardBackground()).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CardLabel("Stockage")
        GaugeRow("Stockage", StorageInfo.text(reading.storage, decimals = 0), reading.storage)
        GaugeRow("Mémoire vive", StorageInfo.text(reading.memory, decimals = 1), reading.memory)
    }
}

@Composable
private fun GaugeRow(label: String, value: String, gauge: Gauge) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { gauge.fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp),
            color = if (gauge.warning) Color(0xFFF9AB00) else colors.primary,
        )
    }
}
```

`BuiltinKind`, après `SHORTCUTS(...)` :

```kotlin
    STORAGE("Stockage et mémoire", "💾", mapOf(SMALL to 140), SMALL),
```

`AppContainer` : `val storage = StorageSource(appContext)`.

`BuiltinServices` : `val storage: StorageSource? = null,` et `val openStorageSettings: () -> Unit = {},`.

`BuiltinWidget`, au `when (kind)` :

```kotlin
        BuiltinKind.STORAGE -> {
            val source = services.storage
            if (source != null) {
                val initial = remember { source.read() }
                val reading by source.readings.collectAsStateWithLifecycle(initial)
                StorageCard(reading, services.openStorageSettings, modifier)
            }
        }
```

`BuiltinPreview.SampleCard` :

```kotlin
        BuiltinKind.STORAGE -> StorageCard(
            StorageReading(Gauge(87_000_000_000, 128_000_000_000), Gauge(7_100_000_000, 12_000_000_000)),
            onClick = {},
            modifier = modifier,
        )
```

`AppRoot`, dans `BuiltinServices(...)` :

```kotlin
        storage = container.storage,
        openStorageSettings = { container.appLauncher.startSafely(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) },
```

- [ ] **Step 4: Run the tests**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest` puis la classe `app.lanceur.builtin.storage.StorageCardTest` sur le téléphone.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Widget Stockage et mémoire"
```

---

### Task 10: README, suites complètes, installation et vérification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: README**

Dans la section « Widgets intégrés » du README, remplacer la première phrase de la liste par la liste complète des treize
widgets, puis ajouter :

```markdown
- **Note rapide / To-do** : on écrit directement sur la carte ; chaque widget a son propre contenu, gardé dans les
  réglages de Lanceur (jamais envoyé ailleurs) et effacé quand on retire le widget.
- **Compte à rebours / Horloges du monde** : une feuille de réglages s'ouvre à l'ajout ; ⚙ en mode Modifier la rouvre.
- **Minuteur** : les pastilles lancent un minuteur de l'Horloge, qui sonne même Lanceur fermé.
- **Contacts favoris** : ceux marqués d'une étoile dans Contacts ; toucher appelle, appui long envoie un SMS.
- **Raccourcis** : la lampe s'allume depuis le widget ; Internet, Bluetooth, Son et Ne pas déranger ouvrent le
  panneau d'Android (une appli ne peut pas les changer elle-même).
```

- [ ] **Step 2: Suites complètes et release**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleRelease`
Expected: BUILD SUCCESSFUL, toutes les suites vertes (téléphone déverrouillé).

- [ ] **Step 3: Installer**

Run: `~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk`
Expected: `Success`.

- [ ] **Step 4: Vérification à la main (avec l'utilisateur)**

1. Sélecteur : les treize widgets avec miniatures.
2. Note : écrire, quitter, forcer l'arrêt de Lanceur, revenir : le texte est là.
3. To-do : ajouter, cocher, effacer.
4. Compte à rebours : feuille à l'ajout, annuler (rien ajouté), recommencer et enregistrer ; ⚙ en mode Modifier.
5. Horloges : ajouter Tokyo et New York.
6. Minuteur : « 1 min » sonne dans l'Horloge ; chrono : démarrer, quitter, revenir, le temps a continué.
7. Contacts favoris : appel et SMS.
8. Raccourcis : lampe torche ; chaque panneau.
9. Stockage : valeurs cohérentes avec les réglages.
10. `adb logcat -d -b crash | grep lanceur` : aucun plantage.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "README : widgets intégrés du lot 2"
```
