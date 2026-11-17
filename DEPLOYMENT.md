# Deploying Techy with Cloudflare Pages

## How the pieces fit

Cloudflare Pages hosts static sites and small edge functions. It can't run the Spring Boot backend or PostgreSQL, so Techy is deployed as four parts:

```
                    ┌──────────────────── Cloudflare ────────────────────┐
  Phone / browser ──▶  Pages: React app (static, PWA)                    │
                    │  Pages Function /api/*  ── adds X-Proxy-Secret ──┐  │
                    └──────────────────────────────────────────────────┼──┘
                                                                       ▼
                         Backend container (Spring Boot Docker image) on Render / Fly.io / Railway / a VPS
                              │                         │
                              ▼                         ▼
                      PostgreSQL (Neon, Render…)   Cloudflare R2 bucket (photos, private)
```

- **The browser only talks to your Pages domain.** `/api/*` is forwarded by `frontend/functions/api/[[path]].js`, so there's no cross-origin setup, the service worker rules keep working, and the backend gets each visitor's real IP for rate limiting.
- **The backend only answers requests that carry `PROXY_SECRET`.** Anyone who finds its own URL gets `403 Direct API access is not allowed`.
- **Photos go to R2** (`STORAGE_TYPE=s3`). Most container hosts wipe their disk on every deploy, so local storage would lose photos.

> Provider details below (free tiers, limits) reflect what was true when this was written. Check current pricing before you rely on it.

---

## 0. Before you start

