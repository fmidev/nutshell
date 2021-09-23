package nutshell;


import java.lang.reflect.Field;
import java.util.*;

public class Flags {

    public Flags(){
        value = 0;
    }

    public Flags(int v){
        value = v;
    }

    static public int getValue(Object obj, String flags) throws NoSuchFieldException, IllegalAccessException{
        return getValue(obj, flags.split(","));
    }

    public int getValue(String flags) throws NoSuchFieldException, IllegalAccessException{
        return getValue(this, flags.split(","));
    }

    static public int getValue(Object obj, String[] flags) throws NoSuchFieldException, IllegalAccessException {

        int result = 0;

        Class c = (obj instanceof  Class) ? (Class)obj : obj.getClass();

        for (String s : flags) {
            // Check here if s  contains comma-separated values.
            String[] subFlags = s.split(",");
            if (subFlags.length > 1)
                 result = result | getValue(obj, subFlags);
            else
                result = result  | c.getField(s).getInt(obj);
        }

        return result;
    }

    public int getValue(String[] flags) throws NoSuchFieldException, IllegalAccessException {
        return getValue(this, flags);
    }

    static public String[] getFlags(Object obj, int value) throws IllegalAccessException{

        Set<String> result = new HashSet<>();

        Class c = (obj instanceof  Class) ? (Class)obj : obj.getClass();

        for (Field field : c.getFields()) {
            String name = field.getName();
            if (name.equals(name.toUpperCase())) { // TODO isValid(name)
                int i = field.getInt(obj);
                if ((i & value) == i){ // i fully covered
                    result.add(name);
                }
            }
        }

        return result.toArray(new String[0]);
    }

    public String[] getFlags(int value) throws IllegalAccessException{
        return getFlags(this, value);
    }

    static
    public String[] getKeys(Class c){
        //return getFlags(this, value);
        return ClassUtils.getConstants(c).keySet().toArray(new String[0]);
    }

    public String[] getEntries() {
        return ClassUtils.getConstants(getClass()).entrySet().toArray(new String[0]);
    }

    public Map<String,Integer> getMap() {
        return ClassUtils.getConstants(getClass());
    }



    /// Further NON-STATICS

    public int value = 0;

    /**
     * @see #add(int) .
     * @param i
     */
    public void set(int i){
        value = i;
        //return true;
        //return this;
    }

    public void set(String s) throws NoSuchFieldException, IllegalAccessException {
        set(s.split(","));
    }

    public void set(String[] s) throws NoSuchFieldException, IllegalAccessException {
        value = getValue(s);
        /*
        try {
            //value = Flags.getValue(AccessFlags.class, s);
            value = getValue(s);
            return true;
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            return false;
        }

         */
    }

    public void add(int i) {
        value = (value | i);
    }

    public void add(String[] s) throws NoSuchFieldException, IllegalAccessException {
        add(getValue(s));
    }

    public void add(String s) throws NoSuchFieldException, IllegalAccessException {
        add(s.split(","));
    }

    public void remove(int i) {

        value = (value & ~i);

    }

    public String[] toStrings() {
        try {
            //return Flags.getFlags(AccessFlags.class, value);
            return getFlags(value);
        }
        catch (IllegalAccessException e) {
            return new String[0];
        }

    }

    @Override
    public String toString() {
        return String.join(",", toStrings());
        //return Arrays.toString(toStrings());
    }



    boolean isSet(int i) {
        return (value & i) == i;
    }

    boolean involves(int i) {
        return (value & i) != 0;
        //return (actions & a) != 0;
    }

    /*
    interface AccessFlags {
        static final int A = 16;
        static final int B = 32;
        static final int C = 64;
    }
    */

    public static void main(String[] args) {



        // class Access { // extends Flags implements AccessFlags {
        // class Access extends Flags implements AccessFlags {
        class Access extends Flags { // implements AccessFlags {

            static public final int READ  = 1;
            static public final int WRITE = 2;
            static public final int EXEC  = 4;


        }

        Access access2 = new Access();

        Access access = new Access(){
            final public int STREAM = 16;
            final public int SUDO = WRITE|READ;
        };

        //Map<String,Integer> map = ClassUtils.getConstants(access.getClass());
        //Map<String,Integer> map = ClassUtils.getConstants(access.getClass());
        //System.out.println(access.getEntries());
        System.out.println(ClassUtils.getConstants(access.getClass()).entrySet());

        for (String s : args) {

            System.out.println(s);

            try {
                int i = Integer.parseInt(s);
                access.set(i);
                //System.out.println(String.format("%s:: %s", s, Arrays.toString(Flags.getFlags(nuevo, i))));
                //System.out.println(String.format("%s.  %s", s, Arrays.toString(Flags.getFlags(Flags.class, i))));
            } catch (NumberFormatException e) {
                try {
                    access.set(s);
                } catch (NoSuchFieldException | IllegalAccessException e2) {
                    e2.printStackTrace();
                }
                //System.out.println(String.format("%s:: %d", s, Flags.getValue(nuevo, s)));
                //System.out.println(String.format("%s.  %d", s, Flags.getValue(Flags.class, s)));
            }

            System.out.printf("  %d=\t %s%n", access.value, access.toString());

        }
    }
}
