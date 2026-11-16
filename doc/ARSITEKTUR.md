# Arsitektur

Aplikasi Android native (Kotlin, XML Views, Material 3), package `com.example.myapplication`, minSdk 24 (diuji di Android 9 / API 28). Tanpa library eksternal baru.

## Alur data

```text
DummySignalSource (HandlerThread, paket tiap 100 ms; meniru notify BLE)
        │  onEcg / onPpg  (main thread)
        ▼
MonitorEngine (singleton di App)
   ├─ Series live (ECG/IR/Red)  ──► WaveformView di LiveFragment
   ├─ DSP: RPeakDetector, PpgPeakDetector, RateTracker ─► BPM, RR, RMSSD
   ├─ deteksi gap (loncatan sample_index) ─► garis diputus + penghitung paket hilang
   └─ SessionRecorder (jika merekam) ─► files/recordings/*.csv + <id>.metadata.json
                                              │
HistoryFragment ◄── SessionRepository ◄───────┘
DetailActivity  ◄── CsvLoader (validasi → Series) ─► WaveformView (zoom/pan, sinkron)
```

**Mengganti dummy dengan hardware:** buat pemindai BLE (`DeviceScanner`) dan `BleSignalSource : SignalSource` yang memanggil `SignalListener.onEcg/onPpg` dari callback notify BLE (timestamp dan indeks sampel dari ESP32). `MonitorEngine.createSource()` adalah satu-satunya tempat yang perlu diubah.

## Navigasi

`MainActivity` = `DrawerLayout` + bottom navigation. Bar bawah: **Live**, **Perangkat** (`DevicesFragment`), **Riwayat**. Drawer kiri: **Pengaturan** (`SettingsFragment`: akun Google simulasi, lokasi penyimpanan, data dummy, simulasi kondisi, tampilan). Pesan sekali-tampil dari `MonitorEngine` (`recordError`, `notice`) ditampilkan oleh `MainActivity`.

## Penyimpanan rekaman

Rekaman selalu ditulis ke `filesDir/recordings` (sumber Riwayat). Jika pengguna memilih folder (SAF), setelah sesi selesai `MonitorEngine.exportAsync()` menyalin CSV + metadata via `StorageExporter` di thread terpisah dan menandai `sync_status = exported` pada metadata.

## Pencarian dan koneksi perangkat

```text
DevicesFragment ── startScan() ──► MonitorEngine ──► DeviceScanner (DummyDeviceScanner)
                                         ▲                    │ onDevice / onFinished
                                         └────────────────────┘
DevicesFragment ── connect(ScannedDevice) ──► state CONNECTING → (kompatibel?) → CONNECTED + SignalSource
```
`DeviceScanner` dan `SignalSource` adalah dua titik pengganti untuk hardware nyata: pemindai BLE dan sumber data notify BLE.

## Format CSV (versi 1)

Satu sesi = satu file `ECGPPG_<yyyy-MM-dd_HHmmss>_<id>.csv`, format long/sparse sesuai README utama:

`session_id,device_id,stream,sample_index,timestamp_us,ecg_adc,ppg_ir,ppg_red,lead_off,valid`

- `timestamp_us` relatif terhadap awal sesi; dihitung dari timestamp paket + indeks × periode (4000 µs ECG, 10000 µs PPG).
- `valid=0` untuk ECG saat lead-off dan PPG saat IR < 20000 (jari tidak menempel). Saat replay baris tidak valid menjadi garis putus.
- Metadata pendamping `<id>.metadata.json`: versi format, waktu mulai (UTC + epoch), zona waktu, durasi, jumlah sampel, sampel hilang, parsial, `sync_status`, sumber.
- Penyimpanan: `filesDir/recordings/` (privat aplikasi, tanpa izin storage).

## DSP (tanpa AI)

- **ECG** (250 Hz): buang baseline → low-pass → turunan → kuadrat → integrasi 150 ms → ambang adaptif (belajar 2 detik pertama) dengan refractory 250 ms; bila > 1 detik tanpa detak, SPKI diturunkan ×0,997/sampel. BPM dari rata-rata 8 RR terakhir; RR di luar 0,3–2,0 detik diabaikan.
- **PPG** (100 Hz): low-pass → buang DC → puncak lokal di atas 0,3 × rata-rata amplitudo 6 puncak terakhir; refractory adaptif 0,5 × median 5 interval (0,33–0,45 s); referensi amplitudo turun bila > 2 detik tanpa puncak.
- **RMSSD**: dari ≥ 6 interval RR valid. **SpO₂: belum ada** (ditampilkan "--").
- Saat gap data, detektor menginisialisasi ulang filter dan rantai RR diputus.

## Kelas penting

| Kelas | Peran |
|---|---|
| `MonitorEngine` | State koneksi, buffer live, DSP, rekaman |
| `SessionRecorder` / `SessionRepository` / `CsvLoader` | Tulis, daftar, dan baca CSV |
| `WaveformView` | Grafik: live mengikuti waktu; replay dengan cubit/geser/dobel-ketuk |
| `DummySignalSource` | Pemutar dataset; opsi `simulateDrops`, `simulateLeadOff` |
