package nutshell;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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

	public Element createElement(String tag, String text){
		Element element = document.createElement(tag);
		if (text != null)
			element.setTextContent(text);
		return element;
	}


	public Element appendElement(Element parent, String tag, String text){
		Element element = createElement(tag, text);
		parent.appendChild(element);
		return element;
	}

	public Element appendElement(Element parent, String tag){
		return appendElement(parent, tag, null);
	}

	public static Transformer getTransformer() {
		
		// Make a transformer factory to create the Transformer
		TransformerFactory tFactory = TransformerFactory.newInstance();

		// Make the Transformer
		try {
			Transformer transformer = tFactory.newTransformer();
			// https://stackoverflow.com/questions/1384802/java-how-to-indent-xml-generated-by-transformer
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			return transformer;
		} catch (TransformerConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;

	}

	// Streamable: File or Writer
	public static void writeDocument(Document document, StreamResult result) {

		Transformer transformer = getTransformer();
		
		// Mark the document as a DOM (XML) source
		DOMSource source = new DOMSource(document);

		// Say where we want the XML to go
		// StreamResult result = new StreamResult(streamable);

		// Write the XML to file
		try {
			transformer.transform(source, result);
		} catch (TransformerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

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

		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		//an instance of builder to parse the specified xml file
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		Document document = docBuilder.parse(file);
		return document;

		/*
		try {
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document document = docBuilder.parse(file);
			return document;
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException | IOException e) {
			e.printStackTrace();
		}
		return null;
		 */
	}

	public static void main(String[] args){

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
			SimpleXML.writeDocument(doc, file);
			
			
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}