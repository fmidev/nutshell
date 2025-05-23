package nutshell;
/**
 *  @author Markus.Peura@fmi.fi
 */

import sun.misc.Signal;

import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 */
public class ShellExec {

	static
	public int TIMEOUT_SEC = 120;

	@Override
	public String toString() {
		// add id
		return String.format("%s", getClass().getSimpleName());
	}

	static
	public class OutputReader implements ShellUtils.ProcessReader {

		final PrintStream stdOut;
		final PrintStream stdErr;

		public String lastLineOut;
		public String lastLineErr;

		/** Simple reader directs both std. output and errors in the same stream.
		 *
		 * @param outStream
		 */
		OutputReader(PrintStream outStream) {
			stdOut = outStream;
			stdErr = outStream;
			lastLineOut = null;
			lastLineErr = null;
		}

		/** Reader that sepately directs std. output and error output.
		 *
		 * @param outStream
		 * @param errStream
		 */
		OutputReader(PrintStream outStream, PrintStream errStream) {
			stdOut = outStream;
			stdErr = errStream;
			lastLineOut = null;
			lastLineErr = null;
		}

		@Override
		public void handleStdOut(String line) {
			lastLineOut = line;
			stdOut.println(line);
		}

		@Override
		public void handleStdErr(String line) {
			lastLineErr = line;
			stdErr.println(line);
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
	protected IndexedState extractErrorMsg(int exitValue, OutputReader reader){

		//System.err.println(String.format("extractErrorMsg: %s", reader.lastLineErr));

		String[] msgErr = IndexedState.split(reader.lastLineErr);

		String code = msgErr[0];
		String text = msgErr[1];

		// System.err.println(String.format("%s = %s", code, text));
		if (code.isEmpty() || text.isEmpty()){
			String[] msgOut = IndexedState.split(reader.lastLineOut);
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
		return new IndexedState(i, String.format("%s (exit value=%d)", text, exitValue));
		//System.err.println(String.format("%s $ %s", code, text));

	}



	/** Utility for generators and input lists retrievals.
	 *
	 *  Note: "cancels" the exception by catching it and converting to a number.
	 *
	 * @param cmd - shell command like "ls" or "/tmp/script.sh"
	 * @param env - environment variables as assignment strings ["KEY=VALUE", "KEY2=VALUE2", ... ]
	 * @param reader - handler for standard and error output of the process
	 * param errorLog
	 */
	static
	int exec(String cmd, String[] env, Path dir, ShellUtils.ProcessReader reader)
			throws IOException, InterruptedException, TimeoutException { //}, final PrintStream errorLog) {
		final String[] cmds = {cmd};
		return exec(cmds, env, dir, reader);
	}

	static
	int exec(String[] cmd, String[] env, Path dir, ShellUtils.ProcessReader reader)
			throws IOException, InterruptedException, TimeoutException { //}, final PrintStream errorLog) {

		int exitValue;

		Process process;
		try {
			// System.err.println(String.format("Starting %s ...", cmd));
			final File d = (dir==null) ? null : dir.toFile();

			//final Process
			process = Runtime.getRuntime().exec(cmd, env, d);

			boolean success = process.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS);
			if (!success) {
				// timeout - kill the process.
				// process.destroy(); // consider using destroyForcibly instead
				process.destroyForcibly();
				// process.waitFor();
				//throw new InterruptedException(String.format("ShellExec timeout (%d s) elapsed", TIMEOUT_SEC));
				//throw new TimeoutException(String.format("ShellExec timeout (%d s) elapsed", TIMEOUT_SEC));
				throw new TimeoutException(String.format("ShellExec timeout (%d) elapsed", TIMEOUT_SEC) );
			}
			//System.err.println(String.format("reading output, process alive? %b", process.isAlive()));
			exitValue = ShellUtils.read(process, reader);
			//System.err.println(String.format("read exitValue=%d, process alive? %b", exitValue, process.isAlive()));
		}
		catch (InterruptedException e) {
			reader.handleStdErr(String.format("%d %s (ShellExec INTERRUPTED) ", HttpLog.HttpStatus.SERVICE_UNAVAILABLE.getIndex(), e.getLocalizedMessage()));
			throw e;
		}
		catch (IOException e) {
			reader.handleStdErr(String.format("%d %s (ShellExec IOEXCEPTION) ", HttpLog.HttpStatus.INTERNAL_SERVER_ERROR.getIndex(), e.getLocalizedMessage()));
			throw e;
		}
		catch (TimeoutException e){
			reader.handleStdErr(String.format("%d %s (ShellExec TIMEOUT) ", HttpLog.HttpStatus.REQUEST_TIMEOUT.getIndex(), e.getLocalizedMessage()));
			//exitValue = +3;
			throw e;
		}
 		/*
		catch (IOException e){
			reader.handleStdErr(String.format("%d %s", 501, e.getLocalizedMessage()));
			exitValue = +1;
			//errorLog.println(e.getLocalizedMessage());
		}
		catch (InterruptedException e){
			if (process != null){
				try {
					ShellUtils.read(process, reader);
				}
				catch (Exception e2){
					System.err.println("User interrupt");
					reader.handleStdErr(String.format("%d Reading output failed %s", 501, e.getLocalizedMessage()));
					return +10;
				}
			}

			reader.handleStdErr(String.format("%d %s", 501, e.getLocalizedMessage()));
			exitValue = -1;
			//errorLog.println(e.getLocalizedMessage());
		}*/
		catch (Exception e){
			// TODO: read (save) process output, even when interrupted. Currently it's dumped
			/*
			if (process != null){
				try {
					ShellUtils.read(process, reader);
				}
				catch (Exception e2){
					System.err.println("User interrupt!");
					reader.handleStdErr(String.format("%d Reading output failed %s", 501, e2.getLocalizedMessage()));
					return +10;
				}
			}
			 */
			// System.err.println("Eror");
			reader.handleStdErr(String.format("%s %s (unexpected error from ShellExec)", HttpLog.HttpStatus.CONFLICT, e.getLocalizedMessage()));
			exitValue = +2;
			//errorLog.println(e.getLocalizedMessage());
		}

		return exitValue;

	}

	static void handleInterrupt(Signal signal) {
		System.err.println(String.format("%s : interrupted (by Ctrl+C?) : %s", ShellExec.class.getSimpleName(), signal));
	}

	public static void main(String[] args) {

		String dir = ".";
		String[] cmd = null;

		switch (args.length){
			case 3:
				ShellExec.TIMEOUT_SEC = Integer.parseInt(args[2]);
			case 2:
				dir = args[1];
			case 1:
				cmd = args[0].trim().split(" ");
				break;
			//case 0:
			default:
				if (args.length != 0)
					System.err.println(String.format("Wrong number of args: %d", args.length));
				System.out.println(ShellExec.class.getCanonicalName());
				System.out.println(String.format("Run shell executable in a given working dir (default: %s)", dir));
				System.out.println("Usage:  <cmd>  [<dir>] [timeout]");
				System.out.printf("Example:%n  'ls -ltr' /tmp %d %n", ShellExec.TIMEOUT_SEC);
				System.out.printf("Example:%n  ./generate.sh /opt/nutshell/products/test/checkboard");
			return;
		}


		//OutputReader reader; // = new OutputReader(System.out);

		final String logname = ShellExec.class.getSimpleName() + ".log";
		System.out.println("# Writing log :" + logname);
		File logFile = new File(logname);
		System.err.println(String.format("Executing: %s (dir=%s)", cmd, dir));
		int result = 0;
		try {
			logFile.createNewFile();
			FileOutputStream fw = new FileOutputStream(logFile);
			OutputReader reader = new OutputReader(new PrintStream(fw), System.err);
			Signal.handle(new Signal("INT"), ShellExec::handleInterrupt);
			result = ShellExec.exec(cmd, null, Paths.get(dir), reader);
			System.err.println(String.format("Done! Result=%d. Wrote '%s'", result, logname));
		}
		catch (IOException e){
			System.err.println(String.format("Oops, I/O error: %s", e.getMessage()));
		}
		catch (InterruptedException e){
			System.err.println(String.format("Oops, interrupt: %s", e.getMessage()));
		}
		catch (TimeoutException e){
			System.err.println(String.format("Oops, timeout: %s", e.getMessage()));
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
