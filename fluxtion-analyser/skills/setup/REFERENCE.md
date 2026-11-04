# Analyser setup reference: app-UI route, other hosts and recovery

This file keeps the detailed procedure from the app-UI route. `SKILL.md` is the main path: the person answers one
question in chat and the assistant does the rest. Use this reference when the person prefers to approve in the app,
when the host has no local shell (for example Claude Desktop), for other hosts, and for recovery.

## App-UI route (from the earlier skill)

This procedure uses Analyser **1.40.3**. A fetched skill provides instructions; it does not automatically
install itself into an assistant or register an MCP server.

## Outcome and scope

The person asks once; you guide installation, open the desktop app, help them connect their chosen assistant and
make a real `analyser_context` call. Some hosts require a reload before newly registered tools appear. Do not say
“connected” just because a registration command succeeded or a bridge probe passed.

Use the person's existing authorisation to set up the app. Explain that JBang may download Java, and that enabling
external access permits local clients to use the Analyser's action API. Honour OS, host and JBang approval prompts.
Do not enable external access or replace client registrations silently. This setup needs no compiler credential,
API key, Node installation or application compilation. It does not install an AI client or sign into one.

## 1. Identify the local environment

Check the OS, whether a desktop session is available, and which local assistant the person wants connected.
If the current assistant has no local shell, give the supported command to the person; do not claim you ran it.
If it has no local build tools, it can still use Analyser investigation tools after registration. Registering this
server does not give Claude Desktop a shell or local Java authoring tools.

Look for JBang and the `analyser` launcher, honouring `JBANG_DIR` where set. Reuse a working installation.
For an existing app or registration, inspect the version/target before deciding anything needs replacing.
Do not treat an unsuccessful status check as permission to overwrite it. Never read client login files or print
the Analyser endpoint file/token. Use the native MCP tools and the app's supported setup UI instead.

## 2. Install using the supported JBang commands

Pinned release:
`https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.40.3/fluxtion-auditlog-analyser.jar`

Expected JAR SHA-256:
`1cb19a64e9866031e5502908f26f78bc886a9d2e1a46fd281929425792eabc7c`

[Published checksums](https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.40.3/SHA256SUMS.sha256)
are a consistency check, not an independent signature. Use the official release; stop on a mismatch. Do not substitute
an unpublished build or `latest` with this pinned version.

### Before running an installer

Tell the person **before starting**: JBang may open a native trust dialog even when the assistant has no terminal.
For the exact official release above, choose **Trust once**. This may recur on later launches. Do not silently add
permanent trust, trust-all flags or `--insecure`.

**`app install` can invoke JBang environment setup.** It may add PATH and the `j!` alias to shell startup files,
and on a machine without Java it can also set `JAVA_HOME` to JBang's `currentjdk`. This can change the Java used
by the person's other builds. Include those changes in installation consent. The Bash commands below default
JBang to Java 21, so a machine without Java can share one JDK for JBang, setup, the Analyser and builds.
An explicit different JBang default may require another JDK; Windows has not had this platform trial.
Do not promise a fixed duration.
Before installation, record which relevant startup files exist and retain private before-copies for comparison;
never print their contents into the conversation. If the person wants to undo setup, show only the installer's
added lines and remove those exact changes after approval, preserving concurrent edits and previous Java settings.

Use a resumable shell/PTY session, retain its process ID and poll while the person handles the dialog. Do not
repeatedly launch the command when it is waiting for trust. Set a bounded wait (for example five minutes).
On timeout/cancellation, stop this installation's process tree, including its JBang/Java children, and verify it
has exited before retrying. Track children while the parent is alive: killing only the wrapper can orphan the
popup. Never use a name-wide `pkill java` or terminate another app. If the host cannot track and clean up its
children, have the person run the command in their own terminal instead; do not start it unattended.

When JBang is already installed and the Analyser launcher is absent:

```sh
JBANG_DEFAULT_JAVA_VERSION="${JBANG_DEFAULT_JAVA_VERSION:-21}" jbang app install --java 21 --name analyser https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.40.3/fluxtion-auditlog-analyser.jar
```

When JBang is absent, macOS/Linux with Bash:

