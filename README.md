# V-Core — Team Task Management

A task-management application for teams with different workflows, shared capacity limits and a traceable history of changes.

[Live demo](https://v-core-saas.pages.dev/) · [Recorded results](docs/evidence/2026-11-09/README.md) · [Screenshots](docs/fde/acceptance-tests.md) · [Run locally](docs/developer-guide.md)

## Problem & Solution

**Problem:** Simultaneous task updates can overfill a work queue. Retrying after losing a response can duplicate a change or create a project twice, while an interrupted setup can leave incomplete records.

**Solution:** V-Core connects task boards to backend APIs that enforce capacity, version and workspace-permission checks. Administrators publish workflow versions and create projects from a selected template; later versions do not silently rewrite existing projects.

Software-delivery and business-approval workflows demonstrate different needs. Both are fictional scenarios, not customer engagements.

## Engineering

- **Feature integration:** Task boards, project setup and workflow configuration connect to Java APIs and PostgreSQL.
- **Concurrent updates:** Column locks protect capacity; task versions reject stale changes. Request identities let retries return the original logical result.
- **Consistent setup:** Project records, workflow columns and creation history commit together or roll back together.
- **Access and traceability:** Server-side roles control workspace changes; activity and outbox records preserve accepted operations.

**Stack:** Java 21, Spring Boot, PostgreSQL, Redis, TypeScript, Next.js/React, Docker and GitHub Actions.

## Impact & Results

**Controlled local measurements with synthetic tasks and disposable PostgreSQL/Redis—not production traffic or performance claims.**

| Problem addressed | Verified result | Practical value |
|---|---|---|
| Duplicate requests for the same change | At **2, 5, 10 and 20 concurrent requests**, each wave committed **one change, one activity record and one outbox record** | Retrying does not become a second task change. |
| Independent moves competing for one remaining slot | **One move accepted; the rest rejected**, with occupancy staying at **3** at every tested level | Accepted work stays within the configured capacity. |

Both workloads ran **three times per level: 24 waves and 222 requests**, with **zero unexpected errors, extra activity records or WIP overruns** in that scope. These are business-state checks, not unit-test counts. Concurrent requests are not requests per second.

[Raw JSON/CSV, methodology and reproduction](docs/evidence/2026-11-09/README.md) preserve the measured source revision. Separate [provisioning acceptance evidence](docs/fde/acceptance-tests.md) covers concurrent project creation, rollback and revision stability.

## Explore the project

- [Workflow walkthrough](docs/fde/customer-demo-script.md)
- [Architecture](docs/architecture.md) · [Design and trade-offs](docs/fde/solution-design.md)
- [Developer guide](docs/developer-guide.md) · [Deployment and support](docs/fde/deployment-handoff.md)

## Scope & Demo Limits

This is a personal portfolio project. The free-hosted backend may cold-start; editing requires a provisioned operator account. Credentials are not published. Local outcomes do not establish customer adoption, productivity gains or production reliability. An outbox record does not guarantee exactly-once SSE delivery.

Former repository name: Project1; the live-demo URL is unchanged.
