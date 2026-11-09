/// usr/bin/env jbang "$0" "$@" ; exit $?
// JDK 17 API-compatible; setup launchers default JBang to Java 21.

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Pinned, consented setup. Preparation is disclosed separately; plan performs local reads only.
 * Application credentials and subprocess output never enter plans, receipts or failure messages.
 * Supported automatic route: macOS/Claude Code. Other platforms remain acceptance work.
 */
public class AnalyserSetup {
    static final String VERSION = "1.42.0";
    static final String RELEASE_DIR =
            "https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v"
                    + VERSION
                    + "/";
    static final String JAR_URL = RELEASE_DIR + "fluxtion-auditlog-analyser.jar";
    static final String JAR_SHA256 =
            "f28cdfcb72050a94df6e3fc5f6b19248a7b9a44475d9278a7feae79abcee4936";
    record PinnedRelease(String version, String url, String sha256, String minimumRequired) {
        Path jar(Machine m) {
            return m.cacheDir.resolve("urls").resolve(hash(url))
                    .resolve("fluxtion-auditlog-analyser.jar");
        }
    }

    // Only these exact released launcher bytes and cached artifacts may be replaced by consent.
    static final List<PinnedRelease> UPGRADE_FROM = List.of(new PinnedRelease(
            "1.40.2",
            "https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.40.2/fluxtion-auditlog-analyser.jar",
            "25c51f9bd5940c84a7820062d4ba56d6a961b071b968ec48f51006c7c3591265", "1.40.2"), new PinnedRelease(
            "1.40.3",
            "https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.40.3/fluxtion-auditlog-analyser.jar",
            "1cb19a64e9866031e5502908f26f78bc886a9d2e1a46fd281929425792eabc7c", "1.40.3"), new PinnedRelease(
            "1.40.4",
            "https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.40.4/fluxtion-auditlog-analyser.jar",
            "3c502efc055b90027bc82f3eea943c2a7d8fb8d8be7d8bfcc3c0eb06cbae7d2e", "1.40.4"), new PinnedRelease(
            "1.40.5",
            "https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.40.5/fluxtion-auditlog-analyser.jar",
            "c447d17ddf5f4cab34b8394710590e5267a72da77d5aebebf269175c4d48a1fd", "1.40.5"), new PinnedRelease(
            "1.40.6",
            "https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.40.6/fluxtion-auditlog-analyser.jar",
            "7f77d46da7bf1c6c7e88967cbf254a05866499fd9ef898bad43cdc813b693f13", "1.40.6"), new PinnedRelease(
            "1.41.2",
            "https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.41.2/fluxtion-auditlog-analyser.jar",
            "76e68f46ea17146599e9340959073206e0f69264298073d5e9c1e8de5dc55a54", "1.41.2"));
    static final PinnedRelease TARGET_RELEASE = new PinnedRelease(VERSION, JAR_URL, JAR_SHA256, VERSION);
    // A previous release is compatible only when its version meets its reviewed minimum marker.
    // Raise that entry's marker to VERSION when this plugin requires newer MCP behaviour.
    static final String SERVER = "fluxtion-analyser";
    static final String SEP = File.pathSeparator;
    static final String SCHEMA = "chat-setup-6";
    // shared user-visible status vocabulary. Confirmed is reserved for a native assistant call.
    enum Status {
        INSTALLED("Installed"),
        OPEN("Open"),
        ACCESS("External access on"),
        REGISTERED("Registered"),
        PLUGIN_DECLARED("Plugin declared"), // a plugin declaration is not user registration.
        CONNECTED("Connected"),
        CONFIRMED("Confirmed"),
        SIGNED_IN("Signed in"),
        NOT_SIGNED_IN("Not signed in");
        final String text;

        Status(String text) {
            this.text = text;
        }
    }
    // one emitter writes the same sanitized progress to stdout and the private run file.
    static final class Progress {
        final Path file;
        final int total;
        int index;

        Progress(Path file, int total) throws IOException {
            this.file = file;
            this.total = total;
            privateWrite(file, "");
        }

        void stage(String wording) throws IOException {
            note("[" + (++index) + "/" + total + "] " + wording);
        }

        void note(String line) throws IOException {
            Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            System.out.println(line);
            System.out.flush();
        }

        static String wording(Kind kind) {
            return switch (kind) {
                case TRUST -> "Trusting the Analyser " + VERSION + " release in JBang";
                case UPGRADE -> "Upgrading the analyser command to " + VERSION;
                case INSTALL -> "Installing the analyser command (downloading Java 21 if needed)";
                case RECORD_CLIENT -> "Recording Claude Code as your Analyser AI client";
                case START_APP -> "Opening the Analyser window";
                case REGISTER, REPLACE_REGISTRATION -> "Registering with Claude Code";
                case REMOVE_USER_REGISTRATION -> "Removing the duplicate user-scope registration";
                case PREPARE_PLUGIN -> "Preparing the plugin bridge with the installed Java"
                        + " runtime";
                case SIGN_IN -> "Waiting for you to approve the Fluxtion sign-in in your browser";
            };
        }
    }

    static final long MAX_CONFIG = 4 * 1024 * 1024, MAX_OUTPUT = 1024 * 1024;

    enum Kind {
        TRUST,
        INSTALL,
        UPGRADE,
        RECORD_CLIENT,
        START_APP,
        PREPARE_PLUGIN,
        REMOVE_USER_REGISTRATION,
        REPLACE_REGISTRATION,
        REGISTER,
        SIGN_IN
    }

    record Action(Kind kind, String text) {}

    record Instance(String state, long pid, String identity) {
        static Instance absent() {
            return new Instance("absent", 0, "absent");
        }
    }

    record Plan(
            List<Action> actions,
            List<String> kept,
            List<String> blocked,
            Map<String, String> snapshot,
            String id) {}

    record Machine(
            Path home,
            Path jbangDir,
            Path cacheDir,
            Path jbang,
            Path launcher,
            Path endpoint,
            Path receipt,
            Path claudeConfig,
            Optional<Path> claude,
            Path program,
            Path cwd,
            String os,
            Optional<Path> claudeConfigDirectory,
            Optional<CompilerSetup> compiler,
            String route) {
        static Machine detect(Path program) {
            Path home = absolute(Path.of(System.getProperty("user.home")));
            Path dir =
                    absolute(Path.of(env("JBANG_DIR").orElse(home.resolve(".jbang").toString())));
            Path cache =
                    absolute(
                            Path.of(
                                    env("JBANG_CACHE_DIR")
                                            .orElse(dir.resolve("cache").toString())));
            Path config =
                    absolute(Path.of(env("CLAUDE_CONFIG_DIR").orElse(home.toString())))
                            .resolve(".claude.json");
            return new Machine(
                    home,
                    dir,
                    cache,
                    onPath("jbang").orElse(dir.resolve("bin/jbang")),
                    dir.resolve("bin/analyser"),
                    home.resolve(".fluxtion-analyser/rest-endpoint"),
                    home.resolve(".fluxtion-analyser/setup-receipt.json"),
                    config,
                    onPath("claude"),
                    absolute(program),
                    absolute(Path.of("")),
                    System.getProperty("os.name", "").toLowerCase(),
                    env("CLAUDE_CONFIG_DIR").map(d -> absolute(Path.of(d))),
                    Optional.of(CompilerSetup.detect(home)),
                    System.getProperty("fluxtion.setupRoute", "url"));
        }
        // preserve search order while avoiding duplicate normal-install entries.
        String serverPath() {
            return String.join(
                    SEP,
                    new LinkedHashSet<>(
                            List.of(
                                    jbang.getParent().toString(),
                                    jbangDir.resolve("bin").toString(),
                                    "/usr/bin",
                                    "/bin",
                                    "/usr/sbin",
                                    "/sbin")));
        }

        Path jar() {
            return cacheDir.resolve("urls")
                    .resolve(hash(JAR_URL))
                    .resolve("fluxtion-auditlog-analyser.jar");
        }

        Path trustFile() {
            return jbangDir.resolve("trusted-sources.json");
        }

        Path appConfig() {
            return home.resolve(".fluxtion-analyser/config");
        }

        Map<String, String> serverEnv() {
            // Explicit empty values suppress inherited JVM injection/options in GUI/terminal hosts
            // alike.
            return new TreeMap<>(
                    Map.of(
                            "PATH",
                            serverPath(),
                            "JBANG_DIR",
                            jbangDir.toString(),
                            "JBANG_CACHE_DIR",
                            cacheDir.toString(),
                            "JAVA_TOOL_OPTIONS",
                            "\"-Duser.home=" + home + "\"",
                            "JDK_JAVA_OPTIONS",
                            "",
                            "_JAVA_OPTIONS",
                            "",
                            "JBANG_JAVA_OPTIONS",
                            ""));
        }

        Map<String, String> childEnv() {
            Map<String, String> e = new HashMap<>(System.getenv());
            e.putAll(serverEnv());
            e.put("HOME", home.toString());
            e.remove("CLAUDE_CONFIG_DIR");
            claudeConfigDirectory.ifPresent(d -> e.put("CLAUDE_CONFIG_DIR", d.toString()));
            return e;
        }

        List<String> appCommand(String flag) {
            // A local verified artifact cannot be replaced by JBang's URL-cache refresh at launch
            // time.
            return List.of(
                    jbang.toString(),
                    "run",
                    "--java",
                    "21",
                    "-Duser.home=" + home,
                    jar().toString(),
                    flag);
        }

        List<String> serverCommand() {
            return appCommand("--mcp");
        }
    }

    // the skill checks source before entry; plan binds its digest and performs local reads
    // only.
    public static void main(String[] args) {
        int result;
        try {
            String cmd = args.length == 0 ? "help" : args[0];
            Optional<String> source = option(args, "--program");
            if (cmd.equals("bridge") && source.isPresent()) {
                try {
                    result = pluginBridge(Machine.detect(Path.of(source.get())));
                } catch (Exception failure) {
                    if (!relayOrphaned) System.err.println(pluginBridgeFailure(failure));
                    result = 2;
                }
                System.exit(result);
                return;
            }
            if (source.isEmpty() || !(cmd.equals("plan") || cmd.equals("apply"))) {
                System.out.println(
                        "Usage: AnalyserSetup plan|apply --program <verified-source> [--plan"
                                + " <id>]");
                result = 2;
            } else {
                Machine m =
                        withRoute(
                                Machine.detect(Path.of(source.get())),
                                option(args, "--route").orElse("url"));
                result =
                        cmd.equals("plan")
                                ? plan(m)
                                : apply(m, option(args, "--plan").orElse(""), new RealOps());
            }
        } catch (Exception e) {
            // A malformed config/endpoint or OS denial must fail closed without printing private
            // contents.
            System.out.println(
                    "BLOCKED: local setup state could not be read safely ("
                            + e.getClass().getSimpleName()
                            + "). Setup stopped; if apply had started, completed effects may"
                            + " remain. Check its private run receipt.");
            result = 2;
        }
        System.exit(result);
    }

    interface Ops {
        Instance instance(Machine m) throws Exception;

        default Path bridgeJava(Machine m) throws Exception {
            Instance current = instance(m);
            if (!current.state.equals("live")) throw new IOException("No selected Analyser JVM");
            return Path.of(
                    ProcessHandle.of(current.pid).orElseThrow().info().command().orElseThrow());
        }

        default AuthIO auth() {
            return new CompilerIO();
        }

        default Path verify(Machine m) throws Exception {
            return verifiedJar(m);
        }

        default void verifyPrevious(Machine m, PinnedRelease release) throws Exception {
            verifyPinnedJar(release.jar(m), release.sha256);
        }

        String run(Machine m, int seconds, List<String> command, Path logs) throws Exception;

        OwnedProcess start(Machine m, Path logs) throws Exception;

        void ready(Machine m, OwnedProcess process, int seconds) throws Exception;

        default void context(Machine m, Path logs) throws Exception {
            probeContext(m, logs);
        }
    }

    static class RealOps implements Ops {
        public Instance instance(Machine m) throws Exception {
            return discover(m);
        }

        public String run(Machine m, int seconds, List<String> command, Path logs)
                throws Exception {
            return runBounded(m, seconds, command, logs);
        }

        public OwnedProcess start(Machine m, Path logs) throws Exception {
            return OwnedProcess.launch(m, m.appCommand("--rest"), logs.resolve("app-start.log"));
        }

        public void ready(Machine m, OwnedProcess process, int seconds) throws Exception {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (System.nanoTime() < end) {
                Instance i = instance(m);
                if (i.state.equals("live") && process.owns(i.pid)) {
                    probeContext(m, process.output.getParent());
                    Instance after = instance(m);
                    if (!after.identity.equals(i.identity) || !process.owns(after.pid))
                        throw new PlanChanged("App ownership changed during readiness check");
                    return;
                }
                if (!process.anyAlive())
                    throw new IOException("Owned application exited before readiness");
                process.checkOutput();
                Thread.sleep(100);
            }
            throw new IOException("Owned application startup timed out");
        }
    }

    static int plan(Machine m) throws Exception {
        Plan p = inspect(m, new RealOps());
        System.out.println(describe(m, p));
        return p.blocked.isEmpty() ? 0 : 2;
    }

