package nutshell10;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public class Program {


    // List<Integer> version = Arrays.asList(1, 0);

    public String getVersion(){
        final List<Integer> v = Arrays.asList(1, 0);
        return v.stream()
            .map(i -> i.toString())
            .collect( Collectors.joining(".") );
    }




    static class Parameter<T> extends BeanLike { // add Program ?E

        protected T reference = null;

        Parameter(String name, String description) {
            super(name, description);
        }

        /**
         *
         * @param name - Command name appearing in command line options.
         * @param description - Short explanation of the command.
         * @param reference - Object. members of which are to be accessed (directly)
         * @param paramKeys - Comma separated names of the members
         */
        Parameter(String name, String description, T reference, String paramKeys) {
            super(name, description);
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
                // Note: "this" does not exist before super()
                this.paramKey = paramKey;
                setReference(this, paramKey);
            }

            Single(String name, String description, String paramKey, Object reference) {
                super(name, description);
                // Note: "this" does not exist before super()
                this.paramKey = paramKey;
                setReference(reference, paramKey);
            }

            @Override
            public void setParams(String args) throws NoSuchFieldException, IllegalAccessException {
                // System.err.println(String.format("Debug: %s", reference.getClass()));
                // System.err.println(String.format("Debug: %s", Manip.toString(reference)));
                // return;
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

            protected
            Simple(String name, String description, T initValue, String paramKey){
                super(name, description, paramKey);
                cls = initValue.getClass(); // or from T ?
                value = initValue;
                setReference(this, paramKey);
            }

            Simple(String name, String description, T initValue){
                this(name, description, initValue, "value");
            }

            public T value;

            final private Class cls;

            @Override
            public void setParam(String key, Object value) throws NoSuchFieldException, IllegalAccessException {
                Field field = reference.getClass().getField(key);
                // setField(reference, field, value);
                Manip.assignToObject(value, reference, field, cls);
            }

        }

        static
        class Const<T> extends Simple<T> {

            Const(String name, String description, T initValue){
                super(name, description, initValue, "");
            }


        }


        @Override
        public void setParam(String key, Object value) throws NoSuchFieldException, IllegalAccessException {
            Manip.assignToObject(value, reference, key);
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
        @Override
        public String toString() {
            // Skip: description
            return valuesToString();
            /*
            if (reference == null) {
                return String.format("%s (simple) %s", name, valuesToString());
            }
            else if (reference == this){
                return String.format("%s %s", name, valuesToString());
            }
            else {
                return String.format("%s -> %s", name, valuesToString());
            }

             */

        }

        /** Returns a simple value directly or a map of object values.
         *
         *  - value
         *  -
         *  - ClassName.{key1=value1,key2=value2,...}
         *
         *
         * @return
         */
        public String valuesToString() {
            String p = hasParams() ? getParams().toString() : "";
            if ((reference == null) || (reference == this)){
                return p;
            }
            else {
                return String.format("%s.%s", reference.getClass().getSimpleName(),p);
            }
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

        public void exec() throws RuntimeException{
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
