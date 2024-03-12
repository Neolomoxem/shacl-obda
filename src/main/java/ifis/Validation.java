package ifis;


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
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
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
import ifis.logic.EqualNode;
import ifis.logic.NotNode;
import ifis.logic.OrNode;
import ifis.logic.PShapeNode;
import ifis.logic.SHACLNode;
import ifis.logic.StringPath;
import ifis.logic.XoneNode;

import static java.lang.StringTemplate.STR;
import static org.fusesource.jansi.Ansi.*;
import static org.fusesource.jansi.Ansi.Color.*;

public class Validation {
    private final Shape shape;                      // Input
    private final List<String> report;              // Output in String
    private final SPARQLGenerator sparqlGenerator;  // Takes care of Variables
    private final QueryExecHTTPBuilder endpoint;    // The SPARQL is queried against this
    private SHACLNode tree;                         // From the input we generate a tree of SHACLNodes
    private Set<ValidationResult> results;
    private boolean isEvaluated = false;
    private String focusVar;
    public String getFocusVar() {
        return focusVar;
    }



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

        focusVar = "targets";

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
        print(node.toString());
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

        for (var c : pshape.getConstraints()) {
            switch (c) {
                /*
                    * Add junctive Constraints as children
                    */
                case ConstraintOp opcomp -> {
                    pnode.addChild(createOpNode(opcomp, pshape));
                }

                /*
                    * ENGINE CONSTRAINTS
                    */
                case ClassConstraint cc -> {
                    pnode.addEngineConstraint(cc);
                }

                case ConstraintComponentSPARQL custom -> {
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
                }
                
                /*
                * Add direct constraints to a child ConstraintNode
                */

                
                
                default -> {
                    cnode.addConstraint(c);
                }
            }
        }

