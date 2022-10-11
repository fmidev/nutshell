package nutshell;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ClassUtils {

    /**
     * Get members that have names in all caps.
     *
     * @param c   - class to be investigated
     * @param <E>
     * @return - a map of names and values.
     */
    static public <E> Map<String, E> getConstants(Class c) {

        Map<String, E> map = new HashMap<>();

        for (Field field : c.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                String name = field.getName();
                if (name.equals(name.toUpperCase())) {
                    /*
                    if (field.isEnumConstant()){
                        map.put(name, Enum.valueOf(c, name).ordinal());
                    }*/
                    try {
                        map.put(name, (E) field.get(null));
                    } catch (Exception e) {
                        //e.printStackTrace();
                    }
                }
            }
        }

        return map;
    }

    static public Set<String> getConstantKeys(Class c) {
        return getConstants(c).keySet();
    }

}