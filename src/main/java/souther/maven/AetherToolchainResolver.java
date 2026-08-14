package souther.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The toolchain, from the repositories the project already resolves against.
 *
 * <p>One artifact is asked for and its dependencies come with it, which is the whole reason the
 * driver exists as an artifact: what a Souther release needs to run a compile is stated by that
 * release, so this plugin holds no list that could fall behind it.
 */
final class AetherToolchainResolver implements ToolchainResolver {

    private static final String GROUP = "org.souther-lang";
    private static final String DRIVER = "souther-build-driver";

    private final RepositorySystem repositories;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> remote;

    AetherToolchainResolver(RepositorySystem repositories, RepositorySystemSession session,
                            List<RemoteRepository> remote) {
        this.repositories = repositories;
        this.session = session;
        this.remote = remote;
    }

    @Override
    public List<Path> resolve(String southerVersion) throws MojoExecutionException {
        Dependency driver = new Dependency(
                new DefaultArtifact(GROUP, DRIVER, "jar", southerVersion), JavaScopes.RUNTIME);
        DependencyRequest request = new DependencyRequest(
                new CollectRequest(driver, remote),
                DependencyFilterUtils.classpathFilter(JavaScopes.RUNTIME));
        try {
            DependencyResult resolved = repositories.resolveDependencies(session, request);
            List<Path> toolchain = new ArrayList<>();
            for (ArtifactResult each : resolved.getArtifactResults()) {
                toolchain.add(each.getArtifact().getFile().toPath());
            }
            return toolchain;
        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException(
                    "Souther " + southerVersion + " could not be resolved: " + GROUP + ":" + DRIVER
                            + ":" + southerVersion + " is what a build needs to compile with. "
                            + e.getMessage(), e);
        }
    }
}
