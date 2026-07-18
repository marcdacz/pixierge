# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

Pixierge is a Spring Boot API plus React TypeScript web app.

- Backend: `backend/` uses Java 21, Maven, Spring Boot, Flyway, PostgreSQL, and Querydsl.
- Frontend: `frontend/` uses React, TypeScript, Vite, npm, and Vitest.
- Local stack: `docker-compose.yml` runs the app and database.

## Commands

- Start the local stack: `docker compose up --build`
- Backend unit tests: `cd backend && mvn clean test`
- Backend unit + integration/REST tests: `cd backend && mvn clean verify`
- Frontend dependencies: `cd frontend && npm install`
- Frontend tests: `cd frontend && npm test`
- Frontend build: `cd frontend && npm run build`
- Frontend E2E tests: `cd frontend && npm run test:e2e`
- Frontend visual tests: `cd frontend && npm run test:vr`
- Validate Compose example wiring: `docker compose --env-file .env.example config`

If Maven cannot write to the default local cache, use:

```sh
cd backend && mvn -Dmaven.repo.local=/private/tmp/pixierge-m2 clean verify
```

## Conventions

- Prefer small, focused changes that match the existing code style.
- Tests must cover meaningful behavior, risk, or regression cases. Do not add getter/setter, DTO-only, or other coverage-padding tests just to raise a percentage.
- Prefer focused unit tests for business logic and boundary/error behavior; use integration tests for database, Querydsl, migration, security, and cross-layer behavior.
- Use Maven for backend build work.
- Use Querydsl for repository SQL access instead of handwritten SQL strings.
- Import Java types normally; avoid fully qualified class names in method signatures, fields, and local declarations unless needed to resolve a real naming conflict.
- Keep backend classes purposeful. Add or keep a class when it owns testable behavior, a stable domain/API boundary, an integration boundary, real reuse, or meaningful caller clarity.
- Avoid backend transport ceremony. Prefer grouped package-local request/response records or nested response subrecords for endpoint-local shapes; do not create one-file DTOs by default.
- Merge thin controllers that only split URLs while delegating to the same service without HTTP-specific policy. Keep behavior-bearing services, repositories, parsers, scan helpers, security, and scheduler classes separate.
- Add Flyway migrations under `backend/src/main/resources/db/migration/`.
- During early development, when migrations are squashed or deleted, run backend tests with `mvn clean verify` so stale copied migrations are removed from `target/classes` and integration tests see the current schema.
- Keep React code typed and covered by nearby Vitest tests when behavior changes.
- Prefer shared React components and small feature components over repeating markup, styling, or state logic across screens.
- Keep frontend code minimal: add abstractions only when they remove real duplication or match an established local pattern.
- Avoid frontend layout magic numbers; use named constants, CSS variables, or theme tokens for repeated structural dimensions.
- For UI-facing acceptance criteria, include unit tests, Playwright E2E tests, and Playwright visual regression tests.
- Write Playwright E2E tests around user-observable workflows with deterministic fixtures or mocks; keep tests independent of order, real user data, and external services.
- Prefer stable, app-owned `data-testid` locators for E2E targets, then role/label locators when they clearly express the user-facing contract; avoid brittle CSS selectors and unscoped text matches.
- Put repeated Playwright locators and workflow actions behind small page/component objects or helpers near the specs; keep assertions in the spec when they describe the behavior under test.
- Avoid fixed waits in Playwright tests; wait for visible UI state, URLs, network mocks, or other behavior that proves the workflow is ready.
- When `.plans/` defines an active slice, treat its test gate as required; skipped environment-dependent tests are unverified, not passed.
- For generated filesystem caches, enforce storage-root containment and use atomic, concurrency-safe writes and database upserts.
- Watch for Spring `TaskExecutor` bean ambiguity after adding executors, wrap Querydsl connection access in repository/service transaction boundaries, compare filesystem timestamps at database-supported precision, and make queued scan completion wait for all active follow-up jobs before missing-file reconciliation.
