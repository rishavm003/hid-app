import SwiftUI

struct ContentView: View {
    @EnvironmentObject var model: SafetyModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var tab = 0
    @State private var minutes = 15
    @State private var confirmSOS = false
    @State private var confirmClear = false
    private let ink = Color(red: 0.08, green: 0.19, blue: 0.20)

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        HStack {
                            Label("HAVEN", systemImage: "circle.hexagongrid.fill").font(.headline).tracking(3)
                            Spacer()
                            Text(model.status?.mode == "live" ? "LIVE PILOT" : "DEMO / NOT CONNECTED")
                                .font(.caption2.bold()).padding(8).background(.white.opacity(0.8), in: Capsule())
                        }
                        Text("A little closer.\nEven from afar.").font(.system(size: 36, weight: .semibold, design: .rounded))
                        Text("Your people, one alert away. Set a check-in before you head out.")
                            .foregroundStyle(.secondary)
                        VStack(spacing: 16) {
                            Image(systemName: "hand.raised.fill").font(.system(size: 36))
                            Text("Need your people?").font(.title2.bold())
                            Text("Send an SMS alert to your configured trusted contacts.").multilineTextAlignment(.center)
                            Button { confirmSOS = true } label: {
                                Text("Send SOS").font(.headline).frame(maxWidth: .infinity).padding(18)
                            }
                            .buttonStyle(.borderedProminent).tint(Color(red: 0.72, green: 0.23, blue: 0.17))
                            .disabled(model.busy || model.pending != nil || model.status == nil || !model.consent)
                            Text("Requires internet and a working SMS server. This does not call emergency services.")
                                .font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
                        }.padding(24).frame(maxWidth: .infinity).background(.white, in: RoundedRectangle(cornerRadius: 28))
                        VStack(alignment: .leading, spacing: 16) {
                            Label("Check-in timer", systemImage: "clock").font(.title3.bold())
                            if let timer = model.activeTimer {
                                Text("Check in by \(Date(timeIntervalSince1970: timer.deadline).formatted(date: .omitted, time: .shortened))")
                                Text(Date(timeIntervalSince1970: timer.deadline), style: .timer).font(.largeTitle.monospacedDigit())
                                Button("I’m safe — complete check-in") { Task { await model.send("/v1/checkins/complete") } }
                                    .buttonStyle(.borderedProminent)
                                    .disabled(model.busy || model.pending != nil)
                                Text("Closing or deleting Haven does not cancel this server timer.").font(.caption)
                            } else {
                                Picker("Duration", selection: $minutes) {
                                    ForEach([1, 5, 15, 30, 60], id: \.self) { Text("\($0) min").tag($0) }
                                }.pickerStyle(.segmented)
                                Button("Start check-in") { Task { await model.send("/v1/checkins", minutes: minutes) } }
                                    .buttonStyle(.borderedProminent)
                                    .disabled(model.busy || model.pending != nil || model.status == nil || !model.consent)
                            }
                        }.padding(24).frame(maxWidth: .infinity, alignment: .leading).background(.white, in: RoundedRectangle(cornerRadius: 28))
                        Toggle("Include current location", isOn: Binding(get: { model.sharingLocation }, set: { model.setLocation($0) }))
                        Text(model.locationText).font(.caption).foregroundStyle(.secondary)
                        statusPanel
                    }.padding(24)
                }
                .background(Color(red: 0.94, green: 0.96, blue: 0.92)).foregroundStyle(ink)
                .toolbar { Button("Refresh", systemImage: "arrow.clockwise") { Task { await model.refresh() } } }
            }.tabItem { Label("Home", systemImage: "house") }.tag(0)
            NavigationStack {
                List {
                    Section("Trusted contacts") {
                        if model.status == nil { Text("Connect a server in Setup to load your contacts.") }
                        ForEach(model.status?.contacts ?? [], id: \.phone) { contact in
                            VStack(alignment: .leading) { Text(contact.name).font(.headline); Text(contact.phone).foregroundStyle(.secondary) }
                        }
                        Text("Contacts are configured by your pilot server administrator. Review this list before enabling alerts.").font(.caption)
                    }
                    Section("Recent alerts") {
                        if model.status?.alerts.isEmpty != false { Text("No alerts loaded.") }
                        ForEach(model.status?.alerts ?? []) { alert in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(alert.kind == "sos" ? "SOS alert" : "Missed check-in").font(.headline)
                                Text(Date(timeIntervalSince1970: alert.created).formatted()).font(.caption)
                                ForEach(alert.deliveries, id: \.phone) { delivery in
                                    Text("\(delivery.phone): \(delivery.state.replacingOccurrences(of: "_", with: " "))").font(.caption)
                                }
                            }
                        }
                        Text("Provider accepted means the SMS service accepted the request. It does not confirm delivery or that someone has read it.").font(.caption)
                    }
                }.navigationTitle("Your circle")
                    .refreshable { await model.refresh() }
            }.tabItem { Label("Circle", systemImage: "person.2") }.tag(1)
            FamilyView().tabItem { Label("Family", systemImage: "location.circle") }.tag(3)
            NavigationStack {
                Form {
                    Section("Connect your pilot server") {
                        TextField("https://safety.example.com", text: $model.endpoint)
                            .textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                        SecureField("API token", text: $model.token).textInputAutocapitalization(.never).autocorrectionDisabled()
                        Toggle("I choose to share alerts and optional location with the contacts shown in Circle.", isOn: $model.consent)
                        Button("Save and connect") { model.saveSettings() }
                    }.disabled(model.pending != nil || model.busy)
                    Section("Discreet by your choice") {
                        Text("You can remove Haven from your Home Screen, or use iOS’s Hide and Require Face ID feature where available. It remains discoverable in system records. Hiding may affect notifications and shortcuts; test your exact setup.")
                        Text("The Home location toggle prepares a sample for an alert or timer. Family sharing is separate: it can send your location in the background only after you join and enable it in Family. You can stop sharing and erase shared data there. Haven does not automatically collect call history or record audio/video.")
                    }
                    Section("Before relying on this") {
                        Text("This is an unvalidated pilot. Practice with consenting contacts, verify live SMS delivery, and use your iPhone’s built-in Emergency SOS for emergency services. A server outage, dead battery, lost connection or SMS failure can prevent help from reaching you.")
                        Text("If a check-in has reached the server, it continues without the phone. If SOS has not reached the server, deleting the app cannot send it. Local reminders do not notify your contacts.")
                    }
                    Section { statusPanel }
                }.navigationTitle("Setup")
            }.tabItem { Label("Setup", systemImage: "slider.horizontal.3") }.tag(2)
        }.tint(ink)
        .confirmationDialog(model.status?.mode == "live" ? "Send an alert to your trusted contacts now?" : "Record a demo SOS? Nobody will be contacted.", isPresented: $confirmSOS, titleVisibility: .visible) {
            Button("Send SOS", role: .destructive) { Task { await model.send("/v1/sos") } }
        }
        .confirmationDialog("Clear the saved retry? This cannot cancel a request already received by the server.", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("Clear local retry", role: .destructive) { model.clearPending() }
        }
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(10))
                if !Task.isCancelled && scenePhase == .active && !model.busy { await model.refresh() }
            }
        }
    }

    private var statusPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(model.message, systemImage: model.pending == nil ? "info.circle" : "exclamationmark.triangle")
                .font(.footnote).accessibilityIdentifier("connectionStatus")
            if model.busy { ProgressView("Waiting for server confirmation…") }
            if model.pending != nil {
                Button("Retry same request") { Task { await model.retry() } }.disabled(model.busy)
                Button("Clear local retry…", role: .destructive) { confirmClear = true }.disabled(model.busy)
            }
        }
    }
}
