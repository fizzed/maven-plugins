Maven Plugins by Fizzed
=======================================

## By

 - [Fizzed, Inc.](http://fizzed.com)
 - Joe Lauer (Twitter: [@jjlauer](http://twitter.com/jjlauer))

## Overview

Collection of Maven plugins useful for tweaking builds.


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
                <version>USE-LATEST-HERE</version>
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

By default this will generate a Version.java source file in:

    ${project.build.directory}/generated-sources/versionizer


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
                <version>USE-LATEST-HERE</version>
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
