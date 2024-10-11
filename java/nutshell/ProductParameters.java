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

    /** The time resolution of a product (file) can be high, for example 5 minutes. Some products,
     *  collecting statistics for example, can be generated once a month only.
     *  Thus, time resolution of the product is important metadata to be stored. It is needed when the filename is
     *  generated from the actual time.
     *
     *  When creating time-stamped directories – like cache and storage directories – the time resolution should
     *  be lower than that of the products.
     *
     */
    public enum TimeResolution {
        // INVALID(0), //-1),
        //UNKNOWN(0),
        UNKNOWN(),
        YEAR("yyyy", "yyyy"),
        MONTH("yyyyMM", "yyyy"),
        DAY("yyyyMMdd", "yyyy/MM"),
        HOUR("yyyyMMddHH", "yyyy/MM/dd"),
        MINUTE("yyyyMMddHHmm", "yyyy/MM/dd");

        final int length;
        //String format;
        final DateFormat timeStampFormat;
        final DateFormat timeDirFormat;

        /*
        TimeResolution(int length){
            this.length = length;
            timeStampFormat = new SimpleDateFormat("");
            timeDirFormat   = new SimpleDateFormat("");
        }
        */

        TimeResolution(){
            this("","");
        }

        TimeResolution(String tsFmt, String dirFmt){
            length = tsFmt.length();
            timeStampFormat = new SimpleDateFormat(tsFmt);
            timeDirFormat = new SimpleDateFormat(dirFmt);
        }

        @Override
        public String toString() {
            return String.format("%s[%d] %s %s", this.getClass().getSimpleName(), length, timeDirFormat + " | " + timeStampFormat);
        }
    }

    /** Data and time formatted as @timeStampFormat or "LATEST".
     */
    public String TIMESTAMP;

    /** Optional. Secondary time, if applicable.
     *
     * TODO: generalize: support multiple timestamps – as far as given as prefix
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
     *  TODO: generalize: support multiple timestamps – as far as given as prefix
     */
    protected long time2;

    /** Experimental. Groups may be needed in future.
     *
     *  Consider: 201708121600_radar.rack.comp.map_CONF=FIN__COLORS=TEST.png
     *
     */
    protected class ParameterContainer extends ArrayList<TreeMap<String,Object>> {

        public TreeMap<String,Object> append(){
            TreeMap<String,Object> m = new TreeMap<>();
            super.add(m);
            return m;
        }
    }

    final protected ParameterContainer PARAMETER_CONTAINER = new ParameterContainer();

    /// Product-specific parameters
    /**
     *  In the future, this may be multiple (array of parameter maps).
     *
     * @see #INPUT_PARAMETERS
     */
    final TreeMap<String,Object> PARAMETERS = PARAMETER_CONTAINER.append();

    /// Product-specific parameters that are also forwarded to (automated) input product request
    final TreeMap<String,Object> INPUT_PARAMETERS = PARAMETER_CONTAINER.append();

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
        throw new ParseException(String.format("Could not parse timestamp '%s'", timestamp), 0);
        //return TimeResolution.UNKNOWN; // error!
        //return TimeResolution.INVALID; // error!
    }

    /**
     *
     * NOTE: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/text/SimpleDateFormat.html
     * Date formats are not synchronized. It is recommended to create separate format instances for each thread.
     * If multiple threads access a format concurrently, it must be synchronized externally.
     *
     * @param timestamp
     * @param timeResolution
     * @return
     * @throws ParseException
     */
    static
    protected long getTime(String timestamp, TimeResolution timeResolution) throws ParseException {

        //TimeResolution timeResolution = getTimeResolution(timestamp);
        switch (timeResolution){
            // case INVALID: // consider hiding ParseException here?
            //     return -1L;
            case UNKNOWN:
                return 0L;
            default: {
                synchronized (timeResolution.timeStampFormat) { // 2024/10
                    return timeResolution.timeStampFormat.parse(timestamp).getTime();
                }
            }
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
