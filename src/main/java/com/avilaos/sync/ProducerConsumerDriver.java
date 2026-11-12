package com.avilaos.sync;

import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ExecutionMode;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;

/**
 * Controlador de sincronización para procesos Productor y Consumidor.
 * Implementa el algoritmo clásico de Stallings por fases de ciclo
 * e integra el cambio de modo SYSTEM / USER y latencia de red.
 * 
 * Requisitos: D-05, D-06, D-11, SEM-02 a SEM-09, Issue #24, Issue #25.
 */
public class ProducerConsumerDriver {

    private final NetworkSimulator networkSimulator;
    public interface IEventListener { void accept(String message); }
    private IEventListener eventListener;
    public void setEventListener(IEventListener listener) { this.eventListener = listener; }
    private void event(String message) { if (eventListener != null) eventListener.accept(message); }

    public ProducerConsumerDriver(int latencia) {
        this.networkSimulator = new NetworkSimulator(latencia);
    }

    public int getLatencia() {
        return networkSimulator.getLatencyCycles();
    }

    public void setLatencia(int latencia) {
        this.networkSimulator.setLatencyCycles(latencia);
    }

    public NetworkSimulator getNetworkSimulator() {
        return networkSimulator;
    }

    public boolean stepNetworkDelay(ProcessControlBlock pcb) {
        return this.networkSimulator != null && this.networkSimulator.stepDelay(pcb);
    }

    public void blockProcess(ProcessControlBlock pcb, BlockReason reason) {
        pcb.setState(ProcessState.BLOQUEADO);
        pcb.setBlockReason(reason);
        pcb.setMode(ExecutionMode.USER);
    }

    /** Una llamada ejecuta a lo sumo una instrucción. Los waits avanzan antes
     * de bloquear; la fase siguiente queda guardada en el PCB para reanudar.
     */
    public boolean executeCycle(ProcessControlBlock pcb, Buffer buffer) {
        if (pcb == null || buffer == null) return false;
        synchronized (buffer) {
            if (pcb.getState() != ProcessState.EJECUTANDO) return false;
            boolean producer = pcb.getType() == ProcessType.PRODUCER;
            if (!producer && pcb.getType() != ProcessType.CONSUMER)
                throw new IllegalArgumentException("El protocolo requiere productor o consumidor");
            if (pcb.getRequiredElements() <= 0 || pcb.getCyclesPerOperation() <= 0)
                throw new IllegalArgumentException("Elementos y ciclos deben ser positivos");
            // Un consumidor empieza esperando un elemento; trabaja después de extraerlo.
            if (!producer && pcb.getProgramCounter() == 0)
                pcb.setSyncPhase(ProcessControlBlock.SyncPhase.WAIT_SLOT);

            if (pcb.getSyncPhase() == ProcessControlBlock.SyncPhase.WORK) {
                pcb.setMode(ExecutionMode.USER);
                advanceInstruction(pcb);
                pcb.setOperationWorkDone(pcb.getOperationWorkDone() + 1);
                if (pcb.getOperationWorkDone() >= pcb.getCyclesPerOperation()) {
                    pcb.setOperationWorkDone(0);
                    if (!producer) completeElement(pcb);
                    if (pcb.getState() != ProcessState.SALIENTE)
                        pcb.setSyncPhase(ProcessControlBlock.SyncPhase.WAIT_SLOT);
                }
                updateRemaining(pcb);
                return pcb.getState() == ProcessState.EJECUTANDO;
            }
            // La red espera antes del primer wait, fuera de la sección crítica.
            if (pcb.getSyncPhase() == ProcessControlBlock.SyncPhase.WAIT_SLOT
                    && !networkSimulator.handleDelay(pcb, buffer)) return false;

            pcb.setMode(ExecutionMode.KERNEL);
            switch (pcb.getSyncPhase()) {
                case WAIT_SLOT -> {
                    buffer.semWait(pcb, producer ? Buffer.SemaphoreKind.VACIOS : Buffer.SemaphoreKind.LLENOS);
                    pcb.setSyncPhase(ProcessControlBlock.SyncPhase.WAIT_MUTEX);
                }
                case WAIT_MUTEX -> {
                    buffer.semWait(pcb, Buffer.SemaphoreKind.MUTEX);
                    pcb.setSyncPhase(ProcessControlBlock.SyncPhase.ACCESS);
                }
                case ACCESS -> {
                    if (!pcb.holdsBufferMutex() || pcb.getReservedBufferSlot() == null)
                        throw new IllegalStateException("Acceso sin permisos");
                    if (producer) buffer.produce(pcb.getId());
                    else buffer.consume();
                    pcb.recordBufferOperation(buffer.getId());
                    String bufName = (buffer.getId() > 0 ? "B" + buffer.getId() : "Buffer " + buffer.getId());
                    String modoAcceso = (pcb.getComputerId() != buffer.getIdComp()) ? "remota" : "local";
                    event("Proceso " + pcb.getName() + " accede al buffer " + bufName + " de forma " + modoAcceso);
                    advanceInstruction(pcb);
                    pcb.setSyncPhase(ProcessControlBlock.SyncPhase.SIGNAL_MUTEX);
                }
                case SIGNAL_MUTEX -> {
                    buffer.semSignal(pcb, Buffer.SemaphoreKind.MUTEX);
                    pcb.setSyncPhase(ProcessControlBlock.SyncPhase.SIGNAL_SLOT);
                }
                case SIGNAL_SLOT -> {
                    buffer.semSignal(pcb, producer ? Buffer.SemaphoreKind.LLENOS : Buffer.SemaphoreKind.VACIOS);
                    pcb.setNetworkDelayDone(false);
                    pcb.setSyncPhase(ProcessControlBlock.SyncPhase.WORK);
                    if (producer) completeElement(pcb);
                }
                default -> throw new IllegalStateException("Fase inesperada");
            }
            updateRemaining(pcb);
            return pcb.getState() == ProcessState.EJECUTANDO;
        }
    }

    private void advanceInstruction(ProcessControlBlock pcb) {
        pcb.advanceInstruction();
    }

    private void updateRemaining(ProcessControlBlock pcb) {
        int total = Math.multiplyExact(pcb.getRequiredElements(), Math.addExact(pcb.getCyclesPerOperation(), 5));
        pcb.setTotalInstructions(total);
        pcb.setRemainingTime(Math.max(0, total - pcb.getProgramCounter()));
    }

    private void completeElement(ProcessControlBlock pcb) {
        pcb.incrementProcessedElements();
        if (pcb.getProcessedElements() >= pcb.getRequiredElements()) {
            pcb.setState(ProcessState.SALIENTE);
            pcb.setBlockReason(BlockReason.NONE);
        }
    }

    public static void wakeUpProcess(ProcessControlBlock pcb) {
        if (pcb == null || pcb.getState() == ProcessState.SALIENTE) {
            return;
        }
        // Marca el proceso como LISTO y limpia la razón de bloqueo.
        // El kernel (ComputerKernel.blockedToReady) gestiona las colas en el siguiente ciclo.
        // No se llama aquí a SimulationController para respetar la separación de capas:
        // sync → kernel (permitido), sync → simulation (no permitido).
        pcb.setState(ProcessState.LISTO);
        pcb.setBlockReason(BlockReason.NONE);
    }
}
