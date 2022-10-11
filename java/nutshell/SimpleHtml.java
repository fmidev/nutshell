/**
 * 
 */
package nutshell;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
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



	}

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


		SimpleHtml html = new SimpleHtml("Kokeilu");

		// Help
		if (args.length == 0){
			System.out.println("Writes SimpleHtml-test.html");
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
