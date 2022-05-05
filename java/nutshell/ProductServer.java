package nutshell;

import sun.misc.Signal;

//import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.*;
import java.text.ParseException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

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
public class ProductServer extends ProductServerBase { //extends Cache {

	ProductServer() {
		//serverLog = new HttpLog(getClass().getSimpleName());
	}

	/// Experimental log of products and their inputs(s)
	//public final Map<String,Map<String,String>> statistics = new HashMap<>();

	/// Experimental: Change MAKE to GENERATE if positive, decrement for each input
	// TODO: consider general query depth (for inputs etc)
	public int defaultRemakeDepth = 0;

	//public GroupPrincipal fileGroupID;
	//Files.readAttributes(originalFile.toPath(), PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS).group();

	/// System side settings.

	@Override
	public String toString() {
		return MapUtils.getMap(this).toString() + "\n" + setup.toString();
	}

	/**
	 * A "tray" containing both the product query info and the resulting object if successfully queried.
	 * Task does not know about the generator. Notice GENERATE and getParamEnv.
	 */
	public class Task extends Thread {

		final HttpLog log;
		final ProductInfo info;

		final public int id;

		public int getTaskId() {
			return id;
		}

		/// Checked "normalized" filename, with ordered parameters.
		final public String filename;

		final public Instructions instructions = new Instructions();

		//public int regenerateDepth = 0;
		//public boolean parallel = true;

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
		public final Map<String, Object> retrievedInputs = new HashMap<>();

		public Graph graph = null;


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
		public Task(String productStr, int instructions, HttpLog parentLog) throws ParseException {

			// final String[] productDef = [productInfo, directives]
			// in LOG // this.creationTime = System.currentTimeMillis();
			this.id = getProcessId();

			this.info = new ProductInfo(productStr);
			this.log = new HttpLog("[" + this.info.PRODUCT_ID + "]", parentLog);

			this.filename = this.info.getFilename();
			this.instructions.set(instructions);
			this.instructions.regenerateDepth = defaultRemakeDepth;

			// Relative
			this.productDir = getProductDir(this.info.PRODUCT_ID);
			this.timeStampDir = getTimestampDir(this.info.time);
			this.relativeOutputDir = this.timeStampDir.resolve(this.productDir);
			this.relativeOutputDirTmp = this.timeStampDir.resolve(this.productDir).resolve("tmp" + getTaskId());
			this.relativeOutputPath = relativeOutputDir.resolve(filename);

			//this.relativeLogPath    = relativeOutputDir.resolve(getFilePrefix() + filename + "." + getTaskId() + ".log");
			this.relativeSystemDir = this.timeStampDir.resolve("nutshell").resolve(this.productDir);
			String systemBaseName = this.info.TIMESTAMP + "_nutshell." + this.info.PRODUCT_ID + "_" + getTaskId();
			this.relativeLogPath = relativeOutputDir.resolve(filename + "." + getTaskId() + ".log");
			this.relativeGraphPath = relativeSystemDir.resolve(systemBaseName + ".svg");

			// Absolute
			this.outputDir = cacheRoot.resolve(this.relativeOutputDir);
			this.outputDirTmp = cacheRoot.resolve(this.relativeOutputDirTmp);
			this.outputPath = outputDir.resolve(filename);
			//this.outputPathTmp = outputDirTmp.resolve(getFilePrefix() + filename);
			this.outputPathTmp = outputDirTmp.resolve(filename);
			this.storagePath = storageRoot.resolve(this.relativeOutputDir).resolve(filename);


			try {
				Path logPath = cacheRoot.resolve(relativeLogPath);
				//ensureFile(cacheRoot, this.relativeLogPath);
				FileUtils.ensureWritableFile(logPath, fileGroupID, filePerms, dirPerms);
				log.setLogFile(logPath);
			} catch (IOException e) {
				System.err.println(String.format("Opening Log file (%s) failed: %s", this.relativeLogPath, e));
				//log.setLogFile(null);
			}

			//log.warn("Where am I?");
			log.debug(String.format("Created TASK %s [%d] [%s] %s ", this.filename, this.getTaskId(), this.instructions, this.info.directives)); //  this.toString()
			this.result = null;
		}


		public String getStatus() {
			return (String.format("%s[%d] %s [%s] {%s}", this.getClass().getSimpleName(), this.getTaskId(), this.info, this.instructions, this.info.directives)); //  this.toString()
		}

		// Consider moving these to @ProductServerBase, with Log param.
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


