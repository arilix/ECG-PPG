import SwiftUI
import UniformTypeIdentifiers

/// Pengaturan (sheet dari tombol ☰): akun Google (simulasi), lokasi penyimpanan, data dummy, simulasi, tampilan.
struct SettingsView: View {
    @EnvironmentObject private var engine: MonitorEngine
    @Environment(\.dismiss) private var dismiss

    @AppStorage(PrefKey.googleName) private var googleName: String?
    @AppStorage(PrefKey.googleEmail) private var googleEmail: String?
    @AppStorage(PrefKey.exportBookmark) private var exportBookmark: Data?
    @AppStorage(PrefKey.ecgRecord) private var ecgRecord = "ecg_mitdb_100.txt"
    @AppStorage(PrefKey.ppgRecord) private var ppgRecord = "ppg_bidmc_01.txt"
    @AppStorage(PrefKey.windowSec) private var windowSec = 10
    @AppStorage(PrefKey.simDrops) private var simDrops = false
    @AppStorage(PrefKey.simLeadOff) private var simLeadOff = false
    @AppStorage(PrefKey.themeMode) private var themeMode = 0

    @State private var pickAccount = false
    @State private var pickFolder = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 12) {
                        Text(avatar)
                            .font(.headline)
                            .foregroundStyle(Theme.onBrandContainer)
                            .frame(width: 44, height: 44)
                            .background(Theme.brandContainer, in: Circle())
                        VStack(alignment: .leading, spacing: 2) {
                            Text(googleEmail == nil ? "Belum masuk" : (googleName ?? googleEmail ?? "")).font(.headline)
                            Text(googleEmail ?? "Untuk cadangan ke Google Drive")
                                .font(.caption).foregroundStyle(Theme.textSecondary)
                        }
                        Spacer()
                        Button(googleEmail == nil ? "Masuk" : "Keluar") {
                            if googleEmail == nil { pickAccount = true } else { googleName = nil; googleEmail = nil }
                        }
                        .buttonStyle(.bordered)
                    }
                } header: { Text("Akun Google (simulasi)") }

                Section {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(exportBookmark == nil ? "Penyimpanan internal aplikasi" : "Internal + salinan ke folder")
                        Text(exportBookmark.map(StorageExporter.folderLabel) ?? "Privat; juga terlihat di app Files › ECG PPG Monitor")
                            .font(.caption).foregroundStyle(Theme.textSecondary)
                    }
                    Button { pickFolder = true } label: { Label("Pilih folder salinan…", systemImage: "folder") }
                    if exportBookmark != nil {
                        Button("Hanya internal", role: .destructive) { exportBookmark = nil }
                    }
                } header: { Text("Lokasi penyimpanan") }

                Section {
                    Picker("Rekaman ECG", selection: $ecgRecord) {
                        ForEach(DummyData.ecgRecords(), id: \.self) { Text(DummyData.label($0)).tag($0) }
                    }
                    Picker("Rekaman PPG", selection: $ppgRecord) {
                        ForEach(DummyData.ppgRecords(), id: \.self) { Text(DummyData.label($0)).tag($0) }
                    }
                    Picker("Jendela grafik", selection: $windowSec) {
                        ForEach([5, 10, 20], id: \.self) { Text("\($0) detik").tag($0) }
                    }
                } header: { Text("Data dummy") } footer: {
                    Text("Pilihan rekaman ECG/PPG berlaku pada sambungan berikutnya (putuskan lalu sambungkan lagi).")
                }

                Section {
                    Toggle("Simulasi paket hilang", isOn: $simDrops)
                    Toggle("Simulasi elektroda terlepas", isOn: $simLeadOff)
                } header: { Text("Simulasi kondisi") }

                Section {
                    Picker("Tema", selection: $themeMode) {
                        Text("Sistem").tag(0)
                        Text("Terang").tag(1)
                        Text("Gelap").tag(2)
                    }
                    .pickerStyle(.segmented)
                } header: { Text("Tampilan") } footer: {
                    Text("Bukan alat medis. Proyek pembelajaran/penelitian; pengolahan sinyal klasik (DSP), tanpa AI.")
                }
            }
            .navigationTitle("Pengaturan")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Selesai") { dismiss() } }
            }
            .onChange(of: simDrops) { engine.refreshSourceFlags() }
            .onChange(of: simLeadOff) { engine.refreshSourceFlags() }
            .confirmationDialog("Masuk dengan Google (simulasi)", isPresented: $pickAccount, titleVisibility: .visible) {
                ForEach(DummyAccounts.list, id: \.self) { a in
                    Button("\(a.name) · \(a.email)") { googleName = a.name; googleEmail = a.email }
                }
                Button("Batal", role: .cancel) {}
            } message: {
                Text("Akun di bawah hanya contoh. Login Google dan Drive sungguhan belum diimplementasikan.")
            }
            .fileImporter(isPresented: $pickFolder, allowedContentTypes: [.folder]) { result in
                guard case .success(let url) = result else { return }
                let ok = url.startAccessingSecurityScopedResource()
                defer { if ok { url.stopAccessingSecurityScopedResource() } }
                if let data = try? url.bookmarkData() { exportBookmark = data }
                else { engine.showToast("Folder tidak dapat dipakai.") }
            }
        }
    }

    private var avatar: String {
        guard let s = googleName ?? googleEmail, let c = s.first else { return "?" }
        return String(c).uppercased()
    }
}
