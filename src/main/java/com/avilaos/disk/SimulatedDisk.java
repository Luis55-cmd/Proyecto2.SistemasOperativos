package com.avilaos.disk;

import com.avilaos.core.kernel.IClockTickable;
import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.structures.MyLinkedList;
import java.util.concurrent.Semaphore;

/**
 * Hardware del Disco Simulado (SD) sincronizado con el reloj global (Issues #4, #5 y #6).
 * <ul>
 *   <li>Issue #4: Bloques numerados de datos [0 .. totalBlocks - 1] y partición lógica de Swap
 *       [totalBlocks .. totalBlocks + swapSizeBlocks - 1], protegidos con {@link Semaphore}.</li>
 *   <li>Issue #5: Asignación encadenada de archivos (lista enlazada de bloques con {@code nextBlock})
 *       y reserva/liberación de bloques en el área de Swap (1 unidad de RAM = 1 bloque).</li>
 *   <li>Issue #6: Cola única de E/S ({@link IORequest}) protegida con {@link Semaphore},
 *       implementación de {@link IClockTickable}, avance configurable del cabezal por tick,
 *       1 ciclo por bloque leído/escrito, atención de una solicitud a la vez y dirección (↑ / ↓).</li>
 * </ul>
 */
public class SimulatedDisk implements IClockTickable {

    public interface IDiskCompletionListener {
        void onRequestCompleted(IORequest request, int cycle);
    }

    public interface IDiskEventListener {
        void onDiskEvent(String message);
    }

    private final int totalBlocks;
    private final int swapSizeBlocks;
    private final DiskBlock[] blocks;
    private final Semaphore blockMapMutex = new Semaphore(1, true);
    private int freeDataBlocks;
    private int freeSwapBlocks;

    private final MyLinkedList<IORequest> ioQueue = new MyLinkedList<>();
    private final Semaphore queueMutex = new Semaphore(1, true);
    private DiskSchedulingPolicy schedulingPolicy;

    private int headPosition;
    private final int initialHeadPosition;
    private HeadDirection headDirection = HeadDirection.UP;
    private int headSpeedPerTick;

    private IORequest activeRequest;
    private int remainingTransferCycles;
    private int nextRequestId = 1;

    private int totalHeadMovement;
    private int completedRequestsCount;
    private int totalWaitTimeCycles;

    private IDiskCompletionListener completionListener;
    private IDiskEventListener eventListener;

    public SimulatedDisk(int totalBlocks, int swapSizeBlocks, int initialHead, int headSpeedPerTick) {
        if (totalBlocks <= 0) {
            throw new IllegalArgumentException("totalBlocks debe ser mayor que 0");
        }
        if (swapSizeBlocks < 0) {
            throw new IllegalArgumentException("swapSizeBlocks no puede ser negativo");
        }
        if (initialHead < 0 || initialHead >= totalBlocks) {
            throw new IllegalArgumentException("initialHead fuera de rango [0, " + (totalBlocks - 1) + "]");
        }
        if (headSpeedPerTick <= 0) {
            throw new IllegalArgumentException("headSpeedPerTick debe ser mayor que 0");
        }
        this.totalBlocks = totalBlocks;
        this.swapSizeBlocks = swapSizeBlocks;
        this.initialHeadPosition = initialHead;
        this.headPosition = initialHead;
        this.headSpeedPerTick = headSpeedPerTick;
        this.freeDataBlocks = totalBlocks;
        this.freeSwapBlocks = swapSizeBlocks;

        int totalPhysical = totalBlocks + swapSizeBlocks;
        this.blocks = new DiskBlock[totalPhysical];
        for (int i = 0; i < totalPhysical; i++) {
            this.blocks[i] = new DiskBlock(i, i >= totalBlocks);
        }
    }

    public SimulatedDisk(int totalBlocks, int swapSizeBlocks, int initialHead) {
        this(totalBlocks, swapSizeBlocks, initialHead, 10);
    }

    // =========================================================================
    // ISSUE #4 & #5: MAPA DE BLOQUES, ASIGNACIÓN ENCADENADA Y ÁREA DE SWAP
    // =========================================================================

