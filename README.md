Maven Plugins by Fizzed
=======================================

 - [Fizzed, Inc.](http://fizzed.com)
 - Joe Lauer (Twitter: [@jjlauer](http://twitter.com/jjlauer))

## Overview

Collection of Maven plugins useful for gettin 'er done.


## Watcher (fizzed-watcher-maven-plugin)

Maven plugin that continually "watches" one or more directories for file changes
and runs one or more maven goals (in the same maven session).  Optional active
profiles can be included as well.  Useful for lots of things...

To use add the following to your POM:

    <build>
        <plugins>
            ...
            <plugin>
                <groupId>com.fizzed</groupId>
                <artifactId>fizzed-watcher-maven-plugin</artifactId>
                <version>1.0.4</version>
                <configuration>
                    <files>
                        <param>src/main/java</param>
                        <param>src/main/resources</param>
                    </files>
                    <goals>
                        <param>clean</param>
                        <param>compile</param>
                    </goals>
                    <profiles>
                        <param>profile-to-activate</param>
                    </profiles>
                </configuration>
            </plugin>
            ...
        </plugins>
    </build>


## Versionizer (fizzed-versionizer-maven-plugin)

Maven plugin that generates a Java source file containing artifact
version info. This is compiled and included with the final artifact.
An alternative to using Jar manifest files for extracting version info
from a library. The directory the file is output to is also added to your
project as a directory containing Java sources (and will be automatically
picked up during the compile phase).

To use add the following to your POM:

    <build>
        <plugins>
            ...
            <plugin>
                <groupId>com.fizzed</groupId>
                <artifactId>fizzed-versionizer-maven-plugin</artifactId>
                <version>1.0.4</version>
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

By default this maven plugin does NOT attach to a lifecycle -- since it is 
essentially a daemon that runs forever.  Usually, you'll run this in a separate
shell and run via:

    mvn fizzed-watcher:run


## Play (fizzed-play-maven-plugin)

Maven plugin that does a best-effort compile of PlayFramework 2.x templates
(file.scala.html) into a Java source file.  This plugin is primarily a hack
to make Netbeans function to work on PlayFramework projects using a pom.xml
file.

Templates are generated to ${project.build.directory}/generated-sources/play-templates

To use add the following to your POM:

    <build>
        <plugins>
            ...
            <plugin>
                <groupId>com.fizzed</groupId>
                <artifactId>fizzed-play-maven-plugin</artifactId>
                <version>1.0.4</version>
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
