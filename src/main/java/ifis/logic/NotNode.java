package ifis.logic;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.parser.Shape;

import ifis.exception.ValidationException;

public class NotNode extends SHACLNode {

    private boolean inverted = false;

    public NotNode(Shape shape) {
        super(shape);
    }

    @Override
    protected void constructFromChildren() {
        if (_children.size() != 1)
            throw new ValidationException("More than one child under a NOT");
        var isUnderPshape = this.getPShape() != null;

        // The NOT needs the set of focus nodes to calculate negation. The source of these differs...
        validFocus = isUnderPshape ? getValidFocusWithPropertyMap() : getValidFocusWithTargets();
    }

    private Set<List<Node>> getValidFocusWithTargets() {
        return null;
    }

    private Set<List<Node>> getValidFocusWithPropertyMap() {
        var childFocus = _children.get(0).validFocus;

        return getPShape()
                .getCountMap()
                .entrySet()
                .stream()
                .parallel()
                .filter(entry -> {
                    var focus = entry.getKey();
                    var count = entry.getValue();

                    // Focus nodes which have no value nodes are superior
                    if (count == 0)
                        return true;

                    return !childFocus.contains(focus);
                })
                .map(entry -> entry.getKey())
                .collect(Collectors.toSet());
    }

    @Override
    public boolean validates(Node atom) {
        /*
         * If child validates the node
         * then the not validates not
         */

        return !_children.get(0).validates(atom);

    }

    @Override
    public boolean validatesRes(Node atom, Set<SHACLNode> valNodes) {
        // If valid Targets arent extracted from validBindings, do it now and memoize
        if (validTargets == null)
            extractValidTargets();
        // If this Node contains, add it to valNodes

        if (inverted) {
            if (!validTargets.contains(atom))
                valNodes.add(this);
            // Check the children
            for (var child : _children)
                child.validatesRes(atom, valNodes);
            return !validTargets.contains(atom);

        } else {
            if (validTargets.contains(atom))
                valNodes.add(this);
            // Check the children
            for (var child : _children)
                child.validatesRes(atom, valNodes);
            return validTargets.contains(atom);

        }

    }

    @Override
    public String getReportString() {
        return "N͟O͟T͟";
    }

}
