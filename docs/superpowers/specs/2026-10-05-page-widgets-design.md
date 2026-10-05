# Lanceur — page de widgets : spécification de conception

Date : 2026-10-05
Statut : en relecture
Complète : `docs/superpowers/specs/2026-10-05-lanceur-design.md` (les widgets y étaient hors périmètre)

## 1. Objectif

Depuis l'accueil, glisser le doigt vers la droite fait apparaître une page à gauche qui contient :

- en haut, un **résumé du jour** construit par Lanceur (événements à venir, alarme suivante) ;
- dessous, les **widgets Android** choisis par l'utilisateur, empilés sur toute la largeur.

La page s'active ou se désactive dans les réglages de Lanceur. Elle est **activée par défaut**.

### Critères de réussite

1. Page activée : un glissement vers la droite sur l'accueil ouvre la page, un glissement vers la gauche revient.
   Le bouton Accueil et le geste retour ramènent aussi à l'accueil.
2. Page désactivée : le glissement horizontal ne fait rien, et les widgets enregistrés sont conservés.
3. Un widget réel (Horloge, Météo…) s'ajoute, se met à jour, se déplace, change de taille et se retire.
4. Un widget d'une appli cachée ou de l'Espace privé n'apparaît jamais, ni dans le sélecteur ni sur la page.
5. L'alphabet, la recherche (glisser vers le haut) et les notifications (glisser vers le bas) fonctionnent comme avant.

## 2. Hors périmètre

Grille libre et redimensionnement à la case, appui long sur un widget, piles de widgets, fil Google Discover,
météo dans le résumé du jour (Lanceur n'a pas d'accès Internet), widgets sur l'écran d'accueil lui-même,
sauvegarde des widgets.

## 3. Choix techniques

| Élément | Choix |
|---|---|
| Défilement | `HorizontalPager` de Compose foundation : 2 pages (widgets, accueil) ou 1 si la page est désactivée ; démarrage sur l'accueil ; la page voisine reste composée (`beyondViewportPageCount = 1`) |
| Hôte | `AppWidgetHost` avec l'identifiant d'hôte `1024` ; `startListening()` en `onStart`, `stopListening()` en `onStop`, et pas d'écoute quand la page est désactivée |
| Affichage d'un widget | `AppWidgetHostView` (créée par `AppWidgetHost.createView`) dans un `AndroidView`, coins arrondis à 16 dp |
| Tailles | `SMALL` = 120 dp, `MEDIUM` = 220 dp, `LARGE` = 340 dp de haut ; largeur = largeur de la page moins les marges ; la taille réelle est transmise au widget (`updateAppWidgetSize`) |
| Stockage | Dans `PrefsRepo` (même DataStore, même protection contre la corruption) |
| Logique | Décisions dans du code pur testé sur la JVM, comme le reste de Lanceur |

## 4. Architecture

```
app.lanceur
├── widgets/
│   ├── WidgetSlot.kt          WidgetSize, WidgetSlot (pur)
│   ├── VisibleWidgets.kt      ce que la page affiche (pur)
│   ├── PickerCatalog.kt       contenu du sélecteur (pur)
│   ├── WidgetIds.kt           identifiants orphelins (pur)
│   ├── WidgetHost.kt          hôte Android : identifiants, vues, liaison, configuration
│   ├── WidgetProviderSource.kt  liste des widgets installés par profil (Android)
│   ├── WidgetPage.kt          page Compose : résumé + widgets + mode édition
│   └── WidgetPicker.kt        sélecteur Compose
├── summary/
│   ├── DaySummary.kt          résumé du jour (pur)
│   └── DaySummarySource.kt    agenda + alarme suivante (Android)
└── home/HomePager.kt          les deux pages autour de HomeScreen
```

### 4.1 Données (pures)

- `enum class WidgetSize(val heightDp: Int) { SMALL(120), MEDIUM(220), LARGE(340) }`, avec
  `WidgetSize.fromMinHeightDp(h)` : `h ≤ 120` → `SMALL`, `h ≤ 220` → `MEDIUM`, sinon `LARGE`.
- `data class WidgetSlot(val appWidgetId: Int, val provider: AppKey, val size: WidgetSize)`. `provider` reprend
  `AppKey` : paquet et classe du fournisseur, numéro de série du profil.
- `LauncherPrefs` gagne `widgetPageEnabled: Boolean = true` et `widgets: List<WidgetSlot> = emptyList()` (dans
  l'ordre d'affichage).
- Stockage : une chaîne `widgets` avec une ligne par widget, au format `id|paquet/classe#série|TAILLE`.
  Une ligne illisible est ignorée.
