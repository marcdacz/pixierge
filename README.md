# Pixierge

Pixierge is a photo management app.

## Prerequisites

- Docker and Docker Compose
- Java 21 for native backend development
- Node.js 20+ for native frontend development
- Maven 3.9+ for native backend development

## Run Locally

```sh
cp .env.example .env
docker compose up --build
```

Then open:

- Frontend: http://localhost:5173
- API health: http://localhost:8080/api/health
- Actuator health: http://localhost:8080/actuator/health

## Test Commands

Backend:

```sh
cd backend
mvn test
```

Frontend:

```sh
cd frontend
npm install
npm test
```

Docker-based backend tests:

```sh
docker run --rm -v "$PWD/backend:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

## Review Steps

1. Start the stack with `docker compose up --build`.
2. Confirm the frontend shows `Pixierge` and database status `Ready`.
3. Confirm `GET http://localhost:8080/api/health` returns:

```json
{
  "status": "ok",
  "database": "ready",
  "app": "pixierge-api"
}
```

4. Run backend and frontend tests.

## Current Scope

The application currently provides the local runtime foundation and health checks. Authentication, libraries, scanner, plugins, albums, and photo management behavior are not implemented yet.
