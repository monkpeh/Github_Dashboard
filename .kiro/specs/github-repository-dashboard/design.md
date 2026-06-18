# Design Document

## Overview

The GitHub Repository Dashboard is a full-stack monorepo with two deployable
parts:

- **Backend** — a Spring Boot application that owns the database, exposes a REST
  API (`API_Server`), and runs an hourly scheduled job (`Data_Collector`) that
  fetches stats from the GitHub REST API and stores them as historical
  snapshots.
- **Frontend** — a React + Vite single-page app (`Dashboard`) that renders
  charts, summary cards, and controls, talking to the backend over `/api`.

This document defines the architecture, data model, API contracts, component
breakdown, and the key design decisions. It is the bridge between
`requirements.md` and `tasks.md`.

---

## Architecture

```
┌─────────────────────────────────────────────┐        ┌──────────────────┐
│  Dashboard (React + Vite)   :5173             │        │   GitHub_API     │
│  ┌──────────┬──────────┬─────────────────┐    │        │   (REST v3)      │
│  │SummaryCard│LineChart │ BarChart        │    │        └────────▲─────────┘
│  │  Grid     │(stars)   │ (forks)         │    │                 │ HTTPS
│  ├──────────┴──────────┴─────────────────┤    │                 │ (hourly)
│  │ DateRangeSelector │ RepositoryToggles  │    │                 │
│  └──────────┬─────────────────────────────┘   │                 │
│             │ fetch /api/...                   │        ┌────────┴─────────┐
└─────────────┼────────────────────────────────-┘        │  Data_Collector  │
              │ Vite dev proxy → :8080                    │  @Scheduled 60m  │
              ▼                                           └────────┬─────────┘
┌─────────────────────────────────────────────┐                  │
│  API_Server (Spring Boot)   :8080            │                  │ writes
│  Controllers → Services → Repositories (JPA) │◄─────────────────┘
└──────────────────────┬──────────────────────┘
                       │ Hibernate (ddl-auto=update)
                       ▼
              ┌──────────────────┐
              │   H2 (file DB)   │   tracked_repo, repo_snapshot
              └──────────────────┘
```

**Architecture style:** Modular monolith. The collector and the API server live
in the **same** Spring Boot process (shared database, shared entities). This is
the simplest design that satisfies all requirements — no message queue or
separate services needed at this scale.

### Technology Stack

| Layer | Choice | Notes |
|-------|--------|-------|
| Language / runtime | **Java 21** | Current LTS |
| Backend framework | **Spring Boot 3.x** | Web, Data JPA, Validation |
| Build tool (backend) | **Maven** | |
| Persistence | **Spring Data JPA + Hibernate** | `ddl-auto=update` (Req 16) |
| Database | **H2, file-based** | Persists to `./data/dashboard.mv.db`; survives restarts |
| HTTP client (→ GitHub) | **Spring `RestClient`** | Synchronous, built into Spring 6 |
| Scheduler | **Spring `@Scheduled`** | Replaces "Spring Batch" — see Decision 1 |
| Frontend framework | **React 18 + Vite** | TypeScript |
| Charts | **Recharts** | Line chart + bar chart |
| Frontend HTTP | **fetch** via a small API client | Relative `/api` paths (Req 17) |

---

## Project Structure

```
Github_Dashboard/
├── backend/                         # Spring Boot app (Maven)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/dashboard/
│       │   ├── DashboardApplication.java
│       │   ├── model/               # JPA entities
│       │   │   ├── TrackedRepo.java
│       │   │   └── RepoSnapshot.java
│       │   ├── repository/          # Spring Data repositories
│       │   │   ├── TrackedRepoRepository.java
│       │   │   └── RepoSnapshotRepository.java
│       │   ├── dto/                 # API request/response shapes
│       │   ├── service/             # business logic
│       │   │   ├── RepoService.java
│       │   │   ├── SummaryService.java
│       │   │   └── GitHubClient.java
│       │   ├── collector/           # scheduled stats collection
│       │   │   └── StatsCollector.java
│       │   ├── web/                 # REST controllers + error handling
│       │   │   ├── RepoController.java
│       │   │   └── ApiExceptionHandler.java
│       │   └── config/              # seeding, GitHub config props
│       │       ├── DataSeeder.java
│       │       └── GitHubProperties.java
│       └── resources/
│           └── application.properties
└── frontend/                        # React + Vite app
    ├── package.json
    ├── vite.config.ts               # dev proxy /api → :8080
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── api/client.ts            # typed fetch wrappers
        ├── types.ts
        ├── hooks/                   # useRepos, useHistory, useSummary
        └── components/
            ├── SummaryCardGrid.tsx
            ├── StarsLineChart.tsx
            ├── ForksBarChart.tsx
            ├── DateRangeSelector.tsx
            ├── RepositoryToggles.tsx
            └── AddRepoForm.tsx
```

