import SwiftUI

struct LiveView: View {
    @EnvironmentObject private var engine: MonitorEngine
    @AppStorage(PrefKey.windowSec) private var windowSec = 10
    var openDevices: () -> Void
    var openSettings: () -> Void

    var body: some View {
        let st = engine.state
        let connected = st == .connected
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    header(st)
                    metrics(connected)
                    charts(connected)
                    Text(info(connected))
                        .font(.footnote)
                        .foregroundStyle(Theme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(16)
            }
            .background(Theme.bg)
            .safeAreaInset(edge: .bottom) { buttons(st) }
            .navigationTitle("Monitor")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: openSettings) { Image(systemName: "line.3.horizontal") }
                        .accessibilityLabel("Pengaturan")
                }
                ToolbarItem(placement: .topBarTrailing) { recBadge }
            }
        }
    }

    private func header(_ st: MonitorEngine.State) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "waveform.path.ecg")
                .font(.title3.weight(.semibold))
                .foregroundStyle(Theme.brand)
                .frame(width: 44, height: 44)
                .background(Theme.brandContainer, in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(st == .connected ? "● Terhubung" : st == .connecting ? "● Menghubungkan…" : "● Terputus")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(st == .connected ? Theme.statusOk : st == .connecting ? Theme.statusWarn : Theme.statusBad)
                Text(engine.device.map { "\($0.displayName) · \($0.address)" } ?? "Belum ada perangkat terhubung")
                    .font(.caption)
                    .foregroundStyle(Theme.textSecondary)
                    .lineLimit(1)
            }
            Spacer()
        }
        .padding(12)
        .card()
    }

    private func metrics(_ connected: Bool) -> some View {
        let ecg = connected && !engine.leadOff ? engine.ecgBpm().map(String.init) ?? "--" : "--"
        let ppg = connected && engine.fingerPresent ? engine.ppgBpm().map(String.init) ?? "--" : "--"
        let hrv = connected && !engine.leadOff ? engine.rmssdMs().map(String.init) ?? "--" : "--"
        return HStack(spacing: 8) {
            MetricCard(title: "ECG", value: ecg, unit: "BPM", color: Theme.accentEcg)
            MetricCard(title: "PPG", value: ppg, unit: "BPM", color: Theme.accentPpg)
            MetricCard(title: "SpO₂", value: "--", unit: "%", color: Theme.accentSpo2)   // belum diimplementasikan/dikalibrasi
            MetricCard(title: "HRV", value: hrv, unit: "ms", color: Theme.accentHrv)
        }
        .opacity(connected ? 1 : 0.45)
    }

    private func charts(_ connected: Bool) -> some View {
        let w = Double(windowSec)
        let ppgMsg = connected && !engine.fingerPresent ? "Jari tidak terdeteksi" : nil
        return VStack(spacing: 10) {
            WaveformChart(series: engine.ecgSeries, title: "ECG", subtitle: "AD8232 · 250 Hz", color: Theme.chartEcg,
                          windowSec: w, message: engine.leadOff ? "Elektroda terlepas (lead-off)" : nil, version: engine.version)
                .frame(height: 170)
            WaveformChart(series: engine.irSeries, title: "PPG IR", subtitle: "MAX30102 · 100 Hz", color: Theme.chartIr,
                          windowSec: w, message: ppgMsg, version: engine.version)
                .frame(height: 150)
            WaveformChart(series: engine.redSeries, title: "PPG Red", subtitle: "MAX30102 · 100 Hz", color: Theme.chartRed,
                          windowSec: w, message: ppgMsg, version: engine.version)
                .frame(height: 150)
        }
    }

    private func info(_ connected: Bool) -> String {
        guard connected else {
            return "Tekan Cari Perangkat untuk menyambung. Pengaturan: ☰ di kiri atas. Bukan alat medis."
        }
        var s = engine.rrMs().map { "RR terakhir \($0) ms" } ?? "Mengukur…"
        if engine.ecgGapsLive + engine.ppgGapsLive > 0 {
            s += "  ·  ⚠ paket hilang: ECG \(engine.ecgGapsLive), PPG \(engine.ppgGapsLive)"
        }
        return s
    }

    @ViewBuilder private var recBadge: some View {
        if let rec = engine.recorder {
            let d = Int(rec.durationS)
            TimelineView(.periodic(from: .now, by: 0.7)) { ctx in
                let on = Int(ctx.date.timeIntervalSinceReferenceDate / 0.7) % 2 == 0
                Text(String(format: "● REC %d:%02d", d / 60, d % 60))
                    .font(.caption.weight(.bold).monospacedDigit())
                    .foregroundStyle(Theme.statusBad)
                    .opacity(on ? 1 : 0.3)
                    .animation(.easeInOut(duration: 0.6), value: on)
            }
        }
    }

    private func buttons(_ st: MonitorEngine.State) -> some View {
        let recording = engine.recorder != nil
        return HStack(spacing: 10) {
            Button {
                if st == .disconnected { openDevices() } else { engine.disconnect() }
            } label: {
                Label(st == .disconnected ? "Cari Perangkat" : st == .connecting ? "Menghubungkan…" : "Putuskan",
                      systemImage: st == .disconnected ? "magnifyingglass" : "link")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(st == .connecting)

            Button {
                if recording {
                    let info = engine.stopRecording()
                    engine.showToast(info.map { "Sesi \($0.id) tersimpan di Riwayat" } ?? "Gagal menyimpan")
                } else if let err = engine.startRecording() {
                    engine.showToast(err)
                }
            } label: {
                Label(recording ? "Selesai Rekam" : "Mulai Rekam", systemImage: recording ? "stop.fill" : "record.circle")
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity)
                    .foregroundStyle(recording ? Color.white : Theme.onBrandContainer)
            }
            .buttonStyle(.borderedProminent)
            .tint(recording ? Theme.statusBad : Theme.brandContainer)
            .disabled(st != .connected)
        }
        .controlSize(.large)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.bar)
    }
}

struct MetricCard: View {
    let title: String
    let value: String
    let unit: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption2.weight(.semibold)).foregroundStyle(Theme.textSecondary)
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(value)
                    .font(.system(size: 22, weight: .bold, design: .rounded).monospacedDigit())
                    .foregroundStyle(color)
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
                Text(unit).font(.system(size: 10)).foregroundStyle(Theme.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .card()
    }
}
