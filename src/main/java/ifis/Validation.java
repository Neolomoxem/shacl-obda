package ifis;


import static java.lang.StringTemplate.STR;
import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.isDetected;
import static org.fusesource.jansi.Ansi.setDetector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.ext.com.google.common.collect.Multimap;
import org.apache.jena.graph.Node;
import org.apache.jena.shacl.engine.Parameter;
import org.apache.jena.shacl.engine.constraint.ClassConstraint;
import org.apache.jena.shacl.engine.constraint.ConstraintComponentSPARQL;
import org.apache.jena.shacl.engine.constraint.ConstraintOp;
import org.apache.jena.shacl.engine.constraint.ConstraintOpN;
import org.apache.jena.shacl.engine.constraint.DatatypeConstraint;
import org.apache.jena.shacl.engine.constraint.HasValueConstraint;
import org.apache.jena.shacl.engine.constraint.InConstraint;
import org.apache.jena.shacl.engine.constraint.MaxCount;
import org.apache.jena.shacl.engine.constraint.MinCount;
import org.apache.jena.shacl.engine.constraint.PatternConstraint;
import org.apache.jena.shacl.engine.constraint.QualifiedValueShape;
import org.apache.jena.shacl.engine.constraint.ShAnd;
import org.apache.jena.shacl.engine.constraint.ShNode;
import org.apache.jena.shacl.engine.constraint.ShNot;
import org.apache.jena.shacl.engine.constraint.ShOr;
import org.apache.jena.shacl.engine.constraint.ShXone;
import org.apache.jena.shacl.engine.constraint.SparqlComponent;
import org.apache.jena.shacl.engine.constraint.StrMaxLengthConstraint;
import org.apache.jena.shacl.engine.constraint.StrMinLengthConstraint;
import org.apache.jena.shacl.engine.constraint.ValueMaxExclusiveConstraint;
import org.apache.jena.shacl.engine.constraint.ValueMaxInclusiveConstraint;
import org.apache.jena.shacl.engine.constraint.ValueMinExclusiveConstraint;
import org.apache.jena.shacl.engine.constraint.ValueMinInclusiveConstraint;
import org.apache.jena.shacl.parser.Constraint;
import org.apache.jena.shacl.parser.NodeShape;
import org.apache.jena.shacl.parser.PropertyShape;
import org.apache.jena.shacl.parser.Shape;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.sparql.function.library.print;
import org.apache.jena.sparql.path.P_Alt;
import org.apache.jena.sparql.path.P_Inverse;
import org.apache.jena.sparql.path.P_Link;
import org.apache.jena.sparql.path.P_Seq;
import org.apache.jena.sparql.path.Path;

import ifis.SPARQLGenerator.Query;
import ifis.exception.InternalValidationException;
import ifis.exception.ValidationException;
import ifis.logic.AndNode;
import ifis.logic.ConstraintNode;
import ifis.logic.NotNode;
import ifis.logic.OrNode;
import ifis.logic.PShapeNode;
import ifis.logic.QualifiedNode;
import ifis.logic.SHACLNode;
import ifis.logic.StringPath;
import ifis.logic.XoneNode;
import ifis.logic.SHACLNode.Mode;

public class Validation {
    private final Shape shape;                      // Input
    private final List<String> report;              // Output in String
    private final SPARQLGenerator sparqlGenerator;  // Takes care of Variables
    private final QueryExecHTTPBuilder endpoint;    // The SPARQL is queried against this
    private SHACLNode tree;                         // From the input we generate a tree of SHACLNodes
    private Set<ValidationResult> results;
    private boolean isEvaluated = false;



    private int indentlevel = 0;                    // Indentation Level used for prettier printing

    public boolean isEvaluated() {
        return isEvaluated;
    }

    public void setEvaluated(boolean isEvaluated) {
        this.isEvaluated = isEvaluated;
    }

    /**
     * @param shapes
     */
    public Validation(Shape shape, QueryExecHTTPBuilder endpoint) {

        this.shape = shape;
        this.report = new ArrayList<String>();
        this.endpoint = endpoint;

        sparqlGenerator = new SPARQLGenerator();

    }

    /*
     * CONTROL FLOW
     */

    /**
     * Entrypoint for the validation process.
     * Executes the validation by accessing the specified SPARQL Endpoint.
     */
    public List<String> exec() {

        // Validate Rootshapes
        validateShape((NodeShape) this.shape);

        // shapes.forEach((shape) -> validateShape((NodeShape) shape));

        // Return Report
        return report;
    }

