# ECG & PPG Portable Health Monitor

**Dokumentasi rancangan hardware, firmware ESP32, dan aplikasi Flutter — versi tanpa AI/ML/DL.**

> **Status:** Spesifikasi proyek dan rencana implementasi, bukan klaim bahwa semua fungsi telah selesai dibuat atau tervalidasi.
>
> **Keselamatan:** Prototipe pembelajaran/penelitian, **bukan perangkat medis**. Saat elektroda ECG dipasang pada tubuh, operasikan alat **hanya dari baterai**, lepas kabel USB, charger, komputer, dan alat ukur ber-ground yang tidak memiliki isolasi medis. Jangan mengisi baterai ketika elektroda terpasang. Nilai BPM/SpO₂ belum dapat dianggap akurat sebelum algoritma dan kalibrasi diuji.

## 1. Ringkasan proyek

Sistem menggunakan **ESP32 DevKit V1** untuk membaca satu kanal ECG dari **AD8232**, dua kanal PPG (IR dan Red) dari **MAX30102**, dan menampilkan informasi pada **OLED I2C 1,3 inci 128×64**. Tiga tombol fisik dipakai untuk mengganti halaman. ESP32 mengirim data melalui **Bluetooth Low Energy (BLE)** ke aplikasi **Flutter** untuk grafik real-time, perhitungan parameter berbasis *digital signal processing* (DSP), dan **perekaman setiap sesi ke CSV, rekonstruksi grafik dari CSV, serta pencadangan/unduh riwayat melalui Google Drive**.

**Tidak menggunakan:** machine learning, deep learning, AI, klasifikasi aritmia otomatis, atau diagnosis medis.

## 2. Daftar komponen

| Komponen | Jumlah | Keterangan |
|---|---:|---|
| ESP32 DevKit V1 | 1 | Mikrokontroler dan BLE |
| AD8232 + kabel RA/LA/RL | 1 | Akuisisi ECG analog satu kanal |
| MAX30102 | 1 | Akuisisi PPG IR dan Red via I2C |
| OLED SH1106 1,3 inci | 1 | 128×64 I2C; sesuaikan driver jika SSD1306 |
| Tombol momentary | 3 | NEXT, PREV, SELECT |
| LiPo 1S 3,7 V | 1 | Rekomendasi 2.000 mAh; opsi 1.200 mAh |
| TP4056C USB-C dengan proteksi | 1 | Untuk satu sel LiPo; bukan untuk 2S |
| Boost converter, mis. MT3608 | 1 | Disetel 5,0 V untuk VIN/5V ESP32 |
| Saklar ON/OFF | 1 | Memutus suplai ke boost converter |
| Kabel, konektor, casing | Sesuai kebutuhan | Pastikan konektor dan polaritas benar |

## 3. Wiring final

### 3.1 AD8232 → ESP32

| AD8232 | ESP32 | Keterangan |
|---|---|---|
| 3.3V | 3V3 | Suplai |
| GND | GND | Ground |
| OUTPUT | GPIO 34 | Input analog (ADC1) |
| LO+ | GPIO 32 | Lead-off digital |
| LO− | GPIO 33 | Lead-off digital |
| SDN | Tidak disambung* | *Cek breakout: harus sudah aktif secara default* |

Elektroda **RA, LA, RL** terhubung ke modul, bukan ke GPIO. Ketiganya membentuk **satu kanal ECG**, bukan tiga kanal.

### 3.2 MAX30102 → ESP32

| MAX30102 | ESP32 |
|---|---|
| VIN | 3V3, **jika modul mendukung input 3,3 V** |
| GND | GND |
| SDA | GPIO 21 |
| SCL | GPIO 22 |
| INT | Tidak dipakai pada versi awal |

### 3.3 OLED → ESP32

| OLED I2C | ESP32 |
|---|---|
| VCC | 3V3, jika sesuai spesifikasi modul |
| GND | GND |
| SDA | GPIO 21 |
| SCL | GPIO 22 |

MAX30102 dan OLED berbagi bus I2C. Pada pengujian sebelumnya: **MAX30102 `0x57`** dan **OLED `0x3C`**. Mulai dari I2C **100 kHz (`I2C_SPEED_STANDARD`)** karena konfigurasi tersebut berhasil pada MAX30102. Periksa level pull-up I2C agar tidak melebihi 3,3 V pada GPIO ESP32.

### 3.4 Tiga tombol → ESP32

