package nutshell;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

//class ProgramRegistry<E> {
class ProgramRegistry {

    //protected
    public TreeMap<String, Program.Parameter> map = new TreeMap<>();



    void add(Program.Parameter param){
        add(param.getName(), param);
    }

    void add(String key, Program.Parameter param){
        map.put(key, param);
    }

    String plainKey(String key){
        if (key.startsWith("--"))
            return key.substring(2);
        else
            return key;
    }

    boolean has(String key){
        return map.containsKey(plainKey(key));
    }

    Program.Parameter get(String key){
        return map.get(plainKey(key));
    }

    public void help(PrintStream stream, String key) {
        key = plainKey(key);
        if (has(key)){
            help(stream, key, get(key));
        }
        else {
            stream.println(String.format("Option '%s' not found", key));
        }
    }

    public void help(PrintStream stream, String key, Program.Parameter param) {

        stream.print("--" + key);
        if (param.hasParams()) {
            stream.print(" " + Arrays.toString(param.getParamKeys()));
        }
        stream.println();
        //stream.println("--" + entry.getKey() + " " + Arrays.toString(param.getParamKeys()) + "");
        stream.println("  " + param.getDescription());
        if (param.hasParams()) {
            // stream.println("  Arguments");
                /*
                for (Object item : param.getParams().entrySet()) {
                    //stream.println(String.format("\t %s [%s]", item.toString()));
                    stream.println(String.format("\t %s", item.toString()));
                }
                 */
            Map<String,Object> m = param.getParams();
            for (Map.Entry item : m.entrySet()) {
                stream.println(String.format("\t %s [%s]", item.getKey(), item.getValue()));
            }
        }
        stream.println();
    }


    public void help(PrintStream stream) {

        for (Map.Entry<String, Program.Parameter> entry : map.entrySet()) {
            help(stream, entry.getKey(), entry.getValue());
        }

    }

    /** Only for debugging.
     *
     */
    protected  void execAll() {
        for (Map.Entry<String, Program.Parameter> entry : map.entrySet()) {
            System.out.println("Exec: " + entry.getKey() + ":");
            entry.getValue().exec();
            System.out.println();
        }
    }

    public void debug(PrintStream stream) {
        for (Map.Entry<String, Program.Parameter> entry : map.entrySet()) {
            stream.println(entry);
            // System.out.println();
        }
    }


    public static void main(String[] args) {

        Program program = new Program();

        ProgramUtils.ExampleOption opt = new ProgramUtils.ExampleOption();
        opt.SECOND = 123.0;

        Program.Parameter limitedOption = new Program.Parameter("FirstOnly", "Handles external obj.",
             opt,  "FIRST,i") {
        };

        Program.Parameter versionOption = new ProgramUtils.Version(program) {
        };

        ProgramRegistry registry = new ProgramRegistry();
        registry.add("example", opt);
        registry.add("limited", limitedOption);
        registry.add("version", versionOption);
        Program.Parameter help = new ProgramUtils.Help(registry);
        //registry.put(new ProgramUtils.Help(registry));
        registry.add(help);

        //Program.Parameter help = new ProgramUtils.Help(registry) {};

        if (args.length == 0){
            registry.help(System.out);
            //registry.execAll();
            System.exit(0);
        }


        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")){
                arg = arg.substring(2);
                if (registry.has(arg)){
                    Program.Parameter parameter = registry.map.get(arg);
                    if (parameter.hasParams()){
                        try {
                            parameter.setParams(args[++i]);
                        } catch (NoSuchFieldException e) {
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
                else {
                    System.err.println(String.format("No such option: '%s'. Type --help for list of commands", arg));
                    System.exit(-1);
                }
            }
            else {
                System.err.println(String.format("Free argument: '%s' (not handled)", arg));
            }

            System.out.println(String.format("Handled: %s", arg) );
            registry.debug(System.out);
            System.out.println("-----");

        }

    }
}