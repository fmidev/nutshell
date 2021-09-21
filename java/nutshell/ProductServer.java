package nutshell;

import sun.misc.Signal;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

//import javax.servlet.http.HttpServletResponse;


/** Extends Cache by not only storing and retrieving existing files,
 *  but also generating them on request. 
 * 
 *  Principles. A product, hence a product request, has the following three following 
 *  parameter types:
 *  - product name
 *  - time (optional; applies only recipient dynamic products)
 *  - product-specific parameters
 *  
 *  Product name, PRODUCT_ID, can be identified with a respective product generator.
 *  Mostly, meteorological products have at least one associated moment of time, 
 *  TIMESTAMP, some may have many. Static products like geographical 
 *  map do not have a timestamp. On the other hand, some meteorological products may 
 *  have several timestamps, like computing time and valid time.
 * 
 * @author Markus Peura fmi.fi Jan 26, 2011
 *
 * TODO: swallows exceptions in command line use?
 * TODO: separate cache at least for java generators
 * TODO: communicate failure with HTTP request codes
 * TODO: design defaults for WRITE
 * 
 * TODO: add i/o handlers (See Image IO Registry), use {@link Serializable}.
 * TODO: simplify REQUEST, add request=GENERATE, 
 * TODO: use SAVE/STREAM in sub-requests
 * TODO: 404: apply directs stream, without redirect. 
 * TODO: add reset/ generatorMap.clear()
 *  NEW
 */


public class ProductServer { //extends Cache {


	final public Log serverLog;

	ProductServer(){
		serverLog = new Log(getClass().getSimpleName());
	}

	/// System side settings.
	public Path confFile    = Paths.get(".", "nutshell.cnf"); //Paths.get("./nutshell.cnf");
	public Path cacheRoot   = Paths.get(".");
	public Path productRoot = Paths.get(".");
	static final public Path cachePrefix = Paths.get("cache");

	/// System side setting.// TODO: conf
	public String inputCmd = "./input.sh";  // NOTE: executed in CWD
	
	/// System side setting. // TODO: conf
	public String generatorCmd = "./generate.sh";  // NOTE: executed in CWD

	//final DateFormat timeStampFormat    = new SimpleDateFormat("yyyyMMddHHmm");
	final DateFormat timeStampDirFormat = 
			new SimpleDateFormat("yyyy"+File.separatorChar+"MM"+File.separatorChar+"dd");

	static public int counter = 0;

	static public int getProcessId(){
		return  ++counter;
	};


	protected void readConfig(){
		readConfig(confFile);
	}

	protected void readConfig(String path) {
		readConfig(Paths.get(path));
	}

	protected void readConfig(Path path){

		Map<String, Object> setup = new HashMap<>();

		try {
			if (path != null) {
				serverLog.debug("Reading setup: " + path.toString());
				MapUtils.read(path.toFile(), setup);
			}
			confFile = path; //Paths.get(path);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			setup.put("confFileError", e.getLocalizedMessage());
		}

		serverLog.debug(setup.toString());

		this.cacheRoot   = Paths.get(setup.getOrDefault("CACHE_ROOT",   ".").toString());
		this.productRoot = Paths.get(setup.getOrDefault("PRODUCT_ROOT", ".").toString());
		// this.generatorScriptName = setup.getOrDefault("CAC",   ".").toString();
		// this.inputScriptName     = setup.getOrDefault("PRO", ".").toString();
	}

	@Override
	public String toString() {
		return MapUtils.getMap(this).toString();
	}


	Path getProductDir(String productID){
		return Paths.get(productID.replace(".", File.separator));
	}
	
	Path getTimestampDir(long time){ // consider DAY or HOUR dirs?
		if (time > 0)
			return Paths.get(timeStampDirFormat.format(time));	
		else
			return Paths.get("");
	}
		
	public static class Actions extends Flags {
		/**
		 *  Checks existence of product generator, memory cache and output directory.
		 *
		 */
		public static final int CHECK  = 1;

		/**
		 *  The product should be finally in memory cache
		 *
		 */
		public static final int MEMORY = 2;