		/**
		 * Runs a thread generating and/or otherwise handling a product
		 *
		 * @see #execute()
		 */
		@Override
		public void run() {

			try {
				Signal.handle(new Signal("INT"), this::handleInterrupt);
				execute();
			} catch (InterruptedException e) {
				//log.status
				log.note(e.toString());
				log.warn("Interrupted");
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

		public Object result;

		public Graph.Node getGraphNode(Graph graph) {
			if (graph.hasNode(info.getID())) {
				return graph.getNode(info.getID());
			} else {
				Graph.Node node = graph.getNode(info.getID());
				node.attributes.put("label", String.format("%s\\n(%s)", node.getName(), info.FORMAT));
				node.attributes.put("fillcolor", "lightblue");
				//node.attributes.put("href", String.format(
				//		"?instructions=GENERATE,STATUS&amp;product=%s", info.getFilename()));
				return node;
			}
		}

		// " MAIN "

		/**
		 * Execute this task: delete, load, generate a product, for example.
		 * <p>
		 * Processing is done inside the parent thread  – by default the main thread.
		 * To invoke this function as a separate thread, use #run().
		 *
		 * @return
		 * @throws InterruptedException // Gene
		 * @see #run()
		 */
		public void execute() throws InterruptedException {

			Generator generator = null;

			//log.log(HttpLog.HttpStatus.ACCEPTED, String.format("Starting %s", this.info.PRODUCT_ID));
			log.log(HttpLog.HttpStatus.ACCEPTED, String.format("Preparing %s", this));

			// Logical corrections

			if (!instructions.copies.isEmpty())
				instructions.add(ActionType.MAKE | ResultType.FILE);

			if (!instructions.links.isEmpty())
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

			log.experimental(String.format("Cache depth: %s", instructions.regenerateDepth));

			if (instructions.involves(ActionType.MAKE | ActionType.GENERATE)
					&& (instructions.regenerateDepth > 0)) {
				log.experimental(String.format("Cache clearance %s > 0, ensuring GENERATE", instructions.regenerateDepth));
				instructions.add(ActionType.GENERATE);
			}
			log.warn(instructions.toString());


			log.log(HttpLog.HttpStatus.ACCEPTED, String.format("Starting %s", this));
			log.log(String.format("Starting: %s ", getStatus())); //  this.toString()


			/// Main DELETE operation
			if (instructions.involves(ActionType.DELETE)) {

				if (instructions.isSet(ResultType.FILE)) {
					try {
						this.delete(outputPath);
					} catch (IOException e) {
						log.log(HttpLog.HttpStatus.CONFLICT, String.format("Failed in deleting file: %s, %s", outputPath, e.getMessage()));
						//return;
					}
				}

				if (instructions.involves(Instructions.GENERATE | Instructions.EXISTS)) {
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
			if (instructions.involves(Instructions.EXISTS | ResultType.FILE) && !instructions.isSet(ActionType.DELETE)) {

				log.debug(String.format("Checking storage path: %s", storagePath));

				if (storagePath.toFile().exists() && !fileFinal.exists()) {
					log.log(HttpLog.HttpStatus.OK, String.format("Stored file exists: %s", this.storagePath));

					try {
						FileUtils.ensureWritableDir(outputDir, fileGroupID, dirPerms);
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
				// Don't wait (so the other process will "notice" ie fail)
				else if (queryFile(fileFinal, 90, log)) {
					// Order? Does it delete immediately?
					result = outputPath;
					log.log(HttpLog.HttpStatus.OK, String.format("File exists: %s", this.outputPath));
				} else { // if (this.instructions.isSet(Actions.EXIST)){
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
			if (instructions.involves(Instructions.GENERATE | Instructions.INPUTLIST | Instructions.STATUS)) {

				log.log(HttpLog.HttpStatus.OK, String.format("Determining generator for : %s", this.info.PRODUCT_ID));
				try {
					generator = getGenerator(this.info.PRODUCT_ID);
					//log.log(HttpLog.HttpStatus.CREATED, String.format("Generator(%s): %s", this.info.PRODUCT_ID, generator));
					log.log(HttpLog.HttpStatus.CREATED, generator.toString());

					if (instructions.involves(Instructions.GENERATE)) {
						// Consider this.addAction() -> log.debug()
						if (generator instanceof ExternalGenerator)
							instructions.add(ResultType.FILE); // PREPARE dir & empty file
						else
							instructions.add(ResultType.MEMORY);
					}

				}
				catch (IndexedState e) {
					log.log(HttpLog.HttpStatus.CONFLICT, "Generator does not exist");
					log.log(e);
					instructions.remove(Instructions.GENERATE);
					instructions.remove(Instructions.INPUTLIST);
					instructions.remove(Instructions.STATUS);
				}

			}

			if (instructions.isSet(ActionType.DELETE) && !instructions.involves(ActionType.MAKE)) {

				if (fileFinal.exists()) {
					log.log(HttpLog.HttpStatus.CONFLICT, String.format("Failed in deleting: %s ", this.outputPath));
				} else {
					log.log(HttpLog.HttpStatus.NO_CONTENT, String.format("Deleted: %s ", this.outputPath));
				}

				if (!(instructions.involves(Instructions.GENERATE | Instructions.EXISTS))) {
					return;
				}
			}


			// Generate or at least list inputs
			if (instructions.involves(Instructions.INPUTLIST | Instructions.GENERATE)) { //

				log.debug(String.format("Determining input list for: %s", this.info.PRODUCT_ID));

				inputs.clear(); // needed?

				try {
					inputs.putAll(generator.getInputList(this));
					log.debug(this.inputs.toString());
					//statistics.put(info.PRODUCT_ID, inputs.keySet());
					//System.err.println("## " + this.inputs);
				} catch (Exception e) {
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
				log.debug("Ok, generate.");

				// Mark this task being processed (empty file)
				// if (this.instructions.isSet(ResultType.FILE) && !fileFinal.exists()){
				try {
					FileUtils.ensureWritableDir(outputDirTmp, fileGroupID, dirPerms);
					FileUtils.ensureWritableDir(outputDir, fileGroupID, dirPerms);
					//ensureDir(cacheRoot, relativeOutputDirTmp);
					//ensureDir(cacheRoot, relativeOutputDir);
					FileUtils.ensureWritableFile(outputPath, fileGroupID, filePerms, dirPerms);
					// ensureFile(cacheRoot, relativeOutputPath);
					// Path genLogPath =  ensureWritableFile(cacheRoot, relativeOutputDirTmp.resolve(filename+".GEN.log"));
				} catch (IOException e) {
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

				if (instructions.regenerateDepth > 0) {
					//instructions.add(ActionType.GENERATE);
					inputInstructions.regenerateDepth = instructions.regenerateDepth - 1;
				} else {
					inputInstructions.regenerateDepth = 0;
				}


				if (!inputs.isEmpty()) {
					log.experimental(String.format("Input retrieval depth: %d",
							inputInstructions.regenerateDepth));

					log.special(String.format("Input instructions: %s", inputInstructions));
				}

				// Consider forwarding directives?
				//Map<String,Task> inputTasks = executeBatch(this.inputs, inputInstructions, null, log);

				Map<String, Task> inputTasks = prepareTasks(this.inputs, inputInstructions, null, log);

				// Statistics
				Graph.Node node = null;
				if (graph != null) {
					node = getGraphNode(graph);
					for (Entry<String, Task> entry : inputTasks.entrySet()) {
						entry.getValue().graph = graph;
					}
					//node.attributes.put("fillcolor", "lightblue");
				}

				runTasks(inputTasks, log);

				for (Entry<String, Task> entry : inputTasks.entrySet()) {
					String key = entry.getKey();
					Task inputTask = entry.getValue();

					Graph.Node inputNode = null;
					Graph.Link link = null;

					if (graph != null) {
						inputNode = inputTask.getGraphNode(graph);
						inputNode.attributes.put("fillcolor", "#40ff40"); // ""lightgreen");
						inputNode.attributes.put("href", String.format(
								"?instructions=GENERATE,STATUS&amp;product=%s",
								inputTask.info.getFilename()));
						inputNode.attributes.put("class", "clickable");

						link = graph.addLink(node, inputNode);
						// link.attributes.put("label", key);
						// link.attributes.put("title", "avain_"+key); // SVG only
					}


					if (inputTask.result != null) {
						String r = inputTask.result.toString();
						log.note(String.format("Retrieved: %s = %s", key, r));
						this.retrievedInputs.put(key, r);
						// inputStats.put(key, inputTask.info.getID());

						if (link != null) {
							if (inputTask.log.indexedState.index > 300) {
								link.attributes.put("style", "dashed");
							}
						}
						if (inputTask.log.indexedState.index > 300) {
							log.warn("Errors in input generation: " + inputTask.log.indexedState.getMessage());
						}
					} else {
						log.warn(inputTask.log.indexedState.getMessage());
						log.log(HttpLog.HttpStatus.PRECONDITION_FAILED, String.format("Retrieval failed: %s=%s", key, inputTask));
						log.reset(); // Forget that anyway...
						if (link != null) {
							// inputNode.attributes.put("color", "red");
							//inputNode.attributes.put("style", "dashed");
							//inputNode.attributes.put("fillcolor", "#ffb080"); // ""orange");"#ffb080"
							inputNode.attributes.put("fillcolor", "#ffc090"); // ""orange");"#ffb080"
							link.attributes.put("color", "#800000");
							link.attributes.put("style", "dashed");
						}
					}
					inputTask.log.close(); // close PrintStream
				}

				/*
				if (collectStatistics) {
					statistics.put(info.getID(), inputStats);
				}
				 */

				/// MAIN
				log.note("Running Generator: " + info.PRODUCT_ID);

				File fileTmp = outputPathTmp.toFile();

				try {
					generator.generate(this);
				} catch (IndexedState e) {

					// NOTE: possibly the file has been generated, but with some less significant errors.
					// serverLog.warn(info.getFilename() + ": " + e);
					// serverLog.warn(info.getFilename());

					log.log(e);

					try {
						log.warn("Error was: " + e.getMessage());
						this.delete(this.outputPathTmp);
						//this.delete(fileFinal);
					} catch (Exception e2) {
						log.error(e2.getLocalizedMessage());
					}
					//log.error(e.getMessage());
					//throw e;
					//return false;
				}


				if (fileTmp.length() > 0) {

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
						// Todo: skip this if already ok...
						Files.setPosixFilePermissions(this.outputPath, filePerms);
					} catch (IOException e) {
						log.warn(e.toString());
						log.warn(String.format("Failed in setting perms %s for file: %s", filePerms, this.outputPath));
						// log.warn(String.format("filePerms: %s", filePerms));
						// log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Failed in setting perms for file: %s", this.outputPath));
						// log.error(String.format("Failed in moving tmp file: %s", this.outputPath));
					}


					// this.result = this.outputPath;
				} else {
					log.log(HttpLog.HttpStatus.CONFLICT, String.format("Generator failed in producing the file: %s", this.outputPath));
					serverLog.fail(info.getFilename());
					// log.error("Generator failed in producing tmp file: " + fileTmp.getName());
					try {
						log.warn(String.format("Failed in generating, so deleting: %s", info));
						this.delete(this.outputPathTmp);
					} catch (Exception e) {
						/// TODO: is it a fatal error if a product defines its input wrong?
						log.error(e.getMessage()); // RETURN?
						log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Failed in deleting tmp file: %s", this.outputPath));
					}
				}

			}

			if (instructions.isSet(ActionType.INPUTLIST) && !instructions.involves(ActionType.GENERATE)) {
				log.note("Input list: requested");
				result = inputs;
			}


			if (instructions.isSet(ResultType.FILE)) {
				// TODO: save file (of JavaGenerator)
				// if (this.result instanceof Path)...

				if (this.instructions.isSet(ActionType.DELETE)) {
					if (fileFinal.exists())
						log.log(HttpLog.HttpStatus.FORBIDDEN, String.format("Deleting failed: %s (%d bytes)", fileFinal, fileFinal.length()));
					else {
						log.reset();
						log.ok("Deleted.");
					}
				} else if (fileFinal.length() == 0) {

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
								Path dir = cacheRoot.resolve(productDir);
								FileUtils.ensureWritableDir(dir, fileGroupID, dirPerms);
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
								Path dir = cacheRoot.resolve(productDir);
								FileUtils.ensureWritableDir(dir, fileGroupID, dirPerms);
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
						Path storageDir = storageRoot.resolve(relativeOutputDir);
						Path storedFile = storageDir.resolve(this.info.getFilename());
						if (Files.exists(storedFile)) {
							log.experimental(String.format("Store: file exists already: %s", storedFile));
						} else {
							try {
								//ensureDir(storageRoot, relativeOutputDir);
								FileUtils.ensureWritableDir(storageDir, fileGroupID, dirPerms);
								this.copy(this.outputPath, storedFile);
								log.experimental(String.format("Stored in: %s", storedFile));
							} catch (IOException e) {
								log.log(HttpLog.HttpStatus.FORBIDDEN, e.getMessage());
								log.reset();
								log.fail(String.format("Store: %s", storedFile));
							}
						}
					}


					for (Path path : this.instructions.copies) {
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

					for (Path path : this.instructions.links) {
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
			} else {
				if (this.result != null) // Object?
					log.info("Result: " + this.result.toString());
				log.note("Task completed: instructions=" + this.instructions);
				// status page?

			}

			if (instructions.isSet(Instructions.RUN)) {
				ShellExec.OutputReader reader = new ShellExec.OutputReader(log.getPrintStream());
				//ShellExec shellExec = new ShellExec(Paths.get("run.sh"), this.productDir);
				ShellExec.exec("./run.sh", null, productRoot.resolve(productDir), reader);

			}

			// return true; // SEMANTICS?
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

			if (!cmdPath.isEmpty()) {
				env.put("PATH", cmdPath);
				log.special("PATH=" + cmdPath);
			}

			if (!this.retrievedInputs.isEmpty()) {
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

		public Graph addGraph(String name){
			this.graph = new Graph(name);
			Graph.Node nodeDef = graph.addNode("node");
			nodeDef.attributes.put("shape", "ellipse");
			nodeDef.attributes.put("style", "filled");
			return this.graph;
		}

		public Path writeGraph() {
			Path dotFile = cacheRoot.resolve(relativeGraphPath);
			log.special(String.format("Writing graph to file: %s", dotFile));
			try {
				Path graphDir = dotFile.getParent();
				FileUtils.ensureWritableDir(graphDir, fileGroupID, dirPerms);
				// ensureDir(cacheRoot, relativeSystemDir);
				// ensureFile(cacheRoot, relativeGraphPath);
				graph.dotToFile(dotFile.toString());
				Files.setPosixFilePermissions(dotFile, filePerms);
			} catch (IOException | InterruptedException e) {
				log.warn(e.getMessage());
				graph.toStream(log.getPrintStream());
				log.fail(String.format("Failed in writing graph to file: %s", dotFile));
			}
			return dotFile;
		}


	}  // Task


	public interface Generator {

		/**
		 *
		 */
		void generate(Task task) throws IndexedState; // throws IOException, InterruptedException ;

		// Semantics? (List or retrieved objects)
		// boolean hasInputs();

		/**
		 * Declare inputs required for this product generation task.
		 *
		 * @param task
		 * @return
		 */
		Map<String, String> getInputList(Task task) throws IndexedState;

	}

	/** Searches for shell side (and later, Java) generators
	 *
	 * @param productID
	 * @return
	 */
	public Generator getGenerator(String productID) throws IndexedState {
		Path dir = productRoot.resolve(getProductDir(productID));
		Generator generator = new ExternalGenerator(productID, dir.toString());
		return generator;
	};

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
	public Map<String,Task> executeBatch(Map<String,String> taskRequests, Instructions instructions, Map directives, HttpLog log) {
		Map<String, Task> tasks = prepareTasks(taskRequests, instructions, directives, log);
		runTasks(tasks, log);
		return tasks;
	}
	 */

	/**
	 *
	 * @param batchConfig
	 * @param log
	 * @return
	 */
	public Map<String,Task> prepareTasks(BatchConfig batchConfig, HttpLog log) {
		return prepareTasks(batchConfig.products, batchConfig.instructions, batchConfig.directives, log);
	}

	public Map<String,Task> prepareTasks(Map<String,String> productRequests, Instructions instructions, Map directives, HttpLog log) {

		Map<String, Task> tasks = new HashMap<>();

		final int count = productRequests.size();

		if (count == 0) {
			//log.info(String.format("Inits (%d) tasks "));
			return tasks;
		}

		log.info(String.format("Inits (%d) tasks: (%s)", count, productRequests.keySet()));

		//log.info(String.format("Inits (%s) tasks ", productRequests.entrySet()));
		log.info(String.format("Instructions: %s , regenerateDepth: %d ",
				instructions, instructions.regenerateDepth));

		/// Check COPY & LINK targets (must be directories, if several tasks)
		if (count > 1) {

			for (Path p : instructions.copies) {
				if (!p.toFile().isDirectory()) {
					log.warn(String.format("Several tasks (%d), but single COPY target: %s", count, p));
				}
			}

			for (Path p : instructions.links) {
				if (!p.toFile().isDirectory()) {
					log.warn(String.format("Several tasks (%d), but single LINK target: %s", count, p));
				}
			}

		}


		for (Entry<String, String> entry : productRequests.entrySet()) {

			String key = entry.getKey();
			String value = entry.getValue();
			// log.debug(String.format("Starting: %s = %s", key, value));

			try {

				Task task = new Task(value, instructions.value, log);

				//log.debug(String.format("Check: %s = %s", key, value));
				task.info.setDirectives(directives);
				task.instructions.addCopies(instructions.copies);
				task.instructions.addLinks(instructions.links);
				task.instructions.addMove(instructions.move); // Thread-safe?
				task.instructions.regenerateDepth = instructions.regenerateDepth;
				if (task.instructions.regenerateDepth > 0){
					task.instructions.add(ActionType.MAKE);
				}

				log.info(String.format("Prepared task: '%s' = %s [%s]", key, task, task.instructions));
				if ((directives != null) && !directives.isEmpty())
					log.info(String.format("Directives: %s = %s", key, directives));

				task.log.setVerbosity(log.getVerbosity());
				task.log.COLOURS = log.COLOURS;
				if (task.log.logFile != null) {
					log.note(String.format("Log for '%s': %s", key, task.log.logFile));
				}

				tasks.put(key, task);

			} catch (ParseException e) {
				//System.err.println(String.format("EROR here: %s = %s", key, value));

				// TODO: is it a fatal error if a product defines its input wrong? Answer: YES
				log.warn(String.format("Could not parse product: %s(%s)", key, value));
				//log.warn(e.getMessage());
				log.log(HttpLog.HttpStatus.NOT_ACCEPTABLE, e.getLocalizedMessage());
				//log.error(e.getLocalizedMessage()); // RETURN?
			} catch (Exception e) {
				// System.err.println(String.format("EROR2 here: %s = %s", key, value));
				log.error(String.format("Unexpected exception in creating product %s(%s)", key, value));
				log.log(HttpLog.HttpStatus.INTERNAL_SERVER_ERROR, e.getLocalizedMessage());
			}
			finally {
				//System.out.println("Final.." + key);
			}

		}
		return tasks;
	}

	public Map<String,Task> runTasks(Map<String,Task> tasks, HttpLog log){

		if (tasks.isEmpty()){
			//log.debug("No subtasks");
			return tasks;
		}

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
			log.info(String.format("Final status: %s", task.log.indexedState));
			//log.log("test");
		}

		return tasks;
	}


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


	public enum OutputFormat {
		TEXT,
		VT100,
		HTML;
	}

	/*
	public void help(){

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

		System.err.println("Options: ");
		System.err.println();

	}

	 */

	class InstructionParameter extends Parameter.Simple<String> {

		InstructionParameter(Instructions instructions){
			super("instructions", "Set actions and properties: " +
							String.join(",", Flags.getKeys(Instructions.class)),
					instructions.toString());
			myInstructions = instructions;
		}

		Instructions myInstructions = null;

		InstructionParameter(Instructions instructions, String fieldName){
			super(fieldName.toLowerCase(),
					String.format("Same as --instructions %s", fieldName),
					fieldName.toUpperCase());
			setReference(this, "");
			//value = fieldName.toUpperCase();
			myInstructions = instructions;
			//System.err.print("Added: ");
			// System.err.println(this);
		}

		@Override
		public void exec() {
			try {
				myInstructions.add(value);
				/*
				if (hasParams()){
					// Definite?  "--instructions A,B,C"
					myInstructions.set(value);
				}
				else {
					// Simple keys like "--generate"
					myInstructions.add(value);
				}
				 */
			} catch (NoSuchFieldException e) {
				serverLog.error(e.getMessage());
				//e.printStackTrace();
			} catch (IllegalAccessException e) {
				serverLog.error(e.getMessage());
				//e.printStackTrace();
			}
		}
	}


	static
	class BatchConfig {

		public Map<String,String> products = new TreeMap<>();
		public Instructions instructions = new Instructions();
		public Map<String ,String> directives = new TreeMap<>();

	}

	public void populate(BatchConfig batchConfig, ProgramRegistry registry){

		registry.add(new ProgramUtils.Help(registry));
		registry.add(new ProgramUtils.LogLevel(serverLog));
		registry.add(new ProgramUtils.LogLevel.Debug(serverLog));
		registry.add(new ProgramUtils.LogLevel.Verbose(serverLog));


		registry.add(new Parameter.Simple<String>("conf","Read configuration", "") {
			/// It is recommended to give --conf among the first options, unless default used.
			@Override
			public void exec() { readConfig(value);}
		});

		registry.add(new Parameter.Simple<String>("log_style","Set formatting",
				OutputFormat.TEXT.toString()){

			@Override
			public void exec() {
				//serverLog.special("Value...");
				//serverLog.warn(value);
				OutputFormat f = OutputFormat.valueOf(value);
				serverLog.special(f.toString());
				switch (f){
					case TEXT:
						serverLog.COLOURS = false;
						break;
					case VT100:
						serverLog.COLOURS = true;
						break;
					case HTML:
					default:
						serverLog.warn(String.format("%s: Not implemented: %s", this.getName(), f));
				}
			}
		});


		registry.add(new InstructionParameter(batchConfig.instructions));
		for (String instr: Flags.getKeys(Instructions.class)){ // consider instant .getClass()
			registry.add(instr.toLowerCase(), new InstructionParameter(batchConfig.instructions, instr));
		}


		registry.add(new Parameter.Simple<String>("directives","Set application properties",
				""){
			@Override
			public void exec() {
				// System.err.print(String.format("Type: %s %s", value.getClass(), value));
				MapUtils.setEntries(value,"\\|", "true", batchConfig.directives);
			}
		});

		registry.add(new Parameter.Simple<String>("product","Set product filename (repeatable)",
				""){
			@Override
			public void exec() {
				batchConfig.products.put("product", value);
			}
		});

		registry.add(new Parameter<Instructions>("regenerate",
				"Cache clearance depth (0=MAKE, 1=GENERATE, N...: remake inputs)",
				batchConfig.instructions, "regenerateDepth"));

		registry.add(new Parameter.Single("regenerate2",
				"Cache clearance depth (0=MAKE, 1=GENERATE, N...: remake inputs)",
				"depth"){

			public int depth;

			@Override
			public void exec() {
				/*
				if (value.intValue() < 0){
					serverLog.warn(String.format("Negative cache clearance depth %d, setting to zero.", value.intValue()));
					value = 0;
				}

				 */
				//serverLog.warn(String.format("Nyt on paha! %d <= %s",
				//		batchConfig.instructions.regenerateDepth, depth));
				//serverLog.warn(value.toString());
				batchConfig.instructions.regenerateDepth = depth;
				/*
				if (depth != null) {
					System.err.println("ASETETAAN");
					System.err.println(depth);
					//serverLog.warn(value.toString());
					for (TypeVariable tv: this.getClass().getTypeParameters()){
						System.err.println(tv);
					}
					// batchConfig.instructions.regenerateDepth = Integer.parseInt(value.toString()); //Integer)value;
				}
				else {
					System.err.println("EI ASETETA");
					batchConfig.instructions.regenerateDepth = 0;
				}
				 */
			}
		});



	}

	/// Command-line interface for 
	public static void main(String[] args) {

		final ProductServer server = new ProductServer();
		server.serverLog.setVerbosity(Log.Status.DEBUG);
		server.timeOut = 20;

		/*
		if (args.length == 0){
			server.help();  // An instance, yes. Default values may habve been changed.
			return;
		}

		 */

		HttpLog log = server.serverLog; //.child("CmdLine");
		log.COLOURS = true;

		/*
		Map<String,String> context_products = new TreeMap<>();
		Instructions context_instructions = new Instructions();
		Map<String ,String> context_directives = new TreeMap<>();
		*/

		// NEW
		ProgramRegistry registry = new ProgramRegistry();

		BatchConfig batchConfig = new BatchConfig();
		server.populate(batchConfig, registry);

		registry.add(new ProgramUtils.Version<>(server));

		registry.add(new Parameter.Single("parse",
				"Debugging (cmd line only): parse product.", "filename"){

			public String filename = "";

			@Override
			public void exec() {
				try {
					Task product = server.new Task(filename, 0, log);
					Map<String,Object> map = product.getParamEnv();
					String[] array = MapUtils.toArray(map);
					for (String s : array) {
						System.out.println(s);
					}
				} catch (ParseException e) {
					log.fail(e.getMessage());
				}
			}

		});


		registry.add(new Parameter("http_params","Debugging/testing: compose HTTP GET params."){

			@Override
			public void exec() {
				String[] p = batchConfig.products.values().toArray(new String[0]);
				if (batchConfig.products.isEmpty()){
					log.error("Product not defined yet, try: [--product] <FILE>"  + this.getName());
				}
				else {

					if (batchConfig.instructions.isEmpty())
						batchConfig.instructions.add(ActionType.MAKE);

					if (batchConfig.products.size() > 1){
						log.warn("Several products defined, using last");
					}

					for (Map.Entry entry: batchConfig.products.entrySet()) {
						try {
							Task task = server.new Task(entry.getValue().toString(), 0, log);
							System.out.println(String.format("instructions=%s&product=%s", batchConfig.instructions, task.info.getFilename()));
						} catch (ParseException e) {
							log.warn(entry.getValue().toString());
							log.error(e.getMessage());
						}
					}
				}
			}
		});




		/// Command line only
		registry.add(new Parameter.Simple<String>("copy",
				"Copy generated product(s) to file (dir). Repeatable.",
				""){
			@Override
			public void exec() {
				batchConfig.instructions.addCopy(value);
			}
		});

		registry.add(new Parameter.Simple<String>("link",
				"Copy generated product/products to file/dir. Repeatable.",
				""){
			@Override
			public void exec() {
				batchConfig.instructions.addLink(value);
			}
		});

		registry.add(new Parameter.Simple<String>("move",
				"Move generated product/products to file/dir. Repeatable",
				""){
			@Override
			public void exec() {
				batchConfig.instructions.addMove(value);
			}
		});

		registry.add(new Parameter.Simple<String>("path","Extend PATH variable for product generation",
				""){
			public void exec() {
				server.cmdPath = value;
				server.setup.put("cmdPath", server.cmdPath); // consider PARASITER...
			}
		});


		//Field[] instructionFields = Instructions.class.getFields();
		if (args.length == 0){
			args = new String[]{"--help"};
		}


		try {
			for (int i = 0; i < args.length; i++) {

				String arg = args[i];
				//log.info(String.format("ARGS[%d]: %s", i, arg));

				if (arg.charAt(0) == '-') {

					if (arg.equals("-h")){
						arg = "--help";
					}
					else if (arg.charAt(1) != '-') {
						throw new IllegalArgumentException("Short options (-x) not supported in this Java version");
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
						log.debug(String.format("Handled: %s", param));
						//log.special(param.toString());
						continue;
					}
					else {
						log.error(String.format("Unknown argument: %s", arg));
						System.exit(-1);
					}

				}
				else {  // Argument does not start with "--"
					batchConfig.products.put("product" + (batchConfig.products.size()+1), arg);
				}

			}
		}
		catch (Exception e) {
			log.error(String.format("Unhandled exception: %s", e));
			//e.printStackTrace(log.printStream);
			System.exit(1);
		}

		if (batchConfig.instructions.isEmpty()){
			batchConfig.instructions.set(ActionType.MAKE);
		}

		int result = 0;

		log.note("Instructions: " + batchConfig.instructions);

		if (batchConfig.instructions.isSet(ActionType.CLEAR_CACHE)) {
			log.warn("Clearing cache");
			if (batchConfig.instructions.value != ActionType.CLEAR_CACHE){
				batchConfig.instructions.remove(ActionType.CLEAR_CACHE);
				log.warn(String.format("Discarding remaining context.instructions: %s", batchConfig.instructions) );
			}

			try {
				server.clearCache(true);
			} catch (IOException e) {
				log.log(HttpLog.HttpStatus.CONFLICT, "Clearing cache failed");
				result = 4;
			}
			System.exit(result);
		}

		// Turhia/väärässä paikassa (tässä)
		if (!batchConfig.instructions.copies.isEmpty())
			log.note(String.format("   COPY(%d):\t %s", batchConfig.instructions.copies.size(), batchConfig.instructions.copies));
		if (!batchConfig.instructions.links.isEmpty())
			log.note(String.format("   LINK(%d):\t %s", batchConfig.instructions.links.size(),  batchConfig.instructions.links));
		if (batchConfig.instructions.move != null)
			log.note(String.format("   MOVE: \t %s", batchConfig.instructions.move));

		if (!batchConfig.directives.isEmpty())
			log.note("Directives: " + batchConfig.directives);

		/// "MAIN"
		//Map<String,ProductServer.Task> tasks = server.executeMany(batchConfig.products, batchConfig.instructions, batchConfig.directives, log);
		// Map<String,ProductServer.Task> tasks = server.executeBatch(batchConfig, log);

		Map<String, Task> tasks = server.prepareTasks(batchConfig, log);

		if (batchConfig.instructions.isSet(ActionType.STATUS)){
			Graph graph = null;
			for (Entry<String,Task> entry : tasks.entrySet()) {
				Task task = entry.getValue();
				if (graph == null){
					graph = task.addGraph("ProductServer");
				}
				task.graph = graph;
			}
		}

		server.runTasks(tasks, log);

		if (log.getStatus() <= Log.Status.ERROR.level){
			log.warn("Errors: %d " + log.getStatus());
			++result;
		}

		for (Entry<String,Task> entry: tasks.entrySet()) {

			String key = entry.getKey();
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

			/*
			// log.info(String.format("status: %s %d", task.info.PRODUCT_ID ,task.log.status) );
			if (task.log.logFile != null) {
				log.info(String.format("Log:\t %s", task.log.logFile.getAbsolutePath()));
			}

			if (task.result != null) {
				String s = String.format("Result [%s]: %s", task.result.getClass(), task.result);
				log.info(s);
				//log.info("Log:\t"  + task.log.logFile.getAbsolutePath());
				task.log.info(s);
			}

			 */

			if (task.outputPath.toFile().exists())
				log.ok(String.format("File exists:\t %s (%d bytes)", task.outputPath.toString(), task.outputPath.toFile().length()));
				//log.note(String.format("File exists:\t %s (%d bytes)", task.outputPath.toString(), task.outputPath.toFile().length()));

			if (task.instructions.isSet(ActionType.INPUTLIST)){

				for (Map.Entry<String,String> ec: task.inputs.entrySet()) {
					System.out.println(String.format("%s:\t %s", ec.getKey(), ec.getValue()));
				}

			}

			if (task.instructions.isSet(ActionType.STATUS) && (task.graph != null)){
				task.writeGraph();
			}

			/*
			if (server.collectStatistics){
				Path dotFile = server.cacheRoot.resolve(task.relativeGraphPath);
				log.special(String.format("writing %s", dotFile));
				server.graph.dotToFile(dotFile.toString(), "");
			}
			 */

			task.log.close();
		}


		// System.err.println("Eksit");
		log.debug("Exiting..");
		log.close();
		System.exit(result);

	}



}


