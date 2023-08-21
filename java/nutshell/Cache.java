package nutshell;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

public class Cache {

    public final JSON jsonConf = new JSON();

    static
    class DiskPath {

        final
        public StringMapper path = new StringMapper();

        public Path resolve(Map<String,?> env){
            return Paths.get(path.toString(env)).normalize();
        }

        public Path query(Path p){
            File file = p.toFile();
            // System.out.printf("Path: %s %b %n", p, file.exists());
            if (file.exists() && file.canRead()){
                return p;
            }
            else {
                return null;
            }
        }

        public Path query(Map<String,?> env){
            Path p = resolve(env);
            return query(p);
        }
    }

    final
    public Map<String,DiskPath> diskPaths = new HashMap<>();

    /**   Read JSON conf file.
     *
     *    Supported sections:
     *    - "dataproxy" : { NAME: { "dir": dirSyntax }}
     *
     * @param filename
     */
    void readConf(String filename){
        try {
            jsonConf.read(filename);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed in opening file %s: %s", filename, e));
        } catch (ParseException e) {
            throw new RuntimeException(String.format("Failed in parsing %s: %s", filename, e));
        }
        System.out.println(jsonConf);
        //JSON.MapJSON sections = jsonConf.getChildren();
        //JSON dataProxy = jsonConf.getChild("dataproxy");
        JSON.MapJSON dataProxies = jsonConf.getChildren(Paths.get("dataproxy"));
        if (dataProxies != null){
            for (JSON.MapJSON.Entry<String,JSON> entry: dataProxies.entrySet()){
                String key = entry.getKey();
                JSON json = entry.getValue();
                if (json != null){
                    JSON jsonDir = json.getChild("dir");
                    if (jsonDir != null){
                        //System.out.println("adding " + key);
                        System.out.printf("%s = %s%n", key, jsonDir);
                        Cache.DiskPath diskPath = new DiskPath();
                        diskPath.path.parse(jsonDir.getValue().toString());
                        diskPaths.put(key, diskPath);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {

        if (args.length == 0){
            System.out.println("Experimental Disk Cache");
            System.out.println("Args: <filename>");
            System.out.println();
            System.exit(1);
        }

        Cache cache = new Cache();

        try {
            cache.readConf(args[0]);
            System.out.println("Result: ");
            System.out.println(cache);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String,Object> env = new HashMap<>();
        env.put("TIMESTAMP_DIR", Paths.get("2017","07", "12"));
        env.put("PRODUCT_DIR", "radar/rack/comp");
        env.put("OUTFILE", "test.png");

        for (Map.Entry<String,Cache.DiskPath> entry: cache.diskPaths.entrySet()){
            Cache.DiskPath diskPath = entry.getValue();
            Path p = diskPath.resolve(env);
            System.out.printf("Testing: %s %b %s  %n",
                    entry.getKey(), (diskPath.query(p) != null), p);

        }

    }
}
