# Emergency SOS Android prototype

An offline Android demo of the SOS event flow for a zeroth review. It demonstrates event creation, duplicate-session prevention, independent location and per-contact alert processing, active tracking updates, and session closure.

## Run

Open this folder in Android Studio with Android SDK 35 and JDK 17 installed, allow Gradle sync to complete, and run the `app` configuration on an emulator or device. No runtime permissions or real contacts are required.

On the home screen, choose **START · LOCATION AVAILABLE** or **START · NO LOCATION**, then observe the event state, alert statuses, location result, and activity timeline. End the session and start again to demonstrate a new event.

## Demo safety and limitations

- The app is simulation-only: it sends no SMS, places no calls, reads no device location, and makes no network requests.
- The displayed contacts and coordinates are sample data. Alert status is explicitly marked simulated and is not proof of real submission or delivery.
- This is an academic prototype, not a production emergency service. Do not rely on it during an actual emergency.
- Authentication, verified contacts, persistence, real location, notification delivery, retries, background tracking, privacy controls, and backend integration are not implemented.

See [ALGORITHM.md](./ALGORITHM.md) for the flow, state machine, and review checklist.
