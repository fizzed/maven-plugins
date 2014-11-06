package co.fizzed.maven.versionizer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;

/**
 * Utility for generating a Java source code file that will be compiled with
 * a project to supply a Version class.  An alternative to extracting the 
 * manifest inside a jar for getting a version of a dependency at runtime.
 * Will generate the Java source file by default during the "generate sources"
 * phase and will also attach the outputDirectory it uses to the project's
 * source code.
 * 
 * @author joelauer
 */
@Mojo(name = "generate",
      defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      threadSafe = true
    )
public class GenerateMojo extends AbstractMojo {

    static final long staticMillis = System.currentTimeMillis();
    static final SimpleDateFormat defaultDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    static {
        defaultDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    /**
     * Directory to output generated Java source file
     *
     * @since 1.0.0
     */
    @Parameter(property = "outputDirectory", alias = "versionizer.outputDirectory", defaultValue = "${project.build.directory}/generated-sources/versionizer", required = true)
    protected File outputDirectory;
    
    /**
     * Package name of Version class file.
     * 
     * @since 1.0.0
     */
    @Parameter(property = "javaPackage", alias = "versionizer.javaPackage", defaultValue = "")
    protected String javaPackage;
    
    /**
     * Name of Version class to generate.
     * 
     * @since 1.0.0
     */
    @Parameter(property = "className", alias = "versionizer.className", defaultValue = "Version", required = true)
    protected String className;
    
    /**
     * Commit of project for Version class (such as git hash, usually produced from another plugin).
     * 
     * @since 1.0.0
     */
    @Parameter(property = "versionCommit", alias = "versionizer.commit", defaultValue = "unknown", required = false)
    protected String versionCommit;
    
    /**
     * Timestamp of build for Version class.
     * 
     * @since 1.0.0
     */
    @Parameter(property = "versionTimestampMillis", alias = "versionizer.timestampMillis", defaultValue = "", required = false)
    protected Long versionTimestampMillis;
    
    /**
     * Timestamp of build for Version class.
     * 
     * @since 1.0.0
     */
    @Parameter(property = "versionTimestamp", alias = "versionizer.timestamp", defaultValue = "", required = false)
    protected String versionTimestamp;
    
    /**
     * Version of build for Version class.
     * 
     * @since 1.0.0
     */
    @Parameter(property = "versionVersion", alias = "versionizer.version", defaultValue = "${project.version}", required = true)
    protected String versionVersion;
    
    /**
     * Name of build for Version class (artifactId is usually best).
     * 
     * @since 1.0.0
     */
    @Parameter(property = "versionName", alias = "versionizer.name", defaultValue = "${project.artifactId}", required = true)
    protected String versionName;
    
    /**
     * Vendor of build for Version class (groupId is usually best).
     * 
     * @since 1.0.0
     */
    @Parameter(property = "versionVendor", alias = "versionizer.vendor", defaultValue = "${project.groupId}", required = true)
    protected String versionVendor;
    
    @Parameter( defaultValue = "${project}", readonly = true )
    protected MavenProject project;
    
    @Component
    private RuntimeInformation runtime;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.javaPackage == null || this.javaPackage.equals("")) {
            getLog().info("Skipping (property javaPackage is empty)");
            return;
        }
        
        if (!outputDirectory.exists()) {
            getLog().info("Versionizer creating generated source directory: " + outputDirectory);
            outputDirectory.mkdirs();
        }
        
        // add the output directory to sources that will be compiled
        this.project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        getLog().info("Versionizer generated source directory: " + outputDirectory + " added to project" );
    
        // convert javaPackage into directory name
        String javaPackagePath = this.javaPackage.replaceAll("\\.", File.separator);
        File javaPackageDir = new File(outputDirectory, javaPackagePath);
        javaPackageDir.mkdirs();
        
        File javaClassFile = new File(javaPackageDir, this.className + ".java");
        getLog().info("Versionizer generating version java source: " + javaClassFile);
        
        PrintWriter pw = null;
        try {
            long ts = staticMillis;
            if (this.versionTimestampMillis != null) {
                ts = this.versionTimestampMillis;
            }
            
            Date defaultTimestamp = new Date(ts);
            String defaultTimestampStr = defaultDateFormat.format(defaultTimestamp);
            if (this.versionTimestamp != null && !this.versionTimestamp.equals("")) {
                defaultTimestampStr = this.versionTimestamp;
            }
            
            String longVersion = this.versionVersion + " (commit " + this.versionCommit + " @ " + defaultTimestampStr + ")";
            pw = new PrintWriter(new FileWriter(javaClassFile));
            pw.append("/**\n");
            pw.append(" * DO NOT EDIT THIS FILE (auto generated by fizzed-versionizer-maven-plugin)\n");
            pw.append(" */\n");
            pw.append("package " + this.javaPackage + ";\n");
            pw.append("public final class " + this.className + " {\n");
            pw.append("    private static final String COMMIT=\"" + this.versionCommit + "\";\n");
            pw.append("    private static final String TIMESTAMP=\"" + defaultTimestampStr + "\";\n");
            pw.append("    private static final String VERSION=\"" + this.versionVersion + "\";\n");
            pw.append("    private static final String NAME=\"" + this.versionName + "\";\n");
            pw.append("    private static final String VENDOR=\"" + this.versionVendor + "\";\n");
            pw.append("    private static final String LONG_VERSION=\"" + longVersion + "\";\n");
            pw.append("    /** Returns the library source control commit tag such as \"ac486420\" */\n");
            pw.append("    static public String getCommit() { return COMMIT; }\n");
            pw.append("    /** Returns the library vendor such as \"" + this.versionVendor + "\" */\n");
            pw.append("    static public String getVendor() { return VENDOR; }\n");
            pw.append("    /** Returns the library build timestamp (date) such as \"" + defaultTimestampStr + "\" */\n");
            pw.append("    static public String getTimestamp() { return TIMESTAMP; }\n");
            pw.append("    /** Returns the library name such as \"" + this.versionName + "\" */\n");
            pw.append("    static public String getName() { return NAME; }\n");
            pw.append("    /** Returns the library version such as \"" + this.versionVersion + "\" */\n");
            pw.append("    static public String getVersion() { return VERSION; }\n");
            pw.append("    /** Returns a longer library version that includes the timestamp such as \"" + longVersion + "\" */\n");
            pw.append("    static public String getLongVersion() { return LONG_VERSION; }\n");
            pw.append("}");
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
                
    }
    
}
