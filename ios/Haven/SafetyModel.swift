import Foundation
import Combine
import CoreLocation
import Security
import UserNotifications

enum Vault {
    static func read(_ key: String) -> String? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "Notes", kSecAttrAccount as String: key,
            kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var item: CFTypeRef?
        if SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
           let data = item as? Data { return String(data: data, encoding: .utf8) }
        // Fallback for previous Haven service key
        let fallback: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "Haven", kSecAttrAccount as String: key,
            kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var fallbackItem: CFTypeRef?
        if SecItemCopyMatching(fallback as CFDictionary, &fallbackItem) == errSecSuccess,
           let data = fallbackItem as? Data { return String(data: data, encoding: .utf8) }
        return nil
    }
    static func write(_ key: String, _ value: String, background: Bool = false) throws {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "Notes", kSecAttrAccount as String: key]
        let attributes: [String: Any] = [kSecValueData as String: Data(value.utf8),
            kSecAttrAccessible as String: background ? kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly : kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var insert = query
            attributes.forEach { insert[$0.key] = $0.value }
            guard SecItemAdd(insert as CFDictionary, nil) == errSecSuccess else { throw NotesError.message("Could not save securely.") }
        } else if status != errSecSuccess { throw NotesError.message("Could not save securely.") }
    }
}

struct Contact: Decodable { let name: String; let phone: String }
struct CheckIn: Decodable { let id: String; let deadline: Double; let state: String }
struct Delivery: Decodable { let phone: String; let state: String; let attempts: Int }
struct AlertRecord: Decodable, Identifiable {
    let id: String; let kind: String; let created: Double; let deliveries: [Delivery]
}
struct ServerStatus: Decodable {
    let mode: String; let contacts: [Contact]; let timer: CheckIn?; let alerts: [AlertRecord]
}
struct CommandResult: Decodable { let id: String; let state: String; let deadline: Double? }
struct PendingRequest: Codable { let id: String; let path: String; let body: Data }
enum NotesError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let text) = self { return text }; return nil }
}
typealias HavenError = NotesError

