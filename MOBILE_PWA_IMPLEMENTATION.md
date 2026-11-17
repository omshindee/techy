# Techy: mobile-first PWA

Techy's existing React (Vite) frontend now works comfortably on phones and installs to the home screen as a Progressive Web App. The React + Spring Boot architecture is unchanged and **no backend code changed**. Nothing was deployed or pushed.

## 1. Audit (before changes)

| Item | Finding |
|---|---|
| Build system | Vite 5.4 (`@vitejs/plugin-react`), ES modules |
| Framework | React 18.3, React Router 6.30 (`BrowserRouter`) |
| Runtime dependencies | `react`, `react-dom`, `react-router-dom` only |
| Auth | JWT from `/api/auth/login`, stored in `localStorage` (`devsocial.token`), sent as `Authorization: Bearer`; 401 signs out |
| Styling | One stylesheet (`src/index.css`), CSS variables, breakpoints at 1180 / 1024 / 720 px; phone layout already had a top bar and bottom tab bar |
| Manifest / service worker | **None**; `public/` was empty |
| Horizontal overflow | **None** at 320–430 px, even with 50-character names and unbroken URLs |
| Tap targets | Too small for fingers: channel tags 26 px, like/comment pills 31 px, icon buttons 36 px, small buttons 33 px, comment menu 20 px |
| Mobile keyboards | Every text field under 16 px (iOS zooms the page on focus); username fields auto-capitalised and autocorrected |
| Safe areas | Bottom sheets and top bar ignored notch / home-indicator insets |
| Landing page | The sign-in page acts as the landing page; on phones it showed only the logo, no explanation |
| Not present | Notifications and a separate post-detail route don't exist in the app, so there was nothing to adapt (posts open in a sheet/modal) |

## 2. Mobile-first changes

Desktop with a mouse keeps the exact previous design. Changes apply to touch devices (`pointer: coarse`) and small screens.

- **Tap targets:** at least 44 px for buttons and icon buttons, 40 px for pills, tabs and small buttons, 36 px tags with more spacing, 52 px action-sheet rows, larger comment menu. Pressed state (slight scale) since touch has no hover. `touch-action: manipulation` removes the double-tap-zoom delay.
- **No iOS zoom on focus:** all text inputs are 16 px on phones and touch devices.
- **Keyboards:** usernames get `autocapitalize="none"`, `autocorrect="off"` and `spellcheck="false"`; search fields are `type="search"` with a Search key; the comment field shows a Send key. Email fields were already `type="email"`.
- **Keyboard vs fixed bar:** the bottom tab bar hides while a field is focused, so it can't cover what you're typing.
- **Safe areas:** with `viewport-fit=cover`, the top bar, tab bar, page sides and bottom sheets respect `env(safe-area-inset-*)` (notches, rounded corners, home indicator).
- **Small phones (≤ 360 px):** tighter padding and smaller headings.
- **Images:** feed photos capped at 70 % of the screen height on phones.
- **Landing / sign-in:** phones now show the tagline under the logo.
- **Errors:** network failures say "Couldn't reach Techy. Check your connection…" (or "You're offline…"); 502/503/504 say "temporarily unavailable" instead of a raw status code.

## 3. PWA

Tooling: **vite-plugin-pwa 0.21.2** (Workbox `generateSW`) and **workbox-window 7.3.0**, both pinned dev dependencies that are bundled at build time.

**Manifest** (`/manifest.webmanifest`, generated from `vite.config.js`)

| Field | Value |
|---|---|
| `name` / `short_name` | Techy / Techy |
| `id`, `start_url`, `scope` | `/` |
| `display` | `standalone` |
| `theme_color` | `#ffffff` (the white app bars) |
| `background_color` | `#f4f3ef` (the page background, used for the splash screen) |
| `icons` | 192 and 512 PNG (`any`), 192 and 512 PNG (`maskable`) |
| `shortcuts` | New post (`/new`), People (`/people`) |

**Icons** (`public/icons/`) come from `scripts/generate-icons.mjs` (`npm run icons`), a dependency-free generator for the `>_` mark. It makes rounded "any" icons, full-bleed maskable icons with the mark inside the 80 % safe zone, an opaque 180×180 `apple-touch-icon.png`, a 32 px favicon and `icon.svg`.

