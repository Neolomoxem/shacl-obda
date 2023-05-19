package ifis;

import org.apache.jena.ext.com.google.common.collect.Multimap;
import org.apache.jena.graph.Node;
import org.apache.jena.shacl.engine.Parameter;
import org.apache.jena.shacl.engine.constraint.ConstraintComponentSPARQL;
import org.apache.jena.shacl.engine.constraint.SparqlComponent;

/**
 * ExposedConstraintComponentSPARQL
 */
public class ExposedConstraintComponentSPARQL extends ConstraintComponentSPARQL {

    public ExposedConstraintComponentSPARQL(SparqlComponent sparqlConstraintComponent,
            Multimap<Parameter, Node> parameterMap) {
        super(sparqlConstraintComponent, parameterMap);
    }

    public SparqlComponent getSparqlComponent() {
        return this.sparqlConstraintComponent;
    }

    
    
}