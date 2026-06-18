# Implementation Plan

Phased, dependency-ordered build tasks. Each task is small enough for a single
focused session. Check items off as we complete them. `_Requirements:_` links each
task back to `requirements.md`. We build top to bottom; later phases depend on
earlier ones.

---

## Phase 0 — Project Setup

- [ ] 0.1 Give the project its own git repository
  - Detach from the home-directory git root; init a repo scoped to
    `Github_Dashboard/`, pointed at the existing `Github_Dashboard` remote.
  - Add a root `.gitignore` (Java/Maven, Node, IDE, H2 `data/`, secrets).
  - _Requirements: foundational (no REQ — enables clean version control)_

- [ ] 0.2 Scaffold the Spring Boot backend (Maven)
  - Java 21, Spring Boot 3.x with Web, Data JPA, Validation, H2.
  - `backend/pom.xml`, `DashboardApplication.java`, empty `application.properties`.
  - Verify it boots on **:8080**.
  - _Requirements: 17.2_

- [ ] 0.3 Scaffold the React + Vite frontend (TypeScript)
  - `frontend/` via Vite, add Recharts.
  - Configure dev server on **:5173** with `/api` proxy → `http://localhost:8080`.
  - Verify it boots on **:5173**.
  - _Requirements: 17.1, 17.3, 17.5_

---

## Phase 1 — Persistence Layer (backend)

- [ ] 1.1 Create the `TrackedRepo` entity
  - Fields: id (auto), owner, repoName, addedAt; `@NotBlank` on owner/repoName;
    unique constraint on `(owner, repoName)`; `addedAt` set on persist.
  - `@OneToMany` to `RepoSnapshot` with cascade ALL + orphanRemoval.
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 9.3, 16.3, 16.5_

- [ ] 1.2 Create the `RepoSnapshot` entity
  - Fields: id, trackedRepo (FK, not null), starCount, forkCount, openIssues,
    fetchedAt (UTC), lastCommitDate (nullable).
  - Composite index on `(tracked_repo_id, fetched_at)`.
  - _Requirements: 5.1, 5.2, 5.3, 16.4, 16.5_

- [ ] 1.3 Create Spring Data repositories
  - `TrackedRepoRepository` (incl. existsByOwnerAndRepoName).
  - `RepoSnapshotRepository` with queries: history within N days ordered by
    fetchedAt ASC; latest snapshot per repo (single query); snapshot closest to
    a target timestamp.
  - _Requirements: 5.4, 5.5, 6.2, 7.1, 7.4, 8.2_

- [ ] 1.4 Configure H2 + Hibernate
  - File-based H2 at `./data/dashboard`, `ddl-auto=update`, H2 console enabled
    for dev. Confirm both tables + FK auto-create on boot.
  - _Requirements: 16.1, 16.2_

---

## Phase 2 — Data Collection (backend)

- [ ] 2.1 GitHub configuration properties
  - Bind `app.github.token` from `application.properties` (env-backed); never
    hardcoded.
  - _Requirements: 4.3, 4.4_

- [ ] 2.2 `GitHubClient` — fetch repo stats
  - `RestClient` call to `GET /repos/{owner}/{repo}`; map stargazers_count,
    forks_count, open_issues_count, pushed_at. Attach Bearer token when present,
    unauthenticated otherwise. Throw typed errors on non-2xx / parse failure.
  - _Requirements: 3.3, 4.1, 4.2, 18.1, 18.3_

- [ ] 2.3 `StatsCollector` — scheduled collection
  - `@Scheduled(fixedRate = 1h)`: load all repos, fetch each, save a snapshot on
    success. On any per-repo error (HTTP, timeout, DB), log with repo id + detail
    and continue. Expose `collectNow()`.
  - _Requirements: 3.1, 3.2, 3.4, 3.5, 18.1, 18.2, 18.4, 18.5_

- [ ] 2.4 First-run seeding + startup collection
  - `DataSeeder` (ApplicationRunner): if no repos exist, seed the 5 specified;
    skip if any exist. Then trigger `collectNow()` so data shows immediately.
  - _Requirements: 2.1, 2.2, 2.3_

