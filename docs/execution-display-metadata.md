# Execution Display Metadata

Execution display metadata lets the Agent show a stable human-readable name for claimed work while preserving technical executor ids for routing.

Before AU0, Agent UI and local history primarily showed technical executor ids, for example:

```text
localhive.docker.workload / SUCCEEDED / 506 ms
```

After AU0, the Agent can show names received from the Master:

```text
Output Artifact Smoke Test / SUCCEEDED / 655 ms
```

## Claim Response Compatibility

The Agent supports the `displayName` field in the Master claim response:

```json
{
  "executionId": "00000000-0000-0000-0000-000000000000",
  "displayName": "Output Artifact Smoke Test",
  "executorId": "localhive.docker.workload",
  "executorContractVersion": 1
}
```

From the Agent perspective:

- `displayName` is optional.
- Older Master responses without `displayName` still work.
- Future additive fields in the claim response are ignored by the claim DTO.
- `leaseToken` remains redacted in string and debug output.

## Fallback Rules

The Agent resolves a display label in this order:

1. Use a nonblank trimmed `displayName` from Master.
2. For `localhive.docker.workload`, use `Docker workload: <image>` when the claimed configuration contains a nonblank `image`.
3. For `localhive.no-op`, use `NO-OP smoke test`.
4. Otherwise use `executorId`; if no executor id is available, use `Execution`.

The display name is for display only. It does not affect executor lookup, claim behavior, lease renewal, output upload, terminal reporting, or failure mapping.

Display names are bounded to 255 characters.

## Runtime State And UI Usage

`CurrentExecution` stores the resolved display name in memory. The current execution summary uses it as the primary label, while `executorId` remains technical detail used by the executor registry.

Example current execution display:

```text
Current execution:
Output Artifact Smoke Test
```

The local last-task summary also uses the display name as the primary label:

```text
Last task:
Output Artifact Smoke Test / SUCCEEDED / 655 ms
```

AU0 did not redesign the JavaFX dashboard. It only changed the data shown in existing current execution and task history summary surfaces.

## Local History

Local SQLite history has a nullable `display_name` column. Existing history databases are migrated safely by adding the column when missing.

Behavior:

- old rows without `display_name` use history fallback labels,
- new rows store the resolved display name,
- retention remains bounded by the existing local history limit,
- API keys are not stored,
- lease tokens are not stored,
- full resolved configuration payloads are not stored,
- output file contents are not stored.

Old NO_OP history rows fall back to `NO-OP smoke test`. Other old rows without a stored display name fall back to their technical `executorId`.

## Logging

The claim log may include `displayName` for operator diagnostics:

```text
Claimed execution <executionId> displayName="Output Artifact Smoke Test" executor=localhive.docker.workload/1
```

The Agent must not log:

- the Worker API key,
- the execution lease token,
- the full resolved configuration payload,
- output file contents.

## Security

`displayName` is user-visible metadata, not a security boundary. It is not used as:

- a path,
- a filename,
- a shell argument,
- a Docker argument,
- an authorization input,
- an artifact storage key.

The value is not secret. Future hardening may normalize carriage returns, line feeds, or other control characters for cleaner logs and UI rendering.

## Compatibility

Master M6.2a added `displayName` to the claim response. Agent AU0 consumes the field and tolerates future additive claim response fields.

The Agent after AU0 works with both old and new Master claim responses. Runtime smoke testing passed with Master M6.2a and Agent AU0.

## Current Limitations

Current display metadata support is intentionally small:

- no full Agent UI redesign yet,
- no dedicated execution details panel yet,
- no output artifact count in UI yet,
- no upload progress UI yet,
- no control-character normalization yet,
- no Master frontend integration yet.

## Future Extensions

Possible future extensions include:

- Agent UI refresh,
- current execution detail card,
- output artifact count or status display,
- task history table with display names,
- technical details expansion with `executorId` and `executionId`,
- Master UI execution tables using the same display name,
- better names for Minecraft and Fabric workloads after those workloads are implemented,
- research or benchmark run names after those domains are designed.
