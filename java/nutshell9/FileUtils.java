package nutshell9;


import java.io.File;
import java.io.IOException;
import java.nio.file.*;
//import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.text.ParseException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.Files.exists;

public class FileUtils {


	/** For initializing a path.
	 *  
	 */
	static public Path getEmptyPath() {
		return Paths.get("");
	}

	/** For initializing a path or testing, if a path is empty.
	 *  
	 */
	final private static Path emptyPath = getEmptyPath();

	
	static public boolean isEmpty(Path path) {
		return path.equals(emptyPath); 
	}
	
    static private int getBit() {
        int counter = 0;
        return (1 << counter++);
    }

    /**
     *  Could be based on {@link PosixFilePermission}
     */
    interface Owner {
        int USER   = FileUtils.getBit();
        int GROUP  = FileUtils.getBit();
        int OTHERS = FileUtils.getBit();
        int OWNER = USER | GROUP | OTHERS;
    }

    /*  Future extension (explain)
    interface Permission {
        int READ  = getBit();
        int WRITE = getBit();
        int EXEC  = getBit();
        //int PERMISSIONS = getBit();
    }


    class Status implements Owner, Permission {
        // ..
    }
     */

    //public final Pattern filePathRe = Pattern.compile("^([^/]*)((/[\\w]*)+)(/[^/]*)$");
    //public final Pattern filePathRe = Pattern.compile("^([^/]*)((/[\\w]*)+/)([\\w]+\\.[a-z]{1,4})?(\\W[^/]*)?$",
    //public final Pattern filePathRe = Pattern.compile("^([^/]*)((/\\w*)+/)([.\\w]+\\.[a-z]+)?(\\s.*)?$",
    /**
     *  Conventionally accepted filename chars (\w, "word characters") complemented with '@', '-', ':', '.' and '='
     *  1 (leading chars?)
     *  1 = directory
     *  2 = filename
     *  3 = extension (optional)
     */
    static
    final String validChars = "a-z0-9_@,:.=+\\-"; // inside brackets, requiring escaping only for '-' (and '^')
    // final String validChars = "a-z0-9_@:\\.\\-\\+\\=";

    /// Path that has an optional directory part and an obligatory filename with extension.
    /**
     *  Matcher groups
     *  0 = source string (convention)
     *  1 = optional directory
     *  2 = full filename (including  extension)
     *  2 = filename without extension
     *  3 = extension (without leading '.')
     */
    static
    // public final Pattern filePathRe = Pattern.compile("^([^/]*)((/\\w*)+/)(\\S+\\.[a-z0-9]+)?(\\W.*)?$",
    // +"^([a-z0-9_:\\.\\-]*/)?([a-z0-9_:\\.\\-\\=]+)(\\.[a-z0-9]+)?$"
    public final Pattern qualifiedFilePathRe = Pattern.compile(
            String.format("^([%s/]*/)?(([%s]+)\\.([a-z0-9]+))", validChars, validChars),
            Pattern.CASE_INSENSITIVE);

    /// A path that ends with dir separator '/'.
    /**
     *   Matcher groups
     *   # 0 - source string (convention)
     *   # 1 -
     */
    static
    // public final Pattern filePathRe = Pattern.compile("^([^/]*)((/\\w*)+/)(\\S+\\.[a-z0-9]+)?(\\W.*)?$",
    // +"^([a-z0-9_:\\.\\-]*/)?([a-z0-9_:\\.\\-\\=]+)(\\.[a-z0-9]+)?$"
    public final Pattern qualifiedDirPathRe = Pattern.compile(
            String.format("^[%s/]*/", validChars),
            Pattern.CASE_INSENSITIVE);

