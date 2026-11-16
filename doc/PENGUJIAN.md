# Pengujian

## Validasi algoritma pada dataset (lihat `validasi/`)
- ECG MIT-BIH (44 rekaman, 100.653 detak, toleransi ±150 ms): Se 98,66%, +P 99,50%.
- PPG BIDMC (52 rekaman): MAE BPM 3,28 (median 2,06); 81,6% titik dalam ±5 BPM.

## Uji unit (JVM) — `app/src/test/.../DspTest.kt`
Jalankan: `./gradlew --offline :app:testDebugUnitTest` (5 tes, lolos pada 2026-11-16).

| Data | Hasil DSP | Referensi |
|---|---|---|
| ECG MIT-BIH 100 | 73 BPM | ≈ 74 BPM (hitung kasar dari puncak R) |
| ECG MIT-BIH 119 / 208 | 64 / 101 BPM | hanya diuji berjalan (ada PVC, tidak ada referensi otomatis) |
| PPG BIDMC 01 / 02 / 05 | 91 / 91 / 98 BPM | HR dataset `Numerics`: 92 / 91 / 98 BPM |

## Uji manual di emulator Android 9 (API 28, 1080×1920)
- Sambungkan → grafik ECG/IR/Red berjalan, BPM ECG ≈ 73–75, BPM PPG ≈ 93–94.
- Rekam 12 dtk → ECG 3014 sampel (≈ 250 Hz), PPG 1206 (≈ 100 Hz); CSV dan `metadata.json` tertulis di `files/recordings/`.
- Riwayat → sesi muncul; Detail → grafik direkonstruksi dari CSV, BPM rata-rata ECG ≈ 70–72.
- Simulasi paket hilang → `ecg_lost=50`, `ppg_lost=20` (200 ms); Detail menampilkan Gap 1 dan garis terputus.
- Tab Perangkat: pencarian otomatis, perangkat muncul bertahap (6 total), sinyal dan dBm berubah; sambung ke `ESP32_HEALTH_01` berhasil (kartu terhubung, Live menampilkan nama + alamat); Putuskan mengembalikan daftar dan badge "terakhir dipakai"; sambung ke Mi Smart Band 6 menampilkan "MENGHUBUNGKAN…" lalu gagal dengan pesan layanan ECG/PPG tidak dikenali.
- Navigasi baru: bar bawah 3 tab (Live, Perangkat, Riwayat); drawer Pengaturan terbuka dengan geser dari kiri atau ikon ☰; Back menutup drawer.
- Akun Google (simulasi): dialog menampilkan 2 akun contoh.
- Lokasi penyimpanan: pemilih folder sistem terbuka, folder Downloads dipilih dan tersimpan; setelah rekam 8 dtk, toast "Salinan sesi … tersimpan di Downloads · Unduhan" dan `ECGPPG_….csv` + `<id>.metadata.json` ada di `/sdcard/Download`.
- Catatan alat uji: `adb input swipe` kadang melaporkan crash `INJECT_EVENTS` (proses tool, bukan aplikasi); dump `uiautomator` pernah ikut memicu drawer terbuka saat pengujian, sedangkan tap biasa tidak.
- Pilihan tema (Sistem/Terang/Gelap) di Pengaturan → Tampilan: Gelap langsung berlaku dan bertahan setelah restart.
- Mode terang dan gelap diperiksa (Live, Riwayat, Pengaturan, Detail).

## Uji manual versi iOS — simulator iPhone 15 (iOS 26.5), Xcode 27.1
- Build Debug (simulator) dan Release `iphoneos` arm64 tanpa tanda tangan berhasil tanpa error; `codesign -dv` pada `.app` Release: *not signed at all* (siap ditandatangani ulang oleh AltStore).
- Sambungkan `ESP32_HEALTH_01` → grafik ECG/IR/Red berjalan; BPM ECG 72–74, BPM PPG 94, HRV (RMSSD) tampil; SpO₂ "--".
- Rekam 29 dtk dengan simulasi paket hilang → sesi muncul di Riwayat; Detail: Durasi 0:29, BPM rata-rata 71, Gap 2 (ECG 1, PPG 1).
- Tab Perangkat: pencarian otomatis, 6 perangkat, urutan kompatibel dulu lalu sinyal; badge "KOMPATIBEL · TERAKHIR".
- Pengaturan (sheet) dan tema Gelap diperiksa. Tangkapan layar: `screenshot/ios/`.
- Belum diuji di iPhone fisik, belum diuji pemilih folder salinan dan Bagikan.

## Belum diuji
- Simulasi elektroda terlepas (lead-off) secara visual, zoom/pan dengan gestur nyata, Bagikan (FileProvider), Hapus, rekaman panjang (> 15 menit), perangkat fisik, rotasi layar.
- BLE dan Google Drive belum ada.
