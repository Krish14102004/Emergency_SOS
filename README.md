# Emergency SOS Android prototype

An Android prototype demonstrating SOS session orchestration, real foreground location capture, local session history, and simulated per-contact alert statuses.

## Run

Open this folder in Android Studio with Android SDK 35 and JDK 17 installed, sync Gradle, and run the `app` configuration on an Android device or emulator. The app asks for foreground location permission only if you choose **START SOS · USE PHONE LOCATION**.

Use **START SOS · CONTINUE WITHOUT LOCATION** to demonstrate the unavailable-location path. Sessions and location updates are saved locally on the device; up to 20 sessions are retained.

## Safety and limitations

- The app can read and display actual device location while it is visible. Location collection stops when the app leaves the foreground and resumes when the app returns.
- Alert statuses remain simulation-only. The app sends no SMS or calls and does not contact a backend or emergency service.
- Location and session history are stored locally in the app's private preferences. There is no account, cloud sync, encryption key management, or remote sharing workflow.
- This is an academic prototype, not a production emergency service. Do not rely on it during an actual emergency.
- Authentication, verified contact management, real alert delivery, retries, background tracking, privacy controls, and backend authorization are not implemented.

See [ALGORITHM.md](./ALGORITHM.md) for the flow, state machine, location rules, and review checklist.
