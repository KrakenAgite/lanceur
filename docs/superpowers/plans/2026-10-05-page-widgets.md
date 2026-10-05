# Page de widgets — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter à Lanceur une page à gauche de l'accueil, activable dans les réglages, avec un résumé du jour et les widgets Android de l'utilisateur empilés en pleine largeur.

**Architecture:** Les décisions sont prises dans du code Kotlin pur testé sur la JVM :
- `WidgetSlot` et son stockage, `VisibleWidgets`, `PickerCatalog`, `WidgetIds`, `HeightReorder` ;
- `WidgetAddFlow`, qui pilote l'ajout d'un widget en machine d'état ;
- `DaySummary`, pour le résumé du jour.

Des couches Android minces entourent cette logique : `WidgetHost`, `WidgetProviderSource`, `DaySummarySource`. Les écrans Compose restent sans état : `HomePager`, `WidgetPage`, `WidgetPicker`. `MainActivity` exécute les effets de l'ajout (écran d'autorisation, écran de configuration).

**Tech Stack:** Kotlin 2.4.20, AGP 9.3.3, Compose (BOM 2026.09.00, `HorizontalPager` de foundation), `AppWidgetHost`, DataStore 1.2.1, JUnit 4, kotlinx-coroutines-test 1.11.0, Compose UI Test (règle `v2`).

**Spec:** `docs/superpowers/specs/2026-10-05-page-widgets-design.md` (complète `docs/superpowers/specs/2026-10-05-lanceur-design.md`)

## Global Constraints

- Projet `/home/gabriel/Projets/lanceur`, branche `page-widgets` (créée depuis `double-toucher-veille`). Code sous `app/src/main/java/app/lanceur/`, tests JVM sous `app/src/test/java/app/lanceur/`, tests d'interface sous `app/src/androidTest/java/app/lanceur/`.
- Commandes Gradle : `JAVA_HOME=/opt/android-studio/jbr ./gradlew …`, lancées depuis `/home/gabriel/Projets/lanceur`.
- Tests d'interface : `connectedDebugAndroidTest` sur le Pixel 9. La version debug s'installe sous le paquet `app.lanceur.debug` et l'app reste installée après les tests (`leaveApksInstalledAfterRun`). Si `~/Android/Sdk/platform-tools/adb devices` ne liste aucun appareil, **arrête-toi et demande à l'utilisateur de reconnecter le téléphone**.
- Tests d'interface : importer `androidx.compose.ui.test.junit4.v2.createComposeRule` (la règle v1 est obsolète).
- `minSdk 35`, `compileSdk`/`targetSdk 37`, aucune nouvelle dépendance.
- Interdits : permission `INTERNET`, Hilt, bibliothèque de navigation.
- Textes affichés en français, écrits dans le code Kotlin.
- Les paquets `widgets/WidgetSlot.kt`, `VisibleWidgets.kt`, `PickerCatalog.kt`, `WidgetIds.kt`, `HeightReorder.kt`, `WidgetAddFlow.kt` et `summary/DaySummary.kt` n'importent rien de `android.*`.
- Vie privée : jamais de widget d'une appli cachée (règle paquet + profil, `VisibleApps.packageInProfile`) ni d'un profil `PRIVATE` ou `UNKNOWN`.
- Tailles des widgets : `SMALL` = 120 dp, `MEDIUM` = 220 dp, `LARGE` = 340 dp ; identifiant d'hôte `1024`.
- Page activée par défaut (`widgetPageEnabled = true`).
- Commits en français, terminés par la ligne `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Lanceur tué ou activité recréée pendant l'autorisation ou la configuration.** Ni widget fantôme ni identifiant perdu. Tests :
   - `WidgetAddFlowTest.result_without_pending_add_is_ignored` (tâche 4) ;
   - `WidgetAddFlowTest.new_add_while_one_is_pending_frees_the_old_id` (tâche 4) ;
   - le contrôle `info(id) != null` avant `Save` (tâche 8).
2. **Appli cachée après une mise à jour qui a renommé la classe de son widget.** Le widget reste invisible. Test : `VisibleWidgetsTest.hidden_package_stays_hidden_even_with_another_class` (tâche 3).
3. **Geste commencé sur l'alphabet.** Il reste à l'alphabet et ne fait pas tourner la page. Test : `HomePagerTest.a_gesture_started_on_the_alphabet_stays_with_the_alphabet` (tâche 7).
4. **Page désactivée alors qu'on est dessus, et bouton Accueil.** Retour sur l'accueil, sans plantage. Test : `HomePagerTest.going_home_and_disabling_bring_back_the_home_page` (tâche 7).
5. **Réordonner des widgets de hauteurs différentes.** Aucun widget sauté, perdu ou dupliqué. Test : `HeightReorderTest` (tâche 4).

---

## Structure des fichiers

```
app/src/main/java/app/lanceur/
  widgets/WidgetSlot.kt        WidgetSize, WidgetSlot (+ forme texte)
  widgets/VisibleWidgets.kt    WidgetCard, VisibleWidgets
  widgets/PickerCatalog.kt     ProviderEntry, PickerGroup, PickerCatalog
  widgets/WidgetIds.kt         identifiants orphelins
  widgets/HeightReorder.kt     déplacement par glisser avec hauteurs différentes
  widgets/WidgetAddFlow.kt     machine d'état de l'ajout
  widgets/WidgetHost.kt        hôte Android
  widgets/WidgetProviderSource.kt  widgets installés par profil
  widgets/HostedWidget.kt      AppWidgetHostView dans Compose
  widgets/WidgetPage.kt        page : résumé + widgets + mode édition
  widgets/WidgetPicker.kt      sélecteur
  summary/DaySummary.kt        résumé du jour (pur)
  summary/DaySummarySource.kt  agenda + alarme suivante
  apps/ProfileKinds.kt         type d'un profil (partagé avec LauncherAppsSource)
  home/HomePager.kt            deux pages autour de l'accueil
Modifiés : home/HomeScreen.kt (double toucher), prefs/LauncherPrefs.kt, prefs/PrefsRepo.kt, home/ListMode.kt,
  home/LauncherViewModel.kt, apps/LauncherAppsSource.kt, AppContainer.kt, LanceurApp.kt, AppRoot.kt, MainActivity.kt,
  settings/SettingsScreen.kt, README.md
```

---

### Task 1: Terminer le double toucher pour verrouiller

**Files:**
- Modify: `app/src/main/java/app/lanceur/home/HomeScreen.kt` (le `detectTapGestures` de la colonne principale)
- Test (déjà écrits, commit 6802efc) : `app/src/androidTest/java/app/lanceur/home/HomeScreenTest.kt`
  - `double_tapping_an_empty_area_locks_the_screen`
  - `double_tapping_an_app_does_not_lock`
  - `a_single_tap_on_an_empty_area_still_returns_to_favorites`

**Interfaces:**
- Consumes: `HomeActions.lockScreen: () -> Unit` (déjà présent, branché dans `AppRoot` sur `LockScreenService.lock()`).
- Produces: le double toucher sur une zone vide appelle `actions.lockScreen()`.

- [ ] **Step 1: Vérifier le téléphone et voir le test échouer**

Run: `~/Android/Sdk/platform-tools/adb devices && JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.home.HomeScreenTest`
Expected: un appareil listé, puis `double_tapping_an_empty_area_locks_the_screen` en échec avec `expected:<1> but was:<0>`. Les deux autres nouveaux tests passent.

- [ ] **Step 2: Brancher le double toucher**

Dans `app/src/main/java/app/lanceur/home/HomeScreen.kt`, remplace :

```kotlin
                        detectTapGestures(
                            onTap = { if (currentMode != ListMode.Favorites) act.changeMode(ListMode.Favorites) },
                            onLongPress = { act.openSettings() },
                        )
```

par :

```kotlin
                        detectTapGestures(
                            onTap = { if (currentMode != ListMode.Favorites) act.changeMode(ListMode.Favorites) },
                            onDoubleTap = { act.lockScreen() },
                            onLongPress = { act.openSettings() },
                        )
```

- [ ] **Step 3: Voir les tests passer**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.home.HomeScreenTest`
Expected: `BUILD SUCCESSFUL`, 7 tests réussis.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/lanceur/home/HomeScreen.kt
git commit -m "Double toucher sur une zone vide de l'accueil : mise en veille" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Modèle et stockage des widgets

**Files:**
- Create: `app/src/main/java/app/lanceur/widgets/WidgetSlot.kt`
- Modify: `app/src/main/java/app/lanceur/prefs/LauncherPrefs.kt`, `app/src/main/java/app/lanceur/prefs/PrefsRepo.kt`
- Test: `app/src/test/java/app/lanceur/widgets/WidgetSlotTest.kt`, `app/src/test/java/app/lanceur/prefs/PrefsRepoTest.kt` (ajouts)

**Interfaces:**
- Consumes: `AppKey` (`encode()`, `decode()`).
- Produces (paquet `app.lanceur.widgets`) :
  - `enum class WidgetSize(val heightDp: Int, val shortLabel: String) { SMALL(120, "S"), MEDIUM(220, "M"), LARGE(340, "L"); companion fun fromMinHeightDp(minHeightDp: Int): WidgetSize }`
  - `data class WidgetSlot(val appWidgetId: Int, val provider: AppKey, val size: WidgetSize) { fun encode(): String; companion fun decode(value: String): WidgetSlot? }`
  - `LauncherPrefs.widgetPageEnabled: Boolean = true`, `LauncherPrefs.widgets: List<WidgetSlot> = emptyList()`
  - `PrefsRepo` : `suspend fun addWidget(slot: WidgetSlot)`, `removeWidget(appWidgetId: Int)`, `setWidgetSize(appWidgetId: Int, size: WidgetSize)`, `setWidgetsOrder(ids: List<Int>)`, `setWidgetPageEnabled(enabled: Boolean)`

- [ ] **Step 1: Écrire les tests**

`app/src/test/java/app/lanceur/widgets/WidgetSlotTest.kt` :

```kotlin
package app.lanceur.widgets

import app.lanceur.apps.AppKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSlotTest {
    private val slot = WidgetSlot(7, AppKey("app.meteo", "app.meteo.Widget", 10), WidgetSize.LARGE)

    @Test
    fun encode_then_decode_gives_the_same_slot() {
        assertEquals("7|app.meteo/app.meteo.Widget#10|LARGE", slot.encode())
        assertEquals(slot, WidgetSlot.decode(slot.encode()))
    }

    @Test
    fun malformed_values_are_rejected() {
        listOf("", "x|a/b#0|SMALL", "1|pas-une-cle|SMALL", "1|a/b#0|ENORME", "1|a/b#0", "1|a/b#0|SMALL|en-trop").forEach {
            assertNull(it, WidgetSlot.decode(it))
        }
    }

    @Test
    fun starting_size_follows_the_widget_minimum_height() {
        assertEquals(WidgetSize.SMALL, WidgetSize.fromMinHeightDp(40))
        assertEquals(WidgetSize.SMALL, WidgetSize.fromMinHeightDp(120))
        assertEquals(WidgetSize.MEDIUM, WidgetSize.fromMinHeightDp(121))
        assertEquals(WidgetSize.MEDIUM, WidgetSize.fromMinHeightDp(220))
        assertEquals(WidgetSize.LARGE, WidgetSize.fromMinHeightDp(221))
    }
}
```

Dans `app/src/test/java/app/lanceur/prefs/PrefsRepoTest.kt`, ajoute ces imports :

```kotlin
import app.lanceur.apps.AppKey
import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSlot
import org.junit.Assert.assertFalse
```

et ajoute ces tests à la fin de la classe (avant la dernière accolade) :

```kotlin
    private fun slot(id: Int, size: WidgetSize = WidgetSize.MEDIUM) =
        WidgetSlot(id, AppKey("app.meteo", "app.meteo.Widget$id", 0), size)

    @Test
    fun widgets_keep_their_order_and_size_and_can_be_removed() = runTest {
        val repo = PrefsRepo(store())
        repo.addWidget(slot(1))
        repo.addWidget(slot(2, WidgetSize.SMALL))
        repo.addWidget(slot(1))
        assertEquals(listOf(slot(1), slot(2, WidgetSize.SMALL)), repo.prefs.first().widgets)

        repo.setWidgetSize(1, WidgetSize.LARGE)
        repo.setWidgetsOrder(listOf(2, 1))
        assertEquals(listOf(slot(2, WidgetSize.SMALL), slot(1, WidgetSize.LARGE)), repo.prefs.first().widgets)

        repo.removeWidget(2)
        assertEquals(listOf(slot(1, WidgetSize.LARGE)), repo.prefs.first().widgets)
    }

    @Test
    fun widget_page_is_enabled_by_default_and_can_be_disabled() = runTest {
        val repo = PrefsRepo(store())
        assertTrue(repo.prefs.first().widgetPageEnabled)
        repo.setWidgetPageEnabled(false)
        assertFalse(repo.prefs.first().widgetPageEnabled)
    }

    @Test
    fun malformed_or_duplicated_widget_lines_are_ignored() = runTest {
        val store = store()
        store.edit { it[stringPreferencesKey("widgets")] = "n'importe quoi\n" + slot(3).encode() + "\n" + slot(3).encode() }
        assertEquals(listOf(slot(3)), PrefsRepo(store).prefs.first().widgets)
    }
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests "app.lanceur.widgets.*" --tests "app.lanceur.prefs.PrefsRepoTest"`
Expected: échec de compilation, `Unresolved reference 'WidgetSlot'` et `'WidgetSize'`.

- [ ] **Step 3: Écrire le modèle et le stockage**

`app/src/main/java/app/lanceur/widgets/WidgetSlot.kt` :