```sh
(
  set -eu
  analyser_bootstrap="$(mktemp)"
  trap 'rm -f "$analyser_bootstrap"' EXIT
  curl -fsSL https://sh.jbang.dev -o "$analyser_bootstrap"
  JBANG_DEFAULT_JAVA_VERSION="${JBANG_DEFAULT_JAVA_VERSION:-21}" bash "$analyser_bootstrap" app install --java 21 --name analyser https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.40.3/fluxtion-auditlog-analyser.jar
)
```

Windows PowerShell uses the documented equivalent (requires a platform trial before release support is claimed):

```powershell
iex "& { $(iwr -useb https://ps.jbang.dev) } app install --java 21 --name analyser https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.40.3/fluxtion-auditlog-analyser.jar"
```

The Bash block downloads completely before executing and preserves a failed fetch status.
If a bootstrap command fails, stop and inspect the stage; do not continue to registration. Do not use `--force`
to replace an existing app with this setup route. Upgrading an existing version is a separate, explicit choice.

### Verify the downloaded JAR before opening the application

On macOS/Linux, this exact command finds candidates under the resolved cache, accepts only the pinned digest,
and fails if no matching JAR exists. Other cached versions are ignored. `shasum` must be available; do not skip
verification if it is missing. Paths containing spaces are supported.

```sh
(
  set -eu
  analyser_cache="${JBANG_CACHE_DIR:-${JBANG_DIR:-$HOME/.jbang}/cache}"
  analyser_matches="$(mktemp)"
  trap 'rm -f "$analyser_matches"' EXIT
  find "$analyser_cache/urls" -type f -name fluxtion-auditlog-analyser.jar -exec sh -c '
    marker=$1
    shift
    for candidate do
      digest=$(shasum -a 256 "$candidate") || exit 1
      digest=${digest%% *}
      if [ "$digest" = 1cb19a64e9866031e5502908f26f78bc886a9d2e1a46fd281929425792eabc7c ]; then
        printf "%s\n" "$candidate" >> "$marker"
      fi
    done
  ' sh "$analyser_matches" {} +
  test -s "$analyser_matches" || { echo "Pinned Analyser JAR absent or checksum mismatch" >&2; exit 1; }
  cat "$analyser_matches"
)
```

