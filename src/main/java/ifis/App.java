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
    
        String filename = args[0];
        String serviceURL = args[1];
        // String filename = "shapes/test.ttl";

        // For now the only way to supply the data.

        // Read File        
        Graph shapesGraph = RDFDataMgr.loadGraph(filename);
        Shapes shapes = Shapes.parse(shapesGraph);
        
        // Create Query Execution Builder on given SPARQL Endpoint, that can run the queries
        QueryExecHTTPBuilder bob = QueryExecHTTPBuilder.service(serviceURL);
        // bob.acceptHeader("application/sparql-results+xml");

        // Set up Validation        

        for (var shape : shapes) {


            var val = new Validation(shape, bob);
    
            // Execute Validation on Shapes
            var before = System.nanoTime();
            val.exec();
            var after = System.nanoTime();
            System.out.println("Validation took: "+ String.valueOf(after-before) +" nanoseconds");

            val.saveReport("report.log");
        }

    }


    

}


