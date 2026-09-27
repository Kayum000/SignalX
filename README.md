# SignalX

Standalone Quotex chart signal assistant.

## Goals
- Analyze the currently selected market from the visible Quotex chart.
- No external market-data provider.
- Signal: CALL / PUT / WAIT.
- Signal score and Bangla reasons.
- Responsive mobile/laptop UI.
- Browser-side bridge for chart candles; the Render-hosted page does not access Quotex data directly.

## Local
Open index.html in a browser, or serve the folder with any static HTTP server.

## Quotex bridge
The extension is intentionally separated from the signal engine. It listens for candle data from a browser-side adapter. The exact Quotex chart implementation can change, so the adapter must be validated against the current live page before treating live extraction as complete.

## Disclaimer
Signals are informational only and are not a guarantee of trading results.


## Android Quotex overlay

The `android/` module adds a SignalX Android overlay that can sit above the Quotex app. It uses Android's user-approved screen capture (MediaProjection) to inspect the visible Quotex chart and derive relative candle geometry locally; it does not call an external market-data provider or place trades. Android 14+ requires the mediaProjection foreground-service declaration and permission. The visual candle extractor must be calibrated against the exact Quotex Android version/device before treating live extraction as production-ready.
