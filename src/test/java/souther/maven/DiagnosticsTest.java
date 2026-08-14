package souther.maven;

import souther.build.BuildDiagnostic;
import souther.build.BuildDiagnostic.Severity;
import souther.build.BuildResult;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a compile's result does to the build. Both halves matter: an error has to stop it, and a
 * warning has to be seen — a build that carries neither lets a project stay green over a model the
 * checker could not discharge.
 */
class DiagnosticsTest {

    @Test
    void warningsAreReportedAndTheBuildGoesOn() throws MojoFailureException {
        Recording log = new Recording();

        Diagnostics.report(new BuildResult(true, List.of(
                new BuildDiagnostic(Severity.WARNING, "E2011 over here"))), log);

        assertEquals(List.of("E2011 over here"), log.warnings);
        assertTrue(log.errors.isEmpty());
    }

    @Test
    void anErrorIsReportedAndStopsTheBuild() {
        Recording log = new Recording();
        BuildResult failed = new BuildResult(false, List.of(
                new BuildDiagnostic(Severity.ERROR, "E1504 unknown module")));

        assertThrows(MojoFailureException.class, () -> Diagnostics.report(failed, log));

        assertEquals(List.of("E1504 unknown module"), log.errors);
    }

    /**
     * A failing compile whose errors were reported is not something to say twice. The exception is
     * what stops the build; the log already carries what was wrong, snippet and all, and a summary
     * that repeated one of them would put a second copy of one diagnostic under all of them.
     */
    @Test
    void whatStopsTheBuildDoesNotRepeatADiagnostic() {
        Recording log = new Recording();
        BuildResult failed = new BuildResult(false, List.of(
                new BuildDiagnostic(Severity.ERROR, "E1504 unknown module"),
                new BuildDiagnostic(Severity.ERROR, "E1505 another")));

        MojoFailureException stopped = assertThrows(MojoFailureException.class,
                () -> Diagnostics.report(failed, log));

        assertEquals(2, log.errors.size());
        assertTrue(stopped.getMessage().contains("2"), stopped.getMessage());
        assertTrue(!stopped.getMessage().contains("E1504"), stopped.getMessage());
    }

    /** Records what reached the log, which is the whole of what this has to do. */
    private static final class Recording extends SystemStreamLog {
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        @Override
        public void warn(CharSequence content) {
            warnings.add(String.valueOf(content));
        }

        @Override
        public void error(CharSequence content) {
            errors.add(String.valueOf(content));
        }
    }
}
