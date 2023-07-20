package ifis.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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


    

    @Override
    protected void constructFromChildren() {
        
        List<SHACLNode> notNots = new ArrayList<>();
        List<SHACLNode> nots = new ArrayList<>();

        // Parition
        for (var child:_children) {
            if (child instanceof NotNode) nots.add(child);
            else notNots.add(child);
        } 


        // Find smallest childset
        var childWSmallestSet2 = notNots.get(0);

        // We can assume that all children have had their validBindings constructed
        for (var child:notNots) childWSmallestSet2 = child.validBindings.size() < childWSmallestSet2.validBindings.size() ? child : childWSmallestSet2;

        final var childWSmallestSet = childWSmallestSet2;

            
        // Now we can construct
        validBindings = childWSmallestSet.validBindings.stream()
            .filter(b->{
                // First check all notNots
                for (var other:notNots) {
                    if (other == childWSmallestSet) continue;
                    if (!other.validBindings.contains(b)) return false;
                }
                // Then check all nots, as inverted Lists
                for (var not:nots) {
                    if (not.validBindings.contains(b)) return false;
                }
                return true;
            })
            .collect(Collectors.toSet());

        
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
