package nutshell;

import sun.misc.Signal; // For interrupt?
import java.io.*;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import static java.nio.file.Files.*;


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
 *  Some version history
 *  3.2 Create dirs automatically under $CACHE_ROOT and  $STORAGE_ROOT
 *
 *  @author Markus Peura fmi.fi Jan 26, 2011
 */
public class ProductServer extends ProductServerBase { //extends Cache {

	public String getVersion(){
		return "3.51"; // Handle missing Dot
	}

	ProductServer() {
		setup.put("ProductServer", this.getVersion());
		// LABEL = "nutshell-"+getVersion();
	}


	/// Experimental: Change MAKE to GENERATE if positive, decrement for each input
	// TODO: consider general query depth (for inputs etc)
	static public int defaultRemakeDepth = 0;

	/** Prefix for the marker used in logs and temporary files.
	 *  To separate different users/processes in shared directories.
	 *
	 *  The label will be automatically appended also the counter number
	 *  and user.
	 *
	 */
	// public String LABEL; // "nutshell-"+getVersion(); // ""%d-%s"; // USER-counter

	/// System side settings.

	@Override
	public String toString() {
		return Config.getMap(this).toString() + "\n" + setup.toString();
	}

	public interface Generator {

		/** Create the product defined in task.
		 */
		void generate(Task task) throws IndexedState; // throws IOException, InterruptedException ;

		/** Declare inputs required for this product generation task.
		 *
		 * @param task
		 * @return
		 */
		Map<String, String> getInputList(Task task) throws IndexedState;

		/** Natural (native) media type for the generated product.
		 *
		 * @return MediaType.FILE or MediaType.MEMORY
		 */
		int getPrimaryMediaType();

	}

	/** Searches for shell side (and later, Java) generators
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
	 * A "tray" containing both the product query info and the resulting object if successfully queried.
	 * Task does not know about the generator. Notice GENERATE and getParamEnv.
	 */
	public class Task extends Thread {

		final HttpLog log;
		final ProductInfo info;

		final public int id;

		//long startTime; //java.lang.System.currentTimeMillis()

		final DateFormat durationFormat = new SimpleDateFormat("mm:ss.S");

		public int getTaskId() {
			return id;
		}

		/// Checked, "normalized" filename, with ordered parameters.
		final public String filename;

		final public Instructions instructions; // = new Instructions();

		public Path timeStampDir;
		public Path productDir;

		public Path relativeOutputDir;
		public Path relativeOutputDirTmp;
		public Path relativeOutputPath;

		// Logging & diagnostics
		private final Path relativeSystemDir;
		public Path relativeLogPath;
		public Path relativeGraphPath;

		public Path outputDir;
		public Path outputDirTmp;
		public Path outputPath;
		public Path outputPathTmp;
		public Path storagePath;

		//public final Map<String,String> directives = new HashMap<>();
		public final Map<String, String> inputs = new HashMap<>();

		// TODO rename inputTasks
		public final Map<String, Task> inputTasks = new HashMap<>();

		// Consider get node

		/** Returns the graph describing state of the product server. Ensures that this task is included.
		 *
		 * @param graph
		 * @return
		 */
		public Graph getGraph(Graph graph) {

			if (graph == null) {
				graph = new Graph(this.info.PRODUCT_ID);
				graph.attributes.put("label", String.format("NutShell request: %s", this));
			}

			// Ensure this task (and its descendants) on the Graph.
			Graph.Node node = getGraphNode(graph, null);

			return graph;
		}

		/** Retrieve a node of this task, including all its input tasks.
		 *
		 * @param graph
		 * @param id – identifier for this node; typically input variable name like $FIKOR, or product id radar.polar.fikor.
		 * @return – created node.
		 */
		public Graph.Node getGraphNode(Graph graph, String id){
			if (graph == null){
				graph = new Graph("request: " + this.info.PRODUCT_ID);
			};
			if (id == null)
				id = this.info.PRODUCT_ID;
			Graph.Node node = graph.getNode(id);
			node.attributes.put("style", "filled");
			// if (result != null){
			File p = outputPath.toFile();
			if (p.exists()){
				String color = "orange"; // ripe fruit
				long ageSeconds = (System.currentTimeMillis() - p.lastModified())/1000;
				if (ageSeconds < 60){ // 1 min
					color = "#60ff30";
				}
				else if (ageSeconds < 300){ // 5 mins
					color = "#90f000";
				}
				else if (ageSeconds < 3600){ // 1 hour
					color = "#b0f030";
				}
				else if (ageSeconds < 86400){ // 24 hours = 60*60*24
					color = "#c0d030";
				}
				node.attributes.put("color", color);
			}
			else {
				node.attributes.put("color", "gray");
			}

			node.attributes.put("comment", log.indexedState.getMessage().replace('"','\''));

			//if (result instanceof Path){
			Instructions instr = new Instructions();
			instr.set(OutputType.STATUS, ActionType.INPUTLIST); //, ActionType.MAKE);
			instr.toString();
			//instr.add(instructions.value & (ActionType.GENERATE | ActionType.MAKE));
			node.attributes.put("href", String.format(
					"?instructions=%s&amp;product=%s", instr, info.getFilename()));
					//"?instructions=GENERATE,STATUS&amp;product=%s", info.getFilename()));
			//}


			for (Map.Entry<String,Task> entry: inputTasks.entrySet()) {
				Task t = entry.getValue();
				Graph.Node n = t.getGraphNode(graph, entry.getKey()+"\n"+t.info.PRODUCT_ID);
				//System.out.println(String.format("%s:\t %s", ec.getKey(), ec.getValue()));
				Graph.Node.Link link = node.addLink(n);
				// TODO: Style
				if (t.log.indexedState.index > 300) {
					n.attributes.put("style", "filled");
					//n.attributes.put("fillcolor", "#ffc090");
					n.attributes.put("fillcolor", "white");
					link.attributes.put("style", "dashed");
					//link.attributes.put("label", t.log.indexedState.getMessage());
					link.attributes.put("label", t.log.indexedState.getIndex());
					// link.attributes.put("color", "red");
				}


				if (t.result == null){
					// link.attributes.put("color", "red"); // ""#800000");
					link.attributes.put("style", "dotted");
				}
				else {
					if (t.result instanceof Exception){
						link.attributes.put("color", "brown");
						// FIX: error msg
						link.attributes.put("label", t.result.getClass().getSimpleName());
					}
					else {
						link.attributes.put("color", "green");
						String label = ""+t.log.indexedState.getIndex(); // t.result.toString();
						if (t.instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)){
							// label = "GENERATE"; disturbs, consider line attribs only:
							link.attributes.put("style", "bold");
						}
						else {
						}
						/*
						else if (t.instructions.isSet(ActionType.MAKE)){
							label = ""+t.log.indexedState.getIndex(); //"MAKE";
						}
						else if (t.instructions.isSet(ActionType.EXISTS)){
							label = ""+t.log.indexedState.getIndex();
							link.attributes.put("style", "dotted");
						}*/
						// REMOVED link.attributes.put("label", label);
					}
					//link.attributes.put("", "");
				}
				// YYY

			}

			return node;
		};



