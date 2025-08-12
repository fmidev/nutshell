package nutshell10;

import java.io.File;
import java.util.Map;

public class TaskGraphNode {

    /** Retrieve a node of this task, including all its input tasks.
     *
     * @param task – identifier for this node; typically input variable name like $FIKOR, or product id radar.polar.fikor.
     * @param graph
     * @return – created node.
     */
    static
    public Graph.Node drawGraph(ProductServer.Task task, Graph graph){
        //if (graph == null){
        // graph = new Graph("request: " + task.info.PRODUCT_ID);

        Graph.Node node = graph.getNode(task.info.getID());

        node.attributes.put("style", "filled");
        // if (result != null){
        File p = task.productPaths.getAbsolutePath().toFile();
        if (p.exists()){
            String color = "orange"; // ripe fruit
            long ageSeconds = (System.currentTimeMillis() - p.lastModified())/1000;
            if (ageSeconds < 60){ // 1 min
                color = "#60ff30";
            }
            else if (ageSeconds < 300){ // 5 mins
                color = "#90f000";
            }
            else if (ageSeconds < 3600){ // 1 hour
                color = "#b0f030";
            }
            else if (ageSeconds < 86400){ // 24 hours = 60*60*24
                color = "#c0d030";
            }
            node.attributes.put("color", color);
        }
        else {
            node.attributes.put("color", "gray");
        }

        // node.attributes.put("comment", info.log.indexedState.getMessage().replace('"','\''));

        // Add clickable node.
        Instructions instr = new Instructions();
        instr.set(OutputType.STATUS, ActionType.INPUTLIST); //, ActionType.MAKE);
        // instr.toString();
        node.attributes.put("href", String.format(
                "?instructions=%s&amp;product=%s", instr, task.info.getFilename()));


        for (Map.Entry<String, ProductServer.Task> entry: task.inputTasks.entrySet()) {
            ProductServer.Task t = entry.getValue();
            Graph.Node n = drawGraph(t, graph);
            // t.getGraphNode(graph, entry.getKey()+"\n"+t.info.PRODUCT_ID);
            // System.out.println(String.format("%s:\t %s", ec.getKey(), ec.getValue()));
            Graph.Node.Link link = node.addLink(n);
            // TODO: Style
            if (t.log.indexedState.index > 300) {
                n.attributes.put("style", "filled");
                n.attributes.put("fillcolor", "white");
                link.attributes.put("style", "dashed");
                link.attributes.put("label", t.log.indexedState.getIndex());
                //n.attributes.put("fillcolor", "#ffc090");
                //link.attributes.put("label", t.log.indexedState.getMessage()); Java.String.replaceAll(VT100)
                // link.attributes.put("color", "red");
            }


            if (t.result == null){
                // link.attributes.put("color", "red"); // ""#800000");
                link.attributes.put("style", "dotted");
            }
            else {
                if (t.result instanceof Exception){
                    link.attributes.put("color", "brown");
                    // FIX: error msg
                    link.attributes.put("label", t.result.getClass().getSimpleName());
                }
                else {
                    link.attributes.put("color", "green");
                    String label = ""+t.log.indexedState.getIndex(); // t.result.toString();
                    if (t.instructions.makeLevelAtLeast(Instructions.MakeLevel.GENERATE)){
                        // label = "GENERATE"; disturbs, consider line attribs only:
                        link.attributes.put("style", "bold");
                    }
                    else {
                    }
						/*
						else if (t.instructions.isSet(ActionType.MAKE)){
							label = ""+t.log.indexedState.getIndex(); //"MAKE";
						}
						else if (t.instructions.isSet(ActionType.EXISTS)){
							label = ""+t.log.indexedState.getIndex();
							link.attributes.put("style", "dotted");
						}*/
                    // REMOVED link.attributes.put("label", label);
                }
                //link.attributes.put("", "");
            }
            // YYY

        }

        return node;
    };

}
