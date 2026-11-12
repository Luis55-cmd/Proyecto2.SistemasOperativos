package com.avilaos.gui;

import com.avilaos.concurrency.StateAccess;
import com.avilaos.core.model.*;
import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.hardware.Computer;
import com.avilaos.simulation.SimulationController;
import com.avilaos.sync.Buffer;
import java.io.File;

/** Guarda y carga configuraciones sin depender de una vista Swing. */
final class ConfiguracionSimulacion {
    private final SimulationController controller;

    ConfiguracionSimulacion(SimulationController controller) {
        this.controller = controller;
    }

    /** Guarda una configuración para comenzar de nuevo, no un checkpoint. */
    boolean guardarEstado(File archivo) {
        try {
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            com.google.gson.JsonArray computers = new com.google.gson.JsonArray();
            com.google.gson.JsonArray buffers = new com.google.gson.JsonArray();
            com.google.gson.JsonArray processes = new com.google.gson.JsonArray();
            StateAccess.enter();
            try {
                root.addProperty("currentCycle", 0);
                root.addProperty("cycleDurationMs", controller.getCycleDurationMs());
                root.addProperty("networkLatency", controller.getNetworkLatency());
                MyLinkedList<Computer> cs = controller.getComputers();
                int[] libre = new int[cs.size()];
                for (int i = 0; i < cs.size(); i++) {
                    Computer c = cs.get(i);
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("name", c.getName());
                    o.addProperty("ramSize", c.getMemoryManager().getTotalMemory());
                    o.addProperty("policy", c.getPolitica().name());
                    o.addProperty("quantum", c.getQuantum());
                    libre[i] = c.getMemoryManager().getTotalMemory();
                    computers.add(o);
                }
                MyLinkedList<Buffer> bs = controller.getBuffers();
                for (int i = 0; i < bs.size(); i++) {
                    Buffer b = bs.get(i);
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", b.getId());
                    o.addProperty("hostComputerId", b.getIdComp());
                    o.addProperty("storage", b.getStorage());
                    o.addProperty("sizeInMemory", b.getSizeInMemory());
                    o.addProperty("latency", b.getLatency());
                    libre[b.getIdComp() - 1] -= b.getStorage();
                    buffers.add(o);
                }
                MyLinkedList<ProcessControlBlock> ps = controller.getAllProcesses();
                for (int i = 0; i < ps.size(); i++) {
                    ProcessControlBlock p = ps.get(i);
                    int memory = p.getRequiredMemory();
                    if (memory <= 0) throw new IllegalStateException("No se conoce la memoria original de " + p.getName());
                    int host = p.getComputerId() - 1;
                    boolean admitted = memory <= libre[host];
                    if (admitted) libre[host] -= memory;
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", p.getId()); o.addProperty("name", p.getName());
                    o.addProperty("computerId", p.getComputerId()); o.addProperty("type", p.getType().name());
                    o.addProperty("totalInstructions", p.getTotalInstructions());
                    o.addProperty("remainingTime", p.getTotalInstructions());
                    o.addProperty("assignedMemory", memory); o.addProperty("priority", p.getPriority());
                    o.addProperty("deadline", p.getDeadline()); o.addProperty("remainingDeadline", p.getDeadline());
                    o.addProperty("state", admitted ? "LISTO" : "NUEVO"); o.addProperty("blockReason", "NONE");
                    o.addProperty("programCounter", 0); o.addProperty("memoryAddressRegister", 0);
                    o.addProperty("ioInterval", p.getIoInterval()); o.addProperty("ioDuration", p.getIoDuration());
                    o.addProperty("ioRequestInstruction", p.isIoRequestInstruction());
                    o.addProperty("ioCycleCountdown", p.getIoInterval());
                    o.addProperty("targetBufferId", p.getTargetBufferId());
                    o.addProperty("cyclesPerOperation", p.getCyclesPerOperation());
                    o.addProperty("requiredElements", p.getRequiredElements()); o.addProperty("processedElements", 0);
                    processes.add(o);
                }
            } finally { StateAccess.exit(); }
            root.add("computers", computers); root.add("buffers", buffers); root.add("processes", processes);
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(root);
            ValidarCarga.validar(json);
            java.nio.file.Path destino = archivo.toPath().toAbsolutePath();
            java.nio.file.Path temporal = java.nio.file.Files.createTempFile(destino.getParent(), ".avilaos-", ".tmp");
            try {
                java.nio.file.Files.writeString(temporal, json, java.nio.charset.StandardCharsets.UTF_8);
                try {
                    java.nio.file.Files.move(temporal, destino, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    java.nio.file.Files.move(temporal, destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { java.nio.file.Files.deleteIfExists(temporal); }
            controller.logEvent("Configuración guardada exitosamente en JSON: " + archivo.getName());
            return true;
        } catch (Exception ex) {
            controller.logEvent("Error al guardar archivo JSON: " + ex.getMessage());
            return false;
        }
    }

    /** Ruta compartida con Cargar Estado; reemplaza la sesión actual. */
    boolean cargarEstado(File archivo) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(archivo))) {
            StringBuilder sb = new StringBuilder();
            String linea;
            while ((linea = reader.readLine()) != null) {
                sb.append(linea.trim());
            }

            String json = sb.toString();

            json = ValidarCarga.validar(json);
            
            StateAccess.enter();
            try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            controller.reset();
            controller.setCycleDurationMs(root.get("cycleDurationMs").getAsInt());
            controller.setNetworkLatency(root.get("networkLatency").getAsInt());
            controller.setCurrentCycle(root.get("currentCycle").getAsInt());
            for (com.google.gson.JsonElement e : root.getAsJsonArray("computers")) {
                com.google.gson.JsonObject c = e.getAsJsonObject();
                controller.createComputer(c.get("name").getAsString(), c.get("ramSize").getAsInt(),
                        SchedulingPolicyType.valueOf(c.get("policy").getAsString()), c.get("quantum").getAsInt());
            }
            for (com.google.gson.JsonElement e : root.getAsJsonArray("buffers")) {
                com.google.gson.JsonObject b = e.getAsJsonObject();
                int lat = b.has("latency") ? b.get("latency").getAsInt() : controller.getNetworkLatency();
                if (controller.createBuffer(b.get("hostComputerId").getAsInt(), b.get("storage").getAsInt(), lat) == null)
                    throw new IllegalStateException("No se pudo crear el buffer");
            }
            int maxId = 0;
            for (com.google.gson.JsonElement e : root.getAsJsonArray("processes")) {
                com.google.gson.JsonObject p = e.getAsJsonObject();
                int id = p.get("id").getAsInt();
                maxId = Math.max(maxId, id);
                ProcessControlBlock pcb = new ProcessControlBlock(p.get("name").getAsString(),
                        p.get("computerId").getAsInt(), ProcessType.valueOf(p.get("type").getAsString()),
                        p.get("totalInstructions").getAsInt(), p.get("assignedMemory").getAsInt(),
                        p.get("priority").getAsInt(), p.get("deadline").getAsInt());
                pcb.setId(id);
                pcb.setRemainingTime(p.get("remainingTime").getAsInt());
                pcb.setRemainingDeadline(p.get("remainingDeadline").getAsInt());
                pcb.setState(ProcessState.valueOf(p.get("state").getAsString()));
                pcb.setBlockReason(BlockReason.valueOf(p.get("blockReason").getAsString()));
                pcb.setProgramCounter(p.get("programCounter").getAsInt());
                pcb.setMemoryAddressRegister(p.get("memoryAddressRegister").getAsInt());
                pcb.setIoInterval(p.get("ioInterval").getAsInt());
                pcb.setIoDuration(p.get("ioDuration").getAsInt());
                pcb.setIoRequestInstruction(p.has("ioRequestInstruction") && p.get("ioRequestInstruction").getAsBoolean());
                pcb.setIoCycleCountdown(p.get("ioCycleCountdown").getAsInt());
                com.google.gson.JsonElement target = p.get("targetBufferId");
                pcb.setTargetBufferId(target == null || target.isJsonNull() ? null : target.getAsString());
                pcb.setCyclesPerOperation(p.get("cyclesPerOperation").getAsInt());
                pcb.setRequiredElements(p.get("requiredElements").getAsInt());
                pcb.setProcessedElements(p.get("processedElements").getAsInt());
                controller.restoreProcess(pcb);
            }
            if (maxId > 0) ProcessControlBlock.resetIdGenerator(maxId + 1);

            } finally { StateAccess.exit(); }
            controller.logEvent("Configuración JSON cargada exitosamente desde: " + archivo.getName());
            return true;
        } catch (Exception ex) {
            controller.logEvent("Error al leer el archivo JSON: " + ex.getMessage());
            return false;
        }
    }

}
