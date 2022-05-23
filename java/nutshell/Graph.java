package nutshell;


import com.sun.istack.internal.NotNull;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


class Entity {

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
            for (Map.Entry<String, Object> entry: attributes.entrySet()) {
                stream.append(entry.getKey()).append('=').append('"').append(entry.getValue().toString()).append('"');
                stream.append(", ");
            }
            stream.append(']');
        }
    }

}

public class Graph extends Entity {

    Graph(String name){
        setName(name);
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
            setName(name);
        }

        void toStream(PrintStream stream){
            stream.append("  ").append(getId()).append(' ');
            attributesToStream(stream);
            stream.append(';');
            stream.println();
        }

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
        /*
        for (Node node: nodes) {
            if (node.getName().equals(name))
                return true;
        }
        return false;

         */
    }

    /*
    public boolean hasLink(String name){
        for (Link link: links) {
            if (link.getName().equals(name))
                return true;
        }
        return false;
    }

     */


    public Node.Link addLink(Node node1, Node node2){
        //Link link = new Link(node1, node2);
        //links.add(link);
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
        /*
        if (nodes.containsKey(key)){
            Node n = nodes.get(key);
            //for (Node.Link link: node.links){
                if (n.links.contains(link.id)){

                }
            }
            return;
        }
        else {
            addNode(node);
        }
        */
        /*
        for (Node n: nodes) {
            if (n.getName().equals(name)){
                // n.links.addAll(node.links);
                for (Node.Link link: node.links){

                }
                return;
            }
        }*/
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


    void toStream(PrintStream stream){

        stream.append("digraph ").append(id);
        stream.println(" {");
        stream.println();

        for (Map.Entry<String,Node> entry: nodes.entrySet()){
            Node n = entry.getValue();
            n.toStream(stream);
            for (Map.Entry<String, Node.Link> l: n.links.entrySet()) {
                l.getValue().toStream(stream);
            }
            stream.println();
        }
        /*
        for (Node n: nodes) {
            n.toStream(stream);
            for (Node.Link l: n.links) {
                l.toStream(stream);
            }
            stream.println();
        }
         */
        stream.println();

        /*
        for (Link l: links) {
            l.toStream(stream);
        }
        */

        stream.println("}");
    }

    /*
    public class Link extends Entity {

        public Link(Node node1, Node node2){
            this.node1 = node1;
            this.target = node2;
        }

        final Node node1;
        final Node target;

        void toStream(PrintStream stream){
            stream.append("  ").append(node1.getId()).append(" -> ").append(target.getId()).append(' ');
            attributesToStream(stream);
            stream.append(';');
            stream.println();
        }

    }
    */
    //final public Node root = new Node();

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

        //log.warn("Creating process");
        final Process process = Runtime.getRuntime().exec(cmd, null, filePath.getParent().toFile());
        OutputStream outputStream = process.getOutputStream();
        PrintStream printStream = new PrintStream(outputStream);

        // log.warn("Sending graph to the process");
        toStream(printStream);
        //process.waitFor();
        // log.warn("Closing streams");
        // TODO: handler error stream
        printStream.close();
        outputStream.close();
        // log.warn("Waiting...");
        process.waitFor();
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
            System.err.println("'My super, test!' node1 ");
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
        Node.Link link2 = node1.addLink(node3);
        link2.attributes.put("label", "reijo");

        // graph.toStream(System.out);
        try {
            graph.dotToFile(args[0], "");
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

    }



}
