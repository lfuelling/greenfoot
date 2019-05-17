# Greenfoot

This is a fork of the [official Greenfoot source](https://www.greenfoot.org/site/download_source) which is currently not in git.

Since I need the bluej and greenfoot jar files for [my greenfoot-maven example](https://lerks.blog/making-games-with-greenfoot-without-greenfoot/) and other projects, I decided to have the whole source in git. 
Maybe I'll even implement fully automated updates later.

## Developing/Building

1. "Open" the project folder using IntelliJ
2. Open the "Ant Build" panel in IntelliJ
3. Click the plus icon in the panel to add `greenfoot/build.xml` and `bluej/build.xml` to the panel
4. Open the "Project Structure" dialog to set the JDK to 1.8 and language level to 8
5. Edit the `bluej/build.properties.template` file and save it as `bluej/build.properties`
6. Run the `copy-imagelib` ant task of the `greenfoot` project
6. Run the `jar-core` ant task of the `bluej` project
  6.1 The jar will be placed in `bluej/lib/bluejcore.jar`
7. Run the `dist` ant task of the `greenfoot` project
  7.1 The jar will be placed in `greenfoot/package/Greenfoot-core-3.0.jar`
8. Use the files locally or push them into a nexus
