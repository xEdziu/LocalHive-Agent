# Workspace Artifacts

Workspace artifacts let a Docker workload receive a small ZIP package of input files. The current Agent support is intentionally narrow: the package is downloaded for one execution, unpacked under the Agent workspace directory, and mounted into Docker as read-only `/workspace`.

This is suitable for small input packages such as:

- `main.py`
- `utils.py`
- `input.json`
- `main.txt`

It is not a persistent workload directory and is not an output artifact channel. Docker workload outputs use the separate writable `/output` mount; see [output-artifacts.md](output-artifacts.md).

## Current Flow

1. Master receives a ZIP workspace package through the dev artifact upload endpoint.
2. Master stores artifact metadata and the ZIP file.
3. Docker workload configuration references the artifact by `workspace.artifactId`.
4. Agent claims the execution.
5. Agent downloads the artifact from the execution-scoped worker endpoint.
6. Agent sends `X-API-KEY` and `X-EXECUTION-LEASE`.
7. Agent stores the ZIP under `.localhive-agent/workspaces/<executionId>/package.zip`.
8. Agent safely unpacks it into `.localhive-agent/workspaces/<executionId>/workspace/`.
9. Agent mounts the unpacked directory to Docker as read-only `/workspace`.
10. The container reads files from `/workspace`.

## Docker Workload Configuration

```json
{
  "image": "alpine:3.20",
  "command": [
    "sh",
    "-c",
    "ls -la /workspace && cat /workspace/main.txt"
  ],
  "timeoutSeconds": 30,
  "resources": {
    "memoryMb": 64,
    "cpuCores": 1
  },
  "gpu": {
    "required": false
  },
  "workspace": {
    "artifactId": "00000000-0000-0000-0000-000000000001",
    "mountPath": "/workspace",
    "readOnly": true
  }
}
```

## Workspace Fields

| Field | Required | Meaning |
| --- | --- | --- |
| `workspace.artifactId` | Yes, when `workspace` is present | UUID of a Master artifact with kind `WORKSPACE_PACKAGE`. |
| `workspace.mountPath` | Yes | Container mount path. The current Agent accepts exactly `/workspace`. |
| `workspace.readOnly` | Yes | Must be `true`. Writable workspace mounts are not supported. |

The entire `workspace` object is optional. Docker workloads without `workspace` continue to run without any mount.

## Local Agent Layout

```text
.localhive-agent/
`-- workspaces/
    `-- <executionId>/
        |-- package.zip
        `-- workspace/
            |-- main.py
            |-- utils.py
            |-- input.json
            `-- main.txt
```

There is no cleanup or retention policy yet. Execution workspace directories are kept locally for debugging until they are removed manually or a future cleanup feature is added.

## Security Rules

- Host paths are not controlled by workload configuration.
- The Docker bind mount source is the Agent-generated local workspace directory.
- The Docker mount is read-only.
- Artifact download is scoped to the claimed execution.
- Artifact download requires both the Worker API key and the execution lease.
- The ZIP download is streamed directly to disk instead of being held fully in memory.
- API keys and execution lease tokens must not be logged, displayed, or persisted as history data.
- ZIP unpacking includes Zip Slip protection.
- Unsafe ZIP paths are rejected, including absolute paths, Windows drive paths, backslash traversal, parent traversal, empty path segments, duplicate target paths, and unsafe existing path chains.
- Symlinks or unsupported existing path types under the Agent workspace path are rejected before download and unpack.

## Limits

| Limit | Current value |
| --- | --- |
| Maximum downloaded package size | 50 MB |
| Maximum unpacked workspace size | 200 MB |
| Maximum ZIP entries | 1000 files or directories |
| Mount path | `/workspace` only |
| Mount access | Read-only only |

## Failure Codes

| Code | Meaning |
| --- | --- |
| `WORKSPACE_ARTIFACT_DOWNLOAD_FAILED` | The Agent could not download the workspace artifact from the Master. |
| `WORKSPACE_PACKAGE_INVALID` | The downloaded ZIP package is invalid or violates package safety limits. |
| `WORKSPACE_UNPACK_FAILED` | The Agent could not safely prepare or unpack the local workspace directory. |
| `DOCKER_WORKLOAD_INVALID_CONFIGURATION` | The Docker workload configuration is invalid, including invalid or unsupported `workspace` fields. |

## Current Limitations

- Writable workspace mounts are not supported.
- No workspace cleanup policy is implemented yet.
- Large multi-GB artifact support is not implemented.
- Minecraft persistent workload support is not implemented.
- GPU Docker execution is not implemented.
- Distributed or sharded workspace support is not implemented.
- YAML import is not implemented.

## Future Extensions

Future workspace and artifact work may add:

- Execution logs upload.
- Cleanup and retention policy.
- Large artifact policy.
- Persistent workload directories.
- Minecraft and Fabric snapshots.
- Backup and restore support.
- Writable controlled mounts.
- Distributed or sharded inputs and outputs.

Minecraft servers should use future persistent workload directories and snapshot flows, not the current small read-only workspace package flow.
