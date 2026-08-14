// Both compiled, and the Java one could name the model — which only works if the model was
// generated before javac ran.
def jar = new java.util.zip.ZipFile(new File(basedir, "target/java-beside-1.0.0-SNAPSHOT.jar"))
try {
    assert jar.getEntry("app/Purses.class") != null
    assert jar.getEntry("shared/money/Amount.class") != null
} finally {
    jar.close()
}
