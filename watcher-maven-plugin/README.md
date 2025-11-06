mvn install

Then to run a goal:

mvn -Dfiles=src/main/java,src/main/resources -Dgoals=clean,compile com.fizzed:fizzed-watcher-maven-plugin:1.0.4-SNAPSHOT:run