- `PrefsRepo` gagne :
  - `addWidget(slot)` ;
  - `removeWidget(id)` ;
  - `setWidgetSize(id, size)` ;
  - `setWidgetsOrder(ids: List<Int>)` ;
  - `setWidgetPageEnabled(enabled)`.

### 4.2 `VisibleWidgets` (pur)

`compute(slots, hidden: Set<AppKey>, available: Set<Int>): List<WidgetCard>` :

- un widget dont le paquet est caché dans ce profil (`packageInProfile`, même règle que les applis) est retiré de
  la liste : aucune vue n'est créée pour lui ;
- `WidgetCard.Live(slot)` si `appWidgetId ∈ available`, c'est-à-dire si Android connaît encore le widget
  (`getAppWidgetInfo(id) != null`) ;
- sinon `WidgetCard.Unavailable(slot)` : carte « Widget indisponible » avec un bouton **Retirer**. Pas de
  suppression automatique, car une mise à jour d'appli peut rendre un widget indisponible un instant.

Les widgets de l'Espace privé ne peuvent pas exister : le sélecteur ne les propose jamais (§ 4.3).

### 4.3 Sélecteur (pur + Android)

- **`WidgetProviderSource`** (Android) :
  - liste les fournisseurs avec `AppWidgetManager.getInstalledProvidersForProfile(user)`, pour chaque profil ;
  - pour chacun, produit un `ProviderEntry(provider: AppKey, profileKind: ProfileKind, appLabel, widgetLabel,
    minHeightDp)` ;
  - le type de profil se détermine comme dans `LauncherAppsSource`.
- **`PickerCatalog.build(entries, hidden, query): List<PickerGroup>`** :
  - exclut les profils `PRIVATE` et `UNKNOWN` ;
  - exclut les paquets cachés dans leur profil ;
  - filtre sur le nom de l'appli ou du widget (sans accents ni casse) ;
  - regroupe par appli, avec les groupes et les widgets triés en français.
- **Écran** : il se superpose comme les réglages (`blockTouchesBelow`).
  - Il contient un champ de filtre et, par appli, un en-tête puis ses widgets.
  - Chaque widget affiche l'aperçu fourni par l'appli (`loadPreviewImage`), ou à défaut l'icône de l'appli.
  - Toucher un widget lance l'ajout (§ 5.1).

### 4.4 Résumé du jour (pur + Android)

- **`DaySummarySource`** (Android) :
  - lit les instances de l'agenda de maintenant jusqu'à la fin de demain, si l'autorisation est accordée ;
  - lit l'alarme suivante avec `AlarmManager.getNextAlarmClock()`, sans autorisation.
- **`DaySummary.build(now, zone, events, nextAlarmMillis, calendarGranted): DaySummaryState`** :
  - **date** : par exemple « Lundi 5 octobre ».
  - **événements** : au plus 3 non terminés, triés par début. Un événement d'aujourd'hui affiche son heure
    (« 14:00 Dentiste ») ; un de demain est préfixé « Demain » ; un événement « toute la journée » n'affiche
    pas d'heure.
  - **alarme** : « Aujourd'hui 07:00 », « Demain 07:00 », ou « lun. 12 oct. 07:00 » au-delà. Absente s'il
    n'y a pas d'alarme.
  - **messages** : « Rien de prévu » s'il n'y a aucun événement. Une ligne d'invitation remplace les
    événements si l'agenda n'est pas autorisé.
- Toucher un événement l'ouvre (comme dans la recherche). Toucher l'alarme ouvre l'Horloge.
- Le résumé est relu à chaque retour de Lanceur au premier plan.

### 4.5 Défilement et état

- **`HomePager`** enveloppe `HomeScreen` :
  - page 0 = `WidgetPage`, page 1 = accueil ;
  - avec la page désactivée, seule l'accueil existe.
- **Le geste horizontal** :
  - il appartient au pager ;
  - l'alphabet consomme le toucher dès qu'on le pose, donc le pager ne prend jamais un geste qui commence sur
    les lettres ;
  - les glissements verticaux existants restent verticaux.
- **`LauncherViewModel`** gagne :
  - `widgetEditMode: StateFlow<Boolean>` ;
  - `homePageRequests`, un compteur incrémenté par `goHome()`, que `AppRoot` observe pour ramener le pager sur
    l'accueil.

  `goHome()` quitte aussi le mode édition.
- **Geste retour** : sur la page widgets, il quitte d'abord le mode édition, puis ramène à l'accueil.

## 5. Cycle de vie d'un widget

### 5.1 Ajout

