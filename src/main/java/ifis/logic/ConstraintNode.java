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
        
        
    }


    @Override
    public boolean validates(Node atom) {
        // TODO Auto-generated method stub
        return false;
    }



    @Override
    public boolean validatesRes(Node atom, Set<SHACLNode> valNodes) {


        // See field dec
        if (tree != null) {
            if (!inverted) {
                if (tree.validates(atom)) {
                    validatesRes(atom, valNodes);
                    valNodes.add(this);
                    return true;
                } else {
                    return false;
                }
            } else {
                if (!tree.validates(atom)) {
                    validatesRes(atom, valNodes);
                    valNodes.add(this);
                    return true;
                } else {
                    return false;
                }
            }
        }
        if (!inverted) {
            if (validates(atom)) {
                valNodes.add(this);
                return true;
            } else {
                return false;
            }
        } else {
            if (!validates(atom)) {
                valNodes.add(this);
                return true;
            } else {
                return false;
            }
        }
        
        
    }



    @Override
    public String getReportString() {
        
        return getConstraints()
                .stream()
                .map((c) -> c.toString())
                .reduce("⬤ " + "hallo".toString() + "> ", (acc, str) -> acc + str + ", ");
        
    }



    
    
    
    
    
}
