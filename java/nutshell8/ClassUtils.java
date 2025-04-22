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

    static
    public  class Test<T>  {

        public T value;


        public Test(T initValue){
            value = initValue;
        }

        @Override
        public String toString() {
            if (value == null)
                return "value=null";
            else
                return String.format("value=%s (%s)", value, value.getClass().getName());
        }
    }

    public enum Format {
        TEXT,
        HTML;
    }

    public static void main(String[] args) {


        Test<String> str = new Test<>("abcd");
        System.out.println(str);

        Test<Format> e = new Test<>(Format.HTML);

        System.out.println(e);
        System.out.println(Manip.toString(e));
        System.out.printf("%s [%s] %n", e.value, e.value.getClass().getName());

        // System.out.printf("%s [%s] %n", e.value, e.value.getClass().getName());

        Test<?> e2 = new Test<Format>(Format.TEXT){
        };

        System.out.println(e2);
        System.out.println(Manip.toString(e2));
        System.out.printf("%s [%s] %n", e2.value, e2.value.getClass().getName());

    }

}