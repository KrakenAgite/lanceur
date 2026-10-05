# Widgets intégrés, lot 1 — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter à la page de widgets cinq widgets dessinés par Lanceur : Lecture en cours, Agenda · mois, Agenda · semaine, Batterie · anneau et Batterie · barre.

**Architecture:** Un widget intégré est un `WidgetSlot` ordinaire à identifiant négatif, de fournisseur `app.lanceur.builtin/<TYPE>`. Il passe par le même stockage, le même sélecteur et le même mode édition que les widgets Android. Seul son rendu diffère : un composable à la place d'`AppWidgetHostView`. Chaque widget a une logique pure, testée sur la JVM, et une source Android fine qui expose un `Flow` ou une fonction `suspend`. Les cartes Compose sont sans état métier et testées sur l'appareil avec des états fournis.

**Tech Stack:** Kotlin 2.4.20, Compose BOM 2026.09.00, Material 3, material-icons-core 1.7.8, `lifecycle-runtime-compose`, `MediaSessionManager` + `NotificationListenerService`, `CalendarContract.Instances`, `ACTION_BATTERY_CHANGED`. Tests : JUnit 4, coroutines-test, Compose UI test v2.

**Spec:** `docs/superpowers/specs/2026-10-05-widgets-integres-lot1-design.md`

## Global Constraints

- minSdk 35, compile/targetSdk 37, namespace `app.lanceur`, version debug `app.lanceur.debug`.
- **Aucune permission Internet** ; aucune nouvelle permission dans le manifeste (le service d'écoute est protégé par `BIND_NOTIFICATION_LISTENER_SERVICE`, ce n'est pas une permission demandée).
- Format de stockage inchangé : `id|paquet/classe#série|TAILLE` ; paquet réservé `app.lanceur.builtin` ; noms d'entrée de `BuiltinKind` enregistrés tels quels, à ne jamais renommer.
- Identifiant intégré : `min(0, plus petit identifiant enregistré) - 1` ; jamais transmis à `AppWidgetHost` (ni `deleteId`, ni `createView`, ni `info`).
- Hauteurs (dp) : Lecture en cours 120/200/300 ; Agenda · mois 300/400/520 ; Agenda · semaine 140/200/280 ; Batterie · anneau 140 (taille unique) ; Batterie · barre 72 (taille unique). Taille par défaut : M pour les trois premiers, S pour la batterie.
- Textes en français. Teintes de batterie : vert `0xFF5BB974` (> 50 %), orange `0xFFF9AB00` (20–50 %), rouge `0xFFEE675C` (< 20 %).
- Commandes : `JAVA_HOME=/opt/android-studio/jbr ./gradlew …` ; adb `~/Android/Sdk/platform-tools/adb`.
- Tests unitaires : `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`.
- Tests d'interface d'une classe : `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<classe>`.
- Ne jamais afficher ni committer `keystore.properties` ou `*.jks`. Mettre à jour le téléphone avec `adb install -r` (garde les réglages).

## Review Focus

1. **Glissement horizontal sur Agenda · mois.** Le pager interne doit consommer le geste, sans que la page de widgets revienne à l'accueil. On le vérifie à la main sur le téléphone (Tâche 9) ; un test d'interface ne reproduit pas fidèlement l'imbrication des pagers.
2. **Accès aux notifications accordé puis retiré pendant que Lanceur tourne.** `getActiveSessions` lève une `SecurityException` : la carte doit passer à « Autoriser », sans planter. Couvert par le `try/catch` de la Tâche 7 et par la vérification de `hasAccess()` à chaque collecte.
3. **Taille enregistrée non permise.** Par exemple, une batterie enregistrée en `LARGE` à la main ou par une future version. L'affichage doit ramener à la taille par défaut, sans `NoSuchElementException`. Le test `WidgetLayoutTest.size_not_offered_falls_back_to_default` (Tâche 1) le couvre.
4. **Événement de plusieurs jours ou journée entière à minuit UTC.** La pastille doit marquer les bons jours, sans décalage d'un jour en France. Les tests `MonthGridTest.all_day_events_use_utc_dates` et `multi_day_event_marks_each_day` (Tâche 4) le couvrent.
5. **Retirer un widget intégré.** Cela ne doit jamais appeler `AppWidgetHost.deleteId` avec un identifiant négatif, et le nettoyage des orphelins ne doit jamais supprimer un widget Android. `removeWidget` est gardé par `BuiltinSlots.isBuiltin` (Tâche 8), et `WidgetIdsTest.builtin_ids_are_never_orphans` (Tâche 2) le couvre.

---

### Task 1: Types intégrés, identifiants et hauteurs

**Files:**
- Create: `app/src/main/java/app/lanceur/builtin/BuiltinKind.kt`
- Create: `app/src/main/java/app/lanceur/builtin/BuiltinSlots.kt`
- Create: `app/src/main/java/app/lanceur/widgets/WidgetLayout.kt`
- Test: `app/src/test/java/app/lanceur/builtin/BuiltinSlotsTest.kt`
- Test: `app/src/test/java/app/lanceur/widgets/WidgetLayoutTest.kt`

**Interfaces:**
- Produces: `enum class BuiltinKind(label: String, emoji: String, heights, defaultSize: WidgetSize)` avec `sizes: List<WidgetSize>`, `effectiveSize(WidgetSize): WidgetSize`, `heightDp(WidgetSize): Int` ; `object BuiltinSlots { PACKAGE; isBuiltin(AppKey|WidgetSlot); kindOf(AppKey|WidgetSlot): BuiltinKind?; provider(kind): AppKey; nextId(List<WidgetSlot>): Int; create(kind, slots): WidgetSlot }` ; `object WidgetLayout { heightDp(slot): Int; sizes(slot): List<WidgetSize>; displaySize(slot): WidgetSize }`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/app/lanceur/builtin/BuiltinSlotsTest.kt` :

```kotlin
package app.lanceur.builtin

import app.lanceur.apps.AppKey
import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinSlotsTest {
    private val android = WidgetSlot(7, AppKey("app.meteo", "app.meteo.Widget", 0), WidgetSize.MEDIUM)

    @Test
    fun next_id_is_negative_and_below_every_stored_id() {
        assertEquals(-1, BuiltinSlots.nextId(emptyList()))
        assertEquals(-1, BuiltinSlots.nextId(listOf(android)))
        val first = BuiltinSlots.create(BuiltinKind.BATTERY_BAR, listOf(android))
        assertEquals(-2, BuiltinSlots.nextId(listOf(android, first)))
    }

    @Test
    fun created_slot_uses_the_reserved_package_and_default_size() {
        val slot = BuiltinSlots.create(BuiltinKind.CALENDAR_MONTH, emptyList())
        assertEquals(WidgetSlot(-1, AppKey("app.lanceur.builtin", "CALENDAR_MONTH", 0), WidgetSize.MEDIUM), slot)
        assertTrue(BuiltinSlots.isBuiltin(slot))
        assertFalse(BuiltinSlots.isBuiltin(android))
        assertEquals(BuiltinKind.CALENDAR_MONTH, BuiltinSlots.kindOf(slot))
    }

    @Test
    fun survives_the_storage_round_trip() {
        val slot = BuiltinSlots.create(BuiltinKind.NOW_PLAYING, emptyList())
        assertEquals(slot, WidgetSlot.decode(slot.encode()))
    }

    @Test
    fun unknown_kind_from_a_future_version_is_builtin_without_kind() {
        val future = WidgetSlot(-4, AppKey("app.lanceur.builtin", "HOLOGRAM", 0), WidgetSize.SMALL)
        assertTrue(BuiltinSlots.isBuiltin(future))
        assertNull(BuiltinSlots.kindOf(future))
        assertNull(BuiltinSlots.kindOf(android))
    }
}
```

`app/src/test/java/app/lanceur/widgets/WidgetLayoutTest.kt` :

```kotlin
package app.lanceur.widgets

