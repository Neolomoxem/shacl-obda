package ifis.logic;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

public class XoneNode extends SHACLNode{

    
    


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
    
    @Override
    protected void constructFromChildren() {       
        // We can assume that all children have had their validBindings constructed
        // If its in more than one,
        var uniqueMap = new HashMap<List<Node>, Boolean>();
        
        for (var child:_children) {
            for (var b:child.validFocus) {
                var c = uniqueMap.get(b);
                uniqueMap.put(b, c == null ? true : false);
            }
        }
        
        this.validFocus = uniqueMap.keySet().stream()
        .filter(b -> uniqueMap.get(b))
        .collect(Collectors.toSet());

    }
    

    
    
}
