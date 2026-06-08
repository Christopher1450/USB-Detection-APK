# USB Guard v5.1 Fix Notes

Changes in this build:

- AC wall charging is treated as a trusted power-only session when no USB data, accessory, descriptor, or valid sudden HID evidence exists.
- Stale USB data/HID state is cleared when the source is AC Charger, Wireless, or Dock.
- HID callbacks no longer write immediate BadUSB logs. HID is validated together with USB-source/data evidence first.
- Recent Security Events now shows current-session events only, so old High Risk logs do not appear while testing a normal charger.
- Protected Logs still preserves event counts and tamper-evident retention state.
- Hash values are hidden from the main Recent Events UI to reduce clutter.
- Log spam is reduced: within one physical connection, only risk escalation is stored (for example WARNING once, then DANGER once), not every repeated scan/broadcast.

Note: previously stored protected events may still remain in local app storage until cleared after the retention period or after uninstalling the app. This is expected for the protected-log feature.
