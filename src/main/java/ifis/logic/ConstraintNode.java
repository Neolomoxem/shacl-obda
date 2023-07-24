package ifis.logic;

import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;


/* Represents a reachable node from a target node */
public class ConstraintNode extends ConstrainedSHACLNode {

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



    
    
    
    
    
}
