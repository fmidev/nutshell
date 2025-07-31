package nutshell9;


//import com.sun.istack.internal.NotNull;

// import javax..bind.Element;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import javax.validation.constraints.NotNull;

// import javax.annotation.Nonnull;


class Entity {

    Entity(){
    }

    Entity(String name){
        setName(name);
    }

    // final
    String id; // = "?";

    String setId(String name){
        id = name.replaceAll("\\W", " ").trim().replaceAll("\\s+", "_").intern();
        return id;
    }

    String getId(){
        return id;
    }

    /*
    public boolean equals(Entity entity) {
        return entity.id == this.id;
    }
     */

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // Uses String.intern, so accessible only with setter and getter.
    private String name = "?";

    @NotNull
    public void setName(String name) {
        this.name = name.intern();
        // TODO: setID only ig unset?
        setId(name);
        if (!id.equals(name))
            attributes.put("label", name);
    }

    public String getName() {
        return name;
    }



    public Map<String, Object> attributes = new HashMap<>();

    void attributesToStream(PrintStream stream){
        if (!attributes.isEmpty()){
            stream.append('[');
            String separator = "";
            for (Map.Entry<String, Object> entry: attributes.entrySet()) {
                stream.append(separator);
                stream.append(entry.getKey()).append('=').append('"').append(entry.getValue().toString()).append('"');
                separator = ", ";
            }
            stream.append(']');
        }
    }

    /// Default implementation.
    void toStream(PrintStream stream){
        stream.append("  ").append(getId()).append(' ');
        attributesToStream(stream);
        stream.append(';');
        stream.println();
    }


}

public class Graph extends Entity {

    Graph(String name){
        super(name);
        // System.err.println("Created: " + name);
        nodeProto = new Node("node");
        graphProto = new Entity("graph");
        graphProto.attributes = attributes;
        //setName(name);
    }

    //String styleSheet = "";

    public class Node extends Entity {

        // consider protected
        /*
            protected Node(){
                setName("");
            }
        */

        protected Node(String name){
            super(name);
            //setName(name);
        }

        /*
        void toStream(PrintStream stream){
            stream.append("  ").append(getId()).append(' ');
            attributesToStream(stream);
            stream.append(';');
            stream.println();
        }
         */

        public class Link extends Entity {

            protected Link(Node node1, Node node2){
                this.source = node1;
                this.target = node2;
            }

            void toStream(PrintStream stream){
                stream.append("    ").append(source.getId()).append(" -> ").append(target.getId()).append(' ');
                attributesToStream(stream);
                stream.append(';');
                stream.println();
            }

            Node source = null;
            Node target = null;

        }

        Link addLink(Node target){
            Link link = new Link(this, target);
            //links.add(link);
            links.put(target.getId(), link);
            return link;
        }

        //final public Set<Link> links = new HashSet<>();
        final public Map<String,Link> links = new HashMap<>();

    };


    // final public Set<Node> nodes = new HashSet<>();
    final public Map<String,Node> nodes = new HashMap<>();

    /** Adds a node, using its ID as a key.
     *
     *  The ID is converted from the name.
     *
     * @param name
     * @return
     */
    synchronized
    public Node addNode(String name){
        return addNode(new Node(name));
    }

    /** Adds a node, using its ID as a key.
     *
     * @param node
     * @return
     */
    synchronized
    public Node addNode(Node node){
        //nodes.add(node);
        nodes.put(node.getId(), node);
        return node;
    }

    /** Returns a node, if exists, or creates one.
     *
     * @param id
     * @return
     */
    synchronized
    public Node getNode(String id){
        Node node = nodes.getOrDefault(id, null);
        if (node != null){
            return node;
        }
        return addNode(id);
        /*
        for (Map.Entry<String,Node> entry: nodes.entrySet()) {
          if (entry.getValue().getId().equals(id))
            return node;
        }
        */
    }

    public boolean hasNode(String id){
        return nodes.containsKey(id);
    }

    /** Add link from node1 to node2
     *
     * @param node1
     * @param node2
     * @return
     */
    public Node.Link addLink(Node node1, Node node2){
        return node1.addLink(node2);
    }

    /** Add node or copy links to already existing node.
     *
     * @param node
     */
    public void importNode(Node node){
        //String name = node.getName();
        String key = node.getId();
        Node n = nodes.getOrDefault(key, null);
        if (n == null) {
            addNode(node);
        }
        else {
            n.links.putAll(node.links);
        }
    }

