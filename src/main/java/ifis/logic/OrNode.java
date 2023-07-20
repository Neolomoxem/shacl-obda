package ifis.logic;

import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

public class OrNode extends SHACLNode {



    
    public OrNode(Shape shape) {
        super(shape);
    }

    @Override
    public boolean validates(Node atom) {
        
        // If any child validates the atom, the OR is validated
        for (var child: _children) {
           if (child.validates(atom)) return true; 
        }

        return false;
    }

    

    
    

    
    @Override
    protected void constructFromChildren() {       
        // We can assume that all children have had their validBindings constructed
        for (var child:_children) {
            validBindings.addAll(child.validBindings);
        }        
    }

    @Override
    public boolean validatesRes(Node atom, Set<SHACLNode> valNodes) {
        /* 
         * if at least one child validates, the OR validates
         */

        // For complete reasoning, we have to traverse the whole tree, so no shortcuts.
        var count = _children
                .stream()
                .map((child) -> child.validatesRes(atom, valNodes))
                .filter((res) -> res==true)
                .count();
            
        if (count > 0) {
            valNodes.add(this);
            return true;
        } else {

            return false;
        }
    }

    @Override
    public String getReportString() {
        return "O͟R͟";
    }
    
}
