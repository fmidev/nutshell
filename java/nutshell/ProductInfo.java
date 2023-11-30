package nutshell;


import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// For future extension (threads): mark busy.
//  setStatus(HttpServletResponse.SC_SEE_OTHER, "Parsing ProductInfo");
		

/**  Formal description of a product to be retrieved.

	PRODUCT_ID = ''
    PARAMETERS = None #{}
    INPUT_PARAMETERS = None
    #PARAMS = None #[]
    FORMAT = ''
    COMPRESSION = ''
    EXTENSION = ''

	The members in capital letters will be passed to generators,
 	for example to ExternalGenerator, as environment variables.
 */
class ProductInfo extends ProductParameters {

	/// Identifier of this "product line", not a specific instance.
	final public String PRODUCT_ID; // TODO consider class / interface, also for Generator's

	/// Comma separated list of the keys (variable names) of the input paths.
	public String INPUTKEYS;

	/// Common prefix for all the successfully retrieved inputs.
	public String INPUT_PREFIX;

	/// Directory part of #INPUT_PREFIX .
	public String INPUT_PREFIX_DIR;

	/// Compression is a "special" filename segment, it is extracted first.
	protected static final Pattern compressionRe = Pattern.compile("^(.*)\\.(zip|gz)$");

	/// Resolve TIMESTAMP, PRODUCT_ID, PARAMETERS
	/**
	 *   1
	 */
	protected static final Pattern filenameRe =
			//Pattern.compile("^((LATEST|TIMESTAMP|[0-9]*)_)?([^_]+)(_(.*))?\\.([a-z][a-z0-9]*)$");
			// GOOD
			Pattern.compile("^((LATEST|TIMESTAMP|[0-9]*)_((LATEST|TIMESTAMP|[0-9]*)_)?)?([^_]+)(_(.*))?\\.([a-z][a-z0-9]*)$");
			// BASENAMED Pattern.compile("^(((LATEST|TIMESTAMP|[0-9]*)_((LATEST|TIMESTAMP|[0-9]*)_)?)?([^_]+)(_(.*))?)\\.([a-z][a-z0-9]*)$");
			// Python: re.compile(r"^((LATEST|TIMESTAMP|[0-9]*)_)?([^_]+)(_(.*))?\.([a-z][a-z0-9]*)$")


	/** Additional parameters forwarded directly to product generator.
	 *
	 */
	public final Map<String,String> directives = new HashMap<>();

	/**
	 *
	 * @param s
	 * @param separator  - typically "\\|" or
	 * @return
	 */
	public Map<String,String> setDirectives(String s, String separator){
		MapUtils.setEntries(s, separator, "true", directives);
		return directives;
	}

