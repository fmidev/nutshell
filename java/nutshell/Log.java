package nutshell;

import javax.servlet.http.HttpServletResponse;
import javax.xml.ws.Response;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;

public class Log {

	
	public Log() {
		startTime = System.currentTimeMillis();
		numberFormat.setMinimumIntegerDigits(5);
		printStream = System.err;
	}

	public Log(String s) {
		startTime = System.currentTimeMillis();
		numberFormat.setMinimumIntegerDigits(5);
		this.setOwner(s);
		printStream = System.err;
	}

	public Log(int verbosity) {
		this.verbosity = verbosity;
		startTime = System.currentTimeMillis();
		numberFormat.setMinimumIntegerDigits(5);
		printStream = System.err;
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
		return log(ERROR, message);
	}

	/*
	public Log error(String message, int i){
		append(ERROR, message);
		System.exit(i);
		return this;
	}
	 */

	public Log warn(String message){
		return log(WARNING, message);
	}

	public Log fail(){
		return log(FAIL, null);
	}

	public Log note(String message){
		return log(NOTE, message);
	}

	public Log info(String message){
		return log(INFO, message);
	}


	public Log success(boolean isTrue){
		if (isTrue)
			return log(OK, null);
		else
			return log(FAIL, null);
	}

	public Log ok(String msg){
		return log(OK, msg);
	}
	
	public Log debug(String message){
		return log(DEBUG, message);
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

	// TODO decoration enum: NONE, VT100, HTML, CSS, static init!
	public boolean VT100 = false;

	public File logFile = null;

	//final static Map<String, Integer> ssCodes; // = ClassUtils.getConstants(Log.class); //new HashMap<>();
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

	/**
	 *
	 * @param status
	 * @param message
	 * @param <E>
	 * @return
	 */
	public <E> Log log(int status, E message){

		/// Consider validate() cf. HttpLog.handleHttpMsg()
		if ((status < 0) || (status > 10)) {
			// or warn? (with infinite loop risk)
			log(String.format("Illegal LOG level: %d", status));  // Or: return false?
		}

		set(status);

		return log(message);

	}

	/**
	 *
	 * @param message
	 * @param <E>
	 * @return
	 */
	public <E> Log log(E message){

		if (this.status <= verbosity){
			buffer.append("[").append(numberFormat.format(System.currentTimeMillis() - startTime)).append("] ");
			buffer.append(String.format("%7s", statusCodes.get(this.status)));
			if (owner != null)
				buffer.append(':').append(' ').append(owner);

			// Ensure printStream to avoid infinite buffer growth
			// TODO: design control for buffer size.
			// TODO: consider: if (size > 1M), clear(), and only light warning in stderr...
			if (printStream == null){
				printStream = System.err;
				printStream.print(buffer.toString()); // "copy" prefix (ie. not do clear it)
				printStream.append("NOTE: printStream undefined, using standard error.\n");
			}



			if (message != null)
				buffer.append(' ').append(message);
				//buffer.append('\t').append(message);

			buffer.append('\n');
			//if (printStream != null){
			printStream.print(buffer.toString());
			buffer.setLength(0);  // TODO CLEAR?
			//}
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

	public void setLogFile(Path path){

		if (this.logFile != null){
			// TODO: close!
		}

		if (path == null){
			this.printStream = System.err;
			this.logFile = null;
			return;
		}

		//Path logPath = cacheRoot.resolve(relativeLogPath);
		try {
			//Path logPath = ensureWritableFile(cacheRoot, this.relativeLogPath);
			this.logFile = path.toFile();
			this.debug(String.format("Continuing log in file: %s", this.logFile));
			FileOutputStream fw = new FileOutputStream(this.logFile);
			this.printStream = new PrintStream(fw);
			//this.log.printStream = System.err;
			this.setVerbosity(Log.DEBUG);
			this.debug(String.format("Started log file: %s", this.logFile));
		}
		catch (IOException e) {
			e.printStackTrace(); //this.log.printStream);
			this.log(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					String.format("Failed in creating log file: %s", path));
		}
		/*
		catch (IndexedException e) {
			//e.printStackTrace(); //this.log.printStream);
			this.log(e);
		}
		 */

	};

	/// TODO: FATAL = 20

	long startTime;
	//String lastMessage = null;
	protected int status = 0;
	final protected StringBuffer buffer = new StringBuffer();
	final NumberFormat numberFormat = NumberFormat.getIntegerInstance();

	public void setVerbosity(int verbosity) {
		this.verbosity = verbosity;
	}

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
			log.log((int)(Math.random()*5.0),line);
			
		}
		System.out.println(log);
	}

}
