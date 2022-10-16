package nutshell;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class HttpLog extends Log {

    public enum HttpStatus implements Indexed {

        CONTINUE(100),
        SWITCHING_PROTOCOLS(101),
        OK(200),
        CREATED(201),
        ACCEPTED(202),
        NON_AUTHORITATIVE_INFORMATION(203),
        NO_CONTENT(204),
        RESET_CONTENT(205),
        PARTIAL_CONTENT(206),
        MULTIPLE_CHOICES(300),
        MOVED_PERMANENTLY(301),
        MOVED_TEMPORARILY(302),
        FOUND(302),
        SEE_OTHER(303),
        NOT_MODIFIED(304),
        USE_PROXY(305),
        TEMPORARY_REDIRECT(307),
        BAD_REQUEST(400),
        UNAUTHORIZED(401),
        PAYMENT_REQUIRED(402),
        FORBIDDEN(403),
        NOT_FOUND(404),
        METHOD_NOT_ALLOWED(405),
        NOT_ACCEPTABLE(406),
        PROXY_AUTHENTICATION_REQUIRED(407),
        REQUEST_TIMEOUT(408),
        CONFLICT(409),
        GONE(410),
        LENGTH_REQUIRED(411),
        PRECONDITION_FAILED(412),
        REQUEST_ENTITY_TOO_LARGE(413),
        REQUEST_URI_TOO_LONG(414),
        UNSUPPORTED_MEDIA_TYPE(415),
        REQUESTED_RANGE_NOT_SATISFIABLE(416),
        EXPECTATION_FAILED(417),
        INTERNAL_SERVER_ERROR(500),
        NOT_IMPLEMENTED(501),
        BAD_GATEWAY(502),
        SERVICE_UNAVAILABLE(503),
        GATEWAY_TIMEOUT(504),
        HTTP_VERSION_NOT_SUPPORTED(505);

        HttpStatus(int i){
            status = i;
        }

        @Override
        public int getIndex() {
            return status;
        }

        final int status;



    }


    final static Map<Integer, HttpStatus> statusCodes = new HashMap<>();
	static {
        // statusCodes = new HashMap<>();
        for (HttpStatus s : HttpStatus.values()) {
            statusCodes.put(s.status, s);
        }
    }

    public HttpLog(String simpleName) {
        super(simpleName);
        reset();
    }

    /** Create a log with a name prefixed with the name of a existing log.
	 *
	 *  This constructor is handy when creating a log for a child process.
	 *
	 * @param name
	 * @param verbosity -
	 */
	public HttpLog(String name, int verbosity) {
        //super(simpleName, mainLog);
        super(name, verbosity, 5);
        reset();
    }

    public <E> Log log(HttpStatus status, E msg) {

	    String s = "??";
        if (msg != null)
            s = msg.toString();

        if (handleHttpMsg(status.status, s)) {
            if (status.status > this.indexedState.index)
                this.indexedState = new IndexedState(status, s);
        } else {
            super.error("Something went wrong with: " + status + ", msg:" + s);
        }

	    return this;
    }

        //@Override
    /**
     *   Store also an exception, if status index exceeds current index.
     *
     */
    public <E> Log log(int status, E msg) {

        String s = "???";
        if (msg != null)
            s = msg.toString();

        if (handleHttpMsg(status, s)) {
            if (status > this.indexedState.index)
                this.indexedState = new IndexedState(status, s);
        } else {
            handleStandardMsg(status, s);
        }

        return this;

    }

    //static final IndexedException defaultException = new IndexedException(HttpServletResponse.SC_CONTINUE, "Ok");
    static final IndexedState defaultException = new IndexedState(HttpStatus.CONTINUE, "Ok");

    void reset() {
        this.indexedState = defaultException;
    }

    /**
     * Set status according to predefined {@link IndexedState}
     *
     * @param e
     */
    void log(IndexedState e) {

        // primarily, handle as HTTP exception
        if (handleHttpMsg(e.index, e.getMessage())) {
            if (e.index > this.indexedState.index)
                this.indexedState = e;
        }
        else {
            handleStandardMsg(e.index, e.getMessage());
        }

    }


    // static final public Map<String,Object> codes; // = ClassUtils.getConstants(HttpServletResponse.class);



    protected boolean handleStandardMsg(int i, String msg){

        if ((i >= 0) && (i <= 10)){
            /// "Standard" LOG levels
            super.log(super.statusCodes.get(i), msg);
            //super.log(i, msg);
            return true;
        }
        else {
            // String m =
            super.warn(String.format("Illegal LOG level: %d", i));  // Or: return false?
            // super.log(i, msg);
            return false;
        }

    }

    /// index is HTTP code (100...5xx) so handle it.
    /// TODO: add SortedMap of exceptions?
    //SortedMap m = new TreeMap();
    public IndexedState indexedState = new IndexedState(HttpStatus.CONTINUE, "Ok");

    protected boolean handleHttpMsg(int i, String msg){

        //if ((i >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) && (i < 600)){ // WHY 600?
        if (i >= 500){ // HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            this.fatal(msg);
        }
        else if (i >= 400){ // HttpServletResponse.SC_BAD_REQUEST
            this.error(msg);
            //this.warn(msg);
        }
        else if (i >= 300){ // HttpServletResponse.SC_MULTIPLE_CHOICES
            this.note(msg);
        }
        else if (i >= 100){
            this.debug(msg);
        }
        else {
            return false;
        }

        return true;
    }




    protected <E> Log flush2(Status status, E message){

        /// Non-HTML
        /*
        if (!this.decoration.involves(OutputFormat.HTML)){
            return super.flush(status, message);
        }

        /// HTML

        // Each line is a PRE element.
        buffer.append("<pre>");

        buffer.append("[").append(numberFormat.format(System.currentTimeMillis() - startTime)).append("] ");

        //buffer.append(String.format("%7s", statusCodes.get(this.status)));
        if (this.decoration.involves(OutputFormat.COLOUR)){
            //buffer.append(String.format("<span style=\"background-color: %s\" >", status.colour.name()));
        }
        buffer.append(String.format("<span class=\"%s\" >", status.name().toLowerCase())); // TODO: levelName (WARN)


        buffer.append(String.format("%7s", status));
        buffer.append(':').append(' ').append(name);

        //String s = message.toString();
        //buffer.append(SimpleHtml.Tag.B.start());
        if (message != null) {
            if (!this.decoration.involves(OutputFormat.MAP_URLS)) {
                buffer.append(' ').append(message);
            } else {
                PathDetector pd = null;
                try {
                    buffer.append(' ');

                    pd = new PathDetector(message.toString());
                    while (pd.next()) {

                        buffer.append(pd.prefix);

                        String result = pd.path.toString(); // default
                        for (Map.Entry<Path, String> entry : pathMap.entrySet()) {
                            Path p = entry.getKey();
                            if (pd.path.startsWith(p)) {
                                Path relative = p.relativize(pd.path);
                                //result = String.format("<a href=\"%s\">%s</a>", relative, pd.filename); //SimpleHtml.Tag.H3.start());
                                result = entry.getValue() + relative.toString();
                                break;
                            }
                        }

                        buffer.append(result);

                    }
                    buffer.append(pd.remainingLine);  // = trailing part of the line
                } catch (Exception e) {
                    System.err.println(pd);
                    e.printStackTrace(getPrintStream());
                    //System.err.print(e.getMessage());
                }
            }


        }
        // TODO: use url mapping!

        if (this.decoration.involves(OutputFormat.COLOUR)){
            buffer.append("</span>");
        }

        buffer.append("</pre>\n");

        flushBuffer();
        */
        return this;

    }

    static
    public void main(String[] args) {
        System.err.println("No demo for this class");
    }

}