**Service worker: what is cached**
- *Precached:* the app shell only, meaning `index.html`, hashed JS/CSS, icons and the manifest (19 entries, about 284 KiB).
- *Runtime:* only Google Fonts' public CSS (stale-while-revalidate) and font files (cache-first, 1 year).
- *Never cached by the service worker:* anything under `/api/`. That covers feeds, profiles, comments, login responses, tokens, and the signed private images (`/api/media/...`). The API also sends `Cache-Control: no-store`. The browser's own private HTTP cache may keep signed images until their link expires (12–24 h), which the server allows with `Cache-Control: private`.
- Deep links (e.g. `/profile/priya`) open the cached app shell; `/api/*` and `/actuator/*` are excluded from that fallback.

**Offline:** the installed app still opens, but a banner says "You're offline. Techy needs a connection…" and each page shows a clear error. It never displays stale private data or pretends to post.

**Updates** (`src/components/PwaUpdater.jsx`): `registerType: 'prompt'`. A new deployment is detected on launch, hourly, and whenever the app returns to the foreground. A toast offers **Update** (activates the new version and reloads) or **Later**. Old precaches are deleted automatically. nginx serves `sw.js` with `no-store`, and `index.html` and the manifest with `no-cache`, so browsers always find new versions. Settings shows the build stamp.

**nginx** (`frontend/nginx.conf`)
- Correct `application/manifest+json` type (the stock nginx image lacks it).
- `Service-Worker-Allowed: /`.
- Hashed `/assets/*` cached for a year as `immutable`; icons for a week.

## 4. Installation

- **Android / Chrome / Edge / Samsung Internet:** `beforeinstallprompt` is captured at startup (`src/utils/install.js`). Phones get a small "Install Techy" banner on Home, and Settings has an **Install Techy** button. Accepting hides both.
- **iPhone / iPad (Safari):** no install API exists, so the banner offers **How?** and Settings shows three illustrated steps (Share → Add to Home Screen → Add). Apple meta tags are set: `apple-mobile-web-app-capable`, `apple-mobile-web-app-title=Techy`, `apple-mobile-web-app-status-bar-style=default` (no content under the status bar), and `apple-touch-icon`.
- **Not nagging:**
  - Nothing shows when installation isn't possible, inside the installed app (`display-mode: standalone` or `navigator.standalone`), or on desktop (desktop users install from Settings).
  - "Not now" hides the banner for 30 days.
- **Standalone mode:** app bars aren't text-selectable, and overscroll bounce is disabled.

## 5. Files

**New**
- `frontend/scripts/generate-icons.mjs`
- `frontend/public/icons/` (7 files)
- `frontend/src/components/PwaUpdater.jsx`, `InstallPrompt.jsx`, `OfflineBanner.jsx`
- `frontend/src/utils/install.js`
- this file

**Modified**
- `frontend/vite.config.js`: PWA plugin, manifest, Workbox rules, preview proxy, build stamp
- `frontend/index.html`: icons, theme colour, Apple meta tags
- `frontend/package.json`, `package-lock.json`: two dev dependencies, `icons` script
- `frontend/nginx.conf`: cache and content-type headers for the PWA files
- `frontend/src/index.css`: touch, keyboard, safe-area, PWA UI
- `frontend/src/main.jsx`: early install-prompt capture
- `frontend/src/App.jsx`: offline banner, update toast
- `frontend/src/services/api.js`: friendly network errors
- `frontend/src/pages/Login.jsx`, `Register.jsx`, `Home.jsx`, `People.jsx`, `Settings.jsx`
- `frontend/src/components/AuthLayout.jsx`, `CommentSection.jsx`

**Unchanged:** all backend code and the auth/token handling. Tokens stay in `localStorage` exactly as before; the service worker never sees or stores them.

## 6. Browser compatibility

