# High-Level Design (HLD)

## Problem

Build a notification platform that accepts send requests from product services and delivers them over multiple channels (Email, SMS, WhatsApp, Push, Slack) with reliability, prioritization, scheduling, templating, and observability — at very high throughput.

## Goals

- Multi-channel delivery behind one accept API
- Fast synchronous accept; asynchronous delivery
- Priority-aware processing (CRITICAL before LOW)
- At-least-once delivery with retries and DLQ
- Scheduled / delayed sends
- Template-based content with validation
- Idempotency and rate limiting
- Delivery analytics

## Non-goals (this codebase)

- Real provider SDKs (SES, Twilio, FCM, Slack API) — channel senders are swappable
- Multi-tenant auth / billing
- Exactly-once end-to-end delivery (providers are typically at-least-once)

## Capacity target

Designed conceptually for **billions of notifications/day**:

- Accept path stays cheap (validate → render → Redis checks → Kafka produce)
- Workers scale horizontally on consumer lag
- Hot-path state is Redis + Kafka; durable notification records can live in a DB at production scale

## System context

```
┌─────────────┐     accept      ┌──────────────────────┐
│  Product    │ ──────────────► │ Notification Platform│
│  Services   │ ◄────────────── │                      │
└─────────────┘   receipt id    └──────────┬───────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    ▼                      ▼                      ▼
              Email / SMS            WhatsApp / Push            Slack
              providers              providers                  API
```

## Logical architecture

```
                ┌─────────────────────────────────────────────┐
                │                 Accept API                  │
                │  validate → template → idempotency (Redis)  │
                │  rate-limit (Redis) → persist → publish     │
                └──────────────────────┬──────────────────────┘
                                       │
                                       ▼
                          ┌────────────────────────┐
                          │  Kafka: notifications  │
                          │  (+ notifications.dlq) │
                          └────────────┬───────────┘
                                       │
                                       ▼
                          ┌────────────────────────┐
                          │ Priority worker queue  │
                          │ visibility timeout     │
                          └────────────┬───────────┘
                                       │
                                       ▼
                          ┌────────────────────────┐
                          │   Channel dispatch     │
                          │ Email SMS WA Push Slack│
                          └────────────┬───────────┘
                                       │
                          success ─────┼───── failure
                             │         │
                             ▼         ▼
                         Delivered   Retry backoff
                                       │
                                       ▼ (max attempts)
                                      DLQ
                                       │
                                       ▼
                                   Analytics
```

Scheduler sits beside accept: future `scheduledAt` notifications are held until due, then published to Kafka.

## Core components

| Component | Responsibility |
|---|---|
| Accept API | Validation, templating, idempotency, rate limit, enqueue |
| Kafka (MessageBroker) | Durable ingress, back-pressure, replay, DLQ topic |
| Redis (CacheStore) | Template cache keys, idempotency, rate counters |
| Priority queue | Ordered work for workers; reclaim on visibility timeout |
| Scheduler | Time-based release of delayed notifications |
| Channel workers | Provider-specific send via Strategy registry |
| Retry + DLQ | Transient failure recovery; poison-message isolation |
| Analytics | Counters for funnel and channel/priority breakdown |

## Data flow

1. Client calls `accept(request[, idempotencyKey])`
2. Validate recipient / template / channel match
3. Render template; create `Notification`
4. If scheduled in the future → scheduler; else → Kafka topic
5. Workers drain Kafka → priority queue → `deliver`
6. On failure → retry with exponential backoff; after max attempts → DLQ + DLQ topic
7. Analytics updated on accept / deliver / retry / DLQ

## Scaling model

| Layer | Scale approach |
|---|---|
| Accept | Stateless replicas behind LB |
| Kafka | Partition by `tenantId` or `recipient` for ordering |
| Workers | Consumer groups; scale on lag |
| Priority | Separate topics per priority band, or weighted fair queues |
| Redis | Cluster / key sharding by recipient |
| Providers | Circuit breaker + bulkhead per channel; vendor failover |

## Failure modes

| Failure | Handling |
|---|---|
| Provider 5xx / timeout | Retry with backoff |
| Poison payload / permanent fail | DLQ after max attempts |
| Worker crash mid-send | Visibility timeout requeues message |
| Duplicate client submit | Idempotency key in Redis |
| Traffic spike per recipient | Rate limit window in Redis |

## Observability

- Accept / delivered / failed / retried / dead-lettered counters
- Breakdown by channel and priority
- At production scale: emit events to a metrics topic → OLAP (ClickHouse / Druid), lag alerts on Kafka and DLQ rate

## Deployment note

This repository ships **in-memory** `MessageBroker` and `CacheStore` implementations for local demo and tests. Production would swap adapters for Kafka and Redis clients without changing the orchestration service.
