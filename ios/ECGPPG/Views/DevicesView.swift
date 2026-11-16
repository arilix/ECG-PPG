import SwiftUI

/// Tab Perangkat: cari perangkat BLE (simulasi), lihat kekuatan sinyal, pilih untuk menyambung.
struct DevicesView: View {
    @EnvironmentObject private var engine: MonitorEngine
    var openSettings: () -> Void

    var body: some View {
        let st = engine.state
        let sorted = engine.devices.sorted {
            $0.compatible != $1.compatible ? $0.compatible : $0.rssi > $1.rssi
        }
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if st != .disconnected, let cur = engine.device { currentCard(cur, st) }

                    Button {
                        if engine.scanning { engine.stopScan() } else { engine.startScan() }
                    } label: {
                        Label(engine.scanning ? "Hentikan Pencarian" : "Cari Perangkat",
                              systemImage: engine.scanning ? "stop.fill" : "magnifyingglass")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .disabled(st != .disconnected)

                    HStack {
                        Text(countText(sorted.count))
                            .font(.subheadline.weight(.semibold))
                        Spacer()
                        if engine.scanning || st == .connecting { ProgressView() }
                    }

                    if sorted.isEmpty {
                        emptyState
                    } else {
                        VStack(spacing: 8) {
                            ForEach(sorted) { d in
                                DeviceRow(device: d,
                                          last: engine.prefs.lastDeviceAddress == d.address,
                                          connecting: st == .connecting && engine.device?.address == d.address)
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        if engine.state != .disconnected { engine.showToast("Putuskan koneksi saat ini dulu.") }
                                        else { engine.connect(d) }
                                    }
                            }
                        }
                    }

                    Text(st != .disconnected ? "Putuskan koneksi untuk mencari perangkat lain."
                                             : "Data perangkat masih simulasi (dummy). Pencarian BLE nyata menyusul.")
                        .font(.footnote)
                        .foregroundStyle(Theme.textSecondary)
                }
                .padding(16)
            }
            .background(Theme.bg)
            .navigationTitle("Perangkat")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: openSettings) { Image(systemName: "line.3.horizontal") }
                }
            }
        }
        .onAppear {
            // Masuk tab ini dalam keadaan terputus dan belum pernah memindai -> langsung cari.
            if engine.state == .disconnected && engine.devices.isEmpty { engine.startScan() }
        }
    }

    private func countText(_ n: Int) -> String {
        if engine.scanning { return "Mencari… \(n) perangkat ditemukan" }
        return n > 0 ? "\(n) perangkat ditemukan" : "Belum ada hasil"
    }

    private func currentCard(_ cur: ScannedDevice, _ st: MonitorEngine.State) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "heart.text.square.fill")
                .font(.title2)
                .foregroundStyle(Theme.brand)
            VStack(alignment: .leading, spacing: 2) {
                Text(cur.displayName).font(.headline)
                Text(st == .connecting ? "Menghubungkan…  ·  \(cur.address)" : "Terhubung  ·  \(engine.sourceLabel)")
                    .font(.caption)
                    .foregroundStyle(Theme.textSecondary)
            }
            Spacer()
            Button(st == .connecting ? "Batal" : "Putuskan") { engine.disconnect() }
                .buttonStyle(.bordered)
                .tint(Theme.statusBad)
        }
        .padding(14)
        .background(Theme.brandContainer.opacity(0.5), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var emptyState: some View {
        VStack(spacing: 6) {
            Image(systemName: "antenna.radiowaves.left.and.right")
                .font(.largeTitle)
                .foregroundStyle(Theme.textSecondary)
            Text(engine.scanning ? "Mencari perangkat…" : "Tidak ada perangkat").font(.headline)
            Text(engine.scanning ? "Pastikan alat menyala dan dekat dengan ponsel." : "Tekan Cari Perangkat untuk memindai lagi.")
                .font(.footnote)
                .foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }
}

private struct DeviceRow: View {
    let device: ScannedDevice
    let last: Bool
    let connecting: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: device.compatible ? "waveform.path.ecg" : "dot.radiowaves.left.and.right")
                .foregroundStyle(device.compatible ? Theme.brand : Theme.textSecondary)
                .frame(width: 40, height: 40)
                .background((device.compatible ? Theme.brandContainer : Theme.cardStroke), in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(device.displayName).font(.body.weight(.semibold))
                Text(device.address).font(.caption.monospaced()).foregroundStyle(Theme.textSecondary)
                Text(badge)
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(device.compatible ? Theme.brand : Theme.textSecondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Image(systemName: "cellularbars", variableValue: Double(device.signalLevel) / 4)
                    .foregroundStyle(Theme.brand)
                Text("\(device.rssi) dBm").font(.caption2).foregroundStyle(Theme.textSecondary)
            }
        }
        .padding(12)
        .card()
        .opacity(device.compatible ? 1 : 0.7)
    }

    private var badge: String {
        if connecting { return "MENGHUBUNGKAN…" }
        if device.compatible && last { return "KOMPATIBEL · TERAKHIR" }
        return device.compatible ? "KOMPATIBEL" : "TIDAK DIKENAL"
    }
}