    static Plan inspect(Machine m, Ops ops) throws Exception {
        List<Action> actions = new ArrayList<>();
        List<String> kept = new ArrayList<>(), blocked = new ArrayList<>();
        if (!Set.of("url", "claude-code-plugin", "manual").contains(m.route))
            blocked.add("Unknown setup route.");
        if (pluginRoute(m) && !validPlugin(m))
            blocked.add(
                    "The bundled plugin manifest or MCP launcher does not match this setup"
                            + " program.");
        if (!m.os.contains("mac"))
            blocked.add(
                    "Automatic setup is currently limited to macOS; use REFERENCE.md on other"
                            + " platforms.");
        if (!Files.isExecutable(m.jbang)) blocked.add("JBang was not found at " + m.jbang);
        if (m.claude.isEmpty())
            blocked.add(
                    "Claude Code (`claude`) was not found on PATH. If installed at "
                            + m.home.resolve(".local/bin/claude")
                            + ", add "
                            + m.home.resolve(".local/bin")
                            + " to PATH for both plan and apply only; otherwise use the actual"
                            + " installed Claude directory. No shell-profile edit is required.");
        if (!Files.isRegularFile(m.program)) blocked.add("Verified setup source is missing.");
        if (hasShadowRegistration(m))
            blocked.add(
                    "This project has another fluxtion-analyser entry; resolve the scope conflict"
                            + " first.");
        if (trusted(m)) kept.add("Existing release trust is kept.");
        else actions.add(new Action(Kind.TRUST, "Permanently trust only " + RELEASE_DIR));
        PinnedRelease previous = previousRelease(m);
        if (Files.exists(m.launcher, LinkOption.NOFOLLOW_LINKS)) {
            if (previous != null) {
                try {
                    ops.verifyPrevious(m, previous);
                    actions.add(new Action(Kind.UPGRADE,
                            "Upgrade: replace only the exact " + previous.version + " analyser launcher at "
                            + m.launcher + " with " + VERSION + ". First download and checksum-verify "
                            + JAR_URL + "; only then run JBang app install --force --java 21. "
                            + "Keep a private before-copy and selective undo. Keep the old trust/cache, saved AI client, external-access choice, "
                            + "plugin Java path and compiler sign-in. JBang may update shell PATH, j! and "
                            + "JAVA_HOME; keep before-copies. Close this home's old Analyser first. "
                            + "This replacement requires specific approval."));
                } catch (Exception e) {
                    blocked.add("Upgrade from " + previous.version + " cannot verify the previous pinned JAR at "
                            + previous.jar(m) + ". Its exact SHA-256 must be " + previous.sha256
                            + "; restore the official previous artifact before replanning. No launcher replacement was made.");
                }
            } else if (!validTargetLauncher(m)) {
                blocked.add("Unrecognised or modified analyser launcher at " + m.launcher
                        + " (SHA-256 " + fingerprint(m.launcher) + "). Expected the exact "
                        + VERSION + " launcher for " + JAR_URL
                        + ", or an allow-listed previous release (1.40.2, 1.40.3, 1.40.4, 1.40.5, 1.40.6 or 1.41.2) for an approved upgrade; it will not be changed.");
            } else {
                kept.add("Existing " + VERSION + " launcher is kept at " + m.launcher);
                try {
                    ops.verify(m);
                } catch (Exception e) {
                    blocked.add("The exact release URL's cached JAR is missing or differs. Use the targeted"
                            + " same-version cache recovery in REFERENCE.md, then replan.");
                }
            }
        } else
            actions.add(
                    new Action(
                            Kind.INSTALL,
                            "Install analyser 1.42.0 in "
                                    + m.launcher.getParent()
                                    + "; verify its exact cached JAR. JBang may download Java 21"
                                    + " and update shell PATH, j! and JAVA_HOME. Private"
                                    + " before-copies of affected shell files will be kept for"
                                    + " selective undo."));
        Instance i = ops.instance(m);
        if (previous != null && !i.state.equals("absent"))
            blocked.add("Close this home's Analyser " + previous.version
                    + " before the upgrade, then rerun setup. Setup will not stop it or start a competing window.");
        // v1.42.0 derives its start-screen choice from this key at startup.
        // Never overwrite an existing choice or race a running app's whole-file saves.
        String client = recordedClient(m);
        if (client != null) kept.add("Existing Analyser AI client choice is kept.");
        else if (i.state.equals("absent"))
            actions.add(
                    new Action(
                            Kind.RECORD_CLIENT,
                            "Tell the Analyser that Claude Code is your AI client in "
                                    + m.appConfig()
                                    + "; preserve all other settings. Write only while the app is"
                                    + " closed."));
        else kept.add(pluginRoute(m) ? PLUGIN_CONNECTION_NOTE : CLIENT_NOTE);
        if (i.state.equals("live"))
            kept.add("The selected home's Analyser has a live endpoint (PID " + i.pid + ").");
        else if (i.state.equals("absent"))
            actions.add(
                    new Action(
                            Kind.START_APP,
                            "Open the verified local 1.42.0 JAR for "
                                    + m.home
                                    + " with persistent external MCP/REST access enabled. Local"
                                    + " clients can use its action API until access is turned"
                                    + " off."));
        else
            blocked.add(
                    "An existing or ambiguous Analyser instance needs attention. Close the intended"
                            + " app and rerun setup, or choose the manual route; setup will not start a"
                            + " competing window.");
        Map<String, Object> old = registration(m);
        if (pluginRoute(m)) {
            if (!i.state.equals("live") || !pluginJavaMatches(m, ops.bridgeJava(m)))
                actions.add(
                        new Action(
                                Kind.PREPARE_PLUGIN,
                                "Save the verified running Analyser's Java executable for the"
                                        + " plugin at "
                                        + pluginJavaFile(m)
                                        + "; use it directly without JBang bootstrap on MCP"
                                        + " launch."));
            else kept.add("The plugin's installed Java runtime path is kept.");
            if (old == null)
                kept.add("The plugin provides the server; no user-scope registration is added.");
            else
                actions.add(
                        new Action(
                                Kind.REMOVE_USER_REGISTRATION,
                                "Remove the existing user-scope "
                                        + SERVER
                                        + " entry from "
                                        + m.claudeConfig
                                        + " (fingerprint "
                                        + hash(canonical(old))
                                        + "); retain a private backup and selective undo. The"
                                        + " plugin provides the server."));
        } else if (old == null)
            actions.add(
                    new Action(
                            Kind.REGISTER,
                            "Register "
                                    + SERVER
                                    + " at user scope in "
                                    + m.claudeConfig
                                    + " with the exact local-JAR command and environment below."));
        else if (matches(m, old)) kept.add("The exact user-scope registration is kept.");
        else
            actions.add(
                    new Action(
                            Kind.REPLACE_REGISTRATION,
                            "Replace only "
                                    + SERVER
                                    + " at user scope in "
                                    + m.claudeConfig
                                    + (appLauncherRegistration(m, old)
                                            ? " (this entry uses the analyser launcher, as the app"
                                                    + " Set up AI route does; setup uses the pinned"
                                                    + " cached JAR)"
                                            : "")
                                    + " (existing entry fingerprint "
                                    + hash(canonical(old))
                                    + "); retain a private backup and restore it if replacement"
                                    + " fails."));
        if (m.compiler.isPresent()) {
            CompilerSetup c = m.compiler.get();
            if (c.ci && !keepsCredential(c))
                blocked.add(
                        "Interactive compiler sign-in is blocked in CI; choose without sign-in or"
                                + " use the documented CI credential route.");
            if (keepsCredential(c))
                kept.add(
                        "Existing build sign-in at "
                                + c.base
                                + " is kept (not validated by this local plan).");
            else
                actions.add(
                        new Action(
                                Kind.SIGN_IN,
                                "Sign in to the Fluxtion compiler at "
                                        + c.base
                                        + ". Your browser opens for approval; save the credential"
                                        + " owner-only at "
                                        + c.file
                                        + (credential(c) == null
                                                ? "."
                                                : "; replace this file and revoke its prior"
                                                        + " credential only after the new one is"
                                                        + " saved.")
                                        + " Signing in charges nothing; free plan limits apply."));
        }
        Map<String, String> snapshot = snapshot(m, i);
        return new Plan(actions, kept, blocked, snapshot, planId(m, actions, snapshot));
    }
    // identify the app-style launcher entry without printing its private environment.
    static boolean appLauncherRegistration(Machine m, Map<String, Object> entry) {
        return (Objects.equals(entry.get("command"), m.launcher.toString())
                        || Objects.equals(entry.get("command"), "analyser"))
                && Objects.equals(entry.get("args"), List.of("--mcp"));
    }

    static String describe(Machine m, Plan p) {
        // the human decision comes before implementation details; planId still binds both.
        StringBuilder s = new StringBuilder();
        p.kept.forEach(x -> s.append("Kept: ").append(x).append('\n'));
        if (!p.blocked.isEmpty()) {
            s.append("BLOCKED:\n");
            p.blocked.forEach(x -> s.append("- ").append(x).append('\n'));
        } else if (p.actions.isEmpty())
            s.append(
                    "Nothing to change in the installation. Apply performs readiness checks and"
                            + " writes a new private receipt.\n");
        else {
            s.append("Proposed changes:\n");
            int n = 1;
            for (Action a : p.actions) s.append(n++).append(". ").append(a.text).append('\n');
            s.append(
                    "Keep private per-run receipts, diagnostics and any needed backups under"
                        + " ~/.fluxtion-analyser/setup-runs.\n"
                        + "Shall I go ahead? (yes / yes, but without the Fluxtion sign-in / no)\n");
        }
        s.append("\nDetails (for review)\nSetup route: ")
                .append(m.route)
                .append("\nSetup scope: ")
                .append(m.home)
                .append("\nClaude executable: ")
                .append(m.claude.map(Path::toString).orElse("absent"))
                .append("\nClaude config: ")
                .append(m.claudeConfig)
                .append("\nJBang cache: ")
                .append(m.cacheDir)
                .append("\nCommand: ")
                .append(Json.write(m.serverCommand()))
                .append("\nPer-server environment: ")
                .append(Json.write(m.serverEnv()))
                .append('\n');
        s.append("plan-id: ").append(p.id).append('\n');
        if (p.actions.stream().anyMatch(a -> a.kind == Kind.SIGN_IN))
            s.append("plan-id-without-signin: ").append(withoutSignIn(m, p).id).append('\n');
        return s.toString();
    }
    // both approvals bind their own exact action list; no mutable skip flag after approval.
    static Plan withoutSignIn(Machine m, Plan p) {
        var actions = p.actions.stream().filter(a -> a.kind != Kind.SIGN_IN).toList();
        var blocked =
                p.blocked.stream()
                        .filter(b -> !b.startsWith("Interactive compiler sign-in is blocked in CI"))
                        .toList();
        return new Plan(actions, p.kept, blocked, p.snapshot, planId(m, actions, p.snapshot));
    }

    static Map<String, String> snapshot(Machine m, Instance instance) throws Exception {
        Map<String, String> s = new TreeMap<>();
        if (m.compiler.isPresent()) {
            CompilerSetup c = m.compiler.get();
            s.put("compilerUrl", c.base.toString());
            s.put("credentialPath", c.file.toString());
            s.put("credentialState", credentialState(c));
            s.put("compilerCi", Boolean.toString(c.ci));
            s.put("renewSignIn", Boolean.toString(c.renew));
        }
        s.put("program", fingerprint(m.program));
        if (pluginRoute(m))
            for (String file : PLUGIN_FILES)
                s.put("plugin:" + file, fileState(pluginRoot(m).resolve(file)));
        if (pluginRoute(m)) s.put("pluginJava", fileState(pluginJavaFile(m)));
        s.put("jbang", fingerprint(m.jbang));
        s.put("claude", m.claude.isPresent() ? fingerprint(m.claude.get()) : "absent");
        s.put("launcher", fingerprint(m.launcher));
        s.put("launcherMode", Files.isRegularFile(m.launcher, LinkOption.NOFOLLOW_LINKS)
                ? PosixFilePermissions.toString(Files.getPosixFilePermissions(m.launcher)) : "not-regular");
        s.put("jar", fingerprint(m.jar()));
        for (PinnedRelease release : UPGRADE_FROM)
            s.put("previousJar:" + release.version, fingerprint(release.jar(m)));
        s.put("trust", fingerprint(m.trustFile()));
        s.put("registration", hash(canonical(registration(m))));
        s.put("appAccess", Boolean.toString(externalAccessConfigured(m)));
        s.put("appClient", clientChoice(m));
        // bind the complete closed config and mode; the running app owns unrelated settings.
        s.put("closedConfig", instance.state.equals("absent") ? configState(m) : "app-owned");
        s.put("shadow", Boolean.toString(hasShadowRegistration(m)));
        s.put("instance", instance.identity);
        for (Path p : profileFiles(m)) s.put("profile:" + p, fingerprint(p));
        return s;
    }

    static String planId(Machine m, List<Action> actions, Map<String, String> snapshot) {
        Map<String, Object> value = new TreeMap<>();
        value.put("route", m.route);
        value.put("schema", SCHEMA);
        value.put("version", VERSION);
        value.put("home", m.home.toString());
        value.put(
                "claudeConfigOverride",
                m.claudeConfigDirectory.map(Path::toString).orElse("default"));
        value.put("config", m.claudeConfig.toString());
        value.put("claude", m.claude.map(Path::toString).orElse(""));
        value.put("cache", m.cacheDir.toString());
        value.put("program", m.program.toString());
        value.put("cwd", m.cwd.toString());
        value.put("command", m.serverCommand());
        value.put("environment", m.serverEnv());
        value.put("snapshot", snapshot);
        value.put("actions", actions.stream().map(a -> List.of(a.kind.name(), a.text)).toList());
        return hash(canonical(value));
    }
    // destinations, effective vectors and before-state are rechecked between effects.
    static class PlanChanged extends IOException {
        PlanChanged(String message) {
            super(message);
        }
    }

    static void unchanged(Machine m, Ops ops, Map<String, String> expected) throws Exception {
        if (!snapshot(m, ops.instance(m)).equals(expected))
            throw new PlanChanged("State changed during setup; replan before any further action");
    }

    static void acceptOwnedChanges(
            Machine m, Ops ops, Map<String, String> expected, Set<String> owned) throws Exception {
        Map<String, String> now = snapshot(m, ops.instance(m));
        for (String key : now.keySet())
            if (!owned.contains(key) && !Objects.equals(now.get(key), expected.get(key)))
                throw new PlanChanged(
                        "Concurrent state change detected; no further setup actions will run");
        for (String key : owned) expected.put(key, now.get(key));
    }

    static int apply(Machine m, String approved, Ops ops) throws Exception {
        Plan p = inspect(m, ops);
        if (p.actions.stream().anyMatch(a -> a.kind == Kind.SIGN_IN)
                && withoutSignIn(m, p).id.equals(approved)) p = withoutSignIn(m, p);
        if (!p.blocked.isEmpty()) {
            System.out.println(describe(m, p));
            return 2;
        }
        if (!p.id.equals(approved)) {
            System.out.println("PLAN CHANGED. No setup action was applied.\n" + describe(m, p));
            return 3;
        }
        Path base = m.receipt.getParent();
        privateDirectory(base);
        // Concurrent setup invocations never share journals or apply simultaneously in this home.
        try (FileChannel channel =
                        FileChannel.open(
                                base.resolve("setup.lock"),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE);
                FileLock lock = channel.tryLock()) {
            if (lock == null) {
                System.out.println("BLOCKED: another setup owns this home.");
                return 2;
            }
            return applyLocked(m, p, ops);
        }
    }

