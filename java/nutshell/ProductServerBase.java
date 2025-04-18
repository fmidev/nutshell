package nutshell;

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
import static java.nio.file.Files.deleteIfExists;


/** Infrastructure for the actual ProductServer
 *
 */
public class ProductServerBase extends Program {

    /** Log for the main process receiving tasks, for example product requests.
     *  For each task, a separate log will be started.
     *
     */
    //final public HttpLog serverLog = new HttpLog("[NutShell]");
    final public HttpLog serverLog = new HttpLog("[NutShell]", Log.Status.UNDEFINED.getIndex());

    static final DateFormat logFilenameTimeFormat = new SimpleDateFormat("yyyy-MM-dd");

    /** Settings for the main log.
     *
     *  - verbosity:
     *  - style:
     *  - format: TEXT, VT100, HTML
     *
     */
    //public String LOG_SERVER = "TEXT,INFO";
    public String LOG_SERVER = "";

    /** Settings for task logs.
     *
     *  - verbosity:
     *  - style:
     *  - format: TEXT, VT100, HTML
     *
     */
    public String LOG_TASKS = "DEBUG,COLOUR";

    /** Format for the task logs.
     *
     */
    // public TextOutput.Format LOG_FORMAT = TextOutput.Format.DEFAULT;

    /** Format for the tasks.
     *
     */
    // final public Flags LOG_STYLE = serverLog.decoration;

    public final Map<String,Object> setup = new HashMap<>();

    // The following HTTP setting are optional

    /** HTTP protocol and host name */
    public String HTTP_HOST = "";

    /** Port for HTTP server */
    public String HTTP_PORT = null; // "8080" or "8000"
    public String HTTP_PREFIX = "/nutshell";
    protected String HTTP_BASE = "";

    /// Numeric group identity. Read in config, set in constructor
    public int GROUP_ID = 0;
    public String DIR_PERMS  = "rwxrwxr-x";
    public String FILE_PERMS = "rw-rw-r--";
    public Set<PosixFilePermission> dirPerms  = PosixFilePermissions.fromString(DIR_PERMS);
    public Set<PosixFilePermission> filePerms = PosixFilePermissions.fromString(FILE_PERMS);

    /** The list of configuration files read.
     */
    public List<Path> confFiles = new ArrayList<>();

    public Path CACHE_ROOT = Paths.get(".");

    // NEW
    public String CACHE_PATH = null;
    public StringMapper cachePathSyntax = null;

    public String STORAGE_PATH = null;
    public StringMapper storagePathSyntax = null;

    //public Path storageRoot   = Paths.get(".");
    public Path PRODUCT_ROOT = Paths.get(".");
    //protected Path storageRoot = Paths.get(".");
    public Path STORAGE_ROOT = Paths.get(".");

    /// User name (alphabetical)
    public String USER = System.getProperty("user.name");;


    final
    public Map<String,String> MAP_URL = new HashMap<>();

    // Consider
    //final protected List<StringMapper> storagePaths = new LinkedList<>();

    // Not configurable at the moment...
    static
    final public Path cachePrefix = Paths.get("cache");

    /// System side setting.// TODO: conf
    //public String inputCmd = "./input.sh";  // -> ExternalGenerator NOTE: executed in CWD

    /// System side setting. // TODO: conf
    //public String generatorCmd = "./generate.sh";  // NOTE: executed in CWD
    //public String generatorCmd = "generate.sh"; // -> ExternalGenerator   // NOTE: executed in CWD

    static
    final DateFormat timeStampDirFormat =
            new SimpleDateFormat("yyyy"+ File.separatorChar+"MM"+File.separatorChar+"dd");
    // = new SimpleDateFormat("yyyyMMddHHmm");

    static public int counter = 0;

    static synchronized
    public int getProcessId(){
        return  ++counter;
    };

    /// Maximum allowed time (in seconds) for product generation (excluding inputs?) FIXME share in two?
    public int TIMEOUT = 60;

    static
    final public Graph serverGraph = new Graph("Product Server");

