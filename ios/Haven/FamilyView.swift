import SwiftUI

struct FamilyView: View {
    @EnvironmentObject var family: FamilyModel
    @EnvironmentObject var safety: SafetyModel
    @State private var origin = ""
    @State private var code = ""
    @State private var name = ""
    @State private var setupCode = ""
    @State private var joinConsent = false
    @State private var enableConfirm = false
    @State private var eraseConfirm = false
    @State private var noteConfirm = false
    @State private var note = ""
    @State private var occurred = Date()
    @State private var noteID = UUID()
    @State private var newServer = ""
    @State private var reconnectConfirm = false

    var body: some View {
        NavigationStack {
            Form {
                if let session = family.session {
                    Section("Your family connection") {
                        LabeledContent("Controller", value: session.controller)
                        LabeledContent("Sharing", value: session.enabled ? "Enabled on this phone" : "Paused on this phone")
                        Text("The controller can see your last uploaded coordinates, capture time, accuracy and notes you explicitly share.")
                        if let date = family.lastUpload {
                            Text("Last confirmed upload: \(date.formatted())")
                        }
                        Text(family.permissionText).font(.footnote)
                        if session.enabled {
                            Button("Improve background sharing") { family.requestBackgroundPermission() }
                            Button("Share my location now") { family.shareNow() }
                            Button("Stop location sharing", role: .destructive) { Task { await family.stop() } }
                        } else {
                            Button("Enable hourly location sharing…") { enableConfirm = true }.disabled(family.busy)
                        }
                        Text("With Always permission, Notes can use movement-based recovery and scheduled refresh. Uploads still depend on iOS and may be delayed, especially while stationary. Force-quitting, an expired installation, no internet or a powered-off phone can stop sharing. Reopen Notes after refreshing its installation.").font(.footnote)
                    }
                    Section("Use this connection for SOS") {
                        Text("Each family member has separate SOS and check-in records. Do not share one person's old API token across family phones.")
                        Button("Connect Home and Circle to my family account") {
                            safety.endpoint = session.origin
                            safety.token = session.token
                            safety.consent = false
                            safety.saveSettings()
                        }.disabled(safety.pending != nil || safety.activeTimer != nil || safety.busy)
                        Text("Then review the contacts in Circle and enable alert consent in Setup. Finish any existing check-in first.").font(.caption)
                    }
                    Section("Share a call note — optional") {
                        Text("iOS does not expose the Phone app's complete call history to Notes. This form shares only what you type; it does not collect call logs or record audio.")
                        DatePicker("Call time", selection: $occurred, in: Date().addingTimeInterval(-30*86400)...Date())
                        TextField("Details you choose to share", text: $note, axis: .vertical).lineLimit(3...6)
                        Text("\(note.count)/1000 characters").font(.caption)
                        Button("Review and share note…") { noteConfirm = true }
                            .disabled(family.busy || note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || note.count > 1000)
                        Text("Notes are visible to your controller and retained for up to 30 days. Erase shared data below to remove them sooner.").font(.caption)
                    }
                    Section("Your data") {
                        Button("Erase shared family location and notes…", role: .destructive) { eraseConfirm = true }.disabled(family.busy)
                        Text("Pausing keeps the last shared data visible. Erasing removes the family location and notes, but cannot retract screenshots, SMS messages or separate SOS records.").font(.caption)
                    }
                    Section("Reconnect after PC restart") {
                        Text("Free test addresses can change. Stop sharing, then paste the new address copied from your own controller. Your account and last location stay on the PC.").font(.footnote)
                        TextField("New HTTPS server address", text: $newServer).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                        Button("Reconnect to my controller…") { reconnectConfirm = true }.disabled(family.busy || session.enabled || newServer.isEmpty)
                    }
                } else {
                    Section("Join your family") {
                        TextField("Paste invitation from your controller", text: $setupCode).textInputAutocapitalization(.never).autocorrectionDisabled()
                        Button("Use invitation") {
                            let text = setupCode.trimmingCharacters(in: .whitespacesAndNewlines)
                            let prefix = text.hasPrefix("NOTES1.") ? "NOTES1." : (text.hasPrefix("HAVEN1.") ? "HAVEN1." : "")
                            if !prefix.isEmpty,
                               let data = Data(base64Encoded: String(text.dropFirst(prefix.count))),
                               let invite = try? JSONDecoder().decode([String: String].self, from: data),
                               let address = invite["origin"], let invitation = invite["code"] {
                                origin = address; code = invitation; setupCode = ""
                            } else { family.message = "That invitation is incomplete. Copy the full invitation from your controller." }
                        }
                        TextField("HTTPS server origin", text: $origin).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                        SecureField("One-use invitation code", text: $code).textInputAutocapitalization(.never).autocorrectionDisabled()
                        TextField("Your display name", text: $name)
                        Button("Review invitation") { Task { await family.preview(origin: origin, code: code) } }.disabled(family.busy)
                        if let controller = family.reviewedController {
                            Text("Controller: \(controller)").font(.headline)
                            Toggle("I know this controller and choose to join. Location remains off until I enable it.", isOn: $joinConsent)
                            Button("Accept and join") { Task { await family.join(origin: origin, code: code, name: name) } }
                                .disabled(!joinConsent || family.busy || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        }
                    }
                }
                Section("Status") {
                    Text(family.message)
                    if family.busy { ProgressView() }
                    Button("Refresh family connection") { Task { await family.reconcile() } }.disabled(family.busy)
                }
                Section("Hide the icon after testing") {
                    Text("If your installed app offers Hide and Require Face ID, use that iOS menu after verifying location sharing. Sideloaded builds may not offer it. Remove from Home Screen alone leaves the app in App Library. Hiding does not make it uninstallable.")
                    Button("Open Notes’ iOS Settings") { family.openSettings() }
                }
            }.navigationTitle("Family sharing")
        }
        .onChange(of: note) { _, _ in noteID = UUID() }
        .onChange(of: occurred) { _, _ in noteID = UUID() }
        .onChange(of: origin) { _, _ in joinConsent = false; family.reviewedController = nil }
        .onChange(of: code) { _, _ in joinConsent = false; family.reviewedController = nil }
        .confirmationDialog("Share your location with \(family.session?.controller ?? "your controller") approximately hourly, including in the background?", isPresented: $enableConfirm, titleVisibility: .visible) {
            Button("Enable sharing") { Task { await family.enable() } }
        }
        .confirmationDialog("Stop sharing and erase your last family location and all manual notes?", isPresented: $eraseConfirm, titleVisibility: .visible) {
            Button("Erase shared data", role: .destructive) { Task { await family.erase() } }
        }
        .confirmationDialog("Only continue if this address came from your own controller. Your member credential will be sent to it to reconnect.", isPresented: $reconnectConfirm, titleVisibility: .visible) {
            Button("Reconnect") { Task { await family.reconnect(origin: newServer.trimmingCharacters(in: .whitespacesAndNewlines)) } }
        }
        .confirmationDialog("Share this manually entered call note with \(family.session?.controller ?? "your controller")?", isPresented: $noteConfirm, titleVisibility: .visible) {
            Button("Share note") { Task {
                if await family.shareNote(text: note, occurred: occurred, id: noteID) { note = ""; noteID = UUID() }
            } }
        }
    }
}
