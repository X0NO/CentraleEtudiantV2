# Construit Centrale Étudiant pour Windows, avec Java inclus (rien à installer pour l'utilisateur).
#
# Produit dans target\dist\ :
#   - CentraleEtudiant-<version>-windows-x64.zip : dossier portable (décompresser, lancer CentraleEtudiant.exe)
#   - CentraleEtudiant-<version>.msi             : installateur (seulement si WiX Toolset est installé)
#
# Utilisation (PowerShell) : .\packaging\construire-windows.ps1 [-Version 1.2.0]
# Prérequis : un JDK 17 ou plus récent (avec jpackage) et Maven. WiX Toolset en plus pour le .msi.
# jpackage ne sait pas créer un paquet Windows depuis Linux : ce script doit tourner sous Windows
# (ou via GitHub Actions, voir .github/workflows/paquets.yml).
param([string]$Version = $(if ($env:VERSION) { $env:VERSION } else { "1.0.0" }))
$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")
$Version = $Version.TrimStart("v")

Write-Host "==> Compilation (mvn package)"
mvn -q -B -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "mvn package a échoué" }

Write-Host "==> Préparation"
Remove-Item -Recurse -Force target\jpackage-input, target\dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force target\jpackage-input, target\dist | Out-Null
Copy-Item target\centrale-etudiant.jar target\jpackage-input\

# Modules Java réellement utilisés (Java embarqué plus léger)
$modules = (jdeps --multi-release base --ignore-missing-deps --print-module-deps target\centrale-etudiant.jar).Trim()
$modules += ",jdk.localedata,jdk.charsets,java.naming"
if (java --list-modules | Select-String -Quiet "^jdk.crypto.ec@") { $modules += ",jdk.crypto.ec" }
Write-Host "    modules : $modules"

$commun = @(
  "--name", "CentraleEtudiant",
  "--app-version", $Version,
  "--vendor", "Centrale Etudiant",
  "--description", "Notes, emploi du temps, devoirs et mails de l'universite",
  "--input", "target\jpackage-input",
  "--main-jar", "centrale-etudiant.jar",
  "--main-class", "com.centrale.Main",
  "--add-modules", $modules,
  "--java-options", "--enable-native-access=ALL-UNNAMED",
  "--icon", "packaging\icone.ico",
  "--dest", "target\dist"
)

Write-Host "==> Appli portable (app-image)"
jpackage --type app-image @commun
if ($LASTEXITCODE -ne 0) { throw "jpackage (app-image) a échoué" }
Compress-Archive -Path target\dist\CentraleEtudiant -DestinationPath "target\dist\CentraleEtudiant-$Version-windows-x64.zip" -Force

$wix = (Get-Command candle.exe -ErrorAction SilentlyContinue) -or (Get-Command wix.exe -ErrorAction SilentlyContinue)
if ($wix) {
  Write-Host "==> Installateur .msi"
  # --win-per-user-install : installation sans droits administrateur (dans le profil de l'utilisateur)
  jpackage --type msi @commun `
    --win-menu --win-menu-group "Centrale Etudiant" --win-shortcut --win-shortcut-prompt `
    --win-per-user-install --win-dir-chooser `
    --win-upgrade-uuid "6f1c2b1e-3c7a-4d5e-9b8a-2f4e6d8c0a11"
  if ($LASTEXITCODE -ne 0) { throw "jpackage (msi) a échoué" }
} else {
  Write-Host "==> .msi ignoré (WiX Toolset absent : https://wixtoolset.org)"
}

Write-Host "==> Terminé :"
Get-ChildItem target\dist -File