    /** Unix PATH variable extension, eg. "/var/local/bin:/media/mnt/bin"
     *
     */
    public String PATH = "";
    public String PATH_EXT = "";

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
        //Class cls = Set<PosixFilePermission>.class;
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

        if (path != null) {
            try {
                /*
                if (this.confFiles.isEmpty())
                    serverLog.debug(String.format("Reading setup: %s", path.toString()));
                else
                    serverLog.note(String.format("Re-reading setup: %s (old: %s)",
                            path, this.confFiles));
                 */
                Config.readConfig(path.toFile(), setup);
                this.confFiles.add(path); // null ok??
                setup.put("confFiles", this.confFiles);
            }
            catch (Exception e) {
                e.printStackTrace();
                setup.put("confFileError", e.getLocalizedMessage());
            }
        }

        // Non-empty, if set on command line.
        String LOG_SERVER_OVERRIDE = LOG_SERVER;

        try {
            Manip.assignToObjectLenient(setup, this);
        }
        catch (RuntimeException e){
            serverLog.warn(e.getMessage());
        }

        serverLog.set(LOG_SERVER);

        if (!LOG_SERVER_OVERRIDE.isEmpty()){
            serverLog.set(LOG_SERVER_OVERRIDE);
        }

        serverLog.note(String.format("This is NutShell (%s) server log", getVersion()));
        serverLog.note("Configuration files: ");
        for (Path p: this.confFiles){
            serverLog.note(p.toString());
        }



        if (CACHE_PATH != null){ // deprecating?
            cachePathSyntax = new StringMapper(CACHE_PATH);
        }
        else {
            cachePathSyntax = new StringMapper(CACHE_ROOT.toString()+"/${TIMESTAMP_DIR}/${PRODUCT_DIR}/${OUTFILE}");
        }
        setup.put("cacheDirSyntax", cachePathSyntax);

        if (STORAGE_PATH != null){
            storagePathSyntax = new StringMapper(STORAGE_PATH);
        }
        else {
            storagePathSyntax = new StringMapper(STORAGE_ROOT.toString()+"/${TIMESTAMP_DIR}/${PRODUCT_DIR}/${OUTFILE}");
        }
        setup.put("storagePath", storagePathSyntax);


        /// Ensure quitting processes as well.
        ShellExec.TIMEOUT_SEC = TIMEOUT;

        if (GROUP_ID == 0){
            String gid = "";
            try {
                gid = Files.getAttribute(CACHE_ROOT, "unix:gid").toString();
                // System.err.println(String.format("# Group id: %s %s ", gid, CACHE_ROOT));
                this.GROUP_ID = Integer.parseInt(gid);
            }
            catch (IOException e) {
                serverLog.fail(String.format("Could not retrieve Group id (gid) from '%s'" , CACHE_ROOT));
                serverLog.error(e.getMessage());
            }
            catch (NumberFormatException e) {
                serverLog.warn(String.format("Consider: getent group '%s'", gid));
                serverLog.fail(String.format("Group id (gid) = '%' should have a numeric value", gid));
                // e.printStackTrace();
                serverLog.error(e.getMessage());
            }
        }


        dirPerms = PosixFilePermissions.fromString(DIR_PERMS); // setup.getOrDefault("DIR_PERMS","rwxrwxr-x").toString());
        filePerms = PosixFilePermissions.fromString(FILE_PERMS); // setup.getOrDefault("FILE_PERMS","rw-rw-r--").toString());
        ExternalGenerator.umask = setup.getOrDefault("UMASK","").toString();

        /// Objects
        setup.put("dirPerms", dirPerms);
        setup.put("filePerms", filePerms);

        /// "read-only" variables (for debugging)
        setup.put("user.name", this.USER);

        this.PATH = System.getenv("PATH") + ":" + this.PATH_EXT;

