package nutshell;


import java.io.IOException;
import java.nio.file.*;
//import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.Files.exists;

public class FileUtils {

    static private int getBit() {
        int counter = 0;
        return (1 << counter++);
    }


    interface Owner {
        int USER   = getBit();
        int GROUP  = getBit();
        int OTHERS = getBit();
        int OWNER = USER | GROUP | OTHERS;
    }

    interface Permission {
        int READ  = getBit();
        int WRITE = getBit();
        int EXEC  = getBit();
        //int PERMISSIONS = getBit();
    }


    class Status implements Owner, Permission {
        // ..
    }

    static
    //public final Pattern filePathRe = Pattern.compile("^([^/]*)((/[\\w]*)+)(/[^/]*)$");
    //public final Pattern filePathRe = Pattern.compile("^([^/]*)((/[\\w]*)+/)([\\w]+\\.[a-z]{1,4})?(\\W[^/]*)?$",
    //public final Pattern filePathRe = Pattern.compile("^([^/]*)((/\\w*)+/)([.\\w]+\\.[a-z]+)?(\\s.*)?$",
    public final Pattern filePathRe = Pattern.compile("^([^/]*)((/\\w*)+/)(\\S+\\.[a-z0-9]+)?(\\W.*)?$",
            Pattern.CASE_INSENSITIVE);

    static
    public Path extractPath(String line){

        Matcher m = filePathRe.matcher(line);
        if (m.matches()){
            //System.out.printf("Matches, %d groups:%n", m.groupCount());
            for (int j = 0; j <= m.groupCount(); j++) {
                System.out.printf("  %d ->  %s %n", j, m.group(j));
            }
            // System.out.println(m);
            // crop leading (0) and trailing (-1)
            String dir  = m.group(2);
            String file = m.group(m.groupCount()-1);

            if (file == null)
                return Paths.get(dir);
            else
                return Paths.get(dir, file);
        }
        return Paths.get("");
    };


    /** If a path exists, try to ensure it is writable. If needed, create paths recursively.
     *
     * @param path
     * @param groupId
     * @param permissions
     * @throws IOException
     */
    static
    public int ensureWritableDir(Path path, int groupId, Set<PosixFilePermission> permissions) throws IOException {

        int experimentalResult = 0;

        if ((path == null) || (path.getNameCount()==0)){
            return experimentalResult;
        }

        // Consider constructing error string (of GID and perms)
        if (!Files.exists(path)){
            experimentalResult |= ensureWritableDir(path.getParent(), groupId, permissions);
            try {
                Files.createDirectory(path); // potentially throws IOException
            }
            catch (Exception e){
                experimentalResult |= Permission.WRITE; // or "ALL" ?
                // Exit, because does dir not exist now.
                throw e;
            }

            try {
                Files.setAttribute(path, "unix:gid", groupId);
            }
            catch (Exception e){
                experimentalResult |= Owner.GROUP;
            }
        }
        else if (Files.isWritable(path)){
            return experimentalResult;
        }


        try {
            Files.setPosixFilePermissions(path, permissions);
        }
        catch (Exception e){
            experimentalResult |= Permission.WRITE; // or "ALL" ?
        }

        if (!Files.isWritable(path)){
            throw new IOException(String.format("Failed in creating dir %s", path, permissions));
        }

        return experimentalResult;

    }

    static
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

        /*
        try {
            //System.err.println(String.format("Trying to set groupID: %d for %s", groupId, path));
            Files.setAttribute(path, "unix:gid", groupId);
        }
        catch (IOException e){
            //System.err.println(e);
            // May be group writable, just altering forbidden?
        }

        try {
            //System.err.println(String.format("Trying to set filePerms: %s ", filePerms));
            Files.setPosixFilePermissions(path, filePerms);
        }
        catch (IOException e){
            //System.err.println(e);
            // May be group writable, just altering forbidden?
        }
        */

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

