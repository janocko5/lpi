import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

class BadTableauException extends Exception {
    Tableau t;
    BadTableauException(Tableau t, String msg) { super(msg); this.t = t; }
}

class Tester {
    int tested = 0;
    int passed = 0;
    int ncase = 0;
    int equiv = 0;
    int size = 0;
    int time = 0;

    static final SignedFormula.Type Alpha = SignedFormula.Type.Alpha;
    static final SignedFormula.Type Beta = SignedFormula.Type.Beta;

    public void compare(Object result, Object expected, String msg) {
        tested++;
        if (result.equals(expected)) {
            passed++;
        } else {
            System.err.println("    Failed: " + msg + ":");
            System.err.println("      got " + result + " expected " + expected);
        }
    }

    public void fail(String msg) {
        tested++;
        System.err.println("FAILED: " + msg);
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

        try {
            SignedFormula.Type typeT = f.signedType(true);
            SignedFormula.Type typeF = f.signedType(false);
            SignedFormula[] sfTs = f.signedSubf(true);
            SignedFormula[] sfFs = f.signedSubf(false);

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
        } catch (Throwable e) {
            fail("Exception: " + e.toString());
            e.printStackTrace();
        }
    }

    int size(Node node) {
        return 1 + node.children().stream().mapToInt(n->size(n)).sum();
    }

    int size(Tableau t) {
        return size(t.root());
    }

    String openClosed(boolean isClosed) {
        return isClosed ? "closed" : "open";
    }

    void testTableauStructure(
        Tableau t,
        Set<SignedFormula> initials
    ) throws BadTableauException {
        if (t.root() == null)
            throw new BadTableauException(t, "Tableau is empty!");
        testTableauStructure(t, t.root(), new ArrayDeque<Node>(), initials);
    }

    void testTableauStructure(
        Tableau t,
        Node node,
        Deque<Node> ancestors,
        Set<SignedFormula> initials
    ) throws BadTableauException {
        Node src = node.source();
        if (src == null) {
            if (!initials.contains(node.sf()))
                throw new BadTableauException(t, String.format(
                    "Node (%d) has no source and is not one of initial formulas: ",
                    node.number()));
        }
        else if (!ancestors.contains(src)) {
            throw new BadTableauException(t, String.format(
                "Node (%d) has source (%d) which is not it's ancestor.",
                node.number(), src.number()));
        }
        else {
            Node parent = ancestors.peek();
            if (!Arrays.asList(src.sf().subf()).contains(node.sf()))
                throw new BadTableauException(t, String.format(
                    "Node (%d) doesn't contain a subformula of it's source (%d).",
                    node.number(), src.number()));

            SignedFormula.Type type = src.sf().type();
            if (type == Alpha && parent.children().size() != 1)
                throw new BadTableauException(t, String.format(
                    "Node (%d) is a result of Alpha rule for (%d): it must not have siblings.",
                    node.number(), src.number()));

            if (type == Beta) {
                if (parent.children().size() != src.sf().subf().length)
                    throw new BadTableauException(t, String.format(
                        "Node (%d) should have %d siblings because it's a result of Beta rule for (%d).",
                        node.number(), src.sf().subf().length, src.number()));
                Set<SignedFormula> srcBetas = new HashSet<SignedFormula>(
                    Arrays.asList(src.sf().subf()));
                Set<SignedFormula> realBetas = parent.children().stream()
                    .map(n -> n.sf()).collect(toSet());
                if (!srcBetas.equals(realBetas))
                    throw new BadTableauException(t, String.format(
                        "Children of (%d) do not match the Beta rule for (%d): %s  vs %s",
                        parent.number(), src.number(),
                        realBetas.toString(), srcBetas.toString()));
            }
        }

        Node closedFrom = node.closedFrom();
        if (closedFrom != null) {
            // This is a 'closing' node
            if (!ancestors.contains(closedFrom))
                throw new BadTableauException(t, String.format(
                    "Close pair (%d) for node (%d) is not it's ancestor.",
                    closedFrom.number(), node.number()));
            if (node.sf().sign() != !closedFrom.sf().sign())
                throw new BadTableauException(t, String.format(
                    "Close pair formula signs (T/F) are from (%d, %d).",
                    node.number(), closedFrom.number()));
            if (!node.sf().f().equals(closedFrom.sf().f()))
                throw new BadTableauException(t, String.format(
                    "Close pair formulas do not match (%d, %d).",
                    node.number(), closedFrom.number()));
        }

        ancestors.push(node);

        if (node.children().isEmpty()) {
            if (ancestors.stream().allMatch(n -> n.closedFrom() == null)) {
                // child on an open branch
                // open branch should be complete
                Set<SignedFormula> branch = ancestors
                    .stream()
                    .map(n->n.sf())
                    .collect(toSet())
                ;
                // need to go over nodes to have reference numbers
                for (Node nd : ancestors) {
                    SignedFormula.Type type = nd.sf().type();
                    if (type == Alpha) {
                        List<SignedFormula> missing =
                            Arrays.stream(nd.sf().subf())
                            .filter(ssf->!branch.contains(ssf))
                            .collect(toList())
                        ;
                        if (!missing.isEmpty())
                            throw new BadTableauException(t, String.format(
                                "Branch ending at (%d) is open but not complete"
                                + " -- (%d) (Alpha) is missing subformula(s) %s.",
                                node.number(), nd.number(), missing.toString()));
                    }
                    else if (type == Beta) {
                        boolean have =
                            Arrays.stream(nd.sf().subf())
                            .anyMatch(ssf->branch.contains(ssf))
                        ;
                        if (!have)
                            throw new BadTableauException(t, String.format(
                                "Branch ending at (%d) is open but not complete"
                                + " -- (%d) (Beta) is missing at least one of its subformulas %s.",
                                node.number(), nd.number(),Arrays.asList(nd.sf().subf())));
                    }
                }
            }
        } else {
            for (Node child : node.children())
                testTableauStructure(t, child, ancestors, initials);
        }
        ancestors.pop();
    }

