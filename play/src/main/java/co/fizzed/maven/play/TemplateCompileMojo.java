package co.fizzed.maven.play;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * 
 * @author joelauer
 */
@Mojo(name = "template-compile", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class TemplateCompileMojo extends AbstractMojo {

    /**
     * Additional compiled source directories.
     * 
     * @since 1.0
     */
    @Parameter(property = "templateDirectory", defaultValue = "${project.build.sourceDirectory}/views", required = true)
    private File templateDirectory;
    
    /**
     * Directory to output generated Java source file
     *
     * @since 1.0.0
     */
    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}/generated-sources/play-templates", required = true)
    protected File outputDirectory;

    /**
     * @since 1.0
     */
    @Parameter( defaultValue = "${project}", readonly = true )
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!outputDirectory.exists()) {
            getLog().info("Play template compiler creating generated source directory: " + outputDirectory);
            outputDirectory.mkdirs();
        }
        
        // add the output directory to sources that will be compiled
        this.project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        getLog().info("Play templates generated source directory: " + outputDirectory + " added to project" );
        
        try {
            File[] files = templateDirectory.listFiles();
            for (File f : files) {
                processFile(f, "");
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
    
    public void processFile(File f, String path) throws Exception {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            for (File child : children) {
                String newPath = (path.equals("") ? f.getName() : path + "/" + f.getName());
                processFile(child, newPath);
            }
        } else {
            if (f.getName().endsWith(".scala.html")) {
                File inputFile = f;
                String javaPackage = "views.html";
                if (!path.equals("")) {
                    javaPackage += "." + path.replace('/', '.');
                }
                
                File outputDir = new File(outputDirectory, javaPackage.replace('.', '/'));
                outputDir.mkdirs();
                
                String className = f.getName().replace(".scala.html", "");
                File outputFile = new File(outputDir, className + ".java");
                getLog().debug(inputFile + " -> " + outputFile);
                getLog().debug(" in package: " + javaPackage);
                
                generateJava(f, outputFile, className, javaPackage);
            }
        }
    }
    
    public void generateJava(File templateFile, File javaFile, String className, String javaPackage) throws Exception {
        String renderArgs = "Object ... params";
        
        // try to parse first line of template with @( line
        BufferedReader br = new BufferedReader(new FileReader(templateFile));
        String firstLine = br.readLine();
        
        // @(accounts : List[AccountSummary])
        if (firstLine.startsWith("@(")) {
            // find end -- first closing parenthese
            int end = -1;
            int parentheseCount = 1;
            for (int i = 2; i < firstLine.length(); i++) {
                char c = firstLine.charAt(i);
                if (c == '(') {
                    parentheseCount++;
                }
                if (c == ')') {
                    parentheseCount--;
                }
                if (parentheseCount == 0) {
                    end = i;
                    break;
                }
            }
            
            if (end < 0) {
                // do nothing
            } else {
                String argLine = firstLine.substring(2, end);
                getLog().debug("argLine: " + argLine);

                String newRenderArgs = "";
                String[] args = argLine.split(":");
                
                // process everything EXCEPT last arg
                for (int i = 0; i < args.length - 1; i++) {
                    // remove everything up to command and then all whitespace
                    String arg = args[i];
                    int commaPos = arg.lastIndexOf(',');
                    if (commaPos >= 0) {
                        arg = arg.substring(commaPos+1);
                    }
                    arg = arg.trim();

                    if (newRenderArgs.length() != 0) {
                        newRenderArgs += ", ";
                    }
                    newRenderArgs += "Object " + arg;
                }

                renderArgs = newRenderArgs;
            }
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(javaPackage).append(";\n");
        sb.append("public class ").append(className).append(" {\n");
        sb.append("    static public String render(").append(renderArgs).append(") {\n");
        sb.append("        // fake method just for command line completion\n");
        sb.append("        return \"\";\n");
        sb.append("    }\n");
        sb.append("}\n");
        
        FileOutputStream fos = new FileOutputStream(javaFile, false);
        fos.write(sb.toString().getBytes("UTF-8"));
        fos.flush();
        fos.close();
    }
    
    
    
}
