# Emergency SOS algorithm prototype

## Purpose and scope

This prototype demonstrates SOS orchestration with actual foreground device location, locally persisted session history, and SMS drafts handed off to the user's SMS app. It does not send SMS itself, place calls, contact a backend, or notify emergency services.

## System architecture

The prototype uses a small, single-process Android architecture. The Activity is the presentation and platform-permission boundary; a coordinator owns the SOS workflow; model classes represent session state; and narrow helper classes adapt Android location, SMS-composer, and local-storage APIs. No server or custom cellular-network component is part of the application.

### Component diagram

```text
┌─────────────────────────────────────────────────────────────┐
│ Android application                                         │
│                                                             │
│  ┌────────────────────────┐       ┌──────────────────────┐ │
│  │ MainActivity            │──────▶│ EmergencyCoordinator │ │
│  │ UI, confirmation,      │       │ session orchestration│ │
│  │ location permission,   │◀──────│ and UI notifications │ │
│  │ status/history display │       └──────────┬───────────┘ │
│  └────────────────────────┘                  │             │
│                           ┌───────────────────┼───────────┐ │
│                           ▼                   ▼           ▼ │
│                 ┌────────────────┐  ┌──────────────┐ ┌────────────┐
│                 │ LocationTracker│  │SmsAlert      │ │SessionStore│
│                 │ current fix and│  │Dispatcher    │ │JSON history│
│                 │ foreground feed│  │open composer │ │in private  │
│                 └───────┬────────┘  └──────┬───────┘ │preferences│
│                         │                  │         └─────┬──────┘
└─────────────────────────┼──────────────────┼───────────────┼──────┘
                          ▼                  ▼               ▼
             Android fused-location     Android SMS     Android app-private
             provider / location APIs   application     SharedPreferences
                                             │
                                             ▼
                                  User reviews and taps Send
                                             │
                                             ▼
                                  Android telephony + carrier
                                  (outside SOS app control)

 PhoneNumberValidator ── validates/normalizes recipient input
 EmergencyModels      ── shared session, location, and alert data
```

### Component responsibilities

| Component | Responsibility | Boundary |
| --- | --- | --- |
| `MainActivity` | Builds the screen, collects numbers and location choice, confirms destinations, requests location permission when needed, and renders session/history/status updates. | UI and Android Activity lifecycle; it does not persist session JSON or access carrier delivery status. |
| `PhoneNumberValidator` | Removes supported formatting, normalizes `00` to `+`, enforces international-number shape, and requires 1-5 unique recipients. | Input validation only; it does not verify that a number exists or belongs to the intended person. |
| `EmergencyCoordinator` | Applies duplicate-session guards, creates and transitions events, coordinates location and SMS-draft actions, updates activity messages, and asks the store to persist changes. | Workflow/orchestration layer; it does not implement GPS hardware, SMS transport, or remote services. |
| `EmergencyModels` | Defines `EmergencyEvent`, `AlertAttempt`, `LocationSnapshot`, and their state enums. | In-memory domain data shared by the Activity, coordinator, adapters, and store. |
| `LocationTracker` | Adapts Android's fused location provider for a one-time current fix and periodic foreground updates; supports cancellation and stopping. | Requires the Activity-granted location permission; it returns location data but does not choose whether a fix is acceptable. |
| Location validation in `EmergencyCoordinator` | Checks coordinate ranges, fix age, accuracy, and timestamp order before accepting or storing a fix. | Application-level quality policy; it cannot guarantee satellite visibility or a precise position. |
| `SmsAlertDispatcher` | Opens an addressed SMS draft with `ACTION_SENDTO` and an `smsto:` URI, including the prepared body. | Handoff only; it does not call `SmsManager`, request `SEND_SMS`, or know whether the user sent the draft. |
| `SessionStore` | Encodes/decodes bounded event history as JSON in app-private `SharedPreferences` and reports storage errors. | On-device persistence; no cloud sync or separate encryption layer is implemented. |
| Android SMS app, telephony, modem, and carrier | Present the message to the user and, after the user taps Send, attempt mobile-network transport. | External platform/network components; sending and delivery are not observable by this prototype. |

### Runtime flow between components

