# Bare icons, tile edges, stops at the middle or higher (mock, 2026-11-11)

After the first rides with v0.2.0. Drawn by the field's own code (`gallery.sh`, branch `bare-icons`): `mockup.html`.

- **Tiles without taps** (quarter, row, small): the stop icon without its box, in its colour (picked B over the
  text colour): timing yellow black on light; success_green_600 and climb blue 400 at night.
- **Inside the tile border:** tag, your line and graph 12 px from the left, 9 px from the right, 6 px off the bottom on
  tiles without a km strip.
- **Stops at the middle or higher** on half and full pages: the lowest row sits at the field's middle at most (it sat
  just above the graph on flat stretches), so the lower half stays free for zoom taps.

Gallery: picture-only state `flat` / `flat-25` (a flat route, where stops sat low).

Device: `01-device-real-route-light.png`, a real route on our test Karoo (build e0ef99a): bare icons on the small
tile, stops at the middle on the bigger one. Picked and built 2026-11-11.
