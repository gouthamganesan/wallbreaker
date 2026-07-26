#!/bin/bash
# SessionStart hook — prepares a Claude Code on the web container to build and
# test Wallbreaker.
#
# The catch with this repo is that it is an Android app, and Android tooling
# lives behind dl.google.com (which also backs maven.google.com). If the
# session's egress policy blocks that host, the *entire* Android chain is
# unreachable: the Android Gradle Plugin, the SDK, AndroidX, Compose, Room,
# WorkManager. Nothing under :app can configure, let alone compile.
#
# So this hook warms the one thing that always works — the standalone JVM build
# in verify/, which compiles the app's Android-free sources and runs the real
# unit suite off Maven Central alone — and then reports honestly whether the
# full Android build is available in this particular container.
set -euo pipefail

# Web sessions only; a local checkout already has Android Studio's SDK.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"

echo "==> Warming the JVM verification build (Gradle dist, Kotlin plugin, JUnit)"
./gradlew -p verify --console=plain testClasses

# Probe the Android tooling host rather than assuming. A 000/403 here means the
# egress policy denied the CONNECT; don't retry or route around it.
echo "==> Probing Android tooling reachability (dl.google.com)"
google_status="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 \
  https://dl.google.com/android/repository/repository2-3.xml 2>/dev/null)" || true
google_status="${google_status:-000}"

if [ "$google_status" = "200" ]; then
  echo "    dl.google.com reachable (HTTP $google_status)."
  if [ -n "${ANDROID_HOME:-}" ] || [ -d "$HOME/Android/Sdk" ]; then
    echo "    Android SDK present — ./gradlew :app:testDebugUnitTest should work."
  else
    echo "    No Android SDK installed in this container; :app tasks need one"
    echo "    (cmdline-tools + platform 36 + build-tools) before they can run."
  fi
else
  echo "    dl.google.com NOT reachable (HTTP $google_status) — blocked by this"
  echo "    session's egress policy. :app tasks cannot resolve the Android"
  echo "    Gradle Plugin and will fail at configuration time. This is a network"
  echo "    policy limit, not a repo problem."
  echo "    Use: ./gradlew -p verify test   (18 unit tests, no Android SDK needed)"
fi

echo "==> Ready. Unit suite: ./gradlew -p verify test"