    /**
     * Validates a specific shape by constructing the logic tree and populating the
     * property nodes.
     *
     * @param shape The shape to validate.
     */
    public void validateShape(NodeShape shape) {

        /*
        * CONSTRUCT LOGIC TREE
        */

        print("Building logic tree.");
        indentlevel++;
        buildTree();
        indentlevel--;
        print("Done.");
        printTree(tree);
    
        /*
        * POPULATE
        */
        print("Populating tree.");
        indentlevel++;
        populateTree(tree);
        indentlevel--;
        print("Nodes populated.");

        /*
        * CONSTRUCT BINDINGS IN TREE
        */

        print("Constructing valid bindings in tree.");
        indentlevel++;
        tree.construct();
        indentlevel--;
        print("Finished constructing.");

        print("Finished eval. Writing Report (this may take some time)");
        results = validate();


    }

    private void printTree(SHACLNode node){
        print(node.getReportString());
        indentlevel++;
        for (var child:node.getChildren()) printTree(child);
        indentlevel--;
    }

    /*
     * TREE BUILDING
     */

    private void buildTree() {
        // Root node



        if (shape.getConstraints().size() + shape.getPropertyShapes().size() > 1) {
            var andnode = new AndNode(shape);
            var cnode = new ConstraintNode(shape);
            andnode.addChild(cnode);
            for (var child:shape.getPropertyShapes()) {
                andnode.addChild(shapeToTree(child));
            }
            tree = andnode;

        } else {
            tree = shapeToTree(shape);
        }

        // Right now, this seems to be the most elegant way of doing this.
        insertEmptyConstraintNodes(tree);
    }

    private void insertEmptyConstraintNodes(SHACLNode node) {
        var hasNoChildren = node.getChildren().size() == 0;
        var isConstraintNode = node instanceof ConstraintNode;
        
        if (hasNoChildren && !isConstraintNode) node.addChild(new ConstraintNode(null));

        for (var child : node.getChildren()) insertEmptyConstraintNodes(child);
    }

    private SHACLNode shapeToTree(Shape shape) {

        return switch (shape) {

            case PropertyShape pshape   -> pShapeToTree(pshape);
            case NodeShape nshape       -> nodeShapeToTree(nshape);

            default -> throw new ValidationException(
                    "There is a new kind of shape. If you see this error you are most likely from the future. Please note that this tool was written in 2023, back when we only knew NodeShapes and PropertyShapes! Can you imagine?");

        };
    }

    private PShapeNode pShapeToTree(PropertyShape pshape) {
        var pnode = new PShapeNode(pshape, sparqlGenerator.getNewVariable());
        var cnode = new ConstraintNode(pshape);

        boolean hasConstraints = false;
        for (var c : pshape.getConstraints()) {
            switch (c) {
                /*
                    * Add junctive Constraints as children
                    */
                case ConstraintOp opcomp -> {
                    pnode.addChild(createOpNode(opcomp, pshape));
                    hasConstraints = true;
                }

                /*
                    * ENGINE CONSTRAINTS
                    */
                case ClassConstraint cc -> {
                    pnode.addEngineConstraint(cc);
                    hasConstraints = true;
                }

                case QualifiedValueShape qs -> {
                
                    // Write cardinality to new QualifiedNode
                    QualifiedNode qnode = new QualifiedNode(pshape, qs.qMin(), qs.qMax());
                    
                    qnode.setMode(Mode.NODES);


                    // Handle the subshape
                    qnode.addChild(shapeToTree(qs.getSub()));
                    
                    // Add as child to the node
                    pnode.addChild(qnode);

                    
                }

                /* case ConstraintComponentSPARQL custom -> {
                    var eqnode = new EqualNode(shape);
                    getParams(custom, eqnode);

                    var c1 = new ConstraintNode(shape);
                    var p1 = new PShapeNode(new StringPath("<urn:absolute/prototyp#hat_Parameter>"), sparqlGenerator.getNewVariable());
                    var p11 = new PShapeNode(new StringPath("<urn:absolute/prototyp#hat_Wert>"), sparqlGenerator.getNewVariable());

                    p1.classc = Util.wrap(eqnode.param1.getURI());
                    c1.retain = true;


                    p1.addChild(p11);
                    p11.addChild(c1);
                    eqnode.addChild(p1);

                    var c2 = new ConstraintNode(shape);
                    var p2 = new PShapeNode(new StringPath("<urn:absolute/prototyp#hat_Parameter>"), sparqlGenerator.getNewVariable());
                    var p22 = new PShapeNode(new StringPath("<urn:absolute/prototyp#hat_Wert>"), sparqlGenerator.getNewVariable());


                    p2.classc = Util.wrap(eqnode.param2.getURI());
                    c2.retain = true;


                    p2.addChild(p22);
                    p22.addChild(c2);
                    eqnode.addChild(p2);

                    pnode.addChild(eqnode);
                } */
                
                /*
                * Add direct constraints to a child ConstraintNode
                */

                
                
                default -> {
                    cnode.addConstraint(c);
                    hasConstraints = true;
                }
            }
        }

        // If there were no direct constraints, we dont have to add the constraintnode
        if (hasConstraints) {
            pnode.addChild(cnode);
        }

        for (var subShape : pshape.getPropertyShapes()) {
            // For all following propertyshapes
            // add them as children to the current PropertyNode
            pnode.addChild(shapeToTree(subShape));
        }

        return pnode;
    }

