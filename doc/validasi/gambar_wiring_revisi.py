import matplotlib; matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Rectangle, Circle, Ellipse
plt.rcParams.update({'font.family': 'Liberation Sans', 'savefig.dpi': 230})
RED = '#d32f2f'; ORG = '#e65100'; BLK = '#212121'; YEL = '#f9a825'; GRN = '#2e7d32'; TEAL = '#00897b'; BLU = '#1565c0'; PUR = '#7b1fa2'; MAG = '#d81b60'; GRY = '#546e7a'
fig, ax = plt.subplots(figsize=(9.6, 6.6)); ax.set_xlim(0, 170); ax.set_ylim(0, 116); ax.axis('off'); fig.subplots_adjust(0, 0, 1, 1)

def box(x, y, w, h, fc, ec, lw=1.2, r=1.2):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle='round,pad=0,rounding_size=%s' % r, fc=fc, ec=ec, lw=lw))
def txt(x, y, s, fs=6, ha='left', va='center', color='k', bold=False, italic=False):
    ax.text(x, y, s, fontsize=fs, ha=ha, va=va, color=color, fontweight='bold' if bold else 'normal', fontstyle='italic' if italic else 'normal')
def wire(pts, color, lw=1.5, z=3):
    ax.plot([p[0] for p in pts], [p[1] for p in pts], color=color, lw=lw, solid_capstyle='round', solid_joinstyle='round', zorder=z)
def dot(x, y, color): ax.add_patch(Circle((x, y), 0.7, fc=color, ec=color, zorder=5))
def gnd(x, y, side='r'):
    d = 1 if side == 'r' else -1
    wire([(x, y), (x + 2.2*d, y)], BLK, 1.3); xx = x + 2.2*d
    for k, w in enumerate((1.6, 1.0, 0.4)): ax.plot([xx + 0.0*d - 0.0, xx], [y - w - k*0.0, y + w], color=BLK, lw=0, zorder=1)
    ax.plot([xx, xx], [y - 1.6, y + 1.6], color=BLK, lw=1.3)
    ax.plot([xx + 0.9*d, xx + 0.9*d], [y - 1.0, y + 1.0], color=BLK, lw=1.1); ax.plot([xx + 1.8*d, xx + 1.8*d], [y - 0.4, y + 0.4], color=BLK, lw=1.0)
def pinlab(x, y, s, side, fs=5.4):
    txt(x + (1.0 if side == 'r' else -1.0), y, s, fs, 'left' if side == 'r' else 'right')

# ---------------- header ----------------
HY = 5
box(1.5, 98.5 + HY, 70, 11.5, '#0d2b45', '#0d2b45')
txt(4, 106.3 + HY, 'ESP32 Health Monitor (Portable)', 12, color='white', bold=True)
txt(4, 102 + HY, 'AD8232 + MAX30102 + OLED I2C + 3 Tombol + LiPo 1S   |   Diagram wiring REVISI catu daya', 5.8, color='#ffd54f', bold=True)
box(74, 96.8 + HY, 46, 13.4, '#e8f1f9', '#90a4ae', 0.8)
txt(76, 108.5 + HY, 'Fitur', 7, bold=True)
for i, t in enumerate(['Baca sinyal ECG (AD8232)', 'Baca PPG (MAX30102: IR & Red)', 'Tampilan OLED I2C (SH1106 128×64)', '3 tombol untuk ganti layar', 'Baterai LiPo 1S + TP4056C berproteksi']):
    ax.add_patch(Circle((77.3, 105.7 + HY - i*1.95), 0.7, fc=GRN, ec=GRN)); txt(79, 105.7 + HY - i*1.95, t, 5.1)