		/**
		 *   The object will be generated, resulting native output (FILE or MEMORY)..
		 *
		 *   If also {@link #DELETE} is requested, the file will be regenerated.
		 */
		public static final int GENERATE = 4;

		/// The object will be generated, unless found in cache.
		//public static final int QUERY = GENERATE|MEMORY;

		/** Product should be saved on disk. System side generator may save it anyway.
		 *   – return immediately in success
		 */
		public static final int FILE = 8;

		/**
		 *  HTTP only: client will be redirected to URL of generated product
		 *  Strictly speaking - needs no file, but a result (file or object).
		 *
		 * @see Nutlet#doGet
		 */
		public static final int REDIRECT = FILE|16;

		/**
		 * Generate product only if it does not exist.
		 *
		 * In program flow, MEMORY and FILE will be checked first, and only if they fail, GENERATION takes place.
		 */
		public static final int MAKE = MEMORY | FILE; // MEMORY|FILE|GENERATE;

		/// Check if product is in cache memory or disk, but do not generate it if missing.
		// public static final int QUERY = MEMORY|FILE;

		/**
		 *  Derive and dump input list (and quit)
		 */
		public static final int INPUTLIST = 64;

		//protected static final int RECURSIVE = 64; // future option
		/**
		 *  Delete the product file on disk (future option: also in memory cache).
		 */
		public static final int DELETE = 128;

		// Output options

		/**
		 *   HTTP: output product is requested as a stream.
		 *
		 * @see Nutlet#doGet
		*/
		public static final int STREAM = 256;

		public boolean isEmpty() {
			return (value == 0);
		}

		/// Computation intensive products are computed in the background; return a notification receipt in HTML format.
		//  public static final int BATCH = 512;

	}


	/**
	 *   A "tray" containing both the product query info and the resulting object if successfully queried.
	 *   Task does not know about the generator. Notice GENERATE and getParamEnv.
	 *
	 */
	public class Task extends Thread { //implements Runnable {
		
		final Log log;
		final ProductInfo info;

		///
		final public Actions actions = new Actions();

		public Path timeStampDir;
		public Path productDir;
		public Path relativeOutputDir;
		public Path relativeOutputPath;
		public Path relativeLogPath;
		public Path outputDir;

		public Path outputDirTmp;
		public Path outputPath;
		public Path outputPathTmp;

		public final Map<String,String> directives = new HashMap<>();

		public final Map<String,String> inputs = new HashMap<>();
		public final Map<String,Object> retrievedInputs = new HashMap<>();

		public File logFile = null;

		/** Product generation task defining a product instance and alternative operations for retreaving it.
		 *
		 * @param productStr
		 * @param actions - definition how a product is retrieved and handled thereafter - @see #Actions
		 * @param parentLog - utility for logging
		 * @throws ParseException - if parsing productStr fails
		 */
		public Task(String productStr, int actions, Log parentLog) throws ParseException {

			this.info = new ProductInfo(productStr);

			this.creationTime = System.currentTimeMillis();

			//this.actions = actions;
			this.actions.set(actions);

			final String filename = info.getFilename();

			// Relative
			this.productDir   = getProductDir(this.info.PRODUCT_ID);
			this.timeStampDir = getTimestampDir(this.info.time);
			this.relativeOutputDir  = this.timeStampDir.resolve(this.productDir);
			this.relativeOutputPath = relativeOutputDir.resolve(filename);
			this.relativeLogPath    = relativeOutputDir.resolve(getFilePrefix() + filename + ".log");

			// Absolute
			this.outputDir     = cacheRoot.resolve(this.relativeOutputDir);
			this.outputDirTmp  = outputDir.resolve("tmp");
			this.outputPath    = outputDir.resolve(filename);
			this.outputPathTmp = outputDirTmp.resolve(getFilePrefix() + filename);

			if (parentLog != null){
				this.log = parentLog.child("["+this.info.PRODUCT_ID+"]");
				this.logFile = null;
			}
			else {
				this.log = new Log(this.info.PRODUCT_ID);
				this.logFile = cacheRoot.resolve(relativeLogPath).toFile();
				try {
					ShellUtils.makeWritableDir(cacheRoot, this.relativeOutputDir);
					logFile.createNewFile();
					FileOutputStream fw = new FileOutputStream(this.logFile);
					this.log.printStream = new PrintStream(fw);
					//System.err.println("LOG FILE:" + logFile.getAbsolutePath());
				} catch (IOException e) {
					e.printStackTrace(this.log.printStream);
					this.log.error("Failed in creating log file: " + this.logFile);
				}
				// Close upon destruction of this Task?
			}

			this.log.debug("started" + this.actions.toString() + " " + this.toString());
			//this.log.debug(this.toString());
			this.result = null;
	
		}