---

## Data Model

Two entities with a one-to-many relationship (Req 16.5).

### TrackedRepo (table `tracked_repo`)

| Column | Type | Constraints | Req |
|--------|------|-------------|-----|
| id | BIGINT | PK, auto-generated | 1.4 |
| owner | VARCHAR | not null, not blank | 1.1, 1.2 |
| repo_name | VARCHAR | not null, not blank | 1.1, 1.2 |
| added_at | TIMESTAMP | not null, set on create | 1.3 |

- Unique constraint on `(owner, repo_name)` to back the 409-conflict rule (Req 9.3).
- `@OneToMany(cascade = ALL, orphanRemoval = true)` to `RepoSnapshot` so deleting
  a repo deletes its snapshots (Req 1.5, 10.1).

### RepoSnapshot (table `repo_snapshot`)

| Column | Type | Constraints | Req |
|--------|------|-------------|-----|
| id | BIGINT | PK, auto-generated | 5.1 |
| tracked_repo_id | BIGINT | FK → tracked_repo.id, not null | 5.1, 5.2, 16.5 |
| star_count | INT | not null | 5.1 |
| fork_count | INT | not null | 5.1 |
| open_issues | INT | not null | 5.1 |
| fetched_at | TIMESTAMP (UTC) | not null | 5.1, 5.3 |
| last_commit_date | TIMESTAMP | nullable | 3.3 (see gap note) |

**Index:** composite index on `(tracked_repo_id, fetched_at)` from day one. It is
free now and is the single most important thing for history/summary query
performance as snapshots accumulate (see Scalability section).

> **Spec gap — latest commit date (RESOLVED).** Req 3.3 retrieves the *latest
> commit date*, but Req 5.1 / 16.4 omit it from the snapshot columns. **Decision:**
> add a nullable `last_commit_date` column (sourced from GitHub `pushed_at`) so the
> fetched value is persisted and usable. This extends the Req 5/16 column list.

Ordering for history/summary queries is by `fetched_at` (Req 5.4, 5.5, 7.4).

---

## GitHub API Integration

`GitHubClient` calls one endpoint per tracked repo:

- `GET https://api.github.com/repos/{owner}/{repo}`
  - `stargazers_count` → `starCount`
  - `forks_count` → `forkCount`
  - `open_issues_count` → `openIssues`
  - `pushed_at` → latest commit date (proxy for "latest commit date"; avoids a
    second `/commits` call and the extra rate-limit cost)

**Auth (Req 4):** if `app.github.token` is set, send
`Authorization: Bearer <token>`; otherwise call unauthenticated. The token is
read from `application.properties` and never hardcoded (Req 4.3, 4.4). For local
use it can be supplied via env var `APP_GITHUB_TOKEN` referenced in properties.

---

## REST API Contracts

Base path `/api`. All responses JSON. Errors use a consistent shape:
`{ "error": "<message>" }`.

### GET /api/repos — list all tracked repos with latest snapshot (Req 6)

`200 OK`
```json
[
  {
    "id": 1,
    "owner": "facebook",
    "repoName": "react",
    "addedAt": "2026-06-17T12:00:00Z",
    "latest": { "starCount": 228000, "forkCount": 46000, "openIssues": 700, "fetchedAt": "2026-06-17T13:00:00Z" }
  },
  { "id": 2, "owner": "torvalds", "repoName": "linux", "addedAt": "...", "latest": null }
]
```
`latest` is `null` when the repo has no snapshots yet (Req 6.3). The "latest
snapshot per repo" is assembled with a **single query** (not a per-repo loop) to
avoid an N+1 as the repo count grows.

### GET /api/repos/{id}/history?days=N — historical snapshots (Req 7)

- `days` defaults to **30** when absent (Req 7.2).
- Returns snapshots where `fetchedAt >= now - N days`, ordered by `fetchedAt` ASC (Req 7.4).
- Unknown id → `404`. Empty range → `200` with `[]` (Req 7.3, 7.5).

`200 OK`
```json
[
  { "fetchedAt": "2026-06-10T13:00:00Z", "starCount": 227500, "forkCount": 45900, "openIssues": 710 },
  { "fetchedAt": "2026-06-11T13:00:00Z", "starCount": 227650, "forkCount": 45920, "openIssues": 705 }
]
```