box(123, 96.8 + HY, 45.5, 13.4, '#f5f5f5', '#90a4ae', 0.8); txt(145.7, 108.5 + HY, 'Tampilan OLED (contoh)', 6.3, 'center', bold=True)
for k, (a_, b_, c_, d_) in enumerate([('HEALTH MONITOR', 'ECG RAW: 1870', 'LEAD: CONNECTED', 'IR: 52341'), ('ECG MONITOR', '~/\\/~', 'ADC: 1870', ''), ('MAX30102 MONITOR', 'IR  : 52341', 'RED : 41230', 'PPG RAW')]):
    x0 = 124.5 + k*14.7; ax.add_patch(Rectangle((x0, 98 + HY), 13.4, 8.8, fc='#111', ec='#455a64', lw=0.8))
    for j, line in enumerate([a_, b_, c_, d_]): txt(x0 + 0.7, 105.8 + HY - j*1.9, line, 3.2, color='#e0e0e0')

# ---------------- ESP32 ----------------
ex0, ex1, ey0, ey1 = 44, 64, 24, 86
box(ex0, ey0, ex1 - ex0, ey1 - ey0, '#e0f2f1', '#00695c', 1.8, 1.5)
txt(54, 83.5, 'ESP32', 8, 'center', bold=True); txt(54, 81.3, 'DevKit V1', 6.2, 'center'); 
Lp = ['3V3', 'EN', 'VP', 'VN', '34', '35', '32', '33', '25', '26', '27', '14', '12', 'GND']
Rp = ['VIN', 'GND', '23', '22', '21', '19', '18', '5', '17', '16', '4', '2', '15']
ypin = lambda i: 78 - 4*i
for i, p in enumerate(Lp): txt(ex0 + 1, ypin(i), p, 5.2, 'left', color='#004d40', bold=p in ('3V3', '34', '32', '33', '25', '26', '27', 'GND'))
for i, p in enumerate(Rp): txt(ex1 - 1, ypin(i), p, 5.2, 'right', color='#004d40', bold=p in ('VIN', 'GND', '22', '21'))
for i in range(len(Lp)): ax.plot([ex0 - 0.8, ex0], [ypin(i)]*2, color='#00695c', lw=1)
for i in range(len(Rp)): ax.plot([ex1, ex1 + 0.8], [ypin(i)]*2, color='#00695c', lw=1)
txt(54, 28.5, '(38 pin)', 5, 'center', color=GRY)
Y = {p: ypin(i) for i, p in enumerate(Lp)}; Yr = {p: ypin(i) for i, p in enumerate(Rp)}

# ---------------- AD8232 ----------------
box(3, 62, 20, 26, '#ffebee', RED, 1.5)
txt(13, 85.7, 'AD8232', 7.5, 'center', bold=True); txt(13, 83.4, '(ECG Sensor)', 5.4, 'center')
adp = {'GND': 84, '3.3V': 80, 'OUTPUT': 76, 'LO+': 72, 'LO−': 68, 'SDN': 64}
for p, yy in adp.items():
    txt(22, yy, p, 5.2, 'right'); ax.plot([23, 23.8], [yy, yy], color=RED, lw=1)
txt(4.2, 72, 'RA  LA  RL', 5, 'left', color=RED, bold=True); txt(4.2, 69.6, 'jack elektroda', 4.6, 'left', color=GRY)
gnd(23.8, 84)
wire([(23.8, 80), (44, 80)], RED)                                   # 3.3V -> 3V3
wire([(23.8, 76), (34, 76), (34, Y['34']), (44, Y['34'])], YEL)      # OUTPUT -> GPIO34
wire([(23.8, 72), (31, 72), (31, Y['32']), (44, Y['32'])], GRN)      # LO+ -> GPIO32
wire([(23.8, 68), (28, 68), (28, Y['33']), (44, Y['33'])], TEAL)     # LO- -> GPIO33
txt(3.5, 60.3, 'SDN: tidak dipakai', 4.4, 'left', color=GRY, italic=True)
txt(36.3, 65.6, 'ADC1', 4.6, 'left', color=YEL, bold=True)

