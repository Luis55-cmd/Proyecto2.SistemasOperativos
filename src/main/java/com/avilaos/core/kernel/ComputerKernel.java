package com.avilaos.core.kernel;

import com.avilaos.hardware.Computer;
import com.avilaos.hardware.CPU;
import com.avilaos.core.structures.*;
import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ExecutionMode;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.scheduler.*;
import com.avilaos.disk.IORequest;
import com.avilaos.disk.SimulatedDisk;

public class ComputerKernel {

    private final MyLinkedList<ProcessControlBlock> processList;
    private final MyQueue<ProcessControlBlock> blockedQueue;
    private final MyQueue<ProcessControlBlock> newQueue;
    private final MyQueue<ProcessControlBlock> readyQueue;
    private final MyQueue<ProcessControlBlock> endedQueue;
    private final MyQueue<ProcessControlBlock> suspendedBlockedQueue;
    private final MyQueue<ProcessControlBlock> suspendedReadyQueue;
    private final GlobalClock clock;
    private IScheduler activeScheduler;
    private SchedulingPolicyType currentPolicy;
    public interface IEventListener { void accept(String message); }
    private IEventListener eventListener;

    // [P2] Planificador a mediano plazo y disco
    private SimulatedDisk disk;
    private final MidTermScheduler midTermScheduler = new MidTermScheduler();
    private int suspensionCount = 0;
    private int reactivationCount = 0;
    private int nextSwapRequestId = 50000;

    public void setEventListener(IEventListener listener) { this.eventListener = listener; }
    private void event(String message) { if (eventListener != null) eventListener.accept(message); }
    private String proceso(ProcessControlBlock p) { return p.getName() + " (ID " + p.getId() + ", computador " + p.getComputerId() + ")"; }
    private String formatBuffer(ProcessControlBlock p) {
        String b = p.getTargetBufferId();
        if (b == null) return "B1";
        if (b.startsWith("B") || b.startsWith("Buffer ")) return b;
        return "B" + b;
    }
    private String motivo(ProcessControlBlock p) {
        return switch (p.getBlockReason()) {
            case WAITING_MUTEX -> "en el sem\u00e1foro 'mutex' del buffer " + formatBuffer(p);
            case WAITING_EMPTY_BUFFER -> "en el sem\u00e1foro 'lleno' del buffer " + formatBuffer(p) + " (sin elementos (sem\u00e1foro llenos))";
            case WAITING_FULL_BUFFER -> "en el sem\u00e1foro 'vac\u00edo' del buffer " + formatBuffer(p) + " (sin espacios (sem\u00e1foro vac\u00edos))";
            case NETWORK_LATENCY -> "latencia de red para buffer " + formatBuffer(p);
            case WAITING_IO -> "espera de E/S";
            case WAITING_DISK -> "espera de disco";
            case SWAPPING_OUT -> "transferencia a swap (swap-out)";
            case SWAPPING_IN -> "transferencia desde swap (swap-in)";
            default -> "motivo no especificado";
        };
    }

    public ComputerKernel(MyLinkedList<ProcessControlBlock> processList,
                          MyQueue<ProcessControlBlock> blockedQueue,
                          MyQueue<ProcessControlBlock> newQueue,
                          MyQueue<ProcessControlBlock> readyQueue,
                          MyQueue<ProcessControlBlock> endedQueue,
                          MyQueue<ProcessControlBlock> suspendedBlockedQueue,
                          MyQueue<ProcessControlBlock> suspendedReadyQueue,
                          GlobalClock clock) {
        this.processList = (processList != null) ? processList : new MyLinkedList<>();
        this.blockedQueue = (blockedQueue != null) ? blockedQueue : new MyQueue<>();
        this.newQueue = (newQueue != null) ? newQueue : new MyQueue<>();
        this.readyQueue = (readyQueue != null) ? readyQueue : new MyQueue<>();
        this.endedQueue = (endedQueue != null) ? endedQueue : new MyQueue<>();
        this.suspendedBlockedQueue = (suspendedBlockedQueue != null) ? suspendedBlockedQueue : new MyQueue<>();
        this.suspendedReadyQueue = (suspendedReadyQueue != null) ? suspendedReadyQueue : new MyQueue<>();
        this.clock = clock;
    }

    /**
     * Admisión de un proceso al computador (Planificador a largo plazo).
     * Si hay memoria suficiente en RAM, se admite a READY; de lo contrario,
     * permanece en NEW en espera de liberación de memoria.
     */
    public boolean admitProcess(Computer com, ProcessControlBlock p) {
        if (p == null || com == null) {
            return false;
        }

        p.setComputerId(com.getId());

        if (com.getMemoryManager() != null && com.getMemoryManager().hasAvailableMemory(p.getAssignedMemory())) {
            com.getMemoryManager().allocateMemory(p.getAssignedMemory());
            p.setState(ProcessState.LISTO);
            p.setBlockReason(BlockReason.NONE);
            this.newQueue.removeElement(p);
            this.readyQueue.enqueue(p);
            reorderReadyQueue(com.getPolitica(), false);
            return true;
        } else {
            p.setState(ProcessState.NUEVO);
            this.newQueue.enqueue(p);
            // [P2] Si la RAM no alcanza, el planificador a mediano plazo intenta suspender un proceso
            evaluateMidTermSuspension(com, p);
            return false;
        }
    }

    /**
     * Evalúa la admisión de procesos en espera en la cola de nuevos (FIFO estricto).
     * Se invoca automáticamente tras la liberación de memoria al terminar un proceso.
     * 
     * @param com Computador anfitrión.
     * @return Cantidad de procesos admitidos a la cola de listos.
     */
    public int evaluateNewQueueAdmission(Computer com) {
        if (com == null || this.newQueue == null || this.newQueue.isEmpty()) {
            return 0;
        }

        int admittedCount = 0;
        while (!this.newQueue.isEmpty()) {
            ProcessControlBlock nextNew = this.newQueue.peek();
            if (com.getMemoryManager() != null && com.getMemoryManager().hasAvailableMemory(nextNew.getAssignedMemory())) {
                this.newQueue.dequeue();
                com.getMemoryManager().allocateMemory(nextNew.getAssignedMemory());
                nextNew.setState(ProcessState.LISTO);
                nextNew.setBlockReason(BlockReason.NONE);
                this.readyQueue.enqueue(nextNew);
                event(proceso(nextNew) + " admitido desde nuevos tras liberarse RAM");
                admittedCount++;
            } else {
                // FIFO estricto: si el primer proceso en espera no cabe, intentar suspender para hacer espacio
                evaluateMidTermSuspension(com, nextNew);
                break;
            }
        }
        if (admittedCount > 0) {
            reorderReadyQueue(com.getPolitica(), false);
        }
        return admittedCount;
    }