| Tombol | GPIO | Sambungan lain | Mode |
|---|---|---|---|
| NEXT | 25 | GND | `INPUT_PULLUP` |
| PREV | 26 | GND | `INPUT_PULLUP` |
| SELECT | 27 | GND | `INPUT_PULLUP` |

Tombol aktif-LOW dan harus diberi *debounce* perangkat lunak.

### 3.5 Baterai dan charger 1S

```text
       USB-C 5V untuk pengisian (alat MATI)
                    │
               TP4056C USB-C
         (varian dengan proteksi 1S)
               B+       B-
                │        │
          LiPo 1S (+)  LiPo 1S (-)

         TP4056C OUT+    OUT-
                │         │
            Saklar        │
                │         │
          BOOST IN+    BOOST IN-
             MT3608 / boost 5,0 V
           BOOST OUT+  BOOST OUT-
                │         │
          ESP32 VIN/5V   ESP32 GND
                │
          Regulator board 3V3
                ├─ AD8232 3.3V
                ├─ MAX30102 VIN*
                └─ OLED VCC*

* Hanya jika spesifikasi masing-masing modul mengizinkan 3,3 V.
```

**Aturan daya:**

1. Atur dan **ukur** boost pada 5,0 V menggunakan multimeter sebelum masuk ESP32.
2. Jangan memasukkan 5 V ke pin `3V3`, dan jangan menyambungkan LiPo 1S langsung ke pin `3V3`.
3. TP4056C **biasa tidak memiliki power-path/load sharing**: matikan alat selama pengisian. Jika ingin perangkat tetap aktif saat diisi, ganti dengan desain charger *power-path* yang sesuai.
4. Jangan menyuplai pin `VIN/5V` dari boost bersamaan dengan 5 V USB pemrograman tanpa rangkaian pemilih/isolasi sumber daya yang benar.
5. Pastikan TP4056C benar-benar versi **dengan proteksi**, baterai sehat, serta arus pengisian sesuai spesifikasi sel. Modul TP4056 tertentu diatur ke 1 A dari pabrik.
6. Baterai 2S **tidak boleh** diisi dengan TP4056C 1S.

## 4. Data yang dibaca

| Sumber | Data mentah | Parameter olahan tanpa AI |
|---|---|---|
| AD8232 | ADC ECG 0–4095, status lead-off | Grafik ECG, R-peak, BPM ECG, RR interval, HRV sederhana bila kualitas memenuhi |
| MAX30102 | Nilai IR, nilai Red | Grafik PPG, pulse rate/BPM PPG, pulse interval, estimasi SpO₂ **jika algoritma dan kalibrasi sesuai** |
| ESP32 | Uptime, status BLE | Koneksi, timestamp, status perangkat |
| Baterai | Belum tersedia langsung | Tegangan/persentase memerlukan rangkaian ADC pembagi tegangan atau IC fuel gauge tambahan |

**Keterangan:** AD8232 membaca ECG satu kanal, bukan ECG 12-lead. MAX30102 membaca dua kanal **PPG optik**, bukan ECG. Modul TP4056 tidak dengan sendirinya menyediakan nilai persentase baterai.

## 5. Layar OLED dan navigasi

| Halaman | Isi |
|---|---|
| 1 — Dashboard | Status perangkat/BLE, ECG raw, IR/Red, status lead-off |
| 2 — ECG Monitor | Grafik ECG berjalan, nilai ADC, status elektroda; BPM ECG jika perhitungan tersedia |
| 3 — PPG Monitor | Grafik PPG IR berjalan, Red, pulse rate dan SpO₂ jika sudah diimplementasikan serta diuji |

- **NEXT**: ke halaman berikutnya.
- **PREV**: ke halaman sebelumnya.
- **SELECT**: kembali ke Dashboard (dapat dikembangkan menjadi menu).

OLED tidak perlu diperbarui pada laju sampling sensor. Target refresh tampilan awal **10–20 fps**, kemudian ukur apakah mengganggu pembacaan sampel.

## 6. Aplikasi Flutter — daftar fitur

### 6.1 Koneksi perangkat

- Memindai perangkat BLE terdekat.
- Menghubungkan dan memutuskan koneksi ESP32.
- Menampilkan status *connecting/connected/disconnected* dan kegagalan koneksi.
- Menerima status pengukuran dan peringatan kualitas sinyal.

### 6.2 Monitoring real-time

