#!/usr/bin/env python3
"""Ubah dataset dummy (MIT-BIH + BIDMC) menjadi aset ringan untuk aplikasi.

Pemakaian:
  python3 tools/make_dummy_assets.py <mitdb.zip> <bidmc.zip> <folder_output>

ECG  : MIT-BIH ch.0 (MLII) 360 Hz -> 250 Hz, dipetakan ke skala ADC AD8232 (0-4095)
PPG  : BIDMC PLETH 125 Hz -> 100 Hz, dipetakan ke skala IR/Red MAX30102.
       BIDMC hanya punya SATU kanal PPG; kanal Red adalah turunan sintetis (dummy).
"""
import sys, zipfile, io
import numpy as np

SECONDS = 90
ECG_RECORDS = ["100", "119", "208"]
PPG_RECORDS = ["01", "02", "05"]

def read_212(raw: bytes, n_samples: int):
    b = np.frombuffer(raw, dtype=np.uint8)
    n = (len(b) // 3) * 3
    b = b[:n].reshape(-1, 3).astype(np.int32)
    s0 = b[:, 0] | ((b[:, 1] & 0x0F) << 8)
    s1 = b[:, 2] | ((b[:, 1] & 0xF0) << 4)
    out = np.empty(len(b) * 2, dtype=np.int32)
    out[0::2], out[1::2] = s0, s1
    out = np.where(out > 2047, out - 4096, out)   # sign-extend 12 bit
    ch0 = out[0::2][:n_samples]                     # 2 kanal berselang-seling
    return ch0

def resample(x, fs_in, fs_out):
    t_out = np.arange(0, (len(x) - 1) / fs_in, 1.0 / fs_out)
    return np.interp(t_out, np.arange(len(x)) / fs_in, x)

def main(mitdb_zip, bidmc_zip, out):
    zm = zipfile.ZipFile(mitdb_zip)
    for rec in ECG_RECORDS:
        base = f"mit-bih-arrhythmia-database-1.0.0/{rec}"
        raw = zm.read(base + ".dat")
        x = read_212(raw, 360 * SECONDS)
        mv = (x - 1024) / 200.0
        adc = np.clip(np.round(2000 + mv * 500), 0, 4095).astype(int)
        adc = np.round(resample(adc.astype(float), 360, 250)).astype(int)
        np.savetxt(f"{out}/ecg_mitdb_{rec}.txt", adc, fmt="%d")
        print("ecg", rec, len(adc), "sampel", adc.min(), adc.max())

    zb = zipfile.ZipFile(bidmc_zip)
    for rec in PPG_RECORDS:
        txt = zb.read(f"bidmc-ppg-and-respiration-dataset-1.0.0/bidmc_csv/bidmc_{rec}_Signals.csv").decode()
        rows = [l.split(",") for l in txt.strip().splitlines()[1:]][: 125 * SECONDS]
        pl = np.array([float(r[2]) for r in rows])
        pl = resample(pl, 125, 100)
        ir = np.round(60000 + pl * 30000).astype(int)
        red = np.round(45000 + pl * 18000).astype(int)   # SINTETIS
        np.savetxt(f"{out}/ppg_bidmc_{rec}.txt", np.stack([ir, red], 1), fmt="%d", delimiter=",")
        print("ppg", rec, len(ir), "sampel")

if __name__ == "__main__":
    main(*sys.argv[1:4])