    /**
     * Crea un proceso y gestiona su admisión formal a través del planificador a largo plazo.
     */
    public String gestionCreateProcess(Computer idComp, boolean asignacionManual, int memoryPt) {
        ProcessControlBlock p = new ProcessControlBlock();
        p.setMemoryPointer(memoryPt);
        return gestionCreateProcess(idComp, p);
    }

    public String gestionCreateProcess(Computer idComp, ProcessControlBlock p) {
        if (idComp == null || p == null) {
            return "Error: Computador o proceso inválido.";
        }

        if (idComp.getMemoryManager() != null && p.getAssignedMemory() > idComp.getMemoryManager().getTotalMemory()) {
            return "Error: La memoria solicitada (" + p.getAssignedMemory() + ") excede la RAM total (" 
                    + idComp.getMemoryManager().getTotalMemory() + ") de " + idComp.getName() + ".";
        }

        this.processList.append(p);

        boolean admitido = admitProcess(idComp, p);
        if (admitido) {
            return "Se ha creado el proceso y admitido a la cola de listos.";
        } else {
            return "Memoria insuficiente: Proceso en cola de NUEVOS en espera de RAM.";
        }
    }

    /**
     * Restaura un proceso desde un snapshot/JSON, ubicándolo directamente en su estado y cola
     * correspondiente y reservando memoria si se encuentra activo en RAM.
     */
    public void restoreProcess(Computer com, ProcessControlBlock p) {
        if (com == null || p == null) return;
        this.processList.append(p);

        if (p.getState() == ProcessState.EJECUTANDO
                || p.getState() == ProcessState.LISTO
                || p.getState() == ProcessState.BLOQUEADO) {
            if (com.getMemoryManager() != null) {
                com.getMemoryManager().allocateMemory(p.getAssignedMemory());
            }
        }

        switch (p.getState()) {
            case EJECUTANDO:
                if (com.getCpu() != null) {
                    com.getCpu().setPCB(p);
                    com.getCpu().setPC(p.getProgramCounter());
                    com.getCpu().setMAR(p.getMemoryAddressRegister());
                    com.getCpu().setMode(p.getMode() != null ? p.getMode() : ExecutionMode.USER);
                }
                break;
            case LISTO:
                this.readyQueue.enqueue(p);
                reorderReadyQueue(com.getPolitica(), false);
                break;
            case BLOQUEADO:
                this.blockedQueue.enqueue(p);
                break;
            case TERMINADO:
                this.endedQueue.enqueue(p);
                break;
            case SUSPENDIDO_LISTO:
                this.suspendedReadyQueue.enqueue(p);
                break;
            case SUSPENDIDO_BLOQUEADO:
                this.suspendedBlockedQueue.enqueue(p);
                break;
            case NUEVO:
            default:
                this.newQueue.enqueue(p);
                break;
        }
    }

    public boolean isMemoryTotallyUsed(Computer com, int memoria) {
        if (com == null || com.getMemoryManager() == null) {
            return false;
        }
        return com.getMemoryManager().hasAvailableMemory(memoria);
    }

    public void verificateRAMSpaceForNew(Computer com, ProcessControlBlock p) {
        if (com != null && p != null && com.getMemoryManager() != null) {
            if (com.getMemoryManager().hasAvailableMemory(p.getAssignedMemory())) {
                com.getMemoryManager().allocateMemory(p.getAssignedMemory());
                this.newQueue.removeElement(p);
                this.readyQueue.enqueue(p);
                p.setState(ProcessState.LISTO);
                reorderReadyQueue(com.getPolitica(), false);
            }
        }
    }

    /**
     * Despacha el siguiente proceso de la cola de listos o evalúa el desalojo del proceso en CPU.
     */
    public void dispatch(Computer com, ProcessControlBlock p) {
        if (com == null) {
            return;
        }

        ProcessControlBlock currentRunning = (p != null && p.getState() == ProcessState.EJECUTANDO) ? p : null;

        // Si la cola de listos está vacía y la CPU está libre, no hay nada que despachar
        if (this.readyQueue.isEmpty() && currentRunning == null) {
            if (com.getCpu() != null) {
                com.getCpu().setPCB(null);
            }
            return;
        }

        IScheduler scheduler = getOrUpdateScheduler(com);
        ProcessControlBlock next = scheduler.scheduleNext(this.readyQueue, currentRunning);

        if (next != null) {
            if (next != currentRunning) {
                if (currentRunning != null) event(com.getName() + " desaloja el proceso " + currentRunning.getName());
                event(com.getName() + " selecciona " + proceso(next) + " (política " + com.getPolitica() + ")");
            }
            // Guardar contexto del proceso desalojado si fue reemplazado por otro proceso
            if (currentRunning != null && currentRunning != next) {
                if (com.getCpu() != null) {
                    currentRunning.setProgramCounter(com.getCpu().getPC());
                    currentRunning.setMemoryAddressRegister(com.getCpu().getMAR());
                }
            }

            next.setState(ProcessState.EJECUTANDO);
            if (com.getCpu() != null) {
                com.getCpu().setPCB(next);
                com.getCpu().setPC(next.getProgramCounter());
                com.getCpu().setMAR(next.getMemoryAddressRegister());
                com.getCpu().setMode(next.getMode() != null ? next.getMode() : ExecutionMode.USER);
            }
        } else {
            if (com.getCpu() != null) {
                com.getCpu().setPCB(null);
            }
        }
    }

