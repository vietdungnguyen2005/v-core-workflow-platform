# V-Core — Configurable Team Workflows

A team workspace that adapts to different approval processes while keeping work limits, access permissions and project history intact.

[Live demo](https://v-core-saas.pages.dev/) · [Evidence and screenshots](docs/fde/acceptance-tests.md) · [Technical design](docs/fde/solution-design.md) · [Run locally](docs/developer-guide.md)

## Problem & Solution

**Problem:** A software team and an operations team do not move work through the same stages. Hard-coding one workflow forces teams to work around the tool; changing it later can unexpectedly alter projects already in progress. Unreliable connections and simultaneous edits can also create duplicate projects or overfill an approval queue.

**Solution:** V-Core lets an administrator publish a workflow version and use it when creating a project. Each project keeps its selected configuration. Teams can assign and move tasks through their own stages, with visible capacity limits and a recorded history of changes.

Two fictional customer scenarios demonstrate the same application serving different needs:

- **Software delivery:** Backlog → In Progress → Review → Done.
- **Business operations:** Received → Validation → Approval → Completed.

<details>
<summary>View the workflow and capacity-conflict screenshot</summary>

![Operations workflow with an approval-capacity conflict](docs/fde/evidence/client-b-wip-conflict.png)

</details>

## Engineering

The backend is designed around protecting business rules, not just displaying a board.

- **Safe retries:** Repeating the same project-creation request returns the original result instead of creating another project.
- **Controlled concurrent changes:** The database checks task versions and locks the relevant workflow capacity before accepting a move.
- **Stable configuration:** Published workflow versions cannot be edited through the API. New versions do not silently change existing projects.
- **Workspace boundaries:** Access is checked against the user's membership and role; knowing another workspace's ID does not grant access.
- **Traceable delivery:** Project setup, workflow columns and the creation history are saved together—or rolled back together if setup fails.

**Core stack:** Java 21, Spring Boot, PostgreSQL, React/Next.js and TypeScript. Redis is configured as a supporting service; workspace authorization does not depend on a Redis membership cache. Docker and GitHub Actions support repeatable delivery.

## Impact & Results

These are **observed business-state outcomes from synthetic local scenarios**, not customer adoption figures or unit-test counts. Recorded on 23 October 2026 with Java, a real PostgreSQL/Redis environment and browser-driven acceptance.

| Situation | Measured outcome | Why it matters |
| --- | --- | --- |
| Two teams need different processes | **2 distinct workflows**, each provisioning **1 project, 1 sprint and 4 correctly ordered columns** | Different processes run in one application without separate customer-specific codebases. |
| A project is saved but its response is lost | Retrying the original request leaves **1 project and 1 creation-history event** | A lost acknowledgement does not duplicate the project or its setup. |
| An approval stage has a capacity of 1 | A second task is refused while occupancy stays at **1**; it can enter after the first task completes | Work cannot silently exceed the configured approval limit. |
| A workflow receives a new version | Existing project limit remains **2**, while the new template version specifies **9** | Publishing a new configuration does not rewrite an existing project's operating rules. |

**Evidence:** [Recorded acceptance outcomes](docs/fde/acceptance-tests.md), especially AC-06, AC-07 and AC-11–13; [reproducible browser scenario](tools/qa_customer_workflow.py). These demonstrate correct setup and recovery, **not** a measured reduction in staff hours or a production reliability percentage.

## Explore the project

- **For a quick walkthrough:** [Customer scenario and demo script](docs/fde/customer-demo-script.md).
- **For engineering detail:** [Architecture](docs/architecture.md) · [Design and trade-offs](docs/fde/solution-design.md).
- **For setup and verification:** [Developer guide](docs/developer-guide.md).
- **For operational limits:** [Deployment and support handoff](docs/fde/deployment-handoff.md).

### Demo boundaries

This is a personal portfolio project; the customer scenarios are fictional. The demo uses Cloudflare for the frontend and Render for the Java backend, with Neon PostgreSQL and Upstash Redis. Free-host startup can delay the first request. Editing requires a deliberately provisioned operator account; credentials are not published.

The results above come from controlled local exercises, not live production traffic. The project does not claim real customer deployments, automatic migration of active workflows or exactly-once operation delivery.

---

Former repository name: `Project1`. The new name changes the repository address, not the live-demo URL.