        StringBuilder sb = new StringBuilder();
        sb.append(HTTP_HOST);
        if ((HTTP_PORT != null) && !HTTP_PORT.isEmpty()) {
            sb.append(':').append(HTTP_PORT);
        }
        sb.append(HTTP_PREFIX);
        HTTP_BASE = sb.toString();
        Log.pathMap.put(CACHE_ROOT,   HTTP_BASE+"/cache/");  // <- append relative path
        Log.pathMap.put(STORAGE_ROOT, HTTP_BASE+"/storage/");
		Log.pathMap.put(PRODUCT_ROOT, HTTP_BASE+"/products/");
        //System.err.println(Manip.toString(this, '\n'));




    }

    /**
     *
     * @param path - absolute path of a filename, optionally containing '%s' expanded as timestamp.
     * @return
     */
    public Path setLogFile(String path) {

        if (path == null)
            path = String.format("/tmp/nutshell-%s.log",
                    logFilenameTimeFormat.format(System.currentTimeMillis()));

        try {
            Path p = Paths.get(path);
            FileUtils.ensureWritableFile(p, GROUP_ID, filePerms, dirPerms);
            //FileUtils.ensureWritableFile();
            //Path p = Paths.get(String.format(pathFormat,
            //        logFilenameTimeFormat.format(System.currentTimeMillis())));
            //ensureFile(p.getParent(), p.getFileName());
            serverLog.setLogFile(p);
            // server Log.debug("Setup: " +setup.toString());
            /*
            try {
                Files.setPosixFilePermissions(p, filePerms);
            }
            catch (IOException e){
                serverLog.error(String.format("Could not set permissions: %s", filePerms));
            }*/
            return p;
        } catch (Exception e) {
            serverLog.setLogFile(null);
            serverLog.warn(String.format("Failed in creating log file %s [%s]", path, filePerms));
            return null;
        }

    }

    /** Compose label.
     *
     * @param prefix
     * @param index
     * @return
     *
     */
    protected String getLabel(String prefix, int index){

        ArrayList<String> labelArray = new ArrayList<>();

        if (!prefix.isEmpty()){
            labelArray.add(prefix);
        }

        if (!USER.isEmpty()){
            labelArray.add(USER);
        }

        labelArray.add(String.valueOf(GROUP_ID));

        if (index > 1){ // getTaskId()
            labelArray.add(String.valueOf(index));
        }
        else {
            labelArray.add(Integer.toHexString(Float.floatToIntBits((float)Math.random())));
            //labelArray.add(Long.toHexString(Double.doubleToLongBits(Math.random())));
        }

        return String.join("-", labelArray).replaceAll("[^\\w\\-\\.\\:@]", "_");
    }


    Path getProductDir(String productID){
        return Paths.get(productID.replace(".", File.separator));
    }

    // Consider moving these to @ProductServerBase, with Log param.
    public Path move(Path src, Path dst, Log log) throws IOException {
        log.note(String.format("Move: from: %s ", src));
        log.note(String.format("        to: %s ", dst));

        if (src.toFile().isFile() && dst.toFile().isDirectory())
            dst = dst.resolve(src.getFileName());
        //Files.setPosixFilePermissions(dst, filePerms);

        Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try {
            if (!Files.isSymbolicLink(dst))
                FileUtils.ensureGroup(dst, GROUP_ID, filePerms);
        }
        catch (IOException e){
            // Could be logged?
        }
        //Files.setPosixFilePermissions(dst, filePerms);
        return dst;
        //return src.renameTo(dst);
    }

    public Path copy(Path src, Path dst, Log log) throws IOException {
        log.note(String.format("Copy: from: %s ", src));
        log.note(String.format("        to: %s ", dst));
        if (dst.toFile().isDirectory())
            dst = dst.resolve(src.getFileName());
        return Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    /** Creates a soft link pointing to a file.
     *
     * @param src - original, physical file
     * @param dst - link to be created
     * @param force - overwrite link or file
     * @return successfully generated path
     * @throws IOException if linking fails
     */
    public Path link(Path src, Path dst, boolean force, Log log) throws IOException {

        log.note(String.format("Link: from: %s ", src));
        log.note(String.format("        to: %s ", dst));

        if (dst.equals(src)){
            log.warn(String.format("Link src == dst : %s", dst));
            return dst;
        }

        if (dst.toFile().isDirectory())
            dst = dst.resolve(src.getFileName());

        if (Files.exists(dst)) {

            if (Files.isSymbolicLink(dst)) {
                log.note(String.format("Link exists (already): %s ", dst));
            } else {
                log.note(String.format("Link name exists already as a file: %s ", dst));
            }

            if (Files.isSameFile(src, dst)) {
                //log.debug(String.format("src (file)", src));
                //log.debug(String.format("dst (link)", src));
                log.note("Link exists, pointing to src");
                if (!force) {
                    return dst;
                }
            }
            // Force!
            // Destination differs, or explicit deletion is requested
            Files.delete(dst);
        }

        Path result = Files.createSymbolicLink(dst, src);
        try {
            FileUtils.ensureGroup(result, GROUP_ID, filePerms); // this may always fail in Unix...
        }
        catch (Exception e) {
            log.note(String.format("Failed setting GROUP_ID=%d %s ", GROUP_ID, filePerms));
        }

        return result;
        //return Files.createLink(src, dst);   //(src, dst, StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean delete(Path dst, Log log) throws IOException {
        log.note(String.format("Deleting: %s ", dst));
        return deleteIfExists(dst);
        //return file.delete();
    }

    /*
    Path getTimestampDir(long time){ // consider DAY or HOUR dirs?
        if (time > 0) {
            // timeresolution?
            synchronized (timeStampDirFormat){
                return Paths.get(timeStampDirFormat.format(time));
            }
        }
        else {
            return Paths.get("");
        }
    }

     */


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
                this.startDir = PRODUCT_ROOT;
            else
                this.startDir = PRODUCT_ROOT.resolve(startDir);
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
                Path dir = PRODUCT_ROOT.relativize(parentDir);
                generators.add(dir);
                //System.out.printf(" add: %s%n", dir);
            }
            return CONTINUE;
        }

        // If there is some error accessing
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException e) {
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
                    //server Log.note(String.format("Deleting file: %s", file));
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
                // server Log.debug(String.format("Delete dir: %s", dir));
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

    public class SetPermissions extends SimpleFileVisitor<Path> {

        // Print information about each type of file.
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
            if (attr.isRegularFile() || attr.isSymbolicLink()) {
                try {
                    Files.setPosixFilePermissions(file, filePerms);
                } catch (IOException e) {
                    serverLog.warn(e.toString());
                }
            }
            return CONTINUE;
        }

        // Print each directory visited.
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attr) {

        //    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {

            // Prevent removing cacheRoot
            // Todo: fix clearCache
            if (dir.endsWith("cache"))
                return CONTINUE;

            try {
                Files.setPosixFilePermissions(dir, dirPerms);
                // server Log.debug(String.format("Delete dir: %s", dir));
                // Files.delete(dir);
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

    /** Removes all the files and directories under $CACHE_ROOT
     *
     * @param confirm
     * @throws IOException
     */
    public void clearCache(boolean confirm) throws IOException {

        if (!this.CACHE_ROOT.endsWith("cache")){
            throw new RuntimeException(String.format("Cache root does not end with 'cache': %s",  this.CACHE_ROOT) );
            //serverLog.error("Cache root does not end with 'cache' : " + this.CACHE_ROOT);
            // return;
        }

        Path p = this.CACHE_ROOT.toRealPath();
        if (!p.endsWith("cache")){
            throw new RuntimeException(String.format("Cache root does not end with 'cache': %s -> %s",
                    this.CACHE_ROOT, p) );
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

    public void releaseCache() throws IOException {

        Path p = this.CACHE_ROOT.toRealPath();
        if (!p.endsWith("cache")){
            throw new RuntimeException(String.format("Cache root does not end with 'cache': %s -> %s",
                    this.CACHE_ROOT, p) );
        }

        serverLog.note("Setting write privileges to group in dir: " + p);
        Files.walkFileTree(p, new SetPermissions());

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
        serverBase.PRODUCT_ROOT = Paths.get(args[0]);

        Path startDir = serverBase.PRODUCT_ROOT;
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
