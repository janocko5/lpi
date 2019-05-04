import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface Formula {
    public Formula[] subf();
    public String toString();
    public boolean equals(Formula other);
    public Set<String> vars();

    public Cnf toCnf();
}

class Variable implements Formula {
    private static int newNum = 0;
    private String name;

    Variable(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public Formula[] subf() {
        return new Formula[]{};
    }

    public String toString() {
        return name();
    }

    public boolean equals(Formula other) {
        if (this == other) return true;
        if (getClass() != other.getClass()) return false;
        Variable otherVar = (Variable)other;
        return name().equals(otherVar.name());
    }

    public Set<String> vars() {
        return new HashSet<String>(Arrays.asList(name()));
    }

    /**
     * @return a new variable name that is not used
     */
    public static String newName() {
        // Yes, this is a hack, but this is
        // guaranteed to be unique in our tests ;-)
        return "_x" + ++newNum;
    }

    public Cnf toCnf() {
        /* TODO Implement this! */
        return new Cnf(new Clause(new Literal(toString())));
    }
}

class CompositeFormula implements Formula {
    Formula[] subs;
    String conn;
    CompositeFormula(Formula[] subs, String conn) {
        this.subs = subs;
        this.conn = conn;
    }
    public Formula[] subf() { return subs; }

    public String toString() {
        return "("
            + Arrays.stream(this.subf())
                .map( f -> f.toString() )
                .collect(Collectors.joining(this.conn))
            + ")"
        ;
    }

    public boolean equals(Formula other) {
        if (this == other) return true;
        if (getClass() != other.getClass()) return false;
        if (subf().length != other.subf().length) return false;
        for (int i=0; i < subf().length; ++i)
            if (!subf()[i].equals(other.subf()[i])) return false;
        return true;
    }

    public Set<String> vars() {
        Set<String> vs = new HashSet<String>();
        for (Formula f : subf()) {
            vs.addAll(f.vars());
        }
        return vs;
    }

    public Cnf toCnf() {
        return new Cnf();
    }
}

class Negation extends CompositeFormula {
    public Negation(Formula formula) {
        super(new Formula[]{formula}, "-");
    }

    public Formula originalFormula() {
        return subf()[0];
    }

    @Override
    public String toString() {
        return "-" + originalFormula().toString();
    }

    @Override
    public Cnf toCnf() {
        Cnf podf = new Cnf();
        podf.addAll(this.originalFormula().toCnf());
        try{
            podf.remove(0);
        }
        catch(Exception e){

        }

        podf.add(new Clause(new Literal(toString()), new Literal(originalFormula().toString())));
        podf.add(new Clause(Literal.Not(originalFormula().toString()), Literal.Not(toString())));

        podf.add(0, new Clause(new Literal(toString())));
        return podf;
    }
}

class Conjunction extends CompositeFormula {
    public Conjunction(Formula[] formulas) {
        super(formulas, "&");
    }

    @Override
    public Cnf toCnf() {
        Cnf podf = new Cnf();
        try{
            podf.remove(0);
        }
        catch(Exception e){

        }
        Clause cl = new Clause(new Literal(toString()));
        for (Formula f: this.subf()) {
            podf.addAll(f.toCnf());
            podf.add(new Clause(Literal.Not(toString()), new Literal(f.toString())));
            cl.add(Literal.Not(f.toString()));
        }
        podf.add(cl);
        podf.add(0, new Clause(new Literal(toString())));

        return podf;

    }
}

class Disjunction extends CompositeFormula {
    public Disjunction(Formula[] formulas) {
        super(formulas, "|");
    }

    @Override
    public Cnf toCnf() {
        Cnf podf = new Cnf();
        try{
            podf.remove(0);
        }
        catch(Exception e){

        }

        if(subf().length == 0) { return new Cnf(new Clause(Literal.Not(toString()))); }
        Clause cl = new Clause(Literal.Not(toString()));
        for (Formula f: this.subf()) {
            podf.addAll(f.toCnf());
            podf.add(new Clause(Literal.Not(toString()), new Literal(f.toString())));
            cl.add(new Literal(f.toString()));
        }
        podf.add(cl);
        podf.add(0, new Clause(new Literal(toString())));

        return podf;
    }
}

class BinaryFormula extends CompositeFormula {
    BinaryFormula(Formula leftSide, Formula rightSide, String conn) {
        super(new Formula[]{leftSide, rightSide}, conn);
    }
    public Formula leftSide() {
        return subf()[0];
    }
    public Formula rightSide() {
        return subf()[1];
    }

    @Override
    public Cnf toCnf() {
        return super.toCnf();
    }
}

class Implication extends BinaryFormula {
    public Implication(Formula a, Formula b) {
        super(a, b, "->");
    }

    @Override
    public Cnf toCnf() {
        Cnf podf = new Cnf();
        podf.addAll(leftSide().toCnf());
        try{
            podf.remove(0);
        }
        catch(Exception e){

        }
        podf.addAll(rightSide().toCnf());
        try{
            podf.remove(0);
        }
        catch(Exception e){

        }

        podf.add(new Clause(Literal.Not(toString()), Literal.Not(leftSide().toString()), new Literal(rightSide().toString())));
        podf.add(new Clause(Literal.Not(rightSide().toString()), new Literal(toString())));
        podf.add(new Clause(new Literal(leftSide().toString()), new Literal(toString())));
        podf.add(0, new Clause(new Literal(toString())));

        return podf;
    }
}

class Equivalence extends BinaryFormula {
    public Equivalence(Formula a, Formula b) {
        super(a, b, "<->");
    }

    @Override
    public Cnf toCnf() {
        Implication prva = new Implication(leftSide(), rightSide());
        Implication druha = new Implication(rightSide(), leftSide());

        Formula[] formulas = {prva, druha};
        Conjunction spolu = new Conjunction(formulas);
        return spolu.toCnf();
    }
}
