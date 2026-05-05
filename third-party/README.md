# Third-Party Downloads

These files are not included in the repository. Download them from the official sources below when running OSCity locally.

## Required

### Paper 1.21.11 build 113

Paper is the Minecraft server software used to run OSCity.

Direct jar download command:

```bash
curl -fL -o paper-1.21.11-113.jar "https://fill-data.papermc.io/v1/objects/d99ef05d75304ca6a7808300354f4bcd7846e015f5c088f218113ed529d1b4f0/paper-1.21.11-113.jar"
```

Download page:

https://fill-ui.papermc.io/projects/paper/version/1.21.11?build=113

Expected jar name:

```text
paper-1.21.11-113.jar
```

### Citizens 2.0.41

Citizens is required for the Kernel Guardian NPC.

Direct jar download command:

```bash
curl -fL -o Citizens-2.0.41-b4122.jar "https://ci.citizensnpcs.co/view/Citizens/job/Citizens2/4122/artifact/dist/target/Citizens-2.0.41-b4122.jar"
```

Download page:

https://ci.citizensnpcs.co/view/Citizens/job/Citizens2/4122/

Expected jar name:

```text
Citizens-2.0.41-b4122.jar
```

## Required For Full Visual Experience

### MapImage 1.3
MapImage is used for the image-based map displays in the OSCity world.

Direct jar download command:

```bash
curl -fL -o mapimage-1.3.jar "https://cdn.modrinth.com/data/P0MLLUxX/versions/W86RyAtp/mapimage-1.3.jar"
```

Download page:

https://modrinth.com/plugin/mapimage

Expected jar name:

```text
mapimage-1.3.jar
```

## Optional

### WorldGuard 7.0.15
WorldGuard is optional for local setup. It can be used for region protection/world restrictions, but the core OSCity plugin is not dependent on it.

https://modrinth.com/plugin/worldguard

Expected jar name:

```text
worldguard-bukkit-7.0.15.jar
```
