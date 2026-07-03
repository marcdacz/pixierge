# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

Pixierge is a Spring Boot API plus React TypeScript web app.

- Backend: `backend/` uses Java 21, Maven, Spring Boot, Flyway, PostgreSQL, and Querydsl.
- Frontend: `frontend/` uses React, TypeScript, Vite, npm, and Vitest.
- Local stack: `docker-compose.yml` runs the app and database.

## Commands

- Start the local stack: `docker compose up --build`
- Backend tests: `cd backend && mvn test`
- Frontend dependencies: `cd frontend && npm install`
- Frontend tests: `cd frontend && npm test`
- Frontend build: `cd frontend && npm run build`

If Maven cannot write to the default local cache, use:

```sh
cd backend && mvn -Dmaven.repo.local=/private/tmp/pixierge-m2 test
```

## Conventions

- Prefer small, focused changes that match the existing code style.
- Use Maven for backend build work.
- Use Querydsl for repository SQL access instead of handwritten SQL strings.
- Add Flyway migrations under `backend/src/main/resources/db/migration/`.
- Keep React code typed and covered by nearby Vitest tests when behavior changes.
