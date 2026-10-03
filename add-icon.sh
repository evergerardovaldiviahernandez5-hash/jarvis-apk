#!/data/data/com.termux/files/usr/bin/bash
# =========================================================
# add-icon.sh — Icono personalizado para la app y el APK
#   · Clona el repo si no existe
#   · Reemplaza icons/icon.png + icon-192.png
#   · Actualiza manifest.json
#   · Bump del Service Worker
#   · Regenera .github/workflows/build-apk.yml
#   · Commit listo para push
# =========================================================
set -u

REPO="https://github.com/evergerardovaldiviahernandez5-hash/analizador-funciones.git"
WORK="$HOME/analizadordefunciones"
IMG="${1:-$HOME/storage/downloads/icono.png}"

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║   ADD ICON — Icono custom para app y APK             ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# ---------- 0. Imagen ----------
if [ ! -f "$IMG" ]; then
    echo "❌ Imagen no encontrada: $IMG"
    echo ""
    echo "   Guardala como ~/storage/downloads/icono.png"
    echo "   O pasala como argumento:"
    echo "     bash add-icon.sh /ruta/a/tu/imagen.png"
    exit 1
fi
echo "  ✓ Imagen: $IMG"
ls -lh "$IMG" | awk '{print "    Tamaño:", $5}'

# ---------- 1. Herramientas ----------
echo ""
echo "═══ 1. Herramientas ═══"
NEED=()
command -v git     >/dev/null 2>&1 || NEED+=(git)
command -v convert >/dev/null 2>&1 || NEED+=(imagemagick)
command -v python3 >/dev/null 2>&1 || NEED+=(python)

