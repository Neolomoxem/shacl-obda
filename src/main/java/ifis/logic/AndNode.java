package ifis.logic;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

public class AndNode extends SHACLNode {

    protected Set<Node> validAtoms;

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

    public void construct() {
        var bindingVar = getBindingVar();

        var smallestMap = _children.get(0).hashes.get(bindingVar);

        for (var child:_children) {
            var childSet = child.hashes.get(bindingVar);
            smallestMap = smallestMap.size() < childSet.size() ? smallestMap : childSet;
        }

        validAtoms = smallestMap.keySet()
            .stream()
            .filter((mentioned) -> {
                for (var child:_children) {
                    if (!child.hashes.get(bindingVar).keySet().contains(mentioned)) return false;
                }
                return true;
            }).collect(Collectors.toSet());

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
