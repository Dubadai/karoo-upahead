# Off route (mock, 2026-11-10)

The Karoo gives apps the way back (`rejoinPolyline`, positions only, no heights) and where it meets the route
(`rejoinDistance`). Mock: the way back in red, as long as the path, a straight dashed slope from your altitude to
the route's height there; the route continues from the rejoin point; header "REJOIN 2.4 KM" in red. Not wired
to live data yet (needs the path length and the altitude stream).

- `red-way-back.png`: 5 km, 25 km (half, light), 5 km dark, full page

**Wired (2026-11-10):** the Karoo's way back and rejoin point from its navigation state, your altitude from
PRESSURE_ELEVATION, ground heights from Open-Meteo (as Mountaineer). Dashed straight slope until they come (or
without a connection), then a solid line. `straight-then-real.png`.

**Lead-in (2026-11-10, after the first device look):** drawn over the route at km 0.0 it looked like you were on
the route. Now the way back is its own red lead-in from your line to the rejoin point; the route is drawn only from
there (no grey ridden part, no stops or km before it), and the km tag hides while off route. `lead-in.png`.
