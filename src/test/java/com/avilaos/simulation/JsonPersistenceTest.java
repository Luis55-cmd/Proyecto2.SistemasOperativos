package com.avilaos.simulation;

import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.hardware.Computer;
import com.avilaos.sync.Buffer;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Scanner;

/**
 * Prueba de verificación para guardar y cargar persistencia JSON de la simulación
 * incluyendo ciclo, computadores, buffers y carga inicial/foto de procesos (CFG-09, CFG-12, CFG-13).
 */
public class JsonPersistenceTest {

    public static void main(String[] args) throws Exception {
        testSaveAndLoadProcessesJson();
        System.out.println(">>> TODAS LAS PRUEBAS DE JsonPersistenceTest PASARON EXITOSAMENTE. <<<");
    }

    private static void testSaveAndLoadProcessesJson() throws Exception {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        // 1. Configurar escenario
        controller.setCycleDurationMs(500);
        controller.setNetworkLatency(3);

        Computer c1 = controller.createComputer("Nodo-Alpha", 256, SchedulingPolicyType.ROUND_ROBIN, 4);
        Computer c2 = controller.createComputer("Nodo-Beta", 128, SchedulingPolicyType.EDF, 2);

        Buffer b1 = controller.createBuffer(c1.getId(), 20, 20);

        ProcessControlBlock p1 = controller.createProcess("Proceso-1", c1.getId(), ProcessType.CPU_BOUND, 15, 30, 2, 80);
        ProcessControlBlock p2 = controller.createProcess("Productor-1", c1.getId(), ProcessType.PRODUCER, 20, 25, 1, 100,
                String.valueOf(b1.getId()), 2, 5);

        // Avanzar 1 ciclo para que p1 pase a EJECUTANDO
        controller.stepSimulation();

        // Verificar estado previo a guardar
        if (controller.getCurrentCycle() != 1) {
            throw new AssertionError("El ciclo actual debe ser 1");
        }
        if (p1.getState() != ProcessState.EJECUTANDO) {
            throw new AssertionError("p1 debe estar en EJECUTANDO");
        }

        // 2. Simular guardado a JSON
        File tempFile = File.createTempFile("avilaos_test_snapshot", ".json");
        tempFile.deleteOnExit();

        try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            writer.println("{");
            writer.println("  \"currentCycle\": " + controller.getCurrentCycle() + ",");
            writer.println("  \"cycleDurationMs\": " + controller.getCycleDurationMs() + ",");
            writer.println("  \"networkLatency\": " + controller.getNetworkLatency() + ",");

            writer.println("  \"computers\": [");
            var comps = controller.getComputers();
            for (int i = 0; i < comps.size(); i++) {
                Computer c = comps.get(i);
                writer.println("    {");
                writer.println("      \"name\": \"" + c.getName() + "\",");
                writer.println("      \"ramSize\": " + c.getMemoryManager().getTotalMemory() + ",");
                writer.println("      \"policy\": \"" + c.getPolitica() + "\",");
                writer.println("      \"quantum\": " + c.getQuantum());
                writer.print("    }" + (i < comps.size() - 1 ? "," : ""));
                writer.println();
            }
            writer.println("  ],");

            writer.println("  \"buffers\": [");
            var bufs = controller.getBuffers();
            for (int i = 0; i < bufs.size(); i++) {
                Buffer b = bufs.get(i);
                writer.println("    {");
                writer.println("      \"id\": " + b.getId() + ",");
                writer.println("      \"hostComputerId\": " + b.getIdComp() + ",");
                writer.println("      \"storage\": " + b.getStorage() + ",");
                writer.println("      \"sizeInMemory\": " + b.getSizeInMemory());
                writer.print("    }" + (i < bufs.size() - 1 ? "," : ""));
                writer.println();
            }
            writer.println("  ],");

            writer.println("  \"processes\": [");
            var procs = controller.getAllProcesses();
            for (int i = 0; i < procs.size(); i++) {
                ProcessControlBlock p = procs.get(i);
                writer.println("    {");
                writer.println("      \"id\": " + p.getId() + ",");
                writer.println("      \"name\": \"" + p.getName() + "\",");
                writer.println("      \"computerId\": " + p.getComputerId() + ",");
                writer.println("      \"type\": \"" + p.getType() + "\",");
                writer.println("      \"totalInstructions\": " + p.getTotalInstructions() + ",");
                writer.println("      \"remainingTime\": " + p.getRemainingTime() + ",");
                writer.println("      \"assignedMemory\": " + p.getAssignedMemory() + ",");
                writer.println("      \"priority\": " + p.getPriority() + ",");
                writer.println("      \"deadline\": " + p.getDeadline() + ",");
                writer.println("      \"remainingDeadline\": " + p.getRemainingDeadline() + ",");
                writer.println("      \"state\": \"" + p.getState() + "\",");
                writer.println("      \"blockReason\": \"" + p.getBlockReason() + "\",");
                writer.println("      \"programCounter\": " + p.getProgramCounter() + ",");
                writer.println("      \"memoryAddressRegister\": " + p.getMemoryAddressRegister() + ",");
                writer.println("      \"ioInterval\": " + p.getIoInterval() + ",");
                writer.println("      \"ioDuration\": " + p.getIoDuration() + ",");
                writer.println("      \"ioCycleCountdown\": " + p.getIoCycleCountdown() + ",");
                writer.println("      \"targetBufferId\": " + (p.getTargetBufferId() != null ? "\"" + p.getTargetBufferId() + "\"" : "null") + ",");
                writer.println("      \"cyclesPerOperation\": " + p.getCyclesPerOperation() + ",");
                writer.println("      \"requiredElements\": " + p.getRequiredElements() + ",");
                writer.println("      \"processedElements\": " + p.getProcessedElements());
                writer.print("    }" + (i < procs.size() - 1 ? "," : ""));
                writer.println();
            }
            writer.println("  ]");
            writer.println("}");
        }

