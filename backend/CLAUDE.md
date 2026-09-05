# Aura Ride — Backend (Django + DRF)

REST API for Aura Ride. Authoritative contract lives in [`../PROJECT_SPEC.md`](../PROJECT_SPEC.md)
(§5–§12, §14, §17); project-wide hard constraints in [`../CLAUDE.md`](../CLAUDE.md)
(no Celery/Redis/Channels/PostGIS/FCM/LLM).

---

## Stack

- **Django + Django REST Framework** — REST only. WSGI is fine; no ASGI/Channels.
- **PostgreSQL** (plain, **NO PostGIS**) — locations stored as `lat`/`lng` float columns.
- **DRF SimpleJWT** — access + refresh tokens.
- **DeepFace** — face matching, run **synchronously inside the request** (no workers).
- Calls two free local services: **OSRM** (routing/ETA for fare estimate) and **Nominatim**
  (geocoding). Both run locally via Docker.

---

## Common commands

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python manage.py migrate
python manage.py createsuperuser          # admin for the verification dashboard
python manage.py runserver 0.0.0.0:8000   # bind 0.0.0.0 so a physical phone can reach it
python manage.py test
```

---

## Local dev / phone access

- Add your machine's LAN IP to `ALLOWED_HOSTS`, or use `adb reverse tcp:8000 tcp:8000`.
- The Android app reaches this server over LAN IP or `adb reverse` (see [`../android/CLAUDE.md`](../android/CLAUDE.md)).

---

## Rules & conventions

- **Admin** = Django's built-in superuser. Verification review is a customized Django admin /
  server-rendered templates — **no SPA**. Audit-log every approve/reject (admin id, timestamp, reason).
- **Locations:** `lat`/`lng` floats; matching = **haversine in Python** over online drivers (§17.4).
- **Matching offers:** sequential nearest-first via `Ride.offered_driver` + `offer_expires_at`;
  guard `accept` with a row lock / conditional update — **first-accept-wins** (§17.8). Make transitions idempotent.
- **Biometric re-auth:** verify an **ECDSA-SHA256** signature over a single-use, short-lived nonce
  against the user's stored public key; **consume** the nonce on use (§17.1).
- **AI checks** (face match + OCR cross-check) are **synchronous** — never add async/worker code (§17.5).
- **Privacy:** delete raw NID/license images **on approval**; keep only extracted fields + face embedding (§14).
- **Never** store fingerprints or private keys — only the public key.
- Keep endpoints/payloads identical to `PROJECT_SPEC.md` §12; update the spec if you change them.
