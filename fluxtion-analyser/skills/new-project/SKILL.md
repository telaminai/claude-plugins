---
name: new-project
description: Build or create a new Fluxtion app, processor, event processor or event-processing application. Starts from the Java-authoring starter (a Mongoose-hosted Maven project), never a project hand-written from Maven Central artifacts. Use for “build a Fluxtion app that…”, “create a Fluxtion processor”, “new Fluxtion project”.
---

# Start a new Fluxtion project

Use this when the person asks to build or create a Fluxtion app, processor or event-processing application.
Do not hand-write a project from Maven Central artifacts, and do not build from web-search docs for older products.

## Facts

- Current Fluxtion artifacts are `com.telamin.fluxtion` on the Fluxtion public repository,
  https://repo.repsy.io/mvn/fluxtion/fluxtion-public. The starter's POM already declares it.
- Maven Central's `com.telamin.fluxtion` 1.0.9 runtime and builder predate compiler sign-in and are not for building.
  `com.fluxtion` 9.x is the retired earlier product. Only `fluxtion-maven-plugin` is on Central.
- The recommended start is the Java-authoring starter, “Java authoring (Mongoose)” in the
  [starter catalogue](https://fluxtion-playground.dev/starter-templates/index.json): a runnable Maven project with the
  graph authored in plain Java and CLAUDE.md / AGENTS.md for the assistant. Mongoose is the server it deploys to.
- Its first build generates the processor on the hosted compiler and needs compiler sign-in.

## Procedure

1. Download the starter into a new empty directory, inspect the archive paths, then extract. `artifact` names the
   project and its top folder (default `java-authoring`); `group` and `basePackage` are also optional.

   ```sh
   curl --fail --location 'https://fluxtion-playground.dev/start/scaffold?template=java-authoring&artifact=<name>' --output <name>.zip
   unzip -l <name>.zip
   unzip <name>.zip
   ```

2. Read the extracted project's `CLAUDE.md` and follow it: it starts with `tools/context.sh` and says what else to
   read. Author the graph as it describes.
3. Build with the command `CLAUDE.md` gives. Builds need JDK 21+: if `java -version` fails or is below 21, use the
   setup skill's JBang Java 21 command for each build and run command.
4. If the build stops with FLX-1022 or FLX-1023 (not signed in), start the sign-in from the project directory in a
   background shell: `./mvnw com.telamin.fluxtion:fluxtion-maven-plugin:1.3.5:login`. Relay the link and the short
   code it prints as soon as they appear, while approval is pending; the person approves in the browser. Never relay a
   token. After it succeeds, rebuild. Rerunning setup with sign-in (“set up the Fluxtion Analyser”) also works.

Sign-in is the expected next step, not a blocker: do not switch template, delete or replace the project, or use -DskipFluxtion to avoid it; if the user declines, stop and say so.

Tell the person to start Claude Code inside the new project directory so its project skills are discovered. Other
templates are alternatives only when the person asks for them; see the setup skill's post-setup notes.
