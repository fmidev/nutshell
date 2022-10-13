package nutshell;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Object manipulation utilities, including configuration read.
 *
 *   See also: @{@link MapUtils}
 */
public class Manip {

    static
    public class Entry {
        public String key = "";
        public String index = "";
        public Object value = null;
    }

    protected static final Pattern commentPattern = Pattern.compile("^(.*)[%#](.*)$");

    /**
     *  Accepts:
     * 	VARIABLE_NAME=VALUE
     * 	VARIABLE_NAME='VALUE'
     * 	VARIABLE_NAME="VALUE"
     *
     *  also:
     *  VARIABLE[KEY]=VALUE
     *  VARIABLE[KEY2]=VALUE2
     *
     *
     */
    protected static final Pattern linePattern = Pattern.compile("^\\s*(\\w+)(\\[(\\w+)\\])?\\s*=[ \t\"']*([^\"']*)[ \t\"']*$");
    // protected static final Pattern linePattern = Pattern.compile("^\\s*(\\w+)\\s*=[ \t\"']*([^\"']*)[ \t\"']*$");

	/*
    static
    public <T> void readConfig(Path path, T target) throws IOException {
        readConfig(path.toFile(), target);
    }
	 */

    /**
     *
     * @param file –
     * @param target – onject in which values are assigned
     * @param <T> – Object or Map<String,Object>
     * @throws Exception – Either {@link IOException} or {@link ParseException}
     */
    static
    public <T> void readConfig(File file, T target) throws Exception {
        BufferedReader input = new BufferedReader(new FileReader(file));
		readConfig(input, target);
		input.close();
    }

    /**
     *
     * @param input
     * @param target
     * @param <T> – Object or Map<String,Object>
     * @throws IOException
     */
    static
    public <T> void readConfig(BufferedReader input, T target) throws Exception {

        // System.err.printf(" Target class=%s %n", target.getClass().getName());
        // System.err.printf(" Map? %b %n", Map.class.isInstance(target));
        // System.err.printf(" Map? %b %n", );
        final boolean MAP = (target instanceof Map);

        Exception exception = null;
        String line;

        Manip.Entry entry = new Manip.Entry();

        while ((line = input.readLine()) != null){

			line = line.trim();

			// Strip (trailing) comments
			Matcher m = commentPattern.matcher(line);
			if (m.matches())
				line = m.group(1);

			if (line.isEmpty())
			    continue;
            // System.err.printf(" LINE: '%s'%n", line);
            try {
                parse(line, entry);
            }
            catch (Exception e){
                exception = e;
                continue;
            }

            try {
                 if (MAP) {
                     ((Map<String, Object>) target).put(entry.key, entry.value);
                 }
                 else {
                     // if entry.index != null, assume target has member "key" which is a map.
                    assignToObject(entry.key, entry.index, entry.value, target);
                 }
            } catch (NoSuchFieldException e) {
                exception = e;
            } catch (IllegalAccessException e) {
                exception = e;
                //e.printStackTrace();
            }

            if (exception != null)
                throw exception;

        }
    }

    /** Extracts KEY=VALUE from the line.
     *
     * @param line
     * @param entry
     * @return
     * @throws ParseException
     */
    static
    public void parse(String line, Manip.Entry entry) throws ParseException {

        Matcher matcher = linePattern.matcher(line);

        if (matcher.matches()) {
            entry.key   = trim(matcher.group(1));
            entry.index = trim(matcher.group(3));
            entry.value = trim(matcher.group(4));
            //return true;
        }
        else {
            //matcher.
            throw new ParseException(String.format("line='%s' regex='%s'", line, linePattern), 0);

        }

        // return false;
        // Map.Entry<String,Integer> entry =
        // new AbstractMap<String,Integer>().SimpleEntry<String, Integer>("exmpleString", 42);
    }

    private static String trim(String s){
        if (s == null)
            return null; // or "" ?
        else
            return s.trim();
    }

    static
    public void assignToMap(String key, Object value,  Map<String,Object> target)  {
        target.put(key, value);
    }

    static
    public void assignToObject(Map<String,Object> source, Object target) throws NoSuchFieldException, IllegalAccessException {
        for (Map.Entry<String,Object> entry: source.entrySet()) {
            assignToObject(entry.getKey(), entry.getValue(), target);
        }
    }

