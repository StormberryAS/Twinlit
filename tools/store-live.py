#!/usr/bin/env python3
"""Show or hide the store buttons on index.html (they are hidden until a listing is live).

  store-live.py --check                 print the state of both buttons
  store-live.py zapstore                show the Zapstore button (the Zapstore listing is live)
  store-live.py play                    show the Google Play button (the Play listing is live)
  store-live.py zapstore play           show both
  store-live.py --hide zapstore play    hide again

Changes only the data-live="no|yes" attribute of the two links. Run bump_assets.py afterwards if
you also changed a stylesheet. Commit and push the page yourself.
"""
import pathlib
import re
import sys

PAGE = pathlib.Path(__file__).resolve().parent.parent / "index.html"
KEY = {"zapstore": "https://zapstore.dev/apps/no.stormberry.twinlit",
       "play": "https://play.google.com/store/apps/details?id=no.stormberry.twinlit.play"}


def main(argv):
    text = PAGE.read_text(encoding="utf-8")
    state = {k: re.search(r'data-live="(yes|no)" href="%s"' % re.escape(u), text) for k, u in KEY.items()}
    if not all(state.values()):
        raise SystemExit("store-live: a store link was not found exactly as expected")
    if "--check" in argv or not argv:
        for k, m in state.items():
            print(f"{k:9} data-live={m.group(1)}")
        return 0
    hide = "--hide" in argv
    names = [a for a in argv if a != "--hide"]
    for n in names:
        if n not in KEY:
            raise SystemExit(f"store-live: unknown store {n!r}; use zapstore or play")
        text = text.replace(f'data-live="{"yes" if hide else "no"}" href="{KEY[n]}"',
                            f'data-live="{"no" if hide else "yes"}" href="{KEY[n]}"', 1)
    PAGE.write_text(text, encoding="utf-8")
    return main(["--check"])


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