		protected String getFilePrefix() {
			return "";
			//return String.format("%s-%d-", getClass().getSimpleName(), getId());
		}

		/*
		public void setDirectives(Map<String, String> map){
			for (Map.Entry<String,String> entry : map.entrySet() ){ //parameters.entrySet()) {
				String key = entry.getKey().toString();
				if (key.equals(key.toUpperCase())){
					String value = entry.getValue();
					if ((value != null) && (!value.isEmpty()))
						directives.put(key, value);
				}
			}
		}
		 */


		public void setDirectives(Map<String,String[]> map){
			for (Map.Entry<String,String[]> entry : map.entrySet() ){ //parameters.entrySet()) {
				String key = entry.getKey().toString();
				if (key.equals(key.toUpperCase())){
					String[] value = entry.getValue();
					if ((value != null) && (value.length > 0))
						directives.put(key, String.join(",", value));
				}
			}
		}

		public boolean move(File fileSrc, File fileDst){
			this.log.note(String.format("Move: from: %s ", fileSrc.getName()));
			this.log.note(String.format("Move:   to: %s ", fileDst.getName()));
			return fileSrc.renameTo(fileDst);
		}

		public boolean delete(File file){
			this.log.note(String.format("Deleting: %s ", file.getAbsolutePath()));
			return file.delete();
		}


		/** Runs a thread generating and/or otherways handling a product
		 *
		 * @see #execute()
		 */
		@Override
		public void run(){
			try {
				Signal.handle(new Signal("INT"), this::handleInterrupt);
				//handle(this);
				execute();
			}
			catch (InterruptedException e) {
				//this.log.status
				this.log.warn("Interrupted");
				e.printStackTrace(log.printStream);
			}
			catch (IndexedException e) {
				this.log.warn("NutShell indexed exception --->");
				//e.printStackTrace(log.printStream);
				this.log.error(e.getMessage());
				this.log.warn("NutShell indexed exception <---");
			}
		}

		/** Method called upon SIGINT signal handler set in {@link #run()}
		 *  Deletes files that were under construction.
		 *
		 * @param signal - unused
		 */
		private void handleInterrupt(Signal signal){
			log.warn("Interrupted (by Ctrl+C?) : " + this.toString());
			// System.out.println("Interrupted by Ctrl+C: " + this.outputPath.getFileName());
			if (actions.involves(Actions.FILE)) {
				delete(this.outputPath.toFile());
				delete(this.outputPathTmp.toFile());
			}
		}

