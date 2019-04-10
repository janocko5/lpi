import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;


class Tester {
    int tested = 0;
    int passed = 0;
    int ncase = 0;

    public boolean compare(Object result, Object expected, String msg) {
        tested++;
        if (result.equals(expected)) {
            passed++;
            return true;
        } else {
            System.err.println("    Failed: " + msg + ":");
            System.err.println("      got " + result + " expected " + expected);
            return false;
        }
    }

    public void fail(String msg) {
        tested++;
        System.err.println("FAILED: " + msg);
    }

    public void startCase(String s) {
        System.err.println(String.format("CASE %d: %s", ++ncase, s));
    }

    public Set<Clause> asSet(List<Clause> cs) {
        return new HashSet<Clause>(cs);
    }


    public Set<UnitClause> units(Theory t) {
        Set<UnitClause> units = new HashSet<UnitClause>();
        for (Clause c : t.cnf()) {
            int size = c.size();
            int nFalse = 0;
            int nUnset = 0;
            Literal unset = null;
            for (Literal l : c) {
                if (l.isFalse()) ++nFalse;
                if (!l.isSet()) {
                    ++nUnset;
                    unset = l;
                }
            }
            if (nUnset == 1 && nFalse == size - 1)
                units.add(new UnitClause(c, unset));
        }
        return units;
    }

    public void testSetLiteral(boolean willBeSat, String assignStr, String name, Theory t) {
        List<Literal> assign = Pattern.compile("[ ∨]+").splitAsStream(assignStr)
                .map(cs -> Literal.fromString(cs, t.vars()))
                .collect(toList())
        ;
        startCase("setLiteral " + name);

        try {
            Set<UnitClause> units = new HashSet<UnitClause>();
            boolean initRes = t.initWatched(units);
            compare(initRes, true, "initWatched thinks it's unsatisfiable");
            Set<UnitClause> realUnits = units(t);
            compare(units, realUnits, "wrong unit clauses reported after initWatched!");

            for (Literal l : assign) {
                // TODO check return value
                t.setLiteral(l, units);
                compare(l.isTrue(), true, "literal was not set or set to wrong value! "  + l + ": " + l.variable());
                for (Variable v : t.vars().values()) {
                    for (Literal ll : Arrays.asList(v.lit(true), v.lit(false))) {
                        Set<Clause> watchedIn = new HashSet<Clause>();
                        for (Clause c : t.cnf()) {
                            if (ll == c.watched()[0] || ll == c.watched()[1]) {
                                watchedIn.add(c);
                            }
                        }
                        compare(ll.watchedIn(), watchedIn, "Bad watchedIn for literal " + ll + " after setting literal " + l);
                    }
                }
            }

            Collections.reverse(assign);
            for (Literal l : assign) {
                t.unsetLiteral();
                compare(l.isSet(), false, "literal was not unset properly!");
            }

        }
        catch (Throwable e) {
            fail("Exception: " + e.toString());
            e.printStackTrace();
        }
    }

    public boolean status() {
        System.err.println("");
        System.err.println("TESTED " + tested);
        System.err.println("PASSED " + passed);

        System.err.println(tested == passed ? "OK" : "ERROR" );
        return tested == passed;
    }

}

public class WatchedLiteralsTest {
    public static Theory T(String... cls) { return new Theory(cls); }
    public static void main(String[] args) {
        Tester t = new Tester();

        t.testSetLiteral(true, "a -b", "simple",
            T("a b c", "-b")
        );

        System.exit(t.status() ? 0 : 1);
    }
}
