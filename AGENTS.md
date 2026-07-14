# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

Pixierge is a Spring Boot API plus React TypeScript web app.

- Backend: `backend/` uses Java 21, Maven, Spring Boot, Flyway, PostgreSQL, and Querydsl.
- Frontend: `frontend/` uses React, TypeScript, Vite, npm, and Vitest.
- Local stack: `docker-compose.yml` runs the app and database.

## Commands

- Start the local stack: `docker compose up --build`
- Backend tests: `cd backend && mvn clean test`
- Frontend dependencies: `cd frontend && npm install`
- Frontend tests: `cd frontend && npm test`
- Frontend build: `cd frontend && npm run build`
- Frontend E2E tests: `cd frontend && npm run test:e2e`
- Frontend visual tests: `cd frontend && npm run test:vr`
- Validate Compose example wiring: `docker compose --env-file .env.example config`

If Maven cannot write to the default local cache, use:

```sh
cd backend && mvn -Dmaven.repo.local=/private/tmp/pixierge-m2 clean test
```

## Conventions

- Prefer small, focused changes that match the existing code style.
- Tests must cover meaningful behavior, risk, or regression cases. Do not add getter/setter, DTO-only, or other coverage-padding tests just to raise a percentage.
- Prefer focused unit tests for business logic and boundary/error behavior; use integration tests for database, Querydsl, migration, security, and cross-layer behavior.
- Use Maven for backend build work.
- Use Querydsl for repository SQL access instead of handwritten SQL strings.
- Import Java types normally; avoid fully qualified class names in method signatures, fields, and local declarations unless needed to resolve a real naming conflict.
- Add Flyway migrations under `backend/src/main/resources/db/migration/`.
- During early development, when migrations are squashed or deleted, run backend tests with `mvn clean test` so stale copied migrations are removed from `target/classes`.
- Keep React code typed and covered by nearby Vitest tests when behavior changes.
- Prefer shared React components and small feature components over repeating markup, styling, or state logic across screens.
- Keep frontend code minimal: add abstractions only when they remove real duplication or match an established local pattern.
- Avoid frontend layout magic numbers; use named constants, CSS variables, or theme tokens for repeated structural dimensions.
- For UI-facing acceptance criteria, include unit tests, Playwright E2E tests, and Playwright visual regression tests.
- When `.plans/` defines an active slice, treat its test gate as required; skipped environment-dependent tests are unverified, not passed.
- For generated filesystem caches, enforce storage-root containment and use atomic, concurrency-safe writes and database upserts.
