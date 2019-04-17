import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

class Tester {
    int tested = 0;
    int passed = 0;
    int ncase = 0;
    int time = 0;
    Deque<Scope> scopes = new ArrayDeque<Scope>();
    Scope lastScope = null;

    class Scope implements AutoCloseable{
        private final Tester t;
        public final String name;
        public final Deque<String> messages = new ArrayDeque<String>();
        Scope(Tester t, String name) {
            this.t = t;
            this.name = name;
            t.scopes.push(this);
        }
        public void close() {
            t.scopes.pop();
            t.lastScope = t.scopes.peek();
        }
        public void message(String msg) {
            messages.push(msg);
        }
        public void print(String prefix) {
            System.err.println(prefix + "In " + name);
            messages.descendingIterator().forEachRemaining(msg -> System.err.println(prefix + "  " + msg));
        }
    }

    public Scope scope(String msg) {
        lastScope = new Scope(this, msg);
        return lastScope;
    }
    public void message(String msg) {
        // throws if there isn't a scope
        lastScope.message(msg);
    }
    public void printScopes(String prefix) {
        scopes.descendingIterator().forEachRemaining(s -> s.print(prefix + "  "));
    }

    public boolean compare(Object result, Object expected, String msg) {
        tested++;
        if (expected.equals(result)) {
            passed++;
            return true;
        } else {
            printScopes("  ");
            System.err.println("    Failed: " + msg + ":");
            System.err.println("      got " + result + " expected " + expected);
            return false;
        }
    }

    public <T> boolean compareRef(T result, T expected, String msg) {
        tested++;
        if (result == expected) {
            passed++;
            return true;
        } else {
            printScopes("  ");
            System.err.println("    Failed: " + msg + ":");
            System.err.println("      got " + result + " expected " + expected);
            return false;
        }
    }

    public void fail(String msg) {
        tested++;
        printScopes("  ");
        System.err.println("    Failed: " + msg);
    }

    public void startCase(String s) {
        System.err.println(String.format("CASE %d: %s", ++ncase, s));
    }

    public static String toString(Clause c) {
        if (c.isEmpty()) return "()";
        return c.stream()
            .map(l -> l.toString())
            .collect(Collectors.joining(" "))
        ;
    }
    public static String toString(Cnf cnf) {
        return cnf.stream()
            .map(c -> toString(c))
            .collect(Collectors.joining("; "))
        ;
    }

    public static boolean isSatisfied(Literal l, Map<String,Boolean> v) {
        return l.sign() == v.get(l.name());
    }

    public static boolean isSatisfied(Clause c, Map<String, Boolean> v) {
        return c.stream().anyMatch(l -> isSatisfied(l, v));
    }

    public void test(boolean expSat, String name, Theory t) {
        startCase(name);
        try (Tester.Scope s = scope(name)) {
            try {

                long start = System.nanoTime();
                SatSolver.Result res = (new SatSolver()).solve(t);
                long duration = (System.nanoTime() - start) / 1000;
                time += duration;
                message("ran in " + (duration / 1000.0) + "ms");

                if (!compare(res.sat, expSat, "bad sat/unsat result"))

                if (res.sat) {
                    message("expSat: " + expSat);
                    message("res.sat: " + res.sat);
                    message("res.valuation: " + res.valuation);

                    if (res.valuation == null) {
                        fail("returned sat == true, but valuation == null");
                        return;
                   }

                    for (String v : res.valuation.keySet())
                        if (res.valuation.get(v) == null) {
                            fail("Variable " + v + " is unset");
                            return;
                        }

                    for (Clause cls : t.cnf())
                        if (!isSatisfied(cls, res.valuation)) {
                            fail("Clause " + toString(cls) + " is not satisfied");
                            return;
                        }
                }

                System.err.println("PASSED in " + (duration / 1000.0) + "ms");
            }
            catch (Throwable e) {
                printScopes("  ");
                fail("Exception: " + e.toString());
                e.printStackTrace();
            }
        }
    }

    public void test(boolean expSat, Theory t) {
        test(expSat, toString(t.cnf()), t);
    }

    public boolean status() {
        System.err.println("");
        System.err.println("TESTED " + tested);
        System.err.println("PASSED " + passed);
        System.err.println("SUM(time) " + (time / 1000.0) + "ms");

        System.err.println(tested == passed ? "OK" : "ERROR" );
        return tested == passed;
    }

}

