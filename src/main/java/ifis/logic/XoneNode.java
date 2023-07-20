package ifis.logic;

import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

public class XoneNode extends SHACLNode{

    
    

    @Override
    protected void constructFromChildren() {
        // TODO Auto-generated method stub
        
    }

    public XoneNode(Shape shape) {
        super(shape);
    }

    @Override
    public boolean validates(Node atom) {
        /* 
         * Iff exactly one child validates
         * the Xone validates.
         */

        for (int i = 0; i<_children.size(); i++) {
            // If one validates, there must not be another
            if (_children.get(i).validates(atom)) {
                // Run for all the rest
                for (int x = i+1; x< _children.size(); x++) {
                    // At least 2 validate --> false
                    if (_children.get(i).validates(atom)) return false;
                }
                // If inner loop finishes, only one validates --> true
                return true;
            }
        }
        
        // If outer loop finishes, none validate --> false
        return false;

    }

    @Override
    public boolean validatesRes(Node atom, Set<SHACLNode> valNodes) {
        /* 
         * Iff exactly one child validates
         * the Xone validates.
         */

        // For complete reasoning, we have to traverse the whole tree, so no shortcuts.
        var count = _children
                .stream()
                .map((child) -> child.validatesRes(atom, valNodes))
                .filter((res) -> res==true)
                .count();
            
        if (count == 1) {
            valNodes.add(this);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String getReportString() {
        return "X͟O͟N͟E͟";
    }
    

    

    
    
}
