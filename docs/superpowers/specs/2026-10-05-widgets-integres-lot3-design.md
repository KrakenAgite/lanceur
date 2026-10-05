# Lanceur — widgets intégrés, lot 3 : spécification de conception

Date : 2026-10-05
Statut : en relecture
Complète : `docs/superpowers/specs/2026-10-05-widgets-integres-lot2-design.md`

## 1. Objectif

Ajouter les deux widgets intégrés qui ont besoin d'Internet : **Météo** et **Flux RSS**. Ils reprennent toute la
base des lots 1 et 2 : `BuiltinKind`, données par widget (`WidgetData`, `widget_data_<id>`), feuille de réglages à
l'ajout et par ⚙, style du résumé du jour, miniatures.

Lanceur obtient pour cela la permission **Internet**. C'est une décision de l'utilisateur, prise en connaissance de
cause : la garantie « rien ne sort du téléphone » est remplacée par des protections (§3).

### Critères de réussite

1. La Météo d'une ville choisie, ou de la position approximative au choix, s'affiche en S / M / L.
2. Le RSS montre les articles récents de 1 à 3 flux (adresses collées ou suggestions) et les ouvre au toucher.
3. Sans réseau, les deux widgets montrent leurs dernières données avec leur ancienneté.
4. Lanceur ne se connecte que pour ces deux widgets, quand ils sont posés et que la page de widgets s'affiche, au plus
   une fois par 30 minutes par widget ; jamais en arrière-plan.
5. La localisation n'est demandée que si l'utilisateur choisit « Ma position ».

## 2. Hors périmètre

Alertes météo, radar de pluie, notifications, actualisation en arrière-plan, lecture des articles dans Lanceur,
images des articles, flux en HTTP, flux protégés par mot de passe, OPML.

## 3. Protections

| Protection | Règle |
|---|---|
| Point de sortie unique | Seule la classe `net/Network` ouvre des connexions ; un test JVM parcourt `app/src/main` et échoue si un autre fichier que `builtin/weather/*` et `builtin/rss/*` l'utilise, ou si `HttpURLConnection` / `URL(` apparaît ailleurs que dans `Network` |
| HTTPS seulement | `Network` refuse toute URL non `https://`, y compris après redirection (3 au plus) ; Android bloque déjà le HTTP non chiffré (targetSdk ≥ 28) |
| Limites | Connexion et lecture 10 s ; réponse lue jusqu'à 1 Mo, au-delà erreur |
| Rien d'identifiant | Pas de cookie, pas d'en-tête personnalisé hormis `User-Agent: Lanceur`, pas d'identifiant |
| Données envoyées | Météo : latitude/longitude arrondies à 0,01° ; géocodage : le texte tapé dans la recherche de ville ; RSS : rien (simple GET de l'adresse) |
| Fréquence | À l'affichage de la page de widgets, seulement si les données ont plus de 30 min ; jamais en arrière-plan |
| Localisation | `ACCESS_COARSE_LOCATION` demandée à l'exécution, uniquement au choix de « Ma position » ; refus → retour au mode Ville |

## 4. Choix techniques

| Élément | Choix |
|---|---|
| Réseau | `HttpURLConnection` dans `Network.get(url): NetResult` (`Ok(body)` / `Failed(reason)`), sur `Dispatchers.IO` |
| JSON | `org.json` d'Android dans l'appli ; `testImplementation("org.json:json")` pour les tests JVM |
| XML | `javax.xml.parsers.DocumentBuilderFactory` (disponible sur Android et sur la JVM), DTD et entités externes désactivées |
| Météo | `https://api.open-meteo.com/v1/forecast` avec `current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,precipitation_probability`, `hourly=temperature_2m,weather_code,precipitation_probability`, `daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max`, `timezone=auto`, `forecast_days=6` |
| Recherche de ville | `https://geocoding-api.open-meteo.com/v1/search?name=…&count=8&language=fr` |
| Position | `LocationManager.getLastKnownLocation(NETWORK_PROVIDER)` si elle a moins d'1 h, sinon `getCurrentLocation(NETWORK_PROVIDER, …)` ; localisation désactivée → « Position indisponible » |
| Cache | Dans les données du widget : réponse brute (`cache`) et heure (`fetchedAt`) ; supprimé avec le widget |
| Ouverture | Météo : `https://www.google.com/search?q=météo+<ville>` ; article : son lien, via `ACTION_VIEW` |

### Hauteurs (dp)

| Widget | S | M | L | Défaut | Configurable |
|---|---|---|---|---|---|
| Météo | 120 | 220 | 340 | M | oui |
| Flux RSS | 160 | 280 | 420 | M | oui |

## 5. Architecture

```
app.lanceur
├── net/Network.kt                  seul point de sortie (HTTPS, limites, redirections)
└── builtin/
    ├── weather/
    │   ├── WeatherConfig.kt        mode Ville (nom, lat, lon) ou Ma position ; toData/fromData (pur)
    │   ├── Forecast.kt             lecture de la réponse Open-Meteo, codes → icône/libellé, heures, jours (pur)
    │   ├── Geocoding.kt            lecture de la recherche de ville (pur)
    │   ├── WeatherSource.kt        position, appels réseau, cache 30 min (Android)
    │   ├── WeatherCard.kt
    │   └── WeatherSettings.kt
    └── rss/
        ├── RssConfig.kt            1 à 3 adresses, suggestions (pur)
        ├── Feed.kt                 lecture RSS 2.0 / Atom, nettoyage, dates, fusion, doublons (pur)
        ├── RssSource.kt            téléchargements, cache 30 min (Android)
        ├── RssCard.kt
        └── RssSettings.kt
```

`Freshness.isStale(fetchedAt, now)` (pur, 30 min) et `Freshness.label(fetchedAt, now)` (« Mis à jour à 14:05 »,
« il y a 2 h ») sont partagés par les deux widgets.

## 6. Widgets

**Météo.** S : ville, icône, température actuelle, « Min 9° · Max 17° ». M : + ressenti, vent (km/h), pluie (%),
6 prochaines heures. L : + 5 prochains jours (jour, icône, min/max, pluie). Pied : fraîcheur. Toucher : météo de
Google pour la ville. Codes WMO : 0 ☀ Ensoleillé ; 1–2 ⛅ Éclaircies ; 3 ☁ Couvert ; 45, 48 🌫 Brouillard ;
51–57 🌦 Bruine ; 61–67, 80–82 🌧 Pluie ; 71–77, 85–86 ❄ Neige ; 95–99 ⛈ Orage ; autre 🌡.
Feuille : « Ville » (recherche, 8 résultats « Nantes, Pays de la Loire ») ou « Ma position ».

**Flux RSS.** Titre « ACTUALITÉS » ; articles de tous les flux, du plus récent au plus ancien (sans date à la fin),
sans doublon de lien ; titre sur 2 lignes, « source · il y a 25 min » ; 3 / 6 / 10 articles en S / M / L, défilement
interne. Toucher : ouvre l'article. Feuille : 3 champs d'adresse + suggestions à cocher (Le Monde, France Info,
Libération, Numerama, Les Numériques, Korben), chaque flux testé à l'enregistrement (« ✓ Le Monde — 20 articles »,
« ✗ Pas un flux RSS/Atom », « ✗ Adresse HTTPS requise »).