- Grafik **ECG** dari AD8232 dengan sumbu waktu.
- Grafik **PPG IR** dan **PPG Red** dari MAX30102.
- BPM ECG dan BPM PPG, jika deteksi puncak sudah diuji.
- RR interval dan HRV sederhana dari ECG, bila data layak dianalisis.
- SpO₂ estimasi, hanya jika perhitungan dan kalibrasi telah diimplementasikan.
- Status elektroda ECG, kontak jari, dan kualitas sinyal.

### 6.3 Perekaman per sesi, riwayat, dan pemutaran ulang grafik CSV

- Tombol **Mulai Rekam** membuat sesi baru (`session_id` unik) dan membuka file CSV di penyimpanan **lokal aplikasi Flutter**.
- Selama perekaman, setiap sampel yang diterima dari BLE ditulis secara berurutan beserta **timestamp yang dibuat ESP32**, bukan sekadar waktu saat paket diterima ponsel.
- Tombol **Selesai Rekam** menutup file dengan aman, menghitung durasi/jumlah sampel, dan menampilkan sesi dalam **Riwayat**.
- Rekaman tetap tersimpan lokal apabila BLE terputus atau ponsel tidak memiliki internet; tampilkan **indikasi gap/drop** bila ada nomor sampel hilang, jangan mengisi data hilang sebagai pengukuran nyata.
- Pengguna bisa membuka sesi lama: aplikasi membaca CSV, memisahkan deret ECG/PPG, lalu **merekonstruksi line chart** berdasarkan `timestamp_us`, termasuk **zoom, pan, pilih kanal, dan durasi tampilan**.
- Pengguna bisa mengunduh, membagikan, mengekspor, dan menghapus rekaman dari aplikasi dengan konfirmasi.
- Sediakan daftar histori dengan tanggal, durasi, status sinkronisasi Drive, jumlah data valid, dan status pemutaran ulang.

### 6.4 Backup dan restore Google Drive

- Pengguna masuk dengan akun Google dan memberi izin eksplisit untuk penyimpanan data pada Google Drive.
- Setelah sesi selesai **dan file lokal valid**, aplikasi mengunggah file `.csv` ke folder Drive yang dikelola aplikasi, misalnya `ECG_PPG_Health_Monitor/`.
- Gunakan **Google Drive API v3** melalui OAuth 2.0 dan cakupan izin minimal `https://www.googleapis.com/auth/drive.file` untuk file yang dibuat/diakses lewat aplikasi.
- Penyimpanan utama bersifat **offline-first**: CSV lokal adalah sumber saat merekam, dan Google Drive adalah cadangan/sinkronisasi, **bukan media streaming tiap sampel**.
- Status sesi: `local_only`, `pending_upload`, `uploading`, `synced`, atau `upload_failed`.
- Jika tidak ada internet, antrekan unggah diproses ulang saat tersedia koneksi dan izin Google masih valid. Tampilkan tombol **Coba Unggah Lagi** untuk kegagalan.
- Simpan `drive_file_id` pada indeks SQLite lokal; gunakan `session_id` sebagai identitas tetap untuk mencegah duplikasi saat retry.
- Untuk file besar gunakan **resumable upload**; pastikan berkas yang diunggah lengkap (ukuran/checksum jika tersedia) sebelum menandainya tersinkron.
- Fitur **Pulihkan dari Google Drive** menampilkan sesi yang pernah dicadangkan, mengunduh CSV ke penyimpanan lokal, memvalidasi format, dan menampilkannya kembali sebagai grafik.
- Jangan meminta akses penuh seluruh Google Drive jika akses file yang dibuat/dipilih aplikasi sudah cukup. Jangan menggunakan `appDataFolder` jika CSV harus terlihat di antarmuka Google Drive, karena folder itu tersembunyi.
- Karena data bersifat sensitif (biosinyal), batasi akses, jangan membuat tautan publik secara default, gunakan HTTPS/OAuth, dan sediakan opsi menghapus data lokal/Drive sesuai kendali pengguna.

### 6.5 Pengaturan

- Pilihan durasi grafik (misalnya 5, 10, 20 detik).
- Skala grafik otomatis/manual.
- Status koneksi dan informasi firmware.
- Opsi tampilan raw vs filtered (jika DSP telah dibuat).
- Akun Google yang tersambung, status ruang penyimpanan, Wi-Fi-only upload (opsional), dan sinkronisasi manual.

**Tidak ada fitur klasifikasi penyakit/aritmia oleh AI** pada versi ini. Semua perhitungan parameter harus dijelaskan sebagai pemrosesan sinyal, bukan diagnosis.


