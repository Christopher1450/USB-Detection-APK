# USB Guard Corporate / Research Build

This build improves the previous USB Guard prototype with a more enterprise-oriented incident and evidence layer.

## Main Capabilities

- Foreground USB monitoring service
- Charging source detection: battery, AC charger, USB, wireless, dock
- USB data-mode inference: charging-only, MTP, PTP, RNDIS, MIDI, accessory, ADB, unknown data
- Voltage, current, battery net power, estimated power going in, and device draw graphs
- External HID detection for keyboard, mouse, touchpad, and D-pad style input
- USB descriptor / accessory identity when Android is acting as USB host/OTG or accessory mode is exposed
- Protected recent events UI
- 1-hour in-app retention lock for security events
- Append-only SHA-256 hash chain for tamper-evident logs

## Protected Log Model

The application writes incident logs to app-private storage using JSON Lines. Each event contains:

- event ID
- title
- detail
- risk level
- timestamp
- locked-until timestamp
- previous event hash
- current event hash

The hash is calculated over the canonical event fields. If an entry is changed, deleted, or reordered outside the app, the hash-chain verification can detect inconsistency.

## Retention Rule

Security events are locked for 1 hour. The in-app clear action only removes events whose lock window has expired.

This is a UI-level and tamper-evident evidence-preservation mechanism. It is not absolute malware-proof storage. A malware sample with root access, device-owner privileges, or full app-data access could still remove local logs. For corporate-grade evidence preservation, upload incident logs to a remote backend, SIEM, or MDM server immediately after creation.

## Corporate Extension

For corporate deployment, this application can be extended into an Android Enterprise / Device Owner agent. In that mode, the app can enforce stronger policies such as disabling USB data signaling when supported by the device and allowed through DevicePolicyManager.

Normal APK mode can detect, score, warn, and log. Device Owner mode is required for direct USB data-signaling cutoff.
