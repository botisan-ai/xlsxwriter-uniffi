#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 4 ]]; then
  echo "Usage: $0 <maven-repository> <version> <consumer-apk> <distribution-directory>" >&2
  exit 2
fi

maven_repository="$1"
version="$2"
consumer_apk="$3"
distribution_directory="$4"
release_zip="${distribution_directory}/xlsxwriter-android-${version}-maven.zip"
release_zip_checksum="${release_zip}.sha256"
release_aar="${distribution_directory}/xlsxwriter-android-${version}.aar"
release_aar_checksum="${release_aar}.sha256"
module_directory="${maven_repository}/ai/botisan/xlsxwriter-android/${version}"
aar="${module_directory}/xlsxwriter-android-${version}.aar"
pom="${module_directory}/xlsxwriter-android-${version}.pom"
module_metadata="${module_directory}/xlsxwriter-android-${version}.module"

for artifact in "$aar" "$pom" "$module_metadata" "$consumer_apk" "$release_zip" "$release_zip_checksum" "$release_aar" "$release_aar_checksum"; do
  if [[ ! -f "$artifact" ]]; then
    echo "Missing release artifact: $artifact" >&2
    exit 1
  fi
done

verify_checksum() {
  local artifact="$1"
  local checksum_file="$2"
  local expected_checksum
  local actual_checksum

  expected_checksum=$(awk '{ print $1 }' "$checksum_file")
  if command -v sha256sum >/dev/null 2>&1; then
    actual_checksum=$(sha256sum "$artifact" | awk '{ print $1 }')
  else
    actual_checksum=$(shasum -a 256 "$artifact" | awk '{ print $1 }')
  fi

  if [[ "$actual_checksum" != "$expected_checksum" ]]; then
    echo "$(basename "$artifact") checksum does not match its sidecar" >&2
    exit 1
  fi
}

verify_checksum "$release_zip" "$release_zip_checksum"
verify_checksum "$release_aar" "$release_aar_checksum"

if ! cmp -s "$aar" "$release_aar"; then
  echo "Standalone release AAR differs from the Maven repository AAR" >&2
  exit 1
fi

jna_dependency=$(awk '
  /<dependency>/ { block = "" }
  { block = block $0 "\n" }
  /<\/dependency>/ && block ~ /<artifactId>jna<\/artifactId>/ { print block }
' "$pom")

if [[ "$jna_dependency" != *"<version>5.19.1</version>"* || "$jna_dependency" != *"<type>aar</type>"* ]]; then
  echo "Published POM does not retain JNA 5.19.1 as an AAR dependency" >&2
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

abis=(arm64-v8a x86_64)
actual_abis=$(find "$temporary_directory/aar/jni" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort)
expected_abis=$(printf '%s\n' "${abis[@]}" | sort)
if [[ "$actual_abis" != "$expected_abis" ]]; then
  echo "AAR ABIs differ from the supported 64-bit set" >&2
  exit 1
fi

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

verify_elf_alignment() {
  local label="$1"
  local library="$2"
  local has_load_segment=false

  while read -r alignment; do
    has_load_segment=true
    if (( alignment < 0x4000 )); then
      echo "$label has PT_LOAD alignment $alignment" >&2
      exit 1
    fi
  done < <("$readelf" -lW "$library" | awk '$1 == "LOAD" { print $NF }')

  if [[ "$has_load_segment" == false ]]; then
    echo "$label has no PT_LOAD segments" >&2
    exit 1
  fi
}

for abi in "${abis[@]}"; do
  library="$temporary_directory/aar/jni/$abi/libxlsxwriter.so"
  verify_elf_alignment "$abi/libxlsxwriter.so" "$library"
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

apk_xlsxwriter_abis=$(sed -nE 's#^lib/([^/]+)/libxlsxwriter\.so$#\1#p' "$temporary_directory/apk-entries.txt" | sort)
if [[ "$apk_xlsxwriter_abis" != "$expected_abis" ]]; then
  echo "Consumer APK ABIs differ from the supported 64-bit set" >&2
  exit 1
fi

mkdir -p "$temporary_directory/apk"
unzip -q "$consumer_apk" -d "$temporary_directory/apk"
for abi in "${abis[@]}"; do
  for library_name in libxlsxwriter.so libjnidispatch.so; do
    library="$temporary_directory/apk/lib/$abi/$library_name"
    verify_elf_alignment "$abi/$library_name" "$library"
  done
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
echo "Verified Maven metadata, release checksums, two 64-bit ABIs, 16 KiB ELF alignment, and consumer APK alignment."
