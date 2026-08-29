# Film Home Remote Content Contract (ads + news)

> Scope: `new/src/client` film home (`mchorse.bbs_mod.ui.film.home`) plus the
> `film-home-publisher/` toolchain (Cloudflare Worker + local push WebUI).

## Delivery chain

```
WebUI (127.0.0.1, editor) --PUT/Bearer--> CF Worker (KV) <--GET-- client background fetch
```

- Worker `bbs-film-home` (repo: `film-home-publisher/worker/`): public
  `GET /api/ads`, `GET /api/news`, `GET /img/<key>`; writes require
  `Authorization: Bearer <ADMIN_TOKEN>`. Content JSON lives in KV keys
  `content:ads` / `content:news`; images in `img:<sha256>.<ext>` with the
  content-type in KV metadata.
- Client contract is identical to the local `film_home/*.json` schema
  (`{"items":[...]}`); `image` fields may be absolute http(s) URLs, which
  `WebImages.resolve` streams with disk cache + spinner.

## Client fallback order (must not regress)

1. `FilmHomeContent.load()` seeds lists synchronously from local JSON
   (config override → jar bundle) so the home paints instantly. It never
   overwrites lists whose `Source` is `REMOTE`/`CACHED` — panel reopenings
   must not roll remote content back to local.
2. `refreshAsync` runs on a daemon pool, tries the `remote.json` URL chain in
   order (10s timeouts, 2MB cap), throttled by `refresh_minutes` (first call
   of a session always fetches). Success swaps the list reference, writes
   `cache/film_home_cache/<kind>.json`, and re-applies the UI on the main
   thread; the UI skips refill when the list reference is unchanged.
3. Total fetch failure: keep showing whatever is on screen; if the source is
   `LOCAL` and a cache copy exists, restore it (`Source.CACHED`). No error UI.
4. While fetching with empty lists, `UINewsStrip`/`UIAdBoard` render the
   `WebImages.drawSpinner` loading state instead of hiding — never block the
   render thread with IO.

## Detail overlays

- News/ads detail panels size to `min(ideal px, 90% of GUI window)`; the
  overlay `bounds()` clamps on tiny windows.
- `MarkdownBody` must scroll when content overflows: wheel, right-edge
  scrollbar (click/drag), and left-drag swipe. Drag-selection is only active
  while the document fits; hitTest y-origin is `area.y + 6 + scrollY`.

## Toolchain rules

- Credentials (CF token, admin token) live only in
  `film-home-publisher/webui/config.local.json` (gitignored). The WebUI binds
  `127.0.0.1` and is the only component holding the admin token.
- `remote.json` URLs accept a string or an array (fallback chain). Default
  jar copy points at the attached custom domain `fsrapi.twiap.studio` only —
  the workers.dev route is disabled on deploy (unreachable in CN, and not
  kept by product decision). `deploy.mjs` attaches domains via
  `PUT /accounts/{id}/workers/domains` and disables workers.dev itself.
- Ad images are 4:3 by design; the WebUI enforces the ratio (auto center-crop
  offered). Client slide height derives from width at 4:3.

## Responsive layout tiers

`UIFilmHomePanel.relayout()` is the single layout control point; it runs on
window resizes AND on content changes (sections appearing), so it must end by
calling `resize()` on the repositioned children — flex changes only apply on
resize cascades, never in-place (see the `.resize()` idiom in UIFilmPanel).

- Two axes: `widthTier` T0–T3 (W≥1000 / ≥700 / ≥480 / else) drives section
  collapsing; `heightStep` (H<700 → banner 96, H<480 → banner 64 + news
  collapsed to a toolbar entry) only shrinks the banner on wide-short windows.
- Every boundary carries a ±8px hysteresis band (strict-less: the no-switch
  band is `[T-8, T+7]`; shrinking switches at T-9, growing at T+8). State
  machine verified standalone — don't "simplify" it to plain threshold tests.
- Tier switches must not reset state: no `fill()` calls, carousel page,
  grid selection, search filter survive. T3 forces the list view and
  remembers `userGridMode`; leaving T3 restores it.
- T2/T3 collapse the ad board into a toolbar entry (`UIAdBoard` is an
  embeddable area-rendered element — reuse a fresh instance inside a
  `UIOverlayPanel`); the news strip does the same when the height collapses.
- Banner renders height-priority contain (logo must stay whole on any
  width — the old width-cover crop sliced through it); empty sides extend
  the texture's outer pixel columns and fade into `deepSurface`. Bottom
  readability gradient + accent hairline stay.
- Entry tooltips reuse existing lang keys (`…home.ads`, `…home.news`) — no
  new strings per tier.

## Verification checklist

- `node worker/worker.test.mjs` (19 checks) and `node worker/deploy.mjs` for
  deploys; `gradlew compileClientJava` + `testThemeCore` for client changes.
