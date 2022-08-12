package nutshell;

import jdk.nashorn.internal.parser.JSONParser;
// import com.google.gson.Gson;


import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.nio.file.FileVisitResult.CONTINUE;

//import org.json.simple.parser.JSONParser;


/** Infrastructure for the actual ProductServer
 *
 */
public class ProductServerBase extends Program {

    final public HttpLog serverLog = new HttpLog(getClass().getSimpleName());

    static final DateFormat logFilenameTimeFormat = new SimpleDateFormat("yyyy-MM-dd");

    public final Map<String,Object> setup = new HashMap<>();

    // Read in config, set in constructor
    public int fileGroupID = 100;  // GROUP_ID
    public Set<PosixFilePermission> dirPerms  = PosixFilePermissions.fromString("rwxrwxr-x");
    public Set<PosixFilePermission> filePerms = PosixFilePermissions.fromString("rw-rw-r--");
    public String user = null;

    public Path confFile    = null; //Paths.get(".", "nutshell.cnf"); //Paths.get("./nutshell.cnf");
    public Path cacheRoot   = Paths.get(".");
    //public Path storageRoot   = Paths.get(".");
    public Path productRoot = Paths.get(".");
    //protected Path storageRoot = Paths.get(".");
    protected Path storageRoot = Paths.get(".");
    // Consider
    final protected List<StringMapper> storagePaths = new LinkedList<>();

    static final public Path cachePrefix = Paths.get("cache");

    /// System side setting.// TODO: conf
    //public String inputCmd = "./input.sh";  // -> ExternalGenerator NOTE: executed in CWD

    /// System side setting. // TODO: conf
    //public String generatorCmd = "./generate.sh";  // NOTE: executed in CWD
    //public String generatorCmd = "generate.sh"; // -> ExternalGenerator   // NOTE: executed in CWD

    //final DateFormat timeStampFormat    = new SimpleDateFormat("yyyyMMddHHmm");
    final DateFormat timeStampDirFormat =
            new SimpleDateFormat("yyyy"+ File.separatorChar+"MM"+File.separatorChar+"dd");

    //protected final List<Path> configFiles = new LinkedList<>();


    static public int counter = 0;

    static public int getProcessId(){
        return  ++counter;
    };

    static public Graph serverGraph = new Graph("Product Server");

    /** Unix PATH variable extension, eg. "/var/local/bin:/media/mnt/bin"
     *
     */
    protected String cmdPath = System.getenv("PATH");

    /** Read configuration file. This operation can be repeated (with --conf ).
     *
    protected void readConfig(){
        readConfig(confFile);
    }
     */

    /** Read configuration file. This operation can be repeated (with --conf ).
     *
     * @param path
     */
    protected void readConfig(String path) {
        readConfig(Paths.get(path));
    }

