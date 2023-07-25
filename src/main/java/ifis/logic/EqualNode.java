package ifis.logic;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

public class EqualNode extends SHACLNode{

    public boolean smallerThan = false;
    
    public EqualNode(Shape shape) {
        super(shape);

    }

    public Node param1;
    public Node param2;

    @Override
    protected void constructFromChildren() {
        var c1 = _children.get(0)._children.get(0)._children.get(0).retained;
        var c2 = _children.get(1)._children.get(0)._children.get(0).retained;

        for (var b:_children.get(0).validBindings) {
            var c1_vals = c1.get(b);
            var c2_vals = c2.get(b);
            
            if (smallerThan) {
                var smaller = true;
                for (var bc1:c1_vals) {
                    for (var bc2:c2_vals) {
                        if (Float.valueOf(bc1.getLiteralValue().toString()) > Float.valueOf(bc2.getLiteralValue().toString())) smaller = false; 
                    }
                }
                if (smaller) validBindings.add(b);
            } else {
                if (c1_vals.equals(c2_vals)) {
                    validBindings.add(b);
                }
            }


        }
        
    }

    @Override
    public String getReportString() {
        return smallerThan ? "SMALLER OR EQUAL" : "EQUAL";
    }

    @Override
    public boolean validates(Node atom) {
        
        throw new UnsupportedOperationException("Unimplemented method 'validates'");
    }

    
}
