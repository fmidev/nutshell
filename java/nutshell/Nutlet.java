package nutshell;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import nutshell.ProductServer.Task;
import org.w3c.dom.Node;


public class Nutlet extends NutWeb { //HttpServlet {

	protected SimpleHtml getHtmlPage(){
		SimpleHtml html = super.getHtmlPage();

		Element elem = html.getUniqueElement(html.body, SimpleHtml.Tag.SPAN, "version");
		elem.setTextContent(String.format("NutLet (%s), Java Version (%s) root=%s template=%s",
				version, getClass().getSimpleName(), HTTP_ROOT, HTML_TEMPLATE));
		return html;
	}


	//static
	public class Tasklet implements SimpleHtml.Nodifiable {

		Tasklet(Task task){
			this.task = task;
		}

		final
		protected Task task;

		@Override
		public Node getNode(Document basedoc) {
			Element elem = basedoc.createElement(SimpleHtml.Tag.A.toString());
			elem.setAttribute("href", String.format("%s/NutShell?product=%s&instructions=MAKE,STATUS",
					productServer.HTTP_BASE, task.filename)); // , task.instructions
			elem.setAttribute("target", "_new");
			elem.setTextContent(task.toString());
			return elem;
		}
	}

	private static final long serialVersionUID;
	static {
		serialVersionUID = 1293000393642243650L;
	}

	static final public String version = "1.7.2";

	String confDir = "";


	//final Map<String,Object> setup;
	final ProductServer productServer;
	//final GregorianCalendar startTime;

	static
	final public Map<Integer, Tasklet> taskMap = new HashMap<>();

	/**
	 * param arg Input
	 *
	 */
	public Nutlet() {
		setup.put("Nutlet-version", version);  // TODO
		productServer = new ProductServer();
		productServer.serverLog.setVerbosity(Log.Status.DEBUG);
		productServer.serverLog.note("Nutlet started");
	}

	//// String confDir = "";
	//// String httpRoot = "";
	ProgramRegistry registry = null;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		confDir  = config.getInitParameter("confDir");
		/** **
		confDir  = config.getInitParameter("confDir");
		setup.putAll(readTomcatParameters()); // from ho
		*/
		////httpRoot = productServer.setup.getOrDefault("HTTP_ROOT", ".").toString();
		// productServer.serverLog.setFormat(TextOutput.Format.HTML);
		// productServer.serverLog.setDecoration(TextOutput.Options.COLOUR, TextOutput.Options.URLS);

		// Note: upon setLogFile() below, this initial log will be dumped?
		// productServer.readConfig(Paths.get(confDir, "nutshell.cnf")); // Read two times? Or NutLet?
		productServer.readConfig(Paths.get(confDir, "nutshell-tomcat.cnf")); // Read two times? Or NutLet?

		// TODO: "re-override" conf with configs ? E.g. LOG_FORMAT

		Path cacheNutShell = productServer.CACHE_ROOT.resolve("nutshell").resolve(productServer.USER);

		if (!productServer.serverLog.logFileIsSet()) {
			String filename;
			if (productServer.serverLog.getFormat() == TextOutput.Format.HTML){
				filename = String.format("%s-%s.%d.html", "nutshell", productServer.USER, productServer.GROUP_ID);
			}
			else {
				filename = String.format("%s-%s.%d.log", "nutshell", productServer.USER, productServer.GROUP_ID);
			}

			// Path p = cacheNutShell.resolve("nutshell-tomcat-%s.html");
			//if (productServer.LOG_FORMAT.equals(TextOutput.Format.DEFAULT))
			//productServer.serverLog.setFormat(TextOutput.Format.HTML); // + MAP_URLS
			// productServer.serverLog.setDecoration(TextOutput.Options.COLOUR, TextOutput.Options.URLS);
			Path p = cacheNutShell.resolve(filename);
			// FileUtils.ensureWritableFile(p, productServer.GROUP_ID, productServer.filePerms, productServer.dirPerms);
			productServer.setLogFile(p.toString());
		}
		/*
		} catch (IOException e) {
			e.printStackTrace();
			productServer.serverLog.setFormat(TextOutput.Format.TEXT);
			productServer.setLogFile("/tmp/nutshell-tomcat-%s.log"); // Also this can fail?

		}
		*/

