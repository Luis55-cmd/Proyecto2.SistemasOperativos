package com.avilaos.sync;

import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;

/**
 * Simulador de latencia de red para ÁvilaOS en accesos a buffers remotos.
 * Permite simular el retardo de comunicación entre diferentes computadores del clúster.
 * 
 * Requisitos: D-11, BUF-14, BUF-15, Issue #25.
 */
public class NetworkSimulator {

    private int latencyCycles;

    public NetworkSimulator() {
        this(0);
    }

    public NetworkSimulator(int latencyCycles) {
        this.latencyCycles = Math.max(0, latencyCycles);
    }

    public int getLatencyCycles() {
        return latencyCycles;
    }

    public void setLatencyCycles(int latencyCycles) {
        this.latencyCycles = Math.max(0, latencyCycles);
    }

    public boolean isRemoteAccess(ProcessControlBlock pcb, Buffer buffer) {
        if (pcb == null || buffer == null) {
            return false;
        }
        return pcb.getComputerId() != buffer.getIdComp();
    }

    public boolean handleDelay(ProcessControlBlock pcb, Buffer buffer) {
        int delay = (buffer != null) ? buffer.getLatency() : this.latencyCycles;
        if (!isRemoteAccess(pcb, buffer) || delay <= 0) {
            pcb.setNetworkDelayRemaining(0);
            pcb.setNetworkDelayDone(true);
            return true; // Acceso local o sin latencia
        }

        // Si ya completó la latencia requerida para esta solicitud, puede proceder
        if (pcb.isNetworkDelayDone()) {
            return true;
        }

        // Si aún no ha iniciado la espera de latencia, se inicializa y bloquea
        if (pcb.getNetworkDelayRemaining() <= 0) {
            pcb.setNetworkDelayRemaining(delay);
        }
        pcb.setNetworkDelayDone(false);
        pcb.setState(ProcessState.BLOQUEADO);
        pcb.setBlockReason(BlockReason.NETWORK_LATENCY);
        return false; // Proceso bloqueado, no permitir operación de buffer durante la espera
    }

    /**
     * Avanza un tick en la demora de red para un proceso pendiente.
     * Si la demora se completa en este ciclo, marca networkDelayDone como true.
     *
     * @param pcb Proceso bloqueado esperando latencia de red.
     * @return true si la demora de red se completó y debe retornar a LISTO; false si aún debe esperar.
     */
    public boolean stepDelay(ProcessControlBlock pcb) {
        if (pcb == null || pcb.getState() == ProcessState.SALIENTE) {
            return false;
        }
        if (pcb.getNetworkDelayRemaining() > 0) {
            pcb.decrementNetworkDelay();
        }
        if (pcb.getNetworkDelayRemaining() <= 0) {
            pcb.setNetworkDelayRemaining(0);
            pcb.setNetworkDelayDone(true);
            return true;
        }
        return false;
    }
}
