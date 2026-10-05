# Lanceur

Launcher Android personnel pour Pixel 9 : favoris en liste, alphabet animé, recherche universelle
(applis, web, agenda, contacts, réglages, calcul) et dossier caché protégé par l'empreinte, avec l'Espace privé.

Conception : `docs/superpowers/specs/2026-10-05-lanceur-design.md`.

## Compiler et installer

Téléphone branché en USB, débogage USB activé :

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:installDebug
```

Puis sur le téléphone : *Paramètres > Applis > Applis par défaut > Appli d'écran d'accueil > Lanceur*.

## Tests

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest
```

Les tests d'interface tournent sur le téléphone branché (USB ou débogage sans fil) et désinstallent l'app à la fin :
relancer `installDebug` ensuite. Espresso est fixé en 3.7.0 : la version tirée par Compose ne marche pas sous Android 17.

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
