# Techy — a social network for developers (v0.1)

A small, real social network for developers: share screenshots and photos of what you're building, or write about what you're learning, follow other developers, like and comment, and get a feed ranked by who you follow and what you care about. Built as an MVP for ~10 initial users, with a layered architecture so features can be added without a rewrite.

**Stack:** React (Vite) · Java 17 · Spring Boot 3 (Security, Data JPA/Hibernate) · JWT · PostgreSQL · Maven · Docker

## Deploy

**Production:** the React app on **Cloudflare Pages** (connected to GitHub, `/api` forwarded by a Pages Function), the Spring Boot Docker image on a container host (e.g. Render), Postgres (e.g. Neon) and photos in **Cloudflare R2**. Step-by-step guide: **[DEPLOYMENT.md](DEPLOYMENT.md)**.

## Run it

### Option A: Docker (everything)
```bash
cp .env.example .env        # set JWT_SECRET (32+ chars) and DB_PASSWORD
docker compose up --build
```
App: http://localhost:3000 (nginx serves the UI and proxies `/api` to the backend). API: http://localhost:8080.

### Option B: local development
```bash
# 1. database (compose needs JWT_SECRET set in .env even for just the db)
cp .env.example .env         # then fill in JWT_SECRET
docker compose up -d db

# 2. backend (needs JDK 17 + Maven)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. frontend
cd frontend
npm install
npm run dev                  # http://localhost:5173, /api proxied to :8080
```

### Tests
```bash
cd backend && mvn verify     # integration, service and repository tests (H2 in-memory, no Postgres needed)
```

## What's in v0.1

| Area | Details |
|---|---|
| Auth | Register, login (username or email), BCrypt passwords, stateless JWT, email confirmation, password reset by email |
| Profiles | @username plus an optional display name, uploaded profile photo, bio, skills, GitHub link, interests |
| Search | Server-side people search by name, @username or skill, paginated |
| Social graph | Follow/unfollow, followers list, following list |
| Content | Photo posts (with optional caption) and text posts, each with a channel; delete own posts and comments |
| Engagement | Likes, threaded comments, counts |
| Feed | "For you" ranked feed, "Latest", and one `#channel` per category |
| Safety | Block, report (posts, comments, accounts), moderator queue, suspensions, rate limits, captcha, private image links |

**Feed ranking** (`FeedService.score`): followed author +50, category matches your interests +25, +2 per like, +3 per comment, plus a recency bonus (40, decaying over about a day). It scores the 200 most recent posts and returns the top 50. No ML, by design.

## API