import app.lanceur.apps.AppKey
import app.lanceur.builtin.BuiltinKind
import app.lanceur.builtin.BuiltinSlots
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutTest {
    private val android = WidgetSlot(3, AppKey("app.meteo", "app.meteo.Widget", 0), WidgetSize.LARGE)
    private fun builtin(kind: BuiltinKind, size: WidgetSize) = BuiltinSlots.create(kind, emptyList()).copy(size = size)

    @Test
    fun android_widgets_keep_the_shared_heights_and_all_sizes() {
        assertEquals(340, WidgetLayout.heightDp(android))
        assertEquals(WidgetSize.entries, WidgetLayout.sizes(android))
    }

    @Test
    fun builtin_widgets_have_their_own_heights() {
        assertEquals(400, WidgetLayout.heightDp(builtin(BuiltinKind.CALENDAR_MONTH, WidgetSize.MEDIUM)))
        assertEquals(120, WidgetLayout.heightDp(builtin(BuiltinKind.NOW_PLAYING, WidgetSize.SMALL)))
        assertEquals(280, WidgetLayout.heightDp(builtin(BuiltinKind.CALENDAR_WEEK, WidgetSize.LARGE)))
    }

    @Test
    fun battery_widgets_offer_a_single_size() {
        assertEquals(listOf(WidgetSize.SMALL), WidgetLayout.sizes(builtin(BuiltinKind.BATTERY_RING, WidgetSize.SMALL)))
        assertEquals(72, WidgetLayout.heightDp(builtin(BuiltinKind.BATTERY_BAR, WidgetSize.SMALL)))
    }

    @Test
    fun size_not_offered_falls_back_to_default() {
        val tooBig = builtin(BuiltinKind.BATTERY_RING, WidgetSize.LARGE)
        assertEquals(WidgetSize.SMALL, WidgetLayout.displaySize(tooBig))
        assertEquals(140, WidgetLayout.heightDp(tooBig))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*BuiltinSlotsTest*' --tests '*WidgetLayoutTest*'`
Expected: FAIL at compilation with "Unresolved reference 'BuiltinSlots'" / "'WidgetLayout'".

- [ ] **Step 3: Write the implementation**

`app/src/main/java/app/lanceur/builtin/BuiltinKind.kt` :

```kotlin
package app.lanceur.builtin

import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSize.LARGE
import app.lanceur.widgets.WidgetSize.MEDIUM
import app.lanceur.widgets.WidgetSize.SMALL

/** Widgets dessinés par Lanceur. Le nom de chaque entrée est enregistré dans les réglages : ne jamais le renommer. */
enum class BuiltinKind(
    val label: String,
    val emoji: String,
    private val heights: Map<WidgetSize, Int>,
    val defaultSize: WidgetSize,
) {
    NOW_PLAYING("Lecture en cours", "🎵", mapOf(SMALL to 120, MEDIUM to 200, LARGE to 300), MEDIUM),
    CALENDAR_MONTH("Agenda · mois", "📅", mapOf(SMALL to 300, MEDIUM to 400, LARGE to 520), MEDIUM),
    CALENDAR_WEEK("Agenda · semaine", "🗓️", mapOf(SMALL to 140, MEDIUM to 200, LARGE to 280), MEDIUM),
    BATTERY_RING("Batterie · anneau", "🔋", mapOf(SMALL to 140), SMALL),
    BATTERY_BAR("Batterie · barre", "🔋", mapOf(SMALL to 72), SMALL),
    ;

    /** Tailles proposées en mode édition, dans l'ordre S, M, L. */
    val sizes: List<WidgetSize> get() = WidgetSize.entries.filter { it in heights }

    /** Taille enregistrée mais non proposée (réglage d'une autre version) : on affiche la taille par défaut. */
    fun effectiveSize(size: WidgetSize): WidgetSize = if (size in heights) size else defaultSize

    fun heightDp(size: WidgetSize): Int = heights.getValue(effectiveSize(size))
}
```

`app/src/main/java/app/lanceur/builtin/BuiltinSlots.kt` :

```kotlin
package app.lanceur.builtin

import app.lanceur.apps.AppKey
import app.lanceur.widgets.WidgetSlot

/**
 * Un widget intégré est un `WidgetSlot` ordinaire : identifiant négatif (Android n'en attribue jamais) et fournisseur
 * `app.lanceur.builtin/<TYPE>`. Rien ne change donc dans le stockage, l'ordre ou les tailles.
 */
object BuiltinSlots {
    const val PACKAGE = "app.lanceur.builtin"

    fun isBuiltin(key: AppKey): Boolean = key.packageName == PACKAGE

    fun isBuiltin(slot: WidgetSlot): Boolean = isBuiltin(slot.provider)

    /** `null` pour un widget Android, ou pour un type inconnu (enregistré par une version plus récente). */
    fun kindOf(key: AppKey): BuiltinKind? =
        if (isBuiltin(key)) BuiltinKind.entries.firstOrNull { it.name == key.className } else null

    fun kindOf(slot: WidgetSlot): BuiltinKind? = kindOf(slot.provider)

    fun provider(kind: BuiltinKind): AppKey = AppKey(PACKAGE, kind.name, 0)

    fun nextId(slots: List<WidgetSlot>): Int = minOf(0, slots.minOfOrNull { it.appWidgetId } ?: 0) - 1

    fun create(kind: BuiltinKind, slots: List<WidgetSlot>): WidgetSlot =
        WidgetSlot(nextId(slots), provider(kind), kind.defaultSize)
}
```

`app/src/main/java/app/lanceur/widgets/WidgetLayout.kt` :

```kotlin
package app.lanceur.widgets

import app.lanceur.builtin.BuiltinSlots

/** Hauteur et tailles proposées d'une carte : celles du type intégré, sinon celles des widgets Android. */
object WidgetLayout {
    fun heightDp(slot: WidgetSlot): Int = BuiltinSlots.kindOf(slot)?.heightDp(slot.size) ?: slot.size.heightDp

    fun sizes(slot: WidgetSlot): List<WidgetSize> = BuiltinSlots.kindOf(slot)?.sizes ?: WidgetSize.entries

    fun displaySize(slot: WidgetSlot): WidgetSize = BuiltinSlots.kindOf(slot)?.effectiveSize(slot.size) ?: slot.size
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*BuiltinSlotsTest*' --tests '*WidgetLayoutTest*'`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lanceur/builtin app/src/main/java/app/lanceur/widgets/WidgetLayout.kt app/src/test/java/app/lanceur/builtin app/src/test/java/app/lanceur/widgets/WidgetLayoutTest.kt
git commit -m "Widgets intégrés : types, identifiants négatifs et hauteurs propres"
```

---

### Task 2: Sélecteur, cartes visibles, stockage et ViewModel

**Files:**
- Modify: `app/src/main/java/app/lanceur/builtin/BuiltinSlots.kt` (ajout de `pickerEntries`)
- Modify: `app/src/main/java/app/lanceur/widgets/PickerCatalog.kt`
- Modify: `app/src/main/java/app/lanceur/widgets/VisibleWidgets.kt`
- Modify: `app/src/main/java/app/lanceur/prefs/PrefsRepo.kt`
- Modify: `app/src/main/java/app/lanceur/home/LauncherViewModel.kt`
- Test: `app/src/test/java/app/lanceur/widgets/PickerCatalogTest.kt`, `VisibleWidgetsTest.kt`, `WidgetIdsTest.kt`, `app/src/test/java/app/lanceur/prefs/PrefsRepoTest.kt`

**Interfaces:**
- Consumes: `BuiltinSlots`, `BuiltinKind` (Tâche 1).
- Produces: `BuiltinSlots.pickerEntries(): List<ProviderEntry>` (groupe « Lanceur », ordre de l'enum) ; `PickerCatalog.build(entries, hidden, query, builtins: List<ProviderEntry> = emptyList())` ; `PrefsRepo.addBuiltinWidget(kind: BuiltinKind)` ; `LauncherViewModel.addBuiltinWidget(kind: BuiltinKind)`.

- [ ] **Step 1: Write the failing tests**

Ajouter à `PickerCatalogTest` (après les tests existants, même classe) :

```kotlin
    @Test
    fun lanceur_group_comes_first_in_enum_order() {
        val groups = PickerCatalog.build(all, emptySet(), "", app.lanceur.builtin.BuiltinSlots.pickerEntries())
        assertEquals(listOf("Lanceur", "Agenda", "Éditions", "Météo"), groups.map { it.appLabel })
        assertEquals(
            listOf("Lecture en cours", "Agenda · mois", "Agenda · semaine", "Batterie · anneau", "Batterie · barre"),
            groups.first().entries.map { it.widgetLabel },
        )
    }

    @Test
    fun lanceur_widgets_follow_the_filter() {
        val groups = PickerCatalog.build(all, emptySet(), "batterie", app.lanceur.builtin.BuiltinSlots.pickerEntries())
        assertEquals(listOf("Lanceur"), groups.map { it.appLabel })
        assertEquals(2, groups.single().entries.size)
    }
```

Ajouter à `VisibleWidgetsTest` :

```kotlin
    @Test
    fun builtin_widgets_are_always_live_and_unknown_kinds_unavailable() {
        val battery = app.lanceur.builtin.BuiltinSlots.create(app.lanceur.builtin.BuiltinKind.BATTERY_BAR, emptyList())
        val future = WidgetSlot(-9, AppKey("app.lanceur.builtin", "HOLOGRAM", 0), WidgetSize.SMALL)
        val hiddenEverything = setOf(AppKey("app.lanceur.builtin", "x", 0), AppKey("app.meteo", "app.meteo.Main", 0))
        val cards = VisibleWidgets.compute(listOf(battery, meteo, future), hidden = hiddenEverything, available = emptySet())
        assertEquals(listOf(WidgetCard.Live(battery), WidgetCard.Unavailable(future)), cards)
    }
```

Ajouter à `WidgetIdsTest` (garde-fou, voir Review Focus 5) :

```kotlin
    @Test
    fun builtin_ids_are_never_orphans() {
        val builtin = app.lanceur.builtin.BuiltinSlots.create(app.lanceur.builtin.BuiltinKind.BATTERY_RING, emptyList())
        assertEquals(listOf(5), WidgetIds.orphans(intArrayOf(5), listOf(builtin)))
    }
```

(Si `WidgetIdsTest` n'importe pas `assertEquals`, ajouter `import org.junit.Assert.assertEquals`.)

Ajouter à `PrefsRepoTest` :

```kotlin
    @Test
    fun builtin_widgets_get_decreasing_negative_ids_after_android_ones() = runTest {
        val repo = PrefsRepo(store())
        repo.addWidget(slot(4))
        repo.addBuiltinWidget(app.lanceur.builtin.BuiltinKind.BATTERY_BAR)
        repo.addBuiltinWidget(app.lanceur.builtin.BuiltinKind.NOW_PLAYING)
        val widgets = repo.prefs.first().widgets
        assertEquals(listOf(4, -1, -2), widgets.map { it.appWidgetId })
        assertEquals(WidgetSize.MEDIUM, widgets.last().size)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*PickerCatalogTest*' --tests '*VisibleWidgetsTest*' --tests '*WidgetIdsTest*' --tests '*PrefsRepoTest*'`
Expected: FAIL at compilation: "Unresolved reference 'pickerEntries'", "'addBuiltinWidget'", "Too many arguments" for `build`.

- [ ] **Step 3: Write the implementation**

Dans `BuiltinSlots.kt`, ajouter les imports `app.lanceur.apps.ProfileKind`, `app.lanceur.widgets.ProviderEntry` et la fonction :

```kotlin
    /** Entrées du groupe « Lanceur » du sélecteur, dans l'ordre de `BuiltinKind`. */
    fun pickerEntries(): List<ProviderEntry> = BuiltinKind.entries.map { kind ->
        ProviderEntry(provider(kind), ProfileKind.MAIN, "Lanceur", kind.label, kind.heightDp(kind.defaultSize))
    }
```

Dans `PickerCatalog.kt`, remplacer la fonction `build` par :

```kotlin
    /** Jamais l'Espace privé ni un profil de type inconnu, jamais une appli cachée. `builtins` : groupe « Lanceur », en tête. */
    fun build(
        entries: List<ProviderEntry>,
        hidden: Set<AppKey>,
        query: String,
        builtins: List<ProviderEntry> = emptyList(),
    ): List<PickerGroup> = with(VisibleApps) {
        val hiddenPackages = hidden.mapTo(HashSet()) { it.packageInProfile() }
        val q = TextNormalizer.fold(query.trim())
        (builtins + entries)
            .filter { it.profileKind == ProfileKind.MAIN || it.profileKind == ProfileKind.OTHER }
            .filter { BuiltinSlots.isBuiltin(it.provider) || it.provider.packageInProfile() !in hiddenPackages }
            .filter { q.isEmpty() || TextNormalizer.fold(it.appLabel).contains(q) || TextNormalizer.fold(it.widgetLabel).contains(q) }
            .groupBy { it.provider.packageInProfile() }
            .values
            .map { group ->
                val first = group.first()
                val builtin = BuiltinSlots.isBuiltin(first.provider)
                PickerGroup(
                    appLabel = first.appLabel,
                    packageName = first.provider.packageName,
                    isWork = first.profileKind == ProfileKind.OTHER,
                    // Les widgets de Lanceur gardent l'ordre voulu (lecteur, agendas, batterie)
                    entries = if (builtin) group else group.sortedWith(compareBy(collator) { it.widgetLabel }),
                )
            }
            .sortedWith(
                compareBy<PickerGroup> { it.packageName != BuiltinSlots.PACKAGE }
                    .thenBy(collator) { it.appLabel }
                    .thenBy { it.isWork },
            )
    }
```

et ajouter `import app.lanceur.builtin.BuiltinSlots`.

Dans `VisibleWidgets.kt`, remplacer le corps de `compute` par :

```kotlin
    fun compute(slots: List<WidgetSlot>, hidden: Set<AppKey>, available: Set<Int>): List<WidgetCard> = with(VisibleApps) {
        val hiddenPackages = hidden.mapTo(HashSet()) { it.packageInProfile() }
        slots.filter { BuiltinSlots.isBuiltin(it) || it.provider.packageInProfile() !in hiddenPackages }
            .map { slot ->
                val live = if (BuiltinSlots.isBuiltin(slot)) BuiltinSlots.kindOf(slot) != null else slot.appWidgetId in available
                if (live) WidgetCard.Live(slot) else WidgetCard.Unavailable(slot)
            }
    }
```

compléter le commentaire KDoc : « Un widget intégré n'appartient à aucune appli : jamais caché, indisponible seulement si son type est inconnu. », et ajouter `import app.lanceur.builtin.BuiltinSlots`.

Dans `PrefsRepo.kt`, après `addWidget` :

```kotlin
    /** L'identifiant est choisi dans la même transaction que l'ajout : deux ajouts rapides n'ont jamais le même. */
    suspend fun addBuiltinWidget(kind: BuiltinKind) = update { prefs ->
        prefs.copy(widgets = prefs.widgets + BuiltinSlots.create(kind, prefs.widgets))
    }
```

avec les imports `app.lanceur.builtin.BuiltinKind` et `app.lanceur.builtin.BuiltinSlots`.

Dans `LauncherViewModel.kt`, après `addWidget` :

```kotlin
    fun addBuiltinWidget(kind: BuiltinKind) {
        viewModelScope.launch { prefsRepo.addBuiltinWidget(kind) }
    }
```

avec `import app.lanceur.builtin.BuiltinKind`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`
Expected: PASS, toute la suite JVM (y compris les anciens tests de `PickerCatalogTest`, inchangés car `builtins` vaut `emptyList()` par défaut). `builtin_ids_are_never_orphans` passe dès l'étape 2 une fois compilé : c'est un garde-fou, à noter dans le registre.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lanceur app/src/test/java/app/lanceur
git commit -m "Widgets intégrés : groupe Lanceur dans le sélecteur, toujours visibles, ajout avec identifiant négatif"
```

---

### Task 3: Batterie (logique, source, deux cartes)

**Files:**
- Create: `app/src/main/java/app/lanceur/builtin/battery/BatteryInfo.kt`
- Create: `app/src/main/java/app/lanceur/builtin/battery/BatterySource.kt`
- Create: `app/src/main/java/app/lanceur/builtin/battery/BatteryCards.kt`
- Test: `app/src/test/java/app/lanceur/builtin/battery/BatteryInfoTest.kt`
- Test: `app/src/androidTest/java/app/lanceur/builtin/battery/BatteryCardsTest.kt`

**Interfaces:**
- Produces: `data class BatteryInfo(level, status: ChargeStatus, chargeTimeRemainingMs: Long, temperatureTenths: Int?, powerSave: Boolean)` avec `tone`, `statusText`, `shortChargeText`, `temperatureText`, `powerSaveText`, `BatteryInfo.from(...)`, `BatteryInfo.duration(ms)` ; `class BatterySource(context) { val info: Flow<BatteryInfo>; fun read(): BatteryInfo }` ; `@Composable BatteryRingCard(info, onClick, modifier)` et `BatteryBarCard(info, onClick, modifier)`.

- [ ] **Step 1: Write the failing JVM test**

`app/src/test/java/app/lanceur/builtin/battery/BatteryInfoTest.kt` :

```kotlin
package app.lanceur.builtin.battery

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryInfoTest {
    private fun info(level: Int, status: ChargeStatus = ChargeStatus.DISCHARGING, remaining: Long = -1) =
        BatteryInfo(level, status, remaining, temperatureTenths = 312, powerSave = false)

    @Test
    fun tone_thresholds() {
        assertEquals(BatteryTone.GOOD, info(51).tone)
        assertEquals(BatteryTone.MEDIUM, info(50).tone)
        assertEquals(BatteryTone.MEDIUM, info(20).tone)
        assertEquals(BatteryTone.LOW, info(19).tone)
    }

    @Test
    fun durations_round_up_to_the_minute() {
        assertEquals("42 min", BatteryInfo.duration(41 * 60_000L + 1))
        assertEquals("1 h 05", BatteryInfo.duration(65 * 60_000L))
        assertEquals("1 min", BatteryInfo.duration(20_000))
    }

    @Test
    fun status_texts() {
        assertEquals("En charge · pleine dans 42 min", info(70, ChargeStatus.CHARGING, 42 * 60_000L).statusText)
        assertEquals("En charge", info(70, ChargeStatus.CHARGING).statusText)
        assertEquals("⚡ 42 min", info(70, ChargeStatus.CHARGING, 42 * 60_000L).shortChargeText)
        assertEquals("Branchée · charge en pause", info(80, ChargeStatus.PLUGGED).statusText)
        assertEquals("Sur batterie", info(70).statusText)
        assertNull(info(70).shortChargeText)
        assertEquals("Chargée", info(100, ChargeStatus.FULL).statusText)
        assertEquals("31 °C", info(70).temperatureText)
        assertEquals("Économiseur désactivé", info(70).powerSaveText)
    }

    @Test
    fun from_battery_extras() {
        val charging = BatteryInfo.from(39, 50, BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_USB, 600_000, 300, false)
        assertEquals(78, charging.level)
        assertEquals(ChargeStatus.CHARGING, charging.status)
        assertEquals(ChargeStatus.FULL, BatteryInfo.from(100, 100, BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, -1, null, false).status)
        assertEquals(ChargeStatus.PLUGGED, BatteryInfo.from(80, 100, BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, -1, null, false).status)
        assertEquals(ChargeStatus.DISCHARGING, BatteryInfo.from(80, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 0, -1, null, true).status)
        assertNull(BatteryInfo.from(80, 100, 0, 0, -1, null, false).temperatureText)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*BatteryInfoTest*'`
Expected: FAIL at compilation, "Unresolved reference 'BatteryInfo'".

- [ ] **Step 3: Write `BatteryInfo.kt`**

```kotlin
package app.lanceur.builtin.battery

import android.os.BatteryManager
import java.util.Locale

enum class ChargeStatus { CHARGING, PLUGGED, DISCHARGING, FULL }

enum class BatteryTone { GOOD, MEDIUM, LOW }

/** État de la batterie tel que les cartes l'affichent. `chargeTimeRemainingMs` : -1 si Android ne l'estime pas. */
data class BatteryInfo(
    val level: Int,
    val status: ChargeStatus,
    val chargeTimeRemainingMs: Long,
    val temperatureTenths: Int?,
    val powerSave: Boolean,
) {
    val tone: BatteryTone
        get() = when {
            level > 50 -> BatteryTone.GOOD
            level >= 20 -> BatteryTone.MEDIUM
            else -> BatteryTone.LOW
        }

    val statusText: String
        get() = when (status) {
            ChargeStatus.CHARGING ->
                if (chargeTimeRemainingMs > 0) "En charge · pleine dans ${duration(chargeTimeRemainingMs)}" else "En charge"
            // Charge adaptative du Pixel : branché, mais la charge attend
            ChargeStatus.PLUGGED -> "Branchée · charge en pause"
            ChargeStatus.DISCHARGING -> "Sur batterie"
            ChargeStatus.FULL -> "Chargée"
        }

    /** Pour la barre : « ⚡ 42 min », « ⚡ » sans estimation, rien hors charge. */
    val shortChargeText: String?
        get() = if (status != ChargeStatus.CHARGING) null
        else if (chargeTimeRemainingMs > 0) "⚡ ${duration(chargeTimeRemainingMs)}" else "⚡"

    val temperatureText: String? get() = temperatureTenths?.let { "${Math.round(it / 10.0)} °C" }

    val powerSaveText: String get() = if (powerSave) "Économiseur activé" else "Économiseur désactivé"

    companion object {
        /** Arrondi à la minute supérieure : « 42 min », « 1 h 05 ». */
        fun duration(ms: Long): String {
            val minutes = ((ms + 59_999) / 60_000).coerceAtLeast(1)
            return if (minutes < 60) "$minutes min" else String.format(Locale.ROOT, "%d h %02d", minutes / 60, minutes % 60)
        }

        /** Extras de `ACTION_BATTERY_CHANGED`. `scale` ≤ 0 : `level` est déjà un pourcentage. */
        fun from(
            level: Int,
            scale: Int,
            status: Int,
            plugged: Int,
            chargeTimeRemainingMs: Long,
            temperatureTenths: Int?,
            powerSave: Boolean,
        ): BatteryInfo {
            val percent = (if (scale > 0) level * 100 / scale else level).coerceIn(0, 100)
            val charge = when {
                status == BatteryManager.BATTERY_STATUS_FULL || (plugged != 0 && percent >= 100) -> ChargeStatus.FULL
                status == BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.CHARGING
                plugged != 0 -> ChargeStatus.PLUGGED
                else -> ChargeStatus.DISCHARGING
            }
            return BatteryInfo(percent, charge, chargeTimeRemainingMs, temperatureTenths, powerSave)
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*BatteryInfoTest*'`
Expected: PASS (4 tests).

- [ ] **Step 5: Write `BatterySource.kt`**

```kotlin
package app.lanceur.builtin.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Batterie en direct, sans permission. N'écoute que tant qu'une carte collecte `info`. */
class BatterySource(private val context: Context) {
    val info: Flow<BatteryInfo> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(read())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        // Diffusions du système : reçues même sans exportation
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        trySend(read())
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate()

    /** Lecture immédiate : `ACTION_BATTERY_CHANGED` est collant, l'enregistrement sans récepteur rend le dernier état. */
    fun read(): BatteryInfo {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val manager = context.getSystemService(BatteryManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        return BatteryInfo.from(
            level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 }
                ?: manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1,
            status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0,
            plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0,
            chargeTimeRemainingMs = manager.computeChargeTimeRemaining(),
            temperatureTenths = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE },
            powerSave = power.isPowerSaveMode,
        )
    }
}
```

- [ ] **Step 6: Write the failing UI test**

`app/src/androidTest/java/app/lanceur/builtin/battery/BatteryCardsTest.kt` :

```kotlin
package app.lanceur.builtin.battery

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BatteryCardsTest {
    @get:Rule val rule = createComposeRule()

    private val charging = BatteryInfo(78, ChargeStatus.CHARGING, 42 * 60_000L, 312, powerSave = false)

    @Test
    fun ring_shows_level_status_temperature_and_opens_settings() {
        var clicked = false
        rule.setContent { MaterialTheme { BatteryRingCard(charging, onClick = { clicked = true }) } }
        rule.onNodeWithText("78 %").assertIsDisplayed()
        rule.onNodeWithText("En charge · pleine dans 42 min").assertIsDisplayed()
        rule.onNodeWithText("31 °C").assertIsDisplayed()
        rule.onNodeWithText("Économiseur désactivé").assertIsDisplayed()
        rule.onNodeWithText("78 %").performClick()
        assertTrue(clicked)
    }

    @Test
    fun bar_shows_level_and_short_charge_time() {
        rule.setContent { MaterialTheme { BatteryBarCard(charging.copy(level = 15), onClick = {}) } }
        rule.onNodeWithText("15 %").assertIsDisplayed()
        rule.onNodeWithText("⚡ 42 min").assertIsDisplayed()
    }
}
```

- [ ] **Step 7: Run it to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.builtin.battery.BatteryCardsTest`
Expected: FAIL at compilation, "Unresolved reference 'BatteryRingCard'".

- [ ] **Step 8: Write `BatteryCards.kt`**

```kotlin
package app.lanceur.builtin.battery

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal fun BatteryTone.color(): Color = when (this) {
    BatteryTone.GOOD -> Color(0xFF5BB974)
    BatteryTone.MEDIUM -> Color(0xFFF9AB00)
    BatteryTone.LOW -> Color(0xFFEE675C)
}

/** En charge, la couleur respire doucement ; sinon elle reste pleine. */
@Composable
private fun chargePulse(info: BatteryInfo): Float {
    if (info.status != ChargeStatus.CHARGING) return 1f
    val transition = rememberInfiniteTransition(label = "charge")
    val alpha by transition.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulsation")
    return alpha
}

@Composable
fun BatteryRingCard(info: BatteryInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tone = info.tone.color()
    val track = colors.surfaceContainerHighest
    val sweep by animateFloatAsState(info.level * 3.6f, label = "niveau")
    val pulse = chargePulse(info)
    Row(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(104.dp).drawBehind {
                val stroke = 10.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                drawArc(
                    tone.copy(alpha = pulse), -90f, sweep, useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${info.level} %", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (info.status == ChargeStatus.CHARGING) Text("⚡", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(info.statusText, style = MaterialTheme.typography.titleMedium)
            info.temperatureText?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            Text(info.powerSaveText, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun BatteryBarCard(info: BatteryInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill by animateFloatAsState(info.level / 100f, label = "niveau")
    val pulse = chargePulse(info)
    Row(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pile dessinée : corps bordé, remplissage à la couleur du niveau, petit téton à droite
        Box(
            Modifier
                .size(width = 64.dp, height = 28.dp)
                .border(2.dp, colors.onSurfaceVariant, RoundedCornerShape(7.dp))
                .padding(4.dp),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill.coerceIn(0.04f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(info.tone.color().copy(alpha = pulse)),
            )
        }
        Box(Modifier.padding(start = 2.dp).size(width = 4.dp, height = 12.dp).clip(RoundedCornerShape(2.dp)).background(colors.onSurfaceVariant))
        Spacer(Modifier.width(16.dp))
        Text("${info.level} %", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        info.shortChargeText?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant) }
    }
}
```

- [ ] **Step 9: Run the UI test to verify it passes**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.builtin.battery.BatteryCardsTest`
Expected: PASS (2 tests on Pixel 9).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/app/lanceur/builtin/battery app/src/test/java/app/lanceur/builtin/battery app/src/androidTest/java/app/lanceur/builtin/battery
git commit -m "Widgets batterie : état, source en direct, anneau et barre"
```

---

### Task 4: Agendas — requête partagée, grille du mois, bandeau de semaine (pur)

**Files:**
- Create: `app/src/main/java/app/lanceur/summary/CalendarEvents.kt`
- Modify: `app/src/main/java/app/lanceur/summary/DaySummarySource.kt`
- Modify: `app/src/main/java/app/lanceur/summary/DaySummary.kt` (ajout de `lineFor`)
- Create: `app/src/main/java/app/lanceur/builtin/calendar/EventDays.kt`
- Create: `app/src/main/java/app/lanceur/builtin/calendar/MonthGrid.kt`
- Create: `app/src/main/java/app/lanceur/builtin/calendar/WeekStrip.kt`
- Create: `app/src/main/java/app/lanceur/builtin/calendar/CalendarRangeSource.kt`
- Test: `app/src/test/java/app/lanceur/builtin/calendar/MonthGridTest.kt`
- Test: `app/src/test/java/app/lanceur/builtin/calendar/WeekStripTest.kt`

**Interfaces:**
- Consumes: `SummaryEvent(eventId, title, begin, end, allDay, color)`, `SummaryLine`, `DaySummary.isBirthday`, `DaySummary.shortBirthdayTitle`.
- Produces: `CalendarEvents.query(context, begin, end): List<SummaryEvent>` ; `DaySummary.lineFor(event, zone): SummaryLine` ; `EventDays.of(event, zone): List<LocalDate>` ; `GridDay`, `MonthGridState`, `MonthGrid.build(month, today, events, zone)`, `MonthGrid.title(month)`, `MonthGrid.dayLines(day, events, zone)` ; `WeekItem`, `WeekDay`, `WeekStripState`, `WeekStrip.weekStart(date)`, `WeekStrip.perDay(size)`, `WeekStrip.build(weekStart, today, events, zone, perDay)` ; `data class CalendarLoad(granted, events)`, `CalendarRangeSource.load(begin: LocalDate, endExclusive: LocalDate): CalendarLoad`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/app/lanceur/builtin/calendar/MonthGridTest.kt` :

```kotlin
package app.lanceur.builtin.calendar

import app.lanceur.summary.SummaryEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthGridTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 10, 5)
    private fun at(day: Int, hour: Int, month: Int = 10) = LocalDateTime.of(2026, month, day, hour, 0).atZone(paris).toInstant().toEpochMilli()
    private fun utcDay(day: Int) = LocalDate.of(2026, 10, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun timed(id: Long, title: String, day: Int, hour: Int, color: Int? = null) =
        SummaryEvent(id, title, at(day, hour), at(day, hour + 1), false, color)
    private fun allDay(id: Long, title: String, from: Int, toExclusive: Int) =
        SummaryEvent(id, title, utcDay(from), utcDay(toExclusive), true)

    @Test
    fun october_2026_starts_on_thursday_with_monday_first_weeks() {
        val grid = MonthGrid.build(YearMonth.of(2026, 10), today, emptyList(), paris)
        assertEquals("octobre 2026", grid.title)
        assertEquals(5, grid.weeks.size)
        assertEquals(LocalDate.of(2026, 9, 28), grid.weeks.first().first().date)
        assertEquals(3, grid.weeks.first().count { !it.inMonth })
        assertEquals(LocalDate.of(2026, 11, 1), grid.weeks.last().last().date)
        assertTrue(grid.weeks.flatten().single { it.isToday }.date == today)
    }

    @Test
    fun august_2026_needs_six_weeks() {
        assertEquals(6, MonthGrid.build(YearMonth.of(2026, 8), today, emptyList(), paris).weeks.size)
    }

    @Test
    fun at_most_three_distinct_colors_per_day() {
        val events = listOf(1, 2, 3, 4, 1).mapIndexed { i, c -> timed(i.toLong(), "E$i", 14, 8 + i, color = c) }
        val day = MonthGrid.build(YearMonth.of(2026, 10), today, events, paris).weeks.flatten().first { it.date.dayOfMonth == 14 && it.inMonth }
        assertEquals(listOf<Int?>(1, 2, 3), day.colors)
    }

    @Test
    fun all_day_events_use_utc_dates() {
        val grid = MonthGrid.build(YearMonth.of(2026, 10), today, listOf(allDay(1, "Férié", 12, 13)), paris)
        val marked = grid.weeks.flatten().filter { it.colors.isNotEmpty() }.map { it.date.dayOfMonth }
        assertEquals(listOf(12), marked)
    }

    @Test
    fun multi_day_event_marks_each_day() {
        val grid = MonthGrid.build(YearMonth.of(2026, 10), today, listOf(allDay(1, "Vacances", 19, 22)), paris)
        assertEquals(listOf(19, 20, 21), grid.weeks.flatten().filter { it.colors.isNotEmpty() }.map { it.date.dayOfMonth })
    }

    @Test
    fun birthday_is_a_gift_not_a_dot() {
        val grid = MonthGrid.build(YearMonth.of(2026, 10), today, listOf(allDay(1, "Léa - Anniversaire", 17, 18)), paris)
        val day = grid.weeks.flatten().first { it.date.dayOfMonth == 17 && it.inMonth }
        assertTrue(day.birthday)
        assertTrue(day.colors.isEmpty())
        assertFalse(grid.weeks.flatten().first { it.date.dayOfMonth == 16 && it.inMonth }.birthday)
    }

    @Test
    fun day_lines_put_birthdays_first_then_all_day_then_by_time() {
        val events = listOf(timed(1, "Sport", 14, 18), allDay(2, "Férié", 14, 15), timed(3, "Dentiste", 14, 9), allDay(4, "Léa - Anniversaire", 14, 15))
        val lines = MonthGrid.dayLines(LocalDate.of(2026, 10, 14), events, paris)
        assertEquals(listOf("Léa", "Férié", "Dentiste", "Sport"), lines.map { it.title })
        assertEquals(listOf("🎁", "Journée", "09:00", "18:00"), lines.map { it.timeLabel })
    }
}
```

`app/src/test/java/app/lanceur/builtin/calendar/WeekStripTest.kt` :

```kotlin
package app.lanceur.builtin.calendar

import app.lanceur.summary.SummaryEvent
import app.lanceur.widgets.WidgetSize
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekStripTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val monday = LocalDate.of(2026, 10, 5)
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(paris).toInstant().toEpochMilli()
    private fun timed(id: Long, title: String, day: Int, hour: Int) = SummaryEvent(id, title, at(day, hour), at(day, hour + 1), false, 7)
    private fun utcDay(day: Int) = LocalDate.of(2026, 10, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun week_starts_on_monday() {
        assertEquals(monday, WeekStrip.weekStart(LocalDate.of(2026, 10, 8)))
        assertEquals(monday, WeekStrip.weekStart(monday))
        assertEquals(monday, WeekStrip.weekStart(LocalDate.of(2026, 10, 11)))
    }

    @Test
    fun seven_days_with_labels_today_and_title() {
        val strip = WeekStrip.build(monday, LocalDate.of(2026, 10, 7), emptyList(), paris, perDay = 2)
        assertEquals(listOf("L", "M", "M", "J", "V", "S", "D"), strip.days.map { it.label })
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11), strip.days.map { it.date.dayOfMonth })
        assertTrue(strip.days[2].isToday)
        assertEquals("5 – 11 octobre", strip.title)
        assertEquals("28 sept. – 4 oct.", WeekStrip.build(LocalDate.of(2026, 9, 28), monday, emptyList(), paris, 2).title)
    }

    @Test
    fun extra_events_become_plus_n_and_birthdays_go_to_the_header() {
        val events = listOf(timed(1, "C", 6, 15), timed(2, "A", 6, 8), timed(3, "B", 6, 10), SummaryEvent(4, "Léa - Anniversaire", utcDay(6), utcDay(7), true))
        val tuesday = WeekStrip.build(monday, monday, events, paris, perDay = 2).days[1]
        assertEquals(listOf("A", "B"), tuesday.items.map { it.title })
        assertEquals(1, tuesday.more)
        assertEquals(listOf("Léa"), tuesday.birthdays)
    }

    @Test
    fun events_per_day_follow_the_size() {
        assertEquals(listOf(2, 4, 6), WidgetSize.entries.map(WeekStrip::perDay))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*MonthGridTest*' --tests '*WeekStripTest*'`
Expected: FAIL at compilation, "Unresolved reference 'MonthGrid'" / "'WeekStrip'".

- [ ] **Step 3: Extract the shared calendar query**

`app/src/main/java/app/lanceur/summary/CalendarEvents.kt` :

```kotlin
package app.lanceur.summary

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Instances

/** Occurrences d'événements entre `begin` et `end` (ms), triées par début. Hors du fil principal, permission accordée. */
object CalendarEvents {
    fun query(context: Context, begin: Long, end: Long): List<SummaryEvent> {
        val uri = Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, begin); ContentUris.appendId(it, end) }
            .build()
        val projection = arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.DISPLAY_COLOR)
        val events = mutableListOf<SummaryEvent>()
        context.contentResolver.query(uri, projection, null, null, "${Instances.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                events += SummaryEvent(
                    eventId = cursor.getLong(0),
                    title = cursor.getString(1).orEmpty(),
                    begin = cursor.getLong(2),
                    end = cursor.getLong(3),
                    allDay = cursor.getInt(4) == 1,
                    color = if (cursor.isNull(5)) null else cursor.getInt(5),
                )
            }
        }
        return events
    }
}
```

Dans `DaySummarySource.kt`, remplacer tout le corps de `queryEvents` par :

```kotlin
    private fun queryEvents(now: ZonedDateTime): List<SummaryEvent> {
        // Fenêtre élargie d'un jour de chaque côté : les événements « toute la journée » sont en UTC ; DaySummary trie
        val begin = now.minusDays(1).toInstant().toEpochMilli()
        val end = now.toLocalDate().plusDays(3).atStartOfDay(now.zone).toInstant().toEpochMilli()
        return CalendarEvents.query(context, begin, end)
    }
```

et retirer les imports devenus inutiles (`ContentUris`, `Instances`).

Dans `DaySummary.kt`, juste après la fonction privée `line(...)`, ajouter :

```kotlin
    /** Une ligne de chronologie hors résumé (agendas intégrés) : mêmes libellés, 🎁 et nom court. */
    fun lineFor(event: SummaryEvent, zone: ZoneId): SummaryLine = line(tomorrow = false, event = event, zone = zone)
```

- [ ] **Step 4: Write `EventDays.kt`, `MonthGrid.kt`, `WeekStrip.kt`, `CalendarRangeSource.kt`**

`EventDays.kt` :

```kotlin
package app.lanceur.builtin.calendar

import app.lanceur.summary.SummaryEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal object EventDays {
    private const val MAX_DAYS = 62L

    /** Jours couverts. Les journées entières sont enregistrées en UTC par Android : les lire dans le fuseau décalerait d'un jour. */
    fun of(event: SummaryEvent, zone: ZoneId): List<LocalDate> {
        val z = if (event.allDay) ZoneOffset.UTC else zone
        val first = Instant.ofEpochMilli(event.begin).atZone(z).toLocalDate()
        val lastMillis = if (event.end > event.begin) event.end - 1 else event.begin
        val last = Instant.ofEpochMilli(lastMillis).atZone(z).toLocalDate()
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.take(MAX_DAYS.toInt()).toList()
    }

    fun byDay(events: List<SummaryEvent>, zone: ZoneId): Map<LocalDate, List<SummaryEvent>> {
        val out = HashMap<LocalDate, MutableList<SummaryEvent>>()
        events.forEach { event -> of(event, zone).forEach { out.getOrPut(it) { mutableListOf() } += event } }
        return out
    }
}
```

`MonthGrid.kt` :

```kotlin
package app.lanceur.builtin.calendar

import app.lanceur.summary.DaySummary
import app.lanceur.summary.SummaryEvent
import app.lanceur.summary.SummaryLine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** `colors` : couleurs d'agenda distinctes (`null` = couleur du thème), au plus 3 ; un anniversaire donne 🎁 à la place. */
data class GridDay(val date: LocalDate, val inMonth: Boolean, val isToday: Boolean, val colors: List<Int?>, val birthday: Boolean)

data class MonthGridState(val month: YearMonth, val title: String, val weeks: List<List<GridDay>>)

object MonthGrid {
    const val MAX_DOTS = 3
    private val TITLE = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.FRENCH)

    fun build(month: YearMonth, today: LocalDate, events: List<SummaryEvent>, zone: ZoneId): MonthGridState {
        val byDay = EventDays.byDay(events, zone)
        val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { date ->
            val (birthdays, others) = byDay[date].orEmpty().partition { DaySummary.isBirthday(it.title, it.allDay) }
            GridDay(
                date = date,
                inMonth = YearMonth.from(date) == month,
                isToday = date == today,
                colors = others.map { it.color }.distinct().take(MAX_DOTS),
                birthday = birthdays.isNotEmpty(),
            )
        }.toList()
        return MonthGridState(month, title(month), days.chunked(7))
    }

    /** « octobre 2026 » (minuscule, à la française). */
    fun title(month: YearMonth): String = TITLE.format(month)

    /** Chronologie d'un jour : anniversaires, puis journées entières, puis par heure. */
    fun dayLines(day: LocalDate, events: List<SummaryEvent>, zone: ZoneId): List<SummaryLine> =
        events.filter { day in EventDays.of(it, zone) }
            .map { DaySummary.lineFor(it, zone) }
            .sortedWith(compareByDescending<SummaryLine> { it.birthday }.thenByDescending { it.event.allDay }.thenBy { it.event.begin })
}
```

`WeekStrip.kt` :

```kotlin
package app.lanceur.builtin.calendar

import app.lanceur.summary.DaySummary
import app.lanceur.summary.SummaryEvent
import app.lanceur.widgets.WidgetSize
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class WeekItem(val event: SummaryEvent, val title: String, val color: Int?)

/** `birthdays` : noms courts, affichés avec 🎁 en tête de colonne ; `more` : événements qui ne tiennent pas. */
data class WeekDay(
    val date: LocalDate,
    val label: String,
    val isToday: Boolean,
    val birthdays: List<String>,
    val items: List<WeekItem>,
    val more: Int,
)

data class WeekStripState(val title: String, val days: List<WeekDay>)

object WeekStrip {
    private val LABELS = listOf("L", "M", "M", "J", "V", "S", "D")
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)
    private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun perDay(size: WidgetSize): Int = when (size) {
        WidgetSize.SMALL -> 2
        WidgetSize.MEDIUM -> 4
        WidgetSize.LARGE -> 6
    }

    fun build(weekStart: LocalDate, today: LocalDate, events: List<SummaryEvent>, zone: ZoneId, perDay: Int): WeekStripState {
        val byDay = EventDays.byDay(events, zone)
        val days = (0L until 7L).map { offset ->
            val date = weekStart.plusDays(offset)
            val (birthdays, others) = byDay[date].orEmpty().partition { DaySummary.isBirthday(it.title, it.allDay) }
            val sorted = others.sortedWith(compareByDescending<SummaryEvent> { it.allDay }.thenBy { it.begin })
            WeekDay(
                date = date,
                label = LABELS[offset.toInt()],
                isToday = date == today,
                birthdays = birthdays.map { DaySummary.shortBirthdayTitle(it.title) },
                items = sorted.take(perDay).map { WeekItem(it, it.title.ifBlank { "(Sans titre)" }, it.color) },
                more = (sorted.size - perDay).coerceAtLeast(0),
            )
        }
        val end = weekStart.plusDays(6)
        val title = if (end.month == weekStart.month) "${weekStart.dayOfMonth} – ${DAY_MONTH.format(end)}"
        else "${SHORT.format(weekStart)} – ${SHORT.format(end)}"
        return WeekStripState(title, days)
    }
}
```

`CalendarRangeSource.kt` :

```kotlin
package app.lanceur.builtin.calendar

import android.Manifest
import android.content.Context
import app.lanceur.search.SearchPermissions
import app.lanceur.summary.CalendarEvents
import app.lanceur.summary.SummaryEvent
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CalendarLoad(val granted: Boolean, val events: List<SummaryEvent>)

class CalendarRangeSource(private val context: Context) {
    /** Événements touchant [begin, endExclusive[. Un jour de marge de chaque côté : les journées entières sont en UTC. */
    suspend fun load(begin: LocalDate, endExclusive: LocalDate, zone: ZoneId = ZoneId.systemDefault()): CalendarLoad =
        withContext(Dispatchers.IO) {
            if (!SearchPermissions.granted(context, Manifest.permission.READ_CALENDAR)) return@withContext CalendarLoad(false, emptyList())
            val from = begin.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = endExclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            CalendarLoad(true, runCatching { CalendarEvents.query(context, from, to) }.getOrDefault(emptyList()))
        }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`
Expected: PASS, toute la suite JVM (dont `DaySummaryTest`, qui couvre le résumé après extraction de la requête).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/lanceur/summary app/src/main/java/app/lanceur/builtin/calendar app/src/test/java/app/lanceur/builtin/calendar
git commit -m "Agendas intégrés : requête partagée, grille du mois et bandeau de semaine"
```

---

### Task 5: Cartes Agenda · mois et Agenda · semaine

**Files:**
- Modify: `app/src/main/java/app/lanceur/widgets/WidgetPage.kt` (`TimelineRow` passe de `private` à `internal`)
- Create: `app/src/main/java/app/lanceur/builtin/calendar/CalendarCards.kt`
- Test: `app/src/androidTest/java/app/lanceur/builtin/calendar/CalendarCardsTest.kt`

**Interfaces:**
- Consumes: `MonthGrid`, `WeekStrip`, `CalendarLoad` (Tâche 4) ; `TimelineRow(line: SummaryLine, isLast: Boolean, onClick: () -> Unit)` (WidgetPage).
- Produces: `class CalendarCardActions(openEvent: (SummaryEvent) -> Unit, openDay: (LocalDate) -> Unit, requestCalendar: () -> Unit)` ; `@Composable MonthCard(today, size, load: CalendarLoad?, onMonthShown: (YearMonth) -> Unit, actions, modifier, zone)` ; `@Composable WeekCard(today, weekStart, size, load: CalendarLoad?, onWeekChange: (LocalDate) -> Unit, actions, modifier, zone)`.

- [ ] **Step 1: Write the failing UI test**

`app/src/androidTest/java/app/lanceur/builtin/calendar/CalendarCardsTest.kt` :

```kotlin
package app.lanceur.builtin.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.lanceur.summary.SummaryEvent
import app.lanceur.widgets.WidgetSize
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalendarCardsTest {
    @get:Rule val rule = createComposeRule()

    private val paris = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 10, 5)
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(paris).toInstant().toEpochMilli()
    private val dentiste = SummaryEvent(1, "Dentiste", at(14, 14), at(14, 15), false, 0xFF3366CC.toInt())
    private val sport = SummaryEvent(2, "Sport", at(7, 18), at(7, 19), false)
    private val load = CalendarLoad(true, listOf(dentiste, sport))

    @Test
    fun month_shows_the_title_and_a_tapped_day_lists_its_events() {
        var shown: YearMonth? = null
        var opened: SummaryEvent? = null
        rule.setContent {
            MaterialTheme {
                Box(Modifier.height(400.dp)) {
                    MonthCard(today, WidgetSize.MEDIUM, load, onMonthShown = { shown = it }, actions = CalendarCardActions(openEvent = { opened = it }), zone = paris)
                }
            }
        }
        rule.onNodeWithText("octobre 2026").assertIsDisplayed()
        rule.onNodeWithTag("day-2026-10-14").performClick()
        rule.onNodeWithText("Dentiste").performClick()
        assertEquals(dentiste, opened)
        rule.onNodeWithContentDescription("Mois suivant").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("novembre 2026").assertIsDisplayed()
        assertEquals(YearMonth.of(2026, 11), shown)
    }

    @Test
    fun month_without_permission_offers_to_allow() {
        var asked = false
        rule.setContent {
            MaterialTheme {
                Box(Modifier.height(400.dp)) {
                    MonthCard(today, WidgetSize.MEDIUM, CalendarLoad(false, emptyList()), {}, CalendarCardActions(requestCalendar = { asked = true }), zone = paris)
                }
            }
        }
        rule.onNodeWithText("Autoriser l'agenda").performClick()
        assertEquals(true, asked)
    }

    @Test
    fun week_shows_title_events_and_opens_a_day() {
        var day: LocalDate? = null
        var week: LocalDate? = null
        rule.setContent {
            MaterialTheme {
                Box(Modifier.height(200.dp)) {
                    WeekCard(today, today, WidgetSize.MEDIUM, load, onWeekChange = { week = it }, actions = CalendarCardActions(openDay = { day = it }), zone = paris)
                }
            }
        }
        rule.onNodeWithText("5 – 11 octobre").assertIsDisplayed()
        rule.onNodeWithText("Sport").assertIsDisplayed()
        rule.onNodeWithTag("week-2026-10-07").performClick()
        assertEquals(LocalDate.of(2026, 10, 7), day)
        rule.onNodeWithContentDescription("Semaine suivante").performClick()
        assertEquals(LocalDate.of(2026, 10, 12), week)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.builtin.calendar.CalendarCardsTest`
Expected: FAIL at compilation, "Unresolved reference 'MonthCard'".

- [ ] **Step 3: Make `TimelineRow` reusable**

Dans `WidgetPage.kt`, remplacer `private fun TimelineRow(` par `internal fun TimelineRow(`.

- [ ] **Step 4: Write `CalendarCards.kt`**

```kotlin
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
```

- [ ] **Step 5: Run the UI test to verify it passes**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.builtin.calendar.CalendarCardsTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/lanceur/widgets/WidgetPage.kt app/src/main/java/app/lanceur/builtin/calendar/CalendarCards.kt app/src/androidTest/java/app/lanceur/builtin/calendar
git commit -m "Cartes Agenda · mois (pager de mois, jour choisi) et Agenda · semaine"
```

---

### Task 6: Lecture en cours — logique pure

**Files:**
- Create: `app/src/main/java/app/lanceur/builtin/media/NowPlaying.kt`
- Test: `app/src/test/java/app/lanceur/builtin/media/NowPlayingTest.kt`

**Interfaces:**
- Produces: `data class MediaSnapshot(packageName, appLabel, title: String?, artist: String?, art: ImageBitmap?, durationMs: Long, positionMs: Long, updatedAtElapsed: Long, speed: Float, isPlaying: Boolean, canPrevious: Boolean, canNext: Boolean, canSeek: Boolean)` ; `sealed interface NowPlayingState { NoAccess; Idle; Active(media) }` ; `NowPlaying.pick(List<MediaSnapshot>)`, `NowPlaying.positionAt(media, nowElapsed)`, `NowPlaying.timeLabel(ms)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.lanceur.builtin.media

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingTest {
    private fun media(pkg: String, playing: Boolean, title: String? = "Titre", position: Long = 10_000, duration: Long = 200_000, speed: Float = 1f) =
        MediaSnapshot(pkg, pkg, title, "Artiste", null, duration, position, updatedAtElapsed = 1_000, speed = speed, isPlaying = playing, canPrevious = true, canNext = true, canSeek = true)

    @Test
    fun picks_the_playing_session_then_the_most_recent_one() {
        val paused = media("a", playing = false)
        val playing = media("b", playing = true)
        assertEquals(NowPlayingState.Active(playing), NowPlaying.pick(listOf(paused, playing)))
        assertEquals(NowPlayingState.Active(paused), NowPlaying.pick(listOf(paused, media("c", playing = false))))
        assertEquals(NowPlayingState.Idle, NowPlaying.pick(emptyList()))
    }

    @Test
    fun paused_sessions_without_title_are_ignored() {
        assertEquals(NowPlayingState.Idle, NowPlaying.pick(listOf(media("a", playing = false, title = null))))
        assertEquals(NowPlayingState.Idle, NowPlaying.pick(listOf(media("a", playing = false, title = " "))))
    }

    @Test
    fun position_advances_while_playing_and_stays_in_bounds() {
        assertEquals(15_000, NowPlaying.positionAt(media("a", true), nowElapsed = 6_000))
        assertEquals(20_000, NowPlaying.positionAt(media("a", true, speed = 2f), nowElapsed = 6_000))
        assertEquals(10_000, NowPlaying.positionAt(media("a", false), nowElapsed = 60_000))
        assertEquals(200_000, NowPlaying.positionAt(media("a", true), nowElapsed = 10_000_000))
        assertEquals(0, NowPlaying.positionAt(media("a", true, position = -5_000, speed = 0f), nowElapsed = 1_000))
    }

    @Test
    fun time_labels() {
        assertEquals("1:42", NowPlaying.timeLabel(102_000))
        assertEquals("0:05", NowPlaying.timeLabel(5_900))
        assertEquals("1:02:03", NowPlaying.timeLabel(3_723_000))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*NowPlayingTest*'`
Expected: FAIL at compilation, "Unresolved reference 'MediaSnapshot'".

- [ ] **Step 3: Write `NowPlaying.kt`**

```kotlin
package app.lanceur.builtin.media

import androidx.compose.ui.graphics.ImageBitmap
import java.util.Locale

/** Une session média à un instant. `updatedAtElapsed` : horloge `elapsedRealtime` de la dernière position connue. */
data class MediaSnapshot(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val artist: String?,
    val art: ImageBitmap?,
    val durationMs: Long,
    val positionMs: Long,
    val updatedAtElapsed: Long,
    val speed: Float,
    val isPlaying: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val canSeek: Boolean,
)

sealed interface NowPlayingState {
    data object NoAccess : NowPlayingState
    data object Idle : NowPlayingState
    data class Active(val media: MediaSnapshot) : NowPlayingState
}

object NowPlaying {
    /** Celle qui joue ; sinon la plus récente (Android les range ainsi) qui a un titre ; sinon rien. */
    fun pick(sessions: List<MediaSnapshot>): NowPlayingState {
        val chosen = sessions.firstOrNull { it.isPlaying } ?: sessions.firstOrNull { !it.title.isNullOrBlank() }
        return chosen?.let { NowPlayingState.Active(it) } ?: NowPlayingState.Idle
    }

    /** Position extrapolée entre deux mises à jour du lecteur, pour une barre qui avance sans attendre. */
    fun positionAt(media: MediaSnapshot, nowElapsed: Long): Long {
        val moved = if (media.isPlaying) ((nowElapsed - media.updatedAtElapsed) * media.speed).toLong() else 0L
        val position = (media.positionMs + moved).coerceAtLeast(0)
        return if (media.durationMs > 0) position.coerceAtMost(media.durationMs) else position
    }

    fun timeLabel(ms: Long): String {
        val total = ms.coerceAtLeast(0) / 1000
        val hours = total / 3600
        val minutes = total % 3600 / 60
        val seconds = total % 60
        return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*NowPlayingTest*'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lanceur/builtin/media/NowPlaying.kt app/src/test/java/app/lanceur/builtin/media
git commit -m "Lecture en cours : choix de la session et position extrapolée"
```

---

### Task 7: Lecture en cours — service, source, carte

**Files:**
- Create: `app/src/main/java/app/lanceur/builtin/media/MediaListener.kt`
- Create: `app/src/main/java/app/lanceur/builtin/media/NowPlayingSource.kt`
- Create: `app/src/main/java/app/lanceur/builtin/media/MediaIcons.kt`
- Create: `app/src/main/java/app/lanceur/builtin/media/NowPlayingCard.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/app/lanceur/builtin/media/NowPlayingCardTest.kt`

**Interfaces:**
- Consumes: `MediaSnapshot`, `NowPlayingState`, `NowPlaying` (Tâche 6).
- Produces: `class NowPlayingSource(context) { val state: Flow<NowPlayingState>; fun hasAccess(); fun playPause(); fun next(); fun previous(); fun seekTo(ms: Long); fun open(): Boolean; fun accessSettingsIntent(): Intent }` ; `class NowPlayingActions(playPause, next, previous, seekTo: (Long) -> Unit, open, grantAccess)` ; `@Composable NowPlayingCard(state, size, actions, modifier, clock: () -> Long)`.

- [ ] **Step 1: Write the failing UI test**

```kotlin
package app.lanceur.builtin.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.lanceur.widgets.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NowPlayingCardTest {
    @get:Rule val rule = createComposeRule()

    private val song = MediaSnapshot(
        "com.spotify.music", "Spotify", "Bohemian Rhapsody", "Queen", null,
        durationMs = 355_000, positionMs = 102_000, updatedAtElapsed = 0, speed = 1f, isPlaying = true,
        canPrevious = true, canNext = false, canSeek = true,
    )

    private fun show(state: NowPlayingState, actions: NowPlayingActions) = rule.setContent {
        MaterialTheme { Box(Modifier.height(200.dp)) { NowPlayingCard(state, WidgetSize.MEDIUM, actions, clock = { 0L }) } }
    }

    @Test
    fun active_shows_the_song_and_drives_the_player() {
        val calls = mutableListOf<String>()
        show(
            NowPlayingState.Active(song),
            NowPlayingActions(playPause = { calls += "pause" }, previous = { calls += "prev" }, open = { calls += "open" }),
        )
        rule.onNodeWithText("Bohemian Rhapsody").assertIsDisplayed()
        rule.onNodeWithText("Queen · Spotify").assertIsDisplayed()
        rule.onNodeWithText("1:42").assertIsDisplayed()
        rule.onNodeWithText("5:55").assertIsDisplayed()
        rule.onNodeWithContentDescription("Pause").performClick()
        rule.onNodeWithContentDescription("Précédent").performClick()
        rule.onNodeWithContentDescription("Suivant").assertIsNotEnabled()
        rule.onNodeWithText("Bohemian Rhapsody").performClick()
        assertEquals(listOf("pause", "prev", "open"), calls)
    }

    @Test
    fun no_access_offers_to_allow() {
        var asked = false
        show(NowPlayingState.NoAccess, NowPlayingActions(grantAccess = { asked = true }))
        rule.onNodeWithText("Autoriser l'accès aux lecteurs").performClick()
        assertEquals(true, asked)
    }

    @Test
    fun idle_says_nothing_is_playing() {
        show(NowPlayingState.Idle, NowPlayingActions())
        rule.onNodeWithText("Rien en lecture").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.builtin.media.NowPlayingCardTest`
Expected: FAIL at compilation, "Unresolved reference 'NowPlayingCard'".

- [ ] **Step 3: Write the listener, the manifest entry and the label**

`MediaListener.kt` :

```kotlin
package app.lanceur.builtin.media

import android.service.notification.NotificationListenerService

/**
 * Ne lit aucune notification : sa seule présence, autorisée par l'utilisateur, ouvre l'accès aux lecteurs
 * (`MediaSessionManager.getActiveSessions`).
 */
class MediaListener : NotificationListenerService()
```

Dans `app/src/main/res/values/strings.xml`, ajouter dans `<resources>` :

```xml
    <string name="media_listener_label">Lanceur — lecteurs en cours</string>
```

Dans `AndroidManifest.xml`, à côté du service `LockScreenService` (dans `<application>`) :

```xml
        <service
            android:name=".builtin.media.MediaListener"
            android:exported="true"
            android:label="@string/media_listener_label"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
```

- [ ] **Step 4: Write `NowPlayingSource.kt`**

```kotlin
package app.lanceur.builtin.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Lecteurs actifs (Spotify, YouTube Music…) et commandes ⏮ ⏯ ⏭ sur celui que la carte montre. */
class NowPlayingSource(private val context: Context) {
    private val component = ComponentName(context, MediaListener::class.java)
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    @Volatile private var current: MediaController? = null

    fun hasAccess(): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)

    val state: Flow<NowPlayingState> = callbackFlow {
        if (!hasAccess()) {
            trySend(NowPlayingState.NoAccess)
            awaitClose { }
            return@callbackFlow
        }
        val handler = Handler(Looper.getMainLooper())
        val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
        var controllers = emptyList<MediaController>()

        fun publish() {
            val state = NowPlaying.pick(controllers.map(::snapshot))
            current = (state as? NowPlayingState.Active)?.let { active -> controllers.firstOrNull { it.packageName == active.media.packageName } }
            trySend(state)
        }

        fun watch(list: List<MediaController>?) {
            callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
            callbacks.clear()
            controllers = list.orEmpty()
            controllers.forEach { controller ->
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                }
                controller.registerCallback(callback, handler)
                callbacks[controller] = callback
            }
            publish()
        }

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { watch(it) }
        try {
            manager.addOnActiveSessionsChangedListener(listener, component, handler)
            watch(manager.getActiveSessions(component))
        } catch (e: SecurityException) {
            // Accès retiré entre la vérification et l'appel
            Log.w("Lanceur", "Accès aux lecteurs refusé", e)
            trySend(NowPlayingState.NoAccess)
        }
        awaitClose {
            runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
            callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
            current = null
        }
    }.conflate()

    fun playPause() {
        val controller = current ?: return
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controller.transportControls.pause()
        else controller.transportControls.play()
    }

    fun next() { current?.transportControls?.skipToNext() }

    fun previous() { current?.transportControls?.skipToPrevious() }

    fun seekTo(ms: Long) { current?.transportControls?.seekTo(ms) }

    /** Intent de l'appli qui joue, ou `null`. */
    fun openIntent(): Intent? = current?.packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }

    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())

    private fun snapshot(controller: MediaController): MediaSnapshot {
        val metadata = controller.metadata
        val playback = controller.playbackState
        val actions = playback?.actions ?: 0L
        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.description?.iconBitmap
        return MediaSnapshot(
            packageName = controller.packageName,
            appLabel = appLabel(controller.packageName),
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: metadata?.description?.title?.toString(),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata?.description?.subtitle?.toString(),
            art = art?.asImageBitmap(),
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionMs = playback?.position ?: 0L,
            updatedAtElapsed = playback?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = playback?.playbackSpeed ?: 0f,
            isPlaying = playback?.state == PlaybackState.STATE_PLAYING || playback?.state == PlaybackState.STATE_BUFFERING,
            canPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
        )
    }

    private fun appLabel(packageName: String): String = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}
```

`Spotify` doit être visible pour `getApplicationInfo` / `getLaunchIntentForPackage` : Lanceur liste déjà les applis lanceables (`<queries>` MAIN/LAUNCHER), ce qui suffit pour une appli de musique. En cas d'échec, le libellé retombe sur le nom du paquet et `open` ne fait rien.

- [ ] **Step 5: Write `MediaIcons.kt` (icônes absentes de material-icons-core)**

```kotlin
package app.lanceur.builtin.media

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** Tracés des icônes Material « Pause », « Skip next », « Skip previous » (24 × 24). */
internal object MediaIcons {
    val Pause: ImageVector = materialIcon(name = "Lanceur.Pause") {
        materialPath {
            moveTo(6f, 19f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineTo(6f); verticalLineToRelative(14f); close()
            moveTo(14f, 5f); verticalLineToRelative(14f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineToRelative(-4f); close()
        }
    }
    val SkipNext: ImageVector = materialIcon(name = "Lanceur.SkipNext") {
        materialPath {
            moveTo(6f, 18f); lineToRelative(8.5f, -6f); lineTo(6f, 6f); verticalLineToRelative(12f); close()
            moveTo(16f, 6f); verticalLineToRelative(12f); horizontalLineToRelative(2f); verticalLineTo(6f); horizontalLineToRelative(-2f); close()
        }
    }
    val SkipPrevious: ImageVector = materialIcon(name = "Lanceur.SkipPrevious") {
        materialPath {
            moveTo(6f, 6f); horizontalLineToRelative(2f); verticalLineToRelative(12f); horizontalLineTo(6f); close()
            moveTo(9.5f, 12f); lineToRelative(8.5f, 6f); verticalLineTo(6f); close()
        }
    }
}
```

- [ ] **Step 6: Write `NowPlayingCard.kt`**

```kotlin
package app.lanceur.builtin.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.widgets.WidgetSize
import kotlinx.coroutines.delay

class NowPlayingActions(
    val playPause: () -> Unit = {},
    val next: () -> Unit = {},
    val previous: () -> Unit = {},
    val seekTo: (Long) -> Unit = {},
    val open: () -> Unit = {},
    val grantAccess: () -> Unit = {},
)

@Composable
fun NowPlayingCard(
    state: NowPlayingState,
    size: WidgetSize,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
    clock: () -> Long = android.os.SystemClock::elapsedRealtime,
) {
    when (state) {
        is NowPlayingState.Active -> ActiveCard(state.media, size, actions, modifier, clock)
        NowPlayingState.Idle -> Row(
            modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("🎵", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Text("Rien en lecture", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        NowPlayingState.NoAccess -> Column(
            modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (size != WidgetSize.SMALL) {
                Text(
                    "Pour voir et piloter tes lecteurs, Lanceur a besoin de l'accès aux notifications. Il ne les lit pas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = actions.grantAccess) { Text("Autoriser l'accès aux lecteurs") }
            if (size == WidgetSize.LARGE) {
                Text(
                    "Option grisée ? Infos de l'appli › ⋮ › Autoriser les paramètres restreints",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActiveCard(media: MediaSnapshot, size: WidgetSize, actions: NowPlayingActions, modifier: Modifier, clock: () -> Long) {
    val colors = MaterialTheme.colorScheme
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(media) {
        now = clock()
        while (media.isPlaying) {
            delay(500)
            now = clock()
        }
    }
    var dragging by remember(media) { mutableStateOf<Float?>(null) }
    Box(modifier.fillMaxSize().clickable(onClick = actions.open)) {
        // Fond : pochette floutée, ou dégradé du thème sans pochette
        if (media.art != null) {
            Image(media.art, contentDescription = null, modifier = Modifier.matchParentSize().blur(28.dp), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer))))
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.65f)))))
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val thumb = if (size == WidgetSize.LARGE) 96.dp else 56.dp
                Box(Modifier.size(thumb).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    if (media.art != null) Image(media.art, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text("🎵", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(media.title.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(media.artist?.takeIf { it.isNotBlank() }, media.appLabel).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (size != WidgetSize.SMALL && media.durationMs > 0) {
                val position = NowPlaying.positionAt(media, now)
                Slider(
                    value = dragging ?: (position.toFloat() / media.durationMs),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { actions.seekTo((it * media.durationMs).toLong()) }
                        dragging = null
                    },
                    enabled = media.canSeek,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                )
                Row(Modifier.fillMaxWidth()) {
                    val shown = dragging?.let { (it * media.durationMs).toLong() } ?: position
                    Text(NowPlaying.timeLabel(shown), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.weight(1f))
                    Text(NowPlaying.timeLabel(media.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val tint = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.35f))
                IconButton(onClick = actions.previous, enabled = media.canPrevious, colors = tint) { Icon(MediaIcons.SkipPrevious, contentDescription = "Précédent") }
                FilledIconButton(
                    onClick = actions.playPause,
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    if (media.isPlaying) Icon(MediaIcons.Pause, contentDescription = "Pause")
                    else Icon(Icons.Default.PlayArrow, contentDescription = "Lecture")
                }
                IconButton(onClick = actions.next, enabled = media.canNext, colors = tint) { Icon(MediaIcons.SkipNext, contentDescription = "Suivant") }
            }
        }
    }
}
```

- [ ] **Step 7: Run the UI test to verify it passes**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.builtin.media.NowPlayingCardTest`
Expected: PASS (3 tests).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/lanceur/builtin/media app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml app/src/androidTest/java/app/lanceur/builtin/media
git commit -m "Lecture en cours : accès aux lecteurs, commandes et carte à pochette floue"
```

---

### Task 8: Brancher les widgets intégrés sur la page

**Files:**
- Create: `app/src/main/java/app/lanceur/builtin/BuiltinWidget.kt`
- Modify: `app/src/main/java/app/lanceur/widgets/WidgetPage.kt` (hauteurs et tailles par carte)
- Modify: `app/src/main/java/app/lanceur/AppContainer.kt`, `app/src/main/java/app/lanceur/AppRoot.kt`, `app/src/main/java/app/lanceur/apps/AppLauncher.kt`
- Test: `app/src/androidTest/java/app/lanceur/widgets/WidgetPageTest.kt`

**Interfaces:**
- Consumes: tout ce qui précède ; `WidgetLayout` (Tâche 1) ; `LauncherViewModel.addBuiltinWidget` (Tâche 2).
- Produces: `class BuiltinServices(...)` et `@Composable BuiltinWidget(slot: WidgetSlot, services: BuiltinServices, modifier: Modifier)` ; `AppLauncher.openCalendar(atMillis: Long = System.currentTimeMillis())`.

- [ ] **Step 1: Write the failing UI tests**

Ajouter à `WidgetPageTest` (imports à ajouter : `androidx.compose.ui.test.onAllNodesWithText`, `androidx.compose.ui.test.assertCountEquals`, `app.lanceur.builtin.BuiltinKind`, `app.lanceur.builtin.BuiltinSlots`) :

```kotlin
    @Test
    fun edit_mode_hides_sizes_for_single_size_builtin_widgets() {
        val battery = BuiltinSlots.create(BuiltinKind.BATTERY_RING, emptyList())
        show(listOf(WidgetCard.Live(battery)), editMode = true, actions = WidgetPageActions())
        rule.onAllNodesWithText("S").assertCountEquals(0)
        rule.onAllNodesWithText("M").assertCountEquals(0)
    }

    @Test
    fun edit_mode_offers_the_builtin_sizes() {
        val month = BuiltinSlots.create(BuiltinKind.CALENDAR_MONTH, emptyList())
        var resized: WidgetSize? = null
        show(listOf(WidgetCard.Live(month)), editMode = true, actions = WidgetPageActions(resize = { _, size -> resized = size }))
        rule.onNodeWithText("L").performClick()
        assertEquals(WidgetSize.LARGE, resized)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.widgets.WidgetPageTest`
Expected: `edit_mode_hides_sizes_for_single_size_builtin_widgets` FAIL ("Expected 0 but found 1" pour « S »), l'autre PASS (garde-fou de non-régression).

- [ ] **Step 3: Per-card heights and sizes in `WidgetPage.kt`**

Remplacer dans `WidgetList` :

```kotlin
    val heightPx = { card: WidgetCard -> with(density) { (card.slot.size.heightDp.dp + TOOLBAR_HEIGHT + FRAME_PADDING * 2 + GAP).toPx() } }
```

par

```kotlin
    val heightPx = { card: WidgetCard -> with(density) { (WidgetLayout.heightDp(card.slot).dp + TOOLBAR_HEIGHT + FRAME_PADDING * 2 + GAP).toPx() } }
```

et `.height(card.slot.size.heightDp.dp)` par `.height(WidgetLayout.heightDp(card.slot).dp)`.

Dans `EditToolbar`, remplacer le bloc `SingleChoiceSegmentedButtonRow(...) { ... }` par :

```kotlin
        val sizes = WidgetLayout.sizes(card.slot)
        // Une seule taille possible (batterie) : rien à choisir
        if (sizes.size > 1) {
            SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 4.dp)) {
                sizes.forEachIndexed { index, size ->
                    SegmentedButton(
                        selected = WidgetLayout.displaySize(card.slot) == size,
                        onClick = { actions.resize(card.slot, size) },
                        shape = SegmentedButtonDefaults.itemShape(index, sizes.size),
                        icon = {},
                        label = { Text(size.shortLabel) },
                    )
                }
            }
        }
```

- [ ] **Step 4: Run the WidgetPage tests to verify they pass**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.widgets.WidgetPageTest`
Expected: PASS (tous les tests de la classe).

- [ ] **Step 5: Write `BuiltinWidget.kt`**

```kotlin
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
import app.lanceur.builtin.media.NowPlayingActions
import app.lanceur.builtin.media.NowPlayingCard
import app.lanceur.builtin.media.NowPlayingSource
import app.lanceur.builtin.media.NowPlayingState
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
    }
}
```

- [ ] **Step 6: Services in `AppContainer`, calendar date in `AppLauncher`**

Dans `AppContainer`, après `val daySummary = DaySummarySource(appContext)` :

```kotlin
    val battery = BatterySource(appContext)
    val nowPlaying = NowPlayingSource(appContext)
    val calendarRange = CalendarRangeSource(appContext)
```

avec les imports `app.lanceur.builtin.battery.BatterySource`, `app.lanceur.builtin.media.NowPlayingSource`, `app.lanceur.builtin.calendar.CalendarRangeSource`.

Dans `AppLauncher`, remplacer `fun openCalendar()` par :

```kotlin
    fun openCalendar(atMillis: Long = System.currentTimeMillis()) {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .also { ContentUris.appendId(it, atMillis) }
            .build()
        startSafely(Intent(Intent.ACTION_VIEW, uri))
    }
```

- [ ] **Step 7: Wire `AppRoot`**

Imports à ajouter : `app.lanceur.builtin.BuiltinServices`, `app.lanceur.builtin.BuiltinSlots`, `app.lanceur.builtin.BuiltinWidget`, `app.lanceur.builtin.calendar.CalendarCardActions`, `androidx.compose.material3.Text`, `androidx.compose.ui.unit.sp`, `java.time.ZoneId`.

Remplacer `widgetLabels` :

```kotlin
    val widgetLabels = remember(prefs.widgets, widgetRefresh) {
        prefs.widgets.associate { slot ->
            slot.appWidgetId to when {
                BuiltinSlots.isBuiltin(slot) -> BuiltinSlots.kindOf(slot)?.label ?: "Widget Lanceur"
                else -> container.widgetHost.label(slot.appWidgetId)
            }
        }
    }
```

Remplacer `pickerGroups` :

```kotlin
    val pickerGroups = remember(providerEntries, prefs.hidden, pickerQuery) {
        PickerCatalog.build(providerEntries, prefs.hidden, pickerQuery, BuiltinSlots.pickerEntries())
    }
```

Remplacer le lambda `widgetPreview` par :

```kotlin
    val widgetPreview: @Composable (ProviderEntry) -> Unit = { entry ->
        val kind = BuiltinSlots.kindOf(entry.provider)
        if (kind != null) {
            Text(kind.emoji, fontSize = 34.sp)
        } else {
            val sizePx = with(LocalDensity.current) { 96.dp.roundToPx() }
            val bitmap by produceState<ImageBitmap?>(null, entry) {
                value = withContext(Dispatchers.IO) { container.widgetProviders.preview(entry, sizePx) }
            }
            bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
    }
```

Remplacer `removeWidget` :

```kotlin
    fun removeWidget(slot: WidgetSlot) {
        // Un widget intégré n'existe pas pour Android : rien à libérer
        if (!BuiltinSlots.isBuiltin(slot)) container.widgetHost.deleteId(slot.appWidgetId)
        vm.removeWidget(slot.appWidgetId)
    }
```

Juste avant `Box(Modifier.fillMaxSize()) {` (après `homeAlpha`), ajouter :

```kotlin
    val builtinServices = BuiltinServices(
        battery = container.battery,
        nowPlaying = container.nowPlaying,
        calendar = container.calendarRange,
        calendarActions = CalendarCardActions(
            openEvent = { event ->
                container.resultActions.open(SearchResult.Event(event.eventId, event.title, event.begin, event.end, event.allDay, null))
            },
            openDay = { day -> container.appLauncher.openCalendar(day.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) },
            requestCalendar = { permissionLauncher.launch(SearchPermissions.ALL) },
        ),
        openBatterySettings = { container.appLauncher.startSafely(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) },
        openPlayer = { container.nowPlaying.openIntent()?.let(container.appLauncher::startSafely) },
        grantMediaAccess = {
            if (!container.appLauncher.startSafely(container.nowPlaying.accessSettingsIntent())) {
                container.appLauncher.startSafely(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        },
        refresh = widgetRefresh,
    )
```

(ajouter `import android.content.Intent` s'il manque).

Dans l'appel `WidgetPage(...)`, remplacer `isReconfigurable` et `widgetView` par :

```kotlin
                    isReconfigurable = { !BuiltinSlots.isBuiltin(it) && container.widgetHost.isReconfigurable(it.appWidgetId) },
                    widgetView = { slot, modifier ->
                        if (BuiltinSlots.isBuiltin(slot)) BuiltinWidget(slot, builtinServices, modifier)
                        else HostedWidget(slot, container.widgetHost, modifier)
                    },
```

Dans `WidgetPicker(onPick = …)`, remplacer le lambda par :

```kotlin
                onPick = { entry ->
                    vm.show(Screen.HOME)
                    val kind = BuiltinSlots.kindOf(entry.provider)
                    if (kind != null) vm.addBuiltinWidget(kind) else widgetHostActions.add(entry)
                },
```

- [ ] **Step 8: Build and run the whole suite**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleRelease`
Expected: BUILD SUCCESSFUL ; toute la suite JVM et toute la suite sur le Pixel 9 passent ; la version release se construit (R8).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/app/lanceur app/src/androidTest/java/app/lanceur/widgets/WidgetPageTest.kt
git commit -m "Page de widgets : widgets intégrés branchés (sélecteur, rendu, retrait, hauteurs propres)"
```

---

### Task 9: README, spec, installation et vérification sur le téléphone

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-10-05-widgets-integres-lot1-design.md` (ligne « Mise à jour » du §3)

- [ ] **Step 1: Mettre la spec en accord avec le code**

Dans le tableau du §3, remplacer la ligne « Mise à jour » par :

```markdown
| Mise à jour | Les flux sont collectés tant que Lanceur est au premier plan et que la carte est composée (la page de widgets reste composée à côté de l'accueil) ; rien en arrière-plan |
```

- [ ] **Step 2: README**

Dans la section du README sur la page de widgets, ajouter :

```markdown
### Widgets intégrés

Le sélecteur propose en tête un groupe « Lanceur » : Lecture en cours, Agenda · mois, Agenda · semaine,
Batterie · anneau et Batterie · barre. Ils s'ajoutent sans autorisation et se déplacent, se redimensionnent
et se retirent comme les autres.

- **Lecture en cours** : la première fois, toucher « Autoriser l'accès aux lecteurs » et activer Lanceur dans
  l'accès aux notifications (Lanceur ne lit aucune notification). Si l'option est grisée : Infos de l'appli ›
  ⋮ › Autoriser les paramètres restreints, comme pour le double toucher.
- **Agendas** : utilisent la même autorisation que le résumé du jour ; glisser sur le mois change de mois.
- **Batterie** : aucune autorisation ; le niveau des accessoires Bluetooth n'est pas disponible pour les applis.
```

- [ ] **Step 3: Installer la release sur le téléphone**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleRelease && ~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk`
Expected: `Success`.

- [ ] **Step 4: Vérification à la main (avec l'utilisateur)**

1. Page de widgets › Ajouter : le groupe « Lanceur » est en tête, avec les 5 widgets.
2. Ajouter les 5 widgets ; vérifier que chacun s'affiche à sa hauteur, sans texte coupé.
3. Lecture en cours : toucher « Autoriser », accorder l'accès, revenir ; lancer Spotify ; tester ⏯, ⏭ et le glissement de la barre.
4. Agenda · mois : glisser vers la gauche et vers la droite sur la grille (le mois change, la page reste sur les widgets) ; toucher un jour.
5. Agenda · semaine : ‹ › ; toucher un jour ouvre l'agenda.
6. Batterie : brancher le chargeur, l'anneau passe en charge.
7. Mode édition : pas de S/M/L sur la batterie ; retirer un widget intégré ; réordonner.
8. `adb logcat -d -b crash | grep lanceur` : aucun plantage.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/superpowers/specs/2026-10-05-widgets-integres-lot1-design.md
git commit -m "README et spec : widgets intégrés du lot 1"
```