    static
    public void assignToObjectLenient(Map<String,Object> source, Object target)  {
        for (Map.Entry<String,Object> entry: source.entrySet()) {
            try {
                assignToObject(entry.getKey(), entry.getValue(), target);
            } catch (NoSuchFieldException e) {
                //e.printStackTrace();
            } catch (IllegalAccessException e) {
                // e.printStackTrace();
            }
        }
    }

    static
    public void assignToObject(String key, Object value, Object target) throws NoSuchFieldException, IllegalAccessException {
        assignToObject(key, null, value, target);
    }


    static
    public void assignToObject(String key, String index, Object value, Object target) throws NoSuchFieldException, IllegalAccessException {

        //if (target instanceof Map<String,Object>){ }
        Field field = target.getClass().getField(key);

        if (index != null){
            target = field.get(target);
            if (target instanceof Map){
                Map<String,Object> map = (Map)target;
                map.put(index, value);
            }
            else {
                throw new NoSuchFieldException(String.format("%s is not a Map", key));
            }
            return;
        }
        //System.err.printf("Key=%s (%s) %n", key, value);

        if (value == null) {
            field.set(target, null);
            return;
        }

        Class cls = field.getType();
        // System.err.printf(" Class=%s %n", cls.getName());

        String s = value.toString(); // (value == null) ? null : value.toString();

        if (cls.isPrimitive() && s.isEmpty()){
            return;
        }

        if (cls.equals(int.class) || (cls.equals(Integer.class))){
            field.set(target, Integer.parseInt(s));
        }
        else if (cls.equals(long.class) || (cls.equals(Long.class))){
            field.set(target, Integer.parseInt(s));
        }
        else if (cls.equals(float.class) || (cls.equals(Float.class))){
            field.set(target, Float.parseFloat(s));
        }
        else if (cls.equals(double.class) || (cls.equals(Double.class))){
            field.set(target, Double.parseDouble(s));
        }
        else if (cls.equals(char.class) || (cls.equals(Character.class))){
            field.set(target, s.charAt(0));
        }
        else if (cls.equals(byte.class) || (cls.equals(Byte.class))){
            field.set(target, Byte.parseByte(s));
        }
        else if (cls.equals(boolean.class) || cls.equals(Boolean.class)){
            field.set(target, Boolean.getBoolean(s));
        }
        else if (cls.equals(String.class)){
            field.set(target, s);
        }
        else if (cls.equals(Path.class)){
            field.set(target, Paths.get(s));
        }
        else {
            if (cls.isPrimitive()){
                throw new IllegalAccessException("Not yet implemented: " + cls.getName());
            }
            field.set(target, value); // obj
        }
    }

    static public String toString(Object obj){
        return toString(obj, ',');
    }

    static public String toString(Object obj, char separator){

        StringBuilder builder = new StringBuilder();
        for (Field field: obj.getClass().getFields()){
            builder.append(field.getName()).append('=');
            try {
                builder.append(field.get(obj));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            // builder.append(field.isAccessible());
            builder.append(separator);
        }
        return builder.toString();
    }


    static
    public class Example {

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

        @Override
        public String toString() {
            return Manip.toString(this, '\n');
        }
    }

    static
    public void main(String[] args) {

        Example example = new Example();

        if (args.length == 0){
            System.out.println("Usage: \n");
            System.out.println(Manip.toString(example, ' '));
            System.out.println("CONFFILE=foo.cnf  f=0.1234\n");
            System.exit(0);
        }

        System.out.println(example);

        Map<String,Object> map = new HashMap<>();

        Manip.Entry entry = new Entry();
        for (String arg: args) {

            try {
                parse(arg, entry);
            } catch (ParseException e) {
                //e.printStackTrace();
                System.out.println(e);
                continue;
            }


            if (entry.key.equals("CONFFILE")){
                Path path = Paths.get(entry.value.toString());
                try {
                    readConfig(path.toFile(), example);
                    readConfig(path.toFile(), map);
                    System.out.println(map);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else {
                try {
                    assignToObject(entry.key, entry.index, entry.value, example);
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }


        //System.out.println(map);
        System.out.println(example);

        try {
            assignToObject(map, example);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        System.out.println(example);

    }
}