    static
    public Path extractPath(String line) throws ParseException {

        Matcher m = qualifiedFilePathRe.matcher(line);
        if (m.matches()){

            /*  // below
            for (int j = 0; j <= m.groupCount(); j++) {
                System.out.printf("  %d ->  %s %n", j, m.group(j));
            }
            */

            String dir; // m.group(1); // 2
            String file = null;
            switch (m.groupCount()){
                case 4:
                case 3:
                    file = m.group(2);
                case 2:
                    dir  = m.group(1); // 2
                    break;
                case 1:
                default:
                    for (int j = 0; j <= m.groupCount(); j++) {
                        System.out.printf("  %d ->  %s %n", j, m.group(j));
                    }
                    throw new ParseException(String.format("Could not parse %s", line), 2);
            }
            if ((dir != null) && (!dir.equals(""))){
                return Paths.get(dir, file);
            }
            else {
                return Paths.get(file);
            }
       }
        return Paths.get("");
    };

    /** Seconds since last modification.
     *
     * @param file - target file or directory
     * @return time [seconds] passed since last modification.
     */
    static
    public long fileModificationAge(File file){
        return (java.lang.System.currentTimeMillis() - file.lastModified()) / 1000;
    }


    /** If a path exists, try to ensure it is writable. If needed, create paths recursively.
     *
     * @param path
     * @param groupId
     * @param permissions
     * @throws IOException
     */
    static
    public void ensureWritableDir(Path path, int groupId, Set<PosixFilePermission> permissions) throws IOException {

        if ((path == null) || (path.getNameCount()==0)){
            return; 
        }

        // Note: a directory may exist, but file not.
        if (!Files.exists(path)) {
            // Consider constructing error string (of GID and perms)

            // Ensure parents, recursively
            ensureWritableDir(path.getParent(), groupId, permissions);
            
            Files.createDirectory(path); // throws IOException

            // Not strict.
            try {
                Files.setAttribute(path, "unix:gid", groupId);
            } catch (Exception e) {
                // experimentalResult |= Owner.GROUP;
            }
        }
        else if (Files.isWritable(path)){
            // In this case, does not try to change file ownership / permissions.
            return;
        }

        // Not strict, at least not yet.
        try {
            Files.setPosixFilePermissions(path, permissions);
        }
        catch (Exception e){
        }

        /*
        Set<?> perms = Files.getPosixFilePermissions(path);
        if (perms.contains(PosixFilePermission.OWNER_WRITE) ||
            perms.contains(PosixFilePermission.GROUP_WRITE) ||
            perms.contains(PosixFilePermission.OTHERS_WRITE)
        ){
            // OK
        }
        else {
         */
        if (!Files.isWritable(path)){
        	try {
        		UserPrincipal owner = Files.getOwner(path); // System.getProperty("user.name");
            	throw new IOException(String.format("Dir %s owned by %s is NOT WRITABLE by this user %s: %s",
                        path, owner, System.getProperty("user.name"), Files.getPosixFilePermissions(path)));
        	}
        	catch (IOException e) {
               	throw new IOException(String.format("Dir %s is NOT WRITABLE by this user %s: %s – and owner cannot be read",
                        path, System.getProperty("user.name"), Files.getPosixFilePermissions(path)));
				
			}
        	
        }

    }


    /** If a path exists, try to ensure it is writable. If needed, create paths recursively.
         * 
         * Does not create actual files, but parent directories as needed.
         * 
         * @param path
         * @param groupId
         * @param dirPermissions
         * @throws IOException
         */
    static
    public Path ensureWritablePath(String path, int groupId, Set<PosixFilePermission> dirPermissions) throws IOException {
        //int experimentalResult = 0;

        if ((path == null) || (path.isEmpty())){
            return FileUtils.getEmptyPath();
        }

        Matcher filenameMatcher = qualifiedFilePathRe.matcher(path.toString());
        if (filenameMatcher.matches()) {
            String dir = filenameMatcher.group(1);
            if (dir == null) {
                // System.err.println("XX: No dir");
                return Paths.get(".").resolve(path);
            }
            else {
                path = dir;
                //path = Paths.get(dir);
            }
        }
        else {
            Matcher dirNameMatcher = qualifiedDirPathRe.matcher(path.toString());
            if (!dirNameMatcher.matches()) {
                throw new IOException(
                        String.format("Bad dir/file name: '%s' - use valid chars [%s], and extension or trailing /",
                                path, validChars));
            }
        }

        // By now, path points to a directory
        Path p = Paths.get(path);
        // System.err.println(String.format("XX: creating dir: %s", p));
        ensureWritableDir(p, groupId, dirPermissions);
        return p;
    }


