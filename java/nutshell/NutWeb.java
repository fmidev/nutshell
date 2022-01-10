package nutshell;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
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

	String confDir = "";
	String httpRoot = "";


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
		confDir  = config.getInitParameter("confDir");
		httpRoot = config.getInitParameter("htmlRoot");
		setup.putAll(getTomcatParameters()); // from ho

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

		//productStr = "";

		/**  TODO: rename main.html to sth like layout.html or template.html
		 */
		String pageName = null;

		Map<String, String[]> parameterMap = request.getParameterMap();

		if (parameterMap.isEmpty()) {

			String uri = request.getRequestURI();

			if (uri.endsWith(".html")) {
				pageName = uri.replace(request.getContextPath(), "");
			}
			else {
				sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "NutWeb request not understood",
						String.format("Query: %s", uri), request, response);
				return;
			}

		}
		else {

			pageName = request.getParameter("page");

			if (pageName.equals("resolve")){

				final Object requestUri = request.getAttribute("javax.servlet.error.request_uri");

				if (requestUri == null){
					sendStatusPage(HttpServletResponse.SC_BAD_REQUEST, "No request_uri for resolving URL", null, response);
					return;
				}
				else {
					pageName = requestUri.toString();
					// Trunc to filename only
					// Path path = Paths.get(requestUri.toString());
					//pageName = path.getFileName().toString();
				}

				//sendStatusPage(HttpServletResponse.SC_OK, "Yes!", requestUri, response);
				//return;
			}
			else if (pageName.equals("status")){
				sendStatusPage(HttpServletResponse.SC_OK, "Yes", "Well done", request, response);
				return;
			}

		}

		SimpleHtml html = includeHtml(pageName); // what if fail?

		Element base = html.getUniqueElement(SimpleHtml.BASE);
		base.setAttribute("href", request.getContextPath()+ '/'); // NEW 2022!

		// debug
		// html.appendElement(SimpleHtml.PRE, request.getContextPath());

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

		Path path = Paths.get(httpRoot,"template", "main.html");

		SimpleHtml html = null;
		try {
			html = new SimpleHtml(path);
		} catch (Exception e) { // throws IOException, SAXException, ParserConfigurationException {
			html = new SimpleHtml(this.getClass().getCanonicalName() + " Exception");
			Element elem = html.getUniqueElement(SimpleHtml.PRE, "errorMsg");
			elem.setTextContent(e.getMessage());

			Element elem2 = html.getUniqueElement(SimpleHtml.PRE, "fileName");
			elem2.setTextContent(path.toString());
		}
		// DOES NOT WORK: html.document.getElementById("main");
		// https://docs.oracle.com/javase/6/docs/api/org/w3c/dom/Document.html#getElementById%28java.lang.String%29
		html.main = html.getUniqueElement(SimpleHtml.SPAN, "main"); // html.createElement(SimpleHtml.SPAN)	;

		Element elem = html.getUniqueElement(SimpleHtml.SPAN, "version");
		elem.setTextContent(String.format("Java Version (%s) root=%s ", getClass().getSimpleName(), httpRoot));
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
			//NodeList list = SimpleHtml.readBody(Paths.get(httpRoot, "template", filename).toString());
			NodeList list = SimpleHtml.readBody(Paths.get(httpRoot, filename));
			for (int i=0; i< list.getLength(); ++i) {
				Node node = list.item(i);
				//System.out.println(node.getNodeName() + ':' + node.getTextContent());
				node = html.document.importNode(node, true);
				html.main.appendChild(node);
			}
		}
		catch (Exception e){
			html.appendElement(SimpleHtml.H2, "Failed in reading file: " + filename);
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
		else if (description instanceof Map){
			html.appendTable((Map) description, "Diagnostics");
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

		html.appendElement(SimpleHtml.H1, "Environment variables");
		html.appendTable(System.getenv(), null);

	}

	protected void addRequestStatus(SimpleHtml html, HttpServletRequest request) {
		if (request != null) {
			html.appendElement(SimpleHtml.H1, "Query string");
			html.appendTable(request.getParameterMap(), null);
			html.appendElement(SimpleHtml.H1, "HttpServletRequest");
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




	///

	/** Return TomCat request parameters
	 *
	 * @param request
	 * @return
	 */
	/*
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

	 */


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
