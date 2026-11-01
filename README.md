# Emergency SOS Android prototype

An Android prototype demonstrating SOS session orchestration, real foreground location capture, local session history, and direct SMS alert attempts with per-contact status callbacks.

## Run

Open this folder in Android Studio with Android SDK 35 and JDK 17 installed, sync Gradle, and run the `app` configuration on an Android phone with a SIM and SMS service. Enter phone numbers with country code. Before every SOS, the app displays the exact recipient list and asks for confirmation. Android's SMS permission is required for direct sending; location permission is requested only for the location-enabled option.

Choose whether to use phone location or send without location. The SOS text is dispatched immediately and does not include coordinates. Location capture proceeds independently and remains visible in the app. SMS submission and carrier delivery callbacks are tracked separately for each number; callback availability depends on the device and carrier. Sessions and status history are saved locally on the device; up to 20 sessions are retained.

## Safety and limitations

- This version sends a real SMS to each confirmed number. Test only with numbers you own or whose recipients have agreed to receive a test; carrier charges may apply. It does not call emergency services or contact a backend.
- The SMS does not include GPS coordinates. It asks the recipient to contact the sender. Location is only displayed and stored on the phone.
- `SUBMITTED` means Android/the carrier accepted a send attempt, not that a person received or read it. `DELIVERED` is shown only when the device/carrier supplies a delivery callback. A missing callback is not reported as success.
- Android, carrier, SIM, regional, and app-distribution policies can restrict `SEND_SMS`. This educational direct-SMS prototype is intended for local testing, not Play Store distribution or reliance in an emergency.
- The app can read and display actual device location while it is visible. Location collection stops when the app leaves the foreground and resumes when the app returns.
- Location, phone numbers, and session history are stored locally in the app's private preferences. There is no account, cloud sync, encryption key management, or remote sharing workflow.
- This is an academic prototype, not a production emergency service. Do not rely on it during an actual emergency.
- Authentication, verified contact management, bounded SMS retry, background tracking, privacy controls, and backend authorization are not implemented. Failed sends are reported but not retried automatically.

See [ALGORITHM.md](./ALGORITHM.md) for the flow, state machine, location rules, and review checklist.
