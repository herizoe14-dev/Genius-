package com.genius.downloader

import com.genius.downloader.download.DownloadManager
import com.genius.downloader.download.SpeedMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedMeterTest {
    @Test
    fun smoothsJumpySpeed() {
        val m = SpeedMeter()
        // 1 % par seconde, avec des à-coups (rien pendant 2 s puis rattrapage)
        val points = listOf(0 to 0.10f, 1 to 0.11f, 2 to 0.11f, 3 to 0.11f, 4 to 0.14f, 5 to 0.15f, 6 to 0.16f)
        points.forEach { (s, p) -> m.add(s * 1000L, p) }
        assertEquals(0.01f, m.rate(), 0.001f)
        assertEquals(84, m.etaSec(0.16f))
        assertEquals(102_508_789L, SpeedMeter.parseSize("97.76MiB"))
    }

    @Test
    fun progressLine() {
        val m = SpeedMeter()
        val line = "[download]  17.0% of ~  97.76MiB at   53.46KiB/s ETA 06:51 (frag 30/176)"
        DownloadManager.prettyLine(line, 0.17f, m, 0)
        val out = DownloadManager.prettyLine(line.replace("17.0", "18.0"), 0.18f, m, 2000)
        assertTrue(out, out.startsWith("≈ 97.8 Mo · ") || out.startsWith("≈ 97,8 Mo · "))
        assertTrue(out, "Mo/s" in out || "Ko/s" in out)
        assertTrue(out, "reste" in out)
    }
}
