package nutshell;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import nutshell.ProductServer.Task;
import org.w3c.dom.Node;


public class Nutlet extends NutWeb { //HttpServlet {

	private static final long serialVersionUID;
	static {
		serialVersionUID = 1293000393642243650L;
	}

	static final public String version = "1.4";

	String confDir = "";


	//final Map<String,Object> setup;
	final ProductServer productServer;
	//final GregorianCalendar startTime;


	/**
	 * param arg Input
	 *
	 *
	 */
	public Nutlet() {
		setup.put("version", version);  // TODO
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
		productServer.readConfig(Paths.get(confDir, "nutshell.cnf")); // Read two times? Or NutLet?
		if (!productServer.serverLog.logFileIsSet()){
			productServer.setLogFile("/tmp/nutshell-tomcat-%s.log");
		}

		// Here, for future extension dependen on ServletConfig config
		registry = new ProgramRegistry();

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
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
	{


		ProductServer.BatchConfig batchConfig = new ProductServer.BatchConfig();

		productServer.populate(batchConfig, registry);

		ProductServer.InstructionParameter instructionParameter = productServer.new InstructionParameter(batchConfig.instructions);
		registry.add(instructionParameter);
		registry.add("actions", instructionParameter);
		registry.add("output", instructionParameter);
		registry.add("request", instructionParameter); // oldest

		Program.Parameter.Simple<String> page = new Program.Parameter.Simple("page",
				"HTML page to be viewed", "menu.html");
		registry.add(page);

		Program.Parameter.Simple<String> product = new Program.Parameter.Simple("product",
				"Product to be processed", "");
		registry.add(product);

		// Pre-interpret some idioms
		if (request.getParameterMap().keySet().size() == 1){
			String q = request.getQueryString();
			if (q != null) {
				switch (q){
					case "status":
					case "catalog":
						page.value = q;
						break;
					default:
						if (q.endsWith(".html")) {
							// NOTE: accepts also files of type: page.value="page=form.html"
							page.value = q;
							// ... but overridden just below
						}
						else {
							productServer.serverLog.warn(String.format("Could not parse query: %s", q));
						}
				}
			}
		}

		// "Main" command handling loop
		for (Map.Entry<String,String[]> entry: request.getParameterMap().entrySet()){
			final String key = entry.getKey();
			if (registry.has(key)){
				Program.Parameter parameter = registry.get(key);
				if (parameter.hasParams()){
					try {
						parameter.setParams(entry.getValue());
						parameter.exec(); // Remember! And TODO: update()
					} catch (NoSuchFieldException | IllegalAccessException e) {
						productServer.serverLog.fail(entry.toString() + " " + e.getMessage());
					}
				}
			}
		}

		// Debug
		//batchConfig.instructions.add(Instructions.STATUS);

		if (batchConfig.instructions.isSet(ActionType.CLEAR_CACHE)) {

			/*
			batchConfig.instructions.remove(ActionType.CLEAR_CACHE);
			if (!batchConfig.instructions.isEmpty()){
				productServer.serverLog.warn(String.format("Discarding remaining instructions: %s", batchConfig.instructions) );
			}
			 */

			try {
				productServer.clearCache(false);
				sendStatusPage(HttpServletResponse.SC_OK, "Cleared cache",
						"OK", request, response);
			} catch (IOException e) {
				sendStatusPage(HttpServletResponse.SC_CONFLICT, "Clearing cache failed", e.getMessage(), response);
			}
			return;
		}

		if (page.value.equals("catalog")){

			ProductServerBase.GeneratorTracker tracker = productServer.new GeneratorTracker(productServer.productRoot.resolve("radar")); // FIX later

			try {
				tracker.run();
				// System.out.println(tracker.generators);
			} catch (IOException e) {
				sendStatusPage(HttpServletResponse.SC_CONFLICT, "Retrieving catalog failed", e.getMessage(), response);
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
			response.setStatus(HttpServletResponse.SC_OK); // tes
			sendToStream(html.document, response);

			return;
		}


		//if (request.getParameterMap().values().isEmpty()){
		/*
		for (String key: request.getParameterMap().keySet()){
			if (key.endsWith(".html")) {
				page.value = key;
			}
			break;
		}

		 */
		if (page.value.equals("status")){
			sendStatusPage(HttpServletResponse.SC_OK, "Status page",
					"NutShell server is running since " + setup.get("startTime"), request, response);
			return;
		}

		if (product.value.equals("resolve")){
			/// Error 404 (not found) is handled as redirection in WEB-INF/web.xml

			product.value = "";

			final Object requestUri = request.getAttribute("javax.servlet.error.request_uri");

			if (requestUri == null){
				sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "Product redirection error",
						"No request_uri for resolving a product", response);
				return;
			}

			Path path = Paths.get(requestUri.toString());
			for (int i = 0; i < path.getNameCount(); i++) {
				if (path.getName(i).toString().equals("cache")){
					product.value = path.getFileName().toString();
					batchConfig.instructions.set(Instructions.MAKE | Instructions.STREAM);
					break;
				}
			}

			if (product.value.isEmpty()){
				Map p = new HashMap();
				p.put("path", path);
				p.put("Parent", path.getParent());
				p.put("FileName", path.getFileName());
				sendStatusPage(HttpServletResponse.SC_BAD_REQUEST,
						String.format("Not-found-404 failed: %s: %s -> %s", requestUri, path.getName(1), path.getFileName()),
						p, response);
				return;
			}

		}

		/// Respond with an HTML page, if query contains no product request
		//if ((productStr == null) || productStr.isEmpty()){
		if (product.value.isEmpty()){ // redesign ?

			if (page.value.isEmpty()){
				sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "NutLet request not understood",
								String.format("Query: %s", request.getQueryString()), request, response);
						return;
			}

			/**  TODO: rename main.html to sth like layout.html or template.html
			 *   Note: main.html is also utilied as index.html -> template/main.html (ie. linked)
			 */
			SimpleHtml html = includeHtml(page.value); // fail?

			if (request.getParameterMap().size() > 1){
				html.appendTable(request.getParameterMap(), "Several parameters");
			}

			Element elem = html.getUniqueElement(html.body, SimpleHtml.Tag.SPAN, "pageName");
			elem.setTextContent(String.format(" Page: %s/%s ", httpRoot, page.value ));
			//html.appendElement(SimpleHtml.H2, "Testi");

			response.setStatus(HttpServletResponse.SC_OK); // tes
			sendToStream(html.document, response);
			return;
		}



		try {

			// Default
			if (batchConfig.instructions.value == 0)
				batchConfig.instructions.set(Instructions.MAKE | Instructions.STREAM);

			// problem: parameter ordering may cause  filename != productStr
			// TODO: -> _link_ equivalent files?

			Task task = productServer.new Task(product.value, batchConfig.instructions.value,null);

			if (task.instructions.isSet(Instructions.LATEST)){
				task.log.note("Action 'LATEST' not allowed in HTTP interface, discarding it");
				task.instructions.remove(Instructions.LATEST);
			}

			if (task.instructions.isSet(Instructions.STATUS)){
				task.addGraph("ProductServer.Task" + task.getTaskId());
			}

				//String[] directives = request.getParameterValues("directives");
			task.info.setDirectives(request.getParameterMap());
			//task.log.setVerbosity(log.verbosity);

			task.log.note(task.toString());

			// Consider Generator gen =

			try {
				// track esp. missing inputs
				//task.log.ok("-------- see separate log --->");
				productServer.serverLog.info(String.format("Executing... %s", task));
				productServer.serverLog.debug(String.format("See separate log: %s", task.log.logFile));
				//log.warn(String.format("Executing... %s", task));
				task.log.ok("Executing...");
				task.execute();
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
				final boolean statusOK = (task.log.getStatus() >= Log.Status.NOTE.level);
				//if (task.actions.involves(Actions.MAKE|Actions.GENERATE) && (task.log.getStatus() >= Log.NOTE)) { // Critical to ORDER!

				if (statusOK && task.instructions.isSet(Instructions.STREAM)) {
					// sendToStream(task, response);
					//Log.Level.NOTE.ordinal();
					task.log.debug("sendToStream: " + task.outputPath);
					try {
						sendToStream(task.outputPath, response);
						return;
					}
					catch (Exception e){
						task.instructions.add(Instructions.STATUS);
					}
				}
				else if (statusOK && task.instructions.isSet(Instructions.REDIRECT)) {
					String url = String.format("%s/cache/%s?redirect=NO", request.getContextPath(), task.relativeOutputPath);
					//String url = request.getContextPath() + "/cache/" + task.relativeOutputDir + "/" + filename + "?redirect=NO";
					response.sendRedirect(url);
					return;
				}
				// if not STATUS
					/*
					 * The page isn’t redirecting properly
					 * Firefox has detected that the server is redirecting the request for this address in a way that will never complete.
					 * This problem can sometimes be caused by disabling or refusing to accept cookies.
					 */
				//}
				else {
					ByteArrayOutputStream os = new ByteArrayOutputStream();

					if (!task.instructions.isSet(Instructions.STATUS)) {
						sendStatusPage(HttpServletResponse.SC_OK, "Product request completed",
								os.toString("UTF8"), request, response);
						return;
					}
				}

				response.setStatus(HttpServletResponse.SC_OK); // tes
			}
			catch (IndexedState e) {
				response.setStatus(e.index);
				task.instructions.add(Instructions.STATUS);
				//task.instructions.add(Instructions.STATUS); // ?
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


			if (task.instructions.isSet(Instructions.STATUS)) {

				Map<String,Object> map = new LinkedHashMap<>();

				map.put("instr", batchConfig.instructions);
				map.put(instructionParameter.getName(), Arrays.toString(instructionParameter.getValues()));
				//map.putAll(registry.map);


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

					if ((task.relativeGraphPath != null) && (task.graph != null)){

						task.log.debug("Writing graph to SVG file");
						Path graphPath = task.writeGraph();
						//html.appendTag(SimpleHtml.Tag.H4, String.format("%s Exists=%b",
						//		task.relativeGraphPath, graphPath.toFile().exists()));


						try {

							task.log.debug("Creating graph as EMBED element containing SVG data");

							Element graphElem = html.createElement(SimpleHtml.Tag.EMBED);
							graphElem.setAttribute("type", "image/svg+xml");
							graphElem.setAttribute("border", "1");
							//graphElem.setAttribute("onclick", "alert('Not implemented')");
							graphElem.setAttribute("title", task.relativeGraphPath.getFileName().toString());
							//html.appendTag(SimpleHtml.Tag.PRE, graphPath.toString());

							// graphElem.setAttribute("src", relativeGraphPath.toString());

							task.log.debug(String.format("Reading SVG file %s", graphPath));
							Document graphSvg = SimpleXML.readDocument(graphPath);

							//SimpleXML.createDocument().
							task.log.debug("Importing and appending XML elem");
							Node node = html.document.importNode(graphSvg.getDocumentElement(), true);
							//task.log.warn("Appending XML (SVG) node to EMBED elem");
							graphElem.appendChild(node);
							//graphElem.appendChild(graphSvg.getDocumentElement());
							graphElem.setAttribute("title", task.info.PRODUCT_ID);
							graphElem.setAttribute("id", "graph");
							// task.log.warn("Appending SVG element");
							html.appendElement(graphElem);
						}
						catch (Exception e){



							Element span = html.appendTag(SimpleHtml.Tag.SPAN, "Failed in generating CLICKABLE graph: ");
							html.appendAnchor("cache/"+task.relativeGraphPath, task.relativeGraphPath.getFileName().toString());

							html.appendTag(SimpleHtml.Tag.PRE, e.getMessage()).setAttribute("class", "error");
							// Comments! Consider tail?
							try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
								PrintStream printStream = new PrintStream(os);
								task.graph.toStream(printStream);
								printStream.append("--------------");
								e.printStackTrace(printStream);
								// html.appendComment(os.toString("UTF8")); ILLEGALS
								html.appendComment(" TEST ");
								html.appendTag(SimpleHtml.Tag.PRE, os.toString("UTF8"));
								printStream.close();
								//os.close();
							}

							//Element graphElem = html.createElement(SimpleHtml.Tag.EMBED);
							Element graphElem = html.createElement(SimpleHtml.Tag.IMG);
							graphElem.setAttribute("type", "image/svg+xml");
							graphElem.setAttribute("border", "1");
							graphElem.setAttribute("src", "cache/"+task.relativeGraphPath.toString());
							html.appendElement(graphElem);

						}
						Path relativeGraphPath = productServer.cachePrefix.resolve(task.relativeGraphPath);
						map.put("Graph", html.createAnchor(relativeGraphPath, task.relativeGraphPath.getFileName()));

						/* <embed id="viewMain" src="" type="image/svg+xml"></embed> */
					}


					map.put("Output dir", html.createAnchor(relativePath.getParent(), null));
				}
				else {
					map.put("Output file", "???");
				}

				// NOTE: these assume ExternalGenerator?
				Path gen = Paths.get("products", task.productDir.toString(), ExternalGenerator.scriptName);
				map.put("Generator dir", html.createAnchor(gen.getParent(),null));
				map.put("Generator file", html.createAnchor(gen, gen.getFileName()));
				map.put("actions", batchConfig.instructions);
				map.put("directives", task.info.directives);


				html.appendTable(map, "Product generator");

				// INPUTS
				if (!task.inputs.isEmpty()) {
					Map<String, Element> linkMap = new TreeMap<>();
					for (Map.Entry<String, String> e : task.inputs.entrySet()) {
						String inputFilename = e.getValue();
						//elem = html.createAnchor("?request=CHECK&product="+e.getValue(), e.getValue());
						String url = String.format("%s%s?actions=DEBUG&product=%s", request.getContextPath(), request.getServletPath(), inputFilename);
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
			String cmdLine = "java -cp %s/WEB-INF/lib/Nutlet.jar %s  --verbose  --conf %s --instructions %s %s";
			String name = productServer.getClass().getCanonicalName();
			html.appendTag(SimpleHtml.Tag.PRE, String.format(cmdLine, httpRoot, name, productServer.confFile, batchConfig.instructions, task.info)).setAttribute("class", "code");

			String cmdLine2 = "nutshell --verbose --conf %s --instructions %s %s ";
			html.appendTag(SimpleHtml.Tag.PRE, String.format(cmdLine2, productServer.confFile, batchConfig.instructions, task.info)).setAttribute("class", "code");

			html.appendTable(task.info.getParamEnv(null), "Product parameters");

			html.appendTable(registry.map, "Command set");

			//log.warn("Nyt jotain");

			// 2
			addRequestStatus(html, request);
			addServerStatus(html);
			sendToStream(html.document, response);

		}
		catch (ParseException e) {
			sendStatusPage(HttpServletResponse.SC_BAD_REQUEST,"Product parse failure",
					e,	request, response);
		}
		catch (Exception e) { // TODO: consider like above, indexedException
			//e.printStackTrace();
			sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "Product generation error",
						e, request, response);
		}
		// 1
	}


	@Override
	protected void addServerStatus(SimpleHtml html) throws IOException{

		html.appendTag(SimpleHtml.Tag.H1, "Product Server setup");
		html.appendTable(productServer.setup, null);

		super.addServerStatus(html);

	}





	/**
	 * @param args
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
