package nutshell;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;


/** Logging safer than Log4j

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
public class Log implements AutoCloseable {


	// Keys and values can be String:s or Path:s
	static
	public Map<Path,String> pathMap = new HashMap<Path,String>();  // URL?

	/// Status levels (Error levels) and their colours (optional)
	public enum Status implements Indexed {

		// Fundamental
		// TODO re-organize according to C error levels
		UNDEFINED(0, VT100.Colours.WHITE_BG),
		FATAL(1, VT100.Colours.RED_BG),
		ERROR(2, VT100.Colours.RED),
		WARNING(3, VT100.Colours.YELLOW),
		//FAIL(4,  VT100.compound(VT100.Colours.YELLOW,  VT100.Highlights.ITALIC.bitvalue)), 	/// Action completed unsuccessfully. // ?
		NOTE(5,  VT100.Colours.DEFAULT), // VT100.compound(VT100.Colours.CYAN, VT100.Highlights.DIM.bitvalue)),   	/// Important information
		INFO(6,  VT100.Colours.DEFAULT),  	/// Default color (white) Less important information
		LOG(9, VT100.Colours.DEFAULT, VT100.Highlights.DIM),     /// Sometimes informative messages.
		DEBUG(10, VT100.Colours.DEFAULT, VT100.Highlights.DIM), // VT100.Highlights.RESET),      /// Technical information about the process
		// Extensions
		FAIL(WARNING.level, VT100.Colours.YELLOW,  VT100.Highlights.DIM), 	/// Action completed unsuccessfully. // ?
		SUCCESS(WARNING.level, VT100.Colours.GREEN,  VT100.Highlights.DIM), 	/// Action completed unsuccessfully. // ?
		WAIT(NOTE.level, VT100.Colours.YELLOW,  VT100.Highlights.ITALIC),  	/// Indication of a "weak fail", pending status, leading soon recipient OK, WARNING, or ERROR.
		OK(NOTE.level,    VT100.Colours.GREEN),      /// Action completed successfully.
		SPECIAL(NOTE.level, VT100.Colours.MAGENTA),
		EXPERIMENTAL(NOTE.level, VT100.Colours.CYAN),
		DEPRECATED(NOTE.level, VT100.Colours.CYAN, VT100.Highlights.DIM),
		UNIMPLEMENTED(WARNING.level, VT100.Colours.YELLOW, VT100.Highlights.DIM),
		;

		Status(int level, VT100.Colours colour){
			this(level, colour, VT100.Highlights.RESET); // default
		}

		Status(int level, VT100.Colours colour, VT100.Highlights highlights){
			this.level = level;
			this.colour = colour;
			this.highlights = highlights;
		}


		public final int level;

		@Override
		public int getIndex() {
			return level;
		}

		protected final VT100.Colours colour;
		protected final VT100.Highlights highlights;

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

	/**
	 *
	 class MyCounter extends Counter<Log> {
	 };
	 */

	public enum OutputFormat { // implements  Indexed {
		TEXT,
		COLOUR,
		VT100,
		MAP_URLS,
		HTML;

		/*
		final int bit;

		OutputFormat(){
			bit = 1 << this.ordinal();
		}

		@Override
		public int getIndex() {
			return bit;
		}

		 */

	}

	// public Flags decorations;
	final public Flags decoration = new Flags(OutputFormat.class);


	/** Create a log with a name prefixed with the name of a existing log.
	 *
	 *  This constructor is handy when creating a log for a child process.
	 *
	 *  Note: @printStream is initialized to @System.err , not to that of the mainLog
	 *
	 * @param localName - name
	 * @param verbosity - log level
	 * @param minDigits -
	 *
	 */
	public Log(String localName, int verbosity, int minDigits) {

		startTime = System.currentTimeMillis();

		if (localName != null)
			setName(localName);
		setVerbosity(verbosity);
		numberFormat.setMinimumIntegerDigits(minDigits);
		printStream = System.err;
		// decoration = new Flags();
	}

	public Log() {
		this("", Status.LOG.level, 5);
	}

	/** Create a log with a given name.
	 *
	 * @param name
	 */
	public Log(String name) {
		this(name, Status.LOG.level, 5);
	}

	/** Create a log similar to an existing log.
	 *
	 *  This constructor is handy when creating...
	 *
	 *  Note: @printStream is initialized to @System.err
	 *
	 * @param log - existing log
	 */
	public Log(Log log) {
		this("", log.getVerbosity(), log.numberFormat.getMinimumIntegerDigits());
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


	@Override
	protected void finalize() throws Throwable {
		close();
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

	public Log fail(String message){
		return log(Status.FAIL, message);
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

	public Log success(String msg){
		return log(Status.SUCCESS, msg);
	}

	/*
	public Log success(boolean isTrue){
		if (isTrue)
			return log(Status.OK, null);
		else
			return log(Status.FAIL, null);
	}
	 */



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

	class PathDetector {

		String prefix = "";
		Path path = null;
		String dir = null;
		String filename = null;
		String remainingLine = "";

		PathDetector(String remainingLine){
			this.remainingLine = remainingLine;
		}

		boolean next(){
			Matcher m = FileUtils.filePathRe.matcher(remainingLine);
			if (m.matches()){
				prefix = m.group(1);
				dir = m.group(2);
				filename = m.group(m.groupCount()-1);
				if (dir == null)
					dir = ".";
				if (filename == null)
					filename = "";
				path = Paths.get(dir, filename);
				remainingLine = m.group(m.groupCount()); // note "N+1"
				if (remainingLine == null)
					remainingLine = "";
				return true;
			}
			else {
				return false;
			}
		};

		@Override
		public String toString() {
			return "PathDetector{" +
					"prefix='" + prefix + '\'' +
					", path=" + path +
					", line='" + remainingLine + '\'' +
					'}';
		}
	}

	/** Start log line with a status label and time stamp (milliseconds).
	 *
	 * @param status
	 * @return
	 */
	protected void appendProlog(Status status) {

		if (this.decoration.involves(OutputFormat.VT100)) {
			if (this.decoration.involves(OutputFormat.COLOUR)) {
				//buffer.append(VT100.Highlights.BRIGHT); // consider conditional
				//buffer.append(VT100.Codes.UNDERLINE);
				buffer.append(status.colour);
			}
		} else if (this.decoration.involves(OutputFormat.HTML)) {
			buffer.append(SimpleHtml.Tag.PRE.start());
		}

		buffer.append("[").append(numberFormat.format(System.currentTimeMillis() - startTime)).append("] ");

		//buffer.append(String.format("%7s", statusCodes.get(this.status)));
		buffer.append(String.format("%7s", status));

		if (name != null)
			buffer.append(':').append(' ').append(name);

	}

	/**
	 * @param message
	 * @param <E>
	 * @return
	 */
	protected <E> void appendMessage(E message){

		if (this.decoration.involves(OutputFormat.HTML)){
			Path root = Paths.get("/opt/nutshell");
			String s = message.toString();
			buffer.append(' ');
			buffer.append(SimpleHtml.Tag.B.start());

			PathDetector pd = new PathDetector(message.toString());
			while (pd.next()){
				buffer.append(pd.prefix);
				//Map<String,String>
				Path relative = root.relativize(pd.path);
				buffer.append(String.format("<a href=\"%s\">%s</a>%n", relative, pd.path.getFileName())); //SimpleHtml.Tag.H3.start());
				//buffer.append(pd.path);
				//buffer.append("}"); //SimpleHtml.Tag.H3.end());
			}
			buffer.append(pd.remainingLine);

			// buffer.append(s);
			buffer.append(SimpleHtml.Tag.B.end());
		}
		else {
			buffer.append(' ').append(message);
		}

	}

	/**
	 *   Ensures that printStream exists
	 *
	 */
	protected void flushBuffer() {

		// Ensure printStream to avoid infinite buffer growth
		// TODO: design control for buffer size.
		// TODO: consider: if (size > 1M), clear(), and only light warning in stderr...
		getPrintStream();

		printStream.print(buffer.toString());
		buffer.setLength(0);  // TODO CLEAR?

	}


	/**
	 * @param status
	 * @param message
	 * @param <E>
	 * @return
	 */
	protected <E> Log flush(Status status, E message){

		if (this.decoration.involves(OutputFormat.VT100)) {

			if (status.highlights != VT100.Highlights.RESET) // default
				buffer.append(status.highlights);

			if (this.decoration.involves(OutputFormat.COLOUR)) {
				buffer.append(status.colour);
			}
		}

		buffer.append("[").append(numberFormat.format(System.currentTimeMillis() - startTime)).append("] ");

		//buffer.append(String.format("%7s", statusCodes.get(this.status)));
		buffer.append(String.format("%7s", status));

		if (name != null)
			buffer.append(':').append(' ').append(name);

		if (message != null) {
			if (!this.decoration.involves(OutputFormat.MAP_URLS)){
				buffer.append(' ').append(message);
			}
			else {
				PathDetector pd = null;
				try {
					buffer.append(' ');

					pd = new PathDetector(message.toString());
					while (pd.next()){

						buffer.append(pd.prefix);

						String result = pd.path.toString(); // default
						for (Map.Entry<Path, String> entry: pathMap.entrySet()){
							Path p = entry.getKey();
							if (pd.path.startsWith(p)){
								Path relative = p.relativize(pd.path);
								//result = String.format("<a href=\"%s\">%s</a>", relative, pd.filename); //SimpleHtml.Tag.H3.start());
								result = entry.getValue() + relative.toString();
								break;
							}
						}

						//if (result == null)
						//result = String.format("<b>%s</b>", pd.path);

						buffer.append(result);

						//buffer.append(pd.path);
						//buffer.append("}"); //SimpleHtml.Tag.H3.end());
					}
					buffer.append(pd.remainingLine);  // = trailing part of the line
				}
				catch (Exception e){
					System.err.println(pd);
					e.printStackTrace(getPrintStream());
					//System.err.print(e.getMessage());
				}
			}
			//appendMessage(message);
		}

		if (this.decoration.involves(OutputFormat.VT100)){
			buffer.append(VT100.Highlights.RESET);
		}

		buffer.append('\n');

		flushBuffer();

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

	@Deprecated
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

	final protected StringBuffer buffer = new StringBuffer();

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
			this.fileOutputStream = new FileOutputStream(this.logFile);
			this.debug(String.format("Continuing log in file: %s", this.logFile));

			this.printStream = new PrintStream(this.fileOutputStream);
			//this.log.printStream = System.err;
			//this.setVerbosity(Status.DEBUG); //?
			//this.printStream.println(SimpleHtml.Tag.HTML.start());
			//this.printStream.println(SimpleHtml.Tag.PRE.start());
			this.debug(String.format("Started log file: %s", this.logFile));
		}
		catch (IOException e) {
			e.printStackTrace(); //this.log.printStream);
			//this.log(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
			//this.log(HttpLog.HttpStatus.INTERNAL_SERVER_ERROR,
			this.error(String.format("Failed in creating log file: %s", path));
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

	private FileOutputStream fileOutputStream;

	public PrintStream getPrintStream() {
		if (printStream == null){
			printStream = System.err;
			printStream.print(buffer.toString()); // "Copy" the prefix (Do not clear it, yet!)
			printStream.append("NOTE: printStream undefined, using standard error.\n");
		}
		return printStream;
	}

	// TODO decoration enum: NONE, VT100, HTML, CSS, static init!
	// public boolean COLOURS = false;
	// public OutputFormat decoration = ne;

	/** Close file (printStream).
	 *
	 * @throws Throwable
	 */
	@Override
	public void close()  {

		if (logFile != null){
			info(String.format("Closing logFile: %s", logFile));
			//this.printStream.close(); // By hazard, can be System.err ?
		}

		if (this.printStream != null){
			/* BUG: this is not fileOutputStream ?
			if (decoration.involves(OutputFormat.HTML)) {
				this.printStream.println(SimpleHtml.Tag.PRE.end());
				this.printStream.println(SimpleHtml.Tag.HTML.end());
			}

			 */
			if ((this.printStream != System.err) && (this.printStream != System.out)){
				this.printStream.close();
				this.printStream = null;
			}
		}
		else {
			warn(String.format("Closing log (unknown output PrintStream)"));
		}

		if (this.fileOutputStream != null){
			try {
				this.fileOutputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			this.fileOutputStream = null;
		}

		logFile = null;
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
		log.decoration.set(OutputFormat.COLOUR);
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