    synchronized static
    public void ensureWritableFile(Path path, int groupId, Set<PosixFilePermission> filePerms, Set<PosixFilePermission> dirPerms) throws IOException {

        if (!exists(path)) {
            if (dirPerms == null){
                // FIX: this may add execution rights unintentionally?
                dirPerms = filePerms;
            }
            FileUtils.ensureWritableDir(path.getParent(), groupId, dirPerms);
            // serverLog.debug("creating file: " + path);
            // Files.createFile(path, PosixFilePermissions.asFileAttribute(filePerms));
            Files.createFile(path); // potentially throws IOException
            // Files.setPosixFilePermissions(path, filePerms);
        }

        try {
            ensureGroup(path, groupId, filePerms);
        }
        catch (IOException e){
        }

        if (!Files.isWritable(path)){
            throw new IOException(String.format("Failed in creating writable file %s (%s)", path, filePerms));
        }

    }

    static
    public void ensureGroup(Path path, int groupId, Set<PosixFilePermission> perms) throws IOException {
        Files.setAttribute(path, "unix:gid", groupId);
        Files.setPosixFilePermissions(path, perms);
    }




    public static class MoveDir extends SimpleFileVisitor<Path> {

        final public Path src;
        final public Path dst;

        /**
         *
         * @param src - leading part of path that will be stripped (relativized)
         * @param dst - target dir - will be created in does not exist.
         * @throws IOException
         */
        MoveDir(Path src, Path dst) throws IOException {
            this.src = src;
            this.dst = dst;
            // Files.createDirectory(dst);
        }
        @Override

