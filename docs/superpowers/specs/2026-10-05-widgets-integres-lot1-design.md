# Lanceur — widgets intégrés, lot 1 : spécification de conception

Date : 2026-10-05
Statut : en relecture
Complète : `docs/superpowers/specs/2026-10-05-page-widgets-design.md`

## 1. Objectif

La page de widgets accueille, en plus du résumé du jour et des widgets Android, des **widgets intégrés** dessinés par
Lanceur. Ce lot pose la base commune et livre cinq widgets :

- **Lecture en cours** : pochette en fond, titre, artiste, barre de progression, ⏮ ⏯ ⏭ ;
- **Agenda · mois** : grille du mois et détail du jour choisi ;
- **Agenda · semaine** : les 7 jours en bandeau ;
- **Batterie · anneau** : anneau, état de charge, temps restant, température, Économiseur ;
- **Batterie · barre** : jauge compacte sur une ligne.

Lots suivants, chacun avec sa propre spécification : lot 2, les widgets locaux (note rapide, to-do, contacts favoris,
raccourcis rapides, minuteur/chrono, horloges du monde, compte à rebours, stockage et mémoire) ; lot 3, les widgets qui
ont besoin d'Internet (météo, flux RSS).

### Critères de réussite

1. Le sélecteur montre un groupe « Lanceur » en tête ; toucher un widget intégré l'ajoute immédiatement en bas de la
   pile, sans fenêtre d'autorisation ni configuration.
2. Un widget intégré se déplace, change de taille (quand il en a plusieurs) et se retire comme un widget Android.
3. Pendant une lecture Spotify ou YouTube Music, le widget montre le morceau et ⏮ ⏯ ⏭ pilotent le lecteur.
4. Les agendas montrent les événements avec les couleurs de leurs agendas et 🎁 pour les anniversaires.
5. La batterie suit le niveau et la charge en direct tant que la page est visible.
6. Les widgets Android, le résumé du jour et le reste du launcher fonctionnent comme avant.

## 2. Hors périmètre

Niveau de batterie des accessoires Bluetooth (aucune API publique : seuls certains casques l'annoncent eux-mêmes, de
façon non fiable), widgets intégrés sur l'écran d'accueil, widgets intégrés exposés aux autres launchers, file
d'attente et paroles dans le lecteur, création ou modification d'événements, lots 2 et 3.

## 3. Choix techniques

