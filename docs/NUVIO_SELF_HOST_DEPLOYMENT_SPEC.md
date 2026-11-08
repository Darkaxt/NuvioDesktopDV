# NuvioDV private backend deployment specification

Date: 2026-11-08. Status: specification only; deployment is explicitly deferred.

## Scope and authority

Plan a stable personal Nuvio backend on the existing Oracle server for both
NuvioMobileDV and NuvioDesktopDV. This document does not authorize deploying it,
changing DNS, migrating accounts, changing schedules, or rebuilding clients today.
The account dashboard is required; the full Nuvio streaming web application is not.
Existing fork identity, credentials, profiles and local RPDB behavior remain intact.

## Verified baseline and intended topology

The Beelink SER5 Max chat inspected Oracle on 2026-11-08: Oracle Linux 9.6,
ARM64, four CPUs, approximately 22 GiB usable RAM with 11 GiB available,
and 65 GiB free on the root filesystem. These are observations, not reservations;
recheck capacity before deployment. Docker 29.3.1 and Compose 5.1.1 are installed.
Traefik already owns public ports 80/443 and certificate renewal. Existing services
use separate `/opt/<service>` Compose projects. Administration uses native SSH
over Tailscale. AIOStreams has a separate Tailscale egress sidecar; the new backend
must not inherit it or change host routing.

Use the official `NuvioMedia/self-host` project in a separate `/opt/nuvio` stack,
with private database networking and independent persistent data. Use its
external-proxy deployment mode and a dedicated hostname under the existing domain;
the exact hostname is a deployment input, not assigned by this document.

```text
Android/Desktop -- HTTPS --> existing Traefik --> Nuvio gateway
                                               |-- account dashboard
                                               |-- auth / API / functions
                                               |-- PostgreSQL
                                               `-- storage metadata + files
```

Use the documented dedicated proxy network for Docker Traefik. Do not expose
database, pooler, Studio or internal API ports publicly. Studio remains disabled
unless needed for a specific administration operation. The supporting API/auth/
storage/function services are required even without the full web application.
Validate every selected image on linux/arm64 before production activation.

## Requirements and acceptance criteria

### B1. Independent, reproducible deployment

Pin the backend source commit and every image digest, including the account
dashboard and transitive services. Record Compose overrides, schema/migration
revision, required environment names and restore instructions; never commit secrets.
Do not run production from floating `latest` tags. Resource limits must follow
measured idle, sync and account-import use while preserving existing workloads.
Acceptance: a clean staged deployment from the recorded pins passes discovery,
authentication, authorized API access and storage checks on ARM64.

### B2. Upgrade only when the supported apps are ready

Production backend changes require a reviewed compatibility record in version
control. Use a small deployment manifest, not a new orchestration service. Record:

- current and candidate backend commits, image digests and migration revisions;
- supported Android and Windows versions, source commits and artifact hashes;
- which phone/tablet/Windows installations still need compatibility support;
- verified old-client/new-server and new-client/old-server results;
- backup/restore evidence and exact promotion/rollback procedure.

An available upstream server release, successful image pull, healthy container,
new client source commit or green build alone MUST NOT promote production.
The candidate client artifacts must be available through the forks' normal
release channels and their required installed versions verified before removing
old-server compatibility. An offline/stale device remains supported or blocks a
breaking promotion; do not assume the newest GitHub release is installed everywhere.

Keep production pinned when fork maintenance pauses. Exclude this stack from
generic image auto-updaters, unattended `git pull`, migration-on-start against
unreviewed source, and any existing service update cron. Monitoring may discover
and report candidates but must not change production pins or apply migrations.
Restarting the pinned stack must not download or activate a newer revision.

Use expand/contract changes when the actual migration permits them: introduce
backward-compatible server support, release/verify clients, then remove obsolete
contracts only after every supported client is ready. If a change cannot support
both versions, retain the old server until a coordinated maintenance cutover can
satisfy the same readiness gate. Keep client releases compatible with the current
server while they are being distributed. Backend advancement and ordinary fork
upstream merges are separate operations.

Acceptance: hold one client on an older supported version, introduce a candidate
in staging, and show that production remains unchanged until all promotion checks
pass. Reject a deliberately incompatible candidate. After a restart, show the
same production digests/schema. A maintenance pause must not change that result.

### B3. Authentication, account migration and data continuity

Use supported custom-server discovery/configuration in both forks. Keep public
publishable keys in clients and service-role/database secrets only on the server.
Do not wrap native auth/API endpoints in proxy Basic Auth. Restrict signup using
supported backend controls. Configure recovery/verification SMTP deliberately;
auto-confirmed email alone does not establish a working recovery path.

Use the official account importer after a verified source export and target
baseline. Do not replace existing target data without reviewing that exact target.
Verify profiles, add-ons, library, collections, watch progress, settings and
referenced artwork. Reconnect trackers/providers/security factors as required;
do not promise imported tokens remain valid. Preserve the original cloud account
for recovery until migration acceptance. Switching servers is not bidirectional
cloud mirroring. Do not copy local RPDB keys into new cloud payloads implicitly.

Acceptance: log in, refresh credentials, restart each client, change progress on
one device and observe it on another, validate artwork/profile separation and
account recovery. Verify the actual phone/tablet identity through the device gate.

### B4. Consistent backups and recovery

Back up PostgreSQL, storage objects, deployment pins/configuration and necessary
secrets as one recoverable set. Keep an encrypted off-host recovery copy with
defined retention and a verified restore; existing on-host `/opt/backups` sets
are not evidence of off-host protection. Protect credentials from logs/source.

Before promotion, create and verify a backup, review migration reversibility and
test a restore in an isolated target. An old image with an incompatible new schema
is not a rollback. If restoration is required, quiesce writes through supported
operations, restore the matching database/storage/configuration, and verify account
and sync behavior before accepting traffic. Record write-cutover and recovery
points so post-upgrade writes are not silently lost. Define acceptable downtime
and recovery-point expectations before production cutover; do not invent zero-loss
or zero-downtime guarantees.

Acceptance: restore a representative account and its artwork in isolation and
demonstrate login, settings and watch-progress continuity with supported clients.

### B5. Operations and supported capabilities

Monitor discovery, auth/API/storage health, disk usage, backup results and failed
sync operations. Notify on actionable changes; healthy checks remain quiet.
Validate tracker callbacks against the selected hostname and actual credentials.
Record unsupported cloud features explicitly rather than claiming cloud parity.
Security fixes still require compatibility review; a time-critical vulnerability
needs a separately recorded incident decision, not a silently bypassed gate.

## Future execution plan

No implementation stage is active. All stages below are NOT STARTED.

| Stage | Requirements | Completion evidence |
| --- | --- | --- |
| Isolated ARM64 deployment | B1, staged parts of B3/B5 | Pinned stack, private routing, real account/API/storage workflow |
| Compatibility and recovery qualification | B2, B4 | Client matrix, rejected incompatible candidate, tested complete restore |
| Coordinated migration and activation | B2, B3, B5 | Ready installed clients, verified migration/callbacks, monitored production |

Missing hostname/SMTP details, selected release pins, resource measurements,
client compatibility results and recovery objectives must be resolved in their
assigned stage before deployment acceptance. They are not completed work.

## References

- https://github.com/NuvioMedia/self-host
- https://github.com/NuvioMedia/self-host/blob/main/docs/architecture.md
- https://github.com/NuvioMedia/self-host/blob/main/docs/external-proxy.md
- https://github.com/NuvioMedia/self-host/blob/main/docs/client-configuration.md
- https://github.com/NuvioMedia/self-host/blob/main/SECURITY.md

