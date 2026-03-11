# Notification Service — Test Documentation

This document explains **all tests** in the project: what they cover, how they work, and how to run and report on them.

---

## 1. Overview

| Type | Scope | Infrastructure | When they run |
|------|--------|-----------------|----------------|
| **Unit tests** | Single class with mocks (services) or slice (controllers) | None; all dependencies mocked or excluded | Always with `mvn test` |
| **Integration tests** | Full Spring context, real HTTP, real DBs | Testcontainers: PostgreSQL, MongoDB, Redis | Only when Docker is available; otherwise **skipped** |

- **Unit tests** do not require a database, Redis, or MongoDB. Controllers use `@WebMvcTest` with security filters disabled and mocked services/repositories.
- **Integration tests** start real containers, boot the full application, and call REST APIs with `TestRestTemplate`, then assert on both HTTP responses and persisted data (repositories).

---

## 2. How to Run Tests

### Run all tests (unit + integration)

```bash
mvn test
```

- Unit tests always run.
- Integration tests run only if Docker is available; otherwise they are **skipped** (no failure).

Or with the Maven wrapper:

```bash
./mvnw test
```

### Run only unit tests

```bash
mvn test -Dtest='!*Integration*' -DfailIfNoTests=false
```

### Run only integration tests (Docker required)

```bash
mvn test -Dtest=*Integration* -DfailIfNoTests=false
```

### Run integration tests in Docker (e.g. CI with Docker socket)

```bash
docker run --rm -v "$(pwd)":/app -w /app -v /var/run/docker.sock:/var/run/docker.sock \
  -e MAVEN_OPTS="-Dmaven.repo.local=/app/.m2" maven:3.9-eclipse-temurin-17 \
  mvn test -Dtest=NotificationServiceIntegrationTest -DfailIfNoTests=false
```

---

## 3. Test Reports

- **Console:** Maven Surefire prints a summary, e.g. `Tests run: 46, Failures: 0, Errors: 0, Skipped: 6` (6 skipped = integration tests when Docker is unavailable).
- **Surefire reports:** `target/surefire-reports/` — `.txt` and `.xml` per test class (JUnit XML can be consumed by CI for dashboards).
- **HTML report (optional):** Use the `surefire-report` plugin if you add it to `pom.xml`.

---

## 4. Unit Tests — Full Explanation

### 4.1 Service layer (plain JUnit + Mockito)

No Spring context. Services are constructed with `@Mock` dependencies.

---

#### **NotificationEngineTest** — `NotificationEngine` (routing, rendering, sending, idempotency)

| Test method | What it does |
|-------------|----------------|
| **resolveRecipients_internal_returnsInternalUserEmails** | For audience INTERNAL, mocks `InternalUserRepository.findAll()` and asserts the engine returns those users’ emails as recipients. |
| **resolveRecipients_mobileApp_returnsSubscriptionDestinations** | For audience MOBILE_APP, mocks `NotificationSubscriptionRepository.findByAudienceTypeAndChannel()` and asserts the engine returns subscription destinations (e.g. FCM tokens). |
| **renderBody_replacesPlaceholders** | Renders a template body with `{{name}}` using a context map and asserts the placeholder is replaced. |
| **renderBody_nullContext_returnsTemplateAsIs** | With null context, asserts the body is returned unchanged (no substitution). |
| **renderSubject_replacesPlaceholders** | Same as body but for subject template. |
| **sendOne_email_callsEmailProviderAndSavesEvent** | Mocks email provider and event repository; calls `sendOne`; verifies provider receives the email, and one `NotificationEvent` is saved to MongoDB (via mock). |
| **sendOne_idempotencyHit_returnsCachedResult** | Mocks `IdempotencyService.getCachedResult` to return a cached `DeliveryResult`; asserts `sendOne` returns that result without calling the email provider. |
| **sendToAudience_resolvesRecipientsAndSends** | Mocks template repo, subscription repo, and email provider; calls `sendToAudience` for INTERNAL; verifies recipients are resolved and provider is invoked for each. |
| **sendToAudience_templateNotFound_throws** | Mocks template repo to return empty; asserts `sendToAudience` throws when the template is missing. |

