# Low-Level Design (LLD)

## Domain model

### Entities

| Entity | Responsibility | Lifecycle |
|---|---|---|
| `Notification` | Single send unit (channel, recipient, rendered body, priority, status, attempts) | ACCEPTED → QUEUED/SCHEDULED → SENDING → DELIVERED / RETRYING / DEAD_LETTERED |
| `NotificationTemplate` | Channel-scoped subject/body patterns + required variables | Registered once; read on accept |
| `DeliveryAttempt` | Record of one provider call | Append-only per send try |
| `DeadLetterRecord` | Snapshot of failed notification + reason | Created when retries exhausted |

### Enums

- `ChannelType`: EMAIL, SMS, WHATSAPP, PUSH, SLACK
- `Priority`: CRITICAL (100), HIGH (75), NORMAL (50), LOW (25)
- `NotificationStatus`: ACCEPTED, QUEUED, SCHEDULED, SENDING, DELIVERED, FAILED, RETRYING, DEAD_LETTERED

### Relationships

```
NotificationTemplate 1 ── renders ──► Notification
Notification 1 ── has many ──► DeliveryAttempt
Notification 0..1 ── becomes ──► DeadLetterRecord
```

## Class diagram (core)

```
SendNotificationRequest
        │
        ▼
NotificationRequestValidator
        │
        ▼
NotificationPlatformService ──► TemplateService ──► TemplateRenderer
        │                         │
        │                         └── TemplateRepository / CacheStore
        ├── NotificationRepository
        ├── MessageBroker (Kafka port)
        ├── NotificationScheduler
        ├── PriorityNotificationQueue
        ├── DeliveryService ──► ChannelSenderRegistry ──► ChannelSender[]
        ├── RetryPolicy
        ├── DeadLetterQueue
        └── AnalyticsService
```

## APIs

### Accept

```text
NotificationReceipt accept(SendNotificationRequest request)
NotificationReceipt accept(SendNotificationRequest request, String idempotencyKey)
```

**Request fields:** channel, recipient, templateId, variables, priority, optional scheduledAt

**Receipt fields:** notificationId, channel, recipient, priority, status, acceptedAt, scheduledAt

### Worker / pipeline controls

```text
int drainKafkaToPriorityQueue(int maxMessages)
int releaseDueScheduled()
int releaseDueRetries()
Optional<Notification> processNext()
int processAvailable(int max)
```

## End-to-end algorithm

### Accept

1. Validate request (recipient format per channel, required fields)
2. If `idempotencyKey` present and cached → return existing receipt
3. Enforce Redis rate limit for `(channel, recipient)`
4. Load template; ensure template channel matches request channel
5. Render subject/body; fail if required variables missing
6. Persist `Notification` (status ACCEPTED)
7. Cache idempotency key → notification id
8. If `scheduledAt` > now → scheduler (SCHEDULED); else publish to Kafka (QUEUED)

### Deliver

1. Poll priority queue (after releasing due retries)
2. Mark SENDING; invoke `ChannelSender` for channel
3. On success → DELIVERED; ack queue; analytics
4. On failure → if attempts < max → RETRYING + schedule retry delay; else DLQ + DLQ topic

## Key classes

### `PriorityNotificationQueue`

- `PriorityBlockingQueue` ordered by priority weight desc, then sequence asc
- On `poll`, message moves in-flight with `visibleAt = now + visibilityTimeout`
- Unacked messages are reclaimed after timeout (worker crash safety)

### `ExponentialBackoffRetryPolicy`

```text
delay(attempt) = min(maxBackoff, initialBackoff * multiplier^(attempt-1))
shouldRetry = attemptCount < maxAttempts
```

### `ChannelSender` + `ChannelSenderRegistry`

- Strategy per channel
- Registry is `EnumMap<ChannelType, ChannelSender>` — no switch explosion
- `ConfigurableChannelSender` used in demo/tests (injectable failure predicate)

### `MessageBroker` / `InMemoryMessageBroker`

- Port: `publish`, `poll`, `commit`, `lag`, `snapshot`
- In-memory append-only log with consumer-group offsets

### `CacheStore` / `InMemoryCacheStore`

- Port: `set`, `get`, `setIfAbsent`, `increment`, `delete` with TTL
- Used for template cache markers, idempotency, rate windows

### `NotificationScheduler`

- Min-heap by `fireAt`
- `dueNotifications()` releases ready items into Kafka publish path

### `TemplateRenderer`

- Replaces `{{var}}` placeholders
- Rejects missing required variables before enqueue

## Patterns used

| Pattern | Class | Reason |
|---|---|---|
| Strategy | `ChannelSender` | Per-channel send behavior |
| Registry | `ChannelSenderRegistry` | O(1) channel lookup |
| Repository | `NotificationRepository`, `TemplateRepository` | Persistence boundary |
| Ports & adapters | `MessageBroker`, `CacheStore` | Swap Kafka/Redis later |
| Builder | `Notification`, requests, `PlatformConfig` | Multi-field construction |
| Policy | `RetryPolicy` | Pluggable backoff |

Not used (no current need): Observer, Decorator, Proxy, State machine hierarchy.

## Thread safety

| Shared state | Approach |
|---|---|
| Repositories / cache / broker | `ConcurrentHashMap` / concurrent collections |
| Priority queue reclaim | `ReentrantLock` around in-flight reclaim/ack |
| Retry heap / scheduler | Locked heap mutations |
| Analytics counters | `AtomicLong` |
| Notification status/attempts | Synchronized mutators on entity |

Time and IDs are injected (`Clock`, `IdGenerator`) for deterministic tests.

## Validation & errors

| Exception | When |
|---|---|
| `ValidationException` | Bad recipient, missing template vars, rate limit, channel mismatch |
| `TemplateNotFoundException` | Unknown template id |
| `ChannelDeliveryException` | Missing sender / channel mismatch at dispatch |
| `NotificationException` | Base type |

Fail-fast on accept: invalid requests never enter Kafka.

## Test strategy

| Layer | Coverage |
|---|---|
| Unit | TemplateRenderer, RetryPolicy, Validator, Queue, Broker, Cache |
| Service | Accept → drain → deliver, retry → DLQ, schedule, idempotency, rate limit, priority order |
| Demo | `Main` scenarios for manual walkthrough |

## Extensibility

- Add channel: implement `ChannelSender`, register in factory
- Real Kafka/Redis: new adapter implementing existing ports
- Persist notifications: swap `InMemoryNotificationRepository` for JDBC/JPA
- Priority at scale: partition Kafka topics by priority band without changing accept contract
