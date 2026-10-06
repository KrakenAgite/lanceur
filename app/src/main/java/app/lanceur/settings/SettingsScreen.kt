package app.lanceur.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import app.lanceur.apps.icons.IconShape
import app.lanceur.builtin.Freshness
import app.lanceur.focus.FocusActions
import app.lanceur.focus.FocusMode
import app.lanceur.focus.FocusSection
import app.lanceur.home.PageKind
import app.lanceur.i18n.tr
import app.lanceur.prefs.AlphabetSide
import app.lanceur.ui.AppLabelStyle
import app.lanceur.ui.HintText
import app.lanceur.ui.SectionTitle
import app.lanceur.ui.blockTouchesBelow
import app.lanceur.ui.cardBackground

class SettingsActions(
    val setDefault: () -> Unit = {},
    val reorderFavorites: (List<AppKey>) -> Unit = {},
    val openHidden: () -> Unit = {},
    val requestPermissions: () -> Unit = {},
    val setSide: (AlphabetSide) -> Unit = {},
    val enableLockService: () -> Unit = {},
    val setWidgetPageEnabled: (Boolean) -> Unit = {},
    val setNewsEnabled: (Boolean) -> Unit = {},
    val movePage: (PageKind, Int) -> Unit = { _, _ -> },
    val openLanguage: () -> Unit = {},
    val openWallpaper: () -> Unit = {},
    val setIconStyle: (app.lanceur.apps.icons.IconStyle) -> Unit = {},
    val setAppLabelStyle: (AppLabelStyle) -> Unit = {},
    val focus: FocusActions = FocusActions(),
    val findIconPacks: () -> Unit = {},
    val grantNotificationAccess: () -> Unit = {},
    val allowNotifications: () -> Unit = {},
    val backupNow: () -> Unit = {},
    val restoreBackup: () -> Unit = {},
    val chooseBackupFolder: () -> Unit = {},
    val setBackupAuto: (Boolean) -> Unit = {},
    val setUpdatesEnabled: (Boolean) -> Unit = {},
    val checkUpdatesNow: () -> Unit = {},
    val openReleases: () -> Unit = {},
    val installUpdate: () -> Unit = {},
    val setAutoInstall: (Boolean) -> Unit = {},
    val allowInstalls: () -> Unit = {},
)

data class BackupState(val auto: Boolean = false, val folderName: String? = null, val last: Long? = null)

data class UpdatesState(
    val installed: String = "",
    val enabled: Boolean = true,
    val latest: String? = null,
    val checking: Boolean = false,
    val lastCheck: Long? = null,
    val canNotify: Boolean = true,
    val autoInstall: Boolean = true,
    val canInstall: Boolean = true,
    val installing: Boolean = false,
)

data class PermissionsState(
    val searchGranted: Boolean = true,
    val notificationAccess: Boolean = true,
    val lockService: Boolean = true,
    val canNotify: Boolean = true,
    val canInstall: Boolean = true,
) {
    val missing: Int get() = listOf(searchGranted, notificationAccess, lockService, canNotify, canInstall).count { !it }
}

/** Sous-menus des réglages, dans l'ordre de la liste. */
enum class SettingsPage(val icon: ImageVector, private val fr: String, private val en: String) {
    HOME(Icons.Outlined.Home, "Accueil", "Home"),
    APPEARANCE(Icons.Outlined.Palette, "Apparence", "Appearance"),
    PAGES(Icons.Outlined.ViewCarousel, "Pages", "Pages"),
    FOCUS(Icons.Outlined.CenterFocusStrong, "Concentration", "Focus"),
    PERMISSIONS(Icons.Outlined.Shield, "Autorisations", "Permissions"),
    BACKUP(Icons.Outlined.Backup, "Sauvegarde", "Backup"),
    ABOUT(Icons.Outlined.Info, "À propos", "About"),
    ;

    val label: String get() = tr(fr, en)
}

private val CARD = RoundedCornerShape(20.dp)

