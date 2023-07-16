package ifis.logic;

import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

public class AndNode extends SHACLNode {

    

    public AndNode(Shape shape) {
        super(shape);
    }

    @Override
    public boolean validates(Node atom) {
        /* 
         * If all child nodes validate the atom
         * then the AND validates the atom
         */

        for (var child : _children) {
            if (!child.validates(atom)) return false;
        }
        return true;

    }

    @Override
    public boolean validatesRes(Node atom, Set<SHACLNode> valNodes) {
        /* 
         * If all children validate, the AND validates
         */

        // For complete reasoning, we have to traverse the whole tree, so no shortcuts.
        var count = _children
                .stream()
                .map((child) -> child.validatesRes(atom, valNodes))
                .filter((res) -> res==false)
                .count();
            
        if (count == 0) {
            valNodes.add(this);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String getReportString() {
        return "A͟N͟D͟";
    }  

}