        // 3. Reiniciar el controlador para simular recarga limpia
        controller.reset();
        if (!controller.getComputers().isEmpty() || !controller.getAllProcesses().isEmpty()) {
            throw new AssertionError("El reset debe vaciar computadores y procesos");
        }

        // 4. Leer archivo JSON y restaurar
        String json;
        try (Scanner s = new Scanner(tempFile)) {
            s.useDelimiter("\\Z");
            json = s.next();
        }

        // Parsear ciclo y red
        int currentCycle = extraerValorInt(json, "currentCycle", 0);
        int cycleDuration = extraerValorInt(json, "cycleDurationMs", 1000);
        int networkLatency = extraerValorInt(json, "networkLatency", 2);

        controller.setCycleDurationMs(cycleDuration);
        controller.setNetworkLatency(networkLatency);
        controller.setCurrentCycle(currentCycle);

        // Reconstrucción computadores
        int startComp = json.indexOf("\"computers\": [");
        int endComp = json.indexOf("]", startComp);
        String bloqComps = json.substring(startComp, endComp);
        String[] objComps = bloqComps.split("\\}\\s*,\\s*\\{");
        for (String obj : objComps) {
            String name = extraerValorString(obj, "name", "PC");
            int ram = extraerValorInt(obj, "ramSize", 128);
            String pol = extraerValorString(obj, "policy", "FCFS");
            int q = extraerValorInt(obj, "quantum", 3);
            controller.createComputer(name, ram, SchedulingPolicyType.valueOf(pol), q);
        }

        // Reconstrucción buffers
        int startBuf = json.indexOf("\"buffers\": [");
        int endBuf = json.indexOf("]", startBuf);
        String bloqBufs = json.substring(startBuf, endBuf);
        String[] objBufs = bloqBufs.split("\\}\\s*,\\s*\\{");
        for (String obj : objBufs) {
            int hostId = extraerValorInt(obj, "hostComputerId", 1);
            int storage = extraerValorInt(obj, "storage", 10);
            int sizeMem = extraerValorInt(obj, "sizeInMemory", storage);
            controller.createBuffer(hostId, storage, sizeMem);
        }

