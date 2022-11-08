package nutshell;
/**
 *  @author Markus.Peura@fmi.fi
 */

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;

/**
 *
 */
public class ExternalGenerator extends ShellExec implements ProductServer.Generator {
	// extends ShellExec
	// TODO: override these from ProductServer.conf?
	//static public final String scriptName = "./generate.sh";
	static public final String scriptName = "generate.sh";
	//static public final String inputDeclarationScript = "./input.sh";
	static public final String inputDeclarationScript = "input.sh";

	/// Permissions to set on Unix file system
	static public String umask = "";

	final String id;
	final Path dir;
	final File cmd;


	final protected File inputDeclarationCmd;

	public ExternalGenerator(String id, String dir) throws IndexedState {
		//super(scriptName, dir);
		this.id = id;
		this.dir = Paths.get(dir).normalize();
		this.cmd = this.dir.resolve(scriptName).toFile().getAbsoluteFile();
		//this.dir = Paths.get(dir); //new File(dir);
		// this.cmd = this.dir.resolve(scriptName).toFile().getAbsoluteFile();
		if (!this.cmd.exists())
			throw new IndexedState(HttpServletResponse.SC_NOT_IMPLEMENTED, String.format("Script %s not found", this.cmd));
		if (!this.cmd.canRead())
			throw new IndexedState(HttpServletResponse.SC_METHOD_NOT_ALLOWED, String.format("Script %s unreadable", this.cmd));

		this.inputDeclarationCmd = this.dir.resolve(inputDeclarationScript).toFile();
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
		return String.format("%s[%s] @%s",getClass().getSimpleName(), id, dir);
	}

	//@Override
	/*
	public boolean hasInputs() {
		return this.inputDeclarationCmd.exists();
	}
 	*/

	// MAIN
	@Override
	public void generate(ProductServer.Task task) throws IndexedState {
		generateFile(MapUtils.toArray(task.getParamEnv()), task.log.getPrintStream());
	}

	/// Generates a product and stores it in a file system.
	public void generateFile(String[] envArray, PrintStream log) throws IndexedState {

		OutputReader reader = new OutputReader(log);

		int exitValue = 0;

		//System.err.println(String.format("UMASK=%s", umask));

		if (umask.isEmpty()){
			//System.err.println(String.format("UMASK empty, ok. ERR: %s"));
			exitValue = ShellExec.exec(cmd.toString(), envArray, dir, reader);
		}
		else {
			final String[] batch = {"bash", "-c", String.format("umask %s; %s", ExternalGenerator.umask, cmd)};
			exitValue = exec(batch, envArray, dir, reader);
			//exitValue = exec(cmd.toString(), envArray, dir, reader);
		}

		// System.err.println(String.format("FINISHED (%d)", exitValue));

		//
		if (exitValue != 0){
			throw extractErrorMsg(exitValue, reader);
		}

	}


	@Override
	public Map<String,String> getInputList(ProductServer.Task task) throws IndexedState {
		//return getInputList(MapUtils.toArray(task.getParamEnv()), task.log.getPrintStream());
		return getInputList(MapUtils.toArray(task.getParamEnv()), task.log);
	}

	// public Map<String,String> getInputList(String[] env, final PrintStream errorLog) { //throws InterruptedException {
	//public Map<String,String> getInputList(String[] env, PrintStream errorLog) throws IndexedException {
	public Map<String,String> getInputList(String[] env, Log errorLog) throws IndexedState {
		//public Map<String,String> getInputList(ProductServer.Task task) { //throws InterruptedException {
		final Map<String, String> result = new HashMap<>();

		// String[] env = MapUtils.toArray(parameters.getParamEnv(null));
		// String[] env = MapUtils.toArray(parameters);

		//OutputReader reader =  new OutputReader(errorLog){ //new ShellUtils.ProcessReader() {
		OutputReader reader =  new OutputReader(errorLog.getPrintStream()){ //new ShellUtils.ProcessReader() {

			@Override
			public void handleStdOut(String line) {
				this.lastLineOut = line;
				MapUtils.parse(line, result);
			}

		};

		// Todo: construct final
		//Path script = dir.toPath().resolve(inputDeclarationScript);

		if (inputDeclarationCmd.exists()){
			if (inputDeclarationCmd.canExecute()){
				//int exitValue = exec(inputDeclarationScript, env, reader);
				int exitValue = exec(inputDeclarationCmd.toString(), env, dir, reader);
				if (exitValue != 0){
					throw extractErrorMsg(exitValue, reader);
				}
			}
			else {
				errorLog.warn(String.format("Cannot execute input declarator script: '%s'", inputDeclarationCmd ));
				//errorLog.println("# WARNING: exists, but not executable: " + inputDeclarationCmd.toString());
			}
		}
		else {
			errorLog.info(String.format("Input declarator script not found: '%s'", inputDeclarationCmd ));
			// errorLog.println("# INFO: not found: " + inputDeclarationCmd.toString());
		}

		return result;
	}

	public static void main(String[] args) {


		if (args.length == 0){
			System.out.println("Usage:   <product_dir>  [<key>=<val> <key>=<val> ... ] ");
			System.out.println("Example: products/test/ppmforge");
			return;
		}

		String dir = args[0];

		Log log = new Log("test");

		ExternalGenerator generator = null;

		try {
			generator = new ExternalGenerator("test", dir);
		} catch (IndexedState e) {
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
			Map<String,String> map = generator.getInputList(env, log);
			for (Entry<String,String> entry: map.entrySet()){
				System.out.println(String.format("\t%s=%s", entry.getKey(), entry.getValue()));
			}
		}
		catch (IndexedState e){
			e.printStackTrace();
			return;
		}

		final String logname = "ExternalGenerator.log";
		System.out.println(String.format("Writing log: %s", logname));
		File logFile = new File(logname);
		System.out.println("Generating...");
		try {
			logFile.createNewFile();
			FileOutputStream fw = new FileOutputStream(logFile);
			generator.generateFile(env, new PrintStream(fw));
			System.out.println(String.format("Success! (See log and %s", generator.dir));
		} catch (IOException | IndexedState e) {
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
