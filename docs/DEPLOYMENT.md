# Deployment Guide — AWS / Azure (PaaS, HA)

## Scalability and availability

- Run **at least 2 instances** of the Notification Service behind a load balancer.
- Use **managed databases and caches** (PaaS) instead of self-managed where possible.

## AWS

### Compute

- **ECS (Fargate):** Service with min 2 tasks; Application Load Balancer (ALB) in front; TLS 1.2+ on ALB.
- **EKS:** Deployment with `replicas: 2`; use ALB Ingress or AWS Load Balancer Controller.

### Data stores

- **PostgreSQL:** Amazon RDS (Multi-AZ for HA); same VPC as the service.
- **MongoDB:** Amazon DocumentDB (MongoDB-compatible) or keep MongoDB on EC2/ECS; use for notification events/logs.
- **Redis:** Amazon ElastiCache for Redis (cluster or replication group); used for idempotency, rate limiting, scheduler lock.

### Channels (PaaS)

- **Email:** Amazon SES (verify domain and from address).
- **SMS:** Amazon SNS (SMS); set region and permissions.
- **Push:** Firebase Cloud Messaging (FCM) via Firebase Admin SDK; store service account key in Secrets Manager.

### Secrets

- Store DB passwords, API keys, and OAuth/JWT config in **AWS Secrets Manager**.
- Inject at runtime via environment variables or IAM-backed SDK calls.

### Example ECS task env (conceptual)

```yaml
POSTGRES_HOST: <rds-endpoint>
POSTGRES_DB: notification_db
MONGODB_URI: mongodb://...
REDIS_HOST: <elasticache-endpoint>
OAUTH2_ISSUER_URI: https://your-tenant.auth0.com/
notification.channels.email.provider: aws-ses
notification.channels.sms.provider: aws-sns
notification.channels.push.provider: firebase-fcm
SSL_ENABLED: true
```

---

## Azure

### Compute

- **App Service:** Web App with **minimum 2 instances** (Scale out); enable HTTPS only (TLS 1.2+).
- **AKS:** Deployment with `replicas: 2`; use Application Gateway or Azure Load Balancer with TLS.

### Data stores

- **PostgreSQL:** Azure Database for PostgreSQL (Flexible Server or Single Server with HA options).
- **MongoDB:** Azure Cosmos DB for MongoDB or Azure Cosmos DB API for MongoDB.
- **Redis:** Azure Cache for Redis (Standard/Premium for replication).

### Channels (PaaS)

- **Email/SMS:** Azure Communication Services (Email and SMS).
- **Push:** Firebase FCM (same as AWS) or Azure Notification Hubs.

### Secrets

- Use **Azure Key Vault** for connection strings, API keys, and OAuth/JWT settings.
- Reference from App Service (Key Vault references) or inject into AKS pods.

### Example App Service app settings (conceptual)

```
POSTGRES_HOST=<postgres-server>.postgres.database.azure.com
MONGODB_URI=<cosmos-mongo-connection-string>
REDIS_HOST=<redis>.redis.cache.windows.net
OAUTH2_ISSUER_URI=https://your-tenant.b2clogin.com/...
notification.channels.email.provider: azure-communication
notification.channels.sms.provider: azure-communication
notification.channels.push.provider: firebase-fcm
SSL_ENABLED=true
```

---

## OWASP and TLS

- Enforce **SSL/TLS 1.2 or higher** at the load balancer and application (disable TLS 1.0/1.1).
- Use **HTTPS only**; secure cookies and headers as per OWASP recommendations.
- Keep dependencies up to date (`./mvnw dependency:check`).

## Summary

| Concern        | AWS (PaaS)              | Azure (PaaS)           |
|----------------|-------------------------|------------------------|
| Compute        | ECS Fargate / EKS       | App Service / AKS      |
| PostgreSQL     | RDS                     | Azure Database for PostgreSQL |
| NoSQL / events | DocumentDB / DynamoDB   | Cosmos DB              |
| Cache          | ElastiCache             | Azure Cache for Redis  |
| Email/SMS      | SES, SNS                | Communication Services |
| Secrets        | Secrets Manager         | Key Vault              |
