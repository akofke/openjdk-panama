/*
 * @test /nodynamiccopyright/
 * @bug 7020044 8062373
 *
 * @summary  Check that diamond is not allowed with anonymous inner class expressions at source < 9
 * @author Maurizio Cimadamore
 * @compile/fail/ref=Neg09.out Neg09.java -source 8 -XDrawDiagnostics
 *
 */

class Neg09 {
    class Member<X> {}

    static class Nested<X> {}

    void testSimple() {
        Member<?> m1 = new Member<>() {};
        Nested<?> m2 = new Nested<>() {};
    }

    void testQualified() {
        Member<?> m1 = this.new Member<>() {};
        Nested<?> m2 = new Neg09.Nested<>() {};
    }
}
