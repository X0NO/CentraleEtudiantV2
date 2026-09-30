#!/usr/bin/env bash
# Construit Centrale Étudiant pour Linux, avec Java inclus (rien à installer pour l'utilisateur).
#
# Produit dans target/dist/ :
#   - CentraleEtudiant-<version>-linux-x64.tar.gz : dossier portable (décompresser, lancer bin/CentraleEtudiant)
#   - centrale-etudiant_<version>_amd64.deb       : paquet Debian / Ubuntu (si dpkg-deb et fakeroot sont installés)
#
# Utilisation : packaging/construire-linux.sh [version]      (ex : packaging/construire-linux.sh 1.2.0)
# Prérequis   : un JDK 17 ou plus récent (avec jpackage) et Maven.
set -euo pipefail

cd "$(dirname "$0")/.."
VERSION="${1:-${VERSION:-1.0.0}}"
VERSION="${VERSION#v}"   # "v1.2.0" -> "1.2.0"

echo "==> Compilation (mvn package)"
mvn -q -B -DskipTests package

echo "==> Préparation"
rm -rf target/jpackage-input target/dist
mkdir -p target/jpackage-input target/dist
cp target/centrale-etudiant.jar target/jpackage-input/

# Modules Java réellement utilisés (Java embarqué plus léger)
# + jdk.localedata (dates en français) + jdk.crypto.ec (connexions HTTPS/IMAPS, n'existe plus à partir de Java 22)
MODULES="$(jdeps --multi-release base --ignore-missing-deps --print-module-deps target/centrale-etudiant.jar)"
MODULES="$MODULES,jdk.localedata,jdk.charsets,java.naming"
if java --list-modules | grep -q '^jdk.crypto.ec@'; then MODULES="$MODULES,jdk.crypto.ec"; fi
echo "    modules : $MODULES"

COMMUN=(
  --name CentraleEtudiant
  --app-version "$VERSION"
  --vendor "Centrale Étudiant"
  --description "Notes, emploi du temps, devoirs et mails de l'université"
  --input target/jpackage-input
  --main-jar centrale-etudiant.jar
  --main-class com.centrale.Main
  --add-modules "$MODULES"
  --java-options "--enable-native-access=ALL-UNNAMED"
  --icon packaging/icone.png
  --dest target/dist
)

echo "==> Appli portable (app-image)"
jpackage --type app-image "${COMMUN[@]}"
tar -C target/dist -czf "target/dist/CentraleEtudiant-$VERSION-linux-x64.tar.gz" CentraleEtudiant

if command -v dpkg-deb >/dev/null 2>&1 && command -v fakeroot >/dev/null 2>&1; then
  echo "==> Paquet .deb"
  jpackage --type deb "${COMMUN[@]}" \
    --linux-package-name centrale-etudiant \
    --linux-shortcut \
    --linux-menu-group Education \
    --linux-app-category education
else
  echo "==> .deb ignoré (dpkg-deb ou fakeroot absent)"
fi

echo "==> Terminé :"
ls -lh target/dist/*.tar.gz target/dist/*.deb 2>/dev/null || true
