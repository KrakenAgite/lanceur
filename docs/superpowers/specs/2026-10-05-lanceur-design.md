# Lanceur — spécification de conception

Date : 2026-10-05
Statut : en relecture

## 1. Objectif

Un launcher Android personnel pour un Pixel 9, installé par APK / `adb` (pas de Play Store).
Il remplace l'écran d'accueil par :

- une **liste de favoris** (icône + nom) ;
- une **barre alphabétique animée** sur le bord, pour atteindre toutes les autres applis ;
- une **recherche universelle** ouverte d'un glissement vers le haut (applis, web, agenda, contacts, réglages, calcul) ;
- un **dossier caché** protégé par biométrie, qui regroupe les applis masquées dans le launcher et l'Espace privé Android.

Le but premier du dossier caché est la **vie privée** : une personne qui prend le téléphone ne doit ni voir ni ouvrir ces applis depuis le launcher.

### Critères de réussite

1. Le launcher peut être défini comme écran d'accueil par défaut et le reste après redémarrage.
2. Depuis l'accueil, toute appli visible s'ouvre en au plus deux gestes (alphabet ou recherche).
3. Les animations (alphabet, ouverture de la recherche) sont fluides à 120 Hz sur le Pixel 9, sans saccade visible.
4. Aucune appli cachée (launcher ou Espace privé) n'apparaît dans l'accueil, l'alphabet ou la recherche.
5. Le dossier caché ne s'ouvre qu'après authentification et se reverrouille dès qu'on le quitte.

## 2. Hors périmètre

Widgets, dock, pages multiples, packs d'icônes, thèmes autres que Material You, publication sur le Play Store,
sauvegarde/restauration des réglages, traitement particulier du profil professionnel (s'il existe, ses applis
sont listées comme les autres avec le badge système), choix du moteur de recherche web.

## 3. Choix techniques

| Élément | Choix |
|---|---|
| Langage / UI | Kotlin, Jetpack Compose, Material 3 avec couleurs dynamiques (Material You) |
| SDK | `minSdk 35` (Android 15, nécessaire pour l'Espace privé) ; `compileSdk` / `targetSdk 35` (plateforme installée ; passage à 37 possible plus tard) |
| Build | Gradle (wrapper), JDK fourni par Android Studio (`/opt/android-studio/jbr`) |
| Persistance | Preferences DataStore, valeurs sérialisées en JSON (kotlinx.serialization) |
| Injection | Manuelle (un `AppContainer` créé dans `Application`), pas de Hilt |
| Navigation | État d'écran dans un ViewModel (`Home`, `Search`, `Vault`, `Settings`), pas de bibliothèque de navigation |
| Identifiant d'appli | `package/activité`, ex. `fr.exemple.app/fr.exemple.app.Main` |
| Tests | JUnit 4 pour la logique pure, Compose UI Test sur émulateur |

### Manifeste

- Activité principale avec les filtres `MAIN` + `HOME` + `DEFAULT`, `launchMode="singleTask"`, `stateNotNeeded`,
  `excludeFromRecents="true"`.
- `android:allowBackup="false"` : la liste des applis cachées ne part pas dans la sauvegarde Google.
- `<queries>` sur l'intent `MAIN`/`LAUNCHER` (pas besoin de `QUERY_ALL_PACKAGES`).
- Permissions :
  - `ACCESS_HIDDEN_PROFILES` : affichage de l'Espace privé (accordée quand l'appli tient le rôle HOME) ;
  - `USE_BIOMETRIC` ;
  - `READ_CALENDAR`, `READ_CONTACTS` : demandées à l'exécution, facultatives ;
  - `EXPAND_STATUS_BAR` : ouverture du volet des notifications ;
  - `REQUEST_DELETE_PACKAGES` : bouton « Désinstaller ».
- Les appels passent par `ACTION_DIAL` : aucune permission téléphone requise.

## 4. Architecture

Une seule app, un seul module Gradle, découpée en paquets avec un rôle chacun :

```
app.lanceur
├── apps/       Catalogue des applis (LauncherApps), profils, Espace privé
├── prefs/      Favoris (ordonnés), applis cachées, réglages
├── home/       Écran d'accueil : en-tête, favoris, intégration de l'alphabet
├── alphabet/   Composant barre alphabétique + logique d'index des lettres
├── search/     Écran de recherche + un fournisseur par source
├── vault/      Dossier caché : authentification, état verrouillé/déverrouillé, écran
└── settings/   Réglages du launcher
```

### Flux de données

```
LauncherApps ──► AppCatalog ─┐
                             ├──► VisibleApps (filtrage) ──► home / alphabet / search
DataStore ────► PrefsRepo ───┘
                                └──► HiddenApps ──► vault (si déverrouillé)
```

