import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
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
        System.err.println("FAILED: " + msg);
    }

    public void startCase(String s) {
        System.err.println(String.format("CASE %d: %s", ++ncase, s));
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

    public boolean checkWatches(Theory t, String msg) {
        boolean ok = true;
        for (Variable v : t.vars().values()) {
            for (Literal ll : Arrays.asList(v.lit(true), v.lit(false))) {
                Set<Clause> watchedIn = new HashSet<Clause>();
                for (Clause c : t.cnf()) {
                    if (ll == c.watched()[0] || ll == c.watched()[1]) {
                        watchedIn.add(c);
                    }
                }
                ok = ok && compare(ll.watchedIn(), watchedIn, msg + ": bad watchedIn for literal " + ll);
            }
        }
        return ok;
    }

    public void testSetLiteral(boolean willBeSat, String assignStr, String name, Theory t) {
        List<Literal> assign = assignStr.isEmpty() ? Collections.emptyList() : Pattern.compile("[ ∨]+").splitAsStream(assignStr)
                .map(cs -> Literal.fromString(cs, t.vars()))
                .collect(toList())
        ;
        startCase(name);

        try (Tester.Scope s = scope(name)) {
            Set<UnitClause> units = new HashSet<UnitClause>();
            boolean initRes = t.initWatched(units);
            compare(initRes, assign.size() > 0 ? true : willBeSat, "initWatched should return that it's unsatisfiable");
            Set<UnitClause> realUnits = units(t);
            compare(units, realUnits, "wrong unit clauses reported after initWatched!");

            int toGo = assign.size();
            String assigned = "";
            for (Literal l : assign) {
                units = new HashSet<UnitClause>();
                Set<UnitClause> oldUnits = units(t);

                boolean ret = t.setLiteral(l, units);
                message(l + " -> true " + "	" + "   vars: " + t.vars().values());

                assigned = assigned + " " + l;

                Set<UnitClause> newUnits = units(t);
                newUnits.removeAll(oldUnits);
                compare(units, newUnits, "Wrong unit clauses reported after setting literals: " + assigned + " vars: " + t.vars().values());

                boolean expRet = (--toGo > 0) ? true : willBeSat;
                compare(ret, expRet, "setLiteral returned wrong value after setting literals " + assigned);

                compare(l.isTrue(), true, "literal was not set or set to wrong value! "  + l + ": " + l.variable());

                checkWatches(t, "After setting literals " + assigned);
            }

            Collections.reverse(assign);
            for (Literal l : assign) {
                t.unsetLiteral();
                message("unsetLiteral()" + "	" + "   vars: " + t.vars().values());
                compare(l.isSet(), false, "literal was not unset properly (expected to unset " + l + ") " + l + ".isSet()");
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


        try (Tester.Scope s = t.scope("setWatch")) {
            t.startCase("setWatch");
            Theory T = T("a b c -e -f -g");
            Clause C = T.cnf().iterator().next();

            t.compareRef(C.watched()[0], null, "Watch 0 is null at the beginning");
            t.compareRef(C.watched()[1], null, "Watch 1 is null at the beginning");

            t.message("set watch 0 to a");
            Literal a = T.vars().get("a").lit(true);
            C.setWatch(0, a);
            t.compare(C.watched()[0], a, "Watch 0 set to a");
            t.checkWatches(T, "watchedIn after watch 0 set to a");

            t.message("set watch 1 to -e");
            Literal ne = T.vars().get("e").lit(false);
            C.setWatch(1, ne);
            t.compare(C.watched()[1], ne, "Watch 1 set to -e");
            t.checkWatches(T, "watchedIn after watch 1 set to -e");

            t.message("change watch 0 from a to b");
            Literal b = T.vars().get("b").lit(true);
            C.setWatch(0, b);
            t.compare(C.watched()[0], b, "Watch 0 after change from a to b");
            t.checkWatches(T, "watchedIn after watch 0 changed from a to b");
        }
        catch (Throwable e) {
            t.fail("Exception: " + e.toString());
            e.printStackTrace();
        }

        try (Tester.Scope s = t.scope("findNewWatch")) {
            t.startCase("findNewWatch");
            Theory T = T("a b c -e -f -g");
            Clause C = T.cnf().iterator().next();
            Literal a = T.vars().get("a").lit(true);
            Literal ne = T.vars().get("e").lit(false);
            C.setWatch(0, a);
            t.message("setWatch(0,a)" + "	" + C);
            C.setWatch(1, ne);
            t.message("setWatch(1,ne)" + "	" + C);
            t.compare(C.watched()[0], a, "Watch 0 set to a");
            t.compare(C.watched()[1], ne, "Watch 1 set to -e");
            t.checkWatches(T, "watchedIn at the beginning");

            Literal w = null;
            boolean ret = false;
            boolean ok = true;

            for (int which : Arrays.asList(0, 1, 0, 1)) {
                if (!ok) break;
                w = C.watched()[which];
                w.not().setTrue();
                t.message(w + " -> false" + "	" + C + "   vars: " + T.vars().values());
                ret = C.findNewWatch(w);
                t.message("findNewWatch(" + w + ")"+ "	" + C + "   vars: " + T.vars().values());
                ok = ok && t.compare(ret, true, "findNewWatch should return true");
                ok = ok && t.compare(!C.watched()[which].isSet() || C.watched()[which].isTrue(), true, "new watched should be unset or true");
                ok = ok && t.checkWatches(T, "watchedIn after findNewWatch");
            }

            // now it's not possible to find new ones, the first time other will still be unset (unit clause), the second time it will be false
            for (int which : Arrays.asList(0, 1)) {
                if (!ok) break;
                w = C.watched()[which];
                w.not().setTrue();
                t.message(w + " -> false" + "	" + C + "   vars: " + T.vars().values());
                ret = C.findNewWatch(w);
                t.message("findNewWatch(" + w + ")"+ "	" + C + "   vars: " + T.vars().values());
                ok = ok && t.compare(ret, false, "findNewWatch should return false");
                ok = ok && t.compare(C.watched()[which], w, "watched literal should not have changed");
                ok = ok && t.checkWatches(T, "watchedIn after findNewWatch");
            }

        }
        catch (Throwable e) {
            t.fail("Exception: " + e.toString());
            e.printStackTrace();
        }

        try (Tester.Scope s = t.scope("findNewWatch true")) {
            t.startCase("findNewWatch true");
            Theory T = T("a b c");
            Clause C = T.cnf().iterator().next();
            Literal a = T.vars().get("a").lit(true);
            Literal b = T.vars().get("b").lit(true);
            Literal c = T.vars().get("c").lit(true);
            C.setWatch(0, a);
            t.message("setWatch(0,a)" + "	" + C);
            C.setWatch(1, b);
            t.message("setWatch(1,b)" + "	" + C);
            t.compare(C.watched()[0], a, "Watch 0 set to a");
            t.compare(C.watched()[1], b, "Watch 1 set to b");

            c.setTrue();
            t.message(c + " -> true " + "	" + C + "   vars: " + T.vars().values());
            a.not().setTrue();
            t.message(a + " -> false" + "	" + C + "   vars: " + T.vars().values());

            boolean ret = C.findNewWatch(a);
            t.message("findNewWatch(" + a + ")"+ "	" + C + "   vars: " + T.vars().values());
            t.compare(ret, true, "findNewWatch should find the true literal");
            t.compare(C.watched()[0], c, "findNewWatch should find the true literal");

        }
        catch (Throwable e) {
            t.fail("Exception: " + e.toString());
            e.printStackTrace();
        }

        t.testSetLiteral(true, "a -b", "setLiteral simple",
            T("a b c", "-b")
        );

        t.testSetLiteral(false, "-a -b -c -d -e", "setLiteral unsat",
            T("a b c d e")
        );

        t.testSetLiteral(false, "", "empty clause", T(""));

        System.exit(t.status() ? 0 : 1);
    }
}
