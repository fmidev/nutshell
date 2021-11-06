package nutshell;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import nutshell.ProductServer.Task;
import nutshell.ProductServer.Actions;


public class Nutlet extends HttpServlet {

	private static final long serialVersionUID;
	static {
		serialVersionUID = 1293000393642243650L;
	}

	static final public String version = "1.2";

	final Map<String,Object> setup;
	final ProductServer productServer;
	//final GregorianCalendar startTime;


	/**
	 * param arg Input
	 *
	 *
	 */
	public Nutlet() {
		setup = new HashMap<>();
		GregorianCalendar startTime = new GregorianCalendar();
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
		setup.put("startTime", simpleDateFormat.format(startTime.getTime()));
		setup.put("startTimeMs", startTime.getTimeInMillis());
		setup.put("version", version);  // TODO
		productServer = new ProductServer();
		productServer.log.setVerbosity(Log.DEBUG);
		// catalina.out
		//System.out.println("Hey, started Nutlet");
		//System.err.println("Hey, errata");
		productServer.log.warn("What about me?");
	}


	String confDir = "";
	String httpRoot = "";

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		confDir  = config.getInitParameter("confDir");
		setup.putAll(readTomcatParameters()); // from ho
		productServer.readConfig(Paths.get(confDir, "nutshell.cnf")); // Read two times? Or NutLet?
		httpRoot = productServer.setup.getOrDefault("HTTP_ROOT", ".").toString();
	}
	

	@Override	
	public void doPost(HttpServletRequest request,HttpServletResponse response) throws IOException, ServletException {
		doGet(request, response);
	}


	/** Main hcdler. Returns a requested product in the HTTP stream or dumps an HTML status page.
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

		String productStr = request.getParameter("product");

		/// Respond with an HTML page, if query contains no product request
		if ((productStr == null) || productStr.isEmpty()){

			/**  TODO: rename main.html to sth like layout.html or template.html
			 *   Note: main.html is also utilied as index.html -> template/main.html (ie. linked)
			 */
			String pageName = null; //"menu.html";

			Map<String, String[]> parameterMap = request.getParameterMap();
			if (!parameterMap.isEmpty()){

				pageName = request.getParameter("page");

				if (pageName == null) {

					String queryString = request.getQueryString();

					if (queryString.endsWith(".html")) {
						pageName = queryString;
					}
					else if (queryString.equals("status")) {
						sendStatusPage(HttpServletResponse.SC_OK, "Status page",
								"NutShell server is running since " + setup.get("startTime"), request, response);
						return;
					}
					else if (queryString.equals("clearCache")){
						productServer.clearCache(false);
						sendStatusPage(HttpServletResponse.SC_OK, "Cleared cache",
								"Statistics... ", request, response);
						return;
					}
					else {
						sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "NutLet request not understood",
								String.format("Query: %s", queryString), request, response);
						return;
					}
				}
			}

			if ((pageName == null) || pageName.isEmpty()){
				pageName = "menu.html";
			}



			SimpleHtml html = includeHtml(pageName); // fail?

			if (parameterMap.size() > 1){
				html.appendTable(request.getParameterMap(), "Several parameters");
			}

			response.setStatus(HttpServletResponse.SC_OK); // tes
			sendToStream(html.document, response);
			return;
		}


		Actions actions = new Actions();
		for (String key: new String[]{"actions", "output",  "request", "action"}) {  // request and action deprecating!
			String[] a = request.getParameterValues(key);
			if (a != null) {
				try {
					actions.add(a);
				} catch (Exception e) {
					sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "Parsing 'request' failed:" + Arrays.toString(a), e.getMessage(), response);
					return;
				}
			}
		}

		// Default
		if (actions.value == 0)
			actions.set(Actions.MAKE | Actions.STREAM);


		/// Error 404 (not found) is handled as redirection in WEB-INF/web.xml
		if (productStr.equals("resolve")){

			final Object requestUri = request.getAttribute("javax.servlet.error.request_uri");

			if (requestUri == null){
				sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "No request_uri for resolving a product", null, response);
				return;
			}

			Path path = Paths.get(requestUri.toString());
			productStr = "";
			for (int i = 0; i < path.getNameCount(); i++) {
				if (path.getName(i).toString().equals("cache")){
					productStr = path.getFileName().toString();
					actions.set(Actions.MAKE | Actions.STREAM);
					break;
				}
			}

			if (productStr.isEmpty()){
				/*
				StringBuffer sb = new StringBuffer();
				sb.append(requestUri);
				sb.append(": ").append(path.getName(1));
				sb.append(" -> ").append(path.getFileName());
				 */
				sendStatusPage(HttpServletResponse.SC_BAD_REQUEST,
						"Could not resolve not found (404) request.",
						String.format("%s: %s -> %s", requestUri, path.getName(1), path.getFileName()),
						//sb.toString(),
						response);
				return;
			}

		}



		try {
			SimpleHtml html = getHtmlPage(); // slows production?

			ByteArrayOutputStream os = new ByteArrayOutputStream();
			final String filename = productStr;

			// problem: parameter ordering may cause  filename != productStr
			// TODO: -> _link_ equivalent files?

			//Log log = new Log(String.format("%s-%d", getClass().getSimpleName(), ++productServer.counter));
			//log.printStream = new PrintStream(os); // printStream;

			// Logging: save logs (disk) with instantaneous or save always?
			//Task task = productServer.new Task(filename, action.value,  log);
			Task task = productServer.new Task(filename, actions.value,null);

			//String[] directives = request.getParameterValues("directives");
			task.setDirectives(request.getParameterMap());
			//task.log.setVerbosity(log.verbosity);

			task.log.note(task.toString());

			// Consider Generator gen =

			try {
				// track esp. missing inputs
				//task.log.ok("-------- see separate log --->");
				productServer.log.warn(String.format("Executing... %s", task));
				productServer.log.debug(String.format("See separate log: %s", task.log.logFile));
				//log.warn(String.format("Executing... %s", task));
				task.log.ok("Executing...");
				task.execute();
				//task.log.ok("-------- see separate log <---");


				if (task.log.indexedException.index >= HttpServletResponse.SC_BAD_REQUEST){
					//task.log.log();
					task.log.warn(String.format("Failed (%d) task: %s", task.log.indexedException.index, task.toString()));
					throw task.log.indexedException;
				}
				else {
					task.log.ok(String.format("Completed task: %s", task.toString()));
				}

				//if (task.actions.involves(Actions.MAKE) && (task.log.getStatus() >= Log.NOTE)) { // Critical to ORDER!
				final boolean statusOK = (task.log.getStatus() >= Log.NOTE);
				//if (task.actions.involves(Actions.MAKE|Actions.GENERATE) && (task.log.getStatus() >= Log.NOTE)) { // Critical to ORDER!

				if (statusOK && task.actions.isSet(Actions.STREAM)) {
					sendToStream(task, response);
					return;
				}
				else if (statusOK && task.actions.isSet(Actions.REDIRECT)) {
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

					if (!task.actions.isSet(ProductServer.Actions.TEST)) {
						sendStatusPage(HttpServletResponse.SC_OK, "Product request completed",
								os.toString("UTF8"), request, response);
						return;
					}
				}

				response.setStatus(HttpServletResponse.SC_OK); // tes
			}
			catch (IndexedException e) {

				response.setStatus(e.index);

				task.actions.add(Actions.TEST);
				task.actions.add(Actions.TEST);
				task.actions.add(Actions.INPUTLIST);
				/*
				switch (e.index){
					case HttpServletResponse.SC_PRECONDITION_FAILED:
						html.appendElement(SimpleHtml.H1, "Input problem(s)");
						html.appendElement(SimpleHtml.P, "See input list further below");
						break;
					default:
						html.appendElement(SimpleHtml.H1, "Product generator error");
				}

				html.appendElement(SimpleHtml.PRE, e.getMessage()).setAttribute("class", "error");

				 */

			}

			html.appendElement(SimpleHtml.H1, "Product: " + task.info.PRODUCT_ID);
			//html.createElement(SimpleHtml.P, "No ST REAM or REDIRECT were requested, so this page appears.");

			Element elem = html.createElement(SimpleHtml.PRE, task.log.indexedException.getMessage());

			switch (task.log.indexedException.index){
				case HttpServletResponse.SC_PRECONDITION_FAILED:
					html.appendElement(SimpleHtml.H2, "Input problem(s)");
					html.appendElement(SimpleHtml.P, "See input list further below");
					elem.setAttribute("class", "error");
					break;
				default:
					// General, section-wise handling
					if (task.log.indexedException.index > 400) {
						html.appendElement(SimpleHtml.H2, "Product generator error");
						elem.setAttribute("class", "error");
					}
					else if (task.log.indexedException.index < 200)
						elem.setAttribute("class", "note");
			}

			html.appendElement(elem);


			if (task.actions.isSet(Actions.TEST)) {

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

					map.put("Output dir", html.createAnchor(relativePath.getParent(), null));
				}
				else {
					map.put("Output file", "???");
				}

				// NOTE: these assume ExternalGenerator?
				Path gen = Paths.get("products", task.productDir.toString(), productServer.generatorCmd);
				map.put("Generator dir", html.createAnchor(gen.getParent(),null));
				map.put("Generator file", html.createAnchor(gen, gen.getFileName()));
				map.put("actions", actions);
				map.put("directives", task.directives);

				html.appendTable(map, "Product generator");


				// INPUTS
				if (!task.inputs.isEmpty()) {
					Map<String, Element> linkMap = new HashMap<>();
					for (Map.Entry<String, String> e : task.inputs.entrySet()) {
						String inputFilename = e.getValue();
						//elem = html.createAnchor("?request=CHECK&product="+e.getValue(), e.getValue());
						String url = String.format("%s%s?actions=TEST&product=%s", request.getContextPath(), request.getServletPath(), inputFilename);
						linkMap.put(e.getKey(), html.createAnchor(url, inputFilename));
						//Object inp = task.retrievedInputs.get(e.getKey());
						//if ()
						//linkMap.put("", html.createAnchor(task.retrievedInputs, "log"));
					}
					html.appendTable(linkMap, "Product inputs");
				}

				html.appendTable(task.getParamEnv(), "Product generator environment");
			}

			html.appendElement(SimpleHtml.H2, "Log");
			if ((task.log.logFile!=null) && task.log.logFile.exists()){
				html.appendElement(SimpleHtml.H3, "Task log");
				StringBuilder builder = new StringBuilder();
				BufferedReader reader = new BufferedReader(new FileReader(task.log.logFile));
				String line = null;
				while ((line = reader.readLine()) != null){
					// TODO: detect ERROR, WARNING, # etc and apply colours
					// Requires separate lines, so something other than StringBuilder
					// builder.append("<b>x</b>").append(line).append('\n');
					builder.append(line).append('\n');
				}
				html.appendElement(SimpleHtml.PRE, builder.toString()).setAttribute("class", "code");

			}
			html.appendElement(SimpleHtml.H3, "NutLet log");
			// log.warn("Nyt jotain");
			html.appendElement(SimpleHtml.PRE, os.toString("UTF-8")).setAttribute("class", "code");
			// html.appendTable(productInfo.getParamEnv(null), "Product parameters");

			html.appendElement(SimpleHtml.H3, "ProductServer log");
			//productServer.log.warn("Empty?");
			html.appendElement(SimpleHtml.PRE, String.format("Length=%d", productServer.log.buffer.length())).setAttribute("class", "code");
			html.appendElement(SimpleHtml.PRE, productServer.log.buffer.toString()).setAttribute("class", "code");

			html.appendElement(SimpleHtml.H3, "Corresponding command line");
			String cmdLine = "java -cp %s/WEB-INF/lib/Nutlet.jar %s  --verbose  --conf %s --actions %s %s";
			//  if (!task.directives.isEmpty())
			//	cmdLine += String.format("--directives %s", task.directives.toString());
			String name = productServer.getClass().getCanonicalName();
			html.appendElement(SimpleHtml.PRE, String.format(cmdLine, httpRoot, name, productServer.confFile, actions, task.info)).setAttribute("class", "code");

			html.appendTable(task.info.getParamEnv(null), "Product parameters");

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

	/** Reads and returns a html page template (template/main.html)
	 *
	 *  @return HTML page object
	 *
	 */
	protected SimpleHtml getHtmlPage(){ // throws IOException, SAXException, ParserConfigurationException {

		Path path = Paths.get(httpRoot,"template", "main.html");

		SimpleHtml html = null;
		try {
			html = new SimpleHtml(path);
		} catch (Exception e) { // throws IOException, SAXException, ParserConfigurationException {
			html = new SimpleHtml(this.getClass().getCanonicalName() + " Exception");
			Element elem = html.getUniqueElement(SimpleHtml.PRE, "errorMsg");
			elem.setTextContent(e.getMessage());
		}
		// DOES NOT WORK: html.document.getElementById("main");
		// https://docs.oracle.com/javase/6/docs/api/org/w3c/dom/Document.html#getElementById%28java.lang.String%29
		html.main = html.getUniqueElement(SimpleHtml.SPAN, "main"); // html.createElement(SimpleHtml.SPAN)	;

		Element elem = html.getUniqueElement(SimpleHtml.SPAN, "version");
		elem.setTextContent("Java Version (" + getClass().getSimpleName() + " " + version + ") built " + getServletConfig().getInitParameter("buildDate") + httpRoot);
		//elem.setTextContent("Java Version (" + getClass().getSimpleName() + " " + version + ") installed " + getServletConfig().getInitParameter("installDate"));
		return html;
	}

	protected SimpleHtml includeHtml(String filename){
		return includeHtml(filename, getHtmlPage());
	}

		/** Given HTML page object, reads a file and copies its BODY element to the page.
         *
         * @param html
         * @param filename
         */
	protected SimpleHtml includeHtml(String filename, SimpleHtml html){
		try {
			NodeList list = SimpleHtml.readBody(Paths.get(httpRoot, "template", filename).toString());
			for (int i=0; i< list.getLength(); ++i) {
				Node node = list.item(i);
				//System.out.println(node.getNodeName() + ':' + node.getTextContent());
				node = html.document.importNode(node, true);
				html.main.appendChild(node);
			}
		}
		catch (Exception e){
			html.appendElement(SimpleHtml.H2, "Failed reading template: " + filename);
			html.appendElement(SimpleHtml.PRE, e.toString());
		}
		return html;
	}


	protected void sendStatusPage(int status, String statusStr, Object description,
								  HttpServletResponse response) throws IOException{
		sendStatusPage(status, statusStr, description, null, response);
	}

	protected void sendStatusPage(int status, String statusStr, Object description,
								  HttpServletRequest request,
								  HttpServletResponse response) throws IOException{

		response.setStatus(status);
		//response.sendError();
		response.setContentType("text/html");

		SimpleHtml html = getHtmlPage();
		html.appendElement(SimpleHtml.H1, statusStr);
		html.appendElement(SimpleHtml.TT, "HttpResponse: " );
		html.appendElement(SimpleHtml.CODE, status + " " + MapUtils.getConstantFieldName(HttpServletResponse.class, status));
		if (description instanceof Exception){
			html.appendElement(SimpleHtml.H2, ((Exception)description).getMessage());
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			((Exception)description).printStackTrace(pw);
			html.appendElement(SimpleHtml.PRE, sw.toString());
		}
		else {
			//html.appendElement(SimpleHtml.H2, "Error"); (only if error...)
			html.appendElement(SimpleHtml.PRE, description.toString());
		}


		addRequestStatus(html, request);
		addServerStatus(html);

		SimpleXML.writeDocument(html.document, new StreamResult(response.getWriter()));

		if (status >= HttpServletResponse.SC_BAD_REQUEST){
			//response.sendError(status, statusStr);
		}

	}

	protected void addServerStatus(SimpleHtml html) throws IOException{

		html.appendElement(SimpleHtml.H1, "Nutlet setup");
		html.appendTable(setup, null);

		html.appendElement(SimpleHtml.H1, "ProductServer setup");
		html.appendTable(productServer.setup, null);
	}

	protected void addRequestStatus(SimpleHtml html, HttpServletRequest request) {
		if (request != null) {
			html.appendElement(SimpleHtml.H1, "Query string");
			html.appendTable(request.getParameterMap(), null);
			html.appendElement(SimpleHtml.H1, "HttpServletRequest");
			html.appendTable(getConf(request), null);
		}
	}

	/** Sends HTML document to stream
	 *
	 * @param document - HTML document
	 * @param response - response, output stream of which will be written to.
	 * @throws IOException
	 */
	void sendToStream(Document document, HttpServletResponse response) throws IOException {
		response.setContentType("text/html");
		SimpleXML.writeDocument(document, new StreamResult(response.getWriter()));
	}

	/**
	 * Sends a generated product to recipient OutputStream
	 * 
	 * @param productTask Creates an HTML document with a given title
	 *
	 *  in memory cache
	 */
	//  TODO: more carefully close resources.
	void sendToStream(Task productTask, HttpServletResponse response) throws IOException {
		productTask.log.debug("sendToStream: " + productTask.outputPath);
		try {
			sendToStream(productTask.outputPath, response);
		}
		catch (Exception e){

		}
	}
	/**
	 * Sends a file to recipient OutputStream. Guesses Content-Type from the file extension.
	 *
	 * @param filePath - system side path to a file
	 * @param response - output stream handler
	 *
	 *  in memory cache
	 */
	void sendToStream(Path filePath, HttpServletResponse response) throws IOException {

		//productTask.log.debug("sendToStream: " + productTask.outputPath);
		//log.debug("sendToStream: " + filePath);

		File file = filePath.toFile(); //productServer.getAbsoluteFilePath(info.getFilename()));
		FileInputStream fis = null;
		BufferedInputStream bis = null;

		try {
			String contentType; // = Files.probeContentType(filePath);
			if (filePath.getFileName().toString().endsWith(".sh")){
				contentType = "application/x-sh";
			}
			else {
				contentType = Files.probeContentType(filePath);
			}

			fis = new FileInputStream(file);    // throws IOException
			bis = new BufferedInputStream(fis);

			response.setStatus(HttpServletResponse.SC_OK); // test this
			response.setContentType(contentType);
			response.setContentLength((int)file.length());

			// Non-standard (suggest filename):
			response.setHeader("Content-Disposition", "inline; filename=\"" + filePath.getFileName() + "\"");

			OutputStream out = response.getOutputStream();
			int b;
			while (bis.available() > 0){
				b = bis.read();
				out.write(b);
			}
			out.close();

			fis.close();
			bis.close();
		}
		catch (IOException e) {
			if (fis != null)
				fis.close();

			if (bis != null)
				bis.close();
			//productTask.log.warn("Failed: " + e.getLocalizedMessage());
			throw e;
		}


	}


	/** Read values listed in init-param section servlet section in WEB-INF/web.xml
	 */
	private Map<String,String> readTomcatParameters(){

		HashMap<String,String> map = new HashMap<String, String>();
		try {
			Enumeration<String> names = getInitParameterNames();
			while (names.hasMoreElements()){
				String name = names.nextElement();
				if (name != null){
					map.put(name, getInitParameter(name));
				}
			}
		} catch (Exception e) {
			map.put("confError",e.getMessage());
		}
		return map;

	}




	///

	/** Return TomCat request parameters
	 *
	 * @param request
	 * @return
	 */
	protected Map<String, Object> getConf(HttpServletRequest request) {
		
		final Object[] empty = new Object[0];
				
		Map<String, Object> conf = new HashMap<String, Object>();

		// TODO: generalize in nutshell.ClassUtils? See also raiddeer.Configuration
		Method[] methods =  HttpServletRequest.class.getMethods();
		for (Method method : methods) {
			String name = method.getName();
			if (name.startsWith("get") && (method.getParameterCount()==0) && (method.getReturnType() != void.class)){
				Object value = null;
				try {
					value = method.invoke(request, empty);
				} catch (IllegalAccessException | IllegalArgumentException
						| InvocationTargetException e) {
					//e1.printStackTrace();
					value = e.getClass().getName();
				}
				if (value == null)
					value = "";
				conf.put(name, value.toString());
			
			}
				
		}
		
		return conf;
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
