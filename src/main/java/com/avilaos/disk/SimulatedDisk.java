package com.avilaos.disk;

import com.avilaos.core.kernel.IClockTickable;
import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.structures.MyLinkedList;
import java.util.concurrent.Semaphore;

/**
 * Hardware del Disco Simulado (SD) sincronizado con el reloj global (Issues #4, #5, #6, #7 y #8).
 * <ul>
 *   <li>Issue #4: Bloques numerados de datos [0 .. totalBlocks - 1] y partición lógica de Swap
 *       [totalBlocks .. totalBlocks + swapSizeBlocks - 1], protegidos con {@link Semaphore}.</li>
 *   <li>Issue #5: Asignación encadenada de archivos (lista enlazada de bloques con {@code nextBlock})
 *       y reserva/liberación de bloques en el área de Swap (1 unidad de RAM = 1 bloque).</li>
 *   <li>Issue #6: Cola única de E/S ({@link IORequest}) protegida con {@link Semaphore},
 *       implementación de {@link IClockTickable}, avance configurable del cabezal por tick,
 *       1 ciclo por bloque leído/escrito, atención de una solicitud a la vez y dirección (↑ / ↓).</li>
 *   <li>Issues #7 y #8: Políticas FIFO, SSTF, SCAN, C-SCAN (y LOOK/C-LOOK) con cambio en caliente
 *       instantáneo (0 ticks) y reordenamiento inmediato de la cola sin perder solicitudes.</li>
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
    private int pendingBoundaryBlock = -1;
    private boolean pendingWrapJumpToZero = false;
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
        this.schedulingPolicy = new FIFODiskPolicy();

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
    // ISSUES #6, #7 Y #8: COLA ÚNICA DE E/S, PLANIFICACIÓN Y PULSO DE RELOJ
    // =========================================================================

    public IORequest enqueueRequest(ProcessControlBlock pcb, IOOperationType operation,
                                    String path, int targetBlock, int blockCount, int arrivalCycle) {
        queueMutex.acquireUninterruptibly();
        try {
            int id = nextRequestId++;
            IORequest req = new IORequest(id, pcb, operation, path, targetBlock, blockCount, arrivalCycle);
            ioQueue.append(req);
            reorderQueueLocked();
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
            reorderQueueLocked();
        } finally {
            queueMutex.release();
        }
    }

    @Override
    public void tick(int currentCycle) {
        if (activeRequest == null) {
            selectNextFromQueue(currentCycle);
            if (activeRequest == null) {
                return;
            }
        }

        int targetPos = normalizeTargetPosition(activeRequest.getTargetBlock());
        if (pendingBoundaryBlock >= 0 || headPosition != targetPos) {
            int budget = headSpeedPerTick;

            // Si la política hizo barrido al extremo (SCAN o C-SCAN), avanzar primero al extremo
            if (pendingBoundaryBlock >= 0) {
                int distToBoundary = Math.abs(pendingBoundaryBlock - headPosition);
                int step1 = Math.min(budget, distToBoundary);
                if (distToBoundary > 0) {
                    headDirection = pendingBoundaryBlock > headPosition ? HeadDirection.UP : HeadDirection.DOWN;
                    headPosition += (headDirection == HeadDirection.UP ? step1 : -step1);
                    totalHeadMovement += step1;
                    budget -= step1;
                }
                if (headPosition == pendingBoundaryBlock) {
                    if (pendingWrapJumpToZero) {
                        // C-SCAN: llega a totalBlocks - 1 y salta a 0 sin contar el salto de regreso
                        headPosition = 0;
                        headDirection = HeadDirection.UP;
                        pendingWrapJumpToZero = false;
                    } else {
                        // SCAN: rebota en el extremo invirtiendo dirección
                        headDirection = headDirection.opposite();
                    }
                    pendingBoundaryBlock = -1;
                }
            }

            if (pendingBoundaryBlock < 0 && budget > 0 && headPosition != targetPos) {
                int dist = Math.abs(targetPos - headPosition);
                int step2 = Math.min(budget, dist);
                headDirection = targetPos > headPosition ? HeadDirection.UP : HeadDirection.DOWN;
                headPosition += (headDirection == HeadDirection.UP ? step2 : -step2);
                totalHeadMovement += step2;
            }
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
            emitEvent("El disco atiende la solicitud de " + procName + " en el bloque "
                    + finished.getTargetBlock() + " (" + policyName + ")");
            if (completionListener != null) {
                completionListener.onRequestCompleted(finished, currentCycle);
            }
        }
    }

    private void selectNextFromQueue(int currentCycle) {
        IORequest selected = null;
        boolean scanBounced = false;
        int scanBoundary = -1;
        boolean cscanWrapped = false;
        boolean clookWrapped = false;

        queueMutex.acquireUninterruptibly();
        try {
            if (ioQueue.isEmpty()) {
                return;
            }
            if (schedulingPolicy != null) {
                if (schedulingPolicy instanceof SCANDiskPolicy scan) {
                    scan.setDirection(headDirection);
                    selected = scan.selectNext(ioQueue, headPosition, totalBlocks);
                    scanBounced = scan.didBounceOnLastSelect();
                    scanBoundary = scan.getLastBounceBoundary();
                } else if (schedulingPolicy instanceof CSCANDiskPolicy cscan) {
                    selected = cscan.selectNext(ioQueue, headPosition, totalBlocks);
                    cscanWrapped = cscan.didWrapOnLastSelect();
                } else if (schedulingPolicy instanceof LOOKDiskPolicy look) {
                    look.setDirection(headDirection);
                    selected = look.selectNext(ioQueue, headPosition, totalBlocks);
                } else if (schedulingPolicy instanceof CLOOKDiskPolicy clook) {
                    selected = clook.selectNext(ioQueue, headPosition, totalBlocks);
                    if (selected != null && selected.getTargetBlock() < headPosition) {
                        clookWrapped = true;
                    }
                } else {
                    selected = schedulingPolicy.selectNext(ioQueue, headPosition, totalBlocks);
                }
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
            pendingBoundaryBlock = -1;
            pendingWrapJumpToZero = false;

            int targetPos = normalizeTargetPosition(selected.getTargetBlock());
            if (scanBounced && scanBoundary >= 0 && headPosition != scanBoundary) {
                pendingBoundaryBlock = scanBoundary;
                headDirection = scanBoundary >= headPosition ? HeadDirection.UP : HeadDirection.DOWN;
            } else if (cscanWrapped) {
                pendingBoundaryBlock = totalBlocks - 1;
                pendingWrapJumpToZero = true;
                headDirection = HeadDirection.UP;
            } else if (clookWrapped) {
                // C-LOOK salta directo al bloque menor sin contar el salto de regreso
                headPosition = targetPos;
                headDirection = HeadDirection.UP;
            } else if (targetPos != headPosition) {
                headDirection = targetPos > headPosition ? HeadDirection.UP : HeadDirection.DOWN;
            }

            // Reordenar el resto de la cola a partir del destino que alcanzará esta solicitud
            queueMutex.acquireUninterruptibly();
            try {
                reorderQueueFromAnchorLocked(targetPos,
                        cscanWrapped || clookWrapped ? HeadDirection.UP
                                : (scanBounced ? headDirection.opposite() : headDirection));
            } finally {
                queueMutex.release();
            }

            String policyName = schedulingPolicy != null ? schedulingPolicy.getPolicyType().name() : "FIFO";
            String procName = selected.getPcb() != null ? selected.getPcb().getName() : ("Req#" + selected.getId());
            emitEvent("El disco selecciona la solicitud de " + procName + " hacia el bloque "
                    + selected.getTargetBlock() + " (" + policyName + ", dir " + headDirection.getSymbol() + ")");
        }
    }

    /**
     * Reordena {@code ioQueue} de forma instantánea (0 ticks) según la política activa,
     * sin perder ninguna solicitud en cola.
     */
    private void reorderQueueLocked() {
        int anchorHead = activeRequest != null
                ? normalizeTargetPosition(activeRequest.getTargetBlock())
                : headPosition;
        reorderQueueFromAnchorLocked(anchorHead, headDirection);
    }

    private void reorderQueueFromAnchorLocked(int anchorHead, HeadDirection anchorDir) {
        if (ioQueue.size() <= 1 || schedulingPolicy == null) {
            return;
        }
        DiskSchedulingPolicy tempPolicy = DiskSchedulerFactory.create(schedulingPolicy.getPolicyType());
        if (tempPolicy instanceof SCANDiskPolicy scan) {
            scan.setDirection(anchorDir);
        } else if (tempPolicy instanceof LOOKDiskPolicy look) {
            look.setDirection(anchorDir);
        }

        MyLinkedList<IORequest> remaining = new MyLinkedList<>();
        while (!ioQueue.isEmpty()) {
            remaining.append(ioQueue.get(0));
            ioQueue.deleteFirst();
        }

        int simulatedHead = anchorHead;
        while (!remaining.isEmpty()) {
            IORequest next = tempPolicy.selectNext(remaining, simulatedHead, totalBlocks);
            if (next == null) {
                next = remaining.get(0);
            }
            remaining.remove(next);
            ioQueue.append(next);
            simulatedHead = normalizeTargetPosition(next.getTargetBlock());
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
    // GETTERS, SETTERS (CAMBIO DE POLÍTICA EN CALIENTE EN 0 TICKS) Y SNAPSHOT
    // =========================================================================

    /**
     * Cambia la política de planificación en caliente (0 ticks) y reordena
     * inmediatamente la cola de E/S sin perder solicitudes.
     */
    public void setSchedulingPolicy(DiskSchedulingPolicy policy) {
        if (policy == null) return;
        queueMutex.acquireUninterruptibly();
        try {
            this.schedulingPolicy = policy;
            if (this.schedulingPolicy instanceof SCANDiskPolicy scan) {
                scan.setDirection(headDirection);
            } else if (this.schedulingPolicy instanceof LOOKDiskPolicy look) {
                look.setDirection(headDirection);
            }
            reorderQueueLocked();
        } finally {
            queueMutex.release();
        }
        emitEvent("Política de planificación de disco cambiada a " + policy.getPolicyType());
    }

    public void setSchedulingPolicy(DiskSchedulingPolicyType policyType) {
        setSchedulingPolicy(DiskSchedulerFactory.create(policyType));
    }

    public DiskSchedulingPolicy getSchedulingPolicy() {
        return schedulingPolicy;
    }

    public DiskSchedulingPolicyType getPolicyType() {
        return schedulingPolicy != null ? schedulingPolicy.getPolicyType() : DiskSchedulingPolicyType.FIFO;
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
        pendingBoundaryBlock = -1;
        pendingWrapJumpToZero = false;
        totalHeadMovement = 0;
        completedRequestsCount = 0;
        totalWaitTimeCycles = 0;
        nextRequestId = 1;
    }
}
