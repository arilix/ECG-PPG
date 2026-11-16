package com.example.myapplication.data

/** Konstanta desain sesuai README (ECG 250 Hz, PPG 100 Hz). */
object Config {
    const val ECG_FS = 250
    const val PPG_FS = 100
    const val ECG_PERIOD_US = 1_000_000L / ECG_FS   // 4000 us
    const val PPG_PERIOD_US = 1_000_000L / PPG_FS   // 10000 us
    const val DEVICE_ID = "ESP32_HEALTH_01"
    /** Di bawah nilai ini IR dianggap "jari tidak menempel" (MAX30102 mentah). */
    const val FINGER_MIN_IR = 20_000
    const val CSV_FORMAT_VERSION = 1
    const val CSV_HEADER =
        "session_id,device_id,stream,sample_index,timestamp_us,ecg_adc,ppg_ir,ppg_red,lead_off,valid"
}