    private IScheduler getOrUpdateScheduler(Computer com) {
        SchedulingPolicyType policy = (com != null && com.getPolitica() != null)
                ? com.getPolitica()
                : SchedulingPolicyType.FCFS;

        if (this.activeScheduler == null || this.currentPolicy != policy) {
            this.currentPolicy = policy;
            switch (policy) {
                case FCFS -> this.activeScheduler = new FcfsScheduler();
                case ROUND_ROBIN -> this.activeScheduler = new RoundRobinScheduler(com != null ? com.getQuantum() : 3);
                case EDF -> this.activeScheduler = new EdfScheduler();
                case PRIORITY_PREEMPTIVE -> this.activeScheduler = new PreemptivePriorityScheduler();
                default -> this.activeScheduler = new FcfsScheduler();
            }
        } else if (policy == SchedulingPolicyType.ROUND_ROBIN && this.activeScheduler instanceof RoundRobinScheduler rr) {
            if (com != null && rr.getQuantum() != com.getQuantum()) {
                rr.setQuantum(com.getQuantum());
            }
        }
        return this.activeScheduler;
    }

    /**
     * Actualiza el quantum del planificador Round Robin activo en caliente.
     */
    private static final MyLinkedList.MyComparator<ProcessControlBlock> FIFO_COMPARATOR = new MyLinkedList.MyComparator<ProcessControlBlock>() {
        @Override
        public int compare(ProcessControlBlock p1, ProcessControlBlock p2) {
            int cmp = Integer.compare(p1.getArrivalCycle(), p2.getArrivalCycle());
            if (cmp == 0) {
                return Integer.compare(p1.getId(), p2.getId());
            }
            return cmp;
        }
    };

    public void onPolicyChanged(Computer com) {
        if (com == null) return;
        SchedulingPolicyType newPolicy = com.getPolitica() != null ? com.getPolitica() : SchedulingPolicyType.FCFS;
        getOrUpdateScheduler(com);
        reorderReadyQueue(newPolicy, true);
    }

    public void reorderReadyQueue(SchedulingPolicyType policy, boolean restoreFifoIfSequential) {
        SchedulingPolicyType effectivePolicy = (policy != null) ? policy : this.currentPolicy;
        if (this.readyQueue == null || this.readyQueue.size() <= 1 || effectivePolicy == null) {
            return;
        }
        if (effectivePolicy == SchedulingPolicyType.EDF) {
            if (this.activeScheduler instanceof EdfScheduler edf) {
                edf.sortReadyQueue(this.readyQueue);
            } else {
                new EdfScheduler().sortReadyQueue(this.readyQueue);
            }
        } else if (effectivePolicy == SchedulingPolicyType.PRIORITY_PREEMPTIVE) {
            if (this.activeScheduler instanceof PreemptivePriorityScheduler prio) {
                prio.sortReadyQueue(this.readyQueue);
            } else {
                new PreemptivePriorityScheduler().sortReadyQueue(this.readyQueue);
            }
        } else if (restoreFifoIfSequential
                && (effectivePolicy == SchedulingPolicyType.FCFS || effectivePolicy == SchedulingPolicyType.ROUND_ROBIN)) {
            MyLinkedList<ProcessControlBlock> list = new MyLinkedList<>();
            while (!this.readyQueue.isEmpty()) {
                list.add(this.readyQueue.dequeue());
            }
            list.sort(FIFO_COMPARATOR);
            MyLinkedList<ProcessControlBlock>.MyLinkedListIterator iter = list.iterator();
            while (iter.hasNext()) {
                this.readyQueue.enqueue(iter.next());
            }
        }
    }

    public void updateQuantum(int quantum) {
        if (this.activeScheduler instanceof RoundRobinScheduler rr) {
            rr.setQuantum(quantum);
        }
    }

    public void terminateFatherProcess(int fatherId) {
        if (this.processList == null) return;
        for (int i = 0; i < this.processList.size(); i++) {
            ProcessControlBlock proceso = this.processList.get(i);
            if (proceso != null && proceso.getIdFather() == fatherId) {
                ProcessState estadoActual = proceso.getState();
                if (estadoActual == ProcessState.BLOQUEADO) {
                    this.blockedQueue.removeElement(proceso);
                } else if (estadoActual == ProcessState.LISTO) {
                    this.readyQueue.removeElement(proceso);
                } else if (estadoActual == ProcessState.NUEVO) {
                    this.newQueue.removeElement(proceso);
                } else if (estadoActual == ProcessState.SUSPENDIDO_LISTO) {
                    this.suspendedReadyQueue.removeElement(proceso);
                } else if (estadoActual == ProcessState.SUSPENDIDO_BLOQUEADO) {
                    this.suspendedBlockedQueue.removeElement(proceso);
                }

                proceso.setState(ProcessState.TERMINADO);
                this.endedQueue.enqueue(proceso);

                this.processList.deletePos(i);
                i--;
            }
        }
    }

    public void terminateProcess(ProcessControlBlock p) {
        terminateProcess(null, p);
    }

    public void terminateProcess(Computer com, ProcessControlBlock p) {
        if (p == null || p.isTerminated()) {
            return;
        }

        if (disk != null && p.getReservedStart() >= 0) {
            disk.finishReservation(p.getReservedStart(), p.getId(), false);
            p.setReservedStart(-1);
        }
        event(proceso(p) + (p.getRemainingDeadline() <= 0 ? " terminó por vencimiento de deadline" : " terminó normalmente"));

        if (com != null) {
            com.registrarTerminacion(p, clock != null ? clock.getCycleCounter() : Math.max(0, p.getCompletionCycle()));
        }
        p.setTerminated(true);
        p.setState(ProcessState.TERMINADO);
        p.setBlockReason(BlockReason.NONE);

        this.blockedQueue.removeElement(p);
        this.readyQueue.removeElement(p);
        this.newQueue.removeElement(p);
        this.suspendedReadyQueue.removeElement(p);
        this.suspendedBlockedQueue.removeElement(p);

        this.endedQueue.removeElement(p); // Prevenir duplicados
        this.endedQueue.enqueue(p);

        // Desalojar CPU si el proceso terminado estaba corriendo en ella
        if (com != null && com.getCpu() != null
                && com.getCpu().getPCB() != null
                && com.getCpu().getPCB().getId() == p.getId()) {
            com.getCpu().setPCB(null);
        }

        // [P2] Liberar bloques de swap si el proceso ocupaba el área de intercambio
        if (disk != null) {
            disk.freeSwapBlocksByProcess(p.getId());
        }

        // Liberar la memoria RAM asignada al proceso (idempotente)
        int memToFree = p.getAssignedMemory();
        if (com != null && com.getMemoryManager() != null && memToFree > 0) {
            com.getMemoryManager().freeMemory(memToFree);
            p.setAssignedMemory(0); // Evitar doble liberación
            // Disparar admisión automática de la cola de NUEVOS
            evaluateNewQueueAdmission(com);
            // Intentar reactivar procesos suspendidos
            evaluateMidTermReactivation(com);
        } else {
            p.setAssignedMemory(0);
        }

        terminateFatherProcess(p.getId());
    }

