package ifis;

import java.util.stream.Stream;

import org.apache.jena.sparql.engine.binding.Binding;

public interface ValidationFilter {
   public Stream<Binding> apply(Stream<Binding> s); 
}
