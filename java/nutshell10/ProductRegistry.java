package nutshell10;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;


/** Container for product Generators
 *
 *  Product-independent; does not store product ID.
 */
public class ProductRegistry { // consider derived classes, like DynamicProductParameters


    static
    public class Entry {

        public final List<String> inputs = new LinkedList<>();

        public ProductServer.Generator generator = null;


    }


}
