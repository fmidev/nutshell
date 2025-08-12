package nutshell9;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/** Object manipulation utilities, including configuration read.
 *
 *   See also: @{@link MapUtils}
 */
public class Manip {

    /** Storage for pairs of type "key=value" and "key[index]=value".
     *
     *  Designed for importing assignment strings to Objects; serves as an in-between container.
     *
     */
    static
    public class Entry {
        public String key = "";
        public String index = "";
        public Object value = null;

        @Override
        public String toString() {
            if (index==null || index.isEmpty()){
                return key+'='+value;
            }
            else {
                return key+'['+index+']'+'='+value;
            }
        }
    }

    // protected static final Pattern linePattern = Pattern.compile("^\\s*(\\w+)\\s*=[ \t\"']*([^\"']*)[ \t\"']*$");

	/*
    static
    public <T> void readConfig(Path path, T target) throws IOException {
        readConfig(path.toFile(), target);
    }
	 */

    static
    public void assignToMap(String key, Object value,  Map<String,Object> target)  {
        target.put(key, value);
    }

    static
    public void assignToObject(Map<String,Object> source, Object target) throws NoSuchFieldException, IllegalAccessException {
        for (Map.Entry<String,Object> entry: source.entrySet()) {
            assignToObject( entry.getValue(), target, entry.getKey());
        }
    }

    /**
     *
     * @param source
     * @param target
     */
    static
    public void assignToObjectLenient(Map<String,Object> source, Object target)  {
    	assignToObjectLenient(source, target, false);
    }
    
    /** Assign values to object and
     *
     * @param source
     * @param target
     */
    static
    public void assignToObjectLenient(Map<String,Object> source, Object target, boolean reassign)  {

        Map<String,String> errorMap = new HashMap<>();
        for (Map.Entry<String,Object> entry: source.entrySet()) {
            String key   = entry.getKey();
            Object value = entry.getValue();
            try {
            	Object result = assignToObject(value, target, key);
            	if (reassign) {
            		source.put(key, result);
            	}
            }
            catch (NoSuchFieldException e) {
                //e.printStackTrace();
            }
            catch (Exception e){
                errorMap.put(key, e.getClass().getSimpleName());
            }
            /*
            catch (NumberFormatException)
            catch (NoSuchFieldException e) {

                //e.printStackTrace();
            } catch (IllegalAccessException e) {
                // e.printStackTrace();
            }
            */
        }
        if (!errorMap.isEmpty()){
            throw new RuntimeException("Errors: " + errorMap.toString());
        }
    }

    static
    public Object assignToObject(Object value, Object target, String key) throws NoSuchFieldException, IllegalAccessException {
        return assignToObject(value, target, key, null);
    }