    private SHACLNode nodeShapeToTree(NodeShape nshape) {
        var cnode = new ConstraintNode(nshape);
        if (nshape.getPropertyShapes().size() > 0 || nshape.getConstraints().stream().filter(c->c instanceof ConstraintOp).count() > 0 || nshape.getConstraints().stream().filter(c->c instanceof ConstraintComponentSPARQL).count() > 0) {
            var andnode = new AndNode(nshape);
            for (var comp : nshape.getConstraints()) {
                switch (comp) {
                    /*
                    * Add junctive Constraints as children
                    */
                    case ConstraintOp opcomp -> {
                        andnode.addChild(createOpNode(opcomp, nshape));
                    }

                    /*
                    * Add direct constraints to a child ConstraintNode
                    */
                    /* case ConstraintComponentSPARQL custom -> {
                        var eqnode = new EqualNode(shape);
                    getParams(custom, eqnode);

                    var c1 = new ConstraintNode(shape);
                    var p1 = new PShapeNode(new StringPath("<urn:absolute/prototyp#hat_Parameter>"), sparqlGenerator.getNewVariable());
                    var p11 = new PShapeNode(new StringPath("<urn:absolute/prototyp#hat_Wert>"), sparqlGenerator.getNewVariable());

                    p1.classc = Util.wrap(eqnode.param1.getURI());
                    c1.retain = true;


                    p1.addChild(p11);
                    p11.addChild(c1);
                    eqnode.addChild(p1);

                    var c2 = new ConstraintNode(shape);
                    var p2 = new PShapeNode(new StringPath("<urn:absolute/prototyp#hat_Parameter>"), sparqlGenerator.getNewVariable());
                    var p22 = new PShapeNode(new StringPath("<urn:absolute/prototyp#hat_Wert>"), sparqlGenerator.getNewVariable());


                    p2.classc = Util.wrap(eqnode.param2.getURI());
                    c2.retain = true;


                    p2.addChild(p22);
                    p22.addChild(c2);
                    eqnode.addChild(p2);


                    cnode.addChild(eqnode); 
                    
                    }
                    */

                    default -> {
                        cnode.addConstraint(comp);
                    }
                }
            }
            if (cnode.getChildren().size() != 0) andnode.addChild(cnode);

            for (var pshape : shape.getPropertyShapes()) {
                var child = shapeToTree(pshape);
                andnode.addChild(child);
            }

            if (andnode.getChildren().size() == 1) return andnode.getChildren().get(0);

            return andnode;

        } else {
            for (var comp:nshape.getConstraints()) {
                cnode.addConstraint(comp);
            }
            return cnode;
        }


    }

    private SHACLNode createOpNode(ConstraintOp c, Shape shape) {

        SHACLNode node = null;

        switch (c) {
            case ShNot not -> {
                node = new NotNode(shape);
                node.addChild(shapeToTree(not.getOther()));
                return node;
            }
            case ShOr x -> node = new OrNode(shape);
            case ShAnd x -> node = new AndNode(shape);
            case ShXone x -> node = new XoneNode(shape);
            case ShNode x -> {
                return node;
            }
            default -> throw new ValidationException(c.getClass().getSimpleName() + " not supported yet.");
        }

        return addChildrenToNode(node, (ConstraintOpN) c, shape);

    }


    /*
     * NODE POPULATION
     */

