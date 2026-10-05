package app.lanceur.builtin.battery

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryInfoTest {
    private fun info(level: Int, status: ChargeStatus = ChargeStatus.DISCHARGING, remaining: Long = -1) =
        BatteryInfo(level, status, remaining, temperatureTenths = 312, powerSave = false)

    @Test
    fun tone_thresholds() {
        assertEquals(BatteryTone.GOOD, info(51).tone)
        assertEquals(BatteryTone.MEDIUM, info(50).tone)
        assertEquals(BatteryTone.MEDIUM, info(20).tone)
        assertEquals(BatteryTone.LOW, info(19).tone)
    }

    @Test
    fun durations_round_up_to_the_minute() {
        assertEquals("42 min", BatteryInfo.duration(41 * 60_000L + 1))
        assertEquals("1 h 05", BatteryInfo.duration(65 * 60_000L))
        assertEquals("1 min", BatteryInfo.duration(20_000))
    }

    @Test
    fun status_texts() {
        assertEquals("En charge · pleine dans 42 min", info(70, ChargeStatus.CHARGING, 42 * 60_000L).statusText)
        assertEquals("En charge", info(70, ChargeStatus.CHARGING).statusText)
        assertEquals("⚡ 42 min", info(70, ChargeStatus.CHARGING, 42 * 60_000L).shortChargeText)
        assertEquals("Branchée · charge en pause", info(80, ChargeStatus.PLUGGED).statusText)
        assertEquals("Sur batterie", info(70).statusText)
        assertNull(info(70).shortChargeText)
        assertEquals("Chargée", info(100, ChargeStatus.FULL).statusText)
        assertEquals("31 °C", info(70).temperatureText)
        assertEquals("Économiseur désactivé", info(70).powerSaveText)
    }

    @Test
    fun from_battery_extras() {
        val charging = BatteryInfo.from(39, 50, BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_USB, 600_000, 300, false)
        assertEquals(78, charging.level)
        assertEquals(ChargeStatus.CHARGING, charging.status)
        assertEquals(ChargeStatus.FULL, BatteryInfo.from(100, 100, BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, -1, null, false).status)
        assertEquals(ChargeStatus.PLUGGED, BatteryInfo.from(80, 100, BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, -1, null, false).status)
        assertEquals(ChargeStatus.DISCHARGING, BatteryInfo.from(80, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 0, -1, null, true).status)
        assertNull(BatteryInfo.from(80, 100, 0, 0, -1, null, false).temperatureText)
    }
}
