package nutshell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


// TODO: redesign such that a that a static instance is used:
// static final Counter<MyApp> counter = new Counter<>();

/**
 *
 */
class MyCounter  {

    static protected int counter = 0;

    static public int getBit() {
        return (1 << counter++);
    }

};

enum MediaTypeNew {

}


/** Storage media for the resulting product.
 *
 */
interface MediaType {

    /** The product is an object in memory
     */
    int MEMORY = MyCounter.getBit();

    /** The product will be saved on disk.
     *
     *  This is a natural setting for system side generator.
     */
    int FILE = MyCounter.getBit();

}


/** Specifies, what will be done after product has been generated (or checked).
 *
 *  If unset, a status report is returned, including path/link to the result.
 *
 */
interface OutputType {

    /** HTTP only: client will be redirected to URL of generated product
     *  Strictly speaking - needs no file, but a result (file or object).
     *
     * @see Nutlet doGet
     */
    int REDIRECT = MyCounter.getBit() | MediaType.FILE; //16 | ResultType.FILE;  // "OUTPUT=REDIRECT require[RESULT=FILE]"

    /** Output in a stream (Currently, HTTP only. Future option: standard output.)
     *
     * @see Nutlet doGet
     */
    int STREAM = MyCounter.getBit(); // 32;  // "OUTPUT=STREAM"

    int STATUS = MyCounter.getBit(); // 4096; consider PostProcessing?
}

/**
 *   TODO: reconsider replacing EXISTS/MAKE/GENERATE with MAKE(depth) only
 */



interface ActionType {

    /** Checks existence of product (used with @MEMORY or @FILE)
     *
     *  Returns immediately, if a non-empty product instance is found.
     *  If an empty product is found, waits for completion.
     */
    // int EXISTS = MyCounter.getBit(); // 64; // "OUTPUT=INFO"

    /** Delete the product file on disk (future option: also in memory cache).
     */
    //// int DELETE = MyCounter.getBit(); // 128; // "ACTION=DELETE_FILE" or "PREOP_DELETE"

    /** Retrieve input list.
     */
    int INPUTLIST = MyCounter.getBit(); // 256; // a "hidden" flag? "OUTPUT=INFO"

    /** The product is (re)generated. Used with MEMORY and FILE
     *
     */
    ////  int GENERATE = MyCounter.getBit(); // 512; //  | INPUTLIST;

    /** Conditional generate: create product only if it does not exist. Used with MEMORY and FILE
     *
     *  In program flow, MEMORY and FILE will be checked first, and only if they fail, GENERATION takes place.
     */
    //// int MAKE = MyCounter.getBit() | EXISTS; // 1024 | EXISTS;
    //public final int MAKE = EXIST | GENERATE;
    // A "hidden" flag? actually unclear, should be either RESULT=FILE or RESULT=MEMORY (but not RESULT=null);
    // Also, acts like "make both MEMORY and FILE objects".


    /** Run script "run.sh" in the product directory (after)
     */
    int RUN = MyCounter.getBit(); // 2048; // PostProcessing!

    /** Go through product request handler checking existence of product generator, memory cache and output directory.
     */
    // int INFO = MyCounter.getBit(); // 4096; // | INPUTLIST; // "OUTPUT=INFO"
    //int STATUS = MyCounter.getBit(); // 4096; consider PostProcessing?

    int PARALLEL = MyCounter.getBit(); // 8192; // use threads

    /// Computation intensive products are computed in the background; return a notification receipt in HTML format.
    //  public static final int BATCH = 4096;

    /// TODO: convert to a separate command?
    //int CLEAR_CACHE = MyCounter.getBit(); // 16384;

}

interface PostProcessing {

    /** Link file to short directory
     *
     */
    int SHORTCUT = MyCounter.getBit() | MediaType.FILE; // 4 | FILE; // "POSTOP=LINK_SHORTCUT"

    /** Link file to short directory, $TIMESTAMP replaced with 'LATEST'
     */
    int LATEST = MyCounter.getBit() | MediaType.FILE; //8 | FILE;  // "POSTOP=LINK_LATEST"

    /// Try to store the resulting file.
    int STORE = MyCounter.getBit();

}

public class Instructions extends Flags implements ActionType, MediaType, OutputType, PostProcessing { //


    // public int MIKA = 1;

    /**
     *
     */
    public enum MakeLevel {
        UNDEFINED("Undefined"),
        DELETE("Delete product"),
        CLEAR_CACHE("Remove all the files in cache"),
        NONE("Do nothing"),
        // CHECK(), PEEK()
        PARSE("Only parse product request"),
        EXISTS("Only check if product exists"),
        // QUERY("Retrieve from storage, if it does not exist"),
        MAKE("Generate product, if it does not exist"),
        GENERATE("Generate product"),
        GENERATE_DEEP("Generate product, and its inputs"),
        GENERATE_ALL("Generate product, and its inputs, recursively");

        final String description;
        MakeLevel(String d){
            description = d;
        }
    }

    /**  When generating a product, @regenerateDepth determines how deeply also inputs will be regenerated.
     *   Reconsider replacing EXISTS/MAKE/GENERATE with this only, ie. MAKE + depth
     */
    public int makeLevel;

