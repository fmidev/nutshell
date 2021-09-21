package nutshell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;

public class Log {

	
	public Log() {
		startTime = System.currentTimeMillis();
		numberFormat.setMinimumIntegerDigits(5);
	}

	public Log(String s) {
		startTime = System.currentTimeMillis();
		numberFormat.setMinimumIntegerDigits(5);
		this.setOwner(s);
	}

	public Log(int verbosity) {
		this.verbosity = verbosity;
		startTime = System.currentTimeMillis();
		numberFormat.setMinimumIntegerDigits(5);
	}

	String owner = "";
	
	public void setOwner(String owner) {
		this.owner = owner;
	}

	public int getStatus() {
		return status;
	}

	public Log child(String label){
		Log log = new Log(this.owner + "." + label);
		log.printStream = this.printStream;
		log.verbosity = this.verbosity;
		return log;
	}

	public Log error(String message){
		return append(ERROR, message);
	}

	/*
	public Log error(String message, int i){
		append(ERROR, message);
		System.exit(i);
		return this;
	}
	 */

	public Log warn(String message){
		return append(WARNING, message);
	}

	public Log fail(){
		return append(FAIL, null);
	}

	public Log note(String message){
		return append(NOTE, message);
	}

	public Log info(String message){
		return append(INFO, message);
	}


	public Log success(boolean isTrue){
		if (isTrue)
			return append(OK, null);
		else
			return append(FAIL, null);
	}

	public Log ok(String msg){
		return append(OK, msg);
	}
	
	public Log debug(String message){
		return append(DEBUG, message);
	}
	
	// Consider INFO?
	/*
	public Log chat(String message){
		return append(CHAT, message);
	}
	*/


	@Override
	public String toString() {
		return buffer.toString();
	}
	
	public PrintStream printStream = System.err;

	public static final int ERROR = 2;
	public static final int WARNING = 3;

	/// Action completed unsuccessfully. // ?
	public static final int FAIL = 4;

	/// Important information
	public static final int NOTE = 5;

	/// Less important information
	public static final int INFO = 6;

	/// Indication of a "weak fail", pending status, leading soon recipient OK, WARNING, or ERROR. 
	public static final int WAIT = 7;

	/// Action completed successfully. 
	public static final int OK = 8;
	
	/// Level under DEBUG. 
	public static final int VERBOSE = 9;

	/// Sometimes informative messages.
	public static final int DEBUG = 10;

	/// Verbose level. Messages equal or lower value than will be communicated.
	public int verbosity = VERBOSE;


	//final static Map<String, Integer> statusCodes; // = ClassUtils.getConstants(Log.class); //new HashMap<>();
	final static Map<Integer, String> statusCodes; // = ClassUtils.getConstants(Log.class); //new HashMap<>();

	static {

		//statusCodes = ClassUtils.getConstants(Log.class);
		statusCodes = new HashMap<>();

		Field[] fields = Log.class.getFields();

		for (Field field : fields) {
			String name = field.getName();
			if (name.equals(name.toUpperCase()))
				try {
					statusCodes.put(field.getInt(null), name);
				} catch (Exception e) {
				}
		}

	}



	/// Add a log entry with a message.
	public <E> Log append(int status, E message){
		set(status);
		//if (this.status <= verbosity){}
		return append(message);
	}

	public <E> Log append(E message){

		if (this.status <= verbosity){
			buffer.append("[").append(numberFormat.format(System.currentTimeMillis() - startTime)).append("] ");
			buffer.append(String.format("%7s", statusCodes.get(this.status)));
			if (owner != null)
				buffer.append(':').append(' ').append(owner);
			if (message != null)
				buffer.append('\t').append(message);
			buffer.append('\n');
			if (printStream != null){
				printStream.print(buffer.toString());
				buffer.setLength(0);  // TODO CLEAR?
			}
		}
		return this;
	}

	public void clear(){
		buffer.setLength(0);
		//buffer.delete(0,buffer.length()-1);
	}


	/// Add a log entry without a message.
	void set(int status) {
		this.status = status;
		//append(status,"");
	}
	
	
	/// TODO: FATAL = 20

	long startTime;
	//String lastMessage = null;
	int status = 0;
	final StringBuffer buffer = new StringBuffer();
	final NumberFormat numberFormat = NumberFormat.getIntegerInstance();
	
			
	public static void main(String[] args) {
		
		Log log = new Log();
		log.verbosity = 0;
		
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
	
		String line = null;
		while (true){
			
			try {
				line = in.readLine();
			} 
			catch (IOException e) {
			}
			if (line.charAt(0) == 'q')
				break;
			log.append((int)(Math.random()*5.0),line);
			
		}
		System.out.println(log);
	}

}
