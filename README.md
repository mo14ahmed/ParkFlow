# ParkFlow

Smart parking capstone: the website and backend API at www.parkflow.tech.
The same API serves the website, the iOS app and the ESP32 in-car screen.

What works now (plan Phases 0 to 3):
- Sign up as a **Customer** or a **Parking provider** (username, password, first and last name, phone; providers add a company name, customers can add a work location).
- Log in on any device with a token (JWT).
- Providers wait for an **admin** to approve them. While pending they can log in and add buildings, but customers don't see those buildings yet.
- Providers add a **parking building**: a name and a pin dropped on an OpenStreetMap map.
- Customers search **any place** (free OpenStreetMap search) and see the **5 closest** parking buildings on a list and a map.
- The old daily clipboard still works, but it is off the menus and needs `PARKFLOW_API_KEY` to be set.

Design rules for every screen: [docs/design-system.md](docs/design-system.md).

## Run it on your PC (no database install)

Needs JDK 17 or newer.

```powershell
.\mvnw.cmd clean spring-boot:run "-Dspring-boot.run.profiles=local"
```

Open http://localhost:8080/login.html. The `local` profile keeps data in `./data/` (H2 database) and creates an admin account `admin` / `admin-local-only`. Never use this profile on the VPS.

Run the tests: `.\mvnw.cmd clean verify` (uses an in-memory database, nothing to install).

Always build with `clean` after pulling these changes: old compiled classes in `target/` from before the package move stop the app from starting.

## Run it with PostgreSQL (the VPS)

1. Install PostgreSQL and create the database once:
   ```sql
   CREATE USER parkflow PASSWORD 'choose-a-strong-password';
   CREATE DATABASE parkflow OWNER parkflow;
   ```
   Tables are created automatically at startup by Flyway (`src/main/resources/db/migration`).
2. Set these environment variables for the service that runs the WAR (for example in the systemd unit):

   | Variable | Required | What it is |
   |---|---|---|
   | `PARKFLOW_DB_URL` | yes | e.g. `jdbc:postgresql://localhost:5432/parkflow` |
   | `PARKFLOW_DB_USER` | yes | e.g. `parkflow` |
   | `PARKFLOW_DB_PASSWORD` | yes | the database password |
   | `PARKFLOW_JWT_SECRET` | yes | random text, 32+ characters. Signs login tokens. Without it, everyone is logged out on every restart. |
   | `PARKFLOW_ADMIN_USERNAME` | first run | admin account to create if it doesn't exist |
   | `PARKFLOW_ADMIN_PASSWORD` | first run | its password (8+ characters) |
   | `PARKFLOW_API_KEY` | only for the clipboard | new clipboard key. If unset, the clipboard API is off. |
   | `PARKFLOW_GEOCODER_COUNTRIES` | no | limit place search to countries, e.g. `ca` |
   | `PARKFLOW_JWT_TTL` | no | how long a login lasts, default `7d` |

   Generate secrets on Linux with `openssl rand -base64 48`.
3. Build and deploy as before: `./mvnw clean package`, then copy `target/ParkFlow-0.0.1-SNAPSHOT.war` and restart. Nginx needs no change.

## Security note: the old clipboard key

The previous clipboard key was committed to git (in `application.properties` and `API-KEY.txt`). It has been removed from the code, but it is still in the git history, so treat it as public:
1. Make a new key (`openssl rand -hex 24`) and set it as `PARKFLOW_API_KEY` on the VPS.
2. Put the new key in your iOS clipboard app.
3. Delete `API-KEY.txt` from the repo (`git rm API-KEY.txt`). It is now in `.gitignore`.

## API for the iOS app and the ESP32

Base URL: `https://www.parkflow.tech`. Send JSON. Errors always look like `{"error": "message to show"}`; validation errors also include `"fields": {"phone": "..."}`.

After sign up or log in, send the token on every call: `Authorization: Bearer <token>`. Logging out means forgetting the token.