    /*
    public enum OutputHandling {
        NONE,
        STREAM,
        REDIRECT
    }
    public OutputHandling outputHandling = OutputHandling.NONE;

    public void setOutputHandling(OutputHandling value) {
        this.outputHandling = value;
    }

    public void setOutputHandling(String value) {
        this.outputHandling = OutputHandling.valueOf(value);
    }
    */
    public
    String label = "";

    public Instructions(){
        // LABEL = "initValue"; here before super() Flags, if CAPS used!
        super(Instructions.class);
        //makeLevel = MakeLevel.MAKE.ordinal();
        makeLevel = MakeLevel.UNDEFINED.ordinal();
        value = 0;
    }

    public Instructions(Instructions instructions){
        super(Instructions.class);
        makeLevel = instructions.makeLevel;
        value = instructions.value;
        addCopies(instructions.copies);
		addLinks(instructions.links);
		addMove(instructions.move); // Thread-safe?
    }
    public Instructions(int a, int level){
        super(Instructions.class);
        this.value = a;
        this.makeLevel = level;
    }


    public boolean isEmpty() {
        return (makeLevel == MakeLevel.UNDEFINED.ordinal());
        // return (value == 0); // && makeLevel == 0 ?
    }


    public MakeLevel getMakeLevel(){
        if (makeLevel < 0){
            return MakeLevel.UNDEFINED;
        }
        else if (makeLevel < MakeLevel.values().length){
            return MakeLevel.values()[makeLevel];
        }
        else {
            return MakeLevel.GENERATE_ALL;
        }
    }

    public void setMakeLevel(int level){
        makeLevel = level;
    }

    public void setMakeLevel(MakeLevel level){
        makeLevel = level.ordinal();
    }

    public void setMakeLevel(String level){
        try {
            int i = Integer.parseInt(level);
            setMakeLevel(i);
            return;
        } catch (NumberFormatException e) {
        }
        setMakeLevel(MakeLevel.valueOf(level));
    }


    public void ensureMakeLevel(MakeLevel level){
        makeLevel = Math.max(makeLevel, level.ordinal());
    }

    public boolean makeLevelEquals(MakeLevel level){
        return makeLevel == level.ordinal();
    }

    public boolean makeLevelAtLeast(MakeLevel level){
        return makeLevel >= level.ordinal();
    }

    public boolean makeLevelBelow(MakeLevel level){
        return makeLevel < level.ordinal();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(getMakeLevel().name());

        // if (outputHandling != OutputHandling.NONE)
        //    sb.append(',').append(outputHandling);

        if (super.value != 0) {
            if (sb.length() > 0)
                sb.append(',');
            sb.append(super.toString());
        }

        return sb.toString();

    }

    /** Add specific request to copy the result.
     *
     *  Several requests can be added.
     *
     * @param filename - target file
     */
    public void addCopy(String filename){
        copies.add(filename);
        //copies.add(Paths.get(filename));
    }

    /** Add specific request to copy the result.
     *
     * @param copies - target files
     */
    public void addCopies(List<String> copies){
        if (copies != null)
            this.copies.addAll(copies);
    }


    /** Add specific request to list the result.
     *
     *  Several requests can be added.
     *
     * @param filename
     */
    public void addLink(String filename){
        links.add(filename);
        //links.add(Paths.get(filename));
    }

    /** Add specific request to link the result.
     *
     *  Several requests can be added.
     *
     * @param links - filenames pointing to the original result
     */
    public void addLinks(List<String> links){
        if (links != null)
            this.links.addAll(links);
    }

    /** Add specific request to link the result.
     *
     *  Several requests can be added.
     *
     * @param path - target
     */
    public void addMove(String path){
        if (path != null)
            move = path;
            //move = Paths.get(path);
    }

    public Program.Parameter getProgramParameter(String fieldName){
        return new Program.Parameter(fieldName.toLowerCase(),
                String.format("Same as --instructions %s,...", fieldName.toUpperCase())) {

            @Override
            public void exec() {
                try {
                    add(fieldName); // Note: could be Integr instead.
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException("Unsupported Instruction", e);
                }
            }

        };

    }

    public Program.Parameter getProgramParameter(Instructions.MakeLevel level){

        return new Program.Parameter(level.name().toLowerCase(),
                String.format("%s. Same as --instructions %s,...",
                        level.description, level.name().toUpperCase())) {
            @Override
            public void exec() {
                setMakeLevel(level);
            }
        };

    }

    public Program.Parameter getProgramParameter(){
        return new Program.Parameter.Simple<String>("instructions",
                String.format("Set main action %s and flags %s", Arrays.toString(MakeLevel.values()), getAllFlags().keySet()),
                this.toString()) {
            @Override
            public void exec() throws RuntimeException {

                // TODO: Flag reset?
                for (String arg: value.split("[,|]")){ // TODO: also "|"

                    try {
                        setMakeLevel(arg);
                        continue;
                    } catch (IllegalArgumentException e) {
                    }

                    try {
                        add(arg);
                        continue;
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        throw new RuntimeException("Unsupported Instruction", e);
                    }
                }

            }
        };
    }



    protected List<String> copies = new ArrayList<>();
    protected List<String> links  = new ArrayList<>();
    protected String move = null;

    public static void main(String[] args) {

        Instructions instructions = new Instructions();
        ProductServer.Parameter p = instructions.getProgramParameter();

        for (String arg: args) {
            try {
                p.setParams(arg);

            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            p.exec();
            System.out.println(instructions);
        }

    }

}
