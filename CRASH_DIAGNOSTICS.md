# Crash diagnostics

LiquidFiles installs an uncaught-exception handler at application startup. When an unhandled Java/Kotlin exception crashes the process, the handler writes a report to the app's private internal storage (`files/crash-logs/latest_crash.txt`) and keeps up to five timestamped reports. It then delegates to Android's prior uncaught-exception handler so normal crash termination still occurs.

On the next app launch, LiquidFiles offers to share or delete the saved report. The Settings screen also has **Crash logs & diagnostics** for manual access. Reports are not uploaded automatically; they are shared only if the user chooses a share target.

Reports include the exception stack trace, app version, package name, Android version, device manufacturer/model, crash time, and thread name. This captures uncaught managed-code exceptions, not every native crash, ANR, or system-level failure.
