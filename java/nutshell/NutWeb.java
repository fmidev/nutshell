package nutshell;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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


public class NutWeb extends HttpServlet {

	String httpRoot = "";

	/** Name of the HTML document in which the other HTML doc will be embedded.
	 *
	 *  Uses BODY contents
	 *
	 */
	String htmlTemplate = "";


	private static final long serialVersionUID;
	static {
		serialVersionUID = 1293000393642243650L;
	}

	// static final public String version = "1.4";

	final Map<String,Object> setup;

	/**
	 *
	 */
	public NutWeb() {
		setup = new HashMap<>();
		GregorianCalendar startTime = new GregorianCalendar();
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
		setup.put("startTime",   simpleDateFormat.format(startTime.getTime()));
		setup.put("startTimeMs", startTime.getTimeInMillis());
		//setup.put("version", version);  // TODO
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		httpRoot = config.getInitParameter("htmlRoot");
		htmlTemplate = config.getInitParameter("htmlTemplate");
		if (htmlTemplate == null)
			htmlTemplate = "nutweb/template.html";
		setup.putAll(getTomcatParameters());
	}

	/*
	public String getInitParameter(ServletConfig config, String key, String defaultValue){
		String s = config.getInitParameter(key);
		if (s == null){
			return defaultValue;
		}
	}
	 */


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

		//productStr = "";

		/**  TODO: rename main.html to sth like layout.html or template.html
		 */
		String fileName = null;

		Map<String, String[]> parameterMap = request.getParameterMap();

