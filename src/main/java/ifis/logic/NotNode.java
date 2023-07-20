package ifis.logic;

import java.util.List;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

import ifis.ValidationException;

public class NotNode extends SHACLNode {

    

    

    public NotNode(Shape shape) {
        super(shape);
    }

    @Override
    protected void constructFromChildren() {
        if (_children.size() != 1) throw new ValidationException("More than one child under a NOT");
        var childBindings = _children.get(0).validBindings;


        if (parent instanceof AndNode && parent._children.size() != 1) {
            // If the parent is an AndNode we can just mark these nodes as negated and leave it to be parsed 
            this.validBindings = childBindings;
        } else {

            // In this case the baseline query has been run
            for (var b:childBindings) {
                baseBindings.remove(b);
            }

        }

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
