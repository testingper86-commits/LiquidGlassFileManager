# Security policy and threat model

## Reporting

Please report security issues privately to the repository owner before publishing exploit details. Include the affected version, Android version, reproduction steps, and whether data was exposed or lost. Do not include real PINs, personal files, or other secrets in reports.

## Security boundaries

- ToxiFiles is a local Android application. Its normal file browsing and media playback do not depend on a cloud account or remote backend.
- LAN transfer listens only while the user starts a receive session. The receiver displays a short-lived pairing code; the sender proves knowledge of the code, derives a session key using PBKDF2-HMAC-SHA-256, and encrypts each payload with AES-GCM. File SHA-256 values are checked as an additional integrity check. Do not use untrusted Wi-Fi, and do not share the pairing code.
- Vault files and their original-path metadata are encrypted with AES-GCM. The key is device-bound through Android Keystore. The Vault PIN is an application-level gate; it does **not** cryptographically wrap the Keystore key. A process compromise while the app is running, a compromised/rooted device, or malware with sufficient device privileges is outside the protection this design can guarantee.
- App Lock uses a salted PBKDF2 verifier, persistent escalating attempt delays, screen-off/background relocking, and screenshot protection on protected screens. It is not equivalent to Android's hardware-backed lock screen.
- Android backup is disabled to avoid copying ciphertext without the device-bound key. Removing app data or uninstalling the app may make Vault contents unrecoverable. Restore Vault items first.
- FileProvider is non-exported and grants access through specific shared URIs. Do not change it to expose the filesystem root.
- Settings import is size-limited and only accepts a small allowlist of portable preferences; security preferences are intentionally excluded.
- Recursive operations reject symbolic links. Archive extraction limits entry count and expanded size, checks destination containment, and removes partial output after failure.
- ToxiFiles uses sensitive Android permissions for core file-management and APK-install workflows. Permission grants remain under Android's control, and distribution policy must be reviewed for the intended channel.

## Known limitations

- The app has not completed the final mass verification pass across all recent phases. Structural smoke tests and CI runs must not be interpreted as a substitute for device-level security testing.
- The Vault PIN does not gate Android Keystore key use at the cryptographic layer.
- A compromised operating system or privileged attacker can bypass in-app controls.
- LAN pairing protects payload confidentiality/integrity but is not a general-purpose replacement for a formally reviewed TLS protocol.
- Signed release builds require the owner to configure their own keystore secrets. Never commit a keystore or its passwords to the repository.
