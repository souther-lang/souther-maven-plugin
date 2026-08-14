package souther.maven;

import souther.build.DriverLoader;
import souther.build.Toolchain;

import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Southers this build has open, one per version.
 *
 * <p>A reactor's modules compile with the same Souther, and the goal runs once per module. Opened
 * per run, the compiler's classes are read and linked once per module and the jars of every one of
 * those toolchains stay open until the JVM ends — a thirty-module build holding thirty of them,
 * which on a constrained machine is file descriptors it does not have and on Windows is a local
 * repository a later build cannot update.
 *
 * <p>A driver is still taken per compile rather than held here. Two modules of a reactor build at
 * the same time under {@code -T}, and what one of them does to its driver is not something the other
 * is holding.
 */
final class Toolchains {

    private Toolchains() {}

    private static final Map<String, Toolchain> OPEN = new ConcurrentHashMap<>();

    // Where a build's JVM ends is the only end a goal is told about — Maven gives a mojo no end of
    // session — and it is where the plugin's own realm goes too, so it is the lifetime to hold these
    // for. Under a daemon that outlives the build, it is what gives the jars back.
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Toolchains::closeAll, "souther-toolchains"));
    }

    /**
     * The Souther {@code version} is, opened if this build has not opened it already.
     *
     * @param version the Souther the project names
     * @param jars what a build resolved for it
     */
    static Toolchain of(String version, List<Path> jars) {
        return OPEN.computeIfAbsent(version, asked -> DriverLoader.open(jars));
    }

    private static void closeAll() {
        for (Toolchain toolchain : OPEN.values()) {
            toolchain.close();
        }
    }
}
