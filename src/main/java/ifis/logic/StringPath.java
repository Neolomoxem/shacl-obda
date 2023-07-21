package ifis.logic;

import org.apache.jena.sparql.path.Path;
import org.apache.jena.sparql.path.PathBase;
import org.apache.jena.sparql.path.PathVisitor;
import org.apache.jena.sparql.util.NodeIsomorphismMap;

public class StringPath extends PathBase {

    public String value;

    


    public StringPath(String value) {
        this.value = value;
    }

    @Override
    public boolean equalTo(Path path2, NodeIsomorphismMap isoMap) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public int hashCode() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void visit(PathVisitor visitor) {
        // TODO Auto-generated method stub
        
    }
    
}
