package nutshell10;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.util.concurrent.TimeoutException;

public class ShellUtils {

	/*
	static public Process run(String[] cmdArray, String[] env, File directory) throws IOException, InterruptedException{

		final Process child = Runtime.getRuntime().exec(cmdArray, env, directory);
		// SP: Lisäsin waitFor-kutsun. Nyt ei ainakaan varmasti aloita uutta säiettä/prosessia.
		// child.waitFor(); // NO! Sync'd read will pend! 				
		return child;
	}

	static public Process run(String cmdLine, String[] env, File directory) throws IOException, InterruptedException{
		return ShellUtils.run(cmdLine.split(" "), env, directory);
	}

	static public Process run(String cmd, String[] args, String[] env, File directory) throws IOException, InterruptedException{
		
		if (args == null)
			args = new  String[0];
		
		String[] cmdArray = new String[1+ args.length];
		
		cmdArray[0] = cmd;
		for (int i = 0; i < args.length; i++) {
			cmdArray[1+i] = args[i];
		}
		
		return ShellUtils.run(cmdArray, env, directory);
	}

	 */
	
	/*
	static public String[] mapToArray(Map<String, Object> map){
		Set<String> set = new HashSet<>();
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			set.add(entry.toString());
		}
		//return set.toArray(new String[set.size()]);
		return set.toArray(new String[0]);
	}
	 */

	/** Create a directory in which all the components are writable.
	 *
	 * param root - starting point
	 * param subdir - subdirectory
	 * @return - resulting directory (root and path concatenated)
	 * @throws IOException
	static public Path makeWritableDir(Path root, Path subdir) throws IOException{

		if (subdir == null)
			return root;

		if (subdir.getNameCount() == 0)
			return root;

		// recursion
		if (makeWritableDir(root, subdir.getParent()) == null)
			return null;

		Path dirPath = root.resolve(subdir);

		File dir = dirPath.toFile();

		if (!dir.exists()){
			//log.note("Creating dir: ").append(root);
			if (!dir.mkdirs()){
				throw new IOException("Creating dir failed: " + dirPath.toString());
				//log.warn("Creating dir failed: ").append(root);
				//return false;
			}
		}

		if (! dir.setWritable(true, false)){
			if (! dir.canWrite()){
				throw new IOException("Could not set dir writable: " + dirPath.toString());
			}
		}

		return dirPath;
	}
	 */

	interface ProcessReader {
		void handleStdOut(String line);
		void handleStdErr(String line);
	}

	/**
	 * 
	 * @param process - Process created with run etc.
	 * @return exit value
	 * @throws InterruptedException 
	 */
	static public int read(Process process, ProcessReader reader) throws InterruptedException{
		Thread stdoutReader = new Thread(() -> readStream(process.getInputStream(), reader, true),
				"nutshell-stdout-reader");
		Thread stderrReader = new Thread(() -> readStream(process.getErrorStream(), reader, false),
				"nutshell-stderr-reader");
		stdoutReader.start();
		stderrReader.start();

		process.waitFor();
		stdoutReader.join();
		stderrReader.join();
		return process.exitValue();

	}

	private static void readStream(InputStream stream, ProcessReader reader, boolean stdout){
		try (BufferedReader input = new BufferedReader(new InputStreamReader(stream))) {
			String line;
			while ((line = input.readLine()) != null) {
				if (stdout)
					reader.handleStdOut(line);
				else
					reader.handleStdErr(line);
			}
		}
		catch (IOException e) {
			reader.handleStdErr(String.format("Shell output read failed: %s", e.getLocalizedMessage()));
		}
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {

		/**
		 *  @see  @ShellExec:main
		 */
		if (args.length == 0){
			System.out.println("Usage:   <command> [<params>]");
			System.out.println("Example: ls . foo.bar");
			return; 
		}

		String[] env = {"A=1", "B=2"};
		
		File directory = new File(".");
		//try {

		if (args.length == 1)
			args = args[0].split(" ");


		ProcessReader handler = new ProcessReader() {

			@Override
			public void handleStdOut(String line) {
				System.out.println("STDOUT:" + line);
			}

			@Override
			public void handleStdErr(String line) {
				System.err.println("STDERR:" + line);
			}
		};

		//Process process = ShellExec.exec(args, env, directory.toPath(), handler);
		int value = 0;
		try {
			//ShellExec.TIMEOUT_SEC = 5;
			value = ShellExec.exec(args, env, directory.toPath(), handler);
		}
		catch (InterruptedException e) {
			System.err.println(String.format("Interrupted: %s", e));
			System.exit(1);
		}
		catch (IOException e) {
			System.err.println(String.format("IO error: %s", e));
			System.exit(2);
		}
		catch (TimeoutException e) {
			System.err.println(String.format("Timeout: %s", e));
			System.exit(3);
		}

		//read(process, handler);
		// System.out.println("exit value: " + process.exitValue());
		System.out.println("exit value: " + value);

		/*
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		 */
		
	}

}
