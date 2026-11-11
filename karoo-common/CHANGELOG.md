# karoo-common changelog

## 1.5.0 (2026-11-11)

Nothing changes for apps that don't use it (no visual change, no release needed).

- `FieldPacer`: a field's redraw loop sleeps until its state or the page on screen changes (or the
  next redraw is due) instead of waking every 40–100 ms to look. Same redraw timing; other inputs
  (settings, night mode) are seen within a second. Unit tests in `src/test`.
- `streamDataFlow`, `consumerFlow`, `StreamState.field` / `single` (KarooFlows.kt): the flow helpers
  every app had its own copy of. An app that keeps its copy keeps using it.

## 1.4.0 (2026-11-10)

Added for UpAhead; nothing changes for apps that don't use it.

- `settingsChoiceList`: one choice from a list too long for a segmented control (a row per option, a
  ring at the right, the picked one on a grey card).

## 1.3.0 (2026-11-10)

- `KarooField.views(…, footerPx = n)`: a bottom band kept at true size too (top fixed, middle in the
  room left, bottom fixed), for a value line under the number (Mountaineer's "+ 0:18 VS PR", grade
  boxes). Without `footerPx` nothing changes.

## 1.2.0 (2026-11-10)

- `KarooField.views(context, bitmap, headerPx)` + `KarooField.headerBandPx(tileH)`: a field drawn as
  one picture keeps its header at true size when the Karoo shortens the row (map page with a route:
  141 → 123 px squashed the whole picture to 0.87). Use it instead of one `fitXY` ImageView.

## 1.1.0 (2026-11-10)

Added for UpAhead; nothing changes for apps that don't use them (no visual change, no release needed).

- `Physics` and `Wind` (Physics.kt): Mountaineer's cycling physics (power ↔ speed on a grade, headwind),
  so apps share one copy. Mountaineer still has its own until it syncs.
- `settingsRankList`: a list the rider orders by dragging a handle (icon priority in UpAhead), with
  the kit icons `ic_settings_handle` and `ic_settings_priority`.

## 1.0.0 (2026-11-10)

First version as a library. Until now `KarooType.kt`, `SettingsKit.kt`, `ExampleCard.kt` and
`UpdateCheck.kt` were copied into each app; this is karoo-starter's copy of 2026-11-10 (settings kit
update 4 plus fixes: swatch row shares the width, the GitHub line shrinks, `showCard`).

- `KarooType.labelPaint(caps, outline = true)` for the apps whose labels always had the outline
  (Mountaineer, SmallerFields).
- The settings app rows read the app name from the installed app (no `R.string.app_name` needed).
- `UpdateActivity` comes with the library's manifest.