```kotlin
package app.lanceur.widgets

import app.lanceur.apps.AppKey

enum class WidgetSize(val heightDp: Int, val shortLabel: String) {
    SMALL(120, "S"),
    MEDIUM(220, "M"),
    LARGE(340, "L"),
    ;

    companion object {
        /** Taille de départ d'après la hauteur minimale prévue par le widget. */
        fun fromMinHeightDp(minHeightDp: Int): WidgetSize = when {
            minHeightDp <= SMALL.heightDp -> SMALL
            minHeightDp <= MEDIUM.heightDp -> MEDIUM
            else -> LARGE
        }
    }
}

/** Un widget posé sur la page. `provider` : paquet et classe du fournisseur, et profil (même forme qu'une appli). */
data class WidgetSlot(val appWidgetId: Int, val provider: AppKey, val size: WidgetSize) {
    /** Forme texte : `id|paquet/classe#série|TAILLE`. */
    fun encode(): String = "$appWidgetId|${provider.encode()}|${size.name}"

    companion object {
        fun decode(value: String): WidgetSlot? {
            val parts = value.split('|')
            if (parts.size != 3) return null
            val id = parts[0].toIntOrNull() ?: return null
            val provider = AppKey.decode(parts[1]) ?: return null
            val size = WidgetSize.entries.firstOrNull { it.name == parts[2] } ?: return null
            return WidgetSlot(id, provider, size)
        }
    }
}
```

`app/src/main/java/app/lanceur/prefs/LauncherPrefs.kt` (remplace tout le contenu) :

```kotlin
package app.lanceur.prefs

import app.lanceur.apps.AppKey
import app.lanceur.widgets.WidgetSlot

enum class AlphabetSide { RIGHT, LEFT }

data class LauncherPrefs(
    val favorites: List<AppKey> = emptyList(),
    val hidden: Set<AppKey> = emptySet(),
    val alphabetSide: AlphabetSide = AlphabetSide.RIGHT,
    val permissionHintDismissed: Boolean = false,
    val widgetPageEnabled: Boolean = true,
    /** Dans l'ordre d'affichage sur la page de widgets. */
    val widgets: List<WidgetSlot> = emptyList(),
)
```

Dans `app/src/main/java/app/lanceur/prefs/PrefsRepo.kt` :

1. Ajoute ces imports :

```kotlin
import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSlot
```

2. Ajoute ces fonctions juste avant `suspend fun prune(catalog: List<AppEntry>)` :

```kotlin
    suspend fun addWidget(slot: WidgetSlot) = update { prefs ->
        if (prefs.widgets.any { it.appWidgetId == slot.appWidgetId }) prefs else prefs.copy(widgets = prefs.widgets + slot)
    }

    suspend fun removeWidget(appWidgetId: Int) = update { prefs ->
        prefs.copy(widgets = prefs.widgets.filterNot { it.appWidgetId == appWidgetId })
    }

    suspend fun setWidgetSize(appWidgetId: Int, size: WidgetSize) = update { prefs ->
        prefs.copy(widgets = prefs.widgets.map { if (it.appWidgetId == appWidgetId) it.copy(size = size) else it })
    }

    /** Réordonne selon `ids` ; un widget absent de `ids` reste à la fin, dans son ordre. */
    suspend fun setWidgetsOrder(ids: List<Int>) = update { prefs ->
        val byId = prefs.widgets.associateBy { it.appWidgetId }
        val ordered = ids.distinct().mapNotNull { byId[it] }
        prefs.copy(widgets = ordered + prefs.widgets.filter { it.appWidgetId !in ids })
    }

    suspend fun setWidgetPageEnabled(enabled: Boolean) = update { it.copy(widgetPageEnabled = enabled) }
```

3. Dans le `companion object`, ajoute ces clés sous `HINT_DISMISSED` :

```kotlin
        val WIDGETS = stringPreferencesKey("widgets")
        val WIDGET_PAGE = booleanPreferencesKey("widget_page_enabled")
```

4. Dans `decode`, remplace la ligne `permissionHintDismissed = stored[HINT_DISMISSED] ?: false,` par :

```kotlin
            permissionHintDismissed = stored[HINT_DISMISSED] ?: false,
            widgetPageEnabled = stored[WIDGET_PAGE] ?: true,
            widgets = stored[WIDGETS].orEmpty().split('\n').mapNotNull(WidgetSlot::decode).distinctBy { it.appWidgetId },
```

5. Dans `encode`, ajoute à la fin :

```kotlin
            out[WIDGET_PAGE] = prefs.widgetPageEnabled
            out[WIDGETS] = prefs.widgets.joinToString("\n") { it.encode() }
```

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils passent**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 90 tests réussis (84 existants + 3 `WidgetSlotTest` + 3 `PrefsRepoTest`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lanceur/widgets/WidgetSlot.kt app/src/main/java/app/lanceur/prefs app/src/test/java/app/lanceur/widgets app/src/test/java/app/lanceur/prefs/PrefsRepoTest.kt
git commit -m "Widgets : modèle et enregistrement (ordre, taille, interrupteur de la page)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Widgets visibles, sélecteur et identifiants orphelins (logique pure)

**Files:**
- Create: `app/src/main/java/app/lanceur/widgets/VisibleWidgets.kt`, `widgets/PickerCatalog.kt`, `widgets/WidgetIds.kt`
- Test: `app/src/test/java/app/lanceur/widgets/VisibleWidgetsTest.kt`, `PickerCatalogTest.kt`, `WidgetIdsTest.kt`

**Interfaces:**
- Consumes: `WidgetSlot`, `WidgetSize` (tâche 2), `AppKey`, `ProfileKind` (`app.lanceur.apps`), `VisibleApps.packageInProfile` (extension membre de `object VisibleApps`, à utiliser via `with(VisibleApps) { … }`), `TextNormalizer.fold`.
- Produces (paquet `app.lanceur.widgets`) :
  - `sealed interface WidgetCard { val slot: WidgetSlot; data class Live(override val slot: WidgetSlot); data class Unavailable(override val slot: WidgetSlot) }`
  - `object VisibleWidgets { fun compute(slots: List<WidgetSlot>, hidden: Set<AppKey>, available: Set<Int>): List<WidgetCard> }`
  - `data class ProviderEntry(val provider: AppKey, val profileKind: ProfileKind, val appLabel: String, val widgetLabel: String, val minHeightDp: Int)`
  - `data class PickerGroup(val appLabel: String, val packageName: String, val isWork: Boolean, val entries: List<ProviderEntry>)`
  - `object PickerCatalog { fun build(entries: List<ProviderEntry>, hidden: Set<AppKey>, query: String): List<PickerGroup> }`
  - `object WidgetIds { fun orphans(hostIds: IntArray, slots: List<WidgetSlot>): List<Int> }`

- [ ] **Step 1: Écrire les tests**

`app/src/test/java/app/lanceur/widgets/VisibleWidgetsTest.kt` :

```kotlin
package app.lanceur.widgets

import app.lanceur.apps.AppKey
import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleWidgetsTest {
    private val meteo = WidgetSlot(1, AppKey("app.meteo", "app.meteo.Widget", 0), WidgetSize.MEDIUM)
    private val musique = WidgetSlot(2, AppKey("app.musique", "app.musique.Player", 0), WidgetSize.SMALL)
    private val agenda = WidgetSlot(3, AppKey("app.agenda", "app.agenda.Month", 0), WidgetSize.LARGE)

    @Test
    fun available_widgets_are_live_in_their_order() {
        val cards = VisibleWidgets.compute(listOf(agenda, meteo), hidden = emptySet(), available = setOf(1, 3))
        assertEquals(listOf(WidgetCard.Live(agenda), WidgetCard.Live(meteo)), cards)
    }

    @Test
    fun unknown_widgets_are_marked_unavailable() {
        val cards = VisibleWidgets.compute(listOf(meteo, musique), hidden = emptySet(), available = setOf(1))
        assertEquals(listOf(WidgetCard.Live(meteo), WidgetCard.Unavailable(musique)), cards)
    }

    @Test
    fun hidden_package_stays_hidden_even_with_another_class() {
        // L'appli est cachée sous son activité principale ; son widget a une autre classe dans le même paquet
        val hiddenApp = AppKey("app.musique", "app.musique.MainActivity", 0)
        val cards = VisibleWidgets.compute(listOf(meteo, musique), hidden = setOf(hiddenApp), available = setOf(1, 2))
        assertEquals(listOf(WidgetCard.Live(meteo)), cards)
    }

    @Test
    fun hiding_is_per_profile() {
        val workMusique = musique.copy(appWidgetId = 4, provider = musique.provider.copy(userSerial = 10))
        val hiddenApp = AppKey("app.musique", "app.musique.MainActivity", 0)
        val cards = VisibleWidgets.compute(listOf(musique, workMusique), hidden = setOf(hiddenApp), available = setOf(2, 4))
        assertEquals(listOf(WidgetCard.Live(workMusique)), cards)
    }
}
```

`app/src/test/java/app/lanceur/widgets/PickerCatalogTest.kt` :

```kotlin
package app.lanceur.widgets

import app.lanceur.apps.AppKey
import app.lanceur.apps.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerCatalogTest {
    private fun entry(pkg: String, cls: String, app: String, widget: String, kind: ProfileKind = ProfileKind.MAIN, serial: Long = 0) =
        ProviderEntry(AppKey(pkg, cls, serial), kind, app, widget, 110)

    private val meteoJour = entry("app.meteo", "Jour", "Météo", "Prévisions du jour")
    private val meteoSemaine = entry("app.meteo", "Semaine", "Météo", "Semaine")
    private val agenda = entry("app.agenda", "Mois", "Agenda", "Mois")
    private val editions = entry("app.editions", "Une", "Éditions", "À la une")
    private val coffre = entry("app.coffre", "W", "Coffre", "Solde", ProfileKind.PRIVATE, 11)
    private val inconnu = entry("app.inconnu", "W", "Inconnu", "Widget", ProfileKind.UNKNOWN, 12)
    private val all = listOf(meteoSemaine, editions, coffre, agenda, inconnu, meteoJour)

    @Test
    fun groups_by_app_sorted_in_french_without_private_or_unknown_profiles() {
        val groups = PickerCatalog.build(all, hidden = emptySet(), query = "")
        assertEquals(listOf("Agenda", "Éditions", "Météo"), groups.map { it.appLabel })
        assertEquals(listOf("Prévisions du jour", "Semaine"), groups.last().entries.map { it.widgetLabel })
    }

    @Test
    fun hidden_apps_offer_no_widget() {
        val hiddenApp = AppKey("app.meteo", "app.meteo.MainActivity", 0)
        val groups = PickerCatalog.build(all, hidden = setOf(hiddenApp), query = "")
        assertEquals(listOf("Agenda", "Éditions"), groups.map { it.appLabel })
    }

    @Test
    fun filter_matches_app_or_widget_names_without_accents() {
        assertEquals(listOf("Météo"), PickerCatalog.build(all, emptySet(), "meteo").map { it.appLabel })
        assertEquals(listOf("Éditions"), PickerCatalog.build(all, emptySet(), "a la UNE").map { it.appLabel })
        assertTrue(PickerCatalog.build(all, emptySet(), "zzz").isEmpty())
    }

    @Test
    fun the_same_app_in_the_work_profile_is_a_separate_group() {
        val workAgenda = entry("app.agenda", "Mois", "Agenda", "Mois", ProfileKind.OTHER, 10)
        val groups = PickerCatalog.build(listOf(agenda, workAgenda), emptySet(), "")
        assertEquals(listOf(false, true), groups.map { it.isWork })
    }
}
```

`app/src/test/java/app/lanceur/widgets/WidgetIdsTest.kt` :

```kotlin
package app.lanceur.widgets

