package nutshell;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/** Container for product parameters; esp. time, file format and free parameters.
 *
 *  Product-independent; does not store product ID.
 */
public class ProductParameters { // consider derived classes, like DynamicProductParameters

    public String TIMESTAMP;

    final public DateFormat timeStampFormat = new SimpleDateFormat("yyyyMMddHHmm");

    long time;

    /// Product-specific parameters
    final Map<String,Object> PARAMETERS = new HashMap<String, Object>();

    /// Product-specific parameters that are also forwarded to (automated) input product request
    final Map<String,Object> INPUT_PARAMETERS = new HashMap<String, Object>();

    // unused
    public String postProcessingInfo;


    //final
    public String EXTENSION;

    // final
    public String FORMAT;

    // final
    public String COMPRESSION;

    // final public String PRODUCT_DIR;
    // public String INPUTKEYS = "";

    /// Returns lines of type:  KEY='VALUE';
    public Map<String,Object> getParamEnv(Map<String,Object> map) {

        if (map == null)
            map = new HashMap<String,Object>();

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

        /// PRODUCT_ID-SPECIFIC PARAMETERS
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
