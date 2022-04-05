package nutshell;


import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


class Entity {

    String id = "?";

    String setId(String name){
        id = name.replaceAll("\\W", " ").trim().replaceAll("\\s+", "_");
        return id;
    }

    String getId(){
        return id;
    }

    String name = "?";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        setId(name);
        if (!id.equals(name))
            attributes.put("label", name);
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

    void toStream(PrintStream stream){

        stream.append("digraph ").append(id);
        stream.println(" {");
        stream.println();

        for (Node n: nodes) {
            n.toStream(stream);
        }
        stream.println();

        for (Link l: links) {
            l.toStream(stream);
        }
        stream.println();

        stream.println("}");
    }

    void dotToFile(String outfile){ /// todo: IOException

        int i = outfile.lastIndexOf('.');
        if (i > 0){
            dotToFile(outfile, outfile.substring(i+1).toLowerCase()) ;
        }

    }

    void dotToFile(String outfile, String format){ /// todo: IOException

        String cmd = String.format("dot -T%s -o %s", format, outfile);
        String dir = ".";

        System.err.println(String.format("cmd: '%s'", cmd));
        //ShellExec.OutputReader reader = new ShellExec.OutputReader(System.err);

        final Process process;
        try {
            process = Runtime.getRuntime().exec(cmd, null, Paths.get(dir).toFile());
            OutputStream outputStream = process.getOutputStream();
            PrintStream printStream = new PrintStream(outputStream);
            toStream(printStream);
            // TODO: handler error stream
            //printStream.println("digraph MIKA { A->B }");
            /*
            DataInputStream in = new DataInputStream(process.getInputStream());
            //DataInputStream in = new DataInputStream(process.getErrorStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = br.readLine()) != null) {
            	System.err.println(line);
            }
            in.close();
            br.close();
            */
            printStream.close();
            outputStream.close();
            //System.err.println("Chiuso");
		}
        catch (IOException e) {
            e.printStackTrace();
        }
        //process.


        // final String logname = ShellExec.class.getSimpleName() + ".log";
        // System.out.println("Writing log :" + logname);
        //File logFile = new File(logname);
        /*
        System.out.println(String.format("Executing: %s (dir=", cmd, dir));
        try {
            //logFile.createNewFile();
            // FileOutputStream fw = new FileOutputStream(logFile);
            ShellExec.exec(cmd, null, Paths.get(dir), reader);
        } catch (Exception e) {
            e.printStackTrace();
        }

         */
    }


    public class Node extends Entity {

        // consider protected
        protected Node(){
            setName("");
        }

        protected Node(String name){
            setName(name);
        }

        void toStream(PrintStream stream){
            stream.append(" ").append(getId()).append(' ');
            attributesToStream(stream);
            stream.append(';');
            stream.println();
        }

    };

    public class Link extends Entity {

        public Link(Node node1, Node node2){
            this.node1 = node1;
            this.node2 = node2;
        }

        final Node node1;
        final Node node2;

        void toStream(PrintStream stream){
            stream.append("  ").append(node1.getId()).append(" -> ").append(node2.getId()).append(' ');
            attributesToStream(stream);
            stream.append(';');
            stream.println();
        }

    }
    //final public Node root = new Node();

    final public List<Node> nodes = new LinkedList<>();
    final public List<Link> links = new LinkedList<>();

    synchronized
    public Node addNode(String name){
        Node node = new Node(name);
        nodes.add(node);
        return node;
    }

    /** Returns a node, if exists, or creates one.
     *
     * @param name
     * @return
     */
    synchronized
    public Node getNode(String name){
        for (Node node: nodes) {
            if (node.getName().equals(name))
                return node;
        }
        return addNode(name);
    }

    public boolean hasNode(String name){
        for (Node node: nodes) {
            if (node.getName().equals(name))
                return true;
        }
        return false;
    }

    public boolean hasLink(String name){
        for (Link link: links) {
            if (link.getName().equals(name))
                return true;
        }
        return false;
    }


    public Link addLink(Node node1, Node node2){
        Link link = new Link(node1, node2);
        links.add(link);
        return link;
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

        Link link = graph.addLink(node1, node2);
        link.attributes.put("style", "dotted");

        Node node3 = graph.addNode("C");
        Link link2 = graph.addLink(node1, node3);
        link2.attributes.put("label", "reijo");

        // graph.toStream(System.out);
        graph.dotToFile(args[0]);

    }



}