Les écrans ne font qu'afficher ce qu'on leur donne. Toute décision (filtrage, tri, classement) est prise dans des
classes Kotlin pures testables sans Android.

### 4.1 `apps`

- `AppCatalog` : expose un `StateFlow<List<AppEntry>>`. Il charge les activités de chaque profil retourné par
  `LauncherApps.getProfiles()` et s'abonne à `LauncherApps.Callback` (ajout, retrait, mise à jour de paquet, profil
  disponible ou non).
- `AppEntry` : `key` (package/activité + numéro de série du profil), `label`, `user`, `isPrivateSpace`.
- Profil privé : détecté par `getLauncherUserInfo(user).userType == USER_TYPE_PROFILE_PRIVATE`.
  - État verrouillé : `UserManager.isQuietModeEnabled(user)`.
  - Bascule : `UserManager.requestQuietModeEnabled(enable, user)`.
- `IconLoader` : chargement asynchrone des icônes, avec un cache LRU en mémoire indexé par `key`.

### 4.2 `prefs`

`PrefsRepo` (DataStore) stocke :

- `favorites` : liste ordonnée de clés ;
- `hidden` : ensemble de clés masquées dans le launcher ;
- `alphabetSide` : `RIGHT` (par défaut) ou `LEFT`.

`VisibleApps` (logique pure) prend le catalogue et les préférences, puis produit :

- `allVisible` : catalogue sans les applis `hidden` ni celles de l'Espace privé, trié ;
- `favorites` : favoris présents dans `allVisible`, dans l'ordre enregistré ;
- `hiddenApps` : applis `hidden` encore installées ;
- `privateApps` : applis de l'Espace privé (vide s'il est verrouillé ou n'existe pas).

Les clés qui ne correspondent plus à aucune appli installée sont retirées des préférences au chargement du catalogue.

### 4.3 `alphabet`

**`LetterIndex` (logique pure)**

- Normalise le libellé : passage en majuscules, accents retirés (`Normalizer` NFD), tri avec `Collator` français.
- Une appli dont le libellé commence par autre chose qu'une lettre A à Z est classée sous `#`.
- Expose les lettres `A`…`Z`, puis `#`, chacune avec ses applis et un indicateur « vide ».

**`AlphabetBar` (Compose)**

- Colonne de lettres sur toute la hauteur disponible. Les lettres vides sont grisées et ne se sélectionnent pas.
- Pendant le glissement, la lettre active est celle sous le doigt (lettre non vide la plus proche si besoin).
- Vague : pour chaque lettre à une distance `d` du doigt (en nombre de lettres), on calcule
  `f = exp(-d² / (2·σ²))` avec `σ = 1,5`. La lettre est agrandie (`échelle = 1 + 1,2·f`) et décalée vers
  l'intérieur de l'écran (`décalage = 28 dp · f`). Les transitions utilisent un ressort (`spring`,
  amortissement moyen) et la vague retombe en ressort quand on lâche.
- Une bulle de grande taille, à côté du doigt, affiche la lettre active.
- Vibration `SEGMENT_FREQUENT_TICK` (repli sur `CLOCK_TICK`) à chaque changement de lettre.
- Événements émis : `onLetterChanged(letter)`, `onRelease()`.

### 4.4 `home`

**Disposition**

- En-tête en haut : heure en grand, date en dessous.
  - Toucher l'heure ouvre l'Horloge (`AlarmClock.ACTION_SHOW_ALARMS`).
  - Toucher la date ouvre l'Agenda (`CalendarContract.CONTENT_URI/time`).
- Liste ancrée dans la moitié basse de l'écran. Le fond d'écran reste visible grâce au thème transparent
  (`windowShowWallpaper`), avec un voile en dégradé pour la lisibilité.

**Modes de la liste**

| Mode | Contenu |
|---|---|
| `Favorites` | Favoris, dans l'ordre enregistré |
| `Letter(x)` | Applis visibles de la lettre `x` |

Transitions :

- glissement sur l'alphabet → `Letter(x)`, mis à jour en direct ;
- toucher dans une zone vide, geste retour ou bouton Accueil → `Favorites` ;
- **glisser-pour-ouvrir** : pendant un glissement sur l'alphabet, si le doigt part vers l'intérieur de l'écran, la
  ligne sous le doigt est mise en surbrillance ; lâcher dessus ouvre l'appli.

**Gestes**

| Geste | Action |
|---|---|
| Glisser vers le haut (en mode `Favorites`) | Ouvre la recherche |
| Glisser vers le bas | Volet des notifications (`StatusBarManager.expandNotificationsPanel` par réflexion ; sans effet si indisponible) |
| Appui long sur une appli | Menu : *Ajouter aux favoris* / *Retirer des favoris*, *Cacher*, *Infos de l'appli*, *Désinstaller* |
| Appui long sur l'heure | Authentification, puis dossier caché |
| Appui long sur une zone vide | Réglages du launcher |

