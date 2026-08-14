package souther.maven;

import org.apache.maven.plugin.MojoExecutionException;

import java.nio.file.Path;
import java.util.List;

/**
 * Where a Souther version comes from. One artifact is asked for — souther-build-driver — and what
 * comes back is it and everything it needs: the artifact's own dependencies are what a Souther
 * release is made of, so nothing here has to know.
 */
interface ToolchainResolver {

    List<Path> resolve(String southerVersion) throws MojoExecutionException;
}
