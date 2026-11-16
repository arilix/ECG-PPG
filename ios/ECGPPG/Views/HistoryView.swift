import SwiftUI

struct HistoryView: View {
    @EnvironmentObject private var engine: MonitorEngine
    var openSettings: () -> Void
    @State private var items: [SessionInfo] = []

    var body: some View {
        NavigationStack {
            Group {
                if items.isEmpty {
                    ContentUnavailableView("Belum ada rekaman", systemImage: "folder",
                                           description: Text("Mulai rekam dari tab Live; sesi tersimpan di sini sebagai CSV."))
                } else {
                    List {
                        Section {
                            ForEach(items) { s in
                                NavigationLink(value: s) { SessionRow(s: s) }
                            }
                            .onDelete { idx in
                                idx.map { items[$0] }.forEach(engine.repo.delete)
                                reload()
                            }
                        } header: {
                            Text("\(items.count) sesi tersimpan di perangkat")
                        }
                    }
                }
            }
            .background(Theme.bg)
            .navigationTitle("Riwayat")
            .navigationDestination(for: SessionInfo.self) { DetailView(info: $0) }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: openSettings) { Image(systemName: "line.3.horizontal") }
                }
            }
            .onAppear(perform: reload)
            .refreshable { reload() }
        }
    }

    private func reload() { items = engine.repo.list() }
}

private struct SessionRow: View {
    let s: SessionInfo

    var body: some View {
        let lost = s.ecgLost + s.ppgLost
        let warn = s.partial || lost > 0
        VStack(alignment: .leading, spacing: 3) {
            HStack {
                Text(formatDate(s.startedAtMs)).font(.body.weight(.semibold))
                Spacer()
                Text(badge)
                    .font(.caption2.weight(.bold))
                    .padding(.horizontal, 8).padding(.vertical, 2)
                    .background(Theme.brandContainer, in: Capsule())
                    .foregroundStyle(Theme.onBrandContainer)
            }
            Text("\(formatDuration(s.durationS)) menit · ECG \(s.ecgSamples) · PPG \(s.ppgSamples) sampel")
                .font(.caption)
                .foregroundStyle(Theme.textSecondary)
            Text(status(lost))
                .font(.caption)
                .foregroundStyle(warn ? Theme.statusWarn : Theme.textSecondary)
        }
        .padding(.vertical, 2)
    }

    private var badge: String {
        switch s.syncStatus {
        case "local_only": return "LOKAL"
        case "exported": return "DISALIN"
        default: return s.syncStatus.uppercased()
        }
    }

    private func status(_ lost: Int64) -> String {
        var t = "ID \(s.id)"
        if s.partial { t += " · parsial" }
        if lost > 0 { t += " · \(lost) sampel hilang" }
        return t
    }
}
