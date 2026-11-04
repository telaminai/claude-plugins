# Telamin plugins for Claude Code

Tools for building and inspecting Fluxtion applications with your assistant.

## Fluxtion Analyser

For macOS and the Claude Code interactive terminal. The plugin helps install and open the
Analyser and connects Claude Code to inspect audit records beside their graph.

```text
/plugin marketplace add telaminai/claude-plugins
/plugin install fluxtion-analyser@telamin
```

If Claude Code asks, run `/reload-plugins --force` or start a new session before setup. Then say:

> Set up the Fluxtion Analyser and connect it to this assistant.

The assistant explains changes and asks for consent. Host and operating-system approvals still apply.
Compiler sign-in is optional for setup and needed for builds; free-plan limits apply.

See the [plugin guide](fluxtion-analyser/README.md) for connection checks, privacy and recovery, and
[Set up with your AI assistant](https://telaminai.github.io/fluxtionauditlog-analyser/start/set-up-with-an-assistant/)
for the full journey. The [manual installation route](https://telaminai.github.io/fluxtionauditlog-analyser/install/)
remains available for people and teams who do not allow assistants to install software.

Licensed under [Apache-2.0](LICENSE).
