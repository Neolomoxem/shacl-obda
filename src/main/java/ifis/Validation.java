package ifis;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
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
import ifis.logic.AndNode;
import ifis.logic.ConstrainedSHACLNode;
import ifis.logic.ConstraintNode;
import ifis.logic.EqualNode;
import ifis.logic.NotNode;
import ifis.logic.OrNode;
import ifis.logic.PShapeNode;
import ifis.logic.SHACLNode;
import ifis.logic.StringPath;
import ifis.logic.XoneNode;

public class Validation {
    private final Shape shape;                      // Input 
    private final List<String> report;              // Output in String
    private final SPARQLGenerator sparqlGenerator;  // Takes care of Variables
    private final QueryExecHTTPBuilder endpoint;    // The SPARQL is queried against this
    private SHACLNode tree;                         // From the input we generate a tree of SHACLNodes
    private Set<ValidationResult> results;          
    private boolean isEvaluated = false;
    private String focusVar;
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
     * LOGIC TREE METHODS
     */

    private void cleanUp(SHACLNode node) {
        for (var child:node.getChildren()) {
            cleanUp(child);

            if (child instanceof ConstraintNode) {
                if (((ConstraintNode) child).getConstraints().size() ==0) {

                    if (child.getChildren().size() <= 1) {
                        node.suspend(child);
                    }
                }
            }
        }
    }

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

