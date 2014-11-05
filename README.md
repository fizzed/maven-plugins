Maven Plugins by Fizzed
=======================================

## By

 - [Fizzed, Inc.](http://fizzed.co)
 - Joe Lauer (Twitter: [@jjlauer](http://twitter.com/jjlauer))

## Overview

Collection of plugins for Maven builds.

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
                <groupId>co.fizzed</groupId>
                <artifactId>fizzed-versionizer-maven-plugin</artifactId>
                <version>USE-LATEST-HERE</version>
                <executions>
                    <execution>
                        <id>generate-version-class</id>
                        <goals>
                            <goal>generate</goal>
                        </goals>
                        <configuration>
                            <javaPackage>co.fizzed.examples.helloworld</javaPackage>
                        </configuration>
                    </execution>
                </executions> 
            </plugin>
            ...
        </plugins>
    </build>

By default this will generate a Version.java source file in:

    ${project.build.directory}/generated-sources/versionizer

