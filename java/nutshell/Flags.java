package nutshell;


import java.lang.reflect.Field;
import java.util.*;


/** Utility for setting flags - labelled bit values.
 *
 *  For example, in a file system flags could be used for defining permissions:
 *
 *  * READ  = 1
 *  * WRITE = 2
 *  * EXEC  = 4
 *  * ALL   = READ | WRITE | EXEC = 7
 *
 *  @Flags supports setting and resetting bits using both labels ("READ", "EXEC", "ALL", ... )
 *  and numeric values (1, 4, 7...)
 *
 *
 *
 *
 *  The labels and bit values can be taken from either
 *
 *  # Enum classes
 *  # Subclasses extending @Flags
 *  # External classes
 *
 *  Enum classes:
 *  # the names of the constants serve as labels
 *  # numeric values indicate the bits, ie. bitValue = (1 << ordinal()).
 *  Consequently, the resulting flags are unique: their values start from 1, and are non-overlapping
 *
 *  Other classes:
 *  # the names of the static public integer constants serve as labels
 *  # numeric values are directly used as bit values; values can be equal or contain overlapping bits
 *
 */
public class Flags {

    public Flags(){
        domain = null;
        set(0);
    }

    public Flags(int bitMask){
        domain = Flags.class;
        setAllowedBits(bitMask);
        set(0);
    }
    /** Sets
     *
     * @param
     */
    public Flags(Class<?> domain){
        setDomain(domain);
        set(0);
    }

    public int value = 0;


