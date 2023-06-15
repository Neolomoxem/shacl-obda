package ifis.logic;

import java.util.HashSet;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

import ifis.ValidationFilter;

public class PropertyNode extends LogicNode {
    private HashSet<Node> validatingAtoms;
    // Filters to be applied in validation, these are different from sparql filters!
    private final HashSet<ValidationFilter> bindingFilters;
    // If the cardinality is only constrained in MAX then the validatingAtoms
    // contain all INvalidating atoms.
    private boolean inverted;
    public LogicNode tree;

    public boolean isInverted() {
        return inverted;
    }


    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }


    public PropertyNode(Shape sourceShape) {
        super(sourceShape);
        validatingAtoms = new HashSet<>();
        bindingFilters = new HashSet<>();
    }

    
    @Override
    public boolean validates(Node atom) {
        if (tree != null) return tree.validates(atom);
        return validatingAtoms.contains(atom);
    }


    public void setValidatingAtoms(HashSet<Node> validatingAtoms) {
        this.validatingAtoms = validatingAtoms;
    }


    @Override
    public boolean validatesRes(Node atom, Set<LogicNode> valNodes) {
        // See field dec
        if (tree != null) {
            if (!inverted) {
                if (tree.validates(atom)) {
                    validatesRes(atom, valNodes);
                    valNodes.add(this);
                    return true;
                } else {
                    return false;
                }
            } else {
                if (!tree.validates(atom)) {
                    validatesRes(atom, valNodes);
                    valNodes.add(this);
                    return true;
                } else {
                    return false;
                }
            }
        }
        if (!inverted) {
            if (validates(atom)) {
                valNodes.add(this);
                return true;
            } else {
                return false;
            }
        } else {
            if (!validates(atom)) {
                valNodes.add(this);
                return true;
            } else {
                return false;
            }
        }
        
        
    }

    public void addBindingFilter(ValidationFilter f) {
        bindingFilters.add(f);
    }



    @Override
    public String getReportString() {
        
        return getNormConstraints()
                .stream()
                .map((c) -> c.toString())
                .reduce("⬤ " + normPath.toString() + "> ", (acc, str) -> acc + str + ", ");
        
    }


    public HashSet<Node> getValidatingAtoms() {
        return validatingAtoms;
    }


    public HashSet<ValidationFilter> getBindingFilters() {
        return bindingFilters;
    }

    

    
    
    
    
    
}
