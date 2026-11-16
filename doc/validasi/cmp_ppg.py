import sys; sys.path.insert(0,'.')
import numpy as np, json
from validate_helpers import Rate, load_ppg
from lib import PpgPeak
class PpgOld:
    def __init__(s, fs):
        s.fs=fs; s.init=False; s.smooth=s.base=s.env=0.0; s.a1=s.a2=0.0; s.last=-1e12; s.refr=0.33*fs
    def process(s, x, idx):
        if not s.init: s.smooth=float(x); s.base=s.smooth; s.a1=s.a2=0.0; s.init=True
        s.smooth += 0.2*(x-s.smooth); s.base += 0.01*(s.smooth-s.base)
        ac=s.smooth-s.base; s.env=max(s.env*0.9995, ac); found=None; cand=idx-1
        if s.a1 > s.a2 and s.a1 >= ac and s.a1 > 0.5*s.env and cand-s.last > s.refr: s.last=cand; found=cand
        s.a2=s.a1; s.a1=ac; return found
def run(make, data):
    out={}
    for n, ir, hr in data:
        det=make(); rate=Rate(); e=[]
        for i, v in enumerate(ir):
            p=det.process(int(v), i)
            if p is not None: rate.add(p/100.0)
            if i % 100 == 99:
                sec=(i+1)//100
                if sec < 10: continue
                b=rate.bpm(i/100.0)
                if b is not None and sec in hr: e.append(abs(b-hr[sec]))
        out[n]=(float(np.mean(e)) if e else None, float(np.mean(np.array(e)<=5)) if e else None, len(e))
    return out
data=load_ppg()
res={}
for name, mk in [('old', lambda: PpgOld(100)), ('new', lambda: PpgPeak(100))]:
    o=run(mk, data); res[name]=o
    v=[(a,b,c) for a,b,c in o.values() if a is not None]
    m=np.array([x[0] for x in v]); w=np.array([x[1] for x in v]); n=np.array([x[2] for x in v])
    print(name,len(v),'MAE mean %.2f median %.2f within5 %.3f >10 %d <=3 %d'%(m.mean(),np.median(m),np.average(w,weights=n),(m>10).sum(),(m<=3).sum()))
json.dump(res, open('ppg_cmp.json','w'))
print([k for k,v in res['new'].items() if v[0] is None])
