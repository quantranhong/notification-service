# Notification Service — Design Document

**Visual review:**  
- **Detail architecture (PDF):** [diagrams/detail-architecture-component-interaction.html](diagrams/detail-architecture-component-interaction.html) — open in browser, then use **File → Print → Save as PDF** to download. Optionally run `npm run generate-pdf` in `docs/diagrams` to generate **detail-architecture-component-interaction.pdf**.  
- **Quick diagrams:** [diagrams/architecture-interaction.html](diagrams/architecture-interaction.html).

## 1. Overview

The Notification Service delivers messages through **SMS**, **Email**, and **Push Notifications**, targeting **internal users** (operators, customer services) and **mobile application users**, with high availability via at least two active instances.

## 2. Functional Requirements Summary

| Requirement | Description |
|-------------|-------------|
| **Channels** | SMS, Email, Push Notifications |
| **Audience** | Internal users, Mobile app users |
| **Scalability** | Min. 2 active instances, fault-tolerant |

## 3. Architecture

### 3.1 High-Level Architecture

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                    API Gateway / Load Balancer           │
                    │                    (SSL/TLS 1.2+)                        │
                    └───────────────────────────┬─────────────────────────────┘
                                                │
                    ┌───────────────────────────▼─────────────────────────────┐
                    │              Notification Service (x2+ instances)        │
                    │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐ │
                    │  │ REST API   │ │ GraphQL     │ │ OAuth2 / JWT Auth    │ │
                    │  └─────┬───────┘ └──────┬─────┘ └──────────┬────────────┘ │
                    │        │                │                   │              │
                    │        └────────────────┼───────────────────┘              │
                    │                         ▼                                  │
                    │  ┌─────────────────────────────────────────────────────┐  │
                    │  │              Core Notification Engine                │  │
                    │  │  (Channel Router, Scheduler, Retry, Idempotency)    │  │
                    │  └─────────────────────┬───────────────────────────────┘  │
                    │                         │                                  │
                    │  ┌──────────────────────┼──────────────────────────────┐  │
                    │  │ SMS Provider │ Email Provider │ Push Provider (PaaS) │  │
                    │  └──────────────────────┼──────────────────────────────┘  │
                    └─────────────────────────┼──────────────────────────────────┘
                                              │
        ┌─────────────────────────────────────┼─────────────────────────────────────┐
        │  PostgreSQL  │  MongoDB (logs/events)  │  Redis (cache, rate limit, locks)  │
        └─────────────────────────────────────┴─────────────────────────────────────┘