1. `MainActivity` reads the entered recipients and calls `PhoneNumberValidator`. Invalid input is reported before a session is created.
2. The Activity shows the normalized recipient list for explicit confirmation. It requests foreground location permission only if the location-enabled action was chosen; there is no SMS permission request.
3. The Activity calls `EmergencyCoordinator.startSos`. The coordinator rejects a duplicate active session, creates an `EmergencyEvent` with one `READY_TO_COMPOSE` alert per recipient, and persists the session through `SessionStore`.
4. If location is enabled and permission is available, the coordinator asks `LocationTracker` for a current fix and starts a five-second deadline. It validates returned fixes before updating the event. Without permission or a usable fix, the session continues with location unavailable.
5. `MainActivity` renders snapshots supplied by the coordinator. The coordinator moves the session from `ALERTING` to `ACTIVE` after initial location resolution; location and alert statuses remain separate from the session state.
6. When the user taps **OPEN SMS APP** for a recipient, the Activity calls `EmergencyCoordinator.openSmsComposer`. The coordinator asks `SmsAlertDispatcher` to launch the system composer. On success it records `COMPOSER_OPENED`; on launch failure it records `COMPOSER_FAILED`. Both results are saved locally and rendered in the UI.
7. The user reviews and sends (or cancels) the draft inside the SMS application. There is intentionally no callback into the SOS app for send or delivery status.
8. Activity lifecycle callbacks tell the coordinator when the app is visible. The coordinator pauses location updates when the app leaves the foreground and resumes them, if permitted, when it returns.
9. On session end, the coordinator stops location work, marks the event `CLOSED`, and persists it. The bounded local history retains up to 20 events.

### Architectural decisions and trade-offs

- **Separation of concerns:** UI, orchestration, platform adapters, domain state, and persistence have distinct responsibilities, making the flow easier to explain and test.
- **Local-first operation:** the prototype needs no account or backend. Session history is stored on the device, so it remains available after restart but is not synchronized to another device.
- **Explicit SMS user control:** opening the system composer avoids direct-send permission and lets the user inspect each recipient and message. The trade-off is that the app cannot automatically send or confirm delivery.
- **Foreground-only location:** this limits collection to periods when the app is visible and avoids implementing background location service behavior. Updates may pause when the Activity stops.
- **Independent status dimensions:** the event lifecycle, location availability, and each recipient's draft state are stored separately. A failed location request does not imply SMS failure, and opening a composer does not make the session active or prove delivery.
- **No network acknowledgement in the app:** any SMS delivery acknowledgement belongs to Android/operator messaging paths and is not exposed to this draft-handoff flow. The UI therefore reports only what the app itself observed.

## Main flow

```text
CONFIRM SOS(phone number list, location choice)
  1. If a session is already active, ignore the duplicate request.
  2. Normalize and validate 1-5 unique phone numbers.
  3. Show the exact destination numbers and require explicit confirmation.
  4. Request foreground location permission only if the user selected location.
  5. Create a unique event, persist each recipient, and move to ALERTING.
  6. Mark each SMS as READY_TO_COMPOSE. No SMS is sent automatically.
  7. If location was requested and granted, obtain a current location
     with a five-second deadline; otherwise mark it unavailable.
  8. If a usable fix arrives, record coordinates, reported accuracy, and timestamp.
  9. Mark the session ACTIVE when location resolution finishes.
 10. When the user taps OPEN SMS APP, launch the system SMS composer for one
     recipient with a prepared message. The user must review and tap Send.
 11. Record COMPOSER_OPENED only; never infer that the user sent or delivered it.
 12. While the app is visible, save valid location updates about every 10 seconds.
 13. When the app leaves the foreground, stop location updates.
     Resume them when the app returns and permission is still granted.
 14. On END SESSION, stop location updates and save the CLOSED state.
END
```

## Session state machine

| Current state | Event | Next state |
| --- | --- | --- |
| IDLE or CLOSED | Valid, confirmed SOS request | INITIATED |
| INITIATED | Session begins | ALERTING |
| ALERTING | Location resolution finishes | ACTIVE |
| ACTIVE | User ends the session | CLOSED |
| Any non-closed state | Duplicate SOS request | No state change |

Location availability and per-recipient SMS draft status are recorded separately from the session state. An unavailable location does not prevent the session from becoming active. SMS draft opening is a user action and does not change the session state.

## SMS draft flow