import app.lanceur.apps.AppKey
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetIdsTest {
    @Test
    fun ids_reserved_but_not_saved_are_orphans() {
        val saved = listOf(WidgetSlot(2, AppKey("a", "a.W", 0), WidgetSize.SMALL))
        assertEquals(listOf(1, 3), WidgetIds.orphans(intArrayOf(1, 2, 3), saved))
        assertEquals(emptyList<Int>(), WidgetIds.orphans(intArrayOf(2), saved))
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests "app.lanceur.widgets.*"`
Expected: échec de compilation, `Unresolved reference 'VisibleWidgets'`, `'WidgetCard'`, `'PickerCatalog'`, `'ProviderEntry'`, `'WidgetIds'`.

- [ ] **Step 3: Écrire le code**

`app/src/main/java/app/lanceur/widgets/VisibleWidgets.kt` :

```kotlin
package app.lanceur.widgets

import app.lanceur.apps.AppKey
import app.lanceur.prefs.VisibleApps

sealed interface WidgetCard {
    val slot: WidgetSlot

    data class Live(override val slot: WidgetSlot) : WidgetCard

    /** Android ne connaît plus ce widget (appli désinstallée, fournisseur supprimé) : l'utilisateur peut le retirer. */
    data class Unavailable(override val slot: WidgetSlot) : WidgetCard
}

object VisibleWidgets {
    /**
     * `available` : identifiants qu'Android sait encore afficher. Le widget d'un paquet caché dans son profil
     * n'apparaît pas du tout : aucune vue n'est créée pour lui.
     */
    fun compute(slots: List<WidgetSlot>, hidden: Set<AppKey>, available: Set<Int>): List<WidgetCard> = with(VisibleApps) {
        val hiddenPackages = hidden.mapTo(HashSet()) { it.packageInProfile() }
        slots.filter { it.provider.packageInProfile() !in hiddenPackages }
            .map { if (it.appWidgetId in available) WidgetCard.Live(it) else WidgetCard.Unavailable(it) }
    }
}
```

`app/src/main/java/app/lanceur/widgets/PickerCatalog.kt` :

```kotlin
package app.lanceur.widgets

import app.lanceur.apps.AppKey
import app.lanceur.apps.ProfileKind
import app.lanceur.prefs.VisibleApps
import app.lanceur.text.TextNormalizer
import java.text.Collator
import java.util.Locale

/** Un widget installé, tel que le sélecteur le présente. */
data class ProviderEntry(
    val provider: AppKey,
    val profileKind: ProfileKind,
    val appLabel: String,
    val widgetLabel: String,
    val minHeightDp: Int,
)

data class PickerGroup(val appLabel: String, val packageName: String, val isWork: Boolean, val entries: List<ProviderEntry>)

object PickerCatalog {
    private val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }

    /** Jamais l'Espace privé ni un profil de type inconnu, jamais une appli cachée. */
    fun build(entries: List<ProviderEntry>, hidden: Set<AppKey>, query: String): List<PickerGroup> = with(VisibleApps) {
        val hiddenPackages = hidden.mapTo(HashSet()) { it.packageInProfile() }
        val q = TextNormalizer.fold(query.trim())
        entries
            .filter { it.profileKind == ProfileKind.MAIN || it.profileKind == ProfileKind.OTHER }
            .filter { it.provider.packageInProfile() !in hiddenPackages }
            .filter { q.isEmpty() || TextNormalizer.fold(it.appLabel).contains(q) || TextNormalizer.fold(it.widgetLabel).contains(q) }
            .groupBy { it.provider.packageInProfile() }
            .values
            .map { group ->
                val first = group.first()
                PickerGroup(
                    appLabel = first.appLabel,
                    packageName = first.provider.packageName,
                    isWork = first.profileKind == ProfileKind.OTHER,
                    entries = group.sortedWith(compareBy(collator) { it.widgetLabel }),
                )
            }
            .sortedWith(compareBy<PickerGroup, String>(collator) { it.appLabel }.thenBy { it.isWork })
    }
}
```

`app/src/main/java/app/lanceur/widgets/WidgetIds.kt` :

```kotlin
package app.lanceur.widgets

object WidgetIds {
    /** Identifiants réservés auprès d'Android mais absents des réglages (ajout interrompu) : à libérer. */
    fun orphans(hostIds: IntArray, slots: List<WidgetSlot>): List<Int> {
        val kept = slots.mapTo(HashSet()) { it.appWidgetId }
        return hostIds.filter { it !in kept }
    }
}
```

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils passent**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests "app.lanceur.widgets.*"`
Expected: `BUILD SUCCESSFUL`, 12 tests réussis (3 `WidgetSlotTest` + 4 `VisibleWidgetsTest` + 4 `PickerCatalogTest` + 1 `WidgetIdsTest`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lanceur/widgets/VisibleWidgets.kt app/src/main/java/app/lanceur/widgets/PickerCatalog.kt app/src/main/java/app/lanceur/widgets/WidgetIds.kt app/src/test/java/app/lanceur/widgets
git commit -m "Widgets : filtrage vie privée, contenu du sélecteur, identifiants orphelins" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Réordonnancement à hauteurs variables et machine d'état de l'ajout

**Files:**
- Create: `app/src/main/java/app/lanceur/widgets/HeightReorder.kt`, `widgets/WidgetAddFlow.kt`
- Test: `app/src/test/java/app/lanceur/widgets/HeightReorderTest.kt`, `WidgetAddFlowTest.kt`

**Interfaces:**
- Consumes: `WidgetSlot`, `WidgetSize`, `ProviderEntry`, `AppKey`, `ProfileKind`.
- Produces (paquet `app.lanceur.widgets`) :
  - `object HeightReorder { data class Result(val from: Int, val to: Int, val offset: Float); fun step(heights: List<Float>, index: Int, offset: Float): Result }`
  - `object WidgetAddFlow` avec :
    - `sealed interface State { data object Idle; data class Binding(val id: Int, val entry: ProviderEntry); data class Configuring(val id: Int, val entry: ProviderEntry) }` ;
    - `sealed interface Event { data class Start(val id: Int, val entry: ProviderEntry, val bound: Boolean, val needsConfiguration: Boolean); data class BindResult(val granted: Boolean, val needsConfiguration: Boolean); data class ConfigureResult(val ok: Boolean) }` ;
    - `sealed interface Effect { data class LaunchBind(val id: Int, val provider: AppKey); data class LaunchConfigure(val id: Int); data class Save(val slot: WidgetSlot); data class Delete(val id: Int) }` ;
    - `fun reduce(state: State, event: Event): Pair<State, List<Effect>>`.

- [ ] **Step 1: Écrire les tests**

`app/src/test/java/app/lanceur/widgets/HeightReorderTest.kt` :

```kotlin
package app.lanceur.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class HeightReorderTest {
    private val heights = listOf(100f, 200f, 300f)

    @Test
    fun stays_until_half_of_the_neighbour_is_passed() {
        assertEquals(HeightReorder.Result(0, 0, 90f), HeightReorder.step(heights, 0, 90f))
        assertEquals(HeightReorder.Result(0, 1, -50f), HeightReorder.step(heights, 0, 150f))
    }

    @Test
    fun a_long_drag_crosses_several_neighbours_of_different_heights() {
        assertEquals(HeightReorder.Result(0, 2, -140f), HeightReorder.step(heights, 0, 360f))
    }

    @Test
    fun dragging_up_uses_the_neighbour_above() {
        assertEquals(HeightReorder.Result(2, 1, 80f), HeightReorder.step(heights, 2, -120f))
        assertEquals(HeightReorder.Result(2, 2, -90f), HeightReorder.step(heights, 2, -90f))
    }

    @Test
    fun never_leaves_the_list() {
        assertEquals(HeightReorder.Result(2, 2, 999f), HeightReorder.step(heights, 2, 999f))
        assertEquals(HeightReorder.Result(0, 0, -999f), HeightReorder.step(heights, 0, -999f))
    }
}
```

`app/src/test/java/app/lanceur/widgets/WidgetAddFlowTest.kt` :

```kotlin
package app.lanceur.widgets

import app.lanceur.apps.AppKey
import app.lanceur.apps.ProfileKind
import app.lanceur.widgets.WidgetAddFlow.Effect
import app.lanceur.widgets.WidgetAddFlow.Event
import app.lanceur.widgets.WidgetAddFlow.State
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAddFlowTest {
    private val meteo = ProviderEntry(AppKey("app.meteo", "app.meteo.Widget", 0), ProfileKind.MAIN, "Météo", "Jour", 200)
    private val saved = Effect.Save(WidgetSlot(7, meteo.provider, WidgetSize.MEDIUM))

    @Test
    fun bound_without_configuration_is_saved_right_away() {
        assertEquals(State.Idle to listOf(saved), WidgetAddFlow.reduce(State.Idle, Event.Start(7, meteo, bound = true, needsConfiguration = false)))
    }

    @Test
    fun refused_permission_frees_the_id() {
        val (binding, effects) = WidgetAddFlow.reduce(State.Idle, Event.Start(7, meteo, bound = false, needsConfiguration = false))
        assertEquals(State.Binding(7, meteo), binding)
        assertEquals(listOf(Effect.LaunchBind(7, meteo.provider)), effects)
        assertEquals(State.Idle to listOf(Effect.Delete(7)), WidgetAddFlow.reduce(binding, Event.BindResult(granted = false, needsConfiguration = false)))
    }

    @Test
    fun granted_then_configured_is_saved() {
        val (binding, _) = WidgetAddFlow.reduce(State.Idle, Event.Start(7, meteo, bound = false, needsConfiguration = false))
        val (configuring, effects) = WidgetAddFlow.reduce(binding, Event.BindResult(granted = true, needsConfiguration = true))
        assertEquals(State.Configuring(7, meteo), configuring)
        assertEquals(listOf(Effect.LaunchConfigure(7)), effects)
        assertEquals(State.Idle to listOf(saved), WidgetAddFlow.reduce(configuring, Event.ConfigureResult(ok = true)))
    }

    @Test
    fun cancelled_configuration_frees_the_id() {
        val (configuring, _) = WidgetAddFlow.reduce(State.Idle, Event.Start(7, meteo, bound = true, needsConfiguration = true))
        assertEquals(State.Idle to listOf(Effect.Delete(7)), WidgetAddFlow.reduce(configuring, Event.ConfigureResult(ok = false)))
    }

    @Test
    fun result_without_pending_add_is_ignored() {
        // Lanceur a été relancé entre-temps : l'identifiant sera libéré par le nettoyage des orphelins
        assertEquals(State.Idle to emptyList<Effect>(), WidgetAddFlow.reduce(State.Idle, Event.ConfigureResult(ok = true)))
        assertEquals(State.Idle to emptyList<Effect>(), WidgetAddFlow.reduce(State.Idle, Event.BindResult(granted = true, needsConfiguration = false)))
    }

    @Test
    fun new_add_while_one_is_pending_frees_the_old_id() {
        val other = meteo.copy(minHeightDp = 80)
        val (state, effects) = WidgetAddFlow.reduce(State.Binding(7, meteo), Event.Start(8, other, bound = true, needsConfiguration = false))
        assertEquals(State.Idle, state)
        assertEquals(listOf(Effect.Delete(7), Effect.Save(WidgetSlot(8, other.provider, WidgetSize.SMALL))), effects)
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests "app.lanceur.widgets.HeightReorderTest" --tests "app.lanceur.widgets.WidgetAddFlowTest"`
Expected: échec de compilation, `Unresolved reference 'HeightReorder'` et `'WidgetAddFlow'`.

- [ ] **Step 3: Écrire le code**

`app/src/main/java/app/lanceur/widgets/HeightReorder.kt` :

```kotlin
package app.lanceur.widgets

/** Glisser-déposer quand les éléments n'ont pas tous la même hauteur. */
object HeightReorder {
    data class Result(val from: Int, val to: Int, val offset: Float)

    /**
     * L'élément `index` a été tiré de `offset` pixels (positif vers le bas). Il prend la place d'un voisin dès qu'il
     * en dépasse la moitié ; `offset` restant = décalage à afficher par rapport à sa nouvelle place.
     */
    fun step(heights: List<Float>, index: Int, offset: Float): Result {
        var to = index
        var rest = offset
        // Le sens est décidé une fois : en descendant, les voisins franchis sont index+1, index+2… (et inversement)
        if (offset > 0) {
            while (to < heights.lastIndex && rest > heights[to + 1] / 2) {
                rest -= heights[to + 1]
                to++
            }
        } else if (offset < 0) {
            while (to > 0 && -rest > heights[to - 1] / 2) {
                rest += heights[to - 1]
                to--
            }
        }
        return Result(index, to, rest)
    }
}
```

> Note : `heights` est la liste des hauteurs **dans l'ordre courant**, l'élément déplacé compris. Les voisins franchis sont lus dans cet ordre (`index + 1`, `index + 2`… en descendant). Il ne faut surtout pas enchaîner une remontée après une descente dans le même appel : le voisin « au-dessus » aurait changé. L'écran rappelle `step` avec la nouvelle liste à chaque mouvement du doigt.

`app/src/main/java/app/lanceur/widgets/WidgetAddFlow.kt` :

```kotlin
package app.lanceur.widgets

import app.lanceur.apps.AppKey

/**
 * Ajout d'un widget : autorisation d'Android, puis configuration éventuelle. Toute sortie anticipée libère
 * l'identifiant réservé, pour ne jamais laisser de widget fantôme. Les effets sont exécutés par `MainActivity`.
 */
object WidgetAddFlow {
    sealed interface State {
        data object Idle : State
        data class Binding(val id: Int, val entry: ProviderEntry) : State
        data class Configuring(val id: Int, val entry: ProviderEntry) : State
    }

    sealed interface Event {
        /** `bound` : lié sans demander ; `needsConfiguration` n'a de sens que si `bound`. */
        data class Start(val id: Int, val entry: ProviderEntry, val bound: Boolean, val needsConfiguration: Boolean) : Event
        data class BindResult(val granted: Boolean, val needsConfiguration: Boolean) : Event
        data class ConfigureResult(val ok: Boolean) : Event
    }

    sealed interface Effect {
        data class LaunchBind(val id: Int, val provider: AppKey) : Effect
        data class LaunchConfigure(val id: Int) : Effect
        data class Save(val slot: WidgetSlot) : Effect
        data class Delete(val id: Int) : Effect
    }

    fun reduce(state: State, event: Event): Pair<State, List<Effect>> = when (event) {
        is Event.Start -> {
            val abandoned = abandon(state)
            when {
                !event.bound -> State.Binding(event.id, event.entry) to abandoned + Effect.LaunchBind(event.id, event.entry.provider)
                event.needsConfiguration -> State.Configuring(event.id, event.entry) to abandoned + Effect.LaunchConfigure(event.id)
                else -> State.Idle to abandoned + save(event.id, event.entry)
            }
        }
        is Event.BindResult -> when {
            state !is State.Binding -> state to emptyList()
            !event.granted -> State.Idle to listOf(Effect.Delete(state.id))
            event.needsConfiguration -> State.Configuring(state.id, state.entry) to listOf(Effect.LaunchConfigure(state.id))
            else -> State.Idle to listOf(save(state.id, state.entry))
        }
        is Event.ConfigureResult -> when {
            state !is State.Configuring -> state to emptyList()
            event.ok -> State.Idle to listOf(save(state.id, state.entry))
            else -> State.Idle to listOf(Effect.Delete(state.id))
        }
    }

    private fun abandon(state: State): List<Effect> = when (state) {
        is State.Binding -> listOf(Effect.Delete(state.id))
        is State.Configuring -> listOf(Effect.Delete(state.id))
        State.Idle -> emptyList()
    }

    private fun save(id: Int, entry: ProviderEntry) =
        Effect.Save(WidgetSlot(id, entry.provider, WidgetSize.fromMinHeightDp(entry.minHeightDp)))
}
```

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils passent**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 109 tests réussis (90 + 9 de la tâche 3 hors `WidgetSlotTest` + 4 `HeightReorderTest` + 6 `WidgetAddFlowTest`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lanceur/widgets/HeightReorder.kt app/src/main/java/app/lanceur/widgets/WidgetAddFlow.kt app/src/test/java/app/lanceur/widgets/HeightReorderTest.kt app/src/test/java/app/lanceur/widgets/WidgetAddFlowTest.kt
git commit -m "Widgets : glisser-déposer à hauteurs variables et machine d'état de l'ajout" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Résumé du jour (logique pure)

**Files:**
- Create: `app/src/main/java/app/lanceur/summary/DaySummary.kt`
- Test: `app/src/test/java/app/lanceur/summary/DaySummaryTest.kt`

**Interfaces:**
- Consumes: rien.
- Produces (paquet `app.lanceur.summary`) :
  - `data class SummaryEvent(val eventId: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean)`
  - `data class SummaryLine(val event: SummaryEvent, val text: String)`
  - `data class DaySummaryState(val dateLabel: String, val events: List<SummaryLine>, val alarmLabel: String?, val calendarGranted: Boolean)`
  - `object DaySummary { const val MAX_EVENTS = 3; fun build(now: ZonedDateTime, events: List<SummaryEvent>, nextAlarmMillis: Long?, calendarGranted: Boolean): DaySummaryState; fun alarmLabel(millis: Long, now: ZonedDateTime): String }`

- [ ] **Step 1: Écrire les tests**

`app/src/test/java/app/lanceur/summary/DaySummaryTest.kt` :

```kotlin
package app.lanceur.summary

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DaySummaryTest {
    private val paris = ZoneId.of("Europe/Paris")

    // Lundi 5 octobre 2026, 10:00 à Paris
    private val now = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, paris)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, paris).toInstant().toEpochMilli()

    private fun utcMidnight(day: Int) = LocalDate.of(2026, 10, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun timed(id: Long, title: String, day: Int, hour: Int, minute: Int = 0, hours: Int = 1) =
        SummaryEvent(id, title, at(day, hour, minute), at(day, hour + hours, minute), allDay = false)

    private fun allDay(id: Long, title: String, day: Int) = SummaryEvent(id, title, utcMidnight(day), utcMidnight(day + 1), allDay = true)

    @Test
    fun keeps_three_upcoming_events_of_today_and_tomorrow() {
        val events = listOf(
            timed(1, "Petit-déjeuner", 5, 8), // terminé
            timed(2, "Dentiste", 5, 14),
            timed(3, "Train", 6, 9, 30),
            allDay(4, "Anniversaire", 6),
            timed(5, "Sport", 5, 18),
            timed(6, "Plus tard", 7, 9), // après-demain
        )
        val summary = DaySummary.build(now, events, nextAlarmMillis = null, calendarGranted = true)
        assertEquals(listOf("14:00 Dentiste", "18:00 Sport", "Demain Anniversaire"), summary.events.map { it.text })
        assertEquals(listOf(2L, 5L, 4L), summary.events.map { it.event.eventId })
    }

    @Test
    fun an_ongoing_event_is_still_shown() {
        val summary = DaySummary.build(now, listOf(timed(1, "Réunion", 5, 9, hours = 2)), null, true)
        assertEquals(listOf("09:00 Réunion"), summary.events.map { it.text })
    }

    @Test
    fun today_all_day_event_has_no_time_and_untitled_events_get_a_title() {
        val summary = DaySummary.build(now, listOf(allDay(1, "Férié", 5), timed(2, " ", 5, 15)), null, true)
        assertEquals(listOf("Férié", "15:00 (Sans titre)"), summary.events.map { it.text })
    }

    @Test
    fun date_and_alarm_labels() {
        assertEquals("Lundi 5 octobre", DaySummary.build(now, emptyList(), null, true).dateLabel)
        assertEquals("Aujourd'hui 22:00", DaySummary.alarmLabel(at(5, 22), now))
        assertEquals("Demain 07:00", DaySummary.alarmLabel(at(6, 7), now))
        assertEquals("lun. 12 oct. 07:00", DaySummary.alarmLabel(at(12, 7), now))
        assertNull(DaySummary.build(now, emptyList(), null, true).alarmLabel)
    }

    @Test
    fun without_calendar_permission_no_event_is_listed() {
        val summary = DaySummary.build(now, listOf(timed(2, "Dentiste", 5, 14)), at(6, 7), calendarGranted = false)
        assertFalse(summary.calendarGranted)
        assertTrue(summary.events.isEmpty())
        assertEquals("Demain 07:00", summary.alarmLabel)
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests "app.lanceur.summary.*"`
Expected: échec de compilation, `Unresolved reference 'SummaryEvent'` et `'DaySummary'`.

- [ ] **Step 3: Écrire le code**

`app/src/main/java/app/lanceur/summary/DaySummary.kt` :

```kotlin
package app.lanceur.summary

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SummaryEvent(val eventId: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean)

data class SummaryLine(val event: SummaryEvent, val text: String)

data class DaySummaryState(
    val dateLabel: String,
    val events: List<SummaryLine>,
    val alarmLabel: String?,
    val calendarGranted: Boolean,
)

object DaySummary {
    const val MAX_EVENTS = 3

    private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
    private val OTHER_DAY = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.FRENCH)

    fun build(now: ZonedDateTime, events: List<SummaryEvent>, nextAlarmMillis: Long?, calendarGranted: Boolean): DaySummaryState {
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)
        val nowMillis = now.toInstant().toEpochMilli()
        val lines = if (!calendarGranted) {
            emptyList()
        } else {
            events.mapNotNull { event ->
                val startDay: LocalDate
                val finished: Boolean
                if (event.allDay) {
                    // Les événements « toute la journée » sont enregistrés en UTC, de minuit à minuit (fin exclue)
                    startDay = utcDate(event.begin)
                    finished = !utcDate(event.end).isAfter(today)
                } else {
                    startDay = Instant.ofEpochMilli(event.begin).atZone(now.zone).toLocalDate()
                    finished = event.end <= nowMillis
                }
                val day = maxOf(startDay, today)
                if (finished || day.isAfter(tomorrow)) null else day to event
            }
                .sortedWith(compareBy<Pair<LocalDate, SummaryEvent>>({ it.first }, { if (it.second.allDay) 0 else 1 }, { it.second.begin }))
                .take(MAX_EVENTS)
                .map { (day, event) -> SummaryLine(event, text(day == tomorrow, event, now.zone)) }
        }
        return DaySummaryState(
            dateLabel = DATE.format(now).replaceFirstChar { it.titlecase(Locale.FRENCH) },
            events = lines,
            alarmLabel = nextAlarmMillis?.let { alarmLabel(it, now) },
            calendarGranted = calendarGranted,
        )
    }

    fun alarmLabel(millis: Long, now: ZonedDateTime): String {
        val at = Instant.ofEpochMilli(millis).atZone(now.zone)
        return when (at.toLocalDate()) {
            now.toLocalDate() -> "Aujourd'hui ${TIME.format(at)}"
            now.toLocalDate().plusDays(1) -> "Demain ${TIME.format(at)}"
            else -> OTHER_DAY.format(at)
        }
    }

    private fun text(tomorrow: Boolean, event: SummaryEvent, zone: ZoneId): String {
        val time = if (event.allDay) null else TIME.format(Instant.ofEpochMilli(event.begin).atZone(zone))
        return listOfNotNull(if (tomorrow) "Demain" else null, time, event.title.ifBlank { "(Sans titre)" }).joinToString(" ")
    }

    private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
```

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils passent**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests "app.lanceur.summary.*"`
Expected: `BUILD SUCCESSFUL`, 5 tests réussis.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lanceur/summary/DaySummary.kt app/src/test/java/app/lanceur/summary
git commit -m "Résumé du jour : événements d'aujourd'hui et de demain, alarme suivante" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: ViewModel et couche Android (hôte, fournisseurs, résumé)

**Files:**
- Modify: `app/src/main/java/app/lanceur/home/ListMode.kt`, `home/LauncherViewModel.kt`, `apps/LauncherAppsSource.kt`, `AppContainer.kt`, `LanceurApp.kt`
- Create: `app/src/main/java/app/lanceur/apps/ProfileKinds.kt`, `widgets/WidgetHost.kt`, `widgets/WidgetProviderSource.kt`, `summary/DaySummarySource.kt`
- Test: `app/src/test/java/app/lanceur/home/LauncherViewModelTest.kt` (ajouts)

**Interfaces:**
- Consumes: `PrefsRepo` (tâche 2), `WidgetAddFlow.State`, `WidgetIds`, `ProviderEntry`, `WidgetSlot`, `DaySummary`, `SearchPermissions`, `ProfileKind`.
- Produces :
  - `Screen.WIDGET_PICKER` (en plus de `HOME`, `SEARCH`, `VAULT`, `SETTINGS`).
  - `LauncherViewModel` gagne :
    - `widgetEditMode: StateFlow<Boolean>` et `homePageRequests: StateFlow<Int>` ;
    - `var widgetAddState: WidgetAddFlow.State` ;
    - `setWidgetEditMode(on: Boolean)`, `addWidget(slot: WidgetSlot)`, `removeWidget(appWidgetId: Int)`, `setWidgetSize(appWidgetId: Int, size: WidgetSize)`, `setWidgetsOrder(ids: List<Int>)`, `setWidgetPageEnabled(enabled: Boolean)` ;
    - `goHome()` quitte aussi le mode édition et incrémente `homePageRequests`.
  - `object ProfileKinds { fun of(launcherApps: LauncherApps, user: UserHandle, me: UserHandle = Process.myUserHandle()): ProfileKind }`
  - `class WidgetHost(context: Context)` avec :
    - cycle de vie : `startListening()`, `stopListening()` ;
    - identifiants : `allocateId(): Int`, `deleteId(id: Int)`, `hostIds(): IntArray` ;
    - informations : `info(id: Int): AppWidgetProviderInfo?`, `availableIds(slots: List<WidgetSlot>): Set<Int>`, `label(id: Int): String` ;
    - liaison : `bindIfAllowed(id: Int, provider: AppKey): Boolean`, `bindIntent(id: Int, provider: AppKey): Intent` ;
    - configuration : `needsConfiguration(id: Int): Boolean`, `isReconfigurable(id: Int): Boolean`, `startConfiguration(activity: Activity, id: Int, requestCode: Int): Boolean` ;
    - vue : `createView(context: Context, id: Int): AppWidgetHostView?`.
  - `class WidgetProviderSource(context: Context)` avec `fun entries(): List<ProviderEntry>` (appels système bloquants) et `fun preview(entry: ProviderEntry, sizePx: Int): ImageBitmap?`
  - `class DaySummarySource(context: Context)` avec `suspend fun load(now: ZonedDateTime = ZonedDateTime.now()): DaySummaryState`
  - `AppContainer.widgetHost`, `AppContainer.widgetProviders`, `AppContainer.daySummary`, `AppContainer.cleanUpWidgetIds()`

- [ ] **Step 1: Écrire les tests du ViewModel**

Dans `app/src/test/java/app/lanceur/home/LauncherViewModelTest.kt`, ajoute ces imports :

```kotlin
import app.lanceur.apps.AppKey
import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSlot
```

et ces tests à la fin de la classe :

```kotlin
    @Test
    fun going_home_leaves_widget_edit_mode_and_asks_for_the_home_page() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.setWidgetEditMode(true)
        val before = vm.homePageRequests.value
        vm.goHome()
        assertFalse(vm.widgetEditMode.value)
        assertEquals(before + 1, vm.homePageRequests.value)
    }

    @Test
    fun widget_changes_reach_the_prefs() = runTest(main.dispatcher) {
        val vm = viewModel()
        val slot = WidgetSlot(5, AppKey("app.meteo", "app.meteo.Widget", 0), WidgetSize.SMALL)
        vm.addWidget(slot)
        vm.prefs.first { it.widgets == listOf(slot) }
        vm.setWidgetSize(5, WidgetSize.LARGE)
        vm.prefs.first { it.widgets.singleOrNull()?.size == WidgetSize.LARGE }
        vm.setWidgetPageEnabled(false)
        vm.prefs.first { !it.widgetPageEnabled }
        vm.removeWidget(5)
        vm.prefs.first { it.widgets.isEmpty() }
    }
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests "app.lanceur.home.LauncherViewModelTest"`
Expected: échec de compilation, `Unresolved reference 'setWidgetEditMode'`, `'homePageRequests'`, `'addWidget'`, etc.

- [ ] **Step 3: Compléter `Screen` et le ViewModel**

Dans `app/src/main/java/app/lanceur/home/ListMode.kt`, remplace `enum class Screen { HOME, SEARCH, VAULT, SETTINGS }` par :

```kotlin
enum class Screen { HOME, SEARCH, VAULT, SETTINGS, WIDGET_PICKER }
```

Dans `app/src/main/java/app/lanceur/home/LauncherViewModel.kt` :

1. Ajoute ces imports :

```kotlin
import app.lanceur.widgets.WidgetAddFlow
import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSlot
```

2. Ajoute, juste après la déclaration de `val vault: StateFlow<VaultState> = _vault.asStateFlow()` :

```kotlin

    private val _widgetEditMode = MutableStateFlow(false)
    val widgetEditMode: StateFlow<Boolean> = _widgetEditMode.asStateFlow()

    private val _homePageRequests = MutableStateFlow(0)

    /** Incrémenté par `goHome()` : l'écran ramène alors le défilement sur la page d'accueil. */
    val homePageRequests: StateFlow<Int> = _homePageRequests.asStateFlow()

    /** Ajout de widget en cours ; vit dans le ViewModel pour survivre à une recréation de l'activité. */
    var widgetAddState: WidgetAddFlow.State = WidgetAddFlow.State.Idle
```

3. Remplace la fonction `goHome()` par :

```kotlin
    /** Bouton Accueil : retour aux favoris, recherche fermée, dossier reverrouillé, page d'accueil. */
    fun goHome() {
        vaultEvent(VaultEvent.HomePressed)
        _screen.value = Screen.HOME
        _listMode.value = ListMode.Favorites
        _widgetEditMode.value = false
        _homePageRequests.value++
    }
```

4. Ajoute, juste avant `/** \`onDone\` est appelé une fois la préférence visible dans \`prefs\`. */` :

```kotlin
    fun setWidgetEditMode(on: Boolean) {
        _widgetEditMode.value = on
    }

    fun addWidget(slot: WidgetSlot) {
        viewModelScope.launch { prefsRepo.addWidget(slot) }
    }

    fun removeWidget(appWidgetId: Int) {
        viewModelScope.launch { prefsRepo.removeWidget(appWidgetId) }
    }

    fun setWidgetSize(appWidgetId: Int, size: WidgetSize) {
        viewModelScope.launch { prefsRepo.setWidgetSize(appWidgetId, size) }
    }

    fun setWidgetsOrder(ids: List<Int>) {
        viewModelScope.launch { prefsRepo.setWidgetsOrder(ids) }
    }

    fun setWidgetPageEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setWidgetPageEnabled(enabled) }
    }

