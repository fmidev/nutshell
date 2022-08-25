package nutshell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.CharBuffer;

public class ShellUtils {


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
		
		// Consider two separate processed, if timing is not issue.
		InputStream inputStream = process.getInputStream();
		InputStream errorStream = process.getErrorStream();

		/*  NOTE

			Now this works better – ie input streams do not block – but still cuts some input, at least stderr.

		 */

		try {

			BufferedReader inputReader = new BufferedReader(new InputStreamReader(inputStream));
			BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream));

			String inputLine = "";
			String errorLine = "";

			// System.out.println(String.format("START read of: ", process.toString()));

			while ((inputLine!=null) && (errorLine!=null)) {

				///if (inputReader.ready()){  EI AUTTANUT!
				if (inputLine != null) {
					if ((inputLine = inputReader.readLine()) != null) { // Jumittuu tähän...
						// Debug
						// System.out.println(String.format("std[%b]: %s \t...", inputReader.ready(), inputLine));
						reader.handleStdOut(inputLine); // oma
					}
				}

				//if (errorReader.ready()){ EI AUTTANUT!
				if (errorLine != null) {
					if ((errorLine = errorReader.readLine()) != null) { // .. tai jumittuu tähän
						// Debug
						// System.out.println(String.format("err[%b]: %s \t...", errorReader.ready(), errorLine));
						reader.handleStdErr(errorLine); // oma
					}
				}

			}
			inputReader.close();
			errorReader.close();

		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		//check child.waitFor();
		process.waitFor();
		
		return process.exitValue();
		
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		
		if (args.length == 0){
			System.out.println("Usage:   <command> [<params>]");
			System.out.println("Example: ls . foo.bar");
			return; 
		}

		String[] env = {"A=1", "B=2"};
		
		File directory = new File(".");
		try {

			if (args.length == 1)
				args = args[0].split(" ");
				
			Process process = ShellUtils.run(args, env, directory);
			
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
			
			read(process, handler);
			
			System.out.println("exit value: " + process.exitValue());
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