        public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) throws IOException {

            // System.out.println(String.format("# Visiting %s ", path));
            // Path dstCurrent = dst.resolve(path.relativize(src).normalize());
            // Path dstCurrent = dst.resolve(src.relativize(path));

            for (File file: path.toFile().listFiles()){
                Path p = file.toPath();
                Path d = dst.resolve(src.relativize(p));
                if (file.isDirectory()){
                    // Path dirPath = file.toPath();
                    // System.out.println(String.format("# MkDir %s ", d));
                    Files.createDirectories(d); // Attribs!
                    //dstCurrent.resolve(file.toPath()).toFile().createNewFile();
                }
                else {
                    // System.out.println(String.format("# Move %s -> %s", file, d));
                    //Files.move(file.toPath(), d, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    Files.move(file.toPath(), d, StandardCopyOption.REPLACE_EXISTING);
                }
                // Catch STOP
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {

            // System.out.println(String.format("RmDir %s", path));
            /*
            System.out.println(String.format("Visiting %s ", path));
            for (File file: path.toFile().listFiles()) {
                if (file.isDirectory()) {
                    // Path dirPath = file.toPath();
                }

            }
            */
            Files.deleteIfExists(path);
            /*
            try {
                Files.deleteIfExists(path);
            }
            catch (IOException e){

            }
            */

            return FileVisitResult.CONTINUE;
        }
    }

    public static void main(String[] args) {

        if (args.length == 0){
            System.out.println("Tests file ops");
            System.out.println("Usage: ");
            System.out.println("--prefix <path> : store file path prefix");
            System.out.println("--mkdir  <dir>/ : make dir <prefix><dir>");
            System.out.println("--ensuredir <path> : make dir, stripping optional filename from path");
            //System.out.println("--mk  <dir>  : make dir <prefix><dir>");
            // System.out.println("  argument: <log_level>  #" + statusCodes.entrySet());
            // log.warn("Quitting");
            return;
        }

        Path prefix = Paths.get("");
        Set<PosixFilePermission> filePerms = PosixFilePermissions.fromString("rwxr-xr--");
        Set<PosixFilePermission> dirPerms = PosixFilePermissions.fromString("rwxr-xr-x");

        // Posix group ID
        int groupID = 0;

        try {

            for (int i = 0; i < args.length; i++) {

                String arg = args[i];

                switch (arg) {
                    case "--prefix":
                        prefix = Paths.get(args[++i]);
                        break;
                    case "--filePerms":
                        filePerms = PosixFilePermissions.fromString(args[++i]);
                        break;
                    case "--dirPerms":
                        dirPerms = PosixFilePermissions.fromString(args[++i]);
                        break;
                    case "--group":
                        groupID = Integer.parseInt(args[++i]);
                        break;
                    case "--mkdir": {
                        Path p = Paths.get(args[++i]);
                        ensureWritableDir(prefix.resolve(p), groupID, dirPerms);
                        // ensureDirOLD(prefix, p, dirPerms);
                        break;
                    }
                    case "--touch": {
                        Path p = Paths.get(args[++i]);
                        ensureWritableFile(prefix.resolve(p), groupID, dirPerms, filePerms);
                        // ensureFileOLD(prefix, p, dirPerms, filePerms);
                        break;
                    }
                    case "--ensuredir": {
                        //Path p = Paths.get(args[++i]);
                        String p = prefix.toString() + args[++i];
                        Path path = ensureWritablePath(p, groupID, dirPerms);
                        // ensureDirOLD(prefix, p, dirPerms);
                        System.out.println(String.format("Extracted dir: %s", path));
                        break;
                    }
                    case "--visit": {
                        //Path startingDir = Paths.get(args[++i]);
                        //DeleteFiles pf = new DeleteFiles();
                        //Files.walkFileTree(startingDir, pf);
                        }
                    break;
                    case "--move": {
                        Path srcDir = Paths.get(args[++i]);
                        Path dstDir = Paths.get(args[++i]);
                        Files.walkFileTree(srcDir, new MoveDir(srcDir, dstDir));
                    }
                    break;
                    case "--status":
                        System.out.println(String.format("prefix:\t%s", prefix));
                        System.out.println(String.format("dirPerms:\t%s", dirPerms));
                        System.out.println(String.format("filePerms:\t%s", filePerms));
                        System.out.println(String.format("groupID:\t%d", groupID));
                        //filePerms.
                        //System.out.println(filePerms.toArray());
                        break;
                    default:
                        if (arg.startsWith("--")){
                            System.out.println(String.format("No such option: %s", arg));
                        }
                        else {

                            File file = new File(arg);
                            System.out.println(String.format("File: %s", file));
                            System.out.println(String.format("File: Dir name: %s", file.getParent()));
                            System.out.println(String.format("File: Filename: %s", file.getName()));

                            Path path = Paths.get(arg);
                            System.out.println(String.format("Path: %s", path));
                            System.out.println(String.format("Path: Dir name: %s", path.getParent()));
                            System.out.println(String.format("Path: Filename: %s", path.getFileName()));

                            Matcher m = qualifiedFilePathRe.matcher(arg);
                            if (m.matches()){
                                System.out.printf("Matches, %d groups:%n", m.groupCount());
                                for (int j = 0; j <= m.groupCount(); j++) {
                                    System.out.printf("  %d: %s %n", j, m.group(j));
                                }
                                System.out.println(String.format("extractPath(%s):", arg));
                                Path p = null;
                                try {
                                    p = extractPath(arg);
                                } catch (ParseException e) {
                                    System.err.println(String.format("Failed in parsing path: %s", arg));
                                }
                                System.out.println(p);
                            }
                            else {
                                System.out.println(String.format("Illegal path: %s", arg));
                            }

                        }
                        break;
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

}