        // Reconstrucción procesos
        int startProc = json.indexOf("\"processes\": [");
        int endProc = json.indexOf("]", startProc);
        String bloqProcs = json.substring(startProc, endProc);
        String[] objProcs = bloqProcs.split("\\}\\s*,\\s*\\{");
        for (String obj : objProcs) {
            if (obj.trim().isEmpty() || !obj.contains("\"name\"")) continue;
            int pId = extraerValorInt(obj, "id", 1);
            String name = extraerValorString(obj, "name", "P");
            int computerId = extraerValorInt(obj, "computerId", 1);
            String typeStr = extraerValorString(obj, "type", "CPU_BOUND");
            int totalInstructions = extraerValorInt(obj, "totalInstructions", 10);
            int remainingTime = extraerValorInt(obj, "remainingTime", totalInstructions);
            int assignedMemory = extraerValorInt(obj, "assignedMemory", 10);
            int priority = extraerValorInt(obj, "priority", 1);
            int deadline = extraerValorInt(obj, "deadline", 100);
            int remainingDeadline = extraerValorInt(obj, "remainingDeadline", deadline);
            String stateStr = extraerValorString(obj, "state", "LISTO");
            String blockReasonStr = extraerValorString(obj, "blockReason", "NONE");
            int pc = extraerValorInt(obj, "programCounter", 0);
            int mar = extraerValorInt(obj, "memoryAddressRegister", 0);

            ProcessControlBlock pcb = new ProcessControlBlock(
                    name, computerId, ProcessType.valueOf(typeStr), totalInstructions, assignedMemory, priority, deadline
            );
            pcb.setId(pId);
            pcb.setRemainingTime(remainingTime);
            pcb.setRemainingDeadline(remainingDeadline);
            pcb.setState(ProcessState.valueOf(stateStr));
            pcb.setBlockReason(BlockReason.valueOf(blockReasonStr));
            pcb.setProgramCounter(pc);
            pcb.setMemoryAddressRegister(mar);

            controller.restoreProcess(pcb);
        }

        // 5. Asertos de verificación
        if (controller.getCurrentCycle() != 1) {
            throw new AssertionError("El ciclo restaurado debe ser 1 pero fue " + controller.getCurrentCycle());
        }
        if (controller.getComputers().size() != 2) {
            throw new AssertionError("Deben haber 2 computadores restaurados");
        }
        if (controller.getBuffers().size() != 1) {
            throw new AssertionError("Debe haber 1 buffer restaurado");
        }
        if (controller.getAllProcesses().size() != 2) {
            throw new AssertionError("Deben haber 2 procesos restaurados pero hay " + controller.getAllProcesses().size());
        }

        Computer restoredC1 = controller.getComputerById(1);
        if (restoredC1.getCpu().getPCB() == null || !restoredC1.getCpu().getPCB().getName().equals("Proceso-1")) {
            throw new AssertionError("El proceso en CPU de C1 debe ser Proceso-1");
        }
        if (restoredC1.getCpu().getPCB().getState() != ProcessState.EJECUTANDO) {
            throw new AssertionError("El estado de Proceso-1 en CPU debe ser EJECUTANDO");
        }
        if (restoredC1.getMemoryManager().getUsedMemory() <= 0) {
            throw new AssertionError("La memoria usada en C1 debe reflejar los procesos restaurados");
        }

        System.out.println("[PASS] testSaveAndLoadProcessesJson completado con éxito.");
    }

    private static int extraerValorInt(String texto, String clave, int defecto) {
        try {
            String patron = "\"" + clave + "\":";
            int idx = texto.indexOf(patron);
            if (idx == -1) return defecto;
            int start = idx + patron.length();
            int end = texto.indexOf(",", start);
            if (end == -1) end = texto.indexOf("}", start);
            if (end == -1) end = texto.indexOf("]", start);
            String val = texto.substring(start, end).replaceAll("[^0-9-]", "").trim();
            return Integer.parseInt(val);
        } catch (Exception e) {
            return defecto;
        }
    }

    private static String extraerValorString(String texto, String clave, String defecto) {
        try {
            String patron = "\"" + clave + "\":";
            int idx = texto.indexOf(patron);
            if (idx == -1) return defecto;
            int start = texto.indexOf("\"", idx + patron.length()) + 1;
            int end = texto.indexOf("\"", start);
            return texto.substring(start, end);
        } catch (Exception e) {
            return defecto;
        }
    }
}
