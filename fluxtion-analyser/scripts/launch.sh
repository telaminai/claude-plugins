#!/bin/sh
set -eu
analyser_plugin_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
. "$analyser_plugin_root/scripts/runtime.sh"
if [ ! -f "$HOME/.fluxtion-analyser/setup-plugin-java" ]; then
  echo "Analyser is not ready; run /fluxtion-analyser:setup." >&2
  exit 2
fi
if [ ! -f "$HOME/.fluxtion-analyser/rest-endpoint" ]; then
  echo "Open the Analyser with /fluxtion-analyser:setup, then /mcp reconnect plugin:fluxtion-analyser:fluxtion-analyser and confirm with native analyser_context. No downloads attempted." >&2
  exit 2
fi
IFS= read -r analyser_java < "$HOME/.fluxtion-analyser/setup-plugin-java"
case "$analyser_java" in /*/bin/java) ;; *) echo "Invalid installed Java path; run /fluxtion-analyser:setup." >&2; exit 2 ;; esac
[ -x "$analyser_java" ] || { echo "Installed Java is missing; run /fluxtion-analyser:setup." >&2; exit 2; }
# Automatic bridge launch must not inherit JVM agents/options or another selected Java home.
unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS JBANG_JAVA_OPTIONS
# Source mode uses the installed JVM with no external dependencies; never invoke JBang's bootstrap here.
exec "$analyser_java" "-Duser.home=$HOME" "$analyser_plugin_root/scripts/AnalyserSetup.java" bridge --program "$analyser_plugin_root/scripts/AnalyserSetup.java"
