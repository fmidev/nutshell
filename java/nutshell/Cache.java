package nutshell;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

        public Path query(Map<String,?> map){
            String s = path.toString(map);
            File file = new File(s);
            if (file.exists()){
                if (file.canRead())
                    return file.toPath();
            }
            return null;
        }
    }

    final
    public Map<String,DiskPath> diskPaths = new HashMap<>();

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

            System.out.println("Example of a JSON structure");
            System.exit(1);
        }

        Cache cache = new Cache();
        Cache.DiskPath diskPath = new DiskPath();

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
            System.out.printf("Testing: %s %n", entry.getKey());
            String s = entry.getValue().path.toString(env);
            System.out.printf("Path: %s %b %n", s, Paths.get(s).toFile().exists());
            // entry.getValue().path.toString();
        }

    }
}
