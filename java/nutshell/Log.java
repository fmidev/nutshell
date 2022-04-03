package nutshell;

import java.io.*;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;



public class Log implements AutoCloseable {

	/// Status levels (Error levels) and their colours (optional)
	public enum Status implements Indexed {

		/*
		INSPIRED BY:
		#define	LOG_EMERG	0	// system is unusable //
		#define	LOG_ALERT	1	// action must be taken immediately //
		#define	LOG_CRIT	2	// critical conditions //
		#define	LOG_ERR		3	// error conditions //
		#define	LOG_WARNING	4	// warning conditions //
		#define	LOG_NOTICE	5	// normal but significant condition  //
		#define	LOG_INFO	6	// informational //
		#define	LOG_DEBUG	7	// debug-level messages //
		 */

		// Fundamental
		// TODO re-organize according to C error levels
		UNDEFINED(0, VT100.Colours.WHITE_BG),
		FATAL(1, VT100.Colours.RED_BG),
		ERROR(2, VT100.Colours.RED),
		WARNING(3, VT100.Colours.YELLOW),
		//FAIL(4,  VT100.compound(VT100.Colours.YELLOW,  VT100.Highlights.ITALIC.bitvalue)), 	/// Action completed unsuccessfully. // ?
		NOTE(5,  VT100.Colours.DEFAULT), // VT100.compound(VT100.Colours.CYAN, VT100.Highlights.DIM.bitvalue)),   	/// Important information
		INFO(6,  VT100.compound(VT100.Colours.DEFAULT,0)),  	/// Default color (white) Less important information
		LOG(9, VT100.Highlights.DIM),     /// Sometimes informative messages.
		DEBUG(10, VT100.compound(VT100.Colours.DEFAULT, VT100.Highlights.DIM.bitvalue)), // VT100.Highlights.RESET),      /// Technical information about the process
		// Extensions
		FAIL(WARNING.level, VT100.compound(VT100.Colours.YELLOW,  VT100.Highlights.ITALIC.bitvalue)), 	/// Action completed unsuccessfully. // ?
		WAIT(NOTE.level, VT100.compound(VT100.Colours.YELLOW,  VT100.Highlights.ITALIC.bitvalue)),  	/// Indication of a "weak fail", pending status, leading soon recipient OK, WARNING, or ERROR.
		OK(NOTE.level,    VT100.Colours.GREEN),      /// Action completed successfully.
		SPECIAL(NOTE.level, VT100.Colours.MAGENTA),
		EXPERIMENTAL(NOTE.level, VT100.Colours.CYAN),
		DEPRECATED(NOTE.level, VT100.compound(VT100.Colours.CYAN, VT100.Highlights.DIM.bitvalue)),
		UNIMPLEMENTED(WARNING.level,  VT100.compound(VT100.Colours.YELLOW, VT100.Highlights.DIM.bitvalue)),
		;

		Status(int level, VT100.Control colour){
			this.level = level;
			this.colour = colour;
		}

		public final int level;

		@Override
		public int getIndex() {
			return level;
		}

		private final VT100.Control colour;

	}

	final static Map<Integer, Status> statusCodes = new HashMap<>();
	final static Map<String, Status>  statusCodesByName = new HashMap<>(); // TODO rename
	static {
		for (Log.Status s: Status.values()) {
			// Accept the first entry of each level.
			if (!statusCodes.containsKey(s.level))
				statusCodes.put(s.level, s);
			statusCodesByName.put(s.name(), s);
		}
	}


