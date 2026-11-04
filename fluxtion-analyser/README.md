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
1.40.2 that route can offer a user-scope entry again. If you add one, rerun plugin setup to review its removal.

## Connect and use it

In the tested interactive terminal, run this when asked:

```text
/mcp reconnect plugin:fluxtion-analyser:fluxtion-analyser
```

Claude must call its own `analyser_context` tool and receive `ok: true` before reporting **Confirmed**.
Installation or registration alone is not confirmation. Same-session reconnect worked in the macOS
Claude Code 2.1.316 trial; `/reload-plugins` alone left the failed server disconnected.

On a later day, the plugin server may fail because the Analyser is closed. Say “set up the Fluxtion Analyser”
again and review the plan: it normally opens the existing app and may refresh the saved Java path for the
bridge. Then reconnect and ask for the native check. Automatic MCP launch does not open the app or grant
external access. A sign-in-only rerun keeps an already confirmed connection.

After confirmation, choose an option on the Analyser start screen or tell Claude what you want to build.
For builds on a Mac without Java, the setup skill explains how to use JBang's Java 21 for each command.

## Preview scope

Tested on macOS with Claude Code in an interactive terminal, using a locally loaded plugin and the staging
compiler. Marketplace installation, managed team rollout, other operating systems, other hosts and the
desktop app have not been acceptance-tested. In the last Manual-mode run, setup required seven host approvals, plus one on the sign-in rerun.
Five or fewer is a target; the refined flow has not had a new cold count. Production verification remains pending. The manual installation route remains available for people and teams
who do not allow an assistant to install software.

## Privacy and undo

Setup downloads tooling and the app, checks the local Analyser and optionally contacts the compiler for
browser-approved sign-in and account usage. The helper does not upload project source files. Your assistant's
provider and permissions govern what it reads and sends. Enabling external access lets local clients use
the Analyser action API.

Build credentials are stored owner-only at `~/.fluxtion/credentials` by default. The Analyser's Compiler account
view uses a separate store and does not show this sign-in. Private receipts and selective undo instructions
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