    /* We populate the leaves of the tree. */
    /* Recursive */
    private void populateTree(SHACLNode node) {
        var hasNoChildren = node.getChildren().size() == 0;

        
        switch (node) {
            case PShapeNode pnode:
                populatePShape(pnode);
                break;
                
                case ConstraintNode cnode:
                populateLeaf(cnode);
                break;
                
                case NotNode nnode:
                break;
                
                default: break;
            }     
            
            
        //  ANCHOR
        if (hasNoChildren) {
            // populateLeaf(node);  
            return;
        }
        // Recurse for all child nodes.
        for (var childNode:node.getChildren()) {
            populateTree(childNode);
        }
    }

    /*
     * QUERY GENERATION
     */

    private void populateLeaf(ConstraintNode node) {

        var mode = node.getMode();

        print("VALID QUERY");
        if (mode == Mode.COUNTS) {    
            // A query counting the number of valid nodes per focus node gets written
            var query       = generateValidQueryCOUNTS(node);
            
            // The query gets executed via SPARQL, resulting in bindings
            var bindings    = executeQuery(query);
            
            // The Bindings repr gets converted into a HashMap<List<Nodes>, Integer>>
            var bench       = Util.startBenchmark("Transforming bindings to map");
            var countMap    = Util.getCountMap(node, bindings);
            print(bench.stop());

            // Writeback countMap into ConstraintNode for later use
            node.setCountMap(countMap);
        
        } else {
            // A query counting the number of valid nodes per focus node gets written
            var query       = generateValidQueryNODES(node);
            
            // The query gets executed via SPARQL, resulting in bindings
            var bindings    = executeQuery(query);
            
            // The Bindings repr gets converted into a HashMap<List<Nodes>, List<Node>>> (Map<Focus, List<ValidValues>>)
            var bench       = Util.startBenchmark("Transforming bindings to Node Map");
            var nodeMap    = Util.getNodesMap(node, bindings);
            print(bench.stop());

            // Writeback countMap into ConstraintNode for later use
            node.setNodeMap(nodeMap);
        
        }
        
    }

    private Query generateValidQueryCOUNTS(ConstraintNode node) {
        
        // New empty Query
        var query = sparqlGenerator.newQuery();
        
        // Write the path of vars from ?targets to this leaf node (focus)
        // with the value node being wrapped in OPTIONAL
        addQueryPath(node, query, false);
        
        // The logic validating constraint components gets added (FILTER statements)
        for (var c:node.getConstraints()) addSPARQLForConstraint(c, node, query);

        // Create a String of all path Vars except the last one
        var focusProjection = query.getFocusProjection();

        // Set projection with COUNT in value var
        query.setProjection(
            STR."\{focusProjection} (COUNT(?\{query.getInmostVar()}) AS ?count)"
            );
        
        // Add GROUP BY focusprojection
        query.addOuterPart(
            STR."GROUP BY \{focusProjection}"
            );

        return query;
    }
    
    private Query generateValidQueryNODES(ConstraintNode node) {
        
        // New empty Query
        var query = sparqlGenerator.newQuery();
        
        // Write the path of vars from ?targets to this leaf node (focus)
        // with the value node being wrapped in OPTIONAL
        addQueryPath(node, query, false);
        
        // The logic validating constraint components gets added (FILTER statements)
        for (var c:node.getConstraints()) addSPARQLForConstraint(c, node, query);

        // Create a String of all path Vars except the last one
        var focusProjection = query.getFocusProjection();

        // Set projection with COUNT in value var
        query.setProjection(
            STR."\{focusProjection} ?\{query.getInmostVar()}"
            );
        
        // Add GROUP BY focusprojection
        query.addOuterPart(
            STR."GROUP BY \{focusProjection}"
            );

        return query;
    }


    private void populatePShape(PShapeNode node) {

        var query    =     generateCountQuery(node);
        print("PROPERTY QUERY");
        var bindings =     executeQuery(query);
        var countMap =     Util.getCountMap(node, bindings);
        
        node.setCountMap(countMap);
        
    }

    private Query generateCountQuery(PShapeNode node) {
        
        // New empty query
        var query = sparqlGenerator.newQuery();
        
        // Write the path of vars from ?targets to this leaf node (focus)
        // with the value node being wrapped in OPTIONAL
        addQueryPath(node, query, true);
        
        var focusProjection = query.getFocusProjection();


        query.setProjection(STR."\{focusProjection} (COUNT(?\{query.getInmostVar()}) AS ?count)");
        query.addOuterPart(STR."GROUP BY \{focusProjection}");

        return query;   


    }