```

### 3.2 Technology Choices

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Application | Java Spring Boot | Main runtime |
| Scheduler | Quarkus (separate service) | Cron job calls Notification Service internal API to process due scheduled notifications |
| Database | PostgreSQL | Relational data: users, templates, subscriptions, audit |
| NoSQL | MongoDB | Notification events, delivery logs, high-volume writes |
| Cache | Redis | Rate limiting, idempotency keys |
| APIs | REST + GraphQL | REST for integrations; GraphQL for flexible queries |
| Auth | OAuth2 + JWT | Industry standard; JWT for stateless validation |
| Deployment | AWS / Azure (PaaS) | ECS/EKS or App Service; RDS, DocumentDB/Cosmos, ElastiCache |
| Channels | PaaS/SaaS | AWS SNS/SES, Azure Communication Services, Firebase FCM |

### 3.3 Security (OWASP)

- **Transport:** SSL/TLS 1.2+ only; HTTPS enforced.
- **Auth:** OAuth2 (e.g. Authorization Code, Client Credentials) + JWT validation.
- **Secrets:** Stored in cloud secret manager (AWS Secrets Manager / Azure Key Vault).
- **Input:** Validation, sanitization; parameterized queries for SQL.

## 4. Data Model

### 4.1 PostgreSQL (Core Domain)

- **audience_type:** `INTERNAL`, `MOBILE_APP`
- **channel:** `SMS`, `EMAIL`, `PUSH`
- **notification_template:** id, channel, name, body_template, subject_template (email), external_template_id
- **notification_subscription:** user_id, channel, audience_type, destination (phone/email/token), preferences
- **scheduled_notification:** id, template_id, audience_filter, scheduled_at, timezone, status, created_at
- **internal_user:** id, email, roles (for “notify internal users when new mobile user subscribes”)

### 4.2 MongoDB (Events & Logs)

- **notification_event:** request_id, channel, recipient, payload, status, sent_at, provider_response
- **delivery_log:** idempotency_key, attempts, last_error (for retries and analytics)

### 4.3 Redis

- Idempotency keys (TTL), rate-limit counters, distributed lock for scheduler (e.g. “scheduled_push_job”).

## 5. Use Cases Mapping

| Use Case | Flow |
|----------|------|
| **New mobile user subscribes → email to internal users** | Event received (API or message queue) → resolve internal recipients from DB → send email via Email Provider (PaaS). |
| **Scheduled push to mobile users** | Scheduler (cron/Quartz) runs periodically → fetch `scheduled_notification` where `scheduled_at <= now` and status = PENDING → resolve mobile subscribers → send via Push Provider (FCM/APNs via PaaS). |

## 6. API Design

### 6.1 REST

- `POST /api/v1/notifications` — Send immediate notification (body: channel, audienceType, templateId, context, recipientOverrides).
- `GET /api/v1/notifications/{id}` — Get status/delivery result.
- `POST /api/v1/notifications/scheduled` — Create scheduled notification.
- `GET /api/v1/notifications/scheduled` — List scheduled (with filters).
- `DELETE /api/v1/notifications/scheduled/{id}` — Cancel scheduled.
- `POST /api/v1/subscriptions` — Register subscription (user, channel, destination).
- `GET /api/v1/subscriptions` — List subscriptions (filter by user, channel, audience).
- `POST /api/v1/internal/process-due-scheduled` — **Internal:** process due scheduled notifications (called by the separate Scheduler Service; optional `X-Internal-Api-Key`).

### 6.2 GraphQL (Optional)

- **Mutation:** `sendNotification`, `createScheduledNotification`, `cancelScheduledNotification`.
- **Query:** `notification(id)`, `notifications(filter)`, `scheduledNotifications(filter)`, `subscriptions(userId)`.

## 7. Scalability & Availability

- **Horizontal scaling:** Stateless service; run ≥2 instances behind a load balancer.
- **Database:** PostgreSQL primary + read replicas; connection pooling (HikariCP).
- **MongoDB:** Replica set; use for append-only event/log writes.
- **Redis:** Cluster or managed cache (ElastiCache / Azure Cache); used for idempotency and rate limit.
- **Scheduler:** A separate **Notification Scheduler Service** (Quarkus) runs a cron and calls `POST /api/v1/internal/process-due-scheduled`; run one instance of the scheduler (or use a distributed lock if running multiple).

## 8. Deployment (AWS / Azure)

- **Compute:** ECS Fargate / EKS (AWS) or App Service / AKS (Azure); min 2 tasks/pods.
- **Data:** RDS PostgreSQL; DocumentDB or Cosmos DB for MongoDB; ElastiCache / Azure Cache for Redis.
- **Channels:** Use managed services (SNS/SES, Azure Communication Services, Firebase Admin SDK for FCM).
- **Secrets:** Secrets Manager / Key Vault; no secrets in code or config files.

## 9. Testing

The service is covered by **unit tests** (services and REST controllers with mocks) and **integration tests** (full Spring context with Testcontainers for PostgreSQL, MongoDB, and Redis).

- **Unit tests:** No external infrastructure; Mockito mocks for repositories and channel providers; `@WebMvcTest` for controllers with security filters disabled and OAuth2 JWT auto-config excluded in test.
- **Integration tests:** Real HTTP calls via `TestRestTemplate`; persistence asserted via JPA and MongoDB repositories; channel providers use stubs; internal process-due endpoint called without API key when not configured. Integration tests are **skipped** when Docker is not available.

**Documentation:** See [src/test/README.md](../src/test/README.md) for a full explanation of every test (what it does, how it works), how to run tests, and where reports are generated. See also [docs/TESTING.md](TESTING.md) for an overview and quick reference.

---

This design is implemented in the accompanying Java Spring Boot project under `../` (parent of `docs/`).
