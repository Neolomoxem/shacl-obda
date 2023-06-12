package ifis.logic;

import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

public class NotNode extends LogicNode {

    public NotNode(Shape shape) {
        super(shape);
    }

    @Override
    public boolean validates(Node atom) {
        /* 
         * If child validates the node
         * then the not validates not
         */

        return !children.get(0).validates(atom);
        
    }
    @Override
    public boolean validatesRes(Node atom, Set<LogicNode> valNodes) {
        /* 
         * If child validates the node
         * then the not validates not
         */

        // For complete reasoning, we have to traverse the whole tree, so no shortcuts.
        
            
        if (!children.get(0).validatesRes(atom, valNodes)) {
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