		productServer.serverLog.note(String.format("NutShell (%s) server log",  productServer.getVersion()));

		// Experimental
		/*
		HttpLog.urlMap.put(Paths.get("/tutka/data/dev/cache"), "http://dev.tutka.fmi.fi/nutshell/cache");
		HttpLog.urlMap.put(Paths.get("/tutka/code/dev/run"), "http://dev.tutka.fmi.fi/nutshell/products");
		 */
		// Here, for future extension dependent on ServletConfig config
		registry = new ProgramRegistry();

		// These general commands not needed, or even allowed!
		// Exception: default timeout (or would it be)
		// productServer.populate(registry);

		/*
		Program.Parameter.Simple<String> product = new Program.Parameter.Simple("product",
				"Product to be processed", "");
		registry.add(product);
		*/
		SimpleHtml.AUTO_ANCHORS = true;

	}

	/** Main handler. Returns a requested product in the HTTP stream or dumps an HTML status page.
	 *
	 *  The default *action* is to generate a product and return it through stream.
	 *
	 *  This Servlet handles the requests in URL
	 *   * http://localhost:8080/nutshell/NutShell?<params>
	 *   * Files that have no been found (Error 404)
	 *
	 *  TODO: request-response wrapper to support offline testing
	 */
	@Override	
	public void doGet(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException, ServletException
	{

		//String page = "menu.html";
		String page = "";
		//String product = "";

		// Yes, create for each httpRequest

		ProgramRegistry taskRegistry = new ProgramRegistry();
		ProductServer.Batch batch = new ProductServer.Batch();
		productServer.populate(batch, taskRegistry);

		// "Main" command handling loop
		for (Map.Entry<String,String[]> entry: httpRequest.getParameterMap().entrySet()){
			final String key = entry.getKey();
			final String[] values = entry.getValue();
			//final String value = (values.length == 0) ? "" : values[0]; // What about other elems?
			final String value = String.join(",", values);

			// Global (server level) settings
			if (registry.has(key)){
				Program.Parameter parameter = registry.get(key);
				System.err.printf(" Still found: %s -> %s  %n", key, parameter);

				if (parameter.hasParams()){
					try {
						parameter.setParams(values);
						parameter.exec(); // Remember! And TODO: update()
					} catch (NoSuchFieldException | IllegalAccessException e) {
						productServer.serverLog.fail(entry.toString() + " " + e.getMessage());
					}
				}
			}
			else if (taskRegistry.has(key)){
				Program.Parameter parameter = taskRegistry.get(key);

				if (!value.isEmpty() && parameter.hasParams()){
					try {
						parameter.setParams(values);
						//parameter.exec(); // Remember! And TODO: update()
						parameter.exec(); // Remember! And TODO: update()
					}
					catch (NoSuchFieldException | IllegalAccessException e) {
						// productServer.serverLog.fail(entry + " " + e.getMessage());
						sendStatusPage(HttpServletResponse.SC_CONFLICT, "Unsupported instruction(s): ",
								e.getMessage(), httpResponse);
						return;
					}
					catch (RuntimeException e){
						sendStatusPage(HttpServletResponse.SC_CONFLICT,
								String.format("Running '%s' with value '%s' failed ", key, value),
								e.getMessage(), httpResponse);
						return;
					}
				}
				// System.err.printf(" completed: %s -> %s  %n", key, parameter);
			}
			/*
			else if (key.equals("productX")){
				//product = value;
				batch.products.put("product1", value);
			}
			 */
			else if (key.equals("request") || key.equals("output")){ // Old alias for instructions
				try {
					//batchConfig.instructions.set(values);
					batch.instructions.add(values);
				}
				catch (NoSuchFieldException | IllegalAccessException e) {
					sendStatusPage(HttpServletResponse.SC_CONFLICT, "Unsupported instruction(s): ", e.getMessage(), httpResponse);
					return;
				}
			}
			/* else if (key.equals("depth")){
				batch.instructions.makeLevel = Integer.parseInt(value);
			} */
			else if (key.equals("page")){
				page = value;
			}
			// Interpret some "idioms"  || key.equals("help")
			else if (key.equals("catalog")  || key.equals("status") || key.endsWith(".html")){
				page = key;
			}
			else if (key.equals("demo")){
				File jsonFile = productServer.CACHE_ROOT.resolve(value).toFile();
                if (jsonFile.exists()){
                	//JSONParser parser = new JSONParser(jsonFile);
                    // JSONParser jsonParser = new JSONParser();
                    //System.out.printf(" Found: %s -> JSON %s %n", parentDir, jsonFile);
					sendStatusPage(HttpServletResponse.SC_ACCEPTED, "JSON demo under construction",
							entry.toString(), httpResponse);
                }
                else {
					sendStatusPage(HttpServletResponse.SC_NOT_FOUND, "Demo conf file  (JSON) not found",
							entry.toString(), httpResponse);
				}

			}
		}

		/*
		if (page.equals("help")){
			SimpleHtml html = getHtmlPage();

			html.appendTable(registry.map, "Commands");
			//html.appendTag(SimpleHtml.Tag.PRE, tracker.generators.toString());
			httpResponse.setStatus(HttpServletResponse.SC_OK); // tes
			sendToStream(html.document, httpResponse);

			return;
		}
		*/


		if (batch.instructions.makeLevelEquals(Instructions.MakeLevel.RELEASE_CACHE)) {

			batch.instructions.setMakeLevel(Instructions.MakeLevel.NONE);
			if (!batch.instructions.isEmpty()){
				productServer.serverLog.warn(String.format("Discarding remaining instructions: %s", batch.instructions) );
			}

			try {
				productServer.releaseCache();
				sendStatusPage(HttpServletResponse.SC_OK, "Released cache files",
						"OK", httpRequest, httpResponse);
			} catch (IOException e) {
				sendStatusPage(HttpServletResponse.SC_CONFLICT, "Releasing cache failed", e.getMessage(), httpResponse);
			}
			return;

		}


			//if (page.value.equals("catalog")){
		if (page.equals("catalog")){

			// ProductServerBase.GeneratorTracker tracker = productServer.new GeneratorTracker(productServer.productRoot.resolve("radar")); // FIX later
			ProductServerBase.GeneratorTracker tracker =
					productServer.new GeneratorTracker(productServer.PRODUCT_ROOT);

			try {
				tracker.run();
				// System.out.println(tracker.generators);
			} catch (IOException e) {
				sendStatusPage(HttpServletResponse.SC_CONFLICT, "Retrieving catalog failed", e.getMessage(), httpResponse);
			}

			SimpleHtml html = getHtmlPage();
			// html.appendTable(request.getParameterMap(), "Catalog");
			// Element elem = html.getUniqueElement(html.body, SimpleHtml.Tag.SPAN, "pageName");
			// elem.setTextContent(String.format(" Page: %s/%s ", httpRoot, page.value ));
			// html.appendElement(SimpleHtml.H2, "Testi");
			Map<String, Element> catalogMap = new HashMap<>();
			for (Path p: tracker.generators) {
				String productId = p.toString().replace("/",".");
				String url = String.format("product=%s", productId);
				catalogMap.put(productId, html.createAnchor(url, p.toString()));
			}
			html.appendTable(catalogMap, "Catalog");
			html.appendTag(SimpleHtml.Tag.PRE, tracker.generators.toString());
			httpResponse.setStatus(HttpServletResponse.SC_OK); // tes
			sendToStream(html.document, httpResponse);

			return;
		}


		// Plain status. (No product handling)
		if (page.equals("status")){ //|| batchConfig.instructions.isSet(ActionType.STATUS)){
			setup.put("counter", ProductServer.counter);

			SimpleHtml html = getHtmlPage();

			addServerStatus(html);

			html.appendTag(SimpleHtml.Tag.H1, "Running tasks");

			if (taskMap.isEmpty()){
				html.appendTag(SimpleHtml.Tag.DIV, "No tasks running at the moment.");
			}
			else {
				html.appendTable(taskMap, "Tasks");
			}

			sendToStream(html.document, httpResponse);

			//sendStatusPage(HttpServletResponse.SC_OK, "Status page",
			//		"NutShell server is running since " + setup.get("startTime"), httpRequest, httpResponse);
			return;
		}

		/*
			String product = "";
			for (String p: batch.products.values()){
				product = p;
			}
		*/

		if (page.equals("resolve")){

			/// Error 404 (not found) is handled as redirection in WEB-INF/web.xml
			/// Expected background: a product has been asked using a path /.../cache/...
			/// TODO: regexp based, and with instr. 'command' detection: query/  storage/ ...

			final Object requestUri = httpRequest.getAttribute("javax.servlet.error.request_uri");

			if (requestUri == null){
				sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "Product redirection error",
						"No request_uri for resolving a product", httpResponse);
				return;
			}

			Path path = Paths.get(requestUri.toString());
			String productFile = path.getFileName().toString();
			for (int i = 0; i < path.getNameCount(); i++) {
				if (path.getName(i).toString().equals("cache")){
					batch.products.put("prod", productFile);
					batch.instructions.setMakeLevel(Instructions.MakeLevel.MAKE);
					batch.instructions.set(Instructions.STREAM);
					break;
				}
			}

			if (productFile.isEmpty()){
				Map p = new HashMap();
				p.put("path", path);
				p.put("Parent", path.getParent());
				p.put("FileName", path.getFileName());
				sendStatusPage(HttpServletResponse.SC_BAD_REQUEST,
						String.format("Not-found-404 failed: %s: %s -> %s", requestUri, path.getName(1), path.getFileName()),
						p, httpResponse);
				return;
			}

			//page = "resolve";
			page = "";
		}

		/// Respond with an HTML page, if query contains no product request
		//if (batch.products.isEmpty()){ // redesign ?  || || !page.isEmpty()
		if ((!page.isEmpty()) || batch.products.isEmpty()){ // redesign ?  || || !page.isEmpty()

			if (page.isEmpty()){
				/*
				sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "NutLet request not understood",
								String.format("Query: %s", httpRequest.getQueryString()), httpRequest, httpResponse);
				return;

				 */
				page = "menu.html";
			}

			/**  TODO: rename main.html to sth like layout.html or template.html
			 *   Note: main.html is also utilized as index.html -> template/main.html (ie. linked)
			 */
			SimpleHtml html = includeHtml(page); // fail?

			if (httpRequest.getParameterMap().size() > 1){
				html.appendTable(httpRequest.getParameterMap(), "Several parameters");
			}

			Element elem = html.getUniqueElement(html.body, SimpleHtml.Tag.SPAN, "pageName");
			elem.setTextContent(String.format(" Page: %s/%s ", HTTP_ROOT, page ));

			httpResponse.setStatus(HttpServletResponse.SC_OK); // tes
			sendToStream(html.document, httpResponse);
			return;
		}


		Task task = null;

			// Default
		if (batch.instructions.value == 0) {
			batch.instructions.set(Instructions.STREAM);
			// batch.instructions.setOutputHandling(Instructions.OutputHandling.STREAM);
		}

		// problem: parameter ordering may cause  filename != productStr
		// TODO: -> _link_ equivalent files?

		try {
			// OutputFormat#HTML
			String product = null;
			for (String p: batch.products.values()) {
				product = p;
			}

			task = productServer.new Task(product, batch.instructions, productServer.serverLog);
			task.log.set(productServer.LOG_TASKS);
			task.log.debug(String.format("Log style: %s", task.log.getConf()));
			taskMap.put(task.getTaskId(), new Tasklet(task));
			task.log.special(String.format("taskMap size: %d", taskMap.size()));

			//task.log.textDecoration.setColour(TextDecoration.Options.COLOUR);
		}
		catch (ParseException e) {
			sendStatusPage(HttpServletResponse.SC_BAD_REQUEST,"Product parse failure",
				e,	httpRequest, httpResponse);
			return;
		}
		catch (Exception e) { // TODO: consider like above, indexedException
			//e.printStackTrace();
			sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "Product generation error",
				e, httpRequest, httpResponse);
			return;
		}

		if (task.instructions.isSet(Instructions.LATEST)){
			task.log.note("Action 'LATEST' not allowed in HTTP interface, discarding it");
			task.instructions.remove(Instructions.LATEST);
		}

		if (task.instructions.isSet(OutputType.STATUS)){
			//task.addGraph("ProductServer.Task" + task.getTaskId());
		}

		String[] directives = httpRequest.getParameterValues("directives");
		if ((directives != null) && (directives.length > 0)){ // Null check needed
			MapUtils.addEntries(directives, "True", task.info.getDirectives());
		}
		// TODO: check usage / wrong usage
		task.info.setDirectives(httpRequest.getParameterMap());
		task.log.note(task.toString());

		try {
			// track esp. missing inputs
			// task.log.ok("-------- see separate log --->");
			productServer.serverLog.debug(String.format("Task: %s", task));
			productServer.serverLog.info(String.format("See separate log: %s", task.log.logFile));
			//log.warn(String.format("Executing... %s", task));
			task.log.ok("Executing...");

			try {
				task.execute();
			} catch (InterruptedException e) {
				taskMap.remove(task.getTaskId());
				task.close();
				sendStatusPage(HttpServletResponse.SC_CONFLICT, "Product request interrupted.",
				e, httpRequest, httpResponse);
				// task.close();
				return;
			}
			//task.log.ok("-------- see separate log <---");

			if (task.log.indexedState.index >= HttpServletResponse.SC_BAD_REQUEST){
				//task.log.log();
				task.log.warn(String.format("Failed (%d) task: %s", task.log.indexedState.index, task.toString()));
				throw task.log.indexedState;
			}
			else {
				task.log.ok(String.format("Completed task: %s", task.toString()));
			}

			//if (task.actions.involves(Actions.MAKE) && (task.log.getStatus() >= Log.NOTE)) { // Critical to ORDER!
			// final boolean statusOK = (task.log.getStatus() >= Log.Status.NOTE.level);
			//if (task.actions.involves(Actions.MAKE|Actions.GENERATE) && (task.log.getStatus() >= Log.NOTE)) { // Critical to ORDER!

			if (task.log.getStatus() >= Log.Status.NOTE.level) {
				if (task.instructions.isSet(OutputType.STREAM)) {
					task.log.debug("sendToStream: " + task.outputPath);
					try {
						sendToStream(task.outputPath, httpResponse);
						taskMap.remove(task.getTaskId());
						task.close();
						return;
					} catch (Exception e) {
						task.instructions.add(OutputType.STATUS);
					}
				} else if (task.instructions.isSet(Instructions.REDIRECT)) {
					taskMap.remove(task.getTaskId());
					task.close();
					String url = String.format("%s/cache/%s?redirect=NO", httpRequest.getContextPath(), task.relativeOutputPath);
					//String url = request.getContextPath() + "/cache/" + task.relativeOutputDir + "/" + filename + "?redirect=NO";
					httpResponse.sendRedirect(url);
					// task.close();
					return;
				}
			}
			else {
				ByteArrayOutputStream os = new ByteArrayOutputStream();

				if (!task.instructions.isSet(OutputType.STATUS)) {
					taskMap.remove(task.getTaskId());
					task.close();
					sendStatusPage(HttpServletResponse.SC_OK, "Product request completed",
							os.toString("UTF8"), httpRequest, httpResponse);
					// task.close();
					return;
				}
			}

			/* if not STATUS
				*
				 * The page isn’t redirecting properly
				 * Firefox has detected that the server is redirecting the request for this address in a way that will never complete.
				 * This problem can sometimes be caused by disabling or refusing to accept cookies.
				 *
			*/


			httpResponse.setStatus(HttpServletResponse.SC_OK); // tes
		}
		catch (IndexedState e) {
			httpResponse.setStatus(e.index);
			task.instructions.add(OutputType.STATUS);
			task.instructions.add(Instructions.INPUTLIST);
		}

		SimpleHtml html = getHtmlPage(); // slows production?

		html.appendTag(SimpleHtml.Tag.H1, "Product: " + task.info.PRODUCT_ID);
		//html.createElement(SimpleHtml.Tag.P, "No ST REAM or REDIRECT were requested, so this page appears.");

		Element elem = html.createElement(SimpleHtml.Tag.PRE, "Status: " + task.log.indexedState.getMessage());

		switch (task.log.indexedState.index){
			case HttpServletResponse.SC_PRECONDITION_FAILED:
				html.appendTag(SimpleHtml.Tag.H2, "Input problem(s)");
				html.appendTag(SimpleHtml.Tag.P, "See input list further below");
				elem.setAttribute("class", "error");
				break;
			default:
				// General, section-wise handling
				if (task.log.indexedState.index > 400) {
					html.appendTag(SimpleHtml.Tag.H2, "Product generator error");
					elem.setAttribute("class", "error");
				}
				else if (task.log.indexedState.index > 300) {
					html.appendTag(SimpleHtml.Tag.B, "Warnings:");
					elem.setAttribute("class", "error");
				}
				else if (task.log.indexedState.index < 200)
					elem.setAttribute("class", "note");
		}

		html.appendElement(elem);


		if (task.instructions.isSet(OutputType.STATUS)) {

			Map<String,Object> map = new LinkedHashMap<>();



			//Path inputScript =  Paths.get("products", productTask.productDir.toString(), productServer.inputCmd);
			if (task.relativeOutputDir != null) {

				Path relativePath = productServer.cachePrefix.resolve(task.relativeOutputPath);
				//task.relativeOutputDir.toString(), task.info.getFilename());
				//elem = html.createAnchor(relativePath, relativePath.getFileName());
				map.put("Output file", html.createAnchor(relativePath, relativePath.getFileName()));
				//elem = html.createAnchor(relativePath.getParent(), null);
				if ((task.log.logFile!=null) && task.log.logFile.exists()){
					Path relativeLogPath = productServer.cachePrefix.resolve(task.relativeLogPath);
					map.put("Log file", html.createAnchor(relativeLogPath, task.relativeLogPath.getFileName()));
				}

				if (task.log.getStatus() <= Log.Status.ERROR.level){
					// TODO: draw graph only...
					// html.appendTag(SimpleHtml.Tag.PRE, "See above message. (Less important debugging follows.)");
					html.appendTag(SimpleHtml.Tag.PRE, "Check for debugging info below.");
					//html.appendTag(SimpleHtml.Tag.PRE, String.format("Success/error status: %d", task.log.getStatus()));
				}

				if ((task.relativeGraphPath != null)){ //  && (task.graph != null)

					task.log.debug("Writing graph to SVG file");
					Path graphPath = task.writeGraph();
					//html.appendTag(SimpleHtml.Tag.H4, String.format("%s Exists=%b",
					//		task.relativeGraphPath, graphPath.toFile().exists()));
					if (ProductServer.serverGraph != null){
						String graphFileName = productServer.serverLog.logFile.getParent()+"/NutShell.svg";
						try {
							// TODO logDir
							ProductServer.serverGraph.dotToFile(graphFileName);
						}
						catch (InterruptedException | IOException e) {
							task.log.warn(e.getMessage());
							task.log.fail(String.format("Could not write to %s", graphFileName));
						}
							// graphPath.getParent().resolve("productServer.svg").toString());
					}


					try {

						task.log.debug("Creating graph as EMBED element containing SVG data");

						Element graphElem = html.createElement(SimpleHtml.Tag.EMBED);
						graphElem.setAttribute("type", "image/svg+xml");
						graphElem.setAttribute("border", "1");
						//graphElem.setAttribute("onclick", "alert('Not implemented')");
						graphElem.setAttribute("title", task.relativeGraphPath.getFileName().toString());

						// For some reason, this does not work. Or it was not clickable...
						// graphElem.setAttribute("src", relativeGraphPath.toString());
						// html.appendTag(SimpleHtml.Tag.PRE, graphPath.toString());

						task.log.debug(String.format("Reading SVG file %s", graphPath));
						Document graphSvg = SimpleXML.readDocument(graphPath);

						task.log.debug("Importing and appending XML elem");
						Node node = html.document.importNode(graphSvg.getDocumentElement(), true);
						//task.log.warn("Appending XML (SVG) node to EMBED elem");
						graphElem.appendChild(node);
						//graphElem.appendChild(graphSvg.getDocumentElement());
						graphElem.setAttribute("title", task.info.PRODUCT_ID);
						graphElem.setAttribute("id", "graph");
						//   task.log.warn("Appending SVG element");
						html.appendElement(graphElem);
					}
					catch (Exception e){

						//Element span =
						html.appendTag(SimpleHtml.Tag.SPAN, "Failed in generating the process graph: ");
						html.appendAnchor("cache/"+task.relativeGraphPath, task.relativeGraphPath.getFileName().toString());
						html.appendTag(SimpleHtml.Tag.PRE, e.getMessage()).setAttribute("class", "error");
						// Comments! Consider tail?

						/*  KEEP THIS, for debugging TODO: conditional on debug level?
						try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
							PrintStream printStream = new PrintStream(os);
							Graph graph = task.getGraph(null);
							graph.toStream(printStream);
							printStream.append("--------------");
							// e.printStackTrace(printStream);
							// html.appendComment(os.toString("UTF8")); ILLEGALS
							//html.appendComment(" TEST ");
							html.appendTag(SimpleHtml.Tag.PRE, os.toString("UTF8"));
							printStream.close();
							//os.close();
						}
						 */

						//Element graphElem = html.createElement(SimpleHtml.Tag.EMBED);
						/*
						Element graphElem = html.createElement(SimpleHtml.Tag.IMG);
						graphElem.setAttribute("type", "image/svg+xml");
						graphElem.setAttribute("border", "1");
						graphElem.setAttribute("src", "cache/"+task.relativeGraphPath.toString());
						html.appendElement(graphElem);
						*/
					}
					Path relativeGraphPath = productServer.cachePrefix.resolve(task.relativeGraphPath);
					map.put("Graph", html.createAnchor(relativeGraphPath, task.relativeGraphPath.getFileName()));

					/* <embed id="viewMain" src="" type="image/svg+xml"></embed> */
				}

				map.put("Output file", html.createAnchor(relativePath, relativePath.getFileName()));
				map.put("Shell cmd request", html.createAnchor(relativePath.getParent().resolve(task.info.getBasename()+".sh"), "basename+'.sh'"));
				//String basenName = relativePath.getFileName();
				map.put("Output dir", html.createAnchor(relativePath.getParent(), null));

			}
			else {
				map.put("Output file", "???");
			}

			// NOTE: these assume ExternalGenerator?
			Path gen = Paths.get("products", task.productDir.toString(), ExternalGenerator.scriptName);
			map.put("Generator dir", html.createAnchor(gen.getParent(),null));
			map.put("Generator file", html.createAnchor(gen, gen.getFileName()));
			// map.put("actions", batch.instructions);
			// task.info.directives.put("TEST", "value");
			map.put("instructions", batch.instructions);
			map.put("directives", task.info.getDirectives());


			html.appendTable(map, "Product generator");

			// INPUTS
			if (!task.inputs.isEmpty()) {
				Map<String, Element> linkMap = new TreeMap<>();
				for (Map.Entry<String, String> e : task.inputs.entrySet()) {
					String inputFilename = e.getValue();
					//elem = html.createAnchor("?request=CHECK&product="+e.getValue(), e.getValue());
					String url = String.format("%s%s?actions=DEBUG&product=%s", httpRequest.getContextPath(), httpRequest.getServletPath(), inputFilename);
					linkMap.put(e.getKey(), html.createAnchor(url, inputFilename));
					//Object inp = task.retrievedInputs.get(e.getKey());
					//if ()
					//linkMap.put("", html.createAnchor(task.retrievedInputs, "log"));
				}
				html.appendTable(linkMap, "Product inputs");
			}

			html.appendTable(task.getParamEnv(), "Product generator environment");
		}

		html.appendTag(SimpleHtml.Tag.H2, "Log");
		if ((task.log.logFile!=null) && task.log.logFile.exists()){
			// <embed type="text/html" src="snippet.html" width="500" height="200">
			Element embed = html.appendTag(SimpleHtml.Tag.EMBED);
			embed.setAttribute("type", "text/html");
			embed.setAttribute("src", Paths.get("cache").resolve(task.relativeLogPath).toString());
			embed.setAttribute("width", "100%");
			embed.setAttribute("height", "500");
			embed.setAttribute("class", "code"); // Has no effect?
			embed.setAttribute("style", "outline: 2px"); // Has no effect?
			/*
			html.appendTag(SimpleHtml.Tag.H3, "Task log");
			StringBuilder builder = new StringBuilder();
			BufferedReader reader = new BufferedReader(new FileReader(task.log.logFile));
			String line = null;
			while ((line = reader.readLine()) != null){
				// TODO: detect ERROR, WARNING, # etc and apply colours
				// Requires separate  lines, so something other than StringBuilder
				// builder.append("<b>x</b>").append(line).append('\n');
				builder.append(line).append('\n');
			}
			html.appendTag(SimpleHtml.Tag.PRE, builder.toString()).setAttribute("class", "code");
			 */

		}

		// html.appendTag(SimpleHtml.Tag.H3, "NutLet log");
		//html.appendTag(SimpleHtml.Tag.PRE, os.toString("UTF-8")).setAttribute("class", "code");
		// html.appendTable(productInfo.getParamEnv(null), "Product parameters");

		/*
		html.appendTag(SimpleHtml.Tag.H3, "ProductServer log");
		html.appendTag(SimpleHtml.Tag.PRE, String.format("Length=%d", productServer.log.buffer.length())).setAttribute("class", "code");
		html.appendTag(SimpleHtml.Tag.PRE, productServer.log.buffer.toString()).setAttribute("class", "code");
		 */
		html.appendTag(SimpleHtml.Tag.H3, "Corresponding command lines:");
		String clsName = productServer.getClass().getCanonicalName();
		String confFiles = productServer.confFiles.stream()
            .map(i -> "--conf "+i)
            .collect( Collectors.joining("."));
		for (String cmd: new String[]{"nutshell", String.format("java -cp %s/WEB-INF/lib/Nutlet.jar %s", HTTP_ROOT, clsName)}){
			html.appendTag(SimpleHtml.Tag.PRE, String.format("%s --verbose %s --instructions %s %s",
					cmd, confFiles, batch.instructions, task.info)).setAttribute("class", "code");
		}
		//String cmdLine = "%s  --verbose  --conf %s --instructions %s %s";
		//String cp = System.getProperty("java.class.path"); // (TomCat)
		//String cmdLine2 = "java -cp %s %s  --verbose  --conf %s --instructions %s %s";
		//html.appendTag(SimpleHtml.Tag.PRE, String.format(cmdLine2, cp, clsName, productServer.confFiles, batch.instructions, task.info)).setAttribute("class", "code");
		//html.appendTag(SimpleHtml.Tag.PRE, String.format(cmdLine2, productServer.confFile, batchConfig.instructions, task.info)).setAttribute("class", "code");

		html.appendTable(task.info.getParamEnv(null), "Product parameters");


		//log.warn("Nyt jotain");

		// 2
		addRequestStatus(html, httpRequest);

		html.appendTag(SimpleHtml.Tag.H1, "Running tasks");
		html.appendTable(taskMap, "Tasks");
		taskMap.remove(task.getTaskId());
		task.close();

		// html.appendTable(registry.map, "Command set");

		addServerStatus(html);


		sendToStream(html.document, httpResponse);
		//task.close();
		// 1
	}
	public void doGetProduct(HttpServletRequest httpRequest, HttpServletResponse httpResponse, ProductServer.Batch batch) throws IOException  {
		// DELEGATE here ... but failure should be caught etc.
	}

	@Override
	protected void addServerStatus(SimpleHtml html) throws IOException{

		html.appendTag(SimpleHtml.Tag.H1, "Product Server setup");

		html.appendTag(SimpleHtml.Tag.P, "NutShell Server is running since " + setup.get("startTime"));

		html.appendTable(productServer.setup, null);

				//sendStatusPage(HttpServletResponse.SC_OK, "Status page",
				//		"NutShell server is running since " + setup.get("startTime"), httpRequest, httpResponse);

		super.addServerStatus(html);

	}





	/**
	 * @param args - command line arguments
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		Method[] methods =  HttpServletRequest.class.getMethods();
		for (Method method : methods) {
			String name = method.getName();
			if (name.startsWith("get"))
				System.out.println(name + ":" + method.getParameterCount());
		}

		Nutlet nut = new Nutlet();
		nut.productServer.readConfig("./nutshell.cnf"); // Read
		for (Map.Entry<?,?> entry: nut.setup.entrySet()) {
			System.out.println(entry.getKey() + ":" + entry.getValue());
		}

		SimpleHtml html = nut.getHtmlPage();
		System.out.print(html.toString());
		SimpleXML.writeDocument(html.document, System.out);

	}

}
