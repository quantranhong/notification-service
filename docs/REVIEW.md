# Notification Service — Full Review

**Scope:** Architecture, code, tests, configuration, documentation, and deployment.  
**Verdict:** Production-ready foundation with clear gaps to address before or shortly after launch.

---

## 1. Strengths

| Area | Summary |
|------|--------|
| **Architecture** | Clear separation: REST + GraphQL, engine + channel providers, Postgres/Mongo/Redis. DESIGN.md and diagrams match the code. |
| **REST API** | Consistent 404 for missing template (send + createScheduled); validation on DTOs (`@Valid`, `@NotNull`); appropriate status codes (200, 202, 204, 404). |
| **Security** | OAuth2/JWT optional (issuer not set → permitAll for dev); internal endpoint protected by configurable API key; no secrets in repo. |
| **Testing** | Unit tests for all services and controllers; integration tests with Testcontainers; tests documented in `src/test/README.md`. |
| **Documentation** | README, DESIGN.md, TESTING.md, CODE_REVIEW.md, architecture diagrams (HTML/PDF); test explanations per method. |
| **Config** | Env-based (POSTGRES_*, MONGODB_URI, REDIS_*, etc.); profile `integrationtest` for tests; no hardcoded credentials. |
| **Scheduling** | External Quarkus scheduler; no in-process cron; internal API for process-due-scheduled is simple and testable. |

---

## 2. Architecture & Design Alignment

- **Channels:** SMS, Email, Push implemented with provider interfaces and AWS/Firebase/stub implementations. ✓  
- **Audience:** INTERNAL (internal users) and MOBILE_APP (subscriptions) supported in engine and APIs. ✓  
- **Data stores:** JPA for templates, subscriptions, internal users, scheduled notifications; MongoDB for events; Redis for idempotency. ✓  
- **Scheduler:** Design says “separate Quarkus service”; no in-app scheduler; internal endpoint used by external caller. ✓  
- **Rate limiting:** Mentioned in DESIGN and in `application.yml` (`rate-limit-window-seconds`, `rate-limit-max-per-user`) but **not implemented** in code. See §5.

---

## 3. Code Quality

### 3.1 APIs and DTOs

- **SendNotificationRequest:** `@NotNull` on channel, audienceType, templateId. Optional context and idempotencyKey. ✓  
- **CreateScheduledRequest:** Has validation; controller returns 404 when template not found. ✓  
- **TemplateController.create:** Accepts `NotificationTemplate` with no `@Valid`; entity has no Bean Validation. Risk: invalid or null fields can be persisted. Consider DTO + validation or `@Valid` on entity.  
- **SubscriptionController.create:** Same: raw entity, no validation. DELETE always returns 204 even if id does not exist (no 404). Consider `findById` + 404 when not found.  
- **NewSubscriberController:** Uses record `MobileUserSubscribedPayload`; template missing → 400. ✓  

### 3.2 Services

- **NotificationEngine:**  
  - `sendToAudience` uses `templateRepository.findById(templateId).orElseThrow()` with no message → throws `NoSuchElementException` if template is missing. Controllers guard send/createScheduled, but GraphQL and any other callers could hit this. Prefer explicit exception or return type.  
  - Template-recipient mismatch: REST ensures template exists then calls engine; engine does not re-check template. Acceptable.  
- **ScheduledNotificationService:** Clear transactions; `processDueScheduled` catches per-item exceptions and logs so one failure does not stop the batch. ✓  
- **IdempotencyService:** Uses Redis with TTL; key prefix avoids collisions. ✓  

### 3.3 Error handling and consistency

- REST: Template not found → 404 on send and createScheduled. ✓  
- GraphQL: `createScheduledNotification` and `sendNotification` use `orElseThrow` / `UUID.fromString` / `Instant.parse` — can throw `IllegalArgumentException` or `NoSuchElementException`. GraphQL typically returns errors in the response; consider mapping these to GraphQL errors with codes (e.g. NOT_FOUND, BAD_REQUEST) instead of raw Java exceptions.  
- **toJson** in NotificationRestController and similar: swallow exceptions and return `"{}"` or null. Adequate for now; consider logging.  

### 3.4 Minor style

- **NotificationRestController:** Uses fully qualified name `com.notification.repository.NotificationTemplateRepository`; other controllers use imports. Prefer imports for consistency.  
- **ObjectMapper:** New `ObjectMapper()` in a few places; could use a shared `@Bean` for reuse and centralised config.  

---

## 4. Security

| Item | Status |
|------|--------|
| **CSRF** | Disabled (`csrf(csrf -> csrf.disable())`). Acceptable for stateless JSON API with JWT; document that session-based UIs would need a different approach. |
| **Internal endpoint** | `/api/v1/internal/**` permitted at security layer; API key checked in controller. When key is blank, anyone can call. Document that key should be set in production. |
| **Actuator** | Health/info permitted; metrics typically should be restricted (e.g. authenticated or internal only). `show-details: when-authorized` is good for health. |
| **Secrets** | No secrets in repo; config uses placeholders and env vars. ✓ |
| **TLS** | Documented (README, DESIGN); `SSL_ENABLED` and load balancer TLS. ✓ |

