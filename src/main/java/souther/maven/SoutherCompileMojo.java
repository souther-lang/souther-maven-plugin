package souther.maven;

import souther.build.BuildRequest;
import souther.build.DriverLoader;
import souther.build.BuildResult;
import souther.build.SoutherBuildDriver;

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
 * <p>Bound to {@code compile}, and writing where {@code javac} writes, so the project's own jar and
 * its test compilation read the generated classes without being told to. A project with no Souther
 * in it is left alone.
 */
@Mojo(name = "compile",
      // Before javac rather than with it: the generated classes go where javac reads, and Java
      // written beside the model can only name it if they are there first. Bound to `compile`, the
      // packaging's own compiler execution is declared before this one and runs before it.
      defaultPhase = LifecyclePhase.PROCESS_SOURCES,
      requiresDependencyResolution = ResolutionScope.COMPILE,
      threadSafe = true)
public class SoutherCompileMojo extends AbstractMojo {

    /** Where the {@code .sou} are. */
    @Parameter(property = "souther.sourceDirectory",
               defaultValue = "${project.basedir}/src/main/souther")
    File sourceDirectory;

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
     */
    @Parameter(defaultValue = "${project.build.directory}/souther", readonly = true, required = true)
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
        Path sources = sourceDirectory.toPath();
        if (!Files.isDirectory(sources)) {
            getLog().debug("no Souther sources under " + sources);
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
        SoutherBuildDriver driver;
        try {
            driver = DriverLoader.over(resolver.resolve(version));
        } catch (IllegalStateException e) {
            // What was resolved is not a Souther this plugin can drive. Said against the version
            // that was asked for: the message on its own names neither the project's choice nor
            // where it came from.
            throw new MojoExecutionException("Souther " + version + ": " + e.getMessage(), e);
        }
        BuildResult result = driver.compile(new BuildRequest(
                List.of(sources), classPath, outputDirectory.toPath(), stateDirectory.toPath(),
                languageTag));
        Diagnostics.report(result, getLog());
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
                if (version.equals(declared.getVersion())) {
                    return;
                }
                throw new MojoExecutionException("this project declares " + RUNTIME_ARTIFACT + " "
                        + declared.getVersion() + " and compiles with Souther " + version
                        + ". Generated code calls the runtime of the Souther that produced it, so "
                        + "those are one version.");
            }
        }
        throw new MojoExecutionException("this project compiles a Souther model and its pom does "
                + "not declare the runtime that model's code calls. Add " + RUNTIME_GROUP + ":"
                + RUNTIME_ARTIFACT + ":" + version + ". This plugin cannot add it for you: what it "
                + "added would not be in the pom this project publishes, and nothing depending on "
                + "this project would get it.");
    }

    private static final String RUNTIME_GROUP = "org.souther-lang";
    private static final String RUNTIME_ARTIFACT = "souther-runtime";
}
