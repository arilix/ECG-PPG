import SwiftUI
import UIKit

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
                  blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}

extension Color {
    init(hex: UInt32) { self.init(uiColor: UIColor(hex: hex)) }

    /// Warna yang mengikuti mode terang/gelap.
    static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
    }
}

/// Palet sama dengan versi Android (res/values/colors.xml dan values-night).
enum Theme {
    static let bg = Color.dynamic(0xF3F6F8, 0x0B1015)
    static let card = Color.dynamic(0xFFFFFF, 0x151C23)
    static let cardStroke = Color.dynamic(0xE0E6EA, 0x25303A)
    static let textSecondary = Color.dynamic(0x5F6B76, 0x9AA8B5)
    static let brand = Color.dynamic(0x00796B, 0x4DD0BE)
    static let brandContainer = Color.dynamic(0xD2F0EA, 0x0F3D38)
    static let onBrandContainer = Color.dynamic(0x00332D, 0xBDF2E9)
    static let accentEcg = Color.dynamic(0x14935A, 0x3DDC84)
    static let accentPpg = Color.dynamic(0x1E88C7, 0x4FC3F7)
    static let accentSpo2 = Color.dynamic(0xD98200, 0xFFB74D)
    static let accentHrv = Color.dynamic(0x7E57C2, 0xB39DDB)
    static let statusOk = Color.dynamic(0x14935A, 0x3DDC84)
    static let statusWarn = Color.dynamic(0xD98200, 0xFFB74D)
    static let statusBad = Color.dynamic(0xD93C3C, 0xFF6E6E)

    // Grafik (selalu gelap seperti layar monitor)
    static let chartBg = Color(hex: 0x0F151B)
    static let chartGrid = Color(hex: 0x26323C)
    static let chartText = Color(hex: 0x8FA3B3)
    static let chartMsg = Color(hex: 0xFFB74D)
    static let chartEcg = Color(hex: 0x3DDC84)
    static let chartIr = Color(hex: 0x4FC3F7)
    static let chartRed = Color(hex: 0xFF6E6E)
}

/// Kartu dengan latar dan garis tepi seperti MaterialCardView di versi Android.
struct CardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Theme.cardStroke, lineWidth: 1))
    }
}

extension View {
    func card() -> some View { modifier(CardStyle()) }
}

func formatDuration(_ s: Double) -> String {
    s < 0 ? "?" : String(format: "%d:%02d", Int(s) / 60, Int(s) % 60)
}

func formatDate(_ ms: Int64) -> String {
    let f = DateFormatter()
    f.locale = Locale(identifier: "id_ID")
    f.dateFormat = "d MMM yyyy, HH:mm:ss"
    return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
}