		/** Execute this task: delete, load, generate and/or send a product.
		 *
		 * @see #run()
		 *
		 * @return
		 * @throws InterruptedException  // Gene
		 */
		public void execute() throws InterruptedException, IndexedException {

			this.log.note("Determining generator for : " + this.info.PRODUCT_ID);
			//this.setStatus(HttpServletResponse.SC_OK, "Determining generator for : " + this.info.PRODUCT_ID);

			Generator generator = getGenerator(this.info.PRODUCT_ID);
			this.log.debug("Generator: " + generator.toString());

			// Implicit action request
			if (this.actions.isSet(Actions.GENERATE)){ // == MAKE ?!
				if (generator instanceof ExternalGenerator)
					this.actions.add(Actions.FILE); // PREPARE dir & empty file
				else
					this.actions.add(Actions.MEMORY);
			}

			/// Flag for creating directories required by the generator.
			//  Note: Java Generators do not need disk, unless FILE
			//  WRONG, ... MAKE will always require FILE

			// TODO: generator could imply GENERATE => FILE so that dirs are created.

			//if (this.hasAction(this.MEMORY)) {
			if (this.actions.isSet(Actions.MEMORY)) {
				// Not implemented yet!
				/*
				if (cache,get(this.filename()))!= null{
					if (!this.hasAction(this.GENERATE)){
						return it... ?
					}
				}
				*/
				// this.result = new BufferedImage();
				// return ?
			}

			File fileFinal = this.outputPath.toFile();
			File fileTmp   = this.outputPathTmp.toFile();

			if (fileFinal.exists()){
				this.log.debug("File exists: " + this.outputPath.toString());

				// if (this.hasAction(this.MEMORY)) { load(this.outputPath ...)

				// WHen making a file, default output action is REDIRECT.
				/*
				if (this.actions.involves(Actions.MAKE && ! this.actions.involves(Actions.CHECK|Actions.STREAM|Actions.REDIRECT))){
					this.actions.add(Actions.REDIRECT);
				}

				 */

				if (this.actions.isSet(Actions.DELETE)) {
					// TODO: delete only after waiting?
					this.delete(fileFinal);
					if (!this.actions.involves(Actions.MAKE)){
						this.actions.add(Actions.CHECK);
					}
				}
				else if (this.actions.isSet(Actions.FILE)){
					if (queryFile(fileFinal,  90, this.log)){
						this.log.note("File query completed: " + fileFinal.toString());
						//if (this.hasAction(this.GENERATE)){ // = re-generate!
						if (this.actions.isSet(Actions.GENERATE)){
							this.log.note("Continue to re-generate file");
						}
						else {
							this.log.ok("Returning file");
							this.result = this.outputPath;
							return; // true;
						}
					}
					else { // ?? explain
						this.log.warn("Gave up waiting...");
						this.delete(fileFinal);
					}
				}
			}
			else {
				this.log.debug("File does not exist: " + this.outputPath.toString());
			}

			// Gone this far, and product does not exist.
			if (this.actions.involves(Actions.MAKE)){
				this.actions.add(Actions.GENERATE);
			}

			/// Prepare directory and empty file (marker).
			if (this.actions.isSet(Actions.FILE)){ // (this.hasAction(this.FILE)){ // && this.hasAction(this.GENERATE)){
				try {
					this.log.debug("Creating dir: " + cacheRoot+"/./"+this.relativeOutputDir);
					//Path outDir =
					ShellUtils.makeWritableDir(cacheRoot, this.relativeOutputDir);
					ShellUtils.makeWritableDir(cacheRoot, this.relativeOutputDir.resolve("tmp"));
					this.log.debug("Creating file: " + fileFinal.toString());
					fileFinal.createNewFile();
					//this.log.debug("Creating file: " + fileTmp.toString());
					//fileTmp.createNewFile();
				} catch (IOException e) {
					this.log.error(e.getLocalizedMessage());
					return;
				}
			}


			// Variables defining the product and production environment (esp. OUTDIR).
			//Map<String,Object> allParameters = this.getParamEnv(); // todo : explicit new//fill
			Map<String,String> dummyContext = new HashMap<>();
			dummyContext.put("OUTDIR",  this.outputPathTmp.getParent().toString());
			dummyContext.put("OUTFILE", this.outputPathTmp.getFileName().toString());


			//System.err.println("## " + this.outputPath);
			//System.err.println("## " + Paths.get(cacheRoot.toString(), this.relativeOutputDir.toString(), this.info.getFilename()));
			//this.outputPath = Paths.get(cacheRoot.toString(), this.relativeOutputDir.toString(), this.info.getFilename());

			// Generate or at least list inputs
			if (this.actions.isSet(Actions.GENERATE) || this.actions.isSet(Actions.INPUTLIST)){ //

				this.log.note("Determining input list for: " + this.info.PRODUCT_ID);
				//Map<String,String>
				this.inputs.clear(); // needed?
				//this.inputs.putAll(generator.getInputList(this.info, dummyContext, this.log.printStream));
				try {
					this.inputs.putAll(generator.getInputList(this));
					this.log.debug(this.inputs.toString());
					//System.err.println("## " + this.inputs);
				}
				catch (Exception e){
					this.log.warn("Input list retrieval failed");
					//e.printStackTrace(this.log.printStream);
					this.log.error(e.getMessage());
					this.log.warn("Removing GENERATE from actions");
					this.actions.remove(Actions.GENERATE);
				}
				//this.inputs = generator.getInputList(this.info, this.log.printStream); // this.getParamEnv(), log.printStream);
				// DEBUG this.setStatus(HttpServletResponse.SC_OK, "Determining input list for: " + this.info.PRODUCT_ID);

				if (this.actions.isSet(Actions.GENERATE)){

					/// Assume Generator uses input similar to output (File or Object)
					final int inputActions = this.actions.value & (Actions.MEMORY | Actions.FILE);

					Map<String,Task> tasks = executeMany(this.inputs, inputActions, null, this.log);

					for (Entry<String,Task> entry : tasks.entrySet()){
						String key = entry.getKey();
						Task inputTask = entry.getValue();
						if (inputTask.result != null){
							this.retrievedInputs.put(key, inputTask.result.toString());
						}
					}


					/// MAIN
					this.log.note("Running Generator: " + this.info.PRODUCT_ID);

					try {
						generator.generate(this);
					}
					catch (IndexedException e) {
						e.printStackTrace(this.log.printStream);
						try {
							this.delete(fileTmp);
							this.delete(fileFinal);
						}
						catch (Exception e2) {
							this.log.error(e2.getLocalizedMessage());
						}
						//this.log.error(e.getMessage());
						throw e;
						//return false;
					}

					if (fileTmp.length() > 0){
						// this.info.getFilename()
						this.log.debug("OK, generator produced tmp file: " + fileTmp.getName());
						this.move(fileTmp, fileFinal);
					}
					else {
						this.log.error("Generator failed in producing tmp file: " + fileTmp.getName());
						try {
							this.delete(fileFinal);
							this.delete(fileTmp);
						}
						catch (Exception e) {
							/// TODO: is it a fatal error if a product defines its input wrong?
							this.log.error(e.getLocalizedMessage()); // RETURN?
						}
					}

				}
				else {
					this.log.note("Input list was requested (only)");
					this.result = this.inputs;
				}

			}

			if (this.actions.isSet(Actions.FILE)){
				if (fileFinal.length() > 0) {
					this.result = this.outputPath;
					this.log.ok("Generated: " + this.result.toString());
					return; // true;
				}
				else {
					// TODO: save file (of JavaGenerator)
					this.log.error("Failed in generating: " + fileFinal.getAbsolutePath());
					this.delete(fileFinal);
					return;
				}
			}
			else {
				if (this.result != null)
					this.log.note("Result: " + this.result.toString());
				this.log.warn("Task completed: actions=" + this.actions);
				// status page?
				return; // true;
			}

			// return true; // SEMANTICS?
		}

