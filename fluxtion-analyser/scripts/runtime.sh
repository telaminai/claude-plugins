#!/bin/sh
# Shared bootstrap environment for both plugin commands; setup policy lives in Java.
[ "$(uname -s)" = Darwin ] || { echo "Automatic setup is currently limited to macOS; use the manual route." >&2; exit 2; }
: "${HOME:?HOME is required}"
export JBANG_DIR="${JBANG_DIR:-$HOME/.jbang}"
export JBANG_CACHE_DIR="${JBANG_CACHE_DIR:-$JBANG_DIR/cache}"
# JBang, the helper and the Analyser share one JDK: JBang's own default (17) would add a second download.
export JBANG_DEFAULT_JAVA_VERSION="${JBANG_DEFAULT_JAVA_VERSION:-21}"
# the same scoped PATH is used by both plan and apply; no profile edits.
if ! command -v claude >/dev/null 2>&1 && [ -x "$HOME/.local/bin/claude" ]; then
  export PATH="$HOME/.local/bin:$PATH"
fi
if [ -x "$JBANG_DIR/bin/jbang" ]; then
  analyser_jbang="$JBANG_DIR/bin/jbang"
else
  analyser_jbang="$(command -v jbang || :)"
  if [ -z "$analyser_jbang" ]; then
    for candidate in /opt/homebrew/bin/jbang /usr/local/bin/jbang; do
      if [ -x "$candidate" ]; then analyser_jbang=$candidate; break; fi
    done
  fi
fi
[ -n "$analyser_jbang" ] && [ -x "$analyser_jbang" ] || {
  echo "JBang is not installed; the setup skill installs it with its bootstrap step." >&2; exit 2;
}
export PATH="$(dirname "$analyser_jbang"):$JBANG_DIR/bin:$PATH"
# macOS Java does not derive user.home from HOME. Keep the selected home explicit.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }\"-Duser.home=$HOME\""