    private void addQueryPath(SHACLNode node, Query query, boolean useOptional) {
        var lineage = node.getLineage();

        for (var target:this.shape.getTargets()) {
            query.addPart(Util.generateTargetString(target, "targets"));
        }

        
        lineage.add(0, node);
        
        var parentPShape = node.getPShape();

        var from = "targets";
        query.pushPathVar(from);
        for (int ancestorIndex = lineage.size()-1; ancestorIndex >= 0; ancestorIndex-- ) {
            var ancestor = lineage.get(ancestorIndex);

            if (ancestor == parentPShape) {
                var pnode = (PShapeNode) ancestor;
                // Add path from previous to new value nodes
                if (useOptional) {
                    query.addOptionalPart(Util.asTriple("?"+from, generatePath(pnode.getPath()),"?"+pnode.getBindingVar()));
                } else {
                    query.addTriple("?"+from, generatePath(pnode.getPath()),"?"+pnode.getBindingVar());

                }
                from = pnode.getBindingVar();
                query.pushPathVar(from);
                

                // Engineconstraints
                for (var engineconstraint : pnode.getEngineConstraints()) {
                    if (useOptional) {
                        query.addOptionalPart(getSPARQLForEngineConstraint(engineconstraint, pnode, query));
                    } else {
                        query.addPart(getSPARQLForEngineConstraint(engineconstraint, pnode, query));
                    }
                        
                }

                continue;
            }

            if (ancestor instanceof PShapeNode) {
                var pnode = (PShapeNode) ancestor;
                // Add path from previous to new value nodes
                query.addTriple("?"+from, generatePath(pnode.getPath()),"?"+pnode.getBindingVar());
                from = pnode.getBindingVar();
                query.pushPathVar(from);

                // Engineconstraints
                for (var engineconstraint : pnode.getEngineConstraints()) {
                    query.addPart(getSPARQLForEngineConstraint(engineconstraint, pnode, query));
                }
            }
        }
        query.setInmostVar(from);
        
    }

    /**
     * Generates a SPARQL query string for a property node.
     *
     * @param node The property node for which to generate the query.
     * @return The generated SPARQL query string.
     */
    
    private Query generateQuery(SHACLNode node) {

        var lineage = node.getLineage();


        // Init new empty Query
        var query = sparqlGenerator.newQuery();

        // For the target-definition
        // TODO this doesnt actually work for more than one Targetdef.
        for (var target:this.shape.getTargets()) {
            query.addPart(Util.generateTargetString(target, "targets"));
        }


        // For the rest
        var from = "targets";

        lineage.add(0, node);

        for (int ancestorIndex = lineage.size()-1; ancestorIndex >= 0; ancestorIndex-- ) {
            var ancestor = lineage.get(ancestorIndex);

            switch (ancestor) {
                case PShapeNode pnode -> {
                    // Add path from previous to new value nodes
                    query.addTriple("?"+from, generatePath(pnode.getPath()),"?"+pnode.getBindingVar());
                    from = pnode.getBindingVar();

                    if (pnode.classc != null) query.addTriple("?"+pnode.getBindingVar(), "a", pnode.classc);

                    // Engineconstraints
                    for (var engineconstraint : pnode.getEngineConstraints()) {
                        getSPARQLForEngineConstraint(engineconstraint, pnode, query);
                    }
                }
                case ConstraintNode cnode -> {

                    /* Add all the constraint logic */
                    for (var c:cnode.getConstraints()) {
                        addSPARQLForConstraint(c, cnode, query);
                    }


                }
                default -> {
                    //Ignore all LogicNodes like AND, OR, NOT, XONE
                }
            }
        }

        // Now the SPARQL-Query and the BINDING-Filters should be all set up.
        return query;
    }

