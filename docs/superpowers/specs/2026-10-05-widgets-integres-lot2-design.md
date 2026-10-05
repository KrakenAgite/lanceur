# Lanceur — widgets intégrés, lot 2 : spécification de conception

Date : 2026-10-05
Statut : en relecture
Complète : `docs/superpowers/specs/2026-10-05-widgets-integres-lot1-design.md` (base commune des widgets intégrés)

## 1. Objectif

Ajouter huit widgets intégrés 100 % locaux : **Note rapide**, **To-do**, **Contacts favoris**, **Raccourcis rapides**,
**Minuteur / chrono**, **Horloges du monde**, **Compte à rebours**, **Stockage et mémoire**. Ils reprennent la base du
lot 1 : `BuiltinKind`, identifiants négatifs, groupe « Lanceur » du sélecteur avec miniatures, style du résumé du jour
(`cardBackground()`, `CardShape`, `CardLabel`), tailles propres.

Nouveauté : un widget peut avoir **ses propres données** (texte, tâches, villes, date, état du chrono), stockées par
identifiant, et une **feuille de réglages**.

### Critères de réussite

1. Les huit widgets apparaissent dans le groupe « Lanceur » avec une miniature et s'ajoutent comme ceux du lot 1.
2. Une note ou une liste de tâches survit au redémarrage de Lanceur ; deux widgets Note ont deux notes distinctes.
3. Horloges du monde et Compte à rebours ouvrent leur feuille à l'ajout ; annuler n'ajoute rien ; ⚙ en mode édition
   la rouvre.
4. La pastille « 5 min » lance un minuteur de 5 minutes dans l'Horloge, qui sonne même Lanceur fermé.
5. La lampe torche s'allume et s'éteint depuis le widget.
6. Retirer un widget supprime ses données.
7. Aucune permission nouvelle ; toujours aucune permission Internet.

## 2. Hors périmètre

Changer soi-même le Wi-Fi, le Bluetooth ou Ne pas déranger (interdit aux applis), sonnerie de minuteur gérée par
Lanceur, choix manuel des contacts, mise en forme du texte des notes, rappels sur les tâches, synchronisation ou
sauvegarde des notes, météo et RSS (lot 3).

## 3. Choix techniques

| Élément | Choix |
|---|---|
| Données d'un widget | Une clé DataStore par widget, `widget_data_<id>` (texte), dans `PrefsRepo`, avec la même protection contre la corruption ; supprimée par `removeWidget` |
| Format | Texte simple encodé et décodé par du code pur (`WidgetData`) : lignes `clé=valeur`, listes séparées, caractères spéciaux échappés. Pas de JSON : `org.json` n'existe pas dans les tests JVM |
| Feuille de réglages | `ModalBottomSheet` Material 3, ouverte à l'ajout (Horloges, Compte à rebours) et par ⚙ en mode édition (`isReconfigurable` vrai pour ces deux types) |
| Note / To-do | Modifiées sur la carte ; écriture différée de 500 ms après la dernière frappe ; la page de widgets reçoit `imePadding()` |
| Contacts | `ContactsContract.Contacts` avec `STARRED = 1`, tri par nom, photo miniature ; `ContentObserver` pour suivre les changements ; permission `READ_CONTACTS` existante |
| Lampe torche | `CameraManager.setTorchMode` sur la première caméra avec flash ; `TorchCallback` pour l'état ; aucune permission |
| Panneaux | `Settings.Panel.ACTION_WIFI`, `ACTION_INTERNET_CONNECTIVITY`, `ACTION_VOLUME` ; `Settings.ACTION_BLUETOOTH_SETTINGS` ; Ne pas déranger : `android.settings.ZEN_MODE_SETTINGS`, repli `Settings.ACTION_SOUND_SETTINGS` |
| Minuteur | `AlarmClock.ACTION_SET_TIMER` avec `EXTRA_LENGTH` et `EXTRA_SKIP_UI = true` ; repli `AlarmClock.ACTION_SHOW_TIMERS` ; « Autre » ouvre `ACTION_SHOW_TIMERS` ; permission `SET_ALARM` existante |
| Chrono | État dans les données du widget : `startedAt` (horloge murale, ms), `accumulated` (ms), tours ; affichage rafraîchi toutes les 100 ms seulement pendant qu'il tourne et que la carte est visible |
| Horloges du monde | Table intégrée d'environ 60 villes (nom français → `ZoneId`) ; recherche sans accents (`TextNormalizer`) ; décalages via `java.time` |
| Stockage / mémoire | `StatFs(Environment.getDataDirectory())` ; `ActivityManager.MemoryInfo` (`totalMem`, `availMem`) ; relu à l'affichage puis toutes les 5 s ; ouvre `Settings.ACTION_INTERNAL_STORAGE_SETTINGS` |

