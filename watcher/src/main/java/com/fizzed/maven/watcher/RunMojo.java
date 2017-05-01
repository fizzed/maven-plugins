package com.fizzed.maven.watcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.maven.Maven;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
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
import org.codehaus.plexus.util.DirectoryScanner;

/**
 * Utility for watching directories/files and triggering a maven goal.
 *
 * @author joelauer
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class RunMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    protected MavenSession session;
    
    @Parameter(property = "watches", alias = "watcher.watches", required = true)
    protected List<WatchFileSet> watches;

    @Parameter(property = "goals", alias = "watcher.goals", required = true)
    protected List<String> goals;
    
    @Parameter(property = "profiles", alias = "watcher.profiles", required = false)
    protected List<String> profiles;

    @Parameter(property = "properties", alias = "watcher.properties", required = false)
    protected List<String> properties;

    @Parameter(property = "watcher.skipTouch", defaultValue = "false")
    protected boolean skipTouch;
    
    @Parameter(property = "watcher.touchFile", defaultValue = "${project.build.directory}/watcher.txt")
    protected File touchFile;

    @Component
    protected PluginPrefixResolver pluginPrefixResolver;

    @Component
    protected PluginVersionResolver pluginVersionResolver;

    @Component
    protected Maven maven;

    private WatchService watchService;
    private Map<Path, WatchFileSet> configMap;
    private Map<Path, WatchKey> pathMap;
    private Map<WatchKey, Path> watchKeyMap;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.configMap = new HashMap<>();
        this.pathMap = new HashMap<>();
        this.watchKeyMap = new HashMap<>();

        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (Exception e) {
            throw new MojoExecutionException("Unable to create watch service");
        }

        getLog().info("Registering " + watches.size() + " watch sets...");
        
        for (WatchFileSet wfs : watches) {
            getLog().info("Registering watch set: " + wfs);
            
            File dir = new File(wfs.getDirectory());
            if (!dir.exists()) {
                throw new MojoFailureException("Directory " + dir + " does not exist. Unable to watch a dir that does not exist");
            }
            if (!dir.isDirectory()) {
                throw new MojoFailureException("Unable to watch " + dir + " - its not a directory");
            }
            
            // add config for this path
            // maven is somehow garbage collecting my includes value -- create copy instead...
            this.configMap.put(dir.toPath(), wfs);
            
            if (wfs.isRecursive()) {
                this.walkTreeAndSetWatches(dir, null);
            } else {
                this.registerWatch(dir.toPath());
            }
        }

        long longTimeout = 60 * 60 * 24 * 1000L;
        long shortTimeout = 750L;
        long timeout = longTimeout;
        int dueToRunGoal = 0;
        
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
                    if (dueToRunGoal > 0) {
                        MavenExecutionRequest request = DefaultMavenExecutionRequest.copy(session.getRequest());
                        if (this.profiles != null && this.profiles.size() > 0) {
                            request.setActiveProfiles(profiles);
                        }

                        if (this.properties != null && this.properties.size() > 0) {
                            request.setSystemProperties(this.parsePropertiesStringList(this.properties));
                        }

                        request.setGoals(goals);

                        getLog().info("Changed detected. Running command-line equivalent of:");
                        getLog().info(" " + this.buildMavenCommandLineEquivalent());
                        MavenExecutionResult executionResult = maven.execute(request);
                        
                        if (executionResult.hasExceptions()) {
                            getLog().error(("Goal(s) had exceptions, skipping touch file"));
                        }
                        else {
                            // touch file after maven executed its "task" -- which is useful
                            // if other things are waiting for a change and they really just
                            // want to know when the watcher plugin ran again...
                            touchFileIfRequested();
                        }
                    }
                    
                    timeout = longTimeout;
                    dueToRunGoal = 0;
                    continue;
                }
                
                // schedule the goal to run
                timeout = shortTimeout;
                dueToRunGoal++;
                
                Path watchPath = watchKeyMap.get(watchKey);

                List<WatchEvent<?>> pollEvents = watchKey.pollEvents(); // take events, but don't care what they are!
                for (WatchEvent event : pollEvents) {
                    if (event.context() instanceof Path) {
                        // event is always relative to what was watched (e.g. testdir)
                        Path eventPath = (Path) event.context();
                        // resolve relative to path watched (e.g. dir/watched/testdir)
                        Path path = watchPath.resolve(eventPath);
                        
                        File file = path.toFile();
                        String fileOrDir = (file.isDirectory() ? "directory" : "file");
                        
                        // find the assigned watch config so we can see if has includes/excludes
                        WatchFileSet wfs = findWatchFileSet(path);

                        getLog().debug("eventPath: " + eventPath);
                        getLog().debug("watchFileSet: " + wfs);
                        
                        boolean matches = matches(eventPath.toString(), wfs);
                        getLog().debug("Watcher - matches=" + matches);
                        
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            getLog().info("Watcher - " + fileOrDir + " created: " + path);
                            // only schedule new directory to be watched if we're recursive
                            if (file.isDirectory()) {
                                if (wfs.isRecursive()) {
                                    // register this new directory as something to watch
                                    walkTreeAndSetWatches(file, new File(wfs.getDirectory()));
                                }
                                // directories by themselves do not trigger a match
                                matches = false;
                            }
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            getLog().info("Watcher - " + fileOrDir + " deleted: " + path);
                            // need to unregister any stale directories from watching
                            int count = unregisterStaleWatches();
                            if (count > 0 && count == event.count()) {
                                // if stale dirs were removed and it matches events count
                                // then should be safe to ignore it
                                matches = false;
                            }
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            getLog().info("Watcher - " + fileOrDir + " modified: " + path);
                            // only schedule new directory to be watched if we're recursive
                            if (file.isDirectory()) {
                                // directories by themselves do not trigger a match
                                matches = false;
                            }
                        } else if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            getLog().warn("Watcher - some events may have been discarded!!!!");
                            getLog().warn("Ideally, just restart maven to pick it up again");
                        }
                        
                        // if no match then do NOT trigger a change
                        if (!matches) {
                            getLog().info("Change either a dir or did not match includes/excludes (not triggering goals...)");
                            dueToRunGoal--;
                        }
                    }
                }

                watchKey.reset();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }
        }
    }
    
    public void touchFileIfRequested() {
        if (!skipTouch) {
            if (touchFile != null) {
                getLog().info("Touching file " + touchFile);
                try {
                    if (!touchFile.exists()) {
                        File parent = touchFile.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }
                        new FileOutputStream(touchFile).close();
                    }
                    touchFile.setLastModified(System.currentTimeMillis());
                } catch (IOException e) {
                    getLog().debug("Unable to touch file", e);
                }
            }
        }
    }
    
    public String buildMavenCommandLineEquivalent() {
        StringBuilder sb = new StringBuilder();
        sb.append("mvn");
        if (this.profiles != null && this.profiles.size() > 0) {
            for (String p : this.profiles) {
                sb.append(" -P").append(p);
            }
        }
        if (this.goals != null && this.goals.size() > 0) {
            for (String g : this.goals) {
                sb.append(" ").append(g);
            }
        }
        return sb.toString();
    }
    
    public void addWatch(WatchFileSet wfs) {
        if (this.watches == null) {
            this.watches = new ArrayList<>();
        }
        this.watches.add(wfs);
    }
    
    private WatchFileSet findWatchFileSet(Path path) {
        // start from back and work to front
        Path p = path;
        while (p != null) {
            if (this.configMap.containsKey(p)) {
                return this.configMap.get(p);
            }
            p = p.getParent();
        }
        return null;
    }
    
    private boolean matches(String name, WatchFileSet wfs) {
        boolean matches = false;
        
        // if no excludes & no includes then everything matches
        if ((wfs.getIncludes() == null || wfs.getIncludes().isEmpty()) &&
                (wfs.getExcludes() == null || wfs.getExcludes().isEmpty())) {
            matches = true;
        }
        
        // process includes first
        if (wfs.getIncludes() != null && !wfs.getIncludes().isEmpty()) {
            for (String include : wfs.getIncludes()) {
                getLog().debug("Trying to match: include=" + include + " for name " + name);
                if (DirectoryScanner.match(include, name)) {
                    matches = true;
                    break;
                }
            }
        }
        else {
            // no specific includes, everything will be included then
            matches = true;
        }
        
        // process excludes second
        if (wfs.getExcludes() != null) {
            for (String exclude : wfs.getExcludes()) {
                getLog().debug("Trying to match: exclude=" + exclude + " for name " + name);
                if (DirectoryScanner.match(exclude, name)) {
                    matches = false;
                    break;
                }
            }
        }
        
        return matches;
    }

    private void walkTreeAndSetWatches(File dir, File root) {
        try {
            // does the new directory have a root we need to check back towards?
            if (root != null) {
                Path parent = dir.toPath().getParent();
                if (!pathMap.containsKey(parent)) {
                    // safer to just re-walk entire tree
                    walkTreeAndSetWatches(root, null);
                    return;
                }
                // otherwise the new directory already has its parent registered!
            }
            
            Files.walkFileTree(dir.toPath(), new FileVisitor<Path>() {
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

    private int unregisterStaleWatches() {
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
        
        return stalePaths.size();
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

    private Properties parsePropertiesStringList(List<String> stringList) {
        final Properties p = new Properties();
        for (String s : stringList) {
            try {
                p.load(new StringReader(s));
            } catch (IOException e) {
                getLog().error("Properties - unable to read string: " + s);
            }

            getLog().debug("Properties - added to Properties HashTable: " + s);
        }

        return p;
    }
}