    public boolean hasAvailableDataBlocks(int requiredBlocks) {
        if (requiredBlocks <= 0) return true;
        blockMapMutex.acquireUninterruptibly();
        try {
            return freeDataBlocks >= requiredBlocks;
        } finally {
            blockMapMutex.release();
        }
    }

    public boolean hasAvailableSwapBlocks(int requiredBlocks) {
        if (requiredBlocks <= 0) return true;
        blockMapMutex.acquireUninterruptibly();
        try {
            return freeSwapBlocks >= requiredBlocks;
        } finally {
            blockMapMutex.release();
        }
    }

    /**
     * Asigna {@code count} bloques libres del área de datos enlazándolos entre sí
     * y retorna el índice del primer bloque ({@code startBlock}), o -1 si no hay espacio.
     */
    public int allocateChainedBlocks(String filePath, int count, int ownerProcessId) {
        if (count <= 0 || filePath == null || filePath.isEmpty()) {
            return -1;
        }
        blockMapMutex.acquireUninterruptibly();
        try {
            if (freeDataBlocks < count) {
                return -1;
            }
            int[] chosen = new int[count];
            int found = 0;
            for (int i = 0; i < totalBlocks && found < count; i++) {
                if (blocks[i].isFree()) {
                    chosen[found++] = i;
                }
            }
            if (found < count) {
                return -1;
            }
            for (int k = 0; k < count; k++) {
                int next = (k + 1 < count) ? chosen[k + 1] : DiskBlock.END_OF_CHAIN;
                blocks[chosen[k]].allocate(filePath, ownerProcessId, next);
            }
            freeDataBlocks -= count;
            return chosen[0];
        } finally {
            blockMapMutex.release();
        }
    }

    /**
     * Asigna una cadena de {@code count} bloques comenzando obligatoriamente en {@code startBlock}
     * (usado al inicializar el sistema de archivos desde el JSON).
     */
    public int allocateChainedBlocksFrom(int startBlock, String filePath, int count, int ownerProcessId) {
        if (count <= 0 || filePath == null || filePath.isEmpty()) {
            return -1;
        }
        if (startBlock < 0 || startBlock >= totalBlocks) {
            return -1;
        }
        blockMapMutex.acquireUninterruptibly();
        try {
            if (freeDataBlocks < count || !blocks[startBlock].isFree()) {
                return -1;
            }
            int[] chosen = new int[count];
            chosen[0] = startBlock;
            int found = 1;
            for (int offset = 1; offset < totalBlocks && found < count; offset++) {
                int candidate = (startBlock + offset) % totalBlocks;
                if (blocks[candidate].isFree()) {
                    chosen[found++] = candidate;
                }
            }
            if (found < count) {
                return -1;
            }
            for (int k = 0; k < count; k++) {
                int next = (k + 1 < count) ? chosen[k + 1] : DiskBlock.END_OF_CHAIN;
                blocks[chosen[k]].allocate(filePath, ownerProcessId, next);
            }
            freeDataBlocks -= count;
            return startBlock;
        } finally {
            blockMapMutex.release();
        }
    }

    /**
     * Recorre la cadena de asignación desde {@code startBlock} y libera todos sus bloques.
     *
     * @return Cantidad de bloques liberados.
     */
    public int freeChainedBlocks(int startBlock) {
        if (startBlock < 0 || startBlock >= totalBlocks) {
            return 0;
        }
        blockMapMutex.acquireUninterruptibly();
        try {
            int freed = 0;
            int curr = startBlock;
            int guard = 0;
            while (curr >= 0 && curr < totalBlocks && guard <= totalBlocks) {
                DiskBlock b = blocks[curr];
                if (!b.isOccupied()) {
                    break;
                }
                int next = b.getNextBlock();
                b.free();
                freeDataBlocks++;
                freed++;
                curr = next;
                guard++;
            }
            return freed;
        } finally {
            blockMapMutex.release();
        }
    }

