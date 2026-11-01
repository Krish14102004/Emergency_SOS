# Emergency SOS algorithm prototype

## Purpose and scope

This prototype demonstrates SOS orchestration with actual foreground device location and locally persisted session history. Per-contact alerts remain simulated: the app does not send SMS, place calls, or contact a backend or emergency service.

## Main flow

```text
START SOS(contact list, location permission)
  1. If a session is already active, ignore the duplicate request.
  2. Remove blank and duplicate contact entries.
  3. If no contacts remain, show a validation message and stop.
  4. Create a unique event, add it to local history, and save it.
  5. Move to ALERTING and start two independent workflows:
       a. Mark each contact's alert PENDING, then SIMULATED_SUBMITTED.
       b. Request a current device location with a five-second deadline.
  6. If a usable fix arrives, record coordinates, reported accuracy, and timestamp.
     Otherwise, mark location UNAVAILABLE; alert processing continues.
  7. When both workflows resolve, move the session to ACTIVE.
  8. While the app is visible, publish valid location updates about every 10 seconds.
     Save each accepted update to the local session.
  9. When the app leaves the foreground, stop location updates.
     Resume them when the app returns and permission is still granted.
 10. On END SESSION, stop location updates and save the CLOSED state.
END
```

## Session state machine

| Current state | Event | Next state |
| --- | --- | --- |
| IDLE or CLOSED | Valid SOS request | INITIATED |
| INITIATED | Alert workflow begins | ALERTING |
| ALERTING | Alerts and location have both resolved | ACTIVE |
| ACTIVE | User ends the session | CLOSED |
| Any non-closed state | Duplicate SOS request | No state change |

Location availability and alert status are recorded separately from the session state. An unavailable location does not prevent the session from becoming active. If the app is reopened with an unfinished alerting session, it restores the saved event and resolves unfinished demo steps without claiming real delivery.

## Location validation and storage

- Location permission is requested only when the user chooses the location-enabled start action.
- Current-location acquisition has a five-second deadline. It accepts a fix no older than 60 seconds and with reported accuracy of 100 m or better.
- A fix no older than 30 seconds with accuracy of 50 m or better is reported as preferred quality; other accepted fixes are shown as lower confidence.
- Live updates request a 10-second interval and reject invalid coordinates, stale fixes, poor accuracy, and out-of-order timestamps.
- The app requests foreground-only permission. Updates stop when the app is not visible.
- Up to 20 events are stored in the app's private SharedPreferences, including status, contact demo labels, activity timeline, and last location.
- A unique event ID is generated for each session; only one session may be active at a time.

## Alert demonstration

Two sample contact names are pre-filled and editable. Each alert remains `PENDING` until its simulated step runs, then displays `SIMULATED · NOT SENT`. This status is not evidence that a message was submitted or delivered.

## Review checklist

1. Choose **START SOS · USE PHONE LOCATION** and grant foreground location permission.
2. Observe the actual coordinates, accuracy, timestamp, and periodic update count.
3. Tap Start again while active; the duplicate request should be ignored.
4. End the session; the state changes to CLOSED and tracking stops.
5. Start with **CONTINUE WITHOUT LOCATION**; alerts still resolve and the session becomes active without coordinates.
6. Close and reopen the app; previously saved sessions should remain in the history.

## Intentionally out of scope

This prototype does not implement authentication, contact verification, real SMS, delivery callbacks, retry policies, cloud backup, backend authorization, background tracking, or emergency-service integration. The on-device history is not encrypted separately from Android's app-private storage. Location is sensitive personal data; clear app data or uninstall to remove this prototype's local history. Simulated alert statuses must not be presented as real-world delivery or safety guarantees.
