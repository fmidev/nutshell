package nutshell;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;



/** Determines, what should be done with the resulting product file or instance.
 *
 */
interface ResultType {

    /** The product is in memory cache
     */
    int MEMORY = 1; // "RESULT=MEMORY"

    /** Product should be saved on disk. System side generator may save it anyway.
     *   – return immediately in success
     */
    int FILE = 2; // "RESULT=FILE"

    /** Link file to short directory
     *
     */
    int SHORTCUT = 4 | FILE; // "POSTOP=LINK_SHORTCUT"

    /** Link file to short directory, $TIMESTAMP replaced with 'LATEST'
     */
    int LATEST = 8 | FILE;  // "POSTOP=LINK_LATEST"

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
     * @see Nutlet#doGet
     */
    int REDIRECT = 16 | ResultType.FILE;  // "OUTPUT=REDIRECT require[RESULT=FILE]"

    /** Output in a stream (Currently, HTTP only. Future option: standard output.)
     *
     * @see Nutlet#doGet
     */
    int STREAM = 32;  // "OUTPUT=STREAM"

}

interface ActionType {

    /** Checks existence of product (used with @MEMORY or @FILE)
     *
     *  Returns immediately, if a non-empty product instance is found.
     *  If an empty product is found, waits for completion.
     */
    int EXISTS = 64; // "OUTPUT=INFO"

    /** Delete the product file on disk (future option: also in memory cache).
     */
    int DELETE = 128; // "ACTION=DELETE_FILE" or "PREOP_DELETE"

    /** Retrieve input list.
     */
    int INPUTLIST = 256; // a "hidden" flag? "OUTPUT=INFO"

    /** The product is (re)generated. Used with MEMORY and FILE
     *
     */
    int GENERATE = 512; //  | INPUTLIST;

    /** Conditional generate: create product only if it does not exist. Used with MEMORY and FILE
     *
     *  In program flow, MEMORY and FILE will be checked first, and only if they fail, GENERATION takes place.
     */
    int MAKE = 1024 | EXISTS;
    //public final int MAKE = EXIST | GENERATE;
    // A "hidden" flag? actually unclear, should be either RESULT=FILE or RESULT=MEMORY (but not RESULT=null);
    // Also, acts like "make both MEMORY and FILE objects".

    /// Check if product is in cache memory or disk, but do not generate it if missing.
    // public static final int QUERY = MEMORY|FILE;

    /** Run script "run.sh" in the product directory (after)
     */
    int RUN = 2048; // PostProcessing!

    /** Go through product request handler checking existence of product generator, memory cache and output directory.
     */
    int DEBUG = 4096; // | INPUTLIST; // "OUTPUT=INFO"

    /// Computation intensive products are computed in the background; return a notification receipt in HTML format.
    //  public static final int BATCH = 4096;
    int CLEAR_CACHE = 16384;

}


public class Instructions extends Flags implements ActionType, ResultType, OutputType {

    public Instructions(){
    }

    public Instructions(int a){
        this.value = a;
    }

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
        copies.add(Paths.get(filename));
    }

    /** Add specific request to copy the result.
     *
     * @param copies - target files
     */
    public void addCopies(List<Path> copies){
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
        links.add(Paths.get(filename));
    }

    /** Add specific request to link the result.
     *
     *  Several requests can be added.
     *
     * @param links - filenames pointing to the original result
     */
    public void addLinks(List<Path> links){
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
            move = Paths.get(path);
    }

    public void addMove(Path path){
        move = path;
    }

    protected List<Path> copies = new ArrayList<>();
    protected List<Path> links  = new ArrayList<>();
    protected Path move = null;

}
