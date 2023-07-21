package ifis.logic;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.engine.constraint.ClassConstraint;
import org.apache.jena.shacl.parser.Shape;
import org.apache.jena.sparql.path.P_Link;

public class EqualNode extends SHACLNode{
    
    public EqualNode(Shape shape) {
        super(shape);

    }

    public Node param1;
    public Node param2;

    @Override
    protected void constructFromChildren() {
        var c1 = _children.get(0)._children.get(1)._children.get(0).retained;
        var c2 = _children.get(1)._children.get(1)._children.get(0).retained;

        for (var b:c1.keySet()) {
            var c1_vals = c1.get(b);
            var c2_vals = c2.get(b);
            
            if (c1_vals == c2_vals) {
                validBindings.add(b);
            }
        }
        
    }

    @Override
    public String getReportString() {
        return "EQUALS";
    }

    @Override
    public boolean validates(Node atom) {
        
        throw new UnsupportedOperationException("Unimplemented method 'validates'");
    }

    
}
