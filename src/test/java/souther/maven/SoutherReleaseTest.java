package souther.maven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Souther a project gets when it names none. It is written into this artifact at build time, so
 * a plugin release says which Souther it was verified against without that being its own version.
 */
class SoutherReleaseTest {

    @Test
    void theVerifiedSoutherVersionIsWrittenIntoThisArtifact() {
        String verified = SoutherRelease.verified();

        assertFalse(verified.isBlank(), "a plugin that defaults to nothing cannot be used as one");
        assertFalse(verified.contains("$"),
                "an unfiltered placeholder reaches a build as an artifact that cannot resolve: "
                        + verified);
        assertTrue(verified.contains("."), verified);
    }
}
