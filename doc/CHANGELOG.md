# Changelog

Format: tanggal · ringkasan · file terkait. Terbaru di atas.

## 2026-11-16 — iOS: Detail rekaman bisa digeser ke samping

**Diubah**
- `ios/ECGPPG/Views/DetailView.swift`: grafik rekaman dibuka dengan jendela **10 detik** (bukan seluruh rekaman sekaligus yang membuat gelombang berdempetan). Kontrol baru: pilihan lebar jendela *5 dtk / 10 dtk / 30 dtk / Semua*, slider posisi waktu, dan label rentang (mis. "0:00 – 0:10 dari 0:29"). Ketiga grafik tetap sinkron.
- `ios/ECGPPG/Views/WaveformChart.swift`: geser mendatar pada grafik = pindah waktu, geser tegak tetap menggulir halaman (gestur simultan dengan `ScrollView`); fungsi `clampWindow` dipakai bersama.
- `ios/ECGPPG/Views/LiveView.swift`: teks tombol "Selesai Rekam" tidak lagi terpotong menjadi dua baris.
- `release/ECGPPG-unsigned.ipa` dan `doc/screenshot/ios/` diperbarui.

## 2026-11-16 — Versi iOS (SwiftUI)

**Ditambah**
- Folder `ios/`: proyek Xcode `ECGPPG.xcodeproj` (SwiftUI, iOS 17+, iPhone & iPad, tanpa dependensi pihak ketiga). Port fitur Android: Live (grafik ECG/PPG IR/PPG Red, BPM ECG/PPG, RMSSD, lead-off, jari, REC), Perangkat (pemindaian BLE simulasi), Riwayat + Detail (replay CSV dengan cubit/geser/ketuk-dua-kali, sinkron antar-grafik, Bagikan, Hapus), Pengaturan (akun Google simulasi, folder salinan, data dummy, simulasi kondisi, tema Sistem/Terang/Gelap).
- `ios/ECGPPG/Core/`: `Config.swift` (+ `Series`), `Dsp.swift` (port 1:1 `RPeakDetector`, `PpgPeakDetector`, `RateTracker`), `SignalSource.swift`, `DeviceScanner.swift`, `Recording.swift` (CSV/metadata format versi 1 yang sama, `CsvLoader`, `StorageExporter`), `Prefs.swift`, `MonitorEngine.swift`.
- `ios/ECGPPG/Views/`: `LiveView`, `DevicesView`, `HistoryView`, `DetailView`, `SettingsView`, `WaveformChart` (Canvas; decimation min/max), `Theme` (palet sama dengan Android).
- Aset dummy disalin ke `ios/ECGPPG/Resources/dummy/`; ikon aplikasi 1024 px dari `ic_launcher-playstore.png`.
- `release/ECGPPG-unsigned.ipa`: hasil build Release untuk perangkat (arm64) **tanpa tanda tangan**.
- `doc/screenshot/ios/`: tangkapan layar dari simulator iPhone 15 (iOS 26.5).

**Perbedaan dengan Android**
- Pengaturan dibuka sebagai *sheet* dari tombol ☰ (bukan drawer).
- Rekaman di `Documents/recordings` aplikasi, sehingga juga terlihat di app **Files** (*On My iPhone › ECG PPG Monitor*).
- Folder salinan dipilih lewat pemilih folder Files (security-scoped bookmark), pengganti Storage Access Framework.
- Metadata JSON ditulis dengan kunci terurut abjad; isi kolom sama.

## 2026-11-16 — Ikon aplikasi