    /** Read configuration file. This operation can be repeated (with --conf ).
     *
     * Reads the following quantities:
     * - PRODUCT_ROOT
     * - CACHE_ROOT
     * - STORAGE_ROOT
     * - DIR_PERMS
     * - FILE_PERMS
     * - UMASK
     * - LOGFILE
     * - PATH_EXT
     * For explanations, see comments in any nutshell.cnf
     *
     * @param path
     */
    protected void readConfig(Path path){

        try {
            if (path != null) {
                if (this.confFile == null)
                    serverLog.debug(String.format("Reading setup: %s", path.toString()));
                else
                    serverLog.note(String.format("Re-reading setup: %s (old: %s)",
                            path, this.confFile));
                //serverLog.note("Reading setup: " + path.toString());
                MapUtils.read(path.toFile(), setup);
            }
            this.confFile = path; // null ok??
            setup.put("confFile", path);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            setup.put("confFileError", e.getLocalizedMessage());
        }

        //log.debug(setup.toString());

        this.productRoot = Paths.get(setup.getOrDefault("PRODUCT_ROOT", ".").toString());
        this.cacheRoot   = Paths.get(setup.getOrDefault("CACHE_ROOT",   ".").toString());
        this.storageRoot = Paths.get(setup.getOrDefault("STORAGE_ROOT",   ".").toString());

        this.dirPerms = PosixFilePermissions.fromString(setup.getOrDefault("DIR_PERMS","rwxrwxr-x").toString());
        this.filePerms = PosixFilePermissions.fromString(setup.getOrDefault("FILE_PERMS","rw-rw-r--").toString());
        ExternalGenerator.umask = setup.getOrDefault("UMASK","").toString();

        /// Objects
        setup.put("dirPerms", dirPerms);
        setup.put("filePerms", filePerms);

        /// "read-only" variables (for debugging)
        this.user = System.getProperty("user.name"); // $USER
        setup.put("user.name", this.user);

        // Todo: consider optional conf file based  fileGroupID?
        // this.fileGroupID = setup.getOrDefault("FILE_GROUP",  ".").toString();
        Object gid = null; //setup.getOrDefault("GROUP_ID", null);
        try {
            // Default: root of the cache dir
            gid = Files.getAttribute(cacheRoot, "unix:gid");
            // Override with conf value of GROUP_ID, if set.
            gid = setup.getOrDefault("GROUP_ID", gid);
            this.fileGroupID = Integer.parseInt(gid.toString()); // null?
            // this.fileGroupID = Integer.parseInt(Files.getAttribute(cacheRoot, "unix:gid").toString());
        }
        catch (IOException e) {
            serverLog.error(String.format("Could not read group id of cache dir: %s", cacheRoot));
        }
        catch (Exception e) {
            serverLog.error(String.format("Could not derive group id: %s, got %s", cacheRoot, gid));
        }
        setup.put("fileGroupID", this.fileGroupID);

        Object logPathFormat = setup.get("LOGFILE");
        if (logPathFormat != null) {
            Path p = setLogFile(logPathFormat.toString());
            //System.err.println(String.format("Log file: ", p);
        }
        //logPathFormat = "./nutshell-" + System.getenv("USER")+"-%s.log";
        // Path p = setLogFile(logPathFormat.toString());
        //
        if (setup.containsKey("PATH_EXT"))
            this.cmdPath += ":" + setup.get("PATH_EXT").toString();
        setup.put("cmdPath", this.cmdPath);
        // this.generatorScriptName = setup.getOrDefault("CAC",   ".").toString();
        // this.inputScriptName     = setup.getOrDefault("PRO", ".").toString();
    }

    /**
     *
     * @param pathFormat - absolute path of a filename, optionally containing '%s' expanded as timestamp.
     * @return
     */
    public Path setLogFile(String pathFormat) {
        if (pathFormat == null)
            pathFormat = "/tmp/nutshell-%s.log";
        try {
            Path p = Paths.get(String.format(pathFormat,
                    logFilenameTimeFormat.format(System.currentTimeMillis())));
            //ensureFile(p.getParent(), p.getFileName());
            serverLog.setLogFile(p);
            serverLog.debug(setup.toString());
            try {
                Files.setPosixFilePermissions(p, filePerms);
            }
            catch (IOException e){
                serverLog.error(String.format("Could not set permissions: %s", filePerms));
            }
            return p;
        } catch (Exception e) {
            serverLog.setLogFile(null);
            return null;
        }

    }



    Path getProductDir(String productID){
        return Paths.get(productID.replace(".", File.separator));
    }

    Path getTimestampDir(long time){ // consider DAY or HOUR dirs?
        if (time > 0)
            return Paths.get(timeStampDirFormat.format(time));
        else
            return Paths.get("");
    }


    /**
     *   For constructing catalog
     *
     */
    public class GeneratorTracker extends SimpleFileVisitor<Path> {

        /*
        GeneratorTracker(){
            this.startDir = productRoot;
        }
        */

        GeneratorTracker(Path startDir){
            if (startDir == null)
                this.startDir = productRoot;
            else
                this.startDir = productRoot.resolve(startDir);
        }

        Path startDir = null;



