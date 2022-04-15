/**
 * 
 */
package nutshell;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;


//import java.io.PrintStream;

/**
 * @author mpeura ,  Aug 2020
 *
 */
public class SimpleHtml extends SimpleXML{

	enum Tag {
		HTML,
		HEAD,
		TITLE,
		META,
		BASE,
		BODY,
		H1,
		H2,
		H3,
		H4,
		H5,
		EMBED,
		IMG,
		TABLE,
		TR,
		TH,
		TD,
		UL,
		OL,
		LI,
		P,
		PRE,
		CODE,
		TT,
		A,
		STYLE,
		SPAN,
		DIV,
		LINK;

		@Override
		public String toString() {
			return super.toString().toLowerCase();
		}
	}

	/** Html starying tag.
	 */
	/*
	public static final String HTML  = "html";
	public static final String HEAD  = "head";
	public static final String TITLE = "title";
	public static final String META  = "meta";
	public static final String BASE  = "base";
	public static final String BODY  = "body";
	public static final String H1    = "h1";
	public static final String H2    = "h2";
	public static final String H3    = "h3";
	public static final String H4    = "h4";
	public static final String H5    = "h5";
	public static final String IMG   = "img";
	public static final String TABLE = "table";
	public static final String TR    = "tr";
	public static final String TH    = "th";
	public static final String TD    = "td";
	public static final String LI    = "li";
	public static final String P     = "p";
	public static final String PRE   = "pre";
	public static final String CODE  = "code";
	public static final String TT    = "tt";
	*/
	/** Html anchor (link) tag.
	 */
	/*
	public static final String A     = "a";
	public static final String STYLE = "style";
	public static final String SPAN = "span";
	public static final String DIV   = "div";
	public static final String LINK  = "link";
	*/

	final public Element root;
	final public Element head;
	final public Element encoding;
	final public Element title;
	final public Element body;
	public Element main;
	// protected Element lastElement = null;


	/** Constructor that creates an empty document with title @title.
	 *
	 * @param title - Title appearing in browser header and/or tab.
	 */
	public SimpleHtml(String title) {
		
		/*
		  https://stackoverflow.com/questions/29041855/how-can-i-build-an-html-org-w3c-dom-document
		DocumentType docType = new DocumentTypeImpl(null, "html",
				"-//W3C//DTD XHTML 1.0 Strict//EN",
				"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd");
		document.appendChild(docType);
		*/
		this.root = this.document.createElementNS("http://www.w3.org/1999/xhtml", "html");
		this.document.appendChild(this.root);

		this.head = this.appendElement(this.root, Tag.HEAD);

		// <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
		this.encoding = this.appendElement(this.head, Tag.META);
		this.encoding.setAttribute("http-equiv", "Content-Type");
		this.encoding.setAttribute("content", "text/html; charset=UTF-8");

		this.title = this.appendElement(this.head, Tag.TITLE);
		if (title != null)
			this.title.setTextContent(title.toString());

		this.body = this.appendElement(this.root, Tag.BODY);
		this.main = this.body;
	}


	/** Constructor of a document containing the body retrieved from HTML file @path.
	 *
	 * @param path - file to be read
	 */
	public SimpleHtml(Path path)  throws ParserConfigurationException, IOException, SAXException {
		super(path);

		this.root = document.getDocumentElement();

		// Search for HEAD element (should be unique)
		this.head = getUniqueElement(this.root, Tag.HEAD);
		// Override...
		this.encoding = this.appendElement(this.head, Tag.META);
		this.encoding.setAttribute("http-equiv", "Content-Type");
		this.encoding.setAttribute("content", "text/html; charset=UTF-8");

		this.title = getUniqueElement(this.head, Tag.TITLE);
		if (this.title.getParentNode() == null){ // ???
			this.head.appendChild(this.title); // REMOVE!
		}

		// Search for BODY element (should be unique)
		this.body = getUniqueElement(this.root, Tag.BODY);
		this.main = this.body;
	}