        tree.setParent(null);
    }



    private SHACLNode shapeToTree(Shape shape) {
        return switch (shape) {
            case PropertyShape pshape -> {

                var pnode = new PShapeNode(pshape, sparqlGenerator.getNewVariable());
                var cnode = new ConstraintNode(pshape);

                /* 
                 * For all constraints of the shape
                 */
                for (var comp : pshape.getConstraints()) {
                    switch (comp) {
                        /* 
                         * Add junctive Constraints as children
                         */
                        case ConstraintOp opcomp -> {
                            pnode.addChild(createOpNode(opcomp, pshape));
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
                            
                            p1.classc = wrap(eqnode.param1.getURI());
                            c1.retain = true;
                            

                            p1.addChild(p11);
                            p11.addChild(c1);
                            eqnode.addChild(p1);
                            
                            var c2 = new ConstraintNode(shape);
                            var p2 = new PShapeNode(new StringPath("<urn:absolute/prototyp#hat_Parameter>"), sparqlGenerator.getNewVariable());
                            var p22 = new PShapeNode(new StringPath("<urn:absolute/prototyp#hat_Wert>"), sparqlGenerator.getNewVariable());
                            

                            p2.classc = wrap(eqnode.param2.getURI());
                            c2.retain = true;
                            

                            p2.addChild(p22);
                            p22.addChild(c2);
                            eqnode.addChild(p2);
                            
                            pnode.addChild(eqnode);
                        }
                        
                        
                        
                        default -> {
                            cnode.addConstraint(comp);
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

                yield pnode;
            }
            case NodeShape nshape -> {



                var cnode = new ConstraintNode(nshape);

                
                // If a pshape is present, transform a nodeshape to an ANDNode of a constraintnode and a pshapenode
                if (nshape.getPropertyShapes().size() > 0 || nshape.getConstraints().stream().filter(c->c instanceof ConstraintOp).count() > 0 || nshape.getConstraints().stream().filter(c->c instanceof ConstraintComponentSPARQL).count() > 0) {

                    var andnode = new AndNode(nshape);
                    
                    /* 
                    * For all constraints of the shape
                    */
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
                            
                            p1.classc = wrap(eqnode.param1.getURI());
                            c1.retain = true;
                            

                            p1.addChild(p11);
                            p11.addChild(c1);
                            eqnode.addChild(p1);
                            
                            var c2 = new ConstraintNode(shape);
                            var p2 = new PShapeNode(new StringPath("<urn:absolute/prototyp#hat_Parameter>"), sparqlGenerator.getNewVariable());
                            var p22 = new PShapeNode(new StringPath("<urn:absolute/prototyp#hat_Wert>"), sparqlGenerator.getNewVariable());
                            

                            p2.classc = wrap(eqnode.param2.getURI());
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

                    if (andnode.getChildren().size() == 1) yield andnode.getChildren().get(0);

                    yield andnode;

                } else {
                    for (var comp:nshape.getConstraints()) {
                        cnode.addConstraint(comp);
                    }
                    yield cnode;
                }

                
            }
            default -> throw new ValidationException(
                    "There is a new kind of shape. If you see this error you are most likely from the future. Please note that this tool was written in 2023, back when we only knew NodeShapes and PropertyShapes! Can you imagine?");

        };
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
    private void populateTree(SHACLNode node) {
        
        if (node instanceof NotNode) {
            populateBaseBindings((NotNode)node);
        }

        if (node.getChildren().size() == 0) {
            // Anchor
            populateNode((ConstrainedSHACLNode) node);
            
        } else {
            // Recurse for all child nodes.
            for (var childNode:node.getChildren()) {
                populateTree(childNode);
            }
        }

    }

    /*
     * QUERY GENERATION
     */

    private void populateBaseBindings(SHACLNode node) {
        var query = generateQuery(node);
        var bindings = getBindingsListed(executeQuery(query).stream().collect(Collectors.toList()), node);
        
        node.setBaseBindings(bindings);
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
            query.addSubQuery(sparqlGenerator.generateTargetQuery(target, focusVar));
        }


        // For the rest
        var from = focusVar;

        lineage.add(0, node);

        for (int ancestorIndex = lineage.size()-1; ancestorIndex >= 0; ancestorIndex-- ) {
            var ancestor = lineage.get(ancestorIndex);
          
            switch (ancestor) {
                case PShapeNode pnode -> {
                    // Add path from previous to new value nodes
                    query.addPart(generatePath("?"+from, "?"+pnode.getBindingVar(), pnode.getPath()));
                    from = pnode.getBindingVar();
                    
                    if (pnode.classc != null) query.addTriple("?"+pnode.getBindingVar(), "a", pnode.classc);
                    
                    // Engineconstraints
                    for (var engineconstraint : pnode.getEngineConstraints()) {
                        // TODO
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
    private String generatePath(String fromVar, String toVar, Path path) {
        
        return switch (path) {

            case StringPath stringPath -> {
                yield fromVar + " "+ stringPath.value +" "+toVar+".";
            }


            /*
             * DIRECT PATH
             */
            case P_Link linkPath -> {
                // Just create a direct path from toVar to fromVar
                yield asTriple(fromVar, wrap(linkPath.getNode().getURI()), toVar);
                // query.addTriple(fromVar, wrap(cpath.getNode().getURI()), toVar);
            }

            /*
             * ALTERNATIVE PATH
             */
            case P_Alt altPath -> {
                // Recurse and UNION
                yield "{ " + generatePath(fromVar, toVar, altPath.getLeft()) +
                        " }\nUNION\n{ " +
                        generatePath(fromVar, toVar, altPath.getRight()) + "}\n";
            }

            /*
             * INVERSE PATH (PARENT)
             */
            case P_Inverse inversePath -> {
                // Just switch toVar and fromVar
                yield generatePath(toVar, fromVar, inversePath.getSubPath());
            }

            /*
             * SEQUENCE PATH
             */
            case P_Seq seqPath -> {
                // A pure seqPath is (counter-intuetively) structured like this:
                // (((A, B), C), D) --> .getRight() is a P_Link and .getLeft() a P_Seq
                yield generatePath(fromVar, toVar + "L", seqPath.getLeft()) + "\n" +
                        generatePath(toVar + "L", toVar, seqPath.getRight());
            }

            default -> {
                // Shouldnt happen. Naturally.
                throw new ValidationException("Weird paths are happening");
            }
        };

    }


    private void addSPARQLForConstraint(Constraint c, ConstrainedSHACLNode node, Query subQuery) {
        var bindingVar = node.getBindingVar();
        switch (c) {
            /*
                * MAIN CONSTRAINTS
                */

                case ClassConstraint classConstraint -> {
                    subQuery.addTriple("?" + bindingVar, "a", wrap(classConstraint.getExpectedClass().getURI()));
                }
            /*
                * STRING BASED CONSTRAINTS
                */
            case StrMinLengthConstraint strMinLengthConstraint -> {
                var minLen = strMinLengthConstraint.getMinLength();
                node.addBindingFilter((s) -> s.filter((b) -> {
                    var val = b.get(bindingVar).getLiteralValue();
                    return val instanceof String && ((String) val).length() >= minLen;
                }));
            }
            case StrMaxLengthConstraint strMaxLengthConstraint -> {
                var maxLen = strMaxLengthConstraint.getMaxLength();
                node.addBindingFilter((s) -> s.filter((b) -> {
                    var val = b.get(bindingVar).getLiteralValue();
                    return val instanceof String && ((String) val).length() >= maxLen;
                }));
            }
            case PatternConstraint patternConstraint -> {
                var patternString = patternConstraint.getPattern();
                Pattern pattern = Pattern.compile(patternString);
                node.addBindingFilter((s) -> s.filter((b) -> {
                    var val = b.get(bindingVar).getLiteralValue();
                    return val instanceof String && pattern.matcher((String) val).matches();
                }));
            }
            /*
                * VALUE RANGE CONSTRAINTS
                */

            case ValueMinExclusiveConstraint minExC -> {

                var minVal = minExC.getNodeValue().getFloat();
                print(minVal);
                node.addBindingFilter((s) -> s.filter((b) -> {
                    if (!b.get(bindingVar).isLiteral())
                        return false;
                    try {
                        var val = Double.parseDouble(b.get(bindingVar).getLiteralValue().toString());
                        return val > minVal;
                    } catch (Exception e) {
                        return false;
                    }

                }));
            }
            case ValueMinInclusiveConstraint minInC -> {

                var minVal = minInC.getNodeValue().getFloat();
                print(minVal);
                node.addBindingFilter((s) -> s.filter((b) -> {
                    if (!b.get(bindingVar).isLiteral())
                        return false;
                    try {
                        var val = Double.parseDouble(b.get(bindingVar).getLiteralValue().toString());
                        return val >= minVal;
                    } catch (Exception e) {
                        return false;
                    }

                }));
            }
            case ValueMaxExclusiveConstraint maxExC -> {

                var maxVal = maxExC.getNodeValue().getFloat();
                print(maxVal);
                node.addBindingFilter((s) -> s.filter((b) -> {
                    if (!b.get(bindingVar).isLiteral())
                        return false;
                    try {
                        var val = Double.parseDouble(b.get(bindingVar).getLiteralValue().toString());
                        return val < maxVal;
                    } catch (Exception e) {
                        return false;
                    }

                }));
            }
            case ValueMaxInclusiveConstraint maxInC -> {

                var maxVal = maxInC.getNodeValue().getFloat();
                print(maxVal);
                node.addBindingFilter((s) -> s.filter((b) -> {
                    if (!b.get(bindingVar).isLiteral())
                        return false;
                    try {
                        var val = Double.parseDouble(b.get(bindingVar).getLiteralValue().toString());
                        return val <= maxVal;
                    } catch (Exception e) {
                        return false;
                    }

                }));
            }

            /*
                * VALUE CONSTRAINTS
                */

            case HasValueConstraint hasValueConstraint -> {
                var expectedVal = hasValueConstraint.getValue();
                node.addBindingFilter((s) -> s.filter((b) -> b.get(bindingVar).equals(expectedVal)));
            }
            case InConstraint inConstraint -> {
                var list = inConstraint.getValues();
                node.addBindingFilter((s) -> s.filter((b) -> list.contains(b.get(bindingVar))));
            }

            case DatatypeConstraint datatypeConstraint -> {
                var datatype = datatypeConstraint.getDatatypeURI();
                node.addBindingFilter(
                        (s) -> s.filter(
                                (binding) -> binding.get(bindingVar).getLiteralDatatypeURI().equals(datatype)));
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
        var targets = executeQuery(sparqlGenerator.generateTargetQuery(shape.getTargets().iterator().next(), focusVar));
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

    /*
     * REPORT GENERATION
     */

    private void populateNode(ConstrainedSHACLNode node) {
        
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
        long startTime = System.nanoTime();
        var results       = executeQuery(query);
        


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
        
            var varHir = genVarHirarchy(node);

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
        
        /* 
         * CONVERT TO FIRST LEVEL MAPPING: [a,b,c,d] => [a, b, c] -> d
         */
       
        /* var map = new HashMap<List<Node>, Set<Node>>();
        
        var varHir = genVarHirarchy(node);

        for (var b:bindingsListed) {
            
            // [a,b,c,d] => [a, b, c] -> d
            
            var sublist = b.subList(0, varHir.size()-1);
            var l = map.get(sublist);

            if (l == null) {
                l = new HashSet<Node>();
                map.put(sublist, l);
            }
            
            l.add(b.get(varHir.size()-1));
        } */

        node.validBindings = bindingsListed;
        
        indentlevel--;

    }

    private Set<List<Node>> filterCardinality(Set<List<Node>> bindingsListed, ConstrainedSHACLNode node) {
        
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
        long startTime2 = System.nanoTime();

        var varHir = genVarHirarchy(node);

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

    private List<String> genVarHirarchy(SHACLNode node) {
        List<String> varHirachyIM = node.getLineage()
            .stream()
            .filter(ancestor -> ancestor instanceof PShapeNode)
            .map(ancestor->ancestor.getBindingVar())
            .toList();

        var varHirachy = new ArrayList<>(varHirachyIM);
        // Add the base target var to the hirachy as top level
        varHirachy.add(focusVar);

        return varHirachy;
    }

    private HashMap<String, HashMap<Node, List<Node>>> buildHashes(List<Binding> filteredBindings, SHACLNode node) {
        
        
        /* 
         * GENERATE VAR HIRARCHY
         */
        var varHirachy = genVarHirarchy(node);
        
        
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
        s += wrap(res.getAtom().getURI()) + ":\n";
        s += drawTreeNode(tree, res, 1);

        return s;
    }

    private String drawTreeNode(SHACLNode node, ValidationResult res, int indentlevel) {
        var valid = res.getValidatingNodes().contains(node);
        var s = indent(indentlevel, "┗━");
        s += valid ? "✅━" : "❌━";
        s += node.getReportString();
        s += node.getChildren()
                .stream()
                .map((childNode) -> drawTreeNode(childNode, res, indentlevel + 1))
                .reduce("", (acc, str) -> acc + "\n" + str);

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

    // TODO Add JSON reports

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
        var sparql = query.getSparqlString("*");

        print("\n------------------- Running the following query: ------------------------");
        print(sparql);
        
        long startTime = System.nanoTime();
        var bindings = endpoint.query(sparql).select().stream().collect(Collectors.toList());
        long endTime = System.nanoTime();
        long duration = (endTime - startTime);
        
        print("----- Received "+bindings.size()+" rows in "+duration/1000000 +"ms. ------\n\n");
        indentlevel--;
        return bindings;
    }

    /**
     * Just concatenates the triple into a sparql statement ended with a dot.
     * TODO is it a statement?
     * 
     * @param sub
     * @param pred
     * @param obj
     * @return
     */
    public static String asTriple(String sub, String pred, String obj) {
        return sub + " " + pred + " " + obj + ".";
    }

    /**
     * Wraps the string with '<' and '>'
     * 
     * @param toWrap
     * @return
     */
    public static String wrap(String toWrap) {
        return "<" + toWrap + ">";
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

    /**
     * Gets the first constraint in CONSTRAINTS that is of class KLASSE
     * 
     * @param constraints
     * @param klasse
     * @return
     */
    private Optional<Constraint> get(HashSet<Constraint> constraints, Class klasse) {
        return constraints
                .stream()
                .filter((c) -> klasse.isInstance(c))
                .findFirst();
    }

    /**
     * Returns level-amount tabs
     * 
     * @param level the amount of tabs
     * @return intendation as a string
     */
    private String indent(int level, String toIndent) {
        var s = " ";
        for (int i = 0; i < level; i++) {
            s += "   ";
        }
        return s + toIndent;
    }

    private void print(String s){
        for (var line:s.split("\n")){

            System.out.println(indent(indentlevel, line));
        }
    }



    private void print(Object o){
        print(o.toString());
    }

}
