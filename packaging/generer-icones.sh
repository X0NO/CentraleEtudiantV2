#!/usr/bin/env bash
# Génère toutes les icônes de l'app à partir de packaging/icone.svg.
# Prérequis : rsvg-convert (librsvg) et python3 + Pillow (pour le .ico Windows).
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

rsvg-convert -w 64  -h 64  packaging/icone.svg -o src/main/resources/icone-64.png
rsvg-convert -w 256 -h 256 packaging/icone.svg -o src/main/resources/icone-256.png
rsvg-convert -w 512 -h 512 packaging/icone.svg -o packaging/icone.png
for t in 16 32 48 256; do rsvg-convert -w $t -h $t packaging/icone.svg -o "$TMP/$t.png"; done

python3 - "$TMP" <<'PY'
import sys
from PIL import Image
d = sys.argv[1]
tailles = [16, 32, 48, 256]
images = [Image.open(f"{d}/{t}.png") for t in tailles]
images[-1].save("packaging/icone.ico", sizes=[(t, t) for t in tailles], append_images=images[:-1])
PY
echo "Icônes générées."
