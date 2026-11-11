# Smallest text and Karoo-native colours (mock, 2026-11-11)

The shared rules: nothing on a field smaller than the Karoo's own small labels (capitals about 16.5 px),
small lines not grey, colours from the Karoo's own palette. Drawn by the field's own code on the
emulator (`gallery.sh`, branch `native-look`), now vs fixed, light and dark: `mockup.html`.

- **Summit heights:** 13 px → 17 px, right above each peak (was one row along the top);
  left out only where a stop's icons or dashed line, your line or the heights on the left are. All elevation
  numbers (summits and the heights on the left) in the Karoo's grey, grey_600 / grey_300 (feedback, 2026-11-11).
- **Stop card:** the name and rows no longer shrink to fit; a long name is cut with "…" at full size,
  a long row label becomes its short form ("Distance", "Arrival").
- **Colours:** red → climb6 #D60404 (one red: off route, late, cut-off badges); green → success_green
  700 / 600; greys → grey_200 / 300 / 600; water blue → climb blue 600; checkpoints → k2DarkBlue;
  the soft white-to-sky background stays (feedback, 2026-11-11).
- **Timing yellow:** stays the Karoo yellow #FFE900 (A). B, the zone yellow climb3 #FFF500, was too close to tell
  apart (`images/yellowA-*`, `images/yellowB-*`).
- **Your line** runs behind the stop tiles (faded tiles sit on the background, so it never shows through).

- **Quarter-tile stop card:** cut off at the bottom, but only in the gallery: the card opens on the tapped field,
  and quarter, row and small tiles take no taps on stops. Not changed.

Pictures: `images/now-*` and `images/fixed-*` (light and dark). Awaiting the OK.