| Élément | Choix |
|---|---|
| Rendu | Composables Compose dans le cadre existant de la page (coins 24 dp, `surfaceContainer`) — pas d'`AppWidgetHostView` |
| Identifiant | Négatif, propre à Lanceur : `min(identifiants négatifs enregistrés, 0) - 1`. Jamais transmis à `AppWidgetHost` |
| Fournisseur | `AppKey("app.lanceur.builtin", "<type>", 0)` ; même format `id|fournisseur|TAILLE` qu'aujourd'hui, sans migration |
| Lecteur | `NotificationListenerService` déclaré (aucune notification n'est lue) ; `MediaSessionManager.getActiveSessions` + `addOnActiveSessionsChangedListener` ; `MediaController.TransportControls` pour ⏮ ⏯ ⏭ et `seekTo` |
| Agenda | `CalendarContract.Instances` (avec `DISPLAY_COLOR`) sur la période affichée, lecture partagée avec le résumé du jour |
| Batterie | Diffusion collante `ACTION_BATTERY_CHANGED`, `BatteryManager.computeChargeTimeRemaining()`, `PowerManager.isPowerSaveMode` + `ACTION_POWER_SAVE_MODE_CHANGED` ; aucune permission |
| Mise à jour | Les flux sont collectés tant que Lanceur est au premier plan et que la carte est composée (la page de widgets reste composée à côté de l'accueil) ; rien en arrière-plan |
| Flou de la pochette | `Modifier.blur` (Android 12+, donc toujours disponible avec minSdk 35) |

### Hauteurs (dp, hors barre d'édition)

| Widget | S | M | L | Tailles proposées |
|---|---|---|---|---|
| Lecture en cours | 120 | 200 | 300 | S, M, L (M par défaut) |
| Agenda · mois | 300 | 400 | 520 | S, M, L (M par défaut) |
| Agenda · semaine | 140 | 200 | 280 | S, M, L (M par défaut) |
| Batterie · anneau | 140 | — | — | une seule : le sélecteur S/M/L est masqué |
| Batterie · barre | 72 | — | — | une seule : le sélecteur S/M/L est masqué |

La hauteur d'une carte vient de `cardHeightDp(slot)` : celle du type intégré, sinon `WidgetSize.heightDp`. Le
glisser-déposer (`HeightReorder`) utilise cette même fonction.

## 4. Architecture

```
app.lanceur
├── builtin/
│   ├── BuiltinKind.kt         types, libellés, hauteurs, tailles permises (pur)
│   ├── BuiltinSlots.kt        nextId, isBuiltin, kindOf, slot(kind) (pur)
│   ├── BuiltinWidget.kt       aiguillage Compose type → widget
│   ├── media/
│   │   ├── NowPlaying.kt      état du lecteur, choix de la session, position extrapolée (pur)
│   │   ├── MediaListener.kt   NotificationListenerService vide (sert d'autorisation)
│   │   ├── NowPlayingSource.kt  sessions actives → StateFlow<NowPlayingState> (Android)
│   │   └── NowPlayingCard.kt
│   ├── calendar/
│   │   ├── MonthGrid.kt       grille lundi-dimanche, pastilles, 🎁 (pur)
│   │   ├── WeekStrip.kt       7 jours et leurs événements, « +N » (pur)
│   │   ├── CalendarRangeSource.kt  événements d'une période (Android)
│   │   ├── MonthCard.kt
│   │   └── WeekCard.kt
│   └── battery/
│       ├── BatteryInfo.kt     état, couleur selon le niveau, textes (pur)
│       ├── BatterySource.kt   diffusions → StateFlow<BatteryInfo> (Android)
│       ├── BatteryRingCard.kt
│       └── BatteryBarCard.kt
├── summary/CalendarEvents.kt  requête Instances partagée (extraite de DaySummarySource)
└── widgets/                   PickerCatalog, VisibleWidgets, WidgetPage, WidgetIds adaptés
```

### 4.1 Base commune (pur)

- `BuiltinKind` : `NOW_PLAYING`, `CALENDAR_MONTH`, `CALENDAR_WEEK`, `BATTERY_RING`, `BATTERY_BAR` ; chacun a un libellé
  (« Lecture en cours », « Agenda · mois »…), ses hauteurs, ses tailles permises et sa taille par défaut.
- `BuiltinSlots.nextId(slots)` : `min(0, plus petit identifiant) - 1` ; `isBuiltin(slot)` : paquet
  `app.lanceur.builtin` ; `kindOf(slot)` : `null` si le type est inconnu (version future), la carte s'affiche alors
  « Widget indisponible » avec **Retirer**.
- `PickerCatalog` : groupe « Lanceur » en tête, toujours présent, filtré par la recherche comme les autres.
- `VisibleWidgets` : un widget intégré est toujours `Live`, jamais caché par le dossier caché.
- `WidgetIds.orphans` et le nettoyage ignorent les identifiants négatifs ; `WidgetHost.deleteId` n'est jamais appelé
  pour eux.

### 4.2 Lecture en cours

- `NowPlaying.pick(sessions)` : la session qui joue ; sinon la dernière mise en pause (ordre fourni par Android) ;
  sinon aucune.
- `NowPlayingState` : `NoAccess`, `Idle`, ou `Playing(title, artist, appLabel, packageName, art, durationMs,
  positionMs, updatedAt, speed, isPlaying, canPrev, canNext, canSeek)`.
- `NowPlaying.positionAt(state, now)` : `positionMs + (now - updatedAt) × speed` si en lecture, borné à
  `[0, durationMs]`.
- La source réécoute `onMetadataChanged` / `onPlaybackStateChanged` de la session choisie. Une
  `SecurityException` (accès retiré) donne `NoAccess`.
- Carte : pochette floue en fond + voile dégradé ; petite pochette nette, titre, « artiste · appli » ; barre
  glissable (`seekTo` au relâchement) avec temps écoulé et total ; ⏮ ⏯ ⏭ (bouton central plus grand, grisé si
  l'action n'existe pas) ; toucher la carte ouvre l'appli. S : sans barre ; L : grande pochette.
- `Idle` : « Rien en lecture » avec 🎵. `NoAccess` : « Autoriser l'accès aux lecteurs », qui ouvre
  `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` pour Lanceur, avec une phrase sur le paramètre restreint.

### 4.3 Agendas

- `CalendarEvents.query(context, begin, end)` : la requête `Instances` du résumé du jour, extraite et partagée
  (titre, début, fin, journée entière, couleur, identifiant).
- `MonthGrid.build(month, today, events)` : semaines du lundi au dimanche couvrant le mois, jours voisins marqués
  « hors mois », pour chaque jour jusqu'à 3 couleurs distinctes et un drapeau anniversaire
  (`DaySummary.isBirthday`). Les événements sur plusieurs jours marquent chaque jour couvert ; les journées entières
  sont lues en UTC, comme dans le résumé.
- `MonthGrid.dayEvents(day, events)` : chronologie du jour, triée, anniversaires en tête avec 🎁 et nom court.
- `WeekStrip.build(weekStart, today, events, perDay)` : 7 colonnes, `perDay` événements visibles puis « +N ».
- Mois : en-tête « octobre 2026 » avec ‹ › ; un `HorizontalPager` interne sans fin (page centrale = mois courant)
  pour glisser d'un mois à l'autre — il consomme le glissement, la page de widgets ne bouge pas. Aujourd'hui
  sélectionné par défaut ; toucher un jour affiche ses événements sous la grille ; toucher un événement l'ouvre.
  S : grille seule ; M : grille + 3 événements ; L : grille + événements du jour (défilement interne).
- Semaine : 7 colonnes, aujourd'hui mis en valeur, barres colorées avec le début du titre, 🎁 en tête de colonne ;
  ‹ › change de semaine ; toucher un jour ouvre l'agenda à cette date (`content://com.android.calendar/time/<ms>`).
  2, 4 ou 6 événements par jour selon S, M, L.
- Agenda non autorisé : grille sans pastilles et ligne « Autoriser l'agenda » (même demande que la recherche).

### 4.4 Batterie

- `BatteryInfo(level, status, chargeTimeRemainingMs, temperatureTenths, powerSave)` avec `status` parmi
  `CHARGING`, `DISCHARGING`, `FULL`.
- `BatteryInfo.tone` : `GOOD` > 50 %, `MEDIUM` de 20 à 50 %, `LOW` < 20 % → vert, orange, rouge (teintes fixes
  harmonisées avec le thème).
- `BatteryInfo.statusText` : « En charge · pleine dans 1 h 05 » / « En charge » (estimation absente) /
  « Sur batterie » / « Chargée ». `temperatureText` : « 31 °C ».
- Anneau : arc animé au niveau, pourcentage au centre, ⚡ et légère pulsation en charge ; à droite l'état, la
  température et « Économiseur activé / désactivé ». Toucher ouvre
  `Intent.ACTION_POWER_USAGE_SUMMARY` (réglages Batterie).
- Barre : jauge en forme de pile de la couleur du niveau, « 78 % » et « ⚡ 1 h 05 » en charge.

## 5. Ajout et vie d'un widget intégré

1. Sélecteur → toucher un widget du groupe « Lanceur ».
2. `BuiltinSlots.slot(kind, slots)` crée `WidgetSlot(nextId, AppKey(builtin, kind), kind.defaultSize)`.
3. `PrefsRepo.addWidget(slot)` ; retour à la page de widgets, la carte apparaît en bas.
4. Retirer : `PrefsRepo.removeWidget(id)` seulement, sans appel à `AppWidgetHost`.
5. Changer de taille : seules les tailles permises sont proposées ; une taille enregistrée non permise est
   ramenée à la taille par défaut à l'affichage.

## 6. Vie privée

- Aucune permission Internet ajoutée.
- Le service d'écoute des notifications ne lit aucune notification : il ne sert qu'à obtenir l'accès aux sessions
  média. Il est déclaré `android:exported="true"` avec `BIND_NOTIFICATION_LISTENER_SERVICE`, comme Android l'exige.
- Les widgets intégrés montrent l'agenda et le morceau en cours sur la page de widgets, comme le résumé du jour.
  Ils n'affichent rien du dossier caché.

## 7. Cas limites

| Cas | Comportement |
|---|---|
| Accès aux notifications non accordé ou retiré | Carte « Autoriser l'accès aux lecteurs » |
| Paramètre restreint (appli installée hors Play Store) | La carte rappelle d'autoriser les paramètres restreints dans les infos de l'appli |
| Aucun lecteur, ou session fermée | « Rien en lecture » |
| Lecteur sans pochette / sans durée | Dégradé du thème / barre de progression masquée |
| Action non prise en charge (pas de ⏭) | Bouton grisé |
| Plusieurs lecteurs | Celui qui joue, sinon le dernier mis en pause |
| Agenda non autorisé | Grille ou bandeau sans événement + ligne d'invitation |
| Événement sur plusieurs jours | Pastille sur chaque jour couvert |
| Plus de 3 couleurs un même jour | 3 pastilles |
| Mois de 6 semaines | Grille de 6 lignes, même hauteur de carte |
| Estimation de charge absente (-1) | « En charge » sans durée |
| Type intégré inconnu (version future) | « Widget indisponible » + **Retirer** |
| Ancien `WidgetSlot` à identifiant positif | Inchangé : widget Android |
| Glissement horizontal sur l'agenda · mois | Change de mois ; la page de widgets ne bouge pas |

## 8. Tests

**JVM**

- `BuiltinSlots` : `nextId` (vide, positifs seuls, négatifs existants), `isBuiltin`, `kindOf` inconnu, aller-retour
  `encode`/`decode`.
- `PickerCatalog` : groupe « Lanceur » en tête, filtré par la recherche.
- `VisibleWidgets` : intégré toujours visible, même si des applis sont cachées.
- `WidgetIds.orphans` : identifiants négatifs ignorés.
- `cardHeightDp` et tailles permises : batterie à taille unique, taille non permise ramenée au défaut.
- `NowPlaying` : choix de session, position extrapolée et bornée, vitesse 0 en pause.
- `MonthGrid` : octobre 2026 commence un jeudi (3 jours hors mois avant), mois de 6 semaines, aujourd'hui,
  3 couleurs au plus, plusieurs jours, journée entière en UTC, anniversaire.
- `WeekStrip` : semaine du lundi, « +N », anniversaire en tête.
- `BatteryInfo` : seuils 20/50, textes de charge (avec et sans estimation, « 1 h 05 », « 42 min »), « Chargée ».

**Interface (Pixel 9, version `app.lanceur.debug`)**

- Le sélecteur montre « Lanceur » ; toucher « Batterie · barre » ajoute la carte sur la page.
- Cartes avec états fournis (sans sources réelles) : lecteur `Playing` (titre, boutons, clic ⏯ transmis), `Idle`,
  `NoAccess` ; agenda · mois (toucher un jour affiche ses événements) ; agenda · semaine ; anneau en charge.
- Mode édition : le sélecteur S/M/L est masqué pour un widget batterie.

**À la main, sur la release**

Ajouter les cinq widgets, accorder l'accès aux notifications, lancer Spotify : pause, suivant, glisser la barre ;
changer de mois ; brancher le chargeur ; retirer un widget intégré puis réactiver/désactiver la page.
