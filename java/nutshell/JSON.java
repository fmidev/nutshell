package nutshell;

import com.sun.istack.internal.NotNull;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.ParseException;
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


    /** Overrides existing children
     *
     * @param key
     * @return
     */
    public
    JSON addNode(String key){
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



    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        write(buffer, "");
        return buffer.toString();
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

    public StringBuffer write(StringBuffer buffer, String indent) {

        if (value == null){
            buffer.append("null");
        }
        else if (value instanceof MapJSON) {
            buffer.append('{');
            MapJSON nodes = (MapJSON) value;
            if (!nodes.isEmpty()) {
                //buffer.append('\n');
                String ind = indent+"  "; // getIndent(indent);
                boolean CONTINUES = false;
                for (Map.Entry<String, JSON> entry : nodes.entrySet()) {
                    if (CONTINUES){
                        buffer.append(',');
                    }
                    else {
                        CONTINUES = true;
                    }
                    buffer.append('\n');

                    String key = entry.getKey();
                    JSON value = entry.getValue();
                    buffer.append(ind);
                    buffer.append('"').append(key).append('"').append(':').append(' ');
                    value.write(buffer, ind);
                }
            }
            buffer.append('}').append('\n');
        }
        else if (value instanceof Number){
            buffer.append(value);
        }
        else if (value instanceof Boolean){
            buffer.append(value);
        }
        else if (value.getClass().isArray()){
            if (value.getClass().getComponentType().isPrimitive()){
                buffer.append(value);
                //buffer.append(Arrays.toString(value));
            }
            else {
                Object[] array = (Object[]) value;
                buffer.append('<').append('\n');
                for (Object v: array){
                    buffer.append(indent).append(v).append(',').append('\n');
                    //write(buffer, indent+"  ");
                }
                buffer.append('>');
            }
        }
        else { // if (value instanceof String){
            buffer.append('"').append(value.toString()).append('"');
        }

        return buffer;
    }

    /** Read JSON file
     *
     * @param filename – path of a JSON file
     * @throws IOException
     */
    void read(String filename) throws IOException, ParseException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename),
        Charset.forName("UTF-8")));
        readValue(reader);
    }

    /** Read JSON section of any type, appearing after ':'
     *
     * @param reader
     * @throws IOException
     * @throws ParseException
     */
    void readValue(BufferedReader reader) throws IOException, ParseException {
        int i;
        //while ((i = reader.read()) != -1) {
        if ((i = skipWhitespace(reader)) != -1) {
            char c = (char) i;
            // System.out.print(c);
            if (c == '"'){
                value = readString(reader);
            }
            else if (c == '['){
                value = readArray(reader);
            }
            else if (c == '{') {
                int i2;
                while ((i2 = skipWhitespace(reader)) != -1) {
                    char c2 = (char) i2;
                    switch (c2){
                        case '"':
                            String key = readString(reader);
                            System.out.println(String.format("Read key <%s>", key));
                            JSON json = ensureChild(key);
                            int i3 = json.skipWhitespace(reader);
                            if (i3 == -1){
                                throw new ParseException(String.format("Premature end of file after '%s'", key), 0);
                            }
                            char c3 = (char) i3;
                            if (c3 != ':'){
                                throw new ParseException(String.format("Illegal text after '%s'", key), 0);
                            }
                            json.readValue(reader);
                            System.out.println(String.format("Now '%s' = %s", key, json));
                            break;
                        case '}':
                            if (value == null)
                                value = "empty-set";
                            return;
                        case ',': // OPEN?
                            System.out.printf("What, comma?");
                            break;
                        default:
                            //throw new ParseException(String.format("Illegal entity end after ''", key), 0);
                            System.out.printf("letter: %s (%d)", c2, i2);
                    }
                }
                /*
                if (i2 == -1){
                    throw new ParseException("premature end of file", 0);
                }*/
            }
            else {
                StringBuffer buffer = new StringBuffer();
                buffer.append(c); // "push back"
                readUntil(reader,",}", buffer);
                String s = buffer.toString().trim();
                value = s;

                // TODO: move to manip
                if (value.equals("true")){
                    value = true;
                    return;
                }
                else if (value.equals("false")){
                    value = false;
                    return;
                }

                try {
                    int n = Integer.parseInt(s);
                    value = n;
                    double d = Double.parseDouble(s);
                    value = d;
                }
                catch (Exception e){
                    // System.out.printf("OK, %s%n", e.getMessage());
                };

                // System.out.printf("Read value starting with '%s...': %s%n", c, value);
                //return;
            }
        }
    }

    static private
    String WHITESPACE = " \t\r\n";

    int skipWhitespace(BufferedReader reader) throws IOException {
        int i;
        // char c;
        while ((i = reader.read()) != -1) {
            //c = (char) i;
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
     * @throws IOException
     */
    static
    String readString(BufferedReader reader) throws IOException {
        StringBuffer buffer = new StringBuffer();
        int i = readUntil(reader, "\"", buffer);
        if (i == -1){
            // throw new ParseException();
        }
        return buffer.toString();
    }


    /** Extract an array (of basetype), assuming that a leading brace ([) has been already read.
     *
     * @param reader
     * @return String, excluding leading and trailing quotes (").
     * @throws IOException
     */
    static protected
    Object[] readArray(BufferedReader reader) throws IOException {
        //String value = readUntil(reader, "]");
        StringBuffer buffer = new StringBuffer();
        int i = readUntil(reader, "]", buffer);
        if (i == -1){
            // throw new ParseException();
        }
        return buffer.toString().split(","); // Temporary solution
    }



    static protected
    int readUntil(BufferedReader reader, String chars, StringBuffer buffer) throws IOException {
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

    public static void main(String[] args) {

        JSON json = new JSON();
        json.put("number", 5);
        json.put("string", "Hello, world!");
        json.put("boolean", false);
        json.put("empty", null);
        json.put("array", new Object[]{1,2,"mika"});
        json.put("floatArray", new float[]{1.2f, 2.23f, 3.4f});


        JSON node = json.addNode("child");
        node.put("subValue", 3.121);
        System.out.println(json);

        try {
            JSON json2 = new JSON();

            json2.read("test.json");
            System.out.println("Result: ");
            System.out.println(json2);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
