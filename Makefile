.DEFAULT_GOAL := help

.PHONY: help build run stop test test-backend-unit test-backend-integration test-frontend-unit test-frontend-e2e test-frontend-vr lint format

help:
	@echo "Usage: ./pmake <target>"
	@echo ""
	@echo "Targets:"
	@echo "  build   Build the backend and frontend"
	@echo "  run     Start the local Docker Compose stack"
	@echo "  stop    Stop the local Docker Compose stack"
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

run:
	docker compose up --build

stop:
	docker compose down

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
