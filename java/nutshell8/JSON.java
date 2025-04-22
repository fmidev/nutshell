package nutshell;

// import com.sun.istack.internal.NotNull;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class JSON { //extends HashMap<String,JSON> {

    static
    public class MapJSON extends HashMap<String,JSON> {
    }

    Object value;

    public JSON(){
        value = null;
    }

    public JSON(Object value){
        put(value);
    }


    public JSON put(Object value) {
        if (value instanceof JSON){
            this.value = ((JSON) value).getValue();
        }
        else {  // NOTE: including null and JSONchildren
            this.value = value;
        }
        return this;
    }

    public JSON put(String key, Object value) {
        // JSON child = ensureChild(key);
        //assert (child != null);
        //child.put(value);
        //return child;
        return ensureChild(key).put(value);
    }

    public Object getValue() {
        return value;
    }

    public Object getValue(Path path) {
        JSON json = getNode(path);
        if (json == null){
            return null;
        }
        else {
            return json.getValue();
        }
    }

    public JSON getNode(Path path){
        int length = path.getNameCount();
        if (length == 0){
            return this;
        }
        else {
            JSON child = getChild(path.getName(0).toString());
            if (child == null){
                return null;
            }
            else {
                //System.out.printf("testing: %s %n", path);
                if (length == 1)
                    return child;
                else
                    return child.getNode(path.subpath(1, length));
            }
        }
    }


    /** Adds a child, possibly replacing an existing one
     *
     * @param key
     * @return
     */
    public
    JSON addChild(String key){
        if (key.isEmpty()){
            return this;
        }
        JSON json = new JSON();
        ensureChildren().put(key, json);
        return json;
        //return ensureChildren().put(key, new JSON()); // or null
    };


    public
    JSON ensureChild(@NotNull String key){

        if (key.isEmpty())
            return this;

        MapJSON children = ensureChildren();
        JSON child;

        if (children.containsKey(key)){
            child = children.get(key);
            if (child != null)
                return child;
        }

        child = new JSON();
        children.put(key, child); // NOTE: Map::put() returns the OLD value!
        return child;
    };


    private MapJSON ensureChildren(){
        if (!(value instanceof MapJSON)){
            value = new MapJSON();
        }
        return (MapJSON)value;
    }

    public boolean hasChildren(){
        return (value instanceof MapJSON);
    }

    public MapJSON getChildren(){
        if (hasChildren()){
            return (MapJSON)value;
        }
        else {
            return null;
        }
    }

    public MapJSON getChildren(Path path){
        JSON json = getNode(path);
        if (json != null){
            return json.getChildren();
        }
        else {
            return null;
        }
    }


    synchronized
    public boolean hasChild(String key){
        if (hasChildren()){
            return ((MapJSON)value).containsKey(key);
        }
        else {
            return false;
        }
    }

    /** Returns child node having the given key. Creates a node, unless exists.
     *
     * @param key
     * @return
     */
    synchronized
    public JSON getChild(String key){
        if (!hasChildren()){
            ensureChildren();
        }
        //if (hasChild(key)){
            return ((MapJSON)value).get(key);
        //}
        // return null;
    }



    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        write(value, buffer, "");
        return buffer.toString();
    }

    // TODO: make static, rename to writeEntity, use also for array elems.
    static
    public StringBuffer write(Object value, StringBuffer buffer, String indent) {

        if (value == null){
            // Node has neither value nor children.
            // Note: if node has an empty child map, an empty object {} is written instead.
            buffer.append("null");
        }
        else if (value instanceof Map<?,?>) {
            buffer.append('{');
            Map<?,?> nodes = (Map) value;
            if (!nodes.isEmpty()) {
                //buffer.append('\n');
                String indentNext = indent+"  "; // getIndent(indent);
                boolean CONTINUES = false;
                for (Map.Entry entry : nodes.entrySet()) {
                    if (CONTINUES){
                        buffer.append(',');
                    }
                    else {
                        CONTINUES = true;
                    }
                    buffer.append('\n');

                    String key = entry.getKey().toString();
                    Object v = entry.getValue();
                    if (v instanceof JSON){
                        v = ((JSON) v).value;
                    }

                    // RECURSION
                    buffer.append(indentNext);
                    buffer.append('"').append(key).append('"').append(':').append(' ');
                    write(v, buffer, indentNext);
                }
                buffer.append('\n');
                buffer.append(indent);
            }
            buffer.append('}'); //.append('\n');
        }
        else if (value.getClass().isArray()){
            writeArray(value, buffer, indent);
        }
        else if (value instanceof Number){
            buffer.append(value);
        }
        else if (value instanceof Boolean){
            buffer.append(value);
        }
        else if ((value instanceof String) || (value instanceof Character)){
            buffer.append('"').append(value).append('"');
        }
        else { // if (value instanceof String){
            // Consider non-string objects as JSON object with "type" and "value" attribute?
            buffer.append('"').append(value.getClass().getSimpleName()).append(":").append(value).append('"');
        }

        return buffer;
    }

    /*
    public <E> boolean  tryToWriteArray(StringBuffer buffer) {
        try {
            buffer.append(Arrays.toString((E[]) value));
            buffer.append(',').append('"').append(value.getClass().getSimpleName()).append('"');
            return true;
        }
        catch (ClassCastException e) {
            return false;
        }
    }
     */

    static
    public StringBuffer writeArray(Object value, StringBuffer buffer, String indent) {

        Class cls = value.getClass();
        if (cls.getComponentType().isPrimitive()){
            // write(buffer, indent+"  ");
            if (cls.equals(short[].class)){
                buffer.append(Arrays.toString((short[]) value));
            }
            else if (cls.equals(int[].class)){
                buffer.append(Arrays.toString((int[]) value));
            }
            else if (cls.equals(long[].class)){
                buffer.append(Arrays.toString((long[]) value));
            }
            else if (cls.equals(float[].class)){
                buffer.append(Arrays.toString((float[]) value));
            }
            else if (cls.equals(double[].class)){
                buffer.append(Arrays.toString((double[]) value));
            }
            else {
                buffer.append("[ null ]"); // error!
            }
            //buffer.append(value); // UNDER CONSTRUCTION
            //buffer.append(Arrays.toString(value));
            //buffer.append('§');
        }
        else {
            Object[] array = (Object[]) value;
            // Basically array of objects, but check if still numeric/boolean, and use flat single row output.
            boolean ALL_PRIMITIVE = true;
            // Essenitially, primitive are SHORT values.
            for (Object v: array){
                // v.getClass().isPrimitive()
                //  || (v instanceof Character)
                if (!(v.getClass().isPrimitive() ||
                        (v instanceof Number) || (v instanceof Boolean))){
                //if (!v.getClass().isPrimitive()){
                    ALL_PRIMITIVE = false;
                    break;
                }
                //write(buffer, indent+"  ");
            }
            //

            if (ALL_PRIMITIVE){
                //buffer.append('[');
                buffer.append(Arrays.toString((Object[]) value)); //.append(']').append('\n');
            } else {
                // UNDER CONSTR! -> print as strings for now
                buffer.append('[');
                boolean CONTINUES = false;
                for (Object v: array){
                    if (!CONTINUES){
                        CONTINUES = true;
                    }
                    else {
                        buffer.append(',');
                    }
                    buffer.append('\n').append(indent + "    "); // indent+2
                    write(v, buffer, indent + "  ");
                    // buffer.append('"').append(v).append('"'); // TODO: writeValue v (support JSON recursion)
                }
                buffer.append(']'); //.append('\n');
            }
            //buffer.append('>');
        }
        return buffer;
    }

    /** Read JSON file
     *
     * @param filename – path of a JSON file
     * @throws IOException
     * */
    void read(String filename) throws IOException, ParseException {
        PushbackReader reader = new PushbackReader(new InputStreamReader(new FileInputStream(filename),
        Charset.forName("UTF-8")));
        value = readEntity(reader);
    }

    /** Read JSON section of any type, appearing after ':'
     *
     * @param reader
     * @throws IOException
     * @throws ParseException
     */
    static
    Object readEntity(PushbackReader reader) throws IOException, ParseException {
        int i;
        if ((i = skipWhitespace(reader)) != -1) {
            char c = (char) i;
            // System.out.print(c);
            switch (c){
                case '"':
                    return readString(reader);
                case '{':
                    return readObject(reader);
                case '[':
                    return readArray(reader);
                default:
                    reader.unread(i);
                    return readPrimitive(reader);
            }
        }
        return "???";
        //throw new ParseException(String.format("Could not parse value '%s'", key, c), 0);
    }

    static private
    final String WHITESPACE = " \t\r\n";

    // Consider
    static private
    final int WHITESPACE_SPACE = (int)' ';

    //enum Mika {A, B, C};

    static
    int skipWhitespace(PushbackReader reader) throws IOException {
        int i;
        // char c;
        while ((i = reader.read()) != -1) {
            if (WHITESPACE.indexOf(i) == -1){
                // Found non-whitespace char
                return i;
            }
        }
        return -1;
    }

    /** Extract a string, assuming that a leading quote (") has been already read.
     *
     * @param reader
     * @return String, excluding leading and trailing quotes (").
     * @throws ParseException upon syntax error
     * @throws IOException if end of file encountered
     */
    static
    String readString(PushbackReader reader) throws IOException, ParseException {
        StringBuffer sb = new StringBuffer();
        int i;
        char c;
        while ((i = reader.read()) != -1) {
            c = (char) i;
            switch (c){
                case '\\': // Escaping
                    if ((i = reader.read()) != -1) {
                        c = (char) i;
                        switch (c){
                            case '"':
                                sb.append('"');
                                break;
                            case 'n':
                                sb.append('\n');
                                break;
                            case 't':
                                sb.append('\t');
                                break;
                            case 'r':
                                sb.append('\r');
                                break;
                            default:
                                sb.append('?');
                        }
                    }
                    break;
                case '"':
                    // The only valid end
                    return sb.toString();
                default:
                    sb.append(c);
            }
        }
        throw new ParseException(String.format(
                    "Premature end of file, no ending double quote (\") in string:  %s",
                    sb.toString()), sb.length());
        //return null;
    }

    /** Assumes leading curly brace '{' read and reads key:value pairs until trailing curly brace '}'.
     *
     * @param reader
     * @return
     * @throws IOException
     * @throws ParseException
     */
    static protected
    Object readObject(PushbackReader reader) throws IOException, ParseException {
        MapJSON children = new MapJSON();
        int i;
        char c;
        while ((i = skipWhitespace(reader)) != -1) {
            c = (char) i;
            String key = "?";
            switch (c){
                case '"': // KEY
                    key = readString(reader);
                    // System.out.println(String.format("Read key <%s>", key));
                    i = skipWhitespace(reader);
                    if (i == -1){
                        throw new ParseException(String.format("Premature end of file after '%s' and char '%s'", key, c), 0);
                    }
                    c = (char) i;
                    if (c != ':'){
                        throw new ParseException(String.format("After key '%s': illegal char '%s' , expected colon (:)", key, c), 0);
                    }
                    JSON json = new JSON();
                    json.value = readEntity(reader);
                    children.put(key, json);
                    // System.out.println(String.format("Added '%s' = %s", key, json.value));
                    break;
                case '}':
                    // Empty child map results. Value has been assigned childMap upon leading '{'
                    // System.out.println(String.format("Returning {...} (%d items)", children.size()));
                    return children;
                case ',': // OPEN?
                    // System.out.printf("What, comma?");
                    break;
                case '[':
                case ']':
                    throw new ParseException(String.format("Illegal char %s (after key '%s')", c, key), 0);
                default:
                    //throw new ParseException(String.format("Illegal entity end after ''", key), 0);
                    System.out.printf("letter: %s (%d)", c, i);
            }
        }
        throw new ParseException(String.format("Premature end of file (after reading '%s')", children), 0);
    }


        /** Extract an array (of basetype), assuming that a leading brace ([) has been already read.
         *
         * @param reader
         * @return String, excluding leading and trailing quotes (").
         * @throws IOException
         */
    static protected
    Object[] readArray(PushbackReader reader) throws IOException, ParseException {
        //String value = readUntil(reader, "]");
        ArrayList<Object> values = new ArrayList<>();
        int i;
        char c;
        while ((i = skipWhitespace(reader)) != -1) {
            c = (char) i;
            switch (c) {
                case ',':
                    break;
                case ']':
                    // System.out.printf("Completed array: %s %n", values);
                    return values.toArray();
                case '}':
                case ':':
                    throw new ParseException(String.format("Illegal char '%s' after reading array: ", c, values), 0);
                default:
                    //System.out.printf("Push back: %s %n", c);
                    reader.unread(i); // push back
                    values.add(readEntity(reader));
                    // System.out.printf("Current array: %s %n", values);
                    //readEntity
            }
        }
        throw new ParseException(String.format("Premature end of file in reading array: %s...", values), 0);
    }

    static protected
    Object readPrimitive(PushbackReader reader) throws IOException, ParseException {

        StringBuffer buffer = new StringBuffer();
        //buffer.append(c); // "push back"
        int i = readUntil(reader,",]}", buffer);
        reader.unread(i);
        String s = buffer.toString().trim();

        // TODO: move to manip
        //Object result = null;
        if (s.equals("true")){
            // value = true;
            return true;
        }
        else if (s.equals("false")){
            return false;
        }
        else if (s.equals("null")){
            return null;
        }
        else {

            try {
                return Integer.parseInt(s);
                //System.out.printf("Numeral %s (int)", result);
            }
            catch (NumberFormatException e) {}

            try {
                return Long.parseLong(s);
                //   System.out.printf("Numeral %s (long)", result);
            }
            catch (NumberFormatException e){}

            try {
                return Double.parseDouble(s);
            }
            catch (NumberFormatException e){}
            //System.out.printf("Numeral %s (double)%n", result);

            throw new ParseException(String.format(
                "Could not parse value '%s' as numeral or boolean", s), 0);
                // throw new ParseException(String.format("Could not parse value '%s'", key, c), 0);
                // System.out.printf("OK, %s%n", e.getMessage());
        }
    }


    static protected
    int readUntil(PushbackReader reader, String chars, StringBuffer buffer) throws IOException {
        int i;
        while ((i = reader.read()) != -1) {
            char c = (char) i;
            if (chars.indexOf(c) >= 0){
                return i;
            }
            else {
                buffer.append(c);
            }
        }
        buffer.append('~'); // premature end
        return i;
        //return buffer.toString();
    }

    //static
    //private ArrayList<String> indents = new ArrayList<String>();

    /*
    static
    private String getIndent(int i){
        if (i < 0){
            return null;
        } else if (i < indents.size()){
            return indents.get(i);
        } else {
            String s = "";
            if (i >= indents.size()){
                s = indents.get(i-1) + "  ";
            }
            indents.add(i, s);
            return s;
        }
    }
    */

    public static void main(String[] args) {

        JSON json = new JSON();

        if (args.length == 0){
            System.out.println("JSON reader and writer");
            System.out.println("Args: <filename>.json");
            System.out.println();

            System.out.println("Example of a JSON structure");
            json.put("number", 5);
            json.put("string", "Hello, world!");
            json.put("boolean", false);
            json.put("empty", null);
            json.put("empty2", null).ensureChildren();
            json.put("arrayMixed", new Object[]{1, false,"mika"});
            json.put("arrayScalar", new Object[]{true, 1,2.0f, 3.14, 123456789L});
            json.put("floatArray", new float[]{1.2f, 2.23f, 3.4f});
            json.addChild("child").put("float", 3.124);
            json.ensureChild("child").put("int", 312);
            System.out.println(json);

            System.exit(1);
        }

        try {
            json.read(args[0]);
            System.out.println("Result: ");
            System.out.println(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            System.err.println(json);
            throw new RuntimeException(e);
        }

    }

}
