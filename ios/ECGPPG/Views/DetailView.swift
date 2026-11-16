import SwiftUI

/// Detail rekaman: baca CSV, susun ulang grafik ECG/PPG dengan zoom/pan (sinkron antar-grafik).
struct DetailView: View {
    @EnvironmentObject private var engine: MonitorEngine
    @Environment(\.dismiss) private var dismiss
    let info: SessionInfo

    @State private var loaded: LoadedSession?
    @State private var error: String?
    @State private var window: ClosedRange<Double> = 0...10
    @State private var dataRange: ClosedRange<Double> = 0...10
    @State private var confirmDelete = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    MetricCard(title: "Durasi", value: formatDuration(info.durationS), unit: "", color: Theme.brand)
                    MetricCard(title: "BPM rata-rata", value: loaded?.avgEcgBpm.map(String.init) ?? "--", unit: "", color: Theme.accentEcg)
                    MetricCard(title: "Gap", value: loaded.map { "\($0.ecgGaps + $0.ppgGaps)" } ?? "--", unit: "", color: Theme.statusWarn)
                }
                VStack(spacing: 10) {
                    WaveformChart(series: loaded?.ecg, title: "ECG", subtitle: "ADC 0–4095", color: Theme.chartEcg,
                                  message: loaded == nil ? (error ?? "Memuat CSV…") : nil,
                                  window: $window, dataRange: dataRange)
                        .frame(height: 200)
                    WaveformChart(series: loaded?.ir, title: "PPG IR", subtitle: "MAX30102", color: Theme.chartIr,
                                  window: $window, dataRange: dataRange)
                        .frame(height: 170)
                    WaveformChart(series: loaded?.red, title: "PPG Red", subtitle: "MAX30102", color: Theme.chartRed,
                                  window: $window, dataRange: dataRange)
                        .frame(height: 170)
                }
                Text(infoText)
                    .font(.footnote)
                    .foregroundStyle(error == nil ? Theme.textSecondary : Theme.statusBad)
                Text("Cubit untuk zoom, geser untuk menggeser, ketuk dua kali untuk tampilkan semua.")
                    .font(.caption2)
                    .foregroundStyle(Theme.textSecondary)
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationTitle("Sesi \(info.id)")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                ShareLink(items: [info.csvFile, info.metaFile].filter { FileManager.default.fileExists(atPath: $0.path) }) {
                    Image(systemName: "square.and.arrow.up")
                }
                Button(role: .destructive) { confirmDelete = true } label: { Image(systemName: "trash") }
            }
        }
        .confirmationDialog("Hapus rekaman?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Hapus", role: .destructive) { engine.repo.delete(info); dismiss() }
            Button("Batal", role: .cancel) {}
        } message: {
            Text("File CSV dan metadata sesi \(info.id) akan dihapus dari penyimpanan lokal.")
        }
        .task { await load() }
    }

    private var infoText: String {
        if let error { return "Gagal membuka CSV: \(error)" }
        guard let s = loaded else { return "Memuat CSV…" }
        var t = "\(formatDate(info.startedAtMs)) · ECG \(s.ecg.size) · PPG \(s.ir.size) baris"
        if s.ecgGaps + s.ppgGaps > 0 { t += "\nGap — ECG \(s.ecgGaps), PPG \(s.ppgGaps): garis sengaja diputus" }
        if s.badRows > 0 { t += "\n\(s.badRows) baris rusak dilewati" }
        if info.partial { t += "\nSesi parsial" }
        return t
    }

    private func load() async {
        guard loaded == nil else { return }
        let url = info.csvFile
        let result = await Task.detached(priority: .userInitiated) { Result { try CsvLoader.load(url) } }.value
        switch result {
        case .success(let s):
            let lo = min(s.ecg.size > 0 ? s.ecg.firstX : .infinity, s.ir.size > 0 ? s.ir.firstX : .infinity)
            let hi = max(s.ecg.lastX, s.ir.lastX)
            dataRange = lo...max(hi, lo + 1)
            window = dataRange
            loaded = s
        case .failure(let e):
            error = e.localizedDescription
        }
    }
}
