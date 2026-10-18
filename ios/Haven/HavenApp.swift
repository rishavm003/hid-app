import SwiftUI
import UIKit
import BackgroundTasks

@MainActor
class HavenDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: FamilyModel.refreshIdentifier, using: .main) { task in
            guard let refresh = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
            Task { @MainActor in FamilyModel.shared.handleRefresh(refresh) }
        }
        if launchOptions?[.location] != nil { FamilyModel.shared.resumeLocationLaunch() }
        return true
    }
}

@main
struct HavenApp: App {
    @UIApplicationDelegateAdaptor(HavenDelegate.self) private var delegate
    @StateObject private var model = SafetyModel()
    @StateObject private var family = FamilyModel.shared
    @Environment(\.scenePhase) private var phase
    var body: some Scene {
        WindowGroup {
            ContentView().environmentObject(model).environmentObject(family)
                .overlay {
                    if phase != .active {
                        ZStack {
                            Color(red: 0.94, green: 0.96, blue: 0.92).ignoresSafeArea()
                            Image(systemName: "circle.hexagongrid.fill").font(.largeTitle)
                        }.accessibilityLabel("Haven privacy screen")
                    }
                }
                .task { await model.refresh(); await family.reconcile() }
                .onChange(of: phase) { _, phase in
                    if phase == .active { Task { await model.refresh(); await family.reconcile() } }
                }
        }
    }
}
