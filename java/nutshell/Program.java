package nutshell;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;

public class Program {

    //public int[] version = {1,0};
    List<Integer> version = Arrays.asList(1, 0);

    static class Parameter<T> implements Option { // add Program ?E

        protected final String name;
        protected final String description;
        protected String[] paramKeys = new String[0];
        protected T reference = null;


        Parameter(String name, String description) {
            this.name = name;
            this.description = description;
        }

        /**
         *
         * @param name - Command name appearing in command line options.
         * @param description - Short explanation of the command.
         * @param reference - Object. members of which are to be accessed (directly)
         * @param paramKeys - Comma separated names of the members
         */
        Parameter(String name, String description, T reference, String paramKeys) {
            this.name = name;
            this.description = description;
            this.reference = reference;
            if (!paramKeys.isEmpty()) {
                this.paramKeys = paramKeys.split(",");
            }
            else {
                this.paramKeys = new String[0];
            }
        }

        /**
         *
         * @param paramName - Parameter key, also appearing as command name in command line options.
         * @param description - Short explanation of the command.
         * @param reference - Object. members of which are to be accessed (directly)
         */
        Parameter(String paramName, String description, T reference)  {
            this(paramName, description, reference, paramName);
        }


        /** Parameter that is handled as single word, even containing commas (,) or assignments (=) .
         *
         *  The member named $paramKey must be implemented in the derived class.
         *
         */
       static class Single extends Parameter { // add Program ?E

            Single(String name, String description, String paramKey) {
                super(name, description);
                this.paramKey = paramKey;
                setReference(this, paramKey);
            }

            @Override
            public void setParams(String args) throws NoSuchFieldException, IllegalAccessException {
                assign(args);
            }

           @Override
           public void setParams(String[] args) throws NoSuchFieldException, IllegalAccessException {
               assign(String.join(",", args));
           }

           private void assign(String args) throws NoSuchFieldException, IllegalAccessException {
               // System.err.println(String.format("assign: %s='%s'", paramKey, args));
               setParam(paramKey, args);
           }

           private
           final String paramKey;

       }

        /** Lazy extension of @{@link Single} with fixed argument name ("value").
         *
         * @param <T>
         */
        static
        class Simple<T> extends Single {

            Simple(String name, String description,T initValue){
                super(name, description, "value");
                value = initValue;
            }

            public T value;



        }

        @Override
        public boolean hasParams() {
            return paramKeys.length > 0;
        }

        @Override
        public void setParam(String key, Object value) throws NoSuchFieldException, IllegalAccessException {
            //Field field = reference.getClass().getField(key);
            // setField(reference, field, value);
            Manip.assignToObject(key, value, reference);
        }

        static
        private void setField(Object target, Field field, Object value) throws NoSuchFieldException, IllegalAccessException {

            Class c = field.getType();


            // System.err.println(String.format("setField: %s :: %s = %s ", target.getClass(), c.getSimpleName(), value));

            if (c.equals(String.class)) {
                field.set(target, value.toString()); // value.toString()
            }
            else if (c.equals(boolean.class)) { // || c.equals(Boolean.class)){
                field.setBoolean(target, Boolean.parseBoolean(value.toString()));
            }
            else if (c.equals(int.class)) { // || c.equals(Integer.class)){
                field.setInt(target, Integer.parseInt(value.toString()));
            }
            else if (c.equals(float.class)) { // || c.equals(Float.class)){
                field.setFloat(target, Float.parseFloat(value.toString()));
            }
            else if (c.equals(double.class)) { // || c.equals(Double.class)){
                field.setDouble(target, Double.parseDouble(value.toString()));
            }
            else {  // Object, also for <T> members?
                //System.err.println("setField unimplemented class: "  + c);
                field.set(target, value.toString());
            }
        }

        @Override
        public void setParams(String args) throws NoSuchFieldException, IllegalAccessException {
            setParams(args.split(","));
        }