```

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils passent**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 116 tests réussis (109 + 5 `DaySummaryTest` + 2 nouveaux `LauncherViewModelTest`).

- [ ] **Step 5: Écrire `ProfileKinds` et l'utiliser dans `LauncherAppsSource`**

`app/src/main/java/app/lanceur/apps/ProfileKinds.kt` :

```kotlin
package app.lanceur.apps

import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/** Type d'un profil ; partagé par la liste des applis et celle des widgets. */
object ProfileKinds {
    fun of(launcherApps: LauncherApps, user: UserHandle, me: UserHandle = Process.myUserHandle()): ProfileKind {
        if (user == me) return ProfileKind.MAIN
        return try {
            when (launcherApps.getLauncherUserInfo(user)?.userType) {
                null -> ProfileKind.UNKNOWN
                UserManager.USER_TYPE_PROFILE_PRIVATE -> ProfileKind.PRIVATE
                else -> ProfileKind.OTHER
            }
        } catch (e: SecurityException) {
            ProfileKind.UNKNOWN
        }
    }
}
```

Dans `app/src/main/java/app/lanceur/apps/LauncherAppsSource.kt`, remplace toute la fonction :

```kotlin
    private fun kindOf(user: UserHandle): ProfileKind {
        if (user == me) return ProfileKind.MAIN
        return try {
            when (launcherApps.getLauncherUserInfo(user)?.userType) {
                null -> ProfileKind.UNKNOWN
                UserManager.USER_TYPE_PROFILE_PRIVATE -> ProfileKind.PRIVATE
                else -> ProfileKind.OTHER
            }
        } catch (e: SecurityException) {
            ProfileKind.UNKNOWN
        }
    }
