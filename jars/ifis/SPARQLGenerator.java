package ifis;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.shacl.engine.Target;


public class SPARQLGenerator {
    

    private int variableIndex = 0;

    public String getNewVariable(){
        variableIndex++;
        return "x" + String.valueOf(variableIndex);
    }

    public Query newQuery() {
        return new Query(this);
    }

    public Query generateTargetQuery(Target target) {

        var query = this.newQuery();
        var tt = target.getTargetType();

        switch (tt) {
            case targetClass:
                query.addTriple("?x", "a", wrap(target.getObject().toString()));
                break;
            
            case targetSubjectsOf:
                query.addTriple("?x", wrap(target.getObject().toString()), "?o");
                break;
            
            case targetObjectsOf:
                query.addTriple("?s", wrap(target.getObject().toString()), "?o");
                break;

            case implicitClass:
                // TODO add implicitClass target
            case targetNode:
                // TODO add targetNode target
            case targetExtension:
                // TODO add targetExtension target
                break;
                        
            //  ? Was ist mit den SPARQL Targets?
            
        };
        return query;
    }

    public static String wrap(String toWrap) {
        return "<" + toWrap + ">";
    }

    class Query {
        public final List<String> triples;
        public final List<String> parts;
        public final List<String> filters;


        private final ArrayList<Query> subQueries;
        
        /**
         * @param sg SPARQLGenerator that gets assigned when getting a new Query from it
         */
        public Query(SPARQLGenerator sg) {
            triples = new ArrayList<String>();
            filters = new ArrayList<String>();
            parts = new ArrayList<String>();

            subQueries = new ArrayList<>();
        }

        public void addPart(String part) {
            parts.add(part);
        }

        public Query addTriple(String sub, String pred, String object) {
            triples.add(sub + " " + pred + " " + object);
            return this;
        }

        public Query addFilter(String var, String filter) {
            // TODO add filter string construction
            return this;
        }
        

        public String getSparqlString(String vars) {
            var sparql = "SELECT DISTINCT "+vars+" WHERE {";
                
            // Add preformatted 'parts'
            sparql += parts
                        .stream()
                        .reduce("", (acc, part)-> acc + "\n" + part);

            // Add triples
            sparql += triples
                .stream()
                .reduce("", (acc, triple) -> acc + "\n" + triple + ".");


            // Add subqueries
            sparql += subQueries
                        .stream()
                        .map((subQuery) -> "\n{\n" + subQuery.getSparqlString("*") + "\n}")
                        .reduce("", (subQuery, acc) -> acc + "\n" + subQuery);

            // TODO add filter string construction
            sparql += "\n}";
            
            
            return sparql;
        }


        public void addSubQuery(Query query) {
            this.subQueries.add(query);
        }



        public List<String> getTriples() {
            return triples;
        }



        public List<String> getParts() {
            return parts;
        }



        public List<String> getFilters() {
            return filters;
        }



        public ArrayList<Query> getSubQueries() {
            return subQueries;
        }
               
    }
}
