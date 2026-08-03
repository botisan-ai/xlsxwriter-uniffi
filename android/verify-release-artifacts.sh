#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 3 ]]; then
  echo "Usage: $0 <maven-repository> <version> <consumer-apk>" >&2
  exit 2
fi

maven_repository="$1"
version="$2"
consumer_apk="$3"
module_directory="${maven_repository}/ai/botisan/xlsxwriter-android/${version}"
aar="${module_directory}/xlsxwriter-android-${version}.aar"
pom="${module_directory}/xlsxwriter-android-${version}.pom"
module_metadata="${module_directory}/xlsxwriter-android-${version}.module"

for artifact in "$aar" "$pom" "$module_metadata" "$consumer_apk"; do
  if [[ ! -f "$artifact" ]]; then
    echo "Missing release artifact: $artifact" >&2
    exit 1
  fi
done

jna_dependency=$(awk '
  /<dependency>/ { block = "" }
  { block = block $0 "\n" }
  /<\/dependency>/ && block ~ /<artifactId>jna<\/artifactId>/ { print block }
' "$pom")

if [[ "$jna_dependency" != *"<type>aar</type>"* ]]; then
  echo "Published POM does not retain JNA as an AAR dependency" >&2
  exit 1
fi

if ! rg -q '"module": "jna"' "$module_metadata"; then
  echo "Gradle Module Metadata does not include JNA" >&2
  exit 1
fi

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/xlsxwriter-release.XXXXXX")
cleanup() {
  rm -rf "$temporary_directory"
}
trap cleanup EXIT

mkdir -p "$temporary_directory/aar"
unzip -q "$aar" -d "$temporary_directory/aar"

abis=(arm64-v8a armeabi-v7a x86 x86_64)
for abi in "${abis[@]}"; do
  library="$temporary_directory/aar/jni/$abi/libxlsxwriter.so"
  if [[ ! -f "$library" ]]; then
    echo "AAR is missing $abi/libxlsxwriter.so" >&2
    exit 1
  fi
done

ndk_root="${ANDROID_NDK_HOME:-}"
if [[ -z "$ndk_root" && -n "${ANDROID_HOME:-}" ]]; then
  for candidate in "$ANDROID_HOME"/ndk/*; do
    ndk_root="$candidate"
  done
fi

readelf=""
for candidate in "$ndk_root"/toolchains/llvm/prebuilt/*/bin/llvm-readelf; do
  if [[ -x "$candidate" ]]; then
    readelf="$candidate"
    break
  fi
done

if [[ -z "$readelf" ]]; then
  echo "Could not locate llvm-readelf; set ANDROID_NDK_HOME" >&2
  exit 1
fi

for abi in arm64-v8a x86_64; do
  library="$temporary_directory/aar/jni/$abi/libxlsxwriter.so"
  while read -r alignment; do
    if (( alignment < 0x4000 )); then
      echo "$abi/libxlsxwriter.so has PT_LOAD alignment $alignment" >&2
      exit 1
    fi
  done < <("$readelf" -lW "$library" | awk '$1 == "LOAD" { print $NF }')
done

unzip -Z1 "$consumer_apk" > "$temporary_directory/apk-entries.txt"
for abi in "${abis[@]}"; do
  if ! rg -qx "lib/$abi/libxlsxwriter.so" "$temporary_directory/apk-entries.txt"; then
    echo "Consumer APK is missing $abi/libxlsxwriter.so" >&2
    exit 1
  fi
  if ! rg -qx "lib/$abi/libjnidispatch.so" "$temporary_directory/apk-entries.txt"; then
    echo "Consumer APK is missing transitive JNA for $abi" >&2
    exit 1
  fi
done

zipalign=""
if [[ -n "${ANDROID_HOME:-}" ]]; then
  for candidate in "$ANDROID_HOME"/build-tools/*/zipalign; do
    if [[ -x "$candidate" ]]; then
      zipalign="$candidate"
    fi
  done
fi

if [[ -z "$zipalign" ]]; then
  echo "Could not locate zipalign; set ANDROID_HOME" >&2
  exit 1
fi

"$zipalign" -c -P 16 4 "$consumer_apk" >/dev/null
echo "Verified Maven metadata, four ABIs, 16 KiB ELF alignment, and consumer APK alignment."
