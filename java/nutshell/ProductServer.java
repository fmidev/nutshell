package nutshell;

import sun.misc.Signal;

//import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.Files.*;

//import javax.servlet.http.HttpServletResponse;

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
 *  @author Markus Peura fmi.fi Jan 26, 2011
 */
public class ProductServer { //extends Cache {

	final public HttpLog serverLog; // = new HttpLog(getClass().getSimpleName());
	static final DateFormat logFilenameTimeFormat = new SimpleDateFormat("yyyy-MM-dd");

	ProductServer(){
		serverLog = new HttpLog(getClass().getSimpleName());
	}

	/**
	 *
	 * @param pathFormat - absolute path of a filename, optionally containing '%s' expanded as timestamp.
	 * @return
	 */
	public Path setLogFile(String pathFormat) {
		if (pathFormat == null)
			pathFormat = "/tmp/nutshell-%s.log";
		try {
			Path p = Paths.get(String.format(pathFormat,
					logFilenameTimeFormat.format(System.currentTimeMillis())));
			//ensureFile(p.getParent(), p.getFileName());
			serverLog.setLogFile(p);
			serverLog.debug(setup.toString());
			try {
				Files.setPosixFilePermissions(p, filePerms);
			}
			catch (IOException e){
				serverLog.error(String.format("Could not set permissions: %s", filePerms));
			}
			return p;
		} catch (Exception e) {
			serverLog.setLogFile(null);
			return null;
		}

	}

	public final Map<String,Object> setup = new HashMap<>();

	/// Experimental log of products and their inputs(s)
	public boolean collectStatistics = false;
	public Graph graph = new Graph("ProductServer");
	public final Map<String,Map<String,String>> statistics = new HashMap<>();

	/// Experimental: Change MAKE to GENERATE if positive, decrement for each input
	// TODO: consider general query depth (for inputs etc)
	public int defaultRemakeDepth = 0;

	// TODO: add to config, set in constructor
	public Set<PosixFilePermission> dirPerms  = PosixFilePermissions.fromString("rwxrwxr-x");
	public Set<PosixFilePermission> filePerms = PosixFilePermissions.fromString("rw-rw-r--");
	public int fileGroupID = 100;
	//public GroupPrincipal fileGroupID;
			//Files.readAttributes(originalFile.toPath(), PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS).group();

	/// System side settings.
	public Path confFile    = Paths.get(".", "nutshell.cnf"); //Paths.get("./nutshell.cnf");
	public Path cacheRoot   = Paths.get(".");
	//public Path storageRoot   = Paths.get(".");
	public Path productRoot = Paths.get(".");
	//protected Path storageRoot = Paths.get(".");
	protected Path storageRoot = Paths.get(".");
	// Consider
	final protected List<StringMapper> storagePaths = new LinkedList<>();

	static final public Path cachePrefix = Paths.get("cache");

	/// System side setting.// TODO: conf
	public String inputCmd = "./input.sh";  // NOTE: executed in CWD
	
	/// System side setting. // TODO: conf
	public String generatorCmd = "./generate.sh";  // NOTE: executed in CWD

	//final DateFormat timeStampFormat    = new SimpleDateFormat("yyyyMMddHHmm");
	final DateFormat timeStampDirFormat = 
			new SimpleDateFormat("yyyy"+File.separatorChar+"MM"+File.separatorChar+"dd");

	//protected final List<Path> configFiles = new LinkedList<>();


	static public int counter = 0;

	static public int getProcessId(){
		return  ++counter;
	};

	/** Unix PATH variable extension, eg. "/var/local/bin:/media/mnt/bin"
	 *
	 */
	protected String cmdPath = System.getenv("PATH");

	protected void readConfig(){
		readConfig(confFile);
	}

	protected void readConfig(String path) {
		readConfig(Paths.get(path));
	}

	protected void readConfig(Path path){

		try {
			if (path != null) {
				serverLog.debug("Reading setup: " + path.toString());
				MapUtils.read(path.toFile(), setup);
			}
			this.confFile = path; //Paths.get(path);
			setup.put("confFile", path);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			setup.put("confFileError", e.getLocalizedMessage());
		}

		//log.debug(setup.toString());

		this.productRoot = Paths.get(setup.getOrDefault("PRODUCT_ROOT", ".").toString());
		this.cacheRoot   = Paths.get(setup.getOrDefault("CACHE_ROOT",   ".").toString());
		this.storageRoot = Paths.get(setup.getOrDefault("STORAGE_ROOT",   ".").toString());

		this.dirPerms = PosixFilePermissions.fromString(setup.getOrDefault("DIR_PERMS","rwxrwxr-x").toString());
		this.filePerms = PosixFilePermissions.fromString(setup.getOrDefault("FILE_PERMS","rwxrwxr--").toString());
		setup.put("dirPerms", dirPerms);
		setup.put("filePerms", filePerms);

		// this.fileGroupID = setup.getOrDefault("FILE_GROUP",  ".").toString();
		try {
			// this.fileGroupID = Files.readAttributes(cacheRoot, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS).group();
			fileGroupID = Integer.parseInt(getAttribute(cacheRoot, "unix:gid").toString());
		} catch (IOException e) {
			serverLog.error(String.format("Could not read group of cache dir: %s", cacheRoot));
		}
		setup.put("fileGroupID", fileGroupID);

		Object logPathFormat = setup.get("LOGFILE");
		if (logPathFormat != null) {
			Path p = setLogFile(logPathFormat.toString());
			//System.err.println(String.format("Log file: ", p);
		}
		//logPathFormat = "./nutshell-" + System.getenv("USER")+"-%s.log";
		// Path p = setLogFile(logPathFormat.toString());
		//
		if (setup.containsKey("PATH_EXT"))
			this.cmdPath += ":" + setup.get("PATH_EXT").toString();
		setup.put("cmdPath", this.cmdPath);
		// this.generatorScriptName = setup.getOrDefault("CAC",   ".").toString();
		// this.inputScriptName     = setup.getOrDefault("PRO", ".").toString();
	}

	@Override
	public String toString() {
		return MapUtils.getMap(this).toString() + "\n" + setup.toString();
	}

