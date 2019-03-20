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

    public SignedFormula.Type signedType(boolean sign);
    public SignedFormula[] signedSubf(boolean sign);
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
    public SignedFormula.Type signedType(boolean sign) {
        return SignedFormula.Type.Alpha;
    }
    public SignedFormula[] signedSubf(boolean sign) {
        return new SignedFormula[0];
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

    public SignedFormula.Type signedType(boolean sign) {
        return SignedFormula.Type.Alpha;
    }
    public SignedFormula[] signedSubf(boolean sign) {
        return new SignedFormula[0];
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
}

class Conjunction extends CompositeFormula {
    public Conjunction(Formula[] formulas) {
        super(formulas, "&");
    }
}

class Disjunction extends CompositeFormula {
    public Disjunction(Formula[] formulas) {
        super(formulas, "|");
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
}

class Implication extends BinaryFormula {
    public Implication(Formula a, Formula b) {
        super(a, b, "->");
    }
}

class Equivalence extends BinaryFormula {
    public Equivalence(Formula a, Formula b) {
        super(a, b, "<->");
    }
}
