package net.geraldhofbauer.vanillaplusadditions.modules.options.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * File-level logic for options snapshots: export, restore, list, delete and the rotating
 * automatic backups. Snapshots are plain {@code options.txt}-format text files under
 * {@code config/vanillaplusadditions/options_backups/}; automatic ones carry the
 * {@code auto_} name prefix and are pruned oldest-first.
 */
@OnlyIn(Dist.CLIENT)
public final class OptionsBackupManager {

    /** Name prefix reserved for automatic (rotating) backups. */
    public static final String AUTO_PREFIX = "auto_";

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String KEY_LINE_PREFIX = "key_";

    /** A snapshot on disk. */
    public record BackupInfo(String name, boolean auto, long lastModified) { }

    /** Outcome of a restore: either a full options.txt swap or N applied keybind lines. */
    public record RestoreResult(boolean fullRestore, int keysApplied) { }

    private OptionsBackupManager() { }

    public static Path backupDir() {
        return FMLPaths.CONFIGDIR.get().resolve("vanillaplusadditions").resolve("options_backups");
    }

    private static Path optionsFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("options.txt");
    }

    /** Valid manual backup name: filename-safe and not colliding with the auto_ namespace. */
    public static boolean isValidName(String name) {
        return VALID_NAME.matcher(name).matches() && !name.startsWith(AUTO_PREFIX);
    }

    public static boolean exists(String name) {
        return Files.isRegularFile(backupDir().resolve(name + ".txt"));
    }

    /** All snapshots: manual ones alphabetically first, then automatic ones newest-first. */
    public static List<BackupInfo> listBackups() {
        Path dir = backupDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<BackupInfo> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        name = name.substring(0, name.length() - 4);
                        long modified;
                        try {
                            modified = Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            modified = 0L;
                        }
                        result.add(new BackupInfo(name, name.startsWith(AUTO_PREFIX), modified));
                    });
        } catch (IOException e) {
            return List.of();
        }
        result.sort(Comparator
                .comparing(BackupInfo::auto)
                .thenComparing((BackupInfo b) -> b.auto() ? -b.lastModified() : 0L)
                .thenComparing(BackupInfo::name));
        return result;
    }

    /**
     * Saves the current options under the given (validated) name. Flushes the in-memory options
     * to disk first so the snapshot matches what the player currently sees.
     */
    public static void export(String name, boolean fullOptions) throws IOException {
        writeSnapshot(name, readCurrentScoped(fullOptions));
    }

    /**
     * Restores the named snapshot. Full snapshots (when the module scope allows it) replace
     * {@code options.txt} and reload it in place; keybind-only snapshots — or a keybind-only
     * scope — apply just the {@code key_*} lines to the live {@link KeyMapping}s. A safety
     * {@code auto_&lt;stamp&gt;-prerestore} snapshot of the current state is taken first.
     */
    public static RestoreResult restore(String name, boolean fullOptions, int autoKeep) throws IOException {
        Path file = backupDir().resolve(name + ".txt");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        // Safety net: snapshot the current state before touching anything.
        writeSnapshot(AUTO_PREFIX + LocalDateTime.now().format(STAMP) + "-prerestore",
                readCurrentScoped(true));
        pruneAutoBackups(autoKeep);

        Minecraft mc = Minecraft.getInstance();
        boolean snapshotKeybindsOnly = lines.stream()
                .allMatch(l -> l.isBlank() || l.startsWith(KEY_LINE_PREFIX));

        if (fullOptions && !snapshotKeybindsOnly) {
            Files.write(optionsFile(), lines, StandardCharsets.UTF_8);
            mc.options.load();
            KeyMapping.resetMapping();
            mc.options.save();
            return new RestoreResult(true, 0);
        }

        // Keybinds only: apply key_<mapping name>:<key> lines to the live mappings.
        Map<String, String> keyValues = new HashMap<>();
        for (String line : lines) {
            if (!line.startsWith(KEY_LINE_PREFIX)) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > KEY_LINE_PREFIX.length()) {
                keyValues.put(line.substring(KEY_LINE_PREFIX.length(), colon), line.substring(colon + 1));
            }
        }
        int applied = 0;
        for (KeyMapping mapping : mc.options.keyMappings) {
            String value = keyValues.get(mapping.getName());
            if (value != null && !value.equals(mapping.saveString())) {
                mapping.setKey(InputConstants.getKey(value));
                applied++;
            }
        }
        KeyMapping.resetMapping();
        mc.options.save();
        return new RestoreResult(false, applied);
    }

    public static void delete(String name) throws IOException {
        Files.deleteIfExists(backupDir().resolve(name + ".txt"));
    }

    /**
     * Creates a rotating {@code auto_&lt;stamp&gt;} backup if the current (scoped) options differ
     * from the newest automatic snapshot. Returns the created backup name, or empty if nothing
     * changed.
     */
    public static Optional<String> autoBackupIfChanged(boolean fullOptions, int keep) throws IOException {
        List<String> current = readCurrentScoped(fullOptions);
        Optional<BackupInfo> newestAuto = listBackups().stream()
                .filter(BackupInfo::auto)
                .max(Comparator.comparingLong(BackupInfo::lastModified));
        if (newestAuto.isPresent()) {
            List<String> previous = Files.readAllLines(
                    backupDir().resolve(newestAuto.get().name() + ".txt"), StandardCharsets.UTF_8);
            if (previous.equals(current)) {
                return Optional.empty();
            }
        }
        String name = AUTO_PREFIX + LocalDateTime.now().format(STAMP);
        writeSnapshot(name, current);
        pruneAutoBackups(keep);
        return Optional.of(name);
    }

    /** Current options.txt content, filtered to {@code key_*} lines when scoped to keybinds. */
    private static List<String> readCurrentScoped(boolean fullOptions) throws IOException {
        Minecraft.getInstance().options.save();
        List<String> lines = Files.readAllLines(optionsFile(), StandardCharsets.UTF_8);
        if (!fullOptions) {
            lines = lines.stream().filter(l -> l.startsWith(KEY_LINE_PREFIX)).toList();
        }
        return lines;
    }

    private static void writeSnapshot(String name, List<String> lines) throws IOException {
        Path dir = backupDir();
        Files.createDirectories(dir);
        Files.write(dir.resolve(name + ".txt"), lines, StandardCharsets.UTF_8);
    }

    private static void pruneAutoBackups(int keep) throws IOException {
        List<BackupInfo> autos = listBackups().stream().filter(BackupInfo::auto).toList();
        if (autos.size() <= keep) {
            return;
        }
        // listBackups sorts autos newest-first — everything past `keep` is the oldest surplus.
        for (BackupInfo stale : autos.subList(keep, autos.size())) {
            delete(stale.name());
        }
    }
}
