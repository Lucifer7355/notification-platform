# Notification Platform

Java 21 Spring Boot service for multi-channel notifications (Email, SMS, WhatsApp, Push, Slack).

Uses **PostgreSQL**, **Redis**, **Kafka**, and **MailHog** — not in-memory stubs.

## Stack

| Layer | Tech |
|---|---|
| API | Spring Boot 3 / REST |
| DB | PostgreSQL + Flyway + JPA |
| Cache / idempotency / rate limit | Redis |
| Ingress + DLQ | Kafka API (Redpanda in Docker Compose) |
| Email | SMTP → MailHog |
| SMS / WhatsApp / Push / Slack | HTTP webhooks (local provider inbox) |
| Workers | `@KafkaListener` + `@Scheduled` |

## Docs

- [High-Level Design (HLD)](docs/HLD.md)
- [Low-Level Design (LLD)](docs/LLD.md)

## Run

Requires JDK 21+, Docker, Maven.

```bash
docker compose up -d
mvn spring-boot:run
```

Services:

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| MailHog UI (emails) | http://localhost:8025 |
| Postgres | localhost:5432 |
| Redis | localhost:6379 |
| Kafka | localhost:9092 |

## API examples

```bash
# Email (visible in MailHog)
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-1" \
  -d "{\"channel\":\"EMAIL\",\"recipient\":\"ada@example.com\",\"templateId\":\"email-welcome\",\"variables\":{\"name\":\"Ada\",\"product\":\"NotifyX\"},\"priority\":\"HIGH\"}"

# SMS
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d "{\"channel\":\"SMS\",\"recipient\":\"+919876543210\",\"templateId\":\"sms-otp\",\"variables\":{\"otp\":\"482913\",\"minutes\":\"5\"},\"priority\":\"CRITICAL\"}"

# Status
curl -s http://localhost:8080/api/v1/notifications/{id}

# Templates / analytics / DLQ / provider inbox
curl -s http://localhost:8080/api/v1/templates
curl -s http://localhost:8080/api/v1/analytics
curl -s http://localhost:8080/api/v1/dead-letters
curl -s http://localhost:8080/api/v1/provider-inbox
```

## Flow

1. `POST /api/v1/notifications` validates, renders template, checks Redis idempotency + rate limit  
2. Persists notification in Postgres  
3. Publishes id to Kafka (`notifications`) — or stores as `SCHEDULED` until due  
4. Kafka listener enqueues into an in-process priority queue and delivers  
5. Email goes through SMTP; other channels POST to local provider webhooks (stored in `provider_inbox`)  
6. Failures retry with exponential backoff (`next_retry_at` in DB); exhausted attempts go to DB DLQ + Kafka `notifications.dlq`

## Tests

```bash
mvn test
```

Unit tests cover templates, validation, retry policy, and priority queue.

## License

MIT
