package nutshell;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TextOutput {

    public enum Format {
        TEXT,
        VT100,
        HTML;
    }


    public Format getFormat(){
        return Format.TEXT;
    }

    public enum Options {
        COLOUR,
        HIGHLIGHT,
        URLS
    }

    static
	public Map<Path,String> pathMap = new HashMap<Path,String>();  // URL?

    public enum Highlight {
        RESET,
        BRIGHT,
        DIM,
        UNDERLINE,
        UNDERLINE2, // Double unnderline
        BLINK,
        ITALIC,
        REVERSE;
    }

    public enum Colour {
        BLACK,
        GRAY, // Not supported in VT100
        RED,
        GREEN,  // # Red
        YELLOW, // # Orange prompt
        BLUE,
        MAGENTA,
        CYAN,
        WHITE,   // # Gray
        DEFAULT, // RESET?
    }


    public TextOutput(){
        highlights = new Flags(Highlight.class);
        reset();
    }

    public void reset(){
        setColour(Colour.DEFAULT);
        setHighlights(0);
    }

    public void setColour(Colour colour) {
        this.colour = colour;
    }

    public void setHighlights(int h) {
        highlights.set(h);
    }

    public void setHighlights(Highlight... hls) {
        highlights.set(0);
        for (Highlight h: hls){
            highlights.add(h);
        }
    }

    public void addHighlight(String h) throws NoSuchFieldException, IllegalAccessException {
        highlights.add(h);
    }

    Colour colour = Colour.DEFAULT;
    final Flags highlights;

    @Override
    public String toString() {
        return String.format(" %s, %s, %s", getClass().getName(), colour, highlights);
    }

    public void startSection(StringBuffer buffer){
    }

    public void endSection(StringBuffer buffer){
    }


    public void startElem(StringBuffer buffer){
    }

    public void endElem(StringBuffer buffer){
    }

    void append(String text, StringBuffer buffer){
        buffer.append(text);
    }

    void appendLink(String label, String url, StringBuffer buffer){
        buffer.append(url);
    }

    /** Text terminal output supporting colours and highlighting
     *
     */
    static
    public class Vt100 extends TextOutput {

        static
        final Map<Highlight,Integer> hmap;

        static
        final Map<Colour,Integer> cmap;

        /**
         *  for i in {0..99}; do echo -e "$i \033[1;${i}m Test \033[0m"; done
         */
        static {
            hmap = new HashMap<>();
            hmap.put(Highlight.RESET,0);
            hmap.put(Highlight.BRIGHT,1);
            hmap.put(Highlight.DIM,2);
            hmap.put(Highlight.UNDERLINE,4);
            hmap.put(Highlight.UNDERLINE2,21); // Double unnderline
            hmap.put(Highlight.BLINK,5);
            hmap.put(Highlight.ITALIC,3);
            hmap.put(Highlight.REVERSE,7);

            cmap = new HashMap<>();
            cmap.put(Colour.BLACK,30);   //
            cmap.put(Colour.RED, 31);    //
            cmap.put(Colour.GREEN, 32);  //
            cmap.put(Colour.YELLOW, 33); // ~Orange
            cmap.put(Colour.BLUE, 34);
            cmap.put(Colour.MAGENTA, 35);
            cmap.put(Colour.CYAN, 36);
            cmap.put(Colour.GRAY,37);   //
            cmap.put(Colour.WHITE,38);   // CHECK
            cmap.put(Colour.DEFAULT,39);
        }

        @Override
        public Format getFormat(){
            return Format.VT100;
        }

        @Override
        public void startElem(StringBuffer buffer){
            buffer.append("\033[");
            // HIGHLIGHT
            for (Highlight h: Highlight.values()) {
                if (highlights.involves(h)){
                    buffer.append(hmap.getOrDefault(h,0)).append(';');
                }
            }
            // COLOUR
            if (!colour.equals(Colour.DEFAULT))
                buffer.append(cmap.getOrDefault(colour, 39));

            buffer.append('m');
        }

        @Override
        public void endElem(StringBuffer buffer){
            buffer.append("\033[0m");
        }

        // TODO: underline etc. Save status before write?
        void appendLink(String label, String url, StringBuffer buffer){
            buffer.append(url);
        }
    }

    /** Lightweight output, esp. for logging.
     *
     */
    static
    public class Html extends TextOutput {

        static
        final Map<Highlight,String> hmap;

        static {
            hmap = new HashMap<>();
            hmap.put(Highlight.RESET,"font-style: normal; font-weight:normal; text-decoration:none");
            hmap.put(Highlight.BRIGHT,"font-weight: bold");
            hmap.put(Highlight.DIM,"font-weight: lighter");
            hmap.put(Highlight.UNDERLINE,"text-decoration: underline");
            hmap.put(Highlight.UNDERLINE2,"text-decoration: double-underline"); // Double unnderline
            // hmap.put(Highlight.BLINK,);
            hmap.put(Highlight.ITALIC,"font-style: italic");
            // hmap.put(Highlight.REVERSE,7); color -> background-color.
        }

        @Override
        public Format getFormat(){
            return Format.HTML;
        }

        @Override
        public void startSection(StringBuffer buffer){
            buffer.append("<pre>");
        }

        @Override
        public void endSection(StringBuffer buffer){
            buffer.append("</pre>\n");
        }

        @Override
        public void startElem(StringBuffer buffer){

            buffer.append("<span");
            // HIGHLIGHT
            boolean STYLE = false;

            for (Highlight h: Highlight.values()) {
                if (highlights.involves(h) && hmap.containsKey(h)) {
                    if (!STYLE){
                        buffer.append(" style=\"");
                        STYLE = true;
                    }
                    buffer.append(hmap.get(h)).append(";");
                }
            }
            // COLOUR
            if (!colour.equals(Colour.DEFAULT)){
                if (!STYLE){
                    buffer.append(" style=\"");
                    STYLE = true;
                }
                if (highlights.involves(Highlight.REVERSE)){
                    buffer.append("background-color: light" + colour.name().toLowerCase());
                }
                else {
                    buffer.append("color: " + colour.name().toLowerCase());
                }
            }

            if (STYLE){
                buffer.append('"');
            }
            buffer.append('>');
        }

        @Override
        public void endElem(StringBuffer buffer){
            buffer.append("</span>");
        }

        void appendLink(String label, String url, StringBuffer buffer){
            buffer.append(String.format("<a href=\"%s\" target=\"_new\">%s</a>", url, label));
        }
    }



    public void decorate(String s, StringBuffer buffer){
        startElem(buffer);
        append(s, buffer);
        endElem(buffer);
    }

    public static void main(String[] args) {

        TextOutput.Vt100 vt100 = new TextOutput.Vt100();
        TextOutput.Html html   = new TextOutput.Html();

        TextOutput textOutput = vt100;

        for (String arg: args) {


            if (arg.equals("HTML")){
                textOutput = html;
                continue;
            }

            if (arg.equals("VT100")){
                textOutput = vt100;
                continue;
            }

            if (arg.equals("RESET")){
                textOutput.reset();
                continue;
            }


            try {
                textOutput.addHighlight(arg);
                continue;
            } catch (Exception e) {
                //System.err.println("not a highlight");
            }

            try {
                textOutput.setColour(Colour.valueOf(arg));
                continue;
            } catch (Exception e) {
                //System.err.println("not a colour");
            }

            System.err.println("    " + arg);
            StringBuffer buffer = new StringBuffer();
            textOutput.decorate(arg, buffer);
            System.err.println(" -> " + buffer.toString());
            buffer.setLength(0);

        }


    }


}