**Ditambah / diubah**
- Ikon launcher baru menggantikan ikon bawaan template: latar gradien teal (`#00897B` → `#004D40`), jantung putih, garis EKG (kompleks QRS) berwarna teal di tengahnya.
- Adaptive icon (API 26+): `drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` (isi dalam zona aman 66 dp), dan `ic_launcher_monochrome.xml` (kontur jantung + garis EKG untuk ikon bertema Android 13+); `mipmap-anydpi-v26/ic_launcher*.xml` memakai ketiganya.
- Ikon lama (API 24–25): `mipmap-*/ic_launcher.png` dan `ic_launcher_round.png` (48–192 px) menggantikan berkas `.webp` bawaan.
- `app/src/main/ic_launcher-playstore.png` (512 × 512) untuk toko aplikasi/materi publikasi.
- Diuji di emulator Android 9: ikon tampil di daftar aplikasi.

## 2026-11-16 — Pilihan tema di drawer Pengaturan

**Ditambah**
- Drawer Pengaturan → "Tampilan" → **Tema**: tiga pilihan *Sistem / Terang / Gelap* (`MaterialButtonToggleGroup`). Pilihan disimpan (`Prefs.themeMode`) dan langsung berlaku tanpa restart (`AppCompatDelegate.setDefaultNightMode`); diterapkan saat aplikasi dimulai di `App.onCreate`. Sebelumnya mode gelap hanya mengikuti tema sistem Android.
- Diuji di emulator Android 9: pilih Gelap → tampilan langsung gelap; pilihan bertahan setelah aplikasi dimulai ulang; kembali ke Sistem.

**Diubah (dokumen)**
- Proposal (PDF/DOCX): deskripsi laci Pengaturan dan tabel uji F9 menyebut pilihan tema.

## 2026-11-16 — Diagram wiring direvisi (catu daya) dan proposal dibangun ulang

**Ditambah**
- `Portable ESP32 Health Monitor Wiring Diagram (Revisi Catu Daya).png`: gambar ulang diagram wiring dengan jalur daya yang benar: LiPo 1S → TP4056C berproteksi → saklar → boost MT3608 5,0 V → pin **VIN**; AD8232, MAX30102, dan OLED memakai 3,3 V dari pin 3V3. Seluruh isi diagram awal dipertahankan (fitur, contoh OLED, tabel koneksi, warna kabel, penempatan elektroda, catatan penting; tabel koneksi dan catatan diperbarui). Skrip pembuatnya: `validasi/gambar_wiring_revisi.py`. File asli `Portable ESP32 Health Monitor Wiring Diagram.png` tidak diubah.

**Diubah**
- `Proposal EKG - Revisi.pdf` dan `.docx` dibangun ulang memakai diagram revisi (Gambar 3.2) dan teks Subbab 3.3.1/3.3.3 disesuaikan; 51 halaman.

**Penyesuaian ukuran halaman**
- Halaman diagram wiring di proposal tidak lagi landscape: semua halaman (PDF dan DOCX) kini portrait A4 dengan margin sama (4/3/3/3 cm). Gambar diputar 90° agar sisi panjangnya memakai tinggi halaman.

**Alasan koreksi**
- Diagram awal menyambungkan keluaran TP4056 (3,0–4,2 V) langsung ke pin 3V3 ESP32 yang hanya mentoleransi ± 3,6 V dan melewati regulator onboard; bertentangan dengan aturan daya di README.

## 2026-11-16 — Proposal tugas akhir revisi, perbaikan detektor, dan screenshot

**Ditambah (dokumen)**
- `Proposal EKG - Revisi.pdf` dan `.docx` (51 halaman, A4): Bab I–VI, abstrak ID/EN, daftar isi/gambar/tabel, 19 pustaka, lampiran. Memuat diagram wiring (`Portable ESP32 Health Monitor Wiring Diagram.png`) beserta koreksi catu daya, perhitungan perancangan (ADC, penguatan, sampling, filter, throughput BLE, ukuran CSV, daya/baterai, TP4056, MT3608), galeri bentuk sinyal ECG (6 irama) dan PPG (6 bentuk) dari data nyata, validasi algoritma, dan screenshot aplikasi sebagai purwarupa. Proposal asli `Proposal EKG.docx` tidak diubah.
- `doc/screenshot/` (8 tangkapan layar), `doc/validasi/` (skrip dan hasil validasi).

