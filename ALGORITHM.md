# Emergency SOS algorithm prototype

## Purpose and scope

This zeroth-review build demonstrates the core SOS orchestration flow from the project requirements. It is an offline simulation for a classroom demonstration, not an emergency service. It does not request permissions, read device location, send SMS, place calls, or contact a backend. Contact names and coordinates shown in the app are sample data.

## Main flow

```text
START SOS(contact list, location scenario)
  1. If a session is already active, ignore the duplicate request.
  2. Remove blank and duplicate contact entries.
  3. If no contacts remain, show a validation message and stop.
  4. Create a unique event in INITIATED state.
  5. Move to ALERTING and start two independent demo tasks:
       a. Mark each contact's alert as PENDING, then SIMULATED_SUBMITTED.
       b. Resolve the selected location scenario.
  6. If location is available, attach sample coordinates and accuracy.
     Otherwise, mark location UNAVAILABLE; do not block alert processing.
  7. When both tasks finish, move the session to ACTIVE.
  8. For the available-location scenario, show periodic sample updates.
  9. On END SESSION, stop scheduled updates and move to CLOSED.
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

Location availability and alert status are recorded separately from the session state. An unavailable location does not prevent the session from becoming active.

## Demonstration values and timing

- Two sample contacts are pre-filled; presenters can edit them.
- The location-available path uses clearly labeled sample coordinates near New Delhi with a sample accuracy of 35 m.
- The no-location path demonstrates that alert processing continues without coordinates.
- Demo alert attempts resolve independently for each contact. `SIMULATED_SUBMITTED` means only that the demo step completed; it does not represent a sent or delivered message.
- Sample tracking updates appear every four seconds until the session is closed.
- A session ID is generated for each new event, and only one session may be active at a time.

## Review checklist

1. Start with **LOCATION AVAILABLE** and observe the event, per-contact alert statuses, sample location, and tracking updates.
2. Tap Start again while active; the duplicate request should be ignored.
3. End the session; the state changes to CLOSED and tracking stops.
4. Start with **NO LOCATION**; alert statuses still resolve and the session becomes active with location marked unavailable.
5. End the session and start again; the new event should have a different ID.

## Intentionally out of scope

This prototype does not implement authentication, contact verification, persistent storage, real GPS, real SMS, delivery callbacks, retry policies, backend authorization, background tracking, or emergency-service integration. These are future implementation and testing tasks; the current simulated statuses must not be presented as real-world delivery or safety guarantees.
