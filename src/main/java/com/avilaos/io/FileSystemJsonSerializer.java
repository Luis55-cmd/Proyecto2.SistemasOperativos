package com.avilaos.io;

import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.disk.DiskBlock;
import com.avilaos.disk.SimulatedDisk;
import com.avilaos.filesystem.FSNode;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FileSystemManager;
import com.avilaos.filesystem.FsNodeType;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Lector y escritor del JSON de Estado del Sistema de Archivos (Issue #11).
 * Utiliza {@link JsonReader} y {@link JsonWriter} en streaming (cero colecciones de java.util).
 * Valida completamente la estructura y los bloques antes de aplicar el resultado.
 */
public final class FileSystemJsonSerializer {

    public record EntrySpec(
            String path,
            FsNodeType type,
            int startBlock,
            int blocks,
            String owner,
            FilePermission permission,
            MyLinkedList<Integer> explicitChain
    ) { }

    public record LoadedFileSystem(
            int totalBlocks,
            int initialHead,
            SimulatedDisk disk,
            FileSystemManager fileSystemManager
    ) { }

    private FileSystemJsonSerializer() {
    }

    /**
     * Carga y valida el JSON del Sistema de Archivos desde un {@link String}.
     * Si algún campo o bloque es inválido, lanza {@link IllegalArgumentException}
     * sin alterar ningún estado previo.
     */
    public static LoadedFileSystem loadFromString(String json, int swapSizeBlocks) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("El JSON del sistema de archivos está vacío.");
        }
        int totalBlocks = -1;
        int initialHead = -1;
        Integer currentHead = null;
        boolean hasDiskConfig = false;
        boolean hasFileSystem = false;
        MyLinkedList<EntrySpec> entries = new MyLinkedList<>();

        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String rootKey = reader.nextName();
                if ("diskConfig".equals(rootKey)) {
                    hasDiskConfig = true;
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String k = reader.nextName();
                        if ("totalBlocks".equals(k)) {
                            totalBlocks = reader.nextInt();
                        } else if ("initialHead".equals(k)) {
                            initialHead = reader.nextInt();
                        } else if ("currentHead".equals(k)) {
                            currentHead = reader.nextInt();
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                } else if ("fileSystem".equals(rootKey)) {
                    hasFileSystem = true;
                    reader.beginArray();
                    while (reader.hasNext()) {
                        entries.append(readEntry(reader));
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        } catch (IOException | IllegalStateException | NumberFormatException ex) {
            throw new IllegalArgumentException("JSON del sistema de archivos mal formado: " + ex.getMessage(), ex);
        }

        if (!hasDiskConfig || !hasFileSystem) {
            throw new IllegalArgumentException("El JSON debe incluir las secciones 'diskConfig' y 'fileSystem'.");
        }
        if (totalBlocks <= 0) {
            throw new IllegalArgumentException("diskConfig.totalBlocks debe ser mayor que 0.");
        }
        if (initialHead < 0 || initialHead >= totalBlocks) {
            throw new IllegalArgumentException("diskConfig.initialHead fuera de rango [0, " + (totalBlocks - 1) + "].");
        }

        int effectiveSwap = Math.max(0, swapSizeBlocks);
        int restoredHead = currentHead != null ? currentHead : initialHead;
        if (restoredHead < 0 || (long) restoredHead >= (long) totalBlocks + effectiveSwap) {
            throw new IllegalArgumentException("diskConfig.currentHead fuera del disco físico para el swap configurado.");
        }
        SimulatedDisk disk = new SimulatedDisk(totalBlocks, effectiveSwap, restoredHead);
        FileSystemManager fsManager = new FileSystemManager(disk);

        // No omitir entradas duplicadas: podrían ocultar permisos o bloques distintos.
        for (int i = 0; i < entries.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (entries.get(i).path().equals(entries.get(j).path()))
                    throw new IllegalArgumentException("Ruta duplicada en fileSystem: " + entries.get(i).path());
            }
        }

        // Si el padre está declarado más adelante, usar sus metadatos explícitos.
        for (int i = 0; i < entries.size(); i++) {
            EntrySpec e = entries.get(i);
            if (e.type() == FsNodeType.DIRECTORY) {
                ensureDirectory(fsManager, e.path(), e.owner(), entries);
            }
        }

        // Pase 2: Crear archivos asegurando que el directorio contenedor exista
        for (int i = 0; i < entries.size(); i++) {
            EntrySpec e = entries.get(i);
            if (e.type() != FsNodeType.DIRECTORY) {
                ensureDirectory(fsManager, FileSystemManager.parentOf(e.path()), e.owner(), entries);
                if (e.blocks() <= 0) {
                    throw new IllegalArgumentException("El archivo '" + e.path() + "' debe tener blocks >= 1.");
                }
                if (e.startBlock() < 0 || e.startBlock() >= totalBlocks) {
                    throw new IllegalArgumentException("startBlock fuera de rango en '" + e.path() + "': " + e.startBlock());
                }
                if (e.explicitChain() != null && e.explicitChain().size() != e.blocks()) {
                    throw new IllegalArgumentException("La longitud de 'chain' no coincide con blocks en " + e.path());
                }
                if (e.explicitChain() != null) {
                    allocateExplicitChain(disk, fsManager, e);
                } else {
                    fsManager.createFileAtStartBlock(
                            e.path(), e.startBlock(), e.blocks(), e.owner(), e.permission(), "JSON");
                }
            }
        }

        return new LoadedFileSystem(totalBlocks, restoredHead, disk, fsManager);
    }

    public static LoadedFileSystem loadFromFile(File file, int swapSizeBlocks) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Archivo JSON del sistema de archivos inexistente.");
        }
        StringBuilder sb = new StringBuilder();
        try (FileReader fr = new FileReader(file, StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int n;
            while ((n = fr.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        return loadFromString(sb.toString(), swapSizeBlocks);
    }

    /**
     * Serializa el estado actual del disco y del sistema de archivos al formato JSON oficial.
     */
    public static String toJsonString(SimulatedDisk disk, FileSystemManager fsManager) {
        if (disk == null || fsManager == null) {
            throw new IllegalArgumentException("Disco y FileSystemManager requeridos para exportar JSON.");
        }
        StringWriter sw = new StringWriter();
        try (JsonWriter writer = new JsonWriter(sw)) {
            writer.setIndent("  ");
            writer.beginObject();

            writer.name("diskConfig").beginObject();
            writer.name("totalBlocks").value(disk.getTotalBlocks());
            int head = disk.getHeadPosition();
            // initialHead conserva el formato oficial, limitado al área de datos.
            // currentHead permite recuperar una posición física situada en swap.
            writer.name("initialHead").value(Math.min(head, disk.getTotalBlocks() - 1));
            if (head >= disk.getTotalBlocks()) writer.name("currentHead").value(head);
            writer.endObject();

            writer.name("fileSystem").beginArray();
            MyLinkedList<FSNode> nodes = fsManager.getAllNodesPreOrder();
            for (int i = 0; i < nodes.size(); i++) {
                FSNode node = nodes.get(i);
                writer.beginObject();
                writer.name("path").value(node.getPath());
                writer.name("type").value(node.getType().name());
                if (node.isDirectory()) writer.name("startBlock").value(node.getFirstBlock());
                if (node.isFile()) {
                    writer.name("startBlock").value(node.getFirstBlock());
                    writer.name("blocks").value(node.getSizeBlocks());
                    MyLinkedList<Integer> chain = disk.getChainBlocks(node.getFirstBlock());
                    if (chain.size() > 1) {
                        writer.name("chain").beginArray();
                        for (int k = 0; k < chain.size(); k++) {
                            writer.value(chain.get(k));
                        }
                        writer.endArray();
                    }
                }
                writer.name("owner").value(node.getOwner());
                writer.name("permissions").value(node.getPermission().name().toLowerCase());
                writer.endObject();
            }
            writer.endArray();

            writer.endObject();
        } catch (IOException ex) {
            throw new IllegalStateException("Error serializando sistema de archivos: " + ex.getMessage(), ex);
        }
        return sw.toString();
    }

    public static void saveToFile(File file, SimulatedDisk disk, FileSystemManager fsManager) throws IOException {
        String json = toJsonString(disk, fsManager);
        Path destination = file.toPath().toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(), ".avilaos-", ".json.tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static EntrySpec readEntry(JsonReader reader) throws IOException {
        String path = null;
        String typeStr = null;
        int startBlock = -1;
        int blocks = 0;
        String owner = "admin";
        String permStr = "public";
        MyLinkedList<Integer> chain = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String k = reader.nextName();
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
                continue;
            }
            switch (k) {
                case "path" -> path = reader.nextString();
                case "type" -> typeStr = reader.nextString();
                case "startBlock" -> startBlock = reader.nextInt();
                case "blocks" -> blocks = reader.nextInt();
                case "owner" -> owner = reader.nextString();
                case "permissions" -> permStr = reader.nextString();
                case "chain" -> {
                    chain = new MyLinkedList<>();
                    reader.beginArray();
                    while (reader.hasNext()) {
                        chain.append(reader.nextInt());
                    }
                    reader.endArray();
                }
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        if (path == null || !path.startsWith("/") || path.length() < 2) {
            throw new IllegalArgumentException("Ruta inválida en fileSystem: " + path);
        }
        if (typeStr == null) {
            throw new IllegalArgumentException("Falta 'type' en la entrada: " + path);
        }
        FsNodeType type;
        try {
            type = FsNodeType.valueOf(typeStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Tipo de nodo inválido en '" + path + "': " + typeStr);
        }
        FilePermission perm = parsePermission(permStr, path);
        if (owner == null || owner.trim().isEmpty()) {
            throw new IllegalArgumentException("Falta 'owner' en '" + path + "'.");
        }
        return new EntrySpec(path.trim(), type, startBlock, blocks, owner.trim(), perm, chain);
    }

    private static void ensureDirectory(FileSystemManager fsManager, String path, String fallbackOwner,
                                        MyLinkedList<EntrySpec> entries) {
        if ("/".equals(path)) return;
        var existing = fsManager.findNode(path);
        if (existing != null) {
            if (!existing.isDirectory()) throw new IllegalArgumentException("No es directorio: " + path);
            return;
        }
        EntrySpec declared = null;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).path().equals(path)) { declared = entries.get(i); break; }
        }
        if (declared != null && declared.type() != FsNodeType.DIRECTORY)
            throw new IllegalArgumentException("La ruta padre no es directorio: " + path);
        ensureDirectory(fsManager, FileSystemManager.parentOf(path), fallbackOwner, entries);
        if (declared != null)
            fsManager.createDirectoryAtBlock(path, declared.owner(), declared.permission(), "JSON", declared.startBlock());
        else
            fsManager.createDirectory(path, fallbackOwner != null ? fallbackOwner : "admin", FilePermission.PUBLIC, "JSON");
    }

    private static void allocateExplicitChain(SimulatedDisk disk, FileSystemManager fsManager, EntrySpec e) {
        MyLinkedList<Integer> chain = e.explicitChain();
        if (chain.get(0) != e.startBlock()) {
            throw new IllegalArgumentException("El primer elemento de 'chain' no coincide con startBlock en " + e.path());
        }
        for (int i = 0; i < chain.size(); i++) {
            int blk = chain.get(i);
            if (blk < 0 || blk >= disk.getTotalBlocks() || !disk.getBlock(blk).isFree()) {
                throw new IllegalArgumentException("Bloque inválido o ya ocupado (" + blk + ") en " + e.path());
            }
        }
        if (!disk.allocateExactChain(chain, e.path(), -1)) {
            throw new IllegalArgumentException("No se pudo restaurar la cadena de " + e.path());
        }
        FSNode node = fsManager.getTree().insertAtPath(
                e.path(), FsNodeType.FILE, e.owner(), e.permission(), e.startBlock(), e.blocks());
        node.setCreatorProcess("JSON");
    }

    public static FilePermission parsePermission(String raw, String contextPath) {
        if (raw == null) {
            throw new IllegalArgumentException("Falta 'permissions' en " + contextPath);
        }
        String norm = raw.trim().toUpperCase();
        if ("PUBLIC".equals(norm)) return FilePermission.PUBLIC;
        if ("PRIVATE".equals(norm)) return FilePermission.PRIVATE;
        throw new IllegalArgumentException("Permiso inválido '" + raw + "' en " + contextPath + " (use public/private).");
    }
}

