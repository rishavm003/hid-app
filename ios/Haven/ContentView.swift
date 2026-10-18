import SwiftUI

struct DailyNote: Identifiable, Codable {
    var id: UUID = UUID()
    var title: String
    var content: String
    var date: Date = Date()
}

struct ContentView: View {
    @EnvironmentObject var model: SafetyModel
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("haven_decoy_pin") private var decoyPin = "1998"
    @State private var isUnlocked = false
    @State private var tab = 0
    @State private var minutes = 15
    @State private var confirmSOS = false
    @State private var confirmClear = false
    private let ink = Color(red: 0.08, green: 0.19, blue: 0.20)

    var body: some View {
        Group {
            if isUnlocked {
                notesInterface
            } else {
                NotesDecoyView(secretPin: decoyPin) {
                    withAnimation(.easeInOut(duration: 0.25)) {
                        isUnlocked = true
                    }
                }
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active {
                // Instantly lock back to Notes when app is minimized or phone locked
                isUnlocked = false
            }
        }
    }

    private var notesInterface: some View {
        TabView(selection: $tab) {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        HStack {
                            Label("NOTES", systemImage: "note.text").font(.headline).tracking(3)
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
                                Text("Closing or deleting Notes does not cancel this server timer.").font(.caption)
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
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button {
                            withAnimation { isUnlocked = false }
                        } label: {
                            Label("Notes", systemImage: "note.text")
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Refresh", systemImage: "arrow.clockwise") { Task { await model.refresh() } }
                    }
                }
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
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button {
                                withAnimation { isUnlocked = false }
                            } label: {
                                Label("Notes", systemImage: "note.text")
                            }
                        }
                    }
            }.tabItem { Label("Circle", systemImage: "person.2") }.tag(1)
            FamilyView().tabItem { Label("Family", systemImage: "location.circle") }.tag(3)
            NavigationStack {
                Form {
                    Section("Disguise & Security") {
                        HStack {
                            Text("Unlock PIN")
                            Spacer()
                            TextField("4-digit PIN", text: $decoyPin)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 80)
                        }
                        Text("When Notes is shown, typing this PIN into the search bar or long-pressing the header title reveals controls.")
                            .font(.caption).foregroundStyle(.secondary)
                        Button("Switch to Notes view now") {
                            withAnimation { isUnlocked = false }
                        }
                    }
                    Section("Connect your pilot server") {
                        TextField("https://safety.example.com", text: $model.endpoint)
                            .textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                        SecureField("API token", text: $model.token).textInputAutocapitalization(.never).autocorrectionDisabled()
                        Toggle("I choose to share alerts and optional location with the contacts shown in Circle.", isOn: $model.consent)
                        Button("Save and connect") { model.saveSettings() }
                    }.disabled(model.pending != nil || model.busy)
                    Section("Discreet by your choice") {
                        Text("You can remove Notes from your Home Screen, or use iOS’s Hide and Require Face ID feature where available. It remains discoverable in system records. Hiding may affect notifications and shortcuts; test your exact setup.")
                        Text("The Home location toggle prepares a sample for an alert or timer. Family sharing is separate: it can send your location in the background only after you join and enable it in Family. You can stop sharing and erase shared data there. Notes does not automatically collect call history or record audio/video.")
                    }
                    Section("Before relying on this") {
                        Text("This is an unvalidated pilot. Practice with consenting contacts, verify live SMS delivery, and use your iPhone’s built-in Emergency SOS for emergency services. A server outage, dead battery, lost connection or SMS failure can prevent help from reaching you.")
                        Text("If a check-in has reached the server, it continues without the phone. If SOS has not reached the server, deleting the app cannot send it. Local reminders do not notify your contacts.")
                    }
                    Section { statusPanel }
                }.navigationTitle("Setup")
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button {
                                withAnimation { isUnlocked = false }
                            } label: {
                                Label("Notes", systemImage: "note.text")
                            }
                        }
                    }
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

// MARK: - Notes Decoy Screen

struct NotesDecoyView: View {
    let secretPin: String
    let onUnlock: () -> Void

    @AppStorage("notes_decoy_notes_json") private var notesJson: String = ""
    @State private var notes: [DailyNote] = []
    @State private var searchText = ""
    @State private var selectedNote: DailyNote?
    @State private var isCreatingNote = false
    @State private var showPinSheet = false

    var filteredNotes: [DailyNote] {
        if searchText.trimmingCharacters(in: .whitespaces).isEmpty {
            return notes
        }
        return notes.filter {
            $0.title.localizedCaseInsensitiveContains(searchText) ||
            $0.content.localizedCaseInsensitiveContains(searchText)
        }
    }

