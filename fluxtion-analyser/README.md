# Fluxtion Analyser for Claude Code

Let Claude Code install and open the Fluxtion Analyser, then connect to it through MCP. The Analyser lets
you and your assistant inspect a run's audit records beside its graph. Compiler sign-in for subsequent
project builds is optional for setup; it is needed to build a project (free, with plan limits).

## Install the plugin

```text
/plugin marketplace add telaminai/claude-plugins
/plugin install fluxtion-analyser@telamin
```

After a marketplace install, if Claude Code asks, run `/reload-plugins --force` or start a new session before setup.

Then say:

> Set up the Fluxtion Analyser and connect it to this assistant.

Or invoke `/fluxtion-analyser:setup`. Claude lists the changes and their destinations, then asks:
**yes / yes, but without the Fluxtion sign-in / no**. No stops without setup changes. Additional changes
need consent, and your host or OS may ask for permission too. Java and JBang need not be installed first;
setup can download them. JBang installation may edit shell PATH, j! and JAVA_HOME, which the question discloses.

The plugin supplies the MCP server. Setup does not add a user-scope registration. If one already exists,
Claude offers to remove that exact entry with consent and a private backup, avoiding duplicate connections.
The connection comes from the plugin; you do not need the Analyser's Set up AI for Claude Code. In Analyser
1.42.1 that route can offer a user-scope entry again. If you add one, rerun plugin setup to review its removal.

## Update the plugin and app

```sh
claude plugin marketplace update telamin
claude plugin update fluxtion-analyser@telamin
```

Then `/reload-plugins --force` or start a new session. Plugin 0.1.8 keeps a verified Analyser 1.40.2, 1.40.3, 1.40.4, 1.40.5, 1.40.6, 1.41.2 or 1.42.0 connection
working and offers a one-time upgrade notice. Say “set up the Fluxtion Analyser” when you want 1.42.1.
Approve the UPGRADE replacement, close the old app when asked, and rerun the plan. Setup downloads and verifies
the new JAR before replacing the launcher, keeps a private backup, and preserves your settings and sign-in.
Reconnect afterwards and ask Claude to confirm with its native `analyser_context` call.
Until 0.1.1 is published, plugin 0.1.0 requires the [manual preview upgrade](skills/setup/REFERENCE.md#manual-upgrade-including-while-using-plugin-010).

Plugin 0.1.2 stops its bridge, and the Analyser `--mcp` process it started, within about five seconds after the
Claude Code session that launched it exits, even when another process still holds the connection open. The
desktop app is not affected.

## Connect and use it

In the tested interactive terminal, run this when asked:

```text
/mcp reconnect plugin:fluxtion-analyser:fluxtion-analyser
```

Claude must call its own `analyser_context` tool and receive `ok: true` before reporting **Confirmed**.
Installation or registration alone is not confirmation. Same-session reconnect worked in the macOS
Claude Code 2.1.316 trial; `/reload-plugins` alone left the failed server disconnected.

On a later day, the plugin server may fail because the Analyser is closed. If you kept 1.40.2, 1.40.3, 1.40.4, 1.40.5, 1.40.6, 1.41.2 or 1.42.0, open
`JBANG_DIR/bin/analyser` (normally `~/.jbang/bin/analyser`) to stay on it, then reconnect. Setup proposes
an upgrade for 1.40.2, 1.40.3, 1.40.4, 1.40.5, 1.40.6, 1.41.2 or 1.42.0. For the current 1.42.1, say “set up the Fluxtion Analyser”
again and review the plan: it normally opens the existing app and may refresh the saved Java path for the
bridge. Then reconnect and ask for the native check. Automatic MCP launch does not open the app or grant
external access. A sign-in-only rerun keeps an already confirmed connection.

After confirmation, choose an option on the Analyser start screen or tell Claude what you want to build.
To start a new project, ask Claude to build a Fluxtion app, or invoke `/fluxtion-analyser:new-project`. It downloads
the Java-authoring starter; the first build generates the processor and needs compiler sign-in.
For builds on a Mac without Java, the setup skill explains how to use JBang's Java 21 for each command.

## Supported scope

The earlier preview passed a production marketplace trial on macOS with Claude Code 2.1.317/2.1.318
in an interactive Manual-mode session, including a Java-free installation, native MCP confirmation,
compiler sign-in, an AOT build/audit, day-two recovery and duplicate-entry removal. That trial counted
four setup command approvals plus one native MCP approval, and none on the sign-in rerun. Counts depend
on the host's existing rules and commands. Release 0.1.0 includes the follow-up fixes; its acceptance results are recorded separately.
Managed rollout, other operating systems, other hosts and the desktop app remain untested. The manual
installation route remains available for people and teams who do not allow assistant-led installation.

On the Java-free trial machine, downloads totalled about 420 MB and installed files about 580 MB.
The one Java 21 download accounts for about 190 MB compressed / 335 MB on disk; the Analyser adds about
227 MB. Sizes vary with platform and version. Setup reuses a detected installed Java 21 when available.

## Privacy and undo

Setup downloads tooling and the app, checks the local Analyser and optionally contacts the compiler for
browser-approved sign-in and account usage. The helper does not upload project source files. Your assistant's
provider and permissions govern what it reads and sends. Enabling external access lets local clients use
the Analyser action API.

Build credentials are stored owner-only at `~/.fluxtion/credentials` by default. Analyser 1.42.1 shares this sign-in with Maven. Private receipts and selective undo instructions
are kept under `~/.fluxtion-analyser/`. Later code generation sends a graph model and generation configuration;
the model can contain configured values, including private fields. Do not put secrets in it.

Read [Set up with your AI assistant](https://telaminai.github.io/fluxtionauditlog-analyser/start/set-up-with-an-assistant/)
for the full journey, or use the [manual install](https://telaminai.github.io/fluxtionauditlog-analyser/install/).
The bundled [reference](skills/setup/REFERENCE.md) covers recovery and undo.

## Tool names and permission rules

Plugin tools have the prefix `mcp__plugin_fluxtion-analyser_fluxtion-analyser__`; for example,
`mcp__plugin_fluxtion-analyser_fluxtion-analyser__analyser_context`. A user-scope connection has different
names, so existing scripts and permission rules may need updating.

Claude Code documents this explicit rule for allowing all tools on this plugin server:

```json
{ "permissions": { "allow": ["mcp__plugin_fluxtion-analyser_fluxtion-analyser__*"] } }
```

Adding this rule authorises every tool on that server; setup does not add a permission rule for you.
See [Claude Code MCP permissions](https://code.claude.com/docs/en/permissions#mcp).
