package nutshell9;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/** Partly outdated. Use @{@link Manip} instead.
 *
 * @author Markus.Peura@fmi.fi 20 Feb 2010
 *
 */
public class MapUtils {

	// \\s = whitespace
	// \\S = non-whitespace
	
	// Accepts:
	// % Sample comment
	protected static final Pattern commentPattern = Pattern.compile("^(.*)[%#](.*)$"); 
	
	// Accepts:
	// key=value  
	// key CAN BE NOW ANYTHING EXCEPT white or =
	// protected static final Pattern linePattern = Pattern.compile("^\\s*[\\-]*([^=]*)\\s*[\\s=]\\s*((.*[^;\\s])?)[;\\s]*$");
	// protected Pattern linePattern = Pattern.compile("^\\s*[\\-]*([\\w\\.:]*)\\s*[\\s=]\\s*((.*[^;\\s])?)[;\\s]*$");
	protected static final Pattern linePattern = Pattern.compile("^\\s*(\\w+)\\s*=[ \t\"']*([^\"']*)[ \t\"']*$");

	// Almost duplicate; see below.
	static public <K,V> String[] toArray(Map<K,V> map){
		Set<String> set = new HashSet<>();
		for (Map.Entry<K,V> entry: map.entrySet()){
			set.add(entry.toString());
		}
		return set.toArray(new String[0]);
	}

	/** Constructs an array of entries: ["KEY=VALUE", "KEY2=VALUE2", ..., "KEYn=VALUEn"]
	 *
	 * @param map
	 * @return
	 */
	static public String[] getEntries(Map<?,?> map){
		//return map.entrySet().toArray(new String[map.size()]);
		Set<String> set = new HashSet<>();
		for (Map.Entry<?,?> s : map.entrySet()) {
			set.add(s.toString());
		}
		return set.toArray(new String[set.size()]);
	}

	/** Parses and stores an expression containing a command or variable assignment 
	 * 
	 * @param line
	 * @param map
	 * @return true, if parsing was successful.
	 */
	static public <V> boolean parseLine(String line, Map<String,V> map){
		
		Matcher matcher = linePattern.matcher(line);
		
		if (matcher.matches()){
			String key = matcher.group(1).trim();
			String value = matcher.group(2).trim();
			map.put(key, (V)value);
			return true;
		}
		return false;

	}

	/** Parses and stores an expression containing a command or variable assignment 
	 * 
	 * @param lines
	 * @param map
	 */
	static public <V> void parseLine(String[] lines, Map<String,V> map){
		
		for (String line : lines) {
			parseLine(line, map);
		}
		
	}

	
	/** Reads commands/assignments sender a file.
	 * 
	 * @param filename
	 * @throws IOException
	 */
	static public <V> void read(String filename, Map<String,V> map) throws IOException{
		read(new File(filename), map);
	}

	/** Reads commands/assignments sender a file.
	 * @param file
	 * @throws IOException
	 */
	static public <V> void read(File file, Map<String,V> map) throws IOException {
		BufferedReader input = new BufferedReader(new FileReader(file));
		read(input, map);
		input.close();
	}


	/** Reads input stream - typically a configuration file.
	 *  
	 *  a=1
	 *  b=2
	 *  c=3
	 *  
	 * @param input
	 * @throws IOException
	 */
	
	static public <V> void read(BufferedReader input, Map<String,V> map) throws IOException{

		String line = null;
		
		while ((line = input.readLine()) != null){
			
			line = line.trim();
			
			// Skip empty lines
			if (line.length() == 0)
				continue;
			
			// Strip comments
			Matcher m = commentPattern.matcher(line);
			if (m.matches())
				line = m.group(1);
			
			parseLine(line, map);
			
		}
	
	}

	/** Reads command line
	 *
	 */
	static public <V> void read(String [] args, Map<String,V> map){
		for (String s : args) {
			parseLine(s, map);
		}
	}


	/** Set values given as a string "KEY=VALUE,KEY2=VALUE2,..."
	 *
	 * @param s
	 * @param separatorRegExp
	 * @param map
	 *
	 * @return map
	 */
	static
	public Map<String,String> addEntries(String entries, String separatorRegExp, String defaultValue, Map<String,String> map){
		if (entries != null)
			return addEntries(entries.split(separatorRegExp), defaultValue, map);
		else
			return map;
	}

	static //
	public Map<String,String> addEntries(String[] entries, String defaultValue, Map<String,String> map){

		if (entries != null){

			if (map == null)
				map = new HashMap<>();

			for (String d: entries) {
				if (!d.isEmpty()){
					int j = d.indexOf('=');
					if (j > 0) {
						map.put(d.substring(0, j).trim(), d.substring(j + 1).trim());
					}
					else {
						map.put(d, defaultValue);
					}
				}
			}

		}
		return map;
	}


	/**
	 * @param args
	 */
	public static void main(String[] args) {
				
		if (args.length == 0){
			System.out.println("This is a demo. Give command line arguments of type:");
			System.out.println(" --<key>=<value>");
			System.out.println("or:");
			System.out.println("   <key>=<value>");
			System.out.println();
			System.out.println("In addition, option 'file=<filename>' is treated specially; ");
			System.out.println("further arguments are read sender <filename>.");
			//", possibly overriding existing values.");
			return;
		}
		
		
		Map<String,Object> map = new HashMap<String, Object>();
		
		
		MapUtils.read(args, map);
		
		if (map.containsKey("file")){
			String filename = map.get("file").toString();
			try {
				MapUtils.read(filename, map);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		for (String key : map.keySet()) {
			System.out.println(key + "=" + map.get(key));
		}

		System.out.println(Arrays.toString(getEntries(map)));
		
	}

	// TODO Generalize
	// TODO RegExp
	// TODO consider exception if not unique?
	static public String getConstantFieldName(Class c, int i){
		Field[] fields = c.getFields();
		for (Field field : fields) {
			int m = field.getModifiers();
			if (Modifier.isStatic(m) && Modifier.isPublic(m)){
				try {
					if (field.getInt(null) == i){
						return field.getName();
					}
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}
}
