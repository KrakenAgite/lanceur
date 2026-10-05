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

`installDebug` installe une app distincte, `app.lanceur.debug`, à côté de la release : elle sert au développement et
aux tests d'interface, sans jamais toucher à la version que tu utilises.

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
| Appui long sur une appli | Favori, cacher, infos, désinstaller |
| Appui long sur l'heure | Dossier caché (empreinte) |
| Appui long sur une zone vide | Réglages |

## Limites d'Android

Une appli seulement masquée dans le launcher reste visible dans le multitâche, dans *Paramètres > Applis*,
et ses notifications s'affichent toujours. Pour une vraie protection, place-la dans l'Espace privé.