## 7. Komunikasi BLE ESP32 ↔ Flutter

**Peran:** ESP32 sebagai BLE Peripheral/GATT Server, Flutter sebagai Central/Client.

| Karakteristik (rancangan) | Properti | Isi |
|---|---|---|
| ECG Stream | Notify | Batch sampel ECG + sample counter/timestamp |
| PPG Stream | Notify | Batch pasangan IR/Red + sample counter/timestamp |
| Device Status | Read/Notify | Lead-off, kualitas sensor, versi firmware, status baterai jika tersedia |
| Device Control | Write | Start/stop stream; konfigurasi yang didukung |

**Penting:** UUID layanan dan karakteristik harus ditetapkan secara konsisten antara firmware dan Flutter sebelum implementasi. Untuk streaming berfrekuensi tinggi, lebih baik **paket biner berisi banyak sampel** daripada JSON per sampel. Ukuran payload aktual bergantung pada MTU BLE, interval koneksi, dan kemampuan ponsel.

### Contoh JSON untuk debugging Serial Monitor

```json
{"device_id":"ESP32_HEALTH_01","timestamp_ms":123450,"ecg":{"adc":1923,"lead_off":false},"ppg":{"ir":82341,"red":60521},"mode":"live"}
```

Contoh tersebut **hanya ilustrasi format**, bukan keluaran sensor yang dijamin. BLE produksi dapat memakai format biner berbeda tetapi berisi informasi yang sama.

## 8. Sampling dan pemrosesan sinyal

| Sinyal | Target awal | Catatan |
|---|---:|---|
| ECG AD8232 | 250 Hz | Gunakan penjadwalan presisi dan buffer; cek kehilangan sampel |
| PPG MAX30102 | 100 Hz | Gunakan FIFO sensor agar pembacaan tidak terlewat |
| OLED | 10–20 fps | Render terpisah dari akuisisi |
| BLE | Batch berkala | Kirim dari buffer, bukan memblokir pembacaan |

Tahapan DSP tanpa AI:

1. **ECG:** pembacaan ADC → cek lead-off → penyaringan noise/baseline → deteksi R-peak → hitung RR interval dan BPM → hitung HRV hanya dari interval valid.
2. **PPG:** baca FIFO IR/Red → cek kontak dan saturasi sinyal → pemisahan komponen AC/DC → deteksi denyut → hitung pulse rate → estimasi SpO₂ bila algoritma dan kalibrasi memadai.
3. **Sinkronisasi:** beri penanda waktu/sampel dari ESP32 dan catat sample rate aktual.
4. **Kualitas:** jangan tampilkan hasil olahan sebagai valid saat elektroda terlepas, jari tidak terdeteksi, sinyal terlalu bising, atau buffer terputus.

## 9. Desain file CSV per sesi

**Satu sesi pemakaian = satu file CSV**, sehingga histori tidak tercampur antarpemakaian. Nama contoh: `ECGPPG_2026-11-16_090700_ab12cd.csv`. Waktu pada nama file menggunakan zona waktu yang diketahui ponsel; sumber waktu setiap sampel tetap dari ESP32.

Karena ECG (target 250 Hz) dan PPG (target 100 Hz) berbeda laju, gunakan **format long/sparse**: satu baris mewakili **satu sampel pada satu stream**, sehingga tidak perlu mengarang nilai PPG pada setiap sampel ECG. Kolom kanal lain dibiarkan kosong.

```csv
session_id,device_id,stream,sample_index,timestamp_us,ecg_adc,ppg_ir,ppg_red,lead_off,valid
ab12cd,ESP32_HEALTH_01,ecg,0,0,1870,,,0,1
ab12cd,ESP32_HEALTH_01,ecg,1,4000,1882,,,0,1
ab12cd,ESP32_HEALTH_01,ppg,0,10000,,85432,62341,,1
ab12cd,ESP32_HEALTH_01,ecg,2,8000,1901,,,0,1
ab12cd,ESP32_HEALTH_01,ppg,1,20000,,85621,62540,,1
```

Angka di atas hanya **data dummy contoh format**. Baris bisa tiba dalam urutan stream yang berbeda; saat memutar ulang, kelompokkan berdasarkan `stream` lalu **urutkan berdasarkan `sample_index`/`timestamp_us`**. Gunakan timestamp kontinu 64-bit atau tangani wrap-around `micros()` ESP32 agar waktu sesi panjang tidak mundur.

