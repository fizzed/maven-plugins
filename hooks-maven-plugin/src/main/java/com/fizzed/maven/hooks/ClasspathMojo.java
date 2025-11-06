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
import org.apache.maven.artifact.Artifact;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * Saves the project's classpath to a file.
 *
 * This Mojo allows you to select the scope (compile, runtime, test)
 * and saves the resulting classpath string to a specified file.
 * It intelligently adds the .jar file if the 'package' phase has run,
 * otherwise it falls back to the 'target/classes' directory.
 */
@Mojo(
    name = "classpath",
    defaultPhase = LifecyclePhase.COMPILE,
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
     * A list of all projects in the current reactor build.
     */
    @Parameter(defaultValue = "${reactorProjects}", readonly = true, required = true)
    private List<MavenProject> reactorProjects;

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

        // Use LinkedHashSet to preserve order and avoid duplicates
        // We will build two separate lists and combine them
        Set<String> reactorPaths = new LinkedHashSet<>();
        Set<String> dependencyPaths = new LinkedHashSet<>();

        List<String> baseClasspathElements;
        try {
            // Select the correct base classpath list from Maven
            switch (scope.toLowerCase()) {
                case "compile":
                    baseClasspathElements = project.getCompileClasspathElements();
                    // Add current project's output
                    addProjectOutputToClasspath(project, reactorPaths, false);
                    break;
                case "test":
                    baseClasspathElements = project.getTestClasspathElements();
                    // Add current project's test/main output directories FIRST
                    addProjectOutputToClasspath(project, reactorPaths, true);
                    break;
                case "runtime":
                    baseClasspathElements = project.getRuntimeClasspathElements();
                    // Add current project's output
                    addProjectOutputToClasspath(project, reactorPaths, false);
                    break;
                default:
                    throw new MojoExecutionException("Invalid scope: " + scope + ". Must be one of 'compile', 'runtime', or 'test'.");
            }
            // Add all other elements to the dependency list
            dependencyPaths.addAll(baseClasspathElements);

        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Dependency resolution failed", e);
        }

        // --- FIX for classpath duplication ---
        // The baseClasspathElements (e.g., from getRuntimeClasspathElements)
        // often contains the target/classes directory for the *current* project.
        // We must remove it here to prevent duplication, since
        // addProjectOutputToClasspath() adds the correct JAR or classes path.
        dependencyPaths.remove(project.getBuild().getOutputDirectory());
        dependencyPaths.remove(project.getBuild().getTestOutputDirectory());
        // --- END FIX ---

        // --- FIX for multi-module reactor builds ---
        // Manually find all reactor dependencies and add their output paths
        // to the high-priority 'reactorPaths' list.

        // Create a quick-lookup map of projects in the reactor
        Set<String> reactorGAVs = reactorProjects.stream()
            .map(p -> p.getGroupId() + ":" + p.getArtifactId() + ":" + p.getVersion())
            .collect(Collectors.toSet());

        // Get all dependency artifacts for the current project
        Set<Artifact> dependencies = project.getArtifacts();

        for (Artifact dep : dependencies) {
            String gav = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion();

            // Check if this dependency is one of the modules in this build
            if (reactorGAVs.contains(gav)) {
                // Find the corresponding MavenProject object
                for (MavenProject reactorProject : reactorProjects) {
                    if (reactorProject.getGroupId().equals(dep.getGroupId()) &&
                        reactorProject.getArtifactId().equals(dep.getArtifactId()) &&
                        reactorProject.getVersion().equals(dep.getVersion())) {

                        getLog().debug("Found reactor dependency: " + gav);

                        // Check if this is a test dependency
                        boolean isTestDependency = "test".equalsIgnoreCase(dep.getScope()) || "test".equalsIgnoreCase(scope);

                        // Add the reactor project's output (JAR or classes) to reactorPaths
                        addProjectOutputToClasspath(reactorProject, reactorPaths, isTestDependency);

                        // Remove from dependencyPaths to avoid duplication
                        dependencyPaths.remove(reactorProject.getBuild().getOutputDirectory());
                        dependencyPaths.remove(reactorProject.getBuild().getTestOutputDirectory());
                        if (reactorProject.getArtifact().getFile() != null) {
                            dependencyPaths.remove(reactorProject.getArtifact().getFile().getAbsolutePath());
                        }

                        break;
                    }
                }
            }
        }
        // --- END FIX ---

        // Now, combine the two lists: reactor paths first, then all other dependencies.
        // There are still possible duplicates, we'll remove them now
        Set<String> finalClasspath = new LinkedHashSet<>();
        finalClasspath.addAll(reactorPaths);
        finalClasspath.addAll(dependencyPaths);

        // Remove any empty entries
        finalClasspath.removeIf(String::isEmpty);

        // Create the classpath string, joined by the system path separator (e.g., ":" on Linux, ";" on Windows)
        String classpath = String.join("\n", finalClasspath);
        classpath += "\n";

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

    /**
     * Helper method to add the correct output (JAR or classes directory) to the classpath.
     *
     * @param proj         The project to get output from.
     * @param pathSet      The set of paths to add to.
     * @param isTestScope  Whether the test-classes directory should also be added.
     */
    private void addProjectOutputToClasspath(MavenProject proj, Set<String> pathSet, boolean isTestScope) {
        // Test output directory is always added first if in test scope
        if (isTestScope) {
            pathSet.add(proj.getBuild().getTestOutputDirectory());
        }

        // Check if the packaged artifact file (JAR) exists
        File artifactFile = proj.getArtifact().getFile();

        if (artifactFile != null && artifactFile.isFile()) {
            // If JAR exists, add it
            pathSet.add(artifactFile.getAbsolutePath());
            getLog().debug("Added JAR path: " + artifactFile.getAbsolutePath());
        } else {
            // Otherwise, fall back to the classes directory
            pathSet.add(proj.getBuild().getOutputDirectory());
            getLog().debug("Added classes directory: " + proj.getBuild().getOutputDirectory());
        }
    }
}