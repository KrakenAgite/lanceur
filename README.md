# Lanceur

Launcher Android personnel pour Pixel 9 : favoris en liste, alphabet animé, recherche universelle
(applis, web, agenda, contacts, réglages, calcul) et dossier caché protégé par l'empreinte, avec l'Espace privé.

Conception : `docs/superpowers/specs/2026-10-05-lanceur-design.md`.

## Compiler et installer

La version de tous les jours est la **release**, signée avec ta propre clé. Téléphone branché (USB ou débogage sans fil) :

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleRelease
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk
```

`install -r` met à jour l'app en gardant favoris, applis cachées et rôle d'écran d'accueil.
Au premier essai : *Paramètres > Applis > Applis par défaut > Appli d'écran d'accueil > Lanceur*.

### Clé de signature

- La clé est dans `~/.android/lanceur/release.jks` ; son mot de passe est dans `keystore.properties`, à la racine
  du projet. Les deux sont exclus de git.
- **Sauvegarde ces deux fichiers** (gestionnaire de mots de passe, disque externe). Sans eux, impossible de mettre
  à jour l'app installée : il faudrait la désinstaller, ce qui efface ses réglages.

### Version debug

La version de test est une app distincte, `app.lanceur.debug`, nommée « Lanceur (test) ». Les tests d'interface
l'installent puis la désinstallent à la fin ; elle ne touche jamais à la release signée (`app.lanceur`), la seule à
utiliser au quotidien. Après des tests, mettre à jour la release avec `adb install -r` (les réglages sont conservés).

## Tests

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest
```

Les tests d'interface tournent sur le téléphone branché (USB ou débogage sans fil). L'app reste installée
ensuite (`android.injected.androidTest.leaveApksInstalledAfterRun` dans `gradle.properties`) : ses réglages et son
rôle d'écran d'accueil sont conservés. Espresso est fixé en 3.7.0 : la version tirée par Compose ne marche pas sous Android 17.

## Gestes

| Geste | Action |
|---|---|
| Glisser sur l'alphabet | Applis de la lettre ; glisser vers une ligne et lâcher pour l'ouvrir |
| Glisser vers le haut | Recherche |
| Glisser vers le bas | Notifications |
| Appui long sur une appli | Favori, ranger dans un dossier, cacher, infos, désinstaller |
| Glisser sur les dossiers | Comme l'alphabet : chaque dossier s'ouvre au passage ; glisser vers une ligne et lâcher pour l'ouvrir |
| Toucher l'icône d'un dossier | Ouvre le dossier ; retoucher (ou toucher une zone vide) revient aux favoris |
| Appui long sur l'icône d'un dossier | Renommer, changer l'icône, supprimer |
| Appui long sur l'heure | Dossier caché (empreinte) |
| Appui long sur une zone vide | Réglages |
| Double toucher sur une zone vide | Mise en veille (voir ci-dessous) |
| Glisser vers la droite | Page de widgets (désactivable dans les réglages) |

## Dossiers

Leurs icônes sont dans l'angle en haut, au-dessus de l'alphabet. Pour en créer un : appui long sur une appli ›
*Ranger dans un dossier…* › *Nouveau dossier*, puis un nom et une icône parmi la sélection. La même feuille coche ou
décoche l'appli dans chaque dossier ; une appli peut être dans plusieurs dossiers. Une appli cachée n'y apparaît pas,
une appli désinstallée en est retirée. Supprimer un dossier ne touche pas aux applis.

## Double toucher pour verrouiller

Android ne laisse une appli mettre l'écran en veille, sans bloquer ensuite l'empreinte, que par un service
d'accessibilité. Celui de Lanceur ne reçoit aucun événement et ne lit rien à l'écran.

1. *Paramètres > Accessibilité > Lanceur : double toucher pour verrouiller* → activer (ou *Réglages* de Lanceur > *Activer*).
2. Si l'option est grisée (« Paramètre restreint », fréquent pour une appli installée hors Play Store) :
   *Paramètres > Applis > Lanceur > ⋮ > Autoriser les paramètres restreints*, puis recommencer l'étape 1.

