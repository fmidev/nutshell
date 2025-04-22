package nutshell;

import java.util.concurrent.Executors;

public class VT100 {

    static public final String START = "\033[";
    static public final String END   = "m";

    interface Control {

    }

    // https://docs.oracle.com/javase/tutorial/java/javaOO/enum.html
    public enum Highlights implements Indexed, Control {
        RESET(0),
        BRIGHT(1),
        DIM(2),
        UNDERLINE(4),
        UNDERLINE2(21), // Double unnderline
        BLINK(5),
        ITALIC(3),
        REVERSE(7);

        Highlights(int code){
            this.code = code;
            this.string = "\033["+code+'m';
            this.bitvalue = 1 << ordinal();
        }

        @Override
        public String toString() {
            return this.string;
        }

        @Override
        public int getIndex() {
            return code;
        }

        private final int code;
        private final String string;
        public final int bitvalue;

    }


    public enum Colours implements Indexed, Control {
        BLACK(30),   // # Dark (green?)
        RED(31),    // # Red
        GREEN(32),  // # Red
        YELLOW(33), // # Orange prompt
        BLUE(34),
        MAGENTA(35),
        CYAN(36),
        WHITE(37),   // # Gray
        DEFAULT(39),
        //GRAY(2),
        BLACK_BG(40), // # Dark (green?)
        RED_BG(41),
        GREEN_BG(42),
        YELLOW_BG(43), // # Orange
        BLUE_BG(44),
        MAGENTA_BG(45),
        CYAN_BG(46),
        WHITE_BG(47),
        DEFAULT_BG(49);

        Colours(int code){
    	    this.code = code;
    	    // Always BRIGHT
    	    this.string = VT100.START+ Highlights.BRIGHT.code + ";" + code + "m";
        }

        /*
        Colour(int colour, int prefix){
            this.code = colour;
            this.string = "\033["+prefix+';'+colour+'m';
        }
         */


        @Override
        public String toString() {
            return this.string;
        }

        @Override
        public int getIndex() {
            return code;
        }

        private final int code;
        private final String string;

    }

    //final static public String END = "\033[0m";
    //final static public String E = "\033[0m";
    static
    public Control compound(Colours colour, int highlights){

        Control c = new Control() {

            String s = null;

            @Override
            public String toString() {
                if (s == null) {
                    s = VT100.START; //"\033[";
                    for (Highlights h : Highlights.values()) {
                        if ((h.bitvalue & highlights) > 0)
                            s = s + h.code + ";";
                    }
                    s = s + colour.code + VT100.END;
                }
                return s;
          }

        };

        return c;

    }

    //private String string;
    /*
    static
    public <T> void write(Colour c, T message, StringBuffer buffer){
        start(c, buffer);
        buffer.append(message);
        end(buffer);
    }

    static
    public <T> void start(Colour c, StringBuffer buffer){
        buffer.append("\033[1;").append(c.code).append('m');
    }

    static
    public <T> void end(StringBuffer buffer){
        buffer.append("\033[0m");
    }
    */


    public static void main(String[] args) {

        System.out.println(Colours.MAGENTA + "Purppura" + Highlights.RESET);
        System.out.println(Colours.BLUE + "Sininen" + Highlights.RESET);
        // System.out.println(Colours.UNDERLINE2 + "Alleviivaus2" + Colours.RESET);

    }

}
