package com.avilaos.core.scheduler;

import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.structures.MyQueue;

/**
 * Interfaz obligatoria para las políticas de planificación de CPU.
 * 
 * Permite cambiar la política de cualquier computador en tiempo de ejecución
 * y agregar nuevas políticas sin modificar el código del núcleo.
 * 
 * Requisito: RF §1 y RF §3.
 */
public interface IScheduler {

    /**
     * Selecciona el siguiente proceso a ejecutar.
     * 
     * @param readyQueue Cola de listos del computador.
     * @param currentRunning Proceso actualmente en ejecución (puede ser null).
     * @return El proceso seleccionado para la CPU.
     */
    ProcessControlBlock scheduleNext(MyQueue<ProcessControlBlock> readyQueue, ProcessControlBlock currentRunning);

    /**
     * Añade un proceso a la cola de listos y reordena según la política.
     * 
     * @param process Proceso a añadir.
     */
    void addProcess(ProcessControlBlock process);

    /**
     * Remueve un proceso de la cola de listos.
     * 
     * @param process Proceso a remover.
     */
    void removeProcess(ProcessControlBlock process);

    /**
     * Retorna la cola de listos administrada por este planificador.
     * 
     * @return Cola de listos.
     */
    MyQueue<ProcessControlBlock> getReadyQueue();

    /**
     * Retorna el tipo de política de este planificador.
     * 
     * @return Tipo de política (FCFS, EDF, ROUND_ROBIN, PRIORITY_PREEMPTIVE).
     */
    SchedulingPolicyType getType();

    /**
     * Notificación de paso de un ciclo de reloj para el planificador
     * (útil para el decremento del quantum en Round Robin).
     */
    void onClockTick();
}
