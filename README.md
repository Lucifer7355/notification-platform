# Notification Platform

Java 21 multi-channel notification system supporting Email, SMS, WhatsApp, Push, and Slack.

Ingress is Kafka-backed, with Redis for caching / idempotency / rate limits, priority queues, retries, DLQ, scheduling, templates, and delivery analytics. Kafka and Redis are behind interfaces with in-memory adapters so the project runs without external infra.

## Features

| Area | Detail |
|---|---|
| Channels | Email, SMS, WhatsApp, Push, Slack |
| Ingress | Kafka-style durable log (`MessageBroker`) |
| Queuing | Priority queue with visibility timeout |
| Reliability | Exponential backoff retries → Dead Letter Queue |
| Scheduling | Delayed / scheduled delivery |
| Templates | `{{placeholder}}` rendering with required-variable checks |
| Redis | Template cache, idempotency keys, per-recipient rate limits |
| Analytics | Accept / deliver / retry / DLQ metrics by channel and priority |

## Docs

- [High-Level Design (HLD)](docs/HLD.md)
- [Low-Level Design (LLD)](docs/LLD.md)

## Quick start

Requires JDK 21+.

```bash
mvn test
mvn -q exec:java
```

`Main` walks through happy path, priority ordering, retries, DLQ, scheduling, validation, Redis idempotency/rate limits, and analytics.

## Package structure

```
com.notificationplatform
├── channel/       Channel senders (Strategy) + registry
├── kafka/         MessageBroker port + in-memory broker
├── redis/         CacheStore port + in-memory cache
├── queue/         Priority queue + visibility timeout
├── retry/         Exponential backoff policy
├── dlq/           Dead letter queue
├── scheduling/    Delayed notification scheduler
├── template/      Template renderer
├── analytics/     Delivery metrics
├── service/       Accept → Kafka → deliver orchestration
├── domain/        Notification, Template, Priority, Status
└── Main.java      End-to-end demo
```

## Tech

- Java 21
- Maven
- JUnit 5 + AssertJ

## License

MIT