public class SatSolverTest {
    static String C(int... ns) { return Arrays.stream(ns).mapToObj(Integer::toString).collect(Collectors.joining(" ")); }
    public static Theory T(String... cls) { return new Theory(cls); }
    static Theory trueChain(int n) {
        List<String> cnf = new ArrayList<String>();
        cnf.add(C(1, 2));
        cnf.add(C(-n, -1));
        for (int i = 0; i < n; ++i)
            cnf.add(C(-i, i + 1));
        return T(cnf.toArray(new String[0]));
    }

    static Theory falseChain(int n) {
        List<String> cnf = new ArrayList<String>();
        cnf.add(C(1, 2));
        cnf.add(C(-n, -2));
        for (int i = 0; i < n; ++i)
            cnf.add(C(-i, i + 1));
        return T(cnf.toArray(new String[0]));
    }

    public static void main(String[] args) {
        Tester t = new Tester();

        // Let's not include the first run in timings
        try { (new SatSolver()).solve(T()); } catch (Throwable e) {}

        t.test(true, "empty theory", T());
        t.test(false, "empty clause", T(""));
        t.test(false, T("", "p"));

        t.test(true, T("a"));
        t.test(true, T("-a"));
        t.test(false, T("a", "-a"));
        t.test(true, T("a b"));
        t.test(true, T("a", "b"));
        t.test(true, "chain", T("-a b", "-b c", "-c d", "-d e", "a"));
        t.test(false, "chainUnsat", T("-a b", "-b c", "-c d", "-d e", "a", "-e"));

        t.test(true, T("p q r s t u v", "-p", "-q", "-r", "-s", "-t", "-u"));
        t.test(false, T("p q r s t u v", "-p", "-q", "-r", "-s", "-t", "-u", "-v"));

        t.test(true, "kim jim sara", T(
            "-kim -sarah", "-jim kim", "-sarah jim", "kim jim sarah"
        ));
        t.test(false, "kim jim sara |= kim", T(
            "-kim -sarah", "-jim kim", "-sarah jim", "kim jim sarah", "-kim"
        ));
        t.test(false, "kim jim sara |= -sarah", T(
            "-kim -sarah", "-jim kim", "-sarah jim", "kim jim sarah", "sarah"
        ));
        t.test(true, "kim jim sara |≠ jim", T(
            "-kim -sarah", "-jim kim", "-sarah jim", "kim jim sarah", "-jim"
        ));
        t.test(true, "kim jim sara |≠ -jim", T(
            "-kim -sarah", "-jim kim", "-sarah jim", "kim jim sarah", "jim"
        ));

        t.test(true, "SAT chain 4", trueChain(4));
        t.test(false, "UNSAT chain 4", falseChain(4));
        t.test(true, "SAT chain 20", trueChain(20));
        t.test(false, "UNSAT chain 20", falseChain(20));

        t.test(false, "nqueens3", T(
            C(1,2,3), C(4,5,6), C(7,8,9), C(-2,-1), C(-3,-1), C(-3,-2), C(-5,-4), C(-6,-4),
            C(-6,-5), C(-8,-7), C(-9,-7), C(-9,-8), C(-4,-1), C(-7,-1), C(-7,-4),
            C(-5,-2), C(-8,-2), C(-8,-5), C(-6,-3), C(-9,-3), C(-9,-6), C(-1,-5),
            C(-4,-2), C(-4,-8), C(-7,-5), C(-1,-9), C(-7,-3), C(-2,-4), C(-5,-1),
            C(-5,-7), C(-8,-4), C(-2,-6), C(-5,-3), C(-5,-9), C(-8,-6), C(-3,-7),
            C(-9,-1), C(-3,-5), C(-6,-2), C(-6,-8), C(-9,-5)
            ));
        t.test(true, "nqueens4", TestData.theory_009_q4());
        t.test(true, "uf20_04130", TestData.theory_200_uf20_0413());
        t.test(true, "uf50_0500", TestData.theory_200_uf50_0500());
        t.test(true, "flat100_22", TestData.theory_300_flat100_22());
        t.test(false, "unsat uuf50_0992", TestData.theory_unsat_200_uuf50_0992());

        System.exit(t.status() ? 0 : 1);
    }
}