    /**
     * Mueve un proceso a la cola de bloqueados y actualiza su estado.
     * Versión sin Computer: usada internamente por ProducerConsumerDriver.
     */
    public void blockProcess(ProcessControlBlock p) {
        if (p == null || p.getState() == ProcessState.TERMINADO) {
            return;
        }
        p.setState(ProcessState.BLOQUEADO);
        this.readyQueue.removeElement(p);
        this.blockedQueue.removeElement(p); // Prevenir duplicados
        this.blockedQueue.enqueue(p);
    }

    /**
     * Mueve un proceso a bloqueados y desaloja la CPU si el proceso estaba corriendo ahí.
     * El kernel es el único responsable de vaciar la CPU — no debe hacerlo la capa superior.
     *
     * @param com Computador anfitrión (para acceder a su CPU).
     * @param p   Proceso a bloquear.
     */
    public void blockProcess(Computer com, ProcessControlBlock p) {
        blockProcess(p); // lógica de colas
        // Desalojar CPU si el proceso bloqueado estaba corriendo en ella
        if (com != null && com.getCpu() != null
                && com.getCpu().getPCB() != null
                && com.getCpu().getPCB().getId() == p.getId()) {
            com.getCpu().setPCB(null);
        }
    }

    /**
     * Desbloquea un proceso y lo retorna a la cola de listos sin duplicados.
     * Acepta procesos en estado BLOQUEADO o LISTO (cuando wakeUpProcess ya actualizó el estado).
     */
    public void blockedToReady(ProcessControlBlock p) {
        if (p == null || p.getState() == ProcessState.TERMINADO) {
            return;
        }
        boolean estabaBloqueado = p.getState() == ProcessState.BLOQUEADO;
        this.blockedQueue.removeElement(p);
        p.setState(ProcessState.LISTO);
        p.setBlockReason(BlockReason.NONE);
        this.readyQueue.removeElement(p); // Prevenir duplicados
        this.readyQueue.enqueue(p);
        reorderReadyQueue(this.currentPolicy, false);
        if (estabaBloqueado) event(proceso(p) + " despertó y pasó a listos");
    }

// [P2-LEGACY]     public boolean placeBufferRAM(Buffer buffer, Computer com) {
// [P2-LEGACY]         if (buffer == null || com == null) {
// [P2-LEGACY]             return false;
// [P2-LEGACY]         }
// [P2-LEGACY]         int cantidad = buffer.getSizeInMemory();
// [P2-LEGACY]         if (cantidad <= 0) {
// [P2-LEGACY]             return false;
// [P2-LEGACY]         }
// [P2-LEGACY]         if (com.getRam() == null || com.getRam().getSpace() == null) {
// [P2-LEGACY]             return false;
// [P2-LEGACY]         }
// [P2-LEGACY] 
// [P2-LEGACY]         // 1. Validar disponibilidad contable previa
// [P2-LEGACY]         if (com.getMemoryManager() != null && !com.getMemoryManager().hasAvailableMemory(cantidad)) {
// [P2-LEGACY]             return false;
// [P2-LEGACY]         }
// [P2-LEGACY] 
// [P2-LEGACY]         // 2. Validar disponibilidad física de bloques contiguos en RAM
// [P2-LEGACY]         int[] space = com.getRam().getSpace();
// [P2-LEGACY]         int ramTotalSize = space.length;
// [P2-LEGACY]         int consecutiveFreeBlocks = 0;
// [P2-LEGACY]         int startIndex = -1;
// [P2-LEGACY]         for (int i = 0; i < ramTotalSize; i++) {
// [P2-LEGACY]             if (space[i] == 0) {
// [P2-LEGACY]                 if (consecutiveFreeBlocks == 0) {
// [P2-LEGACY]                     startIndex = i;
// [P2-LEGACY]                 }
// [P2-LEGACY]                 consecutiveFreeBlocks++;
// [P2-LEGACY]                 if (consecutiveFreeBlocks == cantidad) {
// [P2-LEGACY]                     break;
// [P2-LEGACY]                 }
// [P2-LEGACY]             } else {
// [P2-LEGACY]                 consecutiveFreeBlocks = 0;
// [P2-LEGACY]                 startIndex = -1;
// [P2-LEGACY]             }
// [P2-LEGACY]         }
// [P2-LEGACY] 
// [P2-LEGACY]         if (consecutiveFreeBlocks < cantidad || startIndex < 0) {
// [P2-LEGACY]             return false;
// [P2-LEGACY]         }
// [P2-LEGACY] 
// [P2-LEGACY]         // 3. Realizar reserva contable (solo tras verificar espacio contiguo)
// [P2-LEGACY]         if (com.getMemoryManager() != null) {
// [P2-LEGACY]             if (!com.getMemoryManager().allocateMemory(cantidad)) {
// [P2-LEGACY]                 return false;
// [P2-LEGACY]             }
// [P2-LEGACY]         } else {
// [P2-LEGACY]             com.getRam().setUsedSize(com.getRam().getUsedSize() + cantidad);
// [P2-LEGACY]         }
// [P2-LEGACY] 
// [P2-LEGACY]         // 4. Ubicar físicamente el búfer en RAM con reversión en caso de fallo
// [P2-LEGACY]         try {
// [P2-LEGACY]             for (int i = startIndex; i < startIndex + cantidad; i++) {
// [P2-LEGACY]                 space[i] = buffer.getId() + 1000;
// [P2-LEGACY]             }
// [P2-LEGACY]             return true;
// [P2-LEGACY]         } catch (Exception e) {
// [P2-LEGACY]             for (int i = startIndex; i < startIndex + cantidad; i++) {
// [P2-LEGACY]                 if (space[i] == buffer.getId() + 1000) {
// [P2-LEGACY]                     space[i] = 0;
// [P2-LEGACY]                 }
// [P2-LEGACY]             }
// [P2-LEGACY]             if (com.getMemoryManager() != null) {
// [P2-LEGACY]                 com.getMemoryManager().freeMemory(cantidad);
// [P2-LEGACY]             } else {
// [P2-LEGACY]                 com.getRam().setUsedSize(Math.max(0, com.getRam().getUsedSize() - cantidad));
// [P2-LEGACY]             }
// [P2-LEGACY]             return false;
// [P2-LEGACY]         }
// [P2-LEGACY]     }

