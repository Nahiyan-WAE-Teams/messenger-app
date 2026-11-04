#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")"
pkg update -y
pkg install nodejs-lts git -y
npm install
echo "Setup complete. Start Mira with: npm run offline"