Les favoris se réordonnent depuis les réglages (§ 4.7), avec des poignées de glisser-déposer.

L'ouverture d'une appli passe par `LauncherApps.startMainActivity` (gère aussi les profils).

### 4.5 `search`

**Écran**

- Ouverture en glissement depuis le bas.
- Champ de saisie en bas de l'écran, au-dessus du clavier, avec le focus et le clavier déjà ouverts.
- Résultats au-dessus du champ, mis à jour à chaque frappe (anti-rebond de 80 ms pour l'agenda et les contacts).
- Fermeture : glisser vers le bas, geste retour, ou ouverture d'un résultat.
- La touche Entrée ouvre le premier résultat.

**Fournisseurs**

Interface `SearchProvider { suspend fun search(query: String): List<SearchResult> }`. Chaque fournisseur s'exécute
en parallèle et l'écran fusionne les résultats dans cet ordre fixe :

1. **Applis** (`AppSearchProvider`, uniquement sur `allVisible`), au plus 8 résultats. Classement, puis ordre
   alphabétique à égalité :
   - le libellé commence par la requête ;
   - un mot du libellé commence par la requête ;
   - le libellé contient la requête.

   La comparaison ignore les accents et la casse.
2. **Calcul** (`CalcProvider`) : uniquement si la requête est une expression arithmétique valide.
   - Analyseur maison à descente récursive, sans aucun `eval`.
   - Gère `+ - * / % ^`, les parenthèses, la virgule comme le point décimal, et `×`, `÷`.
   - Résultat arrondi à 10 chiffres significatifs ; division par zéro affichée « — ».
   - Toucher le résultat le copie dans le presse-papiers.
3. **Contacts** (`ContactsProvider`, si la permission est accordée), au plus 5 résultats.
   - Recherche par nom via `ContactsContract.Contacts.CONTENT_FILTER_URI`.
   - Chaque ligne a des boutons Appeler (`ACTION_DIAL`) et SMS (`ACTION_SENDTO smsto:`).
   - Toucher la ligne ouvre la fiche du contact.
4. **Agenda** (`CalendarProvider`, si la permission est accordée), au plus 5 résultats.
   - Instances de maintenant à J+90 via `CalendarContract.Instances`, dont le titre ou le lieu correspond.
   - Triées par date ; toucher une ligne ouvre l'événement.
5. **Réglages** (`SettingsProvider`), au plus 3 résultats.
   - Liste statique de raccourcis `Settings.ACTION_*` avec des mots-clés français : Wi-Fi, Bluetooth, Affichage,
     Batterie, Son, Applis, Notifications, Localisation, Sécurité, Stockage, Réseau, Date et heure, Accessibilité,
     À propos.
6. **Web** (toujours présent, en dernière ligne) : « Rechercher « … » sur le web ».
   - `Intent.ACTION_WEB_SEARCH` ; repli sur `ACTION_VIEW https://www.google.com/search?q=…`.
   - La requête est encodée dans l'URL de l'intent uniquement au moment où l'utilisateur la lance.

**Permissions refusées**

Si l'agenda ou les contacts ne sont pas autorisés, une seule ligne « Autoriser l'accès à l'agenda et aux contacts »
apparaît en bas des résultats tant que l'utilisateur ne l'a pas écartée. Le fait de l'avoir écartée est enregistré
dans `PrefsRepo`.

**Erreurs**

Un fournisseur qui lève une exception renvoie une liste vide ; l'erreur est journalisée (`Log.w`) et les autres
sources s'affichent normalement.

### 4.6 `vault`

**`VaultStateMachine` (logique pure)**

États : `Locked` → `Authenticating` → `Unlocked` → `Locked`.

- `Locked` → `Authenticating` : appui long sur l'heure.
- `Authenticating` → `Unlocked` : authentification réussie.
- `Authenticating` → `Locked` : échec ou annulation.
- `Unlocked` → `Locked` : activité en arrière-plan (`ON_STOP`), écran éteint (`ACTION_SCREEN_OFF`), bouton Accueil
  ou sortie de l'écran du dossier.

L'état n'existe qu'en mémoire.

**Authentification**

- `BiometricPrompt` avec `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`.
- Si `BiometricManager.canAuthenticate(...)` signale l'absence de tout verrouillage d'écran, le dossier ne s'ouvre
  pas et un message invite à configurer un verrouillage d'écran.

**Écran du dossier**

- `FLAG_SECURE` est posé tant que l'écran est affiché : pas de capture et pas d'aperçu dans le multitâche.
- Bloc **Masquées** : `hiddenApps`. L'appui long donne *Ne plus cacher* et *Infos*.
- Bloc **Espace privé** :
  - s'il n'existe pas : un texte explique comment le créer (*Paramètres > Sécurité et confidentialité > Espace
    privé*) ;
  - s'il est verrouillé : un bouton *Déverrouiller*, qui appelle `requestQuietModeEnabled(false)` ; le système peut
    alors demander son propre verrou ;
  - s'il est ouvert : la liste `privateApps` et un bouton *Verrouiller*.
- Quand le dossier se reverrouille, l'Espace privé n'est pas reverrouillé automatiquement : c'est le système qui
  gère sa propre politique de verrouillage.

### 4.7 `settings`

- Rendre ce launcher par défaut : `RoleManager.createRequestRoleIntent(ROLE_HOME)`.
- Réordonner les favoris (glisser-déposer).
- Gérer les applis cachées. Cette entrée demande la même authentification que le dossier caché.
- Accorder les permissions agenda et contacts.
- Choisir le côté de l'alphabet : gauche ou droite.

## 5. Sécurité et limites assumées

Garanti par le launcher :

- Applis cachées et applis de l'Espace privé absentes de l'accueil, de l'alphabet et de la recherche.
- Dossier caché et gestion des applis cachées protégés par une authentification forte ou le code de l'appareil.
- Aucune donnée envoyée hors du téléphone ; aucune sauvegarde cloud.

Limites d'Android, documentées et non contournées :

- Le multitâche est géré par le système : une appli masquée dans le launcher, ouverte récemment, y reste visible.
- Les notifications des applis masquées dans le launcher continuent de s'afficher.
- Les applis masquées dans le launcher restent visibles dans *Paramètres > Applis* et sur le Play Store.

Pour une protection complète, il faut utiliser l'Espace privé.

## 6. Cas limites

| Cas | Comportement |
|---|---|
| Appli désinstallée | Retirée du catalogue ; sa clé est retirée des favoris et des applis cachées |
| Appli installée | Apparaît dans l'alphabet et la recherche, pas dans les favoris |
| Appli cachée alors qu'elle est en favori | Retirée des favoris ; si elle est ensuite démasquée, elle ne revient pas dans les favoris |
| Deux applis de même nom | Distinctes grâce à la clé activité + profil |
| Libellé commençant par un accent, un chiffre ou un emoji | « É » est classé sous E ; un chiffre ou un emoji sous `#` |
| Lettre sans appli | Grisée, ignorée pendant le glissement |
| Aucun favori | Message « Appui long sur une appli pour l'ajouter aux favoris » |
| Espace privé verrouillé pendant que le dossier est ouvert | Le bloc repasse en « verrouillé » via le callback de profil |
| Appli lancée qui n'existe plus | Message court « Appli introuvable », puis rafraîchissement du catalogue |
| Rotation | Orientation verrouillée en portrait |

## 7. Tests

**Tests unitaires (JUnit 4, sans Android)**

- `LetterIndex` : accents, casse, `#`, lettres vides, tri français.
- `VisibleApps` : filtrage des applis cachées et privées, ordre des favoris, nettoyage des clés orphelines.
- `AppSearchProvider` : classement et insensibilité aux accents.
- `CalcProvider` : priorité des opérateurs, parenthèses, virgule décimale, division par zéro, entrées invalides
  (pas de résultat).
- `SettingsProvider` : correspondance des mots-clés.
- `VaultStateMachine` : toutes les transitions, y compris l'échec et l'écran éteint.
- Fusion des résultats de recherche : ordre des sections, limites par section, fournisseur en erreur.

**Tests d'interface (Compose UI Test, émulateur)**

- Un glissement sur l'alphabet affiche les applis de la lettre ; lâcher puis toucher une appli l'ouvre.
- Glisser vers le haut ouvre la recherche ; taper une requête affiche la ligne web en dernier.

**Vérification manuelle sur le Pixel 9 (`adb install`)**

- [ ] Définir comme launcher par défaut, redémarrer : il reste par défaut.
- [ ] Ajouter, retirer et réordonner les favoris.
- [ ] Glisser sur l'alphabet : la vague est fluide, une vibration par lettre, le glisser-pour-ouvrir fonctionne.
- [ ] Recherche : applis, calcul, contacts, agenda, réglages, web.
- [ ] Cacher une appli : elle disparaît de l'accueil, de l'alphabet et de la recherche.
- [ ] Dossier caché : empreinte, visage, code en secours ; se reverrouille à l'extinction de l'écran.
- [ ] Espace privé : affiché, déverrouillé et verrouillé depuis le dossier.
- [ ] Glisser vers le bas : le volet des notifications s'ouvre.
