package ifis.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.engine.constraint.ConstraintOpN;
import org.apache.jena.shacl.engine.constraint.ShNode;
import org.apache.jena.shacl.parser.Constraint;
import org.apache.jena.shacl.parser.PropertyShape;
import org.apache.jena.shacl.parser.Shape;
import org.apache.jena.sparql.path.Path;

public abstract class LogicNode {

    protected final ArrayList<LogicNode> children;
    protected final Shape shape;
    // In order to construct normalized PropertyNodes we have to pass on constraints from parent shapes.
    protected final HashSet<Constraint> normConstraints;
    // Same with the Property Paths
    protected Path normPath;


    public LogicNode(Shape shape) {
        this.shape = shape;

        children = new ArrayList<>();

        normConstraints = new HashSet<>();
        
        
        // Add all non-junctive (e.g local) constraint-components
        // These get inherited by all childcomponents, therby creating the normalization.
        normConstraints.addAll(
            shape.getConstraints()
                .stream()
                .filter((constraint) -> !(constraint instanceof ConstraintOpN) || constraint instanceof ShNode)
                .collect(Collectors.toSet())
            );

        // TODO Look into path behaviour when nesting shapes
        // Add Path Varaiable
        if (shape instanceof PropertyShape) {
            normPath = ((PropertyShape) shape).getPath();
        }

    }

    public void addChild(LogicNode newBorn)  {
        children.add(newBorn);
        // Propagate constraints downwards
        if (normPath != null) {
            
            newBorn.inherit(this.normConstraints, this.normPath);
        }
    }
    
    protected void inherit(Set<Constraint> constraints, Path path){
        normConstraints.addAll(constraints);
        this.normPath = path;
        children.forEach((child) -> child.inherit(constraints, path));
    }

    // Faster, no meta information
    public abstract boolean validates(Node atom);

    // A bit slower, meta information for Report
    public abstract boolean validatesRes(Node atom, Set<LogicNode> valNodes);

    public ArrayList<LogicNode> getChildren() {
        return children;
    }

    public Shape getShape() {
        return shape;
    }

    public HashSet<Constraint> getNormConstraints() {
        return normConstraints;
    }

    public Path getNormPath() {
        return normPath;
    }

    public abstract String getReportString();


    
    


}