if [ ${#NEED[@]} -gt 0 ]; then
    echo "  Instalando: ${NEED[*]} (puede tardar)"
    pkg install -y "${NEED[@]}" >/dev/null 2>&1
fi
echo "  ✓ git, convert, python3 listos"

# ---------- 2. Repo ----------
echo ""
echo "═══ 2. Repo ═══"
if [ -d "$WORK/.git" ]; then
    cd "$WORK"
    git fetch origin main >/dev/null 2>&1
    git reset --hard origin/main >/dev/null 2>&1
    echo "  ✓ Actualizado a origin/main"
else
    rm -rf "$WORK"
    git clone "$REPO" "$WORK" >/dev/null 2>&1
    cd "$WORK"
    echo "  ✓ Clonado desde $REPO"
fi
git config --global --add safe.directory "$WORK" 2>/dev/null || true

# ---------- 3. Iconos PWA ----------
echo ""
echo "═══ 3. Iconos PWA ═══"
mkdir -p icons
[ -f icons/icon.png ] && cp icons/icon.png "icons/icon.png.bak-$(date +%H%M%S)"

convert "$IMG" -resize 512x512^ -gravity center \
        -extent 512x512 "icons/icon.png"
convert "icons/icon.png" -resize 192x192 "icons/icon-192.png"
echo "  ✓ icons/icon.png     (512×512)"
echo "  ✓ icons/icon-192.png (192×192)"

# ---------- 4. Manifest ----------
echo ""
echo "═══ 4. manifest.json ═══"
cp manifest.json "manifest.json.bak-$(date +%H%M%S)"
python3 - <<'PYEOF'
import json
p = 'manifest.json'
m = json.load(open(p, encoding='utf-8'))
m['icons'] = [
    {"src": "icons/icon-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any"},
    {"src": "icons/icon.png",     "sizes": "512x512", "type": "image/png", "purpose": "any maskable"}
]
open(p, 'w', encoding='utf-8').write(json.dumps(m, indent=2, ensure_ascii=False) + '\n')
print('  ✓ manifest.json actualizado')
PYEOF

# ---------- 5. Service Worker ----------
echo ""
echo "═══ 5. Service Worker ═══"
cp service-worker.js "service-worker.js.bak-$(date +%H%M%S)"
python3 - <<'PYEOF'
import re
p = 'service-worker.js'
s = open(p, encoding='utf-8').read()

m = re.search(r"const CACHE = 'af-cache-v(\d+)-(\d+)';", s)
if m:
    major = int(m.group(1)); minor = int(m.group(2)) + 1
    old = m.group(0)
    new = f"const CACHE = 'af-cache-v{major}-{minor}';"
    s = s.replace(old, new, 1)
    print(f'  ✓ cache bumped: {old} → {new}')

if "'./icons/icon-192.png'" not in s:
    s = s.replace(
        "  './icons/icon.png',",
        "  './icons/icon.png',\n  './icons/icon-192.png',",
        1
    )
    print('  ✓ icon-192.png agregado al PRECACHE')

open(p, 'w', encoding='utf-8').write(s)
PYEOF

# ---------- 6. Workflow regenerado ----------
echo ""
echo "═══ 6. Workflow ═══"
WF=".github/workflows/build-apk.yml"
mkdir -p .github/workflows
[ -f "$WF" ] && cp "$WF" "$WF.bak-$(date +%H%M%S)"

cat > "$WF" <<'YAMLEOF'
name: Build APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Install ImageMagick
        run: sudo apt-get update && sudo apt-get install -y imagemagick

      - name: Install dependencies
        run: npm install --no-audit --no-fund

      - name: Build www/
        run: |
          rm -rf www
          mkdir -p www
          for item in index.html manifest.json service-worker.js css js icons; do
            [ -e "$item" ] && cp -r "$item" www/
          done
          for f in LICENSE README.md; do
            [ -f "$f" ] && cp "$f" www/
          done
          rm -f www/test*.html www/test*.js
          echo "www/ listo:"; ls www/

      - name: Add Android platform
        run: |
          if [ ! -d android ]; then
            npx cap add android
          fi

      - name: Sync Capacitor
        run: npx cap sync android

      - name: Replace Android launcher icons
        run: |
          SRC=icons/icon.png
          test -f "$SRC" || { echo "❌ $SRC no existe"; exit 1; }

          # Borrar adaptive icons (XML) para forzar uso del PNG legacy.
          # Android 8+ prefiere el XML si existe; al no existir usa el PNG.
          rm -rf android/app/src/main/res/mipmap-anydpi-v26

          declare -A SIZES=( [mdpi]=48 [hdpi]=72 [xhdpi]=96 [xxhdpi]=144 [xxxhdpi]=192 )
          for d in "${!SIZES[@]}"; do
            s=${SIZES[$d]}
            dir="android/app/src/main/res/mipmap-$d"
            test -d "$dir" || continue
            convert "$SRC" -resize ${s}x${s} "$dir/ic_launcher.png"
            convert "$SRC" -resize ${s}x${s} "$dir/ic_launcher_round.png"
            echo "  ✓ mipmap-$d: ${s}x${s}"
          done

      - name: Grant execute permission for gradlew
        run: chmod +x android/gradlew

      - name: Build Debug APK
        working-directory: android
        run: ./gradlew assembleDebug --no-daemon

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: analizador-debug-apk
          path: android/app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30

      - name: Show APK size
        run: ls -lh android/app/build/outputs/apk/debug/app-debug.apk
YAMLEOF
echo "  ✓ Workflow regenerado"

# ---------- 7. Verificación ----------
echo ""
echo "═══ 7. Verificación ═══"
command -v node >/dev/null 2>&1 && node --check service-worker.js && echo "  ✓ service-worker.js OK"

# ---------- 8. Commit ----------
echo ""
echo "═══ 8. Commit ═══"
git add icons/ manifest.json service-worker.js .github/

echo ""
echo "  Archivos a commitear:"
git status --short

if git diff --cached --quiet; then
    echo ""
    echo "  ⚠  Nada cambió"
else
    git commit -m "Icono personalizado para app y APK" >/dev/null 2>&1
    echo ""
    echo "  ✓ Commit hecho"
fi

# ---------- Resumen ----------
echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║  ✅ LISTO                                                ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "  ▶ Push ahora:"
echo ""
echo "      cd $WORK"
echo "      git push"
echo ""
echo "  ▶ Después:"
echo "      1. Esperá ~3-5 min (la primera build tarda por ImageMagick)"
echo "      2. Andá a:"
echo "         https://github.com/evergerardovaldiviahernandez5-hash/analizador-funciones/actions"
echo "      3. Cuando termine (✅ verde) → bajá el APK desde 'Artifacts'"
echo "      4. El artifact se llama: analizador-debug-apk"
echo ""
echo "  ▶ Para la PWA en GitHub Pages:"
echo "      · Esperá 2-3 min a que re-deploye"
echo "      · Recargá 2 veces el navegador para ver el icono nuevo"
echo ""
