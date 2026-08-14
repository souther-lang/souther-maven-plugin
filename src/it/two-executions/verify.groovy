// Every module both executions compiled is in the jar. What this is watching for is the second
// execution taking the first one's classes back out: they share an output directory, and a compile
// removes what it recorded generating and no longer writes.
def jar = new java.util.zip.ZipFile(new File(basedir, "target/two-executions-1.0.0-SNAPSHOT.jar"))
try {
    assert jar.getEntry("shared/money/Amount.class") != null :
            "the first execution's own directory"
    assert jar.getEntry("app/orders/Order.class") != null :
            "the second directory of the first execution's source set, naming a module in the first"
    assert jar.getEntry("shared/wallet/Balance.class") != null :
            "the second execution"
} finally {
    jar.close()
}