---

#### **ScheduledNotificationServiceTest** — `ScheduledNotificationService` (CRUD, due list, process, cancel)

| Test method | What it does |
|-------------|----------------|
| **create_setsPendingAndSaves** | Builds a `ScheduledNotification` without status; calls `create()`; asserts status is set to PENDING and `scheduledRepository.save()` is invoked. |
| **getById_returnsOptionalFromRepository** | Mocks `findById` to return a scheduled notification; asserts `getById()` returns the same optional. |
| **listDue_returnsFindDueScheduledResult** | Mocks `findDueScheduled(PENDING, any(Instant))` to return a list; asserts `listDue()` returns that list. |
| **processDueScheduled_updatesStatusAndCallsEngine** | Mocks `findDueScheduled` to return one pending scheduled; mocks `save`; calls `processDueScheduled()`; verifies the entity is saved with status SENT and `NotificationEngine.sendToAudience` is called with the correct template/channel/audience. |
| **cancel_pending_returnsTrueAndUpdatesStatus** | Mocks `findById` with a PENDING entity; calls `cancel(id)`; asserts return true, entity saved with status CANCELLED. |
| **cancel_notFound_returnsFalse** | Mocks `findById` empty; asserts `cancel(id)` returns false and save is never called. |
| **cancel_alreadySent_returnsFalse** | Mocks `findById` with status SENT; asserts `cancel(id)` returns false and save is never called. |

---

#### **IdempotencyServiceTest** — `IdempotencyService` (Redis cache behaviour, tested with mock template)

| Test method | What it does |
|-------------|----------------|
| **getCachedResult_miss_returnsEmpty** | With no value in Redis (mock), asserts `getCachedResult(key)` returns `Optional.empty()`. |
| **getCachedResult_hit_returnsCachedResult** | Sets a value in the cache (mock); asserts `getCachedResult(key)` returns the same `DeliveryResult`. |
| **cacheResult_setsValueWithTtl** | Calls `cacheResult(key, result)`; verifies the template’s `opsForValue().set` (or equivalent) is called with the expected key and TTL. |

---

### 4.2 REST controllers (`@WebMvcTest` + MockMvc)

Only the web layer and the declared controller are loaded. Services and repositories are `@MockBean`. Security filters are disabled (`@AutoConfigureMockMvc(addFilters = false)`). Test `application.yml` excludes OAuth2 JWT auto-configuration so no issuer is required.

---

#### **NotificationRestControllerTest** — `NotificationRestController`

| Test method | What it does |
|-------------|----------------|
| **send_notification_returns200AndResults** | Mocks template repo and notification engine to return success; POSTs `/api/v1/notifications` with valid JSON; expects 200 and response body with `success`, `totalRecipients`, `results`. |
| **send_templateNotFound_returns404** | Mocks template repo to return empty for the given template id; POSTs send request; expects **404** (template not found). |
| **createScheduled_returns200AndScheduled** | Mocks template repo and scheduled service; POSTs `/api/v1/notifications/scheduled` with templateId, channel, audienceType, scheduledAt; expects 200 and scheduled entity in body. |
| **listScheduled_returns200AndList** | Mocks scheduled repository to return a list; GETs `/api/v1/notifications/scheduled`; expects 200 and list in body. |
| **cancelScheduled_foundAndPending_returns204** | Mocks scheduled service `cancel(id)` to return true; DELETEs `/api/v1/notifications/scheduled/{id}`; expects 204. |
| **cancelScheduled_notFound_returns404** | Mocks `cancel(id)` to return false; DELETEs same path; expects 404. |

---

#### **InternalProcessControllerTest** — `InternalProcessController` (no API key configured)

