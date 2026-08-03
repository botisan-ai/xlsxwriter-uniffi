#!/bin/bash

set -ex

BUILD_DIR="./build"
LIB_BASENAME="xlsxwriter"

XCFRAMEWORK_NAME="lib${LIB_BASENAME}-rs"
XCFRAMEWORK_DIR="${BUILD_DIR}/${XCFRAMEWORK_NAME}.xcframework"
XCFRAMEWORK_ZIP="${XCFRAMEWORK_DIR}.zip"
ANDROID_DISTRIBUTIONS_DIR="./android/build/distributions"

metadata=$(cargo metadata --format-version 1 --no-deps)
pkg_id=$(jq -r '.workspace_members[0]' <<<"$metadata")
version=$(jq -r --arg pkg_id "$pkg_id" '.packages[] | select(.id==$pkg_id) .version' <<<"$metadata")
if [ -z "$version" ]; then
  echo "Could not parse the package version from Cargo.toml" >&2
  exit 1
fi

swift_release_tag=$(sed -nE 's/^[[:space:]]*let releaseTag = "([^"]+)".*/\1/p' ./Package.swift | head -n 1)
if [ "$swift_release_tag" != "$version" ]; then
  echo "Package.swift is still at $swift_release_tag; run ./build-ios.sh before releasing $version" >&2
  exit 1
fi

ANDROID_ZIP="${ANDROID_DISTRIBUTIONS_DIR}/xlsxwriter-android-${version}-maven.zip"
ANDROID_ZIP_CHECKSUM="${ANDROID_ZIP}.sha256"
ANDROID_AAR="${ANDROID_DISTRIBUTIONS_DIR}/xlsxwriter-android-${version}.aar"
ANDROID_AAR_CHECKSUM="${ANDROID_AAR}.sha256"

for artifact in "$XCFRAMEWORK_ZIP" "$ANDROID_ZIP" "$ANDROID_ZIP_CHECKSUM" "$ANDROID_AAR" "$ANDROID_AAR_CHECKSUM"; do
  if [ ! -f "$artifact" ]; then
    echo "Missing release artifact: $artifact" >&2
    exit 1
  fi
done

gh release create "$version" --generate-notes
gh release upload "$version" "$XCFRAMEWORK_ZIP" "$ANDROID_ZIP" "$ANDROID_ZIP_CHECKSUM" "$ANDROID_AAR" "$ANDROID_AAR_CHECKSUM" --clobber
