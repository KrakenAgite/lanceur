package app.lanceur.apps.icons

/** Choix de l'outil de fond d'écran : celui de la marque du téléphone d'abord, jamais Lanceur. */
object WallpaperPicker {
    /** Outils des constructeurs, du plus spécifique au plus générique. */
    private val BRANDS = listOf(
        "com.google.android.apps.wallpaper", // Pixel : Fond d'écran et style
        "com.samsung.android.app.dressroom", // Samsung
        "com.android.thememanager", // Xiaomi, Redmi, Poco
        "com.miui.miwallpaper",
        "com.oplus.wallpapers", // OnePlus, Oppo, Realme
        "com.oneplus.wallpaper",
        "com.huawei.android.thememanager", // Huawei, Honor
        "com.hihonor.android.thememanager",
        "com.motorola.personalize", // Motorola
        "com.nothing.wallpaper", // Nothing
        "com.sonymobile.wallpaper", // Sony
        "com.android.wallpaper", // Android standard
    )

    fun pick(candidates: List<String>): String? {
        val others = candidates.filterNot { it.startsWith("app.lanceur") }
        return BRANDS.firstOrNull { it in others } ?: others.firstOrNull { !it.endsWith(".livepicker") } ?: others.firstOrNull()
    }
}