	public Path getTimeStampDir(){
		try {
			TimeResolution tr = getTimeResolution(TIMESTAMP);
			return Paths.get(tr.timeDirFormat.format(time));
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}


	/** Imports map to directives map, converting array values to comma-separated strings.
	 *
	 * @param map
	 */
	public <T> Map<String,String> setDirectives(Map<String,T> map){

		if (map != null) {
			for (Map.Entry<String, T> entry : map.entrySet()) { //parameters.entrySet()) {
				String key = entry.getKey();
				if (key.equals(key.toUpperCase())) {
					Object value = entry.getValue();
					if (value != null){
						if (value instanceof String[]) {
							String[] values = (String[])value;
							directives.put(key, String.join(",", values));
						}
						else {
							directives.put(key, value.toString());
						}

					}
					else {
						directives.put(key, "True");
					}


				}
			}
		}

		return directives;
	}



	public ProductInfo(String productStr) throws ParseException { //,Log log){

		String[] s = productStr.split("\\?", 2);
		String filename = s[0];
		//String filenameUncompressed = filename;
		if (s.length == 2) {
			setDirectives(s[1], "[&\\?]");
		}
		//postProcessingInfo = (s.length==2) ? s[1] : "";

		/// Parse compression extension, like .gz in .txt.gz)
		final Matcher cm = compressionRe.matcher(filename);
		if (cm.matches()) {
			filename = cm.group(1); // filename uncompressed
			COMPRESSION = cm.group(2);
		} else {
			COMPRESSION = "";
		}


		/// Main parsing
		final Matcher m = filenameRe.matcher(filename);
		if (m.matches()) {

			//BASENAME = m.group(1);

			TIMESTAMP = m.group(2) == null ? "" : m.group(2);

			TIMESTAMP2 = m.group(4) == null ? "" : m.group(4);

			time  = getTime(TIMESTAMP);
			time2 = getTime(TIMESTAMP2);


			/*
			if (TIMESTAMP.isEmpty() || TIMESTAMP.equals("LATEST") || TIMESTAMP.equals("TIMESTAMP")) {
				time = 0L;
			} else {
				time = timeStampFormat.parse(TIMESTAMP).getTime();
			}

			for (TimeResolution t: TimeResolution.values()){
				if (TIMESTAMP.length() == t.length){
					t.timeStampFormat.parse(TIMESTAMP).getTime();
				}
			}
			 */


			//PRODUCT_ID = m.group(3).replace('-', '.');
			PRODUCT_ID = m.group(5).replace('-', '.');
			Map<String, Object> paramLink = INPUT_PARAMETERS;
			//String param = m.group(5);
			String param = m.group(7);
			// int index=0; // ordered params?

			if (param != null){
			String p[] = param.split("_");
				for (int i = 0; i < p.length; i++) {
					String entry[] = p[i].split("=", 2);
					if (entry.length == 2) /// Specific parameters
						paramLink.put(entry[0], entry[1]);
					else if (entry[0].isEmpty()) {
						paramLink = PARAMETERS;
						//System.out.println(" entry=" + p[0]);
					} else {
						/// Ordered parameters
						paramLink.put("P" + i, entry[0]);
					}
				}
			}

			FORMAT = m.group(8);
			if (COMPRESSION.isEmpty())
				EXTENSION = FORMAT;
			else
				EXTENSION = FORMAT + '.' + COMPRESSION;
		}
		else {
			throw new ParseException("Parsing failed for '" + productStr + "'", 0);
		}

		
	}
	
	/*
	def set_product(self, product=None, filename=None, product_id=None, **kwargs):
	    def set_id(self, product_id):
	    def set_timestamp(self, timestamp):
	    def set_format(self, extension):
	    def set_parameter(self, key, value=''):
	    def set_parameters(self, params):
	    def  _parse_filename(self, filename):
	    def get_filename(self):
	    def get_static_filename(self):
	    def get_filename_latest(self):
	    def get_param_env(self):
	*/
	    	
	//@Override
	public String getID(){
		return PRODUCT_ID;
	}

	@Override
	public String toString() {
		return getFilename();
	}

	boolean isDynamic() {
		return (TIMESTAMP!=null) && !TIMESTAMP.isEmpty();
	}

	/** Note: Here TIMESTAMP2 not supported yet
	 *
	 * @return
	 */
	public String getFilename() {
		return getFilename(TIMESTAMP);
	}

	/** Note: Here TIMESTAMP2 not supported yet
	 *
	 * @return
	 */
	public String getBasename(String prefix, StringBuffer b) {

		//b.append("Time: ").append("<not implemented>").append('\n');
		if ((prefix != null) && !prefix.isEmpty())
			b.append(prefix).append("_");

		b.append(this.PRODUCT_ID);

		//for (Map.Entry<String, Object> entry : toMap(null).entrySet()) {
		for (Map.Entry<String, Object> entry : INPUT_PARAMETERS.entrySet()) {
			b.append("_").append(entry.toString()); // .getKey()).append('=').append(entry.getValue()).append('\n');
		}
		if (!PARAMETERS.isEmpty()) {
			b.append("_");
		}
		for (Map.Entry<String, Object> entry : PARAMETERS.entrySet()) {
			b.append("_").append(entry.toString()); // .getKey()).append('=').append(entry.getValue()).append('\n');
		}
		//b.append(".").append(this.EXTENSION);

		return b.toString();
	}

	public String getBasename() {
		StringBuffer b = new StringBuffer();
		getBasename(TIMESTAMP, b);
		return b.toString();
	}

	public String getFilename(String prefix) {
		StringBuffer b = new StringBuffer();
		getBasename(prefix, b);
		b.append(".").append(this.EXTENSION);
		return b.toString();
	}



	/** Test
         */
	public static void main(String[] args) {

		if (args.length == 0){

			final String cmd = String.format("java %s", ProductInfo.class.getCanonicalName()) + " %s";

			System.out.println("Usage:");
			System.out.println(String.format(cmd, "<product_filename>"));
			System.out.println(String.format(cmd, "<product_filename>?<directives> "));

			System.out.println("Examples:");
			System.out.println(String.format(cmd, "201012161615_misc.ppmforge_DIMENSION=2.5_ANGLE=45.ppm.gz"));
			System.out.println(String.format(cmd, "201012161615_misc.ppmforge_DIMENSION=2.5_ANGLE=45.ppm.gz?Threshold=0.1"));
			System.out.println(String.format(cmd, "201012161615_misc-ppmforge_DIMENSION=2.5_ANGLE=45.ppm.gz  # New!"));
			return;
		}

		ProductInfo info;
		
		try {
			info = new ProductInfo(args[0]);
			System.out.println(info);
			System.out.println(info.directives);
			System.out.println(info.getParamEnv(null));
		}
		catch (ParseException e) {
			e.printStackTrace();
		}
		
	}


};

