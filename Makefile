.DEFAULT_GOAL := help

.PHONY: help build server run dev stop storybook storybook-build test test-backend-unit test-backend-integration test-frontend-unit test-frontend-e2e test-frontend-vr lint format

help:
	@echo "Usage: ./pmake <target>"
	@echo ""
	@echo "Targets:"
	@echo "  build   Build the backend and frontend"
	@echo "  server  Start the Docker backend and database"
	@echo "  dev     Serve the frontend locally with Vite hot reload"
	@echo "  run     Alias for server"
	@echo "  stop    Stop the local Docker Compose stack"
	@echo "  storybook                  Serve frontend Storybook locally"
	@echo "  storybook-build            Build static frontend Storybook"
	@echo "  test                       Run every test target"
	@echo "  test-backend-unit          Run backend unit tests"
	@echo "  test-backend-integration   Run backend unit and integration tests"
	@echo "  test-frontend-unit         Run frontend unit tests"
	@echo "  test-frontend-e2e          Run frontend end-to-end tests"
	@echo "  test-frontend-vr           Run frontend visual regression tests"
	@echo "  lint    Run backend PMD and frontend ESLint"
	@echo "  format  Format backend Java and frontend files"

build:
	mvn -f backend/pom.xml package -DskipTests
	npm --prefix frontend run build

server:
	docker compose -f docker-compose.yml -f docker-compose.local-test.yml up --build postgres api

run: server

dev:
	VITE_API_BASE_URL=http://localhost:8080 npm --prefix frontend run dev -- --host 127.0.0.1

stop:
	docker compose down

storybook:
	npm --prefix frontend run storybook

storybook-build:
	npm --prefix frontend run build-storybook

test: test-backend-unit test-backend-integration test-frontend-unit test-frontend-e2e test-frontend-vr

test-backend-unit:
	mvn -f backend/pom.xml clean test

test-backend-integration:
	mvn -f backend/pom.xml clean verify

test-frontend-unit:
	npm --prefix frontend test

test-frontend-e2e:
	npm --prefix frontend run test:e2e

test-frontend-vr:
	npm --prefix frontend run test:vr

lint:
	mvn -f backend/pom.xml pmd:check
	npm --prefix frontend run lint

format:
	mvn -f backend/pom.xml spotless:apply
	npm --prefix frontend run format