```

par :

```kotlin
    private fun kindOf(user: UserHandle): ProfileKind = ProfileKinds.of(launcherApps, user, me)
```

- [ ] **Step 6: Écrire l'hôte de widgets, la source des fournisseurs et celle du résumé**

`app/src/main/java/app/lanceur/widgets/WidgetHost.kt` :

```kotlin
package app.lanceur.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import app.lanceur.apps.AppKey

/** Hôte Android des widgets de Lanceur (identifiant d'hôte fixe : les widgets survivent aux redémarrages). */
class WidgetHost(context: Context) {
    private val appContext = context.applicationContext
    private val manager = AppWidgetManager.getInstance(appContext)
    private val host = AppWidgetHost(appContext, HOST_ID)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    fun startListening() {
        runCatching { host.startListening() }.onFailure { Log.w(TAG, "Écoute des widgets impossible", it) }
    }

    fun stopListening() {
        runCatching { host.stopListening() }.onFailure { Log.w(TAG, "Arrêt de l'écoute des widgets impossible", it) }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }.onFailure { Log.w(TAG, "Libération du widget $id impossible", it) }
    }

    fun hostIds(): IntArray = runCatching { host.appWidgetIds }.getOrDefault(IntArray(0))

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun availableIds(slots: List<WidgetSlot>): Set<Int> = slots.mapNotNullTo(HashSet()) { slot ->
        slot.appWidgetId.takeIf { info(it) != null }
    }

    fun label(id: Int): String = info(id)?.loadLabel(appContext.packageManager) ?: "Widget"

    /** `true` si Android a lié le widget sans rien demander ; sinon il faut lancer [bindIntent]. */
    fun bindIfAllowed(id: Int, provider: AppKey): Boolean {
        val user = user(provider) ?: return false
        return runCatching { manager.bindAppWidgetIdIfAllowed(id, user, provider.component(), null) }.getOrDefault(false)
    }

    fun bindIntent(id: Int, provider: AppKey): Intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.component())
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, user(provider))

    fun needsConfiguration(id: Int): Boolean {
        val info = info(id) ?: return false
        return info.configure != null &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) == 0
    }

    fun isReconfigurable(id: Int): Boolean {
        val info = info(id) ?: return false
        return info.configure != null && (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0
    }

    /** `false` si l'écran de configuration n'a pas pu s'ouvrir. */
    fun startConfiguration(activity: Activity, id: Int, requestCode: Int): Boolean = try {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "Configuration du widget $id impossible", e)
        false
    }

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return runCatching { host.createView(context, id, info) }.getOrNull()
    }

    private fun user(provider: AppKey): UserHandle? = userManager.getUserForSerialNumber(provider.userSerial)

    private fun AppKey.component() = ComponentName(packageName, className)

    private companion object {
        const val HOST_ID = 1024
        const val TAG = "Lanceur"
    }
}
```

`app/src/main/java/app/lanceur/widgets/WidgetProviderSource.kt` :

```kotlin
package app.lanceur.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import app.lanceur.apps.AppKey
import app.lanceur.apps.ProfileKind
import app.lanceur.apps.ProfileKinds
import kotlin.math.roundToInt

/** Widgets installés, profil principal et profil pro seulement (jamais l'Espace privé). */
class WidgetProviderSource(private val context: Context) {
    private val manager = AppWidgetManager.getInstance(context)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    @Volatile
    private var infos: Map<AppKey, AppWidgetProviderInfo> = emptyMap()

    /** Appels système bloquants : à faire hors du fil principal. */
    fun entries(): List<ProviderEntry> {
        val density = context.resources.displayMetrics.density
        val found = HashMap<AppKey, AppWidgetProviderInfo>()
        val result = launcherApps.profiles.flatMap { user ->
            val kind = ProfileKinds.of(launcherApps, user)
            if (kind != ProfileKind.MAIN && kind != ProfileKind.OTHER) return@flatMap emptyList<ProviderEntry>()
            val serial = userManager.getSerialNumberForUser(user)
            manager.getInstalledProvidersForProfile(user).map { info ->
                val key = AppKey(info.provider.packageName, info.provider.className, serial)
                found[key] = info
                ProviderEntry(
                    provider = key,
                    profileKind = kind,
                    appLabel = appLabel(info.provider.packageName, user),
                    widgetLabel = info.loadLabel(context.packageManager),
                    minHeightDp = (info.minHeight / density).roundToInt(),
                )
            }
        }
        infos = found
        return result
    }

    /** Aperçu fourni par l'appli (ou son icône), réduit pour tenir dans un carré de `sizePx`. */
    fun preview(entry: ProviderEntry, sizePx: Int): ImageBitmap? = runCatching {
        val info = infos[entry.provider] ?: return null
        val drawable = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0) ?: return null
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val scale = sizePx.toFloat() / maxOf(width, height)
        drawable.toBitmap((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1)).asImageBitmap()
    }.getOrNull()

    private fun appLabel(packageName: String, user: UserHandle): String = runCatching {
        launcherApps.getApplicationInfo(packageName, 0, user).loadLabel(context.packageManager).toString()
    }.getOrDefault(packageName)
}
```

`app/src/main/java/app/lanceur/summary/DaySummarySource.kt` :

```kotlin
package app.lanceur.summary

import android.Manifest
import android.app.AlarmManager
import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Instances
import app.lanceur.search.SearchPermissions
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Événements d'aujourd'hui et de demain, et alarme suivante (sans autorisation). */
class DaySummarySource(private val context: Context) {
    suspend fun load(now: ZonedDateTime = ZonedDateTime.now()): DaySummaryState = withContext(Dispatchers.IO) {
        val granted = SearchPermissions.granted(context, Manifest.permission.READ_CALENDAR)
        val events = if (granted) runCatching { queryEvents(now) }.getOrDefault(emptyList()) else emptyList()
        val alarm = context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime
        DaySummary.build(now, events, alarm, granted)
    }

    private fun queryEvents(now: ZonedDateTime): List<SummaryEvent> {
        // Fenêtre élargie d'un jour de chaque côté : les événements « toute la journée » sont en UTC ; DaySummary trie
        val begin = now.minusDays(1).toInstant().toEpochMilli()
        val end = now.toLocalDate().plusDays(3).atStartOfDay(now.zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, begin); ContentUris.appendId(it, end) }
            .build()
        val projection = arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY)
        val events = mutableListOf<SummaryEvent>()
        context.contentResolver.query(uri, projection, null, null, "${Instances.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                events += SummaryEvent(
                    eventId = cursor.getLong(0),
                    title = cursor.getString(1).orEmpty(),
                    begin = cursor.getLong(2),
                    end = cursor.getLong(3),
                    allDay = cursor.getInt(4) == 1,
                )
            }
        }
        return events
    }
}
```

- [ ] **Step 7: Brancher le conteneur et le nettoyage au démarrage**

Dans `app/src/main/java/app/lanceur/AppContainer.kt`, ajoute ces imports :

```kotlin
import app.lanceur.summary.DaySummarySource
import app.lanceur.widgets.WidgetHost
import app.lanceur.widgets.WidgetIds
import app.lanceur.widgets.WidgetProviderSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
```

et ajoute, juste après la ligne `val resultActions = ResultActions(appContext, appLauncher)` :

```kotlin
    val widgetHost = WidgetHost(appContext)
    val widgetProviders = WidgetProviderSource(appContext)
    val daySummary = DaySummarySource(appContext)

    /** Libère les identifiants réservés par un ajout interrompu (Lanceur tué pendant la configuration). */
    fun cleanUpWidgetIds() {
        appScope.launch {
            val slots = prefsRepo.prefs.first().widgets
            WidgetIds.orphans(widgetHost.hostIds(), slots).forEach(widgetHost::deleteId)
        }
    }
```

Dans `app/src/main/java/app/lanceur/LanceurApp.kt`, remplace `        container.catalog.start()` par :

```kotlin
        container.catalog.start()
        container.cleanUpWidgetIds()
```

- [ ] **Step 8: Vérifier la compilation et tous les tests JVM**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 116 tests réussis, aucun avertissement `w:` sur les fichiers modifiés.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/app/lanceur app/src/test/java/app/lanceur/home/LauncherViewModelTest.kt
git commit -m "Widgets : état dans le ViewModel, hôte Android, widgets installés, source du résumé du jour" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Écrans — défilement à deux pages, page de widgets, sélecteur

**Files:**
- Create: `app/src/main/java/app/lanceur/home/HomePager.kt`, `widgets/WidgetPage.kt`, `widgets/WidgetPicker.kt`
- Test: `app/src/androidTest/java/app/lanceur/home/HomePagerTest.kt`, `app/src/androidTest/java/app/lanceur/widgets/WidgetPageTest.kt`, `app/src/androidTest/java/app/lanceur/widgets/WidgetPickerTest.kt`

**Interfaces:**
- Consumes: `HomeScreen`, `HomeActions`, `ListMode` (existants), `WidgetCard`, `WidgetSlot`, `WidgetSize`, `HeightReorder`, `PickerGroup`, `ProviderEntry`, `DaySummaryState`, `SummaryEvent`, `SummaryLine`, `Reorder.move` (`app.lanceur.settings`), `blockTouchesBelow`, `HintText`, `SectionTitle` (`app.lanceur.ui`).
- Produces :
  - `@Composable fun HomePager(widgetsEnabled: Boolean, homePageRequests: Int, editMode: Boolean, onExitEdit: () -> Unit, onWidgetsShown: () -> Unit, widgetPage: @Composable () -> Unit, home: @Composable () -> Unit)` (tag `"pager"`).
  - `class WidgetPageActions(openEvent, openClock, requestCalendar, addWidget, setEditMode, remove, resize, reconfigure, reorder)`. Chaque callback a `{}` comme valeur par défaut. Types : `(SummaryEvent) -> Unit`, `() -> Unit`, `() -> Unit`, `() -> Unit`, `(Boolean) -> Unit`, `(WidgetSlot) -> Unit`, `(WidgetSlot, WidgetSize) -> Unit`, `(WidgetSlot) -> Unit`, `(List<Int>) -> Unit`.
  - `@Composable fun WidgetPage(summary: DaySummaryState?, cards: List<WidgetCard>, editMode: Boolean, label: (WidgetSlot) -> String, isReconfigurable: (WidgetSlot) -> Boolean, widgetView: @Composable (WidgetSlot, Modifier) -> Unit, actions: WidgetPageActions, modifier: Modifier = Modifier)`
  - `@Composable fun WidgetPicker(groups: List<PickerGroup>, query: String, onQueryChange: (String) -> Unit, preview: @Composable (ProviderEntry) -> Unit, onPick: (ProviderEntry) -> Unit, modifier: Modifier = Modifier)` (tag `"picker-filter"` sur le champ).

- [ ] **Step 1: Écrire les tests d'interface**

`app/src/androidTest/java/app/lanceur/home/HomePagerTest.kt` :

```kotlin
package app.lanceur.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import app.lanceur.prefs.AlphabetSide
import app.lanceur.prefs.AppLists
import org.junit.Rule
import org.junit.Test

class HomePagerTest {
    @get:Rule val rule = createComposeRule()

    private fun app(label: String): AppEntry {
        val id = label.lowercase()
        return AppEntry(AppKey("app.$id", "app.$id.Main", 0), label, false)
    }

    private val lists = AppLists(listOf(app("Banque"), app("Chrome")), listOf(app("Chrome")), emptyList(), emptyList())

    private fun simpleHome(): @Composable () -> Unit = { Box(Modifier.fillMaxSize()) { Text("ACCUEIL") } }

    @Test
    fun swiping_right_shows_the_widget_page() {
        rule.setContent {
            MaterialTheme {
                HomePager(true, 0, false, {}, {}, widgetPage = { Text("PAGE WIDGETS") }, home = simpleHome())
            }
        }
        rule.onNodeWithText("PAGE WIDGETS").assertIsNotDisplayed()
        rule.onNodeWithTag("pager").performTouchInput { swipeRight() }
        rule.onNodeWithText("PAGE WIDGETS").assertIsDisplayed()
    }

