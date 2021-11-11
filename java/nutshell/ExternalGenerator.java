package nutshell;
/**
 *  @author Markus.Peura@fmi.fi
 */

import org.omg.Messaging.SyncScopeHelper;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 *
 */
public class ExternalGenerator implements ProductServer.Generator {
	
	// TODO: override these from ProductServer.conf?
	//static public final String scriptName = "./generate.sh";
	static public final String scriptName = "generate.sh";
	//static public final String inputDeclarationScript = "./input.sh";
	static public final String inputDeclarationScript = "input.sh";
	final String id;

	//final protected File dir;
	final protected Path dir;
	final protected File generatorScript;
	final protected File inputScript;

	public ExternalGenerator(String id, String dir) throws IndexedException {
		this.id = id;
		this.dir = Paths.get(dir); //new File(dir);
		this.generatorScript = this.dir.resolve(scriptName).toFile();
		if (!this.generatorScript.exists())
			throw new IndexedException(HttpServletResponse.SC_NOT_IMPLEMENTED, String.format("Script %s not found", this.generatorScript));
		if (!this.generatorScript.canRead())
			throw new IndexedException(HttpServletResponse.SC_METHOD_NOT_ALLOWED, String.format("Script %s unreadable", this.generatorScript));

		this.inputScript     = this.dir.resolve(inputDeclarationScript).toFile();
	}


	/*
	@Override
	public int generate(Map<String,Object> parameters, PrintStream log) { //throws IOException, InterruptedException {
		String[] env = MapUtils.toArray(parameters); // parameters.entrySet().toArray(new String[0]);
		return generate(env,log);
	}
	 */

	@Override
	public String toString() {
		return "ExternalGenerator{" + id + ':' + dir + '}';
	}

	@Override
	public boolean hasInputs() {
		return this.inputScript.exists();
	}

	public class OutputReader implements ShellUtils.ProcessReader {

		final PrintStream stream;

		OutputReader(PrintStream log) {
			stream = log;
			lastLineOut = null;
			lastLineErr = null;
		}

		public String lastLineOut;
		public String lastLineErr;

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

	/** Try to extract numeric value out of script's error dump (last line)
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

	// MAIN
	@Override
	public void generate(ProductServer.Task task) throws IndexedException {
		generateFile(MapUtils.toArray(task.getParamEnv()), task.log.getPrintStream());
	}


	public void generateFile(String[] envArray, PrintStream logStream) throws IndexedException {

		OutputReader reader = new OutputReader(logStream);

		//int exitValue = exec(scriptName, envArray, reader);
		int exitValue = exec(generatorScript.toString(), envArray, reader);
		if (exitValue != 0){
			throw extractErrorMsg(exitValue, reader);
		}


	}

	/*
	public Map<String,String> getInputList(Map<String,Object> parameters, PrintStream errorLog) { //throws InterruptedException {
		String[] env = MapUtils.toArray(parameters); // parameters.entrySet().toArray(new String[0]);
		return getInputList(env, errorLog);
	}
	 */
	@Override
	public Map<String,String> getInputList(ProductServer.Task task) throws IndexedException {
		return getInputList(MapUtils.toArray(task.getParamEnv()), task.log.getPrintStream());
	}

	// public Map<String,String> getInputList(String[] env, final PrintStream errorLog) { //throws InterruptedException {
	public Map<String,String> getInputList(String[] env, PrintStream errorLog) throws IndexedException {
		//public Map<String,String> getInputList(ProductServer.Task task) { //throws InterruptedException {
		final Map<String, String> result = new HashMap<>();

		// String[] env = MapUtils.toArray(parameters.getParamEnv(null));
		// String[] env = MapUtils.toArray(parameters);

		OutputReader reader =  new OutputReader(errorLog){ //new ShellUtils.ProcessReader() {

			@Override
			public void handleStdOut(String line) {
				this.lastLineOut = line;
				MapUtils.parse(line, result);
			}

		};

		// Todo: construct final
		//Path script = dir.toPath().resolve(inputDeclarationScript);

		if (inputScript.exists()){
			if (inputScript.canExecute()){
				//int exitValue = exec(inputDeclarationScript, env, reader);
				int exitValue = exec(inputScript.toString(), env, reader);
				if (exitValue != 0){
					throw extractErrorMsg(exitValue, reader);
				}
			}
			else {
				errorLog.println("warn(): exists, but not executable: " + inputScript.toString());
			}
		}
		else {
			errorLog.println("Not found: " + inputScript.toString());
		}

		return result;
	}

	/** Utility for generators and input lists retrievals.
	 *
	 * @param cmd - shell command like "ls" or "/tmp/script.sh"
	 * @param env - environment variables as assignment strings ["KEY=VALUE", "KEY2=VALUE2", ... ]
	 * @param reader - handler for standard and error output of the process
	 * param errorLog
	 */
	protected int exec(String cmd, String[] env, ShellUtils.ProcessReader reader){ //}, final PrintStream errorLog) {

		int exitValue = 0;

		try {
			final Process process = Runtime.getRuntime().exec(cmd, env, dir.toFile());
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


		if (args.length == 0){
			System.out.println("Usage:   <product_dir>  [<key>=<val> <key>=<val> ... ] ");
			System.out.println("Example: products/test/ppmforge");
			return;
		}

		String dir = args[0];

		ExternalGenerator generator = null;

		try {
			generator = new ExternalGenerator("unnamed.generator", dir);
		} catch (IndexedException e) {
			e.printStackTrace();
			System.exit(1);
		}

		System.out.println(generator.toString());

		ArrayList<String> list = new ArrayList<>();
		list.add("OUTDIR=.");
		list.add("OUTFILE=out.tmp");

		list.addAll(1, Arrays.asList(args.clone()));

		String[] env = list.toArray(new String[0]);

		System.out.println("Env:" + Arrays.toString(env));

		System.out.println("Inputs:");
		try {
			Map<String,String> map = generator.getInputList(env, System.out);
			for (Entry<String,String> entry: map.entrySet()){
				System.out.println(String.format("\t%s=%s", entry.getKey(), entry.getValue()));
			}
		}
		catch (IndexedException e){
			e.printStackTrace();
			return;
		}

		final String logname = "ExternalGenerator.log";
		System.out.println("Writing :" + logname);
		File logFile = new File(logname);
		System.out.println("Generate:");
		try {
			logFile.createNewFile();
			FileOutputStream fw = new FileOutputStream(logFile);
			generator.generateFile(env, new PrintStream(fw));
		} catch (IOException | IndexedException e) {
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