        /*
        try {
            //System.err.println(String.format("Trying to set groupID: %d for %s", groupId, path));
            Files.setAttribute(path, "unix:gid", groupId);
        }
        catch (IOException e){
            System.err.println(e);
            // May be group writable, just altering forbidden?
        }
        */
        Files.setAttribute(path, "unix:gid", groupId);
        Files.setPosixFilePermissions(path, perms);

    }

    /*
    public Set<PosixFilePermission> dirPerms = PosixFilePermissions.fromString("rwxrwxr-x");
    public FileAttribute<Set<PosixFilePermission>> dirPermAttrs = PosixFilePermissions.asFileAttribute(dirPerms);

    public Set<PosixFilePermission> filePerms = PosixFilePermissions.fromString("rw-rw-r--");
    public FileAttribute<Set<PosixFilePermission>> filePermAttrs = PosixFilePermissions.asFileAttribute(filePerms);
    */
    static
    public Path ensureDirOLD(Path root, Path relativePath, Set<PosixFilePermission> perms) throws IOException {

        if (relativePath==null)
            return root;

        if (relativePath.getNameCount() == 0)
            return root;

        Path path = root.resolve(relativePath);
        //this.log.warn(String.format("Checking: %s/./%s",  root, relativePath));

        if (!Files.exists(path)) {
            // Consider(ed?):
            // Files.createDirectories(path, PosixFilePermissions.asFileAttribute(perms));
            ensureDirOLD(root, relativePath.getParent(), perms);
            //this.log.debug(String.format("Creating dir: %s/./%s",  root, relativePath));
            if (perms == null) {
                Files.createDirectory(path);
            }
            else {
                Files.createDirectory(path, PosixFilePermissions.asFileAttribute(perms));

            }
        }
        /*
        else {
            //this.log.warn(String.format("Exists dir: %s/./%s",  root, relativePath));
            //Files.setPosixFilePermissions(path, perms);
        }
         */

        /*
        try {
            //this.log.debug(String.format("Changing permissions for existing dir: %s/./%s",  root, relativePath));
            Files.setPosixFilePermissions(path, perms);
        }
        catch (IOException e){
            if (Files.isWritable(path)) {
                throw new IndexedException(HttpServletResponse.SC_FORBIDDEN, "Failed in modifying file permissions");
            }
            else {
                throw e;
            }
        }

         */

        return path;
    }

    static
    public Path ensureFileOLD(Path root, Path relativePath, Set<PosixFilePermission> dirPerms, Set<PosixFilePermission> filePerms) throws IOException {

        Path path = root.resolve(relativePath);

        if (!Files.exists(path)) {
            ensureDirOLD(root, relativePath.getParent(), dirPerms);
            if (filePerms == null) {
                Files.createFile(path);
                //Files.createDirectories(path, PosixFilePermissions.asFileAttribute(perms));
            }
            else {
                Files.createFile(path, PosixFilePermissions.asFileAttribute(filePerms));
            }
        }

        // TODO: Files.isRegularFile()
        /*
        try {
            //this.log.debug(String.format("Changing permissions for existing file: %s/./%s",  root, relativePath));
            Files.setPosixFilePermissions(path, filePerms);
        }
        catch (IOException e){
            // Convert IOException to weaker, informative message.
            throw new IndexedException(HttpServletResponse.SC_FORBIDDEN, "Failed in modifying file permissions");
        }
        */

        return path;

    }

    public static void main(String[] args) {

        if (args.length == 0){
            System.out.println("Tests file ops");
            System.out.println("Usage: ");
            // System.out.println("  argument: <log_level>  #" + statusCodes.entrySet());
            // log.warn("Quitting");
            return;
        }

        Path prefix = Paths.get(".");
        Set<PosixFilePermission> filePerms = PosixFilePermissions.fromString("rwxr-xr--");
        Set<PosixFilePermission> dirPerms = PosixFilePermissions.fromString("rwxr-xr-x");

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
                    case "--mkdir": {
                        Path p = Paths.get(args[++i]);
                        ensureDirOLD(prefix, p, dirPerms);
                        break;
                    }
                    case "--touch": {
                        Path p = Paths.get(args[++i]);
                        ensureFileOLD(prefix, p, dirPerms, filePerms);
                        break;
                    }
                    case "--visit": {
                        //Path startingDir = Paths.get(args[++i]);
                        //DeleteFiles pf = new DeleteFiles();
                        //Files.walkFileTree(startingDir, pf);
                        break;
                    }
                    case "--status":
                        System.out.println(String.format("prefix:\t%s", prefix));
                        System.out.println(String.format("dirPerms:\t%s", dirPerms));
                        System.out.println(String.format("filePerms:\t%s", filePerms));
                        //filePerms.
                        //System.out.println(filePerms.toArray());
                        break;
                    default:
                        Matcher m = filePathRe.matcher(arg);
                        if (m.matches()){
                            System.out.printf("Matches, %d groups:%n", m.groupCount());
                            for (int j = 0; j <= m.groupCount(); j++) {
                                System.out.printf("  %d: %s %n", j, m.group(j));
                            }
                            Path p = extractPath(arg);
                            System.out.println(p);
                        }
                        else
                            System.out.println(String.format("No such option: %s", arg));
                        break;
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

}
