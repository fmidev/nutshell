package nutshell;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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


public class Nutlet extends NutWeb { //HttpServlet {

	private static final long serialVersionUID;
	static {
		serialVersionUID = 1293000393642243650L;
	}

	static final public String version = "1.4";

	//final Map<String,Object> setup;
	final ProductServer productServer;
	//final GregorianCalendar startTime;


	/**
	 * param arg Input
	 *
	 *
	 */
	public Nutlet() {
		/*
		setup = new HashMap<>();
		GregorianCalendar startTime = new GregorianCalendar();
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
		setup.put("startTime", simpleDateFormat.format(startTime.getTime()));
		setup.put("startTimeMs", startTime.getTimeInMillis());
		 */
		setup.put("version", version);  // TODO
		productServer = new ProductServer();
		productServer.serverLog.setVerbosity(Log.DEBUG);
		productServer.serverLog.note("Nutlet started");
	}

	//// String confDir = "";
	//// String httpRoot = "";

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		/** **
		confDir  = config.getInitParameter("confDir");
		setup.putAll(readTomcatParameters()); // from ho
		*/
		////httpRoot = productServer.setup.getOrDefault("HTTP_ROOT", ".").toString();
		productServer.readConfig(Paths.get(confDir, "nutshell.cnf")); // Read two times? Or NutLet?
		if (!productServer.serverLog.logFileIsSet()){
			productServer.setLogFile("/tmp/nutshell-tomcat-%s.log");
		}
	}
	
	/*
	@Override	
	public void doPost(HttpServletRequest request,HttpServletResponse response) throws IOException, ServletException {
		doGet(request, response);
	}

	 */


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

		Instructions instructions = new Instructions();

		for (String key: new String[]{"instructions", "actions", "output", "request"}) {  // request and action deprecating!
			String[] a = request.getParameterValues(key);
			if (a != null) {
				try {
					instructions.add(a);
				} catch (Exception e) {
					sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "Parsing 'request' failed:" + Arrays.toString(a), e.getMessage(), response);
					return;
				}
			}
		}

		if (instructions.isSet(ActionType.CLEAR_CACHE)) {
			instructions.remove(ActionType.CLEAR_CACHE);
			if (!instructions.isEmpty()){
				productServer.serverLog.warn(String.format("Discarding remaining instructions: %s", instructions) );
			}

			try {
				productServer.clearCache(false);
				sendStatusPage(HttpServletResponse.SC_OK, "Cleared cache",
						"OK", request, response);
			} catch (IOException e) {
				sendStatusPage(HttpServletResponse.SC_CONFLICT, "Clearing cache failed", e.getMessage(), response);
			}
			return;
		}


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




		// Default
		if (instructions.value == 0)
			instructions.set(Instructions.MAKE | Instructions.STREAM);


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
					instructions.set(Instructions.MAKE | Instructions.STREAM);
					break;
				}
			}

			if (productStr.isEmpty()){

				/*
				Flags f = new Flags(123);
				f.set(2);
				*/
				Map p = new HashMap();
				p.put("path", path);
				//p.put("Name", path.getName(0));
				p.put("Parent", path.getParent());
				// p.put("Root", path.getRoot());
				p.put("FileName", path.getFileName());

				/*
				StringBuffer sb = new StringBuffer();
				sb.append(requestUri);
				sb.append(": ").append(path.getName(1));
				sb.append(" -> ").append(path.getFileName());
				 */
				//MapUtils.getMap(path);
				//path.getFileName()
				sendStatusPage(HttpServletResponse.SC_BAD_REQUEST,
						//"Could not resolve not found (404) request.",
						String.format("Not-found-404 failed: %s: %s -> %s", requestUri, path.getName(1), path.getFileName()),
						//MapUtils.getMap(path, Modifier.PUBLIC),
						//MapUtils.getMethods(path),
						p, //MapUtils.getMethods(f),
						response);

				/*
				sendStatusPage(HttpServletResponse.SC_BAD_REQUEST,
						"Could not resolve not found (404) request.",
						String.format("%s: %s -> %s", requestUri, path.getName(1), path.getFileName()),
						response);
				 */
				return;
			}

		}



		try {

			// Debug log? OS
			//ByteArrayOutputStream os = new ByteArrayOutputStream();

			final String filename = productStr;

			// problem: parameter ordering may cause  filename != productStr
			// TODO: -> _link_ equivalent files?

			Task task = productServer.new Task(filename, instructions.value,null);

			if (task.instructions.isSet(Instructions.LATEST)){
				task.log.note("Action 'LATEST' not allowed in HTTP interface, discarding it");
				task.instructions.remove(Instructions.LATEST);
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

				if (statusOK && task.instructions.isSet(Instructions.STREAM)) {
					// sendToStream(task, response);
					task.log.debug("sendToStream: " + task.outputPath);
					try {
						sendToStream(task.outputPath, response);
						return;
					}
					catch (Exception e){
						task.instructions.add(Instructions.DEBUG);
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

					if (!task.instructions.isSet(Instructions.DEBUG)) {
						sendStatusPage(HttpServletResponse.SC_OK, "Product request completed",
								os.toString("UTF8"), request, response);
						return;
					}
				}

				response.setStatus(HttpServletResponse.SC_OK); // tes
			}
			catch (IndexedException e) {
				response.setStatus(e.index);
				task.instructions.add(Instructions.DEBUG);
				task.instructions.add(Instructions.DEBUG); // ?
				task.instructions.add(Instructions.INPUTLIST);
			}

			SimpleHtml html = getHtmlPage(); // slows production?

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


			if (task.instructions.isSet(Instructions.DEBUG)) {

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
				map.put("actions", instructions);
				map.put("directives", task.info.directives);

				html.appendTable(map, "Product generator");


				// INPUTS
				if (!task.inputs.isEmpty()) {
					Map<String, Element> linkMap = new HashMap<>();
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

			html.appendElement(SimpleHtml.H2, "Log");
			if ((task.log.logFile!=null) && task.log.logFile.exists()){
				html.appendElement(SimpleHtml.H3, "Task log");
				StringBuilder builder = new StringBuilder();
				BufferedReader reader = new BufferedReader(new FileReader(task.log.logFile));
				String line = null;
				while ((line = reader.readLine()) != null){
					// TODO: detect ERROR, WARNING, # etc and apply colours
					// Requires separate  lines, so something other than StringBuilder
					// builder.append("<b>x</b>").append(line).append('\n');
					builder.append(line).append('\n');
				}
				html.appendElement(SimpleHtml.PRE, builder.toString()).setAttribute("class", "code");

			}

			// html.appendElement(SimpleHtml.H3, "NutLet log");
			//html.appendElement(SimpleHtml.PRE, os.toString("UTF-8")).setAttribute("class", "code");
			// html.appendTable(productInfo.getParamEnv(null), "Product parameters");

			/*
			html.appendElement(SimpleHtml.H3, "ProductServer log");
			html.appendElement(SimpleHtml.PRE, String.format("Length=%d", productServer.log.buffer.length())).setAttribute("class", "code");
			html.appendElement(SimpleHtml.PRE, productServer.log.buffer.toString()).setAttribute("class", "code");
			 */
			html.appendElement(SimpleHtml.H3, "Corresponding command line");
			String cmdLine = "java -cp %s/WEB-INF/lib/Nutlet.jar %s  --verbose  --conf %s --actions %s %s";
			String name = productServer.getClass().getCanonicalName();
			html.appendElement(SimpleHtml.PRE, String.format(cmdLine, httpRoot, name, productServer.confFile, instructions, task.info)).setAttribute("class", "code");

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


	@Override
	protected void addServerStatus(SimpleHtml html) throws IOException{

		html.appendElement(SimpleHtml.H1, "Product Server setup");
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
