package ifis.logic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.engine.constraint.CardinalityConstraint;
import org.apache.jena.shacl.parser.PropertyShape;
import org.apache.jena.sparql.path.Path;

/* A PathNode is esentially a PropertyShape. It represents all the Valuenodes of a PShape and how to reach them */
public class PShapeNode extends SHACLNode {
    
    public Set<CardinalityConstraint> getCardinalityConstraints() {
        return cardinalityConstraints;
    }

    private final Set<CardinalityConstraint> cardinalityConstraints;

    private String bindingVar;        

    private Path path;
    
    public PShapeNode(PropertyShape shape, String bindingVar) {
        
        super(shape);

        // Whenever a new PropertyShape is parsed, a new Variable will be generated
        this.bindingVar = bindingVar;    
        
        // New empty list of cardinality constraints
        this.cardinalityConstraints = new HashSet<CardinalityConstraint>();
        // The Path to reach this new bindingVar
        this.path = shape.getPath();

        // Its the task of the Validation to fill the correct constraints and children
        }

    

    @Override
    public String getBindingVar() {
        return bindingVar;
    }

    @Override
    public String getReportString() {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public boolean validates(Node atom) {
        // Since a propertyshape is basically an AND-Node, it validates, iff all its children validate
        for (var childNode:_children) {
            if (!childNode.validates(atom)) return false;
        }
        return true;
    }

    @Override
    public boolean validatesRes(Node atom, Set<SHACLNode> valNodes) {
        // TODO Auto-generated method stub

        for (var b:bindings) {
            
        }

        if (cardinalityConstraints.size() != 0) {
            
        }
        return false;
    }
    
    public Path getPath() {
        return path;
    }

    public void addCardinalityConstraint(CardinalityConstraint c){
        this.cardinalityConstraints.add(c);
    }
    

}
