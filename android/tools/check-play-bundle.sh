#!/usr/bin/env bash
# Checks Twinlit's Google Play edition (no.stormberry.twinlit.play) before it is uploaded.
#
#   tools/check-play-bundle.sh <bundle.aab> <versionName> <versionCode> [upload-cert-sha256]
#   tools/check-play-bundle.sh --manifest <merged-AndroidManifest.xml> <versionName> <versionCode>
#   tools/check-play-bundle.sh --dex <file.apk | file.aab | directory of *.dex>
#
# The Play edition IS the sovereign app (the parity rule, Marcos 2026-11-17): the same manifest, the
# same code and the same alarms, with only the package name, the signing and the versionCode changed.
# It fails (exit 1) unless:
#   - the package is no.stormberry.twinlit.play, with the given versionName and versionCode
#   - the uses-permission set equals android/expected-permissions.txt, and no permission carries
#     the application ID (Twinlit removes androidx's app-private DYNAMIC_RECEIVER permission in the
#     main manifest, so the bundle needs no prefix replacement)
#   - no <permission> is declared at all
#   - SCHEDULE_EXACT_ALARM carries maxSdkVersion 32, and INTERNET is not declared
#   - the five alarm components are present (AlarmIntentActivity, RingActivity, AlarmService,
#     AlarmFireReceiver, SystemEventReceiver); the activities are exactly MainActivity,
#     AlarmIntentActivity and RingActivity; the app's receivers are exactly the two alarm
#     receivers, the six widget receivers (three Sun, three Moon) and the one WidgetEventReceiver;
#     library components are listed but not counted
#   - AlarmService is a foreground service of type systemExempted
#   - bundle and --dex modes: the dex holds every class the manifest names (the 13 components of the
#     app itself: MainActivity, the two alarm activities, AlarmService, the two alarm receivers, the six
#     widget receivers and WidgetEventReceiver). AGP keeps exactly those with `-keep class <name>
#     { <init>(); }` (aapt_rules.txt), so R8 can neither remove, merge nor rename them. Every other
#     class may be merged or inlined and is not listed. Each name is matched as a whole type descriptor
#     (with the trailing ';') and as a fixed string.
#   - bundle mode only: exactly one signer, NOT the sovereign release certificate, and equal to
#     Twinlit's Play upload certificate. The 4th argument replaces that certificate (hex, with or
#     without colons). Both certificates are pinned below (made on 2026-11-23, one key per purpose);
#     a certificate that is not a 64-digit SHA-256 never passes.
#
# --dex mode runs only the dex half, on an APK, an AAB or a directory of *.dex files (for example the
# Play edition's `:app:minifyReleaseWithR8` output, which needs no key). It prints "CHECK PASSED (dex
# only)" or "CHECK FAILED".
#
# --manifest mode reads a merged manifest instead of a bundle (the one Gradle writes for
# `:app:processReleaseManifest -PplayBuild=true`, which needs no upload key), so CI can run the
# manifest half of this check on every release. It cannot see the dex or the signature.
#
# Read only: it writes nothing but a temporary directory it removes. Needs python3, unzip and, in
# bundle mode, java (JAVA_HOME or PATH) and the bundletool jar (BUNDLETOOL, default
# ~/Android/tools/bundletool-all-1.18.3.jar). Every check fails closed: no early-exit reader sits
# in a pipe under pipefail (grep reads the files itself; sed -n 1p, never head -1).
set -euo pipefail

usage() {
  echo "usage: $0 <bundle.aab> <versionName> <versionCode> [upload-cert-sha256]" >&2
  echo "       $0 --manifest <merged-AndroidManifest.xml> <versionName> <versionCode>" >&2
  echo "       $0 --dex <file.apk|file.aab|directory of dex files>" >&2
  exit 2
}