        // If there were no direct constraints, we dont have to add the constraintnode
        if (cnode.getConstraints().size() > 0) {
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
                    case ConstraintComponentSPARQL custom -> {
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
                // populatePShape(pnode);
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

        var query = generateValidQuery(node);
        
        executeQuery(query);
        

    }

    private Query generateValidQuery(ConstraintNode node) {
        var query = sparqlGenerator.newQuery();
        
        addQueryPath(node, query);
        
        /* Add all the constraint logic */
        for (var c:node.getConstraints()) {
            addSPARQLForConstraint(c, node, query);
        }

        // BIND ?exists
        var path = generatePath(node.getPShape().getPath());
        var inmostVar = query.getPathVars().get(query.getPathVars().size() - 1);
        var upperVar = query.getPathVars().get(query.getPathVars().size() - 2);
        query.addPart(STR."BIND(EXISTS(?\{upperVar} \{path} ?\{inmostVar}) AS ?exists) .");


        // Create a String of all path Vars except the last one
        String pathVarProjection = "";
        for (var v:query.getPathVars().subList(0, query.getPathVars().size()-1)) {
            pathVarProjection += "?" + v + " ";
        }


        query.setProjection(STR."?exists \{pathVarProjection} (COUNT(?\{query.getInmostVar()}) AS ?count)");
        query.addOuterPart(STR."GROUP BY \{pathVarProjection}");

        return query;   

    }

    private void populatePShape(PShapeNode node) {

        var query = generateCountQuery(node);
        var bindings = executeQuery(query);
        var x = transformToPropertyMap(bindings);
        
    }



    private Map<Node, Integer> transformToPropertyMap(List<Binding> bindings) {
        var map = new HashMap<Node, Integer>();
        for (var b : bindings) {
            map.put(b.get("FOCUS"), (Integer) b.get("VALUE").getLiteralValue());
        }


        return map;
    }

    private Query generateCountQuery(PShapeNode node) {
        var query = sparqlGenerator.newQuery();
        
        addQueryPath(node, query);

        var bindingVar = node.getBindingVar();
        var parentVar = node.getParent() != null ? node.getParent().getBindingVar() : "targets";
        
        // query.setProjection("?" + parentVar + " COUNT(?" +bindingVar+")");
        query.setProjection(STR."(?\{parentVar} AS ?FOCUS) (COUNT(?\{bindingVar}) AS ?VALUE)");
        query.addOuterPart(STR."GROUP BY ?\{parentVar}");
        
        return query;
    }

    private void populateBaseBindings(SHACLNode node) {
        var query = generateQuery(node);

        var bindings = getBindingsListed(executeQuery(query).stream().collect(Collectors.toList()), node);
        
        node.setBaseBindings(bindings);
    }

    private void addQueryPath(SHACLNode node, Query query) {
        var lineage = node.getLineage();

        for (var target:this.shape.getTargets()) {
            query.addPart(Util.generateTargetString(target, focusVar));
        }

        
        lineage.add(0, node);
        
        var parentPShape = node.getPShape();

        var from = focusVar;
        query.pushPathVar(from);
        for (int ancestorIndex = lineage.size()-1; ancestorIndex >= 0; ancestorIndex-- ) {
            var ancestor = lineage.get(ancestorIndex);

            if (ancestor == parentPShape) {
                var pnode = (PShapeNode) ancestor;
                // Add path from previous to new value nodes
                query.addOptionalPart(Util.asTriple("?"+from, generatePath(pnode.getPath()),"?"+pnode.getBindingVar()));
                from = pnode.getBindingVar();
                query.pushPathVar(from);
                

                // Engineconstraints
                for (var engineconstraint : pnode.getEngineConstraints()) {
                    query.addOptionalPart(getSPARQLForEngineConstraint(engineconstraint, pnode, query));
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
            query.addPart(Util.generateTargetString(target, focusVar));
        }


        // For the rest
        var from = focusVar;

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

    private void getParams(ConstraintComponentSPARQL c, EqualNode node) {
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
                    node.smallerThan = true;
                }
                if (param.getSparqlName().equals("paramSmallEq2")) {
                    paramEqual2 = parameterMap.get(param).iterator().next();

                }
            }

            node.param1 = paramEqual1;
            node.param2 = paramEqual2;


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
        // Fetch all atoms mentioned by the target definiton
        // write query
        // Get targets
        var q = sparqlGenerator.newQuery();
        q.addPart(Util.generateTargetString(shape.getTargets().iterator().next(), focusVar));
        var targets = executeQuery(q);
        System.out.println("Receives %d Targets".formatted(targets.size()));

        /*
         * CHECK IF ATOMS VALIDATE
         */
        // For every single atom in the targets, perform 'lookup' in the sets of
        // validationg atoms for each propertynode

        System.out.println("Applying validation logic for targets.");

        // TODO: parallelize?
        return targets
                .stream()
                .map((binding) -> binding.get(focusVar))
                .map((atom) -> {

                    var valNodes = new HashSet<SHACLNode>();

                    var res = tree.validatesRes(atom, valNodes);

                    return new ValidationResult(atom, valNodes, res);
                })
                .collect(Collectors.toSet());

    }

    private void populateNode__old(SHACLNode node) {

        print("> Populating Node "+node.toString()+"\n");
        indentlevel++;

        /*
         * GENERATE QUERY
         */

        var query = generateQuery(node);


        /*
         * EXECUTE QUERY
         */

        print("> Running query.");
        var results       = executeQuery(query);

        switch (node) {
            case PShapeNode pnode:
                return;

            case ConstraintNode cnode:
                handleInMemoryConstraint(cnode, results);
                break;

            default:
                throw new InternalValidationException("Cant populate this node directly "+node.toString());
        }

        if (node instanceof PShapeNode) {
            // We can return, since there is no in memory computation necessary.
            // Iff the node is PSHAPENode, there should be no constraints or only engine constraints.
            // Otherwise there would be children and popoulateNode would not get called on it.
            return;
        }


        indentlevel--;

    }

    private void handleInMemoryConstraint(ConstraintNode node, List<Binding> results ) {

        // Initiate strem for Bindingfilters
        var resultsStream = results.stream();


        /*
        * APPLY BINDING FILTERS
        */

        // Apply Binding filters (Datatype, Value Range, ...)
        print("> Applying BindingFilters.");
        for (var filter : node.getBindingFilters()) {
            resultsStream = filter.apply(resultsStream);
        }

        // Collect stream for hashmaps
        var filteredBindings = resultsStream.toList();


        /*
        * CONVERT BINDINGS TO LIST
        */

        var bindingsListed = getBindingsListed(filteredBindings, node);

        if (node.retain) {
            var map = new HashMap<List<Node>, Set<Node>>();

            var varHir = Util.genVarHirarchy(node, this);

            for (var b:bindingsListed) {

                // [a,b,c,d] => [a, b, c] -> d

                var sublist = b.subList(0, varHir.size()-2);
                var l = map.get(sublist);

                if (l == null) {
                    l = new HashSet<Node>();
                    map.put(sublist, l);
                }

                l.add(b.get(varHir.size()-1));
            }
            node.retained = map;
        }

        /*
         * COUNT FOR CARDINALITY CONSTRAINTS
         */

        bindingsListed = filterCardinality(bindingsListed, node);


        node.validBindings = bindingsListed;

        indentlevel--;

    }

    private Set<List<Node>> filterCardinality(Set<List<Node>> bindingsListed, ConstraintNode node) {

        if (bindingsListed.size() == 0) return bindingsListed;

        var numVars   = bindingsListed.iterator().next().size();


        /*
         * APPLY CARDINALITY LOGIC
         */
        MaxCount maxc2 = null;
        MinCount minc2 = null;

        for (var c:node.getConstraints()) {
            switch (c) {
                case MinCount x -> {
                    minc2 = x;
                }
                case MaxCount x -> {
                    maxc2 = x;
                }
                default -> {
                    // Do nothing
                }
            }
        }
        // Needed for lambda
        var maxc = maxc2;
        var minc = minc2;

        var cardinalBindings = new HashSet<List<Node>>();

        if (numVars==1) {
            var rightAmount = true;
            if (minc != null) {
                 if (bindingsListed.size() < minc.getMinCount()) rightAmount = false;
            }
            if (maxc != null) {
                 if (bindingsListed.size() > maxc.getMaxCount()) rightAmount = false;
            }

            return rightAmount? bindingsListed : new HashSet<List<Node>>();

        }

        if (minc == null && maxc == null) {

            var bMeta = new HashSet<List<Node>>();

            for (var b:bindingsListed) {
                var sublist   = b.subList(0, numVars-1);
                bMeta.add(sublist);

            }
            return bMeta;




        };

        /*
         * GENERATE COUNTMAP
         */

        Map<List<Node>, Integer> countmap = new HashMap<>();

        for (var b:bindingsListed) {

            var sublist   = b.subList(0, numVars-1);
            var count     = countmap.get(sublist);
            if (count == null) {
                count = 0;
            }
            countmap.put(sublist, count + 1);
        }

        /*
         * ONLY MAX
         */

        if (minc == null && maxc != null) {
            for (var b:bindingsListed) {
                var sublist   = (ArrayList<Node>)b.subList(0, numVars-1);
                var count     = countmap.get(sublist);

                // In this case the cardinalBindings are all INVALID and need to be substracted from a baseset
                if (count > maxc.getMaxCount()) cardinalBindings.add(sublist);
            }

            // Now run basequery and subtract
            populateBaseBindings(node);

            Set<List<Node>> baseBindingsMeta = new HashSet<List<Node>>();
            // Shorten by one Var
            for (var b:node.getBaseBindings()) {
                baseBindingsMeta.add(b.subList(0, numVars-1));
            }

            // Remove all mentioned bindings with higher count
            for (var b:cardinalBindings) {
                var count     = countmap.get(b);
                if (count > maxc.getMaxCount()) cardinalBindings.remove(b);
            }


            return baseBindingsMeta;




        }

        /*
         * MIN FILTERING
         */
        if (minc != null) {
            for (var b:bindingsListed) {
                var sublist   = b.subList(0, numVars-1);
                var count     = countmap.get(sublist);
                if (count==null) count=0;

                if (count >= minc.getMinCount()) cardinalBindings.add(sublist);
            }
        }



        /*
         * MAX FILTERING
         */
        if (maxc != null) {
            for (var b:cardinalBindings) {
                var sublist   = b.subList(0, numVars-2);
                var count     = countmap.get(sublist);
                if (count==null) count=0;

                if (count > maxc.getMaxCount()) cardinalBindings.remove(sublist);
            }
        }

        return cardinalBindings;
    }

    private Set<List<Node>> getBindingsListed(List<Binding> filteredBindings, SHACLNode node) {


        var varHir = Util.genVarHirarchy(node, this);
        long startTime2 = System.nanoTime();

        Set<List<Node>> o = filteredBindings.stream()
            .map(b->{
                var l = new ArrayList<Node>();
                for (int i = varHir.size()-1; i>=0; i--) {
                    l.add(b.get(varHir.get(i)));
                }
                return l;
            }).collect(Collectors.toSet());

        long endTime2 = System.nanoTime();
        long duration2 = (endTime2 - startTime2);

        print("> Generated Listviews in "+duration2/1000000 +"ms.");
        return o;
}

    /*
     * REPORT GENERATION
     */

    private HashMap<String, HashMap<Node, List<Node>>> buildHashes(List<Binding> filteredBindings, SHACLNode node) {


        /*
         * GENERATE VAR HIRARCHY
         */
        var varHirachy = Util.genVarHirarchy(node, this);


        /*
         * INIT EMPTY HASHMAP PER VAR
         */

        // This will hold the reachable nodes from every Binding of a variable
        var hashes = new HashMap<String, HashMap<Node, List<Node>>>();

        for (var bVar:varHirachy) {
            if (bVar.equals(varHirachy.get(varHirachy.size()-1))) continue;
            String varName = bVar;
            hashes.put(varName, new HashMap<Node, List<Node>>());
        }

        varHirachy
            .stream()
            // .parallel()
            .forEach((var bVar) -> {
                // The last variable doesnt get its own hashmap
                if (bVar.equals(varHirachy.get(varHirachy.size()-1))) return;
                // Get successor variable
                var prevVar = varHirachy.get(varHirachy.indexOf(bVar)+1);

                // Get HashMap for this variable
                var bMap = hashes.get(bVar);

                // Initiate a new stream
                filteredBindings.stream()
                    .forEach((b) -> {
                        // Get value of this var in binding
                        Node valueV1 = b.get(bVar);
                        // Get value of succesor var in this binding
                        Node valueV2 = b.get(prevVar);

                        var prevList = bMap.get(valueV1);

                        // If there are no previous successors, create new list and link
                        if (prevList == null) {
                            prevList = new ArrayList<Node>();
                            bMap.put(valueV1, prevList);
                        }

                        // Finally, add value of successor var to list of successors.
                        prevList.add(valueV2);

                    });
            });

        return hashes;
    }

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
        // TODO Make this more performant maybe
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