    @Test
    fun a_disabled_page_cannot_be_reached() {
        rule.setContent {
            MaterialTheme {
                HomePager(false, 0, false, {}, {}, widgetPage = { Text("PAGE WIDGETS") }, home = simpleHome())
            }
        }
        rule.onNodeWithTag("pager").performTouchInput { swipeRight() }
        rule.onNodeWithText("ACCUEIL").assertIsDisplayed()
        rule.onNodeWithText("PAGE WIDGETS").assertDoesNotExist()
    }

    @Test
    fun going_home_and_disabling_bring_back_the_home_page() {
        var requests by mutableIntStateOf(0)
        var enabled by mutableStateOf(true)
        rule.setContent {
            MaterialTheme {
                HomePager(enabled, requests, false, {}, {}, widgetPage = { Text("PAGE WIDGETS") }, home = simpleHome())
            }
        }
        rule.onNodeWithTag("pager").performTouchInput { swipeRight() }
        rule.onNodeWithText("PAGE WIDGETS").assertIsDisplayed()
        requests++
        rule.onNodeWithText("ACCUEIL").assertIsDisplayed()

        rule.onNodeWithTag("pager").performTouchInput { swipeRight() }
        rule.onNodeWithText("PAGE WIDGETS").assertIsDisplayed()
        enabled = false
        rule.onNodeWithText("ACCUEIL").assertIsDisplayed()
    }

    @Test
    fun a_gesture_started_on_the_alphabet_stays_with_the_alphabet() {
        rule.setContent {
            MaterialTheme {
                HomePager(
                    true, 0, false, {}, {},
                    widgetPage = { Text("PAGE WIDGETS") },
                    home = { HomeScreen(lists, ListMode.Favorites, AlphabetSide.RIGHT, HomeActions(), icon = {}) },
                )
            }
        }
        rule.onNodeWithTag("alphabet").performTouchInput {
            down(Offset(centerX, height / 27f * 1.5f))
            repeat(10) { moveBy(Offset(-40f, 0f)) }
            repeat(20) { moveBy(Offset(40f, 0f)) }
            up()
        }
        rule.onNodeWithText("PAGE WIDGETS").assertIsNotDisplayed()
    }
}
```

`app/src/androidTest/java/app/lanceur/widgets/WidgetPageTest.kt` :

```kotlin
package app.lanceur.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.lanceur.apps.AppKey
import app.lanceur.summary.DaySummaryState
import app.lanceur.summary.SummaryEvent
import app.lanceur.summary.SummaryLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WidgetPageTest {
    @get:Rule val rule = createComposeRule()

    private val slot = WidgetSlot(3, AppKey("app.meteo", "app.meteo.Widget", 0), WidgetSize.MEDIUM)
    private val summary = DaySummaryState(
        dateLabel = "Lundi 5 octobre",
        events = listOf(SummaryLine(SummaryEvent(1, "Dentiste", 0, 0, false), "14:00 Dentiste")),
        alarmLabel = "Demain 07:00",
        calendarGranted = true,
    )

    private fun show(cards: List<WidgetCard>, editMode: Boolean, actions: WidgetPageActions, onWidgetClick: () -> Unit = {}) {
        rule.setContent {
            MaterialTheme {
                WidgetPage(
                    summary = summary,
                    cards = cards,
                    editMode = editMode,
                    label = { "Météo" },
                    isReconfigurable = { false },
                    widgetView = { _, modifier -> Box(modifier.clickable(onClick = onWidgetClick)) { Text("CONTENU MÉTÉO") } },
                    actions = actions,
                )
            }
        }
    }

    @Test
    fun shows_the_summary_and_the_widgets() {
        show(listOf(WidgetCard.Live(slot)), editMode = false, actions = WidgetPageActions())
        rule.onNodeWithText("Lundi 5 octobre").assertIsDisplayed()
        rule.onNodeWithText("14:00 Dentiste").assertIsDisplayed()
        rule.onNodeWithText("⏰ Demain 07:00").assertIsDisplayed()
        rule.onNodeWithText("CONTENU MÉTÉO").assertIsDisplayed()
        rule.onNodeWithText("Modifier").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun without_widget_it_invites_to_add_one() {
        var added = false
        show(emptyList(), editMode = false, actions = WidgetPageActions(addWidget = { added = true }))
        rule.onNodeWithText("Ajoute ton premier widget").assertIsDisplayed()
        rule.onNodeWithText("+ Ajouter un widget").performScrollTo().performClick()
        assertTrue(added)
    }

    @Test
    fun edit_mode_changes_the_size_and_finishes() {
        var resized: Pair<WidgetSlot, WidgetSize>? = null
        var editMode: Boolean? = null
        show(
            listOf(WidgetCard.Live(slot)),
            editMode = true,
            actions = WidgetPageActions(resize = { s, size -> resized = s to size }, setEditMode = { editMode = it }),
        )
        rule.onNodeWithText("L").performClick()
        assertEquals(slot to WidgetSize.LARGE, resized)
        rule.onNodeWithText("Terminé").performScrollTo().performClick()
        assertEquals(false, editMode)
    }

    @Test
    fun edit_mode_blocks_touches_to_the_widget() {
        var clicks = 0
        show(listOf(WidgetCard.Live(slot)), editMode = true, actions = WidgetPageActions(), onWidgetClick = { clicks++ })
        rule.onNodeWithText("CONTENU MÉTÉO").performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun an_unavailable_widget_can_be_removed() {
        var removed: WidgetSlot? = null
        show(listOf(WidgetCard.Unavailable(slot)), editMode = false, actions = WidgetPageActions(remove = { removed = it }))
        rule.onNodeWithText("Widget indisponible").assertIsDisplayed()
        rule.onNodeWithText("Retirer").performClick()
        assertEquals(slot, removed)
    }
}
```

`app/src/androidTest/java/app/lanceur/widgets/WidgetPickerTest.kt` :

```kotlin
package app.lanceur.widgets

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.lanceur.apps.AppKey
import app.lanceur.apps.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WidgetPickerTest {
    @get:Rule val rule = createComposeRule()

    private val entry = ProviderEntry(AppKey("app.meteo", "app.meteo.Jour", 0), ProfileKind.MAIN, "Météo", "Prévisions", 110)
    private val groups = listOf(PickerGroup("Météo", "app.meteo", isWork = false, entries = listOf(entry)))

    @Test
    fun lists_apps_and_picks_a_widget() {
        var picked: ProviderEntry? = null
        rule.setContent { MaterialTheme { WidgetPicker(groups, "", {}, preview = {}, onPick = { picked = it }) } }
        rule.onNodeWithText("Météo").assertIsDisplayed()
        rule.onNodeWithText("Prévisions").performClick()
        assertEquals(entry, picked)
    }

    @Test
    fun typing_updates_the_filter() {
        var query = ""
        rule.setContent { MaterialTheme { WidgetPicker(groups, query, { query = it }, preview = {}, onPick = {}) } }
        rule.onNodeWithTag("picker-filter").performTextInput("cal")
        assertEquals("cal", query)
    }
}
```

- [ ] **Step 2: Vérifier que les tests ne compilent pas encore**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebugAndroidTest`
Expected: échec de compilation, `Unresolved reference 'HomePager'`, `'WidgetPage'`, `'WidgetPageActions'`, `'WidgetPicker'`.

- [ ] **Step 3: Écrire `HomePager`**

`app/src/main/java/app/lanceur/home/HomePager.kt` :

```kotlin
package app.lanceur.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch

/**
 * Page de widgets à gauche, accueil à droite ; une seule page si les widgets sont désactivés. Un geste qui commence
 * sur l'alphabet ne fait jamais tourner la page : la barre consomme le toucher dès qu'on la pose.
 */
@Composable
fun HomePager(
    widgetsEnabled: Boolean,
    homePageRequests: Int,
    editMode: Boolean,
    onExitEdit: () -> Unit,
    onWidgetsShown: () -> Unit,
    widgetPage: @Composable () -> Unit,
    home: @Composable () -> Unit,
) {
    // Changer le nombre de pages recrée l'état : on repart toujours de l'accueil
    key(widgetsEnabled) {
        val pageCount = if (widgetsEnabled) 2 else 1
        val homeIndex = pageCount - 1
        val state = rememberPagerState(initialPage = homeIndex) { pageCount }
        val scope = rememberCoroutineScope()
        val latestShown by rememberUpdatedState(onWidgetsShown)

        LaunchedEffect(homePageRequests) {
            if (state.currentPage != homeIndex) state.animateScrollToPage(homeIndex)
        }
        LaunchedEffect(state) {
            snapshotFlow { state.settledPage }.collect { if (widgetsEnabled && it == 0) latestShown() }
        }
        BackHandler(enabled = widgetsEnabled && state.currentPage == 0) {
            if (editMode) onExitEdit() else scope.launch { state.animateScrollToPage(homeIndex) }
        }

        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxSize().testTag("pager"),
            beyondViewportPageCount = 1,
            userScrollEnabled = !editMode,
            key = { page -> if (widgetsEnabled && page == 0) "widgets" else "home" },
        ) { page ->
            if (widgetsEnabled && page == 0) widgetPage() else home()
        }
    }
}
```

- [ ] **Step 4: Écrire `WidgetPage`**

`app/src/main/java/app/lanceur/widgets/WidgetPage.kt` :

```kotlin
package app.lanceur.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.lanceur.settings.Reorder
import app.lanceur.summary.DaySummaryState
import app.lanceur.summary.SummaryEvent
import app.lanceur.ui.HintText
import app.lanceur.ui.blockTouchesBelow

class WidgetPageActions(
    val openEvent: (SummaryEvent) -> Unit = {},
    val openClock: () -> Unit = {},
    val requestCalendar: () -> Unit = {},
    val addWidget: () -> Unit = {},
    val setEditMode: (Boolean) -> Unit = {},
    val remove: (WidgetSlot) -> Unit = {},
    val resize: (WidgetSlot, WidgetSize) -> Unit = {},
    val reconfigure: (WidgetSlot) -> Unit = {},
    val reorder: (List<Int>) -> Unit = {},
)

private val TOOLBAR_HEIGHT = 48.dp
private val GAP = 12.dp

@Composable
fun WidgetPage(
    summary: DaySummaryState?,
    cards: List<WidgetCard>,
    editMode: Boolean,
    label: (WidgetSlot) -> String,
    isReconfigurable: (WidgetSlot) -> Boolean,
    widgetView: @Composable (WidgetSlot, Modifier) -> Unit,
    actions: WidgetPageActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        if (summary != null) SummaryCard(summary, actions)
        if (cards.isEmpty()) HintText("Ajoute ton premier widget")
        WidgetList(cards, editMode, label, isReconfigurable, widgetView, actions)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            if (editMode) {
                Button(onClick = { actions.setEditMode(false) }) { Text("Terminé") }
            } else {
                FilledTonalButton(onClick = actions.addWidget) { Text("+ Ajouter un widget") }
                if (cards.isNotEmpty()) OutlinedButton(onClick = { actions.setEditMode(true) }) { Text("Modifier") }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: DaySummaryState, actions: WidgetPageActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
    ) {
        Text(summary.dateLabel, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        when {
            !summary.calendarGranted ->
                TextButton(onClick = actions.requestCalendar) { Text("Autoriser l'agenda pour voir tes événements") }
            summary.events.isEmpty() ->
                Text("Rien de prévu", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> summary.events.forEach { line ->
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { actions.openEvent(line.event) }.padding(vertical = 6.dp),
                )
            }
        }
        summary.alarmLabel?.let { alarm ->
            Text("⏰ $alarm", modifier = Modifier.clickable(onClick = actions.openClock).padding(vertical = 6.dp))
        }
    }
}

/** Liste des widgets ; en mode édition, glisser la poignée les réordonne (hauteurs différentes : `HeightReorder`). */
@Composable
private fun WidgetList(
    cards: List<WidgetCard>,
    editMode: Boolean,
    label: (WidgetSlot) -> String,
    isReconfigurable: (WidgetSlot) -> Boolean,
    widgetView: @Composable (WidgetSlot, Modifier) -> Unit,
    actions: WidgetPageActions,
) {
    // Un seul état stable, resynchronisé hors glisser (même correctif que les favoris)
    var working by remember { mutableStateOf(cards) }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val latestReorder by rememberUpdatedState(actions.reorder)
    LaunchedEffect(cards) { if (draggingId == null) working = cards }
    val density = LocalDensity.current
    val heightPx = { card: WidgetCard -> with(density) { (card.slot.size.heightDp.dp + TOOLBAR_HEIGHT + GAP).toPx() } }

    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        working.forEach { card ->
            key(card.slot.appWidgetId) {
                val dragging = card.slot.appWidgetId == draggingId
                Column(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                ) {
                    if (editMode) {
                        EditToolbar(
                            card = card,
                            label = label(card.slot),
                            reconfigurable = isReconfigurable(card.slot),
                            actions = actions,
                            handle = Modifier.pointerInput(card.slot.appWidgetId) {
                                detectDragGestures(
                                    onDragStart = { draggingId = card.slot.appWidgetId; dragOffset = 0f },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffset = 0f
                                        latestReorder(working.map { it.slot.appWidgetId })
                                    },
                                    onDragCancel = { draggingId = null; dragOffset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val from = working.indexOfFirst { it.slot.appWidgetId == card.slot.appWidgetId }
                                    val step = HeightReorder.step(working.map(heightPx), from, dragOffset)
                                    if (step.to != from) {
                                        working = Reorder.move(working, from, step.to)
                                        dragOffset = step.offset
                                    }
                                }
                            },
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(card.slot.size.heightDp.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    ) {
                        when (card) {
                            is WidgetCard.Live -> widgetView(card.slot, Modifier.fillMaxSize())
                            is WidgetCard.Unavailable -> UnavailableCard(label(card.slot)) { actions.remove(card.slot) }
                        }
                        // En mode édition, le widget ne reçoit plus aucun toucher
                        if (editMode) {
                            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.15f)).blockTouchesBelow())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditToolbar(card: WidgetCard, label: String, reconfigurable: Boolean, actions: WidgetPageActions, handle: Modifier) {
    Row(Modifier.fillMaxWidth().height(TOOLBAR_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Menu, contentDescription = "Déplacer $label", modifier = handle.padding(12.dp))
        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        WidgetSize.entries.forEach { size ->
            FilterChip(
                selected = card.slot.size == size,
                onClick = { actions.resize(card.slot, size) },
                label = { Text(size.shortLabel) },
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        if (reconfigurable) {
            IconButton(onClick = { actions.reconfigure(card.slot) }) { Icon(Icons.Default.Settings, contentDescription = "Reconfigurer") }
        }
        IconButton(onClick = { actions.remove(card.slot) }) { Icon(Icons.Default.Clear, contentDescription = "Retirer") }
    }
}

@Composable
private fun UnavailableCard(label: String, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Widget indisponible", style = MaterialTheme.typography.titleMedium)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRemove) { Text("Retirer") }
    }
}
```

- [ ] **Step 5: Écrire `WidgetPicker`**

`app/src/main/java/app/lanceur/widgets/WidgetPicker.kt` :

```kotlin
package app.lanceur.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.ui.HintText
import app.lanceur.ui.SectionTitle
import app.lanceur.ui.blockTouchesBelow

@Composable
fun WidgetPicker(
    groups: List<PickerGroup>,
    query: String,
    onQueryChange: (String) -> Unit,
    preview: @Composable (ProviderEntry) -> Unit,
    onPick: (ProviderEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .blockTouchesBelow()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Text("Ajouter un widget", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("picker-filter"),
            placeholder = { Text("Filtrer par appli ou widget") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (groups.isEmpty()) item { HintText("Aucun widget trouvé") }
            groups.forEach { group ->
                item(key = "g:${group.packageName}:${group.entries.first().provider.userSerial}") {
                    SectionTitle(if (group.isWork) "${group.appLabel} (pro)" else group.appLabel)
                }
                items(group.entries, key = { "e:" + it.provider.encode() }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPick(entry) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(96.dp, 64.dp), contentAlignment = Alignment.Center) { preview(entry) }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.widgetLabel, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Taille de départ : ${WidgetSize.fromMinHeightDp(entry.minHeightDp).shortLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 6: Lancer les tests d'interface sur le téléphone**

Run: `~/Android/Sdk/platform-tools/adb devices && JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.home.HomePagerTest,app.lanceur.widgets.WidgetPageTest,app.lanceur.widgets.WidgetPickerTest`
Expected: `BUILD SUCCESSFUL`, 11 tests réussis (4 + 5 + 2).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/lanceur/home/HomePager.kt app/src/main/java/app/lanceur/widgets/WidgetPage.kt app/src/main/java/app/lanceur/widgets/WidgetPicker.kt app/src/androidTest/java/app/lanceur/home/HomePagerTest.kt app/src/androidTest/java/app/lanceur/widgets
git commit -m "Widgets : défilement à deux pages, page de widgets avec mode édition, sélecteur" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Assemblage — widget hébergé, ajout depuis l'activité, réglage, `AppRoot`

**Files:**
- Create: `app/src/main/java/app/lanceur/widgets/HostedWidget.kt`
- Modify: `app/src/main/java/app/lanceur/settings/SettingsScreen.kt`, `app/src/main/java/app/lanceur/AppRoot.kt` (remplacement complet), `app/src/main/java/app/lanceur/MainActivity.kt` (remplacement complet)

**Interfaces:**
- Consumes: tout ce qui précède. En particulier :
  - `WidgetHost`, `WidgetAddFlow`, `VisibleWidgets`, `PickerCatalog` ;
  - `WidgetPage`, `WidgetPageActions`, `WidgetPicker`, `HomePager` ;
  - `LauncherViewModel.widgetEditMode`, `homePageRequests`, `widgetAddState` et les fonctions de widgets ;
  - `AppContainer.widgetHost`, `widgetProviders`, `daySummary` ;
  - `Screen.WIDGET_PICKER`, `ResultActions.open(SearchResult.Event(...))`.
- Produces :
  - `@Composable fun HostedWidget(slot: WidgetSlot, host: WidgetHost, modifier: Modifier = Modifier)` ;
  - `class WidgetHostActions(val add: (ProviderEntry) -> Unit = {}, val reconfigure: (WidgetSlot) -> Unit = {})` (dans `AppRoot.kt`) ;
  - la signature `AppRoot(vm, searchVm, container, widgetHostActions: WidgetHostActions)` ;
  - le paramètre `SettingsScreen(… widgetPageEnabled: Boolean …)` et l'action `SettingsActions.setWidgetPageEnabled: (Boolean) -> Unit`.

- [ ] **Step 1: Écrire `HostedWidget`**

`app/src/main/java/app/lanceur/widgets/HostedWidget.kt` :

```kotlin
package app.lanceur.widgets

import android.appwidget.AppWidgetHostView
import android.os.Bundle
import android.util.SizeF
import android.widget.FrameLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Affiche un vrai widget Android ; sa taille réelle lui est transmise pour qu'il choisisse sa mise en page. */
@Composable
fun HostedWidget(slot: WidgetSlot, host: WidgetHost, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val size = SizeF(maxWidth.value, slot.size.heightDp.toFloat())
        key(slot.appWidgetId) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context -> host.createView(context, slot.appWidgetId) ?: FrameLayout(context) },
                update = { view ->
                    // Ne prévenir le widget que si sa taille change vraiment
                    if (view is AppWidgetHostView && view.tag != size) {
                        view.tag = size
                        view.updateAppWidgetSize(Bundle(), listOf(size))
                    }
                },
            )
        }
    }
}
```

- [ ] **Step 2: Ajouter l'interrupteur dans les réglages**

Dans `app/src/main/java/app/lanceur/settings/SettingsScreen.kt` :

1. Ajoute l'import :

```kotlin
import androidx.compose.material3.Switch
```

2. Dans `class SettingsActions`, remplace `    val enableLockService: () -> Unit = {},` par :

```kotlin
    val enableLockService: () -> Unit = {},
    val setWidgetPageEnabled: (Boolean) -> Unit = {},
