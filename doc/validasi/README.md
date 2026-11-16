# Skrip validasi algoritma (untuk proposal)

Mereproduksi angka validasi pada Bab IV proposal.

| Berkas | Isi |
|---|---|
| `lib.py` | Pembaca MIT-BIH (format 212, anotasi `.atr`) dan **salinan Python identik** dari `RPeakDetector` / `PpgPeakDetector` pada `Dsp.kt` |
| `validate.py` | Se, +P (ECG, 44 rekaman MIT-BIH) dan MAE BPM (PPG, rekaman BIDMC) → `ecg_validation.json`, `ppg_validation.json` |
| `validate_helpers.py`, `cmp_ppg.py` | Pembanding detektor PPG awal vs final → `ppg_cmp.json` |

## Cara menjalankan
1. Ekstrak dataset di samping skrip: `mit/` (isi `mitdb-1.0.0.zip`) dan `bidmc/` (isi `bidmc-ppg-and-respiration-dataset-1.0.0.zip`).
2. `python3 validate.py` (numpy diperlukan). Waktu ± 1 menit.

Hasil akhir: ECG Se 98,66%, +P 99,50% (100.653 detak); PPG MAE 3,28 BPM (52 rekaman).
