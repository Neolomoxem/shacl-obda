package ifis;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.compress.harmony.unpack200.bytecode.RuntimeVisibleorInvisibleParameterAnnotationsAttribute.ParameterAnnotation;
import org.apache.jena.ext.com.google.common.collect.Multimap;
import org.apache.jena.graph.Node;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.engine.Parameter;
import org.apache.jena.shacl.engine.Target;
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
import org.apache.jena.shacl.engine.constraint.ShNot;
import org.apache.jena.shacl.engine.constraint.ShOr;
import org.apache.jena.shacl.engine.constraint.ShXone;
import org.apache.jena.shacl.engine.constraint.SparqlComponent;
import org.apache.jena.shacl.engine.constraint.SparqlConstraint;
import org.apache.jena.shacl.engine.constraint.StrMaxLengthConstraint;
import org.apache.jena.shacl.engine.constraint.StrMinLengthConstraint;
import org.apache.jena.shacl.parser.Constraint;
import org.apache.jena.shacl.parser.NodeShape;
import org.apache.jena.shacl.parser.Shape;
import org.apache.jena.sparql.exec.QueryExecBuilder;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.sparql.path.P_Alt;
import org.apache.jena.sparql.path.P_Inverse;
import org.apache.jena.sparql.path.P_Link;
import org.apache.jena.sparql.path.P_Seq;
import org.apache.jena.sparql.path.Path;

import com.github.jsonldjava.shaded.com.google.common.collect.ArrayListMultimap;

import ifis.SPARQLGenerator.Query;
import ifis.logic.AndNode;
import ifis.logic.LogicNode;
import ifis.logic.NotNode;
import ifis.logic.OrNode;
import ifis.logic.PropertyNode;
import ifis.logic.XoneNode;

public class Validation {
    /* The input shape(s) */
    private final Shapes shapes;
    /*
     * A list of violations which gets populated by the application of the
     * logic-tree.
     */
    private final List<String> report;
    /* Some comfort for generating the SPARQL-Strings */
    private final SPARQLGenerator sparqlGenerator;
    /* Handles the access of the SPARQL endpoint */
    private final QueryExecHTTPBuilder endpoint;
    /* The logic tree given by its root node, which behaves like an AND */
    private LogicNode tree;

    private Target baseTarget;

    /**
     * @param shapes
     */
    public Validation(Shapes shapes, QueryExecHTTPBuilder endpoint) {
        this.shapes = shapes;
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
        shapes.forEach((shape) -> validateShape((NodeShape) shape));

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
        
        // Set basetarget
        baseTarget = shape.getTargets().stream().findFirst().get();

        /* 
         * CONSTRUCT LOGIC TREE
         */

        System.out.println("Building logic tree.");
        tree = shapeToTree(shape);
        System.out.println("Done.");


        /* 
         * POPULATE
         */

        // Generate queries and fetch validating atoms per propertynode
        if (tree instanceof PropertyNode) {
            populateNode((PropertyNode) tree);
        } else {
            findAndPopulateNodes(tree);
        }
        System.out.println("Nodes populated.");


        /* 
         * VALIDATE
         */

        // Run Validation of Target atoms and generate Report
        var results = validate();
        System.out.println("Validation done.");


        /* 
         * GENERATE REPORT
         */

        saveReport(results);

    }

    

    /*
     * LOGIC TREE METHODS
     */

    /**
     * Builds the logic tree for a shape.
     * Constructs logic nodes for constraints and property shapes recursively.
     * 
     * @param shape The shape to convert to a logic tree.
     * @return The root node of the constructed logic tree.
     */
    private LogicNode shapeToTree(Shape shape) {
        /*
         * Note:
         * Constraints can either be in directly part of the shape,
         * then they will be part of shape.getPropertyShapes()
         * 
         * Or they are connected using logical junctors or other ref-
         * erences. Then they will be in getConstraints().
         * 
         * So both sources have to be taken into account.
         */

        /* If there are junctors present, have to pass the */
        var opConstraints = shape.getConstraints()
                .stream()
                .filter(
                        (constraint) -> constraint instanceof ConstraintOp)
                .collect(Collectors.toSet());

        if (opConstraints.size() == 0 && shape.getPropertyShapes().size() == 0) {
            // The LogicNode constructor takes care of passing down the constraint semantics
            return new PropertyNode(shape);
        } else {
            var shapeRoot = new AndNode(shape);

            // For all op components create logic nodes
            opConstraints
                    .stream()
                    .map((c) -> createOpNode((ConstraintOp) c, shape))
                    .forEach(shapeRoot::addChild);

            shape
                    .getPropertyShapes()
                    .stream()
                    .map(this::shapeToTree)
                    .forEach((node) -> shapeRoot.addChild(node));

            if (shapeRoot.getChildren().size() == 1)
                return shapeRoot.getChildren().get(0);

            return shapeRoot;
        }

    }

