package nutshell;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *   See also: @{@link MapUtils}
 */
public class Config {

    static
    public class Entry {
        String key = "";
        Object value = null;
    }

    protected static final Pattern commentPattern = Pattern.compile("^(.*)[%#](.*)$");

	// Accepts:
	// key=value
	// key CAN BE NOW ANYTHING EXCEPT white or =
	protected static final Pattern linePattern = Pattern.compile("^\\s*(\\w+)\\s*=[ \t\"']*([^\"']*)[ \t\"']*$");

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
     * @throws IOException
     */
    static
    public <T> void readConfig(File file, T target) throws IOException {
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
    public <T> void readConfig(BufferedReader input, T target) throws IOException {

        // System.err.printf(" Target class=%s %n", target.getClass().getName());
        // System.err.printf(" Map? %b %n", Map.class.isInstance(target));
        // System.err.printf(" Map? %b %n", );
        final boolean MAP = (target instanceof Map);

        String line = null;

        Config.Entry entry = new Config.Entry();

        while ((line = input.readLine()) != null){

			line = line.trim();

			// Skip empty lines
			if (line.length() == 0)
				continue;

			// Strip (trailing) comments
			Matcher m = commentPattern.matcher(line);
			if (m.matches())
				line = m.group(1); //

			parse(line, entry);
            try {
                if (MAP)
                    assignToMap(entry.key, entry.value, (Map<String, Object>) target);
                else
                    assignToObject(entry.key, entry.value, target);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

        }
    }

    static
    public void parse(String line, Config.Entry entry){

        Matcher matcher = linePattern.matcher(line);
        if (matcher.matches()) {
            entry.key = matcher.group(1).trim();
            entry.value = matcher.group(2).trim();
        }
        // Map.Entry<String,Integer> entry =
        // new AbstractMap<String,Integer>().SimpleEntry<String, Integer>("exmpleString", 42);
    }

    static
    public boolean assignToMap(String key, Object value,  Map<String,Object> target)  {
        target.put(key, value);
        return true;
    }

    static
    public boolean assignToObject(String key, Object value, Object target) throws NoSuchFieldException, IllegalAccessException {

        //if (target instanceof Map<String,Object>){ }
        Field field = target.getClass().getField(key);

        if (value == null) {
            field.set(target, null);
            return false; // or true...
        }

        Class cls = field.getType();
        // System.err.printf(" Class=%s %n", cls.getName());

        String s = value.toString(); // (value == null) ? null : value.toString();

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
        else {
            if (cls.isPrimitive()){
                throw new IllegalAccessException("Not yet implemented: " + cls.getName());
            }
            field.set(target, value); // obj
        }
        return true;
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

        @Override
        public String toString() {
            return Config.toString(this, '\n');
        }
    }

    static
    public void main(String[] args) {

        Example example = new Example();

        if (args.length == 0){
            System.out.println("Usage: \n");
            System.out.println(Config.toString(example, ' '));
            System.out.println("CONFFILE=foo.cnf  f=0.1234\n");
            System.exit(0);
        }

        System.out.println(example);

        Config.Entry entry = new Entry();
        for (String arg: args) {

            parse(arg, entry);
            if (entry.key.equals("CONFFILE")){
                Path path = Paths.get(entry.value.toString());
                try {
                    readConfig(path.toFile(), example);
                    Map<String,Object> map = new HashMap<>();
                    readConfig(path.toFile(), map);
                    System.out.println(map);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else {
                try {
                    assignToObject(entry.key, entry.value, example);
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println(example);

    }
}