**Diubah (kode) — `dsp/Dsp.kt`**
- `RPeakDetector`: bila > 1 detik tanpa detak, level sinyal (SPKI) diturunkan ×0,997 per sampel. Sensitivitas pada 44 rekaman MIT-BIH naik dari 95,76% ke 98,66% (+P 99,71% → 99,50%).
- `PpgPeakDetector`: ambang = 0,3 × rata-rata amplitudo 6 puncak terakhir; refractory adaptif (0,5 × median 5 interval, dibatasi 0,33–0,45 s); referensi amplitudo turun bila > 2 detik tanpa puncak. MAE BPM PPG pada rekaman BIDMC turun dari 7,21 ke 3,28 BPM; rekaman dengan MAE > 10 BPM dari 15 menjadi 2.
- `DspTest.kt`: uji memakai rata-rata BPM terhadap referensi dataset (anotasi MIT-BIH dan HR BIDMC) alih-alih nilai sesaat; 5 tes lolos.

**Catatan**
- Perbaikan algoritma dilakukan setelah validasi awal menunjukkan PPG hanya 66,0% dalam ±5 BPM; sebab: BPM terdeteksi terlalu rendah (rasio 0,7–0,9) karena denyut kecil terlewat.
- Pada proposal, panel "irregular pulse" PPG sintetis (template denyut nyata, interval acak); semua panel lain dari rekaman nyata.

## 2026-11-16 — Pengaturan pindah ke drawer samping; Perangkat jadi tab bawah

**Struktur navigasi sekarang**
- **Bar bawah (3 tab):** Live, **Perangkat** (pencarian BLE), Riwayat.
- **Drawer samping = Pengaturan** (geser dari tepi kiri atau ikon ☰ di header tiap tab): Akun Google, Lokasi Penyimpanan Rekaman, Data Dummy, Simulasi Kondisi, Tampilan, Tentang. Tombol Back menutup drawer lebih dulu.
- Tombol "Cari Perangkat" di Live membuka tab Perangkat.

**Ditambah**
- **Lokasi penyimpanan rekaman:** pilih folder lewat pemilih folder sistem (Storage Access Framework, `OpenDocumentTree`, izin dipertahankan). Rekaman tetap disimpan di internal aplikasi (untuk Riwayat); setelah sesi selesai CSV + metadata disalin ke folder pilihan di thread terpisah. Status sesi menjadi `exported` (badge DISALIN di Riwayat); bila gagal kembali `local_only` dan muncul pesan. `data/StorageExporter.kt`, `SessionRepository.setSyncStatus()`, `Prefs.exportTreeUri`.
- **Akun Google (simulasi):** dialog pilih akun contoh (`example.com`), tampil avatar/nama/email, tombol Keluar. Login Google dan Drive sungguhan belum ada. `DummyAccounts`, `Prefs.googleName/googleEmail`.
- `ui/DevicesFragment.kt` dan `fragment_devices.xml` dipulihkan sebagai tab; item perangkat dibuat ringkas.
- `MainActivity`: `DrawerLayout` (drawer = `SettingsFragment`), `openDrawer()`, `openDevices()`, dan penampil pesan global (`engine.recordError`, `engine.notice`) sehingga toast muncul di tab mana pun.
- Ikon `ic_menu`, `ic_folder`.

**Diubah**
- `SettingsFragment` digabung dengan isi drawer (akun + penyimpanan + pengaturan lama). Menu bawah `nav_settings` dihapus.
- `StorageExporter.folderLabel()` mengenali provider Downloads dan penyimpanan perangkat.

**Diperbaiki**
- Dialog akun: `AlertDialog.setMessage()` menyembunyikan daftar akun; catatan dipindah ke judul kustom.

**Dihapus**
- `DrawerFragment` / `fragment_drawer.xml` (sempat dibuat berisi pencarian BLE di drawer, lalu diganti sesuai permintaan: pencarian BLE di bar bawah, pengaturan di drawer).