		/** Declaration of environment variables for external (shell) generators.
		 *
		 *
		 * @return - variables defining a product, its inputs, and output dir and file.
		 */
		public Map<String,Object> getParamEnv() {

			// BASE
			Map<String,Object> env = this.info.getParamEnv(null);

			// EXTENDED
			if (this.info.TIMESTAMP != null) {
				switch (this.info.TIMESTAMP.length()) {
					case 12:
						env.put("MINUTE", this.info.TIMESTAMP.substring(10, 12));
					case 10: // future option
						env.put("HOUR", this.info.TIMESTAMP.substring(8, 10));
					case 8: // future option
						env.put("DAY", this.info.TIMESTAMP.substring(6, 8));
					case 6: // future option
						env.put("MONTH", this.info.TIMESTAMP.substring(4, 6));
						env.put("YEAR", this.info.TIMESTAMP.substring(0, 4));
				}
			}

			//env.put("OUTDIR",  this.outputPath.getParent().toString()); //cacheRoot.resolve(this.relativeOutputDir));
			//env.put("OUTFILE", this.outputPath.getFileName().toString());
			env.put("OUTDIR",  this.outputPathTmp.getParent().toString()); //cacheRoot.resolve(this.relativeOutputDir));
			env.put("OUTFILE", this.outputPathTmp.getFileName().toString());

			if (! this.retrievedInputs.isEmpty()){
				env.put("INPUTKEYS", String.join(",", this.retrievedInputs.keySet().toArray(new String[0])));
				env.putAll(this.retrievedInputs);
			}

			env.putAll(this.directives);

			return env;
		}

