# StauEnde.net — Website

A single self-contained landing page (`index.html`, no build step, no external
requests) introducing the StauEnde Android app: the problem it solves, its
core features, and a preview of the app UI.

## Local preview

```
open website/index.html
# or
python3 -m http.server --directory website 8080
```

## Deploying

`.github/workflows/deploy-website.yml` builds and publishes the site
automatically on every push to `main`:

1. `test-core` runs the `:core` unit tests (also runs on PRs, as a gate).
2. `build-app` assembles a debug APK (`:app:assembleDebug`) — no signing
   secrets required, since debug builds are auto-signed by Gradle.
3. `publish-site` copies `website/` plus the APK
   (`downloads/stauende-latest.apk`) and build metadata
   (`downloads/build-info.json`) into a deployable bundle.
4. `deploy` publishes that bundle to GitHub Pages.

The APK is never committed to git — it's assembled fresh into the Pages
deployment on every run.

**One-time repo setup** (cannot be done from a workflow file):

- Settings → Pages → Source: **GitHub Actions**.
- `CNAME` here points Pages at `stauende.net`; that only takes effect once
  DNS for the domain is pointed at GitHub Pages. Until then the site is
  still reachable at the default `*.github.io` URL.

## Local preview

```
open website/index.html
# or
python3 -m http.server --directory website 8080
```

The "APK herunterladen" button and build-info line only resolve once the
site has been deployed by CI — locally there's no `downloads/` directory.
