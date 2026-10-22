# Pollster viewer

A static React + Vite site that renders a Pollster survey report. It reads the
paste key from the URL fragment (`<viewer-url>/#<key>`), fetches the snapshot
from a bytebin-compatible paste service, and draws it. No backend, no tracking.

## Develop

```
npm install
npm test
npm run dev
```

Open `http://localhost:5173/pollster/#<key>`. Use `?paste=<url>` to fetch from a
different paste service, for example `?paste=http://localhost:8080`.

The default paste service is `https://bytebin.lucko.me`. Change it at build time
with the `VITE_PASTE_URL` environment variable.

`src/fixtures/*.json` are sample snapshots (format 1) used by the tests.

## Publish

The workflow `.github/workflows/viewer.yml` runs the tests, builds, and deploys
to GitHub Pages on every push to `main` that touches `pollster-viewer/**`.

1. In the repository settings, open Pages and set the source to GitHub Actions.
2. Optionally set the repository variable `VITE_PASTE_URL` to use another paste service.
3. Push to `main`. The site appears at `https://<owner>.github.io/<repo>/`.
4. Put that URL in the plugin's `settings.yml` as `publishing.viewer-url`.

The Vite `base` is `/pollster/`, which matches a repository named `pollster`.
For another repository name, change `base` in `vite.config.ts`.

GitHub Pages from a private repository needs a paid GitHub plan. Without one,
build locally (`npm run build`), host the contents of `dist/` on any static host
(adjusting `base` to that host's path), and set `publishing.viewer-url` to it.
