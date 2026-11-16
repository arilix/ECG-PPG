import sys, json, time, glob, os, csv
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib import *
from collections import deque

class Rate:
    def __init__(s): s.rr = deque(); s.last = None
    def add(s, t):
        if s.last is not None:
            d = t - s.last
            if d < 0.30: return
            if d <= 2.0:
                s.rr.append(d)
                if len(s.rr) > 20: s.rr.popleft()
            else: s.rr.clear()
        s.last = t
    def bpm(s, now):
        if s.last is None or now - s.last > 3.0 or len(s.rr) < 3: return None
        r = list(s.rr)[-8:]; return int(60.0/(sum(r)/len(r)))

# ---------------- ECG: MIT-BIH ----------------
recs = [l.strip() for l in open(glob.glob(MIT + 'RECORDS')[0] if glob.glob(MIT + 'RECORDS') else MIT + '../RECORDS') if l.strip()]
paced = {'102', '104', '107', '217'}
out = []
t0 = time.time()
for rec in recs:
    if rec in paced: continue
    ch = mlii_channel(rec); fs, ns, sig = read_hea(rec)
    x = read_dat(rec, ch)[:ns].astype(float)
    mv = (x - sig[ch]['base'])/sig[ch]['gain']
    adc = np.clip(np.round(2000 + mv*500), 0, 4095)
    t_out = np.arange(0, (len(adc)-1)/360.0, 1/250.0)
    y = np.round(np.interp(t_out, np.arange(len(adc))/360.0, adc)).astype(int)
    det = RPeak(250); peaks = []
    for i, v in enumerate(y):
        p = det.process(int(v), i)
        if p is not None: peaks.append(p/250.0)
    peaks = np.array(peaks)
    ann = np.array([t/360.0 for t, c, _ in read_atr(rec) if c in BEATS])
    ann = ann[ann >= 5.0]; pk = peaks[peaks >= 5.0]
    # keterlambatan tetap algoritma (puncak MWI tertinggal dari puncak R): median selisih ke anotasi terdekat
    j = np.searchsorted(ann, pk); j1 = np.clip(j, 1, len(ann)-1)
    nearest = np.where(np.abs(ann[j1-1]-pk) < np.abs(ann[j1]-pk), ann[j1-1], ann[j1])
    dif = pk - nearest; delay = float(np.median(dif[np.abs(dif) < 0.4])) if np.any(np.abs(dif) < 0.4) else 0.0
    pk2 = pk - delay
    used = np.zeros(len(pk2), bool); tp = 0
    for a in ann:
        k = np.searchsorted(pk2, a)
        cand = [m for m in (k-1, k) if 0 <= m < len(pk2) and not used[m] and abs(pk2[m]-a) <= 0.15]
        if cand:
            m = min(cand, key=lambda m: abs(pk2[m]-a)); used[m] = True; tp += 1
    fn = len(ann) - tp; fp = len(pk2) - tp
    dur = ns/360.0
    out.append(dict(rec=rec, lead=sig[ch]['name'], dur=dur, ann=int(len(ann)), det=int(len(pk)), tp=tp, fn=fn, fp=fp,
                    se=tp/len(ann), ppv=tp/max(1, len(pk2)), hr_ref=len(ann)/(dur-5)*60, hr_det=len(pk)/(dur-5)*60, delay_ms=delay*1000))
    print(rec, round(time.time()-t0), 's', out[-1]['se'], out[-1]['ppv'], flush=True)
json.dump(out, open('ecg_validation.json', 'w'), indent=1)

# ---------------- PPG: BIDMC ----------------
res = []
files = sorted(glob.glob(os.path.dirname(os.path.abspath(__file__)) + '/bidmc/*/bidmc_csv/bidmc_*_Signals.csv'))
for f in files:
    n = os.path.basename(f).split('_')[1]
    rows = list(csv.reader(open(f))); h = [c.strip() for c in rows[0]]
    pl = np.array([float(r[h.index('PLETH')]) for r in rows[1:]])
    t125 = np.arange(len(pl))/125.0
    t100 = np.arange(0, t125[-1], 0.01)
    ir = np.round(60000 + np.interp(t100, t125, pl)*30000).astype(int)
    nf = f.replace('_Signals', '_Numerics'); nr = list(csv.reader(open(nf))); nh = [c.strip() for c in nr[0]]
    hr_ref = {}; pulse_ref = {}
    for r in nr[1:]:
        try: t = int(float(r[0]))
        except: continue
        try: hr_ref[t] = float(r[nh.index('HR')])
        except: pass
        try: pulse_ref[t] = float(r[nh.index('PULSE')])
        except: pass
    det = PpgPeak(100); rate = Rate(); eh = []; ep = []
    for i, v in enumerate(ir):
        p = det.process(int(v), i)
        if p is not None: rate.add(p/100.0)
        if i % 100 == 99:
            sec = (i+1)//100
            if sec < 10: continue
            b = rate.bpm(i/100.0)
            if b is None: continue
            if sec in hr_ref: eh.append(abs(b-hr_ref[sec]))
            if sec in pulse_ref: ep.append(abs(b-pulse_ref[sec]))
    eh = np.array(eh); ep = np.array(ep)
    res.append(dict(rec=n, n=len(eh), mae_hr=float(eh.mean()) if len(eh) else None, mae_pulse=float(ep.mean()) if len(ep) else None,
                    within5_hr=float((eh <= 5).mean()) if len(eh) else None, within5_pulse=float((ep <= 5).mean()) if len(ep) else None,
                    hr_mean=float(np.mean(list(hr_ref.values()))) if hr_ref else None))
    print('ppg', n, res[-1]['mae_hr'], flush=True)
json.dump(res, open('ppg_validation.json', 'w'), indent=1)
print('DONE')