### Hauteurs (dp)

| Widget | S | M | L | Défaut |
|---|---|---|---|---|
| Note rapide | 140 | 220 | 340 | M |
| To-do | 160 | 260 | 380 | M |
| Contacts favoris | 120 | 220 | — | S |
| Raccourcis rapides | 120 | — | — | S |
| Minuteur / chrono | — | 200 | — | M |
| Horloges du monde | 120 | 200 | — | M |
| Compte à rebours | 120 | 200 | — | S |
| Stockage et mémoire | 140 | — | — | S |

`BuiltinKind` accepte qu'une taille par défaut soit M sans S (Minuteur / chrono) : `sizes` ne liste que les tailles
déclarées.

## 4. Architecture

```
app.lanceur.builtin
├── BuiltinKind.kt            + NOTE, TODO, FAVORITE_CONTACTS, SHORTCUTS, TIMER, WORLD_CLOCKS, COUNTDOWN, STORAGE
├── WidgetData.kt             encodage clé=valeur (pur)
├── BuiltinSettings.kt        feuilles de réglages (Horloges, Compte à rebours)
├── note/   NoteData (pur), NoteCard
├── todo/   TodoList (pur : ajout, cocher, supprimer, effacer faites, ordre), TodoCard
├── contacts/ FavoriteContactsSource (Android), FavoritesCard
├── shortcuts/ TorchController (Android), ShortcutsCard
├── timer/  Stopwatch (pur), TimerPresets (pur), TimerCard
├── clocks/ Cities (pur, table), WorldClock (pur : heure, décalage, jour, ☀/☾), WorldClocksCard
├── countdown/ Countdown (pur : J-N, j/h, jour J, passé, progression), CountdownCard
└── storage/ StorageInfo (pur : Go, seuil 90 %), StorageSource (Android), StorageCard
```

`PrefsRepo` gagne `widgetData: Map<Int, String>` dans `LauncherPrefs`, `setWidgetData(id, text)` et le nettoyage dans
`removeWidget`. `BuiltinServices` gagne les sources (contacts, lampe, stockage), les actions (appel, SMS, minuteur,
panneaux) et `data(id)` / `saveData(id, text)`.

### 4.1 Données (pur)

