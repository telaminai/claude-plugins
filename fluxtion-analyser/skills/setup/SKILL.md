---
name: setup
description: Set up, install or connect the Fluxtion Analyser to Claude Code on macOS, including optional compiler sign-in. Use for “set up the Fluxtion Analyser”.
---

# Set up the Fluxtion Analyser

**macOS automatic setup.** Pinned Analyser **1.40.5**; automatic route: **macOS and Claude Code**.
This plugin bundles the same setup program as the URL route. No helper download or source checksum step.
The manual app-UI route remains in [REFERENCE.md](REFERENCE.md).

After a marketplace install, if Claude Code asks, run `/reload-plugins --force` or start a new session before setup.

Copy prompt: **Set up the Fluxtion Analyser and connect it to this assistant.**

## Update notices

Updating the plugin does not automatically upgrade the app. The bridge can continue using the exact verified
1.40.2, 1.40.3 or 1.40.4 release. If its native context response has `pluginBridge.upgradeNotice`, relay it once in this conversation: say “set up
the Fluxtion Analyser” to upgrade. Do not run setup or upgrade just because the notice appears. The bridge
remembers this notice per home/release pair and also writes it to stderr for diagnostics.
A future release marked upgrade-required must be upgraded before connecting. Only a native context call
proves the current conversation is connected. After an approved upgrade, manual MCP reconnect is still required.

## 1. Explain and ask once

Before downloads or commands that change state, resolve the home, JBang/cache and Claude config destinations
from the session's environment using local reads. Reuse paths already supplied by the host. If shell
discovery is necessary, batch destination and executable checks into one read-only shell call; do not issue
separate `pwd`, `ls`, `env` and `command -v` calls. In that same call, run the bundled read-only
`osascript -l JavaScript "${CLAUDE_PLUGIN_ROOT}/scripts/inspect-config.js" "<resolved Claude JSON path>"`
with the actual resolved path. It parses JSON (including nulls) and checks `mcpServers.fluxtion-analyser`;
it prints only present, absent or unreadable. Report that status and the path, never the JSON or registration
values. A missing file is absent; malformed/unreadable input needs attention. Do not use `plutil -lint`
for JSON: a valid Claude profile can contain nulls that a property-list parser rejects.
Do not run `claude mcp get` before consent: it can start the existing server for a health check. Respect explicit overrides. Explain preparation yourself;
the person does not need to request technical steps. Present one question with those actual destinations:

> Shall I set up the Analyser? I will:
> 1. Run the setup helper bundled with this plugin; JBang compiles it on first run.
>    Install JBang if missing; Java and cache downloads may be needed for preparation and Java 21 for the app.
> 2. Trust only the Analyser 1.40.5 release folder in JBang, install its analyser command and verify its JAR.
>    When installation is needed, JBang may add PATH, the j! alias and JAVA_HOME to shell startup files,
>    affecting future Java builds; these edits are conditional, not guaranteed.
> 3. While the Analyser is closed, record Claude Code as its AI client if no choice is saved, preserving other
>    settings. Open the Analyser and enable persistent external access, allowing local clients to use its action API.
> 4. Use this plugin’s MCP server. If an existing user-scope fluxtion-analyser entry was found, offer to remove
>    that exact entry with a private backup and selective undo. Never register a second copy.
>    Save the running Analyser’s installed Java path for this plugin’s bridge; it launches without downloads.
> 5. Keep private receipts, diagnostics and necessary before-copies under your Analyser settings directory.
> 6. Optionally open your browser for Fluxtion sign-in and save an owner-only build credential in
>    ~/.fluxtion/credentials. Signing in charges nothing; free-plan limits apply. The Analyser's Compiler account
>    view uses a separate store and will not show this sign-in yet.
>
> Claude Code asks you to allow setup commands. Its “Yes, and don’t ask again for: analyser-setup plan *”
> choice covers later plans in this project; apply has its own “analyser-setup apply *” choice. These do not approve each other.
> The JBang bootstrap or other commands can still ask for approval.
>
> Yes / yes, but without the Fluxtion sign-in / no?

