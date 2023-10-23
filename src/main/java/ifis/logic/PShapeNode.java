package ifis.logic;

import java.util.HashSet;
import java.util.stream.Collectors;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Constraint;
import org.apache.jena.shacl.parser.PropertyShape;
import org.apache.jena.sparql.path.Path;

/* A PathNode is esentially a PropertyShape. It represents all the Valuenodes of a PShape and how to reach them */
public class PShapeNode extends SHACLNode {

    private String bindingVar;        
    private Path path;
    public String classc = null;
    public boolean elevate = true;
    private HashSet<Constraint> engineConstraints = new HashSet<>();
    

    public PShapeNode(PropertyShape shape, String bindingVar) {
        
        super(shape);

        // Whenever a new PropertyShape is parsed, a new Variable will be generated
        this.bindingVar = bindingVar;    
        
        // The Path to reach this new bindingVar
        
        this.path = shape.getPath();

        // Its the task of the Validation to fill the correct constraints and children
        }

    public PShapeNode(Path p, String bindingVar) {
        super(null);

        // Whenever a new PropertyShape is parsed, a new Variable will be generated
        this.bindingVar = bindingVar;    
        
        // New empty list of cardinality constraints
        // The Path to reach this new bindingVar
        
        this.path = p;
    }

    

    @Override
    protected void constructFromChildren() {

        

        if (_children.get(0).validBindings.size() == 0) return;
        // If this is the end highest propertyshape, were finished

        var childNumVars = _children.get(0).validBindings.iterator().next().size();

        elevate = childNumVars != 1;



        
        // We use an AndNode to do this

        var sub = new AndNode(shape);
        for (var child:_children) {
            sub.addChild(child);
        }
        sub.constructFromChildren();

        

        validBindings = elevate ? sub.validBindings
            .stream()
            .map(b->b.subList(0, childNumVars-1))
            .collect(Collectors.toSet()) : sub.validBindings;

    }

    @Override
    public boolean validates(Node atom) {
        // Since a propertyshape is basically an AND-Node, it validates, iff all its children validate
        for (var childNode:_children) {
            if (!childNode.validates(atom)) return false;
        }
        return true;
    }

    /* 
     * GETTERS & SETTERS
     */


    public Path getPath() {
        return path;
    }

    
    @Override
    public String getBindingVar() {
        return bindingVar;
    }

    @Override
    public String getReportString() {
        
        if (path instanceof StringPath) return " " +  (((StringPath)path).value.replaceAll("urn:absolute/prototyp#", "")) + " ?"+bindingVar; 
        return " " +  ((PropertyShape) shape).getPath().toString().replaceAll("urn:absolute/prototyp#", "") + " ?"+bindingVar;
    }

    public HashSet<Constraint> getEngineConstraints() {
        return engineConstraints;
    }

    public void addEngineConstraint(Constraint c) {
        engineConstraints.add(c);
    }

    

}
