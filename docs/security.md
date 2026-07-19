# LocalHive Agent Security

This document describes the security model implemented in the LocalHive Agent repository. It is intentionally limited to current code behavior.

## Security Scope

The current security-relevant areas are:

- Worker API key handling.
- Local configuration.
- Platform credential storage.
- Local file logging.
- Communication with the LocalHive Master.
- Local Docker workload policy.
- Workspace artifact download, unpacking, and read-only Docker mounting.
- GitHub Actions permissions.

The Agent executes only supported assigned workloads through registered executors. Docker workload V1 is constrained by local Agent policy and fixed Docker flags. Future executor expansion and broader process management will require a separate threat model.

## API Key Handling

The Worker API key is not stored in `config.json`.

The Agent accesses the API key through `CredentialStore`:

- `saveApiKey`
- `loadApiKey`
- `deleteApiKey`
- `hasApiKey`
- `backendName`
- `isSecure`

The key is used for authenticated communication with the Master after the worker is registered and approved. Documentation and examples must use `<api-key>` and must not include real credentials.

## Platform CredentialStore

### Windows

Windows uses `WindowsDpapiCredentialStore`.

Behavior:

- Uses Windows DPAPI through JNA Platform.
- Stores encrypted bytes in a local file under the Agent directory.
- Uses atomic replacement for writes when supported.
- Reports backend name `Windows DPAPI`.

### Linux

Linux uses `LinuxSecretServiceCredentialStore` when available.

Behavior:

- Requires `secret-tool` on `PATH`.
- Requires a usable Secret Service provider, such as a desktop keyring available to the current session.
- Passes the secret to `secret-tool store` through standard input rather than command-line arguments.
- Uses application and credential attributes to identify the stored item.
- Reports backend name `Linux Secret Service`.

If Secret Service is unavailable, Linux falls back to insecure local file storage.

### macOS

macOS uses `MacOsKeychainCredentialStore` when Keychain availability checks pass.

Behavior:

- Uses the Apple Security Framework through JNA.
- Does not shell out to `/usr/bin/security`.
- Does not place the secret in process arguments.
- Adds, updates, reads, and deletes a generic password item.
- Releases native Security Framework and CoreFoundation references.
- Clears the Java byte buffer used for save/update payloads after the operation.
- Wraps native failures in safe messages that do not include the API key.
- Reports backend name `macOS Keychain`.

The implementation compiles and passes contract tests on macOS CI, but real Keychain behavior still requires manual verification on physical macOS hardware.

### Insecure Fallback

`InsecureFileCredentialStore` is selected when no supported secure backend is available.

Behavior:

- Stores the API key in a local file under the Agent directory.
- Attempts to restrict POSIX permissions to owner read/write when supported by the file system.
- Reports `isSecure() == false`.
- Reports backend name `Insecure file storage`.
- Is shown in the UI and logs as an insecure backend.

The fallback is not equivalent to platform credential storage. It exists to keep the Agent usable in unsupported or development environments.

The factory selects the backend at startup. It does not silently downgrade a single failed secure backend operation to the fallback during that operation.

## Logging Security

The logging design avoids logging the API key directly.

Implemented protections:

- Startup logging records whether an API key is configured, not the key value.
- macOS Keychain wrapper errors avoid including the secret.
- Master error mapping sanitizes representative authentication header content from backend error messages.
- Tests assert that representative secret values and authentication header markers are not persisted to local logs.
- Logs are bounded by size and file count.

Important rule:

```text
Secrets should not be passed to the logger in the first place.
```

The current sanitization is not a guarantee that any arbitrary future secret in any arbitrary message will be removed. New code should avoid constructing log messages that contain secrets.

## Configuration Security

Config path:

```text
<user-home>/.localhive-agent/config.json
```

Stored fields:

- `masterBaseUrl`
- `workerId`
- `sharedRamMb`
- `pauseEnabled`
- `docker`

The config file is not encrypted. It intentionally does not contain the API key.

Docker policy is local Agent security configuration. See [docker-policy.md](docker-policy.md) for the current Docker workload limits and failure behavior.

Workspace artifact handling is execution-scoped and local to the Agent. The Agent downloads workspace packages with the Worker API key and execution lease, stores them under `.localhive-agent/workspaces/<executionId>/`, rejects unsafe ZIP paths and symlink path chains, and mounts the unpacked workspace read-only at `/workspace`. See [workspace-artifacts.md](workspace-artifacts.md).

## Network Security

The Agent uses Java `HttpClient` for Master communication.

Current facts:

- The Master base URL is user-configurable.
- If the URL has no scheme, the client prepends `http://`.
- TLS is not enforced by the Agent.
- Certificate pinning is not implemented.
- Mutual TLS is not implemented.
- WebSocket, SOAP, and gRPC clients are not implemented in the Agent.

Deployments that require encrypted transport should configure an HTTPS Master URL and handle certificate trust outside the current Agent code.

## Threats Currently Mitigated

The current code mitigates these specific risks:

- API key is kept outside `config.json`.
- Secure platform credential storage is used when available.
- Linux Secret Service storage avoids passing the secret as a command-line argument.
- macOS Keychain integration avoids the shell command interface and process arguments.
- macOS native memory references are released.
- Own save/update byte buffers in the macOS backend are cleared after use.
- Local logs are bounded.
- Representative credential-related log messages are sanitized and tested.
- GitHub Actions workflow uses read-only repository permissions and disables persisted checkout credentials.

## Known Limitations

- Real macOS Keychain behavior still needs manual verification on physical macOS hardware.
- Insecure fallback storage is possible on unsupported systems or systems without an available credential backend.
- Config data is not encrypted.
- The Agent does not enforce HTTPS.
- No certificate pinning or mutual TLS is implemented.
- No code signing is documented here.
- Installer security, auto-update signing, notarization, and native packaging are not implemented in this repository.
- No full external security audit has been performed.
- Future executor expansion will need a separate threat model before adding broader local process or workload capabilities.

## Security Reporting

Use the repository's private vulnerability reporting feature when it is enabled. Do not report secrets or real API keys in public issues.