Name the resolved destinations beside this list, including any credential override and compiler URL.
“No” stops without downloading, compiling or changing setup. Keep the chosen sign-in answer.
On an already-confirmed rerun, use local reads and the native context to identify what appears missing, then
ask only for those missing items (for example, sign-in). Omit installation/profile-edit warnings when installation
is not proposed. After yes, still run the helper's exact plan and check it against that approval before apply;
a native context or prose summary never substitutes for the plan. Ask again only for additional changes.
The host and operating system can still require their own permission prompts; this setup consent does not
disable them. A host refusal must be resolved explicitly, not bypassed by splitting the command.
This approval covers only the listed changes and destinations. Name any existing user-scope entry and its destination in the upfront question before removing it.
If it was not included, obtain specific consent. Do not silently replace a credential. Read [Operator notes](REFERENCE.md#operator-notes) for recovery.

## 2. Prepare and inspect the exact plan

Reuse JBang found during the single preflight above. The bundled wrapper already resolves JBANG_DIR,
PATH and the standard Homebrew locations; do not repeat executable probes after it has succeeded.
Keep setup commands recognisable: `analyser-setup plan`, then `analyser-setup apply --plan <approved-id>`.
Do not wrap them in an extra `cd`, `env`, variable-assignment script or chained verification command unless
a concrete override is required. Use the actual ID directly in apply; it is not a credential.

If JBang is absent, use this bootstrap under the approval above, in a supervised/resumable shell. Retain the job identity;
on cancellation stop only its children. If the host cannot supervise it, use the manual route.

```sh
(
  set -eu
  analyser_bootstrap="$(mktemp)"
  trap 'rm -f "$analyser_bootstrap"' EXIT
  curl -fsSL https://sh.jbang.dev -o "$analyser_bootstrap"
  if [ "${JBANG_DEFAULT_JAVA_VERSION:-21}" = 21 ]; then
  # Preserve an explicit Java 21 first, then discover installed Java 21 without changing shell profiles.
  analyser_existing_java="$(/usr/libexec/java_home -v 21 2>/dev/null || :)"
  for analyser_java_candidate in "${JAVA_HOME:-}" "$analyser_existing_java" "$HOME"/.sdkman/candidates/java/21*; do
    if [ -x "$analyser_java_candidate/bin/java" ] &&
       [ -r "$analyser_java_candidate/release" ] &&
       grep -Eq '^JAVA_VERSION="21([.+-]|")' "$analyser_java_candidate/release"; then
      export JAVA_HOME="$analyser_java_candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      break
    fi
  done
  fi
  JBANG_DEFAULT_JAVA_VERSION="${JBANG_DEFAULT_JAVA_VERSION:-21}" bash "$analyser_bootstrap" version
)
```

The plugin puts `analyser-setup` on the Bash tool's PATH. Use it directly; if it does not resolve, stop and
check plugin loading rather than downloading another helper. If the plan reports `claude` missing
from PATH, inspect its installed location (commonly `~/.local/bin/claude`) and add that existing directory
to PATH for both plan and apply only. Do not install another Claude or edit shell profiles. JBang may download Java/cache dependencies
while preparing the bundled source; the Java planner itself performs local reads only.

```sh
analyser-setup plan
```

Read the whole plan, including **Details (for review)**. Show the plain numbered changes and offer the details.
If its selected actions and destinations are entirely within the upfront approval, apply the corresponding ID
**without asking again**. Use `plan-id` for yes. For without sign-in use `plan-id-without-signin` when supplied;
if there is no SIGN_IN action, use `plan-id` because there is no new sign-in to exclude.
An existing valid sign-in is retained. Never silently change the answer.

When the plan proposes UPGRADE, name only that case in the consent question: “Upgrade your verified
Analyser 1.40.2, 1.40.3 or 1.40.4 to 1.40.5, keeping your settings and sign-in? I will download and verify the new JAR before
replacing the launcher, keep a private backup and the old cache/trust for undo, then open the app.”
This is a replacement requiring specific approval; a previous install-only answer does not approve it.
If the old app is open, ask the person to close it and replan. Never stop their app yourself. A failed download
leaves the old launcher usable. Do not suggest cache recovery for a version upgrade.

A replacement, different destination or BLOCKED result needs attention before proceeding: explain the extra
change and ask for specific approval where applicable. A selected BLOCKED plan cannot be applied; the sole
CI sign-in blocker can be removed by selecting its without-sign-in plan (see Operator notes). The helper detects
matching own-user pinned-JAR processes in this home when no endpoint is available. If an existing window is
identified, ask the person to close that window and rerun setup, or choose the manual route; ambiguous
ownership needs clarification. Do not certify an absent window on the person's behalf.

If nothing needs changing and native tools already work, call `analyser_context` directly. Otherwise the
upfront approval includes readiness checks and a private receipt.

## 3. Apply and relay progress

If sign-in was chosen, remind the person **before** apply: “Your browser will open; approve the Fluxtion sign-in
there.” Never copy a device code or bearer credential into chat; only the public short user code is shareable.

Run `analyser-setup apply` through the host's background/resumable shell. Follow its existing task/output
handle every 5–10 seconds (TaskOutput, or the task output file supplied by Bash). The helper emits every
progress line and its final summary there. Use that stream first; do not also read the separate progress log
or receipt when it already supplies the needed information. Those reads add separate directory approvals.
Only if task output is missing or buffered, use the progress-file fallback below. Do not start a second
shell watcher, `tail`, `cat`, sleep loop or repeated shell poll. These add approval prompts and orphan risks. Retain the job identity. The
first output names `progress-file`. If output is buffered, read `~/.fluxtion-analyser/setup-progress.json`,
require its `planId` to match, then follow only its `path`. Relay each new progress line once; do not read private
startup logs into chat. As soon as the sign-in stage starts, follow the task output (or its matching progress file only if needed) and relay the
**public short user code and manual-page URL immediately, while approval is pending**. Do not wait for the
apply command to finish: keep it in the background and continue polling. Never relay the secret device_code
or access token. On browser failure, relay the manual-page instruction instead of claiming it opened.
When apply finishes or fails, stop its progress monitor; do not leave a background watcher running.
Use the [timeout and recovery notes](REFERENCE.md#operator-notes); do not use an untracked nohup process.

Use the exact approved plan ID:

```sh
analyser-setup apply --plan <approved-id>
```

| Exit | Meaning | Next step |
|---|---|---|
| 0 | Plugin declared and app checked; current session assessed by the assistant | Keep a confirmed connection, or reconnect and confirm below |
| 1 | Failed; some effects may remain | Read sanitized receipt, explain partial state, stop |
| 2 | Blocked | Resolve limitation; no automatic workaround |
| 3 | State changed | Replan; ask again only for changes outside the approved list |

“Connection closed” after a cache clear can mean the pinned JAR is missing: rerun setup and follow its targeted
cache recovery. Do not blindly follow a bridge hint to enable `--rest`; the manual route retains app-UI consent.

## 4. Confirm through this assistant and hand over

On a sign-in-only or no-change rerun, keep a connection already confirmed by this assistant; setup's
receipt cannot inspect that session. Its `nativeConfirmation` is “not assessed by setup”, not a claim
that a previous native confirmation was lost. Reconnect when this apply started the app or prepared its
bridge (`pluginReconnectRecommended`), or when native tools are unavailable.

**Day two: Analyser closed.** If you kept compatible 1.40.2, 1.40.3 or 1.40.4, reopen the existing launcher at
`JBANG_DIR/bin/analyser` (normally `~/.jbang/bin/analyser`) to stay on that version; setup instead proposes
an upgrade. Reconnect and confirm with native `analyser_context`. For the current 1.40.5, say
“set up the Fluxtion Analyser” again: inspect the plan, which normally opens the existing app (START_APP)
and refreshes its installed Java path (PREPARE_PLUGIN). Keep the existing install, access and plugin declaration;
ask for any extra changes under the consent rules above. After opening it, reconnect the plugin and call
its native analyser_context. Automatic MCP startup does not open the app or enable access by itself.
For manual opening, use the installed analyser launcher at the resolved JBANG_DIR/bin/analyser path.

The plugin server may have failed before installation. In an interactive terminal, ask the person to run
`/mcp reconnect plugin:fluxtion-analyser:fluxtion-analyser` (or select that server’s Reconnect action in `/mcp`).
If the plugin declaration itself was just added or changed, use `/reload-plugins` first; follow its displayed
`--force` instruction only if needed. Do not run these session commands through Bash or start another Claude
process as a substitute. In the macOS Claude Code 2.1.316 trial, `/reload-plugins` alone left the server
failed; the explicit reconnect above succeeded in the same session. Treat that as tested behaviour on that
host/version, not a promise for every host. See REFERENCE.md.

After removing an old user entry, inspect `/mcp`: if its old connection is still loaded, disable that old
`fluxtion-analyser` connection before confirming the plugin. Do not remove or disable the plugin server.
Call whichever native tool is actually exposed:
`mcp__plugin_fluxtion-analyser_fluxtion-analyser__analyser_context`.
Do not substitute the old user-scope `mcp__fluxtion-analyser__analyser_context`: that would confirm a
different connection. Other plugin tools use the same prefix and the `analyser_<verb>` tool name.
Require `ok: true`; an empty project is valid. Program diagnostics do not establish host connection.

For `-p`, desktop or other non-interactive sessions, plugin MCP changes require a new session: explain that
limitation and use the URL route’s `/exit` then `claude --continue` handover in the same profile. No handwritten
config, SDK or REST substitution. Missing tools mean native confirmation pending.

| Status | Meaning |
|---|---|
| Installed | analyser command for the pinned version is in place and checksum-verified |
| Open | the selected Analyser is running |
| External access on | local programs can use this Analyser |
| Registered | URL route only: Claude Code has a user-scope server configured |
| Plugin declared | plugin server declaration checked; current session not inspected |
| Connected | the host bridge answered; native confirmation is still pending |
| Confirmed | this assistant called analyser_context and received ok:true |
| Signed in | compiler build credential saved; show account/plan if available and any validation limitation |
| Not signed in | no usable sign-in for the selected compiler; skipped, declined or refused |


Never call Registered or Plugin declared “Connected”, or claim setup is complete until **Confirmed**. Sign-in is independent.
Preserve any qualification about an unvalidated saved credential. When signed in, say **Signed in for builds.
The Analyser's Compiler account view does not show this sign-in yet.** If skipped, offer a later rerun.

Relay `pluginConnectionNote` on every apply, including a rerun: **The connection comes from the plugin;
you do not need the Analyser's Set up AI for Claude Code.** In 1.40.5 that UI checks the user-scope name and
can offer to add it again. If an entry reappears, run the exact plan and offer removal with consent and a
private backup; never silently remove it. Disable the old loaded connection in `/mcp` if it remains.
Relay any `clientDisplayNote` as a display limitation. Do not send a plugin user through the app's
registration flow to fix a label. Setup does not modify a running app's choice or replace an existing one;
this does not invalidate a successful native plugin connection.
For terminal use, relay `terminalPathNote` with the actual launcher directory: ensure it is on PATH. Absolute
paths in the MCP registration work without it; do not edit shell profiles or claim they were changed.

After Confirmed: **The Analyser is ready. Pick one of its start-screen options, or tell me what you want to build.**
Templates supply authoring skills. For a subsequent project request, use the public
[starter catalogue](https://fluxtion-playground.dev/starter-templates/index.json). For Analyser-assisted
Spring authoring, offer the [audit-analyser bundle](https://fluxtion-playground.dev/start/scaffold?template=analyser-bundle)
first: it carries the authoring and evidence skills. The [AOT starter](https://fluxtion-playground.dev/start/scaffold?template=fluxtion-aot)
is a smaller example and does not carry those skills. Check the selected template's actual contents and
catalogue skill metadata; do not infer skills from its name. After scaffolding, tell the person to start
Claude Code inside the new project directory so project skills are discovered. Preserve this plugin's
connection; use setup and reconnect if the app is closed, not another user-scope registration.
Read the downloaded PROJECT.md before authoring. There is no need to inspect JAR internals to find the catalogue.
**Java for project builds.** Use the selected project's PROJECT.md and RUNBOOK.md for commands. The bundle uses `./run-server.sh`
or `./run-and-export.sh`; the standalone AOT starter uses `./run.sh`. Builds and runs need JDK 21+
through `JAVA_HOME` or `java` on PATH. A Java downloaded by JBang need not be exposed through either, and macOS's
`/usr/bin/java` is only a stub when no JDK is installed. Before the first build, run `java -version`. If it fails
or reports below 21, run each build and run command with JBang's Java 21 for that command only:

```sh
(
  set -eu
  analyser_jbang="${JBANG_DIR:-$HOME/.jbang}/bin/jbang"
  if [ ! -x "$analyser_jbang" ]; then
    analyser_jbang="$(command -v jbang || :)"
    if [ -z "$analyser_jbang" ]; then
      for candidate in /opt/homebrew/bin/jbang /usr/local/bin/jbang; do
        if [ -x "$candidate" ]; then analyser_jbang="$candidate"; break; fi
      done
    fi
  fi
  [ -n "$analyser_jbang" ] && [ -x "$analyser_jbang" ] || {
    echo "JBang was not found; rerun setup to locate it." >&2; exit 1;
  }
  analyser_java_home="$(JBANG_DEFAULT_JAVA_VERSION="${JBANG_DEFAULT_JAVA_VERSION:-21}" "$analyser_jbang" jdk home 21)"
  JAVA_HOME="$analyser_java_home" PATH="$analyser_java_home/bin:$PATH" ./mvnw -B package
)
```

The lookup also supports an existing JBang on PATH or in Homebrew; it does not assume setup installed
JBang under your home. Use the same scoped Java environment for the documented setup, validate, generate and run commands.
For the bundle, run `./run-and-export.sh`; for the standalone AOT starter, run `./run.sh`.
After editing a Spring graph, use its `./generate.sh` authoring path, not a direct Maven substitute.
Do not edit shell profiles for this. Tell the person that builds outside the assistant need a JDK 21+ on PATH
or this `JAVA_HOME`.
Setup does not generate or run projects. Report version, paths, diagnostics and actual native result.
Use the latest sanitized receipt; never paste endpoint tokens, private backups or startup logs into chat.
For undo, retained history, other hosts or retrying sign-in, read [Operator notes](REFERENCE.md#operator-notes).
