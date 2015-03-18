Maven Plugins by Fizzed [![Build Status](https://travis-ci.org/fizzed/maven-plugins.svg)](https://travis-ci.org/fizzed/java-maven-plugins)
=======================================

 - [Fizzed, Inc.](http://fizzed.com)
 - Joe Lauer (Twitter: [@jjlauer](http://twitter.com/jjlauer))

## Overview

Collection of Maven plugins useful for gettin 'er done.


## Watcher (fizzed-watcher-maven-plugin)

Ever wish Maven could run a specific command if any files in your project change? Some IDEs
have their own features to do X if a file changes, but they usually don't take your full Maven
project file into account. Problem finally solved with the Watcher Plugin for Maven. Add to your
Maven project file and fire it up in a new shell. Maven will continuously and recursively
watch any number of configured directories and then execute a series of goals if a file change
is detected. Just like if you typed it on the command-line! We use it at Fizzed across a bunch
of projects, but mainly our web projects -- where we want a full compile run any time our Java
code, resources, or templates change. Works especially well from a project parent to watch any
of your sub modules too.

To assist in using this plugin to trigger other workflow/plugins, v1.0.6 added
a feature to "touch" a file upon successful execution of the maven goal(s).

To use add the following to your POM:

```xml
<build>
    <plugins>
        ...
        <plugin>
            <groupId>com.fizzed</groupId>
            <artifactId>fizzed-watcher-maven-plugin</artifactId>
            <version>1.0.6</version>
            <configuration>
                <touchFile>target/classes/watcher.txt</touchFile>
                <watches>
                    <watch>
                        <directory>core/src/main/java</directory>
                    </watch>
                    <watch>
                        <directory>ninja/src/main/java</directory>
                    </watch>
                </watches>
                <goals>
                    <goal>compile</goal>
                    <goal>process-classes</goal>
                </goals>
                <profiles>
                    <profile>optional-profile-to-activate</profile>
                </profiles>
            </configuration>
        </plugin>
        ...
    </plugins>
</build>
```

Each watch entry may also contain include and exclude properties as well as
enabling/disabling of recursively watching a directory.  Here is an example of
watching a directory, but excluding files with a suffix of *.html.

```xml
<watch>
    <directory>src/main/java</directory>
    <exclude>*.html</exclude>
</watch>
```

You may add any number of exclude and include entries.  The recursive property
can be set to true/false to disable/enable recursively watching a directory.

By default this maven plugin does NOT attach to a lifecycle -- since it is 
essentially a daemon that runs forever.  Usually, you'll run this in a separate
shell and run via:

```bash
mvn fizzed-watcher:run
```

## Versionizer (fizzed-versionizer-maven-plugin)

Maven plugin that generates a Java source file containing artifact
version info. This is compiled and included with the final artifact.
An alternative to using Jar manifest files for extracting version info
from a library. The directory the file is output to is also added to your
project as a directory containing Java sources (and will be automatically
picked up during the compile phase).

To use add the following to your POM:

```xml
<build>
    <plugins>
        ...
        <plugin>
            <groupId>com.fizzed</groupId>
            <artifactId>fizzed-versionizer-maven-plugin</artifactId>
            <version>1.0.6</version>
            <executions>
                <execution>
                    <id>generate-version-class</id>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                    <configuration>
                        <javaPackage>com.fizzed.examples.helloworld</javaPackage>
                    </configuration>
                </execution>
            </executions> 
        </plugin>
        ...
    </plugins>
</build>
```

By default this will generate a Version.java source file in:

    ${project.build.directory}/generated-sources/versionizer


## Play (fizzed-play-maven-plugin)

Maven plugin that does a best-effort compile of PlayFramework 2.x templates
(file.scala.html) into a Java source file.  This plugin is primarily a hack
to make Netbeans function to code complete PlayFramework projects using a pom.xml
file.

Templates are generated to ${project.build.directory}/generated-sources/play-templates

To use add the following to your POM:

```xml
<build>
    <plugins>
        ...
        <plugin>
            <groupId>com.fizzed</groupId>
            <artifactId>fizzed-play-maven-plugin</artifactId>
            <version>1.0.6</version>
            <executions>
                <execution>
                    <id>best-effort-play-template-compiler</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>template-compile</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
        ...
    </plugins>
</build>
```