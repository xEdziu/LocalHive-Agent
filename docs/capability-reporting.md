# Capability Reporting

M12 adds safe Agent capability reporting in heartbeat.

## Purpose

The Agent reports capability metadata during heartbeat so the Master can store and display the latest known worker capability snapshot.

Capability reporting is descriptive:

- automatic heartbeat includes capabilities,
- manual heartbeat includes capabilities,
- reported values describe configured support and local policy,
- reporting does not perform expensive live probing,
- reporting does not start, stop, claim, or execute tasks.

The Master stores the latest snapshot and shows it in Admin Worker Detail.

## Reported Capabilities

The Agent reports the currently registered built-in executor identities:

```text
localhive.no-op / contract v1 / enabled true
localhive.docker.workload / contract v1 / enabled according to DockerPolicy.enabled
```

Docker summary fields come from local `DockerPolicy`:

- `enabled`,
- `allowedImages`,
- `maxMemoryMb`,
- `maxCpuCores`,
- `gpuAllowed` from `DockerPolicy.allowGpu`.

The heartbeat capability report does not run `docker --version` on every heartbeat. It is based on local configuration and policy, not proof that the Docker daemon is currently healthy.

The Docker executor remains the final runtime enforcement point. A workload can be reported as configured-capable and still fail later if Docker is unavailable or a specific runtime check fails.

## Heartbeat Compatibility

The heartbeat request shape was extended additively. Existing heartbeat fields remain unchanged:

- `pauseEnabled`,
- `sharedRamMb`.

The new `capabilities` field is intended for M12 Master. Older compatible Master versions may ignore unknown JSON fields, but M12 Master is the expected target for capability reporting.

## Docker Policy Relation

Capability reporting reads the local Docker policy but does not change it.

Mapping:

| Capability field | Source |
| --- | --- |
| Docker workload executor `enabled` | `DockerPolicy.enabled` |
| `docker.enabled` | `DockerPolicy.enabled` |
| `docker.allowedImages` | `DockerPolicy.allowedImages` |
| `docker.maxMemoryMb` | `DockerPolicy.maxMemoryMb` |
| `docker.maxCpuCores` | `DockerPolicy.maxCpuCores` |
| `docker.gpuAllowed` | `DockerPolicy.allowGpu` |

Docker policy remains local Agent enforcement. The Master can observe the summary, but capability reporting does not grant permission to run workloads that the Agent policy rejects.

## Security / Non-Exposed Fields

The capability report does not send:

- API key,
- Master URL,
- local config path,
- credential store details,
- full config JSON,
- task history,
- raw command history,
- workspace or output paths,
- lease tokens,
- file contents.

The Agent should continue to avoid logging secrets or full local configuration. Capability values are safe metadata intended for Master storage and admin display.

## Current Limitations

- no live Docker health probing per heartbeat,
- no GPU execution support,
- no dynamic executor discovery beyond the current built-in executor list,
- no Agent scheduler behavior,
- no UI redesign,
- capabilities do not start, stop, claim, or execute tasks,
- capabilities are not a guarantee that every future runtime operation will succeed.

## Future Extensions

Future work may add:

- richer executor registry introspection,
- cached Docker health or availability reporting,
- worker capability and policy driven Master selection,
- GPU capability reporting after explicit GPU design,
- UI display improvements,
- capability versioning.