---

## 5. Gaps and Recommendations

### High priority

1. **Rate limiting**  
   Config has `rate-limit-window-seconds` and `rate-limit-max-per-user` but no code uses them. Add a filter or aspect that checks Redis (or in-memory for single instance) per user/client and returns 429 when over limit.

2. **GraphQL “template not found”**  
   Align with REST: return a structured error (e.g. `NOT_FOUND`) instead of throwing, so clients get a predictable error shape.

3. **NotificationEngine.sendToAudience**  
   When template is missing, `orElseThrow()` yields a generic exception. Either require callers to resolve template first (and document) or return Optional/Result or throw a domain exception (e.g. `TemplateNotFoundException`) and map it in controllers.

### Medium priority

4. **SubscriptionController DELETE**  
   Always 204. Consider: `findById(id).isEmpty() ? 404 : delete + 204`.

5. **Template/Subscription create validation**  
   Add a DTO with `@Valid` and constraints (e.g. name not blank, channel not null) or add Bean Validation on entities and use `@Valid` so invalid data is rejected with 400.

6. **Shared ObjectMapper**  
   Inject a single `ObjectMapper` bean and use it for `toJson` / `parseContext` to avoid creating new instances and to centralise config (e.g. date format).

### Low priority

7. **DESIGN.md**  
   Diagram text still says “Scheduler” inside the engine box; design is “external scheduler”. Optional: adjust wording so it’s clear scheduling is out-of-process.

8. **Maven wrapper**  
   Only `maven-wrapper.properties` is committed; no `mvnw`/`mvnw.cmd` or `.mvn/wrapper/maven-wrapper.jar`. Add wrapper artifacts or document “run `mvn -N wrapper:wrapper`” in README.

---

## 6. Tests

- **Unit:** Services (engine, scheduled, idempotency) and all REST controllers covered with mocks; test `application.yml` excludes OAuth2 JWT so WebMvcTests load. ✓  
- **Integration:** Six tests against real Postgres/Mongo/Redis via Testcontainers; cover templates, send, subscriptions, new-subscriber, scheduled create+process, cancel. ✓  
- **Docs:** `src/test/README.md` describes each test; TESTING.md and README point to it. ✓  
- **IdempotencyServiceTest:** Uses mock `RedisTemplate`; does not test actual Redis. Acceptable for unit; integration tests cover real Redis for idempotency if a test sends twice with same key (currently no explicit test for that). Optional: add one integration test for idempotency.

---

## 7. Documentation

| Doc | Quality |
|-----|--------|
| **README.md** | Clear features, use cases, quick start, config table, deployment, testing link. ✓ |
| **DESIGN.md** | Overview, requirements, architecture, tech choices, APIs, scalability, deployment, testing §9. ✓ |
| **TESTING.md** | Short index and link to full test doc. ✓ |
| **src/test/README.md** | Per-class and per-method test explanations, run instructions, config files. ✓ |
| **CODE_REVIEW.md** | Pre-commit review and fixes; references test doc. ✓ |
| **Diagrams** | architecture-interaction.html and detail-architecture-component-interaction.html (and PDFs). ✓ |

---

## 8. Build and Deploy

- **pom.xml:** Spring Boot 3.2.5, Java 17; dependencies consistent with design (Web, JPA, Mongo, Redis, Security, OAuth2, GraphQL, Actuator, AWS, Firebase, Testcontainers). No invalid parent config. ✓  
- **.gitignore:** target, .m2, node_modules, IDE files. ✓  
- **Run:** `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`; README and DESIGN mention profiles and env. ✓  

---

## 9. Summary Table

| Category | Status | Notes |
|----------|--------|--------|
| Architecture | ✓ | Matches design; external scheduler. |
| REST API | ✓ | Consistent 404, validation on main DTOs. |
| GraphQL | ⚠ | Throws on missing template; improve error handling. |
| Security | ✓ | JWT optional; internal API key; no secrets in repo. |
| Rate limiting | ✗ | Config only; not implemented. |
| Validation | ⚠ | Template/Subscription create and Subscription DELETE could be tightened. |
| Tests | ✓ | Unit + integration; well documented. |
| Documentation | ✓ | README, DESIGN, TESTING, CODE_REVIEW, diagrams. |
| Build / Deploy | ✓ | Maven, .gitignore; wrapper incomplete. |

**Overall:** Solid, maintainable service that matches its design. Before or soon after production: add rate limiting, align GraphQL and engine behaviour with REST (template not found), and optionally improve validation and DELETE semantics. The rest can be phased in as needed.
