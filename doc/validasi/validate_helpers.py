import numpy as np, csv, glob, os
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
def load_ppg():
    out = []
    for f in sorted(glob.glob('bidmc/*/bidmc_csv/bidmc_*_Signals.csv')):
        n = os.path.basename(f).split('_')[1]
        rows = list(csv.reader(open(f))); h = [c.strip() for c in rows[0]]
        pl = np.array([float(r[h.index('PLETH')]) for r in rows[1:]])
        t125 = np.arange(len(pl))/125.0; t100 = np.arange(0, t125[-1], 0.01)
        ir = np.round(60000 + np.interp(t100, t125, pl)*30000).astype(int)
        nr = list(csv.reader(open(f.replace('_Signals', '_Numerics')))); nh = [c.strip() for c in nr[0]]
        hr = {}
        for r in nr[1:]:
            try: t = int(float(r[0])); hr[t] = float(r[nh.index('HR')])
            except: pass
        out.append((n, ir, hr))
    return out