All routes except `/api/auth/*` and `GET /api/media/*` need `Authorization: Bearer <token>`. `/api/admin/*` needs a moderator.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/auth/config` | `{ captchaSiteKey, emailVerificationRequired }` for the sign-up page |
| POST | `/api/auth/register` · `/api/auth/login` | Returns `{ token, user }`. Register takes `displayName`, `captchaToken` |
| POST | `/api/auth/verify-email` · `/forgot-password` · `/reset-password` | Email confirmation and password reset (single-use emailed links) |
| GET | `/api/users?q=&page=` | People search by name, @username or skill: `{ items, hasMore }`, 20 per page |
| GET | `/api/users/suggestions?limit=` | People you don't follow yet |
| GET / PUT | `/api/users/me` | Current user / update profile (`displayName`, `bio`, `githubUrl`, `skills`, `interests`) |
| PUT / DELETE | `/api/users/me/avatar` | Upload (multipart field `image`) / remove profile photo |
| POST | `/api/users/me/verification` | Resend the confirmation email |
| GET | `/api/users/me/blocked` | Accounts you blocked |
| GET | `/api/users/{username}` | Profile |
| POST / DELETE | `/api/users/{username}/follow` · `/block` | Follow / unfollow, block / unblock |
| GET | `/api/users/{username}/followers` · `/following` | Lists |
| POST | `/api/posts` | JSON `{ content, categories: [1-3] }`, or multipart `content`, `categories` (repeated), `image` |
| GET | `/api/posts?category=&username=` | Recent posts, filtered |
| GET / DELETE | `/api/posts/{id}` | One post / delete (own, or any as moderator) |
| POST / DELETE | `/api/posts/{id}/like` | Like / unlike |
| GET / POST | `/api/posts/{id}/comments` | List / add |
| DELETE | `/api/posts/{id}/comments/{commentId}` | By the comment's author, the post's author, or a moderator |
| GET | `/api/feed` | Personalized feed |
| POST | `/api/reports` | `{ targetType: POST\|COMMENT\|USER, targetId, reason, details }` |
| GET | `/api/admin/reports?status=open\|closed` · `/api/admin/reports/count` | Moderation queue |
| POST | `/api/admin/reports/{id}/resolve` | `{ action: DISMISS \| REMOVE \| SUSPEND }` |
| POST / DELETE | `/api/admin/users/{username}/suspend` | Suspend / lift |
| GET | `/api/media/{key}?exp=&sig=` | Uploaded image; only signed, unexpired links work |

Categories: C++, Java, DSA, Linux, Operating Systems, Web Development, AI/ML, DevOps, Tech, Memes (`Categories.java` and `frontend/src/utils/categories.js`).

## Mobile app (PWA)

Techy is mobile-first and installable: Android/desktop Chrome and Edge get an **Install Techy** button (Settings, plus a small banner on phones), and iPhone users get Share → Add to Home Screen instructions. The service worker caches only the static app shell, never `/api` data, and offers new versions with an **Update** prompt. Installing requires HTTPS in production. Details, browser support and test results: [MOBILE_PWA_IMPLEMENTATION.md](MOBILE_PWA_IMPLEMENTATION.md). App icons are generated by `npm run icons` (in `frontend/`).

## Uploads

Photos and profile pictures are stored on disk in `STORAGE_DIR` (default `backend/uploads/`; in Docker, the `uploads` volume at `/app/uploads`). Back that up along with the database.

- Allowed: JPEG, PNG, GIF, WebP, up to 8 MB. The type is checked from the file's bytes, not its name.
- The browser shrinks photos before upload (longest side 1440 px; profile photos are cropped square to 320 px), so phone photos upload quickly.
- **Image links are signed and expire.** The API only hands out links like `/api/media/{key}?exp=…&sig=…` to signed-in users. Each link works for 12–24 hours; unsigned, tampered or expired links get 403. A copied link stops working, and file names alone are useless. (`<img>` tags can't send a login token, which is why a signature is used instead.)
- Images are sent with `Cache-Control: private`, so CDNs and shared proxies don't keep copies.
- Deleting a post or replacing a profile photo deletes the old file.

## Accounts, spam and safety

| Protection | How it works | Setup |
|---|---|---|
| Email confirmation | New accounts get a link (valid 48 h). Until they confirm, they can browse and edit their profile but not post, comment, like, follow or report. | Needs SMTP (below). `REQUIRE_EMAIL_VERIFICATION=false` turns it off. |
| Password reset | "Forgot password?" emails a single-use link (valid 1 h). Resetting signs out every other session. The form answers the same way whether or not the email exists. | Needs SMTP. |
| Rate limits | Sign-up 5/hour per IP; login 20 per 5 min per IP and 10 per 15 min per account; reset 5/hour per IP and 3/hour per email; posts 20/hour, comments 60/hour, reports 20/day per user. | On by default. In-memory, so one backend instance. |
| Honeypot | A hidden sign-up field that only bots fill in. | Always on. |
| Captcha | Cloudflare Turnstile on sign-up and password reset. | Free: create a widget at dash.cloudflare.com → Turnstile, set `TURNSTILE_SITE_KEY` and `TURNSTILE_SECRET_KEY`. |
| Blocking | Blocked users and the blocker don't see each other's posts, comments or profiles, can't follow or interact, and existing follows are removed. Nobody is notified. | Always on. Manage in Settings. |
| Reporting | Any post, comment or account can be reported with a reason. | Always on. |
| Moderation | Moderators see a queue (sidebar → Moderation) and can remove content, suspend accounts or dismiss. Suspended users are signed out, can't sign in, and their profile and posts are hidden. | Put your email in `ADMIN_EMAILS`, then confirm it. |

**Email setup.** Either set `BREVO_API_KEY` (Brevo's HTTPS API, for hosts that block SMTP such as Render's free plan) or use any SMTP provider: Resend, Brevo, Postmark, Mailgun, Amazon SES, or a Gmail app password for testing. Set `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` and `APP_BASE_URL` (your public URL, used in the links). For good deliverability, send from your own domain with SPF/DKIM set up as the provider explains.

**Without SMTP** (local development), emails aren't sent; they're printed in the backend log instead. Copy the link from:
```bash
docker compose logs backend | grep -A6 "was NOT sent"
```

## Architecture

```
React (Vite) ──REST──▶ Controller ▶ Service ▶ Repository ▶ PostgreSQL
                         (Spring Boot, JWT filter in front of everything)
```
Packages under `com.devsocial`: `config`, `security`, `controller`, `service`, `repository`, `model`, `dto`, `exception`.

## Before you invite your 10 users

- Set a real `JWT_SECRET` (compose won't start without one) and `DB_PASSWORD`. The `DB_PASSWORD` default is for local use only.
- Put the app behind HTTPS (a reverse proxy such as Caddy, or your host's TLS). Tokens are sent in a header, so don't serve it over plain HTTP.
- `ddl-auto=update` creates and alters tables for you. Fine for the MVP; add Flyway before the schema starts changing under real data.
- Configure SMTP (or new users can't confirm their email), set `ADMIN_EMAILS` to yourself, and turn on the Turnstile captcha.
- Only expose nginx (port 3000/80/443). Compose binds the backend's 8080 to localhost so clients can't fake their IP to dodge rate limits. If you put another proxy (Caddy, a load balancer) in front of nginx, configure nginx's `real_ip` module so the real client IP reaches the backend.
- Registration is open. If you want invite-only for the first 10, that's a small change in `AuthService.register`.
- Tokens last 24 hours and live in `localStorage`. A password reset or a suspension cancels them early. Revisit (httpOnly cookies, refresh tokens) when it grows.

## Success criteria checklist (from the spec)

- [x] Deployable: `docker compose up` runs db, API and UI
- [x] Secure registration and authentication (BCrypt + JWT)
- [x] Many users can register (no cap; the "10 real developers" part is on you)
- [x] Follow and unfollow
- [x] Create, view and delete posts
- [x] Like and comment
- [x] Personalized feed
- [x] Documented on GitHub (this README, CI workflow)
- [x] Layered architecture with a clear place for v0.2 features

## Roadmap

v0.2: short videos, multi-photo posts, notifications, bookmarks, search, hashtags, challenges. v0.3: AI content classification, relevance scoring, spam detection, recommendations.
