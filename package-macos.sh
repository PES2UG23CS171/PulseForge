#!/bin/zsh
set -euo pipefail

PROJECT_DIR=${0:A:h}
BUILD_DIR="$PROJECT_DIR/build"
APP_NAME="T&T Pro"
ICONSET_DIR="$BUILD_DIR/tt-pro.iconset"
PACKAGE_INPUT="$BUILD_DIR/package-input"
OUTPUT_DIR="$PROJECT_DIR/dist"

cd "$PROJECT_DIR"
mvn clean package

mkdir -p "$BUILD_DIR" "$ICONSET_DIR" "$PACKAGE_INPUT"
java -Djava.awt.headless=true -cp target/classes app.tonetempo.ui.AppIcon "$BUILD_DIR/icon-1024.png"

for spec in "16:icon_16x16.png" "32:icon_16x16@2x.png" "32:icon_32x32.png" \
            "64:icon_32x32@2x.png" "128:icon_128x128.png" "256:icon_128x128@2x.png" \
            "256:icon_256x256.png" "512:icon_256x256@2x.png" "512:icon_512x512.png" \
            "1024:icon_512x512@2x.png"; do
    pixels=${spec%%:*}
    filename=${spec#*:}
    sips -z "$pixels" "$pixels" "$BUILD_DIR/icon-1024.png" --out "$ICONSET_DIR/$filename" >/dev/null
done
iconutil -c icns "$ICONSET_DIR" -o "$BUILD_DIR/tt-pro.icns"

rm -f "$PACKAGE_INPUT"/*.jar
cp target/tt-pro.jar "$PACKAGE_INPUT/tt-pro.jar"
mkdir -p "$OUTPUT_DIR"
if [[ -d "$OUTPUT_DIR/$APP_NAME.app" ]]; then
    BACKUP_NAME="$APP_NAME.$(date +%Y%m%d-%H%M%S).app"
    mv "$OUTPUT_DIR/$APP_NAME.app" "$OUTPUT_DIR/$BACKUP_NAME"
fi

jpackage \
    --type app-image \
    --name "$APP_NAME" \
    --app-version 1.5.0 \
    --vendor "Tone & Tempo" \
    --mac-package-identifier app.tonetempo.pro \
    --input "$PACKAGE_INPUT" \
    --main-jar tt-pro.jar \
    --main-class app.tonetempo.ToneTempoApplication \
    --icon "$BUILD_DIR/tt-pro.icns" \
    --dest "$OUTPUT_DIR" \
    --java-options "-Dapple.laf.useScreenMenuBar=true"
# The application name for the menu bar is set in ToneTempoApplication; jpackage would split it at the space.

# jpackage writes the ampersand of "T&T Pro" into Info.plist unescaped; make the plist valid XML again.
PLIST="$OUTPUT_DIR/$APP_NAME.app/Contents/Info.plist"
sed -i '' -e 's/&amp;/\&/g' -e 's/&/\&amp;/g' "$PLIST"
plutil -lint "$PLIST" >/dev/null

# Explain the microphone request (the tuner listens only while it is showing), then re-seal the bundle.
/usr/libexec/PlistBuddy -c "Delete :NSMicrophoneUsageDescription" "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c "Add :NSMicrophoneUsageDescription string 'T&T Pro listens to your guitar so the tuner can show which string you are playing and how far it is from pitch.'" "$PLIST"
codesign --force --sign - "$OUTPUT_DIR/$APP_NAME.app"

echo "$OUTPUT_DIR/$APP_NAME.app"
