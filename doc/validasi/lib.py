import numpy as np, os, glob
MIT = glob.glob(os.path.dirname(__file__) + '/mit/*/')[0]

def read_hea(rec):
    L = open(f'{MIT}{rec}.hea').read().splitlines()
    n = int(L[0].split()[1]); fs = float(L[0].split()[2]); ns = int(L[0].split()[3])
    sig = []
    for l in L[1:1+n]:
        p = l.split(); sig.append(dict(gain=float(p[2].split('/')[0]), base=int(p[4]) if len(p) > 4 else 0, name=p[-1]))
    return fs, ns, sig

def read_dat(rec, ch=0):
    raw = np.fromfile(f'{MIT}{rec}.dat', dtype=np.uint8)
    b = raw[:len(raw)//3*3].reshape(-1, 3).astype(np.int32)
    s0 = b[:, 0] | ((b[:, 1] & 0x0F) << 8)
    s1 = b[:, 2] | ((b[:, 1] & 0xF0) << 4)
    out = np.empty(len(b)*2, dtype=np.int32); out[0::2] = s0; out[1::2] = s1
    out = np.where(out > 2047, out - 4096, out)
    return out[ch::2]

def mlii_channel(rec):
    fs, ns, sig = read_hea(rec)
    for i, s in enumerate(sig):
        if s['name'].upper().startswith('MLII'): return i
    return 0

def read_atr(rec):
    b = open(f'{MIT}{rec}.atr', 'rb').read()
    ann = []; t = 0; i = 0
    while i + 1 < len(b):
        w = b[i] | (b[i+1] << 8); i += 2
        if w == 0: break
        code = w >> 10; dt = w & 0x3FF
        if code == 59:
            w1 = b[i] | (b[i+1] << 8); w2 = b[i+2] | (b[i+3] << 8); i += 4
            t += (w1 << 16) | w2; continue
        if code in (60, 61, 62): continue
        if code == 63:
            aux = b[i:i+dt].decode('latin1'); i += dt + (dt & 1)
            if ann: ann[-1] = (ann[-1][0], ann[-1][1], aux)
            continue
        t += dt; ann.append((t, code, None))
    return ann

BEATS = set(range(1, 14)) | {34, 35, 37, 38, 41}

# ---- detektor: salinan 1:1 dari RPeakDetector.kt ----
class RPeak:
    def __init__(self, fs):
        self.fs = fs; self.win = max(1, int(0.15*fs)); self.ring = [0.0]*self.win
        self.pos = 0; self.sum = 0.0; self.init = False; self.base = self.smooth = self.prev = 0.0
        self.spki = self.npki = self.learn = 0.0; self.n = 0; self.inpeak = False
        self.cur = 0.0; self.curidx = 0; self.last = -10**12; self.refr = int(0.25*fs); self.decay_after = int(1.0*fs)
    def process(self, x, idx):
        if not self.init:
            self.base = float(x); self.smooth = 0.0; self.prev = 0.0; self.init = True
        self.base += 0.01*(x - self.base)
        hp = x - self.base
        self.smooth += 0.3*(hp - self.smooth)
        d = self.smooth - self.prev; self.prev = self.smooth
        sq = d*d
        self.sum += sq - self.ring[self.pos]; self.ring[self.pos] = sq
        self.pos = (self.pos + 1) % self.win
        mwi = max(0.0, self.sum/self.win)
        self.n += 1
        lim = int(2*self.fs)
        if self.n <= lim:
            self.learn = max(self.learn, mwi)
            if self.n == lim: self.spki = self.learn*0.5; self.npki = self.learn*0.05
            return None
        if (not self.inpeak) and idx - self.last > self.decay_after: self.spki *= 0.997
        thr = self.npki + 0.25*(self.spki - self.npki)
        found = None
        if not self.inpeak:
            if mwi > thr and idx - self.last > self.refr:
                self.inpeak = True; self.cur = mwi; self.curidx = idx
            elif mwi <= thr:
                self.npki += 0.125*(mwi - self.npki)
        else:
            if mwi > self.cur: self.cur = mwi; self.curidx = idx
            elif mwi < 0.5*self.cur:
                self.spki += 0.125*(self.cur - self.spki); self.last = self.curidx
                self.inpeak = False; found = self.curidx
        return found

class PpgPeak:
    def __init__(self, fs):
        from collections import deque
        self.fs = fs; self.init = False; self.smooth = self.base = self.env = 0.0
        self.a1 = self.a2 = 0.0; self.last = -1e12; self.amps = deque(maxlen=6); self.rrs = deque(maxlen=5)
    def process(self, x, idx):
        if not self.init:
            self.smooth = float(x); self.base = self.smooth; self.a1 = self.a2 = 0.0; self.init = True
        self.smooth += 0.2*(x - self.smooth); self.base += 0.01*(self.smooth - self.base)
        ac = self.smooth - self.base
        self.env = max(self.env*0.9995, ac)
        cand = idx - 1; found = None
        if self.last > 0 and cand - self.last > 2*self.fs and self.amps:
            from collections import deque
            self.amps = deque([a*0.97 for a in self.amps], maxlen=6)
        ref = (sum(self.amps)/len(self.amps)) if self.amps else self.env
        if len(self.rrs) >= 2:
            l = sorted(self.rrs); med = l[len(l)//2] if len(l) % 2 else (l[len(l)//2-1] + l[len(l)//2])/2
            refr = min(0.45, max(0.33, 0.5*med))*self.fs
        else: refr = 0.33*self.fs
        if self.a1 > self.a2 and self.a1 >= ac and self.a1 > 0.3*ref and cand - self.last > refr:
            gap = (cand - self.last)/self.fs
            if self.last > 0 and gap < 2.0: self.rrs.append(gap)
            self.amps.append(self.a1); self.last = cand; found = cand
        self.a2 = self.a1; self.a1 = ac
        return found
