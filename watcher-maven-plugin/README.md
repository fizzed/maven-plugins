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
                <version>1.0.7-SNAPSHOT</version>
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
                <artifactId>version-maven-plugin</artifactId>
                <version>1.0.7-SNAPSHOT</version>
                <executions>
                    <execution>
                        <id>generate-version-class</id>
                        <goals>
                            <goal>generate</goal>
                        </goals>
                        <configuration>
                            <javaPackage>com.fizzed.example_project</javaPackage>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
    <!-- other pom elements -->
</project>
```

## Watcher Maven Plugin

Watches one or more directories for changes, and then runs a maven goal if anything changes. Useful for executing test,
running executables, etc. if source code changes.

No docs yet on how to use it, please look at source code.
