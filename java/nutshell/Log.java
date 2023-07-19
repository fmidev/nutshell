package nutshell;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
		UNDEFINED(0, TextOutput.Colour.WHITE),
		//FATAL(1, TextDecoration.Colour.RED_BG),
		FATAL(1, TextOutput.Colour.BLUE, TextOutput.Highlight.BRIGHT),
		CRITICAL(2, TextOutput.Colour.MAGENTA, TextOutput.Highlight.BRIGHT),
		ERROR(3, TextOutput.Colour.RED, TextOutput.Highlight.BRIGHT),
		WARNING(4, TextOutput.Colour.YELLOW, TextOutput.Highlight.BRIGHT),
		//FAIL(4,  VT100.compound(TextDecoration.Colour.YELLOW,  TextDecoration.Highlight.ITALIC.bitvalue)), 	/// Action completed unsuccessfully. // ?
		NOTE(5,  TextOutput.Colour.DEFAULT), // VT100.compound(TextDecoration.Colour.CYAN, TextDecoration.Highlight.DIM.bitvalue)),   	/// Important information
		INFO(6,  TextOutput.Colour.DEFAULT),  	/// Default color (white) Less important information
		LOG(9, TextOutput.Colour.DEFAULT, TextOutput.Highlight.DIM),     /// Sometimes informative messages.
		DEBUG(10, TextOutput.Colour.GRAY, TextOutput.Highlight.DIM), // TextDecoration.Highlight.RESET),      /// Technical information about the process
		// Extensions
		FAIL(WARNING.level, TextOutput.Colour.YELLOW), 	/// Action completed unsuccessfully. // ?
		SUCCESS(WARNING.level, TextOutput.Colour.GREEN), 	/// Action completed unsuccessfully. // ?
		WAIT(NOTE.level, TextOutput.Colour.YELLOW,  TextOutput.Highlight.ITALIC),  	/// Indication of a "weak fail", pending status, leading soon recipient OK, WARNING, or ERROR.
		OK(NOTE.level,    TextOutput.Colour.GREEN, TextOutput.Highlight.BRIGHT),      /// Action completed successfully.
		SPECIAL(NOTE.level, TextOutput.Colour.MAGENTA,  TextOutput.Highlight.ITALIC),
		EXPERIMENTAL(NOTE.level, TextOutput.Colour.CYAN,  TextOutput.Highlight.ITALIC),
		DEPRECATED(NOTE.level, TextOutput.Colour.CYAN, TextOutput.Highlight.DIM),
		UNIMPLEMENTED(WARNING.level, TextOutput.Colour.YELLOW, TextOutput.Highlight.DIM),
		;

		Status(int level, TextOutput.Colour colour){
			this(level, colour, TextOutput.Highlight.RESET); // default
		}

		Status(int level, TextOutput.Colour colour, TextOutput.Highlight highlights){
			this.level = level;
			this.colour = colour;
			this.highlights = highlights;
		}


		public final int level;

		@Override
		public int getIndex() {
			return level;
		}

		protected final TextOutput.Colour colour;
		protected final TextOutput.Highlight highlights;

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

	static
	final protected TextOutput plainText = new TextOutput();

	static
	final protected TextOutput.Vt100 vt100Text = new TextOutput.Vt100();

	static
	final protected TextOutput.Html htmlText = new TextOutput.Html();

	public TextOutput textOutput = vt100Text; // plain


	// public Flags decorations;
	final
	public Flags decoration = new Flags(TextOutput.Options.class);

	public void setDecoration(String s) throws NoSuchFieldException, IllegalAccessException {
		decoration.set(s);
	};
	public void setDecoration(TextOutput.Options... dec){
		decoration.set(dec);
	}

	public void setDecoration(Flags flags)  {
		decoration.set(flags);
	};

	/** Create a log with a name prefixed with the name of a existing log.
	 *
	 *  This constructor is handy when creating a log for a child process.
	 *
	 *  Note: @printStream is initialized to @System.err , not to that of the mainLog
	 *
	 * @param localName - name
	 * @param verbosity - log level
	 * @param minDigits - digits reserved for timestamp, which is seconds, like [17,025]
	 *
	 */
	public Log(String localName, int verbosity, int minDigits) {
		startTime = System.currentTimeMillis();
		if (localName != null)
			setName(localName);
		setVerbosity(verbosity);
		numberFormat.setMinimumIntegerDigits(minDigits);
		printStream = System.err;
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
		this("", verbosity, 5);
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

		// static
		final Pattern filePathRegExp = Pattern.compile("^([^/]*)((/\\w*)+/)(\\S+\\.[a-z0-9]+)?(\\W.*)?$",
				Pattern.CASE_INSENSITIVE);

		PathDetector(String remainingLine){
			this.remainingLine = remainingLine;
		}

		boolean next(){
			Matcher m = filePathRegExp.matcher(remainingLine);
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

		textOutput.startElem(buffer);
		buffer.append("[").append(numberFormat.format(System.currentTimeMillis() - startTime)).append("] ");
		textOutput.endElem(buffer);

		if (true){
			textOutput.setHighlights(status.highlights);
		}

		if (decoration.isSet(TextOutput.Options.COLOUR)){
			textOutput.setColour(status.colour);
			if (status.colour != TextOutput.Colour.DEFAULT) {
				//this.textOutput.highlights.add(TextOutput.Highlight.BRIGHT);
			}
		}

		// this.textOutput.startSection(buffer); // TODO: Log.startFile()
		textOutput.highlights.add(TextOutput.Highlight.REVERSE);

		textOutput.startElem(buffer);
		buffer.append(String.format("%7s", status));
		buffer.append(':').append(' ').append(name);
		textOutput.endElem(buffer);


		//if (true){
		textOutput.setHighlights(status.highlights);
		//}

		textOutput.startElem(buffer);
		buffer.append(':').append(' ');


		if (message != null){
			if (this.decoration.isSet(TextOutput.Options.URLS)){
				PathDetector pd = null;
				try {
					buffer.append(' ');

					pd = new PathDetector(message.toString());
					while (pd.next()) {

						buffer.append(pd.prefix);

						String label = "";
						String path = pd.path.toString(); // default
						for (Map.Entry<Path, String> entry : pathMap.entrySet()) {
							Path p = entry.getKey();
							if (pd.path.startsWith(p)) {
								Path relative = p.relativize(pd.path);
								label = relative.getFileName().toString();
								path = entry.getValue() + relative.toString();
								break;
							}
						}
						this.textOutput.appendLink(label, path, buffer);


						//buffer.append(result);

					}
					buffer.append(pd.remainingLine);  // = trailing part of the line

				}
				catch (Exception e) {
					System.err.println(pd);
					e.printStackTrace(getPrintStream());
					//System.err.print(e.getMessage());
				}
			}
			else {
				this.textOutput.append(message.toString(), buffer);
			}
		}

		this.textOutput.endElem(buffer);

		//this.textOutput.endSection(buffer); // TODO: Log.startFile()
		this.textOutput.reset(); // check


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

	/** Return current status as a numeric value of enum type @{@link Log.Status}
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

	public void setVerbosity(String verbosity, boolean lenient) throws NoSuchFieldException {

		try {
			int level = Integer.parseInt(verbosity);
			this.setVerbosity(level);
		}
		catch (NumberFormatException e) {

			try {
				setVerbosity(Log.Status.valueOf(verbosity));
			}
			catch (IllegalArgumentException e2){
				if (lenient){
					this.note(String.format("Use numeric levels or keys: %s", Log.statusCodes.entrySet().toString()));
					this.fail(String.format("No such verbosity level: %s", verbosity));
					this.warn(String.format("Retaining level: %s", Log.statusCodes.get(this.getVerbosity())));
				}
				else {
					throw new NoSuchFieldException(verbosity);
				}
			}
			/*
			for (Log.Status s: Status.values()) {
				if (s.name().equals(verbosity)){
					this.setVerbosity(s.level);
					return;
				}
				//statusCodes.put(s.level, s);
			}

			 */
			//return;
		}

	}

	public void setVerbosity(String verbosity) throws NoSuchFieldException {
		setVerbosity(verbosity, false);
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
			textOutput.startSection(buffer);
			this.debug(String.format("Started this log: %s", this.logFile));
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
			if (buffer.length() > 0){
				// Note: matching the section start/end not guaranteed.
				// Consider skipping this, leaving for user to start and end the tags.
				textOutput.endSection(buffer);
				this.printStream.print(buffer);
			}

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
			//System.err.println("test1");
			this.fileOutputStream = null;
			//System.err.println("test2");
		}

		logFile = null;
		//System.out.println("closing " + this.getClass().getCanonicalName());
	}

	@Override
	public String toString() {
		return buffer.toString();
	}

	public TextOutput.Format getFormat() {
		return textOutput.getFormat();
	}

		/** Set formatting: plain TEXT, VT100 text, or HTML.
         *
         */
	public void setFormat(TextOutput.Format fmt) {
		// System.err.printf("Format: %s%n", fmt);

		switch (fmt){
			case DEFAULT:
				// no break
			case TEXT:
				textOutput = Log.plainText;
				break;
			case VT100:
				textOutput = Log.vt100Text;
				break;
			case HTML:
				textOutput = Log.htmlText;
				break;
			default:
				throw new IllegalStateException("Unexpected value: " + fmt); // impossible...
		}

	}

	/** Set decoration, verbosity, and format.
	 *
	 * @param value – comma-separated string of enum keywords.
	 */
	public void set(String value){

		Flags deco = new Flags(TextOutput.Options.class);

		for (String s: value.split(",")){

			System.err.println("STr:" + s);

			if (s.isEmpty()){
				decoration.clear();
				continue;
			}

			try {
				setVerbosity(s);
				//setVerbosity(Log.Status.valueOf(s));
				// server Log.debug(String.format("%s: updated verbosity: %d", getName(), serverLog.getVerbosity()));
				System.err.println("Yeah, verbosity:" + getVerbosity());
				continue;
			}
			catch (Exception e){
			}


			try {
				deco.add(s);
				setDecoration(deco); // overrides
				// serverL og.debug(String.format("%s: updated decoration: %s", getName(), serverLog.decoration));
				continue;
			}
			catch (Exception e){
			}

			try {
				setFormat(TextOutput.Format.valueOf(s));
				//server Log.debug(String.format("%s: updated format: %s", getName(), serverLog.getFormat()));
				continue;
			}
			catch (Exception e){
			}

			//throw new RuntimeException(String.format("%s: unsupported Log parameter %s, see --help log",
			throw new RuntimeException(String.format("%s: unsupported Log parameter %s", getName(), value));
			// serve rLog.error(String.format("%s: unsupported Log parameter %s, see --help log", getName(), value));

		}
	}



	public static void main(String[] args) {


		Log log = new Log();
		//log.printStream = null;
		log.decoration.set(TextOutput.Options.COLOUR);
		log.setVerbosity(Status.DEBUG);

		log.note("Starting");

		if (args.length == 0){
			log.debug("No arguments, invoking 'help'");
			System.out.println("Prints log lines until end-of-line");

			System.out.println("Status levels: ");
			for (Log.Status s: Status.values()) {
				// System.out.println(String.format("\t%s %s = %d %s", s.colour, s, s.level, TextOutput.Highlight.RESET));
				log.log(s, String.format("\t%d = %s (%s)", s.level, s, s.colour));
			}

			System.out.println("Usage:");
			System.out.println("  argument: <log_level>  # Any of these: " + statusCodes.entrySet());
			System.out.println("Example:");
			System.out.println("\tls | java -cp out/production/nutshell/  nutshell.Log DEBUG");
			System.out.println("\tjava -cp out/production/nutshell/  nutshell.Log INFO,,VT100");
			log.warn("Quitting");
			return;
		}

		try {
			log.set(args[0]);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		/*
		try {
			log.setVerbosity(args[0]);
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
			System.exit(1);
		}
		*/

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