---

## Phase 3 — REST API (backend)

- [ ] 3.1 DTOs
  - RepoWithLatest, SnapshotDto, SummaryDto (current + change), CreateRepoRequest.
  - _Requirements: 6.1, 7.1, 8.1, 9.1_

- [ ] 3.2 `RepoService` — CRUD + list assembly
  - List repos with latest snapshot (single query, null when none); create with
    validation + duplicate check; delete with cascade.
  - _Requirements: 1.2, 1.5, 6.2, 6.3, 9.1, 9.3, 10.1_

- [ ] 3.3 `SummaryService` — 7-day percentage change
  - Compare latest vs snapshot closest to 7 days ago; null when no baseline or
    baseline is 0 (divide-by-zero guard).
  - _Requirements: 8.2, 8.3, 8.5_

- [ ] 3.4 `RepoController` — endpoints
  - GET /api/repos; GET /{id}/history?days=30; GET /{id}/summary;
    POST /api/repos; DELETE /{id}. Correct status codes (200/201/204/400/404/409).
  - _Requirements: 6.1, 6.4, 7.1, 7.2, 7.3, 7.5, 8.1, 8.4, 9.2, 9.4, 9.5, 10.2, 10.3, 10.4_

- [ ] 3.5 `ApiExceptionHandler`
  - `@RestControllerAdvice` → 400 (validation), 404 (not found), 409 (duplicate),
    each with `{ "error": ... }`.
  - _Requirements: 9.2, 9.3, 7.3, 8.4, 10.2_

---

## Phase 4 — Frontend Foundation

- [ ] 4.1 Types + API client
  - TypeScript types mirroring the DTOs; `api/client.ts` fetch wrappers using
    relative `/api` paths (getRepos, getHistory, getSummary, addRepo, deleteRepo).
  - _Requirements: 17.5_

- [ ] 4.2 App shell + top-level state
  - `App` holds date range (default 7d), per-repo visibility (all on, preserved
    across range changes), and the repo list; loads repos on mount.
  - _Requirements: 14.3, 15.4, 15.5_

---

## Phase 5 — Frontend Features

- [ ] 5.1 `DateRangeSelector` (7/30/90d)
  - _Requirements: 14.1, 14.2, 14.4_

- [ ] 5.2 `RepositoryToggles`
  - Checkbox per repo driving visibility across all charts.
  - _Requirements: 15.1, 15.2, 15.3_

- [ ] 5.3 `StarsLineChart` (Recharts line)
  - One colored line per visible repo from `/history?days=N`; updates on range
    change.
  - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_

- [ ] 5.4 `ForksBarChart` (Recharts bar)
  - Latest fork count per visible repo from `/api/repos`.
  - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_

- [ ] 5.5 `SummaryCardGrid`
  - Card per repo: current stats + 7-day % change; green/red/neutral; from
    `/summary`.
  - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6_

- [ ] 5.6 `AddRepoForm` + remove control
  - POST new repo, surface 400/409; delete control per repo; refresh on change.
  - _Requirements: 9.1, 9.2, 9.4, 10.1, 10.3_

---

## Phase 6 — Testing & Polish

- [ ] 6.1 Backend unit tests
  - SummaryService math (positive/negative/null-baseline/zero); GitHubClient
    mapping; validation rules.
  - _Requirements: 8.2, 8.3, 8.5, 1.2_

- [ ] 6.2 Backend integration tests (MockMvc)
  - Each endpoint happy path + error codes; seeding-on-empty; cascade delete.
  - _Requirements: 2.1, 2.2, 6–10_

- [ ] 6.3 Collector resilience test
  - Mocked GitHub: one failing repo doesn't stop others; snapshot written on
    success.
  - _Requirements: 3.5, 18.1_

- [ ] 6.4 End-to-end manual QA
  - Run both apps; confirm seeded data, add/remove, toggles, range switching.
  - _Requirements: all UI requirements_