    // =========================================================================
    // CICLO DE EJECUCIÓN — único punto de acceso al hardware de CPU (ARC-06)
    // =========================================================================

    /**
     * Ejecuta exactamente un ciclo temporal sobre el computador dado.
     * Centraliza todo acceso a CPU en la capa del kernel:
     * tick del scheduler, deadlines, despacho, ejecución y sync de registros.
     */
    // [P2-LEGACY] public void executeCycle(Computer comp, int currentCycle,
    // [P2-LEGACY]                          ProducerConsumerDriver syncDriver,
    // [P2-LEGACY]                          MyLinkedList<Buffer> buffers) {
    public void executeCycle(Computer comp, int currentCycle) {
        CPU cpu = comp.getCpu();
        if (cpu == null) return;

        comp.registrarCicloObservado();

        // 1. Notificar al planificador (quantum Round Robin, etc.)
        IScheduler scheduler = getOrUpdateScheduler(comp);
        if (scheduler != null) {
            scheduler.onClockTick();
        }

        // 2. Verificar y vencer deadlines (D-07, D-13)
        // [P2-LEGACY] checkDeadlines(comp, currentCycle, buffers);
        checkDeadlines(comp, currentCycle);

        // 3. Avanzar solicitudes pendientes de red para procesos bloqueados
        // [P2-LEGACY] advanceNetworkDelays(comp, syncDriver);

        // 3.2 Avanzar temporizadores de bloqueo IO para procesos IO-bound
        advanceIOBlocks();

        // 3.5 Mover a readyQueue los procesos que wakeUpProcess marcó como LISTO
        //     pero que aún pueden estar físicamente en blockedQueue.
        flushWokenProcesses();

        // 4. Despachar o evaluar desalojo en CPU (Round Robin quantum, prioridad, etc.)
        ProcessControlBlock currentRunning = cpu.getPCB();
        dispatch(comp, currentRunning);
        currentRunning = cpu.getPCB();

        // 5. Ejecutar 1 ciclo si hay un proceso en CPU
        ProcessControlBlock executedThisCycle = null;
        if (currentRunning != null && currentRunning.getState() == ProcessState.EJECUTANDO) {
            executedThisCycle = currentRunning;
            comp.incrementarCicloActivo();
            if (currentRunning.getStartCycle() < 0) currentRunning.setStartCycle(currentCycle);
// [P2-LEGACY]             if (currentRunning.getType() == ProcessType.PRODUCER
// [P2-LEGACY]                     || currentRunning.getType() == ProcessType.CONSUMER) {
// [P2-LEGACY]                 Buffer targetBuf = findBufferById(
// [P2-LEGACY]                         parseBufferId(currentRunning.getTargetBufferId()), buffers);
// [P2-LEGACY]                 if (targetBuf != null) {
// [P2-LEGACY]                     boolean continueRunning = syncDriver.executeCycle(currentRunning, targetBuf);
// [P2-LEGACY]                     if (!continueRunning) {
// [P2-LEGACY]                         if (currentRunning.getState() == ProcessState.BLOQUEADO) {
// [P2-LEGACY]                             blockProcess(comp, currentRunning);
// [P2-LEGACY]                             event(proceso(currentRunning) + " se bloqueó: " + motivo(currentRunning));
// [P2-LEGACY]                         }
// [P2-LEGACY]                     }
// [P2-LEGACY]                 } else {
// [P2-LEGACY]                     currentRunning.executeOneCycle(currentCycle);
// [P2-LEGACY]                 }
            { // [P2] rama unica: ya no existen PRODUCER/CONSUMER
                if (currentRunning.getType() == ProcessType.FILE_IO && currentRunning.getCreationType() != null
                        && !currentRunning.isCreationIssued()) {
                    try {
                        int inicio = fileSystemManager.reserveCreation(currentRunning);
                        currentRunning.setReservedStart(inicio);
                        currentRunning.setCreationIssued(true);
                        currentRunning.setMode(com.avilaos.core.model.ExecutionMode.KERNEL);
                        cpu.setMode(com.avilaos.core.model.ExecutionMode.KERNEL);
                        currentRunning.executeOneCycle(currentCycle);
                        blockProcess(comp, currentRunning);
                        currentRunning.setBlockReason(BlockReason.WAITING_DISK);
                        disk.enqueueRequest(currentRunning, com.avilaos.core.model.IOOperationType.CREATE,
                                currentRunning.getTargetPath(), inicio, currentRunning.getFileSizeBlocks(), currentCycle);
                        event("Creación pendiente: " + currentRunning.getTargetPath() + " · bloques reservados");
                    } catch (RuntimeException ex) {
                        event("Creación rechazada: " + ex.getMessage());
                        terminateProcess(comp, currentRunning);
                    }
                } else {
                boolean solicitudIO = currentRunning.getType() == ProcessType.IO_BOUND
                        && currentRunning.isIoRequestInstruction() && currentRunning.getIoCycleCountdown() == 0;
                currentRunning.executeOneCycle(currentCycle);

                // Verificar si un proceso IO_BOUND debe bloquearse tras este ciclo de CPU
                if (currentRunning.getType() == ProcessType.IO_BOUND
                        && currentRunning.getIoInterval() > 0
                        && currentRunning.getState() == ProcessState.EJECUTANDO) {
                    int countdown = solicitudIO ? 0 : currentRunning.getIoCycleCountdown() - 1;
                    if (countdown <= 0 && (!currentRunning.isIoRequestInstruction() || solicitudIO)) {
                        // Bloquear: el ioCycleCountdown pasa a ioDuration para el desbloqueo
                        currentRunning.setIoCycleCountdown(currentRunning.getIoDuration());
                        blockProcess(comp, currentRunning);
                        currentRunning.setBlockReason(BlockReason.WAITING_IO);
                        event(proceso(currentRunning) + " se bloqueó: " + motivo(currentRunning));
                    } else {
                        currentRunning.setIoCycleCountdown(countdown);
                    }
                }
                }
            }

            // Sincronizar registros hardware mediante la interfaz común de reloj (ARC-22)
            if (cpu.getPCB() != null) {
                cpu.tick(currentCycle);
            }

            // Verificar terminación natural
            if (currentRunning.getState() == ProcessState.TERMINADO
                    || (currentRunning.getRemainingTime() <= 0)) {
// [P2-LEGACY] || (currentRunning.getType() != ProcessType.PRODUCER
// [P2-LEGACY] && currentRunning.getType() != ProcessType.CONSUMER
// [P2-LEGACY] && currentRunning.getRemainingTime() <= 0)) {
                terminateProcess(comp, currentRunning);
            }
        }

        // 5.5 Tiempo de espera: todo proceso admitido que no ejecutó en este ciclo (listo, bloqueado o suspendido)
        for (int i = 0; i < this.processList.size(); i++) {
            ProcessControlBlock w = this.processList.get(i);
            if (w != null && w != executedThisCycle
                    && (w.getState() == ProcessState.LISTO || w.getState() == ProcessState.BLOQUEADO
                    || w.getState() == ProcessState.SUSPENDIDO_LISTO || w.getState() == ProcessState.SUSPENDIDO_BLOQUEADO)) {
                w.incrementWaitingTime();
                if (w.getState() == ProcessState.LISTO || w.getState() == ProcessState.SUSPENDIDO_LISTO) {
                    w.sampleReadyWait(currentCycle);
                }
            }
        }

        // 6. Avanzar componentes de hardware auxiliares que reaccionan al reloj (ARC-22)
        if (comp.getDma() != null) {
            comp.getDma().tick(currentCycle);
        }

        // 6.5 [P2] Revisar si hay procesos en SUSPENDIDO_LISTO que puedan reactivarse a RAM
        if (!this.suspendedReadyQueue.isEmpty()) {
            evaluateMidTermReactivation(comp);
        }
    }