| Kolom | Keterangan |
|---|---|
| `session_id` | ID unik satu sesi |
| `device_id` | ID perangkat pengirim |
| `stream` | `ecg` atau `ppg` |
| `sample_index` | Nomor urut sampel **pada stream tersebut** |
| `timestamp_us` | Waktu relatif sejak sesi dimulai (mikrodetik) |
| `ecg_adc` | ADC ECG 0–4095; kosong pada baris PPG |
| `ppg_ir`, `ppg_red` | Pembacaan MAX30102; kosong pada baris ECG |
| `lead_off` | 0/1 untuk ECG; kosong jika tidak relevan |
| `valid` | 1 valid, 0 tidak layak dianalisis |

Metadata sesi yang tidak perlu diulang dalam tiap baris (waktu mulai UTC, zona waktu, sampling rate target/aktual, versi firmware, durasi, versi format CSV, jumlah sample yang hilang) disimpan dalam **indeks SQLite lokal**. Jika ingin CSV dapat dipulihkan sepenuhnya setelah menginstal ulang aplikasi, sertakan juga berkas `session_id.metadata.json` di Drive atau simpan metadata penting sebagai kolom tambahan pada CSV. Untuk versi pertama, direkomendasikan **berkas metadata pendamping** agar konfigurasi pengukuran tidak hilang.

### 9.1 Alur perekaman

```text
ESP32 ADC/FIFO -> buffer -> BLE packets (+timestamp & sequence)
                            |
                      Flutter BLE receiver
                            |
                   validasi + deteksi paket hilang
                            |
                  tulis CSV lokal secara bertahap
                            |
                akhir sesi -> flush/close file
                            |
                  SQLite indeks -> riwayat
                            |
               antrean Google Drive upload
```

Tulis CSV **incremental** dengan buffering dan flush berkala, bukan menampung seluruh rekaman di RAM. Ketika aplikasi masuk background, putus BLE, atau penyimpanan penuh, tandai sesi terputus/parsial dan simpan data yang sudah berhasil diterima. Untuk akuisisi terus-menerus di background, pahami pembatasan Android/iOS; versi awal lebih aman menuntut aplikasi tetap aktif saat merekam.

### 9.2 Membuka CSV sebagai line chart

1. Pilih sesi dari **Riwayat Lokal** atau **Google Drive**.
2. Jika file hanya ada di Drive, unduh ke penyimpanan lokal terlebih dahulu.
3. Validasi header, versi format, jenis kolom, nilai numerik, dan ukuran file.
4. Pisahkan `stream=ecg` dan `stream=ppg`, lalu susun titik `(timestamp_us / 1e6, value)`.
5. Gambar **ECG ADC vs waktu**, **PPG IR vs waktu**, dan **PPG Red vs waktu** pada grafik terpisah/kanal pilihan.
6. Gunakan *windowing/downsampling khusus display* untuk file panjang agar grafik tetap responsif, **tanpa mengubah CSV asli**.
7. Pertahankan gap waktu atau sampel yang hilang sebagai **putusnya garis**; jangan menghubungkannya menjadi denyut palsu.

### 9.3 Struktur folder rekomendasi

```text
Google Drive/
└── ECG_PPG_Health_Monitor/
    ├── ECGPPG_2026-11-16_090700_ab12cd.csv
    ├── ab12cd.metadata.json
    ├── ECGPPG_2026-11-16_094500_ef34gh.csv
    └── ef34gh.metadata.json

Flutter (lokal)/
├── recordings/
│   ├── ECGPPG_2026-11-16_090700_ab12cd.csv
│   └── ECGPPG_2026-11-16_094500_ef34gh.csv
└── sessions.sqlite
```

Nama contoh bukan file aktual. File tidak diubah menjadi Google Sheets secara otomatis: simpan sebagai **CSV mentah** agar impor ulang dan pembuatan grafik konsisten.

### 9.4 Layar baru di Flutter

| Layar | Aksi utama |
|---|---|
| **Live Monitor** | Menampilkan grafik, tombol Mulai/Selesai Rekam, status koneksi |
| **Riwayat** | Daftar sesi lokal dan status sinkronisasi |
| **Detail Rekaman** | Buka CSV dan tampilkan grafik ECG/PPG lengkap, zoom/pan |
| **Google Drive** | Login, upload ulang, lihat cadangan, pulihkan file |
| **Pengaturan** | Kebijakan upload, kelola akun, ekspor/hapus data |

## 10. Estimasi ukuran rekaman

