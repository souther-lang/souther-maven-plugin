package souther.maven;

import souther.build.Toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a build's modules share, and what they do not share with the build after them. Every module
 * of a reactor compiles with the same Souther, and a toolchain opened per module reads the
 * compiler's classes once per module and holds the jars of every one of them.
 */
class ToolchainsTest {

    @Test
    void aSoutherAlreadyOpenIsWhatTheNextModuleOfTheSameBuildCompilesWith() {
        Object build = new Object();

        Toolchain first = Toolchains.of(build, SoutherRelease.verified(), resolvedToolchain());
        Toolchain second = Toolchains.of(build, SoutherRelease.verified(), resolvedToolchain());

        assertSame(first, second);
        assertSame(first.driver().getClass(), second.driver().getClass(),
                "one class, which is what says the compiler was read once rather than twice");
    }

    /**
     * A reactor may name one Souther in one module and another in the next. They are two compilers
     * and nothing of one is the other's, so they are two toolchains.
     */
    @Test
    void anotherSoutherIsAnotherToolchain() {
        Object build = new Object();

        Toolchain verified = Toolchains.of(build, SoutherRelease.verified(), resolvedToolchain());
        Toolchain another = Toolchains.of(build, "something-else", resolvedToolchain());

        assertNotSame(verified, another);
    }

    /**
     * One version string, two sets of files. A module resolving against repositories of its own gets
     * what those hold, and a snapshot resolved again mid-session is not the jar it was. Answered
     * from the version alone the second module would compile with the first one's files and nothing
     * would say so.
     */
    @Test
    void anotherSetOfJarsUnderTheSameVersionIsAnotherToolchain(@TempDir Path dir) throws IOException {
        Object build = new Object();
        List<Path> resolvedAgain = new ArrayList<>(resolvedToolchain());
        resolvedAgain.add(anEmptyJar(dir.resolve("resolved-again.jar")));

        Toolchain first = Toolchains.of(build, SoutherRelease.verified(), resolvedToolchain());
        Toolchain second = Toolchains.of(build, SoutherRelease.verified(), resolvedAgain);

        assertNotSame(first, second);
    }

    /**
     * The build a toolchain was opened for is the whole of how long it is worth keeping. Maven runs
     * under a daemon and inside an IDE, where the JVM outlives the build: held past it, the next
     * build compiles with the compiler the last one resolved, and a snapshot of Souther installed in
     * between is read by nothing. Given back at the same moment — a build that no longer has them
     * open is a local repository a later one can write over.
     */
    @Test
    void whatOneBuildOpenedIsClosedWhenTheNextOneStarts() {
        Object build = new Object();
        Toolchain verified = Toolchains.of(build, SoutherRelease.verified(), resolvedToolchain());
        Toolchain another = Toolchains.of(build, "something-else", resolvedToolchain());

        Toolchain afterwards = Toolchains.of(new Object(), SoutherRelease.verified(),
                resolvedToolchain());

        assertNotSame(verified, afterwards);
        assertThrows(IllegalStateException.class, verified::driver);
        assertThrows(IllegalStateException.class, another::driver,
                "every one of them, not the first one closed");
    }

    /** What the build copied there, standing in for what Maven Resolver hands the goal. */
    private static List<Path> resolvedToolchain() {
        try (Stream<Path> jars = Files.list(Path.of("target", "souther-toolchain"))) {
            return jars.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A jar carrying nothing, which is enough to be a file the toolchain did not have before. */
    private static Path anEmptyJar(Path jar) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("META-INF/nothing"));
            out.closeEntry();
        }
        return jar;
    }
}
