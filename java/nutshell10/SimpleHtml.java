/**
 * 
 */
package nutshell10;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
		B,
		I,
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
		SUP,
		A,
		STYLE,
		SPAN,
		DIV,
		LINK;

		Tag(){
			lowerCaseName = name().toLowerCase();
			startStr = toString(true, null);
			endStr   = toString(false,null);
		}

		final String lowerCaseName;
		final String startStr;
		final String endStr;

		public String toString(boolean start, Map<String,?> attributes) {
		  	StringBuilder builder = new StringBuilder();
		  	builder.append('<');
		  	if (!start)
		  		builder.append('/');
			builder.append(lowerCaseName);
		  	if (attributes != null){
				builder.append(' ');
				for (Map.Entry<String,?> a: attributes.entrySet()){
		  			builder.append(a.getKey()).append('=').append('"').append(a.getValue()).append('"').append(' ');
				}
			}
			builder.append('>');
		  	return builder.toString();
		};


			@Override
		/** Returns the tag name in lowercase letters.
		 */
		public String toString() {
			//return super.toString().toLowerCase();
			return lowerCaseName;
		}

		/** Returns the tag name in lowercase letters.
		 */
		public String start(){
			return startStr;
		}

		public String end() {
			return endStr;
		}

		public static void writeDocument(Document document, StreamResult result) {

			try {
				result.getWriter().write("<!DOCTYPE html>\n");
			} catch (IOException e) {
				//  throw new RuntimeException(e);
			}

			SimpleXML.writeDocument(document, result);

		}
	}

	/** Interface for Objects the state of which can appear as HTML nodes.
	 *
	 */
	interface Nodifiable {

		Node getNode(Document basedoc);
	}

	final public Element root;
	final public Element head;
	final public Element encoding;
	final public Element title;
	final public Element body;
	public Element main;
	// protected Element lastElement = null;

	/** If true.
	 *
	 */
	static
	public boolean AUTO_ANCHORS = false;

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

		// Add style for automatic header anchors
		if (AUTO_ANCHORS)
			addAutoAnchorSupport();

		// Search for BODY element (should be unique)
		// ToDO: if BODY was not found at this point, move everything in HTML under BODY
		this.body = getUniqueElement(this.root, Tag.BODY);

		// Default element to append content with append<ELEMENT>() commands
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
		if (nodes.getLength() > 0){
			// Could throw exception if more than 1 elements?
			return (Element) nodes.item(0);
		}
		else {
			Element elem = this.createElement(tag);
			scope.appendChild(elem);
			return elem;
		}
	}

	/** If found, returns the element with given ID, else creates and appends it in doc.
	 *
	 * @param scope - element the scope of which will be searched
	 * @param tag - HTML tag, like "TABLE"
	 * @param id - standard HTML attribute "id"
	 * @return Desired element
	 */
	protected Element getUniqueElement(Element scope, Tag tag, String id){

		NodeList nodes = scope.getElementsByTagName(tag.toString());

		// Step 1: search
		final int N = nodes.getLength();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (node.hasAttributes()){
				Element elem = (Element) node;
				if (elem.getAttribute("id").equals(id))
					return elem;
			}
		}

		// Step 2: not found, so create one.
		Element elem = this.createElement(tag);
		elem.setAttribute("id", id);
		scope.appendChild(elem);
		return elem;
	}

	/*
	public Comment appendComment(String text){
		Comment comment = document.createComment(text);
		return this.main.appendChild();
		//return super.appendElement(this.main, document.createComment(text));
	}
	*/


	public Element appendTag(Tag tag, String text){
		Element elem = createElement(tag, text);
		return appendElement(elem);
		// return super.appendElement(this.main, tag.toString(), text);
	}

	public Element appendTag(Tag tag){
		Element elem = createElement(tag, null);
		return appendElement(elem);
		//return super.appendElement(this.main, tag.toString());
	}

	public Element appendElement(Element elem){
		this.main.appendChild(elem);
		if (AUTO_ANCHORS) {
			String tagName = elem.getTagName();
			// Add anchor to header elements H1, H2, H3,...
			if (tagName.codePointAt(0) == 'h'){
				createSuperAnchor(elem);
			}
		}
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
		Element element = appendElement(this.head, Tag.STYLE);
		element.setAttribute("type", "text/css");
		style = style.replace("{", "{\n   ");
		style = style.replace(";", ";\n   ");
		style = style.replace("}","\n}\n");
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

	public Element createAnchor(Object url) {
		return createAnchor(url, url.toString());
	}

	public Element appendAnchor(Object url, String text) {
		Element elem = createAnchor(url, text);
		this.main.appendChild(elem);
		return elem;
	}

	public Element appendAnchor(String url) {
		return appendAnchor(url, url);
	}

	// teSuperAnchor(node.getTextContent().trim().replaceAll("\\W",""));
	public Element createSuperAnchor(Node node) {
		Element elem = createSuperAnchor(node.getTextContent().trim().replaceAll("\\W",""));
		node.appendChild(elem);
		return elem;
	}


	public Element createSuperAnchor(String name) {
		Element elem = createElement(Tag.A);
		elem.appendChild(createElement(Tag.SUP, "∞"));
		elem.setAttribute("name", name);
		elem.setAttribute("href", "#" + name);
		elem.setAttribute("class", "anchor");
		return elem;
	}

	public Element addAutoAnchorSupport() {
		return appendStyleElement(".anchor {color:black; opacity:0; text-decoration:none}"
			+ ".anchor:hover {opacity:0.25}");
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
			if (value == null)
				tdVal.setTextContent("<null>");
			else if (value instanceof String[])
				tdVal.setTextContent(String.join(",", (String[])value));
			else if (value instanceof Path) {
				Path p = (Path)value;
				if (Files.exists(p)) {
					Flags flags = FileUtils.getPermissions(p);
					tdVal.setTextContent(String.format("%s [%s]", p.toString(), flags));
					/*
					tdVal.setTextContent(String.format("%s [exec=%b,read=%b,write=%b]", 
							p.toString(),
							Files.isExecutable(p),
							Files.isReadable(p),
							Files.isWritable(p)));
					*/					
				}
				else {
					tdVal.setTextContent(String.format("%s [does not exist]", 
							p.toString(),
							Files.isReadable(p),
							Files.isWritable(p)));
				}
			}
			else if (value instanceof Node)
				tdVal.appendChild((Node)value);
			else if (value instanceof Nodifiable)
				tdVal.appendChild(((Nodifiable)value).getNode(document));
			else
				tdVal.setTextContent(value.toString());
				//tdVal.setTextContent(value.toString()+value.getClass().getSimpleName());
		}

		return table;
	}

	// Todo: variadic str, str2, ...
	/*
	public static NodeList readBody(String path) throws ParserConfigurationException, IOException, SAXException {
		return readBody(Paths.get(path));
	}

	public static NodeList readBody(Path path) throws ParserConfigurationException, IOException, SAXException {
		return readNodes(path, Tag.BODY);
	}

	 */

	/** Retrieves the child nodes (directly contained elements) of the first tag of given name
	 *
	 * @param path
	 * @param tagName
	 * @return
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws SAXException
	 */
	public static NodeList getChildNodes(Path path, Object tagName) throws ParserConfigurationException, IOException, SAXException {
		Document doc = SimpleXML.readDocument(path);
		return getChildNodes(doc, tagName);
	}


	public static NodeList getChildNodes(Document doc, Object tagName) {
		NodeList nodeList = doc.getElementsByTagName(tagName.toString());
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

		SimpleHtml.AUTO_ANCHORS = true;

		SimpleHtml html = new SimpleHtml("Kokeilu");

		// Help
		if (args.length == 0){
			System.out.println("Writes SimpleHtml-test.html");
			System.out.println("Example:");
			String cp = System.getProperty("java.class.path");
			String cl = html.getClass().getCanonicalName();
			//System.out.println(System.getProperty("java.class.path"));
			System.out.printf("java -cp %s %s foo.html        # create doc and dump to foo.html", cp, cl);
			System.out.printf("java -cp %s %s test-body.html  # read body, embed, and dump", cp, cl);
			// System.out.println("java " + html.getClass().getCanonicalName() + " test-body.html  # read body");
			return;
		}

		// 1 arg: try to read file
		if (args.length == 1){

			// File file = new File(args[0]);
			html.title.setTextContent(args[0]);
			html.addAutoAnchorSupport();

			try {
				NodeList list = getChildNodes(Paths.get(args[0]), Tag.BODY);
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
					if (nodeName.equals(Tag.H1.lowerCaseName)){
						Element elem = html.createSuperAnchor(node.getTextContent().trim().replaceAll("\\W",""));
						node.appendChild(elem);
					}
					html.main.appendChild(node);

					//html.document.appendChild(node);
				}
			}
			catch (Exception e){
				String msg = e.getMessage();
				//System.err.println(msg);
				html.appendTag(Tag.H1, "A big error!");
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

		//html.appendComment("ABC");
		Comment comment = html.document.createComment("ABC");
		//System.out.println(comment.toString());
		html.body.appendChild(comment);

		File file = new File("SimpleHtml-test.html");
		System.out.println(String.format("Writing: %s", file.getAbsolutePath()));
		writeDocument(html.document, file);


		// File file2 = new File("SimpleHtml-test2.html");
		// writeDocument(html.document, file2);

	}

	/*
	// https://stackoverflow.com/questions/4142046/create-xml-file-using-java
	DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
	*/

	
	
}
