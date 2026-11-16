import SwiftUI

/// Membatasi jendela [lo, hi] agar berada di dalam `range` (lebar minimal 0,5 detik).
func clampWindow(_ lo: Double, _ hi: Double, to range: ClosedRange<Double>) -> ClosedRange<Double> {
    let dMin = range.lowerBound, dMax = max(range.upperBound, dMin + 1)
    let span = min(max(hi - lo, 0.5), dMax - dMin)
    let start = min(max(lo, dMin), dMax - span)
    return start...(start + span)
}

/// Line chart ringan (tanpa library). Dua mode:
/// - live  : jendela mengikuti sampel terbaru selebar `windowSec`.
/// - replay: zoom (cubit), pan (geser), dobel-ketuk = tampilkan semua; jendela dibagi antar-grafik lewat `window`.
/// Nilai NaN pada deret = garis putus. Data panjang di-decimate per piksel (min/max) hanya untuk tampilan.
struct WaveformChart: View {
    let series: Series?
    let title: String
    var subtitle = ""
    let color: Color
    var windowSec = 10.0
    /// Pesan di tengah grafik (mis. "Elektroda terlepas"); nil = tidak ada.
    var message: String?
    /// Berubah setiap ada data baru agar Canvas digambar ulang.
    var version = 0
    /// Mode replay: jendela waktu yang ditampilkan (nil = mode live).
    var window: Binding<ClosedRange<Double>>?
    /// Mode replay: rentang data untuk membatasi zoom/pan.
    var dataRange: ClosedRange<Double> = 0...1

    @State private var dragStart: ClosedRange<Double>?
    @State private var zoomStart: ClosedRange<Double>?

    private let padL: CGFloat = 52, padT: CGFloat = 30, padR: CGFloat = 12, padB: CGFloat = 22

    var body: some View {
        let canvas = Canvas { ctx, size in draw(ctx, size) }
            .background(Theme.chartBg)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        if let window {
            GeometryReader { geo in
                // Simultan dengan ScrollView induk: geser mendatar = pan grafik, geser tegak tetap menggulir halaman.
                canvas
                    .contentShape(Rectangle())
                    .simultaneousGesture(dragGesture(window, width: geo.size.width))
                    .simultaneousGesture(zoomGesture(window, width: geo.size.width))
                    .onTapGesture(count: 2) { window.wrappedValue = dataRange }
            }
        } else {
            canvas
        }
    }

    // ---- gestur (hanya mode replay) --------------------------------------------------------

    private func clamp(_ lo: Double, _ hi: Double) -> ClosedRange<Double> {
        clampWindow(lo, hi, to: dataRange)
    }

    private func dragGesture(_ window: Binding<ClosedRange<Double>>, width: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { v in
                if dragStart == nil && abs(v.translation.height) > abs(v.translation.width) { return }
                let start = dragStart ?? window.wrappedValue
                if dragStart == nil { dragStart = start }
                let span = start.upperBound - start.lowerBound
                let dt = -Double(v.translation.width / max(1, width - padL - padR)) * span
                window.wrappedValue = clamp(start.lowerBound + dt, start.upperBound + dt)
            }
            .onEnded { _ in dragStart = nil }
    }

    private func zoomGesture(_ window: Binding<ClosedRange<Double>>, width: CGFloat) -> some Gesture {
        MagnifyGesture()
            .onChanged { v in
                let start = zoomStart ?? window.wrappedValue
                if zoomStart == nil { zoomStart = start }
                let span = start.upperBound - start.lowerBound
                let frac = Double(min(max(0, (v.startLocation.x - padL) / max(1, width - padL - padR)), 1))
                let focus = start.lowerBound + frac * span
                let newSpan = span / max(0.05, Double(v.magnification))
                let lo = focus - frac * newSpan
                window.wrappedValue = clamp(lo, lo + newSpan)
            }
            .onEnded { _ in zoomStart = nil }
    }

    // ---- menggambar ------------------------------------------------------------------------