        protected void walkSubdir(Path path){
            //File file = path.toFile();

            if (path.toFile().isDirectory()) {
                try {
                    Files.walkFileTree(path, this);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //System.out.println("Directory: " + file.getAbsolutePath());
            }
        }

        void run() throws IOException {

            Files.walk(startDir, 1, FileVisitOption.FOLLOW_LINKS).forEach(path -> walkSubdir(path));


            /*
            Set<FileVisitOption> options = new HashSet<>();
            options.add(FileVisitOption.FOLLOW_LINKS);
            Files.walkFileTree(startDir, options, 10,this);

             */
        }

        //Map<String,Path> generators = new HashMap<>();
        //Set<String> generators = new HashSet<>();
        Set<Path> generators = new HashSet<>();

        String debug(String s){
            return String.format(" '%s': %d", s, s.length());
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attr) throws IOException {
            // System.out.printf("DIR:    %s -> '%s'", dir, dir.getFileName()); //, debug(dir.getFileName()));
            String s = dir.getFileName().toString();
            if (s.startsWith(".")){
                //System.out.println(" SKIP!");
                return FileVisitResult.SKIP_SUBTREE;
            }
            else if (s.equals("bak")){
                return FileVisitResult.SKIP_SUBTREE;
            }
            return CONTINUE;
        }


        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {

            // System.out.printf("  FILE: '%s'%n", path.getFileName().toString());
            if (path.getFileName().toString().equals(ExternalGenerator.scriptName)){  // generatorCmd contains "./"
                Path parentDir = path.getParent();

                File jsonFile = parentDir.resolve("conf.json").toFile();
                if (jsonFile.exists()){
                    //JSONParser parser = new JSONParser(jsonFile);
                    // JSONParser jsonParser = new JSONParser();
                    System.out.printf(" Found: %s -> JSON %s %n", parentDir, jsonFile);
                }
                // System.out.printf(" ADD: %s -> DIR %s %n", path, dir);
                Path dir = productRoot.relativize(parentDir);
                generators.add(dir);
                //System.out.printf(" add: %s%n", dir);
            }
            return CONTINUE;
        }

        // If there is some error accessing
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException e) {
            //serverLog.warn(e.getMessage());
            return CONTINUE;
        }
    }


    public class DeleteFiles extends SimpleFileVisitor<Path> {

        // Print information about
        // each type of file.
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
            if (attr.isRegularFile() || attr.isSymbolicLink()) {
                try {
                    serverLog.note(String.format("Deleting file: %s", file));
                    Files.delete(file);
                } catch (IOException e) {
                    serverLog.warn(e.toString());
                }
            }
            return CONTINUE;
        }

        // Print each directory visited.
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {

            // Prevent removing cacheRoot
            // Todo: fix clearCache
            if (dir.endsWith("cache"))
                return CONTINUE;

            try {
                serverLog.debug(String.format("Delete dir: %s", dir));
                Files.delete(dir);
            } catch (IOException e) {
                serverLog.warn(e.toString());
            }
            return CONTINUE;
        }

        // If there is some error accessing
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException e) {
            serverLog.warn(e.getMessage());
            return CONTINUE;
        }
    }

    public void clearCache(boolean confirm) throws IOException {

        if (!this.cacheRoot.endsWith("cache")){
            serverLog.error("Cache root does not end with 'cache' : " + this.cacheRoot);
            return;
        }

        Path p = this.cacheRoot.toRealPath();
        if (!p.endsWith("cache")){
            serverLog.error("Cache root does not end with 'cache' : " + p);
            return;
        }

        if (confirm){
            System.err.println(String.format("Delete files in %s ? ", p ));
            Scanner kbd = new Scanner(System.in);
            String line = kbd.nextLine();
            if (line.isEmpty() || (line.toLowerCase().charAt(0) != 'y')){
                System.err.println("Cancelled");
                return;
            }
        }

        serverLog.note("Clearing cache: " + p);
        Files.walkFileTree(p, new DeleteFiles());

        serverLog.note("Clearing cache completed");
        //Files.walk(this.cacheRoot).filter(Files::isDirectory).filter(Files::i).forEach(Files::delete);

    }

    public static void main(String[] args) {

        //Set<String> set = new HashSet<>();

        if (args.length == 0){
            System.out.println("Generator tracker: <productRoot> (dir ?)");
            System.out.println("Params: <dir>");
            System.exit(1);
        }


        ProductServerBase serverBase = new ProductServerBase();
        serverBase.productRoot = Paths.get(args[0]);

        Path startDir = serverBase.productRoot;
        if (args.length >= 2)
            startDir = Paths.get(args[1]);

        GeneratorTracker tracker = serverBase.new GeneratorTracker(startDir);

        try {
            tracker.run();
            System.out.println(tracker.generators);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