| Test method | What it does |
|-------------|----------------|
| **processDueScheduled_noApiKeyConfigured_acceptsAndCallsService** | With `notification.internal.api-key=` (empty); POSTs `/api/v1/internal/process-due-scheduled` with no header; expects 202 and verifies `ScheduledNotificationService.processDueScheduled()` is invoked. |
| **processDueScheduled_withValidApiKey_accepts** | Same empty api-key; POSTs with header `X-Internal-Api-Key: ""`; expects 202. |

---

#### **InternalProcessControllerApiKeyTest** — `InternalProcessController` (API key required)

Uses `@TestPropertySource(properties = "notification.internal.api-key=required-secret")`.

| Test method | What it does |
|-------------|----------------|
| **processDueScheduled_missingApiKey_returns401** | POSTs with no `X-Internal-Api-Key`; expects **401** and no call to `processDueScheduled()`. |
| **processDueScheduled_wrongApiKey_returns401** | POSTs with `X-Internal-Api-Key: wrong`; expects 401 and no call to service. |
| **processDueScheduled_correctApiKey_accepts** | POSTs with `X-Internal-Api-Key: required-secret`; expects 202 and verifies `processDueScheduled()` was called. |

---

#### **SubscriptionControllerTest** — `SubscriptionController`

| Test method | What it does |
|-------------|----------------|
| **create_returns200AndSubscription** | POSTs `/api/v1/subscriptions` with subscription JSON (userId, channel, audienceType, destination); mocks repo save; expects 200 and subscription in body. |
| **list_noParams_returnsAll** | Mocks repo to return a list; GETs `/api/v1/subscriptions`; expects 200 and full list. |
| **list_byUserId_returnsFiltered** | Mocks repo `findByUserId`; GETs `/api/v1/subscriptions?userId=u1`; expects 200 and filtered list. |
| **delete_returns204** | Mocks repo `deleteById`; DELETEs `/api/v1/subscriptions/{id}`; expects 204. |

---

#### **TemplateControllerTest** — `TemplateController`

| Test method | What it does |
|-------------|----------------|
| **create_returns200AndTemplate** | POSTs `/api/v1/templates` with template JSON; mocks save; expects 200 and saved template in body. |
| **list_returnsAll** | Mocks repo `findAll()`; GETs `/api/v1/templates`; expects 200 and list. |
| **get_found_returns200** | Mocks `findById` to return a template; GETs `/api/v1/templates/{id}`; expects 200 and template. |
| **get_notFound_returns404** | Mocks `findById` empty; GETs with unknown id; expects **404**. |

---

#### **NewSubscriberControllerTest** — `NewSubscriberController` (mobile-user-subscribed)

| Test method | What it does |
|-------------|----------------|
| **mobileUserSubscribed_templateExists_returns202AndCallsEngine** | Mocks template repo and notification engine; POSTs `/api/v1/events/mobile-user-subscribed?templateId={id}` with body `{ userId, email }`; expects **202** and verifies engine was called to send to INTERNAL audience. |
| **mobileUserSubscribed_templateNotFound_returns400** | Mocks template repo empty for the template id; same POST; expects **400**. |

---

## 5. Integration Tests — Full Explanation

**Base class:** `IntegrationTestBase`  
- Starts **PostgreSQL 15**, **MongoDB 7**, **Redis 7** via Testcontainers.  
- Uses `@DynamicPropertySource` to set `spring.datasource.url`, MongoDB URI, Redis host/port.  
- Profile: **integrationtest** (stub channel providers, `ddl-auto: create-drop`, no internal API key).  
- `@Testcontainers(disabledWithoutDocker = true)`: whole class is **skipped** if Docker is not available.

**Test class:** `NotificationServiceIntegrationTest`  
- Extends `IntegrationTestBase`.  
- In `@BeforeEach`: clears all relevant repositories and MongoDB events, then creates one **Welcome** email template used by several tests.  
- Uses **TestRestTemplate** to call `http://localhost:{port}/api/v1/...` (no auth in this profile).

---

