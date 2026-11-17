# Mengunduh Pulse Transit Time PPG dari PhysioNet

Dataset ini versi **1.1.0**, berisi 66 rekaman multimodal PPG, ECG, IMU, tekanan, dan data numerik. Ukuran total setelah diekstrak sekitar **2,9 GB**. Siapkan ruang kosong setidaknya 4 GB dan koneksi internet yang stabil. Dataset ini open access; akun PhysioNet tidak diperlukan.

## Kebutuhan

- Linux/macOS atau WSL dengan Bash
- Python 3.10 atau lebih baru
- `python3-venv` (pada Ubuntu/Debian: `sudo apt install python3-venv` bila modul `venv` belum tersedia)
- Koneksi internet dan ruang disk yang cukup

Skrip membuat virtual environment lokal `.venv-physionet`, memasang paket resmi `physionet`, lalu mengunduh dataset ke `data/physionet/pulse-transit-time-ppg/1.1.0/`. File data tidak ikut dimasukkan ke Git.

## Jalankan

Dari direktori utama proyek:

```bash
bash tools/download_physionet_ptt.sh
```

Untuk memilih lokasi lain:

```bash
bash tools/download_physionet_ptt.sh /lokasi/penyimpanan/ptt/1.1.0
```

Perintah CLI yang dijalankan skrip:

```bash
physionet download pulse-transit-time-ppg --version 1.1.0 --output data/physionet/pulse-transit-time-ppg/1.1.0
```

Untuk unduhan manual alternatif, PhysioNet juga mencantumkan:

```bash
wget -r -N -c -np https://physionet.org/files/pulse-transit-time-ppg/1.1.0/
```

Jika dipakai dalam publikasi, cantumkan sitasi dataset: Mehrgardt, P., Khushi, M., Poon, S., & Withana, A. (2022). *Pulse Transit Time PPG Dataset* (version 1.1.0). PhysioNet. https://doi.org/10.13026/jpan-6n92

Sumber resmi: https://physionet.org/content/pulse-transit-time-ppg/1.1.0/
