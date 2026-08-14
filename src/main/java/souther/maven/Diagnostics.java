package souther.maven;

import souther.build.BuildDiagnostic;
import souther.build.BuildResult;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

/** What a compile's result does to the build. */
final class Diagnostics {

    private Diagnostics() {}

    /**
     * Reports everything the compile said, and stops the build if it did not succeed.
     *
     * <p>What stops it says how many rather than what: each diagnostic is already in the log with
     * its snippet, and naming one of them here would put a second copy of that one under all of
     * them.
     */
    static void report(BuildResult result, Log log) throws MojoFailureException {
        int errors = 0;
        for (BuildDiagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.severity() == BuildDiagnostic.Severity.ERROR) {
                errors++;
                log.error(diagnostic.rendered());
            } else {
                log.warn(diagnostic.rendered());
            }
        }
        if (!result.succeeded()) {
            throw new MojoFailureException(errors == 1
                    ? "Souther reported 1 error."
                    : "Souther reported " + errors + " errors.");
        }
    }
}
