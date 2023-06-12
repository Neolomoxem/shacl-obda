package ifis;

import java.util.Set;

import org.apache.jena.graph.Node;

import ifis.logic.LogicNode;

public class ValidationResult {
    private final Set<LogicNode> validatingNodes;
    private final boolean valid;
    private final Node atom;
    


    public ValidationResult(Node atom, Set<LogicNode> validatingNodes, boolean valid) {
        this.validatingNodes = validatingNodes;
        this.valid = valid;
        this.atom = atom;
    }

    public Set<LogicNode> getValidatingNodes() {
        return validatingNodes;
    }

    public boolean isValid() {
        return valid;
    }

    public Node getAtom() {
        return atom;
    }
    


    
}
