# Monitor Minimal Boundary (tickets #114/#115/#119)

This correction restores Exit1-like monitor controls while keeping OpenPulse intentionally minimalist.

## Supported monitor model (create/update/read)

Monitor types:
- `HTTP`
- `TCP`
- `PING`

Common fields:
- `name`
- `type`
- `targetUrl`
- `intervalSec` (allowed values: `60`, `120`, `180`, `240`, `300` => 1–5 minutes)
- `enabled`
- `timeoutMs`
- `emailAlertOnDown` (per-monitor email on incident opened)
- `emailAlertOnRecovery` (per-monitor email on incident resolved)

HTTP-only fields:
- `httpMethod` (defaults to `GET` when omitted)
- `expectedResponseKeyword` (optional)

## Runtime behavior

- `HTTP` monitors execute using configured `httpMethod` and optional keyword matching.
- `TCP` monitors execute socket connectivity checks against `host:port`.
- `PING` monitors execute ICMP-like reachability checks via the network check client.
- Scheduler due-check evaluation uses each monitor's selected interval (1–5 minute options).
- Email dispatch keeps monitor-level preferences: down and recovery can be enabled/disabled independently.

## Product boundary kept intentionally minimal

Retained:
- Exit1-like core controls (HTTP/TCP/PING, HTTP method, expected keyword)

Explicitly out of scope:
- SSL/certificate validation controls
- Enterprise extras and additional notification-channel scope changes