        @Override
        public void setParams(String[] args) throws NoSuchFieldException, IllegalAccessException {
            final String[] keys = this.getParamKeys();
            String lastKeywordParam = null;
            int index = -1;
            for (String arg : args) {
                String[] keyWordArg = arg.split("=", 2);
                if (keyWordArg.length == 2){
                    lastKeywordParam = arg;
                    setParam(keyWordArg[0], keyWordArg[1]);
                }
                else {
                    if (lastKeywordParam != null){
                        throw new IllegalAccessException(String.format("Positional arg (%s) not allowed after keyword arg (%s)",
                                arg, lastKeywordParam  ));
                    }
                    ++index;
                    if (index >= keys.length){
                        throw new NoSuchFieldException(String.format("Index (%d) overflow for array of %d keys: %s",
                                index, keys.length, Arrays.toString(keys)));
                    }
                    setParam(keys[index], arg);
                }
            }


        }

        /** Describes a Parameter and its state (options).
         *
         * @return
         */
        public String toString() {
            // Skip: description
            String p = hasParams() ? getParams().toString() : "";
            if (reference == this){
                return String.format("%s %s", name, p);
                //return String.format("ProgramOption{%s} %s,", name, Arrays.toString(paramKeys));
            }
            else if (reference == null) {
                return String.format("%s(simple) %s", name, p);
            }
            // Externaö reference
            else {
                /*
                for (String s: this.paramKeys){
                    this.ge
                }

                 */
                return String.format("%s*%s : %s", name, reference.toString(),p);
            }

        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String[] getParamKeys() {
            return paramKeys;
        }

        public Object[] getValues() {
            List values = new ArrayList();
            for (String key : this.paramKeys) {
                try {
                    Object value = reference.getClass().getField(key).get(reference);
                    values.add(value);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }
            }
            return values.toArray();
        }

        @Override
        public Map<String, Object> getParams() {
            Map<String, Object> result = new TreeMap<>();
            for (String key: getParamKeys()){
                if (key.isEmpty())
                    continue;
                try {
                    Object value = reference.getClass().getField(key).get(reference);
                    result.put(key, value);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }
            }
            return  result;
        }

        public void exec() {
           // System.err.print(getName() + " exec:");
           //  System.err.println(toString());
        }

        // ------------------------------------


        protected void setReference(T reference){
            this.reference = reference;
            this.paramKeys = collectParamKeys(reference.getClass());
        }

        protected void setReference(T reference, String paramKeys){
            this.reference = reference;
            if (paramKeys.isEmpty())
                this.paramKeys = new String[0];
            else
                this.paramKeys = paramKeys.split(",");
        }


        protected static <T> String[] collectParamKeys(Class<T> c){
            ArrayList<String> list = new ArrayList();
            for( Field field: c.getFields()){
                //if (field.isAccessible()){
                if ((field.getModifiers() & Modifier.PUBLIC) > 0){
                    // System.out.println("OK" + field.getName());
                    list.add(field.getName());
                }
                else {
                    // System.out.println("NO" + field.getName());
                }
            }
            return list.toArray(new String[0]);
        }


    }


    public static void main(String[] args) {

        Program program = new Program();


        ProgramUtils.ExampleOption opt = new ProgramUtils.ExampleOption();
        opt.SECOND = 123.0;

        Program.Parameter limitedOption = new Program.Parameter("FirstOnly", "Handles external obj.",
                "FIRST,i", opt.toString()){
        };

        Program.Parameter versionOption = new ProgramUtils.Version(program){};

        ProgramRegistry registry = new ProgramRegistry();
        registry.map.put("example", opt);
        registry.map.put("limited", limitedOption);
        registry.map.put("version", versionOption);

        registry.help(System.out);
        registry.execAll();

        System.err.println(String.format("Param keys: %s", Arrays.toString(opt.getParamKeys())));
        System.err.println(String.format("Values: %s", Arrays.toString(opt.getValues())));
        System.err.println(opt);
        //System.err.println(limitedOption);
        //System.err.println(versionOption);

        for (int i = 0; i < args.length; i++) {
            try {
                opt.setParams(args[i]);
                System.err.println(opt);
                limitedOption.setParams(args[i]);
                System.err.println(limitedOption);
                // System.err.println(String.format("Param keys: %s", Arrays.toString(opt.getParamKeys())));
                // System.err.println(String.format("Values: %s", Arrays.toString(opt.getValues())));
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }


    }

}
