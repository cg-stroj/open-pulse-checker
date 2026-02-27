# Open Pulse Checker - Multi-node Observability Dashboard (Ticket #45)

Use these PromQL panels in Grafana (or equivalent) for multi-node operations.

## 1) Distributed lock contention
- **Lock contention ratio (5m)**
```promql
sum(rate(openpulse_scheduler_lock_acquire_fail_total[5m]))
/
clamp_min(sum(rate(openpulse_scheduler_lock_acquire_success_total[5m]) + rate(openpulse_scheduler_lock_acquire_fail_total[5m])), 1)
```
- **Lock steals (stale lock recovery)**
```promql
sum(rate(openpulse_scheduler_lock_acquire_steal_total[5m]))
```
- **Lease renew failures**
```promql
sum(rate(openpulse_scheduler_lock_renew_fail_total[5m]))
```

## 2) Scheduler skip rate
- **Skip due to distributed lock**
```promql
sum(rate(openpulse_scheduler_execution_skip_lock_total[5m]))
```
- **Skip due to local in-flight dedupe**
```promql
sum(rate(openpulse_scheduler_execution_skip_local_inflight_total[5m]))
```

## 3) DLQ backlog health
- **Current DLQ backlog**
```promql
openpulse_alerts_dlq_backlog
```
- **Oldest unreplayed item age (seconds)**
```promql
openpulse_alerts_dlq_oldest_age_seconds
```
- **DLQ enqueue rate**
```promql
sum(rate(openpulse_alerts_dlq_total[5m]))
```
- **DLQ replay success rate**
```promql
sum(rate(openpulse_alerts_dlq_replay_total{result="success"}[5m]))
```

## 4) Notifier success/failure
- **Dispatch attempts by outcome/channel**
```promql
sum by (channel, outcome) (rate(openpulse_alerts_dispatch_attempts_total[5m]))
```
- **Notifier failure ratio**
```promql
sum(rate(openpulse_alerts_dispatch_attempts_total{outcome="failed"}[10m]))
/
clamp_min(sum(rate(openpulse_alerts_dispatch_attempts_total{outcome=~"success|failed"}[10m])), 1)
```

## 5) Delivery and dispatch latency
- **Dispatch latency p95 by channel**
```promql
histogram_quantile(0.95,
  sum by (channel, le) (rate(openpulse_alerts_dispatch_latency_seconds_bucket[10m]))
)
```
- **End-to-end delivery delay p95**
```promql
histogram_quantile(0.95,
  sum by (channel, le) (rate(openpulse_alerts_delivery_delay_seconds_bucket{outcome="success"}[10m]))
)
```

## SLO-aligned targets
- Lock contention ratio < 10% (warning at >20% for 10m)
- Notifier failure ratio < 1% rolling 10m (critical at >5% for 10m)
- Alert delivery delay p95 < 120s (warning above 120s for 15m)
- DLQ backlog near zero during steady state; critical when backlog >25 or oldest age >15m
