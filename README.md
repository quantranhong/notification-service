# Notification Service

Multi-channel notification service (SMS, Email, Push) for internal and mobile app users, with high availability and PaaS-backed delivery.

## Features

- **Channels:** SMS, Email, Push Notifications (via AWS SNS/SES, Firebase FCM, or stubs)
- **Audience:** Internal users (operators, customer services), Mobile application users
- **APIs:** REST and GraphQL
- **Auth:** OAuth2 + JWT (resource server)
- **Data:** PostgreSQL (core), MongoDB (events/logs), Redis (cache, idempotency)
- **Scheduling:** A separate **Notification Scheduler Service** (Quarkus) calls this service to process due scheduled notifications

## Use Cases

1. **New mobile user subscribes → email to internal users**  
   `POST /api/v1/events/mobile-user-subscribed` with `templateId` and body `{ "userId", "email" }`.

2. **Scheduled push to mobile users**  
   `POST /api/v1/notifications/scheduled` to create; the **Notification Scheduler Service** (separate Quarkus app) calls `POST /api/v1/internal/process-due-scheduled` every minute to process due items.

## Quick Start

### Prerequisites

- Java 17+
- Docker (optional): PostgreSQL, MongoDB, Redis

### Run dependencies (Docker)

```bash
docker run -d --name postgres -e POSTGRES_DB=notification_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:15
docker run -d --name mongo -p 27017:27017 mongo:7
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

### Build and run

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

REST base: `http://localhost:8080/api/v1`  
GraphQL: `http://localhost:8080/graphql`

### OAuth2 / JWT

Set in environment (or `application.yml`):

- `OAUTH2_ISSUER_URI` or `OAUTH2_JWK_SET_URI` — JWT issuer / JWK set from your IdP (e.g. Auth0, Cognito, Azure AD).

For local testing without an IdP, you can temporarily allow unauthenticated access on selected paths by changing `SecurityConfig` (e.g. permitAll for `/api/v1/**` when a profile is active).

## Configuration

| Env / Property | Description |
|----------------|-------------|
| `POSTGRES_*` | PostgreSQL URL, user, password |
| `MONGODB_URI` | MongoDB connection string |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis |
| `notification.channels.email.provider` | `aws-ses` or (default) stub |
| `notification.channels.sms.provider` | `aws-sns` or stub |
| `notification.channels.push.provider` | `firebase-fcm` or stub |
| `NOTIFICATION_INTERNAL_API_KEY` | Optional. If set, the internal endpoint `/api/v1/internal/process-due-scheduled` requires header `X-Internal-Api-Key` with this value (used by the Scheduler Service). |
| `SSL_ENABLED` | Enable TLS (use in production; TLS 1.2+ for OWASP) |

## Deployment (Scalability & HA)

- Run **at least 2 instances** behind a load balancer (e.g. AWS ALB, Azure App Gateway).
- Use **PaaS** where possible:
  - **AWS:** ECS/Fargate or EKS, RDS PostgreSQL, DocumentDB or DynamoDB for events, ElastiCache Redis, SES, SNS.
  - **Azure:** App Service (multi-instance) or AKS, Azure Database for PostgreSQL, Cosmos DB, Azure Cache for Redis, Communication Services.
- Store secrets in **AWS Secrets Manager** or **Azure Key Vault**; never in repo or config.
- Enforce **SSL/TLS 1.2+** at the load balancer and application level.

## API Summary

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/notifications` | Send immediate notification |
| POST | `/api/v1/notifications/scheduled` | Create scheduled notification |
| GET | `/api/v1/notifications/scheduled` | List scheduled (optional `?status=`) |
| DELETE | `/api/v1/notifications/scheduled/{id}` | Cancel scheduled |
| POST | `/api/v1/events/mobile-user-subscribed` | New mobile subscriber → email to internal |
| POST | `/api/v1/subscriptions` | Register subscription |
| GET | `/api/v1/subscriptions` | List (by userId, channel, audienceType) |
| POST | `/api/v1/internal/process-due-scheduled` | **Internal:** process due scheduled notifications (called by Scheduler Service; optional `X-Internal-Api-Key`). |

GraphQL: mutations `sendNotification`, `createScheduledNotification`, `cancelScheduledNotification`; queries `scheduledNotifications`, `subscriptions`.

## Scheduler Service

Scheduled notifications are processed by a separate **Notification Scheduler Service** (Quarkus), which runs a cron and calls this service’s internal endpoint. See the `NotificationSchedulerService` project in this repo.

## Testing

- **Run all tests:** `mvn test` (unit tests always run; integration tests run only when Docker is available, otherwise skipped).
- **Full test documentation:** [src/test/README.md](src/test/README.md) — explains every unit and integration test, how they work, how to run them, and where reports are generated.

| Test type | What it covers | Infrastructure |
|-----------|----------------|----------------|
| **Unit** | Services (engine, scheduled, idempotency) and REST controllers (notifications, internal, subscriptions, templates, new-subscriber) with mocks | None |
| **Integration** | Full app + real HTTP + PostgreSQL, MongoDB, Redis (Testcontainers) — templates, send, subscriptions, new-subscriber, scheduled create/process/cancel | Docker (Postgres, Mongo, Redis containers) |

## Push to remote (fix "no upstream configured for branch main")

If you see **no upstream configured for branch 'main'**, set the remote and push once:

```bash
# Add your remote (replace with your repo URL, e.g. GitHub/GitLab)
git remote add origin https://github.com/YOUR_USER/notification-service.git

# Push and set upstream so future push/pull work
git push -u origin main
```

If `origin` already exists but the branch still has no upstream, run only:

```bash
git push -u origin main
```

## Design & review

- [docs/DESIGN.md](docs/DESIGN.md) — Architecture, data model, and technology choices.
- [docs/REVIEW.md](docs/REVIEW.md) — Full review of code, tests, security, and recommendations.