You need accounts on **GitHub**, **Cloudflare**, a **backend host** (this guide uses **Render**), and a **Postgres provider** (this guide uses **Neon**: free, doesn't expire).

Generate two secrets and keep them somewhere safe, such as a password manager:

```bash
openssl rand -base64 48   # → JWT_SECRET
```
```bash
openssl rand -hex 32      # → PROXY_SECRET
```

## 1. Put the code on GitHub

From the project folder (the one containing `backend/` and `frontend/`):

```bash
git init
```
```bash
git add .
```
```bash
git commit -m "Techy: initial import"
```

Create an **empty private repository** on GitHub, then:

```bash
git remote add origin https://github.com/<you>/<repo>.git
```
```bash
git branch -M main
```
```bash
git push -u origin main
```

`.gitignore` already keeps `.env` (your secrets), `node_modules`, build output, uploads and IDE files out of the repo. Check with `git status` before the first commit that `.env` isn't listed.

The existing GitHub Actions workflow (`.github/workflows/ci.yml`) runs the backend tests and the frontend build on every push.

## 2. Create the R2 bucket (photos)

1. Cloudflare dashboard → **R2 Object Storage** → **Create bucket** → name it e.g. `techy-media`. Leave **public access off**; Techy serves photos itself, through signed links.
2. **R2 → Manage API tokens → Create API token**: permission **Object Read & Write**, limited to that bucket.
3. Note the **Access Key ID**, **Secret Access Key**, and the endpoint `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`.

## 3. Create the database

1. On **Neon**: create a project and a database (e.g. `techy`).
2. From the connection details, note the host, database name, user and password.
3. Convert them to a JDBC URL:
   ```
   DB_URL=jdbc:postgresql://<host>/<database>?sslmode=require
   DB_USERNAME=<user>
   DB_PASSWORD=<password>
   ```

Tables are created automatically on first start (`ddl-auto=update`).

## 4. Deploy the backend (Render example)

1. Render → **New → Web Service** → connect the same GitHub repo.
2. **Language/Runtime: Docker**, **Root Directory: `backend`** (it contains the `Dockerfile`), Branch: `main`.
3. **Health Check Path:** `/actuator/health`.
4. **Environment variables:**

| Variable | Value |
|---|---|
| `JWT_SECRET` | the base64 secret from step 0 (**required**; the app won't start without it) |
| `PROXY_SECRET` | the hex secret from step 0 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | from step 3 |
| `DB_POOL_SIZE` | `5` |
| `STORAGE_TYPE` | `s3` |
| `S3_ENDPOINT` | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` |
| `S3_REGION` | `auto` |
| `S3_BUCKET` | `techy-media` |
| `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY` | from step 2 |
| `APP_BASE_URL` | `https://<your-project>.pages.dev` (update after step 5 if different) |
| `REQUIRE_EMAIL_VERIFICATION` | `true` (needs SMTP, step 6) or `false` until email is set up |
| `ADMIN_EMAILS` | your email, to become a moderator after confirming it |

Don't set `PORT`: Render provides it and the app uses it automatically.

5. Deploy, then open `https://<service>.onrender.com/actuator/health`. It should say `{"status":"UP"}`.
   - The log should show `Uploaded images are stored in S3-compatible bucket techy-media`.
   - `https://<service>.onrender.com/api/auth/config` should answer **403**. That's correct: only Pages may call the API.

**Render free plan note:** free instances sleep after ~15 minutes without traffic, and the first request then takes up to a minute (the app shows "Techy is temporarily unavailable" meanwhile). A paid instance stays awake. Spring Boot fits in 512 MB; the image sizes its memory from the container limit.

**Other hosts:** Fly.io, Railway, Google Cloud Run, Koyeb or a VPS all work the same way: build `backend/Dockerfile` and set the variables above. On a VPS with a persistent disk you could use `STORAGE_TYPE=local`.

## 5. Deploy the frontend on Cloudflare Pages

1. Cloudflare dashboard → **Workers & Pages → Create → Pages → Connect to Git** → choose the repo.
2. Build settings:

| Setting | Value |
|---|---|
| Framework preset | `React (Vite)` or `None` |
| Root directory (advanced) | `frontend` |
| Build command | `npm run build` |
| Build output directory | `dist` |

Node 20 is picked up from `frontend/.nvmrc`. If your build image ignores it, add the variable `NODE_VERSION=20`.

3. **Settings → Variables and Secrets**, for both **Production** and **Preview**:

| Name | Type | Value |
|---|---|---|
| `BACKEND_URL` | Text | `https://<service>.onrender.com` (no trailing slash) |
| `PROXY_SECRET` | **Secret** | same value as the backend's `PROXY_SECRET` |

4. **Save and deploy**, then redeploy once so the Function sees the new variables.
5. Open `https://<your-project>.pages.dev`: sign up, post a photo, like and comment.

What Pages uses from the repo:
- `frontend/functions/api/[[path]].js`: the `/api` proxy (Pages Functions).
- `frontend/public/_headers`: security headers (Content-Security-Policy, X-Frame-Options…), plus cache rules so the PWA always picks up new versions.
- **SPA routing:** there's no `404.html`, so Pages serves `index.html` for app URLs like `/profile/priya`. No `_redirects` needed.
- **Preview deployments** (`<hash>.<project>.pages.dev`) work too; they use the same backend unless you give them a different `BACKEND_URL`.

**Pages Functions quota:** each API call (including each photo) is one Function request. The free plan includes 100,000 per day, plenty for a starting community. The Workers Paid plan raises it a lot.

## 6. Email and captcha (production)

- **Email:** set `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` and `MAIL_FROM` on the backend. Any provider works (Resend, Brevo, Postmark, Mailgun, SES). Send from your own domain and set up SPF/DKIM as the provider describes, or confirmation emails may land in spam. Until SMTP is set, emails are only printed in the backend log.
- **Captcha:** Cloudflare → **Turnstile → Add widget**. Hostnames: your `pages.dev` domain and your custom domain. Put the keys on the backend as `TURNSTILE_SITE_KEY` and `TURNSTILE_SECRET_KEY`. The site's Content-Security-Policy already allows Turnstile.

## 7. Custom domain (optional)

1. Pages → your project → **Custom domains → Set up a domain** (e.g. `techy.example.com`). With DNS on Cloudflare this is one click; HTTPS is automatic.
2. Update the backend's `APP_BASE_URL` to `https://techy.example.com`, so email links point there.
3. Add the domain to your Turnstile widget's hostnames.

## 8. After the first deploy: checklist

- [ ] `https://<site>/` loads, and `/profile/anything` loads the app (not a 404)
- [ ] Sign up → confirmation email arrives → link confirms the account
- [ ] Post with a photo; the photo shows. **Redeploy the backend**, and the photo still shows (R2 works)
- [ ] Like, comment, search people, follow
- [ ] "Forgot password?" email arrives and resets the password
- [ ] Your account shows **Moderation** in the sidebar (if listed in `ADMIN_EMAILS`)
- [ ] `https://<backend>/api/auth/config` answers 403 directly; the site still works
- [ ] On Android Chrome: **Install Techy** appears. On iPhone Safari: Share → Add to Home Screen. The installed app opens full screen
- [ ] After the next deploy, an open app shows "A new version of Techy is available" → Update works

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Every API call fails with `The API is not configured (BACKEND_URL is missing)` (503) | Set `BACKEND_URL` in Pages (Production *and* Preview), then redeploy |
| `Direct API access is not allowed` (403) in the app | `PROXY_SECRET` differs between Pages and the backend, or isn't set in Pages |
| "Techy is temporarily unavailable" | Backend asleep (free plan), crashed, or still starting; check the host's logs and `/actuator/health` |
| Backend won't start: `Could not resolve placeholder 'JWT_SECRET'` | Set `JWT_SECRET` (32+ characters) |
| Backend won't start: `STORAGE_TYPE=s3 needs S3_ENDPOINT…` | Fill in all `S3_*` variables |
| Database connection errors | Check `DB_URL` starts with `jdbc:postgresql://` and has `?sslmode=require` |
| Photos disappear after a redeploy | `STORAGE_TYPE` is `local` on a host without a persistent disk; switch to R2 |
| Confirmation emails never arrive | SMTP not set (look for "was NOT sent" in the backend log), or check spam/SPF/DKIM |
| Old version keeps showing | Normal for a few seconds; the app offers **Update**. `_headers` makes `sw.js` uncacheable, so it always finds new versions |

## Alternative: calling the backend directly (no proxy)

Possible, but not recommended. Build the frontend with `VITE_API_URL=https://<backend>/api` and leave `PROXY_SECRET` empty. Then you must also:
- set `CORS_ALLOWED_ORIGINS` (wildcards like `https://*.techy.pages.dev` are allowed);
- add the backend origin to `connect-src` and `img-src` in `frontend/public/_headers`;
- accept that rate limits may see the host's proxy IP instead of the visitor's.

## Local development stays the same

`docker compose up` still runs everything locally (nginx + backend + Postgres, photos on a local volume). Leave `PROXY_SECRET` empty there.

## What was verified before shipping

- **Backend tests: 34 passing.** They include the proxy-secret checks (direct calls blocked, health open, per-visitor rate limits through the proxy, CORS wildcards) and an S3 storage test run against an S3-compatible server (S3Mock).
- **The production backend image refuses to start without `JWT_SECRET`.** With cloud-style settings it:
  - listens on `PORT`,
  - runs as a non-root user,
  - stores photos in the S3 bucket,
  - answers health checks,
  - returns 403 to direct API calls.
- **Cloudflare's local Pages emulator** (`wrangler pages dev`) serving the built site:
  - every `_headers` rule applied (CSP, `sw.js` no-store, manifest type, immutable assets, SPA fallback);
  - sign-up, photo post (stored in the bucket, displayed via a signed link through the proxy), like, comment and search all worked through the `/api` Function;
  - no Content-Security-Policy violations.
- **Not verified locally:** service-worker registration under the local Pages emulator. Inside the test browser it failed even for a one-line test worker, so it's an emulator/browser quirk; the same build registers fine under `vite preview` and nginx. Check "installable" once on the real `pages.dev` URL (checklist above).