## 2026-11-16 — Menu pencarian perangkat (tab Perangkat, data dummy)

**Ditambah**
- Tab baru **Perangkat** di bottom navigation.
  - Pencarian otomatis saat tab dibuka dalam keadaan terputus; tombol Cari Perangkat / Hentikan Pencarian; progress bar.
  - Daftar perangkat muncul bertahap, RSSI berfluktuasi, indikator kekuatan sinyal (4 level) + dBm, diurutkan kompatibel dulu lalu sinyal terkuat.
  - Badge: KOMPATIBEL, TIDAK DIKENAL, MENGHUBUNGKAN…, TERAKHIR DIPAKAI.
  - Ketuk perangkat untuk menyambung; kartu "perangkat terhubung" dengan tombol Putuskan/Batal.
  - Menyambung ke perangkat tidak kompatibel gagal dengan pesan (meniru layanan GATT ECG/PPG tidak ditemukan).
- `data/DeviceScanner.kt`: `ScannedDevice`, antarmuka `DeviceScanner` (+ `Callback`), dan `DummyDeviceScanner` (6 perangkat palsu, berhenti otomatis setelah 8 dtk). Pemindai BLE nyata nanti cukup mengimplementasikan antarmuka ini.
- `ui/DevicesFragment.kt`, `res/layout/fragment_devices.xml`, `item_device.xml`, drawable `ic_bluetooth`, `ic_search`, `bg_badge_muted`.

**Diubah**
- `MonitorEngine`: `devices`, `scanning`, `device`, `startScan()`, `stopScan()`, `connect(device)` (menggantikan `connect()` tanpa argumen; jeda 1,5 dtk meniru koneksi BLE).
- Tab Live: tombol "Sambungkan" menjadi "Cari Perangkat" (membuka tab Perangkat) saat terputus; subjudul menampilkan nama + alamat perangkat.
- `SessionRecorder`: `device_id` di CSV/metadata kini nama perangkat yang dipilih (bukan konstanta); metadata menambah `device_address`.
- `Prefs`: `lastDeviceAddress`. `MainActivity`: `openDevices()`.

**Catatan**
- Pemindaian masih simulasi. Pemindaian BLE nyata akan memerlukan izin runtime (Android 9: lokasi; Android 12+: `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`) dan filter UUID layanan.
- Pilihan rekaman dummy (Pengaturan) berlaku sama untuk semua perangkat kompatibel.

## 2026-11-16 — Perbaikan tampilan (UI)

**Diubah**
- Tema: palet baru terang/gelap (`values/colors.xml`, `values-night/colors.xml`, `themes.xml` + style `Card`, `ChartCard`, `Label`, `SectionTitle`). Warna aksen tetap per sinyal: ECG hijau, PPG biru, SpO₂ kuning, HRV ungu.
- Layar Live: judul + status koneksi berbentuk pill, kartu metrik berwarna (BPM ECG, BPM PPG, SpO₂, HRV), grafik dalam kartu bersudut membulat dengan label sensor/laju sampling, tombol Material dengan ikon, tombol rekam berubah merah saat merekam, indikator REC berkedip, kartu metrik redup saat terputus.
- Riwayat: item kartu dengan ikon, badge status (LOKAL), peringatan kuning bila ada sampel hilang/parsial, jumlah sesi, tampilan kosong yang informatif.
- Pengaturan: dikelompokkan dalam kartu (Data dummy, Simulasi kondisi, Tampilan, Tentang).
- Detail rekaman: kartu ringkasan (Durasi, BPM ECG, Gap) dan grafik dalam kartu.
- `WaveformView`: latar digambar oleh kartu (sudut membulat benar), subtitle kanan atas, label sumbu tidak terpotong.
- `ic_pulse` dll. tetap; ditambah `ic_rec`, `ic_stop`, `ic_link`, `ic_chevron`, `bg_pill`, `bg_badge`, `bg_icon_circle`; `bg_card` dihapus.