    private LogicNode createOpNode(ConstraintOp c, Shape shape) {

        LogicNode node = null;

        switch (c) {
            case ShNot not -> {
                node = new NotNode(shape);
                node.addChild(shapeToTree(not.getOther()));
                return node;
            }
            case ShOr x -> node = new OrNode(shape);
            case ShAnd x -> node = new AndNode(shape);
            case ShXone x -> node = new XoneNode(shape);
            default -> throw new ValidationException("Constraint supplied to createOpNode was not a accepted.");
        }

        return addChildrenToNode(node, (ConstraintOpN) c, shape);

    }
    


    /*
     * QUERY GENERATION
     */

    /**
     * Generates a SPARQL query string for a property node.
     * 
     * @param node The property node for which to generate the query.
     * @return The generated SPARQL query string.
     */
    private Query generateQuery(PropertyNode node) {
        // Get Base Query (the Target definition)
        var baseQuery = sparqlGenerator.generateTargetQuery(baseTarget);

        var constraints = node.getNormConstraints();

        // New empty subquery for the constraint components
        var subQuery = sparqlGenerator.newQuery();

        // Generate Path from focus node to value node
        subQuery.addPart(generatePath("?x", "?p", node.getNormPath()));
        // addPath("?x", "?p", node.getNormPath(), subQuery);

        // Fill subquery with constraints
        constraints
                .stream()
                .forEach(addSPARQLForConstraint(node, subQuery));

        baseQuery.addSubQuery(subQuery);

        return baseQuery;
    }

