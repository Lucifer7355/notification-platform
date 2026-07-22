# High-Level Design (HLD)

## Problem

Deliver notifications across Email, SMS, WhatsApp, Push, and Slack with reliability, priority, scheduling, templating, idempotency, rate limiting, and analytics at high throughput.

## Architecture

```
 Clients
    │
    ▼
 ┌──────────────────────┐
 │  Spring REST API     │  validate, template, Redis idempotency/rate-limit
 │  /api/v1/notifications│  persist Postgres → publish Kafka
 └──────────┬───────────┘
            │
            ▼
 ┌──────────────────────┐
 │  Kafka               │  topic: notifications
 │                      │  DLQ topic: notifications.dlq
 └──────────┬───────────┘
            │
            ▼
 ┌──────────────────────┐
 │  Workers             │  @KafkaListener → priority queue → channel senders
 │  @Scheduled          │  release SCHEDULED / RETRYING rows from Postgres
 └──────────┬───────────┘
            │
     ┌──────┴──────┬────────────┬───────────┐
     ▼             ▼            ▼           ▼
  SMTP/MailHog   SMS webhook  WA/Push    Slack webhook
                 (inbox)      webhooks   (inbox)
```

## Infrastructure

| Component | Role |
|---|---|
| PostgreSQL | Notifications, templates, delivery attempts, dead letters, provider inbox |
| Redis | Idempotency keys, rate-limit counters, template cache keys |
| Kafka | Durable async ingress + DLQ |
| MailHog | Local SMTP capture for Email |
| Local webhooks | SMS / WhatsApp / Push / Slack provider simulation |

## Scaling notes

- Stateless API replicas behind a load balancer
- Kafka partitions by recipient/tenant for ordering
- Consumer group scales workers on lag
- Priority can be split into dedicated topics at higher QPS
- Redis Cluster for shared rate limits / idempotency across pods
- Analytics counters can be exported to OLAP via a metrics topic

## Failure handling

| Failure | Handling |
|---|---|
| Provider error | Exponential backoff; `next_retry_at` in Postgres |
| Max retries | Dead letter table + Kafka DLQ topic |
| Worker crash | Priority-queue visibility timeout + Kafka redelivery |
| Duplicate client submit | Redis idempotency key |
| Burst per recipient | Redis INCR rate window |