		if (parameterMap.isEmpty()) {

			String uri = request.getRequestURI();

			fileName = uri.replace(request.getContextPath(), "");

			if (fileName.equals("/")){
				fileName = "index.html"; // could be "/index.html";
			}

			/*
			else if (fileName.endsWith("/")){
			}
			*/

			//Paths.get(httpRoot, fileName),
			//if (uri.endsWith(".html")) {
			if (! fileName.endsWith(".html")) {
				//pageName = uri.replace(request.getContextPath(), "");
				//}
				//else {
				super.doGet(request, response);
				/*
				sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "NutWeb request not understood",
						String.format("requestURI: '%s', fileName: '%s'", uri, fileName), request, response);
				 */
				return;
			}

		}
		else {

			fileName = request.getParameter("page");

			if (fileName.equals("resolve")){

				final Object requestUri = request.getAttribute("javax.servlet.error.request_uri");

				if (requestUri == null){
					sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "No request_uri for resolving URL", null, response);
					return;
				}
				else {
					fileName = requestUri.toString();
					// Trunc to filename only
					// Path path = Paths.get(requestUri.toString());
					//pageName = path.getFileName().toString();
				}

				//sendStatusPage(HttpServletResponse.SC_OK, "Yes!", requestUri, response);
				//return;
			}
			else if (fileName.equals("status")){
				sendStatusPage(HttpServletResponse.SC_OK, "Yes", "Well done", request, response);
				return;
			}

		}

		SimpleHtml html = includeHtml(fileName); // what if fail?

		//Element headTitle = html.getUniqueElement(html.head, SimpleHtml.Tag.TITLE);
		//if (headTitle.getAttribute("id").equals("auto")){

			//Element title = html.getUniqueElement(html.body, SimpleHtml.Tag.TITLE, "autoTile");
		//Element title = html.document.getElementById("autoTile");
		Element title = html.getUniqueElement(html.body, SimpleHtml.Tag.SPAN, "autoTile");
		//html.document.getElementById()
		if (title != null){
			//title.setTextContent("MAKI");
			if (html.title != null){
				title.setTextContent(html.title.getTextContent());
			}
			//html.appendTag(SimpleHtml.Tag.PRE, "Special");
		}

		/*
		NodeList nodeList = SimpleHtml.getChildNodes(html.document, SimpleHtml.Tag.BODY);
		final int N = nodeList.getLength(); // appending will modify :-E
		for (int i = 0; i<nodeList.getLength(); ++i){
			Node n =  nodeList.item(i);
			if (n instanceof Element){
				Element e = (Element)n;
				html.appendTag(SimpleHtml.Tag.PRE, String.format("<%s>: %s", n.getNodeName(), e.getAttribute("id")));
			}
		}
		*/

		Element base = html.getUniqueElement(html.head, SimpleHtml.Tag.BASE);
		base.setAttribute("href", request.getContextPath()+ '/'); // NEW 2022!

		// debug
		// html.appendElement(SimpleHtml.Tag.PRE, request.getContextPath());

		response.setStatus(HttpServletResponse.SC_OK); // tes
		sendToStream(html.document, response);
		return;
	}





	/** Reads and returns a html page template (template/main.html)
	 *
	 *  @return HTML page object
	 *
	 */
	protected SimpleHtml getHtmlPage(){ // throws IOException, SAXException, ParserConfigurationException {

		SimpleHtml html = null;
		try {
			//Path path = Paths.get(httpRoot,"template", "main.html");
			Path path = Paths.get(httpRoot, htmlTemplate);
			html = new SimpleHtml(path);
		} catch (Exception e) { // throws IOException, SAXException, ParserConfigurationException {
			html = new SimpleHtml(this.getClass().getCanonicalName() + " Exception");

			Element ul = html.getUniqueElement(html.body, SimpleHtml.Tag.UL, "list");
			html.appendElement(ul, SimpleHtml.Tag.LI, String.format("httpRoot: %s", httpRoot) );
			html.appendElement(ul, SimpleHtml.Tag.LI, String.format("httpTemplate: %s", htmlTemplate) );

			//html.appendElement(ul, SimpleHtml.Tag.LI, e.getMessage());
			html.appendTag(SimpleHtml.Tag.PRE, e.getMessage()).setAttribute("class", "error");
			html.appendTag(SimpleHtml.Tag.PRE, e.toString());
			for (StackTraceElement stackTraceElement: e.getStackTrace()) {
				html.appendTag(SimpleHtml.Tag.PRE, stackTraceElement.toString());
			}

			/*
			Element elem = html.getUniqueElement(html.body, SimpleHtml.Tag.PRE, "error");
			elem.setTextContent(e.toString()); //.getMessage());

			Element elem2 = html.getUniqueElement(html.body, SimpleHtml.Tag.PRE, "errorMsg");
			elem2.setTextContent(e.getMessage()); //.getMessage());

			Element elem3 = html.getUniqueElement(html.body, SimpleHtml.Tag.PRE, "fileName");
			elem3.setTextContent(path.toString());

			 */
		}
		// DOES NOT WORK: html.document.getElementById("main");
		// https://docs.oracle.com/javase/6/docs/api/org/w3c/dom/Document.html#getElementById%28java.lang.String%29
		html.main = html.getUniqueElement(html.body, SimpleHtml.Tag.SPAN, "main"); // html.createElement(SimpleHtml.Tag.SPAN)	;

		if (html.main == null){
			html.appendTag(SimpleHtml.Tag.PRE, "Error: could not set 'main' element");
		}

		Element elem = html.getUniqueElement(html.body, SimpleHtml.Tag.SPAN, "version");
		elem.setTextContent(String.format("Java Version (%s) root=%s template=%s", getClass().getSimpleName(), httpRoot, htmlTemplate));
		//elem.setTextContent("Java Version (" + getClass().getSimpleName() + " ?version? " +  ") built " + getServletConfig().getInitParameter("buildDate") + httpRoot);
		//elem.setTextContent("Java Version (" + getClass().getSimpleName() + " " + version + ") installed " + getServletConfig().getInitParameter("installDate"));
		return html;
	}

	protected SimpleHtml includeHtml(String filename){
		return includeHtml(filename, getHtmlPage());
	}

	/** Reads a file and copies its BODY element to a given HTML document.
	 *
	 * @param html
	 * @param filename
	 */
	protected SimpleHtml includeHtml(String filename, SimpleHtml html){

		try {

			Document doc = SimpleXML.readDocument(Paths.get(httpRoot, filename));

			try {
				//NodeList headNodes = SimpleHtml.readNodes(Paths.get(httpRoot, filename), "head");
				NodeList headNodes = SimpleHtml.getChildNodes(doc, SimpleHtml.Tag.HEAD);
				for (int i=0; i< headNodes.getLength(); ++i) {
					Node node = headNodes.item(i);

					if (node instanceof Element){
						Element elem = (Element)node;
						// Generalize for all unique elements?
						if ((html.title != null) && elem.getTagName().equals(SimpleHtml.Tag.TITLE.toString())){
							html.title.setTextContent(elem.getTextContent());
							continue;
						}
					}

					html.head.appendChild(html.document.importNode(node, true));

				}
				//return html;

			}
			catch (Exception e) {
				html.appendComment("No HEAD element, ok");
			}


			/// Notice: not appended to the end of BODY (html.body), but embedded in its SPAN "main".
			//NodeList body = SimpleHtml.readNodes(Paths.get(httpRoot, filename), "body");
			NodeList bodyNodes = SimpleHtml.getChildNodes(doc, SimpleHtml.Tag.BODY);
			for (int i=0; i< bodyNodes.getLength(); ++i) {

				Node node = bodyNodes.item(i);

				/*
				if (node instanceof Element) {
					Element elem = (Element) node;
					// Copy TITLE
					if ((html.title != null) && elem.getAttribute("id").equals("autoTile")){
						elem.setTextContent(html.title.getTextContent());
					}
				}
				*/

				html.main.appendChild(html.document.importNode(bodyNodes.item(i), true));
			}
		}
		catch (Exception e){
			html.appendTag(SimpleHtml.Tag.H2, "Failed in reading file: " + filename);
			html.appendTag(SimpleHtml.Tag.PRE, e.toString()).setAttribute("class", "error");
			// Element elem = html.appendTag(SimpleHtml.Tag.PRE, e.toString());
			// elem.setAttribute("class", "error");
			//html.appendTag(SimpleHtml.Tag.PRE, e.getMessage()).setAttribute("class", "error");
			//elem.
		}
		return html;
	}

	protected SimpleHtml combineHtml(Document doc, SimpleHtml html){

		try {
			String autoTitle = null;
			//NodeList headNodes = SimpleHtml.readNodes(Paths.get(httpRoot, filename), "head");
			NodeList headNodes = SimpleHtml.getChildNodes(doc, SimpleHtml.Tag.HEAD);
			for (int i=0; i< headNodes.getLength(); ++i) {
				Node node = headNodes.item(i);
				html.head.appendChild(html.document.importNode(node, true));

			}
		}
		catch (Exception e) {
			html.appendComment("No HEAD element, ok");
		}


		/// Notice: not appended to the end of BODY (html.body), but embedded in its SPAN "main".
		//NodeList body = SimpleHtml.readNodes(Paths.get(httpRoot, filename), "body");
		NodeList body = SimpleHtml.getChildNodes(doc, SimpleHtml.Tag.BODY);
		for (int i=0; i< body.getLength(); ++i) {
			html.main.appendChild(html.document.importNode(body.item(i), true));
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
		response.setContentType("text/html");

		SimpleHtml html = getHtmlPage();
		html.appendTag(SimpleHtml.Tag.H1, statusStr);
		html.appendTag(SimpleHtml.Tag.TT, "HttpResponse: " );
		html.appendTag(SimpleHtml.Tag.CODE, status + " " + MapUtils.getConstantFieldName(HttpServletResponse.class, status));
		if (description instanceof Exception){
			html.appendTag(SimpleHtml.Tag.H2, ((Exception)description).getMessage());
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			((Exception)description).printStackTrace(pw);
			html.appendTag(SimpleHtml.Tag.PRE, sw.toString());
		}
		else if (description instanceof Map){
			html.appendTable((Map) description, "Diagnostics");
		}
		else {
			//html.appendTag(SimpleHtml.Tag.H2, "Error"); (only if error...)
			html.appendTag(SimpleHtml.Tag.PRE, description.toString());
		}


		addRequestStatus(html, request);
		addServerStatus(html);

		SimpleXML.writeDocument(html.document, new StreamResult(response.getWriter()));

		if (status >= HttpServletResponse.SC_BAD_REQUEST){
			//response.sendError(status, statusStr);
		}

	}

	protected void addServerStatus(SimpleHtml html) throws IOException{

		html.appendTag(SimpleHtml.Tag.H1, "Nutlet setup");
		html.appendTable(setup, null);

		html.appendTag(SimpleHtml.Tag.H1, "Environment variables");
		html.appendTable(System.getenv(), null);

	}

	protected void addRequestStatus(SimpleHtml html, HttpServletRequest request) {
		if (request != null) {
			html.appendTag(SimpleHtml.Tag.H1, "Query string");
			html.appendTable(request.getParameterMap(), null);
			html.appendTag(SimpleHtml.Tag.H1, "HttpServletRequest");
			//html.appendTable(getConf(request), null);
			html.appendTable(MapUtils.getMethods(request), null);
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
	 * Sends a file to recipient OutputStream. Guesses Content-Type from the file extension.
	 *
	 * @param filePath - system side path to a file
	 * @param response - output stream handler
	 *
	 *  in memory cache
	 */
	void sendToStream(Path filePath, HttpServletResponse response) throws IOException {

		File file = filePath.toFile();
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
	private Map<String,String> getTomcatParameters(){

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

		NutWeb nut = new NutWeb();
		for (Map.Entry<?,?> entry: nut.setup.entrySet()) {
			System.out.println(entry.getKey() + ":" + entry.getValue());
		}

		SimpleHtml html = nut.getHtmlPage();
		System.out.print(html.toString());
		SimpleXML.writeDocument(html.document, System.out);

	}

}