| Platform | Install | Notes |
|---|---|---|
| Android Chrome / Edge / Samsung Internet | In-app **Install** button (`beforeinstallprompt`) and browser menu | Maskable icon, splash from manifest, app shortcuts |
| Android Firefox | Browser menu → Install | No install prompt API, so Settings shows menu instructions |
| iOS / iPadOS Safari 16.4+ | Share → Add to Home Screen (in-app instructions) | Standalone mode, `apple-touch-icon`; Apple doesn't support manifest shortcuts or install prompts |
| iOS Chrome / Firefox / Edge | Share → Add to Home Screen (iOS 16.4+) | Same WebKit engine as Safari |
| Desktop Chrome / Edge | Install button in Settings or the address bar | Opens in its own window |
| Desktop Firefox / Safari | Safari 17+: File → Add to Dock; Firefox: no install | Site works normally |

The layout uses `:has()` to hide the tab bar while typing (Safari 15.4+, Chrome 105+, Firefox 121+). Older browsers simply keep the tab bar visible.

## 7. Test results

Tested against the **production build** (`vite build` + `vite preview`) and the **Docker image** (nginx), with a throwaway in-memory backend so no test accounts touched the real database.

| # | Check | Result |
|---|---|---|
| 1 | Desktop (1280 px) still works and looks the same | ✅ Same layout (252 px sidebar, feed, 300 px right column), same compact sizes (pills 32 px, tags 27 px), no overflow, no install banner |
| 2 | 320 / 375 / 390 / 430 px, on Home, People, two profiles (incl. a 50-character name), New post, Settings | ✅ 0 px overflow everywhere; 0 inputs under 16 px; 0 tap targets under 36 px except the search text field, which sits inside a 48 px tappable box. Tablet (768 px): icon sidebar, no overflow |
| 3 | Registration and login | ✅ (tested in the previous round with email confirmation; login re-checked here) |
| 4 | Auth persists | ✅ Stays signed in across reloads, across an app update, and while the server is unreachable |
| 5 | Feed loads | ✅ |
| 6 | Profiles | ✅ |
| 7 | People search | ✅ "fitz" found "Bartholomew Alexander Fitzgerald-Montgomery III" |
| 8 | Posts, likes, comments | ✅ like 0 → 1, comment added, post published |
| 9 | Image upload | ✅ a 2400×1600 photo was resized in the browser, uploaded, and served via a signed link (200 image/jpeg) |
| 10 | Manifest valid | ✅ `application/manifest+json`; all required fields; icon sizes confirmed from PNG headers (192, 512, maskable 192 and 512, Apple 180) |
| 11 | Service worker registers | ✅ active and controlling, scope `/`; caches contain only static files and fonts (**0 `/api` entries** after using the app) |
| 12 | Installation | ✅ Logic verified by simulating `beforeinstallprompt` (banner appears → Install calls `prompt()` → banner hides) and an iPhone user agent (instructions shown, "Not now" remembered). ⚠️ A real install on a physical Android/iPhone wasn't possible from this environment; do it once after deploying |
| 13 | Updates don't strand users | ✅ With the old build running, a new build was detected → "A new version of Techy is available" → **Update** → reloaded onto the new bundle; old precache removed, still signed in |
| – | Offline / server down | ✅ The app shell opens from cache, API calls fail cleanly ("Couldn't reach Techy…"), the offline banner appears, and no cached private data is shown |
| – | Docker / nginx headers | ✅ `sw.js` no-store + `Service-Worker-Allowed`, manifest type, immutable `/assets/*`, deep links → `index.html`, API stays `no-store` |
| – | Frontend production build | ✅ `vite build` succeeds. Backend code was not changed (its 30 tests passed in the previous round) |

**Not verified here:** real-device installs (Android and iOS), iOS standalone status-bar appearance, and Lighthouse PWA scoring. All three need a public HTTPS URL or a physical device. Service workers only run on HTTPS (or `localhost`), so **the deployed site must use HTTPS**.

## 8. Notes for deployment

- Serve over **HTTPS**; it's required for service workers and installation.
- If `APP_BASE_URL`/the domain changes, nothing in the manifest needs editing (it uses relative `/` paths).
- To change the icon, edit `scripts/generate-icons.mjs` and run `npm run icons`.
- `npm install` prints a deprecation warning for `glob@11`. It comes from Workbox's build tooling, runs only at build time and never ships to users.
