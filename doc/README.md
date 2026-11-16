# Dokumentasi proyek

Folder ini berisi catatan proyek dalam Markdown. Letaknya di **root proyek, di luar `app/src`**, sehingga **tidak ikut dikompilasi** oleh Gradle dan tidak masuk ke APK.

| Berkas | Isi |
|---|---|
| [CHANGELOG.md](CHANGELOG.md) | Catatan setiap perubahan (terbaru di atas). **Wajib diperbarui setiap ada perubahan.** |
| [ARSITEKTUR.md](ARSITEKTUR.md) | Struktur kode, alur data, format CSV, DSP |
| [DATA_DUMMY.md](DATA_DUMMY.md) | Cara data dummy dibuat dan dipetakan, serta batasannya |
| [PENGUJIAN.md](PENGUJIAN.md) | Hasil uji unit dan uji di emulator Android 9 |
| `Proposal EKG - Revisi.pdf` / `.docx` | Proposal tugas akhir hasil revisi (51 halaman), dengan perhitungan, ragam bentuk sinyal, diagram wiring, dan purwarupa aplikasi |
| `Portable ESP32 Health Monitor Wiring Diagram (Revisi Catu Daya).png` | Diagram wiring revisi (catu daya dikoreksi); file asli tetap ada |
| `screenshot/` | Tangkapan layar aplikasi (8 berkas) |
| `validasi/` | Skrip validasi algoritma dan hasil mentah (JSON) |

Spesifikasi awal (hardware, firmware, BLE, Drive) ada di `../README_ECG_PPG_Flutter_CSV_GoogleDrive_Tanpa_AI.md`.
Catatan: spesifikasi itu menyebut Flutter, tetapi implementasi memakai **Android native (Kotlin + XML Views)** sesuai keputusan proyek.

## Aturan pencatatan
Setiap perubahan kode, aset, atau konfigurasi → tambah entri di `CHANGELOG.md` (tanggal, ringkasan, file terkait), dan perbarui dokumen lain bila strukturnya berubah.