    var body: some View {
        NavigationStack {
            List {
                ForEach(filteredNotes) { note in
                    Button {
                        selectedNote = note
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(note.title.isEmpty ? "Untitled Note" : note.title)
                                .font(.headline)
                                .foregroundStyle(.primary)
                            HStack {
                                Text(note.date.formatted(date: .abbreviated, time: .omitted))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Text(note.content)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }
                .onDelete(perform: deleteNotes)
            }
            .listStyle(.insetGrouped)
            .searchable(text: $searchText, prompt: "Search notes")
            .onChange(of: searchText) { _, newValue in
                // Discreet trigger: Typing the secret PIN into search bar reveals controls immediately
                if newValue.trimmingCharacters(in: .whitespaces) == secretPin {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    searchText = ""
                    onUnlock()
                }
            }
            .navigationTitle("Notes")
            .toolbar {
                ToolbarItem(placement: .principal) {
                    // Discreet trigger: Long pressing the header title for 1.2s opens the PIN pad
                    Text("Notes")
                        .font(.headline)
                        .onLongPressGesture(minimumDuration: 1.2) {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            showPinSheet = true
                        }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        isCreatingNote = true
                    } label: {
                        Image(systemName: "square.and.pencil")
                    }
                }
            }
            .sheet(item: $selectedNote) { note in
                NoteDetailView(note: note) { updated in
                    if let index = notes.firstIndex(where: { $0.id == updated.id }) {
                        notes[index] = updated
                        saveNotes()
                    }
                }
            }
            .sheet(isPresented: $isCreatingNote) {
                NoteDetailView(note: DailyNote(title: "", content: "")) { newNote in
                    if !newNote.title.isEmpty || !newNote.content.isEmpty {
                        notes.insert(newNote, at: 0)
                        saveNotes()
                    }
                }
            }
            .sheet(isPresented: $showPinSheet) {
                PinEntrySheet(targetPin: secretPin) {
                    showPinSheet = false
                    onUnlock()
                }
            }
            .onAppear(perform: loadNotes)
        }
    }

    private func loadNotes() {
        if !notesJson.isEmpty, let data = notesJson.data(using: .utf8),
           let decoded = try? JSONDecoder().decode([DailyNote].self, from: data) {
            notes = decoded
        } else {
            // Seed realistic everyday notes if empty
            notes = [
                DailyNote(
                    title: "Grocery & pantry list",
                    content: "Almond milk, organic eggs, avocados, baby spinach, green apples, whole wheat bread, dark roast beans."
                ),
                DailyNote(
                    title: "Books to finish",
                    content: "Atomic Habits (chapter 6), Deep Work, The Psychology of Money."
                ),
                DailyNote(
                    title: "Weekend plans",
                    content: "Morning run in park, laundry, call mom, clean kitchen, prepare presentation slides."
                )
            ]
            saveNotes()
        }
    }

    private func saveNotes() {
        if let data = try? JSONEncoder().encode(notes), let json = String(data: data, encoding: .utf8) {
            notesJson = json
        }
    }

    private func deleteNotes(at offsets: IndexSet) {
        notes.remove(atOffsets: offsets)
        saveNotes()
    }
}

// MARK: - Note Viewer & Editor

struct NoteDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @State var note: DailyNote
    let onSave: (DailyNote) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Title", text: $note.title)
                        .font(.headline)
                    TextEditor(text: $note.content)
                        .frame(minHeight: 200)
                }
            }
            .navigationTitle(note.title.isEmpty ? "New Note" : note.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        note.date = Date()
                        onSave(note)
                        dismiss()
                    }
                    .bold()
                }
            }
        }
    }
}

// MARK: - Discreet PIN Sheet

struct PinEntrySheet: View {
    let targetPin: String
    let onSuccess: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var input = ""
    @State private var isError = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 32) {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 38))
                        .foregroundStyle(isError ? .red : .secondary)
                    Text("Private Notebook")
                        .font(.title3.bold())
                    Text("Enter 4-digit passcode")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                // 4 PIN indicator dots
                HStack(spacing: 18) {
                    ForEach(0..<4, id: \.self) { index in
                        Circle()
                            .fill(index < input.count ? (isError ? Color.red : Color.primary) : Color.secondary.opacity(0.2))
                            .frame(width: 16, height: 16)
                    }
                }
                .padding(.vertical, 8)

                // Numeric keypad
                VStack(spacing: 16) {
                    ForEach([[1, 2, 3], [4, 5, 6], [7, 8, 9]], id: \.self) { row in
                        HStack(spacing: 28) {
                            ForEach(row, id: \.self) { num in
                                keypadButton("\(num)")
                            }
                        }
                    }
                    HStack(spacing: 28) {
                        Button {
                            dismiss()
                        } label: {
                            Text("Cancel")
                                .font(.subheadline)
                                .frame(width: 72, height: 72)
                                .foregroundStyle(.secondary)
                        }
                        keypadButton("0")
                        Button {
                            if !input.isEmpty {
                                input.removeLast()
                                isError = false
                            }
                        } label: {
                            Image(systemName: "delete.left")
                                .font(.title2)
                                .frame(width: 72, height: 72)
                                .foregroundStyle(.primary)
                        }
                    }
                }

                Spacer()
            }
            .padding()
        }
    }

    private func keypadButton(_ digit: String) -> some View {
        Button {
            handleDigit(digit)
        } label: {
            Text(digit)
                .font(.title.weight(.medium))
                .frame(width: 72, height: 72)
                .background(Color(.systemGray6))
                .clipShape(Circle())
                .foregroundStyle(.primary)
        }
    }

    private func handleDigit(_ digit: String) {
        guard input.count < 4 else { return }
        input.append(digit)
        isError = false
        if input.count == 4 {
            if input == targetPin {
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                onSuccess()
            } else {
                UINotificationFeedbackGenerator().notificationOccurred(.error)
                isError = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                    input = ""
                    isError = false
                }
            }
        }
    }
}