		/*
		boolean fileIsComplete(){
			File file = outputPath.toFile();
			return (file.exists() && (file.length()>0));
		}
		 */

		@Override
		public String toString() {
			if (directives.isEmpty())
				return String.format("%s", this.outputPath.getFileName());
			else
				return String.format("%s?%s", this.outputPath.getFileName(), directives.toString());
			//return String.format("%s?%s", this.outputPath.getFileName(), actions.toString());
			//return MapUtils.getMap(this).toString();
		}

		final long creationTime;
		
		public Object result;

	}  // Task




	public interface Generator   {

		/**
		 */
		// Consider Object as return type (java class returning product)
		//int generate(Map<String,Object> parameters, PrintStream log); // throws IOException, InterruptedException ;
		//void generate(Task task, Map<String,Object> parameters); // throws IOException, InterruptedException ;
		//void generateFile(ProductParameters parameters, Map<String,Object> inputs, Map<String,String> envMap,Path outFile, PrintStream log) throws IndexedException; // throws IOException, InterruptedException ;
		void generate(Task task) throws IndexedException; // throws IOException, InterruptedException ;

		//Map<String,String> getInputList(Map<String,Object> parameters, PrintStream log);
		//Map<String,String> getInputList(Task task);
		//Map<String,String> getInputList(ProductParameters parameters, Map<String,String> envMap, PrintStream log);
		// Semantics? (List or retrieved objects)
		boolean hasInputs();

		/** Declare inputs required for this product generation task.
		 *
		 * @param task
		 * @return
		 */
		Map<String,String> getInputList(Task task) throws IndexedException ;

	}

	/** Run a set of tasks in parallel.
	 *
	 * This function is called by
	 * 1) tasks, to obtain inputs
	 * 2) demo (main function)
	 *
	 * @param taskRequests
	 * @param actions
	 * @param log
	 * @return - the completed set of tasks, including failed ones.
	 *
	 */
	public Map<String,Task> executeMany(Map<String,String> taskRequests, int actions, Map directives, Log log){

		Map<String,Task> tasks = new HashMap<>();
		log.note("Inits (" + tasks.size() + ") tasks ");

		for (Entry<String,String> input : taskRequests.entrySet()){
			String key   = input.getKey();
			String value = input.getValue();
			try {
				Log subLog = null;
				if (taskRequests.size() == 1)
					subLog = log.child(key);
				Task task = new Task(value, actions, subLog);
				if (directives != null)
					task.directives.putAll(directives);
				//if (log)
				task.log.verbosity = log.verbosity;
				tasks.put(key, task);
				log.note(String.format("Starting thread: %d %s(%s)", task.getId(), key, task.info.PRODUCT_ID));
				task.start();
			}
			catch (ParseException e) {
				/// TODO: is it a fatal error if a product defines its input wrong?
				log.warn(String.format("Could not parse product: %s(%s)",  key, value));
				//log.warn(e.getMessage());
				log.error(e.getLocalizedMessage()); // RETURN?
			}
			catch (Exception e) {
				log.error("Unexpected exception... " + e.getLocalizedMessage());
			}
		}

		log.note("Waiting for (" + tasks.size() + ") tasks to complete... ");

		for (Entry<String,Task> entry : tasks.entrySet()){
			String key = entry.getKey();
			Task task = entry.getValue();
			try {
				task.join();
				log.note(String.format("Finished thread: %s-%d ", key, task.getId()));
			}
			catch (InterruptedException e) {
				log.note(String.format("Interrupted thread: %s-%d ", key, task.getId()));
			}
		}

		return tasks;
	}

