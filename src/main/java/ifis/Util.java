package ifis;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.jena.shacl.engine.Target;

import ifis.logic.PShapeNode;
import ifis.logic.SHACLNode;

public final class Util {

    public static String triple(String s, String p, String o) {
        return s + " " + p + " " + o + " ."; 
    }
    
    public static String generateTargetString(Target target, String focusVar) {

        return switch (target.getTargetType()) {
            case targetClass:
                yield triple("?"+focusVar, "a", wrap(target.getObject().toString()));
            
            case targetSubjectsOf:
                yield triple("?"+focusVar, wrap(target.getObject().toString()), "?ignore");

            case targetObjectsOf:
                yield triple("?ignore", wrap(target.getObject().toString()), "?"+focusVar);

            case implicitClass:
                yield triple("?"+focusVar, "a", wrap(target.getObject().toString()));

            case targetNode:
                // TODO add targetNode target
            case targetExtension:
                // TODO add targetExtension target
                throw new NotImplementedException("Targettype '"+target.getTargetType().toString()+"' is not supported yet");
            
        };
    }


    public static List<String> genVarHirarchy(SHACLNode node, Validation val) {
        List<String> varHirachyIM = node.getLineage()
            .stream()
            .filter(ancestor -> ancestor instanceof PShapeNode)
            .map(ancestor->ancestor.getBindingVar())
            .toList();

        var varHirachy = new ArrayList<>(varHirachyIM);
        // Add the base target var to the hirachy as top level
        varHirachy.add(val.getFocusVar());

        return varHirachy;
    }

    
    public static String drawTreeNode(SHACLNode node, ValidationResult res, int indentlevel) {
        var valid = res.getValidatingNodes().contains(node);
        var s = indent(indentlevel, "┗━");
        s += valid ? "✅━" : "❌━";
        s += node.getReportString();
        s += node.getChildren()
                .stream()
                .map((childNode) -> drawTreeNode(childNode, res, indentlevel + 1))
                .reduce("", (acc, str) -> acc + "\n" + str);

        return s;
    }

    /**
     * Just concatenates the triple into a sparql statement ended with a dot.
     * TODO is it a statement?
     * 
     * @param sub
     * @param pred
     * @param obj
     * @return
     */
    public static String asTriple(String sub, String pred, String obj) {
        return sub + " " + pred + " " + obj + ".";
    }

    /**
     * Wraps the string with '<' and '>'5
     * 
     * @param toWrap
     * @return
     */
    public static String wrap(String toWrap) {
        return "<" + toWrap + ">";
    }



    /**
     * Returns level-amount tabs
     * 
     * @param level the amount of tabs
     * @return intendation as a string
     */
    public static String indent(int level, String toIndent) {
        var s = " ";
        for (int i = 0; i < level; i++) {
            s += "   ";
        }
        return s + toIndent;
    }
}