    void testTableau(boolean expClosed, SignedFormula[] sfs) {
        System.err.println();
        System.err.println();
        startCase(
            Arrays.stream(sfs).map(sf->sf.toString()).collect(joining("; "))
        );
        tested++;

        TableauBuilder builder = new TableauBuilder();

        try {
            long start = System.nanoTime();
            Tableau t = builder.build(sfs);
            long duration = (System.nanoTime() - start) / 1000;

            testTableauStructure(t, new HashSet<SignedFormula>(Arrays.asList(sfs)));
            time += duration;
            size += size(t);

            if (t.isClosed() != expClosed) {
                throw new BadTableauException(t,
                    String.format("FAILED: Tableau is %s, but should be %s",
                        openClosed(t.isClosed()), openClosed(expClosed)));
            }

            passed++;
            System.err.println(String.format("PASSED: time: %6d  tableau size: %3d  %s",
                duration, size(t), openClosed(expClosed)));
        } catch (BadTableauException e) {
            System.err.println("FAIlED: Bad tableau: " + e.getMessage());
            System.err.println("=====");
            System.err.println(e.t);
            System.err.println("=====");
        } catch (Throwable e) {
            System.err.println("FAILED: Exception: " + e.toString());
            e.printStackTrace();
        }
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

        System.err.println();
        try {
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
        } catch (Throwable e) {
            t.fail("Exception: " + e.toString());
            e.printStackTrace();
        }

        System.err.println();
        try {
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

        } catch (Throwable e) {
            t.fail("Exception: " + e.toString());
            e.printStackTrace();
        }

        System.err.println();
        try {
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
        } catch (Throwable e) {
            t.fail("Exception: " + e.toString());
            e.printStackTrace();
        }


        boolean correctRules = t.tested == t.passed;

        t.testTableau(false, SFS(T(a)));

        t.testTableau(true, SFS(T(a), F(a)));

        t.testTableau(true, SFS( F(Impl(a, a)) ));

        t.testTableau(true, SFS( F(Impl(Var("a"), Var("a"))) ));

        t.testTableau(true, SFS( F(Or(a, Not(a))) ));

        t.testTableau(true, SFS( T(a), F(a) ));

        t.testTableau(true, SFS( T(a), F(a), T(a) ));

        t.testTableau(true, SFS( T(a), F(a), T(b) ));

        t.testTableau(false, SFS( T(Or(a,b)), F(a) ));

        t.testTableau(true, SFS( T(And(a,b)), F(a) ));

        Formula demorgan1 = Eq( Not( And( a, b ) ), Or( Not(a), Not(b) ) );
        t.testTableau(true, SFS( F(demorgan1) ));

        Formula demorgan2 = Eq( Not( Or( a, b ) ), And( Not(a), Not(b) ) );
        t.testTableau(true, SFS( F(demorgan2) ));

        Formula demorgan3 = Eq( Not( Or( a, b, c ) ),
                                 And( Not(a), Not(b), Not(c) ) );
        t.testTableau(true, SFS( F(demorgan3) ));

        Formula contraposition = Eq( Impl(a, b), Impl( Not(b), Not(a) ) );
        t.testTableau(true, SFS( F(contraposition) ));

        Formula impl_impl_distrib = Impl( Impl(a, Impl(b, c)),
                                  Impl( Impl(a, b), Impl(a, c) ) );
        t.testTableau(true, SFS( F(impl_impl_distrib) ));

        Formula impl_or = Eq( Impl(a, b), Or( Not(a), b ) );
        t.testTableau(true, SFS( F(impl_or) ));

        Formula impl_and = Eq( Impl(a, b), Not( And( a, Not(b) ) ) );
        t.testTableau(true, SFS( F(impl_and) ));

        Formula or_and_distrib = Eq( Or( a, And( b, c ) ),
                                      And( Or( a, b ), Or( a, c ) ) );
        t.testTableau(true, SFS( F(or_and_distrib) ));

        Formula bad_demorgan1 = Eq( Not( And( a, b ) ), Or( a, b ) );
        t.testTableau(false, SFS( F(bad_demorgan1) ));

        Formula bad_demorgan2 = Eq( Not( Or( a, b ) ), Or( Not(a), Not(b) ) );
        t.testTableau(false, SFS( F(bad_demorgan2) ));

        Formula bad_demorgan3 = Eq( Not( Or( a, b, c ) ),
                                 And( Not(a), b, Not(c) ) );
        t.testTableau(false, SFS( F(bad_demorgan3) ));

        Formula bad_contraposition = Eq( Impl(a, b), Impl( b, a ) );
        t.testTableau(false, SFS( F(bad_contraposition) ));

        Formula bad_impl_impl_distrib = Impl( Impl(a, Impl(b, c)),
                                  Impl( Impl(b, a), Impl(c, a) ) );
        t.testTableau(false, SFS( F(bad_impl_impl_distrib) ));

        Formula bad_impl_and = Eq( Impl(a, b), Not( And( Not(a), b ) ) );
        t.testTableau(false, SFS( F(bad_impl_and) ));

        Formula bad_or_and_distrib = Eq( Or( a, And( b, c ) ),
                                      Or( And( a, b ), And( a, c ) ) );
        t.testTableau(false, SFS( F(bad_or_and_distrib) ));

        {
            // Keď Katka nakreslí obrazok, je na ňom bud mačka alebo pes. Obrázok mačky
            // Katkin pes vždy hneď roztrhá. Ak jej pes roztrhá obrazok, Katka je
            // smutná. Dokážte, že ak Katka nakreslila obrázok a je šťastná, tak na jej
            // obrázku je pes.
            Formula ax1 = Impl(
                    Var("obrazok"),
                    And(
                        Or(Var("macka"),Var("pes")),
                        Or(Not(Var("macka")),Not(Var("pes")))
                    )
                );
            Formula ax2 = Impl(Var("macka"), Var("roztrha"));
            Formula ax3 = Impl(Var("roztrha"), Var("smutna"));
            Formula conclusion = Impl(
                            And(  Var("obrazok"), Not(Var("smutna"))  ),
                            Var("pes")
                        );

            Formula cax1 = And( ax1, ax2, ax3 );
            t.testTableau(true, SFS( T(And(cax1, Not(conclusion))) ));
            t.testTableau(true, SFS( F(Impl(cax1, conclusion)) ));
            t.testTableau(true, SFS( T(cax1), F(conclusion) ));
            t.testTableau(true, SFS( T(ax1), T(ax2), T(ax3), F(conclusion) ));
            t.testTableau(false, SFS( T(cax1) ));
            t.testTableau(false, SFS( F(conclusion) ));
        }

        {
            // Bez práce nie sú koláče. Ak niekto nemá ani koláče, ani chleba, tak bude
            // hladný. Na chlieb treba múku. Dokážte, že ak niekto nemá múku a je
            // najedený (nie je hladný), tak pracoval.
            Formula ax1 = Impl(Var("kolace"), Var("praca"));

            Formula ax2 = Impl(
                    And(Not(Var("kolace")),Not(Var("chlieb"))),
                    Var("hlad")
                );
            Formula ax3 = Impl(Var("chlieb"), Var("muka"));

            Formula conclusion = Impl(
                            And(  Not(Var("muka")), Not(Var("hlad"))  ),
                            Var("praca")
                        );

            Formula cax1 = And( ax1, ax2, ax3 );
            t.testTableau(true, SFS( T(And(cax1, Not(conclusion))) ));
            t.testTableau(true, SFS( F(Impl(cax1, conclusion)) ));
            t.testTableau(true, SFS( T(cax1), F(conclusion) ));
            t.testTableau(true, SFS( T(ax1), T(ax2), T(ax3), F(conclusion) ));
            t.testTableau(false, SFS( T(cax1) ));
            t.testTableau(false, SFS( F(conclusion) ));
        }


        if (!correctRules) {
            System.err.println();
            System.err.println();
            System.err.println("WARNING:");
            System.err.println("getType and signedSub implementations are not correct.");
            System.err.println("Any PASSED tableaux can be false positives!");
            System.err.println();
        }

        System.exit(t.status() ? 0 : 1);
    }
}