    /**
     * Retorna la lista de bloques que componen la cadena iniciada en {@code startBlock}.
     */
    public MyLinkedList<Integer> getChainBlocks(int startBlock) {
        MyLinkedList<Integer> chain = new MyLinkedList<>();
        if (startBlock < 0 || startBlock >= blocks.length) {
            return chain;
        }
        blockMapMutex.acquireUninterruptibly();
        try {
            int curr = startBlock;
            int guard = 0;
            while (curr >= 0 && curr < blocks.length && guard <= blocks.length) {
                DiskBlock b = blocks[curr];
                if (!b.isOccupied()) {
                    break;
                }
                chain.append(curr);
                curr = b.getNextBlock();
                guard++;
            }
            return chain;
        } finally {
            blockMapMutex.release();
        }
    }

    /**
     * Reserva bloques encadenados en el área de Swap para un proceso suspendido
     * (1 unidad de RAM = 1 bloque de Swap).
     *
     * @return Índice del primer bloque en Swap, o -1 si no hay espacio suficiente.
     */
    public int allocateSwapBlocks(int processId, String processName, int memoryUnits) {
        if (memoryUnits <= 0) {
            return -1;
        }
        blockMapMutex.acquireUninterruptibly();
        try {
            if (freeSwapBlocks < memoryUnits) {
                return -1;
            }
            int[] chosen = new int[memoryUnits];
            int found = 0;
            int limit = totalBlocks + swapSizeBlocks;
            for (int i = totalBlocks; i < limit && found < memoryUnits; i++) {
                if (blocks[i].isFree()) {
                    chosen[found++] = i;
                }
            }
            if (found < memoryUnits) {
                return -1;
            }
            String tag = "SWAP:" + (processName != null && !processName.isEmpty() ? processName : ("P" + processId));
            for (int k = 0; k < memoryUnits; k++) {
                int next = (k + 1 < memoryUnits) ? chosen[k + 1] : DiskBlock.END_OF_CHAIN;
                blocks[chosen[k]].allocate(tag, processId, next);
            }
            freeSwapBlocks -= memoryUnits;
            return chosen[0];
        } finally {
            blockMapMutex.release();
        }
    }

    /**
     * Libera una cadena de bloques en el área de Swap a partir de {@code startSwapBlock}.
     */
    public int freeSwapBlocks(int startSwapBlock) {
        int limit = totalBlocks + swapSizeBlocks;
        if (startSwapBlock < totalBlocks || startSwapBlock >= limit) {
            return 0;
        }
        blockMapMutex.acquireUninterruptibly();
        try {
            int freed = 0;
            int curr = startSwapBlock;
            int guard = 0;
            while (curr >= totalBlocks && curr < limit && guard <= swapSizeBlocks) {
                DiskBlock b = blocks[curr];
                if (!b.isOccupied()) {
                    break;
                }
                int next = b.getNextBlock();
                b.free();
                freeSwapBlocks++;
                freed++;
                curr = next;
                guard++;
            }
            return freed;
        } finally {
            blockMapMutex.release();
        }
    }

    /**
     * Libera todos los bloques del área de Swap asociados al {@code processId} dado.
     */
    public int freeSwapBlocksByProcess(int processId) {
        blockMapMutex.acquireUninterruptibly();
        try {
            int freed = 0;
            int limit = totalBlocks + swapSizeBlocks;
            for (int i = totalBlocks; i < limit; i++) {
                if (blocks[i].isOccupied() && blocks[i].getOwnerProcessId() == processId) {
                    blocks[i].free();
                    freeSwapBlocks++;
                    freed++;
                }
            }
            return freed;
        } finally {
            blockMapMutex.release();
        }
    }

    // =========================================================================
    // ISSUE #6: COLA ÚNICA DE E/S, PLANIFICACIÓN Y PULSO DE RELOJ (IClockTickable)
    // =========================================================================

    /**
     * Encola una nueva solicitud de E/S en la cola única del disco protegida con semáforo.
     */
    public IORequest enqueueRequest(ProcessControlBlock pcb, IOOperationType operation,
                                    String path, int targetBlock, int blockCount, int arrivalCycle) {
        queueMutex.acquireUninterruptibly();
        try {
            int id = nextRequestId++;
            IORequest req = new IORequest(id, pcb, operation, path, targetBlock, blockCount, arrivalCycle);
            ioQueue.append(req);
            return req;
        } finally {
            queueMutex.release();
        }
    }

    public IORequest enqueueRequest(ProcessControlBlock pcb, IOOperationType operation,
                                    String path, int targetBlock, int arrivalCycle) {
        return enqueueRequest(pcb, operation, path, targetBlock, 1, arrivalCycle);
    }

