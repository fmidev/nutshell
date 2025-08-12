package nutshell9;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ProductPathBundle {

    /// Checked, "normalized" filename, with ordered parameters.
    final public String filename;
    final public Path timeStampDir;
    final public Path productDir;

    final public Path relativeOutputDir;
    final public Path relativeOutputDirTmp;
    final public Path relativeOutputPath;

    // Logging & diagnostics
    final public Path relativeSystemDir;
    final public Path systemDir;
    final public Path relativeGraphPath;

    final public Path outputDir;
    final public Path outputDirTmp;
    final public Path outputPath;
    final public Path outputPathTmp;
    final public Path storagePath;
    // final public Path logRel_path;
    // final public Path logPath;



	public class TmpPathBundle extends PathBundle {
		
		public TmpPathBundle(PathBundle bundle){
			super(bundle.getDirname(), Paths.get("tmp"), bundle.getFilename());
		}
	}

	public class LogPathBundle extends PathBundle {
		
		public LogPathBundle(PathBundle bundle, String extension){
			super(bundle.getDirname(), String.format("%s.%s", bundle.getFilename(), extension));
		}
	}

    

    final public PathBundle productRel;
    final public PathBundle productAbs;
    final public PathBundle productTmpRel;
    final public PathBundle productTmpAbs;
    final public PathBundle logRel;
    final public PathBundle logAbs;

    ProductPathBundle(ProductServerBase server, ProductInfo product, String label, String logFormat){

            // super(new PathBundle(server.CACHE_ROOT, logRel));
			if (label == null){
                label = "nutshell";
            }

            /*  TODO: 
             * 
             */
            productRel = new PathBundle(server.getProductDir(product.PRODUCT_ID), 
            		product.getTimeStampDir(), product.getFilename());

            productAbs = new PathBundle(server.CACHE_ROOT, productRel);

            productTmpRel = new TmpPathBundle(productRel);
            
            productTmpAbs = new PathBundle(server.CACHE_ROOT, productTmpRel);
            
            logRel = new LogPathBundle(productRel, logFormat);
            logAbs = new PathBundle(server.CACHE_ROOT, logRel);
            
            System.out.println(productAbs.toString());
            System.out.println(productTmpAbs.toString());
            System.out.println(logAbs.toString());
            
            filename = product.getFilename();
            
            // Relative
			productDir = server.getProductDir(product.PRODUCT_ID);
			timeStampDir = product.getTimeStampDir();
            // relativeOutputDir = timeStampDir.resolve(productDir);
            relativeOutputDir = productRel.getDirname();


			//this.relativeOutputDirTmp = this.timeStampDir.resolve(this.productDir).resolve(String.format("tmp-%s-%d", ) + getTaskId());
			//relativeOutputDirTmp = relativeOutputDir.resolve(label); //String.format("tmp-%s-%d", USER, getTaskId()));
			relativeOutputDirTmp = productTmpRel.getDirname();

			relativeOutputPath   = relativeOutputDir.resolve(filename);
			
			// Absolute
			outputDir    = server.CACHE_ROOT.resolve(relativeOutputDir);
			outputDirTmp = server.CACHE_ROOT.resolve(relativeOutputDirTmp);
		
			outputPath    = outputDir.resolve(filename);
			outputPathTmp = outputDirTmp.resolve(filename);
			
			

            // Todo: many, and perhaps product dependent.
			storagePath     = server.STORAGE_ROOT.resolve(relativeOutputDir).resolve(filename);
            // logRel_path = relativeOutputDir.resolve(String.format("%s_%s.%s", filename, label, logFormat));
            // logPath = server.CACHE_ROOT.resolve(logRel_path); 
			
            String systemBaseName = product.TIMESTAMP + "_nutshell." + product.PRODUCT_ID + "_" + label; //getTaskId();

            relativeSystemDir = timeStampDir.resolve("nutshell").resolve(productDir);
            systemDir = server.CACHE_ROOT.resolve(relativeSystemDir); 
            relativeGraphPath = relativeSystemDir.resolve(systemBaseName + ".svg");

    }

    
    public static void main(String[] args) {

    }

}