	/** Genarate dot graph
	 *
	 *  Can be converted with:  dot file.dot -Tpng -o dot.png
	 *
	 *  TODO: Generalize, with DotWrapper.getFields() to distinguish variations of the same product(ID)
	 *  | my.product.abc [RAVAKE,PNG] |
	 *  | CONF   | RAVAKE             |
	 *  | FORMAT | PNG                |
	 *
	 * @param stream – output stream (file).
	 *
	 */
	protected void dumpStatisticsDot(PrintStream stream){
		//PrintStream stream = System.out;

		stream.println("digraph P");
		stream.println('{');
		// nodesep=.05;
		// stream.println("  rankdir=LR");
		stream.println("  node [shape=record]"); // ,width=.1,height=.1
		for (Map.Entry<String,Map<String,String>> entry: statistics.entrySet()) {
			String source = entry.getKey();
			String srcKey = source.replace('.','_');
			Map<String,String> targets = entry.getValue();
			//stream.println(String.format(" %s [label=\"{%s | {Key|value}}\", color=\"blue\"];", srcKey, source));
			stream.println(String.format(" %s [label=\"%s\", style=filled, color=\"lightblue\"];", srcKey, source));
			for (Entry<String,String> dst: targets.entrySet()) {

				String dstID = dst.getValue();
				String dstKey = dstID.replace('.','_');
				//String dst = inputEntry.getValue();
				//stream.println(String.format(" %s [label=\"%s\", style=filled, color=\"green\"];", dstKey, dstID));
				//String dstKey = dst.replace('.','_');
				// stream.println(String.format("  %s -> %s [label=\"%s\"];", srcKey, dstKey, dst.getKey()));
				stream.println(String.format("  %s -> %s [label=\"%s\"];", dstKey, srcKey, dst.getKey()));
			}
		}
		stream.println('}');
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


	//public Path ensureDir(Path root, Path relativePath, Set<PosixFilePermission> perms) throws IOException {
	public Path ensureDir(Path root, Path relativePath) throws IOException {

		if (relativePath==null)
			return root;

		if (relativePath.getNameCount() == 0)
			return root;

		Path path = root.resolve(relativePath);
		//log.warn(String.format("Checking: %s/./%s",  root, relativePath));

		if (!exists(path)) {
			//Files.createDirectories(path, PosixFilePermissions.asFileAttribute(dirPerms));
			ensureDir(root, relativePath.getParent());
			serverLog.debug("creating dir: " + path);
			Files.createDirectory(path); //, PosixFilePermissions.asFileAttribute(dirPerms));
			Files.setPosixFilePermissions(path, dirPerms);

			//Files.setOwner(path, fileGroupID);
			try {
				Files.setAttribute(path, "unix:gid", fileGroupID);
			}
			catch (IOException e){
				serverLog.warn(e.toString());
				serverLog.warn(String.format("Could not se unix:gid '%d'",  fileGroupID) );
			}

		}

		return path;
	}

	//public Path ensureFile(Path root, Path relativePath, Set<PosixFilePermission> dirPerms, Set<PosixFilePermission> filePerms) throws IOException {
	public Path ensureFile(Path root, Path relativePath) throws IOException {

		Path path = root.resolve(relativePath);

		if (!exists(path)) {
			ensureDir(root, relativePath.getParent());
			serverLog.debug("creating file: " + path);
			Files.createFile(path, PosixFilePermissions.asFileAttribute(filePerms));
			//Files.createFile(path); //, PosixFilePermissions.asFileAttribute(filePerms));
			//Files.setPosixFilePermissions(path, filePerms);
		}

		try {
			Files.setAttribute(path, "unix:gid", fileGroupID);
		}
		catch (IOException e){
			serverLog.warn(e.toString());
			serverLog.warn(String.format("could not se unix GID: ",  fileGroupID) );
		}

		return path;

	}


	/**
	 *   A "tray" containing both the product query info and the resulting object if successfully queried.
	 *   Task does not know about the generator. Notice GENERATE and getParamEnv.
	 *
	 */
	public class Task extends Thread {
		
		final HttpLog log;
		final ProductInfo info;

		final public int id;
		public int getTaskId(){
			return id;
		}

		/// Checked "normalized" filename, with ordered parameters.
		final public String filename;

		final public Instructions instructions = new Instructions();

		public int remakeDepth = 0;
		//public boolean parallel = true;

		public Path timeStampDir;
		public Path productDir;

		public Path relativeOutputDir;
		public Path relativeOutputDirTmp;
		public Path relativeOutputPath;

		public Path relativeLogPath;

		public Path outputDir;
		public Path outputDirTmp;
		public Path outputPath;
		public Path outputPathTmp;
		public Path storagePath;

		//public final Map<String,String> directives = new HashMap<>();
		public final Map<String,String> inputs = new HashMap<>();
		public final Map<String,Object> retrievedInputs = new HashMap<>();

		/** Product generation task defining a product instance and operations on it.
		 *
		 *  In this version, directives can be set but only through '?'
		 *
		 * @param productStr
		 * @param instructions - definition how a product is retrieved and handled thereafter - @see #Actions
		 * @param parentLog - log of the parent task (or main process, a product server)
		 * @throws ParseException - if parsing productStr fails
		 */
		public Task(String productStr, int instructions, HttpLog parentLog) throws ParseException {

			// final String[] productDef = [productInfo, directives]
			// in LOG // this.creationTime = System.currentTimeMillis();
			this.id = getProcessId();

			this.info = new ProductInfo(productStr);
			this.log = new HttpLog("["+this.info.PRODUCT_ID+"]", parentLog);

			this.filename = this.info.getFilename();
			this.instructions.set(instructions);

			this.remakeDepth = defaultRemakeDepth;


			// Relative
			this.productDir   = getProductDir(this.info.PRODUCT_ID);
			this.timeStampDir = getTimestampDir(this.info.time);
			this.relativeOutputDir    = this.timeStampDir.resolve(this.productDir);
			this.relativeOutputDirTmp = this.timeStampDir.resolve(this.productDir).resolve("tmp" + getTaskId());
			this.relativeOutputPath = relativeOutputDir.resolve(filename);

			//this.relativeLogPath    = relativeOutputDir.resolve(getFilePrefix() + filename + "." + getTaskId() + ".log");
			this.relativeLogPath    = relativeOutputDir.resolve(filename + "." + getTaskId() + ".log");

			// Absolute
			this.outputDir     = cacheRoot.resolve(this.relativeOutputDir);
			this.outputDirTmp  = cacheRoot.resolve(this.relativeOutputDirTmp);
			this.outputPath    = outputDir.resolve(filename);
			//this.outputPathTmp = outputDirTmp.resolve(getFilePrefix() + filename);
			this.outputPathTmp = outputDirTmp.resolve(filename);
			this.storagePath   = storageRoot.resolve(this.relativeOutputDir).resolve(filename);


			try {
				ensureFile(cacheRoot, this.relativeLogPath);
				log.setLogFile(cacheRoot.resolve(this.relativeLogPath));
			} catch (IOException e) {
				System.err.println(String.format("Opening Log file (%s) failed: %s", this.relativeLogPath, e));
				//log.setLogFile(null);
			}

			//log.warn("Where am I?");
			log.debug(String.format("Created TASK %s [%d] [%s] %s ", this.filename, this.getTaskId(), this.instructions, this.info.directives)); //  this.toString()
			this.result = null;
		}


		public String getStatus(){
			return (String.format("%s[%d] %s [%s] {%s}", this.getClass().getSimpleName(), this.getTaskId(), this.info, this.instructions, this.info.directives)); //  this.toString()
		}


		//public boolean move(File src, File dst){
		public Path move(Path src, Path dst) throws IOException {
			log.note(String.format("Move: from: %s ", src));
			log.note(String.format("        to: %s ", dst));
			if (dst.toFile().isDirectory())
				dst = dst.resolve(src.getFileName());
			//Files.setPosixFilePermissions(dst, filePerms);
			Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
			return dst;
			//return src.renameTo(dst);
		}

		public Path copy(Path src, Path dst) throws IOException {
			log.note(String.format("Copy: from: %s ", src));
			log.note(String.format("        to: %s ", dst));
			if (dst.toFile().isDirectory())
				dst = dst.resolve(src.getFileName());
			return Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
		}

		public Path link(Path src, Path dst, boolean force) throws IOException {
			log.note(String.format("Link: from: %s ", src));
			log.note(String.format("        to: %s ", dst));

			if (dst.toFile().isDirectory())
				dst = dst.resolve(src.getFileName());

			if (Files.exists(dst)){

				if (Files.isSymbolicLink(dst)){
					log.note(String.format("Link exists: %s ", dst));
				}
				else {
					log.note(String.format("File (not Link) exists: %s ", dst));
				}

				if (Files.isSameFile(src, dst)){
					log.note("File and link are equal");
					if (!force){
						return dst;
					}
				}

				// Destination differs, or explicit deletion is requested
				Files.delete(dst);
			}

			return createSymbolicLink(dst, src);
			//return Files.createLink(src, dst);   //(src, dst, StandardCopyOption.REPLACE_EXISTING);
		}

		public boolean delete(Path dst) throws IOException {
			log.note(String.format("Deleting: %s ", dst));
			return deleteIfExists(dst);
			//return file.delete();
		}



		/** Runs a thread generating and/or otherwise handling a product
		 *
		 * @see #execute()
		 */
		@Override
		public void run(){

			/*
			if (!parallel){
				log.error("Ouch this was not meant to be parallel!");
			}
			 */

			try {
				Signal.handle(new Signal("INT"), this::handleInterrupt);
				execute();
			}
			catch (InterruptedException e) {
				//log.status
				log.note(e.toString());
				log.warn("Interrupted");
				//e.printStackTrace(log.printStream);
			}
			/*
			catch (IndexedException e) {
				log.warn("NutShell indexed exception --->");
				//log.error(e.getMessage());
				log.log(e);
				log.warn("NutShell indexed exception <---");
			}
			 */
		}



			/** Method called upon SIGINT signal handler set in {@link #run()}
             *  Deletes files that were under construction.
             *
             * @param signal - unused
             */
		private void handleInterrupt(Signal signal){
			log.warn("Interrupted (by Ctrl+C?) : " + this.toString());
			// System.out.println("Interrupted by Ctrl+C: " + this.outputPath.getFileName());
			if (instructions.involves(ResultType.FILE)) {
				try {
					delete(this.outputPath);
					delete(this.outputPathTmp); // what about tmpdir?
				} catch (IOException e) {
					log.warn(e.getMessage());
					//e.printStackTrace();
				}
			}
		}


		/** Execute this task: delete, load, generate a product, for example.
		 *
		 *  Processing is done inside the parent thread  – by default the main thread.
		 *  To invoke this function as a separate thread, use #run().
		 *
		 * @see #run()
		 *
		 * @return
		 * @throws InterruptedException  // Gene
		 */
		public void execute() throws InterruptedException {

			Generator generator = null;

			//log.log(HttpLog.HttpStatus.ACCEPTED, String.format("Starting %s", this.info.PRODUCT_ID));
			log.log(HttpLog.HttpStatus.ACCEPTED, String.format("Preparing %s", this));

			// Logical corrections

			if (! instructions.copies.isEmpty())
				instructions.add(ActionType.MAKE | ResultType.FILE);

			if (! instructions.links.isEmpty())
				instructions.add(ActionType.MAKE | ResultType.FILE);

			if (instructions.move != null)
				instructions.add(ActionType.MAKE | ResultType.FILE);

			// Rest default result type
			// if (this.instructions.involves(Actions.MAKE | Actions.DELETE)) { }
			if (!instructions.involves(ResultType.FILE | ResultType.MEMORY)) {
				// This "type selection" could be also done with Generator?
				log.log(HttpLog.HttpStatus.OK, "Setting default result type: FILE");
				instructions.add(ResultType.FILE);
			}

			if (instructions.involves(ActionType.MAKE) && (remakeDepth > 0)){
				log.experimental(String.format("Cache clearance %s > 0, ensuring GENERATE", remakeDepth));
				instructions.add(ActionType.GENERATE);
			}


			log.log(HttpLog.HttpStatus.ACCEPTED, String.format("Starting %s", this));
			log.log(String.format("Starting: %s ", getStatus())); //  this.toString()


			/// Main DELETE operation
			if (instructions.involves(ActionType.DELETE)){

				if (instructions.isSet(ResultType.FILE) ) {
					try {
						this.delete(outputPath);
					} catch (IOException e) {
						log.log(HttpLog.HttpStatus.CONFLICT, String.format("Failed in deleting file: %s, %s", outputPath, e.getMessage()));
						//return;
					}
				}

				if (instructions.involves(Instructions.GENERATE | Instructions.EXISTS)){
					//log.log(HttpLog.HttpStatus.MULTIPLE_CHOICES, String.format("Mutually contradicting instructions: %s ", this.instructions));
					log.experimental(String.format("Mutually contradicting instructions: %s ", instructions));
					// TODO: fix, still doesn't work
					instructions.remove(ActionType.DELETE);
					instructions.add(Instructions.GENERATE); // | Instructions.INPUTLIST);
					log.experimental(String.format("Adjusted instructions: %s ", instructions));
				}

			}


			//  Note: Java Generators do not need disk, unless FILE
			//  WRONG, ... MAKE will always require FILE

			if (instructions.isSet(Instructions.MEMORY)) {
				// Not implemented yet
				// this.result = new BufferedImage();
			}

			// This is a potential path, not committing to a physical file yet.
			File fileFinal = outputPath.toFile();

			/// "MAIN"
			if (instructions.involves(Instructions.EXISTS | ResultType.FILE) && ! instructions.isSet(ActionType.DELETE)) {

				log.debug(String.format("Checking storage path: %s", storagePath));

				if (storagePath.toFile().exists() && ! fileFinal.exists()){
					log.log(HttpLog.HttpStatus.OK, String.format("Stored file exists: %s", this.storagePath));

					try {
						ensureDir(cacheRoot, relativeOutputDir); //, dirPerms);
					}
					catch (IOException e) {
						log.warn(e.getMessage());
						log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Failed in creating dir (with permissions): %s", this.outputDir));
						//e.printStackTrace();
					}

					try {
						this.link(storagePath, outputPath, false);
					}
					catch (IOException e) {
						log.error(e.getMessage());
						log.log(HttpLog.HttpStatus.CONFLICT, String.format("Failed in linking: %s <- %s", this.outputPath, storagePath));
						//e.printStackTrace();
					}
				}
				// Don't wait (so the other process will "notice" ie fail)
				else if (queryFile(fileFinal, 90, log)) {
					// Order? Does it delete immediately?
					result = outputPath;
					log.log(HttpLog.HttpStatus.OK, String.format("File exists: %s", this.outputPath));
				}
				else { // if (this.instructions.isSet(Actions.EXIST)){
					//log.warn(String.format("File does not exist: %s", this.outputPath));
					// TODO: ERROR is a contradiction, unless file strictly required?
					if (this.instructions.isSet(ActionType.MAKE)) {
						this.instructions.add(Instructions.GENERATE);
						log.log(HttpLog.HttpStatus.OK, String.format("File does not exist: %s", this.outputPath));
					} else {
						log.log(HttpLog.HttpStatus.NOT_FOUND, String.format("File does not exist: %s", this.outputPath));
					}
				}

			}


			// Retrieve Geneator, if needed
			if (instructions.involves(Instructions.GENERATE | Instructions.INPUTLIST | Instructions.DEBUG)){

				log.log(HttpLog.HttpStatus.OK, String.format("Determining generator for : %s", this.info.PRODUCT_ID));
				try {
					generator = getGenerator(this.info.PRODUCT_ID);
					//log.log(HttpLog.HttpStatus.CREATED, String.format("Generator(%s): %s", this.info.PRODUCT_ID, generator));
					log.log(HttpLog.HttpStatus.CREATED, generator.toString());

					if (instructions.involves(Instructions.GENERATE )){
						// Consider this.addAction() -> log.debug()
						if (generator instanceof ExternalGenerator)
							instructions.add(ResultType.FILE); // PREPARE dir & empty file
						else
							instructions.add(ResultType.MEMORY);
					}

				}
				catch (IndexedException e){
					log.log(HttpLog.HttpStatus.CONFLICT, "Generator does not exist");
					log.log(e);
					instructions.remove(Instructions.GENERATE);
					instructions.remove(Instructions.INPUTLIST);
					instructions.remove(Instructions.DEBUG);
				}

			}

			if (instructions.isSet(ActionType.DELETE) && ! instructions.involves(ActionType.MAKE)) {

				if (fileFinal.exists()) {
					log.log(HttpLog.HttpStatus.CONFLICT, String.format("Failed in deleting: %s ", this.outputPath));
				}
				else {
					log.log(HttpLog.HttpStatus.NO_CONTENT, String.format("Deleted: %s ", this.outputPath));
				}

				if (! (instructions.involves(Instructions.GENERATE | Instructions.EXISTS))){
					return;
				}
			}


			// Generate or at least list inputs
			if (this.instructions.involves(Instructions.INPUTLIST  | Instructions.GENERATE)) { //

				log.debug(String.format("Determining input list for: %s", this.info.PRODUCT_ID));

				inputs.clear(); // needed?

				try {
					inputs.putAll(generator.getInputList(this));
					log.debug(this.inputs.toString());
					//statistics.put(info.PRODUCT_ID, inputs.keySet());
					//System.err.println("## " + this.inputs);
				}
				catch (Exception e) {
					log.log(HttpLog.HttpStatus.CONFLICT, "Input list retrieval failed");
					log.log(e);

					log.warn("Removing GENERATE from instructions");
					instructions.remove(Instructions.GENERATE);
				}

				if (!inputs.isEmpty())
					log.info(String.format("Collected (%d) input requests for: %s", this.inputs.size(), this.info.PRODUCT_ID));

			}



			if (instructions.isSet(Instructions.GENERATE)) {

				log.reset(); // Forget old sins

				log.info("Generate!");
				// log.info("No generation request, returning.");
				// return;

				// Mark this task being processed (empty file)
				// if (this.instructions.isSet(ResultType.FILE) && !fileFinal.exists()){
				try {
					ensureDir(cacheRoot, relativeOutputDirTmp); //, dirPerms);
					ensureDir(cacheRoot, relativeOutputDir); //,    dirPerms);
					ensureFile(cacheRoot, relativeOutputPath); //, dirPerms, filePerms); // this could be enough?
					//Path genLogPath =  ensureWritableFile(cacheRoot, relativeOutputDirTmp.resolve(filename+".GEN.log"));
				}
				catch (IOException e) {
					log.log(HttpLog.HttpStatus.INTERNAL_SERVER_ERROR, String.format("Failed in creating: %s", e.getMessage()));
					return;
				}

				//	log.debug(String.format("No need to create: %s/./%s",  cacheRoot, this.relativeOutputDirTmp));
				// Assume Generator uses input similar to output (File or Object)
				//final int inputActions = this.instructions.value & (ResultType.MEMORY | ResultType.FILE);
				//final Instructions inputInstructions = new Instructions(this.instructions.value & (ResultType.MEMORY | ResultType.FILE));

				// Input generation uses parallel threads only if this product uses.
				final Instructions inputInstructions = new Instructions(this.instructions.value & (ResultType.MEMORY | ResultType.FILE | ActionType.PARALLEL));
				//inputInstructions.add(Instructions.GENERATE);
				inputInstructions.add(ActionType.MAKE);
				log.special(String.format("Input instructions: %s", inputInstructions));

				// Consider forwarding directives?
				Map<String,Task> inputTasks = executeMany(this.inputs, inputInstructions, null, log);

				Map<String,String> inputStats = new HashMap<>();

				// Collect results
				// Statistics
				Graph.Node node = graph.getNode(info.getID());
				node.attributes.put("href", "http://www.fmi.fi");

				for (Entry<String,Task> entry : inputTasks.entrySet()){
					String key = entry.getKey();
					Task inputTask = entry.getValue();
					if (remakeDepth > 0){
						inputTask.remakeDepth = remakeDepth -1;
						log.experimental(String.format("Input cache clearance: %s", inputTask.remakeDepth));
						//instructions.add(ActionType.GENERATE);
					}
					else {
						inputTask.remakeDepth = 0;
					}


					Graph.Node inputNode = graph.getNode(inputTask.info.getID());
					Graph.Link link = graph.addLink(node, inputNode);
					link.attributes.put("label", key);

					if (inputTask.result != null){
						String r = inputTask.result.toString();
						log.note(String.format("Retrieved: %s = %s", key, r));
						this.retrievedInputs.put(key, r);
						// Stats
						inputStats.put(key, inputTask.info.getID());
						if (collectStatistics) {
							if (!statistics.containsKey(inputTask.info.getID()))
								statistics.put(inputTask.info.getID(), new HashMap<>()); // Marker

						}
						link.attributes.put("color", "green");
						if (inputTask.log.indexedException.index > 300){
							link.attributes.put("style", "dashed");
							log.warn("Errors in input generation: " + inputTask.log.indexedException.getMessage());
						}
					}
					else {
						link.attributes.put("color", "red");
						inputNode.attributes.put("color", "red");
						inputNode.attributes.put("style", "dotted");
						log.warn(inputTask.log.indexedException.getMessage());
						log.log(HttpLog.HttpStatus.PRECONDITION_FAILED, String.format("Retrieval failed: %s=%s", key, inputTask));
						log.reset(); // Forget that anyway...
					}
					inputTask.log.close(); // close PrintStream
				}

				if (collectStatistics) {
					statistics.put(info.getID(), inputStats);
				}

				/// MAIN
				log.note("Running Generator: " + info.PRODUCT_ID);

				File fileTmp = outputPathTmp.toFile();

				try {
					generator.generate(this);

				}
				catch (IndexedException e) {

					// NOTE: possibly the file has been generated, but with some less significant errors.
					// serverLog.warn(info.getFilename() + ": " + e);
					// serverLog.warn(info.getFilename());

					log.log(e);

					try {
						this.delete(this.outputPathTmp);
						//this.delete(fileFinal);
					}
					catch (Exception e2) {
						log.error(e2.getLocalizedMessage());
					}
					//log.error(e.getMessage());
					//throw e;
					//return false;
				}


				if (fileTmp.length() > 0){

					/// Override
					log.setStatus(Log.Status.OK);
					log.debug(String.format("OK, generator produced tmp file: %s", this.outputPathTmp));
					serverLog.success(info.getFilename());

					// Let's take this slowly...
					try {
						this.move(this.outputPathTmp, this.outputPath);
					} catch (IOException e) {
						log.warn(e.toString());
						//log.warn(String.format("filePerms: %s", filePerms));
						log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Failed in moving tmp file: %s", this.outputPathTmp));
						// log.error(String.format("Failed in moving tmp file: %s", this.outputPath));
					}


					try {
						Files.setPosixFilePermissions(this.outputPath, filePerms);
					} catch (IOException e) {
						log.warn(e.toString());
						log.warn(String.format("Failed in setting perms %s for file: %s", filePerms, this.outputPath));
						// log.warn(String.format("filePerms: %s", filePerms));
						// log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Failed in setting perms for file: %s", this.outputPath));
						// log.error(String.format("Failed in moving tmp file: %s", this.outputPath));
					}


					// this.result = this.outputPath;
				}
				else {
					log.log(HttpLog.HttpStatus.CONFLICT, String.format("Generator failed in producing the file: %s", this.outputPath));
					serverLog.fail(info.getFilename());
					// log.error("Generator failed in producing tmp file: " + fileTmp.getName());
					try {
						this.delete(this.outputPathTmp);
					}
					catch (Exception e) {
						/// TODO: is it a fatal error if a product defines its input wrong?
						log.error(e.getMessage()); // RETURN?
						log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Failed in deleting tmp file: %s", this.outputPath));
					}
				}

			}

			if (instructions.isSet(ActionType.INPUTLIST) && ! instructions.involves(ActionType.GENERATE)) {
				log.note("Input list: requested");
				result = inputs;
			}


			if (instructions.isSet(ResultType.FILE)){
				// TODO: save file (of JavaGenerator)
				// if (this.result instanceof Path)...

				if (this.instructions.isSet(ActionType.DELETE)){
					if (fileFinal.exists())
						log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Deleting failed: %s (%d bytes)", fileFinal, fileFinal.length() ));
					else {
						log.reset();
						log.ok("Deleted.");
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

				}
				else {

					this.result = this.outputPath;
					log.ok(String.format("Exists: %s (%d bytes)", this.result, fileFinal.length() ));

					// "Post processing"
					if (this.instructions.isSet(Instructions.SHORTCUT)){
						if (this.info.isDynamic()) {
							try {
								Path dir = ensureDir(cacheRoot,productDir); //, dirPerms);
								this.link(this.outputPath, dir, true);
							} catch (IOException e) {
								log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
							}
						}
						else
							log.debug("Static product (no TIMESTAMP), skipped shortcut");
					}

					if (this.instructions.isSet(Instructions.LATEST)){
						if (this.info.isDynamic()) {
							try {
								Path dir = ensureDir(cacheRoot, productDir); //, dirPerms);
								this.link(this.outputPath, dir.resolve(this.info.getFilename("LATEST")), true);
							} catch (IOException e) {
								log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
							}
						}
						else
							log.debug("Static product (no TIMESTAMP), skipped shortcut");
					}




					for (Path path: this.instructions.copies) {
						try {
							this.copy(this.outputPath, path); // Paths.get(path)
							log.ok(String.format("Copied: %s", path));
							// System.out.println("Copy "+path);
						} catch (IOException e) {
							log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Copying failed: %s", path));
							log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
							//log.error(String.format("Copying failed: %s", path));
						}
					}

					for (Path path: this.instructions.links) {
						try {
							this.link(this.outputPath, path, false); // Paths.get(path)
							log.ok(String.format("Linked: %s", path));
						} catch (IOException e) {
							log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Linking failed: %s", path));
							log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
						}
					}

					if (this.instructions.move != null) {
						try {
							this.move(this.outputPath, this.instructions.move);
							this.result = this.instructions.move;
							log.log(HttpLog.HttpStatus.MOVED_PERMANENTLY, String.format("Moved: %s", this.instructions.move));
						} catch (IOException e) {
							log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Moving failed: %s", this.instructions.move));
							log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
						}
					}

					try {
						if (this.outputDirTmp.toFile().exists()) {
							log.debug(String.format("Remove tmp dir: %s", this.outputDirTmp));
							Files.delete(this.outputDirTmp);
						}
					} catch (IOException e) {
						log.warn(e.getMessage());
						log.log(HttpLog.HttpStatus.SEE_OTHER, String.format("Failed in removing tmp dir %s", this.outputDirTmp));
					}

				}
			}
			else {
				if (this.result != null) // Object?
					log.info("Result: " + this.result.toString());
				log.note("Task completed: instructions=" + this.instructions);
				// status page?

			}

			if (instructions.isSet(Instructions.RUN)){
				ShellExec.OutputReader reader = new ShellExec.OutputReader(log.getPrintStream());
				//ShellExec shellExec = new ShellExec(Paths.get("run.sh"), this.productDir);
				ShellExec.exec("./run.sh", null, productRoot.resolve(productDir), reader);

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
			Map<String,Object> env =  new TreeMap<String, Object>();
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
						log.log(HttpLog.HttpStatus.NOT_MODIFIED, String.format("Odd timestamp '%s' length (%d)",  this.info.TIMESTAMP, this.info.TIMESTAMP.length()));
				}
			}


			// Consider keeping Objects, and calling .toString() only upon ExternalGenerator?
			env.put("OUTDIR",  this.outputPathTmp.getParent().toString()); //cacheRoot.resolve(this.relativeOutputDir));
			env.put("OUTFILE", this.outputPathTmp.getFileName().toString());

			if (!cmdPath.isEmpty()) {
				env.put("PATH", cmdPath);
				log.special("PATH=" + cmdPath);
			}

			if (! this.retrievedInputs.isEmpty()){
				env.put("INPUTKEYS", String.join(",", this.retrievedInputs.keySet().toArray(new String[0])));
				env.putAll(this.retrievedInputs);
			}

			env.putAll(this.info.directives);

			return env;
		}


		@Override
		public String toString() {
			if (this.info.directives.isEmpty())
				return String.format("%s", this.filename);
			else
				return String.format("%s?%s", this.filename, this.info.directives.toString());
		}

		//final long creationTime;
		
		public Object result;


	}  // Task




	public interface Generator   {

		/**
		 */
		void generate(Task task) throws IndexedException; // throws IOException, InterruptedException ;

		// Semantics? (List or retrieved objects)
		// boolean hasInputs();

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
	 * @param instructions
	 * @param log
	 * @return - the completed set of tasks, including failed ones.
	 *
	 */
	/*
	public Map<String,Task> executeMany(Map<String,String> taskRequests, int instructions, Map directives, HttpLog log) {
		return executeMany(taskRequests, new Actions(instructions), directives, log);
	}

	 */

	/** Run a set of tasks in parallel.
	 *
	 * @param taskRequests
	 * @param instructions
	 * @param directives
	 * @param log
	 * @return
	 */
	public Map<String,Task> executeMany(Map<String,String> taskRequests, Instructions instructions, Map directives, HttpLog log){

		Map<String,Task> tasks = new HashMap<>();

		final int count = taskRequests.size();

		if (count == 0){
			//log.info(String.format("Inits (%d) tasks "));
			return tasks;
		}

		log.info(String.format("Inits (%d) tasks ", count));

		log.info(String.format("Inits (%s) tasks ", taskRequests.entrySet()));

		/// Check COPY & LINK targets (must be directories, if several tasks)
		if (count > 1){

			for (Path p : instructions.copies){
				if (!p.toFile().isDirectory()) {
					log.warn(String.format("Several tasks (%d), but single COPY target: %s", count, p));
				}
			}

			for (Path p : instructions.links){
				if (!p.toFile().isDirectory()){
					log.warn(String.format("Several tasks (%d), but single LINK target: %s", count, p));
				}
			}

		}


		for (Entry<String,String> entry : taskRequests.entrySet()){
			String key   = entry.getKey();
			String value = entry.getValue();

			// log.debug(String.format("Starting: %s = %s", key, value));
			//System.err.println(String.format("xStill shere: %s = %s", key, value));

			try {

				//HttpLog subLog = (count==1) ? log.child(key) : null;
				//HttpLog subLog = new HttpLog(key, log);
				// log.debug(String.format("# Still here: %s = %s", key, value));

				Task task = new Task(value, instructions.value, log);

				//log.debug(String.format("Check: %s = %s", key, value));
				task.info.setDirectives(directives);
				task.instructions.addCopies(instructions.copies);
				task.instructions.addLinks(instructions.links);
				task.instructions.addMove(instructions.move); // Thread-safe?

				if ((directives != null) && !directives.isEmpty())
					log.note(String.format("Directives: %s = %s", key, directives));

				log.info(String.format("Prepared TASK: %s = %s [%s]", key, task, task.instructions));


				task.log.setVerbosity(log.getVerbosity());
				task.log.COLOURS = log.COLOURS;
				if (task.log.logFile != null){
					log.note(String.format("See separate log: %s",  task.log.logFile));
				}

				tasks.put(key, task);

			}
			catch (ParseException e) {
				//System.err.println(String.format("EROR here: %s = %s", key, value));

				// TODO: is it a fatal error if a product defines its input wrong? Answer: YES
				log.warn(String.format("Could not parse product: %s(%s)",  key, value));
				//log.warn(e.getMessage());
				log.log(HttpLog.HttpStatus.NOT_ACCEPTABLE, e.getLocalizedMessage());
				//log.error(e.getLocalizedMessage()); // RETURN?
			}
			catch (Exception e) {
				// System.err.println(String.format("EROR2 here: %s = %s", key, value));
				log.error(String.format("Unexpected exception in creating product %s(%s)", key, value));
				log.log(HttpLog.HttpStatus.INTERNAL_SERVER_ERROR, e.getLocalizedMessage());
			}
			finally {
				//System.out.println("Final.." + key);
			}

		}


		if (tasks.size() > 0){

			log.note(String.format("Starting (%d) tasks", tasks.size()));

			/// Start as threads, if requested
			for (Entry<String,Task> entry : tasks.entrySet()){
				String key = entry.getKey();
				Task task = entry.getValue();
				if (task.instructions.isSet(ActionType.PARALLEL)) {
					try {
						// serverLog.special
						log.info(String.format("Starting task[%d] '%s': %s as a thread", task.getTaskId(), key, task));
						// log.debug(String.format("Starting thread '%s': %s", key, task));
						task.start();
					} catch (IllegalStateException e) {
						log.error("Already running? " + e.toString());
					}
				}
			}


			// wait();

			for (Entry<String,Task> entry : tasks.entrySet()){
				String key = entry.getKey();
				Task task = entry.getValue();
				try {
					if (task.instructions.involves(ActionType.PARALLEL)) {
						task.join();
					}
					else {
						// serverLog.special
						log.info(String.format("Starting task '%s': %s (in main thread)", key, task));
						task.execute();
					}
					log.info(String.format("Finished task: %s(%s)[%d]", key, task.info.PRODUCT_ID, task.getTaskId()));
					//serverLog.special(String.format("Finished task: %s", task));
				} catch (InterruptedException e) {
					log.warn(String.format("Interrupted task: %s(%s)[%d]", key, task.info.PRODUCT_ID, task.getTaskId()));
					log.warn(String.format("Pending file? : ", task.outputPathTmp));
				}
				log.info(String.format("Final status: %s", task.log.indexedException));
				//log.log("test");
			}

		}

		return tasks;
	}

	/** Searches for shell side (and later, Java) generators
	 *
	 * @param productID
	 * @return
	 */
	public Generator getGenerator(String productID) throws IndexedException {
		Path dir = productRoot.resolve(getProductDir(productID));
		Generator generator = new ExternalGenerator(productID, dir.toString());
		return generator;
	};

	/// Checks if a file exists in cache or storage, wait for completion if needed.
	/**
	 *
	 * return immediately if non-empty or nonexistent, else wait for an empty file to complete.
	 * @param file
	 * @param maxEmptySec maximum age of empty file in seconds
	 * @param log
	 * @return
	 * @throws InterruptedException
	 */
	public boolean queryFile(File file, int maxEmptySec, Log log) throws InterruptedException {

		if (!file.exists()){
			log.log(HttpLog.HttpStatus.SEE_OTHER, String.format("File does not exist: %s", file));
			return false;
		}

		int remainingSec = this.timeOut;

		final long fileLength = file.length();

		if (fileLength > 0) {
			//log.note("File found");
			log.log(HttpLog.HttpStatus.OK, String.format("File found: %s (%d bytes)", file.getName(), fileLength));
			return true;
		}
		else { // empty file
			long ageSec = (java.lang.System.currentTimeMillis() - file.lastModified()) / 1000;
			if (ageSec > maxEmptySec){
				log.log(HttpLog.HttpStatus.SEE_OTHER, String.format("Time %d", java.lang.System.currentTimeMillis()));
				log.log(HttpLog.HttpStatus.SEE_OTHER, String.format("File %d", file.lastModified()));
				log.log(HttpLog.HttpStatus.NOT_MODIFIED, String.format("Outdated empty file, age=%d min, (max %d s)",(ageSec/60), maxEmptySec));
				//return false;
			}
			else {
				log.log(HttpLog.HttpStatus.SEE_OTHER, "Empty fresh file exists, waiting for it to complete...");
				for (int i = 1; i < 10; i++) {
					int waitSec = i * i;
					log.warn("Waiting for "+ waitSec + " s...");
					TimeUnit.SECONDS.sleep(waitSec);
					if (file.length() > 0){
						log.log(HttpLog.HttpStatus.CREATED,"File appeared");
						return true;
					}
					remainingSec = remainingSec - waitSec;
					if (remainingSec <= 0)
						break;
				}
				log.log(HttpLog.HttpStatus.NOT_MODIFIED,"File did not appear (grow), even after waiting");
				//return false;
			}

			try {
				log.note("Deleting file");
				delete(file.toPath());
				//this.delete(this.outputPath);
			}
			catch (IOException e) {
				// TODO: redesign (check if delete needed at all)
				this.serverLog.log(HttpLog.HttpStatus.CONFLICT, String.format("Failed in deleting file: %s, %s", file.toPath(), e.getMessage()));
			}

			return false;
		}
	}



	/// System side setting.
	//  public String pythonScriptGenerator = "generate.py";

	/// Maximum allowed time (in seconds) for product generation (excluding inputs?) FIXME share in two?
	public int timeOut = 30;

	public class DeleteFiles extends SimpleFileVisitor<Path> {

		// Print information about
		// each type of file.
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
			if (attr.isRegularFile() || attr.isSymbolicLink()) {
				try {
					serverLog.note(String.format("Deleting file: %s", file));
					Files.delete(file);
				} catch (IOException e) {
					serverLog.warn(e.toString());
				}
			}
			return CONTINUE;
		}

		// Print each directory visited.
		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) {

			// Prevent removing cacheRoot
			// Todo: fix clearCache
			if (dir.endsWith("cache"))
				return CONTINUE;

			try {
				serverLog.debug(String.format("Delete dir: %s", dir));
				Files.delete(dir);
			} catch (IOException e) {
				serverLog.warn(e.toString());
			}
			return CONTINUE;
		}

		// If there is some error accessing
		@Override
		public FileVisitResult visitFileFailed(Path file, IOException e) {
			serverLog.warn(e.getMessage());
			return CONTINUE;
		}
	}

