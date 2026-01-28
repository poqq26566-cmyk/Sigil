# SIGIL 
**Open-source, offline zero-trust encryption utility**

[![Version](https://img.shields.io/badge/Version-v0.4.5-blue?style=flat-square&logo=android)](https://github.com/Animesh-Varma/Sigil/releases)
[![License](https://img.shields.io/github/license/Animesh-Varma/Sigil?style=flat-square&color=green)](https://github.com/Animesh-Varma/Sigil/blob/master/LICENSE)
[![Build Status](https://img.shields.io/github/actions/workflow/status/Animesh-Varma/Sigil/android-build.yml?branch=master&style=flat-square&logo=github-actions&label=build)](https://github.com/Animesh-Varma/Sigil/actions/workflows/android-build.yml)
[![CodeQL](https://img.shields.io/github/actions/workflow/status/Animesh-Varma/Sigil/codeql.yml?branch=master&style=flat-square&logo=github&label=CodeQL)](https://github.com/Animesh-Varma/Sigil/actions/workflows/codeql.yml)
[![Issues](https://img.shields.io/github/issues/Animesh-Varma/Sigil?style=flat-square&color=red)](https://github.com/Animesh-Varma/Sigil/issues)
[![Pull Requests](https://img.shields.io/github/issues-pr/Animesh-Varma/Sigil?style=flat-square&color=purple)](https://github.com/Animesh-Varma/Sigil/pulls)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Animesh-Varma_Sigil&metric=alert_status)](https://sonarcloud.io/dashboard?id=Animesh-Varma_Sigil)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=Animesh-Varma_Sigil&metric=security_rating)](https://sonarcloud.io/dashboard?id=Animesh-Varma_Sigil)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=Animesh-Varma_Sigil&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=Animesh-Varma_Sigil)
[![CodeFactor](https://www.codefactor.io/repository/github/Animesh-Varma/Sigil/badge)](https://www.codefactor.io/repository/github/Animesh-Varma/Sigil)

Sigil is an encryption utility built with a focus on defense-in-depth and memory safety. In a world where privacy policies change overnight and "end-to-end" often has a backdoor, Sigil provides a secure, offline-only toolset for securing sensitive information.

By default, Sigil uses a Quad-Layer encryption chain that exceeds almost any threat model. But hey, why settle for standard security when you can have more? (There is also a standard "Raw Mode" for those who critique multi-layered encryption). :)

Sigil aims to be much more than just an encryption app; it aims to be a complete security suite to address all your cryptography needs. Check out the [Roadmap](#roadmap) for planned features—any suggestions are highly appreciated!

---

## Downloads

<div align="left">
    <a href="https://apt.izzysoft.de/fdroid/index/apk/dev.animeshvarma.sigil">
        <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="85">
    </a>
    <a href="https://play.google.com/store/apps/details?id=dev.animeshvarma.sigil">
        <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="85">
    </a>
      <a href="https://github.com/Animesh-Varma/Sigil/releases/latest">
    <img alt="Get it on GitHub" src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" height="85">
  </a>
</div>

### Release Status

| Platform | Current Version  | Build Channel |
| :--- |:-----------------| :--- |
| **IzzyOnDroid** | **v0.4.5**       | Pre-release |
| **Google Play** | **v0.4.5**       | Pre-release |
| **GitHub Releases** | **v0.4.5**       | Pre-release |

---

<h3 align="center">Contents</h2>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#how-it-works">How It Works</a> •
  <a href="#implemented-modules">Modules</a> •
  <a href="#screenshots">Screenshots</a> •
  <a href="#algorithm-registry">Algorithms</a>
  <br>
  <a href="#roadmap">Roadmap</a> •
  <a href="#technical-stack">Tech Stack</a> •
  <a href="#privacy">Privacy</a> •
  <a href="#build-instructions">Build</a> •
  <a href="#contact">Contact</a>
</p>

---

## Features

- **Encryption Profiles (New):** Switch between "Raw Mode" (Standard encryption compatibility for any algo), the classic "Sigil Chain", or create your own chain!
- **Multi-Layer Cascade:** By default, Sigil encrypts your data with the "Sigil Chain" profile (`XChaCha20` → `Serpent` → `Twofish` → `AES-256`). The number of algorithms doesn't really increase encryption time, as for text encryption, most of the time is taken just by the KDF.
- **Zero-Knowledge Auth:** Support for both **PINs** and **Passwords**. Authentication is handled via salted Argon2id hashes. Credentials are never stored in a reversible format.
- **Hardware-Backed Keystore:** Master seeds are generated and stored inside your phone's **Trusted Execution Environment (TEE)**. They never touch the app layer in plaintext.
- **Access Control:** Includes TEE-verified Biometrics and **Screen Shield** (just fancy talk for `flag_secure`).
- **Memory Hygiene:** Sigil zeros out (wipes) byte arrays from RAM the moment they aren't needed to prevent RAM dumps.
- **Material 3 UI:** Just because it's a security tool doesn't mean it has to look like an app from the 90s. ;)

---

## How It Works

### **Key Derivation (Argon2id)**
Sigil uses Argon2id as the primary KDF. It's memory-hard, which means it forces the device to use a chunk of RAM (up to 256MB) to unlock. This makes it incredibly annoying/expensive for attackers with GPUs to try and brute-force your password.

### **Encryption Profiles & Raw Mode**
Sigil v0.4.5 introduces **Encryption Profiles**, allowing users to define the complexity of their encryption once and reuse it:

1.  **Sigil Chain (Default):** The classic hybrid cascade designed for maximum defense depth. It wraps data in a custom container with metadata headers, compression, and KDF salts.
    *   *Layers:* `XChaCha20-Poly1305` → `Serpent-CBC` → `Twofish-CBC` → `AES-256-GCM`.
2.  **Standard AES:** For users who prefer a minimal attack surface or require compatibility with external tools (e.g., OpenSSL). This mode bypasses the multi-cipher chain and metadata headers, outputting pure ciphertext/IV/tag.
    *   *Flexibility:* You may use **any** algorithm from the registry (AES-GCM, XChaCha20, etc.) in Raw Mode, not just AES, by creating a new profile in Custom Mode and checking the RAW mode box (only available if a single algorithm is selected).
3.  **Custom Profiles:** Define your own cryptographic chain. Select from the registry of 20+ algorithms to create a bespoke encryption pipeline suited to your specific threat model. You can also override global KDF settings per profile if required (even though global settings are tweakable in the settings tab).

---

## Implemented Modules

### **Encryption (Auto & Custom)**
- **Auto Tab:** Quickly encrypt your text using Saved Profiles (Custom Chains) and Built-in ones.
- **Custom Tab:** A layer manager allowing users to select specific algorithms from the registry, reorder the cascade, and toggle ZLib compression among other things. This is also where you save new profiles.

### **Keystore**
- A manager for your saved keys. Sigil only decrypts these from the hardware vault when you successfully authenticate.
- Includes an **Entropy Meter** to show you how strong your key actually is.

### **Settings**
- **Cryptography Tuning:** Tweak the Argon2id parameters (Iterations, Memory, Parallelism).
- **App Lock:** A new wizard to set up your PIN or Password safely.
- **Privacy:** Controls for Screen Security, Grace Periods, and clipboard auto-wipe.
- **Appearance:** Dynamic colors and themes.

---

## Screenshots 
`Todo: update before PR`

<details>
<summary><b>Click here to view App Screenshots</b></summary>
<br>

<div align="center">

|                                                                                                                              |                                                                                                                                        |                                                                                                                              |
|:----------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------:|
| <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.jpg" alt="Onboarding" width="200"><br><b>Onboarding</b> |        <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.jpg" alt="App Lock" width="200"><br><b>App Lock</b>        | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.jpg" alt="Navigation" width="200"><br><b>Navigation</b> |
|  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04.jpg" alt="Auto Mode" width="200"><br><b>Auto Mode</b>  |     <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/05.jpg" alt="Custom Mode" width="200"><br><b>Custom Mode</b>     | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/06.jpg" alt="Algorithms" width="200"><br><b>Algorithms</b> |
|      <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/07.jpg" alt="Usage" width="200"><br><b>Usage</b>      |            <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/08.jpg" alt="Logs" width="200"><br><b>Logs</b>            |   <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/09.jpg" alt="Settings" width="200"><br><b>Settings</b>   |
|   <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/10.jpg" alt="Keystore" width="200"><br><b>Keystore</b>   | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/11.jpg" alt="Secure Vaulting" width="200"><br>Secure Vaulting<b></b> |   <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/12.jpg" alt="Releases" width="200"><br><b>Releases</b>   |

</div>

</details>

---

## Algorithm Registry

Sigil currently supports **20 cryptographic algorithms**, including modern standards, AES finalists, and some legacy ones (for educational/testing purposes).

| Algorithm | Type | Block Size | Origin/Standard | Status |
| :--- | :--- | :--- | :--- | :--- |
| **AES-GCM** | Block (AEAD) | 128-bit | NIST Standard (USA) | **Primary** |
| **ChaCha20-Poly1305** | Stream (AEAD) | N/A | IETF Standard | **Primary** |
| **XChaCha20-Poly1305** | Stream (AEAD) | N/A | Extended Nonce Variant | **SOTA** |
| **ARIA-256-GCM** | Block (AEAD) | 128-bit | IETF RFC 5794 (South Korea) | **Very Strong** |
| **Serpent** | Block (CBC) | 128-bit | AES Finalist | **Strong** |
| **Twofish** | Block (CBC) | 128-bit | AES Finalist | **Strong** |
| **Camellia** | Block (CBC) | 128-bit | NESSIE/CRYPTREC (EU/Japan) | **Strong** |
| **SM4** | Block (CBC) | 128-bit | GB/T 32907 (China) | **Strong** |
| **SEED** | Block (CBC) | 128-bit | KISA (South Korea) | **Strong** |
| **CAST-256** | Block (CBC) | 128-bit | AES Finalist | **Strong** |
| **RC6** | Block (CBC) | 128-bit | AES Finalist | **Strong** |
| **AES-CBC** | Block (CBC) | 128-bit | NIST Standard | Legacy Support |
| **Blowfish** | Block (CBC) | 64-bit | Legacy Schneier Design | *Weak (Flagged)* |
| **IDEA** | Block (CBC) | 64-bit | PGP Standard | *Weak (Flagged)* |
| **CAST-128** | Block (CBC) | 64-bit | GPG Legacy | *Weak (Flagged)* |
| **GOST 28147** | Block (CBC) | 64-bit | GOST (USSR/Russia) | *Weak (Flagged)* |
| **TEA** | Block (CBC) | 64-bit | Cambridge | *Weak (Flagged)* |
| **XTEA** | Block (CBC) | 64-bit | Extended TEA | *Weak (Flagged)* |

---

## Roadmap

Development is active. To view the current status, planned features, and release milestones, please visit the official Project Board.

[**View Project Roadmap**](https://github.com/users/Animesh-Varma/projects/2)

---

## Technical Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3 Expressive APIs)
- **Cryptography:** Bouncy Castle (bcprov-jdk18on v1.83)
- **Persistence:** Hardware Keystore (TEE) + Encrypted SharedPreferences
- **Architecture:** MVVM + UDF with Clean Architecture

---

## Privacy

Sigil is strictly **Offline-Only**. 
1. **No Internet:** The `INTERNET` permission is absent from the manifest. Data cannot leave the device.
2. **No Analytics:** No trackers, telemetry, or crash reporters included.
3. **No Backups:** `android:allowBackup` is disabled to prevent encrypted vault data from being synced to cloud providers accidentally.

---

## Build Instructions

Ensure you have the latest Android Studio and JDK 17+.

```bash
git clone https://github.com/Animesh-Varma/Sigil.git
cd Sigil
./gradlew assembleDebug
```

---

## Security Disclaimer

Sigil is an open-learning project. While I try my hardest to adhere to best practices, it hasn't been audited by a professional firm yet. I encourage you to read the code!

**Permanent Data Loss:** If you lose your Master PIN or Password, your data is gone forever. I can't help you recover it. (-_-)

---

## Contact

If you have questions or security findings:
Email: `sigil@animeshvarma.dev`

**Note:** This is my first foray into Android development and cryptography. I’m a high school student building this project in any spare time I can find, so contributors and general advice are always more than welcomed!
