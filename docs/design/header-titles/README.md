# Header titles (mock, 2026-11-11)

Was "NEXT ⏱ 5.4 KM" from the left. Now one sentence across the header: on the left the icon and words ending in "IN"
("COMING UP IN", "NEXT FEED IN", "NEXT CUT-OFF IN", "FINISH IN"), on the right the value with its unit ("5.4 KM", a time).
Small tiles: the icon and "IN" on the left, the value on the right. When space is short the words get fewer ("FEED IN",
"IN"); a number never loses its unit. Cut-off as time: "⌛ CUT-OFF 13:30" and "1:46 H SPARE" (green) / "LATE 0:20 H" (red).
Time: "1:20 H", under an hour "25 MIN" (picked A over "1 H 20 MIN"). Off route unchanged ("REJOIN IN 2.4 KM", red).

Rounds: 1 title left + "icon IN distance" right; 2 small tiles split, summit without its height (read as two numbers);
3 the words move left so the value stands alone with its unit (feedback: "IN 1:20" didn't say it was a time).

Drawn by the field's own code (`gallery.sh`, branch `header-titles`, on top of `bare-icons`): `mockup.html`. Settings: help texts rewritten to match (At the top, Show as, Icon size, Priority, Start with), and "At the top" and "Start with" as
choice cards like SmallerFields' selector (a card per option with a short line, radio on the right, the picked one with an
Apple-blue ring #007AFF / night #0A84FF and a filled radio; an exception to Karoo-native colours, our pick): `images/settings-*.jpg`. In the kit since
karoo-common 1.7.0 (`settingsChoiceCards`); UpAhead uses the kit's. Every help text sits under its heading, before the choices
(our rule, karoo-common 1.8.0 `settingsIntro`).
Device: `01-device-real-route-light.png`, a real route on our test Karoo (build b5f2735): "COMING UP IN … 11.9 KM".
Picked and built 2026-11-11.