    public void enqueueRequest(IORequest request) {
        if (request == null) return;
        queueMutex.acquireUninterruptibly();
        try {
            ioQueue.append(request);
        } finally {
            queueMutex.release();
        }
    }

    /**
     * Pulso del reloj global:
     * <ul>
     *   <li>Si no hay solicitud activa, selecciona una de la cola según la política activa.</li>
     *   <li>Si el cabezal aún no está en el bloque destino, avanza hasta {@code headSpeedPerTick}
     *       posiciones hacia el bloque objetivo y actualiza la dirección (↑ / ↓).</li>
     *   <li>Cuando el cabezal está en el bloque objetivo, cada bloque leído/escrito toma 1 ciclo.</li>
     * </ul>
     */
    @Override
    public void tick(int currentCycle) {
        if (activeRequest == null) {
            selectNextFromQueue(currentCycle);
            if (activeRequest == null) {
                return;
            }
        }

        int targetPos = normalizeTargetPosition(activeRequest.getTargetBlock());
        if (headPosition != targetPos) {
            int distance = Math.abs(targetPos - headPosition);
            int step = Math.min(headSpeedPerTick, distance);
            headDirection = targetPos > headPosition ? HeadDirection.UP : HeadDirection.DOWN;
            headPosition += (headDirection == HeadDirection.UP ? step : -step);
            totalHeadMovement += step;
            return;
        }

        // El cabezal está sobre el bloque objetivo: 1 ciclo de reloj por bloque leído/escrito
        remainingTransferCycles--;
        if (remainingTransferCycles <= 0) {
            IORequest finished = activeRequest;
            activeRequest = null;
            finished.setCompletionCycle(currentCycle);
            completedRequestsCount++;
            if (finished.getStartServiceCycle() >= 0) {
                totalWaitTimeCycles += Math.max(0, finished.getStartServiceCycle() - finished.getArrivalCycle());
            }
            String policyName = schedulingPolicy != null ? schedulingPolicy.getPolicyType().name() : "FIFO";
            String procName = finished.getPcb() != null ? finished.getPcb().getName() : ("Req#" + finished.getId());
            emitEvent("El disco completó la solicitud de " + procName + " (" + finished.getOperation()
                    + ") en el bloque " + finished.getTargetBlock() + " (" + policyName + ")");
            if (completionListener != null) {
                completionListener.onRequestCompleted(finished, currentCycle);
            }
        }
    }

    private void selectNextFromQueue(int currentCycle) {
        IORequest selected = null;
        queueMutex.acquireUninterruptibly();
        try {
            if (ioQueue.isEmpty()) {
                return;
            }
            if (schedulingPolicy != null) {
                selected = schedulingPolicy.selectNext(ioQueue, headPosition, totalBlocks);
            }
            if (selected == null) {
                selected = ioQueue.get(0);
            }
            ioQueue.remove(selected);
        } finally {
            queueMutex.release();
        }

        if (selected != null) {
            activeRequest = selected;
            activeRequest.setStartServiceCycle(currentCycle);
            remainingTransferCycles = Math.max(1, activeRequest.getBlockCount());
            int targetPos = normalizeTargetPosition(activeRequest.getTargetBlock());
            if (targetPos != headPosition) {
                headDirection = targetPos > headPosition ? HeadDirection.UP : HeadDirection.DOWN;
            }
            String policyName = schedulingPolicy != null ? schedulingPolicy.getPolicyType().name() : "FIFO";
            String procName = selected.getPcb() != null ? selected.getPcb().getName() : ("Req#" + selected.getId());
            emitEvent("El disco selecciona la solicitud de " + procName + " hacia el bloque "
                    + selected.getTargetBlock() + " (" + policyName + ", dir " + headDirection.getSymbol() + ")");
        }
    }

    private int normalizeTargetPosition(int targetBlock) {
        if (targetBlock < 0) return 0;
        if (targetBlock >= totalBlocks) return totalBlocks - 1;
        return targetBlock;
    }

    private void emitEvent(String msg) {
        if (eventListener != null) {
            eventListener.onDiskEvent(msg);
        }
    }

