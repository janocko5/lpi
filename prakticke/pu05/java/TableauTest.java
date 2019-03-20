import java.lang.RuntimeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map;
import java.util.Set;
import static java.util.stream.Collectors.toList;

class Tester {
    int tested = 0;
    int passed = 0;
    int ncase = 0;
    int equiv = 0;
    int size = 0;
    int time = 0;

    public void compare(Object result, Object expected, String msg) {
        tested += 1;
        if (result.equals(expected)) {
            passed += 1;
        } else {
            System.out.println("    Failed: " + msg + ":");
            System.out.println("      got " + result + " expected " + expected);
        }
    }

    public void startCase(String s) {
        System.err.println(String.format("CASE %d: %s", ++ncase, s));
    }

    private SignedFormula.Type cType(SignedFormula.Type t) {
        return t == SignedFormula.Type.Alpha
            ? SignedFormula.Type.Beta
            : SignedFormula.Type.Alpha
        ;
    }

    public void testSignedForm(
        Formula f,
        SignedFormula.Type expTypeT,
        Set<SignedFormula> expSfsT
    ) {
        startCase("signedType/signedSubf " + f.toString());

        long start = System.nanoTime();
        SignedFormula.Type typeT = f.signedType(true);
        SignedFormula.Type typeF = f.signedType(false);
        SignedFormula[] sfTs = f.signedSubf(true);
        SignedFormula[] sfFs = f.signedSubf(false);
        long end = System.nanoTime();
        long duration = (end - start) / 1000;

        Set<SignedFormula> sfsT = new HashSet<SignedFormula>(Arrays.asList(sfTs));
        Set<SignedFormula> sfsF = new HashSet<SignedFormula>(Arrays.asList(sfFs));
        Set<SignedFormula> expSfsF = new HashSet<SignedFormula>();
        for (SignedFormula sf : expSfsT) {
            expSfsF.add(sf.neg());
        }

        if (expTypeT != null) {
            compare(typeT, expTypeT,
                String.format("%s.signedType(true)", f.toString()));
            compare(typeF, cType(expTypeT),
                String.format("%s.signedType(false)", f.toString()));
        }
        compare(sfsT, expSfsT,
            String.format("%s.signedSubf(true)", f.toString()));
        compare(sfsF, expSfsF,
            String.format("%s.signedSubf(false)", f.toString()));
    }

    public boolean status() {
        System.err.println("");
        System.err.println("TESTED " + tested);
        System.err.println("PASSED " + passed);
        System.err.println("SUM(equiv) " + equiv);
        System.err.println("SUM(time) " + time);
        System.err.println("SUM(size) " + size);

        System.err.println(tested == passed ? "OK" : "ERROR" );
        return tested == passed;
    }

}

public class TableauTest {
    static Variable Var(String v) { return new Variable(v); }
    static Negation Not(Formula f) { return new Negation(f); }
    static Conjunction And(Formula... fs) { return new Conjunction(fs); }
    static Disjunction Or(Formula... fs) { return new Disjunction(fs); }
    static Implication Impl(Formula l, Formula r) { return new Implication(l, r); }
    static Equivalence Eq(Formula l, Formula r) { return new Equivalence(l, r); }
    static SignedFormula T(Formula f) { return SignedFormula.T(f); }
    static SignedFormula F(Formula f) { return SignedFormula.F(f); }
    static Set<SignedFormula> SFs(SignedFormula... sfs) {
        return new HashSet<SignedFormula>(Arrays.asList(sfs));
    }
    static SignedFormula[] SFS(SignedFormula... sfs) { return sfs; }
    static List<SignedFormula> LSF(SignedFormula... sfs) { return Arrays.asList(sfs); }
    static final SignedFormula.Type Alpha = SignedFormula.Type.Alpha;
    static final SignedFormula.Type Beta = SignedFormula.Type.Beta;

    public static void main(String[] args) {
        Tester t = new Tester();
        Variable a = Var("a");
        Variable b = Var("bb");
        Variable c = Var("cccc");
        Variable d = Var("ddddd");

        t.testSignedForm(a, null, SFs());
        t.testSignedForm(Not(a), null, SFs(F(a)));

        t.testSignedForm(
            And(a, b),
            Alpha,
            SFs( T(a), T(b) )
        );

        t.testSignedForm(
            Or(a, b),
            Beta,
            SFs( T(a), T(b) )
        );

        t.testSignedForm(
            And(a, b, c, d),
            Alpha,
            SFs( T(a), T(b), T(c), T(d) )
        );

        t.testSignedForm(
            Or(a, b, c, d),
            Beta,
            SFs( T(a), T(b), T(c), T(d) )
        );

        t.testSignedForm(
            Or(a, Not(b), And(c, d)),
            Beta,
            SFs( T(a), T(Not(b)), T(And(c, d)) )
        );

        t.testSignedForm(
            Impl(a, b),
            Beta,
            SFs( F(a), T(b) )
        );

        t.testSignedForm(
            Eq(a, b),
            Alpha,
            SFs( T(Impl(a,b)), T(Impl(b,a)) )
        );

        {
            t.startCase("addInitial");
            Tableau T = new Tableau();
            List<Node> added = T.addInitial(SFS( F(Impl(a,b)), T(Impl(a,c))));
            t.compare(
                added.stream().map(n -> n.sf()).collect(toList()),
                LSF( F(Impl(a,b)), T(Impl(a,c)) ),
                "addInitial return"
            );
            t.compare(
                T.root() == added.get(0), true, "addInitial root node"
            );
            t.compare(
                T.root().children().get(0) == added.get(1), true, "addInitial child"
            );
            t.compare(
                T.root().sf(), F(Impl(a,b)), "addInitial root node"
            );
            t.compare(
                T.root().children().stream()
                    .map(n -> n.sf()).collect(toList()),
                LSF( T(Impl(a, c)) ),
                "addInitial child(ren) of root node"
            );
        }

        {
            t.startCase("extendAlpha");
            Tableau T = new Tableau();
            List<Node> addedI = T.addInitial(SFS( F(Impl(a,b)), T(Impl(a,c))));

            Node added1 = T.extendAlpha(
                addedI.get(1),
                addedI.get(0),
                0);
            t.compare(added1.sf(), T(a), "extendAlpha first");
            t.compare(
                T.root().children().get(0).children().get(0) == added1,
                true, "extendAlpha returns added node");

            Node added2 = T.extendAlpha(
                added1,
                addedI.get(0),
                1);
            t.compare(added2.sf(), F(b), "extendAlpha second");
            t.compare(
                T.root().children().get(0).children().get(0).children().get(0) == added2,
                true, "extendAlpha returns added node");

        }

        {
            t.startCase("extendBeta");
            Tableau T = new Tableau();
            List<Node> addedI = T.addInitial(SFS( F(Impl(a,b)), T(Impl(a,c))));

            List<Node> added = T.extendBeta(
                addedI.get(1),
                addedI.get(1)
            );

            t.compare(
                added.stream().map(n->n.sf()).collect(toList()),
                LSF( F(a), T(c) ),
                "extendBeta children");
            t.compare(
                T.root()
                    .children().get(0)
                    .children().get(0),
                added.get(0), "extendBeta returns added first child");
            t.compare(
                T.root()
                    .children().get(0)
                    .children().get(1),
                added.get(1), "extendBeta returns added first child");
        }

        System.exit(t.status() ? 0 : 1);
    }
}