### GET /api/repos/{id}/summary — current stats + 7-day % change (Req 8)

- Compares the most recent snapshot to the snapshot **closest to 7 days ago**.
- Each `*Pct` is `null` when there's no ~7-day-old snapshot, or when the baseline
  value is 0 (avoid divide-by-zero) (Req 8.3, 8.5).
- Unknown id → `404` (Req 8.4).

`200 OK`
```json
{
  "current": { "starCount": 228000, "forkCount": 46000, "openIssues": 700, "fetchedAt": "..." },
  "change":  { "starsPct": 0.22, "forksPct": 0.18, "issuesPct": -1.4 }
}
```

### POST /api/repos — add a repo (Req 9)

Request: `{ "owner": "vuejs", "repoName": "vue" }`
- Missing/blank `owner` or `repoName` → `400` with error message (Req 9.2).
- Duplicate `(owner, repoName)` → `409` with error message (Req 9.3).
- Success → `201` with the created `TrackedRepo` (Req 9.4). It is automatically
  picked up by the next collection cycle since the collector reads all repos
  each run (Req 9.5).

### DELETE /api/repos/{id} — stop tracking (Req 10)

- Deletes the repo and (via cascade) all its snapshots (Req 10.1).
- Unknown id → `404` (Req 10.2). Success → `204` (Req 10.3).

---

## Backend Components

- **`StatsCollector`** — `@Scheduled(fixedRate = 3_600_000)` method. Loads all
  `TrackedRepo`s, calls `GitHubClient` per repo, saves a `RepoSnapshot` on
  success. A failure on one repo is logged and the loop continues (Req 3.5, 18).
  Also exposes a manual `collectNow()` used at startup so there's data
  immediately rather than after the first hour.
- **`GitHubClient`** — wraps `RestClient`, applies auth header, maps JSON →
  internal stats record, throws a typed exception on non-2xx / parse failures
  for the collector to log (Req 18.1–18.3).
- **`RepoService`** — CRUD for tracked repos, list-with-latest assembly,
  validation, duplicate check.
- **`SummaryService`** — the 7-day percentage-change math, including the
  null/zero guards.
- **`DataSeeder`** — `ApplicationRunner` that seeds the 5 repos only when the
  table is empty (Req 2).
- **`ApiExceptionHandler`** — `@RestControllerAdvice` mapping exceptions to
  400 / 404 / 409 with the `{ "error": ... }` body.

---

## Frontend Components

- **`App`** — owns top-level state: selected date range (default **7d**, Req 14.3),
  per-repo visibility toggles (all on by default, preserved across range changes —
  Req 15.4, 15.5), and the repo list.
- **`DateRangeSelector`** — 7 / 30 / 90 day buttons (Req 14).
- **`RepositoryToggles`** — checkbox per repo; drives visibility on all charts
  (Req 15).
- **`StarsLineChart`** (Recharts `LineChart`) — one colored line per visible repo;
  pulls each repo's `/history?days=N` (Req 11).
- **`ForksBarChart`** (Recharts `BarChart`) — latest fork count per visible repo,
  from `/api/repos` (Req 12).
- **`SummaryCardGrid`** — one card per repo with current stats and 7-day change,
  green/red/neutral coloring, from `/summary` (Req 13).
- **`AddRepoForm`** — owner + repoName inputs → `POST /api/repos`; surfaces
  400/409 errors (Req 9).
- **`api/client.ts`** — typed wrappers over `fetch`, all using relative `/api`
  paths (Req 17.5).

**Vite config:** dev server on **5173**, proxy `/api` → `http://localhost:8080`,
so the browser only ever hits same-origin `/api` and no backend CORS config is
needed (Req 17).

---

## Error Handling & Logging

- **Collector** uses SLF4J structured logging: INFO for normal cycles, ERROR
  with `{owner}/{repoName}`, HTTP status, and message on failures; continues to
  the next repo (Req 18.1–18.5).
- **API** errors are normalized by `ApiExceptionHandler` to the right status code
  and a JSON `{ "error": ... }` body.
- **DB persistence failures** in the collector are caught per-repo, logged with
  the repo identifier, and don't abort the cycle (Req 18.4).

---

## Security

- GitHub token comes only from configuration / env, never source (Req 4.3, 4.4).
- No user auth in v1 — this is a local, single-user dashboard (consistent with
  the requirements, which define no login). Noted as a deliberate scope choice.