```

3. Dans la signature de `SettingsScreen`, remplace `    lockServiceEnabled: Boolean,` par :

```kotlin
    lockServiceEnabled: Boolean,
    widgetPageEnabled: Boolean,
```

4. Juste avant la ligne `        SettingRow(\n            title = "Applis cachées",`, insère :

```kotlin
        SwitchRow(
            title = "Page de widgets à gauche",
            subtitle = "Glisser vers la droite depuis l'accueil",
            checked = widgetPageEnabled,
            onCheckedChange = actions.setWidgetPageEnabled,
        )
```

5. Ajoute à la fin du fichier :

```kotlin

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
```

- [ ] **Step 3: Remplacer `AppRoot`**

`app/src/main/java/app/lanceur/AppRoot.kt` (remplace tout le contenu) :

```kotlin
package app.lanceur

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppIcon
import app.lanceur.apps.AppKey
import app.lanceur.apps.HomeRole
import app.lanceur.apps.HomeRoleWatcher
import app.lanceur.home.HomeActions
import app.lanceur.home.HomePager
import app.lanceur.home.HomeScreen
import app.lanceur.home.LauncherViewModel
import app.lanceur.home.ListMode
import app.lanceur.home.Screen
import app.lanceur.lock.LockScreenService
import app.lanceur.search.SearchActions
import app.lanceur.search.SearchPermissions
import app.lanceur.search.SearchResult
import app.lanceur.search.SearchScreen
import app.lanceur.search.SearchViewModel
import app.lanceur.settings.SettingsActions
import app.lanceur.settings.SettingsScreen
import app.lanceur.summary.DaySummaryState
import app.lanceur.ui.AppMenuAction
import app.lanceur.vault.Authenticator
import app.lanceur.vault.VaultActions
import app.lanceur.vault.VaultEvent
import app.lanceur.vault.VaultScreen
import app.lanceur.vault.VaultState
import app.lanceur.widgets.HostedWidget
import app.lanceur.widgets.PickerCatalog
import app.lanceur.widgets.ProviderEntry
import app.lanceur.widgets.VisibleWidgets
import app.lanceur.widgets.WidgetPage
import app.lanceur.widgets.WidgetPageActions
import app.lanceur.widgets.WidgetPicker
import app.lanceur.widgets.WidgetSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Actions qui passent par l'activité : écrans d'Android pour lier ou configurer un widget. */
class WidgetHostActions(
    val add: (ProviderEntry) -> Unit = {},
    val reconfigure: (WidgetSlot) -> Unit = {},
)

/** Assemble les écrans : les deux pages (widgets, accueil) sont dessous, les autres écrans se superposent. */
@Composable
fun AppRoot(vm: LauncherViewModel, searchVm: SearchViewModel, container: AppContainer, widgetHostActions: WidgetHostActions) {
    val context = LocalContext.current
    val activity = LocalActivity.current ?: return
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val mode by vm.listMode.collectAsStateWithLifecycle()
    val vault by vm.vault.collectAsStateWithLifecycle()
    val widgetEditMode by vm.widgetEditMode.collectAsStateWithLifecycle()
    val homePageRequests by vm.homePageRequests.collectAsStateWithLifecycle()
    val privateSpace by container.catalog.privateSpace.collectAsStateWithLifecycle()
    val query by searchVm.query.collectAsStateWithLifecycle()
    val results by searchVm.results.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val authenticator = remember(activity) { Authenticator(activity) }
    val icon: @Composable (AppKey) -> Unit = { key -> AppIcon(key, container.iconLoader) }
    val roleWatcher = remember { HomeRoleWatcher { container.catalog.reload() } }
    var isDefault by remember { mutableStateOf(HomeRole.isHeld(context).also(roleWatcher::update)) }
    var permissionsGranted by remember { mutableStateOf(SearchPermissions.allGranted(context)) }
    var lockServiceEnabled by remember { mutableStateOf(LockScreenService.isEnabled(context)) }
    var summary by remember { mutableStateOf<DaySummaryState?>(null) }
    var widgetRefresh by remember { mutableIntStateOf(0) }
    var pickerQuery by remember { mutableStateOf("") }

    fun reloadSummary() {
        scope.launch { summary = container.daySummary.load() }
    }

    LifecycleResumeEffect(Unit) {
        isDefault = HomeRole.isHeld(context).also(roleWatcher::update)
        permissionsGranted = SearchPermissions.allGranted(context)
        lockServiceEnabled = LockScreenService.isEnabled(context)
        widgetRefresh++
        reloadSummary()
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsGranted = SearchPermissions.allGranted(context)
        searchVm.refresh()
        reloadSummary()
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = HomeRole.isHeld(context).also(roleWatcher::update)
    }

    // Pas d'écoute des widgets quand la page est désactivée (MainActivity gère onStart / onStop)
    LaunchedEffect(prefs.widgetPageEnabled) {
        if (prefs.widgetPageEnabled) container.widgetHost.startListening() else container.widgetHost.stopListening()
    }
    val availableIds = remember(prefs.widgets, widgetRefresh) { container.widgetHost.availableIds(prefs.widgets) }
    val widgetCards = remember(prefs.widgets, prefs.hidden, availableIds) {
        VisibleWidgets.compute(prefs.widgets, prefs.hidden, availableIds)
    }
    val widgetLabels = remember(prefs.widgets, widgetRefresh) {
        prefs.widgets.associate { it.appWidgetId to container.widgetHost.label(it.appWidgetId) }
    }
    val providerEntries by produceState(emptyList<ProviderEntry>(), screen) {
        if (screen == Screen.WIDGET_PICKER) value = withContext(Dispatchers.IO) { container.widgetProviders.entries() }
    }
    val pickerGroups = remember(providerEntries, prefs.hidden, pickerQuery) {
        PickerCatalog.build(providerEntries, prefs.hidden, pickerQuery)
    }
    val widgetPreview: @Composable (ProviderEntry) -> Unit = { entry ->
        val sizePx = with(LocalDensity.current) { 96.dp.roundToPx() }
        val bitmap by produceState<ImageBitmap?>(null, entry) {
            value = withContext(Dispatchers.IO) { container.widgetProviders.preview(entry, sizePx) }
        }
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun launch(entry: AppEntry) {
        if (!container.appLauncher.launch(entry.key)) {
            toast("Appli introuvable")
            container.catalog.reload()
        }
    }

    fun openVault() {
        when {
            vm.vault.value is VaultState.Unlocked -> vm.show(Screen.VAULT)
            !authenticator.canAuthenticate() -> toast("Configure un verrouillage d'écran pour utiliser le dossier caché")
            vm.requestVault() -> authenticator.authenticate("Dossier caché") { ok ->
                vm.vaultEvent(if (ok) VaultEvent.AuthSucceeded else VaultEvent.AuthFailed)
            }
        }
    }

    fun openAccessibilitySettings() {
        container.appLauncher.startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun lockScreen() {
        if (!LockScreenService.lock()) {
            toast("Active « Lanceur » dans Accessibilité pour verrouiller d'un double toucher")
            openAccessibilitySettings()
        }
    }

    fun onMenu(entry: AppEntry, action: AppMenuAction) {
        when (action) {
            AppMenuAction.ADD_FAVORITE -> vm.addFavorite(entry.key)
            AppMenuAction.REMOVE_FAVORITE -> vm.removeFavorite(entry.key)
            AppMenuAction.HIDE -> vm.hide(entry.key)
            AppMenuAction.UNHIDE -> vm.unhide(entry.key)
            AppMenuAction.INFO -> container.appLauncher.openAppInfo(entry.key)
            AppMenuAction.UNINSTALL -> container.appLauncher.uninstall(entry.key)
        }
    }

    fun openResult(result: SearchResult) {
        if (result == SearchResult.PermissionHint) {
            permissionLauncher.launch(SearchPermissions.ALL)
            return
        }
        val opened = container.resultActions.open(result)
        if (!opened && result is SearchResult.App) {
            toast("Appli introuvable")
            container.catalog.reload()
        }
        // Copier un calcul garde la recherche ouverte
        if (opened && result !is SearchResult.Calc) vm.show(Screen.HOME)
    }

    fun removeWidget(slot: WidgetSlot) {
        container.widgetHost.deleteId(slot.appWidgetId)
        vm.removeWidget(slot.appWidgetId)
    }

    BackHandler(enabled = screen != Screen.HOME || mode != ListMode.Favorites) { vm.back() }
    LaunchedEffect(screen) {
        if (screen != Screen.SEARCH) searchVm.setQuery("")
        if (screen != Screen.WIDGET_PICKER) pickerQuery = ""
    }

    Box(Modifier.fillMaxSize()) {
        // Seul le fond d'écran est visible pendant les quelques millisecondes du chargement
        if (loaded) HomePager(
            widgetsEnabled = prefs.widgetPageEnabled,
            homePageRequests = homePageRequests,
            editMode = widgetEditMode,
            onExitEdit = { vm.setWidgetEditMode(false) },
            onWidgetsShown = {
                widgetRefresh++
                reloadSummary()
            },
            widgetPage = {
                WidgetPage(
                    summary = summary,
                    cards = widgetCards,
                    editMode = widgetEditMode,
                    label = { widgetLabels[it.appWidgetId] ?: "Widget" },
                    isReconfigurable = { container.widgetHost.isReconfigurable(it.appWidgetId) },
                    widgetView = { slot, modifier -> HostedWidget(slot, container.widgetHost, modifier) },
                    actions = WidgetPageActions(
                        openEvent = { event ->
                            container.resultActions.open(SearchResult.Event(event.eventId, event.title, event.begin, event.end, event.allDay, null))
                        },
                        openClock = { container.appLauncher.openClock() },
                        requestCalendar = { permissionLauncher.launch(SearchPermissions.ALL) },
                        addWidget = { vm.show(Screen.WIDGET_PICKER) },
                        setEditMode = vm::setWidgetEditMode,
                        remove = ::removeWidget,
                        resize = { slot, size -> vm.setWidgetSize(slot.appWidgetId, size) },
                        reconfigure = widgetHostActions.reconfigure,
                        reorder = vm::setWidgetsOrder,
                    ),
                )
            },
            home = {
                HomeScreen(
                    lists = lists,
                    mode = mode,
                    side = prefs.alphabetSide,
                    icon = icon,
                    actions = HomeActions(
                        launch = ::launch,
                        menu = ::onMenu,
                        changeMode = vm::setListMode,
                        openSearch = { vm.show(Screen.SEARCH) },
                        openNotifications = { container.appLauncher.expandNotifications() },
                        openVault = ::openVault,
                        openSettings = { vm.show(Screen.SETTINGS) },
                        openClock = { container.appLauncher.openClock() },
                        openCalendar = { container.appLauncher.openCalendar() },
                        lockScreen = ::lockScreen,
                    ),
                )
            },
        )
        AnimatedVisibility(
            visible = screen == Screen.SEARCH,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            SearchScreen(
                query = query,
                results = results,
                icon = icon,
                actions = SearchActions(
                    queryChange = searchVm::setQuery,
                    open = ::openResult,
                    call = { container.resultActions.call(it) },
                    sms = { container.resultActions.sms(it) },
                    requestPermissions = { permissionLauncher.launch(SearchPermissions.ALL) },
                    dismissHint = { vm.dismissPermissionHint { searchVm.refresh() } },
                    close = { vm.show(Screen.HOME) },
                ),
            )
        }
        AnimatedVisibility(
            visible = screen == Screen.WIDGET_PICKER,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            WidgetPicker(
                groups = pickerGroups,
                query = pickerQuery,
                onQueryChange = { pickerQuery = it },
                preview = widgetPreview,
                onPick = { entry ->
                    vm.show(Screen.HOME)
                    widgetHostActions.add(entry)
                },
            )
        }
        // Pas d'animation de sortie : le contenu caché disparaît immédiatement au verrouillage
        AnimatedVisibility(
            visible = screen == Screen.VAULT && vault is VaultState.Unlocked,
            enter = fadeIn(),
            exit = ExitTransition.None,
        ) {
            VaultScreen(
                hidden = lists.hiddenApps,
                privateApps = lists.privateApps,
                privateSpace = privateSpace,
                icon = icon,
                actions = VaultActions(
                    launch = ::launch,
                    menu = ::onMenu,
                    unlockPrivateSpace = {
                        // L'écran du système s'ouvre après ce callback : l'événement arrive avant la mise en arrière-plan
                        if (container.catalog.setPrivateSpaceLocked(false)) vm.vaultEvent(VaultEvent.ExternalPromptStarted)
                    },
                    lockPrivateSpace = { container.catalog.setPrivateSpaceLocked(true) },
                ),
            )
        }
        AnimatedVisibility(visible = screen == Screen.SETTINGS, enter = fadeIn(), exit = fadeOut()) {
            SettingsScreen(
                favorites = lists.favorites,
                side = prefs.alphabetSide,
                isDefaultLauncher = isDefault,
                permissionsGranted = permissionsGranted,
                lockServiceEnabled = lockServiceEnabled,
                widgetPageEnabled = prefs.widgetPageEnabled,
                icon = icon,
                actions = SettingsActions(
                    setDefault = {
                        try {
                            roleLauncher.launch(HomeRole.requestIntent(context))
                        } catch (e: ActivityNotFoundException) {
                            container.appLauncher.startSafely(Intent(Settings.ACTION_HOME_SETTINGS))
                        }
                    },
                    reorderFavorites = vm::setFavoritesOrder,
                    openHidden = ::openVault,
                    requestPermissions = { permissionLauncher.launch(SearchPermissions.ALL) },
                    setSide = vm::setAlphabetSide,
                    enableLockService = ::openAccessibilitySettings,
                    setWidgetPageEnabled = vm::setWidgetPageEnabled,
                ),
            )
        }
    }
}
```

- [ ] **Step 4: Remplacer `MainActivity`**

`app/src/main/java/app/lanceur/MainActivity.kt` (remplace tout le contenu) :

```kotlin
package app.lanceur

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.lanceur.home.LauncherViewModel
import app.lanceur.search.SearchPermissions
import app.lanceur.search.SearchViewModel
import app.lanceur.ui.theme.LanceurTheme
import app.lanceur.vault.VaultEvent
import app.lanceur.widgets.ProviderEntry
import app.lanceur.widgets.WidgetAddFlow
import app.lanceur.widgets.WidgetSlot

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as LanceurApp).container }

    private val launcherVm: LauncherViewModel by viewModels { LauncherViewModel.factory(container) }

    private val searchVm: SearchViewModel by viewModels {
        viewModelFactory {
            initializer {
                // Copies locales : les lambdas gardées par le ViewModel ne retiennent pas l'activité
                val launcher = launcherVm
                val appContext = applicationContext
                SearchViewModel(
                    container.searchEngine(
                        visibleApps = { launcher.lists.value.allVisible },
                        showPermissionHint = {
                            !launcher.prefs.value.permissionHintDismissed && !SearchPermissions.allGranted(appContext)
                        },
                    ),
                )
            }
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = launcherVm.vaultEvent(VaultEvent.ScreenOff)
    }

    /** Fenêtre d'Android « Autoriser Lanceur à créer des widgets… ». */
    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = launcherVm.widgetAddState as? WidgetAddFlow.State.Binding
        val granted = result.resultCode == RESULT_OK
        val needsConfiguration = granted && pending != null && container.widgetHost.needsConfiguration(pending.id)
        runWidgetEvent(WidgetAddFlow.Event.BindResult(granted, needsConfiguration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        setContent {
            LanceurTheme {
                AppRoot(launcherVm, searchVm, container, WidgetHostActions(add = ::addWidget, reconfigure = ::reconfigureWidget))
            }
        }
    }

    /** Bouton Accueil (ou geste) alors que Lanceur est déjà lancé. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) launcherVm.goHome()
    }

    override fun onStart() {
        super.onStart()
        if (launcherVm.prefs.value.widgetPageEnabled) container.widgetHost.startListening()
    }

    override fun onResume() {
        super.onResume()
        launcherVm.vaultEvent(VaultEvent.Resumed)
    }

    override fun onStop() {
        super.onStop()
        container.widgetHost.stopListening()
        launcherVm.vaultEvent(VaultEvent.Backgrounded)
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }

    private fun addWidget(entry: ProviderEntry) {
        val host = container.widgetHost
        val id = host.allocateId()
        val bound = host.bindIfAllowed(id, entry.provider)
        runWidgetEvent(WidgetAddFlow.Event.Start(id, entry, bound, bound && host.needsConfiguration(id)))
    }

    private fun reconfigureWidget(slot: WidgetSlot) {
        container.widgetHost.startConfiguration(this, slot.appWidgetId, REQUEST_RECONFIGURE)
    }

    private fun runWidgetEvent(event: WidgetAddFlow.Event) {
        val (next, effects) = WidgetAddFlow.reduce(launcherVm.widgetAddState, event)
        launcherVm.widgetAddState = next
        effects.forEach(::runWidgetEffect)
    }

    private fun runWidgetEffect(effect: WidgetAddFlow.Effect) {
        val host = container.widgetHost
        when (effect) {
            is WidgetAddFlow.Effect.LaunchBind -> bindLauncher.launch(host.bindIntent(effect.id, effect.provider))
            is WidgetAddFlow.Effect.LaunchConfigure -> {
                if (!host.startConfiguration(this, effect.id, REQUEST_CONFIGURE)) runWidgetEvent(WidgetAddFlow.Event.ConfigureResult(false))
            }
            // Un identifiant qu'Android ne connaît plus (Lanceur relancé entre-temps) n'est jamais enregistré
            is WidgetAddFlow.Effect.Save -> {
                if (host.info(effect.slot.appWidgetId) != null) launcherVm.addWidget(effect.slot) else host.deleteId(effect.slot.appWidgetId)
            }
            is WidgetAddFlow.Effect.Delete -> host.deleteId(effect.id)
        }
    }

    /** `startAppWidgetConfigureActivityForResult` ne renvoie son résultat que par cette méthode. */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE) runWidgetEvent(WidgetAddFlow.Event.ConfigureResult(resultCode == RESULT_OK))
    }

    private companion object {
        const val REQUEST_CONFIGURE = 41
        const val REQUEST_RECONFIGURE = 42
    }
}
```

- [ ] **Step 5: Compiler et lancer tous les tests**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 116 tests JVM réussis, la release se compile avec R8.

Run: `~/Android/Sdk/platform-tools/adb devices && JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`, 25 tests d'interface réussis : les 14 existants (dont les 3 du double toucher), plus les 11 de la tâche 7.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/lanceur/widgets/HostedWidget.kt app/src/main/java/app/lanceur/settings/SettingsScreen.kt app/src/main/java/app/lanceur/AppRoot.kt app/src/main/java/app/lanceur/MainActivity.kt
git commit -m "Widgets : assemblage (page à gauche, ajout avec autorisation et configuration, réglage)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Vérification sur le Pixel 9, release et README

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: la release signée (`assembleRelease`, clé dans `keystore.properties`).
- Produces: la release mise à jour sur le téléphone (réglages conservés) et la documentation.

- [ ] **Step 1: Mettre à jour la release en gardant les réglages**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleRelease && ~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk && ~/Android/Sdk/platform-tools/adb shell am start -n app.lanceur/.MainActivity`
Expected: `Success`, puis Lanceur s'ouvre ; `adb shell cmd role get-role-holders android.app.role.HOME` affiche toujours `app.lanceur`.