## Page de widgets

À gauche de l'accueil : le résumé du jour (prochains événements, alarme suivante), puis tes widgets en pleine largeur.
*+ Ajouter un widget* ouvre le sélecteur ; la première fois, Android demande d'autoriser Lanceur à créer des widgets.
*Modifier* permet de les déplacer (poignée), de choisir leur hauteur (S, M, L), de les reconfigurer ou de les retirer.
Les widgets d'une appli cachée ou de l'Espace privé ne sont jamais proposés ni affichés.

### Widgets intégrés

Le sélecteur propose en tête un groupe « Lanceur » : Lecture en cours, Agenda · mois, Agenda · semaine,
Batterie · anneau, Batterie · barre, Note rapide, To-do, Compte à rebours, Horloges du monde,
Minuteur / chrono, Contacts favoris, Raccourcis rapides, Stockage et mémoire, Météo et Flux RSS. Ils s'ajoutent sans
autorisation et se déplacent, se redimensionnent et se retirent comme les autres.

- **Note rapide / To-do** : on écrit directement sur la carte ; chaque widget a son propre contenu, gardé dans les
  réglages de Lanceur (jamais envoyé ailleurs) et effacé quand on retire le widget.
- **Compte à rebours / Horloges du monde** : une feuille de réglages s'ouvre à l'ajout ; ⚙ en mode Modifier la rouvre.
- **Minuteur** : les pastilles lancent un minuteur de l'Horloge, qui sonne même Lanceur fermé.
- **Contacts favoris** : ceux marqués d'une étoile dans Contacts ; toucher appelle, appui long envoie un SMS.
- **Raccourcis** : la lampe s'allume depuis le widget ; Internet, Bluetooth, Son et Ne pas déranger ouvrent le
  panneau d'Android (une appli ne peut pas les changer elle-même).
- **Météo / Flux RSS** : les seuls widgets qui utilisent Internet, par un point de sortie unique (HTTPS seulement,
  vérifié par un test). Ils se connectent quand la page de widgets s'affiche, au plus toutes les 30 minutes, jamais en
  arrière-plan. La météo (Open-Meteo, sans compte) ne reçoit que des coordonnées arrondies au kilomètre ; la position
  approximative n'est demandée que si tu choisis « Ma position ». Sans réseau, les dernières données restent affichées.

- **Lecture en cours** : la première fois, toucher « Autoriser l'accès aux lecteurs » et activer Lanceur dans
  l'accès aux notifications (Lanceur ne lit aucune notification). Si l'option est grisée : Infos de l'appli ›
  ⋮ › Autoriser les paramètres restreints, comme pour le double toucher.
- **Agendas** : utilisent la même autorisation que le résumé du jour ; glisser sur le mois change de mois.
- **Batterie** : aucune autorisation ; le niveau des accessoires Bluetooth n'est pas disponible pour les applis.

## Pages et Actualités

Réglages › Pages : active ou désactive Actualités et Widgets, et choisis leur ordre de gauche à droite avec ↑ / ↓
(l'Accueil reste toujours là). Lanceur démarre sur l'Accueil ; le bouton Accueil et le geste Retour y ramènent.

La page **Actualités** rassemble tes flux RSS/Atom : bulles en haut pour filtrer (« Tout » ou un flux), « + » pour en
ajouter, appui long pour en retirer, puis les articles du plus récent au plus ancien, avec l'image fournie par le flux.
Tirer vers le bas ou ⟳ actualise ; sinon la page se met à jour à l'affichage, au plus toutes les 30 minutes, jamais
en arrière-plan. Tout passe par le même point de sortie réseau que la Météo et le RSS (HTTPS seulement) ; les images
sont gardées en cache (50 Mo au plus).

## Limites d'Android

Une appli seulement masquée dans le launcher reste visible dans le multitâche, dans *Paramètres > Applis*,
et ses notifications s'affichent toujours. Pour une vraie protection, place-la dans l'Espace privé.
