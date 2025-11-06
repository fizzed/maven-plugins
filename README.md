# Maven Plugins by Fizzed

## Overview

Collection of Maven plugins for various tasks.

## Sponsorship & Support

![](https://cdn.fizzed.com/github/fizzed-logo-100.png)

Project by [Fizzed, Inc.](http://fizzed.com) (Follow on Twitter: [@fizzed_inc](http://twitter.com/fizzed_inc))

**Developing and maintaining opensource projects requires significant time.** If you find this project useful or need
commercial support, we'd love to chat. Drop us an email at [ping@fizzed.com](mailto:ping@fizzed.com)

Project sponsors may include the following benefits:

- Priority support (outside of Github)
- Feature development & roadmap
- Priority bug fixes
- Privately hosted continuous integration tests for their unique edge or use cases

# Maven Plugins by Fizzed

## Version Maven Plugin

Provides a plugin to generate Java source code which contains a `Version` class with information about the version of
the project, the git commit hash, timestamp, etc.  Useful for generating version information in your application and
including it in the final .jar file, so consumers of your library can see what version they're running.

### Usage

Even if you include the plugin in your project, by default if the `javaPackage` is not specified, it will not generate
any source code.  You must specify the `javaPackage` to generate source code for the Version class, and that property
effectively serves as a way to `skip` the plugin. In your Maven pom.xml, add the following:

```xml
<project>
    <!-- other pom elements -->
    <build>
        <plugins>
            <plugin>
                <groupId>com.fizzed</groupId>
                <artifactId>version-maven-plugin</artifactId>
                <version>2.0.0</version>
                <executions>
                    <execution>
                        <id>generate-version-class</id>
                        <goals>
                            <goal>generate</goal>
                        </goals>
                        <configuration>
                            <javaPackage>com.fizzed.project</javaPackage>
                        </configuration>   
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
    <!-- other pom elements -->
</project>
```

## Hooks Maven Plugin

Provides "hooks" into Maven build process to extract information to be used outside of Maven.  The initial implementation
provides a `classpath` goal that will return the correct classpath list in a file, so that you can use it directly
(e.g. to launch a java process yourself). Basically, this is a better "exec-maven-plugin" where you can run classes
directly.

### Usage

This will compile your project/module, and write the "runtime" classpath to <target>/classpath.txt, which will include
the "classes" directory and all of its jar dependencies.  For mutli-module projects, this will also include any of the
modules it depends on.

    mvn compile com.fizzed:hooks-maven-plugin:1.0.7-SNAPSHOT:classpath -Dclasspath.scope=runtime

Same as above, but for test classes, along with the test classpath.

    mvn test-compile com.fizzed:hooks-maven-plugin:1.0.7-SNAPSHOT:classpath -Dclasspath.scope=test

If you prefer to `package` your project, and get a classpath of the produced jars, this will work. Please note that you
must run `package` first, before running the hook-maven-plugin:classpath goal, otherwise the jar will not exist yet and
you'll end up with the last module's `classes` directory instead of the jar.

    mvn package com.fizzed:hooks-maven-plugin:1.0.7-SNAPSHOT:classpath -Dclasspath.scope=runtime -DskipTests=true

If you want to always build `classpath.txt` files, you can also add this plugin to run all the time in your project.
Add the following to your pom.xml:

```xml
<project>
    <!-- other pom elements -->
    <build>
        <plugins>
            <plugin>
                <groupId>com.fizzed</groupId>
                <artifactId>hooks-maven-plugin</artifactId>
                <version>2.0.0</version>
                <executions>
                    <execution>
                        <id>save-classpath</id>
                        <goals>
                            <goal>classpath</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
    <!-- other pom elements -->
</project>
```

## Watcher Maven Plugin

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
            <artifactId>watcher-maven-plugin</artifactId>
            <version>2.0.0</version>
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
mvn com.fizzed:watcher-maven-plugin:2.0.0:run
```

## License

Copyright (C) 2015+ Fizzed, Inc.

This work is licensed under the Apache License, Version 2.0. See LICENSE for details.