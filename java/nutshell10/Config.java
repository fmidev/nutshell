package nutshell10;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utilities for reading configuration files using simple KEY=VALUE syntax.
 *
 * @see Manip
 * @see JSON
 *
 */
public class Config {

    /** Splits a string at the first percent (%) or hash (#) character.
     *
     */
    static
    protected final Pattern commentPattern = Pattern.compile("^([^%#]*)[%#](.*)$");

    /** Syntax for assignment lines of general type "KEY=VALUE"
     *
     *  Accepts:
     * 	VARIABLE_NAME=VALUE
     * 	VARIABLE_NAME='VALUE'
     * 	VARIABLE_NAME="VALUE"
     *
     *  Also:
     *  VARIABLE[KEY]=VALUE
     *  VARIABLE[KEY2]=VALUE2
     *
     *
     */
    static
    protected  final Pattern linePattern = Pattern.compile("^\\s*(\\w+)(\\[(\\w+)\\])?\\s*=[ \t\"']*([^\"']*)[ \t\"']*$");

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
    	/*
    	try {
    	} catch (IOException e) {
    	}
    	*/
    }

    /**
     *
     * @param input
     * @param target
     * @param <T> – Object or Map<String,Object>
     * @throws IOException – only after read and assignment, if any error(s) occurred.
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
                parseConfigLine(line, entry);
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
                    Manip.assignToObject(entry.value, target, entry.key, entry.index);
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

    /** Extracts KEY=VALUE from the line – typically read in a file.
     *
     * @param line - input, assumed to be cleaned from trailing comment
     * @param entry - resulting (key,value) pair
     * @throws ParseException
     */
    static
    public void parseConfigLine(String line, Manip.Entry entry) throws ParseException {

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
    public Map<String, Object> getMap(Object src){
        return getMap(new HashMap<>(), src);
    }

    /*
    static
    public Map<String, Object> getMap(Object src, int modifiers){
        return getFields(new HashMap<String,Object>(), src, modifiers);
    }
     */

    static
    public Map<String, Object> getMap(Map<String, Object> map, Object src){
        return getFieldValues(map, src, Modifier.STATIC);
    }

    // See also configuration
    static
    public Map<String, Object> getFieldValues(Map<String, Object> map, Object src, int modifiers){

        Field[] fields = src.getClass().getFields();
        for (Field field : fields) {
            if ((field.getModifiers() & modifiers) > 0){
                // if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())){ // consider Bool nonStatic
                String name = field.getName();
                try {
                    map.put(name, field.get(src));
                } catch (Exception e) {
                    map.put(name, e.getMessage());
                }
            }
        }
        return map;
    }

    /** Retrieve values accessible with getters
     *
     * @see Manip
     *
     * @param src
     * @return
     */
    static
    public Map<String, Object> getValues(Object src) {
        return getValues(src, new HashMap<>());
    }

    /** Retrieve values accessible with getters
     *
     * @see Manip
     *
     * @param src
     * @return
     */
    static
    public Map<String, Object> getValues(Object src, Map<String, Object> map) { //}, int modifiers){

        final Object[] empty = new Object[0]; // ??

        Method[] methods =  src.getClass().getMethods();
        for (Method method : methods) {
            // if (method.isAccessible())
            String name = method.getName();
            if (name.startsWith("get") && (method.getParameterCount()==0) && (method.getReturnType() != void.class)){
                Object value;
                try {
                    value = method.invoke(src, empty);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    // value = e.getClass().getName();
                    value = e.getMessage();
                }
                if (value == null)
                    value = "";
                map.put(name, value.toString()); // +method.toGenericString()); // or object?

            }

        }

        return map;
    }
}
