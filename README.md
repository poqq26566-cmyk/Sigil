> ## **Note:** This is a placeholder README. Content will be updated shortly. Please refer to the [Latest Release](https://github.com/Animesh-Varma/Sigil/releases) for details till then.

# SIGIL
**A Zero-Trust Encryption Environment for Android**

Sigil is not a password manager, nor is it a simple note-taking app. It is an exercise in cryptographic paranoia. Designed for whistleblowers, journalists, and privacy maximalists, Sigil provides an offline-first, defense-in-depth fortress for your most sensitive text data.

It operates on a simple premise: **Trust nothing.** Not the operating system, not the clipboard, and certainly not the cloud.

---

## Downloads

<a href="https://apt.izzysoft.de/fdroid/index/apk/dev.animeshvarma.sigil">
    <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80">
</a>

<a href="https://play.google.com/store/apps/details?id=dev.animeshvarma.sigil">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">
</a>
<br>
<a href="https://github.com/Animesh-Varma/Sigil/releases/latest">
    <img src="https://img.shields.io/badge/GitHub-Download_APK-black?style=for-the-badge&logo=github" alt="Download from GitHub" height="80">
</a>


### Release Status

| Platform | Current Version | Build Channel |
| :--- | :--- | :--- |
| **IzzyOnDroid** | *Pending* | --- |
| **Google Play** | **v0.4.0** | Pre-release |
| **GitHub Releases** | **v0.4.0** | Pre-release |

## Screenshots 

> *Screenshots coming soon*

---

## Table of Contents

