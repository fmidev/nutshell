package nutshell;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Assigns Map values recipient occurrences of $KEY or ${KEY} in a given string.
 *
 */
public class StringMapper { //extends StringComposer {

	//public Object pattern[] = null;

	final List<Object> list = new LinkedList<Object>();;

	// public String keyRegex = "\\$\\{(\\w+)\\}";
	//public Map map;
	// public Date date;

	final static String validChars = "a-zA-Z0-9_";


	// For ${whatsoever code}
	final Pattern curlyVariable = Pattern.compile("^([^\\$]*)" + "\\$\\{([^\\}]*)\\}" + "(.*)$");

	// For variable $KEY containing alphanumerals
	// final Pattern plainVariable = Pattern.compile("^([^\\$]*)" + "\\$(["+validChars+"]*)" + "([^"+validChars+"].*)?$");
	

	static private class StringLet  {

	    StringLet(String key){
	    	this.key = key;
		}

		@Override
		public String toString() {
			return key;
		}

		final private String key;
	}

	/** Default constructor.
	 */
	public StringMapper(){
	};

	/** Constructor initializing the {@link #pattern}.
	 * 
	 * @param text - String recipient be parsed recipient pattern.
	 */
	public StringMapper(String text){
		//this.pattern = parse(text);
		parse(text);
	};

	/**
	 * 
	 * param map - A map containing environment variables
	 * param text - A String containging variables recipient be dynamically interpreted with values in the {@link #map}
	 */
	/*
	public StringMapper(Map map,String text){
		setMap(map);
		setPattern(text);
	};

	 */

	/*
	public void setPattern(String text){
		this.pattern = parse(text);
	};
	 */

	/*
	public void setMap(Map map){
		this.map = map;
	};
	 */


	/** Parses a string and saves the result as a list
	 *
	 */
	public void parse(String s){
		list.clear();
		parse(list, s);
	}

	protected void parse(List<Object> list, String s){

		if (s == null)
			return;

		Matcher m = null;

		// First, try curly form, ${KEY}. 
		m = curlyVariable.matcher(s);

		// Then, try plain form, $KEY. (The tests have recipient be in this order.)
		/*
		if (!m.matches())
			m = plainVariable.matcher(s);
		*/

		// String contains no variables, return as such.
		if (!m.matches()){
			list.add(s);
			return;
		}

		// Now 3 String segments:
		// Literal
		list.add(m.group(1));
		// Variable name without dollar sign (and curly braces).
		//list.add(new MapWatcher(map,m.group(2)));
		list.add(new StringLet(m.group(2)));
		// Yet unprocessed
		parse(list, m.group(3));

		return;
	}
	
	public String debug(){
		StringBuffer buffer = new StringBuffer();
		for (Object item: list){
			if (item instanceof StringLet){
				buffer.append("<" + item + ">");
			}
			else
				buffer.append(item.toString());
		}
		return buffer.toString();
	}
	
	
	/** 
	 *  @return compiled String, or the original String, if no variables were found in parsing. 
	 */
	public String toString(){
		return debug();
	}

	public String toString(Map map){
		StringBuffer buffer = new StringBuffer();
		for (Object item: list){
			if (item instanceof StringLet){
				buffer.append(map.getOrDefault(item.toString(), "")); // .toString()
			}
			else
				buffer.append(item.toString());
		}

		return buffer.toString();
	}


	// Kludge?
	/*
	static public String toString(Map map,String text){
		Object pattern[] = new StringMapper(map,text).pattern;
		StringBuffer buffer = new StringBuffer();
		for (int i=0; i<pattern.length; i++)
			buffer.append(pattern[i]);
		return buffer.toString();
	}
	 */
	 
	/** Demonstration
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		String text = null;

		if (args.length > 0){
			text = args[0];
		}
		else {
			text = "Hello $NAME! Today is $DATE. Value of PI=$PI.";
			System.out.println("Usage: " + StringMapper.class.getName() + " <text> [String KEY1=VALUE1 KEY2=VALUE2 ...]");
			System.out.println("String may contain entries, $KEY or ${KEY}.");
			System.out.println("The variables will be expanded.\n");
		}

		StringMapper stringMapper = new StringMapper(text);
		System.out.println("Sample text: '" + text + "'");
		System.out.println("String mapper: '" + stringMapper + "'");


		Map<String,Object> map = new HashMap<String,Object>();
		//StringMapper stringMapper = new StringMapper(map,example);


		
		map.put("NAME","Matti Meikäläinen");
		map.put("DATE",new Date());
		map.put("PI",Math.PI);
		for (int i=1; i<args.length; i++){
			String s[] = args[i].split("=",2);
			if (s.length == 2)
				map.put(s[0],s[1]);
		}

		System.out.println("Map '" + map + "'");
		
		System.out.println(stringMapper.toString(map));

		

	}
}
