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
- `intervalSec`
- `enabled`
- `timeoutMs`

HTTP-only fields:
- `httpMethod` (defaults to `GET` when omitted)
- `expectedResponseKeyword` (optional)

## Runtime behavior

- `HTTP` monitors execute using configured `httpMethod` and optional keyword matching.
- `TCP` monitors execute socket connectivity checks against `host:port`.
- `PING` monitors execute ICMP-like reachability checks via the network check client.

## Product boundary kept intentionally minimal

Retained:
- Exit1-like core controls (HTTP/TCP/PING, HTTP method, expected keyword)

Explicitly out of scope:
- SSL/certificate validation controls
- Enterprise extras and additional notification-channel scope changes
