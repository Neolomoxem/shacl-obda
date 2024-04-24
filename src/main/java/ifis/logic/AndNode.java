package ifis.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        for (var child:notNots) childWSmallestSet2 = child.validFocus.size() < childWSmallestSet2.validFocus.size() ? child : childWSmallestSet2;

        final var childWSmallestSet = childWSmallestSet2;

            
        // Now we can construct
        validFocus = childWSmallestSet.validFocus.stream()
            .filter(b->{
                // First check all notNots
                for (var other:notNots) {
                    if (other == childWSmallestSet) continue;
                    if (!other.validFocus.contains(b)) return false;
                }
                // Then check all nots, as inverted Lists
                for (var not:nots) {
                    if (not.validFocus.contains(b)) return false;
                }
                return true;
            })
            .collect(Collectors.toSet());

        
    }


    @Override
    public String getReportString() {
        return "A͟N͟D͟";
    }  

}