    // [P2-LEGACY] private void checkDeadlines(Computer comp, int currentCycle, MyLinkedList<Buffer> buffers) {
    private void checkDeadlines(Computer comp, int currentCycle) {
        for (int i = 0; i < this.processList.size(); i++) {
            ProcessControlBlock p = this.processList.get(i);
            if (p != null
                    && p.getState() != ProcessState.TERMINADO
                    && p.getState() != ProcessState.NUEVO
                    && p.getState() != ProcessState.SUSPENDIDO_LISTO
                    && p.getState() != ProcessState.SUSPENDIDO_BLOQUEADO) {
                p.updateDeadlineOnTick(currentCycle);
                if (p.getRemainingDeadline() <= 0) {
                    // [P2-LEGACY] cleanupProcessResources(p, buffers);
                    terminateProcess(comp, p);
                }
            }
        }
        if (this.currentPolicy == SchedulingPolicyType.EDF) {
            reorderReadyQueue(this.currentPolicy, false);
        }
    }

// [P2-LEGACY]     private void advanceNetworkDelays(Computer comp, ProducerConsumerDriver syncDriver) {
// [P2-LEGACY]         if (syncDriver == null) return;
// [P2-LEGACY]         for (int i = 0; i < this.processList.size(); i++) {
// [P2-LEGACY]             ProcessControlBlock p = this.processList.get(i);
// [P2-LEGACY]             if (p != null
// [P2-LEGACY]                     && p.getState() == ProcessState.BLOQUEADO
// [P2-LEGACY]                     && p.getBlockReason() == BlockReason.NETWORK_LATENCY) {
// [P2-LEGACY]                 boolean completed = syncDriver.stepNetworkDelay(p);
// [P2-LEGACY]                 // Notificar y desbloquear al completarse, sin reactivar si fue cancelado por deadline
// [P2-LEGACY]                 if (completed && p.getState() != ProcessState.TERMINADO) {
// [P2-LEGACY]                     blockedToReady(p);
// [P2-LEGACY]                 }
// [P2-LEGACY]             }
// [P2-LEGACY]         }
// [P2-LEGACY]     }

    /**
     * Avanza el contador de bloqueo IO para procesos IO-bound bloqueados por WAITING_IO.
     * Cuando ioCycleCountdown llega a 0, el proceso vuelve a la cola de listos
     * y su contador se resetea al ioInterval para el próximo ciclo IO.
     */
    private void advanceIOBlocks() {
        for (int i = 0; i < this.processList.size(); i++) {
            ProcessControlBlock p = this.processList.get(i);
            if (p == null || p.getState() == ProcessState.TERMINADO) continue;

            if (p.getState() == ProcessState.BLOQUEADO
                    && p.getBlockReason() == BlockReason.WAITING_IO) {
                int countdown = p.getIoCycleCountdown() - 1;
                p.setIoCycleCountdown(countdown);
                if (countdown <= 0) {
                    // Desbloquear: volver a READY y preparar countdown para el próximo IO
                    p.setIoCycleCountdown(p.getIoInterval());
                    blockedToReady(p);
                }
            } else if (p.getState() == ProcessState.SUSPENDIDO_BLOQUEADO
                    && p.getBlockReason() == BlockReason.WAITING_IO) {
                int countdown = p.getIoCycleCountdown() - 1;
                p.setIoCycleCountdown(countdown);
                if (countdown <= 0) {
                    p.setIoCycleCountdown(p.getIoInterval());
                    this.suspendedBlockedQueue.removeElement(p);
                    p.setState(ProcessState.SUSPENDIDO_LISTO);
                    p.setBlockReason(BlockReason.NONE);
                    this.suspendedReadyQueue.enqueue(p);
                    event("Evento completado para " + p.getName() + ": pasa de Bloqueado/Suspendido a Listo/Suspendido");
                }
            }
        }
    }

