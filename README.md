# Emergency SOS Android prototype

An Android prototype demonstrating SOS session orchestration, real foreground location capture, local session history, and user-reviewed SMS drafts.

## Run

Open this folder in Android Studio with Android SDK 35 and JDK 17 installed, sync Gradle, and run the `app` configuration on an Android phone. Enter 1-5 unique phone numbers with country code. Before starting an SOS, the app displays the exact recipient list and asks for confirmation.

The app does not request SMS permission and does not send SMS itself. For each confirmed recipient, tap **Open SMS app**, review the addressed draft, then tap **Send** in your SMS app. The prototype can record that it opened a composer, but cannot tell whether you sent the message or whether it was delivered. Test only with numbers you own or whose recipients have agreed to receive a test; carrier charges may apply.

Choose whether to use phone location or proceed without it. Location permission is requested only for the location-enabled option. A location fix is captured and displayed independently of SMS. Sessions and status history are saved locally on the device; up to 20 sessions are retained.

## Safety and limitations

- The SMS is prepared for the user to review and send. It does not include GPS coordinates and asks the recipient to contact the sender. The app cannot verify sending, delivery, or reading.
- The SMS-app handoff uses Android's `ACTION_SENDTO` with an `smsto:` address. The app has no `SEND_SMS` permission, does not call `SmsManager`, and cannot silently send messages.
- This prototype does not call emergency services or contact a backend. Do not rely on it during an actual emergency.
- The app can read and display actual device location while it is visible. Location collection stops when the app leaves the foreground and resumes when the app returns and permission remains granted.
- Location, phone numbers, and session history are stored locally in the app's private preferences. There is no account, cloud sync, encryption key management, or remote sharing workflow.
- Authentication, verified contact management, background tracking, privacy controls, and backend authorization are not implemented.

See [ALGORITHM.md](./ALGORITHM.md) for the flow, state machine, location rules, and review checklist.
