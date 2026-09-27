import SwiftUI

@main
struct MadusApp: App {
    @StateObject private var model = AppModel()
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(model)
                .environmentObject(model.player)
                .environmentObject(model.library)
                .tint(.primary)
                .task { await model.bootstrap() }
        }
    }
}