    /**
     * Recursively resolves the path and returns a string that can be used in a
     * query to fetch all value nodes described by it
     *
     * @return
     */
    private String generatePath(Path path) {

        return switch (path) {

            case StringPath stringPath -> {
                yield stringPath.value;
            }


            /*
             * DIRECT PATH
             */
            case P_Link linkPath -> {
                yield Util.wrap(linkPath.getNode().getURI());
            }

            /*
             * ALTERNATIVE PATH
             */
            case P_Alt altPath -> {
                yield "(" + generatePath(altPath.getLeft()) + ")|(" + generatePath(altPath.getRight()) + ")";
            }

            /*
             * INVERSE PATH (PARENT)
             */
            case P_Inverse inversePath -> {
                // Just switch toVar and fromVar
                yield "^("+generatePath(inversePath.getSubPath())+")";
            }

            /*
             * SEQUENCE PATH
             */
            case P_Seq seqPath -> {
                // A pure seqPath is (counter-intuitively) structured like this:
                // (((A, B), C), D) --> .getRight() is a P_Link and .getLeft() a P_Seq
                yield "("+generatePath(seqPath.getLeft()) + ")/(" + generatePath(seqPath.getRight())+")";
            }

            default -> {
                // Shouldnt happen. Naturally.
                throw new InternalValidationException("Weird paths are happening");
            }
        };

    }


    private String getSPARQLForEngineConstraint(Constraint constraint, PShapeNode node , Query subQuery) {
        var bindingVar = node.getBindingVar();
        return switch (constraint) {
            case ClassConstraint classConstraint -> {
                    yield Util.asTriple("?" + bindingVar, "a", Util.wrap(classConstraint.getExpectedClass().getURI()));
                }
            default -> {
                throw new InternalValidationException("EngineConstraint not supported!");
            }

        };
    }

    private void addSPARQLForConstraint(Constraint c, ConstraintNode cnode, Query subQuery) {
        var bindingVar = cnode.getBindingVar();
        switch (c) {
            /*
                * MAIN CONSTRAINTS
                */

            case ClassConstraint classConstraint -> {
                subQuery.addTriple("?" + bindingVar, "a", Util.wrap(classConstraint.getExpectedClass().getURI()));
            }
            /*
                * STRING BASED CONSTRAINTS
                */
            case StrMinLengthConstraint strMinLengthConstraint -> {
                var minLen = strMinLengthConstraint.getMinLength();
                subQuery.addFilter("STRLEN(?"+bindingVar+") <= "+minLen);

            }
            case StrMaxLengthConstraint strMaxLengthConstraint -> {
                var maxLen = strMaxLengthConstraint.getMaxLength();
                subQuery.addFilter("STRLEN(?"+bindingVar+") <= "+maxLen);
            }
            case PatternConstraint patternConstraint -> {
                subQuery.addFilter("REGEX(?"+bindingVar+", \""+patternConstraint.getPattern()+"\")");
            }
            /*
                * VALUE RANGE CONSTRAINTS
                */

            case ValueMinExclusiveConstraint minExC -> {
                var minVal = minExC.getNodeValue().getFloat();
                subQuery.addFilter("?"+bindingVar+" > " + minVal);

            }
            case ValueMinInclusiveConstraint minInC -> {
                var minVal = minInC.getNodeValue().getFloat();
                subQuery.addFilter("?"+bindingVar+" >= " + minVal);
            }
            case ValueMaxExclusiveConstraint maxExC -> {
                var maxVal = maxExC.getNodeValue().getFloat();
                subQuery.addFilter("?"+bindingVar+" < " + maxVal);
            }
            case ValueMaxInclusiveConstraint maxInC -> {
                var maxVal = maxInC.getNodeValue().getFloat();
                subQuery.addFilter("?"+bindingVar+" <= " + maxVal);
            }

            /*
                * VALUE CONSTRAINTS
                */

            case HasValueConstraint hasValueConstraint -> {
                var expectedVal = hasValueConstraint.getValue();
                cnode.addBindingFilter((s) -> s.filter((b) -> b.get(bindingVar).equals(expectedVal)));
            }
            case InConstraint inConstraint -> {
                var list = inConstraint.getValues();
                cnode.addBindingFilter((s) -> s.filter((b) -> list.contains(b.get(bindingVar))));
            }

            case DatatypeConstraint datatypeConstraint -> {
                var datatype = datatypeConstraint.getDatatypeURI();
                subQuery.addFilter("DATATYPE(?"+bindingVar+") = "+Util.wrap(datatype));
            }

            /*
                * CUSTOM CONSTRAINTS
                */
            case ConstraintComponentSPARQL custom -> {}

            /*
                * CARDINALITY, NEGATION
                */
            case MinCount min -> {
                // Do nothing
            }
            case MaxCount maxCount -> {
                // Do nothing
            }
            case ShNot shNot -> {
                // DO nothing
            }
            case ShNode shNode -> {

                // Setup and link subvalidation in propertyNode

                // Add bindingfilter for subVal Results
            }

            default -> {
                throw new ValidationException("Unsupported Constraint: " + c.toString());
            }
        }
    }