**Diperbaiki**
- `SettingsFragment`: Spinner di API 28 mengirim callback awal yang menimpa preferensi (durasi grafik berubah sendiri jadi 5 dtk). Sekarang hanya pilihan dari sentuhan pengguna yang disimpan.
- Tombol Mulai Rekam tidak lagi hitam saat tint di-reset.

## 2026-11-16 — Versi awal aplikasi ECG/PPG dengan data dummy

**Diganti**
- Template To-Do (`MainActivity` lama dan `activity_main.xml` lama) diganti aplikasi monitor ECG/PPG.
- `strings.xml`: nama aplikasi menjadi "ECG PPG Monitor", teks UI berbahasa Indonesia.

**Ditambah — data dummy**
- `tools/make_dummy_assets.py`: mengubah dataset MIT-BIH (ECG) dan BIDMC (PPG) menjadi aset ringan.
- `app/src/main/assets/dummy/`: `ecg_mitdb_{100,119,208}.txt` (250 Hz) dan `ppg_bidmc_{01,02,05}.txt` (100 Hz, kolom `ir,red`), masing-masing 90 detik.

**Ditambah — kode (`app/src/main/java/com/example/myapplication/`)**
- `data/Config.kt`: konstanta (250/100 Hz, header CSV, ID perangkat).
- `data/Series.kt`: deret waktu (buffer geser untuk live, tumbuh untuk replay); NaN = garis putus.
- `data/SignalSource.kt`: antarmuka `SignalSource`/`SignalListener`, `DummyData`, dan `DummySignalSource` (memutar dataset real-time per 100 ms; opsi simulasi paket hilang dan lead-off).
- `dsp/Dsp.kt`: `RPeakDetector` (Pan-Tompkins sederhana), `PpgPeakDetector`, `RateTracker` (BPM, RR, RMSSD).
- `data/Recording.kt`: `SessionRecorder` (CSV long/sparse bertahap + metadata JSON), `SessionRepository`, `CsvLoader` (replay, deteksi gap, validasi header), `SessionInfo`.
- `data/Prefs.kt`: pengaturan (rekaman dummy, durasi grafik, simulasi).
- `MonitorEngine.kt` dan `App.kt`: pusat data live pada level aplikasi (rekaman tetap jalan saat pindah tab).
- `ui/WaveformView.kt`: grafik Canvas buatan sendiri (live, zoom/pan, decimation min/max).
- `ui/LiveFragment.kt`, `ui/HistoryFragment.kt`, `ui/SettingsFragment.kt`, `ui/DetailActivity.kt`, `MainActivity.kt` (bottom navigation).

**Ditambah — resource dan konfigurasi**
- Layout: `activity_main`, `activity_detail`, `fragment_live`, `fragment_history`, `fragment_settings`, `item_session`.
- Menu `bottom_nav`, `detail`; drawable `ic_pulse`, `ic_list`, `ic_tune`, `bg_card`; `xml/file_paths.xml`.
- `AndroidManifest.xml`: `android:name=".App"`, `DetailActivity`, `FileProvider` (berbagi CSV).
- Tidak ada dependency baru di Gradle.

**Ditambah — tes**
- `app/src/test/.../DspTest.kt`: uji DSP pada dataset dummy.

**Ditambah — dokumentasi**
- Folder `doc/` ini.

**Belum diimplementasikan**
- BLE nyata (sumber data hardware), Google Drive (login, upload, restore), SpO₂, indeks SQLite (sementara memakai berkas metadata JSON), pemfilteran tampilan raw vs filtered.

**Catatan diketahui**
- Setelah Putuskan, grafik live menampilkan bingkai terakhir (tidak dibersihkan).
- Kanal Red PPG adalah turunan sintetis dari PPG BIDMC (dataset hanya punya satu kanal PPG).
