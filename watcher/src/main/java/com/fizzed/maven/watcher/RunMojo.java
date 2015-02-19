package com.fizzed.maven.watcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.maven.Maven;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.prefix.PluginPrefixResolver;
import org.apache.maven.plugin.version.PluginVersionResolver;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Utility for watching directories/files and triggering a maven goal.
 *
 * @author joelauer
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class RunMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    protected MavenSession session;

    @Parameter(property = "files", alias = "watcher.files", required = true)
    protected List<String> files;

    @Parameter(property = "goals", alias = "watcher.goals", required = true)
    protected List<String> goals;
    
    @Parameter(property = "profiles", alias = "watcher.profiles", required = false)
    protected List<String> profiles;

    @Component
    protected PluginPrefixResolver pluginPrefixResolver;

    @Component
    protected PluginVersionResolver pluginVersionResolver;

    @Component
    protected Maven maven;

    private WatchService watchService;
    private Map<Path, WatchKey> pathMap;
    private Map<WatchKey, Path> watchKeyMap;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.pathMap = new HashMap<>();
        this.watchKeyMap = new HashMap<>();

        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (Exception e) {
            throw new MojoExecutionException("Unable to create watch service");
        }

        getLog().info("Recursively registering " + files.size() + " dirs...");
        for (String s : files) {
            File f = new File(s);
            if (f.isFile()) {
                this.registerWatch(f.toPath());
            } else {
                this.walkTreeAndSetWatches(f);
            }
        }

        long longTimeout = 60 * 60 * 24 * 1000L;
        long shortTimeout = 500L;
        long timeout = longTimeout;
        boolean dueToRunGoal = false;
        
        while (true) {
            try {
                
                if (timeout > shortTimeout) {
                    getLog().info("Watcher - waiting for changes...");
                }
                
                // timeout to poll for (this way we can let lots of quick changes
                // take place -- and only run the goal when things settles down)
                WatchKey watchKey = watchService.poll(timeout, TimeUnit.MILLISECONDS);
                if (watchKey == null) {
                    // timeout occurred!
                    if (dueToRunGoal) {
                        MavenExecutionRequest request = DefaultMavenExecutionRequest.copy(session.getRequest());
                        if (this.profiles != null && this.profiles.size() > 0) {
                            request.setActiveProfiles(profiles);
                        }
                        request.setGoals(goals);
                        maven.execute(request);
                    }
                    
                    timeout = longTimeout;
                    dueToRunGoal = false;
                    continue;
                }
                
                // schedule the goal to run
                timeout = shortTimeout;
                dueToRunGoal = true;
                
                Path watchPath = watchKeyMap.get(watchKey);

                List<WatchEvent<?>> pollEvents = watchKey.pollEvents(); // take events, but don't care what they are!
                for (WatchEvent event : pollEvents) {
                    if (event.context() instanceof Path) {
                        Path eventPath = (Path) event.context();
                        Path path = watchPath.resolve(eventPath);
                        File file = path.toFile();
                        String fileOrDir = (file.isDirectory() ? "directory" : "file");

                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            getLog().info("Watcher - " + fileOrDir + " created: " + path);
                            if (file.isDirectory()) {
                                // need to register this new directory as something to watch
                                walkTreeAndSetWatches(file);
                            }
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            getLog().info("Watcher - " + fileOrDir + " deleted: " + path);
                            // need to unregister any stale directories from watching
                            unregisterStaleWatches();
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            getLog().info("Watcher - " + fileOrDir + " modified: " + path);
                        } else if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            getLog().warn("Watcher - some events may have been discarded!!!!");
                            getLog().warn("Ideally, just restart maven to pick it up again");
                        }
                    }
                }

                watchKey.reset();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }
        }
    }

    private void walkTreeAndSetWatches(File file) {
        try {
            Files.walkFileTree(file.toPath(), new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    registerWatch(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Don't care
        }
    }

    private void unregisterStaleWatches() {
        Set<Path> paths = new HashSet<>(pathMap.keySet());
        Set<Path> stalePaths = new HashSet<>();

        for (Path path : paths) {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                stalePaths.add(path);
            }
        }

        if (stalePaths.size() > 0) {
            //logger.log(Level.INFO, "Cancelling stale path watches ...");
            for (Path stalePath : stalePaths) {
                unregisterWatch(stalePath);
            }
        }
    }

    private void registerWatch(Path dir) {
        if (!pathMap.containsKey(dir)) {
            getLog().info("Watcher - registering watch on dir: " + dir);
            try {
                WatchKey watchKey = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW);
                // reverse maps...
                pathMap.put(dir, watchKey);
                watchKeyMap.put(watchKey, dir);
            } catch (IOException e) {
                // don't care!
            }
        }
    }

    private void unregisterWatch(Path dir) {
        WatchKey watchKey = pathMap.get(dir);
        if (watchKey != null) {
            getLog().info("Watcher - unregistering watch on dir: " + dir);
            watchKey.cancel();
            pathMap.remove(dir);
            watchKeyMap.remove(watchKey);
        }
    }
}
