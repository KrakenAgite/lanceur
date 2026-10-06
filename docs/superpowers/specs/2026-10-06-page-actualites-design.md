# Lanceur — page Actualités et pages réordonnables : spécification de conception

Date : 2026-10-06
Statut : en relecture
Complète : `docs/superpowers/specs/2026-10-05-widgets-integres-lot3-design.md` (réseau, lecture des flux)

## 1. Objectif

1. **Pages réordonnables** : Réglages › Pages liste Actualités, Widgets et Accueil. On active ou désactive chacune
   (sauf l'Accueil) et on choisit leur ordre de gauche à droite.
2. **Page Actualités** : une page plein écran de flux RSS/Atom, avec une rangée de bulles de filtre en haut et la
   liste des articles en cartes, l'image du flux sous le titre.

### Critères de réussite

1. Avec `Actualités · Widgets · Accueil`, deux glissements vers la droite depuis l'accueil mènent aux Actualités ;
   avec `Widgets · Accueil · Actualités`, un glissement vers la gauche y mène.
2. Lanceur démarre sur l'Accueil ; le bouton Accueil et le geste Retour y ramènent depuis toute page.
3. Les bulles filtrent par flux ; « + » ajoute un flux (vérifié) ; un appui long retire un flux.
4. Les articles s'affichent du plus récent au plus ancien, avec l'image du flux quand il y en a une.
5. Les protections réseau du lot 3 s'appliquent à tout, images comprises ; aucune connexion en arrière-plan.

## 2. Hors périmètre

Lecture des articles dans Lanceur, image d'aperçu tirée de la page web de l'article, notifications de nouveaux
articles, articles lus / non lus, recherche dans les articles, partage, OPML, plus de trois pages.

## 3. Choix techniques

| Élément | Choix |
|---|---|
| Pages | `enum PageKind { NEWS, WIDGETS, HOME }` ; réglage `pages: List<PageEntry(kind, enabled)>` (ordre = défilement) dans `LauncherPrefs` ; `HOME` toujours activée ; migration : sans réglage `pages`, ordre `NEWS(off) · WIDGETS(widgetPageEnabled) · HOME` |
| Logique des pages | `PageLayout` (pur) : `active(pages)`, `homeIndex(active)`, liste des pages actives, validation (une seule de chaque, `HOME` présente et activée) |
| Pager | `HomePager` généralisé à N pages (1 à 3) : démarre sur `homeIndex`, `homePageRequests` y ramène, recréé quand la liste active change ; Retour vers l'accueil depuis toute autre page |
| Réglages › Pages | Liste réordonnable par poignée (`Reorder.move`, comme les favoris) avec interrupteur ; remplace l'interrupteur « Page de widgets » |
| Flux de la page | `NewsConfig(feeds: List<String>)` + cache `NewsCache(perFeed: Map<url, FeedSnapshot(title, articles, fetchedAt, failed)>)`, stockés sous une clé DataStore `news` |
| Lecture | `Feed.parse` du lot 3, étendu : `Article.image: String?` (`media:content` / `media:thumbnail` d'image, `enclosure` `image/*`, premier `<img src>` de la description) ; lien relatif résolu par rapport au lien de l'article ; HTTPS exigé, sinon `null` |
| Fil | `NewsFeed` (pur) : fusion, tri récent d'abord, sans doublon de lien, 30 articles au plus par flux, filtre par flux, libellés de bulles (noms en double → `Nom · domaine`) |
| Actualisation | À l'affichage de la page si un flux a plus de 30 min ; bouton ⟳ et « tirer pour actualiser » (`PullToRefreshBox` Material 3) forcent ; flux téléchargés en parallèle, lecture XML sur `Dispatchers.Default` |
| Images | `ImageLoader` : téléchargement par `Network` (HTTPS, 1 Mo), décodage réduit (`inSampleSize` calculé par `ImageSizing.sampleSize(w, h, cible)`, pur), cache mémoire `LruCache` (40 images) et cache disque dans `cacheDir/news-images` (50 Mo, les plus anciens effacés, `DiskCachePolicy`, pur) ; chargement lié à la composition de la carte (annulé hors écran) |
| Garde-fou | `NetworkGuardTest` autorise aussi `news/` à utiliser `Network` |

## 4. Architecture

```
app.lanceur
├── home/
│   ├── PageKind.kt          PageKind, PageEntry (pur)
│   ├── PageLayout.kt        pages actives, accueil, validation, migration (pur)
│   └── HomePager.kt         généralisé à N pages
├── settings/SettingsScreen.kt   section « Pages »
├── builtin/rss/Feed.kt      + Article.image
└── news/
    ├── NewsConfig.kt        flux, cache par flux, encodage (pur)
    ├── NewsFeed.kt          fusion, filtre, bulles (pur)
    ├── NewsSource.kt        actualisation (Android, Network)
    ├── ImageSizing.kt       inSampleSize, politique du cache disque (pur)
    ├── ImageLoader.kt       téléchargement, décodage, caches (Android, Network)
    ├── NewsPage.kt          bulles, liste, cartes, tirer pour actualiser
    └── NewsFeedSheet.kt     ajout d'un flux (réutilise RssSettings en mode « un flux à la fois »)
```

## 5. Page Actualités

- En-tête : `CardLabel("Actualités")`, bouton ⟳ (« Actualiser »).
- Bulles (`FilterChip`, `LazyRow`) : « Tout », une par flux (pastille ⚠ si le dernier essai a échoué), « + ». Toucher
  filtre ; appui long → menu « Retirer ce flux ». La rangée défile seule, sans changer de page.
- Liste (`LazyColumn`) : cartes `cardBackground()`, `CardShape` ; source en `CardLabel` + « il y a 25 min » ;
  titre `titleMedium` gras, 3 lignes ; image 16:9 recadrée, coins `28 dp − marge`. Sans image : texte seul.
  Toucher : ouvre l'article (liens web uniquement, comme le widget).
- Pied : « Mis à jour à 14:05 » ou « Hors ligne · il y a 2 h ».
- Vide : carte « Ajoute tes premiers flux » qui ouvre la feuille.
- Tous les flux en erreur sans article : « Flux indisponibles » + « Réessayer ».

## 6. Cas limites

| Cas | Comportement |
|---|---|
| Page désactivée puis réactivée | Mêmes flux, même place |
| Ancien réglage `widgetPageEnabled = false` | Widgets désactivée, Actualités désactivée, ordre `Widgets · Accueil` (Actualités en tête, désactivée) |
| Seul l'Accueil actif | Pas de défilement horizontal |
| Ordre changé hors de l'accueil | Retour à l'Accueil |
| Flux injoignable | Articles gardés + ⚠ sur sa bulle |
| Tous injoignables, rien de gardé | « Flux indisponibles » + Réessayer |
| Image HTTP, > 1 Mo, illisible | Pas d'image |
| Image très haute ou large | Recadrée 16:9 |
| Lien d'image relatif | Résolu, puis HTTPS exigé |
| Filtre sur un flux retiré | Retour à « Tout » |
| Deux flux de même nom | `Nom · domaine` |
| Défilement rapide | Chargements hors écran annulés |

## 7. Tests

**JVM** : `PageLayout` (actives, accueil, validation, migration, Accueil non désactivable) ; `Feed` (images :
`media:content`, `media:thumbnail`, `enclosure`, `<img>`, relatif, HTTP refusé) ; `NewsConfig` (aller-retour,
retrait d'un flux) ; `NewsFeed` (fusion, tri, doublons, 30 par flux, filtre, bulles homonymes, filtre réinitialisé) ;
`ImageSizing` (facteur de réduction, effacement au-delà de 50 Mo) ; `NetworkGuardTest` étendu ; `PrefsRepo` (pages).

**Interface (Pixel 9)** : Réglages › Pages (interrupteur, réordonnancement) ; pager à trois pages (aller aux
Actualités, Accueil ramène) ; `NewsPage` avec données fournies (filtre, « + », appui long → retirer, ouverture d'un
article, carte sans image, message d'erreur).

**À la main** : Actualités à droite de l'accueil, Le Monde et Numerama avec images, filtre, mode avion.
