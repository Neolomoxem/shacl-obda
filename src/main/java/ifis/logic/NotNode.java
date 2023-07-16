package ifis.logic;

import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

public class NotNode extends SHACLNode {

    public NotNode(Shape shape) {
        super(shape);
    }

    @Override
    public boolean validates(Node atom) {
        /* 
         * If child validates the node
         * then the not validates not
         */

        return !_children.get(0).validates(atom);
        
    }
    @Override
    public boolean validatesRes(Node atom, Set<SHACLNode> valNodes) {
        /* 
         * If child validates the node
         * then the not validates not
         */

        // For complete reasoning, we have to traverse the whole tree, so no shortcuts.
        
            
        if (!_children.get(0).validatesRes(atom, valNodes)) {
            valNodes.add(this);
            return true;
        } else {

            return false;
        }
    }

    @Override
    public String getReportString() {
        return "N͟O͟T͟";
    }
    

    
    
}
