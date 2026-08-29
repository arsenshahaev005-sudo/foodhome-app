import UIKit

@MainActor
final class IOSPaymentLauncher: PaymentLaunching {
    func launch(_ destination: ValidatedPaymentDestination) -> Bool {
        guard UIApplication.shared.canOpenURL(destination.url) else { return false }
        UIApplication.shared.open(destination.url, options: [:], completionHandler: nil)
        return true
    }
}