    static int applyLocked(Machine m, Plan p, Ops ops) throws Exception {
        Path runDir =
                Files.createTempDirectory(
                        privateDirectory(m.receipt.getParent().resolve("setup-runs")),
                        "run-",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
        Map<String, Object> receipt = new LinkedHashMap<>();
        List<Object> effects = new ArrayList<>();
        List<String> undo = new ArrayList<>();
        receipt.put("route", m.route);
        receipt.put("analyserVersion", VERSION);
        receipt.put("planId", p.id);
        receipt.put("time", Instant.now().toString());
        // make the deliberate app-UI/setup registration difference visible in the receipt.
        receipt.put(
                "registrationRecovery",
                "The URL registration and plugin bridge use the verified cached JAR; the app Set up"
                    + " AI route registers the analyser launcher. Switching routes may request"
                    + " replacement. Connection closed after a cache clear: rerun setup and follow"
                    + " its targeted cache recovery; the bridge does not fetch missing bytes.");
        receipt.put("beforeFingerprints", p.snapshot);
        receipt.put("effects", effects);
        receipt.put("undo", undo);
        // setup cannot observe confirmation in the calling session.
        receipt.put(
                "nativeConfirmation",
                pluginRoute(m)
                        ? "not assessed by setup; owned by the current assistant session"
                        : "pending");
        Map<String, String> expected = new TreeMap<>(p.snapshot);
        OwnedProcess app = null;
        Progress progress = new Progress(runDir.resolve("progress.log"), p.actions.size() + 1);
        privateWrite(
                m.receipt.resolveSibling("setup-progress.json"),
                Json.write(Map.of("planId", p.id, "path", progress.file.toString())));
        System.out.println("progress-file: " + progress.file);
        Map<String, String> statuses = new LinkedHashMap<>();
        receipt.put("statuses", statuses);
        try {
            for (Action action : p.actions) {
                if (action.kind == Kind.SIGN_IN)
                    continue; // Sign-in follows successful installation/host checks.
                unchanged(m, ops, expected);
                progress.stage(Progress.wording(action.kind));
                Map<String, Object> effect = new LinkedHashMap<>();
                effect.put("kind", action.kind.name());
                effect.put("status", "attempted");
                effects.add(effect);
                writeReceipt(m, runDir, receipt, false);
                Set<String> owned = new HashSet<>();
                switch (action.kind) {
                    case TRUST -> {
                        try {
                            ops.run(
                                    m,
                                    120,
                                    List.of(m.jbang.toString(), "trust", "add", RELEASE_DIR),
                                    runDir);
                        } finally {
                            if (trusted(m)) {
                                owned.add("trust");
                                undo.add(
                                        "Remove only the newly added trust rule "
                                                + RELEASE_DIR
                                                + " using this JBang installation: "
                                                + m.jbang);
                            }
                        }
                        if (!trusted(m))
                            throw new IOException(
                                    "Trust command did not persist the exact release rule");
                    }
                    case INSTALL, UPGRADE -> {
                        Map<Path, byte[]> before = backupProfiles(m, runDir);
                        boolean upgrade = action.kind == Kind.UPGRADE;
                        if (upgrade) {
                            PinnedRelease previous = previousRelease(m);
                            if (previous == null || !ops.instance(m).state.equals("absent"))
                                throw new PlanChanged("Close the previous Analyser and replan the upgrade");
                            ops.verifyPrevious(m, previous);
                            boolean cached = true;
                            try { ops.verify(m); } catch (Exception missing) { cached = false; }
                            if (!cached) {
                                ops.run(m, 900, List.of(m.jbang.toString(), "info", "jar", "--fresh", JAR_URL), runDir);
                                ops.verify(m);
                                acceptOwnedChanges(m, ops, expected, Set.of("jar"));
                            }
                            effect.put("downloadVerifiedBeforeSwitch", true);
                            unchanged(m, ops, expected);
                            if (!ops.instance(m).state.equals("absent"))
                                throw new PlanChanged("An Analyser opened during download; close it and replan");
                            Path backup = runDir.resolve("previous-analyser-launcher");
                            privateWrite(backup, new String(readOptional(m.launcher), StandardCharsets.UTF_8));
                            effect.put("previousVersion", previous.version);
                            effect.put("previousJar", previous.jar(m).toString());
                            effect.put("previousJarSha256", previous.sha256);
                            effect.put("launcherBackup", backup.toString());
                            effect.put("previousLauncherMode", PosixFilePermissions.toString(Files.getPosixFilePermissions(m.launcher)));
                            effect.put("previousLauncherFingerprint", fingerprint(backup));
                            undo.add("Only while this home's Analyser is closed: restore the launcher from "
                                    + backup + " with mode " + effect.get("previousLauncherMode")
                                    + " only if the current launcher is absent or its fingerprint is still "
                                    + hash(launcherText(JAR_URL))
                                    + ". Preserve later edits; keep the previous JAR/cache and trust. "
                                    + "Use a plugin that allows the restored release; this plugin also supports 1.40.2, 1.40.3, 1.40.4, 1.40.5 and 1.40.6.");
                            writeReceipt(m, runDir, receipt, false);
                            unchanged(m, ops, expected);
                            if (!ops.instance(m).state.equals("absent") || previousRelease(m) == null)
                                throw new PlanChanged("An Analyser or launcher changed before the upgrade; close it and replan");
                            ops.verifyPrevious(m, previous);
                            ops.verify(m);
                        }
                        List<String> installCommand = new ArrayList<>(List.of(m.jbang.toString(), "app", "install"));
                        if (upgrade) installCommand.add("--force");
                        installCommand.addAll(List.of("--java", "21", "--name", "analyser", JAR_URL));
                        try {
                            ops.run(m, 900, installCommand, runDir);
                            if (!validTargetLauncher(m))
                                throw new IOException(
                                        "Installer did not produce the exact pinned launcher");
                            ops.verify(m);
                        } finally {
                            if (!upgrade && validTargetLauncher(m))
                                undo.add(
                                        "This run created "
                                                + m.launcher
                                                + ". Uninstall only if its current fingerprint is"
                                                + " still "
                                                + fingerprint(m.launcher));
                            for (Path f : before.keySet())
                                if (!Arrays.equals(before.get(f), readOptional(f))) {
                                    undo.add(
                                            before.get(f) == null
                                                    ? "This run created shell file "
                                                            + f
                                                            + "; remove only its added lines after"
                                                            + " reviewing later edits."
                                                    : "Review the private before-copy for "
                                                            + f
                                                            + " under "
                                                            + runDir
                                                            + "; undo only this run's added lines,"
                                                            + " preserving later edits.");
                                }
                        }
                        owned.addAll(List.of("launcher", "launcherMode", "jar"));
                        for (Path f : profileFiles(m)) owned.add("profile:" + f);
                    }
                    case RECORD_CLIENT -> {
                        recordClient(m, ops, expected.get("closedConfig"), effect, undo);
                        owned.addAll(List.of("appClient", "closedConfig"));
                    }
                    case START_APP -> {
                        ops.verify(m);
                        boolean accessWasOn = externalAccessConfigured(m);
                        app = ops.start(m, runDir);
                        effect.put("startedPid", app.pid());
                        undo.add(
                                "This run started PID "
                                        + app.pid()
                                        + "; close only that app if it is still the same process.");
                        if (!accessWasOn)
                            undo.add(
                                    "This startup requested persistent external access. If it was"
                                        + " enabled, turn it off in this home's Analyser; preserve"
                                        + " any later owner choice.");
                        ops.ready(m, app, 150);
                        if (!app.owns(ops.instance(m).pid))
                            throw new PlanChanged(
                                    "A different app published the endpoint during startup");
                        owned.addAll(List.of("instance", "appAccess", "closedConfig"));
                    }
                    case SIGN_IN -> throw new IllegalStateException("Sign-in must run last");
                    case PREPARE_PLUGIN -> {
                        preparePlugin(m, ops, runDir, effect, undo, expected.get("pluginJava"));
                        owned.add("pluginJava");
                    }
                    case REMOVE_USER_REGISTRATION -> {
                        removeUserRegistration(
                                m, ops, runDir, effect, undo, expected.get("registration"));
                        owned.add("registration");
                    }
                    case REGISTER, REPLACE_REGISTRATION -> {
                        replaceRegistration(
                                m,
                                ops,
                                runDir,
                                effect,
                                undo,
                                action.kind == Kind.REPLACE_REGISTRATION);
                        owned.add("registration");
                    }
                }
                effect.put("status", "completed");
                acceptOwnedChanges(m, ops, expected, owned);
                writeReceipt(m, runDir, receipt, false);
                System.out.println("done: " + action.kind);
            }
            progress.stage("Checking the connection");
            unchanged(m, ops, expected);
            ops.verify(m);
            if (pluginRoute(m)
                    ? registration(m) != null || !validPlugin(m)
                    : !matches(m, registration(m)))
                throw new IOException("Registration verification failed");
            if (!ops.instance(m).state.equals("live"))
                throw new IOException("The selected application's endpoint is not live");
            ops.context(m, runDir);
            receipt.put("diagnosticContext", "ok; not a native assistant call");
            // a subprocess CLI cannot certify this interactive session's plugin
            // connection.
            if (!pluginRoute(m)) {
                String check =
                        ops.run(
                                m,
                                120,
                                List.of(m.claude.orElseThrow().toString(), "mcp", "get", SERVER),
                                runDir);
                boolean connected =
                        check.lines()
                                .filter(l -> l.contains("Status:"))
                                .anyMatch(
                                        l ->
                                                l.replaceAll("\\u001B\\[[;\\d]*m", "")
                                                        .matches(
                                                                ".*Status:\\s*(?:[✔✓]\\s*)?Connected\\s*"));
                if (!connected) throw new IOException("Host bridge check did not report Connected");
            }
            unchanged(m, ops, expected);
            if (app != null && !app.owns(ops.instance(m).pid))
                throw new PlanChanged("Application ownership changed before completion");
            // sign-in and duplicate removal do not restart the bridge.
            boolean pluginReconnect =
                    p.actions.stream()
                            .anyMatch(
                                    action ->
                                            action.kind == Kind.START_APP
                                                    || action.kind == Kind.PREPARE_PLUGIN);
            if (pluginRoute(m)) receipt.put("pluginReconnectRecommended", pluginReconnect);
            receipt.put(
                    "hostCheck",
                    pluginRoute(m)
                            ? "current assistant session not assessed by setup"
                            : Status.CONNECTED.text);
            receipt.put(
                    "result",
                    pluginRoute(m)
                            ? "Plugin declared; native confirmation is assessed by the current assistant"
                                  + " session"
                            : "Connected; native confirmation pending");
            statuses.put(Status.INSTALLED.text, VERSION + " checksum-verified");
            statuses.put(Status.OPEN.text, "selected Analyser instance");
            statuses.put(Status.ACCESS.text, "local action API enabled");
            statuses.put(
                    pluginRoute(m) ? Status.PLUGIN_DECLARED.text : Status.REGISTERED.text,
                    pluginRoute(m)
                            ? "Claude Code plugin declaration checked; current session not"
                                  + " inspected"
                            : "Claude Code");
            if (!pluginRoute(m))
                statuses.put(
                        Status.CONNECTED.text,
                        "Claude Code bridge and diagnostic context answered");
            if (app != null) {
                app.detach();
                app = null;
            }
            if (p.actions.stream().anyMatch(a -> a.kind == Kind.SIGN_IN)) {
                unchanged(m, ops, expected);
                Map<String, Object> effect = new LinkedHashMap<>();
                effect.put("kind", "SIGN_IN");
                effect.put("status", "attempted");
                effects.add(effect);
                writeReceipt(m, runDir, receipt, false);
                String result =
                        signIn(
                                m.compiler.orElseThrow(),
                                ops.auth(),
                                receipt,
                                undo,
                                new SignInProgress() {
                                    public void waiting(String code, URI page) throws Exception {
                                        progress.stage(
                                                Progress.wording(Kind.SIGN_IN)
                                                        + " (code "
                                                        + code
                                                        + "); manual page: "
                                                        + page);
                                    }

                                    public void browserUnavailable() throws Exception {
                                        progress.note(
                                                "Browser did not open; open the manual page above"
                                                        + " and enter the displayed code.");
                                    }
                                });
                effect.put(
                        "status",
                        result.equals("declined") ? "declined; prior setup retained" : "completed");
                acceptOwnedChanges(m, ops, expected, Set.of("credentialState"));
            }
            if (m.compiler.isPresent()) {
                if ("signed in for builds".equals(receipt.get("compilerSignIn"))
                        && !Boolean.TRUE.equals(receipt.get("compilerCredentialRefused")))
                    statuses.put(
                            Status.SIGNED_IN.text,
                            "Fluxtion compiler: account "
                                    + receipt.get("compilerAccount")
                                    + ", "
                                    + receipt.getOrDefault("compilerUsage", "plan unavailable"));
                else if (Boolean.TRUE.equals(receipt.get("compilerCredentialRefused")))
                    statuses.put(
                            Status.NOT_SIGNED_IN.text,
                            "saved build credential refused; rerun with -Dfluxtion.renewSignIn=true"
                                    + " and approve replacement");
                else if (targetCredential(m.compiler.get())) {
                    CompilerSetup c = m.compiler.get();
                    StoredCredential saved = credential(c);
                    try {
                        AuthReply usage =
                                ops.auth().request(c.base, "GET", "/v1/usage", null, saved.token);
                        if (usage.status == 401) {
                            statuses.put(
                                    Status.NOT_SIGNED_IN.text,
                                    "saved build credential refused; rerun with"
                                        + " -Dfluxtion.renewSignIn=true and approve replacement");
                            receipt.put(
                                    "compilerSignIn",
                                    "saved credential refused; retained for explicit replacement");
                        } else if (usage.status == 200) {
                            String summary = usageSummary(usage.body);
                            receipt.put("compilerUsage", summary);
                            statuses.put(
                                    Status.SIGNED_IN.text,
                                    "Fluxtion compiler: account "
                                            + safeAccount(saved.account)
                                            + ", "
                                            + summary);
                        } else
                            statuses.put(
                                    Status.SIGNED_IN.text,
                                    "saved build credential retained; live validation unavailable");
                    } catch (Exception unavailable) {
                        statuses.put(
                                Status.SIGNED_IN.text,
                                "saved build credential retained; live validation unavailable");
                    }
                } else {
                    statuses.put(
                            Status.NOT_SIGNED_IN.text,
                            "sign-in skipped or declined; builds cannot generate until you sign"
                                    + " in");
                    System.out.println("Not signed in. Rerun setup to sign in for builds.");
                }
            }
            unchanged(m, ops, expected);
            if (statuses.containsKey(Status.SIGNED_IN.text))
                System.out.println(
                        "Signed in for builds. Analyser 1.42.0 shares this sign-in with Maven.");
            // the plugin owns the connection. The app's user-scope registration UI cannot
            // detect it in 1.42.0 and can offer a duplicate; retain this note even on idle reruns.
            if (pluginRoute(m)) {
                receipt.put("pluginConnectionNote", PLUGIN_CONNECTION_NOTE);
                System.out.println(PLUGIN_CONNECTION_NOTE);
            }
            // a kept/running app reads its own client choice; registration alone cannot update
            // it. on the plugin route do not direct the person into user-scope registration.
            if (!"CLAUDE_CODE".equals(clientChoice(m))
                    || "IN_APP"
                            .equals(
                                    appSettings(m)
                                            .getProperty("assistant.claudeCode.profile", "")
                                            .trim())) {
                String clientNote = pluginRoute(m)
                        ? "The Analyser start screen still reflects its saved client choice. "
                          + "This does not assess the plugin connection. " + PLUGIN_CONNECTION_NOTE
                        : CLIENT_NOTE;
                receipt.put("clientDisplayNote", clientNote);
                System.out.println(clientNote);
            }
            // absolute MCP paths work without a shell PATH edit. Do not claim one happened.
            String terminalNote =
                    "To use analyser from a terminal, ensure "
                            + m.launcher.getParent()
                            + " is on PATH. Setup does not guarantee shell PATH changes.";
            receipt.put("terminalPathNote", terminalNote);
            System.out.println(terminalNote);
            statuses.forEach((name, detail) -> System.out.println(name + ": " + detail));
            System.out.println(
                    pluginRoute(m)
                            ? (pluginReconnect
                                    ? "Plugin declared. Reconnect the plugin in this interactive"
                                          + " session, then call its native analyser_context."
                                    : "Plugin declared. Keep an already confirmed plugin connection. If"
                                          + " not yet confirmed, call its native analyser_context;"
                                          + " reconnect only if unavailable.")
                            : "Connected. Native confirmation pending: use analyser_context;"
                                    + " restart the session only if the tool is not loaded.");
            return 0;
        } catch (Exception e) {
            receipt.put("result", "failed: " + safeFailure(e));
            for (Object item : effects) {
                @SuppressWarnings("unchecked")
                Map<String, Object> effect = (Map<String, Object>) item;
                if (effect.get("status").equals("attempted"))
                    effect.put(
                            "status", "failed or partially applied; see private recovery record");
            }
            System.out.println(
                    (e instanceof PlanChanged ? "PLAN CHANGED: " : "FAILED: ")
                            + safeFailure(e)
                            + ". Completed effects are retained; consult the private receipt and"
                            + " replan.");
            return e instanceof PlanChanged ? 3 : 1;
        } finally {
            if (app != null) {
                app.close();
                receipt.put(
                        "startupCleanup",
                        app.anyAlive()
                                ? "incomplete; owned process remains"
                                : "owned startup stopped");
            }
            receipt.put("launcher", m.launcher.toString());
            receipt.put("jar", m.jar().toString());
            receipt.put("registrationConfig", m.claudeConfig.toString());
            writeReceipt(m, runDir, receipt, true);
            System.out.println("receipt: " + runDir.resolve("receipt.json"));
        }
    }

    // bind runtime-path replacement exactly like registration replacement.
    static void preparePlugin(
            Machine m,
            Ops ops,
            Path dir,
            Map<String, Object> effect,
            List<String> undo,
            String approvedState)
            throws Exception {
        Path java = ops.bridgeJava(m).toAbsolutePath().normalize();
        if (!Files.isExecutable(java) || !java.getFileName().toString().equals("java"))
            throw new IOException("The running application's Java executable is unavailable");
        Path destination = pluginJavaFile(m);
        if (!fileState(destination).equals(approvedState))
            throw new PlanChanged("Plugin runtime path changed after approval");
        byte[] before = readOptional(destination);
        if (pluginJavaMatches(m, java)) {
            effect.put("keptExistingJavaPath", true);
            return;
        }
        Path backup = dir.resolve("previous-plugin-java.txt");
        if (before != null) privateWrite(backup, new String(before, StandardCharsets.UTF_8));
        Path temp =
                Files.createTempFile(
                        privateDirectory(destination.getParent()),
                        ".runtime-",
                        ".tmp",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rw-------")));
        try {
            Files.writeString(temp, java + "\n", StandardCharsets.UTF_8);
            if (!ops.bridgeJava(m).toAbsolutePath().normalize().equals(java)
                    || !fileState(destination).equals(approvedState))
                throw new PlanChanged(
                        "Plugin runtime path or Analyser JVM changed during preparation");
            Files.move(
                    temp,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            effect.put("javaExecutable", java.toString());
            effect.put("writtenFingerprint", fingerprint(destination));
            effect.put("previousPathPresent", before != null);
            undo.add(
                    (before == null
                                    ? "Remove only this run's newly created plugin runtime path "
                                            + destination
                                    : "Restore only the plugin runtime path from private"
                                            + " before-copy "
                                            + backup)
                            + " only while its fingerprint is still "
                            + fingerprint(destination)
                            + "; preserve later setup choices.");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static Path pluginJavaFile(Machine m) {
        return m.home.resolve(".fluxtion-analyser/setup-plugin-java");
    }

    static boolean pluginJavaMatches(Machine m, Path java) throws Exception {
        byte[] current = readOptional(pluginJavaFile(m));
        return current != null
                && new String(current, StandardCharsets.UTF_8)
                        .equals(java.toAbsolutePath().normalize() + "\n");
    }

    static String fileState(Path path) throws Exception {
        if (Files.isSymbolicLink(path)) throw new IOException("Refusing symlink plugin state");
        return fingerprint(path)
                + ":"
                + (Files.exists(path)
                        ? PosixFilePermissions.toString(Files.getPosixFilePermissions(path))
                        : "absent");
    }

    static boolean pluginRoute(Machine m) {
        return m.route.equals("claude-code-plugin");
    }

    static Machine withRoute(Machine m, String route) {
        return new Machine(
                m.home,
                m.jbangDir,
                m.cacheDir,
                m.jbang,
                m.launcher,
                m.endpoint,
                m.receipt,
                m.claudeConfig,
                m.claude,
                m.program,
                m.cwd,
                m.os,
                m.claudeConfigDirectory,
                m.compiler,
                route);
    }

    static final List<String> PLUGIN_FILES =
            List.of(
                    ".claude-plugin/plugin.json",
                    ".mcp.json",
                    "scripts/launch.sh",
                    "scripts/runtime.sh",
                    "bin/analyser-setup");

    static Path pluginRoot(Machine m) {
        return m.program.getParent().getParent();
    }

    static boolean validPlugin(Machine m) throws Exception {
        Path root = pluginRoot(m);
        if (!m.program.equals(root.resolve("scripts/AnalyserSetup.java"))) return false;
        for (String name : PLUGIN_FILES) if (!Files.isRegularFile(root.resolve(name))) return false;
        if (!Files.isExecutable(root.resolve("scripts/launch.sh"))
                || !Files.isExecutable(root.resolve("bin/analyser-setup"))) return false;
        Object manifest = Json.read(Files.readString(root.resolve(".claude-plugin/plugin.json")));
        Object config = Json.read(Files.readString(root.resolve(".mcp.json")));
        return manifest instanceof Map<?, ?> map
                && SERVER.equals(map.get("name"))
                && config.equals(
                        Map.of(
                                "mcpServers",
                                Map.of(
                                        SERVER,
                                        Map.of(
                                                "command",
                                                "${CLAUDE_PLUGIN_ROOT}/scripts/launch.sh"))));
    }

    // distinguish a closed app from ambiguous or invalid setup state.
    static class PluginAppClosed extends IOException {
        String version;
        Path launcher;
    }

    static class PluginUpgradeRequired extends IOException {
        final String previous, required;
        PluginUpgradeRequired(String previous, String required) {
            this.previous = previous;
            this.required = required;
        }
    }

    static class PluginArtifactInvalid extends IOException {
        final String version;
        PluginArtifactInvalid(String version) { this.version = version; }
    }

    static void requireLivePlugin(Instance instance) throws IOException {
        if (instance.state.equals("absent")) throw new PluginAppClosed();
        if (!instance.state.equals("live")) throw new IOException("Selected Analyser is not ready");
    }

    static String pluginBridgeFailure(Exception failure) {
        if (failure instanceof PluginUpgradeRequired upgrade)
            return "Analyser " + upgrade.previous + " is installed; this plugin needs " + upgrade.required
                    + ". Run /fluxtion-analyser:setup to upgrade. No downloads attempted.";
        if (failure instanceof PluginArtifactInvalid artifact)
            return "Analyser " + artifact.version + " is installed, but its pinned cached JAR could not be verified. "
                    + "Run /fluxtion-analyser:setup for "
                    + (artifact.version.equals(VERSION) ? "same-version recovery" : "upgrade")
                    + " guidance. No downloads attempted.";
        if (failure instanceof PluginAppClosed closed && closed.version != null && !closed.version.equals(VERSION))
            return "Analyser " + closed.version + " is closed. Reopen it without upgrading using the existing launcher "
                    + closed.launcher + ", then /mcp reconnect plugin:fluxtion-analyser:fluxtion-analyser"
                    + " and confirm with native analyser_context. Alternatively run /fluxtion-analyser:setup"
                    + " to review an upgrade to " + VERSION + ". No downloads attempted.";
        if (failure instanceof PluginAppClosed)
            return "Open the Analyser with /fluxtion-analyser:setup, then /mcp reconnect"
                       + " plugin:fluxtion-analyser:fluxtion-analyser and confirm with native"
                       + " analyser_context. No downloads attempted.";
        return "Analyser bridge unavailable; run /fluxtion-analyser:setup. No downloads attempted.";
    }

    // use the approved installed JVM directly for both helper and verified local JAR.
    // Never invoke JBang's bootstrap here: it may download before processing --offline.
    static int pluginBridge(Machine m) throws Exception {
        PinnedRelease selected = installedRelease(m);
        if (!m.os.contains("mac") || selected == null || !Files.isExecutable(m.jbang))
            throw new IOException("Setup required");
        requireCompatible(selected);
        try { verifyPinnedJar(selected.jar(m), selected.sha256); }
        catch (IOException invalid) { throw new PluginArtifactInvalid(selected.version); }
        Instance instance = endpointState(m, SYSTEM_ENDPOINTS, selected.jar(m));
        if (instance.state.equals("absent")) instance = discover(m);
        try { requireLivePlugin(instance); }
        catch (PluginAppClosed closed) {
            closed.version = selected.version;
            closed.launcher = m.launcher();
            throw closed;
        }
        // Direct JVM invocation is essential: JBang's shell may download before parsing --offline.
        var command =
                List.of(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-Duser.home=" + m.home,
                        "-jar",
                        selected.jar(m).toString(),
                        "--mcp");
        return relayChild(m, selected, command);
    }

    static final Duration ORPHAN_POLL = Duration.ofSeconds(5);
    // Set before the watchdog exits so a racing main thread does not report a setup failure.
    static volatile boolean relayOrphaned;

    // Start the --mcp child and relay stdio until EOF, child exit, or orphaning of this relay.
    static int relayChild(Machine m, PinnedRelease selected, List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.environment().clear();
        builder.environment().putAll(m.childEnv());
        Process bridge = builder.start();
        Thread stop =
                new Thread(
                        () -> {
                            bridge.descendants().forEach(ProcessHandle::destroy);
                            bridge.destroy();
                        });
        Runtime.getRuntime().addShutdownHook(stop);
        // launch.sh execs this JVM, so the parent recorded here is Claude Code itself. If Claude is
        // killed while another process still holds our stdin, EOF never arrives; the parent check
        // is then the only signal, and the child (whose parent is this live relay) cannot see it.
        // Always installed: an unavailable parent at start counts as orphaned, never as "no watchdog".
        Thread watchdog =
                orphanWatchdog(
                        ProcessHandle.current().parent(),
                        ORPHAN_POLL,
                        () -> {
                            relayOrphaned = true;
                            System.err.println("Analyser plugin bridge: Claude Code has gone; stopping the bridge.");
                            stop.run();
                            System.exit(0);
                        });
        try {
            relayPlugin(m, selected, bridge);
            return bridge.waitFor();
        } finally {
            watchdog.interrupt();
            if (bridge.isAlive()) stop.run();
            try { Runtime.getRuntime().removeShutdownHook(stop); }
            catch (IllegalStateException shuttingDown) { /* watchdog exit already in progress; the hook runs stop */ }
        }
    }

    // originalAlive is ProcessHandle.isAlive() of the handle taken at start, which also compares
    // the start time, so a reused pid never counts. A zombie parent still reads as alive, but once
    // a parent exits its children are re-parented (launchd/init), so the current parent pid
    // changes too; both must hold. A recorded parent of pid 1 means Claude had already gone before
    // the watchdog started (e.g. during the source-mode compile), so that is orphaned at once.
    static boolean attached(long originalPid, boolean originalAlive, Optional<Long> currentParentPid) {
        return originalPid != 1 && originalAlive && currentParentPid.map(pid -> pid == originalPid).orElse(false);
    }

    // The watchdog for a relay whose parent at start was `original`. It is always started; an
    // unavailable initial parent (empty) means the relay cannot prove it is attached, so it is
    // treated as orphaned at once, mirroring the Analyser's McpBridge.launch.
    static Thread orphanWatchdog(Optional<ProcessHandle> original, Duration poll, Runnable exit) {
        return exitWhenOrphaned(relayAttached(original), poll, exit);
    }

    static java.util.function.BooleanSupplier relayAttached(Optional<ProcessHandle> original) {
        return () -> original.map(p -> attached(p.pid(), p.isAlive(),
                ProcessHandle.current().parent().map(ProcessHandle::pid))).orElse(false);
    }

    static Thread exitWhenOrphaned(java.util.function.BooleanSupplier stillAttached, Duration poll, Runnable exit) {
        Thread watchdog =
                new Thread(
                        () -> {
                            try {
                                while (stillAttached.getAsBoolean()) Thread.sleep(poll.toMillis());
                                exit.run();
                            } catch (InterruptedException stopped) {
                                Thread.currentThread().interrupt();
                            }
                        },
                        "analyser-plugin-orphan-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        return watchdog;
    }

    // removing a user entry is an explicit, plan-bound effect, never implicit
    // migration.
    static void removeUserRegistration(
            Machine m,
            Ops ops,
            Path dir,
            Map<String, Object> effect,
            List<String> undo,
            String approvedFingerprint)
            throws Exception {
        Map<String, Object> old = registration(m);
        if (old == null || !hash(canonical(old)).equals(approvedFingerprint))
            throw new PlanChanged("User registration changed after approval");
        String before = hash(canonical(old));
        Path backup = dir.resolve("previous-registration.json");
        privateWrite(backup, Json.write(old));
        effect.put("backup", backup.toString());
        if (!before.equals(hash(canonical(registration(m)))))
            throw new PlanChanged("User registration changed before removal");
        try {
            ops.run(
                    m,
                    60,
                    List.of(
                            m.claude.orElseThrow().toString(),
                            "mcp",
                            "remove",
                            "--scope",
                            "user",
                            SERVER),
                    dir);
            if (registration(m) != null) throw new IOException("User registration was not removed");
            effect.put("oldEntryRemoved", true);
            undo.add(
                    "After disabling the plugin, restore only "
                            + SERVER
                            + " from private backup "
                            + backup
                            + " if the user-scope name is still absent; preserve every other"
                            + " server.");
        } catch (Exception failure) {
            if (registration(m) == null) {
                try {
                    ops.run(
                            m,
                            60,
                            List.of(
                                    m.claude.orElseThrow().toString(),
                                    "mcp",
                                    "add-json",
                                    "--scope",
                                    "user",
                                    SERVER,
                                    Json.write(old)),
                            dir);
                    effect.put(
                            "previousEntryRestored",
                            before.equals(hash(canonical(registration(m)))));
                } catch (Exception restore) {
                    effect.put("previousEntryRestored", false);
                }
            }
            undo.add(
                    "Removal failed: inspect current user-scope "
                            + SERVER
                            + " and private backup "
                            + backup
                            + "; restore only if the name is absent. Do not overwrite a later"
                            + " entry.");
            throw failure;
        }
    }

    // replacement is journalled; only our written entry may be undone or restored.
    static void replaceRegistration(
            Machine m,
            Ops ops,
            Path dir,
            Map<String, Object> effect,
            List<String> undo,
            boolean replace)
            throws Exception {
        Map<String, Object> old = registration(m);
        String before = hash(canonical(old));
        if (replace != (old != null)) throw new IOException("Registration changed before mutation");
        Path backup = dir.resolve("previous-registration.json");
        if (old != null) privateWrite(backup, Json.write(old));
        boolean removed = false;
        try {
            if (old != null) {
                if (!before.equals(hash(canonical(registration(m)))))
                    throw new IOException("Registration changed before removal");
                ops.run(
                        m,
                        60,
                        List.of(
                                m.claude.orElseThrow().toString(),
                                "mcp",
                                "remove",
                                "--scope",
                                "user",
                                SERVER),
                        dir);
                removed = registration(m) == null;
                effect.put("oldEntryRemoved", removed);
                if (!removed) throw new IOException("Old entry was not removed");
            }
            if (registration(m) != null)
                throw new IOException("Registration appeared before add; refusing overwrite");
            List<String> c =
                    new ArrayList<>(
                            List.of(
                                    m.claude.orElseThrow().toString(),
                                    "mcp",
                                    "add",
                                    "--scope",
                                    "user",
                                    "--env"));
            for (var e : m.serverEnv().entrySet()) c.add(e.getKey() + "=" + e.getValue());
            c.addAll(List.of("--transport", "stdio", SERVER, "--"));
            c.addAll(m.serverCommand());
            ops.run(m, 60, c, dir);
            if (!matches(m, registration(m)))
                throw new IOException("Written registration does not match approved values");
            effect.put("newEntryWritten", true);
            undo.add(
                    old == null
                            ? "Remove only this run's user-scope "
                                    + SERVER
                                    + " entry if unchanged; preserve other servers."
                            : "Restore only the previous user-scope entry from private backup "
                                    + backup
                                    + " if this run's entry is still unchanged; preserve other"
                                    + " servers.");
        } catch (Exception error) {
            // An add can write then fail. Restore only if no concurrent writer supplied a different
            // entry.
            Map<String, Object> now = registration(m);
            if (old != null && (removed || now == null) && (now == null || matches(m, now))) {
                try {
                    if (now != null)
                        ops.run(
                                m,
                                60,
                                List.of(
                                        m.claude.orElseThrow().toString(),
                                        "mcp",
                                        "remove",
                                        "--scope",
                                        "user",
                                        SERVER),
                                dir);
                    if (registration(m) != null)
                        throw new IOException("Concurrent entry prevents restore");
                    ops.run(
                            m,
                            60,
                            List.of(
                                    m.claude.orElseThrow().toString(),
                                    "mcp",
                                    "add-json",
                                    "--scope",
                                    "user",
                                    SERVER,
                                    Json.write(old)),
                            dir);
                    if (!before.equals(hash(canonical(registration(m)))))
                        throw new IOException("Restored entry differs");
                    effect.put("previousEntryRestored", true);
                } catch (Exception restore) {
                    effect.put("previousEntryRestored", false);
                    undo.add(
                            "Replacement failed. Restore the private previous-registration.json"
                                + " entry through Claude's user-scope configuration after reviewing"
                                + " current state: "
                                    + backup);
                }
            } else if (old != null && !before.equals(hash(canonical(now)))) {
                effect.put("previousEntryRestored", false);
                undo.add(
                        "A concurrent entry prevents automatic restoration. Review the current"
                            + " user-scope entry and the private previous-registration.json backup:"
                            + " "
                                + backup);
            } else if (old == null && matches(m, now))
                undo.add(
                        "The failed add wrote this run's user entry. Remove it only if still"
                                + " unchanged.");
            throw error;
        }
    }

    // validate the selected endpoint first; otherwise inspect only matching own-user app
    // candidates.
    record ProcessFact(boolean alive, Instant start, List<String> arguments) {}

    interface EndpointView {
        byte[] endpoint(Path path) throws Exception;

        ProcessFact process(long pid);
    }

    static final EndpointView SYSTEM_ENDPOINTS =
            new EndpointView() {
                public byte[] endpoint(Path path) throws Exception {
                    return readOptional(path);
                }

                public ProcessFact process(long pid) {
                    return ProcessHandle.of(pid)
                            .map(
                                    p ->
                                            new ProcessFact(
                                                    p.isAlive(),
                                                    p.info().startInstant().orElse(null),
                                                    Arrays.asList(
                                                            p.info()
                                                                    .arguments()
                                                                    .orElse(new String[0]))))
                            .orElse(new ProcessFact(false, null, List.of()));
                }
            };

    record AppProcess(long pid, String user, ProcessFact fact) {}

    interface ProcessInventory {
        List<AppProcess> processes();
    }

    static final ProcessInventory SYSTEM_PROCESSES =
            () -> {
                try (Stream<ProcessHandle> processes = ProcessHandle.allProcesses()) {
                    return processes
                            .map(
                                    p -> {
                                        ProcessHandle.Info info = p.info();
                                        return new AppProcess(
                                                p.pid(),
                                                info.user().orElse(""),
                                                new ProcessFact(
                                                        p.isAlive(),
                                                        info.startInstant().orElse(null),
                                                        Arrays.asList(
                                                                info.arguments()
                                                                        .orElse(new String[0]))));
                                    })
                            .toList();
                }
            };

    static Instance discover(Machine m) throws Exception {
        return discover(
                m,
                SYSTEM_ENDPOINTS,
                SYSTEM_PROCESSES,
                ProcessHandle.current().info().user().orElse(""));
    }
    // injected inventory keeps tests independent of other running applications.
    static Instance discover(
            Machine m, EndpointView endpoint, ProcessInventory inventory, String user)
            throws Exception {
        Instance state = endpointState(m, endpoint);
        if (!state.state.equals("absent")) return state;
        if (user.isBlank()) return new Instance("blocked", 0, "unknown-process-owner");
        List<AppProcess> matches = new ArrayList<>();
        for (AppProcess app : inventory.processes()) {
            ProcessFact fact = app.fact;
            if ((!app.user.isBlank() && !app.user.equals(user))
                    || !fact.alive
                    || fact.arguments.stream()
                            .anyMatch(
                                    Set.of(
                                                    "--mcp",
                                                    "--verify",
                                                    "--unpack",
                                                    "--replay-compare",
                                                    "--pack",
                                                    "--bundle-profile",
                                                    "--help",
                                                    "-h")
                                            ::contains)
                    || !(fact.arguments.contains(m.jar().toString())
                            || UPGRADE_FROM.stream().anyMatch(r -> fact.arguments.contains(r.jar(m).toString())))) continue;
            List<String> homes =
                    fact.arguments.stream().filter(a -> a.startsWith("-Duser.home=")).toList();
            if (homes.size() == 1 && !absolute(Path.of(homes.get(0).substring(12))).equals(m.home))
                continue;
            if (app.user.isBlank())
                return new Instance("blocked", app.pid, "matching-app-owner-unknown");
            matches.add(app);
        }
        if (matches.isEmpty()) return Instance.absent();
        if (matches.size() != 1)
            return new Instance("blocked", 0, "multiple-matching-app-processes");
        AppProcess app = matches.get(0);
        List<String> homes =
                app.fact.arguments.stream().filter(a -> a.startsWith("-Duser.home=")).toList();
        if (app.fact.start == null || homes.size() != 1)
            return new Instance("blocked", app.pid, "matching-app-home-or-start-unknown");
        // Never enable access or restart an existing access-disabled window behind the user's back.
        return new Instance(
                "blocked", app.pid, "open-without-endpoint:" + app.pid + ":" + app.fact.start);
    }

    static Instance endpointState(Machine m, EndpointView view) throws Exception {
        return endpointState(m, view, m.jar());
    }

    static Instance endpointState(Machine m, EndpointView view, Path selectedJar) throws Exception {
        byte[] bytes = view.endpoint(m.endpoint);
        if (bytes == null) return Instance.absent();
        try {
            Map<?, ?> ep = (Map<?, ?>) Json.read(new String(bytes, StandardCharsets.UTF_8));
            long pid = ((Number) ep.get("pid")).longValue();
            if (pid <= 0) return new Instance("blocked", 0, "invalid-pid");
            ProcessFact process = view.process(pid);
            if (!process.alive) return Instance.absent(); // A crash file is not a ready endpoint.
            Instant published = Instant.parse((String) ep.get("startedAt"));
            if (process.start == null
                    || process.start.isAfter(published)
                    || published.isAfter(Instant.now().plusSeconds(5)))
                return new Instance("blocked", pid, "unbound-process");
            // PID reuse and unrelated process files are refused; no token is needed for this
            // ownership check.
            if (!process.arguments.contains(selectedJar.toString())
                    || process.arguments.contains("--mcp"))
                return new Instance("blocked", pid, "different-artifact-or-bridge");
            Optional<String> homeArg =
                    process.arguments.stream()
                            .filter(a -> a.startsWith("-Duser.home="))
                            .findFirst();
            if (homeArg.isPresent()
                    && !absolute(Path.of(homeArg.get().substring(12))).equals(m.home))
                return new Instance("blocked", pid, "different-home");
            return new Instance("live", pid, "live:" + pid + ":" + process.start + ":" + published);
        } catch (RuntimeException invalid) {
            return new Instance("blocked", 0, "invalid-endpoint-metadata");
        }
    }

    // exact machine-tier contract in released Analyser v1.42.0, AiSetupState.Client.
    static final String CLIENT_KEY = "assistant.setup.client";
    static final String PLUGIN_CONNECTION_NOTE =
            "The connection comes from the plugin; you do not need the Analyser's Set up AI for "
                    + "Claude Code. If that route adds a user-scope fluxtion-analyser entry, rerun "
                    + "plugin setup to review its removal with a private backup. Do not add a second connection.";
    static final String CLIENT_NOTE =
            "In the Analyser, choose AI ▸ Set up AI…, pick 'Your own AI client connects'"
                    + " (not 'Claude inside'), then Claude Code.";

    static Properties appSettings(Machine m) throws Exception {
        Properties settings = new Properties();
        byte[] bytes = readOptional(m.appConfig());
        if (bytes != null)
            settings.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
        return settings;
    }

    static String recordedClient(Machine m) throws Exception {
        return appSettings(m).getProperty(CLIENT_KEY);
    }

    // The app normalises unknown/blank choices on its own save. Bind their raw closed-file
    // representation above, but compare the same semantic client state across app startup.
    static String clientChoice(Machine m) throws Exception {
        String value = Objects.toString(recordedClient(m), "").trim();
        return Set.of("CLAUDE_CODE", "CODEX", "GENERIC").contains(value) ? value : "NONE";
    }

    static String configState(Machine m) throws Exception {
        if (Files.isSymbolicLink(m.appConfig()))
            throw new IOException("Refusing symlink Analyser config");
        return fingerprint(m.appConfig())
                + ":"
                + (Files.exists(m.appConfig())
                        ? PosixFilePermissions.toString(
                                Files.getPosixFilePermissions(m.appConfig()))
                        : "absent");
    }

    static void recordClient(
            Machine m,
            Ops ops,
            String approvedConfig,
            Map<String, Object> effect,
            List<String> undo)
            throws Exception {
        if (!ops.instance(m).state.equals("absent") || !configState(m).equals(approvedConfig))
            throw new PlanChanged("Analyser or its config changed before recording the client");
        Properties settings = appSettings(m);
        if (settings.containsKey(CLIENT_KEY))
            throw new PlanChanged("An existing client choice will not be replaced");
        var mode =
                Files.exists(m.appConfig())
                        ? Files.getPosixFilePermissions(m.appConfig())
                        : PosixFilePermissions.fromString("rw-------");
        settings.setProperty(CLIENT_KEY, "CLAUDE_CODE");
        StringWriter text = new StringWriter();
        settings.store(text, "Analyser setup client choice");
        Path temp =
                Files.createTempFile(
                        privateDirectory(m.appConfig().getParent()),
                        ".client-",
                        ".tmp",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rw-------")));
        try {
            Files.writeString(temp, text.toString(), StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(temp, mode);
            // Recheck immediately before publication. The app has no shared config lock: another
            // process starting after this check remains a bounded race, not an atomic ownership
            // claim.
            if (!ops.instance(m).state.equals("absent") || !configState(m).equals(approvedConfig))
                throw new PlanChanged("Analyser or its config changed during client preparation");
            Files.move(
                    temp,
                    m.appConfig(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            effect.put("key", CLIENT_KEY);
            effect.put("previousValue", "absent");
            effect.put("writtenValue", "CLAUDE_CODE");
            undo.add(
                    "Only while this home's Analyser is closed, and only if no later client choice"
                            + " was made, remove just assistant.setup.client from "
                            + m.appConfig()
                            + " if its value is still CLAUDE_CODE. This run added that key;"
                            + " preserve every other setting and the file mode. Do not restore or"
                            + " delete the whole config.");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static boolean externalAccessConfigured(Machine m) throws Exception {
        byte[] bytes = readOptional(m.appConfig());
        if (bytes == null) return false;
        Properties p = new Properties();
        p.load(new ByteArrayInputStream(bytes));
        return Boolean.parseBoolean(p.getProperty("assistant.rest", "false"));
    }

    // owned process identities are tracked during execution, with bounded output and
    // termination waits.
    static class OwnedProcess implements AutoCloseable {
        // macOS Bash creates a separate job group before exec. The group survives an early wrapper
        // exit,
        // so timeout/EXIT cleanup does not depend on polling descendants quickly enough.
        static final String GROUP_WRAPPER =
                "set -m\n"
                    + "group_file=$1; shift\n"
                    + "child=\n"
                    + "cleanup() { trap '' HUP INT TERM; if [ -n \"$child\" ]; then kill -TERM --"
                    + " -\"$child\" 2>/dev/null || :; sleep 0.1; kill -KILL -- -\"$child\""
                    + " 2>/dev/null || :; fi; }\n"
                    + "trap cleanup EXIT\n"
                    + "trap 'exit 143' HUP INT TERM\n"
                    + "\"$@\" <&0 &\n"
                    + "child=$!\n"
                    + "printf '%s\\n"
                    + "' \"$child\" > \"$group_file\"\n"
                    + "wait \"$child\"\n"
                    + "status=$?\n"
                    + "exit \"$status\"\n";
        final Process process;
        final Path output, groupFile;
        final Map<Long, ProcessHandle> tracked = new ConcurrentHashMap<>();
        final ScheduledExecutorService watcher =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "setup-process-tracker");
                            t.setDaemon(true);
                            return t;
                        });
        final Thread shutdown;
        volatile boolean detached;

        OwnedProcess(Process p, Path output, Path groupFile) {
            this.process = p;
            this.output = output;
            this.groupFile = groupFile;
            tracked.put(p.pid(), p.toHandle());
            shutdown = new Thread(this::close, "setup-owned-cleanup");
            Runtime.getRuntime().addShutdownHook(shutdown);
            watcher.scheduleAtFixedRate(this::track, 0, 10, TimeUnit.MILLISECONDS);
        }

        static OwnedProcess launch(Machine m, List<String> command, Path output) throws Exception {
            return launch(m, command, output, false);
        }

        static OwnedProcess launch(Machine m, List<String> command, Path output, boolean pipe)
                throws Exception {
            privateWrite(output, "");
            Path group = Files.createTempFile(output.getParent(), "owned-group-", ".pid");
            privateWrite(group, "");
            List<String> vector =
                    new ArrayList<>(
                            List.of(
                                    "/bin/bash",
                                    "-c",
                                    GROUP_WRAPPER,
                                    "setup-owned-group",
                                    group.toString()));
            vector.addAll(command);
            ProcessBuilder b = new ProcessBuilder(vector).directory(m.home.toFile());
            if (pipe) b.redirectError(output.toFile());
            else b.redirectErrorStream(true).redirectOutput(output.toFile());
            b.environment().clear();
            b.environment().putAll(m.childEnv());
            Process p = b.start();
            if (!pipe) p.getOutputStream().close();
            return new OwnedProcess(p, output, group);
        }

        void track() {
            for (ProcessHandle h : List.copyOf(tracked.values())) {
                try {
                    h.descendants().forEach(d -> tracked.putIfAbsent(d.pid(), d));
                } catch (RuntimeException denied) {
                    /* Group cleanup does not depend on this optional identity view. */
                }
            }
        }

        long pid() {
            return process.pid();
        }

        boolean owns(long pid) {
            track();
            return tracked.containsKey(pid);
        }

        boolean anyAlive() {
            return tracked.values().stream().anyMatch(ProcessHandle::isAlive);
        }

        void checkOutput() throws IOException {
            if (Files.size(output) > MAX_OUTPUT)
                throw new IOException("Setup child exceeded its output limit");
        }

        void detach() {
            detached = true;
            watcher.shutdownNow();
            try {
                Runtime.getRuntime().removeShutdownHook(shutdown);
            } catch (IllegalStateException ignored) {
            }
        }

        public synchronized void close() {
            if (detached) return;
            track();
            watcher.shutdownNow();
            // Let the supervisor's TERM/EXIT trap finish. Force only this owned supervisor after
            // bounded cleanup.
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            for (ProcessHandle child : tracked.values())
                if (child.pid() != process.pid() && child.isAlive()) child.destroyForcibly();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (anyAlive() && System.nanoTime() < until) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            try {
                Runtime.getRuntime().removeShutdownHook(shutdown);
            } catch (IllegalStateException ignored) {
            }
        }
    }

    static String runBounded(Machine m, int seconds, List<String> command, Path logs)
            throws Exception {
        Path log = Files.createTempFile(logs, "command-", ".log");
        try (OwnedProcess p = OwnedProcess.launch(m, command, log)) {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (p.process.isAlive()) {
                p.checkOutput();
                if (System.nanoTime() >= end)
                    throw new IOException("Setup command timed out; see private diagnostic log");
                Thread.sleep(20);
            }
            p.checkOutput();
            if (p.process.exitValue() != 0)
                throw new IOException(
                        "Setup command failed with exit "
                                + p.process.exitValue()
                                + "; see private diagnostic log");
            byte[] out = Files.readAllBytes(log);
            return new String(
                    out,
                    Math.max(0, out.length - 65536),
                    Math.min(out.length, 65536),
                    StandardCharsets.UTF_8);
        }
    }

    // a bridge handshake is insufficient. Diagnostic tools/call must reach the selected open
    // application.
    static void probeContext(Machine m, Path logs) throws Exception {
        Path errors = Files.createTempFile(logs, "bridge-", ".log");
        try (OwnedProcess owned = OwnedProcess.launch(m, m.serverCommand(), errors, true)) {
            Process process = owned.process;
            BlockingQueue<String> lines = new ArrayBlockingQueue<>(64);
            Thread reader =
                    new Thread(
                            () -> {
                                try (InputStream in = process.getInputStream()) {
                                    ByteArrayOutputStream line = new ByteArrayOutputStream();
                                    int n;
                                    while ((n = in.read()) >= 0) {
                                        if (n == '\n') {
                                            if (!lines.offer(line.toString(StandardCharsets.UTF_8)))
                                                return;
                                            line.reset();
                                        } else {
                                            if (line.size() >= 65536) {
                                                lines.offer("invalid");
                                                return;
                                            }
                                            line.write(n);
                                        }
                                    }
                                } catch (IOException ignored) {
                                }
                            },
                            "setup-bridge-reader");
            reader.setDaemon(true);
            reader.start();
            Writer writer =
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
            writer.write(
                    Json.write(
                                    Map.of(
                                            "jsonrpc",
                                            "2.0",
                                            "id",
                                            1,
                                            "method",
                                            "initialize",
                                            "params",
                                            Map.of(
                                                    "protocolVersion",
                                                    "2024-11-05",
                                                    "capabilities",
                                                    Map.of(),
                                                    "clientInfo",
                                                    Map.of(
                                                            "name",
                                                            "analyser-setup-check",
                                                            "version",
                                                            SCHEMA))))
                            + "\n");
            writer.flush();
            response(owned, lines, 1, 60);
            writer.write("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n");
            writer.write(
                    "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"analyser_context\",\"arguments\":{}}}\n");
            writer.flush();
            Map<?, ?> reply = response(owned, lines, 2, 30);
            Object result = reply.get("result");
            boolean ok = false;
            if (result instanceof Map<?, ?> r
                    && !Boolean.TRUE.equals(r.get("isError"))
                    && r.get("content") instanceof List<?> content) {
                for (Object c : content)
                    if (c instanceof Map<?, ?> item
                            && "text".equals(item.get("type"))
                            && item.get("text") instanceof String text) {
                        try {
                            Object body = Json.read(text);
                            if (body instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("ok")))
                                ok = true;
                        } catch (RuntimeException ignored) {
                        }
                    }
            }
            if (!ok) throw new IOException("Diagnostic analyser_context did not reach a ready app");
            owned.checkOutput();
        }
    }

    static Map<?, ?> response(OwnedProcess owned, BlockingQueue<String> lines, int id, int seconds)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        // continue enforcing stderr limits while a silent bridge stalls its handshake.
        for (int count = 0; count < 64; ) {
            owned.checkOutput();
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            String line =
                    lines.poll(
                            Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(20)),
                            TimeUnit.NANOSECONDS);
            if (line == null) {
                if (!owned.process.isAlive())
                    throw new IOException("Bridge exited before diagnostic response");
                continue;
            }
            count++;
            Object value;
            try {
                value = Json.read(line);
            } catch (RuntimeException bad) {
                throw new IOException("Invalid bridge response");
            }
            if (value instanceof Map<?, ?> map
                    && map.get("id") instanceof Number n
                    && n.intValue() == id) {
                if (map.containsKey("error"))
                    throw new IOException("Bridge diagnostic request failed");
                return map;
            }
        }
        throw new IOException("Bridge diagnostic timed out");
    }

    // Compiler protocol: released Maven plugin 1.3.1, not an Analyser keychain credential.
    // renewal is explicit and bound into approval, never inferred from a
    // network check.
    record CompilerSetup(URI base, Path file, boolean ci, boolean renew) {
        CompilerSetup(URI base, Path file, boolean ci) {
            this(base, file, ci, false);
        }

        static CompilerSetup detect(Path home) {
            String url =
                    System.getProperty(
                            "fluxtion.compilerUrl", "https://compiler.fluxtion-playground.dev");
            return new CompilerSetup(
                    compilerUri(url),
                    absolute(
                            Path.of(
                                    System.getProperty(
                                            "fluxtion.credentialsFile",
                                            home.resolve(".fluxtion/credentials").toString()))),
                    isCi(System.getenv()),
                    Boolean.getBoolean("fluxtion.renewSignIn"));
        }
    }

    static boolean isCi(Map<String, String> env) {
        String ci = env.get("CI");
        if (ci != null && !ci.isBlank() && !ci.equalsIgnoreCase("false")) return true;
        for (String key :
                List.of(
                        "JENKINS_URL",
                        "TF_BUILD",
                        "TEAMCITY_VERSION",
                        "CODEBUILD_BUILD_ID",
                        "BUILDKITE",
                        "GITHUB_ACTIONS",
                        "GITLAB_CI",
                        "BITBUCKET_BUILD_NUMBER"))
            if (env.containsKey(key) && !env.get(key).isBlank()) return true;
        return false;
    }

    static URI compilerUri(String text) {
        URI u = URI.create(text.trim());
        if (!"https".equals(u.getScheme())
                || u.getHost() == null
                || u.getUserInfo() != null
                || u.getQuery() != null
                || u.getFragment() != null
                || !(u.getPath().isEmpty() || u.getPath().equals("/")))
            throw new IllegalArgumentException("Compiler URL must be an HTTPS origin");
        return URI.create(
                "https://"
                        + u.getHost().toLowerCase(Locale.ROOT)
                        + (u.getPort() == -1 || u.getPort() == 443 ? "" : ":" + u.getPort()));
    }

    static final class StoredCredential {
        final String token, id, account, url;

        StoredCredential(String token, String id, String account, String url) {
            this.token = token;
            this.id = id;
            this.account = account;
            this.url = url;
        }

        public String toString() {
            return "[private compiler credential]";
        }
    }

    static StoredCredential credential(CompilerSetup c) throws IOException {
        if (!Files.exists(c.file, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(c.file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Credential path is not a regular file");
        Properties p = new Properties();
        p.load(new ByteArrayInputStream(readOptional(c.file)));
        String token = p.getProperty("token");
        if (token == null || token.isBlank())
            throw new IOException(
                    "Existing compiler credential is incomplete; resolve it before setup");
        String url = p.getProperty("url");
        try {
            compilerUri(url);
        } catch (RuntimeException invalid) {
            throw new IOException("Stored compiler issuer is invalid");
        }
        return new StoredCredential(
                token.trim(),
                p.getProperty("credential_id"),
                p.getProperty("account_id"),
                compilerUri(url).toString());
    }

    static String credentialState(CompilerSetup c) throws IOException {
        StoredCredential old = credential(c);
        if (old == null) return "absent";
        var attrs =
                Files.readAttributes(
                        c.file,
                        java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
        // bind non-secret identity and file metadata; no token or token digest enters the
        // approval/receipt.
        return hash(
                canonical(
                        Arrays.asList(
                                old.id,
                                old.account,
                                old.url,
                                attrs.lastModifiedTime().toString(),
                                attrs.size(),
                                String.valueOf(attrs.fileKey()),
                                Files.getPosixFilePermissions(c.file).stream()
                                        .map(Enum::name)
                                        .sorted()
                                        .toList()
                                        .toString())));
    }

    static boolean targetCredential(CompilerSetup c) throws IOException {
        StoredCredential old = credential(c);
        return old != null && old.url.equals(c.base.toString());
    }

    static boolean keepsCredential(CompilerSetup c) throws IOException {
        return !c.renew && targetCredential(c);
    }

    static void writeCredential(CompilerSetup c, StoredCredential s) throws IOException {
        // CREATE_NEW and 0600 on the same open handle precede every secret byte, matching
        // CredentialStore.
        privateDirectory(c.file.getParent());
        Path tmp = c.file.resolveSibling(".compiler-credential-" + UUID.randomUUID() + ".tmp");
        Properties props = new Properties();
        props.setProperty("token", s.token);
        props.setProperty("credential_id", s.id);
        props.setProperty("account_id", s.account);
        props.setProperty("url", s.url);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        props.store(bytes, "Fluxtion compiler credential - do not commit or share");
        try {
            try (var ch =
                    Files.newByteChannel(
                            tmp,
                            Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                            PosixFilePermissions.asFileAttribute(
                                    PosixFilePermissions.fromString("rw-------")))) {
                var buf = java.nio.ByteBuffer.wrap(bytes.toByteArray());
                while (buf.hasRemaining()) ch.write(buf);
            }
            Files.move(
                    tmp,
                    c.file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    static final class AuthReply {
        final int status;
        final Map<String, Object> body;

        AuthReply(int status, Map<String, Object> body) {
            this.status = status;
            this.body = body;
        }

        public String toString() {
            return "HTTP " + status;
        }
    }

    interface AuthIO {
        AuthReply request(
                URI base, String method, String path, Map<String, Object> body, String token)
                throws Exception;

        long millis();

        void sleep(long ms) throws InterruptedException;

        void browse(URI uri) throws Exception;
    }

    static class CompilerIO implements AuthIO {
        public long millis() {
            return System.nanoTime() / 1_000_000;
        }

        public void sleep(long ms) throws InterruptedException {
            Thread.sleep(ms);
        }

        public void browse(URI uri) throws Exception {
            java.awt.Desktop.getDesktop().browse(uri);
        }

        protected java.net.HttpURLConnection connection(URI uri) throws IOException {
            return (java.net.HttpURLConnection) uri.toURL().openConnection();
        }

        public AuthReply request(
                URI base, String method, String path, Map<String, Object> body, String token)
                throws Exception {
            var con = connection(base.resolve(path));
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(10_000);
            con.setReadTimeout(30_000);
            con.setRequestMethod(method);
            long bodyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            con.setRequestProperty("X-Fluxtion-Client", "fluxtion-analyser-setup");
            con.setRequestProperty("Accept", "application/json");
            if (token != null) con.setRequestProperty("Authorization", "Bearer " + token);
            try {
                if (body != null) {
                    con.setDoOutput(true);
                    con.setRequestProperty("Content-Type", "application/json");
                    byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
                    if (bytes.length > 65536)
                        throw new IOException("Compiler request exceeded size limit");
                    try (OutputStream out = con.getOutputStream()) {
                        out.write(bytes);
                    }
                }
                int status = con.getResponseCode();
                InputStream stream = status >= 400 ? con.getErrorStream() : con.getInputStream();
                Map<String, Object> parsed = Map.of();
                if (stream != null)
                    try (InputStream in = stream) {
                        ByteArrayOutputStream capture = new ByteArrayOutputStream();
                        byte[] block = new byte[4096];
                        while (true) {
                            long left =
                                    TimeUnit.NANOSECONDS.toMillis(bodyDeadline - System.nanoTime());
                            if (left <= 0) throw new IOException("Compiler response timed out");
                            con.setReadTimeout((int) Math.min(30_000, Math.max(1, left)));
                            int n =
                                    in.read(
                                            block,
                                            0,
                                            Math.min(block.length, 65537 - capture.size()));
                            if (n < 0) break;
                            capture.write(block, 0, n);
                            if (capture.size() > 65536) break;
                        }
                        byte[] bytes = capture.toByteArray();
                        if (bytes.length > 65536)
                            throw new IOException("Compiler response exceeded size limit");
                        if (bytes.length > 0) {
                            Object obj = Json.read(new String(bytes, StandardCharsets.UTF_8));
                            if (!(obj instanceof Map<?, ?>))
                                throw new IOException("Compiler response is not JSON");
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (Map<String, Object>) obj;
                            parsed = map;
                        }
                    }
                return new AuthReply(status, parsed);
            } catch (RuntimeException invalid) {
                throw new IOException("Compiler response could not be read");
            } finally {
                con.disconnect();
            }
        }
    }

    static String requiredText(Map<String, Object> map, String key) throws IOException {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank() || text.length() > 8192)
            throw new IOException("Compiler response is missing a required field");
        return text;
    }

    static String publicId(Map<String, Object> map, String key) throws IOException {
        String value = requiredText(map, key);
        if (!value.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IOException("Compiler returned an invalid identifier");
        return value;
    }

    static long seconds(Map<String, Object> map, String key, long fallback) {
        Object o = map.get(key);
        return o instanceof Number n ? n.longValue() : fallback;
    }

    static URI browserUri(String text) throws IOException {
        try {
            URI u = URI.create(text);
            if (!"https".equals(u.getScheme())
                    || u.getHost() == null
                    || u.getUserInfo() != null
                    || u.getFragment() != null) throw new IllegalArgumentException();
            return u;
        } catch (RuntimeException invalid) {
            throw new IOException("Compiler returned an unsafe sign-in page");
        }
    }

    interface SignInProgress {
        void waiting(String userCode, URI page) throws Exception;

        default void browserUnavailable() throws Exception {
            System.out.println("Browser did not open; use the manual sign-in page and short code.");
        }
    }

    static boolean revoke(AuthIO io, StoredCredential s) {
        try {
            if (s.id == null || !s.id.matches("[A-Za-z0-9_-]{1,128}")) return false;
            int code =
                    io.request(
                                    compilerUri(s.url),
                                    "DELETE",
                                    "/v1/credentials/" + s.id,
                                    null,
                                    s.token)
                            .status;
            return code == 204 || code == 404;
        } catch (Exception failed) {
            return false;
        }
    }

    static String signIn(
            CompilerSetup c,
            AuthIO io,
            Map<String, Object> receipt,
            List<String> undo,
            SignInProgress progress)
            throws Exception {
        String before = credentialState(c);
        StoredCredential previous = credential(c), issued = null;
        AuthReply start = io.request(c.base, "POST", "/auth/device/authorize", Map.of(), null);
        if (start.status != 200)
            throw new IOException("Compiler sign-in could not start; try again");
        String device = requiredText(start.body, "device_code"),
                code = requiredText(start.body, "user_code");
        if (!code.matches("[A-Za-z0-9-]{1,64}"))
            throw new IOException("Invalid browser comparison code");
        URI page =
                browserUri(
                        requiredText(
                                start.body,
                                start.body.get("verification_uri_complete") instanceof String s
                                                && !s.isBlank()
                                        ? "verification_uri_complete"
                                        : "verification_uri"));
        long expires = Math.max(1, Math.min(600, seconds(start.body, "expires_in", 600))),
                interval = Math.max(1, seconds(start.body, "interval", 5));
        URI manual = browserUri(requiredText(start.body, "verification_uri"));
        if (manual.toString().contains(device) || page.toString().contains(device))
            throw new IOException("Compiler returned an unsafe sign-in page");
        long deadline = io.millis() + expires * 1000;
        progress.waiting(code, manual);
        // Browser opens automatically only inside the explicitly approved SIGN_IN action.
        try {
            io.browse(page);
        } catch (Exception unavailable) {
            progress.browserUnavailable();
        }
        int failures = 0;
        while (issued == null) {
            long remaining = deadline - io.millis();
            if (remaining <= 0) throw new IOException("Compiler sign-in code expired; rerun setup");
            io.sleep(Math.min(remaining, Math.min(600, interval) * 1000));
            if (io.millis() >= deadline)
                throw new IOException("Compiler sign-in code expired; rerun setup");
            AuthReply reply;
            try {
                reply =
                        io.request(
                                c.base,
                                "POST",
                                "/auth/device/token",
                                Map.of("device_code", device, "device_name", "Fluxtion setup"),
                                null);
            } catch (IOException transport) {
                if (++failures >= 3)
                    throw new IOException(
                            "Compiler sign-in stopped after three transport failures");
                continue;
            }
            if (reply.status == 200) {
                String token = requiredText(reply.body, "access_token");
                if (!"Bearer".equals(reply.body.get("token_type")))
                    throw new IOException("Compiler returned unsupported credential type");
                issued =
                        new StoredCredential(
                                token,
                                publicId(reply.body, "credential_id"),
                                publicId(reply.body, "account_id"),
                                c.base.toString());
                break;
            }
            if (reply.status == 429 || reply.status >= 500) {
                if (++failures >= 3)
                    throw new IOException("Compiler sign-in stopped after three service failures");
                interval += 5;
                continue;
            }
            failures = 0;
            String error = String.valueOf(reply.body.get("error"));
            if (error.equals("authorization_pending")) continue;
            if (error.equals("slow_down")) {
                interval += 5;
                continue;
            }
            if (error.equals("access_denied")) {
                receipt.put("compilerSignIn", "declined; Analyser setup retained");
                return "declined";
            }
            if (error.equals("expired_token"))
                throw new IOException("Compiler sign-in code expired; rerun setup");
            throw new IOException("Compiler sign-in was refused; rerun setup");
        }
        try {
            if (!before.equals(credentialState(c)))
                throw new PlanChanged(
                        "Compiler credential changed while waiting; refusing replacement");
            writeCredential(c, issued);
        } catch (Exception failed) {
            receipt.put("unsavedCredentialId", issued.id);
            receipt.put("unsavedCredentialRevoked", revoke(io, issued));
            throw failed;
        }
        receipt.put("compilerAccount", issued.account);
        receipt.put("compilerCredentialId", issued.id);
        receipt.put("compilerSignIn", "signed in for builds");
        undo.add(
                "Revoke only credential "
                        + issued.id
                        + " at "
                        + c.base
                        + " using the account credential list or mvn fluxtion:logout; remove "
                        + c.file
                        + " only if it still names that credential. Do not remove a later"
                        + " sign-in.");
        if (previous != null && !Objects.equals(previous.id, issued.id))
            receipt.put("previousCompilerCredentialRevoked", revoke(io, previous));
        try {
            AuthReply usage = io.request(c.base, "GET", "/v1/usage", null, issued.token);
            if (usage.status == 200) {
                String summary = usageSummary(usage.body);
                receipt.put("compilerUsage", summary);
                System.out.println(summary);
            } else {
                receipt.put("compilerUsage", "unavailable; credential saved");
                if (usage.status == 401) {
                    receipt.put("compilerCredentialRefused", true);
                    receipt.put(
                            "compilerSignIn",
                            "saved credential refused; retained for explicit replacement");
                }
            }
        } catch (Exception unavailable) {
            receipt.put("compilerUsage", "unavailable; credential saved");
        }
        return "signed in";
    }

    static String usageSummary(Map<String, Object> body) {
        Object raw = body.get("plan");
        String plan = raw instanceof String s && s.matches("[A-Za-z0-9 _-]{1,64}") ? s : "unknown";
        String text =
                "Plan: "
                        + plan
                        + "; compile credits available: "
                        + creditNumber(body.get("available"));
        if (body.get("kinds") instanceof Map<?, ?> kinds
                && kinds.get("interpret") instanceof Map<?, ?> interpret)
            text += "; interpret credits available: " + creditNumber(interpret.get("available"));
        return text;
    }

    static String safeAccount(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,128}") ? id : "unknown";
    }

    static String creditNumber(Object v) {
        if (!(v instanceof Number n) || !Double.isFinite(n.doubleValue())) return "unknown";
        double d = n.doubleValue();
        return d == Math.rint(d) && Math.abs(d) < 1e15
                ? Long.toString(n.longValue())
                : n.toString();
    }

    // never scan other URL entries, and never accept added launcher commands or arguments.
    static Path verifiedJar(Machine m) throws Exception {
        return verifyExactJar(m, JAR_SHA256);
    }

    static Path verifyExactJar(Machine m, String expectedHash) throws Exception {
        return verifyPinnedJar(m.jar(), expectedHash);
    }

    static Path verifyPinnedJar(Path jar, String expectedHash) throws Exception {
        if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)
                || !fingerprint(jar).equals(expectedHash))
            throw new IOException(
                    "Exact pinned URL artifact is missing or has a different checksum");
        return jar;
    }

    static String launcherText(String url) {
        return "#!/bin/sh\nexec jbang run --java 21 " + url + " \"$@\"\n";
    }

    static boolean launcherMatches(Machine m, String url) throws Exception {
        if (!Files.isRegularFile(m.launcher, LinkOption.NOFOLLOW_LINKS)
                || !Files.isExecutable(m.launcher)) return false;
        return launcherText(url).equals(new String(readOptional(m.launcher), StandardCharsets.UTF_8));
    }

    static PinnedRelease installedRelease(Machine m) throws Exception {
        if (launcherMatches(m, TARGET_RELEASE.url)) return TARGET_RELEASE;
        return previousRelease(m);
    }

    static void requireCompatible(PinnedRelease release) throws PluginUpgradeRequired {
        // A per-release reviewed marker, not a permissive version-range guess.
        if (!release.version.equals(release.minimumRequired))
            throw new PluginUpgradeRequired(release.version, release.minimumRequired);
    }

    static boolean validLauncher(Machine m) throws Exception {
        PinnedRelease release = installedRelease(m);
        return release != null && release.version.equals(release.minimumRequired);
    }

    static String takeUpgradeNotice(Machine m, PinnedRelease release) {
        if (release.version.equals(VERSION)) return null;
        try {
            Path dir = privateDirectory(m.home.resolve(".fluxtion-analyser/plugin-notices"));
            Path marker = dir.resolve("context-upgrade-" + release.version + "-to-" + VERSION);
            Files.createFile(marker, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            return "Analyser " + release.version + " is connected; " + VERSION
                    + " is available. Say 'set up the Fluxtion Analyser' to upgrade.";
        } catch (FileAlreadyExistsException seen) {
            return null;
        } catch (IOException unavailable) {
            return null; // A notice must not prevent a verified connection.
        }
    }

    // A single-child relay, not a reconnecting proxy: preserve handshake and request IDs unchanged.
    // Only a successful context response gains wrapper-owned metadata; stdout remains MCP JSON-RPC.
    static void relayPlugin(Machine m, PinnedRelease release, Process bridge) throws Exception {
        Set<String> contextIds = ConcurrentHashMap.newKeySet();
        Thread input = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(bridge.getOutputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    rememberContext(line, contextIds);
                    writer.write(line); writer.newLine(); writer.flush();
                }
            } catch (IOException closed) {
                bridge.destroy();
            }
        }, "analyser-plugin-input");
        input.setDaemon(true);
        input.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(bridge.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(contextReply(m, release, line, contextIds));
                System.out.flush();
                if (System.out.checkError()) throw new IOException("Host transport closed");
            }
        }
    }

    static void rememberContext(String line, Set<String> contextIds) {
        if (line.length() > MAX_CONFIG) return;
        try {
            Object parsed = Json.read(line);
            if (parsed instanceof Map<?, ?> request && request.get("id") != null
                    && "tools/call".equals(request.get("method"))
                    && request.get("params") instanceof Map<?, ?> params
                    && "analyser_context".equals(params.get("name")))
                contextIds.add(contextId(line));
        } catch (RuntimeException ignored) {
            // The child owns protocol errors; forward the original message unmodified.
        }
    }

    // Match numeric IDs without rounding, while preserving their original wire representation.
    static String contextId(String line) {
        int[] span = Json.memberSpan(line, 0, "id");
        String raw = line.substring(span[0], span[1]);
        return raw.startsWith("\"") ? "string:" + Json.read(raw)
                : "number:" + new java.math.BigDecimal(raw).stripTrailingZeros().toString();
    }

    static String contextReply(Machine m, PinnedRelease release, String line, Set<String> contextIds) {
        if (line.length() > MAX_CONFIG) return line;
        try {
            Object parsed = Json.read(line);
            if (!(parsed instanceof Map<?, ?> response) || response.get("id") == null
                    || !contextIds.remove(contextId(line))
                    || !(response.get("result") instanceof Map<?, ?> result)
                    || Boolean.TRUE.equals(result.get("isError"))
                    || !(result.get("content") instanceof List<?> content)) return line;
            for (Object item : content) {
                if (!(item instanceof Map<?, ?> block) || !"text".equals(block.get("type"))
                        || !(block.get("text") instanceof String text)) continue;
                Object bodyValue = Json.read(text);
                if (!(bodyValue instanceof Map<?, ?> body) || !Boolean.TRUE.equals(body.get("ok"))) continue;
                int[] resultSpan = Json.memberSpan(line, 0, "result");
                int[] contentSpan = Json.memberSpan(line, resultSpan[0], "content");
                int insertion = contentSpan[1] - 1;
                if (line.charAt(contentSpan[0]) != '[' || line.charAt(insertion) != ']') return line;
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("verifiedRelease", release.version);
                String notice = takeUpgradeNotice(m, release);
                if (notice != null) {
                    metadata.put("upgradeNotice", notice);
                    System.err.println(notice);
                }
                String annotation = Json.write(Map.of("type", "text", "text",
                        Json.write(Map.of("pluginBridge", metadata))));
                // Splice only a new block: IDs, existing content, numbers, escapes and whitespace
                // remain byte-identical UTF-8. Never serialise a parsed child response.
                return line.substring(0, insertion) + "," + annotation + line.substring(insertion);
            }
        } catch (RuntimeException ignored) {
            // Annotation failure must not replace a tool response or corrupt the stream.
        }
        return line;
    }

    static boolean validTargetLauncher(Machine m) throws Exception {
        return launcherMatches(m, JAR_URL);
    }

    static PinnedRelease previousRelease(Machine m) throws Exception {
        for (PinnedRelease release : UPGRADE_FROM)
            if (launcherMatches(m, release.url)) return release;
        return null;
    }

    static boolean trusted(Machine m) throws Exception {
        byte[] raw = readOptional(m.trustFile());
        if (raw == null) return false;
        // Native-trial regression: JBang 0.143.0 writes a comment-headed string array,
        // not an object with a trustedSources field. Keep other JSON readers strict.
        Object value;
        try {
            value = Json.read(new String(raw, StandardCharsets.UTF_8), true);
        } catch (RuntimeException malformed) {
            throw new IOException("JBang trust file is malformed; review it before retrying");
        }
        if (!(value instanceof List<?> entries)
                || entries.stream().anyMatch(entry -> !(entry instanceof String))) {
            throw new IOException("JBang trust file must be an array of source strings");
        }
        return entries.contains(RELEASE_DIR);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> registration(Machine m) throws Exception {
        Map<?, ?> root = config(m.claudeConfig);
        Object servers = root.get("mcpServers");
        if (servers == null) return null;
        if (!(servers instanceof Map<?, ?> all))
            throw new IOException("Invalid MCP configuration structure");
        Object entry = all.get(SERVER);
        if (entry == null) return null;
        if (!(entry instanceof Map<?, ?>)) throw new IOException("Invalid server entry");
        return (Map<String, Object>) entry;
    }

    static Map<?, ?> config(Path file) throws Exception {
        byte[] raw = readOptional(file);
        if (raw == null) return Map.of();
        Object value = Json.read(new String(raw, StandardCharsets.UTF_8));
        if (!(value instanceof Map<?, ?> map))
            throw new IOException("Invalid configuration structure");
        return map;
    }

    static boolean hasShadowRegistration(Machine m) throws Exception {
        Map<?, ?> local = config(m.cwd.resolve(".mcp.json"));
        if (local.get("mcpServers") instanceof Map<?, ?> all && all.containsKey(SERVER))
            return true;
        Object projects = config(m.claudeConfig).get("projects");
        return projects instanceof Map<?, ?> ps
                && ps.get(m.cwd.toString()) instanceof Map<?, ?> project
                && project.get("mcpServers") instanceof Map<?, ?> servers
                && servers.containsKey(SERVER);
    }
    // compare the complete environment including order-sensitive PATH and custom cache.
    static boolean matches(Machine m, Map<String, Object> entry) {
        if (entry == null) return false;
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("type", "stdio");
        expected.put("command", m.serverCommand().get(0));
        expected.put("args", m.serverCommand().subList(1, m.serverCommand().size()));
        expected.put("env", m.serverEnv());
        return canonical(expected).equals(canonical(entry));
    }

    static List<Path> profileFiles(Machine m) {
        return List.of(
                m.home.resolve(".zshrc"),
                m.home.resolve(".bash_profile"),
                m.home.resolve(".bashrc"),
                m.home.resolve(".config/fish/conf.d/jbang.fish"));
    }

    static Map<Path, byte[]> backupProfiles(Machine m, Path dir) throws Exception {
        Map<Path, byte[]> result = new LinkedHashMap<>();
        for (Path file : profileFiles(m)) {
            byte[] data = readOptional(file);
            result.put(file, data);
            if (data != null)
                privateWrite(
                        dir.resolve("before-" + file.getFileName()),
                        new String(data, StandardCharsets.UTF_8));
        }
        return result;
    }

    static void writeReceipt(Machine m, Path dir, Map<String, Object> receipt, boolean terminal)
            throws Exception {
        String json = Json.write(receipt) + "\n";
        privateWrite(dir.resolve("receipt.json"), json);
        privateWrite(m.receipt, json);
        if (terminal) {
            Path history = m.receipt.resolveSibling("setup-history.jsonl");
            if (!Files.exists(history)) privateWrite(history, "");
            Files.writeString(history, json, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }
    }

    static Path privateDirectory(Path path) throws IOException {
        if (!Files.exists(path))
            Files.createDirectories(
                    path,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
        if (Files.isSymbolicLink(path)) throw new IOException("Refusing symlink setup directory");
        return path;
    }

    static void privateWrite(Path path, String text) throws IOException {
        privateDirectory(path.getParent());
        Path tmp =
                Files.createTempFile(
                        path.getParent(),
                        ".setup-",
                        ".tmp",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rw-------")));
        try {
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            Files.move(
                    tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    static byte[] readOptional(Path path) throws IOException {
        if (!Files.exists(path)) return null;
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_CONFIG)
            throw new IOException("Unsupported configuration file size/type");
        return Files.readAllBytes(path);
    }

    static String fingerprint(Path path) throws IOException {
        if (!Files.exists(path)) return "absent";
        MessageDigest d;
        try {
            d = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = Files.newInputStream(path)) {
            byte[] block = new byte[65536];
            for (int n; (n = in.read(block)) >= 0; ) d.update(block, 0, n);
        }
        return HexFormat.of().formatHex(d.digest());
    }

    static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach((k, v) -> result.put(k.toString(), sorted(v)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(AnalyserSetup::sorted).toList();
        return value;
    }

    static String canonical(Object value) {
        return Json.write(sorted(value));
    }

    static Path absolute(Path path) {
        return path.toAbsolutePath().normalize();
    }

    static Optional<String> env(String name) {
        return Optional.ofNullable(System.getenv(name)).filter(s -> !s.isBlank());
    }

    static Optional<Path> onPath(String name) {
        for (String dir : env("PATH").orElse("").split(java.util.regex.Pattern.quote(SEP)))
            if (!dir.isBlank()) {
                Path p = absolute(Path.of(dir, name));
                if (Files.isExecutable(p) && Files.isRegularFile(p)) return Optional.of(p);
            }
        return Optional.empty();
    }

    static Optional<String> option(String[] args, String name) {
        for (int i = 0; i + 1 < args.length; i++)
            if (args[i].equals(name)) return Optional.of(args[i + 1]);
        return Optional.empty();
    }

    static String safeFailure(Exception e) {
        return e instanceof IOException
                ? e.getMessage()
                : "setup stopped (" + e.getClass().getSimpleName() + ")";
    }
    /**
     * Minimal JSON for the receipt and for reading two known config files; no dependency to
     * download.
     */
    static final class Json {
        private final String s;
        private int i;
        private final boolean comments;

        private Json(String s, boolean comments) {
            this.s = s;
            this.comments = comments;
        }

        static Object read(String text) {
            return read(text, false);
        }

        static Object read(String text, boolean comments) {
            Json j = new Json(text, comments);
            Object v = j.value();
            j.ws();
            if (j.i != j.s.length())
                throw new IllegalArgumentException("trailing JSON content at " + j.i);
            return v;
        }

        // Locate a member's original value, using this parser only to advance over values.
        // Reject duplicate target members rather than ambiguously annotating a response.
        static int[] memberSpan(String text, int start, String member) {
            Json j = new Json(text, false);
            j.i = start;
            j.ws(); j.expect('{'); j.ws();
            int[] found = null;
            while (j.s.charAt(j.i) != '}') {
                String key = j.string(); j.ws(); j.expect(':'); j.ws();
                int valueStart = j.i;
                j.value();
                if (key.equals(member)) {
                    if (found != null) throw new IllegalArgumentException("duplicate member");
                    found = new int[] { valueStart, j.i };
                }
                j.ws();
                if (j.s.charAt(j.i) == '}') break;
                j.expect(','); j.ws();
            }
            if (found == null) throw new IllegalArgumentException("missing member");
            return found;
        }

        private Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> m = new LinkedHashMap<>();
                i++;
                ws();
                if (s.charAt(i) == '}') {
                    i++;
                    return m;
                }
                while (true) {
                    ws();
                    String k = string();
                    ws();
                    expect(':');
                    m.put(k, value());
                    ws();
                    if (s.charAt(i) == ',') {
                        i++;
                        continue;
                    }
                    expect('}');
                    return m;
                }
            }
            if (c == '[') {
                List<Object> l = new ArrayList<>();
                i++;
                ws();
                if (s.charAt(i) == ']') {
                    i++;
                    return l;
                }
                while (true) {
                    l.add(value());
                    ws();
                    if (s.charAt(i) == ',') {
                        i++;
                        continue;
                    }
                    expect(']');
                    return l;
                }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw new IllegalArgumentException("unexpected JSON at " + i);
            return Double.parseDouble(s.substring(start, i));
        }

        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> b.append(e);
                }
            }
        }

        private void ws() {
            while (i < s.length()) {
                if (Character.isWhitespace(s.charAt(i))) {
                    i++;
                } else if (comments && s.startsWith("//", i)) {
                    while (i < s.length() && s.charAt(i) != '\n' && s.charAt(i) != '\r') i++;
                } else if (comments && s.startsWith("/*", i)) {
                    int end = s.indexOf("*/", i + 2);
                    if (end < 0) throw new IllegalArgumentException("unterminated JSON comment");
                    i = end + 2;
                } else {
                    break;
                }
            }
        }

        private void expect(char c) {
            if (s.charAt(i) != c)
                throw new IllegalArgumentException("expected '" + c + "' at " + i);
            i++;
        }

        static String write(Object v) {
            if (v == null) return "null";
            if (v instanceof Map<?, ?> m) {
                StringBuilder b = new StringBuilder("{");
                m.forEach(
                        (k, x) ->
                                b.append(b.length() > 1 ? "," : "")
                                        .append(write(k.toString()))
                                        .append(':')
                                        .append(write(x)));
                return b.append('}').toString();
            }
            if (v instanceof List<?> l) {
                StringBuilder b = new StringBuilder("[");
                l.forEach(x -> b.append(b.length() > 1 ? "," : "").append(write(x)));
                return b.append(']').toString();
            }
            if (v instanceof Boolean || v instanceof Number) return v.toString();
            StringBuilder b = new StringBuilder("\"");
            for (char c : v.toString().toCharArray()) {
                switch (c) {
                    case '"' -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    case '\n' -> b.append("\\n");
                    case '\r' -> b.append("\\r");
                    case '\t' -> b.append("\\t");
                    default -> b.append(
                            c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
                }
            }
            return b.append('"').toString();
        }
    }
}