    private func draw(_ c: GraphicsContext, _ size: CGSize) {
        let w = size.width, h = size.height
        let left = padL, top = padT, right = w - padR, bottom = h - padB
        let pw = right - left, ph = bottom - top
        guard pw > 0, ph > 0 else { return }

        c.draw(Text(title).font(.system(size: 13, weight: .bold)).foregroundColor(color),
               at: CGPoint(x: left, y: top - 7), anchor: .bottomLeading)
        if !subtitle.isEmpty {
            c.draw(Text(subtitle).font(.system(size: 10)).foregroundColor(Theme.chartText),
                   at: CGPoint(x: right, y: top - 7), anchor: .bottomTrailing)
        }

        let s = series
        let live = window == nil
        var x0: Double, x1: Double
        if let window {
            x0 = window.wrappedValue.lowerBound; x1 = window.wrappedValue.upperBound
        } else {
            x1 = (s?.size ?? 0) > 0 ? s!.lastX : windowSec
            x0 = x1 - windowSec
        }
        let span = max(x1 - x0, 1e-6)

        // grid vertikal + label waktu
        var grid = Path()
        let step = niceStep(span / 6)
        var t = (x0 / step).rounded(.up) * step
        while t <= x1 {
            let px = left + CGFloat((t - x0) / span) * pw
            grid.move(to: CGPoint(x: px, y: top)); grid.addLine(to: CGPoint(x: px, y: bottom))
            let label = live ? String(format: "%.0fs", (t - x1).rounded() + 0) : String(format: "%.1fs", t)
            c.draw(Text(label).font(.system(size: 10)).foregroundColor(Theme.chartText),
                   at: CGPoint(x: min(max(px, left + 10), w - 22), y: h - 5), anchor: .bottom)
            t += step
        }
        for i in 0...4 {
            let py = top + ph * CGFloat(i) / 4
            grid.move(to: CGPoint(x: left, y: py)); grid.addLine(to: CGPoint(x: right, y: py))
        }
        c.stroke(grid, with: .color(Theme.chartGrid), lineWidth: 1)

        guard let s, s.size > 0 else { drawMessage(c, message ?? "Menunggu data…", size); return }

        let i0 = max(0, s.lowerBound(x0) - 1)
        let i1 = min(s.size - 1, s.lowerBound(x1))
        guard i0 <= i1 else { drawMessage(c, message ?? "Tidak ada data valid", size); return }
        var ymin = Float.greatestFiniteMagnitude, ymax = -Float.greatestFiniteMagnitude
        for i in i0...i1 { let y = s.ys[i]; if !y.isNaN { ymin = min(ymin, y); ymax = max(ymax, y) } }
        if ymin > ymax { drawMessage(c, message ?? "Tidak ada data valid", size); return }
        if ymax - ymin < 1e-3 { ymin -= 1; ymax += 1 }
        let pad = (ymax - ymin) * 0.08
        ymin -= pad; ymax += pad
        c.draw(Text(String(format: "%.0f", ymax)).font(.system(size: 10)).foregroundColor(Theme.chartText),
               at: CGPoint(x: 8, y: top), anchor: .topLeading)
        c.draw(Text(String(format: "%.0f", ymin)).font(.system(size: 10)).foregroundColor(Theme.chartText),
               at: CGPoint(x: 8, y: bottom), anchor: .bottomLeading)

        let sx = Double(pw) / span
        let sy = ph / CGFloat(ymax - ymin)
        var path = Path()
        var pen = false
        if CGFloat(i1 - i0 + 1) <= pw * 2 {
            for i in i0...i1 {
                let y = s.ys[i]
                if y.isNaN { pen = false; continue }
                let p = CGPoint(x: left + CGFloat((s.xs[i] - x0) * sx), y: bottom - CGFloat(y - ymin) * sy)
                if !pen { path.move(to: p); pen = true } else { path.addLine(to: p) }
            }
        } else {                       // decimation min/max per kolom piksel
            var col = Int.min
            var colTop = CGFloat.greatestFiniteMagnitude, colBot = -CGFloat.greatestFiniteMagnitude
            func flush() {
                guard col != Int.min, colTop <= colBot else { return }
                let px = left + CGFloat(col) + 0.5
                if !pen { path.move(to: CGPoint(x: px, y: colBot)); pen = true } else { path.addLine(to: CGPoint(x: px, y: colBot)) }
                path.addLine(to: CGPoint(x: px, y: colTop))
                colTop = .greatestFiniteMagnitude; colBot = -.greatestFiniteMagnitude
            }
            for i in i0...i1 {
                let y = s.ys[i]
                if y.isNaN { flush(); pen = false; col = Int.min; continue }
                let cx = Int((s.xs[i] - x0) * sx)
                if cx != col { flush(); col = cx }
                let py = bottom - CGFloat(y - ymin) * sy
                colTop = min(colTop, py); colBot = max(colBot, py)
            }
            flush()
        }
        var clipped = c
        clipped.clip(to: Path(CGRect(x: left, y: top, width: pw, height: ph)))
        clipped.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 1.8, lineJoin: .round))
        if let message { drawMessage(c, message, size) }
    }

    private func drawMessage(_ c: GraphicsContext, _ m: String, _ size: CGSize) {
        c.draw(Text(m).font(.system(size: 14, weight: .medium)).foregroundColor(Theme.chartMsg),
               at: CGPoint(x: size.width / 2, y: size.height / 2))
    }

    private func niceStep(_ raw: Double) -> Double {
        let steps = [0.1, 0.2, 0.5, 1, 2, 5, 10, 30, 60, 120, 300]
        return steps.first { $0 >= raw } ?? steps.last!
    }
}