| Test method | What it does |
|-------------|----------------|
| **templates_api_createAndList** | POSTs a new template (SMS Alert) to `/api/v1/templates`; asserts 200 and name in response. Then GETs `/api/v1/templates` and asserts at least 2 templates (the one from setUp + the one just created). |
| **sendNotification_toInternalAudience_returns200AndLogsEvent** | Saves two internal users (ops@test.com, support@test.com). POSTs `/api/v1/notifications` with channel EMAIL, audience INTERNAL, and the saved template id; asserts 200, `success` true, `totalRecipients` 2, `successCount` 2. Then asserts **MongoDB** `NotificationEventRepository.findAll()` has exactly 2 events (one per recipient). |
| **subscriptions_api_createAndList** | POSTs a subscription (userId mobile-user-1, PUSH, MOBILE_APP, fcm-token-abc); asserts 200. GETs `/api/v1/subscriptions?userId=mobile-user-1`; asserts 200 and list size 1. |
| **mobileUserSubscribed_sendsEmailToInternal_returns202** | Saves one internal user. POSTs `/api/v1/events/mobile-user-subscribed?templateId={savedTemplateId}` with body `{ userId, email }`; expects **202**. Asserts MongoDB has exactly 1 notification event. |
| **scheduledNotification_createAndProcessDue** | Saves one internal user. POSTs `/api/v1/notifications/scheduled` with template id, EMAIL, INTERNAL, and `scheduledAt` in the past (60 seconds ago); expects 200 and status PENDING. Extracts scheduled id from response. POSTs `/api/v1/internal/process-due-scheduled` (no API key in profile); expects 202. Loads the scheduled entity from **PostgreSQL** and asserts status is **SENT**. |
| **cancelScheduled_returns204** | Creates a template and a PENDING scheduled notification in the DB. DELETEs `/api/v1/notifications/scheduled/{id}`; expects **204**. Loads the entity again from DB and asserts status is **CANCELLED**. |

---

## 6. Test Configuration Files

| File | Purpose |
|------|--------|
| **src/test/resources/application.yml** | Loaded for all tests. Excludes `OAuth2ResourceServerJwtConfiguration` and sets empty `issuer-uri` so WebMvcTest does not require a JWT issuer. |
| **src/test/resources/application-integrationtest.yml** | Profile **integrationtest**. Sets JPA `ddl-auto: create-drop`, stub channel providers, empty internal API key. |
| **src/test/resources/application-test.yml** | Profile **test**. Excludes Redis, Mongo, DataSource auto-config; used by some unit tests that need a minimal context (if any). |

---

## 7. Summary Table

| Test class | Type | # Tests | Purpose |
|------------|------|--------|---------|
| NotificationEngineTest | Unit | 9 | Engine: resolve, render, sendOne, sendToAudience, idempotency, template not found |
| ScheduledNotificationServiceTest | Unit | 7 | Scheduled: create, getById, listDue, processDueScheduled, cancel (3 cases) |
| IdempotencyServiceTest | Unit | 3 | Idempotency: cache miss/hit, cacheResult TTL |
| NotificationRestControllerTest | Unit | 6 | Notifications REST: send (200/404), scheduled CRUD, cancel (204/404) |
| InternalProcessControllerTest | Unit | 2 | Internal process-due when API key not configured |
| InternalProcessControllerApiKeyTest | Unit | 3 | Internal process-due with API key (401/401/202) |
| SubscriptionControllerTest | Unit | 4 | Subscriptions REST: create, list (all/by userId), delete |
| TemplateControllerTest | Unit | 4 | Templates REST: create, list, get (200/404) |
| NewSubscriberControllerTest | Unit | 2 | Mobile-user-subscribed: 202 when template exists, 400 when not |
| **NotificationServiceIntegrationTest** | **Integration** | **6** | Full stack: templates, send, subscriptions, new-subscriber, scheduled create+process, cancel |

Total: **40** unit test methods + **6** integration test methods. Integration tests are skipped when Docker is unavailable.