    public boolean contains(String label){
        //java.lang.reflect.Modifier.isStatic(field.getModifiers()
        try {
            Field field = domain.getField(label);
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())){
                return isIncluded(field);
            }
        } catch (NoSuchFieldException e) {
        }
        return false;
    };

    static
    private int getValue(Object obj, String flags) throws NoSuchFieldException, IllegalAccessException{
        return getValue(obj, flags.split(","));
    }

    public int getValue(String flags) throws NoSuchFieldException, IllegalAccessException{
        return getValue(getDomain(), flags.split(","));
    }

    /**
     *  Supports mixed integers and string labels
     *
     * @param obj
     * @param flags
     * @return
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    static
    private int getValue(Object obj, String[] flags) throws NoSuchFieldException, IllegalAccessException {

        int result = 0;

        Class c = (obj instanceof Class) ? (Class)obj : obj.getClass();

        for (String s : flags) {
            // If s contains comma-separated values.
            String[] subFlags = s.split(",");
            if (subFlags.length > 1)
                 result = result | getValue(obj, subFlags);
            else {
                try {
                    // Flexible: supports mixed intergers and string labels
                    int i = Integer.parseInt(s);
                    result = result | i;
                }
                catch (NumberFormatException e){
                    result |= getStaticFieldValue(c.getField(s));
                    //result = result | c.getField(s).getInt(obj);
                }
            }
        }

        return result;
    }

    public int getValue(String[] flags) throws NoSuchFieldException, IllegalAccessException {
        return getValue(this, flags);
    }


    /** A reference class, the static integer members of which will be used as (label,value) pairs.
     *
     *  The set of permissible values consists of
     *
     */
    private Class<?> domain;

    /**  Return the referemce class - the static integer members of which will be used as (label,value) pairs.
     *
     *
     *  @param domain
     */
    public void setDomain(Class<?> domain){
        this.domain = domain;
        setAllowedBits(domain);
        // System.out.printf("Set domain: %s <-%s", getClass().getSimpleName(), domain.getSimpleName());
    };

    /** Return the referemce class
     *
     *
     */
    public Class<?> getDomain(){
        if (domain == null) {
            setDomain(getClass());
            //return getClass();
        }
        return domain;
    };



    /**  The allowed bit values.
     *
     *   A quick filter that accepts and rejects bit values of flags.
     *
     */
    protected int bitMask = ~0;

    /** A quick filter that accepts and rejects bit values of flags.
     *
     */
    public void setAllowedBits(int bitMask){
        this.bitMask = bitMask;
    }


    /** Get current flags (the ones that are set).
     *
     * @param value
     * @return
     * @throws IllegalAccessException
     */
    @Deprecated
    public String[] getFlags(int value) throws IllegalAccessException{
        return getFlags(getDomain(), value);
    }


    /**
     *
     *  Demonstration
     *
     * @param
     */
    static
    private String[] getFlags(Class c, int value) throws IllegalAccessException{

        Set<String> result = new HashSet<>();

        // Class c = (obj instanceof  Class) ? (Class)obj : obj.getClass();

        for (Field field : c.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                if (isIncluded(field)) {
                    int i = getStaticFieldValue(field);
                    if ((value & i) == i) { // i fully covered
                        result.add(field.getName());
                    }
                }
            }
        }

        return result.toArray(new String[0]);
    }

    /*
    static public String[] getFlags(Object obj, Enum<?> value) throws IllegalAccessException{
        return getFlags(obj, 1 << value.ordinal());
        return new String[] {value.name()};
    }
     */


    public Map<String,Integer> getAllFlags() {

        Map<String,Integer> m = new HashMap<>();

        for (Field field : getDomain().getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                if (isIncluded(field)) {
                    int i = getStaticFieldValue(field);
                    m.put(field.getName(), i);
                }
            }
        }
        return m;

        // return ClassUtils.getConstants(getDomain());
    }


    /** Check is a field is accepted as a (label,value) pair.
     *
     *  A filed is always checked to be constant and public.
     *
     * @param field
     * @return
     */
    static
    public boolean isIncluded(Field field){
        String name = field.getName();
        return name.equals(name.toUpperCase());
    };

    static
    protected int getStaticFieldValue(Field field){
        Class c = field.getDeclaringClass();
        String name = field.getName();
        if (c.isEnum()){
            return (1 << Enum.valueOf(c, name).ordinal());
        }
        else {
            try {
                return field.getInt(null);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return 0;
    };

    /** Sets bit mask for accepting valid values.
     *
     * @param c
     */
    public void setAllowedBits(Class c){
        int m = 0;
        for (Field field : c.getFields()) {
            if (isIncluded(field)) {
                m |= getStaticFieldValue(field);
            }
        }

        this.bitMask = m;
    }

    //static
    //public final int ALL_FIELDS = ~0;



    /// Further NON-STATICS

    /*
    public void validateStrict(int i) throws NoSuchFieldException{
        int rejected = validate(i);
        if (rejected > 0) {
            throw new NoSuchFieldException(String.format("Illegal bits in %d : %d (mask=%d)", i, (i & rejected), bitMask));
        }
    }
    */

    public int validate(int i) {
        return (i & ~bitMask);
    }

    public boolean check(int i) {
       return (validate(i)>0);
    }

    /**
     * @see #add(int) .
     * @param i
     */
    public void set(int i){
        value = i;
    }

    /**
     *  Assumes a valid (compatible) Enum class is used.
     *
     *  @param enums
     */
    public void set(Enum<?>... enums){
        value = 0;
        for (Enum e: enums) {
            add(1<<e.ordinal());
        }
    }

    /** Copy bits from another Flags object.
     *
     * @param flags
     */
    public void set(Flags flags){
        set(flags.value);
    }

    /**
     *
     * @param s
     * @param scope – Object or Enum class the static members of which are considered.
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    public void set(String[] s, Class<?> scope) throws NoSuchFieldException, IllegalAccessException {
        set(getValue(scope, s));
    }

    /**
     *
     * @param s
     * @param scope – Object or Enum class the static members of which are considered.
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private void set(String s, Class<?> scope) throws NoSuchFieldException, IllegalAccessException {
        set(getValue(scope, s));
    }


    public void set(String[] s) throws NoSuchFieldException, IllegalAccessException {
        set(getValue(s));
    }

    public void set(String s) throws NoSuchFieldException, IllegalAccessException {
        set(s.split(","), getDomain());
    }

    public void add(int i) {
        value |= (i & bitMask);
    }

    public void add(Enum<?> e) {
        add(1<<e.ordinal());
    }

    public void add(String[] s) throws NoSuchFieldException, IllegalAccessException {
        add(getValue(getDomain(), s));
    }

    public void add(String s) throws NoSuchFieldException, IllegalAccessException {
        // add(s.split(","));
        add(getValue(getDomain(), s));
    }

    public void remove(int i) {
        value = (value & ~i);
    }

    /*
    public String[] toStrings() {
        try {
            //return Flags.getFlags(AccessFlags.class, value);
            return getFlags(value);
        }
        catch (IllegalAccessException e) {
            return new String[0];
        }
    }
     */

    @Override
    public String toString() {
        try {
            //return Flags.getFlags(AccessFlags.class, value);
            return String.join(",", getFlags(value));
        }
        catch (IllegalAccessException e) {
            return "";
        }

        // return new String[0];
        // return String.join(",", toStrings());
        // return Arrays.toString(toStrings());
    }



    boolean isSet(int i) {
        return (value & i) == i;
    }

    boolean isSet(Enum e) {
        return isSet(1 << e.ordinal());
    }


    boolean involves(int i) {
        return (value & i) != 0;
    }

    boolean involves(Enum e) {
        return involves(1 << e.ordinal());
    }



    protected enum ExampleEnum {
        READ,
        WRITE,
        EXEC,
        SPECIAL;

        final int bit;
        ExampleEnum(){
            bit = 1 << ordinal();
        }

    }

    public static void main(String[] args) {

        if (args.length == 0){
            System.out.printf("Usage: %n java %s <flags>  # flags = %s %n",
                    Flags.class.getCanonicalName(), Arrays.toString(ExampleEnum.values()));
            System.out.printf("Example: %n java %s READ WRITE READ,EXEC 7 1 0 %n",
                    Flags.class.getCanonicalName());
        }


        Map<String,Flags> map = new HashMap<>();

        map.put("Plain (unlabelled) Flags class, 8 bits", new Flags(0xff));
        map.put("Flags class + Enum", new Flags(ExampleEnum.class));

        class ExampleClass extends Flags {
            static public final int READ  = 1;
            static public final int WRITE = 2;
            static public final int EXEC  = 4;
        }
        map.put("Inherited Class", new ExampleClass());


        for (Map.Entry<String,Flags> entry: map.entrySet()){

            Flags flags = entry.getValue();
            flags.getDomain(); // DEBUG

            System.out.println();
            System.out.printf("EXAMPLE: %s: ", entry.getKey());
            //System.out.printf("EXAMPLE: ");
            if (flags.getClass() == flags.getDomain()){
                System.out.printf("%s", flags.getClass().getSimpleName());
            }
            else {
                System.out.printf("%s[%s]", flags.getClass().getSimpleName(),flags.getDomain().getSimpleName());
            }
            System.out.printf("  %s", flags.getAllFlags());
            // System.out.println(flags.getAllFlags().entrySet());
            System.out.printf("  mask: %d %n", flags.bitMask);

            for (String s : args) {

                System.out.println();
                System.out.printf("Arg: '%s' %n", s);

                try {
                    int i = Integer.parseInt(s);
                    int rejectedBits = flags.validate(i);
                    if (rejectedBits != 0) {
                        System.out.printf("Reject %d (mask=%d)%n", rejectedBits, flags.bitMask);
                    }
                    else {  // Alternatively: valid bits could be set (application dependent choice)
                        flags.set(i);
                    }
                }
                catch (NumberFormatException e) {
                    try {
                        flags.set(s, flags.getDomain());
                    } catch (NoSuchFieldException | IllegalAccessException e2) {
                        e2.printStackTrace();
                    }
                }

                System.out.printf(" Value: %d == (%s)%n", flags.value, flags.toString());


            }

        }


    }
}
