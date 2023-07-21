package ifis.logic;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

public class QueryNode extends SHACLNode {

    public QueryNode(Shape shape) {
        super(shape);
        //TODO Auto-generated constructor stub
    }

    public String queryString = null;


    @Override
    protected void constructFromChildren() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String getReportString() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean validates(Node atom) {
        // TODO Auto-generated method stub
        return false;
    }
    
}