@Composable
fun SettingsScreen(
    favorites: List<AppEntry>,
    side: AlphabetSide,
    isDefaultLauncher: Boolean,
    permissionsGranted: Boolean,
    lockServiceEnabled: Boolean,
    pageOrder: List<PageKind>,
    widgetPageEnabled: Boolean,
    newsEnabled: Boolean,
    icon: @Composable (AppKey) -> Unit,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    appearance: AppearanceState = AppearanceState(),
    focus: FocusMode = FocusMode(),
    focusActive: Boolean = false,
    focusApps: List<AppEntry> = emptyList(),
    notificationAccess: Boolean = true,
    backup: BackupState = BackupState(),
    updates: UpdatesState = UpdatesState(),
    now: Long = System.currentTimeMillis(),
    initialPage: SettingsPage? = null,
) {
    var page by remember { mutableStateOf(initialPage) }
    var choosingPack by remember { mutableStateOf(false) }
    val permissions = PermissionsState(permissionsGranted, notificationAccess, lockServiceEnabled, updates.canNotify, updates.canInstall)
    BackHandler(enabled = page != null) { page = null }

    Column(
        modifier
            .fillMaxSize()
            // Comme la recherche : le fond d'écran reste visible derrière un voile
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                ),
            )
            .blockTouchesBelow()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        val current = page
        if (current == null) {
            Text(tr("Réglages", "Settings"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
            if (!isDefaultLauncher) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(CARD).background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("Lanceur n'est pas ton écran d'accueil", "Lanceur is not your home app"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Button(onClick = actions.setDefault) { Text(tr("Définir", "Set")) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsPage.entries.forEach { p ->
                    MenuRow(p.icon, p.label, summary(p, side, favorites.size, appearance, pageOrder, widgetPageEnabled, newsEnabled, focus, focusActive, permissions, backup, updates, now), tag = "settings-${p.name}") {
                        page = p
                    }
                }
                Spacer(Modifier.height(4.dp))
                MenuRow(Icons.Outlined.Lock, tr("Applis cachées", "Hidden apps"), null, tag = "settings-hidden", external = true, onClick = actions.openHidden)
                MenuRow(Icons.Outlined.Language, tr("Langue", "Language"), tr("Français", "English"), tag = "settings-language", external = true, onClick = actions.openLanguage)
            }
        } else {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page = null }, modifier = Modifier.testTag("settings-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Retour", "Back"))
                }
                Text(current.label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
            }
            Column(Modifier.fillMaxWidth().clip(CARD).background(cardBackground()).padding(horizontal = 18.dp, vertical = 8.dp)) {
            when (current) {
                SettingsPage.HOME -> {
                    SettingRow(
                        tr("Double toucher pour verrouiller", "Double tap to lock"),
                        if (lockServiceEnabled) tr("Activé", "On") else tr("Désactivé", "Off"),
                        if (lockServiceEnabled) null else tr("Activer", "Turn on"),
                        actions.enableLockService,
                    )
                    SectionTitle(tr("Côté de l'alphabet", "Alphabet side"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = side == AlphabetSide.LEFT, onClick = { actions.setSide(AlphabetSide.LEFT) }, label = { Text(tr("Gauche", "Left")) })
                        FilterChip(selected = side == AlphabetSide.RIGHT, onClick = { actions.setSide(AlphabetSide.RIGHT) }, label = { Text(tr("Droite", "Right")) })
                    }
                    SectionTitle(tr("Ordre des favoris", "Favorites order"))
                    if (favorites.isEmpty()) HintText(tr("Aucun favori pour l'instant.", "No favorites yet."))
                    else ReorderableFavorites(favorites, icon, actions.reorderFavorites)
                }
                SettingsPage.APPEARANCE -> AppearanceSection(appearance, actions, onChoosePack = { choosingPack = true })
                SettingsPage.PAGES -> PagesSection(pageOrder, widgetPageEnabled, newsEnabled, actions)
                SettingsPage.FOCUS -> FocusSection(focus, focusActive, focusApps, actions.focus, notificationAccess)
                SettingsPage.PERMISSIONS -> {
                    SettingRow(
                        tr("Agenda et contacts", "Calendar and contacts"),
                        if (permissionsGranted) tr("Autorisés", "Allowed") else tr("Non autorisés", "Not allowed"),
                        if (permissionsGranted) null else tr("Autoriser", "Allow"),
                        actions.requestPermissions,
                    )
                    SettingRow(
                        tr("Accès aux notifications", "Notification access"),
                        if (notificationAccess) tr("Autorisé", "Allowed") else tr("Non autorisé", "Not allowed"),
                        if (notificationAccess) null else tr("Autoriser", "Allow"),
                        actions.grantNotificationAccess,
                    )
                    SettingRow(
                        tr("Accessibilité", "Accessibility"),
                        if (lockServiceEnabled) tr("Autorisée", "Allowed") else tr("Non autorisée", "Not allowed"),
                        if (lockServiceEnabled) null else tr("Autoriser", "Allow"),
                        actions.enableLockService,
                    )
                    SettingRow(
                        tr("Envoyer des notifications", "Send notifications"),
                        if (updates.canNotify) tr("Autorisé", "Allowed") else tr("Non autorisé", "Not allowed"),
                        if (updates.canNotify) null else tr("Autoriser", "Allow"),
                        actions.allowNotifications,
                    )
                    SettingRow(
                        tr("Installer les mises à jour", "Install updates"),
                        if (updates.canInstall) tr("Autorisé", "Allowed") else tr("Non autorisé", "Not allowed"),
                        if (updates.canInstall) null else tr("Autoriser", "Allow"),
                        actions.allowInstalls,
                    )
                }
                SettingsPage.BACKUP -> BackupPage(backup, actions, now)
                SettingsPage.ABOUT -> AboutPage(updates, actions, now)
            }
            }
        }
    }
    if (choosingPack) {
        IconPackSheet(
            state = appearance,
            onPick = { actions.setIconStyle(appearance.iconStyle.copy(pack = it)); choosingPack = false },
            onFindPacks = { actions.findIconPacks(); choosingPack = false },
            onDismiss = { choosingPack = false },
        )
    }
}

