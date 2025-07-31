package nutshell10;

import java.io.File;
import java.util.Map;

/** Given a map of Variables, returns a file (path)
 *

 */
interface FileTracker {

    File getFile(Map<String,?> map);

}