MODE=bundle
if [ "${1:-}" = "--manifest" ]; then
  MODE=manifest; shift
  [ $# -eq 3 ] || usage
elif [ "${1:-}" = "--dex" ]; then
  MODE=dex; shift
  [ $# -eq 1 ] || usage
else
  [ $# -ge 3 ] && [ $# -le 4 ] || usage
fi
UPLOAD_CERT=c9188922c73d3a48e379673fe994d4f0a6d2367942b7f1e747192bfccdd13c33    # the Twinlit Play upload key (alias upload, 2026-11-23)
if [ "$MODE" = dex ]; then SRC="$1" NAME="" CODE="" CERT=""; else SRC="$1" NAME="$2" CODE="$3" CERT="${4:-$UPLOAD_CERT}"; fi
HERE="$(cd "$(dirname "$0")" && pwd)"
EXPECTED="${EXPECTED_PERMISSIONS:-$HERE/../expected-permissions.txt}"
BUNDLETOOL="${BUNDLETOOL:-$HOME/Android/tools/bundletool-all-1.18.3.jar}"
RELEASE_CERT=d54d480d04ece7e8e3a0215abfacffaa2f0f84e4b0303c224a6b89bc84a77418   # the sovereign release key (alias release, 2026-11-23)
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"; KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
fail=0
ok()  { printf 'ok    %s\n' "$*"; }
bad() { printf 'FAIL  %s\n' "$*" >&2; fail=1; }

if [ "$MODE" = dex ]; then
  [ -f "$SRC" ] || [ -d "$SRC" ] || { echo "no such file or directory: $SRC" >&2; exit 2; }
else
  [ -f "$SRC" ] || { echo "no such file: $SRC" >&2; exit 2; }
  [ -f "$EXPECTED" ] || { echo "no expected-permissions.txt at $EXPECTED" >&2; exit 2; }
  command -v python3 >/dev/null || { echo "python3 is needed" >&2; exit 2; }
fi
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT

# The dex half: every class the manifest keeps must be in the dex files of <dir>.
# The list EQUALS the app's own activities, services and receivers (ReleaseKitTest checks both ways).
DEX_CLASSES="MainActivity alarm/AlarmIntentActivity alarm/RingActivity alarm/AlarmService
  alarm/AlarmFireReceiver alarm/SystemEventReceiver
  widget/sun/SunWidgetSmall widget/sun/SunWidgetMedium widget/sun/SunWidgetLarge
  widget/moon/MoonWidgetSmall widget/moon/MoonWidgetMedium widget/moon/MoonWidgetLarge
  widget/WidgetEventReceiver"
check_dex() {
  local dir="$1" cls
  if ls "$dir"/*.dex >/dev/null 2>&1; then
    for cls in $DEX_CLASSES; do
      if grep -a -q -F "Lno/stormberry/twinlit/$cls;" "$dir"/*.dex; then
        ok "the dex holds $cls"
      else
        bad "the dex does not hold $cls"
      fi
    done
  else
    bad "no dex files in $dir"
  fi
}

if [ "$MODE" = dex ]; then
  if [ -d "$SRC" ]; then
    check_dex "$SRC"
  else
    mkdir -p "$T/dex"
    # An APK keeps classes*.dex at its root, an AAB under base/dex/. unzip -j flattens the names.
    case "$SRC" in
      *.aab) unzip -q -o -j "$SRC" 'base/dex/*.dex' -d "$T/dex" || true ;;
      *)     unzip -q -o -j "$SRC" 'classes*.dex' -d "$T/dex" || true ;;
    esac
    check_dex "$T/dex"
  fi
  [ "$fail" = 0 ] && { echo "CHECK PASSED (dex only)"; exit 0; } || { echo "CHECK FAILED" >&2; exit 1; }
fi

if [ "$MODE" = bundle ]; then
  [ -f "$BUNDLETOOL" ] || { echo "no bundletool jar at $BUNDLETOOL" >&2; exit 2; }
  "$JAVA" -jar "$BUNDLETOOL" dump manifest --bundle="$SRC" > "$T/manifest.xml"
else
  cp "$SRC" "$T/manifest.xml"
fi
grep -vE '^[[:space:]]*(#|$)' "$EXPECTED" | sort -u > "$T/expected.txt"

# The manifest half, parsed as XML (a merged manifest spreads one element over many lines).
# Prints ok/FAIL lines; exits 1 on any FAIL, 2 when the file is not a manifest.
set +e
python3 -I - "$T/manifest.xml" "$T/expected.txt" "$NAME" "$CODE" <<'PY'
import sys, xml.etree.ElementTree as ET
path, expected_path, name, code = sys.argv[1:5]
A = '{http://schemas.android.com/apk/res/android}'
PLAY, SOV = 'no.stormberry.twinlit.play', 'no.stormberry.twinlit'
fail = False
def ok(m): print('ok    ' + m, flush=True)
def bad(m):
    global fail
    print('FAIL  ' + m, file=sys.stderr, flush=True); fail = True
try:
    root = ET.parse(path).getroot()
except ET.ParseError as e:
    print('not a manifest: %s' % e, file=sys.stderr); sys.exit(2)
if root.tag != 'manifest':
    print('not a manifest: root is %s' % root.tag, file=sys.stderr); sys.exit(2)

(ok if root.get('package') == PLAY else bad)('package %s (is %s)' % (PLAY, root.get('package')))
(ok if root.get(A + 'versionName') == name else bad)('versionName %s (is %s)' % (name, root.get(A + 'versionName')))
(ok if root.get(A + 'versionCode') == code else bad)('versionCode %s (is %s)' % (code, root.get(A + 'versionCode')))

uses = [e for e in root if e.tag in ('uses-permission', 'uses-permission-sdk-23')]
names = [e.get(A + 'name') for e in uses]
actual = sorted(set(names))
expected = [l.strip() for l in open(expected_path) if l.strip()]
if len(names) != len(set(names)):
    bad('a permission is declared twice: %s' % names)
if actual == sorted(expected):
    ok('the %d permissions equal expected-permissions.txt' % len(actual))
else:
    bad('the permissions differ from expected-permissions.txt:\n      missing %s\n      extra   %s'
        % (sorted(set(expected) - set(actual)), sorted(set(actual) - set(expected))))
own = [n for n in names if n.startswith(SOV + '.')]
(ok if not own else bad)('no permission carries the application ID (the androidx DYNAMIC_RECEIVER one is removed): %s' % own)
sched = [e for e in uses if e.get(A + 'name') == 'android.permission.SCHEDULE_EXACT_ALARM']
(ok if len(sched) == 1 and sched[0].get(A + 'maxSdkVersion') == '32' else bad)('SCHEDULE_EXACT_ALARM has maxSdkVersion 32')
(ok if 'android.permission.INTERNET' not in names else bad)('no INTERNET permission')
decl = sorted(e.get(A + 'name') for e in root if e.tag == 'permission')
(ok if not decl else bad)('no <permission> is declared (are %s)' % decl)

apps = [e for e in root if e.tag == 'application']
if len(apps) != 1:
    bad('%d <application> elements' % len(apps)); sys.exit(1)
app = apps[0]
def full(n):
    return SOV + n if n.startswith('.') else n
comps = {}
for e in app:
    if e.tag in ('activity', 'activity-alias', 'service', 'receiver', 'provider'):
        comps.setdefault(e.tag, []).append((full(e.get(A + 'name')), e))
mine = lambda tag: sorted(n for n, _ in comps.get(tag, []) if n.startswith(SOV + '.'))
P = SOV + '.'
want_acts = sorted(P + x for x in ('MainActivity', 'alarm.AlarmIntentActivity', 'alarm.RingActivity'))
all_acts = sorted(n for n, _ in comps.get('activity', []))
(ok if all_acts == want_acts else bad)('the activities are exactly MainActivity, AlarmIntentActivity and RingActivity (are %s)' % all_acts)
(ok if not comps.get('activity-alias') else bad)('no activity-alias')
want_recv = sorted(P + x for x in ('alarm.AlarmFireReceiver', 'alarm.SystemEventReceiver',
    'widget.sun.SunWidgetSmall', 'widget.sun.SunWidgetMedium', 'widget.sun.SunWidgetLarge',
    'widget.moon.MoonWidgetSmall', 'widget.moon.MoonWidgetMedium', 'widget.moon.MoonWidgetLarge',
    'widget.WidgetEventReceiver'))
(ok if mine('receiver') == want_recv else bad)("the app's receivers are the two alarm, six widget and one widget event receiver (are %s)" % mine('receiver'))
(ok if mine('service') == [P + 'alarm.AlarmService'] else bad)("the app's only service is AlarmService (are %s)" % mine('service'))
(ok if not mine('provider') else bad)("the app declares no provider of its own (%s)" % mine('provider'))
for c in ('alarm.AlarmIntentActivity', 'alarm.RingActivity', 'alarm.AlarmService', 'alarm.AlarmFireReceiver', 'alarm.SystemEventReceiver'):
    present = any(n == P + c for lst in comps.values() for n, _ in lst)
    (ok if present else bad)('alarm component %s is present' % c)
svc = [e for n, e in comps.get('service', []) if n == P + 'alarm.AlarmService']
fgs = svc[0].get(A + 'foregroundServiceType') if svc else None
# Text in a merged manifest, a flag value in a bundle dump (systemExempted = 0x400).
is_exempt = fgs is not None and (fgs == 'systemExempted' or (fgs.lower().startswith('0x') and int(fgs, 16) == 0x400) or fgs == '1024')
(ok if is_exempt else bad)('AlarmService is a foreground service of type systemExempted (is %s)' % fgs)
lib = sorted(n for lst in comps.values() for n, _ in lst if not n.startswith(SOV + '.'))
print('      library components (not counted): %s' % (' '.join(lib) or 'none'))
sys.exit(1 if fail else 0)
PY
rc=$?
set -e
case "$rc" in
  0) ;;
  1) fail=1 ;;
  *) echo "the manifest could not be read (exit $rc)" >&2; exit 2 ;;
esac

if [ "$MODE" = bundle ]; then
  mkdir -p "$T/dex"
  unzip -q -o -j "$SRC" 'base/dex/*.dex' -d "$T/dex" || bad "no dex in the bundle"
  check_dex "$T/dex"

  SHA="$("$KEYTOOL" -printcert -jarfile "$SRC" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | sed -n 1p | tr -d ':' | tr 'A-F' 'a-f' || true)"
  if [ -z "$SHA" ]; then
    bad "the bundle carries no signature"
  else
    signers="$(unzip -Z1 "$SRC" | grep -cE '^META-INF/[^/]+\.(RSA|EC|DSA)$' || true)"
    printf '      signing certificate SHA-256: %s (signers: %s)\n' "$SHA" "$signers"
    [ "$signers" = 1 ] && ok "exactly one signer" || bad "$signers signers"
    # Fails closed: until the real release certificate is pinned above, "is it the release key?" cannot
    # be answered, and an answer that cannot be given is not "no".
    if [ "${#RELEASE_CERT}" != 64 ]; then
      bad "the sovereign release certificate is not pinned in this script yet (a placeholder)"
    else
      [ "$SHA" != "$RELEASE_CERT" ] && ok "not signed with the sovereign release key" || bad "SIGNED WITH THE SOVEREIGN RELEASE KEY: never upload this"
    fi
    want="$(printf '%s' "$CERT" | tr -d ':' | tr 'A-F' 'a-f')"
    [ "${#want}" = 64 ] || bad "the expected upload certificate '$CERT' is not a SHA-256"
    [ "$SHA" = "$want" ] && ok "signed with the expected upload certificate $want" || bad "certificate is not the expected $want"
  fi
else
  echo "      manifest mode: the dex and the signature are not checked"
fi

[ "$fail" = 0 ] && { echo "CHECK PASSED"; exit 0; } || { echo "CHECK FAILED" >&2; exit 1; }