	/*
	public static final int FATAL = 1;
	public static final int ERROR = 2;
	public static final int WARNING = 3;
	public static final int FAIL = 4;
	public static final int NOTE = 5;
	public static final int INFO = 6;
	public static final int WAIT = 7;
	public static final int OK = 8;
	public static final int VERBOSE = 9;
	public static final int DEBUG = 10;
	*/


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
		setVerbosity(Status.LOG.level);
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
			setVerbosity(Status.LOG);
			numberFormat.setMinimumIntegerDigits(5);
		}
		printStream = System.err;
	}

	public Log fatal(String message){
		return log(Status.FATAL, message);
	}

	public Log error(String message){
		return log(Status.ERROR, message);
	}

	public Log warn(String message){
		return log(Status.WARNING, message);
	}

	public Log fail(){
		return log(Status.FAIL, null);
	}

	public Log note(String message){
		return log(Status.NOTE, message);
	}

	public Log info(String message){
		return log(Status.INFO, message);
	}

	public Log ok(String msg){
		return log(Status.OK, msg);
	}

	public Log verbose(String message){
		return log(Status.LOG, message);
	}

	public Log debug(String message){
		return log(Status.DEBUG, message);
	}

	public Log special(String msg){
		return log(Status.SPECIAL, msg);
	}

	public Log deprecated(String msg){
		return log(Status.DEPRECATED, msg);
	}

	public Log experimental(String msg){
		return log(Status.EXPERIMENTAL, msg);
	}

	public Log success(boolean isTrue){
		if (isTrue)
			return log(Status.OK, null);
		else
			return log(Status.FAIL, null);
	}



	/**
	 *
	 * @param status
	 * @param message
	 * @param <E>
	 * @return
	 */
	public <E> Log log(Status status, E message){
		setStatus(status);
		if (this.status > verbosity) {
			return this;
		}
		return flush(status, message);
	}


	/**
	 *
	 * @param status
	 * @param message
	 * @param <E>
	 * @return
	 */
	public <E> Log log(int status, E message){
		setStatus(status);
		return log(message);
	}

	/**
	 *
	 * @param status
	 * @param message
	 * @param <E>
	 * @return
	 */
	public <E> Log log(Indexed status, E message){
		setStatus(status.getIndex());
		return log(message);
	}

	/**
	 *
	 * @param message
	 * @param <E>
	 * @return
	 */
	public <E> Log log(E message) {
		if (this.status > verbosity) {
			return this;
		}
		Status s = statusCodes.getOrDefault(status, Status.DEBUG);
		return flush(s, message);
	}

	/**
	 * @param status
	 * @param message
	 * @param <E>
	 * @return
	 */
	protected <E> Log flush(Status status, E message){

		if (this.COLOURS){
			//buffer.append(VT100.Highlights.BRIGHT); // consider conditional
			//buffer.append(VT100.Codes.UNDERLINE);
			buffer.append(status.colour);
		}

		buffer.append("[").append(numberFormat.format(System.currentTimeMillis() - startTime)).append("] ");

		//buffer.append(String.format("%7s", statusCodes.get(this.status)));
		buffer.append(String.format("%7s", status));

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

		if (this.COLOURS){
			buffer.append(VT100.Highlights.RESET);
		}
		buffer.append('\n');

		printStream.print(buffer.toString());
		buffer.setLength(0);  // TODO CLEAR?

		return this;
	}

	String name = "";

	/** Set a name for this log, typically the name of a function or module.
	 *
	 * @param name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/** Get the name of this log.
	 */
	public String getName() {
		return name;
	}

	protected int status = Status.UNDEFINED.level;
	//protected Status status = Status.UNDEFINED;

	/// Set status level
	void setStatus(int status) {
		this.status = status;
	}

	/// Set status level
	void setStatus(Indexed status) {
		this.status = status.getIndex();
	}

	/** Return current status.
	 *
	 * @return
	 */
	public int getStatus() {
		return status;
	}

	public String getStatusString() {
		if (statusCodes.containsKey(status))
			return statusCodes.get(status).name();
		else
			return "---";
	}


	/// Verbose level. Messages equal or lower value than will be communicated.
	protected int verbosity = Status.LOG.level;

	public void setVerbosity(Status verbosity) {
		this.verbosity = verbosity.level;
	}

	public void setVerbosity(int verbosity) {
		this.verbosity = verbosity;
	}

	public void setVerbosity(String verbosity) throws NoSuchFieldException {

		try {
			int level = Integer.parseInt(verbosity);
			this.setVerbosity(level);
		}
		catch (NumberFormatException e) {
			for (Log.Status s: Status.values()) {
				if (s.name().equals(verbosity)){
					this.setVerbosity(s.level);
					return;
				}
				//statusCodes.put(s.level, s);
			}
			/*
			for (Map.Entry<Integer, Status> entry: statusCodes.entrySet()){
				if (entry.getValue().equals(verbosity)) {
					this.verbosity = entry.getKey();
					return; // true
				}
			}

			 */
			this.note(String.format("Use numeric levels or keys: %s", Log.statusCodes.entrySet().toString()));
			this.warn(String.format("No such verbosity level: %s", verbosity));
			this.warn(String.format("Retaining level: %s", Log.statusCodes.get(this.getVerbosity())));
			//return;
			throw new NoSuchFieldException(verbosity);
		}

	}


	/** Return current level of verbosity
	 *
	 * @return
	 */
	public int getVerbosity() {
		return verbosity;
	}

	final long startTime;

	final NumberFormat numberFormat = NumberFormat.getIntegerInstance();

	final private StringBuffer buffer = new StringBuffer();

	public void clearBuffer(){
		buffer.setLength(0);
		//buffer.delete(0,buffer.length()-1);
	}

	public File logFile = null;

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
			this.setVerbosity(Status.DEBUG);
			this.debug(String.format("Started log file: %s", this.logFile));
		}
		catch (IOException e) {
			e.printStackTrace(); //this.log.printStream);
			//this.log(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
			//this.log(HttpLog.HttpStatus.INTERNAL_SERVER_ERROR,
			this.error(
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

	private PrintStream printStream;

	public PrintStream getPrintStream() {
		return this.printStream;
	}

	// TODO decoration enum: NONE, VT100, HTML, CSS, static init!
	public boolean COLOURS = false;

	/** Close file (printStream).
	 *
	 * @throws Throwable
	 */
	@Override
	public void close()  {
		if (logFile != null){
			info(String.format("Closing log: %s", logFile));
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

	@Override
	public String toString() {
		return buffer.toString();
	}

	/*
	public Log getChild(String childName){
		Log log = new Log(this.name + "." + childName);
		log.printStream = System.err; //this.printStream;
		log.verbosity = this.verbosity;
		return log;
	}

	 */





	public static void main(String[] args) {


		Log log = new Log();
		//log.printStream = null;
		log.COLOURS = true;
		log.setVerbosity(Status.DEBUG);

		log.note("Starting");

		if (args.length == 0){
			log.debug("No arguments, invoking 'help'");
			System.out.println("Prints log lines until end-of-line");

			System.out.println("Statuses: ");
			for (Log.Status s: Status.values()) {
				System.out.println(String.format("\t%s %s = %d %s", s.colour, s, s.level, VT100.Highlights.RESET));
			}

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

		log.special("Now it starts");
		log.deprecated(".. nut be careful..");
		log.experimental(".. errøœr will come!");

		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

		try {
			String line = null;
			while (((line = in.readLine()) != null) && (!line.isEmpty())) {
				int randomKey = (int) (Math.random() * log.getVerbosity()+1);
				//statusCodes.containsKey(randomKey);
				//log.log((int) (Math.random() * 5.0), line);
				log.log(statusCodes.getOrDefault(randomKey, Status.UNDEFINED), line);
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
