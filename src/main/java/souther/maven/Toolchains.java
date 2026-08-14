package souther.maven;

import souther.build.DriverLoader;
import souther.build.Toolchain;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Southers a build has open, one per version it names.
 *
 * <p>A reactor's modules compile with the same Souther, and the goal runs once per module. Opened
 * per run, the compiler's classes are read and linked once per module and the jars of every one of
 * those toolchains stay open — a thirty-module build holding thirty of them, which on a constrained
 * machine is file descriptors it does not have and on Windows is a local repository a later build
 * cannot write over.
 *
 * <p>Held for the build that opened them and no longer. Maven runs under a daemon and inside an IDE,
 * where the JVM outlives the build: kept past it, the next build would compile with the compiler the
 * last one resolved, and a snapshot of Souther installed in between would be read by nothing. A
 * build that is not the one these were opened for gets them closed and its own opened instead, which
 * is also where the jars are given back.
 *
 * <p>A driver is taken per compile rather than held here. Two modules of a reactor build at the same
 * time under {@code -T}, and what one of them does to its driver is not something the other is
 * holding.
 */
final class Toolchains {

    private Toolchains() {}

    /** The version and the files it resolved to. A snapshot resolved again is not the jar it was,
     *  and a module resolving against repositories of its own gets what those hold. */
    private record Resolved(String version, List<Path> jars) {}

    private static final Map<Resolved, Toolchain> OPEN = new HashMap<>();
    private static Object opener;

    // The last build of a JVM has no build after it to give its jars back, and under plain Maven
    // that is every build. Nothing else is left to close them at.
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Toolchains::closeAll, "souther-toolchains"));
    }

    /**
     * The Souther {@code version} is, opened unless {@code build} has it open already.
     *
     * @param build what is asking — one Maven session, held by identity
     * @param version the Souther the project names
     * @param jars what the build resolved for it
     */
    static synchronized Toolchain of(Object build, String version, List<Path> jars) {
        if (build != opener) {
            closeAll();
            opener = build;
        }
        return OPEN.computeIfAbsent(new Resolved(version, List.copyOf(jars)),
                resolved -> DriverLoader.open(resolved.jars()));
    }

    /**
     * Every one of them, whatever closing one came to. A toolchain that will not close is a loader
     * this cannot do anything more about, and the ones after it in the map are still holding jars.
     */
    private static synchronized void closeAll() {
        for (Toolchain toolchain : OPEN.values()) {
            try {
                toolchain.close();
            } catch (RuntimeException wouldNotClose) {
                // Nowhere to report it: this runs between two builds, or on the way out of the JVM.
            }
        }
        OPEN.clear();
    }
}
