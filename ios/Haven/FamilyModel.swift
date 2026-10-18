import Foundation
import Combine
import CoreLocation
import UIKit
import BackgroundTasks

struct FamilySession: Codable {
    let origin: String
    let token: String
    let id: String
    let controller: String
    var enabled: Bool
}

@MainActor
final class FamilyModel: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = FamilyModel()
    static let refreshIdentifier = "com.example.notes.location-refresh"
    @Published var session: FamilySession?
    @Published var message = "Join only a controller you know and trust."
    @Published var busy = false
    @Published var lastUpload: Date?
    @Published var reviewedController: String?
    @Published var permissionText = "Location permission not checked"
    private var reviewedOrigin = ""
    private var reviewedCode = ""
    private let manager = CLLocationManager()
    private var uploading = false
    private var nextAttempt = Date.distantPast
    private var generation = 0
    private var foregroundReady = false
    private var backgroundCompletion: ((Bool) -> Void)?
    private var forceNextUpload = false

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.distanceFilter = kCLDistanceFilterNone
        manager.pausesLocationUpdatesAutomatically = true
        manager.showsBackgroundLocationIndicator = true
        if let raw = Vault.read("familySession"), let data = raw.data(using: .utf8) {
            session = try? JSONDecoder().decode(FamilySession.self, from: data)
        }
        if let id = session?.id {
            lastUpload = UserDefaults.standard.object(forKey: "familyUpload-"+id) as? Date
        }
        updatePermissionText()
    }

    private func store() throws {
        let raw = try session.map { String(decoding: try JSONEncoder().encode($0), as: UTF8.self) } ?? ""
        // Background location uploads need this token while locked, after first unlock.
        try Vault.write("familySession", raw, background: true)
    }

    private func api(_ origin: String, _ path: String, token: String? = nil, body: [String: Any]? = nil) async throws -> [String: Any] {
        guard let url = URL(string: origin), url.scheme == "https", url.host != nil,
              url.user == nil, url.password == nil, url.query == nil, url.fragment == nil,
              url.path.isEmpty || url.path == "/" else { throw HavenError.message("Use an HTTPS server origin.") }
        var req = URLRequest(url: url.appendingPathComponent(String(path.dropFirst())))
        req.timeoutInterval = 20
        if let token { req.setValue("Bearer "+token, forHTTPHeaderField: "Authorization") }
        if let body {
            req.httpMethod = "POST"
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        let (data, response) = try await URLSession.shared.data(for: req)
        let result = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
        guard (response as? HTTPURLResponse)?.statusCode == 200 else {
            throw HavenError.message(result["error"] as? String ?? "Server did not confirm the request.")
        }
        return result
    }

    func preview(origin: String, code: String) async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        reviewedController = nil
        do {
            let result = try await api(origin, "/v1/family/preview", body: ["code": code])
            reviewedController = result["controller_name"] as? String
            reviewedOrigin = origin
            reviewedCode = code
            message = "Review the controller identity below before joining. Location stays off until you enable it."
        } catch { message = error.localizedDescription }
    }

    func join(origin: String, code: String, name: String) async {
        guard !busy, session == nil, reviewedController != nil,
              reviewedOrigin == origin, reviewedCode == code else {
            message = "Review this invitation first."
            return
        }
        busy = true
        defer { busy = false }
        do {
            let result = try await api(origin, "/v1/family/join", body: ["code": code, "name": name, "consent": true])
            guard let token = result["token"] as? String, let id = result["id"] as? String,
                  let controller = result["controller_name"] as? String else { throw HavenError.message("Invalid join response.") }
            session = FamilySession(origin: origin, token: token, id: id, controller: controller, enabled: false)
            try store()
            message = "Joined \(controller). Location sharing is OFF."
        } catch { message = "Join not confirmed: \(error.localizedDescription). A used invitation may need replacing." }
    }

    func enable() async {
        guard var current = session, !busy else { return }
        busy = true
        let version = generation
        defer { busy = false }
        do {
            _ = try await api(current.origin, "/v1/family/sharing", token: current.token, body: ["enabled": true])
            guard generation == version else {
                _ = try? await api(current.origin, "/v1/family/sharing", token: current.token, body: ["enabled": false])
                return
            }
            current.enabled = true
            session = current
            try store()
            foregroundReady = true
            message = "Sharing enabled with \(current.controller). iOS may delay or pause updates."
            startLocation()
            scheduleRefresh()
        } catch { message = "Could not enable sharing: \(error.localizedDescription)" }
    }

    private func startLocation() {
        guard session?.enabled == true, foregroundReady else { return }
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedAlways, .authorizedWhenInUse:
            manager.allowsBackgroundLocationUpdates = true
            manager.startUpdatingLocation()
            if manager.authorizationStatus == .authorizedAlways,
               CLLocationManager.significantLocationChangeMonitoringAvailable() {
                manager.startMonitoringSignificantLocationChanges()
            } else { manager.stopMonitoringSignificantLocationChanges() }
        default:
            manager.stopUpdatingLocation()
            manager.stopMonitoringSignificantLocationChanges()
            message = "Location permission is unavailable. No coordinates can be uploaded; change permission in iOS Settings if you choose."
        }
    }

    func stop() async {
        generation += 1
        foregroundReady = false
        manager.stopUpdatingLocation()
        manager.stopMonitoringSignificantLocationChanges()
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.refreshIdentifier)
        finishBackground(false)
        manager.allowsBackgroundLocationUpdates = false
        session?.enabled = false
        forceNextUpload = false
        do { try store() } catch { message = "Stopped locally, but could not save that preference. Turn off location permission in iOS Settings."; return }
        message = "Stopped on this phone. Confirming pause with the server…"
        guard let current = session else { return }
        do {
            _ = try await api(current.origin, "/v1/family/sharing", token: current.token, body: ["enabled": false])
            message = "Sharing paused. Your controller can still see your last shared location and notes until you erase them."
        } catch { message = "Stopped locally. Server pause is unconfirmed; last shared data remains visible. Reopen Family when online to retry." }
    }

    func reconcile() async {
        guard let current = session, !busy else { return }
        do {
            let result = try await api(current.origin, "/v1/family/me", token: current.token)
            if current.enabled {
                if result["sharing"] as? Bool == true { foregroundReady = true; startLocation(); scheduleRefresh() }
                else { await stop() }
            } else if result["sharing"] as? Bool == true { await stop() }
        } catch {
            message = "Cannot verify family access. No new uploads started. \(error.localizedDescription)"
        }
    }

    func erase() async {
        await stop()
        guard let current = session else { return }
        busy = true
        defer { busy = false }
        do {
            _ = try await api(current.origin, "/v1/family/erase", token: current.token, body: [:])
            lastUpload = nil
            UserDefaults.standard.removeObject(forKey: "familyUpload-"+current.id)
            message = "Shared family location and manual notes erased. Existing SOS/check-in records and messages are separate."
        } catch { message = "Erase not confirmed. Shared data may remain on the server. \(error.localizedDescription)" }
    }

    func shareNote(text: String, occurred: Date, id: UUID) async -> Bool {
        guard let current = session, !busy else { return false }
        busy = true
        defer { busy = false }
        do {
            _ = try await api(current.origin, "/v1/family/notes", token: current.token,
                              body: ["id": id.uuidString, "text": text, "occurred": occurred.timeIntervalSince1970, "consent": true])
            message = "Your manual note was shared with \(current.controller)."
            return true
        } catch { message = "Note not confirmed. Retry the same note. \(error.localizedDescription)"; return false }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        updatePermissionText()
        if session?.enabled == true { startLocation() }
    }

    private func updatePermissionText() {
        switch manager.authorizationStatus {
        case .authorizedAlways: permissionText = "Always allowed — background recovery available"
        case .authorizedWhenInUse: permissionText = "While using — enable Always for background recovery"
        case .denied, .restricted: permissionText = "Location blocked — open Settings to change it"
        default: permissionText = "Location permission not granted yet"
        }
    }

    func requestBackgroundPermission() {
        guard session?.enabled == true else { message = "Enable sharing first."; return }
        if manager.authorizationStatus == .authorizedWhenInUse {
            manager.requestAlwaysAuthorization()
            message = "Choose Always if iOS offers it. Otherwise open Location Settings and change Haven's permission to Always."
        } else if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else { openSettings() }
    }

    func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }

    func reconnect(origin: String) async {
        guard let current = session, !busy, !current.enabled else {
            message = "Stop sharing locally before reconnecting to a new server address."
            return
        }
        busy = true
        defer { busy = false }
        do {
            let result = try await api(origin, "/v1/family/me", token: current.token)
            guard result["id"] as? String == current.id,
                  result["controller_name"] as? String == current.controller else {
                throw HavenError.message("This server does not match your family account.")
            }
            _ = try await api(origin, "/v1/family/sharing", token: current.token, body: ["enabled": false])
            session = FamilySession(origin: origin, token: current.token, id: current.id, controller: current.controller, enabled: false)
            try store()
            message = "Reconnected. Enable sharing again, then reconnect Home and Circle below if you use SOS."
        } catch { message = "Reconnect failed: \(error.localizedDescription)" }
    }

    func shareNow() {
        guard session?.enabled == true else { message = "Enable family sharing first."; return }
        nextAttempt = .distantPast
        forceNextUpload = true
        manager.requestLocation()
        message = "Requesting a fresh location. Waiting for server confirmation…"
    }

    func scheduleRefresh() {
        guard session?.enabled == true else { return }
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.refreshIdentifier)
        let request = BGAppRefreshTaskRequest(identifier: Self.refreshIdentifier)
        request.earliestBeginDate = Date().addingTimeInterval(3600)
        do { try BGTaskScheduler.shared.submit(request) }
        catch { /* Core Location remains available; scheduled refresh is best effort. */ }
    }

    func resumeLocationLaunch() {
        guard session?.enabled == true, manager.authorizationStatus == .authorizedAlways else { return }
        // Re-register after an OS location launch; the server still enforces owner consent/revocation.
        manager.startMonitoringSignificantLocationChanges()
        scheduleRefresh()
    }

    func handleRefresh(_ task: BGAppRefreshTask) {
        scheduleRefresh()
        guard session?.enabled == true, manager.authorizationStatus == .authorizedAlways,
              backgroundCompletion == nil else { task.setTaskCompleted(success: false); return }
        backgroundCompletion = { success in task.setTaskCompleted(success: success) }
        task.expirationHandler = { Task { @MainActor in self.finishBackground(false) } }
        manager.requestLocation()
    }

    private func finishBackground(_ success: Bool) {
        let completion = backgroundCompletion
        backgroundCompletion = nil
        completion?(success)
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let current = session, current.enabled else { finishBackground(false); return }
        if !forceNextUpload, let lastUpload, Date().timeIntervalSince(lastUpload) < 3600 { finishBackground(true); return }
        guard !uploading, Date() >= nextAttempt else { finishBackground(false); return }
        guard let location = locations.last, location.horizontalAccuracy >= 0,
              abs(location.timestamp.timeIntervalSinceNow) < 90 else { finishBackground(false); return }
        uploading = true
        nextAttempt = Date().addingTimeInterval(60)
        let version = generation
        Task {
            defer { uploading = false }
            do {
                _ = try await api(current.origin, "/v1/family/location", token: current.token,
                    body: ["location": ["latitude": location.coordinate.latitude, "longitude": location.coordinate.longitude,
                                        "accuracy": location.horizontalAccuracy, "timestamp": location.timestamp.timeIntervalSince1970]])
                guard generation == version, session?.enabled == true else { finishBackground(false); return }
                lastUpload = Date()
                forceNextUpload = false
                UserDefaults.standard.set(lastUpload, forKey: "familyUpload-"+current.id)
                message = "Location received by your family server. Next upload is due in about one hour, when iOS provides a fresh sample."
                finishBackground(true)
            } catch {
                if generation == version { message = "Location upload unconfirmed. Your controller still has the previous sample. \(error.localizedDescription)" }
                finishBackground(false)
            }
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        finishBackground(false)
        message = "Location unavailable. Your controller retains only the last successfully shared sample."
    }
}
