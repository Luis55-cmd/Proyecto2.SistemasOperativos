package com.avilaos.core.scheduler;

import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.structures.MyQueue;

/**
 * Planificador FCFS (First-Come, First-Served).
 * Atiende a los procesos en orden estricto de llegada (FIFO no apropiativo).
 * 
 * Requisitos: RF §1, RF §3, PLA-01.
 */
public class FcfsScheduler implements IScheduler {

    private final MyQueue<ProcessControlBlock> readyQueue;

    public FcfsScheduler() {
        this.readyQueue = new MyQueue<>();
    }

    @Override
    public ProcessControlBlock scheduleNext(MyQueue<ProcessControlBlock> queue, ProcessControlBlock currentRunning) {
        MyQueue<ProcessControlBlock> targetQueue = (queue != null) ? queue : this.readyQueue;

        // FCFS es no apropiativo: si el proceso actual sigue ejecutándose, retiene la CPU
        if (currentRunning != null && currentRunning.getState() == ProcessState.EJECUTANDO) {
            return currentRunning;
        }

        // Si la CPU está libre o el proceso actual terminó/se bloqueó
        if (!targetQueue.isEmpty()) {
            ProcessControlBlock next = targetQueue.dequeue();
            next.setState(ProcessState.EJECUTANDO);
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
        return SchedulingPolicyType.FCFS;
    }

    @Override
    public void onClockTick() {
        // FCFS no requiere acción por ciclo
    }
}
