package nutshell;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.dom.DOMSource; 
import javax.xml.transform.stream.StreamResult;

import java.io.*;
import java.nio.file.Path;
//import java.io.PrintWriter;
//import java.io.Writer;


// https://stackoverflow.com/questions/4142046/create-xml-file-using-java
	
public class SimpleXML {

	final public Document document;

	SimpleXML(){
		this.document = createDocument();
		this.document.setXmlStandalone(true);
		this.document.setXmlVersion("1.0");
	}

	SimpleXML(Path path) throws ParserConfigurationException, IOException, SAXException {
		this.document = readDocument(path);
	}


	static public Document createDocument(){

		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			return docBuilder.newDocument();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;

	}

	public <T> Element createElement(T tag){
		// Element element = document.createElement(tag);
		return document.createElement(tag.toString());
	}

	public <T> Element createElement(T tag, String text){
		Element element = document.createElement(tag.toString());
		if (text != null)
			element.setTextContent(text);
		return element;
	}


	public <T> Element appendElement(Element parent, T tag, String text){
		Element element = createElement(tag, text);
		parent.appendChild(element);
		return element;
	}

	public <T> Element appendElement(Element parent, T tag){
		return appendElement(parent, tag, null);
	}

	public static Node prune(Node node){

		NodeList list = node.getChildNodes();
		for (int i=0; i< list.getLength(); ++i) {
			Node child = list.item(i);
			prune(child);
			String text = child.getTextContent();
			if (!text.isEmpty()){
				if (text.trim().isEmpty()) {
					child.setTextContent("");
				}
			}
		}

		return node;
	}

	public static Transformer getTransformer(int indent) {
		
		// Make a transformer factory to create the Transformer
		TransformerFactory tFactory = TransformerFactory.newInstance();

		// Make the Transformer
		try {
			Transformer transformer = tFactory.newTransformer();
			// https://stackoverflow.com/questions/1384802/java-how-to-indent-xml-generated-by-transformer
			if (indent > 0) {
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.format("%d", indent));
			}
			return transformer;
		} catch (TransformerConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;

	}

	// Streamable: File or Writer
	public static void writeDocument(Document document, StreamResult result) {

		Transformer transformer = getTransformer(2);
		
		// Mark the document as a DOM (XML) source
		DOMSource source = new DOMSource(document);

		// Say where we want the XML to go
		// StreamResult result = new StreamResult(streamable);

		// Write the XML to file
		try {
			transformer.transform(source, result);
		} catch (TransformerException e) {
			// TODO Auto-generated catch block
			// result.getOutputStream()
			e.printStackTrace();
		}
	}

	/*
	public static <T> void writeDocument(Document document, T file) {
		System.out.println(file); // OK
		writeDocument(document,  new StreamResult(file)); // NOT OK
	}
	 */


	public static void writeDocument(Document document, File file) {
		writeDocument(document,  new StreamResult(file));
	}

	public static void writeDocument(Document document, OutputStream stream) {
		writeDocument(document,  new StreamResult(stream));
	}

	public static void writeDocument(Document document, PrintWriter writer) {
		writeDocument(document,  new StreamResult(writer));
	}


	static public Document readDocument(Path path) throws ParserConfigurationException, IOException, SAXException {
		return readDocument(path.toFile());
	}

	static public Document readDocument(String path) throws ParserConfigurationException, IOException, SAXException {
		return readDocument(new File(path));
	}

	static public Document readDocument(File file) throws ParserConfigurationException, IOException, SAXException {

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

		// https://stackoverflow.com/questions/6204827/xml-parsing-too-slow
		factory.setNamespaceAware(false);
		factory.setValidating(false);
		factory.setFeature("http://xml.org/sax/features/namespaces", false);
		factory.setFeature("http://xml.org/sax/features/validation", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

		DocumentBuilder docBuilder = factory.newDocumentBuilder();
		Document document = docBuilder.parse(file);

		return document;

	}

	public static void main(String[] args){

		String fileName = null;

		// https://stackoverflow.com/questions/4142046/create-xml-file-using-java

		Document doc = null;
		Node rootElement = null;

		if (args.length == 0) {
			try {
				DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
				doc = docBuilder.newDocument();
				rootElement = doc.createElement("company");
				doc.appendChild(rootElement);
			}
			catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(-1);
			}
		}
		else {
			try {
				doc = SimpleXML.readDocument(args[0]);
				prune(doc);
				rootElement = doc.getFirstChild();
			} catch (ParserConfigurationException | IOException | SAXException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}

		//Document doc = docBuilder.newDocument();

		//Element rootElement = doc.createElement("company");


		//staff elements
		Element staff = doc.createElement("Staff");
		rootElement.appendChild(staff);


		//set attribute to staff element
		// == staff.setAttribute("id", "1");
		Attr attr = doc.createAttribute("id");
		attr.setValue("1");
		staff.setAttributeNode(attr);

		System.out.println(doc.toString());

		File file = new File("out.xml");
		SimpleXML.writeDocument(doc, file);

		//SimpleHtml.readDocument()
			

	}
}