package nutshell10;

import sun.misc.Signal; // For interrupt?
import java.io.*;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// import static java.nio.file.Files.*;

/* TODO: swallows exceptions in command line use?
 * TODO: separate cache at least for java generators
 * TODO: communicate failure with HTTP request codes
 * TODO: design defaults for WRITE
 *
 * TODO: add i/o handlers (See Image IO Registry), use {@link Serializable}.
 * TODO: simplify REQUEST, add request=GENERATE,
 * TODO: use SAVE/STREAM in sub-requests
 * TODO: 404: apply directs stream, without redirect.
 * TODO: add reset/ generatorMap.clear()
 * NEW
 */

/**
 * Extends Cache by not only storing and retrieving existing files, but also
 * generating them on request.
 * 
 * Principles. A product, hence a product request, has the following three
 * following parameter types: - product name - time (optional; applies only
 * recipient dynamic products) - product-specific parameters
 * 
 * Product name, PRODUCT_ID, can be identified with a respective product
 * generator. Mostly, meteorological products have at least one associated
 * moment of time, TIMESTAMP, some may have many. Static products like
 * geographical map do not have a timestamp. On the other hand, some
 * meteorological products may have several timestamps, like computing time and
 * valid time.
 *
 * Some version history 
 * 4.6.1 Manip map reassign, dir perms visualised
 * 4.6 Explicit directory permission tests
 * 4.5 Revised path bundle
 * 4.4.1 LOG_SERVER_PATH 
 * 4.4 TomCat 10+9 via util/downgrade-code.sh 
 * 4.3 TomCat 10.1 
 * 4.0 Fixed web.xml rules, simplified doGet()
 * 3.7.7 Methods moved to enclosing class. 
 * 3.7.6 External path container: {@link ProductPathBundle} 
 * 3.7.5 Fixed double response.getOutputStream() call – STREAM ends silently. 
 * 3.7.4 Fixed timeformat bug with synchronized 
 * 3.6 Adds INPUT_PREFIX - the common prefix for all the retrieved input paths. 
 * 3.5 Added *.sh link in NutLet 
 * 3.2 Create dirs automatically under $CACHE_ROOT and $STORAGE_ROOT
 *
 * @author Markus Peura fmi.fi Nov 26, 2023
 */
public class ProductServer extends ProductServerBase { // extends Cache {

	// TODO: Handle missing Dot

	// static
	public String getVersion() {
		return "4.6.2"; 
	}

	ProductServer() {
		setup.put("ProductServer", this.getVersion());
	}

	/// Experimental: Change MAKE to GENERATE if positive, decrement for each input
	// TODO: consider general query depth (for inputs etc)
	static public int defaultRemakeDepth = 0;

	/**
	 * Prefix for the marker used in logs and temporary files. To separate different
	 * users/processes in shared directories.
	 *
	 * The label will be automatically appended also the counter number and user.
	 *
	 */
	// public String LABEL; // "nutshell-"+getVersion(); // ""%d-%s"; //
	// USER-counter

	/// System side settings.

	@Override
	public String toString() {
		return Config.getMap(this).toString() + "\n" + setup.toString();
	}

	public interface Generator {

		/**
		 * Create the product defined in task.
		 */
		void generate(Task task) throws IndexedState; // throws IOException, InterruptedException ;

		/**
		 * Declare inputs required for this product generation task.
		 *
		 * @param task
		 * @return
		 */
		Map<String, String> getInputList(Task task) throws IndexedState;

		/**
		 * Natural (native) media type for the generated product.
		 *
		 * @return MediaType.FILE or MediaType.MEMORY
		 */
		int getPrimaryMediaType();

	}

	/**
	 * Searches for shell side (and later, Java) generators
	 *
	 * @param productID
	 * @return
	 */
	public Generator getGenerator(String productID) throws IndexedState {
		Path dir = PRODUCT_ROOT.resolve(getProductDir(productID));
		Generator generator = new ExternalGenerator(productID, dir.toString());
		return generator;
	}

	/**
	 * Make logical checks and complementing of flags.
	 * 
	 * @param instructions
	 */
	static protected void completeInstructions(Instructions instructions) {

		// Logical corrections
		// Note: STATUS involves Task/product wise and also Server level info.
		if (instructions.isEmpty() || !instructions.copies.isEmpty() || !instructions.links.isEmpty()
				|| (instructions.move != null)
				|| instructions.involves(PostProcessing.STORE | PostProcessing.LATEST | PostProcessing.SHORTCUT)) {
			instructions.ensureMakeLevel(Instructions.MakeLevel.MAKE); // NEW
			// log.debug(String.format("Instructions (updated): %s", instructions));
		}

		// Media must be defined in most of the operations (DELETE, EXISTS, MAKE,
		// GENERATE) so define it here.
		if (instructions.makeLevelAtLeast(Instructions.MakeLevel.EXISTS)
				|| instructions.makeLevelEquals(Instructions.MakeLevel.DELETE)) {
			if (!instructions.involves(MediaType.FILE | MediaType.MEMORY)) {
				// Note: media selection could be also done by Generator?
				// log.log(HttpLog.HttpStatus.OK, "Product requested, media type undefined.
				// Setting default: FILE");
				instructions.add(MediaType.FILE);
			}
		}

	}
	

	public class PathEntry {
		
		public Path getFileName() {
			return relativePath.getFileName();
		}

		public Path getRelativePath() {
			return relativePath;
		}

		public Path getRelativeDir() {
			return relativePath.getParent();
		}

		/** Relative path prefixed with "cache/" or "storage/" 
		 * 
		 * @return 
		 */
		public Path getPrefixedRelativePath() {
			return rootDir.getFileName().resolve(relativePath);
		}

		/** Relative directory prefixed with "cache/" or "storage/" 
		 * 
		 * @return 
		 */
		public Path getPrefixedRelativeDir() {
			return rootDir.getFileName().resolve(relativePath.getParent());
		}

		
		public Path getAbsolutePath() {
			return absolutePath;
		}

		public Path getAbsoluteDir() {
			return absolutePath.getParent();
		}

		public Path getRootDir() {
			return rootDir;
		}

		@Override
		public String toString() {
			return String.format("%s//%s", getRootDir(), getRelativePath());
		}


		// --------------------------------------
		
		final private Path rootDir;
		final private Path relativePath;
		final private Path absolutePath;
		
		protected PathEntry(Path rootDir, Path path){
			this.rootDir = rootDir;
			this.relativePath = path;
			this.absolutePath = rootDir.resolve(path);
			// System.out.println("PathEntry: " + this.toString());
		}	

		/*
		protected PathEntry(Path path){
			this(CACHE_ROOT, path);
		}
		*/	
	
	
	}

	PathEntry getGeneratorEntry(ProductInfo product){
		
		Path productDir = getProductDir(product.getID());
		
		Path generatorDirAbsolute = PRODUCT_ROOT.resolve(productDir);

		//Path generatorScriptRelative = null;

		if (Files.exists(generatorDirAbsolute.resolve(ExternalGenerator.scriptName))) {
			return new PathEntry(PRODUCT_ROOT, productDir.resolve(ExternalGenerator.scriptName));			
		}

		// Future extension
		return new PathEntry(PRODUCT_ROOT, productDir.resolve("generator.py"));
	}

	PathEntry getLongEntry(ProductInfo product){
		return new PathEntry(CACHE_ROOT, 
				product.getTimeStampDir().resolve(getProductDir(product.getID())).resolve(product.getFilename()));
	}

	PathEntry getShortEntry(ProductInfo product){
		return new PathEntry(CACHE_ROOT, 
				getProductDir(product.getID()).resolve(product.getFilename()));
	}


	PathEntry getTmpEntry(PathEntry pathEntry){
		return new PathEntry(CACHE_ROOT,
				pathEntry.getRelativeDir().resolve("tmp").resolve(pathEntry.relativePath.getFileName()));
	}

	PathEntry getStorageEntry(ProductInfo product){
		return new PathEntry(STORAGE_ROOT,
				product.getTimeStampDir().resolve(getProductDir(product.getID())).resolve(product.getFilename()));
	}

	
	/// Given a product file entry, returns a respective tmp/aux directory for saving logs etc
	/**
	 * 
	 * @param bundle
	 * @param suffix - file extension without period, for example "log" or "svg"
	 * @return
	 */
	PathEntry getSystemEntry(PathEntry bundle, String suffix){
		return new PathEntry(CACHE_ROOT, bundle.getRelativeDir().
				resolve(String.format(".nutshell-%s", System.getProperty("user.name"))).
				resolve(String.format("%s.%s", bundle.getFileName(), suffix)));
	}
	
	PathEntry getLogEntry(PathEntry bundle, TextOutput.Format logFormat){
		if (logFormat.equals(TextOutput.Format.HTML)) {
			return getSystemEntry(bundle, "html");				
		} 
		else {
			return getSystemEntry(bundle, "log");			
		}
	}

	PathEntry getGraphEntry(PathEntry bundle){
		return getSystemEntry(bundle, "svg");			
	}

	/**
	 * A "tray" containing both the product query info and the resulting object if
	 * successfully queried. Task does not know about the generator. Notice GENERATE
	 * and getParamEnv.
	 */
	// static
	public class Task extends Thread {

		final ProductServerBase pserver = ProductServer.this;

		final HttpLog log;
		final ProductInfo info;

		final public int id;

		// long startTime; //java.lang.System.currentTimeMillis()

		final DateFormat durationFormat = new SimpleDateFormat("mm:ss.S");

		public int getTaskId() {
			return id;
		}

