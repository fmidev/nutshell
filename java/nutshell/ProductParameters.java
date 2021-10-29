package nutshell;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;


/** Container for product parameters; esp. time, file format and free parameters.
 *
 *  Product-independent; does not store product ID.
 */
public class ProductParameters { // consider derived classes, like DynamicProductParameters

    /** Data and time formatted as @timeStampFormat or "LATEST".
     */
    public String TIMESTAMP;

    static
    final public DateFormat timeStampFormat = new SimpleDateFormat("yyyyMMddHHmm");

    /** Time of the product in Unix seconds.
     *
     */
    protected long time;

    /// Product-specific parameters
    final TreeMap<String,Object> PARAMETERS = new TreeMap<String, Object>();

    /// Product-specific parameters that are also forwarded to (automated) input product request
    final TreeMap<String,Object> INPUT_PARAMETERS = new TreeMap<String, Object>();

    // unused
    public String postProcessingInfo;


    //final
    public String EXTENSION;

    // final
    public String FORMAT;

    // final
    public String COMPRESSION;


    /** Product paramaters as a map.
     *
     */
    public Map<String,Object> getParamEnv(Map<String,Object> map) {

        if (map == null)
            map = new TreeMap<String, Object>();

        /// STANDARD PARAMETERS (YEAR, MONTH,...)
        Field[] fields = getClass().getFields();
        for (Field field : fields) {
            try {
                String name = field.getName();
                if (name.toUpperCase().equals(name)){
                    map.put(name,field.get(this));
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


}
