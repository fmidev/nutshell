package nutshell;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


/** Container for product parameters; esp. time, file format and free parameters.
 *
 *  Product-independent; does not store product ID.
 */
public class ProductParameters { // consider derived classes, like DynamicProductParameters

    public enum TimeResolution {
        INVALID(-1),
        UNKNOWN(0),
        YEAR("yyyy", "yyyy"),
        MONTH("yyyyMM", "yyyy"),
        DAY("yyyyMMdd", "yyyy/MM"),
        HOUR("yyyyMMddHH", "yyyy/MM/dd"),
        MINUTE("yyyyMMddHHmm", "yyyy/MM/dd");

        final int length;
        //String format;
        final DateFormat timeStampFormat;
        final DateFormat timeDirFormat;

        TimeResolution(int length){
            this.length = length;
            timeStampFormat = new SimpleDateFormat("");
            timeDirFormat   = new SimpleDateFormat("");
            /*
            if (length > 0) {
                timeStampFormat = new SimpleDateFormat("yyyyMMddHHmm".substring(0, length));
                timeDirFormat   = new SimpleDateFormat("");
            }
            else {
                timeStampFormat = new SimpleDateFormat("");
                timeDirFormat   = new SimpleDateFormat("");
            }
            */
        }

        TimeResolution(String tsFmt, String dirFmt){
            length = tsFmt.length();
            timeStampFormat = new SimpleDateFormat(tsFmt);
            timeDirFormat = new SimpleDateFormat(dirFmt);
        }

            //protected
        //static String fullFormat = "YYYmmddHHmm";

    }
    /** Data and time formatted as @timeStampFormat or "LATEST".
     */
    public String TIMESTAMP;

    /** Optional. Secondary time, if applicable.
     */
    public String TIMESTAMP2;

    /**
     *   Note: in future versions, this may change. Products may have time resolution of days or seconds.
     */
    // static final public DateFormat timeStampFormat = new SimpleDateFormat("yyyyMMddHHmm");

    /** Time of the product in Unix seconds.
     *
     */
    protected long time;

    /** Secondary time (like end time) of the product in Unix seconds.
     *
     */
    protected long time2;

    /// Product-specific parameters
    /**
     *  In the future, this may be multiple (array of parameter maps).
     *
     * @see #INPUT_PARAMETERS
     */
    final TreeMap<String,Object> PARAMETERS = new TreeMap<String, Object>();

    /// Product-specific parameters that are also forwarded to (automated) input product request
    final TreeMap<String,Object> INPUT_PARAMETERS = new TreeMap<String, Object>();

    // unused
    // public String postProcessingInfo;


    //final
    public String EXTENSION;

    // final
    public String FORMAT;

    // final
    public String COMPRESSION;

    static
    protected TimeResolution getTimeResolution(String timestamp) throws ParseException {
        if (timestamp.isEmpty() || timestamp.equals("LATEST") || timestamp.equals("TIMESTAMP")) {
            return TimeResolution.UNKNOWN;
        }
        else {
            for (TimeResolution t : TimeResolution.values()) {
                if (timestamp.length() == t.length) {
                    // System.err.println(String.format("TimeResolution t=%s %s", t, t.timeStampFormat.toString()));
                    //return t.timeStampFormat.parse(timestamp).getTime();
                    return t;
                }
            }
            //final DateFormat timeStampFormat = new SimpleDateFormat("YYYYmmddHHMM");
            // return timeStampFormat.parse(timestamp).getTime();
        }
        return TimeResolution.INVALID; // error!
    }

    static
    protected long getTime(String timestamp, TimeResolution timeResolution) throws ParseException {

        //TimeResolution timeResolution = getTimeResolution(timestamp);
        switch (timeResolution){
            case INVALID: // consider hiding ParseException here?
                return -1L;
            case UNKNOWN:
                return 0L;
            default:
                return timeResolution.timeStampFormat.parse(timestamp).getTime();
        }

    }

    static
    protected long getTime(String timestamp) throws ParseException {
           return getTime(timestamp, getTimeResolution(timestamp));
    }

        /** Product parameters as a map.
         *
         */
    public Map<String,Object> getParamEnv(Map<String,Object> map) {

        if (map == null)
            map = new TreeMap<>();

        /// STANDARD PARAMETERS (YEAR, MONTH,...)
        Field[] fields = getClass().getFields();
        for (Field field : fields) {
            try {
                String key = field.getName();
                if (key.toUpperCase().equals(key)){
                    map.put(key,field.get(this));
                }
            } catch (Exception e) {
                //System.err.println("Koe " + e.getMessage());
            }
        }

        /// Product specific parameters
        // TODO: replace with param groups?
        map.putAll(PARAMETERS);
        map.putAll(INPUT_PARAMETERS);

        StringBuffer sb = new StringBuffer();
		for (Map.Entry<?,?> s : INPUT_PARAMETERS.entrySet()) {
		    sb.append(s.toString()).append('_');
		}
        for (Map.Entry<?,?> s : PARAMETERS.entrySet()) {
            sb.append(s.toString()).append('_');
        }
        map.put("PARAMETERS", sb.toString());

        return map;
    }

    public static void main(String[] args) {

        if (args.length == 0){
            System.err.println("Usage: \n 20130817 [...]");
        }
        else {
            final Date date = new Date();
            for (String arg: args) {
                try {
                    TimeResolution timeResolution = getTimeResolution(arg);
                    System.out.println(String.format("Time resolution: %s", timeResolution));
                    long time = getTime(arg, timeResolution);
                    date.setTime(time);
                }
                catch (Exception e){

                }
                System.err.println(date);
                for (TimeResolution t : TimeResolution.values()) {
                    System.out.println(String.format("%s:\t %s", t, t.timeStampFormat.format(date)));
                }

            }
        }

    }

}
