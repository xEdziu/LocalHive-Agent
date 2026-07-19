# Agent Docker Policy

Docker policy is a local security policy enforced by the Agent. The Master can send a Docker workload, but the Agent executes it only when the local policy allows the requested image and resource limits. Agent policy is the final enforcement layer on the worker machine.

## Configuration

The `docker` section is stored in the Agent config file:

```json
{
  "docker": {
    "enabled": true,
    "allowedImages": [
      "alpine:3.20"
    ],
    "maxMemoryMb": 4096,
    "maxCpuCores": 8,
    "allowGpu": false
  }
}
```

Fields:

| Field | Meaning |
| --- | --- |
| `docker.enabled` | If `true`, Docker workloads may run when all checks pass. If `false`, Docker workloads are rejected with `DOCKER_DISABLED`. |
| `docker.allowedImages` | Exact image references allowed by this Agent. Default: `["alpine:3.20"]`. There is no wildcard support yet. An empty list means no Docker image is allowed. |
| `docker.maxMemoryMb` | Maximum memory allowed for one Docker workload. Default: `4096`. Minimum valid policy value: `16`. |
| `docker.maxCpuCores` | Maximum CPU cores allowed for one Docker workload. Default: `8`. Minimum valid policy value: `1`. |
| `docker.allowGpu` | Default: `false`. GPU Docker execution is not implemented yet. Workloads with `gpu.required=true` are rejected in current V1 even if this flag is set. The flag is reserved for future GPU policy support. |

## Current Docker Workload V1 Behavior

Current V1 constraints:

- Only exact allowed images are accepted.
- Docker command is built as an argument list.
- The Agent uses fixed Docker flags.

```text
docker run --rm --network none --memory <memoryMb>m --cpus <cpuCores> <image> <command...>
```

When a workspace artifact is configured, the Agent adds one generated read-only bind mount:

```text
--mount type=bind,source=<agent-workspace-directory>,target=/workspace,readonly
```

See [workspace-artifacts.md](workspace-artifacts.md) for the current workspace artifact flow and limits.

The current implementation does not support:

- User-configured host mounts.
- Writable workspace mounts.
- `docker.sock` mounts.
- Privileged mode.
- Host network.
- Custom environment variables.
- GPU flags.
- Output artifacts.
- YAML import.
- Distributed or sharded execution.

## Failure Codes

| Code | Meaning |
| --- | --- |
| `DOCKER_DISABLED` | Docker workloads are disabled by local Agent policy. |
| `DOCKER_IMAGE_NOT_ALLOWED` | The requested Docker image is not present in `docker.allowedImages`. |
| `DOCKER_WORKLOAD_INVALID_CONFIGURATION` | The workload violates config or policy limits. |
| `DOCKER_UNAVAILABLE` | Docker CLI is not available on the Agent machine. |
| `DOCKER_WORKLOAD_TIMEOUT` | The container exceeded `timeoutSeconds`. |
| `DOCKER_WORKLOAD_FAILED` | The container exited with a non-zero exit code or failed to start. |
| `WORKSPACE_ARTIFACT_DOWNLOAD_FAILED` | The workspace artifact could not be downloaded from the Master. |
| `WORKSPACE_PACKAGE_INVALID` | The downloaded workspace ZIP is invalid or violates package safety limits. |
| `WORKSPACE_UNPACK_FAILED` | The Agent could not safely prepare or unpack the local workspace directory. |

## Future Extensions

Docker policy can be extended later with:

- Allowed registries.
- Image digest pinning.
- Disallowing the `latest` tag.
- Pull policy.
- Per-image resource limits.
- Workspace and artifact mount policy.
- Network policy.
- Environment variable allowlist.
- GPU policy.
- Docker runtime and device policy.
- Log and artifact upload policy.

YAML or workload definitions may describe the requested workload in the future, but they will not grant permissions by themselves. Permissions remain controlled by Master policy and Agent local policy, with Agent local policy as the final enforcement layer.
