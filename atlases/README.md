# Atlas definitions, one part per module

Minecraft reads an atlas definition from exactly one path per pack:
`assets/minecraft/atlases/<atlas>.json` (`SpriteSourceList.load` derives it from the atlas id). Two
modules that both add sprite sources therefore have to share that one file. That breaks the
standalone jars: if a jar's file names a sprite-source type that no module in that jar registers, the
whole file fails to parse and every entry in it is lost, including the ones that would have worked.

So each module keeps its own part here, `atlases/<atlas>/<moduleId>.json`, and the build assembles:

* the bundle jar: all parts of an atlas merged into `assets/minecraft/atlases/<atlas>.json`;
* each standalone module jar: only its own part, under the same path.

See `generateAtlases` in `build.gradle`. Never put a file under `src/main/resources/assets/minecraft/atlases/`.
