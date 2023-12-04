package ifis.logic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.engine.constraint.ConstraintOp;
import org.apache.jena.shacl.parser.Constraint;
import org.apache.jena.shacl.parser.Shape;
import org.apache.jena.sparql.engine.binding.Binding;

import ifis.BindingFilter;
import ifis.exception.ValidationException;


/* Represents a reachable node from a target node */
public class ConstraintNode extends SHACLNode{

    // If the cardinality is only constrained in MAX then the validatingAtoms
    // contain all INvalidating atoms.
    private boolean inverted;
    
    public SHACLNode tree;



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
    protected void constructFromChildren() {
        if (_children.size() != 0) this.validBindings = _children.get(0).validBindings;
        
    }


    @Override
    public boolean validates(Node atom) {
        // TODO Auto-generated method stub
        return false;
    }




    @Override
    public String getReportString() {
        
        var s = new StringBuilder("⬤ > ");
        for (var c:constraints) {
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