    /**
     * Mueve a la cola de listos todos los procesos que wakeUpProcess ya marcó como LISTO
     * pero que físicamente aún pueden estar en blockedQueue.
     * Mantiene la separación de capas: sync solo modifica el estado del PCB,
     * el kernel es el único responsable de gestionar las colas.
     */
    private void flushWokenProcesses() {
        int size = this.blockedQueue.size();
        for (int i = 0; i < size; i++) {
            ProcessControlBlock p = this.blockedQueue.dequeue();
            if (p == null) continue;
            if (p.getState() == ProcessState.LISTO) {
                // El proceso fue despertado por wakeUpProcess: moverlo a readyQueue
                this.readyQueue.removeElement(p); // Prevenir duplicados
                this.readyQueue.enqueue(p);
                event(proceso(p) + " despertó y pasó a listos");
            } else {
                // El proceso sigue bloqueado: volver a encolarlo
                this.blockedQueue.enqueue(p);
            }
        }
        reorderReadyQueue(this.currentPolicy, false);
    }

// [P2-LEGACY]     private void cleanupProcessResources(ProcessControlBlock pcb, MyLinkedList<Buffer> buffers) {
// [P2-LEGACY]         if (pcb == null) return;
// [P2-LEGACY]         Buffer buf = findBufferById(parseBufferId(pcb.getTargetBufferId()), buffers);
// [P2-LEGACY]         if (buf == null) return;
// [P2-LEGACY] 
// [P2-LEGACY]         buf.cleanupPermits(pcb);
// [P2-LEGACY]     }
// [P2-LEGACY] 
// [P2-LEGACY]     private Buffer findBufferById(int id, MyLinkedList<Buffer> buffers) {
// [P2-LEGACY]         if (buffers == null) return null;
// [P2-LEGACY]         for (int i = 0; i < buffers.size(); i++) {
// [P2-LEGACY]             Buffer b = buffers.get(i);
// [P2-LEGACY]             if (b != null && b.getId() == id) return b;
// [P2-LEGACY]         }
// [P2-LEGACY]         return null;
// [P2-LEGACY]     }
// [P2-LEGACY] 
// [P2-LEGACY]     private int parseBufferId(String targetId) {
// [P2-LEGACY]         if (targetId == null || targetId.trim().isEmpty()) return 1;
// [P2-LEGACY]         try {
// [P2-LEGACY]             return Integer.parseInt(targetId.replaceAll("[^0-9]", ""));
// [P2-LEGACY]         } catch (NumberFormatException e) {
// [P2-LEGACY]             return 1;
// [P2-LEGACY]         }
// [P2-LEGACY]     }

    // --- GETTERS DE COLAS ---
    public MyLinkedList<ProcessControlBlock> getProcessList() {
        return processList;
    }

    public MyQueue<ProcessControlBlock> getBlockedQueue() {
        return blockedQueue;
    }

    public MyQueue<ProcessControlBlock> getNewQueue() {
        return newQueue;
    }

    public MyQueue<ProcessControlBlock> getReadyQueue() {
        return readyQueue;
    }

    public MyQueue<ProcessControlBlock> getEndedQueue() {
        return endedQueue;
    }

    /** [P2] Cola de procesos en estado SUSPENDIDO_LISTO. */
    public MyQueue<ProcessControlBlock> getSuspendedReadyQueue() {
        return suspendedReadyQueue;
    }

    /** [P2] Cola de procesos en estado SUSPENDIDO_BLOQUEADO. */
    public MyQueue<ProcessControlBlock> getSuspendedBlockedQueue() {
        return suspendedBlockedQueue;
    }

    public GlobalClock getClock() {
        return clock;
    }

    public IScheduler getActiveScheduler() {
        return activeScheduler;
    }

    // --- [P2] GETTERS/SETTERS DEL DISCO Y PLANIFICADOR A MEDIANO PLAZO ---

    private com.avilaos.filesystem.FileSystemManager fileSystemManager;
    public void setFileSystemManager(com.avilaos.filesystem.FileSystemManager fs) { this.fileSystemManager = fs; }
    public SimulatedDisk getDisk() { return disk; }
    public void setDisk(SimulatedDisk disk) { this.disk = disk; }
    public MidTermScheduler getMidTermScheduler() { return midTermScheduler; }
    public int getSuspensionCount() { return suspensionCount; }
    public int getReactivationCount() { return reactivationCount; }

    // =========================================================================
    // [P2] LÓGICA DEL PLANIFICADOR A MEDIANO PLAZO Y REGISTRO DE EVENTOS
    // =========================================================================

    /**
     * Intenta liberar RAM suspendiendo un proceso Bloqueado cuando un proceso nuevo
     * no puede ser admitido por falta de espacio en memoria principal.
     */
    public void evaluateMidTermSuspension(Computer com, ProcessControlBlock targetNew) {
        if (com == null || disk == null) return;

        int neededMem = (targetNew != null)
                ? (targetNew.getAssignedMemory() > 0 ? targetNew.getAssignedMemory() : targetNew.getRequiredMemory())
                : 1;
        if (com.getMemoryManager() != null && com.getMemoryManager().hasAvailableMemory(neededMem)) return;

        ProcessControlBlock victim = midTermScheduler.selectVictimToSuspend(this.blockedQueue, disk);
        if (victim == null) return;

        int mem = victim.getAssignedMemory() > 0 ? victim.getAssignedMemory() : victim.getRequiredMemory();
        if (mem <= 0) return;

        int startSwapBlock = disk.allocateSwapBlocks(victim.getId(), victim.getName(), mem);
        if (startSwapBlock < 0) return;

        victim.setPreSuspensionBlockReason(victim.getBlockReason());
        victim.setBlockReason(BlockReason.SWAPPING_OUT);

        // Registro del evento exigido: "Se suspende al proceso P7 para admitir a P9"
        String nuevoProcName = (targetNew != null) ? targetNew.getName() : "nuevos procesos";
        event("Se suspende al proceso " + victim.getName() + " para admitir a " + nuevoProcName);
        event("Iniciando swap-out de " + victim.getName() + " al área de intercambio (bloque " + startSwapBlock + ")");

        int reqId = nextSwapRequestId++;
        IORequest req = new IORequest(reqId, victim, IOOperationType.SUSPEND,
                "SWAP:" + victim.getName(), startSwapBlock, mem, clock != null ? clock.getCycleCounter() : 0);
        disk.enqueueRequest(req);
        suspensionCount++;
    }

