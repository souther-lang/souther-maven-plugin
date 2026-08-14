// The jar carries the model, and nothing had to be written in another language to get it there.
// Checked in the jar rather than in target/classes: an empty jar over a full output directory is
// one of the two ways the build this plugin replaces went wrong without reporting anything.
def jar = new java.util.zip.ZipFile(new File(basedir, "target/sou-only-1.0.0-SNAPSHOT.jar"))
try {
    assert jar.getEntry("shared/money/Amount.class") != null
    assert jar.getEntry('shared/money/$Module.class') != null :
            "the declarations another project imports this module by"
} finally {
    jar.close()
}

assert !new File(basedir, "src/main/java").exists()

def log = new File(basedir, "build.log").text
assert !log.contains("JAR will be empty")
