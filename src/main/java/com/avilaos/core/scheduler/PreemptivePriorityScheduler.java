package com.avilaos.core.scheduler;

import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.core.structures.MyQueue;

/**
 * Planificador por Prioridades Apropiativo (Preemptive Priority).
 * Compara la prioridad del proceso de mayor urgencia en la cola de listos con el que está en CPU.
 * Si el nuevo tiene mayor prioridad (por convenio: mayor número = mayor prioridad),
 * desaloja al actual a la cola de listos y asigna la CPU al nuevo.
 * 
 * Requisitos: RF §1, RF §3, PLA-05.
 */
public class PreemptivePriorityScheduler implements IScheduler {

    private final MyQueue<ProcessControlBlock> readyQueue;
    private boolean higherNumberIsHigherPriority;

    // Se utiliza la clase abstracta MyComparator propia en lugar de java.util.Comparator
    private final MyLinkedList.MyComparator<ProcessControlBlock> priorityComparator = new MyLinkedList.MyComparator<ProcessControlBlock>() {
        @Override
        public int compare(ProcessControlBlock p1, ProcessControlBlock p2) {
            int cmp;
            if (higherNumberIsHigherPriority) {
                // Mayor número = mayor prioridad (ej: prioridad 10 antes que prioridad 1)
                cmp = Integer.compare(p2.getPriority(), p1.getPriority());
            } else {
                // Menor número = mayor prioridad (ej: prioridad 1 antes que prioridad 10)
                cmp = Integer.compare(p1.getPriority(), p2.getPriority());
            }
            if (cmp == 0) {
                // Empate resuelto por FIFO (menor ID primero)
                return Integer.compare(p1.getId(), p2.getId());
            }
            return cmp;
        }
    };

    public PreemptivePriorityScheduler() {
        this(true); // Por defecto: mayor número = mayor prioridad
    }

    public PreemptivePriorityScheduler(boolean higherNumberIsHigherPriority) {
        this.readyQueue = new MyQueue<>();
        this.higherNumberIsHigherPriority = higherNumberIsHigherPriority;
    }

    @Override
    public ProcessControlBlock scheduleNext(MyQueue<ProcessControlBlock> queue, ProcessControlBlock currentRunning) {
        MyQueue<ProcessControlBlock> targetQueue = (queue != null) ? queue : this.readyQueue;

        // El kernel entrega su propia cola, que puede contener nuevas admisiones sin ordenar.
        // Ordenar antes de peek/dequeue cubre CPU libre y apropiación, y deja visible el orden real.
        sortQueue(targetQueue);

        // Si hay un proceso en CPU, verificamos si algún proceso en cola tiene mayor prioridad (desalojo)
        if (currentRunning != null && currentRunning.getState() == ProcessState.EJECUTANDO) {
            if (!targetQueue.isEmpty()) {
                ProcessControlBlock candidate = targetQueue.peek();
                if (isStrictlyHigherPriority(candidate, currentRunning)) {
                    // Desalojo: el actual vuelve a la cola y se reordena
                    currentRunning.setState(ProcessState.LISTO);
                    targetQueue.enqueue(currentRunning);
                    sortQueue(targetQueue);

                    ProcessControlBlock next = targetQueue.dequeue();
                    next.setState(ProcessState.EJECUTANDO);
                    return next;
                }
            }
            return currentRunning;
        }

        // Si la CPU está libre
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
            sortQueue(this.readyQueue);
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
        return SchedulingPolicyType.PRIORITY_PREEMPTIVE;
    }

    @Override
    public void onClockTick() {
        // No requiere acción especial por ciclo
    }

    private boolean isStrictlyHigherPriority(ProcessControlBlock p1, ProcessControlBlock p2) {
        if (p1 == null) return false;
        if (p2 == null) return true;
        if (higherNumberIsHigherPriority) {
            return p1.getPriority() > p2.getPriority();
        } else {
            return p1.getPriority() < p2.getPriority();
        }
    }

    public void sortReadyQueue(MyQueue<ProcessControlBlock> queue) {
        sortQueue(queue);
    }

    private void sortQueue(MyQueue<ProcessControlBlock> queue) {
        if (queue == null || queue.size() <= 1) {
            return;
        }
        MyLinkedList<ProcessControlBlock> list = new MyLinkedList<>();
        while (!queue.isEmpty()) {
            list.add(queue.dequeue());
        }
        
        list.sort(this.priorityComparator);
        
        // Uso del iterador manual instanciado desde la clase concreta (sin for-each)
        MyLinkedList<ProcessControlBlock>.MyLinkedListIterator iter = list.iterator();
        while (iter.hasNext()) {
            queue.enqueue(iter.next());
        }
    }

    public boolean isHigherNumberIsHigherPriority() {
        return higherNumberIsHigherPriority;
    }

    public void setHigherNumberIsHigherPriority(boolean higherNumberIsHigherPriority) {
        this.higherNumberIsHigherPriority = higherNumberIsHigherPriority;
        sortQueue(this.readyQueue);
    }
}