    /**
     * Invocado por el disco al completar una solicitud SUSPEND (swap-out).
     * Libera la RAM de la víctima y la traslada formalmente a SUSPENDIDO_BLOQUEADO.
     */
    public void onSwapOutCompleted(Computer com, IORequest request) {
        if (request == null || request.getPcb() == null) return;
        ProcessControlBlock p = request.getPcb();
        if (p.isTerminated()) return;

        int mem = p.getAssignedMemory() > 0 ? p.getAssignedMemory() : p.getRequiredMemory();
        if (com != null && com.getMemoryManager() != null && p.getAssignedMemory() > 0) {
            com.getMemoryManager().freeMemory(p.getAssignedMemory());
            p.setAssignedMemory(0);
        }

        this.blockedQueue.removeElement(p);
        p.setState(ProcessState.SUSPENDIDO_BLOQUEADO);
        p.setBlockReason(p.getPreSuspensionBlockReason() != BlockReason.NONE ? p.getPreSuspensionBlockReason() : BlockReason.WAITING_IO);
        this.suspendedBlockedQueue.enqueue(p);

        event("Swap-out completado: el proceso " + p.getName() + " pasa a Bloqueado/Suspendido (liberada RAM: " + mem + ")");

        if (com != null) {
            evaluateNewQueueAdmission(com);
            evaluateMidTermReactivation(com);
        }
    }

    /**
     * Evalúa si puede reactivar un proceso Listo/Suspendido (swap-in), trayéndolo de vuelta a RAM.
     */
    public void evaluateMidTermReactivation(Computer com) {
        if (com == null || disk == null) return;

        ProcessControlBlock candidate = midTermScheduler.selectProcessToReactivate(
                this.suspendedReadyQueue, com.getMemoryManager());
        if (candidate == null) return;

        int mem = candidate.getRequiredMemory() > 0 ? candidate.getRequiredMemory() : candidate.getAssignedMemory();
        if (mem <= 0) return;

        if (com.getMemoryManager() != null) {
            if (!com.getMemoryManager().hasAvailableMemory(mem)) return;
            com.getMemoryManager().allocateMemory(mem);
        }
        candidate.setAssignedMemory(mem);
        candidate.setBlockReason(BlockReason.SWAPPING_IN);

        int swapBlock = findSwapStartBlock(candidate.getId());

        event("Se reactiva al proceso " + candidate.getName() + ": iniciando swap-in desde área de intercambio (bloque " + swapBlock + ")");

        int reqId = nextSwapRequestId++;
        IORequest req = new IORequest(reqId, candidate, IOOperationType.REACTIVATE,
                "SWAP:" + candidate.getName(), swapBlock, mem, clock != null ? clock.getCycleCounter() : 0);
        disk.enqueueRequest(req);
        reactivationCount++;
    }

    /**
     * Invocado por el disco al completar una solicitud REACTIVATE (swap-in).
     * Libera los bloques del área de swap y traslada el proceso a la cola de listos (READY).
     */
    public void onSwapInCompleted(Computer com, IORequest request) {
        if (request == null || request.getPcb() == null) return;
        ProcessControlBlock p = request.getPcb();
        if (p.isTerminated()) return;

        if (disk != null) {
            disk.freeSwapBlocksByProcess(p.getId());
        }

        this.suspendedReadyQueue.removeElement(p);
        p.setState(ProcessState.LISTO);
        p.setBlockReason(BlockReason.NONE);
        this.readyQueue.enqueue(p);
        reorderReadyQueue(this.currentPolicy, false);

        event("Swap-in completado: el proceso " + p.getName() + " pasa a Listo (RAM asignada: " + p.getAssignedMemory() + ")");
    }

    /**
     * Maneja la finalización de solicitudes de E/S del disco enrutando según la operación.
     */
    public void handleDiskCompletion(Computer com, IORequest request, int cycle) {
        if (request == null) return;
        switch (request.getOperation()) {
            case SUSPEND -> onSwapOutCompleted(com, request);
            case REACTIVATE -> onSwapInCompleted(com, request);
            default -> {
                ProcessControlBlock p = request.getPcb();
                if (p != null && p.getComputerId() != com.getId()) return;
                if (p != null && p.getCreationType() != null && !p.isTerminated()) {
                    try {
                        fileSystemManager.completeCreation(p);
                        event("Creación completada: " + p.getTargetPath());
                    } catch (RuntimeException ex) {
                        event("Creación fallida: " + ex.getMessage());
                        terminateProcess(com, p);
                        return;
                    }
                }
                if (p != null && !p.isTerminated()) {
                    if (p.getState() == ProcessState.BLOQUEADO && p.getBlockReason() == BlockReason.WAITING_DISK) {
                        blockedToReady(p);
                        event("Disco completó " + request.getOperation() + " para " + proceso(p) + " → LISTO");
                    } else if (p.getState() == ProcessState.SUSPENDIDO_BLOQUEADO) {
                        this.suspendedBlockedQueue.removeElement(p);
                        p.setState(ProcessState.SUSPENDIDO_LISTO);
                        p.setBlockReason(BlockReason.NONE);
                        this.suspendedReadyQueue.enqueue(p);
                        event("Evento completado para " + p.getName() + ": pasa de Bloqueado/Suspendido a Listo/Suspendido");
                        if (com != null) evaluateMidTermReactivation(com);
                    }
                }
            }
        }
    }

    private int findSwapStartBlock(int processId) {
        if (disk == null) return 0;
        int total = disk.getTotalBlocks();
        int limit = total + disk.getSwapSizeBlocks();
        for (int i = total; i < limit; i++) {
            com.avilaos.disk.DiskBlock b = disk.getBlock(i);
            if (b != null && b.isOccupied() && b.getOwnerProcessId() == processId) {
                return i;
            }
        }
        return total;
    }
}
