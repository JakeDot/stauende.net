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

Any static host works since the page has no dependencies or build step —
e.g. GitHub Pages pointed at this directory, Netlify, or Cloudflare Pages.