Asumsi ECG **250 baris/detik**, PPG **100 baris/detik**, total **350 baris/detik**. Jika rata-rata tiap baris CSV 65–100 byte termasuk metadata berulang, estimasi kasar sekitar **1,4–2,1 MB/menit** (sekitar **21–32 MB per 15 menit**). Angka ini adalah perkiraan desain; lakukan pengukuran pada hasil ekspor aktual dan atur strategi batch/resumable upload sesuai kebutuhan.

## 11. Estimasi waktu operasional

Gunakan pendekatan energi agar rugi konversi diperhitungkan. Asumsi energi efektif **80% energi nominal** (sekadar ilustrasi, bukan hasil pengujian):

| LiPo 1S | Energi nominal | Energi efektif asumsi | Pada 0,5 W | Pada 0,75 W | Pada 1,0 W |
|---|---:|---:|---:|---:|---:|
| 1.200 mAh | 4,44 Wh | 3,55 Wh | ~7,1 jam | ~4,7 jam | ~3,6 jam |
| 2.000 mAh | 7,40 Wh | 5,92 Wh | ~11,8 jam | ~7,9 jam | ~5,9 jam |

Waktu nyata bergantung pada efisiensi boost converter, ESP32, OLED, LED MAX30102, dan aktivitas BLE. Ukur arus/energi baterai ketika semua fungsi aktif. Arus charger TP4056 harus diperiksa dan disesuaikan dengan batas baterai.

## 12. Tahapan implementasi

- [ ] Uji keluaran daya 5 V boost dan 3V3 ESP32 menggunakan multimeter.
- [ ] Uji I2C Scanner: MAX30102 `0x57`, OLED `0x3C`.
- [ ] Uji tampilan OLED SH1106 dan tiga tombol.
- [ ] Uji pembacaan RAW IR/Red MAX30102 secara stabil.
- [ ] Uji pembacaan ADC ECG AD8232 dan lead-off.
- [ ] Implementasi buffer sampel dan timestamp, ukur laju efektif.
- [ ] Implementasi grafik ECG/PPG OLED dan Serial JSON untuk debugging.
- [ ] Implementasi BLE GATT dan pengiriman data batch.
- [ ] Bangun Flutter: scan, connect, disconnect, live chart.
- [ ] Tambahkan DSP untuk BPM, RR, HRV dan PPG pulse rate.
- [ ] Tambahkan estimasi SpO₂ hanya setelah algoritma dan pengujiannya siap.
- [ ] Implementasi sesi rekaman terpisah, penulisan CSV incremental, serta indeks histori SQLite.
- [ ] Implementasi impor CSV dan pemutaran ulang sebagai grafik ECG/PPG.
- [ ] Implementasi Google Sign-In/OAuth, Drive API, upload resumable, antrean retry offline, restore dari Drive.
- [ ] Uji pengukuran timestamp, gap sampel, data parsial, CSV rusak, dan sinkronisasi tanpa duplikasi.
- [ ] Uji daya baterai, kualitas sinyal, kehilangan paket dan reconnect BLE.
- [ ] Dokumentasikan keterbatasan dan hasil pengujian.

## 13. Lingkup dan keterbatasan

- **Termasuk (rencana):** perangkat portabel, grafik ECG/PPG, koneksi BLE, OLED, kontrol tombol, rekaman CSV setiap sesi, riwayat lokal, pemulihan line chart dari CSV, cadangan/pemulihan Google Drive, perhitungan parameter berbasis DSP.
- **Tidak termasuk:** AI/ML/DL, supervised/unsupervised learning, pengenalan otomatis penyakit, diagnosis, alarm medis, klaim akurasi klinis.
- AD8232 adalah **single-channel ECG** dan tidak menghasilkan tiga lead berbeda hanya karena menggunakan tiga elektroda.
- Perubahan filter, posisi elektroda, gerakan, serta artefak dapat mengubah bentuk ECG dan PPG.
- Penggunaan pada tubuh harus mematuhi persyaratan keselamatan listrik dan privasi data.

---

## 14. Referensi implementasi Google Drive

- [Google Drive API: scopes / `drive.file`](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Google Drive API: upload file dan resumable upload](https://developers.google.com/workspace/drive/api/guides/manage-uploads)
- [Google Drive API: folder data aplikasi yang tersembunyi](https://developers.google.com/workspace/drive/api/guides/appdata)

**Versi dokumen:** 1.1 — rancangan tanpa AI/ML/DL, ditambahkan rekaman CSV per sesi, replay grafik, dan backup/restore Google Drive.
