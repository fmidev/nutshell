package nutshell;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class ClassUtils {

    /** Get members that have names in all caps.
     *
     * @param c - class to be investigated
     * @param <E>
     * @return - a map of names and values.
     */
    static public <E> Map<String, E> getConstants(Class c){

        Map<String, E> map = new HashMap<>();

        for (Field field : c.getFields()) {
            String name = field.getName();
            if (name.equals(name.toUpperCase()))
                try {
                    map.put(name, (E)field.get(null));
                } catch (Exception e) {
            }
        }
        return map;
    }
}