	/** Returns an element, or creates it if missing.
	 *
	 *  If an element is
	 *
	 * @param tag - HTML tag, like "TITLE"
	 * @return
	 */
	protected Element getUniqueElement(Element scope, Tag tag) {

		NodeList nodes = scope.getElementsByTagName(tag.toString());
		if (nodes.getLength() == 0){
			Element elem = this.createElement(tag);
			scope.appendChild(elem);
			return elem;
		}
		else {
			// Could throw exception if more than 1 elements?
			return (Element) nodes.item(0);
		}
	}

	/** If found, returns the element with given ID, else creates and appends it in doc.
	 *
	 * @param tag - HTML tag, like "TABLE"
	 * @param id - standard HTML attribute "id"
	 * @return Desired element
	 */
	protected Element getUniqueElement(Element scope, Tag tag, String id){

		NodeList nodes = scope.getElementsByTagName(tag.toString());
		final int N = nodes.getLength();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (node.hasAttributes()){
				Element elem = (Element) node;
				if (elem.getAttribute("id").equals(id))
					return elem;
			}
		}
		Element elem = this.createElement(tag);
		elem.setAttribute("id", id);
		scope.appendChild(elem);
		return elem;
	}


	public Element appendComment(String text){
		return super.appendElement(this.main, document.createComment(text));
	}


	public Element appendTag(Tag tag, String text){
		return super.appendElement(this.main, tag.toString(), text);
	}

	public Element appendTag(Tag tag){
		return super.appendElement(this.main, tag.toString());
	}

	public Element appendElement(Element elem){
		this.main.appendChild(elem);
		return elem;
	}


	public Element appendStyleLink(String link){
		//<link href="/directory/css/style.css" rel="stylesheet">
		Element element = appendElement(this.head, Tag.LINK);
		element.setAttribute("href", link);
		element.setAttribute("rel", "stylesheet");
		return element;
	}

	public Element appendStyleElement(String style){
		Element element = appendElement(this.head, Tag.STYLE, style);
		element.setAttribute("type", "text/css");
		element.setTextContent(style);
		return element;
	}

	public Element createAnchor(Object url, Object text) {
		if (text == null)
			text = url;
		Element elem = createElement(Tag.A, text.toString());
		elem.setAttribute("href",  url.toString());
		elem.setAttribute("target", "_new");
		return elem;
	}

	public Element appendAnchor(Object url, String text) {
		Element elem = createAnchor(url, text);
		this.main.appendChild(elem);
		return elem;
	}

	public Element appendAnchor(String url) {
		return appendAnchor(url, url);
	}

	public <K,V> Element appendTable(Map<K,V> map, String title){ //} throws IOException {

		Element table = this.appendTag(Tag.TABLE);

		if (title != null){
			Element tr = this.appendElement(table, Tag.TR);
			Element th = this.appendElement(tr, Tag.TH);
			th.setAttribute("colspan", "2");
			th.setAttribute("class", "LEAD");
			th.setTextContent(title);

		}

		for (Map.Entry<K, V> entry : map.entrySet()){
			Element tr = this.appendElement(table, Tag.TR);
			Element tdKey = this.appendElement(tr, Tag.TD);
			tdKey.setAttribute("class", "KEY");
			tdKey.setTextContent(entry.getKey().toString());
			Element tdVal = this.appendElement(tr, Tag.TD);
			tdVal.setAttribute("class", "value");
			V value = entry.getValue();
			if (value != null) {
				//if (value.getClass().isArray())
				if (value instanceof String[]) {
					tdVal.setTextContent(String.join(",", (String[])value));
					//tdVal.setTextContent(Arrays.toString((String[])value));
				}
				else if (value instanceof Node)
					tdVal.appendChild((Node)value);
				else
					tdVal.setTextContent(value.toString());
			}
		}

		return table;
	}

	// Todo: variadic str, str2, ...
	public static NodeList readBody(String path) throws ParserConfigurationException, IOException, SAXException {
		return readBody(Paths.get(path));
	}

	public static NodeList readBody(Path path) throws ParserConfigurationException, IOException, SAXException {
		return readNodes(path, "body");
		/*
		Document comp = SimpleXML.readDocument(path);
		NodeList body = comp.getElementsByTagName("body");
		final int nodes = body.getLength();
		if (nodes == 0){
			// Consider reading <HTML> contents?
			throw new NullPointerException("File contained no 'body' element");
		}
		else if (nodes > 1){
			//System.err.println("File contains several 'body' elements");
		}
		return body.item(0).getChildNodes();

		 */
	}

	/** Retrieves the nodes (elements) of the first tag of given name
	 *
	 * @param path
	 * @param tagName
	 * @return
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws SAXException
	 */
	public static NodeList readNodes(Path path, String tagName) throws ParserConfigurationException, IOException, SAXException {
		Document comp = SimpleXML.readDocument(path);
		NodeList nodeList = comp.getElementsByTagName(tagName);
		final int nodes = nodeList.getLength();
		if (nodes == 0){
			// Consider reading <HTML> contents?
			throw new NullPointerException(String.format("File contained no '%s' element", tagName));
		}
		else if (nodes > 1){
			//System.err.println("File contains several 'body' elements");
		}
		return nodeList.item(0).getChildNodes();
	}

	public static void main(String[] args) {


		SimpleHtml html = new SimpleHtml("Kokeilu");

		// Help
		if (args.length == 0){
			System.out.println("Example:");
			System.out.println("java " + html.getClass().getCanonicalName() + " foo.html  # create doc and dump");
			System.out.println("java " + html.getClass().getCanonicalName() + " test-body.html  # read body");
			return;
		}


		// 1 arg: try to read file
		if (args.length == 1){

			// File file = new File(args[0]);
			html.title.setTextContent(args[0]);

			try {
				NodeList list = readBody(args[0]);
				//Document comp = SimpleXML.readDocument();

				for (int i=0; i< list.getLength(); ++i) {
					Node node = list.item(i);
					String nodeName = node.getNodeName();
					if (nodeName.equals("#text"))
						System.out.print(node.getTextContent());
					else
						System.out.println("[" + nodeName + "]:" + node.getTextContent());
					//node.cloneNode()
					node = html.document.importNode(node, true);
					//
					html.main.appendChild(node);
					//html.document.appendChild(node);
				}
			}
			catch (Exception e){
				String msg = e.getMessage();
				//System.err.println(msg);
				Element li = html.appendTag(Tag.PRE);
				li.setTextContent(msg);
				//html.title.setTextContent(msg);
			}
			// return;
		}
		else {
			for (String arg : args) {
				Element li = html.appendTag(Tag.LI);
				li.setTextContent(arg);
				//html.title.setTextContent(arg); //?
			}
		}


		File file = new File("SimpleHtml-test.html");
		writeDocument(html.document, file);


		// File file2 = new File("SimpleHtml-test2.html");
		// writeDocument(html.document, file2);

	}

	/*
	public static Document createDocument(){
		
		// https://stackoverflow.com/questions/4142046/create-xml-file-using-java
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		try {

			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

			Document doc = docBuilder.newDocument();

			Element rootElement = doc.createElement("company");
			doc.appendChild(rootElement);

			//staff elements
			Element staff = doc.createElement("Staff");
			rootElement.appendChild(staff);

			//set attribute to staff element
			Attr attr = doc.createAttribute("id");
			attr.setValue("1");
			staff.setAttributeNode(attr);

			System.out.println(doc.toString());
			
			File file = new File("mika.html");
			SimpleXML.writeDocumentToFile(doc, file);
			
			
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	*/
	
	/*
	public SimpleHtml(Writer writer) {
		this.writer = writer;
	}

	/// Begins a document
	SimpleHtml begin(String title) throws IOException{
		return begin(title, null);
	}
	
	/// Begins a document
	SimpleHtml begin(String title, String styleFile) throws IOException{
		writer.append("<html>\n");
		writer.append("<head>\n");
		writer.append("<meta http-equiv=\"Content-Type\" content=\"text/xhtml;charset=UTF-8\"/ >\n");
		if (title != null)
			writer.append("<title>").append(title).append("</title>\n");
		if (styleFile != null)
			writer.append("<link href=\"").append(styleFile).append("\" rel=\"stylesheet\" type=\"text/css\">\n");
		writer.append("</head>\n");
		writer.append("<body bgcolor=\"white\">\n");
		return this;
	}
	
	SimpleHtml end() throws IOException{
		closeTag();
		writer.append('\n');
		writer.append("</body>\n");
		writer.append("</html>\n");
		writer.flush();
		return this;
	}

	SimpleHtml flush() throws IOException{
		String t = currentTag;
		boolean n = newLine;
		closeTag();
		writer.flush();
		//openTag(t,n);
		return this;
	}

	SimpleHtml style(String s) {
		style = s;
		return this;
	}

	SimpleHtml append(CharSequence s) throws IOException{
		writer.append(s);
		return this;
	}

	SimpleHtml append(char c) throws IOException{
		writer.append(c);
		return this;
	}

	SimpleHtml appendln(CharSequence s) throws IOException{
		writer.append(s).append('\n');
		return this;
	}

	SimpleHtml appendln() throws IOException{
		writer.append('\n');
		return this;
	}
	
	SimpleHtml p(String s)  throws IOException{
		openTag("p",true);
		writer.append(s);
		return this;
	}

	SimpleHtml pre(String s)  throws IOException{
		openTag("pre",true);
		writer.append(s);
		return this;
	}

	SimpleHtml span(String s)  throws IOException{
		openTag("span", false);
		writer.append(s);
		return this;
	}

	
	SimpleHtml a(String link, String s, String target)  throws IOException{
		if (s == null)
			s = link;
		writer.append("<a href=\"").append(link).append("\" target=\"").append(target).append("\">").append(s).append("</a>");
		return this;
	}
	
	SimpleHtml a(String link, String s)  throws IOException{
		writer.append("<a href=\"").append(link).append("\">").append(s).append("</a>");
		return this;
	}

	SimpleHtml a(String link)  throws IOException{
		return a(link,link);
	}

	
	SimpleHtml h1(String s)  throws IOException{
		openTag("h1",true);
		writer.append(s);
		return this;
	}


	SimpleHtml h2(String s)  throws IOException{
		openTag("h2",true);
		writer.append(s);
		return this;
	}

	SimpleHtml h3(String s)  throws IOException{
		openTag("h3",true);
		writer.append(s);
		return this;
	}

	SimpleHtml h4(String s)  throws IOException{
		openTag("h4",true);
		writer.append(s);
		return this;
	}

	
	 <K,V> SimpleHtml table(Map<K,V> map, String title) throws IOException {
		closeTag();
		
		writer.append("<table border=\"1\">\n");
		
		if (title != null)
			writer.append("<tr><th colspan=\"2\">").append(title).append("<tr><th>\n");
		
		for (Map.Entry<K, V> entry : map.entrySet()) 
			writer.append("  <tr><td>"+entry.getKey()+"</td><td>"+entry.getValue()+"</td></tr>\n");
		
		writer.append("</table>\n");
		
		return this;
	}

	 <K,V> SimpleHtml table(Map<K,V> map) throws IOException {
		 return table(map, null);
	 }
	 
	protected Writer writer;
	protected String currentTag = "";
	protected boolean newLine = false;
	
	protected String style = null;
	

	protected void openTag(String tag, boolean newLine) throws IOException{
		closeTag();
		currentTag = tag;
		this.newLine = newLine;
		writer.append('<').append(currentTag);
		if (style != null){
			writer.append(" style=\"").append(style).append('"');
			style = null;
		}
		writer.append('>');
		if (newLine){
			writer.append('\n');
		}
	}

	protected void closeTag() throws IOException{
		if (newLine)
			writer.append('\n');
		if (!currentTag.isEmpty()){			
			writer.append("</").append(currentTag).append('>');
			if (newLine)
				writer.append('\n').append('\n');
		}
		currentTag = "";
		newLine = false;
	}
	
	
	*/

	/**
	 * @param args
	 */
	public static void main2(String[] args) {
		// TODO Auto-generated method stub
		/*
		PrintWriter p = new PrintWriter(System.out);
		SimpleHtml writer = new SimpleHtml(p);
		try {
			writer.begin("Keijo");
			writer.pre("Reijo ");
			writer.append("vaan jatkuu");
			p.flush();
			writer.p("Reijo ");
			writer.append("vaan jatkuu");
			writer.pre("Reijo ");
			writer.append("vaan jatkuu");
			writer.pre("Reijo ");
			writer.append("vaan jatkuu");
			writer.end();
		} catch (IOException e) {
			e.printStackTrace();
		}
		*/
	}
	
	
}