    private void getParams(ConstraintComponentSPARQL c, SHACLNode node) {
        SparqlComponent customComponent;
        Multimap<Parameter, Node> parameterMap;
        try {

            var f1 = c.getClass().getDeclaredField("sparqlConstraintComponent");
            f1.setAccessible(true);
            customComponent = (SparqlComponent) f1.get(c);

            var f2 = c.getClass().getDeclaredField("parameterMap");
            f2.setAccessible(true);
            parameterMap = (Multimap<Parameter, Node>) f2.get(c);





            Node paramEqual1= null, paramEqual2 = null;

            for (var param:parameterMap.keySet()) {
                System.out.println(param.getSparqlName());
                if (param.getSparqlName().equals("paramEqual1")) {
                    paramEqual1 = parameterMap.get(param).iterator().next();
                }
                if (param.getSparqlName().equals("paramEqual2")) {
                    paramEqual2 = parameterMap.get(param).iterator().next();

                }
                if (param.getSparqlName().equals("paramSmallEq1")) {
                    paramEqual1 = parameterMap.get(param).iterator().next();
                    // node.smallerThan = true;
                }
                if (param.getSparqlName().equals("paramSmallEq2")) {
                    paramEqual2 = parameterMap.get(param).iterator().next();

                }
            }

            // node.param1 = paramEqual1;
            // node.param2 = paramEqual2;


            // Per SHACL-Def: The $this template is used to bind valid focus nodes (so in
            // our case ?x)
            // selectString = selectString.replaceAll("\\?this", "?"+node.getBindingVar());

            // Apply parameter-map
            /* for (var param : parameterMap.keys()) {
                // TODO why is parameter map a multimap?
                String paramValue = switch (parameterMap.get(param).stream().findFirst().get()) {
                    case Node_Literal literal:
                        yield "\"" + literal.getLiteral().getLexicalForm() + "\"";
                    case Node_URI uri:
                        yield uri.getURI();


                    default:
                        throw new ValidationException("Variable wrong");
                };

                // selectString = selectString.replaceAll("\\?" + param.getSparqlName(), paramValue);
            }

            customSelect.addPart(selectString);

            subQuery.addSubQuery(customSelect); */

        } catch (Exception e) {
            e.printStackTrace();
            throw new ValidationException("Cannot force access to ConstraintComponentSPARQL's protected fields.");
        }
    }

    private void handleCustomConstraint(ConstraintComponentSPARQL c, SHACLNode node, Query subQuery) {

        SparqlComponent customComponent;
        Multimap<Parameter, Node> parameterMap;
        try {
            /*
             * For some reason the actual semantics of a ConstraintComponentSPARQL are not
             * directly accessible by default.
             * So lets use reflection to circumvent central java concepts!
             */
            var f1 = c.getClass().getDeclaredField("sparqlConstraintComponent");
            f1.setAccessible(true);
            customComponent = (SparqlComponent) f1.get(c);

            var f2 = c.getClass().getDeclaredField("parameterMap");
            f2.setAccessible(true);
            parameterMap = (Multimap<Parameter, Node>) f2.get(c);

            // The subquery already binds variable ?x with valid focus nodes
            // we just have to replace the $this template the custom Constraint
            // and run it as a subquery
            var customSelect = sparqlGenerator.newQuery();
            String selectString = customComponent.getQuery().serialize();

            // If the custom constraint also uses variable ?x everything will go up in
            // flames
            // But ?x is pretty common so we transfer the problem to ?CUSTOMCONSTRAINT_x
            // If the user tries to use that, give up.

            Node paramEqual1= null, paramEqual2 = null;

            for (var param:parameterMap.keySet()) {
                System.out.println(param.getSparqlName());
                if (param.getSparqlName().equals("paramEqual1")) {
                    paramEqual1 = parameterMap.get(param).iterator().next();
                }
                if (param.getSparqlName().equals("paramEqual2")) {
                    paramEqual2 = parameterMap.get(param).iterator().next();

                }
            }


            System.out.println("Attached to params");


            // Per SHACL-Def: The $this template is used to bind valid focus nodes (so in
            // our case ?x)
            // selectString = selectString.replaceAll("\\?this", "?"+node.getBindingVar());

            // Apply parameter-map
            /* for (var param : parameterMap.keys()) {
                // TODO why is parameter map a multimap?
                String paramValue = switch (parameterMap.get(param).stream().findFirst().get()) {
                    case Node_Literal literal:
                        yield "\"" + literal.getLiteral().getLexicalForm() + "\"";
                    case Node_URI uri:
                        yield uri.getURI();


                    default:
                        throw new ValidationException("Variable wrong");
                };

                // selectString = selectString.replaceAll("\\?" + param.getSparqlName(), paramValue);
            }

            customSelect.addPart(selectString);

            subQuery.addSubQuery(customSelect); */

        } catch (Exception e) {
            e.printStackTrace();
            throw new ValidationException("Cannot force access to ConstraintComponentSPARQL's protected fields.");
        }

        // After just a few lines of black magic we can proceed as planned

    }