- The user confirms the exact phone numbers before the app creates a session. SMS permission is never requested.
- Phone numbers are normalized and limited to 1-5 unique entries per SOS.
- Tap **OPEN SMS APP** beside a recipient to open an addressed draft. The user must review the message and tap **Send** in the SMS app.
- The message does not contain GPS coordinates. No location-follow-up message is created.
- `READY_TO_COMPOSE`: a draft can be opened; no SMS has been sent.
- `COMPOSER_OPENED`: the phone's SMS composer was launched. It does not mean the user sent the message.
- `COMPOSER_FAILED`: the SMS composer could not be opened; the app allows trying again.
- The app has no SMS sent/delivery callback and makes no claim that a message was submitted, delivered, or read.
- Reopening a draft may lead to duplicate messages if an earlier draft was sent.

## Wireless mobile communication: how this algorithm fits

This project is an application-layer prototype. It demonstrates how a mobile app can prepare an emergency notification, but it does not implement a cellular radio, GSM/LTE/5G protocol, base station, or SMS network service. Android and the mobile operator perform those communication functions.

### Mapping to subject concepts

| Wireless/mobile communication concept | Correspondence in this project |
| --- | --- |
| Mobile station / user equipment (MS/UE) | The user's Android phone, its operating system, and the SOS app. |
| Application layer / service request | The app validates recipients, creates the event, and asks Android to open an addressed SMS draft. |
| Human-in-the-loop control | The user reviews the message and taps **Send** in the SMS app. The SOS app itself cannot transmit it. |
| Radio access network (RAN) | The phone's cellular modem communicates with the operator's radio access network when the user sends the SMS. Depending on the network, this can involve a GSM/UMTS base station subsystem or LTE/5G radio access equipment. |
| Core network and SMS service | The operator routes the SMS through its core/SMS infrastructure. In a classic GSM explanation, the mobile station communicates through a BTS/BSC and MSC to an SMSC; newer LTE/5G networks can use IMS or interworking paths. |
| Store-and-forward | SMS infrastructure can store and forward a message when the destination is temporarily unavailable, subject to operator behavior and message validity. This project does not control that process. |
| Acknowledgement / delivery status | The app records only that the composer opened. It does not receive a send or delivery report, so its status cannot be interpreted as a network acknowledgement. |
| GNSS/GPS positioning | The phone's receiver obtains satellite navigation signals and the Android location service provides a position estimate. In this prototype, coordinates are displayed and stored locally; they are not attached to the SMS or uploaded by the app. |
| Mobility and changing radio conditions | The phone can move between coverage areas while the app runs. The operator manages registration, handover, and radio-link procedures; the app only accepts fresh, sufficiently accurate location fixes while visible. |

### Conceptual communication path

```text
SOS app
  └─ validates number, prepares text, opens SMS composer (ACTION_SENDTO)
       └─ user reviews and taps Send
            └─ Android SMS app / telephony framework
                 └─ handset modem and cellular radio link
                      └─ operator radio access network
                           └─ operator core and SMS service/SMSC
                                └─ recipient's operator/network
                                     └─ recipient handset
```

The diagram is a conceptual path, not a guarantee of one exact network architecture. The SMS app and operator select the actual route based on the handset, subscription, coverage, and network technology. An SMSC is a useful teaching model for store-and-forward SMS; LTE/5G may use IP Multimedia Subsystem (IMS) or interworking with legacy SMS infrastructure.

The SOS app and the recipient do not exchange a continuous connection. The app hands a draft to the SMS application; after the user sends it, delivery depends on the phone, SIM/subscription, radio coverage, operator routing, and recipient availability. Because the SOS app does not request SMS privileges or use send/delivery callbacks, it cannot observe those network stages.

GPS/GNSS is a separate receive-only positioning path for this prototype:

```text
Navigation satellites --one-way radio signals--> phone GNSS receiver
phone location service --> SOS app --> local display and session history
```

The app does not transmit the coordinates through SMS. A satellite fix can be delayed or unavailable indoors, which is why the algorithm has a five-second initial-location deadline and continues the session with location marked unavailable if no acceptable fix arrives.

### Algorithm in communication-system terms