1. `allocateAppWidgetId()`.
2. `bindAppWidgetIdIfAllowed(id, user, provider, options)`. En cas de refus, lancer `ACTION_APPWIDGET_BIND`
   (fenêtre d'Android « Autoriser Lanceur à créer des widgets… »). Si l'utilisateur refuse, appeler
   `deleteAppWidgetId(id)` et s'arrêter.
3. Si le widget a une activité de configuration qui n'est pas déclarée facultative
   (`WIDGET_FEATURE_CONFIGURATION_OPTIONAL`, Android 12+), lancer
   `startAppWidgetConfigureActivityForResult`. En cas d'annulation, appeler `deleteAppWidgetId(id)` et
   s'arrêter.
4. Appeler `addWidget(WidgetSlot(id, provider, WidgetSize.fromMinHeightDp(minHeightDp)))` : le widget
   apparaît en bas de la page.

L'identifiant en cours d'ajout est gardé par `MainActivity` pendant le passage par les écrans d'Android.

### 5.2 Vie courante

- **Nettoyage** : au démarrage, `WidgetIds.orphans(host.appWidgetIds, slots)` liste les identifiants réservés
  par l'hôte mais absents des réglages, et ils sont libérés.
- **Reconfigurer** : apparaît seulement si le widget déclare `WIDGET_FEATURE_RECONFIGURABLE`. Relance
  l'activité de configuration sur le même identifiant.
- **Retirer** : appelle `deleteAppWidgetId(id)`, puis `removeWidget(id)`.
- **Taille** : `setWidgetSize`, puis la nouvelle taille est transmise au widget.

## 6. Page de widgets (écran)

- **Haut de page** : carte du résumé du jour (§ 4.4).
- **Liste** : les widgets, dans l'ordre enregistré, chacun à la hauteur de sa taille. La page défile
  verticalement.
- **Aucun widget** : « Ajoute ton premier widget ».
- **Bas de page** : boutons **« + Ajouter un widget »** et **« Modifier »**.
- **Mode édition** :
  - un voile intercepte les touchers des widgets ;
  - chaque widget reçoit une barre avec une poignée de déplacement (même mécanisme que
    `ReorderableFavorites`, état stable), le choix S / M / L, **⚙** si reconfigurable et **✕ Retirer** ;
  - on en sort avec **« Terminé »**, Retour ou Accueil.
- **Réglages** : ligne « Page de widgets à gauche » avec un interrupteur (`setWidgetPageEnabled`).

## 7. Vie privée

- Le sélecteur ne liste ni l'Espace privé, ni un profil de type inconnu, ni une appli cachée.
- Le widget d'une appli cachée n'est pas créé. Il réapparaît quand l'appli est ressortie du dossier caché.
- Toujours aucune permission Internet : chaque widget fait son réseau dans sa propre appli.

## 8. Cas limites

| Cas | Comportement |
|---|---|
| Autorisation de liaison refusée, configuration annulée | Rien n'est ajouté, l'identifiant est libéré |
| Lanceur tué pendant l'ajout | Identifiant orphelin libéré au démarrage suivant |
| Appli du widget désinstallée, ou fournisseur supprimé | Carte « Widget indisponible » + **Retirer** |
| Widget qui plante à l'affichage | Carte d'erreur d'Android, Lanceur continue |
| Agenda non autorisé | Ligne d'invitation dans le résumé |
| Aucune alarme | Ligne d'alarme absente |
| Accueil pendant le mode édition | Sortie du mode édition, retour à l'accueil |
| Page désactivée puis réactivée | Mêmes widgets, même ordre |

## 9. Tests

**JVM**

- Stockage des widgets : aller-retour, ordre, tailles, lignes illisibles ignorées, `setWidgetsOrder`,
  `setWidgetSize`, `removeWidget`.
- `WidgetSize.fromMinHeightDp` : bornes 120 et 220.
- `VisibleWidgets` : paquet caché (y compris après un changement de classe), widget indisponible, ordre conservé.
- `PickerCatalog` : exclusion `PRIVATE` / `UNKNOWN` / paquets cachés, regroupement et tri français, filtre sans
  accents.
- `WidgetIds.orphans`.
- `DaySummary` : sélection aujourd'hui/demain, 3 au maximum, événements terminés exclus, « toute la journée »,
  libellés d'alarme, « Rien de prévu », agenda non autorisé.
- `LauncherViewModel` : `goHome()` quitte le mode édition et incrémente `homePageRequests`.

**Interface (Pixel 9, version `app.lanceur.debug`)**

- Glisser vers la droite affiche la page de widgets (résumé visible) ; rien quand la page est désactivée.
- Un geste commencé sur l'alphabet reste à l'alphabet.
- Mode édition : la barre d'outils apparaît et S / M / L change la taille.
- Le sélecteur filtre et n'affiche pas un fournisseur caché.

**À la main, sur la release**

Ajouter un widget Horloge et un widget Météo (fenêtre d'autorisation, configuration), le déplacer, le
redimensionner, le retirer, cacher son appli, désactiver puis réactiver la page.
