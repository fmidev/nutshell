package nutshell;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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




/** Determines, what should be done with the resulting product file or instance.
 *
 */
interface ResultType {


    // static final Counter<ResultType> counter = new Counter<>();

    /**
     * The product is in memory cache
     */
    int MEMORY = MyCounter.getBit(); //Counter.getBit(); // 1 "RESULT=MEMORY"

    /**
     * Product should be saved on disk. System side generator may save it anyway.
     * – return immediately in success
     */
    int FILE = MyCounter.getBit(); // 2; // "RESULT=FILE"

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
    int REDIRECT = MyCounter.getBit() | ResultType.FILE; //16 | ResultType.FILE;  // "OUTPUT=REDIRECT require[RESULT=FILE]"

    /** Output in a stream (Currently, HTTP only. Future option: standard output.)
     *
     * @see Nutlet doGet
     */
    int STREAM = MyCounter.getBit(); // 32;  // "OUTPUT=STREAM"

}

interface ActionType {

    /** Checks existence of product (used with @MEMORY or @FILE)
     *
     *  Returns immediately, if a non-empty product instance is found.
     *  If an empty product is found, waits for completion.
     */
    int EXISTS = MyCounter.getBit(); // 64; // "OUTPUT=INFO"

    /** Delete the product file on disk (future option: also in memory cache).
     */
    int DELETE = MyCounter.getBit(); // 128; // "ACTION=DELETE_FILE" or "PREOP_DELETE"

    /** Retrieve input list.
     */
    int INPUTLIST = MyCounter.getBit(); // 256; // a "hidden" flag? "OUTPUT=INFO"

    /** The product is (re)generated. Used with MEMORY and FILE
     *
     */
    int GENERATE = MyCounter.getBit(); // 512; //  | INPUTLIST;

    /** Conditional generate: create product only if it does not exist. Used with MEMORY and FILE
     *
     *  In program flow, MEMORY and FILE will be checked first, and only if they fail, GENERATION takes place.
     */
    int MAKE = MyCounter.getBit() | EXISTS; // 1024 | EXISTS;
    //public final int MAKE = EXIST | GENERATE;
    // A "hidden" flag? actually unclear, should be either RESULT=FILE or RESULT=MEMORY (but not RESULT=null);
    // Also, acts like "make both MEMORY and FILE objects".


    /** Run script "run.sh" in the product directory (after)
     */
    int RUN = MyCounter.getBit(); // 2048; // PostProcessing!

    /** Go through product request handler checking existence of product generator, memory cache and output directory.
     */
    // int INFO = MyCounter.getBit(); // 4096; // | INPUTLIST; // "OUTPUT=INFO"
    int STATUS = MyCounter.getBit(); // 4096; // | INPUTLIST; // "OUTPUT=INFO"

    int PARALLEL = MyCounter.getBit(); // 8192; // use threads

    /// Computation intensive products are computed in the background; return a notification receipt in HTML format.
    //  public static final int BATCH = 4096;
    int CLEAR_CACHE = MyCounter.getBit(); // 16384;

}

interface PostProcessing {

    /** Link file to short directory
     *
     */
    int SHORTCUT = MyCounter.getBit() | ResultType.FILE; // 4 | FILE; // "POSTOP=LINK_SHORTCUT"

    /** Link file to short directory, $TIMESTAMP replaced with 'LATEST'
     */
    int LATEST = MyCounter.getBit() | ResultType.FILE; //8 | FILE;  // "POSTOP=LINK_LATEST"

    /// Try to store the resulting file.
    int STORE = MyCounter.getBit();

}


public class Instructions extends Flags implements ActionType, ResultType, OutputType, PostProcessing {

    public Instructions(){
        super(Instructions.class);
    }

    public Instructions(Instructions instructions){
        super(Instructions.class);
        this.value = instructions.value;
    }
    public Instructions(int a){
        super(Instructions.class);
        this.value = a;
    }

    /**  When generating a product, @regenerateDepth determines how deeply also inputs will be regenerated.
     */
    public int regenerateDepth = 0;

    public boolean isEmpty() {
        return (value == 0);
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

    /*
    public void addMove(Path path){
        move = path;
    }
     */

    /*
    protected List<Path> copies = new ArrayList<>();
    protected List<Path> links  = new ArrayList<>();
    protected Path move = null;
     */
    protected List<String> copies = new ArrayList<>();
    protected List<String> links  = new ArrayList<>();
    protected String move = null;

}
