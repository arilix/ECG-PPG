import SwiftUI

@main
struct ECGPPGApp: App {
    @StateObject private var engine = MonitorEngine()
    @AppStorage(PrefKey.themeMode) private var themeMode = 0

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(engine)
                .preferredColorScheme(colorScheme)
        }
    }

    private var colorScheme: ColorScheme? {
        switch themeMode {
        case 1: return .light
        case 2: return .dark
        default: return nil
        }
    }
}

/// Navigasi utama: tab Live, Perangkat, Riwayat; Pengaturan dibuka sebagai sheet dari tombol ☰.
struct ContentView: View {
    enum Tab { case live, devices, history }

    @EnvironmentObject private var engine: MonitorEngine
    @AppStorage(PrefKey.themeMode) private var themeMode = 0
    @State private var tab = Tab.live
    @State private var showSettings = false

    var body: some View {
        TabView(selection: $tab) {
            LiveView(openDevices: { tab = .devices }, openSettings: { showSettings = true })
                .tabItem { Label("Live", systemImage: "waveform.path.ecg") }
                .tag(Tab.live)
            DevicesView(openSettings: { showSettings = true })
                .tabItem { Label("Perangkat", systemImage: "antenna.radiowaves.left.and.right") }
                .tag(Tab.devices)
            HistoryView(openSettings: { showSettings = true })
                .tabItem { Label("Riwayat", systemImage: "list.bullet.rectangle") }
                .tag(Tab.history)
        }
        .tint(Theme.brand)
        .sheet(isPresented: $showSettings) {
            SettingsView()
                .environmentObject(engine)
                .preferredColorScheme(themeMode == 1 ? .light : themeMode == 2 ? .dark : nil)
        }
        .overlay(alignment: .top) {
            if let msg = engine.toast {
                Text(msg)
                    .font(.subheadline)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Color.black.opacity(0.82), in: Capsule())
                    .padding(.top, 8)
                    .padding(.horizontal, 16)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .onTapGesture { engine.toast = nil }
            }
        }
        .animation(.spring(duration: 0.3), value: engine.toast)
    }
}
