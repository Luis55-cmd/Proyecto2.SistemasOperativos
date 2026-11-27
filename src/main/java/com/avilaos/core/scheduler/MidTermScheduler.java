package com.avilaos.core.scheduler;

import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.structures.MyQueue;
import com.avilaos.disk.SimulatedDisk;
import com.avilaos.memory.MemoryManager;

/**
 * Planificador a mediano plazo de ÁvilaOS (Proyecto 2).
 *
 * <p>Decide qué proceso BLOQUEADO suspender cuando la memoria principal (RAM) no alcanza,
 * y qué proceso SUSPENDIDO_LISTO reactivar cuando se libera espacio en RAM.</p>
 *
 * <p><b>Criterio de selección de víctima para suspender</b> (justificación para la defensa):
 * Siguiendo la convención de prioridades de ÁvilaOS (1 = baja, 10 = alta), se elige el proceso
 * Bloqueado con menor prioridad (menor valor numérico) que no esté ya en transferencia de swap-out.
 * En caso de empate, se selecciona el que lleva más tiempo bloqueado (mayor {@code blockedWaitTicks})
 * y luego mayor {@code waitingTime}. Así se libera RAM afectando al proceso menos urgente.</p>
 *
 * <p><b>Criterio de selección para reactivar</b>:
 * Se elige el proceso SUSPENDIDO_LISTO con mayor prioridad (mayor valor numérico) que quepa en la
 * RAM disponible y no esté ya en transferencia de swap-in. En caso de empate, se selecciona el que
 * tiene mayor tiempo acumulado de espera (mayor {@code waitingTime}).</p>
 */
public class MidTermScheduler {

    /**
     * Selecciona el proceso Bloqueado candidato a ser suspendido (swap-out).
     *
     * @param blockedQueue Cola de procesos bloqueados del kernel.
     * @param disk         Disco simulado para verificar disponibilidad en el área de intercambio.
     * @return PCB de la víctima seleccionada, o {@code null} si no hay candidato viable.
     */
    public ProcessControlBlock selectVictimToSuspend(MyQueue<ProcessControlBlock> blockedQueue,
                                                     SimulatedDisk disk) {
        if (blockedQueue == null || blockedQueue.isEmpty() || disk == null) {
            return null;
        }

        ProcessControlBlock victim = null;

        for (int i = 0; i < blockedQueue.size(); i++) {
            ProcessControlBlock p = blockedQueue.get(i);
            if (p == null || p.getState() != ProcessState.BLOQUEADO) continue;
            // Ignorar procesos que ya están en tránsito de swap-out o swap-in
            if (p.getBlockReason() == BlockReason.SWAPPING_OUT
                    || p.getBlockReason() == BlockReason.SWAPPING_IN) {
                continue;
            }

            int mem = p.getAssignedMemory() > 0 ? p.getAssignedMemory() : p.getRequiredMemory();
            if (mem <= 0) continue;

            // Verificar si hay bloques suficientes en el área de intercambio
            if (!disk.hasAvailableSwapBlocks(mem)) continue;

            if (victim == null) {
                victim = p;
                continue;
            }

            // Criterio 1: Menor prioridad (en ÁvilaOS: 1 = baja, 10 = alta -> menor número = menor prioridad)
            if (p.getPriority() < victim.getPriority()) {
                victim = p;
            } else if (p.getPriority() == victim.getPriority()) {
                // Criterio 2 (desempate): Más tiempo bloqueado
                if (p.getBlockedWaitTicks() > victim.getBlockedWaitTicks()) {
                    victim = p;
                } else if (p.getBlockedWaitTicks() == victim.getBlockedWaitTicks()) {
                    if (p.getWaitingTime() > victim.getWaitingTime()) {
                        victim = p;
                    }
                }
            }
        }

        return victim;
    }

    /**
     * Selecciona el proceso SUSPENDIDO_LISTO candidato a ser reactivado (swap-in).
     *
     * @param suspendedReadyQueue Cola de procesos listos/suspendidos.
     * @param memoryManager       Administrador de memoria RAM.
     * @return PCB del proceso a reactivar, o {@code null} si ninguno cabe en RAM.
     */
    public ProcessControlBlock selectProcessToReactivate(MyQueue<ProcessControlBlock> suspendedReadyQueue,
                                                         MemoryManager memoryManager) {
        if (suspendedReadyQueue == null || suspendedReadyQueue.isEmpty() || memoryManager == null) {
            return null;
        }

        ProcessControlBlock best = null;

        for (int i = 0; i < suspendedReadyQueue.size(); i++) {
            ProcessControlBlock p = suspendedReadyQueue.get(i);
            if (p == null || p.getState() != ProcessState.SUSPENDIDO_LISTO) continue;
            // Ignorar procesos que ya iniciaron su solicitud de swap-in
            if (p.getBlockReason() == BlockReason.SWAPPING_IN) continue;

            int mem = p.getRequiredMemory() > 0 ? p.getRequiredMemory() : p.getAssignedMemory();
            if (mem <= 0) continue;

            // Verificar si cabe en la RAM disponible
            if (!memoryManager.hasAvailableMemory(mem)) continue;

            if (best == null) {
                best = p;
                continue;
            }

            // Criterio 1: Mayor prioridad (en ÁvilaOS: 1 = baja, 10 = alta -> mayor número = mayor prioridad)
            if (p.getPriority() > best.getPriority()) {
                best = p;
            } else if (p.getPriority() == best.getPriority()) {
                // Criterio 2 (desempate): Más tiempo en espera
                if (p.getWaitingTime() > best.getWaitingTime()) {
                    best = p;
                }
            }
        }

        return best;
    }
}