		/// Checked, "normalized" filename, with ordered parameters.
		// final public String filename;

		final public Instructions instructions; // = new Instructions();

		final public PathEntry generatorPath; // conditional (not used for Java Classes?)
		final public PathEntry productPath;
		final public PathEntry productPathTmp;
		final public PathEntry logPath;
		final public PathEntry storagePath;

		final public PathEntry graphPath;
		
		// final public ProductTask test = new ProductTask();

		// public final Map<String,String> directives = new HashMap<>();
		public final Map<String, String> inputs = new HashMap<>();

		// TODO rename inputTasks
		public final Map<String, Task> inputTasks = new HashMap<>();

		// Consider get node


		
		/**
		 * Product generation task defining a product instance and operations on it.
		 * <p>
		 * In this version, directives can be set but only through '?'
		 *
		 * @param productStr
		 * @param instr - definition how a product is retrieved and handled thereafter - @see #Actions
		 * @param parentLog    - log of the parent task (or main process, a product server)
		 * @throws ParseException - if parsing productStr fails
		 * @throws IOException - if writing a log file fails
		 */
		public Task(String productStr, Instructions instr, HttpLog parentLog) throws ParseException, IOException {


			id = getProcessId();
			info = new ProductInfo(productStr);
			instructions = new Instructions(instr);
			// filename = info.getFilename();

			/*
			if (info.time > 0){
				int year = Integer.parseInt(info.TIMESTAMP.substring(0,4));
				if (year > 2030){
					throw new ParseException(
							String.format("NSH failed in parsing YEAR: %s -> %d", info.TIMESTAMP, year), 0);
				}
			}
			 */
			
			
			final String label = getLabel(instructions.label, getTaskId());

			if (info.isDynamic()){
			}

			if (parentLog == null){
				parentLog = serverLog;
			}

			log = new HttpLog(parentLog.getName() + "[" + this.info.PRODUCT_ID + "]", parentLog.getVerbosity());
			log.set(LOG_TASKS);

			//paths = new ProductPathBundle(ProductServer.this, info, label, log.textOutput.getFormat().toString());

			generatorPath  = getGeneratorEntry(info);
			productPath    = getLongEntry(info);
			productPathTmp = getTmpEntry(productPath);
			logPath     = getLogEntry(productPath, log.textOutput.getFormat());
			graphPath   = getGraphEntry(productPath); // NOTE: filename still same
			storagePath = getStorageEntry(info);
			
			try {
				FileUtils.ensureWritableFile(logPath.getAbsolutePath(), GROUP_ID, filePerms, dirPerms);
				log.setLogFile(logPath.getAbsolutePath());
				log.debug(String.format("Log format: %s (%s)",  this.log.getFormat(), log.decoration));
				log.debug(String.format("Label: %s",  label)); // , labelArray
			} 
			catch (IOException e) {
				parentLog.warn("stackTrace follows");
				e.printStackTrace(parentLog.getPrintStream());
				parentLog.error(String.format("Could not open Task log: %s", logPath.getAbsolutePath()));
				System.err.println(String.format("Opening Log file failed: Log GID=%d, file=%s dir=%s",
						GROUP_ID, filePerms, dirPerms));
				System.err.println(String.format("Opening Log file failed: Error: %s", e));
				System.err.println(String.format("Opening Log file failed: File: %s", logPath.getAbsolutePath()));
				//log.setLogFile(null); ?
				log.log(HttpLog.HttpStatus.INTERNAL_SERVER_ERROR, e.toString());
				throw e; // currently
			}

			/*
			// DEBUGGING!
			if (paths.timeStampDir.toString().length()==10) {  // Typical format: "2017/08/12".length() = 10
				if (!paths.timeStampDir.toString().startsWith("20")) {
					//throw new ParseException(String.format("NSH timeStampDir failed : %s (%s)",timeStampDir, info.TIMESTAMP), 0);
					System.out.println(String.format("NutShell DEBUG suspicious timeStampDir: ", paths.timeStampDir));
					System.out.println(String.format("  TIMESTAMP=%s -> %s", info.TIMESTAMP, info.getTimeResolution(info.TIMESTAMP)));
					System.out.println(String.format("  %s (%s -> %s), see also log: %s", paths.timeStampDir, info.TIMESTAMP, info.getTimeStampDir(), logPath));
				}
			}
			else if (paths.timeStampDir.toString().length() > 10){// "2017/08/12".length() = 10
				//throw new ParseException(String.format("NSH timeStampDir failed : %s (%s)",timeStampDir, info.TIMESTAMP), 0);
				System.out.println(String.format("NutShell DEBUG fatal: timeStampDir failed : %s (%s), see also log: %s",
						paths.timeStampDir, info.TIMESTAMP, logPath));
			}
			*/

			// this.relativeSystemDir = this.timeStampDir.resolve("nutshell").resolve(this.productDir);
			// Path systemPath =  CACHE_ROOT.resolve(paths.relativeSystemDir);
			// System.err.println(graphPath.absolutePath);
			try {
				FileUtils.ensureWritableDir(graphPath.getAbsoluteDir(), GROUP_ID, dirPerms);
			} catch (IOException e) {
				log.error(String.format("Creating product aux dir failed: %s '%s'", graphPath.getAbsolutePath() , e));
			}

			//log.warn("Where am I?");
			//log.debug(String.format("Log format: %s (%s)",  this.log.getFormat(), log.decoration));
			log.info(String.format("Created Task: %s ", this.toString())); //
			//log.debug(String.format("Created TASK %s [%d] [%s] %s ", this.paths.filename, this.getTaskId(), this.instructions, this.info.directives)); //  this.toString()
			this.result = null;
		}

		/**
		 * 
		 * @param productStr
		 * @param instructions
		 * @throws ParseException - if productStr cannot be parsed
		 * @throws IOException - if writing a log file fails
		 */
		public Task(String productStr, Instructions instructions) throws ParseException, IOException {
			this(productStr, instructions, null);
		}

		protected void close() {
			// System.err.println("CLOSE task: " + toString());
			this.log.close();
		}

		@Override
		public String toString() {
			if (this.info.getDirectives().isEmpty())
				return String.format("%s # %s", productPath.getFileName(), instructions);
			else
				return String.format("%s?%s # %s", productPath.getFileName(), info.getDirectives(), instructions);
		}

		public String getStatus() {
			int i = log.getStatus();
			return String.format("%s(%d)", Log.statusCodes.getOrDefault(i, Log.Status.UNDEFINED), i);
			// return (String.format("%s[%d] %s [%s] %s", this.getClass().getSimpleName(),
			// this.getTaskId(), this.info, this.instructions, this.info.directives)); //
			// this.toString()
		}

		/**
		 * Runs a thread generating and/or otherwise handling a product
		 *
		 * @see #execute()
		 * @see #handleInterrupt(Signal) ()
		 */
		@Override // Thread
		public void run() {

			try {
				log.note("RUN..");
				Signal.handle(new Signal("INT"), this::handleInterrupt);
				execute();
			} catch (InterruptedException e) {
				// log.status
				log.note(e.toString());
				log.warn("Interrupted");
				log.log(HttpLog.HttpStatus.INTERNAL_SERVER_ERROR, e.toString()); // ? too strong?
				// e.printStackTrace(log.printStream);
			}
			/*
			 * catch (IOException e) { //log.status log.note(e.toString());
			 * log.warn("Internal problem"); e.printStackTrace(log.getPrintStream());
			 * log.log(HttpLog.HttpStatus.INTERNAL_SERVER_ERROR, e.toString()); // ? too
			 * strong? }
			 */
		}

		/**
		 * Method called upon SIGINT signal handler set in {@link #run()} Deletes files
		 * that were under construction.
		 *
		 * @param signal - unused
		 */
		private void handleInterrupt(Signal signal) {
			log.warn("Interrupted (by Ctrl+C?) : " + this.toString());
			// System.out.println("Interrupted by Ctrl+C: " +
			// this.outputPath.getFileName());
			if (instructions.involves(MediaType.FILE)) {
				try {
					pserver.delete(productPath.getAbsolutePath(), log);
					pserver.delete(productPathTmp.getAbsolutePath(), log); // what about tmpdir?
				} catch (IOException e) {
					log.warn(e.getMessage());
					// e.printStackTrace();
				}
			}
		}

		/**
		 * For now, not critical. Future option: memory cached products.
		 *
		 */
		public Object result;

		/**
		 * Execute this task on a single product: delete, load, generate a product, for
		 * example.
		 *
		 * This is a central function of this software, "MAIN".
		 *
		 * Processing is done inside the parent thread – by default the main thread. To
		 * invoke this function as a separate thread, use #run().
		 **
		 * @throws InterruptedException // Gene
		 * @throws IOException
		 * @see #run()
		 */
		public void execute() throws InterruptedException {

			long startTime = java.lang.System.currentTimeMillis();

			Generator generator = null;

			log.log(HttpLog.HttpStatus.OK, String.format("Preparing %s", this));

			ProductServer.completeInstructions(instructions);

			log.log(HttpLog.HttpStatus.ACCEPTED, String.format("Handling %s", this));

			if (instructions.makeLevelEquals(Instructions.MakeLevel.PARSE)) {
				log.log(HttpLog.HttpStatus.CONTINUE, String.format("Parsing requested only"));
				Map<String, Object> map = getParamEnv();
				String[] array = MapUtils.toArray(map);
				for (String s : array) {
					System.out.println(s); // NutLet: to Server log?
				}
			}

			// This is a potential path, not committing to a physical file yet.
			File fileFinal = productPath.getAbsolutePath().toFile();

			/// Main DELETE operation
			if (instructions.makeLevelEquals(Instructions.MakeLevel.DELETE)
					|| instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)) {

				if (instructions.isSet(MediaType.FILE)) {

					if (queryFile(fileFinal, TIMEOUT, log) >= 0) {

						long ageSec = FileUtils.fileModificationAge(fileFinal);
						log.warn(String.format("Deleting...(Age %ss)", ageSec));

						if (ageSec < (2 * TIMEOUT)) {
							log.log(HttpLog.HttpStatus.SEE_OTHER,
									String.format("Deleting a freshly (re)generated file? (Age %ss)", ageSec));
						}

						try {
							// Native log
							pserver.delete(productPath.getAbsolutePath(), log);
						} catch (IOException e) {
							log.log(HttpLog.HttpStatus.CONFLICT,
									String.format("Failed in deleting file: %s, %s", productPath.getAbsolutePath(), e.getMessage()));
							if (instructions.makeLevelEquals(Instructions.MakeLevel.DELETE)) {
								return; // STATUS?
							}
						}
					} else {
						log.log(HttpLog.HttpStatus.CONTINUE,
								String.format("File does not exist: %s", productPath.getAbsolutePath()));
						// result = new Boolean(true);
					}
				}
				// TODO: memory

			}