	/** Searches for shell side (and later, Java) generators
	 *
	 * @param productID
	 * @return
	 */
	public Generator getGenerator(String productID){
		Path dir = productRoot.resolve(getProductDir(productID));
		Generator generator = new ExternalGenerator(productID, dir.toString());
		return generator;
	};

	/// Checks if a file exists, return if non-empty, else wait for an empty file to complete.
	/**
	 * @param file
	 * @param maxEmptySec maximum age of empty file in seconds
	 * @param log
	 * @return
	 * @throws InterruptedException
	 */
	public boolean queryFile(File file, int maxEmptySec, Log log) throws InterruptedException {

		int remainingSec = this.timeOut;

		if (file.length() > 0) {
			log.note("File found");
			return true;
		}
		else { // empty file
			long ageSec = (java.lang.System.currentTimeMillis() - file.lastModified()) / 1000;
			if (ageSec > maxEmptySec){
				log.note("Outdated empty file, age=" + (ageSec/60) + " min, (max " + maxEmptySec + "s)");
				return false;
			}
			else {
				log.warn("Empty fresh file exists, waiting for it to complete...");
				for (int i = 1; i < 10; i++) {
					int waitSec = i * i;
					log.warn("Waiting for "+ waitSec + " s...");
					TimeUnit.SECONDS.sleep(waitSec);
					if (file.length() > 0){
						log.note("File appeared");
						return true;
					}
					remainingSec = remainingSec - waitSec;
					if (remainingSec <= 0)
						break;
				}
				log.note("File did not appear (grow), even after waiting");
				return false;
			}
		}
	}



	/// System side setting.
	//  public String pythonScriptGenerator = "generate.py";


	/// Maximum allowed time (in seconds) for product generation (excluding inputs?) FIXME share in two?
	public int timeOut = 30;

	public static void help(){

		System.err.println("Usage:   java -cp $NUTLET_PATH nutshell.ProductServer  [<options>] <products>");
		System.err.println("    - $NUTLET_PATH: directory of nutshell class files or path up to Nutlet.jar");
		System.err.println();
		System.err.println("Options: ");
		System.err.println("    --help: this help dump");
		System.err.println("    --verbose <level> : set verbosity (DEBUG, INFO, NOTE, WARN, ERROR)");
		System.err.println("    --debug : same as --verbose DEBUG");
		System.err.println("    --config <file> : read configuration file");
		System.err.println("    --actions <string> : main operation: (MAKE,DELETE,GENERATE,STREAM,REDIRECT,INPUTS)");
		System.err.println("    --directives <key>=<value>|<key>=<value>|... : instructions (pipe-separated) for product generator");
		System.err.println();
		System.err.println("Examples: ");
		System.err.println("    java -cp $NUTLET_PATH 201012161615_test.ppmforge_DIMENSION=2.5.png");

	}

	/*
	protected void setField(Object obj, String key, String value, Log log){
		key = key.toUpperCase();
		try {
			Field field = obj.getClass().getField(key);
			actions.add(a);
			log.note("Added action:"  + a);
		} catch (NoSuchFieldException e) {
			log.note("Use following action codes: ");
			for (Field field: Actions.class.getFields()){
				String name = field.getName();
				if (name.equals(name.toUpperCase())){
					log.note("  " + name + "=" + field.getInt(null));
				}
			}
			log.error("No such action code: "  + a);
			System.exit(2;
		}
	}
	*/