- [ ] **Step 2: Contrôle visuel**

Faire une capture (`adb exec-out screencap -p`) après un glissement vers la droite (`adb shell input swipe 100 1200 900 1200 300`). Vérifier :
- le résumé du jour ;
- « Ajoute ton premier widget » et « + Ajouter un widget » ;
- aucun plantage (`adb logcat -d -b crash | grep -c app.lanceur` vaut `0`).

- [ ] **Step 3: Liste de vérification manuelle avec l'utilisateur**

- [ ] Ajouter un widget Horloge : la fenêtre d'autorisation d'Android apparaît la première fois, puis le widget s'affiche.
- [ ] Ajouter un widget Météo ou Agenda avec un écran de configuration ; l'annuler n'ajoute rien.
- [ ] Mode édition : déplacer, passer en S, M et L, retirer.
- [ ] Cacher l'appli d'un widget : il disparaît de la page et du sélecteur, puis revient quand on la ressort.
- [ ] Réglages : désactiver la page (le glissement ne fait plus rien), la réactiver (mêmes widgets).
- [ ] Bouton Accueil depuis la page de widgets : retour à l'accueil.
- [ ] Double toucher sur une zone vide (après avoir activé le service dans Accessibilité) : l'écran se verrouille.

- [ ] **Step 4: Documenter**

Dans `README.md`, ajoute dans le tableau des gestes, après la ligne `| Double toucher sur une zone vide | Mise en veille (voir ci-dessous) |` :

```markdown
| Glisser vers la droite | Page de widgets (désactivable dans les réglages) |
```

et ajoute, juste avant `## Limites d'Android` :

```markdown
## Page de widgets

À gauche de l'accueil : le résumé du jour (prochains événements, alarme suivante), puis tes widgets en pleine largeur.
*+ Ajouter un widget* ouvre le sélecteur ; la première fois, Android demande d'autoriser Lanceur à créer des widgets.
*Modifier* permet de les déplacer (poignée), de choisir leur hauteur (S, M, L), de les reconfigurer ou de les retirer.
Les widgets d'une appli cachée ou de l'Espace privé ne sont jamais proposés ni affichés.

```

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "README : page de widgets" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
