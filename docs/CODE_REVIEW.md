# Code Review (Pre-Commit)

**Date:** 2025-03-11  
**Scope:** Integration tests, test config, REST controller consistency, and related changes.

---

## Summary

The codebase is in good shape for commit. One **consistency fix** was applied (createScheduled now returns 404 when template is missing, like `send()`). Remaining notes are optional improvements and documentation.

---

## Changes Applied During Review

### 1. **NotificationRestController – createScheduled template not found**

- **Before:** `templateRepository.findById(...).orElseThrow(() -> new IllegalArgumentException("Template not found"))` → 500.
- **After:** `orElse(null)` + `ResponseEntity.notFound().build()` → **404**.
- **Reason:** Align with `send()` and REST semantics (resource not found = 404).

### 2. **Style – braces on single-line `if`**

- Added braces to both `if (template == null)` blocks in `NotificationRestController` for consistency and safer edits.

---

## What Was Reviewed

### Integration tests

| Item | Status | Notes |
|------|--------|--------|
| **IntegrationTestBase** | OK | Containers (Postgres, Mongo, Redis) and `@DynamicPropertySource` are correct. `disabledWithoutDocker = true` avoids failures when Docker is unavailable. |
| **NotificationServiceIntegrationTest** | OK | Clear flows; uses repositories for setup/assertions. Delete order in `@BeforeEach` is correct (scheduled → templates) for FK constraints. |
| **Stale entity in cancel test** | OK | Assertion uses `scheduledRepository.findById()` (fresh read), not the in-memory `scheduled` reference. |
| **process-due-scheduled in integration** | OK | Profile sets `notification.internal.api-key: ""`, so no header is required; behavior matches design. |

### Test configuration

| Item | Status | Notes |
|------|--------|--------|
| **application.yml (test)** | OK | Excluding `OAuth2ResourceServerJwtConfiguration` avoids needing a JWT issuer in unit/WebMvc tests. |
| **application-integrationtest.yml** | OK | Stub providers, `create-drop`, empty API key. Base test `application.yml` is also loaded, so OAuth2 exclude applies. |
| **application-test.yml** | OK | Used by tests that exclude Redis/Mongo/DataSource; not used by integration tests. |

### REST API consistency

| Endpoint | Template not found | Status |
|----------|--------------------|--------|
| `POST /api/v1/notifications` | 404 | OK (fixed earlier) |
| `POST /api/v1/notifications/scheduled` | 404 | OK (fixed in this review) |
| `GET /api/v1/templates/{id}` | 404 | OK (already returns notFound()) |

### Security

- Internal endpoint allows empty API key when not configured; integration tests rely on that. No sensitive data in test config.
- Test profiles use stub providers; no real AWS/Firebase in tests.

### Dependencies (pom.xml)

- Testcontainers: `testcontainers`, `junit-jupiter`, `postgresql`, `mongodb` (no `spring-boot-testcontainers` to avoid affecting `@WebMvcTest`).
- Parent: no invalid `<scope>` under `<parent>`.

---

## Optional Follow-Ups (Non-Blocking)

1. **GraphQL**  
   `NotificationGraphQLController` still uses `orElseThrow` for missing template. GraphQL typically maps that to an error in the response; can be left as-is or aligned with a “not found” error type later.

2. **Integration test response types**  
   Some tests use `ResponseEntity<Map>` for JSON. Consider typed DTOs or at least `@SuppressWarnings("unchecked")` + a short comment where raw Map is intentional.

3. **Test order**  
   Integration tests don’t depend on order; JUnit 5 runs them in default order. If flakiness appears, consider `@Order` or a single “flow” test.

4. **Redis in IntegrationTestBase**  
   `GenericContainer` for Redis is correct. If you add more Redis-dependent tests, consider reusing the same container (already shared per class).

---

## How to Run Before Commit

```bash
# All tests (unit + integration; integration skipped without Docker)
mvn clean test

# Only integration tests (Docker required)
mvn test -Dtest=*Integration* -DfailIfNoTests=false
```

For a **full explanation of every test** (unit and integration), see [../src/test/README.md](../src/test/README.md).

---

## Verdict

**Ready to commit** with the applied fixes. Optional items above can be handled in a later change.