Check that the installed launcher's release URL and `--java 21` match this pin as well; finding a cached matching
JAR does not establish which version an unrelated launcher will run. On Windows, identify the pinned JAR below
`$env:JBANG_CACHE_DIR` (or the resolved JBang directory's `cache\urls`) and use
`Get-FileHash -Algorithm SHA256 -LiteralPath <resolved-jar-path>`; stop unless it matches the digest above.
Windows remains pending its own platform trial.

Open the installed absolute `analyser` launcher under the resolved JBang directory's `bin` folder. On macOS/Linux,
supply both the resolved JBang executable directory and the app bin directory on this process's PATH, as below. Do not make additional shell
startup edits. Keep application startup output in a private local file: 1.40.3 prints a token prefix, so do not
paste raw startup output into prompts or reports. Leave the desktop application running.
Do not pass `--rest` as an installation shortcut: it persists external access without the setup UI's confirmation.
**If a bridge error recommends `--rest`, do not follow that hint for setup.** Open the app normally and use
**AI → Set up AI…** to obtain external-access consent. There is no `--setup-ai` flag in this release.

## 3. Connect through the app

Find **AI → Set up AI…** in the **Analyser window's own menu bar**, not the macOS screen menu bar.
On the start screen, the AI line also offers **Set up AI…**, or **Change…** when an AI configuration is active.
Choose **Your own AI client connects**. **Do not choose “Claude inside Fluxtion Audit Log Analyser”** for this
task: that enables the embedded assistant and does not register this external host. **Both** also enables the
embedded panel and is appropriate only if the person wants both routes. Let the person confirm external access
in the app and check its status line reports access enabled; do not inspect the endpoint file to confirm it. For this version use **More options… → Generic MCP** to inspect the command, then register through
the chosen host as below. **Do not use 1.40.3's one-click Claude/Codex registration for a JBang launcher:** it
omits the per-server PATH needed by GUI-launched hosts. The environment repair below is part of this skill's
supported registration procedure; it does not enable external access or bypass that consent.

Verify the displayed command against the intended installation. 1.40.3 checks `user.home/.jbang/bin`, ignoring
custom `JBANG_DIR`, so it can show an older default launcher. Use the verified absolute launcher under the
resolved install directory instead, with `--mcp`; never register a mismatched release or copy endpoint tokens.

### macOS/Linux registration (one chosen host)

Resolve `analyser_install_dir` to the **absolute** JBang application directory containing the verified
`bin/analyser`. Resolve the working **JBang executable independently** (for example, `command -v jbang` in the
working shell), verify its absolute executable path and retain its parent directory. Homebrew, SDKMAN and
manual installations can keep `jbang` outside the app directory; do not copy or reinstall it just to colocate
these files. If resolution is missing or ambiguous, stop and explain the layout before registration.
Show the person the exact launcher, host, registration name and environment before registration. These are
per-server settings, not shell profile edits. Set `analyser_server_path` to
`<absolute-jbang-executable-dir>:<absolute-install-dir>/bin:/usr/bin:/bin:/usr/sbin:/sbin`.
For a required custom JBang cache, also carry the
resolved absolute `JBANG_CACHE_DIR` in the server environment. Do not copy the whole shell environment.

For an absent `fluxtion-analyser` entry, use the chosen host's documented CLI. Set the two shell variables to the
resolved values first; the guard prevents running an incomplete example. Use only one of these blocks:

```sh
: "${analyser_install_dir:?Resolve the absolute JBang installation first}"
: "${analyser_server_path:?Resolve the server PATH first}"
claude mcp add --scope user --env "PATH=$analyser_server_path" "JBANG_DIR=$analyser_install_dir" --transport stdio fluxtion-analyser -- "$analyser_install_dir/bin/analyser" --mcp
```

```sh
: "${analyser_install_dir:?Resolve the absolute JBang installation first}"
: "${analyser_server_path:?Resolve the server PATH first}"
codex mcp add fluxtion-analyser --env "PATH=$analyser_server_path" --env "JBANG_DIR=$analyser_install_dir" -- "$analyser_install_dir/bin/analyser" --mcp
```

For Claude Desktop/generic clients, use their copy/save configuration flow, replacing the placeholders with
resolved absolute paths. Shell syntax such as `$HOME` is not expanded in this JSON:

```json
{
  "mcpServers": {
    "fluxtion-analyser": {
      "command": "<absolute-install-dir>/bin/analyser",
      "args": ["--mcp"],
      "env": {
        "PATH": "<absolute-jbang-executable-dir>:<absolute-install-dir>/bin:/usr/bin:/bin:/usr/sbin:/sbin",
        "JBANG_DIR": "<absolute-install-dir>"
      }
    }
  }
}
```

This setup route does not automatically merge Desktop configuration. Preserve other servers and settings. An
existing same-name entry needs comparison and explicit replacement consent; do not remove/re-add it silently.
If the app already wrote an entry without PATH, explain the defect and obtain approval to replace **only that
entry** through the host's supported mechanism. Check the resulting command and per-server environment before
reload. Repeat for additional clients only when requested. Windows environment/launcher handling and full
Desktop/Codex journeys are untested; do not advertise them as validated configurations.

If another Analyser window is serving, resolve that explicitly in the UI. A missing endpoint is not proof that no
Analyser is running. Do not open repeated windows or switch projects to make a connection check pass.

## 4. Verify with this assistant

After the person says setup is complete, verify the entry **before asking for a reload**. For Claude Code use
`claude mcp get fluxtion-analyser`; for Codex use `codex mcp get fluxtion-analyser --json`, or the chosen host's
server configuration view. These checks may start the bridge, so perform them only after registration consent.
Check the intended command, `--mcp`, scope and environment; do not print unrelated config or tokens. Missing or
mismatched registration is not success even if the in-app Claude route showed a success message.

Then reload/restart the assistant if its host requires it. Give the person this continuation:

> Use the registered Fluxtion Analyser MCP server to call analyser_context. Tell me whether it reached my open
> Analyser and what project or log is open. Do not open, modify or run a project yet.

Make the actual native tool call when it is available. No project/log loaded is a valid connection result.
If tools are absent, report **registered; host reload/connection verification pending**. An MCP SDK or REST probe
can help diagnose the bridge, but is not evidence that this assistant has the tools. Do not fall back to showing
tokens or claim an SDK call was a model-host call.

Report the app version, OS/host, install location, chosen registration, whether external access was enabled by the
person, and the actual context-call result. Keep credentials and private project contents out of the receipt.
Only after connection succeeds ask for the project or task to work on. The assistant runs builds with its own
tools; the Analyser reads the results and evidence.

## Recovery and limits

- `Connection closed` alone does not show that the Analyser is closed. For this JBang launcher, check the
  server's PATH and whether it can resolve `jbang` first; a missing executable caused the observed GUI-style
  failure. Also check the launcher/release and the app's access status. Do not diagnose from that symptom alone.
- A checksum mismatch means stop before launching. A corrupt cached JAR can produce “No main class” and will
  not necessarily be downloaded again on a normal launch. Preserve the failed file for diagnosis; do not clear
  the whole JBang cache or reinstall with `--force`. After explaining the targeted refresh, use the verified
  absolute JBang executable with the same JBANG_DIR/JBANG_CACHE_DIR to refresh only the pinned release URL:

  ```sh
  : "${analyser_jbang_executable:?Resolve the absolute JBang executable first}"
  "$analyser_jbang_executable" info jar --fresh https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.40.3/fluxtion-auditlog-analyser.jar
  ```

  This uses JBang's documented info command rather than starting the app or replacing its installed launcher.
  Supervise any renewed trust prompt as above. Repeat the checksum check on the **returned JAR path itself**;
  continue only if its digest matches the pin. If refresh fails or the mismatch persists, stop and report it.
  Install only if the launcher is absent; otherwise preserve the existing verified launcher. This recovery recipe
  is documentation/source-checked, but a real corrupt-cache recovery trial remains pending.
- Retry the failed stage, preserving successful setup. Don't repeat installation after a host reload issue.
- Cancel stops further setup; it does not roll back completed stages. If this wizard enabled external access before
  cancellation, 1.40.3 leaves it on and offers **Turn it off**. Explain that state and let the person choose. Do not turn off
  pre-existing access or undo pre-existing installations/registrations as an assumed cancellation cleanup.
- A name conflict, managed policy or indeterminate CLI result needs a clear explanation, not an overwrite.
- An unavailable GUI or missing local shell is a supported limitation; give instructions for the local machine.
- This skill changes no production service and performs no hosted generation.

References: [Analyser connection guide](https://telaminai.github.io/fluxtionauditlog-analyser/connect-an-llm/),
[JBang installation](https://www.jbang.dev/documentation/jbang/latest/installation.html),
[JBang app install](https://www.jbang.dev/documentation/jbang/latest/cli/jbang-app-install.html).

Host registration references: [Claude Code MCP](https://code.claude.com/docs/en/mcp),
[Codex MCP](https://learn.chatgpt.com/docs/extend/mcp).

Cache recovery reference: [JBang info jar](https://www.jbang.dev/documentation/jbang/latest/cli/jbang-info-jar.html).


## Operator notes

These notes support the chat-consent route; the manual procedure above remains first-class.

### Registration difference and cache recovery

The app's Set up AI route registers the `analyser` launcher. This setup registers JBang with the exact verified
cached JAR, preventing a URL cache refresh from changing bytes between verification and launch. Switching
between routes may therefore offer a replacement; the plan identifies an existing launcher entry and requires
explicit approval.

`jbang cache clear` can remove that pinned JAR. “Connection closed” after clearing the cache means to rerun
setup and follow its targeted cache-recovery instructions, then verify the digest and replan. Do not assume the
bridge downloads missing bytes. Do not follow a bridge's `--rest` hint without access consent.

### Supervision, receipt and routes

Allow an overall timeout covering the sum of bounded stages: installation can take 15 minutes, browser approval
10 minutes plus bounded requests, with separate connection limits. Use a resumable job. If unavailable, explain
these waits and run foreground. The per-home lock prevents concurrent apply. Ordinary cancellation stops owned
children; SIGKILL or host failure can leave them behind. Inspect the receipt before retrying. Successful apps
stay open; completed effects are retained rather than rolled back wholesale.

The latest receipt is `~/.fluxtion-analyser/setup-receipt.json`; immutable run receipts and `setup-history.jsonl`
retain history. Undo only this run's still-current effects, preserving prior installations and subsequent edits.
Route defaults to `url`. Direct terminal use may choose `-Dfluxtion.setupRoute=manual` consistently for plan and
apply. The plugin wrapper selects `claude-code-plugin`; it uses the plugin declaration instead of adding
a user-scope registration.

### Optional sign-in and CI

To replace a refused credential, use `-Dfluxtion.renewSignIn=true` before the source path for both plan and apply,
and request explicit replacement approval. Never silently delete the old credential. Maven plugin 1.3.1 uses
the saved issuer when a project has no compilerUrl; an explicit conflicting URL is refused. The Analyser account
keychain is separate. No legacy API-key route is used.

Interactive sign-in is blocked in CI. If that is the only blocker, the explicitly chosen without-sign-in ID can
still apply other actions. Existing matching credentials are kept. Otherwise BLOCKED stops the route.

Never print endpoint credentials, private registration backups or startup logs. Never modify the pinned helper,
bypass its digest, use force-install or trust-all. Its source pin is a consistency check, not a signature.


## Claude Code plugin preview

The plugin bundles the same helper at build time; the URL route above and all
manual procedures remain available. The macOS plugin route is selected explicitly by `--route claude-code-plugin`.
The helper prepares the app and its installed JVM path, never adds a user-scope registration, and proposes
removing an existing user-scope duplicate with a private backup. Disable the plugin before restoring that backup,
and restore only if its user-scope name is still absent.

The bridge runs the installed JVM directly, verifies the pinned cached Analyser JAR, and starts its MCP mode.
It never calls the JBang bootstrap: `--offline` alone is insufficient because that shell can download before
Java parses its options. If the saved JVM or app is missing, run setup again. The shared wrapper resolves JBang
from JBANG_DIR, PATH, or standard macOS Homebrew locations for setup; custom locations need an explicit override.
Automatic MCP launch clears inherited JVM options before starting Java. No program download or source pin step
is needed on the plugin route; the plugin version packages the helper, while plans still bind its exact bytes.

### Enterprise controls — researched, not rollout-tested

| Control | Status here |
|---|---|
| `extraKnownMarketplaces` + `enabledPlugins` in managed settings | Officially documented fleet installation; not tested against an enterprise policy service. |
| `pluginTrustMessage` | Documented additional trust-warning text; no policy installed or tested. |
| Managed allow/block marketplace controls | Documented; not exercised with this plugin. |
| Manual non-skill install | Preserved above; plugin installation is optional. |

These settings are administration proposals, not code this plugin installs. Managed policy takes precedence;
a user-scope choice cannot undo a managed requirement. See the official
[organisation controls](https://code.claude.com/docs/en/plugins/org).

### Plugin availability

The approved marketplace is `telamin` in `telaminai/claude-plugins`, with plugin name `fluxtion-analyser`.
Install commands:

```text
/plugin marketplace add telaminai/claude-plugins
/plugin install fluxtion-analyser@telamin
```

After a marketplace install, if Claude Code asks, run `/reload-plugins --force` or start a new session before setup.

The manual install above remains available. Marketplace installation and managed rollout have not been tested.

Official references rechecked 2026-11-03:
[manifest and bin layout](https://code.claude.com/docs/en/plugins-reference),
[plugin server names](https://code.claude.com/docs/en/plugins/components),
[reload and nonterminal restrictions](https://code.claude.com/docs/en/plugins/cli-reference),
[MCP reconnect](https://code.claude.com/docs/en/mcp).
On macOS Claude Code 2.1.316, `/reload-plugins` alone left the server failed;
`/mcp reconnect plugin:fluxtion-analyser:fluxtion-analyser` then connected it without restarting the session.
If a removed user-scope server remains loaded, disable it in `/mcp`. A nonterminal reload does not
connect newly declared plugin servers; use the new-session handover there. Do not equate a local plugin
manifest, or the helper's diagnostic bridge, with a native connection in the current assistant session.