    /*
     * VALIDATION
     */

     private Set<ValidationResult> validate() {

        /*
         * GET ALL TARGETS
         */

        List<Node> targets;

        switch (tree) {
            case PShapeNode pShape -> {
                targets = pShape.getCountMap().keySet().stream().map((k->k.get(0))).collect(Collectors.toList());
            }

            default -> {

                print("TARGET QUERY");
                var q = sparqlGenerator.newQuery();
                q.addPart(Util.generateTargetString(shape.getTargets().iterator().next(), "targets"));
                targets = executeQuery(q).stream().map((binding) -> binding.get("targets")).collect(Collectors.toList());
                System.out.println("Receives %d Targets".formatted(targets.size()));
            }
        }

        

        /*
         * CHECK IF ATOMS VALIDATE
         */
        // For every single atom in the targets, perform 'lookup' in the sets of
        // validationg atoms for each propertynode

        System.out.println("Applying validation logic for targets.");

        return targets
                .stream()
                .parallel()
                .map((atom) -> {

                    var valNodes = new HashSet<SHACLNode>();

                    var res = tree.validatesRes(atom, valNodes);

                    return new ValidationResult(atom, valNodes, res);
                })
                .collect(Collectors.toSet());

    }




    /*
     * REPORT GENERATION
     */


    private String generateReportEntry(ValidationResult res) {

        String s = "";

        s += res.isValid() ? "✅ " : "❌ ";
        s += Util.wrap(res.getAtom().getURI()) + ":\n";
        s += Util.drawTreeNode(tree, res, 1);

        return s;
    }

    public void saveReport(String file) {

        print("Generating report.");

        // Generate stats
        var validRes = results.stream().filter((res) -> res.isValid()).count();

        var report = "🔍 VALIDATION REPORT:\nTotal targets: " + results.size() + " | ❌ INVALID: "
                + String.valueOf(results.size() - validRes) + " | ✅ VALID: " + validRes + "\n\n\n\n";

        report += results
                .stream()
                .map((res) -> generateReportEntry(res))
                .reduce("", (acc, str) -> acc + str + "\n\n\n");

        try {
            Files.write(Paths.get(file), report.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            print("Error saving report.");
        }

    }

    /*
     * HELPER FUNCTIONS
     */

    /**
     * Executes a SPARQL query against the SPARQL endpoint and filters the results
     * based on constraints.
     *
     * @param query The SPARQL query to execute.
     * @return The filtered query results.
     */
    private List<Binding> executeQuery(Query query) {
        indentlevel++;
        var sparql = query.getSparqlString();

        print(ansi().bgYellow().a("\n\n------------------- Running the following query: ------------------------"));
        print(sparql);
        print(ansi().reset());

        long startTime = System.nanoTime();
        var bindings = endpoint.query(sparql).select().stream().collect(Collectors.toList());
        long endTime = System.nanoTime();
        long duration = (endTime - startTime);

        print(ansi().fgGreen().a("\n----- Received "+bindings.size()+" rows in "+duration/1000000 +"ms. ------\n\n").reset());
        indentlevel--;
        return bindings;
    }

    /**
     *
     * @param node
     * @param c
     * @param s
     * @return
     */
    public SHACLNode addChildrenToNode(SHACLNode node, ConstraintOpN c, Shape s) {
        for (var child : c.getOthers()) {
            node.addChild(shapeToTree(child));
        }
        return node;
    }

    private void print(String s){
        for (var line:s.split("\n")){
            System.out.println(Util.indent(indentlevel, line));
        }
    }

    private void print(Object o){
        print(o.toString());
    }

}
