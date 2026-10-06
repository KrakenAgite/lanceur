package app.lanceur.apps.icons

import app.lanceur.apps.AppKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconStyleTest {
    @Test
    fun shape_decodes_by_name_and_falls_back_to_system() {
        assertEquals(IconShape.CIRCLE, IconShape.decode("CIRCLE"))
        assertEquals(IconShape.SYSTEM, IconShape.decode("NOPE"))
        assertEquals(IconShape.SYSTEM, IconShape.decode(null))
    }

    @Test
    fun appfilter_component_is_read_with_or_without_package_prefix() {
        assertEquals("com.whatsapp" to "com.whatsapp.Main", AppFilter.component("ComponentInfo{com.whatsapp/com.whatsapp.Main}"))
        assertEquals("com.whatsapp" to "com.whatsapp.Main", AppFilter.component("ComponentInfo{com.whatsapp/.Main}"))
        assertEquals("a.b" to "a.b.C", AppFilter.component(" ComponentInfo{ a.b / a.b.C } "))
        assertNull(AppFilter.component("ComponentInfo{}"))
        assertNull(AppFilter.component(":LAUNCHER_ACTION_APP_DRAWER"))
        assertNull(AppFilter.component("ComponentInfo{com.x}"))
    }

    @Test
    fun pack_lookup_prefers_exact_activity_then_any_activity_of_the_package() {
        val filter = AppFilter(mapOf(("com.a" to "com.a.Main") to "a_main", ("com.b" to "com.b.Other") to "b_other"))
        assertEquals("a_main", filter.drawableFor(AppKey("com.a", "com.a.Main", 0L)))
        assertEquals("b_other", filter.drawableFor(AppKey("com.b", "com.b.Launcher", 0L)))
        assertNull(filter.drawableFor(AppKey("com.c", "com.c.Main", 0L)))
    }

    @Test
    fun wallpaper_picker_prefers_the_brand_app_and_never_lanceur() {
        assertEquals("com.google.android.apps.wallpaper", WallpaperPicker.pick(listOf("com.android.wallpaper.livepicker", "com.google.android.apps.wallpaper", "app.lanceur")))
        assertEquals("com.samsung.android.app.dressroom", WallpaperPicker.pick(listOf("com.sec.android.gallery3d", "com.samsung.android.app.dressroom")))
        assertEquals("com.example.walls", WallpaperPicker.pick(listOf("app.lanceur", "com.example.walls")))
        assertNull(WallpaperPicker.pick(listOf("app.lanceur")))
    }
}