    /** Assign value to target.key[index] .
     *
     * @param value - value to be assigned
     * @param target - object
     * @param key - name of a member of target, assuming that target.key exists
     * @param index – if not null, assumes that target.key is a Map; value will be assigned to target.key[index]
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    static
    public Object assignToObject(Object value, Object target, String key, String index) throws NoSuchFieldException, IllegalAccessException {

        Field field = target.getClass().getField(key);
        if (index != null){
            target = field.get(target);
            if (target instanceof Map){
                Map<String,Object> map = (Map)target;
                map.put(index, value);
                return value;
            }
            else {
                throw new NoSuchFieldException(String.format("%s is not a Map", key));
            }
            // return null;
        }

        return assignToObject(value, target, field, field.getType());
    }

    /** Assignment with explicit class definition
     *
     *  Designed for generic classes to keep the members class-specific.
     *
     * @param value
     * @param field
     * @param target
     * @param cls – explicit class parameter (when field.getType() would give a superclass, say Object)
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    static
    public Object assignToObject(Object value, Object target, Field field,  Class cls) throws IllegalAccessException {

        if (value == null) {
            field.set(target, null);
            return null;
        }

        // Of actual type
        Object finalValue = null;
        
        // System.err.printf(" Field=%s [%s] <- Value %s [%s] %n",
        //         field.getName(), cls.getName(), value.toString(), value.getClass().getName());

        String s = value.toString(); // value not null here
        
        if (cls.isPrimitive() && s.isEmpty()){ // Don't assign "" to number/boolean. Basically, an exception?
            return null;
        }

        if (cls.equals(int.class) || (cls.equals(Integer.class))){
            field.set(target,  finalValue = Integer.parseInt(s));
        }
        else if (cls.equals(long.class) || (cls.equals(Long.class))){
            field.set(target,  finalValue = Integer.parseInt(s));
        }
        else if (cls.equals(float.class) || (cls.equals(Float.class))){
            field.set(target,  finalValue = Float.parseFloat(s));
        }
        else if (cls.equals(double.class) || (cls.equals(Double.class))){
            field.set(target,  finalValue = Double.parseDouble(s));
        }
        else if (cls.equals(char.class) || (cls.equals(Character.class))){
            field.set(target,  finalValue = s.charAt(0));
        }
        else if (cls.equals(byte.class) || (cls.equals(Byte.class))){
            field.set(target,  finalValue = Byte.parseByte(s));
        }
        else if (cls.equals(boolean.class) || cls.equals(Boolean.class)){
            field.set(target,  finalValue = Boolean.getBoolean(s));
        }
        else if (cls.equals(String.class)){
            field.set(target, finalValue = s);
        }
        else if (cls.equals(Path.class)){
            field.set(target,  finalValue = Paths.get(s));
        }
        else if (cls.isAssignableFrom(Flags.class)){
            Flags flags = (Flags) field.get(target);
            try {
                //String v = value.toString();
                //if (v.isEmpty())
                //    flags.set(
                flags.set(value.toString()); // how should empty string be handled?
                finalValue = flags;
            } catch (NoSuchFieldException e) {
                throw new IllegalAccessException(e.getMessage()); // kludge
            }
        }
        else if (cls.isEnum()){
            //System.err.printf(" Enum [%s] '%s' %n", Enum.valueOf(cls, s), s);
            field.set(target,  finalValue = Enum.valueOf(cls, s));
        }
        else {
            if (cls.isPrimitive()){
                throw new IllegalAccessException("Not yet implemented: " + cls.getName());
            }
            field.set(target, value); // obj
            finalValue = value;
        }
        return finalValue;
    }

    static public String toString(Object obj){
        return toString(obj, ',');
    }

    static public String toString(Object obj, char separator){

        StringBuilder sb = new StringBuilder();
        for (Field field: obj.getClass().getFields()){
            sb.append(field.getName()).append('=');
            try {
                sb.append(field.get(obj));
                sb.append(" [").append(field.getType().getName()).append("] ");
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            // builder.append(field.isAccessible());
            sb.append(separator);
        }
        return sb.toString();
    }

    /** For local demo only.
     *
     */
    static
    private class Example {

        public enum Values {
          FIRST,
          SECOND
        };

        public Object obj;
        public String ss;
        public Integer ii;
        public float f;
        public double d;
        public int i;
        public long l;
        public boolean b;
        public byte B;
        public HashMap<String,Object> map = new HashMap<>();
        public Values e;
        public Flags flags = new Flags(Values.class);

        @Override
        public String toString() {
            return Manip.toString(this, '\n');
        }
    }

    static
    public void main(String[] args) {

        Example example = new Example();
        Map<String,Object> map = new HashMap<>();

        if (args.length == 0){
            System.out.println("Usage: \n");
            System.out.println("java -cp out/production/nutshell nutshell.Manip map[eka]=toka e=SECOND flags=FIRST,SECOND");
            System.out.println(Manip.toString(example, ' '));
            System.out.println("CONFFILE=foo.cnf  f=0.1234\n");
            System.exit(0);
        }

        System.out.println("Initial values: ");
        System.out.println(example);
        System.out.println();


        Manip.Entry entry = new Entry();
        for (String arg: args) {

            try {
                Config.parseConfigLine(arg, entry);
                System.out.println(String.format("Read entry: %s", entry));
                //System.out.println();
            } catch (ParseException e) {
                //e.printStackTrace();
                System.out.println(e);
                continue;
            }


            if (entry.key.equals("CONFFILE")){
                Path path = Paths.get(entry.value.toString());
                try {
                    Config.readConfig(path.toFile(), example);
                    Config.readConfig(path.toFile(), map);
                    System.out.println(map);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else {
                try {
                    assignToObject(entry.value, example, entry.key, entry.index);
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println();

        System.out.println("Values after assigning entries directly: ");
        System.out.println(example);

        if (!map.isEmpty()){ // Conf file has been read

            System.out.print("Map contents: ");
            System.out.println(map);

            Example example2 = new Example();

            try {
                assignToObject(map, example2);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            System.out.println("Values after assigning map: ");
            System.out.println(example2);

        }

    }
}
