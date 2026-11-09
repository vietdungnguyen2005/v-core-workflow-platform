# Task concurrency evidence

This package supports the README's capacity and duplicate-request claims. It measures application behavior with synthetic tasks, not real customer activity or production performance.

## Recorded snapshot

- Measured backend revision: [`44a06a12d031a52dbca72f55ea60dfdbd16434c3`](https://github.com/vietdungnguyen2005/v-core-workflow-platform/commit/44a06a12d031a52dbca72f55ea60dfdbd16434c3).
- Recorded on 9 November 2026, 01:52 UTC; published in this evidence package without changing its recorded revision.
- Java 21, Spring MockMvc, disposable Testcontainers PostgreSQL 17.6 and Redis 8.2. Requests exercise application controllers, services and database transactions; they do not traverse a browser or public network.
- Machine-readable observations: [JSON](concurrency-results.json) and [CSV](concurrency-results.csv).

## Method and results

Each workload uses 2, 5, 10 and 20 concurrent requests, repeated three times. Fixtures reset between waves. Authentication is prepared before the start barrier; this does not benchmark login.

| Workload | Input | Required and observed result in each wave |
|---|---|---|
| Same-operation retry | Same task, move payload and idempotency key | All responses HTTP 200; one task-version increment, activity, outbox and idempotency record. Target occupancy ends at 2. |
| Independent moves | Distinct tasks and keys; destination starts at 2 tasks with a limit of 3 | One HTTP 200; all remaining responses HTTP 409. One version increment, activity, outbox and idempotency record; occupancy ends at 3. |

Across **24 waves / 222 requests**, there were **123 successful responses and 99 expected business conflicts**, with zero unexpected errors, extra activity records or WIP overruns. Successful retry responses are not additional accepted moves. `wipOverage: null` in the retry workload means that metric is not applicable there.

These observations support bounded correctness claims under the measured contention. They do **not** establish requests per second, latency percentiles, production availability, productivity savings or exactly-once event delivery.

## Reproduce

Requirements: PowerShell 7+, Java 21 and a working Docker engine. Maven Wrapper and container images require network access on first use. No hosted credentials or production database are required.

From the repository root:

```powershell
pwsh -File ./tools/evidence/run-concurrency.ps1
```

The [runner](../../../tools/evidence/run-concurrency.ps1) copies the unchanged [application fixture](../../../backend/src/test/java/dev/vcore/VCoreApplicationTest.java) and the [measurement extension](../../../tools/evidence/PortfolioConcurrencyEvidenceTest.java) into a generated test build. Reflection accesses private fixture helpers; this adapter is intentionally coupled to that fixture and may need updating if it changes. Product sources and the original POM are not modified.

Outputs go to ignored `backend/target/portfolio-evidence/concurrency/`: timestamped JSON, a local execution log, an attempt manifest with fixture/harness hashes and Surefire reports under `build/target/`. The runner rejects tracked backend changes, skipped/failed execution and a changed source snapshot. It uses the current checkout's SHA; rerunning a newer revision does not recreate the historical SHA.

## Provenance and limits

The recorded JSON/CSV came from a measurement-only extension outside the application source tree. The published extension preserves its workload and assertions; class/environment-variable names, synthetic labels and path handling were generalized for independent reruns. The repository-relative runner was also executed successfully before publication. Rerun artifacts remain separate from the historical observations above.

An earlier harness-scaffolding attempt stopped with an unimplemented helper. It was not a product failure and is not counted in the successful observations. No application change was made to produce this result. Raw machine logs and private preparation documents are deliberately excluded.

This is a bounded concurrency exercise, not the full backend suite or an end-to-end browser test. Separate [provisioning acceptance evidence](../../fde/acceptance-tests.md) documents project creation, rollback and workflow-version behavior; those are not measured by this package.