	public void clearCache(boolean confirm) throws IOException {

		if (!this.cacheRoot.endsWith("cache")){
			serverLog.error("Cache root does not end with 'cache' : " + this.cacheRoot);
			return;
		}

		Path p = this.cacheRoot.toRealPath();
		if (!p.endsWith("cache")){
			serverLog.error("Cache root does not end with 'cache' : " + p);
			return;
		}

		if (confirm){
			System.err.println(String.format("Delete files in %s ? ", p ));
			Scanner kbd = new Scanner(System.in);
			String line = kbd.nextLine();
			if (line.isEmpty() || (line.toLowerCase().charAt(0) != 'y')){
				System.err.println("Cancelled");
				return;
			}
		}

		serverLog.note("Clearing cache: " + p);
		Files.walkFileTree(p, new DeleteFiles());

		serverLog.note("Clearing cache completed");
		//Files.walk(this.cacheRoot).filter(Files::isDirectory).filter(Files::i).forEach(Files::delete);

	}

	public enum OutputFormat {
		TEXT,
		VT100,
		HTML;
	}



	public static void help(){

		System.err.println("Usage:   java -cp $NUTLET_PATH nutshell.ProductServer  [<options>] <products>");
		System.err.println("    - $NUTLET_PATH: directory of nutshell class files or path up to Nutlet.jar");
		System.err.println();
		System.err.println("Options: ");
		System.err.println("    --help: this help dump");
		System.err.println("    --conf <file> : read configuration file");
		System.err.println();
		System.err.println("    Product generation:");
		System.err.println("    --instructions <string> : main operation: " + String.join(",", Flags.getKeys(Instructions.class)));
		System.err.println("      or shorthands: --make --delete --generate ... )");
		System.err.println("    --directives <key>=<value>|<key>=<value>|... : instructions for product generator");
		System.err.println();
		System.err.println("    Additional actions:");
		System.err.println("    --copy <target>: copy file to target (repeatable)");
		System.err.println("    --link <target>: link file to target (repeatable)");
		System.err.println("    --move <target>: move file to target");
		System.err.println();
		System.err.println("    Logging:");
		System.err.println("    --log_level <level> : set verbosity (DEBUG, INFO, NOTE, WARN, ERROR)");
		System.err.println("      or shorthands --debug / --info --note --warn --error");
		System.err.println("    --log_style <style>: " + Arrays.toString(OutputFormat.values()));
		//System.err.println("    --statistics: analyze,collect/diagnose product generation");
		System.err.println("    --analyse: collect/diagnose product generation");
		System.err.println();
		System.err.println("Examples: ");
		System.err.println("    java -cp $NUTLET_PATH 201012161615_test.ppmforge_DIMENSION=2.5.png");

		//System.err.println(HttpLog.messages);

	}


