# Golden data of Twinlit

Every test corpus in `android/app/src/test/resources/`, where it came from, and how to make it
again. The rule for all of them: **an expected value comes from the library or the page the app
must agree with, never from the Kotlin code under test.** A corpus is changed only by running its
generator, never by hand.

Places in every corpus are Oslo and other big world cities (SPEC 13). The two source apps' corpora
held places this build must not use; each such case was swapped for Oslo or Helsinki and made
again with the original generator, or, where a generator cannot run from this repository, the
rows were deleted (listed below).

## Sun data: can be regenerated from this repository

`tools/golden/suncalc.js` is SunCalc 1.8.0 (mourner/suncalc, BSD-2-Clause), the library code byte
for byte as SunApp ships it. Its header comment carries the copyright line and the licence terms
(the licence's first condition), and `NOTICE` the full text. `tools/golden/gen-solar-golden.ts` runs it under Node 24
and writes the corpus:

    node tools/golden/gen-solar-golden.ts           # write android/app/src/test/resources/suncalc-golden.csv
    node tools/golden/gen-solar-golden.ts --check   # write nothing, exit 1 unless the committed CSV matches

| CSV | Source | Generator | Swapped | Date |
|---|---|---|---|---|
| `suncalc-golden.csv` (25 cases, 14 events each) | SunApp `2a26f98` `tools/gen-solar-golden.ts` and its CSV | `tools/golden/gen-solar-golden.ts` (the same script; the library and output paths adapted) | cases G04 to G09 were the place the privacy rules forbid; now **Oslo** (59.9139, 10.7522) on the same six dates. The other 19 cases are byte-identical to SunApp's | 2026-11-20 |

Check done when it was made: `diff` of the 19 unchanged cases against SunApp's CSV is empty, and
the generator's own assertions (G12, G13, G25) pass.

## City search: cannot be regenerated here

| CSV | Source | Generator | Change | Date |
|---|---|---|---|---|
| `citysearch-golden.csv` (152 queries) | MoonApp `68c7596`, written from the compiled web page by MoonApp's `tools/gen-search-parity.ts` | not carried: it loads MoonApp's web tree (`app.js`, `cities.js`) | the 10 rows whose query or listed cities name a place the build must not use were **deleted** (162 to 152); no row was edited | 2026-11-20 |

## Moon data: cannot be regenerated from this repository alone

Added by milestone M2. MoonApp's generators load MoonApp's committed
web tree (`app.js`, `cities.js`, `suncalc.js`), which Twinlit does not carry (D35). To regenerate a
CSV, run its generator in a scratch copy of MoonApp 68c7596, with Node 24 and no network:

    mkdir -p "$SCRATCH/moon" && git -C <MoonApp clone> archive 68c7596 | tar -x -C "$SCRATCH/moon"
    cd "$SCRATCH/moon"
    # the one place the build must not use, swapped for Helsinki (60.1699, 24.9384, Europe/Helsinki),
    # in the three generators that hold it (gen-lunar-parity.ts, gen-webparity-golden.ts,
    # gen-moontimes-golden.ts); nothing else is changed
    node tools/gen-lunar-parity.ts       # lunation-parity.csv, transit-parity.csv, lunar-daykind-parity.csv
    node tools/gen-webparity-golden.ts   # webparity-golden.csv
    node tools/gen-moontimes-golden.ts   # moontimes-golden.csv
    node tools/gen-quarter-parity.ts     # quarter-parity.csv (no swap needed)
    node tools/gen-golden.ts             # mooncalc-golden.csv (no swap needed)
    # then copy android/app/src/test/resources/*.csv here

Before any change, `node tools/gen-*.ts --check` in the untouched scratch copy matches MoonApp's own
CSVs (done 2026-11-20: all seven generators OK), which proves the offline run reproduces them.

| CSV | Rows | Source | Generator | Swapped | Date |
|---|---|---|---|---|---|
| `mooncalc-golden.csv` | 60 | MoonApp `68c7596` | `gen-golden.ts` (suncalc.js `getMoonIllumination`) | none | byte-identical, 2026-11-20 |
| `moontimes-golden.csv` | 48 | MoonApp `68c7596` | `gen-moontimes-golden.ts` (suncalc.js `getMoonTimes`, UTC mode) | the 6 rows of the forbidden place became **Helsinki** | 2026-11-20 |
| `webparity-golden.csv` | 91 | MoonApp `68c7596` | `gen-webparity-golden.ts` (the compiled web page, city-local day) | the 11 rows of that place (9 dates and 2 DST days) became **Helsinki** | 2026-11-20 |
| `transit-parity.csv` | 820 | MoonApp `68c7596` | `gen-lunar-parity.ts` (the web page's `lunarTransitFrom`) | the 89 rows of that place became **Helsinki** | 2026-11-20 |
| `lunation-parity.csv` | 487 | MoonApp `68c7596` | `gen-lunar-parity.ts` (`getNextLunations`) | none (zones only) | byte-identical, 2026-11-20 |
| `lunar-daykind-parity.csv` | 355 | MoonApp `68c7596` | `gen-lunar-parity.ts` (the day-kind label) | none (its places are Tromso, Longyearbyen, Murmansk, Utqiagvik, McMurdo and the grid) | byte-identical, 2026-11-20 |
| `quarter-parity.csv` | 475 | MoonApp `68c7596` | `gen-quarter-parity.ts` (`phaseCrossing`) | none | byte-identical, 2026-11-20 |

Check done when they were made: `diff` against MoonApp's CSVs shows exactly 89 + 11 + 6 changed
rows (the swapped place) and nothing else; the row counts are unchanged. Helsinki changes its clocks
on the same two days as Oslo (29 March and 25 October 2026), so the daylight-saving rows keep their
meaning. The transit and web-parity rows also feed `MoonOccurrenceTest`, which holds the alarm
engine to them.

## Day kind (the polar label): run in a scratch copy of SunApp

Added by milestone M3. `daykind-parity.csv` (360 rows: six places, sixty dates each, label NORMAL,
POLAR_NIGHT or MIDNIGHT_SUN) holds what **the web page's own classifier** gives (SunApp's
`app.js` section 9.4, `onCalculate`, with the bundled `suncalc.js`). SunApp has no generator script
for it: the rows are made by SunApp's `tests/load.ts` (`loadPage()`, the page's three scripts in a
Node `vm` context), as its `tests/golden.test.ts` does. To make it again, in a scratch
`git archive 2a26f98` copy of SunApp with Node 24 and no network: for each row, `switchTab('gps')`,
put latitude, longitude and date in the page's fields, call `onCalculate()` and read whether the
sunrise and sunset cards say "Midnight Sun" or "Polar Night" (otherwise NORMAL).

| CSV | Rows | Source | Generator | Swapped | Date |
|---|---|---|---|---|---|
| `daykind-parity.csv` | 360 | SunApp `2a26f98` | the page classifier through `tests/load.ts` (above) | the 60 rows of the place the privacy rules forbid became **Helsinki** (60.1699, 24.9384) on the same 60 dates | 2026-11-20 |

Check done when it was made: running the same procedure over all 360 rows of SunApp's own CSV
reproduced it byte for byte (so the method is the one that made it); the 300 rows of the other
five places (Longyearbyen, the North Pole, Singapore, Tromso, Ushuaia) are byte-identical to
SunApp's. Helsinki is normal on all 60 dates, as the swapped place was.