1. **Service preparation:** validate and normalize the destination identifiers (phone numbers), obtain confirmation, and create an event record.
2. **Optional positioning:** request a foreground position estimate from Android. Check its age, coordinate range, and reported accuracy before accepting it.
3. **Application-to-OS handoff:** create one addressed SMS draft per confirmed recipient. This is a local inter-application handoff; it is not a radio transmission.
4. **User authorization to transmit:** the user inspects the destination and payload and taps **Send** in the messaging application.
5. **Network delivery outside the app:** Android telephony and the operator handle radio access, routing, SMS service/store-and-forward, and any network-side status.
6. **Honest status reporting:** keep `READY_TO_COMPOSE`, `COMPOSER_OPENED`, and `COMPOSER_FAILED` distinct. Do not label a composer launch as SMS submission, network delivery, or recipient reading.
7. **Session and mobility handling:** while the app is visible, collect valid location updates at the configured interval; pause tracking when it leaves the foreground and resume on return if permission remains granted.

## Location validation and storage

- Location permission is requested only when the user chooses the location-enabled start action.
- Current-location acquisition has a five-second deadline. It accepts a fix no older than 60 seconds and with reported accuracy of 100 m or better.
- A fix no older than 30 seconds with accuracy of 50 m or better is reported as preferred quality; other accepted fixes are shown as lower confidence.
- Live updates request a 10-second interval and reject invalid coordinates, stale fixes, poor accuracy, and out-of-order timestamps.
- The app requests foreground-only permission. Updates stop when the app is not visible.
- Up to 20 events are stored in the app's private SharedPreferences, including recipient phone numbers, alert statuses, activity timeline, and last location.
- A unique event ID is generated for each session; only one session may be active at a time.

## Review checklist

1. Enter a test number you own and confirm the exact destination in the confirmation dialog.
2. Start an SOS; verify the app requests no SMS permission. Grant foreground location permission only if you selected location.
3. Tap **OPEN SMS APP** and verify that the SMS app shows the correct recipient and prepared text. Do not send to anyone who has not agreed to the test.
4. Return to the app and confirm that the status says **OPENED · NOT CONFIRMED**, not sent or delivered.
5. Tap Start again while active; the duplicate request should be ignored.
6. End the session; the state changes to CLOSED and location tracking stops.
7. Start without location; no location permission should be requested.
8. Close and reopen the app; previously saved sessions and draft-opening states should remain in history.

## Short explanation for a teacher

> “My project demonstrates an application-layer SOS workflow for a wireless mobile communication system. The app validates a destination phone number, creates a session, and prepares an addressed SMS using Android's SMS composer. It does not directly send the message: the user reviews it and presses Send. After that, Android's telephony framework and the mobile operator carry the SMS over the available cellular network. In the traditional GSM model, the path is the mobile station, BTS/BSC, MSC, and SMSC; modern LTE and 5G networks may use IMS or interworking, so the exact route varies. SMS is store-and-forward, so opening the composer is not proof of network delivery. The app therefore reports only that the composer opened and does not claim delivery. Separately, the phone can receive GNSS signals to estimate its location. In this prototype, the location is displayed and saved locally; it is not included in the SMS. The algorithm also checks location freshness and accuracy and pauses location updates when the app is not visible.”

### Questions the teacher may ask

- **Does the app implement GSM or 5G?** No. It is an Android application-layer prototype. The modem, Android telephony framework, and operator implement the lower communication layers and SMS transport.
- **What is the role of the SMSC?** In the classic SMS model, the SMSC receives and forwards messages and may store them temporarily if delivery is not immediately possible. Modern networks may provide SMS through IMS or interworking.
- **Why doesn't the app show “delivered”?** It hands off a draft rather than sending SMS itself and has no access to the messaging app's send result or carrier delivery report.
- **Is GPS the same as cellular triangulation?** No. GNSS estimates position from satellite signals. Cellular positioning can use network measurements or cell information, but this prototype uses Android's location service and displays its reported fix.
- **What happens when location is unavailable?** The session continues, location is marked unavailable, and the user can still prepare SMS drafts. The drafts do not contain coordinates.

## Limitations

This prototype does not implement authentication, contact verification, automatic retries, cloud backup, backend authorization, background tracking, or emergency-service integration. Opening a composer does not confirm that an SMS was sent or delivered. The on-device history contains destination phone numbers and location and is not encrypted separately from Android's app-private storage. Test only with consenting recipients; carrier charges may apply if you send a message. This prototype is not a production emergency service and must not be relied on in an actual emergency.
