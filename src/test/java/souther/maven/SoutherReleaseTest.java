package souther.maven;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /**
     * A properties file that is there and says nothing is the same mistake as one that is not there,
     * and has to fail the same way. Answered with an empty string it becomes the version a build
     * compiles with: the project is told to declare a runtime with no version after the colon, or
     * Maven is asked to resolve an artifact whose version is nothing.
     */
    @Test
    void aPropertiesFileWithNoVersionInItIsAsMuchOfAFailureAsOneThatIsNotThere() {
        IllegalStateException failed = assertThrows(IllegalStateException.class,
                () -> SoutherRelease.stated(new Properties()));

        assertTrue(failed.getMessage().contains("souther.version"), failed.getMessage());
    }
}
