package com.example.myapplication

import com.example.myapplication.data.Config
import com.example.myapplication.data.DummyData
import com.example.myapplication.dsp.PpgPeakDetector
import com.example.myapplication.dsp.RPeakDetector
import com.example.myapplication.dsp.RateTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DspTest {
    private val dir = File("src/main/assets/dummy")

    /** Rata-rata BPM (setelah 10 detik pertama) sepanjang rekaman. */
    private fun ecgMeanBpm(name: String): Double {
        val data = File(dir, name).inputStream().use { DummyData.parseEcg(it) }
        val det = RPeakDetector(Config.ECG_FS.toDouble()); val rate = RateTracker()
        val vals = ArrayList<Int>()
        data.forEachIndexed { i, v ->
            det.process(v, i.toLong())?.let { rate.addPeak(it * Config.ECG_PERIOD_US / 1e6) }
            if (i >= 10 * Config.ECG_FS && i % Config.ECG_FS == 0) rate.bpm(i * Config.ECG_PERIOD_US / 1e6)?.let { vals.add(it) }
        }
        return vals.average()
    }

    private fun ppgMeanBpm(name: String): Double {
        val (ir, _) = File(dir, name).inputStream().use { DummyData.parsePpg(it) }
        val det = PpgPeakDetector(Config.PPG_FS.toDouble()); val rate = RateTracker()
        val vals = ArrayList<Int>()
        ir.forEachIndexed { i, v ->
            det.process(v, i.toLong())?.let { rate.addPeak(it * Config.PPG_PERIOD_US / 1e6) }
            if (i >= 10 * Config.PPG_FS && i % Config.PPG_FS == 0) rate.bpm(i * Config.PPG_PERIOD_US / 1e6)?.let { vals.add(it) }
        }
        return vals.average()
    }

    // Referensi: anotasi MIT-BIH (rata-rata detak/menit pada 90 detik) dan HR 'Numerics' BIDMC.
    @Test fun ecgRecord100MatchesAnnotationRate() {
        val b = ecgMeanBpm("ecg_mitdb_100.txt"); println("ECG 100 mean BPM = $b (ref 75.5)")
        assertEquals(75.5, b, 3.0)
    }

    @Test fun ecgOtherRecordsRun() {
        for ((n, ref) in listOf("ecg_mitdb_119.txt" to 66.0, "ecg_mitdb_208.txt" to 98.0)) {
            val b = ecgMeanBpm(n); println("$n mean BPM = $b (ref ~$ref)")
            assertEquals(n, ref, b, 6.0)
        }
    }

    @Test fun ppgRecordsMatchReferenceHr() {
        for ((n, ref) in listOf("ppg_bidmc_01.txt" to 92.0, "ppg_bidmc_02.txt" to 91.0, "ppg_bidmc_05.txt" to 98.0)) {
            val b = ppgMeanBpm(n); println("$n mean BPM = $b (ref $ref)")
            assertEquals(n, ref, b, 4.0)
        }
    }

    @Test fun rateTrackerComputesBpmAndRmssd() {
        val r = RateTracker()
        var t = 0.0
        repeat(10) { r.addPeak(t); t += 0.8 }
        assertEquals(75, r.bpm(t - 0.8))
        assertEquals(0, r.rmssdMs())
        assertNotNull(r.lastRrMs()); assertTrue(r.lastRrMs()!! in 790..810)
    }
}