		/**
		 * Product generation task defining a product instance and operations on it.
		 * <p>
		 * In this version, directives can be set but only through '?'
		 *
		 * @param productStr
		 * @param instructions - definition how a product is retrieved and handled thereafter - @see #Actions
		 * @param parentLog    - log of the parent task (or main process, a product server)
		 * @throws ParseException - if parsing productStr fails
		 */
		public Task(String productStr, Instructions instructions, HttpLog parentLog) throws ParseException {

			id = getProcessId();
			info = new ProductInfo(productStr);
			filename = info.getFilename();
			this.instructions = new Instructions(instructions);


			if (info.time > 0){
				int year = Integer.parseInt(info.TIMESTAMP.substring(0,4));
				if (year > 2030){
					throw new ParseException(
							String.format("NSH failed in parsing YEAR: %s -> %d", info.TIMESTAMP, year), 0);
				}
			}

			// Accept only word \\w chars and '-'.
			//final String label = String.format("%s_%d-%s.%d", instructions.label, getTaskId(), USER, GROUP_ID).replaceAll("[^\\w\\-\\.\\:@]", "-");
			//StringBuffer sb = new StringBuffer();
			ArrayList<String> labelArray = new ArrayList<>();
			if (!instructions.label.isEmpty())
				labelArray.add(instructions.label);
			if (!USER.isEmpty()){
				labelArray.add(USER);
			}
			labelArray.add(String.valueOf(GROUP_ID));
			if (getTaskId() > 1){
				labelArray.add(String.valueOf(getTaskId()));
			}
			else {
				labelArray.add(Integer.toHexString(Float.floatToIntBits((float)Math.random())));
				//labelArray.add(Long.toHexString(Double.doubleToLongBits(Math.random())));
			}

			final String label = String.join("-", labelArray).replaceAll("[^\\w\\-\\.\\:@]", "_");
			//final String label = String.join("-", labelArray).replaceAll("[^\\w]", "_"); //

					//this.instructions.set(instructions);
			// this.instructions.makeLevel = defaultRemakeDepth;

			// Relative
			productDir = getProductDir(this.info.PRODUCT_ID);

			// timeStampDir = getTimestampDir(this.info.time);
			timeStampDir = this.info.getTimeStampDir();
			// this.info.TIMESTAMP
			relativeOutputDir = timeStampDir.resolve(this.productDir);

			//this.relativeOutputDirTmp = this.timeStampDir.resolve(this.productDir).resolve(String.format("tmp-%s-%d", ) + getTaskId());
			relativeOutputDirTmp = this.relativeOutputDir.resolve(label); //String.format("tmp-%s-%d", USER, getTaskId()));
			relativeOutputPath = relativeOutputDir.resolve(filename);

			//this.relativeLogPath    = relativeOutputDir.resolve(getFilePrefix() + filename + "." + getTaskId() + ".log");
			// Absolute
			this.outputDir = CACHE_ROOT.resolve(this.relativeOutputDir);
			this.outputDirTmp = CACHE_ROOT.resolve(this.relativeOutputDirTmp);
			this.outputPath = outputDir.resolve(filename);
			//this.outputPathTmp = outputDirTmp.resolve(getFilePrefix() + filename);
			this.outputPathTmp = outputDirTmp.resolve(filename);
			this.storagePath = STORAGE_ROOT.resolve(this.relativeOutputDir).resolve(filename);

			if (parentLog == null){
				parentLog = serverLog;
			}
			//parentLog.special("koe1");
			log = new HttpLog(parentLog.getName() + "[" + this.info.PRODUCT_ID + "]", parentLog.getVerbosity());
			//log.setFormat(parentLog.getFormat());
			// log.setFormat(LOG_FORMAT);
			// log.setDecoration(LOG_STYLE);
			log.set(LOG_TASKS);
			// log.setDecoration(parentLog.decoration);

			// Is this sometimes confusing?
			// Consider extension in uppercase: .LOG and  .HTML
			if (log.textOutput.getFormat() == TextOutput.Format.HTML)
				this.relativeLogPath = relativeOutputDir.resolve(filename + "." + label + ".log.html");
			else
				this.relativeLogPath = relativeOutputDir.resolve(filename + "." + label + ".log");

			Path logPath = CACHE_ROOT.resolve(relativeLogPath);
			try {
				FileUtils.ensureWritableFile(logPath, GROUP_ID, filePerms, dirPerms);
				log.setLogFile(logPath);
			} catch (IOException e) {
				System.err.println(String.format("Opening Log file failed: Log GID=%d, file=%s dir=%s",
						GROUP_ID, filePerms, dirPerms));
				System.err.println(String.format("Opening Log file failed: Error: %s", e));
				System.err.println(String.format("Opening Log file failed: File: %s", logPath));
				//log.setLogFile(null); ?
			}
			log.debug(String.format("Log format: %s (%s)",  this.log.getFormat(), log.decoration));
			log.debug(String.format("Label: %s %s",  label, labelArray));


			this.relativeSystemDir = this.timeStampDir.resolve("nutshell").resolve(this.productDir);
			Path systemPath = CACHE_ROOT.resolve(relativeSystemDir);
			try {
				FileUtils.ensureWritableDir(systemPath, GROUP_ID, dirPerms);
			} catch (IOException e) {
				log.error(String.format("Creating product aux dir failed: %s '%s'", systemPath , e));
			}

			String systemBaseName = this.info.TIMESTAMP + "_nutshell." + this.info.PRODUCT_ID + "_" + label; //getTaskId();
			this.relativeGraphPath = relativeSystemDir.resolve(systemBaseName + ".svg");

			//log.warn("Where am I?");
			//log.debug(String.format("Log format: %s (%s)",  this.log.getFormat(), log.decoration));
			log.info(String.format("Created Task: %s ", this.toString())); //
			//log.debug(String.format("Created TASK %s [%d] [%s] %s ", this.filename, this.getTaskId(), this.instructions, this.info.directives)); //  this.toString()
			this.result = null;
		}

		public Task(String productStr, Instructions instructions) throws ParseException {
			this(productStr, instructions, null);
		}

		protected void close() {
			// System.err.println("CLOSE task: " + toString());
			this.log.close();
		}

		/*
		@Override
		protected void finalize() throws Throwable {
			this.close();
		}
		 */
		@Override
		public String toString() {
			if (this.info.directives.isEmpty())
				return String.format("%s # %s", this.filename, instructions);
			else
				return String.format("%s?%s # %s", this.filename, info.directives, instructions);
		}

		public String getStatus() {
			int i = log.getStatus();
			return String.format("%s(%d)", Log.statusCodes.getOrDefault(i, Log.Status.UNDEFINED), i);
			// return (String.format("%s[%d] %s [%s] %s", this.getClass().getSimpleName(), this.getTaskId(), this.info, this.instructions, this.info.directives)); //  this.toString()
		}

		// Consider moving these to @ProductServerBase, with Log param.
		public Path move(Path src, Path dst) throws IOException {
			log.note(String.format("Move: from: %s ", src));
			log.note(String.format("        to: %s ", dst));
			if (dst.toFile().isDirectory())
				dst = dst.resolve(src.getFileName());
			//Files.setPosixFilePermissions(dst, filePerms);
			Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);

