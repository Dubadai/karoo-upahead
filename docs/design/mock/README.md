# UpAhead mock, round 1 (2026-11-10)

`python3 -I make.py` builds `mockup.html` (the mock kit's `build.py`, plus the POI glyphs from
`../icons/gen.py`); `shoot.sh` made `shots/` (light on Sky, then dark on Night; Settings light only).
Test route Mallorca 312: the profile is traced from the organiser's picture, POIs from it (`../feasibility.md`).

Decided before this round: icon style B, the stacked column per stop; graph polish later,
main things first; two graph styles (Detailed like Mountaineer, Simple like the Karoo's own in
another colour); readability first: icons never too small, an icon size setting and a priority list.

| Shot | What |
| --- | --- |
| 01 / 02 / 03 | Full page, whole route: Simple (climb blue), Detailed (grade colours), Simple in the Karoo's own yellow |
| 04 / 05 | Half page; two tiles + one tile (one icon per stop) |
| 06 (GIF), 07–11 | Ranges: whole → 100 → 50 → 10 → 5 → 1 km (5 and 1 km Detailed) |
| 12 | Map: the Karoo's own pins + our corner badges, timed section as black dashes |
| 13–15 | Settings (Graph, Icon size, Priority, Start with, On the graph, On the map) |
| 16–18 | Large icons: full page, half page (default priority), half page with water first |

Colours: Simple = Karoo `climb_blue_500` line / 50 % fill (dark: `climb_blue_400`); the Karoo's own
elevation graph is `elevation_line` #CBBA00 / #FFE900 with a 50 % fill. Detailed = `climb1..7`.

Open: graph default and colour; icon sizes and the default priority; small sizes with one
icon per stop; the range set; the map additions. The priority list is a new settings-kit piece
(`rankList`): it goes into `karoo-common` first once it's OKd.

## Round 2 (2026-11-10)

Feedback: no circles around "+3"; bigger map icons; text and icons never on the graph; dashed lines
always straight; the graph fills the tile to its bottom edge; only the next cut-off.
Done: the graph runs edge to edge down to the tile's bottom (no km axis); columns, cut-off time,
summit heights and the next-stop note sit in their own band above it; columns stand at their real
km with straight dashed lines; stops closer than an icon merge into one column (zoom in to see them
apart); only the next cut-off shows, with its time on top of its column; "+3" as plain text; map
pins 104 px with bigger glyphs and 44 px corner badges. Same shot names as round 1.

## Round 3 (2026-11-10)

Feedback: try Karoo purple and Karoo yellow for the graph; icons on the graph much bigger.
Done: Simple in Karoo purple (the lap colour, `purple_500` #A252CC; dark: `purple_400` #EABFFF line)
and Karoo yellow (the Karoo's own `elevation_line`), shots 03, 19–21. Icons: Small 50 / 42 / 36 px,
Large 62 / 52 / 44 px (full page / half page / tiles). Trade-off: at half page only two icons fit per
column (the rest as "+2"), and close stops merge more often on the whole route.

## Round 4 (2026-11-10)

Decided: **climb blue** for the graph (purple and yellow tried in round 3, blue was better).
Icons even bigger: Small 62 / 52 / 44 px, Large 74 / 62 / 52 px (full page / half page / tiles).
At half page a column now holds two icons plus "+n".

## Round 5 (2026-11-10)

Feedback: the cut-off time in the header ("Next cut"); the range as just km; Large icons larger,
Small a tad smaller; bigger glyphs in the yellow badges.
Done: header "NEXT CUT 11:20" (time in red; "CUT 11:20" on a one-tile field) left, "312 KM" / "50 KM"
right; icons Small 56 / 48 / 40 px, Large 86 / 72 / 58 px (full page / half page / tiles); yellow and
red badges draw their glyph at 0.86 of the circle (was 0.68). "+n" is now a text line above the
icons, so every stop always keeps at least one icon.

## Round 6 (2026-11-10)

Feedback: the rider picks the header's info (next cut, feed…) and shows it as ETA or km; use
Mountaineer's ETA, made smoother for the flat and for bunch riding.
Done: Settings › At the top (Cut / Feed / Water / Toilet / Off) and Show as (Distance / Time); shots
22–26 (header variants), 14 (those settings), 14b (icon size and priority). Time = arrival time for a
cut-off (green before, red after), time to go for the rest. The ETA plan is in `../feasibility.md` 4f.