	/// Command-line interface for 
	public static void main(String[] args) {
		
		if (args.length == 0){
			help();
			return;
		}

		final ProductServer server = new ProductServer();
		server.serverLog.setVerbosity(Log.Status.DEBUG);
		server.timeOut = 20;

		HttpLog log = server.serverLog; //.child("CmdLine");
		log.COLOURS = true;

		String confFile = null;

		Map<String,String> products = new TreeMap<>();
		Instructions instructions = new Instructions();
		Map<String ,String> directives = new TreeMap<>();

		//Field[] instructionFields = Instructions.class.getFields();

		try {
			for (int i = 0; i < args.length; i++) {

				String arg = args[i];
				//log.info(String.format("ARGS[%d]: %s", i, arg));

				if (arg.charAt(0) == '-') {

					if (arg.charAt(1) != '-') {
						throw new IllegalArgumentException("Short options (-x) not suppported");
					}

					String opt = arg.substring(2);

					if (opt.equals("help")) {
						System.err.println(String.format("version: %s", server.getVersionString()));
						System.err.println(String.format("confFile: %s", server.confFile));
						System.err.println();
						help();
						return;
					}

					if (opt.equals("verbose")) {
						log.setVerbosity(Log.Status.LOG);
						continue;
					}

					if (opt.equals("log_level")) {
						arg = args[++i];
						log.setVerbosity(arg);
						continue;
					}


					/// It is recommended to give --config among the first options, unless default used.
					if (opt.equals("conf")) {
						if (confFile != null){
							log.warn("Reading second conf file (already read: " + confFile + ")");
						}
						confFile = args[++i];
						server.readConfig(confFile);
						//log.note(String.format("Server log %s", p));
						//System.err.println(String.format("Server log %s", p));
						continue; // Note
					}

					// Now, read conf file if not read this far.
					if (confFile == null){
						server.readConfig(confFile = "nutshell.cnf");
						//Path p = server.setLogFile(System.getenv("USER"));
						//log.note(String.format("Server log: %s", p));
					}


					if (opt.equals("version")) {
						System.out.println(server.getVersionString());
					}
					if (opt.equals("log_style")) {
						//String arg = args[++i];
						OutputFormat f = OutputFormat.valueOf(args[++i]);
						switch (f){
							case TEXT:
								log.COLOURS = false;
								break;
							case VT100:
								log.COLOURS = true;
								break;
							case HTML:
							default:
								log.warn("Not implemented:" + f);
						}

					}
					else if (opt.equals("product")) {
						products.put("product", args[++i]);
					}
					else if (opt.equals("parse")) { // Debugging
						Task product = server.new Task(args[++i], 0, log);
						Map<String,Object> map = product.getParamEnv();
						String[] array = MapUtils.toArray(map);
						for (String s : array) {
							System.out.println(s);
						}
						return;
					}
					else if (opt.equals("http_params")) { // HTTP Get Params
						String[] p = products.values().toArray(new String[0]);
						if (p.length == 0){
							log.warn("give --product <FILE>");
							System.err.println("Product not defined yet?");
							System.exit(1);
						}
						else {
							if (p.length > 1){
								System.err.println("Several products defined, using last");
							}
							if (instructions.isEmpty())
								instructions.add(ActionType.MAKE);
							Task product = server.new Task(p[p.length-1], 0, log);
							System.out.println(String.format("instructions=%s&product=%s", instructions, product));
							return;
						}
					}
					else if (opt.equals("remake")) {
						int d = Integer.parseInt(args[++i]);
						if (d < 0){
							log.warn(String.format("Negative cache clearance depth %d, setting to zerp.", d));
							d = 0;
						}
						server.defaultRemakeDepth = d;
					}
					else if (opt.equals("analyse")) { // Debugging
						server.collectStatistics = true;
					}
					else if (opt.equals("status")) { // Debugging
						System.out.println(server.toString());
					}
					else if (opt.equals("copy")) {
						instructions.addCopy(args[++i]);
					}
					else if (opt.equals("link")) {
						instructions.addLink(args[++i]);
					}
					else if (opt.equals("move")) {
						instructions.addMove(args[++i]);
					}
					else if (opt.equals("directives")) {
						MapUtils.setEntries( args[++i],"\\|", "true", directives);
					}
					else if (opt.equals("path")) {
						server.cmdPath = args[++i]; // overríde!
						server.setup.put("cmdPath", server.cmdPath);
					}
					else {
						// Actions
						try {
							if (opt.equals("instructions")) {
								opt = args[++i];
								log.info("Adding instructions: " + opt);
								instructions.set(opt);
							}
							else {
								// Set instructions from invidual args: --make --delete --generate
								// TODO: longName => LONG_NAME
								opt = opt.toUpperCase();

								boolean handled = false;
								if (Log.statusCodesByName.containsKey(opt)){
									log.setVerbosity(Log.statusCodesByName.get(opt).level);
									// System.out.println("Testing:" + log.getVerbosity() + "/" + opt);
									handled = true;
									//break;
								}

								if (! handled){
									log.info("Adding instruction: " + opt);
									Instructions.class.getField(opt); // ensure field exists
									instructions.add(opt);
								}

							}
						}
						catch (NoSuchFieldException|IllegalAccessException e) {
							log.note("Use following instruction codes: ");
							for (Field field : Instructions.class.getFields()) {
								String name = field.getName();
								if (name.equals(name.toUpperCase())) {
									try {
										log.note("  " + name + "=" + field.getInt(null));
									} catch (IllegalAccessException e1) {
										//illegalAccessException.printStackTrace();
									}
								}
							}
							//log.log(HttpLog.HttpStatus.METHOD_NOT_ALLOWED, String.format("No such instruction code: %s", opt));
							log.error(String.format("No such instruction code: %s", opt));
							System.exit(2);
						}

					}
				}
				else {  // Argument does not start with "--"
					products.put("product" + (products.size()+1), arg);
				}

			}
		}
		catch (Exception e) {
			log.error(String.format("Unhandled exception: %s", e));
			//e.printStackTrace(log.printStream);
			System.exit(1);
		}

		if (instructions.isEmpty()){
			instructions.set(ActionType.MAKE);
		}

		int result = 0;

		log.note("Instructions: " + instructions);

		if (instructions.isSet(ActionType.CLEAR_CACHE)) {
			log.warn("Clearing cache");
			if (instructions.value != ActionType.CLEAR_CACHE){
				instructions.remove(ActionType.CLEAR_CACHE);
				log.warn(String.format("Discarding remaining instructions: %s", instructions) );
			}

			try {
				server.clearCache(true);
			} catch (IOException e) {
				log.log(HttpLog.HttpStatus.CONFLICT, "Clearing cache failed");
				result = 4;
			}
			System.exit(result);
		}

		if (!instructions.copies.isEmpty())
			log.note(String.format("   COPY(%d):\t %s", instructions.copies.size(), instructions.copies));
		if (!instructions.links.isEmpty())
			log.note(String.format("   LINK(%d):\t %s", instructions.links.size(),  instructions.links));
		if (instructions.move != null)
			log.note(String.format("   MOVE: \t %s", instructions.move));

		if (!directives.isEmpty())
			log.note("Directives: " + directives);

		/// "MAIN"
		//log.warn("Starting..");

		Map<String,ProductServer.Task> tasks = server.executeMany(products, instructions, directives, log);
		//log.note(String.format("Waiting for (%d) tasks to complete... ", tasks.size()));

		//System.err.println(log.indexedException);
		////System.err.println(log);
		// System.err.println(log.getStatus());
		if (log.getStatus() <= Log.Status.ERROR.level){
			log.warn("Errors: %d " + log.getStatus());
			++result;
		}

		for (Entry<String,Task> entry: tasks.entrySet()) {
			String key = entry.getKey();
			Task  task = entry.getValue();
			if (task.log.indexedException.index >= HttpLog.HttpStatus.BAD_REQUEST.getIndex()){
				log.warn(String.format("Exception occurred: %s", task.log.indexedException.getMessage()));
				//log.debug(task.log.indexedException);
				if (result < 20)
					++result;
			}
			else {
				log.info(String.format("Status:\t%s", task.log.indexedException.getMessage()));
			}
			// log.info(String.format("status: %s %d", task.info.PRODUCT_ID ,task.log.status) );
			if (task.log.logFile != null)
				log.info(String.format("Log:\t %s", task.log.logFile.getAbsolutePath()));

			if (task.result != null)
				log.info(String.format("Result [%s]: %s", task.result.getClass().getCanonicalName(), task.result));
			//log.info("Log:\t"  + task.log.logFile.getAbsolutePath());

			if (task.outputPath.toFile().exists())
				log.ok(String.format("File exists:\t %s (%d bytes)", task.outputPath.toString(), task.outputPath.toFile().length()));
				//log.note(String.format("File exists:\t %s (%d bytes)", task.outputPath.toString(), task.outputPath.toFile().length()));

			if (task.instructions.isSet(ActionType.INPUTLIST)){

				for (Map.Entry<String,String> ec: task.inputs.entrySet()) {
					System.out.println(String.format("%s:\t %s", ec.getKey(), ec.getValue()));
				}

			}

			if (server.collectStatistics){
				//String dotFileName = task.info.getFilename("") + ".dot.png";
				String dotFileName = task.info.getFilename("") + ".svg";
				log.special(String.format("writing %s", dotFileName));
				server.graph.dotToFile(dotFileName);
			}

			task.log.close();
		}


		// System.err.println("Eksit");
		log.debug("Exiting..");
		log.close();
		System.exit(result);

	}

	final
	public List<Integer> version = Arrays.asList(1, 6);

	public String getVersionString() {
		//Arrays.
		//
		//version.stream().toArray();
		return version.toString();
		//return version.stream().forEach(System.err::println);
		//collect(Collectors.joining(",")).toString();
		//return String.join(",", version.toArray(null));
		//return Arrays.toString(version.toArray());
	}


}