			// Note: Java Generators do not need disk, unless FILE
			// WRONG, ... MAKE will always require FILE
			/*
			 * if (instructions.isSet(Instructions.MEMORY)) { // Not implemented yet //
			 * this.result = new BufferedImage(); }
			 */

			final boolean STORED_FILE_EXISTS = storagePath.getAbsolutePath().toFile().exists();

			/// "MAIN"
			// Consider also semantics: CHECK > EXISTS also actively links stored file or
			/// waits for a new file to appear.
			if ((instructions.involves(MediaType.FILE)) && instructions.makeLevelAtLeast(Instructions.MakeLevel.MAKE)) {

				// if ((instructions.involves(MediaType.FILE)) &&
				// !instructions.makeLevelEquals(Instructions.MakeLevel.DELETE)) {
				log.debug(String.format("Stored file exists? %s : %s", STORED_FILE_EXISTS, storagePath.getAbsolutePath()));

				if (!fileFinal.exists() && STORED_FILE_EXISTS) {
					log.log(HttpLog.HttpStatus.OK,
							String.format("Trying to use (link) stored file: %s", storagePath.getAbsolutePath()));

					try {
						FileUtils.ensureWritableDir(productPath.getAbsolutePath().getParent(), GROUP_ID, dirPerms);
						// ensureDir(cacheRoot, relativeOutputDir); //, dirPerms);
					} catch (IOException e) {
						log.warn(e.getMessage());
						log.log(HttpLog.HttpStatus.FORBIDDEN,
								String.format("Failed in creating dir (with permissions): %s", productPath.getAbsoluteDir()));
						// e.printStackTrace();
					}

					try {
						pserver.link(storagePath.getAbsolutePath(), productPath.getAbsolutePath(), false, log);
					} catch (IOException e) {
						log.error(e.getMessage());
						log.log(HttpLog.HttpStatus.CONFLICT,
								String.format("Failed in linking: %s <- %s", productPath.getAbsolutePath(), storagePath.getAbsolutePath()));
						// e.printStackTrace();
					}
				} else if (queryFile(fileFinal, TIMEOUT, log) >= 0) {
					result = productPath.getAbsolutePath();
					log.log(HttpLog.HttpStatus.OK, String.format("File exists: %s", productPath.getAbsolutePath()));
				} else {
					log.log(HttpLog.HttpStatus.OK, String.format("File does not exist: %s", productPath.getAbsolutePath()));
					instructions.ensureMakeLevel(Instructions.MakeLevel.GENERATE);
				}
			}

