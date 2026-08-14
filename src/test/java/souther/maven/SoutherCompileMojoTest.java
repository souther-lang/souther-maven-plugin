package souther.maven;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    /**
     * A source set with more than one directory in it is one compile, not one per directory: a
     * module in the second names a module in the first, and only a compile that was given both can
     * resolve it. Run as two compiles they would also share an output directory and a record of what
     * was generated, and each would take the other's classes back out.
     */
    @Test
    void aModelSpreadOverSeveralSourceDirectoriesIsOneCompile(@TempDir Path dir) throws Exception {
        Path written = Files.createDirectories(dir.resolve("src/main/souther"));
        Path generated = Files.createDirectories(dir.resolve("target/generated-sources/souther"));
        Files.writeString(written.resolve("money.sou"), """
                module shared.money exposing ( Amount )
                data Amount = Int
                """);
        Files.writeString(generated.resolve("order.sou"), """
                module app.order exposing ( Order )
                import shared.money ( Amount )
                data Order = { total: Amount }
                """);
        Path classes = dir.resolve("target/classes");

        SoutherCompileMojo mojo = mojo(written, classes);
        mojo.sourceDirectories = List.of(written.toFile(), generated.toFile());
        mojo.execute();

        assertTrue(Files.exists(classes.resolve("app/order/Order.class")));
        assertTrue(Files.exists(classes.resolve("shared/money/Amount.class")));
    }

    /** One of them holding nothing is not a failure, for the same reason a project with no Souther
     *  at all is not: a directory a build names ahead of writing anything into it is ordinary. */
    @Test
    void aSourceDirectoryThatIsNotThereIsPassedOverRatherThanStoppingTheCompile(@TempDir Path dir)
            throws Exception {
        Path written = Files.createDirectories(dir.resolve("src/main/souther"));
        Files.writeString(written.resolve("money.sou"), """
                module shared.money exposing ( Amount )
                data Amount = Int
                """);
        Path classes = dir.resolve("target/classes");

        SoutherCompileMojo mojo = mojo(written, classes);
        mojo.sourceDirectories = List.of(written.toFile(), dir.resolve("not-there").toFile());
        mojo.execute();

        assertTrue(Files.exists(classes.resolve("shared/money/Amount.class")));
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

    /**
     * Renaming a module takes the old one's classes out of the output, including the {@code $Module}
     * another project imports it by. The output directory is shared with javac, so this only works
     * if the state directory the goal hands over is one the compile can keep a record in.
     */
    @Test
    void aRenamedModuleLeavesNothingOfTheOldNameBehind(@TempDir Path dir) throws Exception {
        Path sources = Files.createDirectories(dir.resolve("src/main/souther"));
        Path classes = dir.resolve("target/classes");
        Files.writeString(sources.resolve("money.sou"), """
                module shared.money exposing ( Amount )
                data Amount = Int
                """);
        built(sources, classes);

        Files.writeString(sources.resolve("money.sou"), """
                module shared.wallet exposing ( Amount )
                data Amount = Int
                """);
        built(sources, classes);

        assertTrue(Files.exists(classes.resolve("shared/wallet/$Module.class")));
        assertFalse(Files.exists(classes.resolve("shared/money/$Module.class")));
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

    /**
     * A scope decides whether a dependency reaches the projects that depend on this one, and the
     * check is there for exactly them: {@code provided} and {@code test} reach none of them, so the
     * pom satisfies the check and the downstream build still has no runtime. {@code runtime} reaches
     * them at run time only, and a project importing this model compiles against classes whose
     * signatures name the runtime.
     */
    @ParameterizedTest
    @ValueSource(strings = {"provided", "test", "runtime", "system"})
    void aRuntimeInAScopeThatDoesNotReachTheProjectsDependingOnThisOneIsRefused(String scope,
                                                                               @TempDir Path dir)
            throws IOException {
        Path sources = Files.createDirectories(dir.resolve("src/main/souther"));
        Files.writeString(sources.resolve("money.sou"), "data Amount = Int\n");

        SoutherCompileMojo mojo = mojo(sources, dir.resolve("target/classes"));
        mojo.project = projectDeclaringRuntime(SoutherRelease.verified(), scope);

        MojoExecutionException failed = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(failed.getMessage().contains(scope), failed.getMessage());
    }

    /** Written out is the same as left out: both are what the generated code needs where it needs
     *  it, and a pom that says so is not one to complain about. */
    @Test
    void aRuntimeDeclaredAtCompileScopeIsWhatTheCheckIsAskingFor(@TempDir Path dir) throws Exception {
        Path sources = Files.createDirectories(dir.resolve("src/main/souther"));
        Files.writeString(sources.resolve("money.sou"), """
                module shared.money exposing ( Amount )
                data Amount = Int
                """);
        Path classes = dir.resolve("target/classes");

        SoutherCompileMojo mojo = mojo(sources, classes);
        mojo.project = projectDeclaringRuntime(SoutherRelease.verified(), "compile");
        mojo.execute();

        assertTrue(Files.exists(classes.resolve("shared/money/Amount.class")));
    }

    /**
     * A toolchain that cannot be opened is not only one stating the wrong protocol — a service
     * declaration naming a class that is not there raises an error of another kind entirely. Every
     * one of them reaches the build against the version that was asked for, or the reader gets a
     * class-loading complaint and no way to tell which Souther it is about.
     */
    @Test
    void aToolchainThatCannotBeOpenedAtAllIsStillReportedAgainstThatVersion(@TempDir Path dir)
            throws IOException {
        Path sources = Files.createDirectories(dir.resolve("src/main/souther"));
        Files.writeString(sources.resolve("money.sou"), "data Amount = Int\n");
        Path broken = dir.resolve("broken-toolchain");
        Files.createDirectories(broken.resolve("META-INF/services"));
        Files.writeString(broken.resolve("META-INF/souther-build-protocol"), "1");
        Files.writeString(broken.resolve("META-INF/services/souther.build.SoutherBuildDriver"),
                "nowhere.NoSuchDriver\n");

        SoutherCompileMojo mojo = mojo(sources, dir.resolve("target/classes"));
        mojo.southerVersion = "9.9.9";
        mojo.project = projectDeclaringRuntime("9.9.9");
        mojo.toolchain = version -> List.of(broken);

        MojoExecutionException failed = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(failed.getMessage().contains("9.9.9"), failed.getMessage());
    }

    private static SoutherCompileMojo mojo(Path sources, Path classes) {
        SoutherCompileMojo mojo = new SoutherCompileMojo();
        mojo.setLog(new SystemStreamLog());
        mojo.sourceDirectories = List.of(sources.toFile());
        mojo.outputDirectory = classes.toFile();
        mojo.stateDirectory = classes.resolveSibling("souther").toFile();
        mojo.compileClasspathElements = new ArrayList<>();
        mojo.languageTag = "en";
        mojo.southerVersion = SoutherRelease.verified();
        mojo.project = projectDeclaringRuntime(SoutherRelease.verified());
        mojo.toolchain = version -> resolvedToolchain();
        return mojo;
    }

    /** A project whose pom says what a project compiling a model has to say. */
    private static MavenProject projectDeclaringRuntime(String version) {
        return projectDeclaringRuntime(version, null);
    }

    /** The same, at a scope it wrote out. Unset is what most poms have, and is {@code compile}. */
    private static MavenProject projectDeclaringRuntime(String version, String scope) {
        Dependency runtime = new Dependency();
        runtime.setGroupId("org.souther-lang");
        runtime.setArtifactId("souther-runtime");
        runtime.setVersion(version);
        runtime.setScope(scope);
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
