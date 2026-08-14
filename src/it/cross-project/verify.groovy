// The importing project built its own module, and did not take the imported one's classes with it:
// those belong to the build that produced them.
def jar = new java.util.zip.ZipFile(new File(basedir, "orders/target/orders-1.0.0-SNAPSHOT.jar"))
try {
    assert jar.getEntry("app/orders/Order.class") != null
    assert jar.getEntry("shared/money/Amount.class") == null
} finally {
    jar.close()
}
