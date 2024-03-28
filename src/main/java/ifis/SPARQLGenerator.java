package ifis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ifis.SPARQLGenerator.Query;


public class SPARQLGenerator {

    private List<String> alphabet = Arrays.asList("abcdefghijklmnopqrstuvwyz".split(""));

    public SPARQLGenerator() {
    }

    private int variableIndex = 0;

    public String getNewVariable() {
        String varname = getVarWithIndex(variableIndex);
        variableIndex++;
        return varname;
    }

    /**
     * This is seperated from getNewVariable() since we use this in
     * getSuccessorVar()
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

    public static String wrap(String toWrap) {
        return "<" + toWrap + ">";
    }

    public class Query {
        private final List<String> triples;
        private final List<String> parts;
        private final List<String> outerParts;
        private final List<String> filters;
        private final List<String> havings;
        private final List<String> pathVars;
        private final List<String> optionalParts;

        public void addOptionalPart(String part) {
            optionalParts.add(part);
        }

        public List<String> getOptionalParts() {
            return optionalParts;
        }

        public List<String> getPathVars() {
            return pathVars;
        }

        public void pushPathVar(String var) {
            pathVars.add(var);
        }

        private String projection = "*";

        public String getProjection() {
            return projection;
        }

        public void setProjection(String projection) {
            this.projection = projection;
        }

        private String inmostVar;

        public String getInmostVar() {
            return inmostVar;
        }

        public void setInmostVar(String inmostVar) {
            this.inmostVar = inmostVar;
        }

        public void addOuterPart(String outerPart) {
            this.outerParts.add(outerPart);
        }

        private final ArrayList<Query> subQueries;

        /**
         * @param sg SPARQLGenerator that gets assigned when getting a new Query from it
         */
        public Query(SPARQLGenerator sg) {
            triples = new ArrayList<>();
            filters = new ArrayList<>();
            parts = new ArrayList<>();
            havings = new ArrayList<>();
            outerParts = new ArrayList<>();
            subQueries = new ArrayList<>();
            pathVars = new ArrayList<>();
            optionalParts = new ArrayList<>();
        }

        /* Parts are preformatted Strings that just get added into the query */
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

        public Query setProjection(List<String> vars) {
            return this;
        }

        public String getSparqlString() {
            // var sparql = "SELECT DISTINCT "+ vars +" WHERE {";
            var sparql = STR."SELECT DISTINCT \{projection} WHERE {";

            // Add triples
            sparql += triples
                    .stream()
                    .reduce("", (acc, triple) -> acc + "\n\t" + triple + ".\n");

            // Add preformatted 'parts'
            sparql += parts
                    .stream()
                    .reduce("", (acc, part) -> acc + "\n\t" + part);

            /*
             * // Add subqueries
             * sparql += subQueries
             * .stream()
             * .map((subQuery) -> "\n{\n" + subQuery.getSparqlString() + "\n}")
             * .reduce("", (subQuery, acc) -> acc + "\n" + subQuery);
             */

            if (optionalParts.size() > 0) {
                sparql += "\n\tOPTIONAL {";
            }

            // Optional Parts
            sparql += optionalParts
                    .stream()
                    .reduce("", (acc, part) -> acc + "\n\t\t" + part);

            // add filter
            sparql += filters
                    .stream()
                    .map((filter) -> "\n\t\tFILTER(" + filter + ").")
                    .reduce("", (subQuery, acc) -> acc + "\n" + subQuery);

            if (optionalParts.size() > 0) {
                sparql += "\n\t}";
            }

            sparql += "\n} ";

            sparql += outerParts
                    .stream()
                    .reduce("", (acc, part) -> acc + "\n" + part);

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

        public List<String> getOuterParts() {
            return outerParts;
        }

        public List<String> getHavings() {
            return havings;
        }

        String getFocusProjection() {
            String focusProjection = "";
            for (var v:getPathVars().subList(0, getPathVars().size()-1)){
                focusProjection += "?" + v + " ";
            }
            return focusProjection;
        }

    }
}
