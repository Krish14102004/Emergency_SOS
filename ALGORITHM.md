# Emergency SOS algorithm prototype

## Purpose and scope

This prototype demonstrates SOS orchestration with actual foreground device location, locally persisted session history, and SMS drafts handed off to the user's SMS app. It does not send SMS itself, place calls, contact a backend, or notify emergency services.

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

## Limitations

This prototype does not implement authentication, contact verification, automatic retries, cloud backup, backend authorization, background tracking, or emergency-service integration. Opening a composer does not confirm that an SMS was sent or delivered. The on-device history contains destination phone numbers and location and is not encrypted separately from Android's app-private storage. Test only with consenting recipients; carrier charges may apply if you send a message. This prototype is not a production emergency service and must not be relied on in an actual emergency.