/** L'état actuel de chaque sous-menu, en quelques mots. */
private fun summary(
    page: SettingsPage,
    side: AlphabetSide,
    favorites: Int,
    appearance: AppearanceState,
    pages: List<PageKind>,
    widgets: Boolean,
    news: Boolean,
    focus: FocusMode,
    focusActive: Boolean,
    permissions: PermissionsState,
    backup: BackupState,
    updates: UpdatesState,
    now: Long,
): String = when (page) {
    SettingsPage.HOME -> listOf(
        if (side == AlphabetSide.LEFT) tr("Alphabet à gauche", "Alphabet on the left") else tr("Alphabet à droite", "Alphabet on the right"),
        tr("$favorites favoris", "$favorites favorites"),
    ).joinToString(" · ")
    SettingsPage.APPEARANCE -> listOfNotNull(
        if (appearance.labelStyle.showIcons) appearance.iconStyle.shape.takeIf { it != IconShape.SYSTEM }?.label ?: tr("Icônes", "Icons") else tr("Sans icônes", "No icons"),
        if (appearance.labelStyle.uppercase) tr("MAJUSCULES", "CAPITALS") else null,
    ).joinToString(" · ")
    SettingsPage.PAGES -> app.lanceur.home.PageLayout.normalize(pages)
        .filter { it == PageKind.HOME || (it == PageKind.WIDGETS && widgets) || (it == PageKind.NEWS && news) }
        .joinToString(" · ") { it.label }
    SettingsPage.FOCUS -> listOfNotNull(
        if (focusActive) tr("En cours", "On") else tr("Arrêtée", "Off"),
        focus.schedules.size.takeIf { it > 0 }?.let { tr("$it plage" + (if (it > 1) "s" else ""), "$it range" + (if (it > 1) "s" else "")) },
    ).joinToString(" · ")
    SettingsPage.PERMISSIONS -> if (permissions.missing == 0) tr("Tout est autorisé", "All allowed") else tr("${permissions.missing} à autoriser", "${permissions.missing} to allow")
    SettingsPage.BACKUP -> listOf(
        if (backup.auto && backup.folderName != null) tr("Automatique", "Automatic") else tr("Manuelle", "Manual"),
        backup.last?.let { Freshness.ago(it, now) } ?: tr("jamais", "never"),
    ).joinToString(" · ")
    SettingsPage.ABOUT -> updates.latest?.let { tr("$it disponible", "$it available") } ?: tr("Version ", "Version ") + updates.installed
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, summary: String?, tag: String, external: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(CARD).background(cardBackground()).clickable(onClick = onClick).testTag(tag)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.padding(end = 16.dp).size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
        }
        Icon(
            if (external) Icons.AutoMirrored.Outlined.OpenInNew else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun BackupPage(backup: BackupState, actions: SettingsActions, now: Long) {
    SettingRow(
        tr("Dernière sauvegarde", "Last backup"),
        backup.last?.let { Freshness.ago(it, now) } ?: tr("Jamais", "Never"),
        null,
    ) {}
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = actions.backupNow, modifier = Modifier.weight(1f).testTag("backup-now")) { Text(tr("Sauvegarder", "Back up")) }
        FilledTonalButton(onClick = actions.restoreBackup, modifier = Modifier.weight(1f).testTag("backup-restore")) { Text(tr("Restaurer", "Restore")) }
    }
    SectionTitle(tr("Automatique", "Automatic"))
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Après chaque changement", "After every change"), style = MaterialTheme.typography.titleMedium)
            Text(tr("Les ${app.lanceur.prefs.Backup.KEEP} dernières sont gardées", "The last ${app.lanceur.prefs.Backup.KEEP} are kept"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = backup.auto, onCheckedChange = actions.setBackupAuto, modifier = Modifier.testTag("backup-auto"))
    }
    SettingRow(
        tr("Dossier", "Folder"),
        backup.folderName ?: tr("Aucun", "None"),
        if (backup.folderName == null) tr("Choisir", "Choose") else tr("Changer", "Change"),
        actions.chooseBackupFolder,
    )
}