    // =========================================================================
    // GETTERS, SETTERS Y SNAPSHOT SEGURO CON SEMÁFORO
    // =========================================================================

    public void setSchedulingPolicy(DiskSchedulingPolicy policy) {
        queueMutex.acquireUninterruptibly();
        try {
            this.schedulingPolicy = policy;
        } finally {
            queueMutex.release();
        }
    }

    public DiskSchedulingPolicy getSchedulingPolicy() {
        return schedulingPolicy;
    }

    public DiskBlock getBlock(int index) {
        blockMapMutex.acquireUninterruptibly();
        try {
            return blocks[index];
        } finally {
            blockMapMutex.release();
        }
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getSwapSizeBlocks() {
        return swapSizeBlocks;
    }

    public int getTotalPhysicalBlocks() {
        return blocks.length;
    }

    public int getFreeDataBlocks() {
        blockMapMutex.acquireUninterruptibly();
        try {
            return freeDataBlocks;
        } finally {
            blockMapMutex.release();
        }
    }

    public int getFreeSwapBlocks() {
        blockMapMutex.acquireUninterruptibly();
        try {
            return freeSwapBlocks;
        } finally {
            blockMapMutex.release();
        }
    }

    public int getHeadPosition() {
        return headPosition;
    }

    public void setHeadPosition(int headPosition) {
        if (headPosition < 0 || headPosition >= totalBlocks) {
            throw new IllegalArgumentException("headPosition fuera de rango");
        }
        this.headPosition = headPosition;
    }

    public HeadDirection getHeadDirection() {
        return headDirection;
    }

    public void setHeadDirection(HeadDirection headDirection) {
        if (headDirection != null) {
            this.headDirection = headDirection;
        }
    }

    public int getHeadSpeedPerTick() {
        return headSpeedPerTick;
    }

    public void setHeadSpeedPerTick(int headSpeedPerTick) {
        if (headSpeedPerTick <= 0) {
            throw new IllegalArgumentException("headSpeedPerTick debe ser mayor que 0");
        }
        this.headSpeedPerTick = headSpeedPerTick;
    }

    public IORequest getActiveRequest() {
        return activeRequest;
    }

    public int getRemainingTransferCycles() {
        return remainingTransferCycles;
    }

    public int getQueueSize() {
        queueMutex.acquireUninterruptibly();
        try {
            return ioQueue.size();
        } finally {
            queueMutex.release();
        }
    }

    public MyLinkedList<IORequest> getQueueSnapshot() {
        queueMutex.acquireUninterruptibly();
        try {
            MyLinkedList<IORequest> copy = new MyLinkedList<>();
            for (int i = 0; i < ioQueue.size(); i++) {
                copy.append(ioQueue.get(i));
            }
            return copy;
        } finally {
            queueMutex.release();
        }
    }

    public int getTotalHeadMovement() {
        return totalHeadMovement;
    }

    public int getCompletedRequestsCount() {
        return completedRequestsCount;
    }

    public double getAverageWaitTimeCycles() {
        return completedRequestsCount == 0 ? 0.0 : (double) totalWaitTimeCycles / completedRequestsCount;
    }

    public void setCompletionListener(IDiskCompletionListener completionListener) {
        this.completionListener = completionListener;
    }

    public void setEventListener(IDiskEventListener eventListener) {
        this.eventListener = eventListener;
    }

    public void reset() {
        blockMapMutex.acquireUninterruptibly();
        try {
            for (int i = 0; i < blocks.length; i++) {
                blocks[i].free();
            }
            freeDataBlocks = totalBlocks;
            freeSwapBlocks = swapSizeBlocks;
        } finally {
            blockMapMutex.release();
        }
        queueMutex.acquireUninterruptibly();
        try {
            while (!ioQueue.isEmpty()) {
                ioQueue.deleteFirst();
            }
            if (schedulingPolicy != null) {
                schedulingPolicy.reset();
            }
        } finally {
            queueMutex.release();
        }
        headPosition = initialHeadPosition;
        headDirection = HeadDirection.UP;
        activeRequest = null;
        remainingTransferCycles = 0;
        totalHeadMovement = 0;
        completedRequestsCount = 0;
        totalWaitTimeCycles = 0;
        nextRequestId = 1;
    }
}

