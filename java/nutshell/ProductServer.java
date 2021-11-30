package nutshell;

import sun.misc.Signal;

import javax.servlet.http.HttpServletResponse;
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

	final public HttpLog serverLog; // = new HttpLog(getClass().getSimpleName());
	final DateFormat logFilenameTimeFormat = new SimpleDateFormat("yyyy-MM-dd");


	ProductServer(){
		serverLog = new HttpLog(getClass().getSimpleName());
		//log.setLogFile(ensureFile(cacheRoot));
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
	protected Path storageRoot = Paths.get(".");

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

	/** Unix PATH variable extension, eg. "/var/local/bin:/media/mnt/bin"
	 *
	 */
	//public String cmdPath = "";

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
		// this.cmdPath     = setup.getOrDefault("PATH2", ".").toString();
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

	public interface ResultType {

		/** The product is in memory cache
		 */
		int MEMORY = 1; // "RESULT=MEMORY"

		/** Product should be saved on disk. System side generator may save it anyway.
		 *   – return immediately in success
		 */
		int FILE = 2; // "RESULT=FILE"

		/** Link file to short directory
		 *
		 */
		int SHORTCUT = 4 | FILE; // "POSTOP=LINK_SHORTCUT"

		/** Link file to short directory, $TIMESTAMP replaced with 'LATEST'
		 */
		int LATEST = 8 | FILE;  // "POSTOP=LINK_LATEST"


	}

	/** Specifies, what will be done after product has been generated (or checked).
	 *
	 *  If unset, a status report is returned, including path/link to the result.
	 *
	 */
	public interface OutputType {


		/** HTTP only: client will be redirected to URL of generated product
		 *  Strictly speaking - needs no file, but a result (file or object).
		 *
		 * @see Nutlet#doGet
		 */
		int REDIRECT = 16 | ResultType.FILE;  // "OUTPUT=REDIRECT require[RESULT=FILE]"

		/** Output in a stream (Currently, HTTP only. Future option: standard output.)
		 *
		 * @see Nutlet#doGet
		 */
		int STREAM = 32;  // "OUTPUT=STREAM"

	}

	public interface ActionType {

		/** Only checks existence of product (used with @MEMORY or @FILE)
		 *
		 *  Returns immediately, if a non-empty product instance is found.
		 *  If an empty product is found, waits for completion.
		 */
		int EXISTS = 64; // "OUTPUT=INFO"

		/** Delete the product file on disk (future option: also in memory cache).
		 */
		int DELETE = 128; // "ACTION=DELETE_FILE" or "PREOP_DELETE"

		/** Retrieve input list.
		 */
		int INPUTLIST = 256; // a "hidden" flag? "OUTPUT=INFO"

		/** The product is (re)generated. Used with MEMORY and FILE
		 *
		 */
		int GENERATE = 512; //  | INPUTLIST;

		/** Conditional generate: create product only if it does not exist. Used with MEMORY and FILE
		 *
		 *  In program flow, MEMORY and FILE will be checked first, and only if they fail, GENERATION takes place.
		 */
		int MAKE = 1024 | EXISTS;
		//public final int MAKE = EXIST | GENERATE;
		// A "hidden" flag? actually unclear, should be either RESULT=FILE or RESULT=MEMORY (but not RESULT=null);
		// Also, acts like "make both MEMORY and FILE objects".

		/// Check if product is in cache memory or disk, but do not generate it if missing.
		// public static final int QUERY = MEMORY|FILE;

		/** Run script "run.sh" in the product directory (after)
		 */
		int RUN = 2048; // PostProcessing!

		/** Go through product request handler checking existence of product generator, memory cache and output directory.
		 */
		int DEBUG = 4096; // | INPUTLIST; // "OUTPUT=INFO"

		/// Computation intensive products are computed in the background; return a notification receipt in HTML format.
		//  public static final int BATCH = 4096;

	}


	public static class Actions extends Flags implements ActionType, ResultType, OutputType {

		public Actions(){
		}

		public Actions(int a){
			this.value = a;
		}

		public boolean isEmpty() {
			return (value == 0);
		}


		/** Add specific request to copy the result.
		 *
		 *  Several requests can be added.
		 *
		 * @param filename - target file
		 */
		public void addCopy(String filename){
			copies.add(Paths.get(filename));
		}

		/** Add specific request to copy the result.
		 *
		 * @param copies - target files
		 */
		public void addCopies(List<Path> copies){
			if (copies != null)
				this.copies.addAll(copies);
		}


		/** Add specific request to list the result.
		 *
		 *  Several requests can be added.
		 *
		 * @param filename
		 */
		public void addLink(String filename){
			links.add(Paths.get(filename));
		}

		/** Add specific request to link the result.
		 *
		 *  Several requests can be added.
		 *
		 * @param links - filenames pointing to the original result
		 */
		public void addLinks(List<Path> links){
			if (links != null)
				this.links.addAll(links);
		}

		/** Add specific request to link the result.
		 *
		 *  Several requests can be added.
		 *
		 * @param path - target
		 */
		public void addMove(String path){
			if (path != null)
				move = Paths.get(path);
		}

		public void addMove(Path path){
			move = path;
		}

		protected List<Path> copies = new ArrayList<>();
		protected List<Path> links  = new ArrayList<>();
		protected Path move = null;

	}

	//public Path ensureDir(Path root, Path relativePath, Set<PosixFilePermission> perms) throws IOException {
	public Path ensureDir(Path root, Path relativePath) throws IOException {

		if (relativePath==null)
			return root;

		if (relativePath.getNameCount() == 0)
			return root;

		Path path = root.resolve(relativePath);
		//this.log.warn(String.format("Checking: %s/./%s",  root, relativePath));

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
				serverLog.warn(String.format("could not se unix GID: ",  fileGroupID) );
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

		final public String filename;

		final public Actions actions = new Actions();

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

		public final Map<String,String> directives = new HashMap<>();
		public final Map<String,String> inputs = new HashMap<>();
		public final Map<String,Object> retrievedInputs = new HashMap<>();

		/** Product generation task defining a product instance and operations on it.
		 *
		 * @param productStr
		 * @param actions - definition how a product is retrieved and handled thereafter - @see #Actions
		 * @param parentLog - log of the parent task (or main process, a product server)
		 * @throws ParseException - if parsing productStr fails
		 */
		public Task(String productStr, int actions, HttpLog parentLog) throws ParseException {

			// in LOG // this.creationTime = System.currentTimeMillis();
			this.info = new ProductInfo(productStr);
			this.log = new HttpLog("["+this.info.PRODUCT_ID+"]", parentLog);

			this.filename = this.info.getFilename();
			this.actions.set(actions);

			// Relative
			this.productDir   = getProductDir(this.info.PRODUCT_ID);
			this.timeStampDir = getTimestampDir(this.info.time);
			this.relativeOutputDir    = this.timeStampDir.resolve(this.productDir);
			this.relativeOutputDirTmp = this.timeStampDir.resolve(this.productDir).resolve("tmp" + getId());
			this.relativeOutputPath = relativeOutputDir.resolve(filename);

			//this.relativeLogPath    = relativeOutputDir.resolve(getFilePrefix() + filename + "." + getId() + ".log");
			this.relativeLogPath    = relativeOutputDir.resolve(filename + "." + getId() + ".log");

			// Absolute
			this.outputDir     = cacheRoot.resolve(this.relativeOutputDir);
			this.outputDirTmp  = cacheRoot.resolve(this.relativeOutputDirTmp);
			this.outputPath    = outputDir.resolve(filename);
			//this.outputPathTmp = outputDirTmp.resolve(getFilePrefix() + filename);
			this.outputPathTmp = outputDirTmp.resolve(filename);
			this.storagePath   = storageRoot.resolve(this.relativeOutputDir).resolve(filename);



			try {
				ensureFile(cacheRoot, this.relativeLogPath);
				this.log.setLogFile(cacheRoot.resolve(this.relativeLogPath));
			} catch (IOException e) {
				System.err.println(String.format("Opening Log file (%s) failed: %s", this.relativeLogPath, e));
				//this.log.setLogFile(null);
			}

			//this.log.warn("Where am I?");
			this.log.debug(String.format("Created TASK %s [%d] [%s] %s ", this.filename, this.getId(), this.actions, this.directives)); //  this.toString()
			this.result = null;
		}



		/** Imports map to directives map, converting array values to comma-separated strings.
		 *
		 * @param map
		 */
		public void setDirectives(Map<String,String[]> map){

			if (map == null)
				return;

			for (Map.Entry<String,String[]> entry : map.entrySet() ){ //parameters.entrySet()) {
				String key = entry.getKey().toString();
				if (key.equals(key.toUpperCase())){
					String[] value = entry.getValue();
					if ((value != null) && (value.length > 0))
						directives.put(key, String.join(",", value));
				}
			}
		}

		//public boolean move(File src, File dst){
		public Path move(Path src, Path dst) throws IOException {
			this.log.note(String.format("Move: from: %s ", src));
			this.log.note(String.format("        to: %s ", dst));
			if (dst.toFile().isDirectory())
				dst = dst.resolve(src.getFileName());
			//Files.setPosixFilePermissions(dst, filePerms);
			Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
			return dst;
			//return src.renameTo(dst);
		}

		public Path copy(Path src, Path dst) throws IOException {
			this.log.note(String.format("Copy: from: %s ", src));
			this.log.note(String.format("        to: %s ", dst));
			if (dst.toFile().isDirectory())
				dst = dst.resolve(src.getFileName());
			return Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
		}

		public Path link(Path src, Path dst, boolean force) throws IOException {
			this.log.note(String.format("Link: from: %s ", src));
			this.log.note(String.format("        to: %s ", dst));

			if (dst.toFile().isDirectory())
				dst = dst.resolve(src.getFileName());

			if (Files.exists(dst)){

				if (Files.isSymbolicLink(dst)){
					this.log.note(String.format("Link exists: %s ", dst));
				}
				else {
					this.log.note(String.format("File (not Link) exists: %s ", dst));
				}

				if (Files.isSameFile(src, dst)){
					this.log.note("File and link are equal");
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
			this.log.note(String.format("Deleting: %s ", dst));
			return deleteIfExists(dst);
			//return file.delete();
		}



		/** Runs a thread generating and/or otherwise handling a product
		 *
		 * @see #execute()
		 */
		@Override
		public void run(){
			try {
				Signal.handle(new Signal("INT"), this::handleInterrupt);
				execute();
			}
			catch (InterruptedException e) {
				//this.log.status
				this.log.note(e.toString());
				this.log.warn("Interrupted");
				//e.printStackTrace(log.printStream);
			}
			/*
			catch (IndexedException e) {
				this.log.warn("NutShell indexed exception --->");
				//this.log.error(e.getMessage());
				this.log.log(e);
				this.log.warn("NutShell indexed exception <---");
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
			if (actions.involves(ResultType.FILE)) {
				try {
					delete(this.outputPath);
					delete(this.outputPathTmp); // what about tmpdir?
				} catch (IOException e) {
					log.warn(e.getMessage());
					//e.printStackTrace();
				}
			}
		}


		/** Execute this task: delete, load, generate and/or send a product.
		 *
		 * @see #run()
		 *
		 * @return
		 * @throws InterruptedException  // Gene
		 */
		public void execute() throws InterruptedException {

			Generator generator = null;

			this.log.log(HttpServletResponse.SC_ACCEPTED, String.format("Starting %s", this.info.PRODUCT_ID));

			// Logical corrections

			if (! this.actions.copies.isEmpty())
				this.actions.add(Actions.MAKE | ResultType.FILE);

			if (! this.actions.links.isEmpty())
				this.actions.add(Actions.MAKE | ResultType.FILE);

			if (this.actions.move != null)
				this.actions.add(Actions.MAKE | ResultType.FILE);

			// Rest default result type
			// if (this.actions.involves(Actions.MAKE | Actions.DELETE)) { }
			if (!this.actions.involves(ResultType.FILE | ResultType.MEMORY)) {
				// This "type selection" could be also done with Generator?
				this.log.log(HttpServletResponse.SC_OK, "Setting default result type: FILE");
				this.actions.add(ResultType.FILE);
			}


			if (this.actions.involves(Actions.DELETE)){

				if (this.actions.isSet(ResultType.FILE) ) {
					try {
						this.delete(this.outputPath);
					} catch (IOException e) {
						this.log.log(HttpServletResponse.SC_CONFLICT, String.format("Failed in deleting file: %s, %s", this.outputPath, e.getMessage()));
						//return;
					}
				}

			}


			//  Note: Java Generators do not need disk, unless FILE
			//  WRONG, ... MAKE will always require FILE

			if (this.actions.isSet(Actions.MEMORY)) {
				// Not implemented yet
				// this.result = new BufferedImage();
			}

			// This is a potential path, not committing to a physical file yet.
			File fileFinal = this.outputPath.toFile();

			if (this.actions.involves(Actions.EXISTS | ResultType.FILE) && ! this.actions.isSet(Actions.DELETE)) {

				if (storagePath.toFile().exists() && ! fileFinal.exists()){
					this.log.log(HttpServletResponse.SC_OK, String.format("Stored file exists: %s", this.storagePath));

					try {
						ensureDir(cacheRoot, relativeOutputDir); //, dirPerms);
					}
					catch (IOException e) {
						this.log.warn(e.getMessage());
						this.log.log(HttpServletResponse.SC_FORBIDDEN, String.format("Failed in creating dir (with permissions): %s", this.outputDir));
						//e.printStackTrace();
					}

					try {
						this.link(this.storagePath, this.outputPath, false);
					}
					catch (IOException e) {
						this.log.error(e.getMessage());
						this.log.log(HttpServletResponse.SC_CONFLICT, String.format("Failed in linking: %s <- %s", this.outputPath, storagePath));
						//e.printStackTrace();
					}
				}
				// Don't wait (so the other process will "notice" ie fail)
				else if (queryFile(fileFinal, 90, this.log)) {
					// Order? Does it delete immediately?
					this.result = this.outputPath;
					this.log.log(HttpServletResponse.SC_OK, String.format("File exists: %s", this.outputPath));
				}
				else { // if (this.actions.isSet(Actions.EXIST)){
					this.log.log(HttpServletResponse.SC_NOT_FOUND, String.format("File does not exist: %s", this.outputPath));
					//this.log.log(HttpServletResponse.SC_OK, String.format("File does not exist: %s", this.outputPath));
					if (this.actions.isSet(Actions.MAKE)) {
						this.actions.add(Actions.GENERATE);
					}
				}

			}


			// Retrieve Geneator, if needed
			if (this.actions.involves(Actions.GENERATE | Actions.INPUTLIST | Actions.DEBUG)){

				this.log.log(HttpServletResponse.SC_OK, String.format("Determining generator for : %s", this.info.PRODUCT_ID));
				try {
					generator = getGenerator(this.info.PRODUCT_ID);
					this.log.log(HttpServletResponse.SC_CREATED, String.format("Generator(%s): %s", this.info.PRODUCT_ID, generator));

					if (this.actions.involves(Actions.GENERATE )){
						if (generator instanceof ExternalGenerator)
							this.actions.add(ResultType.FILE); // PREPARE dir & empty file
						else
							this.actions.add(ResultType.MEMORY);
					}

				}
				catch (IndexedException e){
					this.log.log(HttpServletResponse.SC_CONFLICT, "Generator does not exist");
					this.log.log(e);
					this.actions.remove(Actions.GENERATE);
					this.actions.remove(Actions.INPUTLIST);
					this.actions.remove(Actions.DEBUG);
				}

			}

			if (this.actions.isSet(Actions.DELETE)) {

				if (fileFinal.exists()) {
					this.log.log(HttpServletResponse.SC_CONFLICT, String.format("Failed in deleting: %s ", this.outputPath));
				}
				else {
					this.log.log(HttpServletResponse.SC_NO_CONTENT, String.format("Deleted: %s ", this.outputPath));
				}

				if (this.actions.involves(Actions.GENERATE|Actions.EXISTS)){
					this.log.log(HttpServletResponse.SC_MULTIPLE_CHOICES, String.format("Mutually contradicting actions: %s ", this.actions));
				}

				return;
			}


			// Generate or at least list inputs
			if (this.actions.involves(Actions.INPUTLIST  | Actions.GENERATE)) { //

				this.log.debug(String.format("Determining input list for: %s", this.info.PRODUCT_ID));

				this.inputs.clear(); // needed?

				try {
					this.inputs.putAll(generator.getInputList(this));
					this.log.debug(this.inputs.toString());
					//System.err.println("## " + this.inputs);
				}
				catch (Exception e) {
					log.log(HttpServletResponse.SC_CONFLICT, "Input list retrieval failed");
					log.log(e);

					this.log.warn("Removing GENERATE from actions");
					this.actions.remove(Actions.GENERATE);
				}

				if (!this.inputs.isEmpty())
					this.log.info(String.format("Collected (%d) input requests for: %s", this.inputs.size(), this.info.PRODUCT_ID));

			}



			if (this.actions.isSet(Actions.GENERATE)) {

				this.log.reset(); // Forget old sins

				this.log.info("Generate!");
				// this.log.info("No generation request, returning.");
				// return;

				// Mark this task being processed (empty file)
				// if (this.actions.isSet(ResultType.FILE) && !fileFinal.exists()){
				try {
					ensureDir(cacheRoot, relativeOutputDirTmp); //, dirPerms);
					ensureDir(cacheRoot, relativeOutputDir); //,    dirPerms);
					ensureFile(cacheRoot, relativeOutputPath); //, dirPerms, filePerms); // this could be enough?
					//Path genLogPath =  ensureWritableFile(cacheRoot, relativeOutputDirTmp.resolve(filename+".GEN.log"));
				}
				catch (IOException e) {
					this.log.log(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, String.format("Failed in creating: %s", e.getMessage()));
					return;
				}

				//	this.log.debug(String.format("No need to create: %s/./%s",  cacheRoot, this.relativeOutputDirTmp));

				// Assume Generator uses input similar to output (File or Object)
				final int inputActions = this.actions.value & (ResultType.MEMORY | ResultType.FILE);

				Map<String,Task> tasks = executeMany(this.inputs, new Actions(inputActions), null, this.log);

				// Collect results
				for (Entry<String,Task> entry : tasks.entrySet()){
					String key = entry.getKey();
					Task inputTask = entry.getValue();
					if (inputTask.result != null){
						String r = inputTask.result.toString();
						this.log.note(String.format("Retrieved: %s = %s", key, r));
						this.retrievedInputs.put(key, r);
						if (inputTask.log.indexedException.index > 300){
							this.log.warn("Errors in input generation: " + inputTask.log.indexedException.getMessage());
						}
					}
					else {
						this.log.error(inputTask.log.indexedException.getMessage());
						log.log(HttpServletResponse.SC_PRECONDITION_FAILED, String.format("Retrieval failed: %s=%s", key, inputTask));
					}
					inputTask.log.close(); // close PrintStream
				}


				/// MAIN
				this.log.note("Running Generator: " + this.info.PRODUCT_ID);
				/*
				if (this.log.logFile == null){
					try {
						Path p = ensureFile(cacheRoot, relativeLogPath);
						this.log.setLogFile(p);
						this.log.warn(String.format("Directing log to file: ", this.log.logFile));
					} catch (IOException e) {
						this.log.warn(e.getMessage());
						this.log.error(String.format("Could not open log file: ", this.log.logFile));
						//e.printStackTrace();
					}

				}*/

				File fileTmp   = this.outputPathTmp.toFile();

				try {
					generator.generate(this);
				}
				catch (IndexedException e) {

					this.log.log(e);

					try {
						this.delete(this.outputPathTmp);
						//this.delete(fileFinal);
					}
					catch (Exception e2) {
						this.log.error(e2.getLocalizedMessage());
					}
					//this.log.error(e.getMessage());
					//throw e;
					//return false;
				}


				if (fileTmp.length() > 0){

					this.log.debug(String.format("OK, generator produced tmp file: %s", this.outputPathTmp));

					// Let's take this slowly...
					try {
						this.move(this.outputPathTmp, this.outputPath);
					} catch (IOException e) {
						this.log.warn(e.toString());
						//this.log.warn(String.format("filePerms: %s", filePerms));
						log.log(HttpServletResponse.SC_FORBIDDEN, String.format("Failed in moving tmp file: %s", this.outputPathTmp));
						// this.log.error(String.format("Failed in moving tmp file: %s", this.outputPath));
					}


					try {
						Files.setPosixFilePermissions(this.outputPath, filePerms);
					} catch (IOException e) {
						this.log.warn(e.toString());
						log.warn(String.format("Failed in setting perms %s for file: %s", filePerms, this.outputPath));
						// this.log.warn(String.format("filePerms: %s", filePerms));
						//log.log(HttpServletResponse.SC_FORBIDDEN, String.format("Failed in setting perms for file: %s", this.outputPath));
						// this.log.error(String.format("Failed in moving tmp file: %s", this.outputPath));
					}


					// this.result = this.outputPath;
				}
				else {
					log.log(HttpServletResponse.SC_CONFLICT, String.format("Generator failed in producing the file: %s", this.outputPath));
					// this.log.error("Generator failed in producing tmp file: " + fileTmp.getName());
					try {
						this.delete(this.outputPathTmp);
					}
					catch (Exception e) {
						/// TODO: is it a fatal error if a product defines its input wrong?
						this.log.error(e.getMessage()); // RETURN?
						log.log(HttpServletResponse.SC_FORBIDDEN, String.format("Failed in deleting tmp file: %s", this.outputPath));
					}
				}

			}

			if (this.actions.isSet(Actions.INPUTLIST) && ! this.actions.involves(Actions.GENERATE)) {
				this.log.note("Input list: requested");
				this.result = this.inputs;
			}


			if (this.actions.isSet(ResultType.FILE)){
				// TODO: save file (of JavaGenerator)
				// if (this.result instanceof Path)...

				if (this.actions.isSet(ActionType.DELETE)){
					if (fileFinal.exists())
						this.log.log(HttpServletResponse.SC_FORBIDDEN, String.format("Deleting failed: %s (%d bytes)", fileFinal, fileFinal.length() ));
					else {
						this.log.reset();
						this.log.ok("Deleted.");
					}
				}
				else if (fileFinal.length() == 0) {

					this.log.log(HttpServletResponse.SC_CONFLICT, String.format("Failed in generating: %s ", this.outputPath));
					try {
						this.delete(this.outputPath);
					} catch (IOException e) {
						//this.log.error(String.format("Failed in deleting: %s ", this.outputPath));
						this.log.log(HttpServletResponse.SC_FORBIDDEN, String.format("Failed in deleting: %s ", this.outputPath));
						this.log.log(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
					}

				}
				else {

					this.result = this.outputPath;
					this.log.ok(String.format("Exists: %s (%d bytes)", this.result, fileFinal.length() ));

					// "Post processing"
					if (this.actions.isSet(Actions.SHORTCUT)){
						if (this.info.isDynamic()) {
							try {
								Path dir = ensureDir(cacheRoot,productDir); //, dirPerms);
								this.link(this.outputPath, dir, true);
							} catch (IOException e) {
								log.log(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
							}
						}
						else
							log.debug("Static product (no TIMESTAMP), skipped shortcut");
					}

					if (this.actions.isSet(Actions.LATEST)){
						if (this.info.isDynamic()) {
							try {
								Path dir = ensureDir(cacheRoot, productDir); //, dirPerms);
								this.link(this.outputPath, dir.resolve(this.info.getFilename("LATEST")), true);
							} catch (IOException e) {
								log.log(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
							}
						}
						else
							log.debug("Static product (no TIMESTAMP), skipped shortcut");
					}




					for (Path path: this.actions.copies) {
						try {
							this.copy(this.outputPath, path); // Paths.get(path)
						} catch (IOException e) {
							this.log.log(HttpServletResponse.SC_FORBIDDEN, String.format("Copying failed: %s", path));
							this.log.log(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
							//this.log.error(String.format("Copying failed: %s", path));
						}
					}

					for (Path path: this.actions.links) {
						try {
							this.link(this.outputPath, path, false); // Paths.get(path)
						} catch (IOException e) {
							this.log.log(HttpServletResponse.SC_FORBIDDEN, String.format("Linking failed: %s", path));
							this.log.log(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
						}
					}

					if (this.actions.move != null) {
						try {
							this.move(this.outputPath, this.actions.move);
							this.result = this.actions.move;
							this.log.log(HttpServletResponse.SC_MOVED_PERMANENTLY, String.format("Moved: %s", this.actions.move));
						} catch (IOException e) {
							this.log.log(HttpServletResponse.SC_FORBIDDEN, String.format("Moving failed: %s", this.actions.move));
							this.log.log(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
						}
					}

					try {
						if (this.outputDirTmp.toFile().exists()) {
							this.log.debug(String.format("Remove tmp dir: %s", this.outputDirTmp));
							Files.delete(this.outputDirTmp);
						}
					} catch (IOException e) {
						this.log.warn(e.getMessage());
						this.log.log(HttpServletResponse.SC_SEE_OTHER, String.format("Failed in removing tmp dir %s", this.outputDirTmp));
					}

				}
			}
			else {
				if (this.result != null) // Object?
					this.log.info("Result: " + this.result.toString());
				this.log.note("Task completed: actions=" + this.actions);
				// status page?

			}

			if (actions.isSet(Actions.RUN)){
				ShellExec.OutputReader reader = new ShellExec.OutputReader(this.log.getPrintStream());
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
						this.log.log(HttpServletResponse.SC_NOT_MODIFIED, String.format("Odd timestamp '%s' length (%d)",  this.info.TIMESTAMP, this.info.TIMESTAMP.length()));
				}
			}

			// Consider keeping Objects, and calling .toString() only upon ExternalGenerator?
			env.put("OUTDIR",  this.outputPathTmp.getParent().toString()); //cacheRoot.resolve(this.relativeOutputDir));
			env.put("OUTFILE", this.outputPathTmp.getFileName().toString());

			if (! this.retrievedInputs.isEmpty()){
				env.put("INPUTKEYS", String.join(",", this.retrievedInputs.keySet().toArray(new String[0])));
				env.putAll(this.retrievedInputs);
			}

			env.putAll(this.directives);

			return env;
		}


		@Override
		public String toString() {
			if (this.directives.isEmpty())
				return String.format("%s", this.filename);
			else
				return String.format("%s?%s", this.filename, this.directives.toString());
		}

		//final long creationTime;
		
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
	/*
	public Map<String,Task> executeMany(Map<String,String> taskRequests, int actions, Map directives, HttpLog log) {
		return executeMany(taskRequests, new Actions(actions), directives, log);
	}

	 */

	/** Run a set of tasks in parallel.
	 *
	 * @param taskRequests
	 * @param actions
	 * @param directives
	 * @param log
	 * @return
	 */
	public Map<String,Task> executeMany(Map<String,String> taskRequests, Actions actions, Map directives, HttpLog log){

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

			for (Path p : actions.copies){
				if (!p.toFile().isDirectory()) {
					log.warn(String.format("Several tasks (%d), but single COPY target: %s", count, p));
				}
			}

			for (Path p : actions.links){
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
				log.debug(String.format("Still here: %s = %s", key, value));
				System.err.println(String.format("Still here: %s = %s", key, value));

				Task task = new Task(value, actions.value, log);
				System.err.println(String.format("NOTTT here: %s = %s", key, value));

				log.debug(String.format("Still life: %s = %s", key, value));
				task.setDirectives(directives);
				task.actions.addCopies(actions.copies);
				task.actions.addLinks(actions.links);
				task.actions.addMove(actions.move); // Thread-safe?

				task.log.setVerbosity(log.getVerbosity());
				log.debug(String.format("Starting thread: %s(%s)[%d]", key, task.info.PRODUCT_ID, task.getId()));
				if (task.log.logFile != null){
					log.info(String.format("See separate log: %s",  task.log.logFile));
				}

				tasks.put(key, task);


			}
			catch (ParseException e) {
				System.err.println(String.format("EROR here: %s = %s", key, value));

				/// TODO: is it a fatal error if a product defines its input wrong?
				log.warn(String.format("Could not parse product: %s(%s)",  key, value));
				//log.warn(e.getMessage());
				log.error(e.getLocalizedMessage()); // RETURN?
			}
			catch (Exception e) {
				System.err.println(String.format("EROR2 here: %s = %s", key, value));
				log.error("Unexpected exception... " + e.getLocalizedMessage());
			}

		}

		for (Entry<String,Task> entry : tasks.entrySet()){
			String key = entry.getKey();
			Task task = entry.getValue();

			try {
				log.debug(String.format("Starting thread %s: %s", key, task));
				task.start();
			}
			catch (IllegalStateException e){
				log.error("Already running? " + e.toString());
			}
		}


		log.note("Started (" + tasks.size() + ") tasks... ");
		// wait();

		for (Entry<String,Task> entry : tasks.entrySet()){
			String key = entry.getKey();
			Task task = entry.getValue();
			try {
				task.join();
				log.debug(String.format("Finished thread: %s(%s)[%d]", key, task.info.PRODUCT_ID, task.getId()));
			}
			catch (InterruptedException e) {
				log.warn(String.format("Interrupted thread: %s(%s)[%d]", key, task.info.PRODUCT_ID, task.getId()));
				log.warn(String.format("Pending file? : ", task.outputPathTmp));
			}
			log.log(String.format("Final status: %s", task.log.indexedException));
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
			log.log(HttpServletResponse.SC_SEE_OTHER, String.format("File does not exist: %s", file));
			return false;
		}

		int remainingSec = this.timeOut;

		final long fileLength = file.length();

		if (fileLength > 0) {
			//log.note("File found");
			log.log(HttpServletResponse.SC_OK, String.format("File found: %s (%d bytes)", file.getName(), fileLength));
			return true;
		}
		else { // empty file
			long ageSec = (java.lang.System.currentTimeMillis() - file.lastModified()) / 1000;
			if (ageSec > maxEmptySec){
				log.log(HttpServletResponse.SC_SEE_OTHER, String.format("Time %d", java.lang.System.currentTimeMillis()));
				log.log(HttpServletResponse.SC_SEE_OTHER, String.format("File %d", file.lastModified()));
				log.log(HttpServletResponse.SC_NOT_MODIFIED, String.format("Outdated empty file, age=%d min, (max %d s)",(ageSec/60), maxEmptySec));
				//return false;
			}
			else {
				log.log(HttpServletResponse.SC_SEE_OTHER, "Empty fresh file exists, waiting for it to complete...");
				for (int i = 1; i < 10; i++) {
					int waitSec = i * i;
					log.warn("Waiting for "+ waitSec + " s...");
					TimeUnit.SECONDS.sleep(waitSec);
					if (file.length() > 0){
						log.log(HttpServletResponse.SC_CREATED,"File appeared");
						return true;
					}
					remainingSec = remainingSec - waitSec;
					if (remainingSec <= 0)
						break;
				}
				log.log(HttpServletResponse.SC_NOT_MODIFIED,"File did not appear (grow), even after waiting");
				//return false;
			}

			try {
				log.note("Deleting file");
				delete(file.toPath());
				//this.delete(this.outputPath);
			}
			catch (IOException e) {
				this.serverLog.log(HttpServletResponse.SC_CONFLICT, String.format("Failed in deleting file: %s, %s", file.toPath(), e.getMessage()));
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




	public static void help(){

		System.err.println("Usage:   java -cp $NUTLET_PATH nutshell.ProductServer  [<options>] <products>");
		System.err.println("    - $NUTLET_PATH: directory of nutshell class files or path up to Nutlet.jar");
		System.err.println();
		System.err.println("Options: ");
		System.err.println("    --help: this help dump");
		System.err.println("    --verbose <level> : set verbosity (DEBUG, INFO, NOTE, WARN, ERROR)");
		System.err.println("    --debug : same as --verbose DEBUG");
		System.err.println("    --conf <file> : read configuration file");
		System.err.println("    --actions <string> : main operation: " + String.join(",", Flags.getKeys(Actions.class)));
		System.err.println("      (all the actions can be also supplied invidually: --make --delete --generate ... )");
		System.err.println("    --copy <target>: copy file to target (repeatable)");
		System.err.println("    --link <target>: link file to target (repeatable)");
		System.err.println("    --move <target>: move file to target");
		System.err.println("    --directives <key>=<value>|<key>=<value>|... : instructions (pipe-separated) for product generator");
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
		server.serverLog.setVerbosity(Log.DEBUG);
		server.timeOut = 20;

		HttpLog log = server.serverLog; //.child("CmdLine");

		String confFile = null;

		Map<String,String> products = new TreeMap<>();
		Actions actions = new Actions();
		Map<String ,String> directives = new TreeMap<>();

		Field[] actionFields = Actions.class.getFields();

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
						help();
						return;
					}

					if (opt.equals("verbose")) {
						log.setVerbosity(Log.DEBUG);
						continue;
					}

					if (opt.equals("log_level")) {
						arg = args[++i];
						try {
							int level = Integer.parseInt(arg);
							log.setVerbosity(level);
						} catch (NumberFormatException e) {
							try {
								log.setVerbosity(arg);
							} catch (NoSuchFieldException e2) {
								log.note(String.format("Use numeric levels or keys: %s", Log.statusCodes.entrySet().toString()));
								log.error("No such verbosity level: " + arg);
								log.warn(String.format("Retaining level: %s", Log.statusCodes.get(log.getVerbosity())));
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
						server.readConfig(confFile);
						//log.note(String.format("Server log %s", p));
						//System.err.println(String.format("Server log %s", p));
						continue; // Note
					}

					if (opt.equals("clearCache")) {
						log.warn("Clearing cache");
						server.clearCache(true);
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
							if (actions.isEmpty())
								actions.add(Actions.MAKE);
							Task product = server.new Task(p[p.length-1], 0, log);
							System.out.println(String.format("actions=%s&product=%s", actions, product));
							return;
						}
					}
					else if (opt.equals("copy")) {
						actions.addCopy(args[++i]);
					}
					else if (opt.equals("link")) {
						actions.addLink(args[++i]);
					}
					else if (opt.equals("move")) {
						actions.addMove(args[++i]);
					}
					else if (opt.equals("directives")) {
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

								if (HttpLog.statusCodes.containsValue(opt)){
									log.error(String.format("Not implemented, use --log_level %s", opt));
									continue;
								}

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
							//log.log(HttpServletResponse.SC_METHOD_NOT_ALLOWED, String.format("No such action code: %s", opt));
							log.error(String.format("No such action code: %s", opt));
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

		if (actions.isEmpty()){
			actions.set(Actions.MAKE);
		}

		log.note("Actions: " + actions);
		if (!actions.copies.isEmpty())
			log.note(String.format("   COPY(%d):\t %s", actions.copies.size(), actions.copies));
		if (!actions.links.isEmpty())
			log.note(String.format("   LINK(%d):\t %s", actions.links.size(),  actions.links));
		if (actions.move != null)
			log.note(String.format("   MOVE: \t %s", actions.move));

		if (!directives.isEmpty())
			log.note("Directives: " + directives);

		/// "MAIN"
		int result = 0;

		Map<String,ProductServer.Task> tasks = server.executeMany(products, actions, directives, log);
		//log.note(String.format("Waiting for (%d) tasks to complete... ", tasks.size()));

		log.warn("Starting..");

		for (Entry<String,Task> entry: tasks.entrySet()) {
			String key = entry.getKey();
			Task  task = entry.getValue();
			if (task.log.indexedException.index >= HttpServletResponse.SC_BAD_REQUEST){
				//log.note(String.format("exception: %s", task.log.indexedException.getMessage()));
				log.log(task.log.indexedException);
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
				log.note(String.format("File exists:\t %s (%d bytes)", task.outputPath.toString(), task.outputPath.toFile().length()));

			task.log.close();
		}

		// System.err.println("Eksit");
		log.warn("Exiting..");
		System.exit(result);

	}

	final
	public List<Integer> version = Arrays.asList(1, 3);

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


