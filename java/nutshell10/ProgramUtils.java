package nutshell10;

import java.util.stream.Collector;
import java.util.stream.Collectors;

public class ProgramUtils {


    static
    public class ExampleOption extends Program.Parameter {
        public String FIRST = "1";
        public double SECOND = 123.456;
        //public Integer i = new Integer(0);
        public int i = 432;
        public Integer i2 = 3;

        ExampleOption() {
            //super()
            super("example", "Just an example");
            this.reference = this;
            this.paramKeys = collectParamKeys(getClass());
        }
    }

    /** Version displayer for something that has a version...
     *
     * @param <E>
     */
    static
    public class Version<E extends Program> extends Program.Parameter<E> {

        Version(E program) {
            super("version", "Show program version", program, "");
        }

        @Override
        public void exec() {
            // Collector<CharSequence, ?, String> c = Collectors.joining(".");
            /*
            String s = reference.version.stream()
                .map(i -> i.toString())
                .collect( Collectors.joining(".") );
                 //.collect( Collectors.joining(",","(",")") );*/
            System.out.println(reference.getVersion());
        }

        // static Collector
    }

    /** Set verbosity of logging
     *
     */
    static
    public class LogLevel extends Program.Parameter<Object> {

        // Base class constructor
        LogLevel(String name, Log log, Log.Status init) {
            super(name, String.format("Same as --log_level %s", init.toString()));
            this.level = init;
            this.log = log;
        }

        // Fival class constructor
        LogLevel(Log log) {
            super("log_level", "Set verbosity level");
            this.log = log;
            this.level = Log.Status.UNDEFINED;
            for (Log.Status status:  Log.Status.values()){
                if (status.level == log.getVerbosity()){
                    this.level = status;
                }
            }
            setReference(this, "level");
        }


        @Override
        public void exec() {
            log.setVerbosity(level);
            /*
            try {
                log.setVerbosity(level);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }

             */
        }

        //public String level = Log.Status.NOTE.toString();
        public Log.Status level;
        protected final Log log;

        static
        public class Verbose extends LogLevel {
            Verbose(Log log){
                super("verbose", log, Log.Status.LOG);
            }
        }

        static
        public class Debug extends LogLevel {
            Debug(Log log){
                super("debug", log, Log.Status.DEBUG);
            }
        }

    }



    static
    public class Help extends Program.Parameter.Simple<String> {
        //public class Help extends Program.Parameter<ProgramRegistry> {

        Help(ProgramRegistry program) {
            super("help", "Display help", "");
            this.program = program;
        }

        ProgramRegistry program = null;

        @Override
        public void exec() {
            if ((this.value == null) || (this.value.isEmpty()))
                program.help(System.out);
            else
                program.help(System.out, this.value);
            //System.out.println(Arrays.toString(reference.version));
        }
    }
}
