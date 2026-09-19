# AGENTS.md

## Project Overview

This is a real-world Smart Expense Tracker backend being developed as a college project and portfolio project.

Backend stack:
- Java 21
- Spring Boot
- Maven
- Spring Security
- Spring Data MongoDB
- MongoDB
- REST API

The project is a modular monolith.

The frontend is developed separately using React Native.

## Core Development Principles

Prioritize:

1. Correctness
2. Security
3. Maintainability
4. Simplicity
5. Testability
6. Performance where relevant
7. Future extensibility

Do not add complexity only to make the project look advanced.

Do not implement features that were not requested.

## Before Making Changes

Always:

1. Inspect the existing project structure.
2. Read the relevant existing classes.
3. Understand existing APIs, services, repositories, models, configuration, and tests.
4. Reuse existing domain concepts where appropriate.
5. Identify possible compatibility issues.
6. Make the smallest clean change that solves the requested requirement.

Never recreate the project.

Never rewrite unrelated working code.

Preserve existing functionality unless the requested feature explicitly requires a change.

## Architecture

Prefer a modular monolith.

For backend functionality generally follow:

Controller
→ Service
→ Repository
→ Database

Use DTOs between API and persistence layers when appropriate.

Prefer:
- constructor injection
- cohesive classes
- clear naming
- centralized exception handling
- validation
- meaningful HTTP status codes
- environment-based configuration
- secure secret management
- automated tests

Avoid:
- premature microservices
- unnecessary design patterns
- excessive abstraction
- giant controllers/services
- duplicated business logic
- unnecessary dependencies

Every architectural decision should have a practical reason.

## Package Structure

Keep functionality organized by domain/module.

Current modules include areas such as:

- auth
- user
- common
- config

When adding a new domain feature, place its controller, service, repository, DTOs, and models within an appropriate module rather than creating unrelated global classes.

## API

Use APIs:

/api/...

Prefer resource-oriented REST endpoints.

Use:
- appropriate HTTP methods
- meaningful HTTP status codes
- request/response DTOs
- validation
- authentication
- authorization
- centralized error handling

Do not design APIs around individual frontend screens.

## Database

MongoDB is the application's database.

When modifying database models, consider:
- ownership
- uniqueness
- indexes
- access patterns
- query performance
- consistency
- lifecycle
- concurrency
- future changes

Use database constraints where appropriate.

Avoid multiple sources of truth.

Do not trust client-provided IDs for ownership or authorization.

## Security

Security is a first-class requirement.

Never:
- store plaintext passwords
- expose password hashes
- hardcode secrets
- hardcode credentials
- log passwords
- log authentication tokens
- trust client ownership information
- expose unnecessary internal exceptions
- bypass authorization

Use secure configuration through environment variables.

Never commit `.env` or real secrets.

Authentication and authorization must be enforced server-side.

## Validation

Validate external input at the API boundary.

Consider:
- required fields
- format
- length
- invalid values
- duplicate data
- authorization
- edge cases

Do not rely only on frontend validation.

## Error Handling

Use centralized exception handling where appropriate.

Return safe, consistent API error responses.

Do not expose:
- stack traces
- database exceptions
- internal implementation details
- secrets
- credentials

Use appropriate status codes such as:

400 → invalid request/validation
401 → unauthenticated
403 → authenticated but not authorized
404 → resource not found
409 → conflict/duplicate resource
500 → unexpected server error

## Testing

Testing is required for meaningful changes.

Use appropriate:
- unit tests
- service tests
- controller/API tests
- integration/database tests
- security tests
- edge-case tests

Tests should verify meaningful behavior rather than only increasing test count.

After implementation run the relevant Maven tests.

For meaningful changes also run:

./mvnw clean test

and, when appropriate:

./mvnw clean package

Do not claim tests passed unless they were actually executed.

## Configuration

Keep environment-specific values outside source code.

Examples:
- database URI
- JWT secret
- ports
- external API keys
- credentials

Use environment variables or appropriate configuration mechanisms.

Never place real secrets in source files, README files, tests, or documentation.

## Git

The developer manually manages Git and GitHub.

Do not execute Git commands.

Do not create commits.

Do not push to GitHub.

The developer will handle Git after implementation, review, testing, and manual verification.

## Scope Control

Implement only the requested feature.

Do not:
- implement future expense features prematurely
- modify unrelated modules
- refactor working code without a practical reason
- introduce new frameworks unnecessarily
- create microservices without a real requirement

If an existing design creates a problem for the requested feature, explain the issue before making a broad architectural change.

## Final Report

After completing a task, report:

1. What was implemented
2. Files changed
3. Database changes
4. API changes
5. Security/validation changes
6. Tests executed
7. Exact test/build results
8. Manual verification needed
9. Any unresolved issues

Do not claim completion based only on compilation.

## Working Style

Work autonomously within the project workspace when permissions allow.

Do not repeatedly ask for approval for individual file edits or normal build/test commands.

Complete the requested task as a coherent unit.

If something is unclear or technically unsafe, stop and explain the specific issue rather than guessing.

Keep changes focused, maintainable, and compatible with the existing system.