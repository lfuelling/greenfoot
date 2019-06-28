# Greenfoot

This is a fork of the [official Greenfoot source](https://www.greenfoot.org/site/download_source) which is currently not in git.

Since I need the bluej and greenfoot jar files for [my greenfoot-maven example](https://lerks.blog/making-games-with-greenfoot-without-greenfoot/) and other projects, I decided to have the whole source in git. 
Maybe I'll even implement fully automated updates later.

## Usage

To use this Greenfoot version in Maven, just add the following to your `pom.xml`:

```
  <dependencies>
    <dependency>
      <groupId>org.greenfoot</groupId>
      <artifactId>greenfoot</artifactId>
      <version>3.6.0</version>
    </dependency>
    <dependency>
      <groupId>org.bluej</groupId>
      <artifactId>bluej-core</artifactId>
      <version>greenfoot-3.6.0</version>
    </dependency>
  </dependencies>
```

### Java 8

The last version supporting Java 8 is `3.5.4` (`greenfoot-3.5.4` for bluej). 


## Developing/Building

1. "Open" the project folder using IntelliJ
2. Open the "Ant Build" panel in IntelliJ
3. Click the plus icon in the panel to add `greenfoot/build.xml` and `bluej/build.xml` to the panel
4. Open the "Project Structure" dialog to set the JDK to 11 and language level to 11
5. Edit the `bluej/build.properties.template` file and save it as `bluej/build.properties`
6. Run the `copy-imagelib` ant task of the `greenfoot` project
7. Run the `jar-core` ant task of the `bluej` project
    - The jar will be placed in `bluej/lib/bluejcore.jar`
8. Run the `dist` ant task of the `greenfoot` project
    - The jar will be placed in `greenfoot/package/Greenfoot-core-3.0.jar`
9. Use the files locally or push them into a nexus

## Publishing

1. Update current Greenfoot version in the `.version` file
2. Download the latest Greenfoot source zip
3. Extract everything in `bluej` and `greenfoot` to the folders in this project
4. Replace the sample JAVA_HOME path in `bluej/build.properties.template` with `<JAVA_HOME>`
5. Commit everything and build locally (clean afterwards using the `clean` ant targets ob both greenfoot and bluej)
6. Wait for GitLab CI to publish, fix any bugs if it fails