# ---------------- tombol ----------------
for yy, nm, gp in [(Y['25'], 'NEXT', 25), (Y['26'], 'PREV', 26), (Y['27'], 'SELECT', 27)]:
    box(20, yy - 1.7, 18, 3.4, '#fce4ec', MAG, 1.0, 0.8); txt(29, yy, '%s (GPIO %d)' % (nm, gp), 4.6, 'center', bold=True)
    wire([(38, yy), (ex0, yy)], MAG); gnd(20, yy, 'l')
txt(3, 52, 'INPUT_PULLUP', 4.8, 'left', color=MAG, bold=True); txt(3, 50, 'sisi lain ke GND', 4.6, 'left', color=GRY)

# ---------------- MAX30102 & OLED ----------------
box(96, 66, 20, 22, '#ede7f6', PUR, 1.5); txt(106, 85.7, 'MAX30102', 7.2, 'center', bold=True); txt(106, 83.5, '(Pulse Oximeter)', 5.2, 'center')
mp = {'VIN': 82, 'GND': 78, 'SCL': 74, 'SDA': 70, 'INT': 66.8}
for p, yy in mp.items(): txt(97.2, yy, p, 5.2, 'left'); ax.plot([95.2, 96], [yy, yy], color=PUR, lw=1)
box(96, 38, 20, 24, '#e3f2fd', BLU, 1.5); txt(106, 59.5, 'OLED I2C', 7.2, 'center', bold=True); txt(106, 57.3, 'SH1106 1,3" 128×64', 4.8, 'center'); txt(106, 55.3, '(alamat 0x3C)', 4.6, 'center', color=GRY)
op = {'GND': 52, 'VCC': 48, 'SCL': 44, 'SDA': 40.5}
for p, yy in op.items(): txt(97.2, yy, p, 5.2, 'left'); ax.plot([95.2, 96], [yy, yy], color=BLU, lw=1)
txt(106, 64, 'alamat I2C 0x57', 4.6, 'center', color=GRY)
# I2C
SCLx, SDAx = 84, 80
wire([(ex1 + 0.8, Yr['22']), (SCLx, Yr['22'])], BLU); wire([(SCLx, 74), (SCLx, 44)], BLU)
wire([(ex1 + 0.8, Yr['21']), (SDAx, Yr['21'])], PUR); wire([(SDAx, 70), (SDAx, 40.5)], PUR)
wire([(SCLx, 74), (95.2, 74)], BLU); wire([(SCLx, 44), (95.2, 44)], BLU); wire([(SDAx, 70), (95.2, 70)], PUR); wire([(SDAx, 40.5), (95.2, 40.5)], PUR)
for (x_, y_, c_) in [(SCLx, 74, BLU), (SCLx, 44, BLU), (SDAx, 70, PUR), (SDAx, 40.5, PUR)]: dot(x_, y_, c_)
txt(72, Yr['22'] + 1.3, 'SCL GPIO22', 4.6, 'left', color=BLU, bold=True); txt(70.5, Yr['21'] - 1.5, 'SDA GPIO21', 4.6, 'left', color=PUR, bold=True)
# 3V3 rail
R3 = 91
wire([(44, 80), (40.5, 80), (40.5, R3), (90, R3)], RED); dot(40.5, 80, RED)
wire([(90, R3), (90, 82), (95.2, 82)], RED); wire([(90, 48), (90, R3)], RED); wire([(90, 48), (95.2, 48)], RED); dot(90, 82, RED); dot(90, 48, RED)
txt(41.5, R3 + 1.3, '3V3 (3,3 V, regulator onboard)', 4.8, 'left', color=RED, bold=True); txt(68.5, R3 + 1.3, '→ AD8232, MAX30102, OLED', 4.8, 'left', color=RED, bold=True)
gnd(95.2, 78, 'l'); gnd(95.2, 52, 'l'); gnd(ex0 - 0.8, Y['GND'], 'l'); gnd(ex1 + 0.8, Yr['GND'], 'r')

