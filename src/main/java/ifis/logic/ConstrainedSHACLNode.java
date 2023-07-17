package ifis.logic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.engine.constraint.ConstraintOp;
import org.apache.jena.shacl.parser.Constraint;
import org.apache.jena.shacl.parser.Shape;
import org.apache.jena.sparql.engine.binding.Binding;

import ifis.BindingFilter;
import ifis.ValidationException;

public abstract class ConstrainedSHACLNode extends SHACLNode{

    protected final Set<Constraint> constraints;
        // Filters to be applied in validation, these are different from sparql filters!
    private final HashSet<BindingFilter> bindingFilters;
    public List<Binding> filteredBindings;
    


    public Set<Constraint> getConstraints() {
        return constraints;
    }
    public ConstrainedSHACLNode(Shape shape) {
        super(shape);
        bindingFilters = new HashSet<>();

        constraints = new HashSet<Constraint>();
    }

    

    public void addConstraint(Constraint c) {
        if (c instanceof ConstraintOp) throw new ValidationException("Internal: Tried to add a OpConstraint to a ConstrainedSHACLNode");
        constraints.add(c);
    }
    public HashSet<BindingFilter> getBindingFilters() {
        return bindingFilters;
    }
    
    public void addBindingFilter(BindingFilter f){
        bindingFilters.add(f);
    }

    
    
}