| Method | Path | Who | Body / query | Returns |
|---|---|---|---|---|
| POST | `/api/auth/signup` | anyone | `{username, password, firstName, lastName, phone, role: "CUSTOMER"\|"PROVIDER", companyName?, workLocation?}` | 201 `{token, expiresAt, user}` |
| POST | `/api/auth/login` | anyone | `{username, password}` | `{token, expiresAt, user}` |
| GET | `/api/me` | logged in | | `user` |
| PUT | `/api/me` | logged in | `{firstName, lastName, phone, companyName?, workLocation?}` | `user` |
| PUT | `/api/me/password` | logged in | `{currentPassword, newPassword}` | 204 |
| GET | `/api/search?q=CN Tower` | customer, admin | | `{place: {name, latitude, longitude}, buildings: [...]}` (best match + 5 closest; easiest for the ESP32) |
| GET | `/api/places?q=...` | customer, provider, admin | | up to 5 `{name, latitude, longitude}` |
| GET | `/api/buildings/nearby?lat=..&lng=..&limit=5` | customer, admin | | `[{id, name, latitude, longitude, company, distanceKm}]`, closest first |
| GET | `/api/provider/buildings` | provider | | `[{id, name, latitude, longitude, visible, createdAt, updatedAt}]` |
| POST | `/api/provider/buildings` | provider | `{name, latitude, longitude}` | 201 building |
| PUT | `/api/provider/buildings/{id}` | provider (own) | `{name, latitude, longitude}` | building |
| DELETE | `/api/provider/buildings/{id}` | provider (own) | | 204 |
| GET | `/api/admin/providers?status=PENDING` | admin | status optional | `[{provider: user, buildingCount}]` |
| POST | `/api/admin/providers/{id}/approve` | admin | | `user` |
| POST | `/api/admin/providers/{id}/reject` | admin | | `user` |

`user` is `{id, username, firstName, lastName, phone, role, status, companyName, workLocation, createdAt, reviewedAt}`.
`status` is `ACTIVE`, `PENDING` (provider waiting) or `REJECTED`. Distances are straight-line kilometres.

Common status codes: 400 bad input, 401 not logged in or token expired, 403 wrong account type, 404 not found, 409 username taken, 503 place search temporarily unavailable.

Example (ESP32 or curl):

```bash
TOKEN=$(curl -s https://www.parkflow.tech/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"mohamed","password":"..."}' | jq -r .token)
curl -s "https://www.parkflow.tech/api/search?q=CN%20Tower" -H "Authorization: Bearer $TOKEN"
```

## Maps and place search

- Map: [Leaflet](https://leafletjs.com) 1.9.4 (copied into `static/vendor/leaflet`) with OpenStreetMap tiles. Keep the "© OpenStreetMap contributors" credit visible.
- Place search: OpenStreetMap [Nominatim](https://nominatim.org). It is free but allows about 1 search per second, so the server waits between calls and remembers recent results. That is fine for the demo; for heavy use, host your own Nominatim and set `PARKFLOW_GEOCODER_URL`.

## Code layout

```
src/main/java/com/parkflow/ParkFlow/
  user/       accounts, sign up, log in, profile, admin approvals
  building/   providers' parking buildings and the "closest buildings" query
  search/     place search (Nominatim)
  config/     security (tokens, roles), first admin account
  common/     JSON error responses
  clipboard/  the private daily clipboard (unchanged, hidden from menus)
src/main/resources/
  db/migration/   database tables (Flyway)
  static/         website: login, signup, account, provider, search, admin pages + css/js
docs/design-system.md   colors, fonts, components for web, iOS and ESP32
```

## The clipboard

Still at `/clipboard.html` for anyone who knows the link (not linked from the site, and marked so search engines don't list it). Its API is unchanged (`/api/clipboard`, `X-API-Key` header) but only works once `PARKFLOW_API_KEY` is set. The iOS client is in `ios/ClipboardAPI.swift`.
