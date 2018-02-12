/*
 * @test /nodynamiccopyright/
 * @summary Check that a static final field may not be modified in a value factory
 * @compile/fail/ref=CheckStaticFinalAssign.out -XDrawDiagnostics -XDdev CheckStaticFinalAssign.java
 */

__ByValue final class CheckStaticFinalAssign {
    static final int x;
    static {
        x = 10;
    }

    static __ValueFactory CheckStaticFinalAssign foo() {
       CheckStaticFinalAssign x = __MakeDefault CheckStaticFinalAssign();
       x.x = 100;
       return x;
    }
}
