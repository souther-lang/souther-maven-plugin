package souther.maven;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The goal over a real toolchain: a project whose sources are only {@code .sou} gets its generated
 * classes where its own jar and its test compilation read them, with nothing of another language in
 * it. Only the resolution is stood in for — the compile is the compiler's.
 */
class SoutherCompileMojoTest {

    @Test
    void aProjectWhoseSourcesAreOnlySouGetsItsClassesWhereTheJarReadsThem(@TempDir Path dir)
            throws Exception {
        Path sources = Files.createDirectories(dir.resolve("src/main/souther"));
        Files.writeString(sources.resolve("money.sou"), """
                module shared.money exposing ( Amount )
                data Amount = Int
                    invariant value >= 0
                """);
        Path classes = dir.resolve("target/classes");

        mojo(sources, classes).execute();

        assertTrue(Files.exists(classes.resolve("shared/money/Amount.class")));
    }

    @Test
    void aCompileErrorStopsTheBuild(@TempDir Path dir) throws IOException {
        Path sources = Files.createDirectories(dir.resolve("src/main/souther"));
        Files.writeString(sources.resolve("order.sou"), """
                module app.order
                import shared.money ( Amount )
                data Order = { total: Amount }
                """);
        Path classes = dir.resolve("target/classes");

        assertThrows(MojoFailureException.class, () -> mojo(sources, classes).execute());

        assertFalse(Files.exists(classes.resolve("app/order/Order.class")));
    }

    /** A source directory a project does not have is not a failure: the goal is bound to every
     *  build, and a project with no Souther in it is most of them. */
    @Test
    void aProjectWithNoSoutherSourceDirectoryIsLeftAlone(@TempDir Path dir) throws Exception {
        Path classes = dir.resolve("target/classes");

        mojo(dir.resolve("src/main/souther"), classes).execute();

        assertFalse(Files.exists(classes));
    }

    /**
     * Maven puts the project's own output directory on its compile class path, and after one build
     * that directory holds the module being compiled. Read as a dependency it is the same module
     * arriving twice, which the compiler refuses — so a second build without a clean would fail on
     * a project that built once.
     */
    @Test
    void aRebuildDoesNotReadTheProjectsOwnOutputAsADependency(@TempDir Path dir) throws Exception {
        Path sources = Files.createDirectories(dir.resolve("src/main/souther"));
        Files.writeString(sources.resolve("money.sou"), """
                module shared.money exposing ( Amount )
                data Amount = Int
                    invariant value >= 0
                """);
        Path classes = dir.resolve("target/classes");

        built(sources, classes);
        built(sources, classes);

        assertTrue(Files.exists(classes.resolve("shared/money/Amount.class")));
    }

    /** One build, with the class path Maven would hand it: its own output first. */
    private static void built(Path sources, Path classes) throws Exception {
        SoutherCompileMojo mojo = mojo(sources, classes);
        mojo.compileClasspathElements = new ArrayList<>(List.of(classes.toString()));
        mojo.execute();
    }

    @Test
    void aProjectThatNamesNoVersionGetsTheOneThisPluginWasVerifiedAgainst(@TempDir Path dir)
            throws Exception {
        Path sources = Files.createDirectories(dir.resolve("src/main/souther"));
        Files.writeString(sources.resolve("money.sou"), "data Amount = Int\n");
        List<String> asked = new ArrayList<>();

        SoutherCompileMojo mojo = mojo(sources, dir.resolve("target/classes"));
        mojo.southerVersion = null;
        mojo.toolchain = version -> {
            asked.add(version);
            return resolvedToolchain();
        };
        mojo.execute();

        assertEquals(List.of(SoutherRelease.verified()), asked);
    }

    /**
     * The refusal {@link souther.build.DriverLoader} raises reaches the build as an error naming the Souther
     * that was asked for. Left as it is, the reader gets a class-loading complaint and no version.
     */
    @Test
    void aToolchainThatIsNotOneIsReportedAgainstTheVersionThatWasAskedFor(@TempDir Path dir)
            throws IOException {
        Path sources = Files.createDirectories(dir.resolve("src/main/souther"));
        Files.writeString(sources.resolve("money.sou"), "data Amount = Int\n");

        SoutherCompileMojo mojo = mojo(sources, dir.resolve("target/classes"));
        mojo.southerVersion = "9.9.9";
        mojo.toolchain = version -> List.of(dir.resolve("nothing-here"));

        MojoExecutionException failed =
                assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(failed.getMessage().contains("9.9.9"), failed.getMessage());
    }

    /**
     * The generated code calls the runtime, so a project that compiles a model depends on it — and
     * has to say so in its pom, or the projects that depend on this one do not get it. A plugin
     * cannot say it on the project's behalf: what it added would not be in the pom that is
     * published.
     */
    @Test
    void aProjectThatDoesNotDeclareTheRuntimeIsToldToDeclareIt(@TempDir Path dir) throws IOException {
        Path sources = Files.createDirectories(dir.resolve("src/main/souther"));
        Files.writeString(sources.resolve("money.sou"), "data Amount = Int\n");

        SoutherCompileMojo mojo = mojo(sources, dir.resolve("target/classes"));
        mojo.project = new MavenProject();

        MojoExecutionException failed = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(failed.getMessage().contains("souther-runtime"), failed.getMessage());
        assertTrue(failed.getMessage().contains(SoutherRelease.verified()), failed.getMessage());
    }

    /** Declared, but not the runtime belonging to the Souther this compiles with. */
    @Test
    void aRuntimeOfAnotherSoutherIsReportedAgainstTheOneBeingCompiledWith(@TempDir Path dir)
            throws IOException {
        Path sources = Files.createDirectories(dir.resolve("src/main/souther"));
        Files.writeString(sources.resolve("money.sou"), "data Amount = Int\n");

        SoutherCompileMojo mojo = mojo(sources, dir.resolve("target/classes"));
        mojo.project = projectDeclaringRuntime("0.0.1-something-else");

        MojoExecutionException failed = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(failed.getMessage().contains("0.0.1-something-else"), failed.getMessage());
        assertTrue(failed.getMessage().contains(SoutherRelease.verified()), failed.getMessage());
    }

    private static SoutherCompileMojo mojo(Path sources, Path classes) {
        SoutherCompileMojo mojo = new SoutherCompileMojo();
        mojo.setLog(new SystemStreamLog());
        mojo.sourceDirectory = sources.toFile();
        mojo.outputDirectory = classes.toFile();
        mojo.compileClasspathElements = new ArrayList<>();
        mojo.languageTag = "en";
        mojo.southerVersion = SoutherRelease.verified();
        mojo.project = projectDeclaringRuntime(SoutherRelease.verified());
        mojo.toolchain = version -> resolvedToolchain();
        return mojo;
    }

    /** A project whose pom says what a project compiling a model has to say. */
    private static MavenProject projectDeclaringRuntime(String version) {
        Dependency runtime = new Dependency();
        runtime.setGroupId("org.souther-lang");
        runtime.setArtifactId("souther-runtime");
        runtime.setVersion(version);
        MavenProject project = new MavenProject();
        project.getModel().addDependency(runtime);
        return project;
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
