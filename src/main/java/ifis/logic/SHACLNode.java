package ifis.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Constraint;
import org.apache.jena.shacl.parser.Shape;

public abstract class SHACLNode {

    /* 
     * every subtree under at least one QualifiedCountComponent cannot be evaluated correctly using
     * just COUNTS and has to work with unaggregated queries. 
     * Whenever a shape contains a QualifiedCount, the mode is explicitly set to NODES
     * After tree construciton, Nodes can query their mode with getMode(), which will traverse the tree upward until an
     * explicit MODE Specifier (COUTNS or NODES) is found.
     */
    public enum Mode {
        COUNTS,
        NODES,
        INHERIT
    }
    private Mode mode = Mode.INHERIT;
    
    // Holds the valid focus nodes per ShaclNode
    protected Set<List<Node>> validFocus = new HashSet<List<Node>>();
    protected HashMap<List<Node>, Integer> countMap; // Only applies if in Counts Mode
    protected HashMap<List<Node>, List<Node>> nodeMap; // Only applies if in NODES mode


    public void setNodeMap(HashMap<List<Node>, List<Node>> nodeMap) {
        this.nodeMap = nodeMap;
    }

    // The tree structure is stored in the _children List
    protected final ArrayList<SHACLNode> _children;
    protected final Shape shape;
    
    protected Set<Node> validTargets = null;

    protected SHACLNode parent;
    // List of all parents up to the root, starts with the direct parent
    // Gets filled in if a query is ever generated for this SHACLNode
    protected List<SHACLNode> lineage;

    private boolean populated;

    private List<Node> targetNodes;
    


    public List<Node> getTargetNodes() {
        return targetNodes;
    }


    public void setTargetNodes(List<Node> targetNodes) {
        this.targetNodes = targetNodes;
    }


    /**
     * Return the mode of this node by inheritance or its explicit value.
     * @return this node's mode
     */
    public Mode getMode() {
        return switch (mode) {
            case INHERIT:
                yield parent != null ? (parent.getMode()) : Mode.COUNTS;
            default:
                yield mode;
        };
    }


    public void setMode(Mode mode) {
        this.mode = mode;
    }


    public SHACLNode(Shape shape) {
        this.shape = shape;

        _children = new ArrayList<>();
    }

    
    // Construct all children, then construct yourself
    // Calling this on the root node constructs the whole tree
    public void construct() {
        // First of all construct all children
        for (var child:_children) {
            child.construct();
        }

        // Then construct this node from their bindings
        constructFromChildren();

    }



    public List<SHACLNode> getLineage() {
        if (lineage == null) {
            /* 
            * GENERATE LINEAGE
            */
            lineage = new ArrayList<SHACLNode>();
            SHACLNode lookAt = this;
            while (lookAt.getParent() != null) {
                lineage.add(lookAt.getParent());
                lookAt = lookAt.getParent();
            }
        }

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
        if (parent == null) return "targets";
        return parent.getBindingVar();
    }


    /**
     * @return the next higher PShape in the tree
     */
    public PShapeNode getPShape() {
        if (parent == null) return null;
        return parent.getPShape();
    }

    
    /**
     * Adds a new child to this node's _children
     * Sets the parent of the child to this node
     * Bequeaths this node's MODE to the children -> set correct mode before adding children
     * @param newBorn
     */
    public void addChild(SHACLNode newBorn)  {
        _children.add(newBorn);
        newBorn.parent = this;
    }


    
    // Faster, no meta information
    public abstract boolean validates(Node atom);
    
    /**
        A bit slower, meta information for Report
     */
    public boolean validatesRes(Node atom, Set<SHACLNode> valNodes) {
        
        // If valid Targets arent extracted from validBindings, do it now and memoize
        if (validTargets == null) extractValidTargets();
        
        // If this Node contains, add it to valNodes
        if (validTargets.contains(atom)) valNodes.add(this);
        
        // Check the children
        for (var child:_children) child.validatesRes(atom, valNodes);
        
        return validTargets.contains(atom);
    }

    public ArrayList<SHACLNode> getChildren() {
        return _children;
    }

    public Shape getShape() {
        return shape;
    }

    public abstract String getReportString();


    public Set<List<Node>> getValidFocus() {
        return validFocus;
    }

    public void setValidFocus(Set<List<Node>> validBindings) {
        this.validFocus = validBindings;
    }

    public void setParent(SHACLNode parent) {
        this.parent = parent;
    }


    public HashMap<List<Node>, Integer> getCountMap() {
        return countMap;
    }

    public void setCountMap(HashMap<List<Node>, Integer> countMap) {
        this.countMap = countMap;
    }

    // Construct the set of valid focus nodes for this node
    // Called from construct() after all children are constructed
    protected abstract void constructFromChildren();

    protected void inheritConstraints(Set<Constraint> constraints){
        constraints.addAll(constraints);
        _children.forEach((child) -> child.inheritConstraints(constraints));
    }

    // 
    /** Just takes the first variable from each focus node list (?targets) */
    protected void extractValidTargets(){
        this.validTargets = validFocus.stream().map(b->b.get(0)).collect(Collectors.toSet());
    }

    public boolean isPopulated() {
        return populated;
    }
    
    public void setPopulated(boolean populated) {
        this.populated = populated;
    }



    
    


}
