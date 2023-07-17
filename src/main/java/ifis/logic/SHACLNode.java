package ifis.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Constraint;
import org.apache.jena.shacl.parser.Shape;

public abstract class SHACLNode {

    protected final ArrayList<SHACLNode> _children;


    protected Set<Node> validAtoms;

    public HashMap<List<Node>, Set<Node>> bindingMap;
 

    protected SHACLNode parent;

    // List of all parents up to the root, starts with the direct parent
    // Gets filled in if a query is ever generated for this SHACLNode
    protected List<SHACLNode> lineage;

    protected final Shape shape;
    private boolean populated;


    protected HashMap<ArrayList<Node>, Set<Node>> proliferate(){
        
        var map = new HashMap<List<Node>, Set<Node>>();
        
        var varHir = genVarHirarchy( );

        for (var b:bindingsListed) {
            
            // [a,b,c,d] => [a, b, c] -> d
            
            var sublist = b.subList(0, varHir.size()-1);
            var l = map.get(sublist);

            if (l == null) {
                l = new HashSet<Node>();
                map.put(sublist, l);
            }
            
            l.add(b.get(varHir.size()-1));
        }

        node.bindingMap = map;

    }
    
    
    public boolean isPopulated() {
        return populated;
    }
    
    public void setPopulated(boolean populated) {
        this.populated = populated;
    }

    public List<SHACLNode> getLineage() {
        return lineage;
    }

    public void setLineage(List<SHACLNode> lineage) {
        this.lineage = lineage;
    }

    
    // In order to construct normalized PropertyNodes we have to pass on constraints from parent shapes.
    
    public SHACLNode getParent() {
        return parent;
    }



    public String getBindingVar() {
        // Walk up the tree until you find a bindingvar in a PShapeNode
        return parent.getBindingVar();
    }

    public SHACLNode(Shape shape) {
        this.shape = shape;

        _children = new ArrayList<>();
    }

    public void addChild(SHACLNode newBorn)  {
        _children.add(newBorn);
        newBorn.parent = this;
        // Propagate constraints downwards
        
    }
    
    protected void inheritConstraints(Set<Constraint> constraints){
        constraints.addAll(constraints);
        _children.forEach((child) -> child.inheritConstraints(constraints));
    }

    // Faster, no meta information
    public abstract boolean validates(Node atom);

    // A bit slower, meta information for Report
    public abstract boolean validatesRes(Node atom, Set<SHACLNode> valNodes);

    public ArrayList<SHACLNode> get_children() {
        return _children;
    }

    public Shape getShape() {
        return shape;
    }


    public abstract String getReportString();


    
    


}
