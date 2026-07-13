# LocalHive Agent

LocalHive Agent is the worker-side desktop application of LocalHive. It runs on a local machine, registers that machine with the LocalHive Master, reports worker availability through heartbeat messages, lets the user choose how much RAM is shared with the swarm, and supports a Pause/Resume mode for personal use of the machine.

The Agent is a desktop application with a JavaFX dashboard and System Tray integration. It stores the Worker API key outside `config.json` through a platform credential backend when one is available.

The Agent does not execute workloads or tasks yet. The current foundation is implemented and is being prepared for future Task Protocol integration.

## Current Capabilities

Implemented:

- Local JavaFX dashboard with TilesFX resource tiles and Ikonli icons.
- System Tray lifecycle: close-to-tray when supported, Open Dashboard, Pause/Resume, and controlled Exit.
- Local configuration stored under `<user-home>/.localhive-agent/config.json`.
- Worker registration with the Master.
- Shared RAM allocation update.
- Hardware specification update based on OSHI-detected machine data.
- Automatic heartbeat startup when Master URL, Worker ID, and API key are configured.
- Manual heartbeat controls for diagnostics.
- Gamer Mode through Pause/Resume with rollback on failed Master update.
- Central `AgentStateStore` used by the dashboard and tray.
- Native or platform-aware API key storage: Windows DPAPI, Linux Secret Service, macOS Keychain, and an explicitly insecure local fallback.
- Local bounded file logging with representative secret sanitization tests.
- Cross-platform GitHub Actions CI for Ubuntu, Windows, and macOS.

Planned:

- Task Protocol integration.
- Current Workload display.
- Process execution.
- Minecraft server workloads.
- Docker/RCON/native packaging work described by the broader LocalHive ADR.

## Technology Stack

The main runtime stack is:

- Java 21
- JavaFX
- Maven
- OSHI
- Jackson 3 (`tools.jackson.*`)
- JNA
- TilesFX
- Ikonli
- SLF4J Simple
- JUnit 5

Versions are managed in `pom.xml`.

## Requirements

- JDK 21
- Maven
- Git

Platform credential requirements:

| Platform | Expected backend | Notes |
| --- | --- | --- |
| Windows | DPAPI | Uses the current Windows user profile. |
| Linux | Secret Service | Requires `secret-tool`, a usable desktop keyring, and an active session that can access it. |
| macOS | Apple Keychain | Uses the Apple Security Framework through JNA. Real Keychain behavior still needs manual verification on physical macOS hardware. |
| Fallback | Local file | Explicitly marked insecure and used when no supported secure backend is available. |

System Tray support depends on the desktop environment. Some Linux desktops, WSL sessions, Wayland setups, and headless environments may not support it.

## Build And Run

Build and test:

```bash
mvn clean verify
```

Run the desktop application during development:

```bash
mvn javafx:run
```

The Maven JavaFX plugin starts:

```text
dev.adrian.goral.localhiveagent/dev.adrian.goral.localhiveagent.app.Launcher
```

## Configuration

The Agent stores local configuration in:

```text
<user-home>/.localhive-agent/config.json
```

On Windows this is the same `user.home` based location, using Windows path separators.

Current config fields:

```json
{
  "masterBaseUrl": "<master-url>",
  "workerId": "<worker-id>",
  "sharedRamMb": 8192,
  "pauseEnabled": false
}
```

The API key is not stored in `config.json`. It is stored through the selected `CredentialStore`.

## Credential Storage

| Platform | Backend | Secure |
| --- | --- | --- |
| Windows | Windows DPAPI | Yes |
| Linux | Linux Secret Service | Yes |
| macOS | macOS Keychain | Yes |
| Fallback | Insecure file storage | No |

See [docs/security.md](docs/security.md) for the security model and limitations.

## Logs

Local logs are stored under:

```text
<user-home>/.localhive-agent/logs
```

The default policy keeps approximately 50 MiB of managed logs:

- 10 MiB per file.
- 5 files maximum.

Logging mirrors console output to bounded local files when file logging can be initialized. If file logging fails, the console remains available.

See [docs/security.md](docs/security.md) for logging security notes.

## System Tray

When `SystemTray.isSupported()` is true and tray initialization succeeds:

- Closing the dashboard hides the window.
- The Agent keeps running.
- Heartbeat continues running.
- The tray menu can open the dashboard again.
- The tray menu can pause or resume the worker through the same controller flow used by the dashboard.
- Exit from the tray performs controlled shutdown.

When System Tray is unsupported or fails to initialize, the application keeps the standard JavaFX lifecycle and closing the window exits the application.

## Tests And CI

Run the full local verification:

```bash
mvn clean verify
```

The current suite has 92 tests. This number can grow as the Agent evolves.

GitHub Actions workflow:

```text
.github/workflows/agent-ci.yml
```

The CI matrix runs on:

- Ubuntu
- Windows
- macOS

CI uses Temurin 21, Maven cache, and uploads Surefire reports only on failure.

## Documentation

- [Architecture](docs/architecture.md)
- [Security](docs/security.md)
- [Development](docs/development.md)

## Project Status

The Agent foundation is implemented and is being prepared for future Task Protocol integration.
