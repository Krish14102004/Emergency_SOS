# Emergency SOS algorithm prototype

## Purpose and scope

This prototype demonstrates SOS orchestration with actual foreground device location, locally persisted session history, and direct SMS attempts to confirmed phone numbers. It does not place calls or contact a backend or emergency service.

## Main flow

```text
CONFIRM SOS(phone number list, location choice)
  1. If a session is already active, ignore the duplicate request.
  2. Normalize and validate 1-5 unique phone numbers.
  3. Show the exact destination numbers and SMS warning; require explicit confirmation.
  4. Request Android SEND_SMS permission. If permission is denied, send nothing and stop.
  5. Create a unique event, persist each alert ID, and move to ALERTING.
  6. Request one SMS per confirmed number immediately; record send failures separately.
  7. In parallel, if requested and permitted, obtain current device location
     with a five-second deadline.
  8. If a usable fix arrives, record coordinates, reported accuracy, and timestamp.
     Otherwise, mark location UNAVAILABLE; alert processing continues.
  9. Mark the session ACTIVE when initial dispatch and location resolve.
 10. Process per-SMS sent/delivery callbacks and persist each status separately.
     Never equate submission with delivery or reading.
 11. While the app is visible, save valid location updates about every 10 seconds.
 12. When the app leaves the foreground, stop location updates.
     Resume them when the app returns and permission is still granted.
 13. On END SESSION, stop location updates and save the CLOSED state.
END
```

## Session state machine

| Current state | Event | Next state |
| --- | --- | --- |
| IDLE or CLOSED | Valid, confirmed SOS request | INITIATED |
| INITIATED | SMS dispatch begins | ALERTING |
| ALERTING | SMS dispatch attempts and location resolution finish | ACTIVE |
| ACTIVE | User ends the session | CLOSED |
| Any non-closed state | Duplicate SOS request | No state change |

Location availability and per-recipient SMS status are recorded separately from the session state. A failed SMS or unavailable location does not silently become a success and does not prevent the session from becoming active. The app does not automatically resend messages after a restart.

## SMS delivery status

- The user must confirm the exact phone numbers before Android permission is requested and before any SMS send.
- Phone numbers are normalized and limited to 1-5 unique entries per SOS.
- One SMS is sent to each number. It does not contain GPS coordinates; no location-follow-up message is sent.
- `SENDING`: Android send was requested and a callback is pending.
- `SUBMITTED`: the Android sent callback reported success; human delivery is not implied.
- `DELIVERED`: the device/carrier reported delivery; this does not confirm that a person read it.
- `FAILED`: the device reported a failed send request.
- `SUBMISSION_UNCONFIRMED` / `DELIVERY_UNCONFIRMED`: the corresponding status could not be verified. No automatic retry is attempted.
- SMS attempts and callback statuses are saved per event and per recipient. If the app restarts, a stale attempt is not automatically sent again.

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
2. Grant Android SMS permission. Grant foreground location permission if you selected location.
3. Check the recipient device and app status. A sent callback is not a read receipt.
4. Tap Start again while active; the duplicate request should be ignored.
5. End the session; the state changes to CLOSED and location tracking stops.
6. Start without location; the SMS should still be sent and the session should record location as unavailable.
7. Close and reopen the app; previously saved sessions and delivery states should remain in history.

## Limitations

This prototype does not implement authentication, contact verification, automatic retries, cloud backup, backend authorization, background tracking, or emergency-service integration. Android/carrier delivery callbacks are not guaranteed. The on-device history contains destination phone numbers and location and is not encrypted separately from Android's app-private storage. Test only with consenting recipients; carrier charges may apply. This prototype is not a production emergency service and must not be relied on in an actual emergency.
