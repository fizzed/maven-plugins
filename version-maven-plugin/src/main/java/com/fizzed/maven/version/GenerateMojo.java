package com.fizzed.maven.version;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
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
    @Parameter(property = "outputDirectory", alias = "versionizer.outputDirectory", defaultValue = "${project.build.directory}/generated-sources/version", required = true)
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
            getLog().info("Version creating generated source directory: " + outputDirectory);
            outputDirectory.mkdirs();
        }
        
        // add the output directory to sources that will be compiled
        this.project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        getLog().info("Version generated source directory: " + outputDirectory + " added to project" );
    
        // convert javaPackage into directory name
        String javaPackagePath = this.javaPackage.replace('.', File.separatorChar);
        getLog().debug("Version java package converted to path: " + javaPackagePath);
        File javaPackageDir = new File(outputDirectory, javaPackagePath);
        javaPackageDir.mkdirs();
        getLog().debug("Version java package dir: " + javaPackageDir);
        
        File javaClassFile = new File(javaPackageDir, this.className + ".java");
        getLog().info("Version generating version java source: " + javaClassFile);

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
        StringBuilder sb = new StringBuilder();
        sb.append("/**\n");
        sb.append(" * DO NOT EDIT THIS FILE (auto generated by com.fizzed:version-maven-plugin)\n");
        sb.append(" */\n");
        sb.append("package ").append(this.javaPackage).append(";\n");
        sb.append("public final class ").append(this.className).append(" {\n");
        sb.append("    private static final String COMMIT=\"").append(this.versionCommit).append("\";\n");
        sb.append("    private static final String TIMESTAMP=\"").append(defaultTimestampStr).append("\";\n");
        sb.append("    private static final String VERSION=\"").append(this.versionVersion).append("\";\n");
        sb.append("    private static final String NAME=\"").append(this.versionName).append("\";\n");
        sb.append("    private static final String VENDOR=\"").append(this.versionVendor).append("\";\n");
        sb.append("    private static final String LONG_VERSION=\"").append(longVersion).append("\";\n");
        sb.append("    /** Returns the library source control commit tag such as \"ac486420\" */\n");
        sb.append("    static public String getCommit() { return COMMIT; }\n");
        sb.append("    /** Returns the library vendor such as \"").append(this.versionVendor).append("\" */\n");
        sb.append("    static public String getVendor() { return VENDOR; }\n");
        sb.append("    /** Returns the library build timestamp (date) such as \"").append(defaultTimestampStr).append("\" */\n");
        sb.append("    static public String getTimestamp() { return TIMESTAMP; }\n");
        sb.append("    /** Returns the library name such as \"").append(this.versionName).append("\" */\n");
        sb.append("    static public String getName() { return NAME; }\n");
        sb.append("    /** Returns the library version such as \"").append(this.versionVersion).append("\" */\n");
        sb.append("    static public String getVersion() { return VERSION; }\n");
        sb.append("    /** Returns a longer library version that includes the timestamp such as \"").append(longVersion).append("\" */\n");
        sb.append("    static public String getLongVersion() { return LONG_VERSION; }\n");
        sb.append("}");

        // write it out quick
        try {
            byte[] b = sb.toString().getBytes(StandardCharsets.UTF_8);
            Files.write(javaClassFile.toPath(), b, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
    
}
