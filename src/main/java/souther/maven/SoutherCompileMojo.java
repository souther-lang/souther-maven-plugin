package souther.maven;

import souther.build.BuildRequest;
import souther.build.BuildResult;
import souther.build.DriverLoader;
import souther.build.Toolchain;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Compiles this project's Souther sources.
 *
 * <p>Bound to {@code process-sources}, before {@code javac}, and writing where {@code javac} writes,
 * so the project's own jar and its test compilation read the generated classes without being told to
 * and Java written beside the model can name it. A project with no Souther in it is left alone.
 */
@Mojo(name = "compile",
      // Before javac rather than with it: the generated classes go where javac reads, and Java
      // written beside the model can only name it if they are there first. The phase before the one
      // the packaging binds its own compiler execution to is what puts them there in time.
      defaultPhase = LifecyclePhase.PROCESS_SOURCES,
      requiresDependencyResolution = ResolutionScope.COMPILE,
      threadSafe = true)
public class SoutherCompileMojo extends AbstractMojo {

    /**
     * Where the {@code .sou} are. Several of them are one compile rather than one each: a module in
     * one directory names a module in another, and only a compile that was given both resolves it.
     * One that is not there is passed over.
     */
    @Parameter(property = "souther.sourceDirectories",
               defaultValue = "${project.basedir}/src/main/souther")
    List<File> sourceDirectories;

    /** Where javac writes, because the jar and the test compile both read it. */
    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true, required = true)
    File outputDirectory;

    /** What an import of another project's module resolves against — already resolved by Maven. */
    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    List<String> compileClasspathElements;

    /**
     * Somewhere of the build's own for what the compile keeps between runs — the record of what it
     * generated, which is how a class it no longer generates is taken back out of an output
     * directory it shares with javac.
     *
     * <p>One per execution of the goal. A record says what this execution generated, and two
     * executions reading one would each find the other's modules listed as generated and no longer
     * written, and take the other's classes back out.
     */
    @Parameter(defaultValue = "${project.build.directory}/souther/${mojoExecution.executionId}",
               readonly = true, required = true)
    File stateDirectory;

    /** The language diagnostics are written in. Unset is what a command line naming none gets. */
    @Parameter(property = "souther.lang")
    String languageTag;

    /** The Souther to compile with. Unset is the one this plugin release was verified against. */
    @Parameter(property = "souther.version")
    String southerVersion;

    @Component
    RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repositorySession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    List<RemoteRepository> remoteRepositories;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    /** How the toolchain is found. Maven's, unless a test has put its own here. */
    ToolchainResolver toolchain;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<Path> sources = new ArrayList<>();
        for (File each : sourceDirectories) {
            Path source = each.toPath();
            if (Files.isDirectory(source)) {
                sources.add(source);
            } else {
                getLog().debug("no Souther sources under " + source);
            }
        }
        if (sources.isEmpty()) {
            return;
        }
        // Maven's compile class path begins with this project's own output, and after one build
        // that directory holds the very modules being compiled. Handed over as a dependency they
        // are the same module arriving twice, which is refused — so a project that built once could
        // not build again without a clean.
        Path output = outputDirectory.toPath().toAbsolutePath().normalize();
        List<Path> classPath = new ArrayList<>();
        for (String element : compileClasspathElements) {
            Path entry = Path.of(element).toAbsolutePath().normalize();
            if (!entry.equals(output)) {
                classPath.add(entry);
            }
        }
        String version = southerVersion == null || southerVersion.isBlank()
                ? SoutherRelease.verified()
                : southerVersion;
        declaresTheRuntimeOf(version);
        ToolchainResolver resolver = toolchain != null ? toolchain
                : new AetherToolchainResolver(repositorySystem, repositorySession, remoteRepositories);
        BuildResult result;
        // Opened for this compile and given back after it. Held any longer it would have to be held
        // by something, and the only thing here that outlives a goal run is the JVM — which under a
        // daemon or an IDE outlives the build too. What that would buy is the compiler's classes
        // read once for a reactor rather than once per module, measured at about a tenth of a second
        // a module; what it would cost is a toolchain whose owner nothing here can name.
        try (Toolchain souther = DriverLoader.open(resolver.resolve(version))) {
            result = souther.driver().compile(new BuildRequest(
                    sources, classPath, outputDirectory.toPath(), stateDirectory.toPath(),
                    languageTag));
        } catch (RuntimeException e) {
            // Three things at once, and one message for them: what was resolved is not a Souther
            // this plugin can drive, or the compile raised rather than reported — an output
            // directory it cannot write — or the jars would not go back. Each says the version that
            // was asked for, because on its own none of them names the project's choice or where it
            // came from, and Maven would have it as an internal error in this plugin.
            throw new MojoExecutionException("Souther " + version + ": " + said(e), e);
        }
        Diagnostics.report(result, getLog());
    }

    /** What it said, or what it is when it said nothing — not every refusal carries a message. */
    private static String said(Throwable refusal) {
        return refusal.getMessage() != null ? refusal.getMessage() : refusal.toString();
    }

    /**
     * That the pom declares the runtime the generated code calls, at the version of the Souther
     * that generates it.
     *
     * <p>Checked rather than added. What a plugin adds is not in the pom this project publishes, so
     * nothing depending on this project would get it — the failure would move downstream, to a
     * build that has no Souther in it at all.
     */
    private void declaresTheRuntimeOf(String version) throws MojoExecutionException {
        for (Dependency declared : project.getDependencies()) {
            if (RUNTIME_GROUP.equals(declared.getGroupId())
                    && RUNTIME_ARTIFACT.equals(declared.getArtifactId())) {
                if (!version.equals(declared.getVersion())) {
                    throw new MojoExecutionException("this project declares " + RUNTIME_ARTIFACT
                            + " " + declared.getVersion() + " and compiles with Souther " + version
                            + ". Generated code calls the runtime of the Souther that produced it, "
                            + "so those are one version.");
                }
                atAScopeThatTravels(declared);
                return;
            }
        }
        throw new MojoExecutionException("this project compiles a Souther model and its pom does "
                + "not declare the runtime that model's code calls. Add " + RUNTIME_GROUP + ":"
                + RUNTIME_ARTIFACT + ":" + version + ". This plugin cannot add it for you: what it "
                + "added would not be in the pom this project publishes, and nothing depending on "
                + "this project would get it.");
    }

    /**
     * That the runtime is declared where the projects depending on this one will see it.
     *
     * <p>The check above is for them, and a scope is what decides whether they get it at all:
     * {@code provided} and {@code test} reach none of them, and {@code runtime} reaches them only
     * when the code runs — while a project importing this model compiles against classes whose
     * signatures name the runtime. Declared in any of those the pom passes the check and the
     * downstream build fails, which is the failure the check is here to move upstream.
     */
    private void atAScopeThatTravels(Dependency declared) throws MojoExecutionException {
        String scope = declared.getScope();
        if (scope == null || scope.isBlank() || TRAVELLING_SCOPE.equals(scope)) {
            return;
        }
        throw new MojoExecutionException("this project declares " + RUNTIME_ARTIFACT + " at " + scope
                + " scope. A project depending on this one compiles against the classes this model "
                + "generates, and their signatures name the runtime — at " + scope + " scope it "
                + "does not reach that build. Declare it at " + TRAVELLING_SCOPE + " scope, which "
                + "is what leaving the scope out gives you.");
    }

    private static final String RUNTIME_GROUP = "org.souther-lang";
    private static final String RUNTIME_ARTIFACT = "souther-runtime";
    private static final String TRAVELLING_SCOPE = "compile";
}
