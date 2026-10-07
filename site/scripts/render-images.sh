#!/bin/sh
# Renders the raster assets that cannot be plain files: OG image, Apple touch icon and favicon from the
# HTML sources in site/og/ (headless Chrome), and the WhatsApp QR code (npm "qrcode", fetched on demand).
# Run from site/: `npm run images`. Needs network (Google Fonts, npm).
set -eu
cd "$(dirname "$0")/.."
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
render() { # $1 source html, $2 output png, $3 width, $4 height
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --allow-file-access-from-files --virtual-time-budget=5000 --default-background-color=00000000 \
    --window-size="$3,$4" --screenshot="$PWD/$2" "file://$PWD/$1" >/dev/null 2>&1
  echo "wrote $2"
}
render og/og-image.html public/og-image.png 1200 630
render og/apple-touch-icon.html public/apple-touch-icon.png 180 180
render og/favicon.html public/favicon.png 64 64
# The QR opens the same link as every "מתחילים בוואטסאפ" button.
WA='https://wa.me/972552961164?text=%D7%94%D7%99%D7%99%2C%20%D7%90%D7%A0%D7%99%20%D7%A8%D7%95%D7%A6%D7%94%20%D7%9C%D7%94%D7%AA%D7%97%D7%99%D7%9C'
QR_TMP="$(mktemp -d)"
npm install --silent --no-save --prefix "$QR_TMP" qrcode@1.5.4 >/dev/null
WA="$WA" NODE_PATH="$QR_TMP/node_modules" node -e "require('qrcode').toFile('public/img/wa-qr.svg', process.env.WA, { type: 'svg', errorCorrectionLevel: 'M', margin: 1, color: { dark: '#101C32', light: '#FFFFFF' } }, (e) => { if (e) { console.error(e); process.exit(1); } })"
rm -rf "$QR_TMP"
echo "wrote public/img/wa-qr.svg"
