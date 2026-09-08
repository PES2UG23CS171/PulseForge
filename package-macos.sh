#!/bin/zsh
set -euo pipefail

PROJECT_DIR=${0:A:h}
BUILD_DIR="$PROJECT_DIR/build"
ICONSET_DIR="$BUILD_DIR/PulseForge.iconset"
PACKAGE_INPUT="$BUILD_DIR/package-input"
OUTPUT_DIR="$PROJECT_DIR/dist"

cd "$PROJECT_DIR"
mvn package

mkdir -p "$BUILD_DIR" "$ICONSET_DIR" "$PACKAGE_INPUT"
java -Djava.awt.headless=true -cp target/classes app.pulseforge.ui.AppIcon "$BUILD_DIR/icon-1024.png"

for spec in "16:icon_16x16.png" "32:icon_16x16@2x.png" "32:icon_32x32.png" \
            "64:icon_32x32@2x.png" "128:icon_128x128.png" "256:icon_128x128@2x.png" \
            "256:icon_256x256.png" "512:icon_256x256@2x.png" "512:icon_512x512.png" \
            "1024:icon_512x512@2x.png"; do
    pixels=${spec%%:*}
    filename=${spec#*:}
    sips -z "$pixels" "$pixels" "$BUILD_DIR/icon-1024.png" --out "$ICONSET_DIR/$filename" >/dev/null
done
iconutil -c icns "$ICONSET_DIR" -o "$BUILD_DIR/PulseForge.icns"

cp target/pulseforge.jar "$PACKAGE_INPUT/pulseforge.jar"
mkdir -p "$OUTPUT_DIR"
if [[ -d "$OUTPUT_DIR/PulseForge.app" ]]; then
    BACKUP_NAME="PulseForge.$(date +%Y%m%d-%H%M%S).app"
    mv "$OUTPUT_DIR/PulseForge.app" "$OUTPUT_DIR/$BACKUP_NAME"
fi

jpackage \
    --type app-image \
    --name PulseForge \
    --app-version 1.0.0 \
    --vendor PulseForge \
    --mac-package-identifier app.pulseforge.metronome \
    --input "$PACKAGE_INPUT" \
    --main-jar pulseforge.jar \
    --main-class app.pulseforge.PulseForgeApplication \
    --icon "$BUILD_DIR/PulseForge.icns" \
    --dest "$OUTPUT_DIR" \
    --java-options "-Dapple.awt.application.name=PulseForge" \
    --java-options "-Dapple.laf.useScreenMenuBar=true"

echo "$OUTPUT_DIR/PulseForge.app"
