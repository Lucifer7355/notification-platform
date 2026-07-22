# Notification Platform

Multi-channel notification system designed for **billions of notifications/day** — built as a portfolio / interview-ready Java 21 project.

## Why this project

Shows end-to-end design of a production-style notification platform:

| Capability | Implementation |
|---|---|
| Channels | Email, SMS, WhatsApp, Push, Slack (Strategy + Registry) |
| Ingress | Kafka-like partitioned log (`MessageBroker`) |
| Hot path | Priority queue with visibility timeout |
| Reliability | Exponential backoff retries → Dead Letter Queue |
| Timing | Heap-based scheduler for delayed send |
| Templates | `{{placeholder}}` rendering + required-variable validation |
| Redis | Template cache, idempotency keys, per-recipient rate limits |
| Analytics | Accept / deliver / retry / DLQ counters by channel & priority |

## Architecture (billions/day)

```
 Client / Services
        │
        ▼
 ┌──────────────────┐
 │  Accept API      │  validate → render template → idempotency (Redis)
 │  (sync, fast)    │  rate-limit (Redis) → publish
 └────────┬─────────┘
          │
          ▼
 ┌──────────────────┐
 │  Kafka topic     │  notifications  (+ notifications.dlq)
 │  (durable log)   │  back-pressure, replay, fan-out to workers
 └────────┬─────────┘
          │
          ▼
 ┌──────────────────┐
 │  Priority Queue  │  CRITICAL > HIGH > NORMAL > LOW
 │  + visibility TO │  in-flight reclaim if worker crashes
 └────────┬─────────┘
          │
          ▼
 ┌──────────────────┐
 │  Channel Workers │  Email │ SMS │ WhatsApp │ Push │ Slack
 └────────┬─────────┘
          │
     success / fail
          │
    ┌─────┴─────┐
    ▼           ▼
 Delivered   Retry (backoff) → DLQ after max attempts
                │
                ▼
           Analytics
```

### Scale notes (what you'd say in interviews)

- **Ingress**: Kafka partitions by `recipient` / `tenantId` for ordering + horizontal consumers.
- **Workers**: Stateless pods; scale on consumer lag.
- **Priorities**: Separate topics (`critical`, `normal`) or weighted fair queues at high QPS.
- **Providers**: Circuit breakers + bulkheads per channel; vendor failover (SES ↔ SendGrid).
- **Idempotency**: Redis / DB unique key on `(tenant, idempotencyKey)`.
- **Templates**: Redis cache + CDN for media; versioned templates.
- **Analytics**: Emit metrics to Kafka → ClickHouse / Druid; not on the hot path.
- **DLQ**: Replay tooling + alerting on DLQ rate.

This repo uses **in-memory Kafka/Redis adapters** so it runs with zero infra. Interfaces are swap-ready for real clients.

## Quick start

```bash
# Requires JDK 21+
mvn test
mvn -q exec:java
```

## Demo scenarios (`Main`)

1. Happy path across all 5 channels  
2. Priority ordering (CRITICAL before LOW)  
3. Retry with exponential backoff  
4. Dead letter queue after max attempts  
5. Scheduled / delayed notifications  
6. Template + recipient validation  
7. Redis idempotency + rate limiting  
8. Analytics snapshot  

## Package map

```
com.notificationplatform
├── channel/          # Strategy senders + registry
├── kafka/            # MessageBroker abstraction + in-memory log
├── redis/            # CacheStore abstraction + in-memory Redis
├── queue/            # Priority queue + visibility timeout
├── retry/            # Exponential backoff policy
├── dlq/              # Dead letter queue
├── scheduling/       # Delayed notification heap
├── template/         # Placeholder renderer
├── analytics/        # Funnel metrics
├── service/          # Orchestration (accept → kafka → deliver)
├── domain/           # Notification, Template, Priority, Status
└── Main.java         # Interview-style walkthrough
```

## Design patterns (used deliberately)

| Pattern | Where | Why |
|---|---|---|
| Strategy | `ChannelSender` | Swap Email/SMS/... without changing orchestrator |
| Registry | `ChannelSenderRegistry` | O(1) lookup, no switch explosion |
| Template Method-ish flow | `NotificationPlatformService` | Shared accept → queue → deliver algorithm |
| Repository | Notification / Template stores | Testable persistence boundary |
| Adapter-ready ports | `MessageBroker`, `CacheStore` | Real Kafka/Redis plug in later |
| Builder | `Notification`, requests, config | Safe construction of multi-field objects |

Not forced: Observer, Decorator, Proxy, State — no current need.

## Resume bullet (copy-paste)

> Designed a multi-channel Notification Platform (Email/SMS/WhatsApp/Push/Slack) in Java 21 with Kafka-style ingress, Redis idempotency & rate limits, priority queues, exponential-backoff retries, DLQ, scheduling, template rendering, and delivery analytics — modeled for billions of notifications/day.

## Tech

- Java 21  
- Maven  
- JUnit 5 + AssertJ  

## License

MIT — use freely in your portfolio.
