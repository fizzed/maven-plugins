package com.fizzed.maven.hooks;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.artifact.DependencyResolutionRequiredException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Saves the project's classpath to a file.
 *
 * This Mojo allows you to select the scope (compile, runtime, test)
 * and saves the resulting classpath string to a specified file.
 */
@Mojo(
    name = "classpath",
    defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    requiresDependencyResolution = ResolutionScope.TEST,
    threadSafe = true
)
public class ClasspathMojo extends AbstractMojo {

    /**
     * The Maven project instance. This is injected by Maven.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The scope of the classpath to save.
     * Valid values are "compile", "runtime", or "test".
     */
    @Parameter(property = "classpath.scope", defaultValue = "runtime")
    private String scope;

    /**
     * The path to the file where the classpath will be saved.
     * Defaults to ${project.build.directory}/classpath.txt
     */
    @Parameter(property = "classpath.outputFile", defaultValue = "${project.build.directory}/classpath.txt")
    private File outputFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Saving classpath for scope: " + scope);

        List<String> classpathElements;
        try {
            // Select the correct classpath based on the 'scope' parameter
            switch (scope.toLowerCase()) {
                case "compile":
                    classpathElements = project.getCompileClasspathElements();
                    break;
                case "test":
                    classpathElements = project.getTestClasspathElements();
                    break;
                case "runtime":
                    classpathElements = project.getRuntimeClasspathElements();
                    break;
                default:
                    throw new MojoExecutionException("Invalid scope: " + scope + ". Must be one of 'compile', 'runtime', or 'test'.");
            }

        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Dependency resolution failed", e);
        }

        // Create the classpath string, joined by the system path separator (e.g., ":" on Linux, ";" on Windows)
        String classpath = String.join(File.pathSeparator, classpathElements);

        // Ensure the parent directory exists
        try {
            File parentDir = outputFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Could not create parent directory: " + outputFile.getParent(), e);
        }


        // Write the classpath string to the output file
        try {
            Files.write(outputFile.toPath(), classpath.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write classpath file", e);
        }

        getLog().info("Classpath saved to: " + outputFile.getAbsolutePath());
    }
}