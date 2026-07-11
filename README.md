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

## Photo Sources In Docker

Pixierge validates source folders from inside the API container. Docker must mount your laptop folders into the API container before Pixierge can add them as sources.

Copy the override example:

```sh
cp docker-compose.override.yml.example docker-compose.override.yml
```

Then edit `docker-compose.override.yml`. Use one mount for one folder, or multiple mounts for multiple folders:

```yaml
services:
  api:
    volumes:
      # Read-write mounts are required for folder rename.
      - /Users/your-name/Pictures:/photos/pictures
      - /Volumes/ArchiveDrive/Photos:/photos/archive
      - /Users/your-name/Scans:/photos/scans
```

After restarting Docker Compose, add these source paths in Pixierge:

```sh
/photos/pictures
/photos/archive
/photos/scans
```

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