- `WidgetData.encode(Map<String, String>)` / `decode(String): Map<String, String>` : une paire par ligne, `\` `=` et
  retour à la ligne échappés ; une ligne illisible est ignorée.
- Chaque widget a son modèle avec `toData()` / `fromData(map)` qui tolère les clés absentes : `NoteData(text)`,
  `TodoList(items: List<TodoItem(id, text, done)>)`, `StopwatchState(startedAt?, accumulated, laps)`,
  `WorldClocksConfig(cityIds)`, `CountdownConfig(title, date, time?, createdAt)`.

### 4.2 Comportements (pur)

- `TodoList` : `add(text)` ignore le texte vide, `toggle(id)`, `remove(id)`, `clearDone()` ; `visible()` = à faire
  dans l'ordre d'ajout, puis faites.
- `Stopwatch` : `start(now)`, `pause(now)`, `reset()`, `lap(now)` (3 derniers gardés), `elapsed(now)` ; `format(ms)`
  → « 00:42,3 », « 1:02:03,4 ».
- `TimerPresets` : 1, 3, 5, 10, 15, 30 min → libellés et durées en secondes.
- `WorldClock.of(city, now, home)` → heure « 14:05 », décalage « +7 h » / « −6 h » / « +3 h 30 » / « même heure »,
  jour relatif (« demain », « hier », rien), `isDay` (7 h ≤ heure < 19 h).
- `Countdown.of(config, now)` → « J-12 », « 12 j 4 h » (heure fixée), « C'est aujourd'hui 🎉 », « Il y a 3 jours »,
  progression entre `createdAt` et la date (0..1).
- `StorageInfo` → « 87 / 128 Go », fraction, `warning` au-delà de 90 %.

## 5. Cartes

Toutes : `cardBackground()`, coins concentriques (marge 10–22 dp), `CardLabel` en tête (« NOTE », « À FAIRE »,
« FAVORIS », « RACCOURCIS », « MINUTEUR » / « CHRONO », « HORLOGES », titre du compte à rebours, « STOCKAGE »).

- **Note** : `BasicTextField` multiligne, défilement interne, indication « Touche pour écrire… ».
- **To-do** : cases à cocher, tâches faites barrées en fin de liste, glisser pour supprimer, ligne « Ajouter une
  tâche… », bouton « Effacer les tâches faites » s'il y en a.
- **Contacts favoris** : bulles (photo ou initiale) et prénom, 4 par ligne ; toucher appelle, appui long envoie un
  SMS ; sans numéro, ouvre la fiche ; invitations « Autoriser les contacts » / « Ajoute des favoris ⭐ dans Contacts ».
- **Raccourcis** : cinq boutons ronds avec libellé ; la lampe est remplie quand elle est allumée, grisée si
  indisponible.
- **Minuteur / chrono** : onglets ; pastilles de durée + « Autre » ; message « Minuteur de 5 min lancé » ; chrono en
  grand avec Démarrer/Pause, Tour, Réinitialiser et 3 tours.
- **Horloges** : S, 2 villes en ligne ; M, jusqu'à 4 en grille ; nom, heure, décalage, jour relatif, ☀/☾.
- **Compte à rebours** : grand « J-12 », titre, date complète ; M ajoute la barre de progression ; sans réglage,
  « Régler le compte à rebours ».
- **Stockage et mémoire** : deux jauges avec libellé et valeurs ; orange au-delà de 90 %.

Feuilles : **Horloges**, recherche + liste des villes, 1 à 4 cochées (ta ville cochée par défaut), Enregistrer ;
**Compte à rebours**, titre, date (`DatePicker` Material 3), heure facultative (`TimePicker`), Enregistrer. Annuler à
l'ajout n'ajoute rien.

## 6. Vie privée

Tout reste sur le téléphone, dans les réglages de Lanceur. Les notes et tâches sont visibles sur la page de widgets
comme le résumé du jour ; le dossier caché ne les masque pas.

## 7. Cas limites

| Cas | Comportement |
|---|---|
| Données illisibles ou absentes | Widget vide ; Horloges : ville du téléphone ; Compte à rebours : « Régler le compte à rebours » |
| Widget retiré | Ses données sont supprimées |
| Annuler la feuille à l'ajout | Rien n'est ajouté |
| Lampe indisponible ou caméra occupée | Bouton grisé, message court |
| Horloge sans minuteur direct | Ouverture de l'écran Minuteur |
| Chrono en marche, redémarrage du téléphone | Le temps reprend depuis `startedAt` |
| Fuseau à demi-heure | « +3 h 30 » |
| Changement d'heure | Décalages recalculés à chaque minute |
| Jour J / date passée | « C'est aujourd'hui 🎉 » / « Il y a N jours » |
| Contacts non autorisés, aucun favori | Invitation |
| Favori sans numéro | Ouvre la fiche du contact |
| Note ou liste très longue | Défilement interne, hauteur inchangée |
| Clavier ouvert | La page remonte (`imePadding`) |

## 8. Tests

**JVM** : `WidgetData` (aller-retour, caractères spéciaux, lignes illisibles) ; `PrefsRepo` (`setWidgetData`,
nettoyage au retrait) ; `TodoList` ; `Stopwatch` ; `TimerPresets` ; `WorldClock` (+7 h, −6 h, +5 h 30 vu de Paris,
demain/hier, ☀/☾, recherche sans accents dans `Cities`) ; `Countdown` ; `StorageInfo` ; `BuiltinKind` (nouvelles
hauteurs et tailles, dont Minuteur sans S).

**Interface (Pixel 9)** : Note (saisie transmise) ; To-do (ajouter, cocher, effacer) ; Contacts (bulles, appel,
appui long SMS, invitation) ; Raccourcis (lampe, chaque action) ; Minuteur (« 5 min » → 300 s ; chrono démarrer,
pause, tour) ; feuilles (choisir une ville, choisir une date) ; miniatures des huit types.

**À la main** : vrai minuteur qui sonne, lampe torche, note conservée après redémarrage de Lanceur.