## 7. Cas limites

| Cas | Comportement |
|---|---|
| Réseau absent, délai dépassé | Dernières données + « il y a 2 h » ; sans données : « Météo indisponible » / « Flux indisponibles » |
| Réponse > 1 Mo, illisible | Erreur, sans plantage |
| Adresse `http://` | Refusée dans la feuille |
| Redirection | Suivie (3 au plus), HTTPS seulement |
| Ville introuvable | « Aucune ville trouvée » |
| Localisation désactivée, permission retirée | « Position indisponible » + « Choisir une ville » |
| Code météo inconnu | 🌡 |
| Fuseau de la ville | Heures locales de la ville (`timezone=auto`) |
| HTML et entités dans un titre | Nettoyés |
| Article sans date | Après ceux qui en ont une |
| Même lien dans deux flux | Une seule fois |
| Un flux en erreur | Ignoré, les autres s'affichent |
| Plusieurs widgets Météo | Ville et intervalle propres à chacun |
| Widget retiré | Données et cache supprimés |

## 8. Tests

**JVM** : `Network` (refus du HTTP, redirection vers HTTP refusée — logique de décision pure), garde-fou du point de
sortie unique (lecture des sources) ; `Forecast` sur une réponse Open-Meteo enregistrée (actuel, min/max, 6 heures
à partir de maintenant, 5 jours), codes WMO ; `Geocoding` ; `WeatherConfig` et `RssConfig` (aller-retour, HTTPS) ;
`Feed` sur un RSS 2.0 et un Atom enregistrés (titres nettoyés, entités, dates RFC 822 et ISO 8601, fusion, tri,
doublons, limite) ; `Freshness` (30 min, libellés).

**Interface (Pixel 9)** : `WeatherCard` S / M / L avec prévision fournie ; `RssCard` (liste, ouverture, message
d'erreur) ; feuilles (ville trouvée via une recherche fournie, choix « Ma position », suggestion cochée).

**À la main** : vraie météo (Ville, puis Ma position), vrai flux Le Monde, mode avion.