- [Sigil's Philosophy](#sigils-philosophy-overkill-is-underrated)
- [Core Capabilities](#core-capabilities)
- [Algorithm Registry](#algorithm-registry)
- [Roadmap & Planned Modules](#roadmap--planned-modules)
- [Technical Stack](#technical-stack)
- [Privacy & Offline Policy](#privacy--offline-policy)
- [Build Instructions](#build-instructions)
- [Security Disclaimer](#security-disclaimer)
- [Contact & License](#license)

---

## Sigil's Philosophy: Overkill is Underrated

Standard security applications typically rely on a single layer of AES-256 encryption. While mathematically secure, this creates a single point of failure against implementation bugs or future cryptanalytic breakthroughs.

Sigil rejects this approach. The app implements a **Quad-Layer Hybrid Cascade**. When data is locked in Sigil, it is not simply encrypted; it is buried beneath four distinct mathematical architectures.

### The Algorithm Chain
Every payload in "Auto Mode" is subjected to the following sequence, using Bouncy Castle 1.83 primitives:

1.  **AES-256 (GCM Mode):** The global standard for authenticated encryption (SPN Structure).
2.  **ChaCha20-Poly1305:** A high-speed stream cipher, immune to padding oracle attacks (ARX Structure).
3.  **Twofish:** A complex block cipher with a heavy key schedule (Feistel Structure).
4.  **Serpent:** A conservative finalist in the AES competition, offering the highest security margin with 32 rounds.

If a mathematical vulnerability is discovered in AES tomorrow, the data remains protected by three other independent ciphers.

---

## Core Capabilities

### Hardware-Backed Vault
Sigil does not trust plain text storage. It utilizes the Android Hardware Keystore (TEE/Secure Element) to generate and wrap keys. Master secrets never leave the physical security chip of the device.

### Amnesia Protocol
The application architecture fights against memory forensics:
*   **RAM Wiping:** Secrets are actively zeroed out in memory immediately after use.
*   **Lifecycle Management:** All sensitive data is purged from RAM the moment the app enters the background.
*   **Clipboard Hygiene:** Utilizes Android 13+ sensitive content flags and an aggressive auto-wipe timer to prevent clipboard history loggers from capturing data.

### Adversarial Authentication
The security model assumes the device can be stolen:
*   **Argon2id KDF:** PINs and Passwords are protected by the memory-hard Argon2id algorithm, configured to consume significant RAM (up to 128MB) to neutralize GPU brute-force attacks.
*   **Anti-Spoofing:** Biometric unlock requires a cryptographic signal from the hardware. A simple software hook cannot bypass the lock screen.
*   **Scorched Earth:** An optional "Emergency Reset" feature cryptographically shreds the master keys and wipes the application storage instantly.

### Configurable Paranoia
While the defaults are sane, Sigil puts the user in control.
*   **KDF Tuning:** Manually adjust the Memory Cost, Parallelism, and Iterations of the key derivation function to match specific threat models.
*   **Custom Chains:** Build custom encryption sequences using the comprehensive Algorithm Registry.

---

## Algorithm Registry

The app includes a comprehensive suite of cryptographic primitives, ranging from modern standards to legacy educational ciphers.

### Modern Standards (High Security)
| Algorithm | Type | Description |
| :--- | :--- | :--- |
| **AES-GCM** | AEAD Block | NIST Standard. Hardware-accelerated. |
| **ChaCha20-Poly1305** | AEAD Stream | IETF Standard. High performance, constant-time execution. |
| **Serpent** | Block (CBC) | AES Finalist. 32 rounds. Highest theoretical security margin. |
| **Twofish** | Block (CBC) | AES Finalist. Complex key schedule. |
| **Camellia** | Block (CBC) | NESSIE/CRYPTREC Standard. |
| **SM4** | Block (CBC) | Chinese National Standard (GB/T 32907). |
| **SEED** | Block (CBC) | South Korean KISA Standard. |
| **CAST-256** | Block (CBC) | RFC 2612. Resistant to linear/differential cryptanalysis. |

### Legacy & Educational (Use with Caution)
*Supported for educational analysis or compatibility, but explicitly flagged as "Weak" in the UI due to 64-bit block sizes.*
*   **Blowfish:** Legacy Schneier design. Vulnerable to birthday attacks on large data.
*   **IDEA:** The original PGP cipher.
*   **CAST-128:** Default cipher for older GPG versions.
*   **GOST 28147:** Soviet/Russian standard.
*   **TEA / XTEA:** Tiny Encryption Algorithms.

---

## Roadmap & Planned Modules

Current development is focused on the following features to further harden the application:

*   **Steganography:** Hiding encrypted ciphertext inside innocuous image/txt/video/audio files.
*   **File/Directory Encryption:** Extending the Quad-Layer engine to support arbitrary file types (PDF, JPG, ZIP).
*   **Headerless Mode:** A raw output mode that strips all metadata headers for Plausible Deniability.
*   **Asymmetric Encryption:** Elliptic Curve Cryptography (ECC) for secure key exchange between users.
*   **Partitions:** Creation of hidden "Ghost Vaults" accessible only via specific distress PINs.

---

## Technical Stack

*   **Language:** Kotlin
*   **UI Toolkit:** Jetpack Compose (Material You / M3)
*   **Cryptography:** Bouncy Castle (bcprov-jdk18on v1.83)
*   **Architecture:** Clean Architecture + MVVM + MVI
*   **Minimum SDK:** Android 8.0 (Oreo)
*   **License:** GPL-3.0

---

## Privacy & Offline Policy

Sigil is designed to be strictly **Offline-First**.

1.  **No Internet Permission:** The application manifest does not request `android.permission.INTERNET`. It is physically impossible for the app to exfiltrate keys or data.
2.  **No Analytics:** There are no trackers, crash reporters (Firebase/Crashlytics), or telemetry SDKs.
3.  **No Cloud Backup:** Android Auto-Backup is explicitly disabled to prevent keys from being synced to Google Drive.

---

## Build Instructions

To build Sigil from source, ensure you have Android Studio Ladybug (or newer) and JDK 17.

```bash
# Clone the repository
git clone https://github.com/Animesh-Varma/Sigil.git

# Navigate to directory
cd Sigil

# Build debug APK
./gradlew assembleDebug
```

*Note: The release builds require a keystore.properties file which is not included in the repository.*

---

## Security Disclaimer

Sigil is provided "as is", without warranty of any kind. While the architecture adheres to the highest standards of modern cryptography, no software is infallible.

**Loss of your Master Password or PIN will result in permanent data loss.** Due to the architecture of the encryption (Argon2id + TEE), there is no "Forgot Password" mechanism. Not even the developer can recover your data.

**Backup your data.** Do not rely on Sigil as the sole repository for critical information.

---

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

### Contact
If you have questions, security findings, or want to contribute:
Email: `sigil@animeshvarma.dev`

**NOTE:** I am a student building this project in my spare time. Contributors and general security advice are always welcome.