- `.gitignore` will exclude `application.properties` secrets / the H2 data file.

---

## Testing Strategy

- **Unit:** `SummaryService` percentage math (positive, negative, null-baseline,
  divide-by-zero); `GitHubClient` JSON mapping; validation rules.
- **Integration (`@SpringBootTest` + MockMvc):** each endpoint's happy path and
  error codes (400/404/409/204); seeding-on-empty behavior; cascade delete.
- **Collector:** GitHub client mocked — verify a failing repo doesn't stop the
  others and that a snapshot is written on success.
- **Frontend:** component tests for chart visibility toggling and date-range
  switching; API client tested against mocked responses.
- **Manual QA:** run both apps, confirm seeded data appears, add/remove a repo,
  toggle repos, switch ranges.

---

## Key Design Decisions

**Decision 1 — `@Scheduled` instead of Spring Batch.**
The glossary calls the collector a "Spring Batch scheduled job," but the actual
work — "every 60 minutes, fetch N repos, save a row each" — is a simple periodic
task. Spring Batch adds a job repository, chunk/step model, and readers/writers
that bring no benefit here. *Chosen:* a single `@Scheduled` method.
*Alternative:* Spring Batch (rejected as overkill). *Revisit if* collection grows
into a multi-stage pipeline needing restartability/checkpointing.

**Decision 2 — H2 file-based database.**
*Chosen:* H2 persisting to a local file, so snapshots survive restarts with zero
external setup. *Alternative:* PostgreSQL (more production-realistic, but needs a
running server). The JPA/entity layer is DB-agnostic, so switching later is a
config change.

**Decision 3 — `pushed_at` as the "latest commit date" source.**
Avoids a second GitHub call per repo (`/commits`), saving rate limit. Close
enough for an activity signal.

**Decision 4 — Collect once at startup.**
Beyond the hourly schedule, run one collection on boot so a freshly seeded DB
shows real data immediately rather than after the first hour. Satisfies the
spirit of Req 2 (see sample data immediately).

---

## Scalability & Future Refactoring

The design intentionally keeps each scaling bottleneck isolated to one component,
so growth in `TrackedRepo` count is handled by swapping parts, never a rewrite.

| Bottleneck | Limit | Refactor (isolated to) | Difficulty |
|---|---|---|---|
| GitHub rate limit | ~5,000 repos/hr authenticated (1 req/repo) | ETags/conditional requests or GraphQL batching — `GitHubClient` | Medium |
| Sequential collection | Slow at thousands of repos | Thread-pool parallelism, or migrate to **Spring Batch** for partitioning/restartability — `StatsCollector` | Easy |
| DB growth | ~8.7M rows/yr at 1,000 repos hourly; H2 strains | Swap H2 → PostgreSQL/TimescaleDB (JPA is DB-agnostic) | Easy (config) |
| Query speed | Scans grow with snapshots | Composite index `(tracked_repo_id, fetched_at)` — **already in data model** | Done |
| Old-data bloat | Years of unused hourly data | Retention + downsampling (hourly→daily rollups) — additive | Medium |
| Frontend N+1 fetch | `/history` per repo painful at 100s | Batch history endpoint + repo search/pagination — additive | Easy |
| API N+1 query | "latest per repo" loop | Single-query assembly — **already in API design** | Done |

**Baked in now (free, prevents future pain):** the composite index, a single-query
`GET /api/repos`, and `GitHubClient` as the sole GitHub touch-point. **Deferred
until needed:** Postgres migration, collector parallelism/Batch, data retention,
frontend pagination.

---

## Requirements Traceability

| Req | Covered by |
|-----|-----------|
| 1, 16 | `TrackedRepo` entity + repository + unique constraint + cascade |
| 2 | `DataSeeder` |
| 3, 4, 18 | `StatsCollector` + `GitHubClient` + `GitHubProperties` |
| 5, 16 | `RepoSnapshot` entity + repository |
| 6 | `GET /api/repos` + `RepoService` |
| 7 | `GET /api/repos/{id}/history` |
| 8 | `GET /api/repos/{id}/summary` + `SummaryService` |
| 9 | `POST /api/repos` + `ApiExceptionHandler` |
| 10 | `DELETE /api/repos/{id}` + cascade |
| 11 | `StarsLineChart` |
| 12 | `ForksBarChart` |
| 13 | `SummaryCardGrid` |
| 14 | `DateRangeSelector` + `App` state |
| 15 | `RepositoryToggles` + `App` state |
| 17 | Vite proxy + relative `/api` paths |
