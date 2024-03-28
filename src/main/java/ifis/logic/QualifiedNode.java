package ifis.logic;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

public class QualifiedNode extends SHACLNode {


    private final Integer minCount;
    private final Integer maxCount;

    public QualifiedNode(Shape shape, Integer minCount, Integer maxCount) {
        super(shape);
        this.minCount = minCount;
        this.maxCount = maxCount;
    }

    @Override
    protected void constructFromChildren() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String getReportString() {

        String lower = minCount == -1 ? "0" : minCount.toString();
        String upper = maxCount == -1 ? "∞" : maxCount.toString();
        
        return STR."QualifiedValue [\{lower}, \{upper}]";

    }

    @Override
    public boolean validates(Node atom) {
        // TODO Auto-generated method stub
        return false;
    }
    
}
