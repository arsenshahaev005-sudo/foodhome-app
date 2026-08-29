import SwiftUI

@main
struct FoodHomeApp: App {
    @UIApplicationDelegateAdaptor(FoodHomeAppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