# 5 V ke VIN
R5 = 94.5
wire([(ex1 + 0.8, Yr['VIN']), (67, Yr['VIN']), (67, R5), (119, R5), (119, 17), (99, 17)], ORG, 1.9)
txt(68.5, R5 + 1.3, 'VIN 5,0 V dari boost converter (bukan baterai langsung)', 4.8, 'left', color=ORG, bold=True)

# ---------------- jalur daya ----------------
box(3, 6, 16, 15, '#fff8e1', '#ff8f00', 1.4); txt(11, 18, 'LiPo 1S', 6.8, 'center', bold=True); txt(11, 15.3, '3,7 V nominal', 4.8, 'center'); txt(11, 13.1, '(3,0 – 4,2 V)', 4.8, 'center'); txt(11, 9, '+  /  –', 5, 'center')
box(25, 3, 26, 21, '#e8eaf6', '#3949ab', 1.4); txt(38, 21, 'TP4056C', 7, 'center', bold=True); txt(38, 18.7, '(versi dengan proteksi)', 4.6, 'center')
for p, yy, side in [('B+', 17, 'l'), ('B–', 9, 'l'), ('OUT+', 17, 'r'), ('OUT–', 9, 'r')]:
    txt(26 if side == 'l' else 50, yy, p, 5, 'left' if side == 'l' else 'right')
txt(38, 4.8, 'IN+/IN– : USB-C 5 V (alat MATI)', 4.4, 'center', color='#283593')
wire([(19, 17), (25, 17)], RED); wire([(19, 9), (25, 9)], BLK)
txt(22, 18.8, '', 4)
box(57, 13, 12, 8, '#f3e5f5', PUR, 1.2); txt(63, 18.5, 'Saklar', 5.8, 'center', bold=True); txt(63, 15.6, 'ON / OFF', 4.8, 'center')
wire([(51, 17), (57, 17)], RED); wire([(69, 17), (75, 17)], RED)
box(75, 4, 24, 19, '#fff3e0', ORG, 1.4); txt(87, 20, 'Boost MT3608', 6.4, 'center', bold=True); txt(87, 17.5, 'Vout = 5,0 V', 5.2, 'center', color=ORG, bold=True)
txt(76, 17, 'IN+', 4.8, 'left'); txt(76, 9, 'IN–', 4.8, 'left'); txt(98, 17, 'OUT+', 4.8, 'right'); txt(98, 9, 'OUT–', 4.8, 'right')
wire([(51, 9), (75, 9)], BLK); gnd(99, 9, 'r')
txt(87, 7, 'ukur 5,0 V dgn multimeter', 4.2, 'center', color=ORG, italic=True)
txt(122.2, 56, '', 4)
# peringatan
box(3, 25.5, 33, 10, '#ffebee', RED, 1.2, 0.8)
txt(19.5, 33.4, 'JANGAN sambung baterai / TP4056', 5.0, 'center', color=RED, bold=True); txt(19.5, 31.3, 'langsung ke pin 3V3 (maks. 3,6 V)', 5.0, 'center', color=RED, bold=True)
txt(19.5, 28.9, 'Gunakan boost 5 V → VIN', 4.8, 'center', color=RED); txt(19.5, 27.0, '(regulator onboard → 3V3)', 4.6, 'center', color=RED)

# ---------------- kolom info kanan ----------------
box(124, 54, 45, 40, 'white', '#90a4ae', 0.9, 0.8)
txt(146.5, 92, 'Tabel Koneksi', 7, 'center', bold=True)
rows = [('AD8232', '3.3V / GND', '3V3 / GND'), ('', 'OUTPUT', 'GPIO 34 (ADC1)'), ('', 'LO+ / LO–', 'GPIO 32 / 33'), ('MAX30102', 'VIN / GND', '3V3 / GND'), ('', 'SDA / SCL', 'GPIO 21 / 22'),
        ('OLED I2C', 'VCC / GND', '3V3 / GND'), ('', 'SDA / SCL', 'GPIO 21 / 22 (sama)'), ('Tombol', 'NEXT/PREV/SEL', 'GPIO 25 / 26 / 27'), ('TP4056C', 'B+ / B–', 'LiPo 1S'), ('', 'OUT+ / OUT–', 'saklar → boost'),
        ('Boost', 'OUT+ (5,0 V)', 'pin VIN ESP32'), ('', 'OUT– / GND', 'GND bersama')]
