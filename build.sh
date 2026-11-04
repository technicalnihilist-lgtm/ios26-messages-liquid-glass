#!/usr/bin/env bash
# Build + sign the Liquid Glass Messages APK without Gradle.
#   export JAVA_HOME=/path/to/jdk-17
#   export ANDROID_SDK=/path/to/android-sdk   (build-tools/34.0.0 + platforms/android-34)
set -euo pipefail

: "${JAVA_HOME:?set JAVA_HOME to a JDK 17}"
: "${ANDROID_SDK:?set ANDROID_SDK to your Android SDK root}"

BT="$ANDROID_SDK/build-tools/34.0.0"
AJAR="$ANDROID_SDK/platforms/android-34/android.jar"
ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app"
OUT="$ROOT/build"
KS="$ROOT/release.keystore"

export PATH="$JAVA_HOME/bin:$PATH"
rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"
cd "$APP"

# the manifest is the single source of truth for the version
VC="$(sed -n 's/.*android:versionCode="\([0-9]*\)".*/\1/p' AndroidManifest.xml | head -1)"
VN="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)"
: "${VC:?could not read android:versionCode from AndroidManifest.xml}"
: "${VN:?could not read android:versionName from AndroidManifest.xml}"

echo "==> aapt2 compile";  "$BT/aapt2" compile --dir res -o "$OUT/res.zip"
echo "==> aapt2 link (v$VN / code $VC)"; "$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$AJAR" \
    --manifest AndroidManifest.xml -A assets --java "$OUT/gen" \
    --min-sdk-version 24 --target-sdk-version 34 \
    --version-code "$VC" --version-name "$VN" "$OUT/res.zip"
echo "==> javac";          javac -nowarn -source 8 -target 8 \
    -bootclasspath "$AJAR" -classpath "$AJAR" -d "$OUT/classes" \
    "$OUT/gen/com/liquidglass/messages/R.java" \
    $(find src -name "*.java")
echo "==> d8";             "$BT/d8" --release --min-api 24 --lib "$AJAR" \
    --output "$OUT/dex" $(find "$OUT/classes" -name "*.class")
echo "==> package";        (cd "$OUT/dex" && zip -q -X "$OUT/unsigned.apk" classes.dex)
echo "==> zipalign";       "$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KS" ]; then
  echo "==> keystore (dev key - replace before publishing)"
  keytool -genkeypair -v -keystore "$KS" -alias liquidglass -keyalg RSA \
    -keysize 2048 -validity 10000 -storepass liquidglass -keypass liquidglass \
    -dname "CN=Liquid Glass Messages, O=Open Source, C=IN" >/dev/null 2>&1
fi

echo "==> sign"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:liquidglass --key-pass pass:liquidglass \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT/Messages-LiquidGlass.apk" "$OUT/aligned.apk"
mkdir -p "$ROOT/apk"
cp "$OUT/Messages-LiquidGlass.apk" "$ROOT/apk/Messages-LiquidGlass.apk"
echo "==> done: $OUT/Messages-LiquidGlass.apk (also copied to apk/)"