			// Retrieve Generator, if needed
			if (instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)
					|| instructions.involves(Instructions.INPUTLIST | Instructions.STATUS)) {

				log.log(HttpLog.HttpStatus.OK, String.format("Determining generator for : %s", this.info.PRODUCT_ID));
				try {
					generator = getGenerator(this.info.PRODUCT_ID);
					// log.log(HttpLog.HttpStatus.CREATED, String.format("Generator(%s): %s",
					// this.info.PRODUCT_ID, generator));
					log.log(HttpLog.HttpStatus.CREATED, generator.toString());

					if (instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)) {
						instructions.add(generator.getPrimaryMediaType()); // FILE or MEMORY
					}

				} catch (IndexedState e) {
					log.log(HttpLog.HttpStatus.CONFLICT, "Generator does not exist");
					log.log(e);
					// instructions.remove(Instructions.GENERATE);
					if (instructions.makeLevelAtLeast(Instructions.MakeLevel.MAKE)) {
						instructions.setMakeLevel(Instructions.MakeLevel.EXISTS); // ? External file copied in cache?
					}
					instructions.remove(Instructions.INPUTLIST);
					// instructions.remove(Instructions.STATUS);
				}

			}

			// Generate or at least list inputs
			// if (instructions.involves(Instructions.INPUTLIST | Instructions.GENERATE)) {
			// //
			if (instructions.involves(Instructions.INPUTLIST)
					|| instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)) { //

				log.debug(String.format("Determining input list for: %s", this.info.PRODUCT_ID));

				inputs.clear(); // needed?

				try {
					inputs.putAll(generator.getInputList(this));
					for (Entry<String, String> s : inputs.entrySet()) {
						log.debug(String.format("%s = '%s'", s.getKey(), s.getValue()));
					}
					// statistics.put(info.PRODUCT_ID, inputs.keySet());
					// System.err.println("## " + this.inputs);
				} catch (Exception e) {
					log.log(HttpLog.HttpStatus.CONFLICT, "Input list retrieval failed");
					log.log(e);

					log.warn("Removing GENERATE from instructions");
					if (instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)) {
						instructions.setMakeLevel(Instructions.MakeLevel.MAKE);
					}
					// instructions.remove(Instructions.GENERATE);
				}

				if (!inputs.isEmpty())
					log.info(String.format("Collected (%d) input requests for: %s", inputs.size(), info.PRODUCT_ID));

			}

			if (instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)) {

				log.resetState(); // Forget old sins

				// Debugging
				Path p = null;

				// Mark this task being processed (empty file)
				try {
					FileUtils.ensureWritableDir(p = productPathTmp.getAbsoluteDir(), GROUP_ID, dirPerms);
					log.info(String.format("Created tmp dir: %s", productPathTmp.getAbsoluteDir()));
					FileUtils.ensureWritableDir(p = productPath.getAbsoluteDir(), GROUP_ID, dirPerms);
					log.info(String.format("Created dir: %s", productPath.getAbsoluteDir()));
					FileUtils.ensureWritableFile(p = productPath.getAbsolutePath(), GROUP_ID, filePerms, dirPerms);
					log.debug(String.format("Created empty file: %s", productPath.getAbsolutePath()));
				} catch (IOException e) {
					log.log(HttpLog.HttpStatus.CONFLICT, e.toString());
					e.printStackTrace(log.getPrintStream());
					log.log(HttpLog.HttpStatus.INTERNAL_SERVER_ERROR,
							String.format("Failed in creating:: %s, %s", p, e.getMessage()));
					// return;
				}

				log.debug("Ok, generate...");

				/// Retrieve inputs
				if (!inputs.isEmpty()) {

					final Instructions inputInstructions = new Instructions();

					if (instructions.makeLevelEquals(Instructions.MakeLevel.GENERATE_ALL)) {
						inputInstructions.setMakeLevel(Instructions.MakeLevel.GENERATE_ALL);
					} else {
						inputInstructions.setMakeLevel(instructions.makeLevel - 1);
					}

					if (instructions.isSet(Instructions.INPUTLIST)) {
						inputInstructions.add(ActionType.INPUTLIST);
					}

					// Input generation uses parallel threads only if this product uses - ?
					if (instructions.isSet(Instructions.PARALLEL)) {
						inputInstructions.add(ActionType.PARALLEL);
					}

					// log.experimental(String.format("Input retrieval depth: %d",
					// inputInstructions.makeLevel));

					log.special(String.format("Input instructions: %s", inputInstructions));

					// Ok - forwarding directives?
					// FIX: WHY repeated inputTasks <--> this.inputTasks
					Map<String, Task> tentativeInputTasks = prepareTasks(this.inputs, inputInstructions,
							info.getDirectives(), log);

					serverLog.debug("runTasks:" + tentativeInputTasks.keySet());
					runTasks(tentativeInputTasks, log);

					for (Entry<String, Task> entry : tentativeInputTasks.entrySet()) {
						String key = entry.getKey();
						Task inputTask = entry.getValue();

						// this.inputTasks.put(key, inputTask);
						// FIX: check unneeded errors if only INPUTLIST requested
						if (inputTask.result != null) {
							log.ok(String.format("Retrieved: %s = %s [%s]", key, inputTask.result,
									inputTask.result.getClass().getSimpleName()));

							this.inputTasks.put(key, inputTask);

							if (inputTask.log.indexedState.index >= 400) {
								log.warn(String.format("Errors for input of %s: %s", key,
										inputTask.log.indexedState.getMessage()));
							} else if (inputTask.log.indexedState.index >= 300) {
								log.note(String.format("Warnings for input of %s: %s", key,
										inputTask.log.indexedState.getMessage()));
							}
						} else {
							log.warn(inputTask.log.indexedState.getMessage());
							log.log(HttpLog.HttpStatus.PRECONDITION_FAILED,
									String.format("Retrieval failed: %s=%s", key, inputTask));
							log.resetState(); // Forget that anyway...
							inputTask.close();
						}
					}

					/*
					 * for (Task inputTask : tentativeInputTasks.values()) { inputTask.close(); }
					 */

				}

				/// "MAIN"
				if (instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)) {
					// if (instructions.isSet(ActionType.GENERATE)){
					log.note("Running Generator: " + info.PRODUCT_ID);

					File fileTmp = productPathTmp.getAbsolutePath().toFile();

					try {
						/*
						 * TODO: StringMapper -based dynamic Path TODO: Needs more envs, like
						 * TIMESTAMP_DIR log.experimental(relativeOutputPath.toString()); Path test =
						 * Paths.get(cachePathSyntax.toString(getParamEnv())).normalize();
						 * log.experimental(test.toString());
						 */
						generator.generate(this);
					} catch (IndexedState e) {

						// NOTE: possibly the file has been generated, but with some less significant
						// errors.
						log.log(e);

						try {
							log.warn(String.format("Error index=%d, msg: %s", e.getIndex(), e.getMessage()));
							pserver.delete(productPathTmp.getAbsolutePath(), log);
							// this.delete(fileFinal);
						} catch (Exception e2) {
							log.error(e2.getLocalizedMessage());
						}
					}

					if (fileTmp.length() > 0) {

						/// Override
						log.setStatus(Log.Status.OK);
						log.debug(String.format("OK, generator produced tmp file: %s", productPathTmp.getAbsolutePath()));
						serverLog.success(String.format("generated: %s", info.getFilename()));

						// Let's take this slowly...
						try {
							pserver.move(productPathTmp.getAbsolutePath(), productPath.getAbsolutePath(), log);
							log.success(productPath.getAbsolutePath().toString());
							// this.copy(productPaths.getAbsolutePath()Tmp, productPaths.getAbsolutePath());
						} catch (IOException e) {
							log.warn(e.toString());
							// log.warn(String.format("filePerms: %s", filePerms));
							log.log(HttpLog.HttpStatus.FORBIDDEN,
									String.format("Failed in moving tmp file: %s", productPathTmp.getAbsolutePath()));
							// log.error(String.format("Failed in moving tmp file: %s", productPaths.getAbsolutePath()));
						}

						if (!Files.isSymbolicLink(productPath.getAbsolutePath())) {
							try {
								// Todo: skip this if already ok...
								// Check: is needed? this.move contains copy_attributes
								Files.setPosixFilePermissions(productPath.getAbsolutePath(), filePerms);
							} catch (IOException e) {
								log.warn(e.toString());
								log.warn(String.format("Failed in setting perms %s for file: %s", filePerms,
										productPath.getAbsolutePath()));
								// log.warn(String.format("filePerms: %s", filePerms));
								// log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Failed in setting perms
								// for file: %s", productPaths.getAbsolutePath()));
								// log.error(String.format("Failed in moving tmp file: %s", productPaths.getAbsolutePath()));
							}
						}

					} else {
						// server Log.fail(info.getFilename());
						// serverLog.warn(String.format("Problems: %s", log.indexedState.toString()));
						if (log.indexedState.getIndex() >= 400) {
							log.debug("Forwarding original error (at least HTTP 400)");
						} else {
							log.debug("Generator failed but returned no error code");
							log.log(HttpLog.HttpStatus.CONFLICT,
									String.format("Generator '' failed in producing the file: %s", productPath.getAbsolutePath()));
						}

						if (instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)) {
							// if (instructions.isSet(Instructions.GENERATE)) {
							try {
								log.info(String.format("Failed in generating: %s, removing result file(s)", info));

								if (productPathTmp.getAbsolutePath().toFile().exists())
									pserver.delete(productPathTmp.getAbsolutePath(), log);
							} catch (Exception e) {
								/// TODO: is it a fatal error if a product defines its input wrong?
								log.error(e.getMessage()); // RETURN?
								log.log(HttpLog.HttpStatus.FORBIDDEN,
										String.format("Failed in deleting tmp file: %s", productPathTmp.getAbsolutePath()));
							}
						}
					}

				}

			}

			// if (instructions.isSet(ActionType.INPUTLIST) &&
			// !instructions.involves(ActionType.GENERATE)) {
			if (instructions.isSet(ActionType.INPUTLIST)
					&& instructions.makeLevelBelow(Instructions.MakeLevel.GENERATE)) {
				log.note("Input list: requested");
				// result = inputs;
			}

			if (instructions.isSet(MediaType.FILE)) {
				// TODO: save file (of JavaGenerator)
				// if (this.result instanceof Path)...

				if (this.instructions.makeLevelEquals(Instructions.MakeLevel.DELETE)) {
					if (fileFinal.exists())
						log.log(HttpLog.HttpStatus.FORBIDDEN,
								String.format("Deleting failed: %s (%d bytes)", fileFinal, fileFinal.length()));
					else {
						log.resetState();
						log.log(HttpLog.HttpStatus.ACCEPTED, String.format("File does not exist: %s", fileFinal)); // Consider
																													// storing
																													// last
																													// messages
																													// in
																													// LOG?
																													// See
																													// HttpLog.indexedState
						// log.ok("Deleted."); // Consider storing last messages in LOG? See
						// HttpLog.indexedState
						// System.err.println("DELE: " + log.indexedState.getMessage());
					}
				} else if (this.instructions.makeLevelEquals(Instructions.MakeLevel.EXISTS)) {
					if (!fileFinal.exists()) {
						log.log(HttpLog.HttpStatus.CONFLICT,
								String.format("File does not exist: %s ", productPath.getAbsolutePath()));
					} else if (fileFinal.length() == 0) {
						log.log(HttpLog.HttpStatus.CONFLICT,
								String.format("File exists, but is empty: %s ", productPath.getAbsolutePath()));
					} else {
						result = fileFinal;
						log.log(HttpLog.HttpStatus.OK, String.format("File exists: %s ", productPath.getAbsolutePath()));
					}
				} else if (fileFinal.length() == 0) {

					log.debug(String.format("Empty file or no file %s", productPath.getAbsolutePath()));
					// TODO: consider method for this:
					if (log.indexedState.getIndex() >= 400) {
						log.debug("Forwarding original error (at least HTTP 400)");
					} else {
						// log.debug("Generator failed but returned no error code");
						// log.log(HttpLog.HttpStatus.CONFLICT, String.format("Generator '' failed in
						// producing the file: %s", productPaths.getAbsolutePath()));
						log.log(HttpLog.HttpStatus.CONFLICT,
								String.format("Yes, failed in generating: %s ", productPath.getAbsolutePath()));
					}

					try {
						pserver.delete(productPath.getAbsolutePath(), log);
					} catch (IOException e) {
						// log.error(String.format("Failed in deleting: %s ", productPaths.getAbsolutePath()));
						log.log(HttpLog.HttpStatus.FORBIDDEN,
								String.format("Failed in deleting: %s ", productPath.getAbsolutePath()));
						log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
					}

				} else {

					this.result = productPath.getAbsolutePath();
					log.ok(String.format("Exists: %s (%d bytes)", this.result, fileFinal.length()));

					if (this.instructions.isSet(PostProcessing.SHORTCUT)) {
						if (this.info.isDynamic()) {
							try {
								// Path dir = ensureDir(cacheRoot, productDir);
								Path dir = CACHE_ROOT.resolve(getProductDir(info.getID()));
								FileUtils.ensureWritableDir(dir, GROUP_ID, dirPerms);
								pserver.link(productPath.getAbsolutePath(), dir, true, log);
							} catch (IOException e) {
								log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
							}
						} else
							log.debug("Static product (no TIMESTAMP), skipped shortcut");
					}

					if (this.instructions.isSet(PostProcessing.LATEST)) {
						if (this.info.isDynamic()) {
							try {
								Path dir = CACHE_ROOT.resolve(getProductDir(info.getID()));
								FileUtils.ensureWritableDir(dir, GROUP_ID, dirPerms);
								pserver.link(productPath.getAbsolutePath(), dir.resolve(this.info.getFilename("LATEST")), true, log);
								log.ok(String.format("Linked as LATEST in dir: %s", dir));
							} catch (IOException e) {
								log.log(HttpLog.HttpStatus.FORBIDDEN,
										String.format("Linking to LATEST failed: %s", e.getMessage()));
							}
						} else
							log.debug("Static product (no TIMESTAMP), skipped shortcut");
					}

					if (this.instructions.isSet(PostProcessing.STORE)) {
						// Todo: traverse storage paths.
						Path storageDir = STORAGE_ROOT.resolve(productPath.getRelativeDir());
						Path storedFile = storageDir.resolve(this.info.getFilename());
						if (Files.exists(storedFile)) {
							log.experimental(String.format("Store: file exists already: %s", storedFile));
						} else {
							try {
								// ensureDir(storageRoot, relativeOutputDir);
								FileUtils.ensureWritableDir(storageDir, GROUP_ID, dirPerms);
								pserver.copy(productPath.getAbsolutePath(), storedFile, log);
								log.experimental(String.format("Stored in: %s", storedFile));
							} catch (IOException e) {
								log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
								log.resetState();
								log.fail(String.format("Store: %s", storedFile));
							}
						}
					}

					// Complementary tasks: copying
					// for (Path path : this.instructions.copies) {
					for (String path : this.instructions.copies) {
						Path p = Paths.get(path);
						try {
							if (p.startsWith(CACHE_ROOT)) { // XXX
								FileUtils.ensureWritablePath(path, GROUP_ID, dirPerms);
							}
							pserver.copy(productPath.getAbsolutePath(), p, log); // Paths.get(path)
							log.ok(String.format("Copied: %s", p));
							// System.out.println("Copy "+path);
						} catch (IOException e) {
							log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Copying failed: %s", p));
							log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
							// log.error(String.format("Copying failed: %s", path));
						}
					}

					// Complementary tasks: linking
					for (String path : this.instructions.links) {
						Path p = Paths.get(path);
						try {
							if (p.startsWith(CACHE_ROOT)) {
								FileUtils.ensureWritablePath(path, GROUP_ID, dirPerms);
							}
							pserver.link(productPath.getAbsolutePath(), p, false, log); // Paths.get(path)
							log.ok(String.format("Linked: %s", p));
						} catch (IOException e) {
							log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Linking failed: %s", p));
							log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
						}
					}

					// Complementary tasks: moving (single-object operation)
					if (this.instructions.move != null) {
						Path p = Paths.get(this.instructions.move);
						try {
							if (p.startsWith(CACHE_ROOT)) {
								FileUtils.ensureWritablePath(this.instructions.move, GROUP_ID, dirPerms);
							}
							pserver.move(productPath.getAbsolutePath(), p, log);
							this.result = this.instructions.move;
							log.log(HttpLog.HttpStatus.OK, String.format("Moved: %s", p));
						} catch (IOException e) {
							log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Moving failed: %s", p));
							log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
						}
					}

					try {
						Path p = productPathTmp.getAbsoluteDir();
						File dir = p.toFile();
						if (dir.exists()) {
							log.debug(String.format("Remove tmp dir: %s", p));
							// NEW! Empty the dir
							Files.walkFileTree(p,
									new FileUtils.MoveDir(p, productPath.getAbsoluteDir()));
							/*
							Files.walkFileTree(paths.outputDirTmp,
									new FileUtils.MoveDir(paths.outputDirTmp, paths.outputDir));
							*/
							/*
							 * for (File file: dir.listFiles()){ // if (file.isFile() || file.isDirectory())
							 * if (file.isFile()){ this.move(file.toPath(), this.outputDir); } else if
							 * (file.isDirectory()){ log.special("Moving dir " + file.toPath()); // In
							 * future, if this works, can do the whole thing...
							 * //Files.walkFileTree(file.toPath(), new FileUtils.MoveDir(file.toPath(),
							 * this.outputDir)); // Files.move(file.toPath(), this.outputDir,
							 * StandardCopyOption.REPLACE_EXISTING); } else {
							 * log.warn("Not knowing how to handle " + file.toPath()); } // Move dirs as
							 * well // Consider try-catch block here, and just collect errors?
							 * 
							 * } Files.delete(this.outputDirTmp);
							 */

						}
					} catch (IOException e) {
						e.printStackTrace(log.getPrintStream());
						log.warn("Move failed: " + e.getMessage());
						log.log(HttpLog.HttpStatus.SEE_OTHER,
								String.format("Failed in removing tmp dir %s", productPathTmp.getAbsoluteDir()));
					}

				}
			} else {
				if (this.result != null) // Object?
					log.info("Result: " + this.result.toString());
				log.note(String.format("Task completed: instructions=%s, status=%s", this.instructions,
						this.getStatus()));
				// status page?

			}

			long duration = java.lang.System.currentTimeMillis() - startTime;

			log.info(String.format("Duration %s (Timeout: %d)", durationFormat.format(duration), TIMEOUT));

			if (instructions.isSet(Instructions.RUN)) {
				try {
					// TODO: path to paths bundle
					ShellExec.OutputReader reader = new ShellExec.OutputReader(log.getPrintStream());
					ShellExec.exec("./run.sh", null, PRODUCT_ROOT.resolve(getProductDir(info.getID())), reader);
				} catch (Exception e) {
					log.error(String.format("Failed: %s", e));
				}

			}

			// if (graph != null) {
			// serverGraph.importGraph(graph); importNode?

			// }

		}

		/**
		 * Declaration of environment variables for external (shell) generators.
		 *
		 * @return - variables defining a product, its inputs, and output dir and file.
		 */
		public Map<String, Object> getParamEnv() {

			// BASE
			Map<String, Object> env = new TreeMap<String, Object>();
			// OLD this.info.getParamEnv(env); (see INPUT_PREFIX_BELOW)

			// EXTENDED this could be in ServerBase? or bundle
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
					break;
				case 0:
					break;
				default:
					log.log(HttpLog.HttpStatus.NOT_MODIFIED, String.format("Odd timestamp '%s' length (%d)",
							this.info.TIMESTAMP, this.info.TIMESTAMP.length()));
				}
			}

			// Consider keeping Objects, and calling .toString() only upon
			// ExternalGenerator?
			env.put("OUTDIR", productPathTmp.getAbsolutePath().getParent().toString()); // cacheRoot.resolve(this.relativeOutputDir));
			env.put("OUTFILE", productPathTmp.getAbsolutePath().getFileName().toString());

			if (!PATH.isEmpty()) {
				env.put("PATH", PATH);
				log.debug("PATH=" + PATH);
			}

			this.info.INPUT_PREFIX = null;
			this.info.INPUT_PREFIX_DIR = null;

			if (!this.inputTasks.isEmpty()) {
				int inputPrefixLength = 0;
				// int inputDirPrefixLength = 0;
				// env.put("INPUTKEYS", String.join(",", this.inputTasks.keySet().toArray(new
				// String[0])));
				// info.INPUT_KEYS = String.join(",", this.inputTasks.keySet().toArray(new
				// String[0]));
				ArrayList<String> inputKeys = new ArrayList<>();
				// info.INPUT_KEYS = String.join(",", this.inputTasks.keySet().toArray(new
				// String[0]));
				// FIX: String.join(",", retrievedInputs.keySet());
				// env.putAll(this.inputTasksNEW);
				for (Map.Entry<String, Task> entry : inputTasks.entrySet()) {
					String key = entry.getKey();
					Task inputTask = entry.getValue();
					if (inputTask.productPath.getAbsolutePath() != null) {

						env.put(key, inputTask.productPath.getAbsolutePath());
						inputKeys.add(key);

						Path dir = inputTask.productPath.getAbsolutePath().getParent();
						if (dir == null) {
							// At least one input is a plain filename -> skip whole thing.
							this.info.INPUT_PREFIX = null;
							break;
						}
						String d = dir.toString();
						if (this.info.INPUT_PREFIX == null) {
							this.info.INPUT_PREFIX = d;
							inputPrefixLength = d.length();
						} else {
							for (int i = 0; i < inputPrefixLength; i++) {
								if (i == d.length()) {
									inputPrefixLength = d.length();
									break;
								}
								if (d.charAt(i) != this.info.INPUT_PREFIX.charAt(i)) {
									inputPrefixLength = i;
									break;
								}
							}
						}
					}

				}

				info.INPUTKEYS = String.join(",", inputKeys);

				if (this.info.INPUT_PREFIX != null) {
					///
					// this.info.INPUT_PREFIX =
					this.info.INPUT_PREFIX = this.info.INPUT_PREFIX.substring(0, inputPrefixLength);
					// env.put("INPUT_PREFIX", this.info.INPUT_PREFIX);

					int lastDirSepIndex = Math.max(0, this.info.INPUT_PREFIX.lastIndexOf('/'));
					// env.put("INPUT_PREFIX_DIR", this.info.INPUT_PREFIX.substring(0,
					// lastDirSepIndex));
					this.info.INPUT_PREFIX_DIR = this.info.INPUT_PREFIX.substring(0, lastDirSepIndex);

				} else {
					this.info.INPUT_PREFIX = "";
				}
			}

			/// Could warn, if overrides input variables.
			this.info.getParamEnv(env);

			env.putAll(this.info.getDirectives()); // may override input_prefix

			return env;
		}

		/**
		 * Write the graph describing this task into a file.
		 *
		 * @return – full path of the created file.
		 *
		 */
		public Path writeGraph() {
			// Path graphFile = CACHE_ROOT.resolve(paths.relativeGraphPath);
			log.special(String.format("Writing graph to file: %s", graphPath.getAbsolutePath()));

			// Graph graph = this.getGraph(null);
			// Graph graph = this.getGraph();
			Graph graph = new Graph(this.info.PRODUCT_ID);
			//graph.attributes.put("label", String.format("NutShell request: %s", this));
			graph.attributes.put("label", String.format("%s", this));

			TaskGraphNode.drawGraph(this, graph);

			// graph.graphProto.attributes.put("size", "24,20");
			// graph.nodeProto.attributes.put("shape", "record");
			graph.nodeProto.attributes.put("shape", "box");
			try {
				// Path graphDir = graphFile.getParent();
				FileUtils.ensureWritableDir(graphPath.getAbsoluteDir(), GROUP_ID, dirPerms);
				graph.dotToFile(graphPath.getAbsolutePath().toString()); // svg
				graph.dotToFile(graphPath.getAbsolutePath().toString() + ".dot"); // debugging
				Files.setPosixFilePermissions(graphPath.getAbsolutePath(), filePerms);
			} catch (IOException | InterruptedException e) {
				log.warn(e.getMessage());
				graph.toStream(log.getPrintStream());
				log.fail(String.format("Failed in writing graph to file: %s", graphPath.getAbsolutePath()));
			}
			return graphPath.getAbsolutePath();
		}

	} // Task

	/**
	 * Creates Tasks from product definitions. Ensures directories for copying,
	 * linking, and moving.
	 *
	 * @param batch
	 * @param log
	 * @return
	 */
	public Map<String, Task> prepareTasks(Batch batch, HttpLog log) {
		return prepareTasks(batch.products, batch.instructions, batch.directives, log);
	}

	/**
	 * Creates Tasks from product definitions. Ensures directories for copying,
	 * linking, and moving.
	 *
	 * @param productRequests
	 * @param instructions    – shared product request handling instructions
	 * @param directives      – shared product generation auxiliary instructions.
	 * @param log             – parent log, under which each Task creates a separate
	 *                        log.
	 * @return
	 */
	public Map<String, Task> prepareTasks(Map<String, String> productRequests, Instructions instructions,
			Map<String, String> directives, HttpLog log) {

		Map<String, Task> tasks = new HashMap<>();

		final int productCount = productRequests.size();

		if (productCount == 0) {
			return tasks;
		}

		// log.info(String.format("Preparing %d tasks: %s", productCount,
		// productRequests.keySet()));
		log.info(String.format("Preparing %d tasks, Instructions: %s, Directives: %s ", productCount, instructions,
				directives));
		/*
		 * log.info(String.format("Instructions: %s (depth: %d), Directives: %s ",
		 * instructions, instructions.regenerateDepth, directives));
		 */

		final boolean MULTIPLE = (productCount > 1);
		if (MULTIPLE) { // and parallel
			// log.special("parallel");
			//
		}

		/// Check COPY & LINK targets: must be directories, if several tasks (several
		/// files produced)
		for (String path : instructions.copies) {
			Path p = Paths.get(path);
			if (p.startsWith(CACHE_ROOT)) { // XXX
				try {
					FileUtils.ensureWritablePath(path, GROUP_ID, dirPerms);
				} catch (IOException e) {
					log.warn(e.getMessage());
				}
			}
			if (MULTIPLE && !p.toFile().isDirectory()) {
				log.warn(String.format("Several tasks (%d), but single file COPY target: %s", productCount, p));
			}
		}
		//
		for (String path : instructions.links) {
			Path p = Paths.get(path);
			if (p.startsWith(CACHE_ROOT)) { // XXX
				try {
					FileUtils.ensureWritablePath(path, GROUP_ID, dirPerms);
				} catch (IOException e) {
					log.warn(e.getMessage());
				}
			}
			if (MULTIPLE && !p.toFile().isDirectory()) {
				log.warn(String.format("Several tasks (%d), but single file LINK target: %s", productCount, p));
			}
		}

		if (instructions.move != null) {
			Path p = Paths.get(instructions.move);
			if (p.startsWith(CACHE_ROOT)) { // XXX
				try {
					FileUtils.ensureWritablePath(instructions.move, GROUP_ID, dirPerms);
				} catch (IOException e) {
					log.warn(String.format("Problems ahead: %s", e.getMessage()));
					// throw new RuntimeException(e);
				}
			}
			if (MULTIPLE && !p.toFile().isDirectory()) {
				log.warn(String.format("Several tasks (%d), but single file MOVE target: %s", productCount, p));
			}
		}

		for (Entry<String, String> entry : productRequests.entrySet()) {

			String key = entry.getKey();
			String value = entry.getValue();
			// log.debug(String.format("Starting: %s = %s", key, value));

			try {

				// Task task = new Task(value, instructions.value, log);
				Task task = new Task(value, instructions, log);
				task.info.setDirectives(directives);
				/*
				 * task.instructions.addCopies(instructions.copies);
				 * task.instructions.addLinks(instructions.links);
				 * task.instructions.addMove(instructions.move); // Thread-safe?
				 * task.instructions.makeLevel = instructions.makeLevel;
				 */
				/*
				 * if (task.instructions.makeLevel > 0){ task.instructions.add(ActionType.MAKE);
				 * }
				 */

				log.info(String.format("[%s] Task %s", key, task)); // , task.instructions
				if ((directives != null) && !directives.isEmpty())
					log.debug(String.format("[%s] Directives (final): %s", key, directives));

				// log.info(task.toString());

				task.log.setVerbosity(log.getVerbosity());
				// task.log.decoration.set(Log.OutputFormat.COLOUR);
				if (task.log.logFile != null) {
					log.note(String.format("[%s] Log: %s", key, task.log.logFile));
				}

				tasks.put(key, task);

			} catch (ParseException e) {
				// A fatal error if a product defines its input wrong? Probably yes...
				log.warn(String.format("Could not parse product: %s(%s)", key, value));
				log.log(HttpLog.HttpStatus.NOT_ACCEPTABLE, e.getLocalizedMessage());
			} catch (Exception e) {
				// System.err.println(String.format("EROR2 here: %s = %s", key, value));
				log.warn(e.getMessage());
				log.error(String.format("Unexpected exception in creating product %s(%s)", key, value));
				log.log(HttpLog.HttpStatus.INTERNAL_SERVER_ERROR, e.getLocalizedMessage());
			} finally {
				// System.out.println("Final.." + key);
			}

		}
		return tasks;
	}

	/**
	 * Execute tasks, optionally in parallel threads.
	 *
	 * @param tasks
	 * @param log
	 * @return
	 */
	public Map<String, Task> runTasks(Map<String, Task> tasks, HttpLog log) {

		if (tasks.isEmpty()) {
			// log.debug("No subtasks");
			return tasks;
		}

		log.note(String.format("Starting (%d) tasks, GROUP_ID=%d", tasks.size(), GROUP_ID));
		// log.debug(String.format("Starting (%d) tasks, GROUP_ID=%d", tasks.size(),
		// GROUP_ID));

		/// Loop 1: if PARALLEL requested, start task as a thread
		for (Entry<String, Task> entry : tasks.entrySet()) {
			String key = entry.getKey();
			Task task = entry.getValue();
			// task.log.debug("Decoration: " + task.log.decoration.toString());
			if (task.instructions.isSet(ActionType.PARALLEL)) {
				try {
					log.info(String.format("Starting task[%d] '%s': %s as a thread", task.getTaskId(), key, task));
					// log.debug(String.format("Starting thread '%s': %s", key, task));
					task.start(); // Thread.start() -> (Thread)task.run()
				} catch (IllegalStateException e) {
					log.error("Already running? " + e);
				}
			}
		}

		/// Loop 2: run tasks, or if already started above, wait to stop
		for (Entry<String, Task> entry : tasks.entrySet()) {
			String key = entry.getKey();
			Task task = entry.getValue();
			try {
				if (task.instructions.involves(ActionType.PARALLEL)) {
					task.join();
				} else {
					log.info(String.format("Starting task '%s': %s (in main thread)", key, task));
					task.execute();
				}

				log.info(String.format("Finished task: %s(%s)[%d]", key, task.info.PRODUCT_ID, task.getTaskId()));

			} catch (InterruptedException e) {
				log.warn(String.format("Interrupted task: %s(%s)[%d]", key, task.info.PRODUCT_ID, task.getTaskId()));
				log.warn(String.format("Pending file? : %s", task.productPathTmp.getAbsolutePath()));
			}

			IndexedState state = task.log.indexedState;

			// log.debug(String.format("%s status: %s", key, state.getMessage()));

			// FIX: check/skip unneeded errors if only INPUTLIST requested
			if (task.result != null) {
				if (state.index >= 400) { // ?
					log.warn(String.format("Error for %s: %s", key, state.getMessage()));
				} else if (state.index >= 300) {
					log.note(String.format("Notes for %s: %s", key, state.getMessage()));
				}
			} else if (task.instructions.makeLevelAtLeast(Instructions.MakeLevel.EXISTS)) {
				log.warn(state.getMessage());
				log.log(HttpLog.HttpStatus.PRECONDITION_FAILED, String.format("Task [%s] failed: %s", key, task));
				log.resetState(); // Forget that anyway...
			}

		}

		return tasks;
	}

	/// Checks if a file exists in cache or storage, wait for completion if needed.
	/// Delete if outdated.
	/**
	 *
	 * return immediately if non-empty or nonexistent, else wait for an empty file
	 * to complete.
	 * 
	 * @param file
	 * @param maxEmptySec maximum age of empty file in seconds
	 * @param log         - stream to write success of the process
	 * @return - consider -1 not exists, 0 = exists cold, 1.. hot waited for seconds
	 * @throws InterruptedException
	 */
	public int queryFile(File file, int maxEmptySec, HttpLog log) throws InterruptedException {

		if (!file.exists()) {
			log.log(HttpLog.HttpStatus.SEE_OTHER, String.format("File does not exist: %s", file));
			return -1;
		}

		int remainingSec = this.TIMEOUT;

		final long fileLength = file.length();

		if (fileLength > 0) {
			// log.note("File found");
			log.log(HttpLog.HttpStatus.OK, String.format("File found: %s (%d bytes)", file.getName(), fileLength));
			return 0;
		} else { // empty file
					// long ageSec = (java.lang.System.currentTimeMillis() - file.lastModified()) /
					// 1000;
			long ageSec = FileUtils.fileModificationAge(file);
			if (ageSec > maxEmptySec) {
				log.log(HttpLog.HttpStatus.SEE_OTHER, String.format("Time %d", java.lang.System.currentTimeMillis()));
				log.log(HttpLog.HttpStatus.SEE_OTHER, String.format("File %d", file.lastModified()));
				log.log(HttpLog.HttpStatus.NOT_MODIFIED,
						String.format("Outdated empty file, age=%d min, (max %d s)", (ageSec / 60), maxEmptySec));
			} else {
				log.log(HttpLog.HttpStatus.SEE_OTHER, "Empty fresh file exists, waiting for it to complete...");
				for (int i = 1; i < 10; i++) {
					// int waitSec = i*i; 1, 4, 9, 16, 25, // 1 5 13 29 54
					int waitSec = 2 << (i - 1);// 1, 2, 4, 16, 32, // 1 3 7 31 63
					// log.warn(String.format("Waiting for %d s...", waitSec));
					log.log(HttpLog.HttpStatus.CONTINUE, String.format("Waiting for %d s...", waitSec));
					TimeUnit.SECONDS.sleep(waitSec);
					if (file.length() > 0) {
						log.log(HttpLog.HttpStatus.CREATED, "File appeared");
						return (this.TIMEOUT - remainingSec);
					}
					remainingSec = remainingSec - waitSec;
					if (remainingSec <= 0)
						break;
				}
				log.log(HttpLog.HttpStatus.NOT_MODIFIED,
						String.format("Timeout - file did not appear (grow) in %d s", maxEmptySec));
			}

			try {
				log.note("Deleting file");
				delete(file.toPath(), log);
				// this.delete(productPaths.getAbsolutePath());
			} catch (IOException e) {
				// TODO: redesign (check if delete needed at all)
				log.log(HttpLog.HttpStatus.CONFLICT,
						String.format("Failed in deleting file: %s, %s", file.toPath(), e.getMessage()));
			}

			return -1;
		}
	}

	/// System side setting.
	// public String pythonScriptGenerator = "generate.py";

	/**
	 * A container, a "super task" for generating one or several products.
	 *
	 * Limitation: instructions and directives are shared for all the products.
	 */
	static class Batch {

		public Map<String, String> products = new TreeMap<>();
		public Instructions instructions = new Instructions();
		public Map<String, String> directives = new TreeMap<>();

		/// Future fix: timeout should be adjustable for each batch
		// private int timeOut = 0;

	}

	public void populate(ProgramRegistry registry) {

		registry.add(new ProgramUtils.Help(registry));
		registry.add(new ProgramUtils.LogLevel(serverLog));
		registry.add(new ProgramUtils.LogLevel.Debug(serverLog));
		registry.add(new ProgramUtils.LogLevel.Verbose(serverLog));

		/*
		 * registry.add(new Parameter.Simple<Float>("test", "Log file format.", (float)
		 * 123.456){
		 * 
		 * @Override public void exec() { System.err.println(" VALUE="+value);
		 * System.err.println(Manip.toString(this));
		 * System.err.println(getClass().getGenericSuperclass().getTypeName());
		 * System.err.println(value.getClass()); } });
		 */

		registry.add(new Parameter.Simple<String>("conf", "Read configuration file", "") {
			/// It is recommended to give --conf as the first option, unless default used.
			@Override
			public void exec() {
				readConfig(value);
			}
		});

		registry.add(new Parameter.Simple<String>("killall",
				String.format("Kill named process(es) of this USER=%s", USER), "") {

			@Override
			public void exec() {

				String[] killCmd = new String[] { "killall", value };
				serverLog.special("Executing KILL command: " + Arrays.toString(killCmd));

				ShellUtils.ProcessReader handler = new ShellUtils.ProcessReader() {
					@Override
					public void handleStdOut(String line) {
						serverLog.note(line);
					}

					@Override
					public void handleStdErr(String line) {
						serverLog.warn(line);
					}
				};

				int result = 0;
				try {
					result = ShellExec.exec(killCmd, null, null, handler);
				} catch (IOException | InterruptedException | TimeoutException e) {
					// throw new RuntimeException(e);
					serverLog.error(String.format("%s failed: %s", getName(), e.getMessage()));
					return;
				}

				serverLog.special("Return code: " + result);

			}
		});

		registry.add(
				new Parameter.Simple<TextOutput.Format>("log_format", "Log file format.", TextOutput.Format.VT100) {

					@Override
					public void exec() {
						serverLog.setFormat(value); // needed?
						LOG_SERVER = serverLog.getConf();
						// String.format("%s %s", serverLog.textOutput, serverLog.decoration);
						LOG_TASKS = LOG_SERVER;
						// server Log.debug(server Log.textOutput.toString());
						serverLog.special(String.format("New state for all logs: '%s'", LOG_SERVER));
						// serverLog.deprecated(String.format("Use generalized command --log '%s'",
						// value));
						// server Log.debug(server Log.textOutput.toString());
					}
				});

		registry.add(new Parameter.Simple<String>("log_style",
				"Set decoration: " + serverLog.decoration.getAllFlags().keySet(), "") {

			@Override
			public void setParam(String key, Object value) throws NoSuchFieldException, IllegalAccessException {
				// super.setParam(key, value);
				serverLog.deprecated(String.format("Use generalized command --log '%s'", value));
				String s = value.toString();

				if (s.isEmpty()) {
					serverLog.decoration.clear();
					// LOG_STYLE.clear();
				} else {
					serverLog.decoration.set(s);
					// LOG_STYLE.set(value.toString());
				}
				LOG_SERVER = serverLog.getConf();
				LOG_TASKS = LOG_SERVER;
				// serverLog.decoration.set(LOG_STYLE);
			}

		});

		registry.add(new Parameter.Simple<String>("log",
				String.format("Set log properties: verbosity %s, format %s, decoration %s ", Log.statusCodes.values(), // Arrays.toString(Log.Status.values()),
						Arrays.toString(TextOutput.Format.values()), Arrays.toString(TextOutput.Options.values())),
				"INFO,TEXT") {

			@Override
			public void exec() {
				serverLog.set(value);
				if (serverLog.decoration.isSet(TextOutput.Options.COLOUR)
						&& (serverLog.getFormat() == TextOutput.Format.TEXT)) {
					serverLog.setFormat(TextOutput.Format.VT100);
				}
				LOG_SERVER = serverLog.getConf();
				LOG_TASKS = LOG_SERVER;
				serverLog.special(String.format("New state for all logs: '%s'", LOG_SERVER));

			}
		});

		registry.add(new Parameter<ProductServer>("gid", "Unix file group id (gid) to use.", this, "GROUP_ID"));

		// Consider: to NutLet and @ShellExec
		registry.add(new Parameter<ProductServer>("timeout", "Time in seconds to wait.", this, "TIMEOUT") {
			@Override
			public void exec() {
				ShellExec.TIMEOUT_SEC = TIMEOUT;
			}
		});

		registry.add(new Parameter<ProductServer>("counter", "Initial value of task counter (id).", this));

		registry.add(new Parameter.Single("parseOld", "Debugging (cmd line only): parse product.", "filename") {

			public String filename = "";

			@Override
			public void exec() {
				try {
					Task product = // server.
							new Task(filename, new Instructions(), serverLog);
					Map<String, Object> map = product.getParamEnv();
					String[] array = MapUtils.toArray(map);
					for (String s : array) {
						System.out.println(s);
					}
					// System.out.println(String.format("time : %d", product.info.time ));
					// System.out.println(String.format("time2: %d", product.info.time2));
				} catch (ParseException e) {
					serverLog.fail(e.getMessage());
				} catch (Exception e) {
					e.printStackTrace(System.err);
					serverLog.fail(e.getMessage());
				}
			}

		});

	}

	// Note: this turns program registry dynamic. It should be shared, static?
	static public void populate(Batch batch, ProgramRegistry registry) {

		// registry.add(new InstructionParameter(batch.instructions));
		registry.add(batch.instructions.getProgramParameter());

		for (String instr : ClassUtils.getConstantKeys(Instructions.class)) { // consider instant .getClass()
			registry.add(batch.instructions.getProgramParameter(instr));
			// registry.add(instr.toLowerCase(), new
			// InstructionParameter(batch.instructions, instr));
		}

		for (Instructions.MakeLevel level : Instructions.MakeLevel.values()) { // consider instant .getClass()
			registry.add(batch.instructions.getProgramParameter(level));
			// registry.add(level.name().toLowerCase(), new
			// InstructionParameter(batch.instructions, level));
			// .toLowerCase()
		}

		registry.add(new Parameter.Simple<String>("product", "Set product filename (repeatable)", "") {
			@Override
			public void exec() {
				int i = batch.products.size();
				batch.products.put("product" + i, value);
			}
		});

		registry.add(new Parameter<Instructions>("label", "Marker for logs and tmps", // supporting %d=task-id
																						// [%s=user].",
				batch.instructions, "label"));

		/*
		 * registry.add(new Parameter<Instructions>("regenerate",
		 * "Deprecating, use --depth instead.", batch.instructions, "regenerateDepth"));
		 */

		// deprecating
		/*
		 * registry.add(new Parameter<Instructions>("depth",
		 * "Cache clearance depth (0=EXISTS, 0=MAKE, 1=GENERATE, N...: remake inputs)",
		 * batch.instructions, "makeLevel"));
		 * 
		 * 
		 * registry.add(new Parameter.Single("regenerate",
		 * "Deprecating, use --depth instead.",
		 * //"Cache clearance depth (0=MAKE, 1=GENERATE, N...: remake inputs)",
		 * "depth"){
		 * 
		 * public int depth;
		 * 
		 * @Override public void exec() { serverLog.deprecated("Use --depth instead.");
		 * batch.instructions.makeLevel = depth; } });
		 */

		registry.add(
				new Parameter.Simple<String>("directives", "Set application (env) variables separated with '|'", "") {
					@Override
					public void exec() {
						// System.err.print(String.format("Type: %s %s", value.getClass(), value));
						MapUtils.addEntries(value, "\\|", "true", batch.directives);
					}
				});

	}

	/**
	 * Command-line interface for the NutShell product server
	 *
	 */

	public static void main(String[] args) {

		final ProductServer server = new ProductServer();

		HttpLog log = server.serverLog;
		log.setVerbosity(Log.Status.DEBUG);
		log.decoration.set(TextOutput.Options.COLOUR);

		ProgramRegistry registry = new ProgramRegistry();
		// NEW global (batch-independent)
		server.populate(registry);

		Batch batch = new Batch();
		server.populate(batch, registry);

		registry.add(new ProgramUtils.Version<>(server));

		/**
		 * This is for standard tests. Given a command line, construct corresponding GET
		 * parameters for an URL.
		 * 
		 */
		registry.add(new Parameter("http_params", "Debugging/testing: compose HTTP GET params.") {

			@Override
			public void exec() {
				String[] p = batch.products.values().toArray(new String[0]);
				if (batch.products.isEmpty()) {
					log.error("Product not defined yet, try: [--product] <FILE>" + this.getName());
				} else {

					// if (batch.instructions.isEmpty())
					// batch.instructions.setMakeLevel(Instructions.MakeLevel.Make);

					if (batch.products.size() > 1) {
						log.warn("Several products defined, using last");
					}

					for (Map.Entry entry : batch.products.entrySet()) {
						try {
							Task task = server.new Task(entry.getValue().toString(), batch.instructions, log);
							System.out.println(String.format("instructions=%s&product=%s", batch.instructions,
									task.info.getFilename()));
						}
						catch (ParseException e) {
							// TODO: specify
							log.warn(entry.getValue().toString());
							log.error(e.getMessage());
						}
						catch (IOException e) {
							// TODO: specify
							log.warn(entry.getValue().toString());
							log.error(e.getMessage());
						}
					}

					// Do not actually process the request.
					batch.instructions.setMakeLevel(Instructions.MakeLevel.NONE);
				}
			}
		});

		registry.add(new Parameter.Simple<String>("path", "Extend PATH variable for product generation", "") {
			public void exec() {
				server.PATH = value;
				server.setup.put("cmdPath", server.PATH); // consider PARASITER...
			}
		});

		registry.add(new Parameter.Simple<String>("copy", "Copy generated product(s) to file (dir). Repeatable.", "") {
			@Override
			public void exec() {
				batch.instructions.addCopy(value);
			}
		});

		registry.add(
				new Parameter.Simple<String>("link", "Copy generated product/products to file/dir. Repeatable.", "") {
					@Override
					public void exec() {
						batch.instructions.addLink(value);
					}
				});

		registry.add(
				new Parameter.Simple<String>("move", "Move generated product/products to file/dir. Repeatable", "") {
					@Override
					public void exec() {
						batch.instructions.addMove(value);
					}
				});

		/// Command line only
		registry.add(new Parameter("clear_cache", // .Simple<String>
				"Clear cache (and exit.)" // reconsider exit
		) {
			@Override
			public void exec() {
				log.warn("Clearing cache");
				try {
					server.clearCache(true);
					System.exit(0); // reconsider exit
				} catch (IOException e) {
					log.log(HttpLog.HttpStatus.CONFLICT, "Clearing cache failed");
					System.exit(4);
				}
			}
		});

		registry.add(new Parameter.Simple<String>("catalog", "List products found under productRoot", "") {

			public void exec() {

				GeneratorTracker tracker = server.new GeneratorTracker(server.PRODUCT_ROOT.resolve(value));

				try {
					System.out.println(String.format("List products (dir=%s)", tracker.startDir));
					tracker.run();
					System.out.println(tracker.generators);
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		});

		// Field[] instructionFields = Instructions.class.getFields();
		if (args.length == 0) {
			args = new String[] { "--help" };
		}

		// log.special("Instructions..." + batch.instructions);

		try {
			for (int i = 0; i < args.length; i++) {

				String arg = args[i];
				// log.info(String.format("ARGS[%d]: %s", i, arg));

				if (arg.charAt(0) == '-') {

					if (arg.equals("-h")) {
						arg = "--help";
					} else if (arg.charAt(1) != '-') {
						throw new IllegalArgumentException("Short options (-x) not supported in this version (Java)");
					}

					String opt = arg.substring(2);

					if (registry.has(opt)) {
						Program.Parameter param = registry.get(opt);
						// log.special(String.format("Handling: '%s' -> %s has params:%b", opt, param,
						// param.hasParams()));
						if (param.hasParams()) {
							if (i < (args.length - 1)) {
								// log.special(String.format("%s has argument '%s'", param.getName(),
								// args[i+1]));
								param.setParams(args[++i]);
							} else
								param.setParams(""); // Support "premature" end of cmd line, esp. with --help
						}
						param.exec();
						log.debug(String.format("Handled: %s [%s]", opt, param));
						// log.special(param.toString());
						continue;
					} else {
						log.error(String.format("Unknown argument: %s", arg));
						System.exit(-1);
					}

				} else { // Argument does not start with "--"
					batch.products.put("product" + (batch.products.size() + 1), arg);
				}

				log.debug("Instructions: " + batch.instructions);
			}
		} catch (Exception e) {
			if (log.getVerbosity() >= Log.Status.NOTE.level) {
				e.printStackTrace(log.getPrintStream());
			}
			log.error(String.format("Unhandled exception: %s", e));
			// e.printStackTrace(log.printStream);
			System.exit(1);
		}

		if (batch.instructions.isEmpty()) {
			batch.instructions.setMakeLevel(Instructions.MakeLevel.MAKE);
		}

		int result = 0;

		// if (!batch.instructions.isEmpty())
		// log.debug("Instructions: " + batch.instructions);

		/*
		 * if (batch.instructions.isSet(ActionType.CLEAR_CACHE)) {
		 * log.warn("Clearing cache"); if (batch.instructions.value !=
		 * ActionType.CLEAR_CACHE){ batch.instructions.remove(ActionType.CLEAR_CACHE);
		 * log.warn(String.format("Discarding remaining instructions: %s",
		 * batch.instructions) ); }
		 * 
		 * try { server.clearCache(true); } catch (IOException e) {
		 * log.log(HttpLog.HttpStatus.CONFLICT, "Clearing cache failed"); result = 4; }
		 * System.exit(result); }
		 * 
		 */

		// Turhia/väärässä paikassa (tässä) (näin alussa?)
		for (String copy : batch.instructions.copies) {
			log.note(String.format("   COPY:\t %s", copy));
		}
		/*
		 * if (!batch.instructions.copies.isEmpty()) {
		 * log.note(String.format("   COPY(%d):\t %s", batch.instructions.copies.size(),
		 * batch.instructions.copies)); }
		 */

		for (String link : batch.instructions.links) {
			log.note(String.format("   LINK:\t %s", link));
		}
		/*
		 * if (!batch.instructions.links.isEmpty()) {
		 * log.note(String.format("   LINK(%d):\t %s", batch.instructions.links.size(),
		 * batch.instructions.links)); }
		 */

		if (batch.instructions.move != null)
			log.note(String.format("   MOVE: \t %s", batch.instructions.move));

		/*
		 * if (!batch.directives.isEmpty()) log.debug("Directives: " +
		 * batch.directives);
		 */

		/// MAIN
		Map<String, Task> tasks = server.prepareTasks(batch, log);

		// Graph graph = new Graph("ProductServer");
		server.runTasks(tasks, log);

		if (log.getStatus() <= Log.Status.ERROR.level) {
			log.warn(String.format("Errors (level: %d)", log.getStatus()));
			++result;
		}

		for (Entry<String, Task> entry : tasks.entrySet()) {

			// String key = entry.getKey();
			Task task = entry.getValue();
			if (task.log.indexedState.index >= HttpLog.HttpStatus.BAD_REQUEST.getIndex()) {
				// log.warn(String.format("Generator Exception: %s",
				// task.log.indexedState.getMessage()));
				log.warn(task.log.indexedState.getMessage()); // todo retrieve HTTP str
				// log.debug(task.log.indexedException);
				if (result < 20)
					++result;
			} else {
				log.info(String.format("Status:\t%s", task.log.indexedState.getMessage()));
			}

			// Collect information on overall dependencies ("product flow").
			TaskGraphNode.drawGraph(task, serverGraph);
			// task.getGraphNode(serverGraph, entry.getKey()+'$');

			if (task.productPath.getAbsolutePath().toFile().exists()) {
				log.ok(String.format("File exists:\t %s (%d bytes)", task.productPath.getAbsolutePath().toString(),
						task.productPath.getAbsolutePath().toFile().length()));
				// log.note(String.format("File exists:\t %s (%d bytes)",
				// task.outputPath.toString(), task.outputPath.toFile().length()));
			}

			if (task.instructions.isSet(ActionType.INPUTLIST)) {

				System.out.println();
				for (Map.Entry<String, String> ec : task.inputs.entrySet()) {
					System.out.println(String.format("%s:\t %s", ec.getKey(), ec.getValue()));
				}

			}

			if (task.instructions.isSet(OutputType.STATUS)) {
				Path p = task.writeGraph();
				log.experimental(String.format("Wrote graph %s", p));
			}

			/*
			 * if (server.collectStatistics){ Path dotFile =
			 * server.cacheRoot.resolve(task.relativeGraphPath);
			 * log.special(String.format("writing %s", dotFile));
			 * server.graph.dotToFile(dotFile.toString(), ""); }
			 */

			// task.close(); // Don't close! Task is a reference...
		}

		if (batch.instructions.isSet(OutputType.STATUS)) {
			//
			System.out.println();
			for (Entry<String, Object> entry : server.setup.entrySet()) {
				System.out.printf("%s = %s %n", entry.getKey(), entry.getValue());
			}

		}

		// System.err.println("Eksit");
		log.debug("Exiting..");
		log.close();
		System.exit(result);

	}

}
