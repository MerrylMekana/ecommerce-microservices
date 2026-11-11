# Observability

The local observability stack uses Spring Boot Actuator/Micrometer, Prometheus,
Grafana, Loki, and Tempo's Zipkin receiver. Prometheus scrapes each application
at `/actuator/prometheus`; the application services send Logback events to Loki
and Micrometer traces to the Zipkin endpoint on port 9411.

## Business meters

The custom counters have no request-derived labels. The shared `application`
tag identifies the service; standard HTTP meters retain their bounded
method/status/URI tags.

| Prometheus metric | Type | Meaning |
|---|---|---|
| `orders_created_count_total` | Counter | Order persistence transaction created an order |
| `orders_succeeded_total` | Counter | Order persistence and idempotency transition succeeded |
| `orders_failed_total` | Counter | Order request ended with a runtime failure |
| `orders_idempotency_replays_total` | Counter | Existing matching idempotency key was reused |
| `inventory_reservations_total` | Counter | New inventory reservation succeeded |
| `inventory_reservation_failures_total` | Counter | Reservation returned a rejection or failed with a runtime exception |
| `inventory_insufficient_stock_total` | Counter | Reservation was rejected specifically for insufficient stock |
| `inventory_adjustment_replays_total` | Counter | Matching admin adjustment key replayed a stored result |
| `order_outbox_pending` | Gauge | Outbox rows not yet marked published, including active leases |
| `order_outbox_publish_success_total` | Counter | Kafka acknowledged publication and the row was marked published |
| `order_outbox_publish_failure_total` | Counter | Outbox publish/mark attempt failed |
| `order_outbox_retries_total` | Counter | Failed outbox attempt was scheduled for retry |
| `inventory_compensation_pending` | Gauge | Orders whose inventory compensation remains pending |

The compensation backlog is a gauge without a `_total` suffix because it reports
the current outstanding count, not a cumulative event count.

## Demo alert thresholds

Rules are in `api-gateway/docker/prometheus/alerts.yml` and are intentionally
local/demo defaults, not production SLOs:

| Alert | Condition | Hold time |
|---|---|---|
| `ElevatedHttp5xxRate` | 5xx share above 5%, with traffic | 5 minutes |
| `HighHttpRequestLatency` | p95 HTTP latency above 1 second | 5 minutes |
| `OrderOutboxBacklog` | More than 10 unpublished rows | 2 minutes |
| `OrderOutboxPublishFailures` | Any publish failure in 5 minutes | 2 minutes |
| `InventoryCompensationPending` | At least one compensation remains pending | 2 minutes |
| `InventoryReservationFailuresIncreasing` | At least 3 reservation failures in 5 minutes | 2 minutes |
| `ApplicationServiceDown` | A configured application scrape is down | 1 minute |

Prometheus evaluates these rules and shows their state in its UI. An
Alertmanager or notification destination is not configured.

## Trace and log correlation

HTTP request traces use Micrometer Tracing/Brave and are exported to Tempo via
the Zipkin receiver. The Order-to-Inventory `RestClient` uses Spring Boot's
configured builder so client observations can propagate trace context. Kafka
template/listener observations are enabled for the outbox publisher and
Notification consumer. The asynchronous outbox dispatch runs after the original
request; therefore, its Kafka trace is not guaranteed to be the same trace as
the HTTP order-creation trace. The stable event ID is logged on both sides to
correlate that asynchronous boundary.

Loki labels are limited to application, host, and log level; trace and span IDs
are included in log message text for lookup, not used as Loki labels. No code
logs Authorization headers, access tokens, client secrets, passwords, or JWTs.
The Angular application is not browser-instrumented, so traces begin at the
Gateway rather than in the browser. The current setup does not add explicit
JDBC/ORM tracing spans, so database calls are not guaranteed to appear as
individual spans. Configure a remote exporter URL and a lower sampling rate
when moving beyond local development.