@MainActor
final class SafetyModel: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var status: ServerStatus?
    @Published var busy = false
    @Published var message = "Connect your server to get started."
    @Published var locationText = "Location sharing is off"
    @Published var sharingLocation = false
    @Published var pending: PendingRequest?
    @Published var endpoint = UserDefaults.standard.string(forKey: "endpoint") ?? ""
    @Published var token = Vault.read("token") ?? ""
    @Published var consent = UserDefaults.standard.bool(forKey: "consent")
    private let locationManager = CLLocationManager()
    private var lastLocation: CLLocation?
    var activeTimer: CheckIn? { status?.timer?.state == "active" ? status?.timer : nil }

    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        if let raw = Vault.read("pending"), let data = raw.data(using: .utf8) {
            pending = try? JSONDecoder().decode(PendingRequest.self, from: data)
        }
    }

    func saveSettings() {
        guard pending == nil else { message = "Resolve the pending request before changing servers."; return }
        do {
            _ = try baseURL()
            guard token.count >= 32 else { throw HavenError.message("Enter the server's API token (at least 32 characters).") }
            try Vault.write("token", token)
            UserDefaults.standard.set(endpoint, forKey: "endpoint")
            UserDefaults.standard.set(consent, forKey: "consent")
            status = nil
            Task { await refresh() }
        } catch { message = error.localizedDescription }
    }

    private func baseURL() throws -> URL {
        guard let url = URL(string: endpoint), url.scheme == "https", url.host != nil,
              url.user == nil, url.password == nil, url.query == nil, url.fragment == nil,
              url.path.isEmpty || url.path == "/" else {
            throw HavenError.message("Use your server's HTTPS origin, e.g. https://safety.example.com")
        }
        return url
    }

    private func request(_ path: String, pending: PendingRequest? = nil) async throws -> Data {
        let url = try baseURL().appendingPathComponent(String(path.dropFirst()))
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if let pending {
            request.httpMethod = "POST"
            request.httpBody = pending.body
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue(pending.id, forHTTPHeaderField: "Idempotency-Key")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            let detail = (try? JSONSerialization.jsonObject(with: data)) as? [String: String]
            throw HavenError.message(detail?["error"] ?? "Server did not confirm the request.")
        }
        return data
    }

    func refresh() async {
        guard !endpoint.isEmpty, !token.isEmpty else { return }
        do {
            status = try JSONDecoder().decode(ServerStatus.self, from: await request("/v1/status"))
            if pending == nil { message = status?.mode == "demo" ? "Demo connected. No SMS messages will be sent." : "Server connected. SMS delivery is not guaranteed." }
        } catch { message = "Cannot reach server: \(error.localizedDescription)" }
    }

    func setLocation(_ enabled: Bool) {
        sharingLocation = enabled
        if enabled {
            locationText = "Requesting location…"
            switch locationManager.authorizationStatus {
            case .notDetermined: locationManager.requestWhenInUseAuthorization()
            case .authorizedAlways, .authorizedWhenInUse: locationManager.requestLocation()
            default: locationText = "Permission denied. Alerts will omit location."
            }
        } else { lastLocation = nil; locationText = "Location sharing is off" }
    }
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if sharingLocation { setLocation(true) }
    }
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard sharingLocation else { return }
        lastLocation = locations.last
        locationText = "Location available; shared only when you start an alert or timer."
    }
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        locationText = "Location unavailable. Alerts can still be sent."
    }
    private func locationPayload() -> [String: Double]? {
        guard sharingLocation, let location = lastLocation, location.horizontalAccuracy >= 0,
              abs(location.timestamp.timeIntervalSinceNow) < 90 else { return nil }
        return ["latitude": location.coordinate.latitude, "longitude": location.coordinate.longitude,
                "accuracy": location.horizontalAccuracy, "timestamp": location.timestamp.timeIntervalSince1970]
    }

    func send(_ path: String, minutes: Int? = nil) async {
        guard consent else { message = "First agree to sharing alerts with your chosen contacts in Setup."; return }
        guard !busy, pending == nil else { return }
        var body: [String: Any] = [:]
        if path == "/v1/checkins/complete" { body["id"] = activeTimer?.id }
        else if let location = locationPayload() { body["location"] = location }
        if let minutes { body["minutes"] = minutes }
        do {
            let new = PendingRequest(id: UUID().uuidString, path: path, body: try JSONSerialization.data(withJSONObject: body))
            try Vault.write("pending", String(decoding: JSONEncoder().encode(new), as: UTF8.self))
            pending = new
            await retry()
        } catch { message = error.localizedDescription }
    }

    func retry() async {
        guard let pending, !busy else { return }
        busy = true
        defer { busy = false }
        do {
            let result = try JSONDecoder().decode(CommandResult.self, from: await request(pending.path, pending: pending))
            try Vault.write("pending", "")
            self.pending = nil
            await refresh()
            if result.state == "active", let deadline = result.deadline {
                if deadline <= Date().timeIntervalSince1970 {
                    message = "This saved request was accepted earlier, but its deadline has passed. Review Circle for alerts and contact your trusted people directly."
                    return
                }
                message = "Server confirmed your check-in. It will alert contacts if you miss the deadline."
                if status?.mode == "demo" { message = "Demo timer started. Missed check-ins will NOT send SMS." }
                await scheduleReminder(deadline)
            } else if result.state == "expired" {
                message = "The deadline already passed. The alert cannot be recalled. Contact your trusted people directly."
            } else if result.state == "complete" {
                UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["checkin"])
                message = "Server confirmed your check-in is complete."
            } else { message = result.state == "demo" ? "Demo alert recorded. Nobody was contacted." : "Alert saved on server. Check activity for SMS submission status." }
        } catch {
            message = "Request not confirmed. It may have reached the server. Retry this request or refresh status. \(error.localizedDescription)"
        }
    }

    func clearPending() {
        do {
            try Vault.write("pending", "")
            pending = nil
            message = "Local retry cleared. This does not cancel anything already received by the server. Refresh status."
        } catch { message = error.localizedDescription }
    }

    private func scheduleReminder(_ deadline: Double) async {
        let center = UNUserNotificationCenter.current()
        do {
            guard try await center.requestAuthorization(options: [.alert, .sound]) else { return }
            let content = UNMutableNotificationContent()
            content.title = "Notes check-in"
            content.body = "Open Notes to complete your check-in."
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, deadline - Date().timeIntervalSince1970 - 30), repeats: false)
            try await center.add(UNNotificationRequest(identifier: "checkin", content: content, trigger: trigger))
        } catch { /* The server timer remains active if local reminders are unavailable. */ }
    }
}
