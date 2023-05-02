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

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.engine.Target;
import org.apache.jena.shacl.engine.constraint.ClassConstraint;
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
import org.apache.jena.shacl.engine.constraint.StrMaxLengthConstraint;
import org.apache.jena.shacl.engine.constraint.StrMinLengthConstraint;
import org.apache.jena.shacl.parser.Constraint;
import org.apache.jena.shacl.parser.NodeShape;
import org.apache.jena.shacl.parser.Shape;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.sparql.path.P_Alt;
import org.apache.jena.sparql.path.P_Inverse;
import org.apache.jena.sparql.path.P_Link;
import org.apache.jena.sparql.path.P_Seq;
import org.apache.jena.sparql.path.Path;

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
    /* A list of violations which gets populated by the application of the logic-tree. */
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

    /** Executes the validation by accessing the specified SPARQL Endpoint. */
    public List<String> exec() {

                            // Validate Rootshapes
        shapes.forEach((shape) -> validateShape((NodeShape) shape));

        // Generate Report
        
        // Return Report
        return report;
    }
    
    /* 
     * Generates the logic tree for a given shape and applies 
     */
    public void validateShape(NodeShape shape) {
        
        // Build Tree
        System.out.println("Building logic tree.");
        tree = shapeToTree(shape);
        System.out.println("Done.");
        
        // Set basetarget
        baseTarget = shape.getTargets().stream().findFirst().get();

        // Generate queries and fetch validating atoms per propertynode

        if (tree instanceof PropertyNode) {
            populateNode((PropertyNode) tree);
        } else {
            findAndPopulateNodes(tree);
        }
        System.out.println("Nodes populated.");

        // Run Validation of Target atoms and generate Report
        
        var results = validate();
        System.out.println("Validation done.");

        saveReport(results);
        

    }
    
    private void saveReport(Set<ValidationResult> results) {
        System.out.println("Generating report.");


        // Generate stats 
        // TODO Make this more performant maybe
        var validRes = results.stream().filter((res) -> res.isValid()).count();

        var report = "🔍 VALIDATION REPORT:\nTotal targets: "+results.size()+" | ❌ INVALID: "+String.valueOf(results.size()-validRes)+" | ✅ VALID: "+validRes+"\n\n\n\n";
        
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

        return s ;
    }

  
    private Set<ValidationResult> validate() {

        var baseQuery = sparqlGenerator.generateTargetQuery(baseTarget);

/*         executeQuery(baseQuery)
            .stream()
            .parallel()
            .map((binding) -> binding.get("x"))
            .forEach((atom) -> {
                tree.validates(atom);
            }); */
        var targets = executeQuery(baseQuery).materialize();
        

        System.out.println("Applying validation logic for targets.");

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


    /* Logic Tree Methods */

    private LogicNode createOpNode(ConstraintOp c, Shape shape) {
        
        LogicNode node = null;

        /* Dont talk to me about any of this */
        if (c instanceof ShNot) {
            node = new NotNode(shape);
            node.addChild(shapeToTree(((ShNot) c).getOther()));
            return node;
        }
        
        if (c instanceof ShOr) {
            node = new OrNode(shape);
        } else if (c instanceof ShAnd) {
            node = new AndNode(shape);
        } else if (c instanceof ShXone) {
            node = new XoneNode(shape);
        }

        return addChildrenToNode(node, (ConstraintOpN) c, shape);

    }
    
    /** Creates a logic tree for a given shape */
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

        /* If there are junctors present, have to pass the  */
        var opConstraints = shape.getConstraints()
         .stream()
         .filter(
            (constraint) -> constraint instanceof ConstraintOp )
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

            if (shapeRoot.getChildren().size() == 1) return shapeRoot.getChildren().get(0);

            return shapeRoot;
        }

    } 

    
    /* Endpoint */

    private RowSet executeQuery(Query query) {
        
        var sparql = query.getSparqlString("?x ?p");
        
        System.out.println("Running the following query: ");
        System.out.println(sparql);

        return endpoint.query(sparql).select();
    }

    /** After the Logic three was constructed, we get the validating set of atoms per normalized property-node (populating the node) */
    private void findAndPopulateNodes(LogicNode node){
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
     * Takes a PropertyNode, constructs a query and runs it against the endpoint, thereby popluating the node.
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
        System.out.println("> Recieved "+ results.size() +" bindings");

        

        var constraints = node.getNormConstraints();


        
        // Apply Binding filters (Datatype, Value Range, ...)
        System.out.println("> Applying BindingFilters.");
        for (var filter : node.getBindingFilters()) {
            resultsStream = filter.apply(resultsStream);
        }
        
        
        // Filter Rows based on datatype
        /*         if (datatypeConstraint.isPresent()) {
            var datatype = ((DatatypeConstraint) datatypeConstraint.get()).getDatatypeURI();
            resultsStream = resultsStream
            .filter((binding) -> {
                System.out.println(datatype);
                System.out.println(binding.get("p").getLiteralDatatypeURI());
                return binding.get("p").getLiteralDatatypeURI().equals(datatype);
                
            });
            
        } */
        
        
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
                    countMap.getOrDefault(res, 0) + 1
                    ));
        

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
                                .filter((atom) -> countMap.get(atom) > maxVal) //We want atoms to contain all that invalidate
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

    
    
    /* Query Generators */
    private Query generateQuery(PropertyNode node) {
        // Get Base Query (the Target definition)
        var baseQuery = sparqlGenerator.generateTargetQuery(baseTarget);
        
        
        
        var constraints = node.getNormConstraints();
        
        // New empty subquery for the constraint components
        var subQuery = sparqlGenerator.newQuery();

        // Generate Path from focus node to value node
        addPath("?x", "?p", node.getNormPath(), subQuery);
        
        // Fill subquery with constraints
        constraints
            .stream()
            .forEach(addSPARQLForConstraint(node, subQuery));

        baseQuery.addSubQuery(subQuery);

        return baseQuery;
    }
    
    private void addPath(String fromVar, String toVar, Path path, Query query) {

        switch (path) {
            case P_Link cpath -> {
                query.addTriple(fromVar, wrap(cpath.getNode().getURI()), toVar);
            }
            case P_Alt altPath -> {
                // TODO: handle P_Alt path
            }
            case P_Inverse inversePath -> {
                // TODO: handle P_Inverse path
                query.addTriple(toVar, inversePath.getSubPath(), fromVar)
            }
            case P_Seq seqPath -> {
                // TODO: add Seq path
            }
            default -> {
                // TODO: handle unknown path
            }
        }
        

/*         if (path instanceof P_Link) {
            var cpath = (P_Link) path;
            query.addTriple(fromVar, wrap(cpath.getNode().getURI()), toVar);
        } else if (path instanceof P_Alt) {
            // TODO support alt path
        } else if (path instanceof P_Inverse) {
            // TODO support inverse path
        } else if (path instanceof P_Seq) {
            // TODO support P_Seq path
        } */
    }

    private Consumer<? super Constraint> addSPARQLForConstraint(PropertyNode node, Query subQuery) {
        return (c) -> {
            // Value-type Constraint Components
            if (c instanceof ClassConstraint) {
                subQuery.addTriple(
                    "?p",
                    "a",
                    wrap(((ClassConstraint) c).getExpectedClass().getURI())
                    );
            } 
            
            else if (c instanceof DatatypeConstraint) {
                var datatype = ((DatatypeConstraint) c).getDatatypeURI();
                
                node.addBindingFilter(
                    (s) -> s.filter((binding) -> binding.get("p").getLiteralDatatypeURI().equals(datatype))
                    );
            }
            
/*             else if (c instanceof NodeKindConstraint) {
                // TODO
            } */
            // Value-Range Constraint Components
/*             else if (c instanceof ValueMaxExclusiveConstraint) {
                // TODO
            }  */
            
/*             else if (c instanceof ValueMaxInclusiveConstraint) {
                // TODO
            }  */
/*             
            else if (c instanceof ValueMinExclusiveConstraint) {
                // TODO
            }  */
            
/*             else if (c instanceof ValueMinInclusiveConstraint) {
                // TODO
            }  */

/*             // String-based Constraint Components
            else if (c instanceof StrLanguageIn) {
                // langs ist nicht exposed, muss man aus dem string hacken
            }  */
            
            else if (c instanceof StrMinLengthConstraint) {
                var minLen = ((StrMinLengthConstraint) c).getMinLength();
                node.addBindingFilter(
                    (s) -> s.filter(
                        (b) -> {
                            var val = b.get("p").getLiteralValue();
                            if (b.get("p").getLiteralValue() instanceof String) {
                                return ((String)val).length() >= minLen;
                            } else {
                                return false;
                            }
                        }
                    )
                );
            } 
            
            else if (c instanceof StrMaxLengthConstraint) {
                var maxLen = ((StrMaxLengthConstraint) c).getMaxLength();
                node.addBindingFilter(
                    (s) -> s.filter(
                        (b) -> {
                            var val = b.get("p").getLiteralValue();
                            if (b.get("p").getLiteralValue() instanceof String) {
                                return ((String)val).length() >= maxLen;
                            } else {
                                return false;
                            }
                        }
                    )
                );
            } 
            
            else if (c instanceof PatternConstraint) {
                var patternString = ((PatternConstraint) c).getPattern();
                Pattern pattern = Pattern.compile(patternString);            
                node.addBindingFilter(
                    (s) -> s.filter(
                        (b) -> {
                            if (b.get("p").getLiteralValue() instanceof String) {
                                return pattern.matcher((String) b.get("p").getLiteralValue()).matches();
                            } else {
                                return false;
                            }
                        }
                    ));
            } 
            
/*             else if (c instanceof UniqueLangConstraint) {
                // TODO
            } */
            
/*             // Property-Pair constraint components
            else if (c instanceof EqualsConstraint) {
                // TODO
            }  */
            
/*             else if (c instanceof DisjointConstraint) {
                // TODO
            } */

            // Other Constraints
/*             else if (c instanceof ClosedConstraint) {
                // TODO
            }   */
            
            else if (c instanceof HasValueConstraint) {
                var expectedVal = ((HasValueConstraint) c).getValue();
                node.addBindingFilter(
                    (s) -> s.filter(
                        (b) -> {
                            var val = b.get("p");
                            return val.equals(expectedVal);
                        }
                    )
                );
            }  
            
            else if (c instanceof InConstraint) {
                var list = ((InConstraint) c).getValues();
                node.addBindingFilter(
                    (s) -> s.filter(
                        (b) -> {
                            var val = b.get("p");
                            return list.contains(val);
                        }
                    )
                );
            }

            else if (c instanceof MinCount) {
                // Do nothing
            }
            else if (c instanceof MaxCount) {
                // Do nothing
            } else if (c instanceof ShNot) {

            }
            

            else {
                // throw new ValidationException("Unsupported Constraint: "+c.toString());
            }
            
        };
    }

    
    /* Helper Functions */
    public static String wrap(String toWrap) {
        return "<" + toWrap + ">";
    }

    public LogicNode addChildrenToNode(LogicNode node, ConstraintOpN c, Shape s) {
        c.getOthers()
                .stream()
                .map((childShape) -> shapeToTree(childShape))
                .forEach((subtree) -> node.addChild(subtree));
        return node;
    }

    private Optional<Constraint> get(HashSet<Constraint> constraints, Class klasse) {
        return constraints
                .stream()
                .filter((c) -> klasse.isInstance(c))
                .findFirst();
    }

      /**
     * Returns level-amount tabs
     * @param level the amount of tabs
     * @return intendation as a string
      */
    private String indent(int level, String toIndent) {
        var s = " ";
        for (int i = 0; i<=level; i++) {
            s += "       ";
        }
        return s + toIndent;
    }

   

}
