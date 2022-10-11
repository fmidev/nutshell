package nutshell;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
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

    static
    public void readConfig(Path path, Object target) throws IOException {
        readConfig(path.toFile(), target);
    }

    static
    public void readConfig(File file, Object target) throws IOException {
        BufferedReader input = new BufferedReader(new FileReader(file));
		read(input, target);
		input.close();
    }

    static
    public void read(BufferedReader input, Object target) throws IOException {
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
                assign(entry, target);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

        }
    }



    static
    public boolean parse(String line, Config.Entry entry){

        Matcher matcher = linePattern.matcher(line);

        if (matcher.matches()) {
            //return new HashMap<String,String>().Entry<>("","");
            entry.key = matcher.group(1).trim();
            entry.value = matcher.group(2).trim();
            return true;
        }
        // Map.Entry<String,Integer> entry =
        // new AbstractMap<String,Integer>().SimpleEntry<String, Integer>("exmpleString", 42);

        return false;
    }

    static
    public boolean assign(Config.Entry entry, Object target) throws NoSuchFieldException, IllegalAccessException {
        Field field = target.getClass().getField(entry.key);
        Class cls = field.getType();
        System.err.printf(" Class=%s %n", cls.getName());
        if (cls.isPrimitive()){

            field.setInt(target, Integer.parseInt(entry.value.toString()));
            //field.setFloat(target, Float.parseFloat(entry.value.toString()));
        }
        else {
            field.set(target, entry.value);
        }
        return true;
    }



    static
    public class Example {
        public Object obj;
        public String s;
        public int i;

        @Override
        public String toString() {
            return "Example{" +
                    "obj=" + obj +
                    ", s='" + s + '\'' +
                    ", i=" + i +
                    '}';
        }
    }

    static
    public void main(String[] args) {

        if (args.length == 0){
            System.out.println("Usage: ");
            System.exit(0);
        }

        Example example = new Example();
        System.out.println(example);

        Config.Entry entry = new Entry();
        for (int i = 0; i < args.length; i++) {
            String line = args[i];
            parse(line, entry);
            if (entry.key.equals("CONFFILE")){
                Path path = Paths.get(entry.value.toString());
                try {
                    readConfig(path, example);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else {
                try {
                    assign(entry, example);
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
