# Testing — Overview

This document points to the full test documentation and summarizes how testing is organized.

## Full documentation

**[src/test/README.md](../src/test/README.md)** — Complete explanation of all tests:

- **Unit tests:** Every test method in each service and controller test class, with a short description of what it does and what it asserts.
- **Integration tests:** All six integration test methods, what they call (REST + DB), and what they verify (HTTP status + PostgreSQL/MongoDB state).
- **How tests work:** Mocks vs Testcontainers, test configuration files, profiles.
- **How to run and report:** Commands for all tests, unit-only, integration-only, and where Surefire reports are written.

## Quick reference

| Layer | Test type | Location | Count |
|-------|------------|----------|--------|
| Services (Engine, Scheduled, Idempotency) | Unit | `src/test/java/.../service/*Test.java` | 19 methods |
| REST controllers (Notifications, Internal, Subscriptions, Templates, NewSubscriber) | Unit | `src/test/java/.../api/*Test.java` | 21 methods |
| Full application (REST + Postgres + Mongo + Redis) | Integration | `NotificationServiceIntegrationTest` | 6 methods |

Run everything: `mvn test`. Integration tests are skipped when Docker is not available.

See also: [DESIGN.md](DESIGN.md) § 9 (Testing), [CODE_REVIEW.md](CODE_REVIEW.md) (pre-commit review and test run commands).
