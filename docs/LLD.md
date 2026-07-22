# Low-Level Design (LLD)

## Domain

- `Notification` — send unit with status, attempts, `nextRetryAt`
- `NotificationTemplate` — channel template with required variables
- `DeliveryAttempt` — persisted provider attempt
- `DeadLetterRecord` — exhausted notification snapshot

Statuses: `ACCEPTED → QUEUED|SCHEDULED → SENDING → DELIVERED|RETRYING|DEAD_LETTERED`

## Persistence (PostgreSQL)

| Table | Purpose |
|---|---|
| `templates` | Seeded channel templates |
| `notifications` | Source of truth for each send |
| `delivery_attempts` | Per-try provider results |
| `dead_letters` | Poison messages after max retries |
| `provider_inbox` | Captured SMS/WA/Push/Slack webhook payloads |

JPA entities live under `persistence/`; domain mapping via `NotificationMapper`.

## Redis

`RedisCacheStore` implements `CacheStore`:

- `idempotency:{key}` → notification id (SET NX + TTL)
- `rate:{channel}:{recipient}` → INCR + TTL window
- `template:{id}` → cache marker

## Kafka

- Produce: `KafkaMessageBroker` via `KafkaTemplate`
- Consume: `NotificationKafkaListener` on `platform.kafka-topic`
- DLQ produce: `platform.dlq-topic`
- Lag: AdminClient-based `lag()` for ops

## Delivery

| Channel | Implementation |
|---|---|
| EMAIL | `EmailChannelSender` → JavaMailSender (MailHog) |
| SMS / WhatsApp / Push / Slack | `HttpWebhookChannelSender` → local `/internal/providers/*` |

`ChannelSenderRegistry` holds all `ChannelSender` beans (Strategy + Registry).

## Workers

- Kafka listener: enqueue into `PriorityNotificationQueue`, process one
- Scheduled: release due `SCHEDULED` rows → Kafka
- Scheduled: release due `RETRYING` rows → priority queue → batch process

## API

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/notifications` | Optional `Idempotency-Key` header |
| GET | `/api/v1/notifications/{id}` | Status + body |
| GET | `/api/v1/notifications/{id}/attempts` | Attempt history |
| GET | `/api/v1/templates` | Template catalog |
| GET | `/api/v1/analytics` | In-process funnel counters |
| GET | `/api/v1/dead-letters` | DLQ rows |
| GET | `/api/v1/provider-inbox` | Non-email provider captures |

## Patterns

| Pattern | Where |
|---|---|
| Strategy | `ChannelSender` |
| Registry | `ChannelSenderRegistry` |
| Ports & adapters | `MessageBroker`, `CacheStore`, repositories |
| Builder | `Notification`, request DTOs |
| Policy | `RetryPolicy` |

## Thread safety

- Redis / Postgres / Kafka handle cross-process state
- In-process priority queue uses concurrent structures + reclaim lock
- Notification mutators are synchronized before flush to JPA
