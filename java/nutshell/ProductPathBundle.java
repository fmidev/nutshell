package nutshell;

import java.nio.file.Path;

public class ProductPathBundle {

    final public String filename;
    final public Path timeStampDir;
    final public Path productDir;

    final public Path relativeOutputDir;
    final public Path relativeOutputDirTmp;
    final public Path relativeOutputPath;

    // Logging & diagnostics
    final public Path relativeSystemDir;
    public Path relativeLogPath;
    public Path relativeGraphPath;

    final public Path outputDir;
    final public Path outputDirTmp;
    final public Path outputPath;
    final public Path outputPathTmp;
    final public Path storagePath;

    ProductPathBundle(ProductServerBase server, ProductInfo product, String label){

            if (label == null){
                label = "nutshell";
            }

            filename = product.getFilename();
               // Relative
			productDir = server.getProductDir(product.PRODUCT_ID);

			// timeStampDir = getTimestampDir(this.info.time);
			timeStampDir = product.getTimeStampDir();

            relativeSystemDir = this.timeStampDir.resolve("nutshell").resolve(this.productDir);

            relativeOutputDir = timeStampDir.resolve(this.productDir);

			//this.relativeOutputDirTmp = this.timeStampDir.resolve(this.productDir).resolve(String.format("tmp-%s-%d", ) + getTaskId());
			relativeOutputDirTmp = this.relativeOutputDir.resolve(label); //String.format("tmp-%s-%d", USER, getTaskId()));
			relativeOutputPath = relativeOutputDir.resolve(filename);

			//this.relativeLogPath    = relativeOutputDir.resolve(getFilePrefix() + filename + "." + getTaskId() + ".log");
			// Absolute
			outputDir = server.CACHE_ROOT.resolve(this.relativeOutputDir);
			outputDirTmp = server.CACHE_ROOT.resolve(this.relativeOutputDirTmp);
			outputPath = outputDir.resolve(filename);
			//this.outputPathTmp = outputDirTmp.resolve(getFilePrefix() + filename);
			outputPathTmp = outputDirTmp.resolve(filename);

            // Todo: many, and perhaps product dependent.
			storagePath = server.STORAGE_ROOT.resolve(this.relativeOutputDir).resolve(filename);
            relativeLogPath = relativeOutputDir.resolve(filename + "." + label + ".log");  // add ".html" in log

            String systemBaseName = product.TIMESTAMP + "_nutshell." + product.PRODUCT_ID + "_" + label; //getTaskId();
            this.relativeGraphPath = relativeSystemDir.resolve(systemBaseName + ".svg");

    }

    public static void main(String[] args) {

    }

}
