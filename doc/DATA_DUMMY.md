# Data dummy

Sumber: `~/AndroidStudioProjects/Dummy-Data/` (di luar proyek).
- `mitdb-1.0.0.zip` — MIT-BIH Arrhythmia Database (ECG 360 Hz, format 212).
- `bidmc-ppg-and-respiration-dataset-1.0.0.zip` — BIDMC PPG and Respiration Dataset (PPG 125 Hz).

## Membuat ulang aset
```bash
python3 tools/make_dummy_assets.py \
  ../Dummy-Data/mitdb-1.0.0.zip \
  ../Dummy-Data/bidmc-ppg-and-respiration-dataset-1.0.0.zip \
  app/src/main/assets/dummy
```
Perlu Python 3 dan numpy. Daftar record ada di konstanta `ECG_RECORDS` / `PPG_RECORDS` pada skrip (90 detik per record).

## Pemetaan
| | Sumber | Hasil |
|---|---|---|
| ECG | MIT-BIH kanal 0 (MLII), 360 Hz | Resample ke 250 Hz; `adc = 2000 + mV × 500`, dibatasi 0–4095 (meniru ADC ESP32) |
| PPG IR | BIDMC `PLETH` 125 Hz (ternormalisasi 0–1) | Resample ke 100 Hz; `ir = 60000 + pleth × 30000` |
| PPG Red | — | **Sintetis**: `45000 + pleth × 18000` (BIDMC hanya punya satu kanal PPG) |

## Batasan
- Bentuk ECG MIT-BIH berbeda dari AD8232 asli (derau, baseline, polaritas).
- Karena Red sintetis, SpO₂ tidak dapat dihitung dari data dummy ini.
- Data diputar berulang; ada loncatan kecil pada titik pengulangan (90 detik).
- Sumber data: PhysioNet; perhatikan lisensi masing-masing dataset bila aplikasi didistribusikan.