for i, (a, b, c) in enumerate(rows):
    yy = 89.2 - i*2.85
    if i % 2 == 0: ax.add_patch(Rectangle((124.6, yy - 1.4), 43.8, 2.85, fc='#f1f8f6', ec='none', zorder=0))
    txt(125.5, yy, a, 4.8, bold=True); txt(135.3, yy, b, 4.7); txt(150.5, yy, c, 4.7)
box(124, 38.5, 45, 14.5, 'white', '#90a4ae', 0.9, 0.8); txt(146.5, 51.3, 'Keterangan Warna Kabel', 6.4, 'center', bold=True)
leg = [(RED, '3V3 (daya sensor)'), (ORG, 'VIN 5,0 V'), (BLK, 'GND'), (YEL, 'ECG OUTPUT'), (GRN, 'LO+ (GPIO 32)'), (TEAL, 'LO– (GPIO 33)'), (BLU, 'I2C SCL (GPIO 22)'), (PUR, 'I2C SDA (GPIO 21)'), (MAG, 'Tombol (25/26/27)')]
for i, (c, t) in enumerate(leg):
    col, row = divmod(i, 5); x0 = 126 + col*22; yy = 48.6 - row*2.15
    ax.plot([x0, x0 + 4], [yy, yy], color=c, lw=2.2); txt(x0 + 5, yy, t, 4.5)
box(124, 20, 45, 16.5, 'white', '#90a4ae', 0.9, 0.8); txt(146.5, 35, 'Penempatan Elektroda', 6.4, 'center', bold=True)
ax.add_patch(FancyBboxPatch((127.5, 21.2), 12, 12.8, boxstyle='round,pad=0,rounding_size=3', fc='#ffe0b2', ec='#8d6e63', lw=0.9)); txt(133.5, 33.2, 'dada / perut', 3.8, 'center', color='#8d6e63')
for (px, py, c, lab) in [(130.4, 30.2, RED, 'RA'), (136.6, 30.2, YEL, 'LA'), (136.0, 24.0, GRN, 'RL')]:
    ax.add_patch(Circle((px, py), 1.25, fc=c, ec='k', lw=0.5)); txt(px, py - 2.1, lab, 4.2, 'center', bold=True)
txt(141.5, 32, 'RA (Right Arm) – merah', 4.4, bold=True); txt(141.5, 30.3, 'dada kanan atas', 4.2, color=GRY)
txt(141.5, 28.2, 'LA (Left Arm) – kuning', 4.4, bold=True); txt(141.5, 26.5, 'dada kiri atas', 4.2, color=GRY)
txt(141.5, 24.4, 'RL (Right Leg) – hijau', 4.4, bold=True); txt(141.5, 22.7, 'perut kanan bawah (referensi)', 4.2, color=GRY)
box(124, 1.5, 45, 16.5, '#fffde7', '#fbc02d', 0.9, 0.8); txt(126, 16.3, 'Catatan Penting', 6.4, bold=True)
notes = ['Hanya baterai saat elektroda terpasang (lepas USB).', 'Matikan alat saat mengisi baterai (TP4056 tanpa power-path).', 'Atur & ukur boost = 5,0 V sebelum disambung ke VIN.', 'Modul sensor memakai 3,3 V dari pin 3V3 ESP32.', 'Tombol INPUT_PULLUP: satu sisi ke GPIO, sisi lain ke GND.', 'Bukan alat medis — hanya untuk pembelajaran.']
for i, t in enumerate(notes): txt(126, 14 - i*2.0, '•  ' + t, 4.1)
fig.savefig('fig/wiring_revisi.png', dpi=230); print('ok')
