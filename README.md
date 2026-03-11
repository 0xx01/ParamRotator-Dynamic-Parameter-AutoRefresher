# ParamRotator

[![Burp Suite](https://img.shields.io/badge/Burp_Suite-2025+-orange?logo=burpsuite&logoColor=white)](https://portswigger.net/burp)
[![Java](https://img.shields.io/badge/Java-21-blue?logo=openjdk&logoColor=white)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Maven](https://img.shields.io/badge/Maven-Build-red?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-22c55e)](LICENSE)
[![Status: Active](https://img.shields.io/badge/Status-Active-22c55e)]()

> A Burp Suite extension that automates dynamic HTTP parameter management, keeping requests valid throughout your entire security testing workflow.

---

## Overview

**ParamRotator** eliminates the overhead of manually managing rotating tokens, session identifiers, and other dynamic parameters during penetration testing.

By automatically extracting and refreshing these values, the extension ensures that requests remain valid throughout the testing workflow — allowing security testers to focus on vulnerability discovery rather than request maintenance.

---

## Table of Contents

- [The Problem](#the-problem)
- [The Solution](#the-solution)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
- [Demo](#demo)
- [Contributing](#contributing)
- [License](#license)

---

## The Problem

Modern web applications rely heavily on **dynamic parameters** that rotate frequently during a user session — session identifiers, application-specific security parameters, and more.

When performing security testing in **Burp Suite**, these values expire quickly. Previously captured requests become invalid and must be manually updated before they can be replayed.

This creates real friction for pentesters:

- Requests fail in **Repeater** due to expired tokens
- Testing workflows are constantly interrupted by stale parameters
- Multiple dynamic values must stay synchronized across requests
- Time is spent on maintenance instead of vulnerability discovery

Handling these parameters manually slows testing down and introduces errors — outdated values, missed parameters, and unreproducible findings.

---

## The Solution

**ParamRotator** automates the full lifecycle of dynamic parameter management. The extension extracts values directly from requests and keeps them fresh using a configurable reference request — no manual copying required.

By keeping requests valid, ParamRotator helps surface vulnerabilities that might otherwise remain hidden behind expired tokens or broken request state.

---

## Features

### Parameter Extraction
- Extract **URL query parameters** (`?param=value`)
- Extract **matrix parameters** (`;param=value`)
- Extract **custom parameters** (user-defined)
- Extract parameters from **Location headers**

### Automatic Token Refresh
- Periodically refresh tokens using a **reference request**
- Automatically update stored parameters from response
- Configurable refresh interval in seconds

### Parameter Management UI
- Visual table showing all tracked parameters with real-time values
- Add, edit, delete parameters
- Enable/disable parameters individually
- Toggle auto-update per parameter

### Burp Integration
- Works directly inside **Repeater**
- Right-click context menu integration
- Clean extension unload with no resource leaks

### Debugging Mode
- Toggle detailed logging for parameter updates and refresh cycles

---

## Requirements

| Dependency | Version                 |
|------------|:------------------------|
| Java JDK   | 21                      |
| Maven      | 3.6+                    |
| Burp Suite | 2025+ (Montoya API) |

---

## Installation

ParamRotator can be installed in two ways:

- **Option 1:** Install the pre-built release *(recommended)*
- **Option 2:** Build from source

---

### Option 1 — Install from Release (Recommended)

1. Download the latest `ParamRotator.jar` from the [GitHub Releases](https://github.com/0xx01/ParamRotator/releases) page.
2. Open **Burp Suite**.
3. Navigate to **Extensions**.
4. Click **Add**.
5. Set **Extension Type** to `Java`.
6. Select the downloaded `ParamRotator.jar`.
7. Click **Next** to load the extension.

---

### Option 2 — Build from Source

**Prerequisites:** Java 21 and Maven installed.

```bash
git clone https://github.com/0xx01/ParamRotator.git
cd ParamRotator
mvn clean package
```

The compiled JAR will be generated at `target/ParamRotator.jar`.

**Load into Burp Suite:**

1. Open **Burp Suite**
2. Navigate to **Extensions**
3. Click **Add**
4. Set **Extension Type** to `Java`
5. Select `target/ParamRotator.jar`
6. Click **Next** — the extension loads automatically

---

## Usage

### Step 1 — Extract Parameters
Right-click any request in Burp → **Extract Parameters from Request**

ParamRotator will detect and store all dynamic parameters from the URL automatically (matrix params and query params).

![extract_Parameter](docs/images/extract_Parameter.gif)

### Step 2 — Set Reference Request
Right-click the request that issues fresh tokens (e.g. login request) → **Set as Reference**

This is the request ParamRotator will send periodically to fetch updated token values.

![set-refrence](docs/images/set-refrence.gif)

### Step 3 — Start Auto-Refresh
Open your target request in **Repeater**, then right-click → **Start Auto-Refresh**

ParamRotator will now refresh tokens in the background at your configured interval and update the Repeater request automatically.

![start_auto-refresh](docs/images/start_auto-refresh.gif)

### Step 4 — Manage Parameters *(optional)*
Right-click → **Manage Parameters...** to open the Parameter Manager UI.

From here you can:
- View all tracked parameters and their current values
- Enable or disable individual parameters
- Add custom parameters manually
- Toggle auto-update per parameter

![Parameter_Manager](docs/images/Parameter_Manager.gif)

### Other Options

| Menu Item                                   | Description                                               |
|---------------------------------------------|-----------------------------------------------------------|
| **Extract Parameters from Location Header** | Extract params from a redirect response's Location header |
| **Stop Auto-Refresh**                       | Stop the background refresh scheduler                     |
| **Set Interval**                            | Change the refresh interval (default: 30 seconds)         |
| **Debugging Mode**                          | Toggle verbose logging in Burp output                     |

---

## Demo

> The demo below shows ParamRotator extracting parameters from a live request and automatically refreshing tokens during a testing session.

[![Watch Demo](https://img.youtube.com/vi/QiWHnsslB0c/0.jpg)](https://youtu.be/QiWHnsslB0c)

---

## Contributing

Contributions, issues, and feature requests are welcome.
Feel free to open an [Issue](https://github.com/0xx01/ParamRotator/issues) or submit a Pull Request.

---

## License

This project is licensed under the **MIT License**. See the [LICENSE](https://github.com/0xx01/ParamRotator/blob/main/LICENSE) file for full details.
