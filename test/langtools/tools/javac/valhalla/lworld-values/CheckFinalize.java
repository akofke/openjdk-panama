/*
 * @test /nodynamiccopyright/
 * @summary Value types do not support finalize
 *
 * @compile/fail/ref=CheckFinalize.out -XDrawDiagnostics CheckFinalize.java
 */

final value class CheckFinalize {
    @Override
    protected void finalize() {}

    final value class CheckFinalizeInner { int x = 10; }

    void foo(CheckFinalizeInner cfi, CheckFinalize cf) {
        cfi.finalize();          // Error
        cf.finalize();           // Error.
    }
    int x = 10;
}
