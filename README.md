# OSCity

OSCity is an educational Minecraft Java Edition world and Paper plugin for learning operating-system virtual memory concepts. It turns TLB lookup, page tables, page faults, swapping, lazy loading, lazy allocation, and copy-on-write into an interactive room-based experience guided by the Kernel Guardian NPC.

## Repository Contents

This repository contains:

- Java plugin code in `src/main/java`
- Plugin resources in `src/main/resources`
- Automated tests in `src/test/java`
- Gradle build files
- Image assets in `assets`
- Third-party download notes in `third-party`
- Example server configuration in `server-config`
- The OSCity world package in `worlds/OSCityWorld.zip`

The full playable experience requires the OSCity world because gameplay depends on pre-built rooms, signs, buttons, chests, item frames, maps, and configured coordinates.

## Local Setup

This section is only needed for local reproduction or development.

Requirements:

- Java 21
- Minecraft Java Edition `1.21.11`
- Paper `paper-1.21.11-113.jar`
- Citizens `2.0.41`
- MapImage `1.3` for the image-based map displays
- `OSCityWorld.zip`

Download links for Paper, Citizens, MapImage, and optional WorldGuard are listed in `third-party/README.md`.

## Server Folder Setup

1. Create a Paper server folder and enter it:

   ```bash
   mkdir PaperServer
   cd PaperServer
   ```

2. Clone this repository inside the server folder:

   ```bash
   git clone https://github.com/talax16/OSCity_Submission.git OSCity
   ```

3. Check `OSCity/third-party/README.md` and download the required third-party jars from the listed link or with the provided command.

4. Place `paper-1.21.11-113.jar` directly inside `PaperServer`.

5. Create the plugins folder:

   ```bash
   mkdir -p plugins
   ```

6. Download Citizens and MapImage, then place both jars inside `plugins/`:

   ```text
   PaperServer/plugins/
     Citizens-2.0.41-b4122.jar
     mapimage-1.3.jar
   ```

7. WorldGuard is optional. If using it locally, place `worldguard-bukkit-7.0.15.jar` inside `plugins/`.

8. Extract the world zip into `PaperServer`:

   ```bash
   unzip OSCity/worlds/OSCityWorld.zip
   ```

   The structure should look like:

   ```text
   PaperServer/
     OSCity/
     OSCityWorld/
     plugins/
     paper-1.21.11-113.jar
   ```

   `OSCityWorld` must be directly inside the server folder. If it is left inside `OSCity/worlds/`, Paper will create a new empty world instead.

9. Copy the example server properties:

   ```bash
   cp OSCity/server-config/server.properties.example server.properties
   ```

10. Copy the MapImage config:

   ```bash
   mkdir -p plugins/MapImage
   cp OSCity/server-config/MapImage/config.yml plugins/MapImage/config.yml
   ```

11. If using WorldGuard locally, copy the provided WorldGuard config:

   ```bash
   mkdir -p plugins/WorldGuard/worlds/OSCityWorld
   cp OSCity/server-config/WorldGuard/worlds/OSCityWorld/config.yml plugins/WorldGuard/worlds/OSCityWorld/config.yml
   cp OSCity/server-config/WorldGuard/worlds/OSCityWorld/regions.yml plugins/WorldGuard/worlds/OSCityWorld/regions.yml
   ```

12. Build OSCity from this repository:

   ```bash
   cd OSCity
   ./gradlew build
   ```

13. Copy the built OSCity plugin jar into the server plugins folder:

   ```bash
   cp build/libs/OSCity-1.0-SNAPSHOT.jar ../plugins/
   cd ..
   ```

14. Accept the Minecraft EULA:

    ```bash
    echo "eula=true" > eula.txt
    ```

15. Start the server:

    ```bash
    java -jar paper-1.21.11-113.jar
    ```

16. Join the server from Minecraft Java Edition `1.21.11`.

## World Name

The plugin expects the world to be named:

```text
OSCityWorld
```

Most room coordinates, buttons, signs, chests, NPC positions, and map locations in `src/main/resources/config.yml` reference this world name. Renaming the world requires updating the configuration.

## Running Tests

To run the automated tests:

```bash
./gradlew test
```

## Build Output

The plugin jar is generated at:

```text
build/libs/OSCity-1.0-SNAPSHOT.jar
```

The plugin uses SQLite for local progress/session data. The Gradle build packages the SQLite JDBC runtime dependency into the generated OSCity jar, so a clean local Paper server only needs the built plugin jar in the `plugins/` folder.

Generated folders such as `build/`, `.gradle/`, and `bin/` are not part of the source submission.

## Main Files

- `src/main/java/com/oscity/OSCity.java` is the plugin entry point.
- `src/main/resources/plugin.yml` defines plugin metadata and commands.
- `src/main/resources/config.yml` defines room coordinates, buttons, signs, chests, doors, and world locations.
- `src/main/resources/dialogue.yml` contains Kernel Guardian dialogue.
- `src/main/resources/questions.yml` contains quiz questions.
- `src/main/resources/messages.yml` contains reusable player-facing messages.
- `src/main/resources/achievements.yml` contains achievement display text.
- `server-config/server.properties.example` contains a minimal local server template.
- `server-config/MapImage/config.yml` contains the MapImage map display configuration.

## Command

The plugin includes one optional player command:

```text
/progress
```

This shows achievement progress and session statistics. The same progress information can also be accessed through the Kernel Guardian NPC.

## Notes

The plugin source can compile on its own, but the full playable experience requires the provided `OSCityWorld` world and the runtime plugins listed above.
