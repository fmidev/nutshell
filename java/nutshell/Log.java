package nutshell;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;

public class Log implements AutoCloseable {

	public static final int FATAL = 1;
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
	protected int verbosity = VERBOSE;

	long startTime;
	final NumberFormat numberFormat = NumberFormat.getIntegerInstance();
	protected int status = 0;

	final private StringBuffer buffer = new StringBuffer();

	public File logFile = null;
	private PrintStream printStream;

	public Log() {
		startTime = System.currentTimeMillis();
		//numberFormat = NumberFormat.getIntegerInstance();
		numberFormat.setMinimumIntegerDigits(5);
		printStream = System.err;
	}

	/** Create a log similar to an existing log.
	 *
	 *  This constructor is handy when creating...
	 *
	 *  Note: @printStream is initialized to @System.err , not to that of the mainLog
	 *
	 * @param log - existing log
	 */
	public Log(Log log) {
		startTime = System.currentTimeMillis();
		verbosity = log.getVerbosity();
		// TODO: copy
		numberFormat.setMinimumIntegerDigits(log.numberFormat.getMinimumIntegerDigits());
		setVerbosity(log.getVerbosity());
		printStream = System.err;
	}

	/** Create a log with a given verbosity.
	 *
	 * @param name
	 */
	public Log(String name) {
		startTime = System.currentTimeMillis();
		numberFormat.setMinimumIntegerDigits(5);
		setVerbosity(VERBOSE);
		this.setName(name);
		printStream = System.err;
	}

	/** Create a log with a given verbosity.
	 *
	 * @param verbosity
	 */
	public Log(int verbosity) {
		this.verbosity = verbosity;
		startTime = System.currentTimeMillis();
		numberFormat.setMinimumIntegerDigits(5);
		printStream = System.err;
	}

	/** Create a log with a name prefixed with the name of a existing log.
	 *
	 *  This constructor is handy when creating a log for a child process.
	 *
	 *  Note: @printStream is initialized to @System.err , not to that of the mainLog
	 *
	 * @param localName
	 * @param mainLog - existing log ("parent" or main log)
	 */
	public Log(String localName, Log mainLog) {
		startTime = System.currentTimeMillis();

		if (mainLog != null){
			setName(mainLog.getName() + '.' + localName);
			setVerbosity(mainLog.getVerbosity());
			numberFormat.setMinimumIntegerDigits(mainLog.numberFormat.getMinimumIntegerDigits());
		}
		else {
			setName(localName);
			setVerbosity(VERBOSE);
			numberFormat.setMinimumIntegerDigits(5);
		}
		printStream = System.err;
	}


	/**
	 *
	 * @throws Throwable
	 */
	@Override
	public void close()  {
		if (logFile != null){
			warn(String.format("Closing log: %s", logFile));
			this.printStream.close();
		}
		else if (printStream == null) {
			warn(String.format("Closing log (unknow output stream)"));
		}
		else
		{
			// warn("NOT closing log (stdout/stderr): " + this.printStream.toString());
		}
		//System.out.println("closing " + this.getClass().getCanonicalName());
	}

	/*
	@Override
	protected void finalize() throws Throwable {
		System.out.println("finalizing " + this.getClass().getCanonicalName());
	}
	 */

	String name = "";
	
	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}


	public void setVerbosity(int verbosity) {
		this.verbosity = verbosity;
	}

	public void setVerbosity(String verbosity) throws NoSuchFieldException {
		for (Map.Entry<Integer, String> entry: statusCodes.entrySet()){
			if (entry.getValue().equals(verbosity)) {
				this.verbosity = entry.getKey();
				return; // true
			}
		}
		throw new NoSuchFieldException(verbosity);
		//return false;
	}


	/** Return current level of verbosity
	 *
	 * @return
	 */
	public int getVerbosity() {
		return verbosity;
	}

	/** Return current status.
	 *
	 * @return
	 */
	public int getStatus() {
		return status;
	}

	/*
	public Log getChild(String childName){
		Log log = new Log(this.name + "." + childName);
		log.printStream = System.err; //this.printStream;
		log.verbosity = this.verbosity;
		return log;
	}

	 */

	public PrintStream getPrintStream() {
		return this.printStream;
	}

	@Override
	public String toString() {
		return buffer.toString();
	}



	// TODO decoration enum: NONE, VT100, HTML, CSS, static init!
	public boolean VT100 = false;


	public Log fatal(String message){
		return log(FATAL, message);
	}

	public Log error(String message){
		return log(ERROR, message);
	}

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
		else {
			set(status);
		}

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
			if (name != null)
				buffer.append(':').append(' ').append(name);

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

	public boolean logFileIsSet(){
		return (this.logFile != null);
	}

	public void setLogFile(Path path){

		close();
		/*
		 	Todo: there is a chance that something is written no to the log
		 	until a new printStream is set
		 */


		if (path == null){
			this.printStream = System.err;
			this.logFile = null;
			return;
		}

		// PrintStream oldPrintStream = this.printStream;
		try {
			this.logFile = path.toFile();
			FileOutputStream fw = new FileOutputStream(this.logFile);
			this.debug(String.format("Continuing log in file: %s", this.logFile));
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

		// oldPrintStream.close();
		/*
		catch (IndexedException e) {
			//e.printStackTrace(); //this.log.printStream);
			this.log(e);
		}
		 */

	};



	public static void main(String[] args) {
		
		Log log = new Log();
		//log.printStream = null;

		log.note("Starting");

		if (args.length == 0){
			log.debug("No arguments, invoking 'help'");
			System.out.println("Prints log lines until end-of-line");
			System.out.println("Usage: ");
			System.out.println("  argument: <log_level>  #" + statusCodes.entrySet());
			log.warn("Quitting");
			return;
		}

		try {
			log.setVerbosity(args[0]);
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
			System.exit(1);
		}


		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

		try {
			String line = null;
			while (!(line = in.readLine()).isEmpty()) {
				log.log((int) (Math.random() * 5.0), line);
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		// System.out.println(log.buffer.toString()); EMPTY!
		//System.out.println(log);

		//System.out.println(log.getClass().getCanonicalName());

		//Log test = new Log();
		//test.setVerbosity(10);
		//log.warn("Hey!");

	}

}
