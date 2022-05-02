package nutshell;
/**
 *  @author Markus.Peura@fmi.fi
 */

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.*;

/**
 *
 */
public class ShellExec {
	

	final Path dir;
	final File cmd;
	/*
	@Override
	public int generate(Map<String,Object> parameters, PrintStream log) { //throws IOException, InterruptedException {
		String[] env = MapUtils.toArray(parameters); // parameters.entrySet().toArray(new String[0]);
		return generate(env,log);
	}
	 */
	public ShellExec(Path cmd, Path dir){
		//throws IndexedException
		this.dir = dir.normalize(); //new File(dir);
		this.cmd = this.dir.resolve(cmd).toFile().getAbsoluteFile();
	}


	public ShellExec(String cmd, String dir){
		this(Paths.get(cmd), Paths.get(dir));
		// throws IndexedException
		// this.dir = Paths.get(dir).normalize(); //new File(dir);
		// this.cmd = this.dir.resolve(cmd).toFile().getAbsoluteFile();
	}

	@Override
	public String toString() {
		// add id
		return String.format("%s", getClass().getSimpleName());
	}

	static
	public class OutputReader implements ShellUtils.ProcessReader {

		final PrintStream stream;
		public String lastLineOut;
		public String lastLineErr;

		OutputReader(PrintStream log) {
			stream = log;
			lastLineOut = null;
			lastLineErr = null;
		}


		@Override
		public void handleStdOut(String line) {
			lastLineOut = line;
			stream.println(line);
		}

		@Override
		public void handleStdErr(String line) {
			lastLineErr = line;
			stream.println(line);
		}

	}

	/** Try to extract numeric value out of script's error dump (last line).
	 *
	 * The script (generate.sh) should print (echo) a {@link HttpServletResponse} status code
	 * followed by space and an optional description of the status (error).
	 *
	 * See https://developer.mozilla.org/en-US/docs/Web/HTTP/Status
	 *
	 * @param exitValue
	 * @param reader
	 * @return
	 */
	protected IndexedException extractErrorMsg(int exitValue, OutputReader reader){

		String[] msgErr = IndexedException.split(reader.lastLineErr);

		String code = msgErr[0];
		String text = msgErr[1];

		// System.err.println(String.format("%s = %s", code, text));
		if (code.isEmpty() || text.isEmpty()){
			String[] msgOut = IndexedException.split(reader.lastLineOut);
			if (code.isEmpty()) {
				if (!msgOut[0].isEmpty()) {
					code = msgOut[0];
					if (!msgOut[1].isEmpty()) { // "numeric force": replace error msg
						text = msgOut[1]; // override even if
					}
				}
			}
			if (text.isEmpty()) { // Rare mixed case: code from stderr, text from stdout
				text = msgOut[1];
			}
			//System.err.println(String.format("%s > %s", code, text));
		}

		if (text.isEmpty()){
			text = "Script error without message";
		}

		int i = 0;
		try {
			i = Integer.parseInt(code); // ! NumberFormatException
		}
		catch (NumberFormatException e){
			i = 501;
		}
		return new IndexedException(i, String.format("%s (exit value=%d)", text, exitValue));
		//System.err.println(String.format("%s $ %s", code, text));

	}



	/** Utility for generators and input lists retrievals.
	 *
	 * @param cmd - shell command like "ls" or "/tmp/script.sh"
	 * @param env - environment variables as assignment strings ["KEY=VALUE", "KEY2=VALUE2", ... ]
	 * @param reader - handler for standard and error output of the process
	 * param errorLog
	 */
	static
	int exec(String cmd, String[] env, Path dir, ShellUtils.ProcessReader reader){ //}, final PrintStream errorLog) {

		int exitValue = 0;

		try {
			//final Process process1 = Runtime.getRuntime().exec(cmd, )
			final Process process = Runtime.getRuntime().exec(cmd, env, dir.toFile());
			//process.
			exitValue = ShellUtils.read(process, reader);
		}
 		catch (IOException e){
			reader.handleStdErr(String.format("%d %s", 501, e.getLocalizedMessage()));
			exitValue = +1;
			//errorLog.println(e.getLocalizedMessage());
		}
		catch (InterruptedException e){
			reader.handleStdErr(String.format("%d %s", 501, e.getLocalizedMessage()));
			exitValue = -1;
			//errorLog.println(e.getLocalizedMessage());
		}
		catch (Exception e){
			reader.handleStdErr(String.format("%d %s", 501, e.getLocalizedMessage()));
			exitValue = +2;
			//errorLog.println(e.getLocalizedMessage());
		}

		return exitValue;

	}

	public static void main(String[] args) {

		String dir = ".";
		String cmd = null;

		switch (args.length){
			case 2:
				dir = args[1];
			case 1:
				cmd = args[0];
				break;
			case 0:
				System.err.println(String.format("Wrong number of args: %d", args.length));
			default:
				System.out.println(ShellExec.class.getCanonicalName());
				System.out.println(String.format("Run shell executable in a given working dir (default: %s)", dir));
				System.out.println("Usage:   <cmd>  [<dir>] ");
				System.out.println("Example: ls");
			return;
		}

		// ?? WHy instance
		ShellExec shellExec = null;
		try {
			// ??
			shellExec = new ShellExec(dir, cmd);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		System.out.println(shellExec.toString());

		OutputReader reader = new OutputReader(System.err);

		final String logname = ShellExec.class.getSimpleName() + ".log";
		System.out.println("Writing log :" + logname);
		File logFile = new File(logname);
		System.out.println(String.format("Executing: %s (dir=", shellExec.cmd, shellExec.dir));
		try {
			logFile.createNewFile();
			FileOutputStream fw = new FileOutputStream(logFile);
			ShellExec.exec(cmd, null, shellExec.dir, reader);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}



		/*
		Map<String,String> map = generator.getInputList(args, System.err);
		for (Entry<String, String> entry: map.entrySet()) {
			System.out.println("\t" + entry.toString());
		}
		*/
		//generator.generate(args, System.out);


		
	}



}