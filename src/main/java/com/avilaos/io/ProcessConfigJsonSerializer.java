package com.avilaos.io;

import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.disk.DiskSchedulingPolicyType;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Lector y escritor del JSON de Procesos y Configuración (Issue #11).
 * Utiliza {@link JsonReader} y {@link JsonWriter} sin colecciones de java.util.
 */
public final class ProcessConfigJsonSerializer {

    public record GlobalConfigSpec(
            int cycleDurationMs,
            int swapSizeBlocks,
            DiskSchedulingPolicyType initialDiskPolicy
    ) { }

    public record ComputerSpec(
            int ramCapacity,
            SchedulingPolicyType policy,
            int quantum
    ) { }

    public record ProcessSpec(
            String name,
            int memory,
            int deadline,
            int instructions,
            String targetPath,
            IOOperationType operation,
            int fileSizeBlocks,
            int priority,
            String owner,
            String newFileName
    ) { }

    public record LoadedProcessConfig(
            GlobalConfigSpec globalConfig,
            ComputerSpec computer,
            MyLinkedList<ProcessSpec> processes
    ) { }

    private ProcessConfigJsonSerializer() {
    }

    /**
     * Carga y valida el JSON de Procesos y Configuración desde un {@link String}.
     */
    public static LoadedProcessConfig loadFromString(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("El JSON de procesos y configuración está vacío.");
        }
        GlobalConfigSpec globalConfig = null;
        ComputerSpec computer = null;
        MyLinkedList<ProcessSpec> processes = null;

        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                switch (key) {
                    case "globalConfig" -> globalConfig = readGlobalConfig(reader);
                    case "computer" -> computer = readComputer(reader);
                    case "processes" -> {
                        processes = new MyLinkedList<>();
                        reader.beginArray();
                        while (reader.hasNext()) {
                            processes.append(readProcess(reader));
                        }
                        reader.endArray();
                    }
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
        } catch (IOException | IllegalStateException | NumberFormatException ex) {
            throw new IllegalArgumentException("JSON de procesos mal formado: " + ex.getMessage(), ex);
        }

        if (globalConfig == null) {
            throw new IllegalArgumentException("Falta la sección 'globalConfig' en el JSON de procesos.");
        }
        if (computer == null) {
            throw new IllegalArgumentException("Falta la sección 'computer' en el JSON de procesos.");
        }
        if (processes == null) {
            throw new IllegalArgumentException("Falta el arreglo 'processes' en el JSON de procesos.");
        }
        for (int i = 0; i < processes.size(); i++) {
            ProcessSpec process = processes.get(i);
            if (process.memory() > computer.ramCapacity()) {
                throw new IllegalArgumentException("El proceso '" + process.name()
                        + "' requiere más memoria que la capacidad del computador.");
            }
        }
        return new LoadedProcessConfig(globalConfig, computer, processes);
    }

    public static LoadedProcessConfig loadFromFile(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Archivo JSON de procesos inexistente.");
        }
        StringBuilder sb = new StringBuilder();
        try (FileReader fr = new FileReader(file, StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int n;
            while ((n = fr.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        return loadFromString(sb.toString());
    }

    public static String toJsonString(LoadedProcessConfig cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("Configuración nula.");
        }
        StringWriter sw = new StringWriter();
        try (JsonWriter writer = new JsonWriter(sw)) {
            writer.setIndent("  ");
            writer.beginObject();

            writer.name("globalConfig").beginObject();
            writer.name("cycleDurationMs").value(cfg.globalConfig().cycleDurationMs());
            writer.name("swapSizeBlocks").value(cfg.globalConfig().swapSizeBlocks());
            writer.name("initialDiskPolicy").value(formatDiskPolicy(cfg.globalConfig().initialDiskPolicy()));
            writer.endObject();

            writer.name("computer").beginObject();
            writer.name("ramCapacity").value(cfg.computer().ramCapacity());
            writer.name("policy").value(cfg.computer().policy().name());
            writer.name("quantum").value(cfg.computer().quantum());
            writer.endObject();

            writer.name("processes").beginArray();
            for (int i = 0; i < cfg.processes().size(); i++) {
                ProcessSpec p = cfg.processes().get(i);
                writer.beginObject();
                writer.name("name").value(p.name());
                writer.name("memory").value(p.memory());
                writer.name("deadline").value(p.deadline());
                writer.name("instructions").value(p.instructions());
                writer.name("targetPath").value(p.targetPath());
                writer.name("operation").value(p.operation().name());
                writer.name("fileSizeBlocks").value(p.fileSizeBlocks());
                writer.endObject();
            }
            writer.endArray();

            writer.endObject();
        } catch (IOException ex) {
            throw new IllegalStateException("Error serializando configuración de procesos: " + ex.getMessage(), ex);
        }
        return sw.toString();
    }

    public static void saveToFile(File file, LoadedProcessConfig cfg) throws IOException {
        String json = toJsonString(cfg);
        try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8)) {
            fw.write(json);
        }
    }

    private static GlobalConfigSpec readGlobalConfig(JsonReader reader) throws IOException {
        int cycleDurationMs = -1;
        int swapSizeBlocks = -1;
        String policyStr = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String k = reader.nextName();
            if ("cycleDurationMs".equals(k)) {
                cycleDurationMs = reader.nextInt();
            } else if ("swapSizeBlocks".equals(k)) {
                swapSizeBlocks = reader.nextInt();
            } else if ("initialDiskPolicy".equals(k)) {
                policyStr = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        if (cycleDurationMs <= 0) {
            throw new IllegalArgumentException("globalConfig.cycleDurationMs debe ser mayor que 0.");
        }
        if (swapSizeBlocks < 0) {
            throw new IllegalArgumentException("globalConfig.swapSizeBlocks no puede ser negativo.");
        }
        DiskSchedulingPolicyType diskPolicy = parseDiskPolicy(policyStr);
        return new GlobalConfigSpec(cycleDurationMs, swapSizeBlocks, diskPolicy);
    }

    private static ComputerSpec readComputer(JsonReader reader) throws IOException {
        int ramCapacity = -1;
        String policyStr = null;
        int quantum = 0;

        reader.beginObject();
        while (reader.hasNext()) {
            String k = reader.nextName();
            if ("ramCapacity".equals(k)) {
                ramCapacity = reader.nextInt();
            } else if ("policy".equals(k)) {
                policyStr = reader.nextString();
            } else if ("quantum".equals(k)) {
                quantum = reader.nextInt();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        if (ramCapacity <= 0) {
            throw new IllegalArgumentException("computer.ramCapacity debe ser mayor que 0.");
        }
        SchedulingPolicyType cpuPolicy = parseCpuPolicy(policyStr);
        int effectiveQuantum = quantum > 0 ? quantum : 3;
        return new ComputerSpec(ramCapacity, cpuPolicy, effectiveQuantum);
    }

    private static ProcessSpec readProcess(JsonReader reader) throws IOException {
        String name = null;
        int memory = -1;
        int deadline = -1;
        int instructions = -1;
        String targetPath = null;
        String operationStr = null;
        int fileSizeBlocks = 0;
        int priority = 1;
        String owner = "admin";
        String newFileName = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String k = reader.nextName();
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
                continue;
            }
            switch (k) {
                case "name" -> name = reader.nextString();
                case "memory" -> memory = reader.nextInt();
                case "deadline" -> deadline = reader.nextInt();
                case "instructions" -> instructions = reader.nextInt();
                case "targetPath" -> targetPath = reader.nextString();
                case "operation" -> operationStr = reader.nextString();
                case "fileSizeBlocks" -> fileSizeBlocks = reader.nextInt();
                case "priority" -> priority = reader.nextInt();
                case "owner" -> owner = reader.nextString();
                case "newFileName" -> newFileName = reader.nextString();
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Todo proceso debe tener un 'name' no vacío.");
        }
        if (memory <= 0) {
            throw new IllegalArgumentException("El proceso '" + name + "' debe tener memory > 0.");
        }
        if (deadline <= 0) {
            throw new IllegalArgumentException("El proceso '" + name + "' debe tener deadline > 0.");
        }
        if (instructions <= 0) {
            throw new IllegalArgumentException("El proceso '" + name + "' debe tener instructions > 0.");
        }
        if (targetPath == null || !targetPath.startsWith("/")) {
            throw new IllegalArgumentException("El proceso '" + name + "' tiene un targetPath inválido: " + targetPath);
        }
        if (operationStr == null) {
            throw new IllegalArgumentException("El proceso '" + name + "' no especifica 'operation'.");
        }
        IOOperationType op;
        try {
            op = IOOperationType.valueOf(operationStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Operación inválida '" + operationStr + "' en el proceso '" + name + "'.");
        }
        if (fileSizeBlocks < 0) {
            throw new IllegalArgumentException("fileSizeBlocks no puede ser negativo en '" + name + "'.");
        }
        if (op == IOOperationType.CREATE && fileSizeBlocks <= 0) {
            throw new IllegalArgumentException("El proceso CREATE '" + name + "' debe indicar fileSizeBlocks > 0.");
        }
        return new ProcessSpec(name.trim(), memory, deadline, instructions,
                targetPath.trim(), op, fileSizeBlocks, Math.max(1, priority), owner, newFileName);
    }

    public static DiskSchedulingPolicyType parseDiskPolicy(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Falta initialDiskPolicy.");
        }
        String norm = raw.trim().toUpperCase().replace("-", "_");
        if ("CSCAN".equals(norm)) norm = "C_SCAN";
        if ("CLOOK".equals(norm)) norm = "C_LOOK";
        try {
            return DiskSchedulingPolicyType.valueOf(norm);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Política de disco desconocida: " + raw);
        }
    }

    public static String formatDiskPolicy(DiskSchedulingPolicyType policy) {
        if (policy == DiskSchedulingPolicyType.C_SCAN) return "C-SCAN";
        if (policy == DiskSchedulingPolicyType.C_LOOK) return "C-LOOK";
        return policy.name();
    }

    public static SchedulingPolicyType parseCpuPolicy(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Falta computer.policy.");
        }
        String norm = raw.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        if ("RR".equals(norm)) norm = "ROUND_ROBIN";
        if ("PRIORITY".equals(norm) || "PRIORIDADES".equals(norm)) norm = "PRIORITY_PREEMPTIVE";
        try {
            return SchedulingPolicyType.valueOf(norm);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Política de CPU desconocida: " + raw);
        }
    }
}

