package ifis.logic;

import java.util.List;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

import ifis.ValidationException;

public class NotNode extends SHACLNode {

    
    private boolean inverted = false;
    

    public NotNode(Shape shape) {
        super(shape);
    }

    @Override
    protected void constructFromChildren() {
        if (_children.size() != 1) throw new ValidationException("More than one child under a NOT");
        var childBindings = _children.get(0).validBindings;


        if (parent instanceof AndNode && parent._children.size() != 1) {
            // If the parent is an AndNode we can just mark these nodes as negated and leave it to be parsed
            this.inverted = true;
            this.validBindings = childBindings;
        } else {

            // In this case the baseline query has been run
            for (var b:childBindings) {
                baseBindings.remove(b);
            }

            validBindings = baseBindings;

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
        // If valid Targets arent extracted from validBindings, do it now and memoize
        if (validTargets == null) extractValidTargets();
        // If this Node contains, add it to valNodes

        if (inverted) {
            if (!validTargets.contains(atom)) valNodes.add(this);
            // Check the children
            for (var child:_children) child.validatesRes(atom, valNodes);
            return !validTargets.contains(atom);

        } else {
            if (validTargets.contains(atom)) valNodes.add(this);
            // Check the children
            for (var child:_children) child.validatesRes(atom, valNodes);
            return validTargets.contains(atom);

        }

    }


    @Override
    public String getReportString() {
        return "N͟O͟T͟";
    }

    

    
    
}