@Composable
private fun AboutPage(updates: UpdatesState, actions: SettingsActions, now: Long) {
    SettingRow(
        tr("Version", "Version"),
        updates.installed + (updates.latest?.let { " · " + tr("$it disponible", "$it available") } ?: ""),
        updates.latest?.let { if (updates.installing) tr("Installation…", "Installing…") else tr("Installer", "Install") },
        actions.installUpdate,
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Vérifier les mises à jour", "Check for updates"), style = MaterialTheme.typography.titleMedium)
            Text(
                updates.lastCheck?.let { tr("Dernière vérification ", "Last checked ") + Freshness.ago(it, now) } ?: tr("Jamais vérifié", "Never checked"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = updates.enabled, onCheckedChange = actions.setUpdatesEnabled, modifier = Modifier.testTag("updates-enabled"))
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Installer automatiquement", "Install automatically"), style = MaterialTheme.typography.titleMedium)
            Text(
                if (updates.canInstall) tr("Dès qu'une version sort", "As soon as a version is out") else tr("Autorisation d'installer requise", "Install permission needed"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = updates.autoInstall, onCheckedChange = actions.setAutoInstall, modifier = Modifier.testTag("updates-auto-install"))
    }
    if (updates.autoInstall && !updates.canInstall) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HintText(tr("Lanceur n'a pas le droit d'installer.", "Lanceur can't install apps yet."))
            androidx.compose.material3.TextButton(onClick = actions.allowInstalls) { Text(tr("Autoriser", "Allow")) }
        }
    }
    FilledTonalButton(onClick = actions.checkUpdatesNow, enabled = !updates.checking, modifier = Modifier.fillMaxWidth().testTag("updates-check")) {
        Text(if (updates.checking) tr("Vérification…", "Checking…") else tr("Vérifier maintenant", "Check now"))
    }
    if (updates.enabled && !updates.canNotify) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HintText(tr("Notifications bloquées pour Lanceur.", "Notifications are blocked for Lanceur."))
            androidx.compose.material3.TextButton(onClick = actions.allowNotifications) { Text(tr("Autoriser", "Allow")) }
        }
    }
    SettingRow(tr("Code source", "Source code"), "github.com/KrakenAgite/lanceur", tr("Ouvrir", "Open"), actions.openReleases)
}

@Composable
internal fun SettingRow(title: String, subtitle: String, actionLabel: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null) FilledTonalButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** Pages de gauche à droite, comme quand on fait défiler ; l'Accueil ne se masque pas. */
@Composable
private fun PagesSection(order: List<PageKind>, widgetsEnabled: Boolean, newsEnabled: Boolean, actions: SettingsActions) {
    HintText(tr("Appui long puis glisse pour changer l'ordre", "Long press then drag to reorder"))
    PagePreviews(
        order = order,
        isShown = { kind ->
            when (kind) {
                PageKind.HOME -> true
                PageKind.WIDGETS -> widgetsEnabled
                PageKind.NEWS -> newsEnabled
            }
        },
        onToggle = { kind, on ->
            when (kind) {
                PageKind.WIDGETS -> actions.setWidgetPageEnabled(on)
                PageKind.NEWS -> actions.setNewsEnabled(on)
                PageKind.HOME -> Unit
            }
        },
        onMove = actions.movePage,
    )
}
