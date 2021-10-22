package nutshell;

import sun.misc.Signal;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
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

	final public HttpLog log = new HttpLog(getClass().getSimpleName());

	/*
	ProductServer(){
		log = new HttpLog(getClass().getSimpleName());
	}
	*/

	public final Map<String,Object> setup = new HashMap<>();

	// TODO: add to config, set in constructor
	//public final Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
	public Set<PosixFilePermission> dirPerms = PosixFilePermissions.fromString("rwxrwxr-x");
	//public FileAttribute<Set<PosixFilePermission>> dirPermAttrs = PosixFilePermissions.asFileAttribute(dirPerms);

	public Set<PosixFilePermission> filePerms = PosixFilePermissions.fromString("rw-rw-r--");
	//public FileAttribute<Set<PosixFilePermission>> filePermAttrs = PosixFilePermissions.asFileAttribute(filePerms);

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

	/** Unix PATH variable extension, eg. "/var/local/bin:/media/mnt/bin"
	 *
	 */
	public String cmdPath = "";

	protected void readConfig(){
		readConfig(confFile);
	}

	protected void readConfig(String path) {
		readConfig(Paths.get(path));
	}

	protected void readConfig(Path path){

		//Map<String, Object> setup = new HashMap<>();

		try {
			if (path != null) {
				log.debug("Reading setup: " + path.toString());
				MapUtils.read(path.toFile(), setup);
			}
			this.confFile = path; //Paths.get(path);
			setup.put("confFile", path);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			setup.put("confFileError", e.getLocalizedMessage());
		}

		log.debug(setup.toString());

		this.cacheRoot   = Paths.get(setup.getOrDefault("CACHE_ROOT",   ".").toString());
		this.productRoot = Paths.get(setup.getOrDefault("PRODUCT_ROOT", ".").toString());

		this.dirPerms = PosixFilePermissions.fromString(setup.getOrDefault("DIR_PERMS","rwxrwxr-x").toString());
		this.filePerms = PosixFilePermissions.fromString(setup.getOrDefault("FILE_PERMS","rwxrwxr--").toString());
		setup.put("dirPerms", dirPerms);
		setup.put("filePerms", filePerms);
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

		/**
		 *  The product should be finally in memory cache
		 *
		 */
		static final int MEMORY = 2; // "RESULT=MEMORY"

		/** Product should be saved on disk. System side generator may save it anyway.
		 *   – return immediately in success
		 */
		static final int FILE = 8; // "RESULT=FILE"

	}

	/** Specifies, what will be done after product has been generated (or checked)
	 *
	 */
	public interface OutputType {

		/**
		 *  Checks existence of product generator, memory cache and output directory.
		 *
		 */
		static final int CHECK  = 1; // "OUTPUT=INFO"
		// Rename STATUS or INFO or REPORT

		/**
		 *  Checks existence of product generator, memory cache and output directory.
		 *
		 */
		static final int EXIST  = 2048; // "OUTPUT=INFO"


		/** Output in a stream (typically as a response to an HTTP request, but also as pipe)
		 *
		 * @see Nutlet#doGet
		 */
		public static final int STREAM = 256;  // "OUTPUT=STREAM"


	}

	public static class Actions extends Flags implements ResultType, OutputType {

		public Actions(){

		}

		public Actions(int a){
			this.value = a;
		}

		/**
		 *   The object will be generated, resulting native output (FILE or MEMORY)..
		 *
		 *   If also {@link #DELETE} is requested, the file will be regenerated.
		 */
		public static final int GENERATE = 4;  // "ACTION=GENERATE_FILE"

		/**
		 *  HTTP only: client will be redirected to URL of generated product
		 *  Strictly speaking - needs no file, but a result (file or object).
		 *
		 * @see Nutlet#doGet
		 */
		public static final int REDIRECT = FILE|16;  // "OUTPUT=REDIRECT require[RESULT=FILE]"


		/**
		 *  Delete the product file on disk (future option: also in memory cache).
		 */
		public static final int DELETE = 128; // "ACTION=DELETE_FILE" or "PREOP_DELETE"


		/** Generate product only if it does not exist.
		 *
		 * In program flow, MEMORY and FILE will be checked first, and only if they fail, GENERATION takes place.
		 */
		public static final int MAKE = MEMORY | FILE;
		// A "hidden" flag? actually unclear, should be either RESULT=FILE or RESULT=MEMORY (but not RESULT=null);
		// Also, acts like "make both MEMORY and FILE objects".

		/// Check if product is in cache memory or disk, but do not generate it if missing.
		// public static final int QUERY = MEMORY|FILE;

		/**
		 *  Derive and dump input list (and quit)
		 */
		public static final int INPUTLIST = 64; // a "hidden" flag? "OUTPUT=INFO"


		// Output options


		/** Link file to short directory
		 *
		 */
		public static final int SHORTCUT = 512 | FILE; // "POSTOP=LINK_SHORTCUT"

		/** Link file to short directory, $TIMESTAMP replaced with 'LATEST'
		 *
		 */
		public static final int LATEST = 1024 | FILE;  // "POSTOP=LINK_LATEST"

		public boolean isEmpty() {
			return (value == 0);
		}

		/// Computation intensive products are computed in the background; return a notification receipt in HTML format.
		//  public static final int BATCH = 512;

		/** Add specific request to copy the result.
		 *
		 *  Several requests can be added.
		 *
		 * @param filename
		 */
		public void addCopy(String filename){
			copies.add(Paths.get(filename));
		}

		/** Add specific request to copy the result.
		 *
		 *  Several requests can be added.
		 *
		 * @param filename
		 */
		public void addLink(String filename){
			links.add(Paths.get(filename));
		}

		protected List<Path> copies = new ArrayList<>();
		protected List<Path> links  = new ArrayList<>();

	}


	/**
	 *   A "tray" containing both the product query info and the resulting object if successfully queried.
	 *   Task does not know about the generator. Notice GENERATE and getParamEnv.
	 *
	 */
	public class Task extends Thread { //implements Runnable {
		
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

		public final Map<String,String> directives = new HashMap<>();

		public final Map<String,String> inputs = new HashMap<>();
		public final Map<String,Object> retrievedInputs = new HashMap<>();





		/** Product generation task defining a product instance and alternative operations for retreaving it.
		 *
		 * @param productStr
		 * @param actions - definition how a product is retrieved and handled thereafter - @see #Actions
		 * @param parentLog - utility for logging
		 * @throws ParseException - if parsing productStr fails
		 */
		public Task(String productStr, int actions, HttpLog parentLog) throws ParseException {

			this.creationTime = System.currentTimeMillis();

			this.info = new ProductInfo(productStr);
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

			if (parentLog != null){
				this.log = parentLog.child("["+this.info.PRODUCT_ID+"]");
				this.log.setLogFile(null);
			}
			else {
				this.log = new HttpLog("<"+this.info.PRODUCT_ID+">");
				this.log.setLogFile(cacheRoot.resolve(this.relativeLogPath));
			}
			//this.log.warn("Where am I?");
			this.log.debug(String.format("started %s [%d] [%s] %s ", this.filename, this.getId(), this.actions, this.directives)); //  this.toString()
			this.result = null;
	}



		/** Imports map to directives map, converting array values to comma-separated strings.
		 *
		 * @param map
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

		//public boolean move(File src, File dst){
		public Path move(Path src, Path dst) throws IOException {
			this.log.note(String.format("Move: from: %s ", src));
			this.log.note(String.format("        to: %s ", dst));
			return Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
			//return src.renameTo(dst);
		}

		public Path copy(Path src, Path dst) throws IOException {
			this.log.note(String.format("Copy: from: %s ", src));
			this.log.note(String.format("        to: %s ", dst));
			if (dst.toFile().isDirectory())
				dst = dst.resolve(src.getFileName());
			return Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);   //(src, dst, StandardCopyOption.REPLACE_EXISTING);
		}

		public Path link(Path src, Path dst) throws IOException {
			this.log.note(String.format("Link: from: %s ", src));
			this.log.note(String.format("        to: %s ", dst));
			if (dst.toFile().isDirectory())
				dst = dst.resolve(src.getFileName());
			return Files.createSymbolicLink(dst, src);
			//return Files.createLink(src, dst);   //(src, dst, StandardCopyOption.REPLACE_EXISTING);
		}

		public boolean delete(Path dst) throws IOException {
			this.log.note(String.format("Deleting: %s ", dst));
			return Files.deleteIfExists(dst);
			//return file.delete();
		}


		/** Runs a thread generating and/or otherways handling a product
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
				this.log.warn("Interrupted");
				e.printStackTrace(log.printStream);
			}
			catch (IndexedException e) {
				this.log.warn("NutShell indexed exception --->");
				//this.log.error(e.getMessage());
				this.log.log(e);
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
		public void execute() throws InterruptedException, IndexedException {

			this.log.log(HttpServletResponse.SC_OK, "Determining generator for : " + this.info.PRODUCT_ID);

			Generator generator = getGenerator(this.info.PRODUCT_ID);
			this.log.log(HttpServletResponse.SC_CREATED, String.format("Generator(%s): %s", this.info.PRODUCT_ID, generator));
			//this.log.log(HttpServletResponse.SC_ACCEPTED, String.format("Generator(%s): %s", this.info.PRODUCT_ID, generator));

			if (! this.actions.copies.isEmpty())
				this.actions.add(Actions.FILE);

			if (! this.actions.links.isEmpty())
				this.actions.add(Actions.FILE);

			if (this.actions.involves(Actions.EXIST)){
				if (!this.actions.involves(Actions.MEMORY | Actions.FILE)) {
					this.log.log(HttpServletResponse.SC_OK, "Adding FILE");
					this.actions.add(Actions.FILE);
				}
			}



			// Implicit action request
			// MAKE = lazy: do not generate if exists
			if (this.actions.involves(Actions.MAKE | Actions.GENERATE)){ // == MAKE ?!
				if (generator instanceof ExternalGenerator)
					this.actions.add(Actions.FILE); // PREPARE dir & empty file
				else
					this.actions.add(Actions.MEMORY);
			}

			//  Note: Java Generators do not need disk, unless FILE
			//  WRONG, ... MAKE will always require FILE

			if (this.actions.isSet(Actions.MEMORY)) {
				// Not implemented yet
				// this.result = new BufferedImage();
			}

			// These are file paths, not committing to provide to actual physical files
			File fileFinal = this.outputPath.toFile();

			if (this.actions.involves(Actions.DELETE | Actions.FILE | Actions.EXIST)) {
				// Wait
				if (queryFile(fileFinal,90, this.log) && !this.actions.isSet(Actions.DELETE)){
					// Order? Does it delete immediately?
					this.result = this.outputPath;
					if (this.actions.isSet(Actions.FILE) && this.actions.isSet(Actions.EXIST)){
						this.log.log(HttpServletResponse.SC_OK, String.format("Completed! File exists: %s", this.outputPath));
						return;
					}
				}
				else {
					try {
						this.delete(this.outputPath);
					}
					catch (IOException e) {
						this.log.log(HttpServletResponse.SC_CONFLICT, String.format("Failed in deleting file: %s, %s", this.outputPath, e.getMessage()));
					}

					if (this.actions.isSet(Actions.FILE) && this.actions.isSet(Actions.EXIST)){
						this.log.log(HttpServletResponse.SC_NOT_FOUND, String.format("File does not exist: %s", this.outputPath));
						return;
					}
				}

			}

			if (this.actions.isSet(Actions.FILE) && (fileFinal.length()==0)){
				this.actions.add(Actions.GENERATE);
			}

			// Mark this task being processed (empty file)
			// if (this.actions.isSet(Actions.FILE) && !fileFinal.exists()){
			if (this.actions.isSet(Actions.GENERATE)){

				try {
					FileUtils.ensureDir(cacheRoot, relativeOutputDirTmp, dirPerms);
					FileUtils.ensureDir(cacheRoot, relativeOutputDir,    dirPerms);
					FileUtils.ensureFile(cacheRoot, relativeOutputPath, filePerms, dirPerms); // this could be enough?
					//Path genLogPath =  ensureWritableFile(cacheRoot, relativeOutputDirTmp.resolve(filename+".GEN.log"));

				}
				catch (IndexedException e) {
					this.log.log(e);
					if (e.index >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR){
						return;
					}
				}
				catch (Exception e) {
					this.log.log(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, String.format("Failed in creating: %s", e.getMessage()));
					return;
				}

			}
			else {
				this.log.debug(String.format("No need to create: %s/./%s",  cacheRoot, this.relativeOutputDirTmp));
			}

			// Generate or at least list inputs
			if (this.actions.involves(Actions.GENERATE | Actions.INPUTLIST)) { //

				this.log.debug(String.format("Determining input list for: %s", this.info.PRODUCT_ID));

				this.inputs.clear(); // needed?

				try {
					this.inputs.putAll(generator.getInputList(this));
					this.log.debug(this.inputs.toString());
					//System.err.println("## " + this.inputs);
				} catch (Exception e) {
					log.log(HttpServletResponse.SC_CONFLICT, "Input list retrieval failed");
					this.log.error(e.getMessage());

					this.log.warn("Removing GENERATE from actions");
					this.actions.remove(Actions.GENERATE);
				}

				if (!this.inputs.isEmpty())
					this.log.info(String.format("Collected (%d) input requests for: %s", this.inputs.size(), this.info.PRODUCT_ID));

			}



			if (this.actions.isSet(Actions.GENERATE)){

				/// Assume Generator uses input similar to output (File or Object)
				final int inputActions = this.actions.value & (Actions.MEMORY | Actions.FILE);

				Map<String,Task> tasks = executeMany(this.inputs, inputActions, null, this.log);

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
				}


				/// MAIN
				this.log.note("Running Generator: " + this.info.PRODUCT_ID);
				if (this.log.logFile == null){
					this.log.setLogFile(cacheRoot.resolve(this.relativeLogPath));
				}
				this.log.warn(String.format("Directing log to file: ", this.log.logFile));

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
					// this.info.getFilename()
					this.log.debug(String.format("OK, generator produced tmp file: %s", this.outputPathTmp));
					// this.move(fileTmp, fileFinal);
					try {
						this.move(this.outputPathTmp, this.outputPath);
					} catch (IOException e) {
						this.log.warn(e.toString());
						log.log(HttpServletResponse.SC_FORBIDDEN, String.format("Failed in moving tmp file: %s", this.outputPathTmp));
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
			else {
				this.log.note("Input list: requested");
				this.result = this.inputs;
			}


			if (this.actions.isSet(Actions.FILE)){

				if (fileFinal.length() > 0) {

					this.result = this.outputPath;
					this.log.ok(String.format("Exists: %s (%d bytes)", this.result, fileFinal.length() ));

					if (this.actions.involves(Actions.LATEST|Actions.SHORTCUT)){

						try {

							Path dir = FileUtils.ensureDir(cacheRoot, productDir, dirPerms);
							// Path dir = ShellUtils.makeWritableDir(cacheRoot, productDir);
							// ensureWritableDir(cacheRoot, productDir);

							if (this.actions.isSet(Actions.LATEST)){
								this.link(this.outputPath, dir.resolve(this.info.getFilename("LATEST")));
							}

							if (this.actions.isSet(Actions.SHORTCUT)){
								this.link(this.outputPath, dir);
							}

						}
						catch (IOException e) {
							log.log(HttpServletResponse.SC_FORBIDDEN,e.getMessage());
						}

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
							this.link(this.outputPath, path); // Paths.get(path)
						} catch (IOException e) {
							this.log.log(HttpServletResponse.SC_FORBIDDEN, String.format("Linking failed: %s", path));
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

					return; // true;
				}
				else {
					// TODO: save file (of JavaGenerator)
					// this.log.error(String.format("Failed in generating: %s ", this.outputPath));
					this.log.log(HttpServletResponse.SC_CONFLICT, String.format("Failed in generating: %s ", this.outputPath));
					try {
						this.delete(this.outputPath);
					} catch (IOException e) {
						//this.log.error(String.format("Failed in deleting: %s ", this.outputPath));
						this.log.log(HttpServletResponse.SC_FORBIDDEN, String.format("Failed in deleting: %s ", this.outputPath));
						this.log.log(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
					}
					return;
				}
			}
			else {
				if (this.result != null)
					this.log.info("Result: " + this.result.toString());
				this.log.note("Task completed: actions=" + this.actions);
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
	public Map<String,Task> executeMany(Map<String,String> taskRequests, int actions, Map directives, HttpLog log) {
		return executeMany(taskRequests, new Actions(actions), directives, log);
	}

	public Map<String,Task> executeMany(Map<String,String> taskRequests, Actions actions, Map directives, HttpLog log){

		Map<String,Task> tasks = new HashMap<>();

		final int count = taskRequests.size();

		if (count == 0){
			//log.info(String.format("Inits (%d) tasks "));
			return tasks;
		}

		log.info(String.format("Inits (%d) tasks ", count));

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
			try {

				//HttpLog subLog = (count==1) ? log.child(key) : null;
				HttpLog subLog = log.child(key);

				Task task = new Task(value, actions.value, subLog);

				if (directives != null)
					task.directives.putAll(directives);

				if (actions.copies != null)
					task.actions.copies.addAll(actions.copies);

				if (actions.links != null)
					task.actions.links.addAll(actions.links);


				//if (log)
				task.log.verbosity = log.verbosity;
				tasks.put(key, task);
				log.debug(String.format("Starting thread: %s(%s)[%d]", key, task.info.PRODUCT_ID, task.getId()));
				if (task.log.logFile != null)
					log.info(String.format("See separate log: %s",  task.log.logFile));
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

		log.note("Started (" + tasks.size() + ") tasks... ");
		//wait();

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
			log.log(task.log.indexedException);
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

		final long fileLength = file.length();

		if (fileLength > 0) {
			//log.note("File found");
			log.log(HttpServletResponse.SC_OK, String.format("File found: %s ( bytes)", file.getName(), fileLength));
			return true;
		}
		else { // empty file
			long ageSec = (java.lang.System.currentTimeMillis() - file.lastModified()) / 1000;
			if (ageSec > maxEmptySec){
				log.note("Outdated empty file, age=" + (ageSec/60) + " min, (max " + maxEmptySec + "s)");
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
			}
			return false;
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
		System.err.println("    --conf <file> : read configuration file");
		System.err.println("    --actions <string> : main operation: " + String.join(",", Flags.getKeys(Actions.class)));
		System.err.println("      (all the actions can be also supplied invidually: --make --delete --generate ... )");
		System.err.println("    --copy <target>: copy file to target");
		System.err.println("    --link <target>: link file to target");
		System.err.println("    --directives <key>=<value>|<key>=<value>|... : instructions (pipe-separated) for product generator");
		System.err.println();
		System.err.println("Examples: ");
		System.err.println("    java -cp $NUTLET_PATH 201012161615_test.ppmforge_DIMENSION=2.5.png");

		//System.err.println(HttpLog.messages);

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
		server.log.printStream = System.err;  // System.out  Note: testing STREAM action needs clean stdout
		server.log.setVerbosity(Log.DEBUG);
		server.timeOut = 20;

		HttpLog log = server.log; //.child("CmdLine");

		String confFile = null;

		Map<String,String> products = new HashMap<>();
		Actions actions = new Actions();
		Map<String ,String> directives = new HashMap();

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
						log.verbosity = Log.DEBUG;
						continue;
					}

					if (opt.equals("log_level")) {
						arg = args[++i];
						try {
							int level = Integer.parseInt(arg);
							log.setVerbosity(level);
						} catch (NumberFormatException e) {
							try {
								Field field = Log.class.getField(arg);
								log.verbosity = field.getInt(null);
							} catch (NoSuchFieldException e2) {
								log.note("Use following keys or values (0...10)");
								for (Field field : Log.class.getFields()) {
									String name = field.getName();
									if (name.equals(name.toUpperCase())) {
										//Integer value = field.getInt(null);
										//if (value != null)
										//	log.note(name + "=" + field.getInt(null));
										try {
											log.note(name + "=" + field.get(null));
										}
										catch (Exception e1){ // VT100 boolean
											//log.debug(field.getType().toString());
											//log.debug("(" + field.toString() + ")");
										}
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
						server.readConfig(confFile);
						continue; // Note
					}

					// Now, read conf file if not read this far.
					if (confFile == null){
						confFile = "nutshell.cnf";
						server.readConfig(confFile);
					}


					if (opt.equals("product")) {
						products.put("product", args[++i]);
					}
					else if (opt.equals("copy")) {
						actions.addCopy(args[++i]);
					}
					else if (opt.equals("link")) {
						actions.addLink(args[++i]);
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
			e.printStackTrace(log.printStream);
			System.exit(1);
		}

		if (actions.isEmpty()){
			actions.set(Actions.MAKE);
		}

		log.note("Actions: " + actions);
		log.note(String.format("   COPY(%d): %s", actions.copies.size(), actions.copies));
		log.note(String.format("   LINK(%d): %s", actions.links.size(),  actions.links));

		log.note("Directives: " + directives);
		//System.out.println(directives);


		/// "MAIN"

		int result = 0;

		Map<String,ProductServer.Task> tasks = server.executeMany(products, actions, directives, log);

		//log.note(String.format("Waiting for (%d) tasks to complete... ", tasks.size()));

		for (Entry<String,Task> entry: tasks.entrySet()) {
			String key = entry.getKey();
			Task  task = entry.getValue();
			if (task.log.indexedException.index >= HttpServletResponse.SC_BAD_REQUEST){
				//log.warn("Problem(s): ");
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
				log.info("Log:\t"  + task.log.logFile.getAbsolutePath());
			if (task.outputPath.toFile().exists())
				log.note(String.format("File exists:\t %s (%d bytes)", task.outputPath.toString(), task.outputPath.toFile().length()));
		}

		System.exit(result);

	}



}