			try {
				if (!Files.isSymbolicLink(dst))
					FileUtils.ensureGroup(dst, GROUP_ID, filePerms);
			}
			catch (IOException e){
				// Could be logged?
			}
			//Files.setPosixFilePermissions(dst, filePerms);
			return dst;
			//return src.renameTo(dst);
		}

		public Path copy(Path src, Path dst) throws IOException {
			log.note(String.format("Copy: from: %s ", src));
			log.note(String.format("        to: %s ", dst));
			if (dst.toFile().isDirectory())
				dst = dst.resolve(src.getFileName());
			return Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
		}

		/** Creates a soft link pointing to a file.
		 *
		 * @param src - original, physical file
		 * @param dst - link to be created
		 * @param force - overwrite link or file
		 * @return successfully generated path
		 * @throws IOException if linking fails
		 */
		public Path link(Path src, Path dst, boolean force) throws IOException {
			log.note(String.format("Link: from: %s ", src));
			log.note(String.format("        to: %s ", dst));

			if (dst.toFile().isDirectory())
				dst = dst.resolve(src.getFileName());

			if (Files.exists(dst)) {

				if (Files.isSymbolicLink(dst)) {
					log.note(String.format("Link exists: %s ", dst));
				} else {
					log.note(String.format("File (not Link) exists: %s ", dst));
				}

				if (Files.isSameFile(src, dst)) {
					log.note("File and link are equal");
					if (!force) {
						return dst;
					}
				}

				// Force!
				// Destination differs, or explicit deletion is requested
				Files.delete(dst);
			}

			Path result = Files.createSymbolicLink(dst, src);
			try {
				FileUtils.ensureGroup(result, GROUP_ID, filePerms); // this may always fail in Unix...
			}
			catch (Exception e) {
				log.note(String.format("Failed setting GROUP_ID=%d %s ", GROUP_ID, filePerms));
			}

			return result;
			//return Files.createLink(src, dst);   //(src, dst, StandardCopyOption.REPLACE_EXISTING);
		}

		public boolean delete(Path dst) throws IOException {
			log.note(String.format("Deleting: %s ", dst));
			return deleteIfExists(dst);
			//return file.delete();
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
				Signal.handle(new Signal("INT"), this::handleInterrupt);
				execute();
			} catch (InterruptedException e) {
				//log.status
				log.note(e.toString());
				log.warn("Interrupted");
				log.log(HttpLog.HttpStatus.INTERNAL_SERVER_ERROR, e.toString()); // ? too strong?
				//e.printStackTrace(log.printStream);
			}

		}


		/**
		 * Method called upon SIGINT signal handler set in {@link #run()}
		 * Deletes files that were under construction.
		 *
		 * @param signal - unused
		 */
		private void handleInterrupt(Signal signal) {
			log.warn("Interrupted (by Ctrl+C?) : " + this.toString());
			// System.out.println("Interrupted by Ctrl+C: " + this.outputPath.getFileName());
			if (instructions.involves(MediaType.FILE)) {
				try {
					delete(this.outputPath);
					delete(this.outputPathTmp); // what about tmpdir?
				} catch (IOException e) {
					log.warn(e.getMessage());
					//e.printStackTrace();
				}
			}
		}

		/** For now, not critical. Future option: memory cached products.
		 *
		 */
		public Object result;

		/** Execute this task on a single product: delete, load, generate a product, for example.
		 *
		 *  This is a central function of this software, "MAIN".
		 *
		 * Processing is done inside the parent thread  – by default the main thread.
		 * To invoke this function as a separate thread, use #run().
		 **
		 * @throws InterruptedException // Gene
		 * @see #run()
		 */
		public void execute() throws InterruptedException {

			long startTime = java.lang.System.currentTimeMillis();

			Generator generator = null;

			log.log(HttpLog.HttpStatus.OK, String.format("Preparing %s", this));


			// Logical corrections
			// Note: STATUS involves Task/product wise and also Server level info.
			if (instructions.isEmpty()
					|| !instructions.copies.isEmpty()
					|| !instructions.links.isEmpty()
					|| (instructions.move != null)
					|| instructions.involves(PostProcessing.STORE|PostProcessing.LATEST|PostProcessing.SHORTCUT)) {
				instructions.ensureMakeLevel(Instructions.MakeLevel.MAKE);  // NEW
				log.debug(String.format("Instructions (updated): %s", instructions));
			}


			// Media must be defined in most of the operations (DELETE, EXISTS, MAKE, GENERATE) so define it here.
			//if (instructions.makeLevelAtLeast(Instructions.MakeLevel.EXISTS)){
			if (instructions.makeLevelAtLeast(Instructions.MakeLevel.EXISTS) || instructions.makeLevelEquals(Instructions.MakeLevel.DELETE)){
				if (! instructions.involves(MediaType.FILE | MediaType.MEMORY)) {
					// Note: media selection could be also done by Generator?
					log.log(HttpLog.HttpStatus.OK, "Product requested, media type undefined. Setting default: FILE");
					instructions.add(MediaType.FILE);
				}
			}
			// }

			log.log(HttpLog.HttpStatus.ACCEPTED, String.format("Handling %s", this));

			if (instructions.makeLevelEquals(Instructions.MakeLevel.PARSE)){
				log.log(HttpLog.HttpStatus.CONTINUE, String.format("Parsing requested only"));
				Map<String,Object> map = getParamEnv();
				String[] array = MapUtils.toArray(map);
				for (String s : array) {
					System.out.println(s); // NutLet: to Server log?
				}
			}

			// This is a potential path, not committing to a physical file yet.
			File fileFinal = outputPath.toFile();

			/// Main DELETE operation
			if (instructions.makeLevelEquals(Instructions.MakeLevel.DELETE) || instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)) {

				if (instructions.isSet(MediaType.FILE)) {
					// if (instructions.makeLevelEquals(Instructions.MakeLevel.DELETE)){
					;
					if (queryFile(fileFinal, TIMEOUT, log) >= 0){

						long ageSec = FileUtils.fileModificationAge(fileFinal);
						log.warn(String.format("Deleting...(Age %ss)", ageSec));

						if (ageSec < (2*TIMEOUT)){
							log.log(HttpLog.HttpStatus.SEE_OTHER, String.format("Deleting a freshly (re)generated file? (Age %ss)", ageSec));
							//log.log(HttpLog.HttpStatus.SEE_OTHER,"Deleting a freshly generated file?");
						}

						try {
							// Native log
							this.delete(outputPath);
						} catch (IOException e) {
							log.log(HttpLog.HttpStatus.CONFLICT, String.format("Failed in deleting file: %s, %s", outputPath, e.getMessage()));
							if (instructions.makeLevelEquals(Instructions.MakeLevel.DELETE)){
								return; // STATUS?
							}
						}
					}
					else {
						log.log(HttpLog.HttpStatus.CONTINUE, String.format("File does not exist: %s", outputPath));
						// result = new Boolean(true);
					}
				}
				// TODO: memory

			}


			//  Note: Java Generators do not need disk, unless FILE
			//  WRONG, ... MAKE will always require FILE
			/*
			if (instructions.isSet(Instructions.MEMORY)) {
				// Not implemented yet
				// this.result = new BufferedImage();
			}
			*/


			final boolean STORED_FILE_EXISTS = storagePath.toFile().exists();

			/// "MAIN"
			// Consider also semantics: CHECK > EXISTS also actively links stored file or waits for a new file to appear.
			if ((instructions.involves(MediaType.FILE)) && instructions.makeLevelAtLeast(Instructions.MakeLevel.MAKE)) {

				// if ((instructions.involves(MediaType.FILE)) && !instructions.makeLevelEquals(Instructions.MakeLevel.DELETE)) {
				log.debug(String.format("Stored file exists? %s : %s", STORED_FILE_EXISTS, storagePath));

				if (!fileFinal.exists() && STORED_FILE_EXISTS) {
					log.log(HttpLog.HttpStatus.OK, String.format("Trying to use (link) stored file: %s", this.storagePath));

					try {
						FileUtils.ensureWritableDir(outputDir, GROUP_ID, dirPerms);
						// ensureDir(cacheRoot, relativeOutputDir); //, dirPerms);
					} catch (IOException e) {
						log.warn(e.getMessage());
						log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Failed in creating dir (with permissions): %s", this.outputDir));
						//e.printStackTrace();
					}

					try {
						this.link(storagePath, outputPath, false);
					} catch (IOException e) {
						log.error(e.getMessage());
						log.log(HttpLog.HttpStatus.CONFLICT, String.format("Failed in linking: %s <- %s", this.outputPath, storagePath));
						//e.printStackTrace();
					}
				}
				else if (queryFile(fileFinal, TIMEOUT, log) >= 0) {
					result = outputPath;
					log.log(HttpLog.HttpStatus.OK, String.format("File exists: %s", this.outputPath));
				}
				else {
					log.log(HttpLog.HttpStatus.OK, String.format("File does not exist: %s", this.outputPath));
						instructions.ensureMakeLevel(Instructions.MakeLevel.GENERATE);
				}
			}

			// Retrieve Generator, if needed
			if (instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE) || instructions.involves(Instructions.INPUTLIST | Instructions.STATUS)) {

				log.log(HttpLog.HttpStatus.OK, String.format("Determining generator for : %s", this.info.PRODUCT_ID));
				try {
					generator = getGenerator(this.info.PRODUCT_ID);
					//log.log(HttpLog.HttpStatus.CREATED, String.format("Generator(%s): %s", this.info.PRODUCT_ID, generator));
					log.log(HttpLog.HttpStatus.CREATED, generator.toString());

					if (instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)){
						instructions.add(generator.getPrimaryMediaType());
						/*
						// if (instructions.involves(Instructions.GENERATE)) {
						// Consider this.addAction() -> log.debug()
						if (generator instanceof ExternalGenerator)
							instructions.add(MediaType.FILE); // PREPARE dir & empty file
						else
							instructions.add(MediaType.MEMORY); // Yes, internal should not save?

						 */
					}

				}
				catch (IndexedState e) {
					log.log(HttpLog.HttpStatus.CONFLICT, "Generator does not exist");
					log.log(e);
					// instructions.remove(Instructions.GENERATE);
					if (instructions.makeLevelAtLeast(Instructions.MakeLevel.MAKE)){
						instructions.setMakeLevel(Instructions.MakeLevel.EXISTS); //? External file copied in cache?
					}
					instructions.remove(Instructions.INPUTLIST);
					// instructions.remove(Instructions.STATUS);
				}

			}



			// Generate or at least list inputs
			//if (instructions.involves(Instructions.INPUTLIST | Instructions.GENERATE)) { //
			if (instructions.involves(Instructions.INPUTLIST) ||
					instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)) { //

				log.debug(String.format("Determining input list for: %s", this.info.PRODUCT_ID));

				inputs.clear(); // needed?

				try {
					inputs.putAll(generator.getInputList(this));
					for (Entry<String,String> s: this.inputs.entrySet()){
						log.debug(String.format("%s = '%s'", s.getKey(), s.getValue()));
					}
					//statistics.put(info.PRODUCT_ID, inputs.keySet());
					//System.err.println("## " + this.inputs);
				} catch (Exception e) {
					log.log(HttpLog.HttpStatus.CONFLICT, "Input list retrieval failed");
					log.log(e);

					log.warn("Removing GENERATE from instructions");
					if (instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)){
						instructions.setMakeLevel(Instructions.MakeLevel.MAKE);
					}
					// instructions.remove(Instructions.GENERATE);
				}

				if (!inputs.isEmpty())
					log.info(String.format("Collected (%d) input requests for: %s", this.inputs.size(), this.info.PRODUCT_ID));

			}

			if (instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)){

				log.reset(); // Forget old sins

				// Mark this task being processed (empty file)
				try {
					FileUtils.ensureWritableDir(outputDirTmp, GROUP_ID, dirPerms);
					log.info(String.format("Created tmp dir: %s", outputDirTmp));
					FileUtils.ensureWritableDir(outputDir, GROUP_ID, dirPerms);
					FileUtils.ensureWritableFile(outputPath, GROUP_ID, filePerms, dirPerms);
					log.debug(String.format("Created empty file: %s", outputPath));
				} catch (IOException e) {
					log.log(HttpLog.HttpStatus.CONFLICT, e.toString());
					log.log(HttpLog.HttpStatus.INTERNAL_SERVER_ERROR, String.format("Failed in creating:: %s", e.getMessage()));
					return;
				}

				log.debug("Ok, generate...");

				/// Retrieve inputs
				if (!inputs.isEmpty()) {

					final Instructions inputInstructions = new Instructions();

					if (instructions.makeLevelEquals(Instructions.MakeLevel.GENERATE_ALL)){
						inputInstructions.setMakeLevel(Instructions.MakeLevel.GENERATE_ALL);
					}
					else {
						inputInstructions.setMakeLevel(instructions.makeLevel - 1);
					}


					/*
					if (instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)) {
						inputInstructions.setMakeLevel(instructions.makeLevel - 1);
					} else {
						inputInstructions.setMakeLevel(Instructions.MakeLevel.NONE);
					}
					 */

					if (instructions.isSet(Instructions.INPUTLIST)) {
						inputInstructions.add(ActionType.INPUTLIST);
					}

					// Input generation uses parallel threads only if this product uses - ?
					if (instructions.isSet(Instructions.PARALLEL)) {
						inputInstructions.add(ActionType.PARALLEL);
					}


					//log.experimental(String.format("Input retrieval depth: %d",
					//		inputInstructions.makeLevel));

					log.special(String.format("Input instructions: %s", inputInstructions));

					// Ok - forwarding directives?
					// FIX: WHY repeated inputTasks <--> this.inputTasks
					Map<String, Task> inputTasks = prepareTasks(this.inputs, inputInstructions, info.directives, log);

					serverLog.debug("runTasks:" + inputTasks.keySet());
					runTasks(inputTasks, log);


					for (Entry<String, Task> entry : inputTasks.entrySet()) {
						String key = entry.getKey();
						Task inputTask = entry.getValue();

						this.inputTasks.put(key, inputTask);
						// FIX: check unneeded errors if only INPUTLIST requested
						if (inputTask.result != null) {
							log.ok(String.format("Retrieved: %s = %s [%s]",
									key, inputTask.result, inputTask.result.getClass().getSimpleName()));

							if (inputTask.log.indexedState.index >= 400) {
								log.warn(String.format("Errors for input of %s: %s", key, inputTask.log.indexedState.getMessage()));
							} else if (inputTask.log.indexedState.index >= 300) {
								log.note(String.format("Warnings for input of %s: %s", key, inputTask.log.indexedState.getMessage()));
							}
						} else {
							log.warn(inputTask.log.indexedState.getMessage());
							log.log(HttpLog.HttpStatus.PRECONDITION_FAILED, String.format("Retrieval failed: %s=%s", key, inputTask));
							log.reset(); // Forget that anyway...
						}
					}

					for (Task inputTask : inputTasks.values()) {
						inputTask.close();
					}

				}

				/// "MAIN"
				if (instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)){
				//if (instructions.isSet(ActionType.GENERATE)){
					log.note("Running Generator: " + info.PRODUCT_ID);

					File fileTmp = outputPathTmp.toFile();

					try {
						/*
						TODO: StringMapper -based dynamic Path
						TODO: Needs more envs, like TIMESTAMP_DIR
						log.experimental(relativeOutputPath.toString());
						Path test = Paths.get(cachePathSyntax.toString(getParamEnv())).normalize();
						log.experimental(test.toString());
						*/
						generator.generate(this);
					} catch (IndexedState e) {

						// NOTE: possibly the file has been generated, but with some less significant errors.

						log.log(e);

						try {
							log.warn("Error was: " + e.getMessage());
							this.delete(this.outputPathTmp);
							//this.delete(fileFinal);
						} catch (Exception e2) {
							log.error(e2.getLocalizedMessage());
						}
					}


					if (fileTmp.length() > 0) {

						/// Override
						log.setStatus(Log.Status.OK);
						log.debug(String.format("OK, generator produced tmp file: %s", this.outputPathTmp));
						serverLog.success(info.getFilename());

						// Let's take this slowly...
						try {
							this.move(this.outputPathTmp, this.outputPath);
							log.success(this.outputPath.toString());
							//this.copy(this.outputPathTmp, this.outputPath);
						} catch (IOException e) {
							log.warn(e.toString());
							//log.warn(String.format("filePerms: %s", filePerms));
							log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Failed in moving tmp file: %s", this.outputPathTmp));
							// log.error(String.format("Failed in moving tmp file: %s", this.outputPath));
						}

						if (!Files.isSymbolicLink(this.outputPath)) {
							try {
								// Todo: skip this if already ok...
								// Check: is needed? this.move contains copy_attributes
								Files.setPosixFilePermissions(this.outputPath, filePerms);
							} catch (IOException e) {
								log.warn(e.toString());
								log.warn(String.format("Failed in setting perms %s for file: %s", filePerms, this.outputPath));
								// log.warn(String.format("filePerms: %s", filePerms));
								// log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Failed in setting perms for file: %s", this.outputPath));
								// log.error(String.format("Failed in moving tmp file: %s", this.outputPath));
							}
						}


						// this.result = this.outputPath;
					}
					else {
						log.log(HttpLog.HttpStatus.CONFLICT, String.format("Generator failed in producing the file: %s", this.outputPath));
						// server Log.fail(info.getFilename());
						// log.error("Generator failed in producing tmp file: " + fileTmp.getName());
						if (instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)){
						    // if (instructions.isSet(Instructions.GENERATE)) {
							try {
								log.warn(String.format("Failed in generating: %s", info));
								if (outputPathTmp.toFile().exists())
									this.delete(this.outputPathTmp);
							} catch (Exception e) {
								/// TODO: is it a fatal error if a product defines its input wrong?
								log.error(e.getMessage()); // RETURN?
								log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Failed in deleting tmp file: %s", this.outputPath));
							}
						}
					}

				}



			}

			//if (instructions.isSet(ActionType.INPUTLIST) && !instructions.involves(ActionType.GENERATE)) {
			if (instructions.isSet(ActionType.INPUTLIST) &&
					instructions.makeLevelBelow(Instructions.MakeLevel.GENERATE)) {
				log.note("Input list: requested");
				// result = inputs;
			}


			if (instructions.isSet(MediaType.FILE)) {
				// TODO: save file (of JavaGenerator)
				// if (this.result instanceof Path)...

				if (this.instructions.makeLevelEquals(Instructions.MakeLevel.DELETE)) {
					if (fileFinal.exists())
						log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Deleting failed: %s (%d bytes)", fileFinal, fileFinal.length()));
					else {
						log.reset();
						log.log(HttpLog.HttpStatus.ACCEPTED, String.format("File does not exist: %s", fileFinal)); // Consider storing last messages in LOG? See HttpLog.indexedState
						//log.ok("Deleted."); // Consider storing last messages in LOG? See HttpLog.indexedState
						// System.err.println("DELE: " + log.indexedState.getMessage());
					}
				}
				else if (this.instructions.makeLevelEquals(Instructions.MakeLevel.EXISTS)) {
					if (!fileFinal.exists()){
						log.log(HttpLog.HttpStatus.CONFLICT, String.format("File does not exist: %s ", this.outputPath));
					}
					else if (fileFinal.length() == 0){
						log.log(HttpLog.HttpStatus.CONFLICT, String.format("File exists, but is empty: %s ", this.outputPath));
					}
					else {
						result = fileFinal;
						log.log(HttpLog.HttpStatus.OK, String.format("File exists: %s ", this.outputPath));
					}
				}
				else if (fileFinal.length() == 0) {

					log.log(HttpLog.HttpStatus.CONFLICT, String.format("Failed in generating: %s ", this.outputPath));
					try {
						this.delete(this.outputPath);
					} catch (IOException e) {
						//log.error(String.format("Failed in deleting: %s ", this.outputPath));
						log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Failed in deleting: %s ", this.outputPath));
						log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
					}

				} else {

					this.result = this.outputPath;
					log.ok(String.format("Exists: %s (%d bytes)", this.result, fileFinal.length()));

					if (this.instructions.isSet(PostProcessing.SHORTCUT)) {
						if (this.info.isDynamic()) {
							try {
								//Path dir = ensureDir(cacheRoot, productDir);
								Path dir = CACHE_ROOT.resolve(productDir);
								FileUtils.ensureWritableDir(dir, GROUP_ID, dirPerms);
								this.link(this.outputPath, dir, true);
							} catch (IOException e) {
								log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
							}
						} else
							log.debug("Static product (no TIMESTAMP), skipped shortcut");
					}

					if (this.instructions.isSet(PostProcessing.LATEST)) {
						if (this.info.isDynamic()) {
							try {
								//Path dir = ensureDir(cacheRoot, productDir);
								Path dir = CACHE_ROOT.resolve(productDir);
								FileUtils.ensureWritableDir(dir, GROUP_ID, dirPerms);
								this.link(this.outputPath, dir.resolve(this.info.getFilename("LATEST")), true);
								log.ok(String.format("Linked as LATEST in dir: %s", dir));
							} catch (IOException e) {
								log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
							}
						} else
							log.debug("Static product (no TIMESTAMP), skipped shortcut");
					}

					if (this.instructions.isSet(PostProcessing.STORE)) {
						// Todo: traverse storage paths.
						Path storageDir = STORAGE_ROOT.resolve(relativeOutputDir);
						Path storedFile = storageDir.resolve(this.info.getFilename());
						if (Files.exists(storedFile)) {
							log.experimental(String.format("Store: file exists already: %s", storedFile));
						} else {
							try {
								//ensureDir(storageRoot, relativeOutputDir);
								FileUtils.ensureWritableDir(storageDir, GROUP_ID, dirPerms);
								this.copy(this.outputPath, storedFile);
								log.experimental(String.format("Stored in: %s", storedFile));
							} catch (IOException e) {
								log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
								log.reset();
								log.fail(String.format("Store: %s", storedFile));
							}
						}
					}

					// Complementary tasks: copying
					//for (Path path : this.instructions.copies) {
					for (String path : this.instructions.copies) {
						Path p = Paths.get(path);
						try {
							if (p.startsWith(CACHE_ROOT)){ // XXX
								FileUtils.ensureWritablePath(path, GROUP_ID, dirPerms);
							}
							this.copy(this.outputPath, p); // Paths.get(path)
							log.ok(String.format("Copied: %s", p));
							// System.out.println("Copy "+path);
						} catch (IOException e) {
							log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Copying failed: %s", p));
							log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
							//log.error(String.format("Copying failed: %s", path));
						}
					}

					// Complementary tasks: linking
					for (String path : this.instructions.links) {
						Path p = Paths.get(path);
						try {
							if (p.startsWith(CACHE_ROOT)){
								FileUtils.ensureWritablePath(path, GROUP_ID, dirPerms);
							}
							this.link(this.outputPath, p, false); // Paths.get(path)
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
							if (p.startsWith(CACHE_ROOT)){
								FileUtils.ensureWritablePath(this.instructions.move, GROUP_ID, dirPerms);
							}
							this.move(this.outputPath, p);
							this.result = this.instructions.move;
							log.log(HttpLog.HttpStatus.OK, String.format("Moved: %s", p));
						} catch (IOException e) {
							log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Moving failed: %s", p));
							log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
						}
					}

					try {
						File dir = this.outputDirTmp.toFile();
						if (dir.exists()) {
							log.debug(String.format("Remove tmp dir: %s", this.outputDirTmp));
							// NEW! Empty the dir
							for (File file: dir.listFiles()){
								if (file.isFile())
									this.move(file.toPath(), this.outputDir);
								// Maybe dirs ok as well?
							}
							Files.delete(this.outputDirTmp);
						}
					} catch (IOException e) {
						log.warn(e.getMessage());
						log.log(HttpLog.HttpStatus.SEE_OTHER, String.format("Failed in removing tmp dir %s", this.outputDirTmp));
					}

				}
			} else {
				if (this.result != null) // Object?
					log.info("Result: " + this.result.toString());
				log.note(String.format("Task completed: instructions=%s, status=%s", this.instructions, this.getStatus()));
				// status page?

			}

			long duration = java.lang.System.currentTimeMillis() - startTime;

			log.info(String.format("Duration %s (Timeout: %d)", durationFormat.format(duration), TIMEOUT));

			if (instructions.isSet(Instructions.RUN)) {
				ShellExec.OutputReader reader = new ShellExec.OutputReader(log.getPrintStream());
				//ShellExec shellExec = new ShellExec(Paths.get("run.sh"), this.productDir);
				ShellExec.exec("./run.sh", null, PRODUCT_ROOT.resolve(productDir), reader);

			}

			//if (graph != null) {
			// serverGraph.importGraph(graph); importNode?

			//}

		}


		/**
		 * Declaration of environment variables for external (shell) generators.
		 *
		 * @return - variables defining a product, its inputs, and output dir and file.
		 */
		public Map<String, Object> getParamEnv() {

			// BASE
			Map<String, Object> env = new TreeMap<String, Object>();
			this.info.getParamEnv(env);

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
						break;
					case 0:
						break;
					default:
						log.log(HttpLog.HttpStatus.NOT_MODIFIED, String.format("Odd timestamp '%s' length (%d)", this.info.TIMESTAMP, this.info.TIMESTAMP.length()));
				}
			}


			// Consider keeping Objects, and calling .toString() only upon ExternalGenerator?
			env.put("OUTDIR", this.outputPathTmp.getParent().toString()); //cacheRoot.resolve(this.relativeOutputDir));
			env.put("OUTFILE", this.outputPathTmp.getFileName().toString());

			if (!PATH.isEmpty()) {
				env.put("PATH", PATH);
				log.special("PATH=" + PATH);
			}

			if (!this.inputTasks.isEmpty()) {
				env.put("INPUTKEYS", String.join(",", this.inputTasks.keySet().toArray(new String[0])));
				// FIX: String.join(",", retrievedInputs.keySet());
				// env.putAll(this.inputTasksNEW);
				for (Map.Entry<String,Task> entry: inputTasks.entrySet()){
					String key = entry.getKey();
					Task inputTask = entry.getValue();
					if (inputTask.outputPath != null){
						env.put(key,inputTask.outputPath);
					}

				}
			}

			env.putAll(this.info.directives);

			return env;
		}



		/** Write the graph describing this task into a file.
		 *
		 * @return – full path of the created file.
		 *
		 */
		public Path writeGraph() {
			Path graphFile = CACHE_ROOT.resolve(relativeGraphPath);
			log.special(String.format("Writing graph to file: %s", graphFile));
			Graph graph = this.getGraph(null);
			try {
				Path graphDir = graphFile.getParent();
				FileUtils.ensureWritableDir(graphDir, GROUP_ID, dirPerms);
				graph.dotToFile(graphFile.toString()); // svg
				graph.dotToFile(graphFile.toString()+".dot"); // debugging
				Files.setPosixFilePermissions(graphFile, filePerms);
			} catch (IOException | InterruptedException e) {
				log.warn(e.getMessage());
				graph.toStream(log.getPrintStream());
				log.fail(String.format("Failed in writing graph to file: %s", graphFile));
			}
			return graphFile;
		}


	}  // Task



	/** Creates Tasks from product definitions. Ensures directories for copying, linking, and moving.
	 *
	 * @param batch
	 * @param log
	 * @return
	 */
	public Map<String,Task> prepareTasks(Batch batch, HttpLog log) {
		return prepareTasks(batch.products, batch.instructions, batch.directives, log);
	}

	/** Creates Tasks from product definitions. Ensures directories for copying, linking, and moving.
	 *
	 * @param productRequests
	 * @param instructions – shared product request handling instructions
	 * @param directives – shared product generation auxiliary instructions.
	 * @param log – parent log, under which each Task creates a separate log.
	 * @return
	 */
	public Map<String,Task> prepareTasks(Map<String,String> productRequests, Instructions instructions, Map<String,String> directives, HttpLog log) {

		Map<String, Task> tasks = new HashMap<>();

		final int productCount = productRequests.size();

		if (productCount == 0) {
			return tasks;
		}

		log.info(String.format("Preparing %d tasks: %s", productCount, productRequests.keySet()));
		log.info(String.format("Instructions: %s, Directives: %s ",
				instructions, directives));
		/*
		log.info(String.format("Instructions: %s (depth: %d), Directives: %s ",
				instructions, instructions.regenerateDepth, directives));
		 */

		final boolean MULTIPLE = (productCount > 1);

		/// Check COPY & LINK targets: must be directories, if several tasks (several files produced)
		for (String path : instructions.copies) {
			Path p = Paths.get(path);
			if (p.startsWith(CACHE_ROOT)){ // XXX
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
			if (p.startsWith(CACHE_ROOT)){ // XXX
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

		if (instructions.move != null){
			Path p = Paths.get(instructions.move);
			if (p.startsWith(CACHE_ROOT)){ // XXX
				try {
					FileUtils.ensureWritablePath(instructions.move, GROUP_ID, dirPerms);
				} catch (IOException e) {
					log.warn(String.format("Problems ahead: %s", e.getMessage()));
					//throw new RuntimeException(e);
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

				//Task task = new Task(value, instructions.value, log);
				Task task = new Task(value, instructions, log);
				task.info.setDirectives(directives);
				/*
				task.instructions.addCopies(instructions.copies);
				task.instructions.addLinks(instructions.links);
				task.instructions.addMove(instructions.move); // Thread-safe?
				task.instructions.makeLevel = instructions.makeLevel;
				 */
				/*
				if (task.instructions.makeLevel > 0){
					task.instructions.add(ActionType.MAKE);
				}
				 */

				log.info(String.format("Prepared task: %s= %s", key, task));  //, task.instructions
				if ((directives != null) && !directives.isEmpty())
					log.info(String.format("Directives: %s = %s", key, directives));

				//log.info(task.toString());

				task.log.setVerbosity(log.getVerbosity());
				// task.log.decoration.set(Log.OutputFormat.COLOUR);
				if (task.log.logFile != null) {
					log.note(String.format("Log for '%s': %s", key, task.log.logFile));
				}

				tasks.put(key, task);

			}
			catch (ParseException e) {
				// A fatal error if a product defines its input wrong? Probably yes...
				log.warn(String.format("Could not parse product: %s(%s)", key, value));
				log.log(HttpLog.HttpStatus.NOT_ACCEPTABLE, e.getLocalizedMessage());
			}
			catch (Exception e) {
				// System.err.println(String.format("EROR2 here: %s = %s", key, value));
				log.warn(e.getMessage());
				log.error(String.format("Unexpected exception in creating product %s(%s)", key, value));
				log.log(HttpLog.HttpStatus.INTERNAL_SERVER_ERROR, e.getLocalizedMessage());
			}
			finally {
				//System.out.println("Final.." + key);
			}

		}
		return tasks;
	}

	/** Execute tasks, optionally in parallel threads.
	 *
	 * @param tasks
	 * @param log
	 * @return
	 */
	public Map<String,Task> runTasks(Map<String,Task> tasks, HttpLog log) {

		if (tasks.isEmpty()){
			//log.debug("No subtasks");
			return tasks;
		}

		log.note(String.format("Starting (%d) tasks, GROUP_ID=%d", tasks.size(), GROUP_ID));
		//log.debug(String.format("Starting (%d) tasks, GROUP_ID=%d", tasks.size(), GROUP_ID));

		/// Start as threads, if requested
		for (Entry<String,Task> entry : tasks.entrySet()){
			String key = entry.getKey();
			Task task = entry.getValue();
			// task.log.debug("Decoration: " + task.log.decoration.toString());
			if (task.instructions.isSet(ActionType.PARALLEL)) {
				try {
					log.info(String.format("Starting task[%d] '%s': %s as a thread", task.getTaskId(), key, task));
					// log.debug(String.format("Starting thread '%s': %s", key, task));
					task.start(); // Thread.start() -> (Thread)task.run()
				} catch (IllegalStateException e) {
					log.error("Already running? " + e.toString());
				}
			}
		}

		for (Entry<String,Task> entry : tasks.entrySet()){
			String key = entry.getKey();
			Task task = entry.getValue();
			try {
				if (task.instructions.involves(ActionType.PARALLEL)) {
					task.join();
				}
				else {
					log.info(String.format("Starting task '%s': %s (in main thread)", key, task));
					task.execute();
				}

				log.info(String.format("Finished task: %s(%s)[%d]", key, task.info.PRODUCT_ID, task.getTaskId()));

			} catch (InterruptedException e) {
				log.warn(String.format("Interrupted task: %s(%s)[%d]", key, task.info.PRODUCT_ID, task.getTaskId()));
				log.warn(String.format("Pending file? : %s", task.outputPathTmp));
			}

			IndexedState state = task.log.indexedState;

			// log.debug(String.format("%s status: %s", key, state.getMessage()));

			// FIX: check/skip unneeded errors if only INPUTLIST requested
			if (task.result != null) {
				if (state.index >= 400) { //?
					log.warn(String.format("Error for %s: %s", key, state.getMessage()));
				}
				else if (state.index >= 300) {
					log.note(String.format("Notes for %s: %s", key, state.getMessage()));
				}
			} else if (task.instructions.makeLevelAtLeast(Instructions.MakeLevel.EXISTS)){
				log.warn(state.getMessage());
				log.log(HttpLog.HttpStatus.PRECONDITION_FAILED, String.format("Task [%s] failed: %s", key, task));
				log.reset(); // Forget that anyway...
			}

		}

		return tasks;
	}


	/// Checks if a file exists in cache or storage, wait for completion if needed. Delete if outdated.
	/**
	 *
	 * return immediately if non-empty or nonexistent, else wait for an empty file to complete.
	 * @param file
	 * @param maxEmptySec maximum age of empty file in seconds
	 * @param log - stream to write success of the process
	 * @return - consider -1 not exists, 0 = exists cold, 1.. hot waited for seconds
	 * @throws InterruptedException
	 */
	public int queryFile(File file, int maxEmptySec, HttpLog log) throws InterruptedException {

		if (!file.exists()){
			log.log(HttpLog.HttpStatus.SEE_OTHER, String.format("File does not exist: %s", file));
			return -1;
		}

		int remainingSec = this.TIMEOUT;

		final long fileLength = file.length();

		if (fileLength > 0) {
			//log.note("File found");
			log.log(HttpLog.HttpStatus.OK, String.format("File found: %s (%d bytes)", file.getName(), fileLength));
			return 0;
		}
		else { // empty file
			// long ageSec = (java.lang.System.currentTimeMillis() - file.lastModified()) / 1000;
			long ageSec = FileUtils.fileModificationAge(file);
			if (ageSec > maxEmptySec){
				log.log(HttpLog.HttpStatus.SEE_OTHER, String.format("Time %d", java.lang.System.currentTimeMillis()));
				log.log(HttpLog.HttpStatus.SEE_OTHER, String.format("File %d", file.lastModified()));
				log.log(HttpLog.HttpStatus.NOT_MODIFIED, String.format("Outdated empty file, age=%d min, (max %d s)",(ageSec/60), maxEmptySec));
			}
			else {
				log.log(HttpLog.HttpStatus.SEE_OTHER, "Empty fresh file exists, waiting for it to complete...");
				for (int i = 1; i < 10; i++) {
					// int waitSec = i*i;      1, 4, 9, 16, 25, // 1 5 13 29 54
					int waitSec = 2<<(i-1);//  1, 2, 4, 16, 32, // 1 3 7  31 63
					//log.warn(String.format("Waiting for %d s...", waitSec));
					log.log(HttpLog.HttpStatus.CONTINUE,String.format("Waiting for %d s...", waitSec));
					TimeUnit.SECONDS.sleep(waitSec);
					if (file.length() > 0){
						log.log(HttpLog.HttpStatus.CREATED,"File appeared");
						return (this.TIMEOUT - remainingSec);
					}
					remainingSec = remainingSec - waitSec;
					if (remainingSec <= 0)
						break;
				}
				log.log(HttpLog.HttpStatus.NOT_MODIFIED, String.format("Timeout - file did not appear (grow) in %d s", maxEmptySec));
			}

			try {
				log.note("Deleting file");
				delete(file.toPath());
				//this.delete(this.outputPath);
			}
			catch (IOException e) {
				// TODO: redesign (check if delete needed at all)
				log.log(HttpLog.HttpStatus.CONFLICT, String.format("Failed in deleting file: %s, %s", file.toPath(), e.getMessage()));
			}

			return -1;
		}
	}



	/// System side setting.
	//  public String pythonScriptGenerator = "generate.py";

	/** A container, a "super task" for generating one or several products.
	 *
	 *  Limitation: instructions and directives are shared for all the products.
	 */
	static
	class Batch {

		public Map<String,String> products = new TreeMap<>();
		public Instructions instructions = new Instructions();
		public Map<String,String> directives = new TreeMap<>();

		/// Future fix: timeout should be adjustable for each batch
		//  private int timeOut = 0;

	}


	public void populate(ProgramRegistry registry) {

        registry.add(new ProgramUtils.Help(registry));
        registry.add(new ProgramUtils.LogLevel(serverLog));
        registry.add(new ProgramUtils.LogLevel.Debug(serverLog));
        registry.add(new ProgramUtils.LogLevel.Verbose(serverLog));

		/*
		registry.add(new Parameter.Simple<Float>("test",
				"Log file format.", (float) 123.456){

			@Override
			public void exec() {
				System.err.println(" VALUE="+value);
				System.err.println(Manip.toString(this));
				System.err.println(getClass().getGenericSuperclass().getTypeName());
				System.err.println(value.getClass());
			}
		});
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
			public void exec(){

				String[] killCmd = new String[]{"killall", value};
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

				int result = ShellExec.exec(killCmd, null, null, handler);

				serverLog.special("Return code: " + result);

			}
		});

		registry.add(new Parameter.Simple<TextOutput.Format>("log_format",
				"Log file format.", TextOutput.Format.VT100) {

			@Override
			public void exec() {
				serverLog.setFormat(value); // needed?
				LOG_SERVER = serverLog.getConf();
				//String.format("%s %s", serverLog.textOutput, serverLog.decoration);
				LOG_TASKS = LOG_SERVER;
				// server Log.debug(server Log.textOutput.toString());
				serverLog.special(String.format("New state for all logs: '%s'", LOG_SERVER));
				//serverLog.deprecated(String.format("Use generalized command --log '%s'", value));
				//server Log.debug(server Log.textOutput.toString());
			}
		});


        registry.add(new Parameter.Simple<String>("log_style",
				"Set decoration: " + serverLog.decoration.getAllFlags().keySet(),
				"") {

			@Override
			public void setParam(String key, Object value) throws NoSuchFieldException, IllegalAccessException {
				//super.setParam(key, value);
				serverLog.deprecated(String.format("Use generalized command --log '%s'", value));
				String s = value.toString();

				if (s.isEmpty()) {
					serverLog.decoration.clear();
					//LOG_STYLE.clear();
				} else {
					serverLog.decoration.set(s);
					// LOG_STYLE.set(value.toString());
				}
				LOG_SERVER = serverLog.getConf();
				LOG_TASKS = LOG_SERVER;
				//serverLog.decoration.set(LOG_STYLE);
			}


		});

		registry.add(new Parameter.Simple<String>("log",
				String.format("Set log properties: verbosity %s, format %s, decoration %s ",
						Log.statusCodes.values(),  // Arrays.toString(Log.Status.values()),
						Arrays.toString(TextOutput.Format.values()),
						Arrays.toString(TextOutput.Options.values())),
				"INFO,TEXT") {

			@Override
			public void exec() {
				serverLog.set(value);
				if (serverLog.decoration.isSet(TextOutput.Options.COLOUR) &&
						(serverLog.getFormat() == TextOutput.Format.TEXT)){
					serverLog.setFormat(TextOutput.Format.VT100);
				}
				LOG_SERVER = serverLog.getConf();
				LOG_TASKS = LOG_SERVER;
				serverLog.special(String.format("New state for all logs: '%s'", LOG_SERVER));

			}
		});

        registry.add(new Parameter<ProductServer>("gid",
                "Unix file group id (gid) to use.",
                this, "GROUP_ID"));


        // Consider: to NutLet and @ShellExec
        registry.add(new Parameter<ProductServer>("timeout",
                "Time in seconds to wait.",
                this, "TIMEOUT"){
			@Override
			public void exec() {
				ShellExec.TIMEOUT_SEC = TIMEOUT;
			}
		});

        registry.add(new Parameter<ProductServer>("counter",
                "Initial value of task counter (id).", this));

		registry.add(new Parameter.Single("parseOld",
				"Debugging (cmd line only): parse product.", "filename"){

			public String filename = "";

			@Override
			public void exec() {
				try {
					Task product = //server.
						new Task(filename, new Instructions(), serverLog);
					Map<String,Object> map = product.getParamEnv();
					String[] array = MapUtils.toArray(map);
					for (String s : array) {
						System.out.println(s);
					}
					//System.out.println(String.format("time : %d", product.info.time ));
					//System.out.println(String.format("time2: %d", product.info.time2));
				}
				catch (ParseException e) {
					serverLog.fail(e.getMessage());
				}
				catch (Exception e) {
					e.printStackTrace(System.err);
					serverLog.fail(e.getMessage());
				}
			}

		});


	}


	// Note: this turns program registry dynamic. It should be shared, static?
	static
	public void populate(Batch batch, ProgramRegistry registry){

		//registry.add(new InstructionParameter(batch.instructions));
		registry.add(batch.instructions.getProgramParameter());

		for (String instr: ClassUtils.getConstantKeys(Instructions.class)){ // consider instant .getClass()
			registry.add(batch.instructions.getProgramParameter(instr));
			//registry.add(instr.toLowerCase(), new InstructionParameter(batch.instructions, instr));
		}

		for (Instructions.MakeLevel level: Instructions.MakeLevel.values()){ // consider instant .getClass()
			registry.add(batch.instructions.getProgramParameter(level));
			//registry.add(level.name().toLowerCase(), new InstructionParameter(batch.instructions, level));
			//.toLowerCase()
		}



		registry.add(new Parameter.Simple<String>("product","Set product filename (repeatable)",
				""){
			@Override
			public void exec() {
				int i = batch.products.size();
				batch.products.put("product" + i, value);
			}
		});

		registry.add(new Parameter<Instructions>("label",
				"Marker for logs and tmps", //supporting %d=task-id [%s=user].",
				batch.instructions, "label"));

		/*
		registry.add(new Parameter<Instructions>("regenerate",
				"Deprecating, use --depth instead.",
				batch.instructions, "regenerateDepth"));
		 */

		// deprecating
		/*
		registry.add(new Parameter<Instructions>("depth",
				"Cache clearance depth (0=EXISTS, 0=MAKE, 1=GENERATE, N...: remake inputs)",
				batch.instructions, "makeLevel"));


		registry.add(new Parameter.Single("regenerate",
				"Deprecating, use --depth instead.",
				//"Cache clearance depth (0=MAKE, 1=GENERATE, N...: remake inputs)",
				"depth"){

			public int depth;

			@Override
			public void exec() {
				serverLog.deprecated("Use --depth instead.");
				batch.instructions.makeLevel = depth;
			}
		});
		 */

		registry.add(new Parameter.Simple<String>("directives",
				"Set application (env) variables separated with '|'",
				""){
			@Override
			public void exec() {
				//System.err.print(String.format("Type: %s %s", value.getClass(), value));
				MapUtils.setEntries(value,"\\|", "true", batch.directives);
			}
		});


	}

	/** Command-line interface for the NutShell product server
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



		registry.add(new Parameter("http_params","Debugging/testing: compose HTTP GET params."){

			@Override
			public void exec() {
				String[] p = batch.products.values().toArray(new String[0]);
				if (batch.products.isEmpty()){
					log.error("Product not defined yet, try: [--product] <FILE>"  + this.getName());
				}
				else {

					//if (batch.instructions.isEmpty())
					//	batch.instructions.setMakeLevel(Instructions.MakeLevel.Make);

					if (batch.products.size() > 1){
						log.warn("Several products defined, using last");
					}

					for (Map.Entry entry: batch.products.entrySet()) {
						try {
							Task task = server.new Task(entry.getValue().toString(), batch.instructions, log);
							System.out.println(String.format("instructions=%s&product=%s", batch.instructions, task.info.getFilename()));
						} catch (ParseException e) {
							log.warn(entry.getValue().toString());
							log.error(e.getMessage());
						}
					}

					// Do not actually process the request.
					batch.instructions.setMakeLevel(Instructions.MakeLevel.NONE);
				}
			}
		});



		registry.add(new Parameter.Simple<String>("path","Extend PATH variable for product generation",
				""){
			public void exec() {
				server.PATH = value;
				server.setup.put("cmdPath", server.PATH); // consider PARASITER...
			}
		});


		registry.add(new Parameter.Simple<String>("copy",
				"Copy generated product(s) to file (dir). Repeatable.",
				""){
			@Override
			public void exec() {
				batch.instructions.addCopy(value);
			}
		});

		registry.add(new Parameter.Simple<String>("link",
				"Copy generated product/products to file/dir. Repeatable.",
				""){
			@Override
			public void exec() {
				batch.instructions.addLink(value);
			}
		});

		registry.add(new Parameter.Simple<String>("move",
				"Move generated product/products to file/dir. Repeatable",
				""){
			@Override
			public void exec() {
				batch.instructions.addMove(value);
			}
		});

		/// Command line only
		registry.add(new Parameter("clear_cache", // .Simple<String>
				"Clear cache (and exit.)" // reconsider exit
				){
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


		registry.add(new Parameter.Simple<String>("catalog",
				"List products found under productRoot",""){

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



		//Field[] instructionFields = Instructions.class.getFields();
		if (args.length == 0){
			args = new String[]{"--help"};
		}

		// log.special("Instructions..." + batch.instructions);

		try {
			for (int i = 0; i < args.length; i++) {

				String arg = args[i];
				//log.info(String.format("ARGS[%d]: %s", i, arg));

				if (arg.charAt(0) == '-') {

					if (arg.equals("-h")){
						arg = "--help";
					}
					else if (arg.charAt(1) != '-') {
						throw new IllegalArgumentException("Short options (-x) not supported in this version (Java)");
					}

					String opt = arg.substring(2);

					if (registry.has(opt)){
						Program.Parameter param = registry.get(opt);
						//log.special(String.format("Handling: '%s' -> %s has params:%b", opt, param, param.hasParams()));
						if (param.hasParams()){
							if (i < (args.length-1)) {
								//log.special(String.format("%s has argument '%s'", param.getName(), args[i+1]));
								param.setParams(args[++i]);
							}
							else
								param.setParams(""); // Support "premature" end of cmd line, esp. with --help
						}
						param.exec();
						log.debug(String.format("Handled: %s [%s]", opt, param));
						//log.special(param.toString());
						continue;
					}
					else {
						log.error(String.format("Unknown argument: %s", arg));
						System.exit(-1);
					}

				}
				else {  // Argument does not start with "--"
					batch.products.put("product" + (batch.products.size()+1), arg);
				}

				log.info("Instructions: " + batch.instructions);
			}
		}
		catch (Exception e) {
			if (log.getVerbosity() >= Log.Status.NOTE.level){
				e.printStackTrace(log.getPrintStream());
			}
			log.error(String.format("Unhandled exception: %s", e));
			//e.printStackTrace(log.printStream);
			System.exit(1);
		}

		if (batch.instructions.isEmpty()){
			batch.instructions.setMakeLevel(Instructions.MakeLevel.MAKE);
		}

		int result = 0;

		//if (!batch.instructions.isEmpty())
		//	log.debug("Instructions: " + batch.instructions);

		/*
		if (batch.instructions.isSet(ActionType.CLEAR_CACHE)) {
			log.warn("Clearing cache");
			if (batch.instructions.value != ActionType.CLEAR_CACHE){
				batch.instructions.remove(ActionType.CLEAR_CACHE);
				log.warn(String.format("Discarding remaining instructions: %s", batch.instructions) );
			}

			try {
				server.clearCache(true);
			} catch (IOException e) {
				log.log(HttpLog.HttpStatus.CONFLICT, "Clearing cache failed");
				result = 4;
			}
			System.exit(result);
		}

		 */

		// Turhia/väärässä paikassa (tässä)
		if (!batch.instructions.copies.isEmpty())
			log.note(String.format("   COPY(%d):\t %s", batch.instructions.copies.size(), batch.instructions.copies));
		if (!batch.instructions.links.isEmpty())
			log.note(String.format("   LINK(%d):\t %s", batch.instructions.links.size(),  batch.instructions.links));
		if (batch.instructions.move != null)
			log.note(String.format("   MOVE: \t %s", batch.instructions.move));

		/*
		if (!batch.directives.isEmpty())
			log.debug("Directives: " + batch.directives);
		 */

		/// MAIN
		Map<String, Task> tasks = server.prepareTasks(batch, log);

		// Graph graph = new Graph("ProductServer");
		server.runTasks(tasks, log);

		if (log.getStatus() <= Log.Status.ERROR.level){
			log.warn(String.format("Errors: %d ", log.getStatus()));
			++result;
		}

		for (Entry<String,Task> entry: tasks.entrySet()) {

			// String key = entry.getKey();
			Task  task = entry.getValue();
			if (task.log.indexedState.index >= HttpLog.HttpStatus.BAD_REQUEST.getIndex()){
				log.warn(String.format("Generator Exception: %s", task.log.indexedState.getMessage()));
				//log.debug(task.log.indexedException);
				if (result < 20)
					++result;
			}
			else {
				log.info(String.format("Status:\t%s", task.log.indexedState.getMessage()));
			}

			task.getGraphNode(serverGraph, entry.getKey()+'$');

			if (task.outputPath.toFile().exists()) {
				log.ok(String.format("File exists:\t %s (%d bytes)", task.outputPath.toString(), task.outputPath.toFile().length()));
				//log.note(String.format("File exists:\t %s (%d bytes)", task.outputPath.toString(), task.outputPath.toFile().length()));
			}

			if (task.instructions.isSet(ActionType.INPUTLIST)){

				System.out.println();
				for (Map.Entry<String,String> ec: task.inputs.entrySet()) {
					System.out.println(String.format("%s:\t %s", ec.getKey(), ec.getValue()));
				}

			}

			if (task.instructions.isSet(OutputType.STATUS)){
				Path p = task.writeGraph();
				log.experimental(String.format("Wrote graph %s", p));
			}

			/*
			if (server.collectStatistics){
				Path dotFile = server.cacheRoot.resolve(task.relativeGraphPath);
				log.special(String.format("writing %s", dotFile));
				server.graph.dotToFile(dotFile.toString(), "");
			}
			 */

			task.close();
		}

		if (batch.instructions.isSet(OutputType.STATUS)){
			//
			System.out.println();
			for (Entry<String,Object> entry: server.setup.entrySet()) {
				System.out.printf("%s = %s %n", entry.getKey(), entry.getValue());
			}

		}

		// System.err.println("Eksit");
		log.debug("Exiting..");
		log.close();
		System.exit(result);

	}



}