	/// Command-line interface for 
	public static void main(String[] args) {
		
		if (args.length == 0){
			help();
			return;
		}

		final ProductServer server = new ProductServer();
		server.serverLog.printStream = System.err;  // System.out  Note: testing STREAM action needs clean stdout
		server.serverLog.verbosity = Log.DEBUG;
		server.timeOut = 20;

		Log log = server.serverLog.child("CmdLine");
		//server.readConfig();

		String confFile = null;

		Map<String,String> products = new HashMap<>();
		Actions actions = new Actions();
		Map<String ,String> directives = new HashMap();

		Field[] actionFields = Actions.class.getFields();

		try {
			for (int i = 0; i < args.length; i++) {

				String arg = args[i];

				if (arg.charAt(0) == '-') {

					if (arg.charAt(1) != '-') {
						throw new IllegalArgumentException("Short options (-x) not suppported");
					}

					String opt = arg.substring(2);

					if (opt.equals("help")) {
						help();
						return;
					}

					if (opt.equals("verbose")) {
						log.verbosity = Log.DEBUG;
						continue;
					}

					if (opt.equals("log_level")) {
						arg = args[++i];
						try {
							int level = Integer.parseInt(arg);
							log.verbosity = level;
						} catch (NumberFormatException e) {
							try {
								Field field = Log.class.getField(arg);
								log.verbosity = field.getInt(null);
							} catch (NoSuchFieldException e2) {
								log.note("Use following keys or values (0...10)");
								for (Field field : Log.class.getFields()) {
									String name = field.getName();
									if (name.equals(name.toUpperCase())) {
										log.note(name + "=" + field.getInt(null));
									}
								}
								log.error("No such verbosity level: " + arg);
								return;
							}
						}
						continue;
					}

					/// It is recommended to give --config among the first options, unless default used.
					if (opt.equals("conf")) {
						if (confFile != null){
							log.warn("Reading second conf file (already read: " + confFile + ")");
						}
						confFile = args[++i];
						//log.info("Reading conf file: " + confFile);
						server.readConfig(confFile);
						continue; // Note
					}

					// Now, read conf file if not read this far.
					if (confFile == null){
						confFile = "nutshell.cnf";
						// log.info("Reading conf file: " + confFile);
						server.readConfig(confFile);
					}


					if (opt.equals("directives")) {
						for (String d : args[++i].split("\\|")) { // Note: regexp
							log.info("Adding directive: " + d);
							int j = d.indexOf('=');
							if (j > 1) {
								directives.put(d.substring(0, j), d.substring(j + 1));
							} else {
								directives.put(d, "true");
							}
						}
					}
					else if (opt.equals("product")) {
						products.put("product", args[++i]);
					}
					else {
						// Actions
						try {
							if (opt.equals("actions")) {
								opt = args[++i];
								log.info("Adding actions: " + opt);
								actions.set(opt);
							}
							else {
								// Set actions from invidual args: --make --delete --generate
								opt = opt.toUpperCase();
								//Field field =
								log.info("Adding action:" + opt);
								Actions.class.getField(opt); // ensure field exists
								actions.add(opt);
							}
						}
						catch (NoSuchFieldException|IllegalAccessException e) {
							log.note("Use following action codes: ");
							for (Field field : Actions.class.getFields()) {
								String name = field.getName();
								if (name.equals(name.toUpperCase())) {
									try {
										log.note("  " + name + "=" + field.getInt(null));
									} catch (IllegalAccessException e1) {
										//illegalAccessException.printStackTrace();
									}
								}
							}
							log.error("No such action code: " + opt);
							System.exit(2);
						}

					}
				}
				else {  // Argument does not start with "--"
					products.put("product" + products.size(), arg);
				}

			}
		}
		catch (Exception e) {
			log.error("Unhandled exception: " + e.getMessage());
			//System.err.println("Interrupted");
			e.printStackTrace(log.printStream);
			System.exit(1);
		}

		if (actions.isEmpty()){
			actions.set(Actions.MAKE);
		}

		log.note("Actions: " + actions);
		log.note("Directives: " + directives);
		//System.out.println(directives);

		//Log taskLog = (products.size() == 1) ? log : null;

		Map<String,ProductServer.Task> tasks = server.executeMany(products, actions.value, directives, log);

		log.note("Waiting for (" + tasks.size() + ") tasks to complete... ");

		for (Entry<String,Task> entry: tasks.entrySet()) {
			String key = entry.getKey();
			Task  task = entry.getValue();

			log.note("status(" + task.info.PRODUCT_ID + "): " + task.log.status);
			if (task.logFile != null)
				log.info("Log: "  + task.logFile.getAbsolutePath());
			log.note("File: " + task.outputPath.toString());
		}


	}



}


