package nutshell;


import java.lang.reflect.Field;
import java.util.*;


/**
 *
 */
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
    enum Liput {
        MIKA,
        MÄKI;

        int bit(){
            return 1 << ordinal();
        }
    }

    protected static int counter;

    static {
        counter = 0;
    }

    public static int getBit(){
      return 1 << ++counter;
    };


    public static void main(String[] args) {

        //class Lipat extends Liput {
        //}

        Liput liput1 = Liput.MIKA;
        Liput liput2 = Liput.MÄKI;
        System.out.println(Liput.MIKA.bit() | Liput.MÄKI.bit());
        // System.out.println(Liput.MIKA.ordinal());
        // System.out.println(Liput.MÄKI.ordinal());
        System.out.println(String.format(" %d ", Liput.MÄKI.ordinal()));
        System.out.println(String.format(" %s ", Liput.MÄKI));
        //Liput.MIKA.ordinal();

        // class Access { // extends Flags implements AccessFlags {
        // class Access extends Flags implements AccessFlags {
        class Access extends Flags { // implements AccessFlags {
            public final int READ  = Flags.getBit();
            public final int WRITE = Flags.getBit();
            public final int EXEC  = Flags.getBit();
        }

        Access access2 = new Access();

        Access access = new Access(){
            final public int STREAM = Flags.getBit();
            final public int SUDO = WRITE|READ;
        };

        System.out.println(ClassUtils.getConstants(access.getClass()).entrySet());

        for (String s : args) {

            System.out.println(s);

            try {
                int i = Integer.parseInt(s);
                access.set(i);
            } catch (NumberFormatException e) {
                try {
                    access.set(s);
                } catch (NoSuchFieldException | IllegalAccessException e2) {
                    e2.printStackTrace();
                }
            }

            System.out.printf("  %d=\t %s%n", access.value, access.toString());

        }
    }
}