    /** Combines nodes, appending links of already existing nodes.
     *
     *  Does not override existing nodes buts appens links of already existing nodes.
     *
     * @param src
     */
    public void importGraph(Graph src){
        for (Map.Entry<String,Node> entry: src.nodes.entrySet()){
            importNode(entry.getValue());
        }
        /*
        for (Node node: src.nodes) {
            importNode(node);
        }

         */
    }

    final public Node nodeProto; // = new Node("node");
    final public Entity graphProto; // = new Entity("graph");

    void toStream(PrintStream stream){

        stream.append("digraph ").append(id);
        stream.println(" {");
        stream.println();

        if (this.graphProto != null){
            graphProto.toStream(stream);
            stream.println();
        }

        if (this.nodeProto != null){
            nodeProto.toStream(stream);
            stream.println();
        }
        // /opt/products/radar/class/graph/target-taxonomy.dot

        for (Map.Entry<String,Node> entry: nodes.entrySet()){
            Node n = entry.getValue();
            n.toStream(stream);
            for (Map.Entry<String, Node.Link> l: n.links.entrySet()) {
                l.getValue().toStream(stream);
            }
            stream.println();
        }
        stream.println();

        stream.println("}");
    }


    void dotToFile(String outfile) throws IOException, InterruptedException { // }, String styleSheet){ /// todo: IOException
        int i = outfile.lastIndexOf('.');
        if (i > 0){
            dotToFile(outfile, outfile.substring(i+1).toLowerCase()); //, styleSheet) ;
        }
    }

    void dotToFile(String outfile, String format) throws IOException, InterruptedException { //, String styleSheet){ /// todo: IOException

        //Log log = new Log("DOT");
        //log.experimental("Format:" + format);

        if (format.equals("dot")){
            PrintStream printStream = new PrintStream(new FileOutputStream(outfile));
            toStream(printStream);
            printStream.close();
            return;
        }

        String cmd = String.format("dot -T%s -o %s", format, outfile);
        // TODO: add error output read
        // String cmd = String.format("dot -T%s -stylesheet='%s' -o %s", format, styleSheet, outfile);
        //String dir = ".";

        // System.err.println(String.format("cmd: '%s'", cmd));

        Path filePath = Paths.get(outfile);
        File file = filePath.toFile();

        Path dirPath = filePath.getParent() != null ? filePath.getParent() : Paths.get(".");

        //log.warn("Creating process");
        final Process process = Runtime.getRuntime().exec(cmd, null, dirPath.toFile());
        OutputStream outputStream = process.getOutputStream();
        PrintStream printStream = new PrintStream(outputStream);

        // log.warn("Sending graph to the process");
        toStream(printStream);
        //process.waitFor();
        // process.waitFor(); // NEW 2024/10
        // log.warn("Closing streams");
        // TODO: handler error stream
        printStream.close();
        outputStream.close();
        // log.warn("Waiting...");
        process.waitFor(); // OLD
        // log.warn("Checking file:");
        //process.wait(3000); // milliseconds
        if (file.exists()) {
            //System.err.println(String.format("DOT %d", file.lastModified()));
            //System.err.println("DOT compare with: find "+file.getParent()+" -name "+file.getName()+" -printf '%AT\\n' ");
        }
        else {
            // log.warn("File does not exist");
            // System.err.println("DOT WHAT! file does not (yet) exist?\"");
            throw new IOException("DOT graph creation error: file does not (yet) exist?");
        }

        // log.warn("Completed");

    }

    public static void main(String[] args) {


        Graph graph = new Graph("example");

        if (args.length == 0){
            System.err.println();
            System.err.println("Example:");
            //System.err.println(System.getProperty("classp") + "/tmp/file.dot  dot");
            System.err.println("/tmp/file.dot  dot");
            System.exit(1);
        }

        Node node1 = graph.addNode("A");
        node1.attributes.put("color", "blue");

        Node node2 = graph.addNode("B");
        node2.attributes.put("style", "dotted");
        node2.attributes.put("fill", "lighblue");

        // Alternative 1
        Node.Link link = graph.addLink(node1, node2);
        link.attributes.put("style", "dotted");

        Node node3 = graph.addNode("C");
        // Alternative 2
        Node.Link link2 = node2.addLink(node3);
        link2.attributes.put("label", "reijo");

        // graph.toStream(System.out);
        try {
            graph.dotToFile(args[0], args[1]);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

    }



}
