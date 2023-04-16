package ifis;

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;

/**
 * Hello world!
 */
public final class App {
    private App() {
    }

    /**
     * Says hello to the world.
     * @param args The arguments of the program.
     */
    public static void main(String[] args) throws Exception{
    
        // Read File        
        Graph shapesGraph = RDFDataMgr.loadGraph("./shapes/implicitAnd.ttl");
        Shapes shapes = Shapes.parse(shapesGraph);
        
        // Create Query Execution Builder on given SPARQL Endpoint, that can run the queries
        QueryExecHTTPBuilder bob = QueryExecHTTPBuilder.service("http://127.0.0.1:8080/sparql");

        // Set up Validation
        Validation validator = new Validation(shapes, bob);
        
        // Execute Validation on Shapes
        validator.exec();

    }


    

}


