package nutshell;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.*;
//import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static java.nio.file.FileVisitResult.*;

public class FileUtils {


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
                        System.out.println(String.format("No such option: %s", arg));
                        break;
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

}
