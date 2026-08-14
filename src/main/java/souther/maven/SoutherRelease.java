package souther.maven;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** The Souther this plugin release was verified against. */
final class SoutherRelease {

    private SoutherRelease() {}

    private static final String RESOURCE = "/souther-maven-plugin.properties";

    /**
     * The version a project gets when it names none.
     *
     * <p>Written in at build time rather than named in the code, so that what this plugin was tested
     * against and what it defaults to are one thing. It is not this plugin's own version: the two
     * move for different reasons, and a project that wants a newer Souther says so without waiting
     * for a plugin release.
     */
    static String verified() {
        Properties properties = new Properties();
        try (InputStream in = SoutherRelease.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("this plugin was built without " + RESOURCE);
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("unreadable " + RESOURCE, e);
        }
        return stated(properties);
    }

    /**
     * The version {@code properties} states, refusing one that states none.
     *
     * <p>A file that is there and says nothing is the same mistake as one that is not there: answered
     * with an empty string it becomes the version a build compiles with, and what the reader gets is
     * a runtime to declare with nothing after the colon, or a resolution failure for an artifact
     * whose version is nothing.
     */
    static String stated(Properties properties) {
        String version = properties.getProperty("souther.version");
        if (version == null || version.isBlank()) {
            throw new IllegalStateException(
                    "this plugin was built without a souther.version in " + RESOURCE);
        }
        return version;
    }
}
