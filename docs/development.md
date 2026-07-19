# LocalHive Agent Development

This document explains how to build, test, and extend the LocalHive Agent repository.

## Prerequisites

- JDK 21
- Maven
- Git

Platform features may require additional local setup:

- Linux Secret Service requires `secret-tool` and an accessible desktop keyring session.
- System Tray depends on the desktop environment.
- macOS Keychain behavior should be manually verified on physical macOS hardware.

## Clone And Build

```bash
git clone <repository-url>
cd LocalHive-Agent
mvn clean verify
```

The repository does not require a real LocalHive Master for the standard automated test suite.

## Run

Run the JavaFX desktop app:

```bash
mvn javafx:run
```

The Maven JavaFX plugin starts the modular application through `dev.adrian.goral.localhiveagent.app.Launcher`.

## Project Structure

```text
.github/workflows/
  agent-ci.yml
docs/
  architecture.md
  security.md
  development.md
  docker-policy.md
  workspace-artifacts.md
  output-artifacts.md
src/main/java/dev/adrian/goral/localhiveagent/
  app/
  config/
  desktop/
  heartbeat/
  logging/
  master/
  security/
  state/
  system/
  task/
  ui/
  util/
  validation/
src/main/resources/dev/adrian/goral/localhiveagent/
  desktop/
  ui/
src/test/java/dev/adrian/goral/localhiveagent/
```

Package responsibilities:

| Package | Responsibility |
| --- | --- |
| `app` | Launcher, JavaFX application lifecycle, runtime composition, paths. |
| `config` | Local config record and JSON persistence. |
| `desktop` | AWT System Tray service and tray action contract. |
| `heartbeat` | Scheduled heartbeat execution and tick results. |
| `logging` | Console/file logging, bounded rotation, lifecycle. |
| `master` | Java HTTP client, registration service, error mapping, DTOs. |
| `security` | CredentialStore implementations and backend selection. |
| `state` | UI-independent central Agent state and listeners. |
| `system` | OSHI machine specification detection. |
| `task` | Task polling, current execution state, local history, and registered executors. |
| `ui` | JavaFX dashboard view, controller, panes, icons, badges. |
| `util` | Small shared utilities. |
| `validation` | Config and Shared RAM validation. |

## Coding Conventions

- Write Java code, technical names, and code comments in English.
- Keep classes focused and aligned with current package responsibilities.
- Do not put business or network logic inside JavaFX controls.
- Do not put network logic inside System Tray code.
- Use `AgentStateStore` for shared UI/tray state instead of local duplicated status.
- Keep state model classes independent of JavaFX and AWT.
- Use `Platform.runLater` for JavaFX updates from background work.
- Use the AWT Event Dispatch Thread for tray menu and icon updates.
- Do not log secrets.
- Do not store the API key in `config.json`.
- Do not introduce Agent-side Task models before the Master task domain and API are defined.

## Testing

Run all tests:

```bash
mvn clean verify
```

The current suite has 92 tests.

Test stack:

- JUnit 5
- Maven Surefire
- `@TempDir` for filesystem isolation
- Fake or stub implementations for platform and Master-facing behavior

Main tested areas:

- `AgentConfig` and `ConfigService`
- Shared RAM and readiness validation
- `HeartbeatScheduler`
- `MasterClientErrorMapper`
- `CredentialStoreFactory`
- `InsecureFileCredentialStore`
- `MacOsKeychainCredentialStore` through a fake backend
- `AgentStateStore`
- Task polling, NO_OP execution, and Docker workload policy checks
- Logging initialization, rotation, flush/close, and representative secret sanitization

The standard automated tests do not require:

- Real LocalHive Master.
- Real GUI interaction.
- Real Linux Secret Service.
- Real Windows DPAPI.
- Real macOS Keychain.

## Cross-Platform CI

Workflow:

```text
.github/workflows/agent-ci.yml
```

Triggers:

- Push
- Pull request
- Manual dispatch

Matrix:

- `ubuntu-latest`
- `windows-latest`
- `macos-latest`

CI uses:

- Temurin 21
- Maven cache
- `mvn --batch-mode --no-transfer-progress clean verify`
- Surefire report upload on failure
- Read-only repository permissions

## Manual Platform Testing

### Windows

- Start the Agent with `mvn javafx:run`.
- Confirm the dashboard opens.
- Save Master URL and API key using placeholders or a test Master environment.
- Verify Windows DPAPI backend is selected.
- Register the worker against a test Master.
- Confirm heartbeat starts automatically after full configuration.
- Use Pause Worker and Resume Worker.
- Close the dashboard and confirm the tray icon keeps the process alive.
- Reopen the dashboard from tray.
- Confirm logs are written under `<user-home>/.localhive-agent/logs`.
- Exit from tray and confirm the process terminates.

### Linux

- Start the Agent with `mvn javafx:run`.
- Confirm dashboard startup.
- Test with `secret-tool` and an active keyring session.
- Confirm Linux Secret Service backend is selected when available.
- Test a desktop environment with System Tray support.
- Test an environment without tray support or a WSL/headless session and confirm standard close-to-exit lifecycle.
- Register against a test Master when available.
- Verify heartbeat, Pause/Resume, logs, and shutdown.
- Confirm insecure fallback is clearly shown when Secret Service is unavailable.

### macOS

- Start the Agent with `mvn javafx:run`.
- Confirm dashboard startup.
- Verify macOS Keychain backend selection on physical macOS hardware.
- Save an API key, restart the Agent, and confirm the key can be read again.
- Overwrite the credential and confirm the new value is used.
- Delete the credential through code or test tooling when available and confirm the Agent reports it missing.
- Verify tray behavior.
- Register against a test Master when available.
- Verify heartbeat, Pause/Resume, logs, and shutdown.

Real macOS Keychain behavior is still pending manual verification on physical hardware.

## Adding New Features

Use this sequence for new Agent capabilities:

1. Define the domain contract and Master API boundary first.
2. Add DTOs and client/service code in the appropriate package.
3. Keep UI code as a caller of application use cases, not the owner of business logic.
4. Publish shared status through `AgentStateStore` when dashboard and tray both need it.
5. Keep platform-specific behavior behind small adapters.
6. Add focused tests with fakes or stubs where real system services are not required.
7. Avoid broad refactors unless the feature genuinely changes an ownership boundary.

## Future Task Protocol Work

The current Agent supports the initial Task Protocol path through registered executors. Keep future additions aligned with the Master task domain and API.

Future work may include:

- Current Workload state and dashboard display.
- Task assignment client.
- Task execution manager.
- Local process execution.
- Broader Docker integration.
- Minecraft server workloads.
- RCON and graceful game server shutdown.
- Native image and installer packaging.
- System autostart.
- Signed updates.
