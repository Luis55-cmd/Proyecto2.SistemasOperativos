package com.avilaos.core.scheduler;

import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.structures.MyQueue;

/**
 * Planificador Round Robin con quantum de tiempo configurable.
 * Desaloja al proceso cuando consume su cuota de tiempo y lo envía al final de la cola de listos.
 * 
 * Requisitos: RF §1, RF §3, PLA-03, PLA-04.
 */
public class RoundRobinScheduler implements IScheduler {

    private final MyQueue<ProcessControlBlock> readyQueue;
    private int quantum;
    private int currentQuantumRemaining;

    public RoundRobinScheduler() {
        this(5); // Quantum por defecto de 5 ciclos
    }

    public RoundRobinScheduler(int quantum) {
        this.readyQueue = new MyQueue<>();
        this.quantum = Math.max(1, quantum);
        this.currentQuantumRemaining = this.quantum;
    }

    @Override
    public ProcessControlBlock scheduleNext(MyQueue<ProcessControlBlock> queue, ProcessControlBlock currentRunning) {
        MyQueue<ProcessControlBlock> targetQueue = (queue != null) ? queue : this.readyQueue;

        // Si hay un proceso en ejecución
        if (currentRunning != null && currentRunning.getState() == ProcessState.EJECUTANDO) {
            // Si consumió todo su quantum de tiempo
            if (currentQuantumRemaining <= 0) {
                // Desalojo: vuelve a la cola de listos
                currentRunning.setState(ProcessState.LISTO);
                targetQueue.enqueue(currentRunning);
                currentRunning = null;
            } else {
                // Conserva la CPU dentro de su quantum
                return currentRunning;
            }
        }

        // Si la CPU quedó libre o fue desalojada
        if (!targetQueue.isEmpty()) {
            ProcessControlBlock next = targetQueue.dequeue();
            next.setState(ProcessState.EJECUTANDO);
            this.currentQuantumRemaining = this.quantum; // Reinicia el quantum para el nuevo proceso
            return next;
        }

        return null;
    }

    @Override
    public void addProcess(ProcessControlBlock process) {
        if (process != null) {
            process.setState(ProcessState.LISTO);
            this.readyQueue.enqueue(process);
        }
    }

    @Override
    public void removeProcess(ProcessControlBlock process) {
        if (process != null && !readyQueue.isEmpty()) {
            int count = readyQueue.size();
            for (int i = 0; i < count; i++) {
                ProcessControlBlock current = readyQueue.dequeue();
                if (current.getId() != process.getId()) {
                    readyQueue.enqueue(current);
                }
            }
        }
    }

    @Override
    public MyQueue<ProcessControlBlock> getReadyQueue() {
        return this.readyQueue;
    }

    @Override
    public SchedulingPolicyType getType() {
        return SchedulingPolicyType.ROUND_ROBIN;
    }

    @Override
    public void onClockTick() {
        if (this.currentQuantumRemaining > 0) {
            this.currentQuantumRemaining--;
        }
    }

    public int getQuantum() {
        return quantum;
    }

    public void setQuantum(int quantum) {
        this.quantum = Math.max(1, quantum);
        // Decisión D-10 / A29: recortar el quantum en curso si excede el nuevo límite
        if (this.currentQuantumRemaining > this.quantum) {
            this.currentQuantumRemaining = this.quantum;
        }
    }

    public int getCurrentQuantumRemaining() {
        return currentQuantumRemaining;
    }

    public void resetQuantum() {
        this.currentQuantumRemaining = this.quantum;
    }
}
