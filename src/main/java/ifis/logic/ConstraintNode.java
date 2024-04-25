package ifis.logic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.engine.constraint.ConstraintOp;
import org.apache.jena.shacl.engine.constraint.MaxCount;
import org.apache.jena.shacl.engine.constraint.MinCount;
import org.apache.jena.shacl.parser.Constraint;
import org.apache.jena.shacl.parser.Shape;
import org.apache.jena.sparql.engine.binding.Binding;

import ifis.BindingFilter;
import ifis.exception.ValidationException;

/* Represents a reachable node from a target node */
public class ConstraintNode extends SHACLNode {

    // If the cardinality is only constrained in MAX then the validatingAtoms
    // contain all INvalidating atoms.
    private boolean inverted;

    public SHACLNode tree;

    public MinCount min;
    public MaxCount max;

    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }

    public ConstraintNode(Shape sourceShape) {
        super(sourceShape);
        bindingFilters = new HashSet<>();

        constraints = new HashSet<Constraint>();
    }

    @Override
    // Determines the set of valid Focus by using the parent PShape
    protected void constructFromChildren() {

        validFocus =
            getPShape()
            .getCountMap()
            .entrySet()
            .stream()
            .parallel()
            .filter(entry -> {
                var focus       = entry.getKey();
                var count       = entry.getValue();
                var validCount  = this.countMap.get(focus);
                
                // A focus is trivially valid if it has no values
                if (count == 0) return true;

                // If a focus node (with values, from here on out) has no valid value nodes it cannot be valid
                if (countMap == null) return false;

                // If all values are valid, the focus is valid, barring the count is not in the cardinality range
                if (count == validCount) {
                                    
                    if (min != null && count < min.getMinCount()) return false;
                    if (max != null && count > max.getMaxCount()) return false;

                    return true;
                }

                return false;

            })
            .map(entry -> entry.getKey())
            .collect(Collectors.toSet());
        
    }

    @Override
    public boolean validates(Node atom) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String getReportString() {

        var s = new StringBuilder("⬤ > ");
        for (var c : constraints) {
            s.append(c.toString());
        }

        return s.toString();

    }

    protected final Set<Constraint> constraints;
    
    // Filters to be applied in validation, these are different from sparql filters!
    private final HashSet<BindingFilter> bindingFilters;
    public List<Binding> filteredBindings;

    public Set<Constraint> getConstraints() {
        return constraints;
    }

    public void addConstraint(Constraint c) {
        if (c instanceof ConstraintOp)
            throw new ValidationException("Internal: Tried to add a OpConstraint to a ConstrainedSHACLNode");
        constraints.add(c);
    }

    public HashSet<BindingFilter> getBindingFilters() {
        return bindingFilters;
    }

    public void addBindingFilter(BindingFilter f) {
        bindingFilters.add(f);
    }

}