    /**
     * Recursively resolves the path and returns a string that can be used in a query to fetch all value nodes described by it
     * @return
     */
    private String generatePath(String fromVar, String toVar, Path path) {
        return switch (path) {
            
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
                yield 
                    generatePath(fromVar, toVar+"L", seqPath.getLeft()) + "\n" +
                    generatePath(toVar+"L", toVar, seqPath.getRight());
            }
            
            default -> {
                // Shouldnt happen. Naturally.
                throw new ValidationException("Weird paths are happening");
            }
        };

    }

    private Consumer<? super Constraint> addSPARQLForConstraint(PropertyNode node, Query subQuery) {
        return (c) -> {
            switch (c) {

                /*
                 * MAIN CONSTRAINTS
                 */

                case ClassConstraint classConstraint -> {
                    subQuery.addTriple("?p", "a", wrap(classConstraint.getExpectedClass().getURI()));
                }

                /*
                 * STRING BASED CONSTRAINTS
                 */
                case StrMinLengthConstraint strMinLengthConstraint -> {
                    var minLen = strMinLengthConstraint.getMinLength();
                    node.addBindingFilter((s) -> s.filter((b) -> {
                        var val = b.get("p").getLiteralValue();
                        return val instanceof String && ((String) val).length() >= minLen;
                    }));
                }
                case StrMaxLengthConstraint strMaxLengthConstraint -> {
                var maxLen = strMaxLengthConstraint.getMaxLength();
                node.addBindingFilter((s) -> s.filter((b) -> {
                        var val = b.get("p").getLiteralValue();
                        return val instanceof String && ((String) val).length() >= maxLen;
                    }));
                }
                case PatternConstraint patternConstraint -> {
                    var patternString = patternConstraint.getPattern();
                    Pattern pattern = Pattern.compile(patternString);
                    node.addBindingFilter((s) -> s.filter((b) -> {
                        var val = b.get("p").getLiteralValue();
                        return val instanceof String && pattern.matcher((String) val).matches();
                    }));
                }

                /*
                 * VALUE CONSTRAINTS
                 */

                case HasValueConstraint hasValueConstraint -> {
                    var expectedVal = hasValueConstraint.getValue();
                    node.addBindingFilter((s) -> s.filter((b) -> b.get("p").equals(expectedVal)));
                }
                case InConstraint inConstraint -> {
                    var list = inConstraint.getValues();
                    node.addBindingFilter((s) -> s.filter((b) -> list.contains(b.get("p"))));
                }

                case DatatypeConstraint datatypeConstraint -> {
                    var datatype = datatypeConstraint.getDatatypeURI();
                    node.addBindingFilter(
                            (s) -> s.filter((binding) -> binding.get("p").getLiteralDatatypeURI().equals(datatype)));
                }

                /* 
                 * CUSTOM CONSTRAINTS
                 */

                case ConstraintComponentSPARQL customConstraint -> {
                    handleCustomConstraint(customConstraint, node, subQuery);
                }

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

                // Everything else IS an error
                default -> {
                    // throw new ValidationException("Unsupported Constraint: " + c.toString());
                }
            }
        };
    }

    private void handleCustomConstraint(ConstraintComponentSPARQL c, PropertyNode node, Query subQuery) {
        
        SparqlComponent             customComponent;
        Multimap<Parameter, Node>   parameterMap;
        try {
            /* 
             * For some reason the actual semantics of a ConstraintComponentSPARQL are not directly accessible by default. 
             * So lets use reflection to circumvent central java concepts!
             */
            var f1 = c.getClass().getDeclaredField("sparqlConstraintComponent");
            f1.setAccessible(true);
            customComponent = (SparqlComponent) f1.get(c);
            
            var f2 = c.getClass().getDeclaredField("parameterMap");
            f2.setAccessible(true);
            parameterMap = (Multimap<Parameter, Node>) f2.get(c);

            // The subquery already binds variable ?x with valid focus nodes
            // we just have to replace the $this template  the custom Constraint
            // and run it as a subquery
            var customSelect    = sparqlGenerator.newQuery();
            String selectString = customComponent.getQuery().serialize();

            // If the custom constraint also uses variable ?x everything will go up in flames
            // But ?x is pretty common so we transfer the problem to ?CUSTOMCONSTRAINT_x
            // If the user tries to use that, give up.
            if (selectString.contains("?CUSTOMCONSTRAINT_x")) throw new ValidationException("Please dont call a variable ?CUSTOMCONSTRAINT_x. Why would you?");
            selectString.replaceAll("?x", "?CUSTOMCONSTRAINT_x");

            // Per SHACL-Def: The $this template is used to bind valid focus nodes (so in our case ?x)
            selectString.replaceAll("$this", "?x");
            
            customSelect.addPart(selectString);

            subQuery.addSubQuery(customSelect);
            

            
        } catch (Exception e) {
            e.printStackTrace();
            throw new ValidationException("Cannot force access to ConstraintComponentSPARQL's protected fields.");
        }
        
        // After just a few lines of black magic we can proceed as planned
        
    }



    /*
     * NODE POPULATION
     */
    
    private Set<ValidationResult> validate() {

        /* 
         * GET ALL TARGETS
         */
        // Fetch all atoms mentioned by the target definiton
        // write query
        var baseQuery = sparqlGenerator.generateTargetQuery(baseTarget);
        // Get targets
        var targets = executeQuery(baseQuery).materialize();
        System.out.println("Receives %d Targets".formatted(targets.getRowNumber()));

        /* 
         * CHECK IF ATOMS VALIDATE
         */
        // For every single atom in the targets, perform 'lookup' in the sets of
        // validationg atoms for each propertynode

        System.out.println("Applying validation logic for targets.");

        // TODO: parallelize?
        return targets
                .stream()
                .map((binding) -> binding.get("x"))
                .map((atom) -> {

                    var valNodes = new HashSet<LogicNode>();

                    var res = tree.validatesRes(atom, valNodes);

                    return new ValidationResult(atom, valNodes, res);
                })
                .collect(Collectors.toSet());

    }

    /**
     * Populates the validating atoms per normalized property node.
     * Identifies property nodes and populates them by calling the populateNode()
     * method.
     * 
     * @param node The logic node to search for property nodes and populate.
     */
    private void findAndPopulateNodes(LogicNode node) {
        /**
         * After the Logic three was constructed, we get the validating set of atoms per
         * normalized property-node (populating the node)
         */
        // recursively find property nodes to be populated
        node.getChildren()
                .forEach((child) -> {
                    if (child instanceof PropertyNode) {
                        // Populate node
                        populateNode((PropertyNode) child);

                    } else {
                        findAndPopulateNodes(child);
                    }
                });

    }

    /**
     * Takes a PropertyNode, constructs a query and runs it against the endpoint,
     * thereby popluating the node.
     * 
     * @param node
     */
    private void populateNode(PropertyNode node) {

        // generate query
        System.out.println("\n Populating " + node.getReportString());
        System.out.println("\n> Generating Query.");
        Query nodeQuery = generateQuery(node);

        // Execute Query
        System.out.println("> Running query.");
        var results_raw = executeQuery(nodeQuery);

        var results = results_raw.stream().collect(Collectors.toSet());
        var resultsStream = results.stream();
        System.out.println("> Recieved " + results.size() + " bindings");

        var constraints = node.getNormConstraints();

        // Apply Binding filters (Datatype, Value Range, ...)
        System.out.println("> Applying BindingFilters.");
        for (var filter : node.getBindingFilters()) {
            resultsStream = filter.apply(resultsStream);
        }

        // Filter Rows based on datatype
        /*
         * if (datatypeConstraint.isPresent()) {
         * var datatype = ((DatatypeConstraint)
         * datatypeConstraint.get()).getDatatypeURI();
         * resultsStream = resultsStream
         * .filter((binding) -> {
         * System.out.println(datatype);
         * System.out.println(binding.get("p").getLiteralDatatypeURI());
         * return binding.get("p").getLiteralDatatypeURI().equals(datatype);
         * 
         * });
         * 
         * }
         */

        results = resultsStream.collect(Collectors.toSet());

        HashSet<Node> atoms = results
                .stream()
                .map((binding) -> binding.get("x"))
                .collect(Collectors.toCollection(HashSet::new));

        // Get min and max constraints if they are pr
        var minConstraint = get(constraints, MinCount.class);
        var maxConstraint = get(constraints, MaxCount.class);

        // If cardinality-constrained count bindings
        if (minConstraint.isPresent() || maxConstraint.isPresent()) {
            System.out.println("> Applying cardinality-rules \n");
            // Count results in HashMap
            var countMap = new HashMap<Node, Integer>();
            results
                    .stream()
                    .forEach((res) -> countMap.put(
                            res.get("x"),
                            countMap.getOrDefault(res, 0) + 1));

            // If min, filter out
            if (minConstraint.isPresent()) {
                var minVal = ((MinCount) minConstraint.get()).getMinCount();
                atoms = atoms
                        .stream()
                        .filter((atom) -> countMap.get(atom) >= minVal)
                        .collect(Collectors.toCollection(HashSet::new));
            }

            // if max filter out
            if (maxConstraint.isPresent()) {
                var maxVal = ((MaxCount) maxConstraint.get()).getMaxCount();
                // Special case only Max Constrained
                if (!minConstraint.isPresent()) {
                    // the validatingAtoms now become the invalidating atoms, if there are any
                    node.setInverted(true);
                    atoms = atoms
                            .stream()
                            .filter((atom) -> countMap.get(atom) > maxVal) // We want atoms to contain all that
                                                                           // invalidate
                            .collect(Collectors.toCollection(HashSet::new));
                } else {
                    atoms = atoms
                            .stream()
                            .filter((atom) -> countMap.get(atom) <= maxVal)
                            .collect(Collectors.toCollection(HashSet::new));

                }
            }

            // Populate node
            node.setValidatingAtoms(atoms);
        }
        System.out.println();
        node.setValidatingAtoms(atoms);

    }



    /*
     * REPORT GENERATION
     */

    private String generateReportEntry(ValidationResult res) {
        var s = "";

        s += res.isValid() ? "✅ " : "❌ ";
        s += wrap(res.getAtom().getURI()) + ":\n";
        s += drawTreeNode(tree, res, -1);

        return s;
    }

    private String drawTreeNode(LogicNode node, ValidationResult res, int indentlevel) {
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

    private void saveReport(Set<ValidationResult> results) {
        System.out.println("Generating report.");

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
            Files.write(Paths.get("./report.log"), report.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            System.out.println("Error saving report.");
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
    private RowSet executeQuery(Query query) {

        var sparql = query.getSparqlString("?x ?p");

        System.out.println("Running the following query: ");
        System.out.println(sparql);
        // System.out.println(endpoint.query(sparql).build().getHttpResponseContentType());
        
        return endpoint.query(sparql).select();
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
    public LogicNode addChildrenToNode(LogicNode node, ConstraintOpN c, Shape s) {
        c.getOthers()
                .stream()
                .map((childShape) -> shapeToTree(childShape))
                .forEach((subtree) -> node.addChild(subtree));
        return node;
    }

    /**
     * Gets the first constraint in CONSTRAINTS that is of class KLASSE
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
        for (int i = 0; i <= level; i++) {
            s += "       ";
        }
        return s + toIndent;
    }

}
