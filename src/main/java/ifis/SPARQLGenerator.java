package ifis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.jena.shacl.engine.Target;


public class SPARQLGenerator {
    
    private List<String> alphabet = Arrays.asList("abcdefghijklmnopqrstuvwyz".split(""));


    public SPARQLGenerator() {
    }

    private int variableIndex = 0;

    public String getNewVariable(){
        String varname = getVarWithIndex(variableIndex);
        variableIndex++;
        return varname;
    }

    /** 
     * This is seperated from getNewVariable() since we use this in getSuccessorVar()
     */
    private String getVarWithIndex(int varIndex) {
        var letter = alphabet.get(variableIndex % alphabet.size());
        var dedupIndex = variableIndex / alphabet.size();

        String varname = dedupIndex == 0 ? letter : letter + String.valueOf(dedupIndex);
        return varname;
    }

    public String getSuccessorVar(String variable) {
        if (variable.length() == 1) {
            return getVarWithIndex(alphabet.indexOf(variable) + 1);
        }

        // Seperate letter and number
        String letter = String.valueOf(variable.charAt(0));
        var number = Integer.valueOf(variable.substring(1));

        // Calculate originating variableIndex
        var oIndex = alphabet.indexOf(letter) + (number * alphabet.size() + 1);

        // return the successor variable
        return getVarWithIndex(oIndex + 1);
        
    }

    public Query newQuery() {
        return new Query(this);
    }

    public Query generateTargetQuery(Target target, String focusVar) {

        var query = this.newQuery();
        var tt = target.getTargetType();

        switch (tt) {
            case targetClass:
                query.addTriple("?"+focusVar, "a", wrap(target.getObject().toString()));
                break;
            
            case targetSubjectsOf:
                query.addTriple("?"+focusVar, wrap(target.getObject().toString()), "?o");
                break;
            
            case targetObjectsOf:
                query.addTriple("?s", wrap(target.getObject().toString()), "?"+focusVar);
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
        private final List<String> triples;
        private final List<String> parts;
        private final List<String> filters;
        private final List<String> havings;


        private final ArrayList<Query> subQueries;
        
        /**
         * @param sg SPARQLGenerator that gets assigned when getting a new Query from it
         */
        public Query(SPARQLGenerator sg) {
            triples     = new ArrayList<>();
            filters     = new ArrayList<>();
            parts       = new ArrayList<>();
            havings     = new ArrayList<>();
            subQueries  = new ArrayList<>();
        }

        /* Parts are preformatted Strings that just get added into the query*/
        public void addPart(String part) {
            parts.add(part);
        }

        public Query addTriple(String sub, String pred, String object) {
            triples.add(sub + " " + pred + " " + object);
            return this;
        }

        public Query addFilter(String filter) {
            this.filters.add(filter);
            return this;
        }

        public Query addHaving(String having) {
            havings.add(having);
            return this;
        }
        

        public String getSparqlString(String vars) {
            var sparql = "SELECT DISTINCT "+ vars +" WHERE {";
                
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

            // add filter
            sparql += filters
                        .stream()
                        .map((filter) -> "FILTER("+filter+").")
                        .reduce("", (subQuery, acc) -> acc + "\n" + subQuery);
            
            

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
