package souther.maven;

import souther.build.Toolchain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What a reactor's modules share. Every module of a build compiles with the same Souther, and a
 * toolchain opened per module reads the compiler's classes once per module and holds the jars of
 * every one of them until the JVM ends.
 */
class ToolchainsTest {

    @Test
    void aSoutherAlreadyOpenIsWhatTheNextModuleCompilesWith() {
        Toolchain first = Toolchains.of(SoutherRelease.verified(), resolvedToolchain());
        Toolchain second = Toolchains.of(SoutherRelease.verified(), resolvedToolchain());

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
        Toolchain verified = Toolchains.of(SoutherRelease.verified(), resolvedToolchain());
        Toolchain another = Toolchains.of("something-else", resolvedToolchain());

        assertNotSame(verified, another);
    }

    /** Two modules of a reactor build at once, and a driver is not something to hand to both. */
    @Test
    void everyCompileGetsADriverOfItsOwn() {
        Toolchain toolchain = Toolchains.of(SoutherRelease.verified(), resolvedToolchain());

        assertNotSame(toolchain.driver(), toolchain.driver());
    }

    /** What the build copied there, standing in for what Maven Resolver hands the goal. */
    private static List<Path> resolvedToolchain() {
        try (Stream<Path> jars = Files.list(Path.of("target", "souther-toolchain"))) {
            return jars.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
