package com.avilaos.simulation;
import com.avilaos.core.model.*;
import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.hardware.Computer;
import com.avilaos.sync.Buffer;

/** Copia de valores, sin referencias al modelo vivo. Se publica con volatile.
 * Las listas y arrays se encapsulan; sus elementos son registros inmutables.
 */
public final class SimulationSnapshot {
    public final long session;
    public final int cycle;
    public final boolean running, paused;
    public final int interval;
    public final Values<Node> computers;
    public final Values<BufferView> buffers;
    public final Values<ProcessRow> processes;
    public record ProcessRow(int computerId, String computerName, ProcessState state, ProcessView process) { }
    public record Usage(int computerId, double percent, double throughput, double successRate, double waitTime) {
        public Usage(int computerId, double percent) { this(computerId, percent, 0.0, 0.0, 0.0); }
    }
    public record Sample(int cycle, Values<Usage> usage) { }
    public final Values<Sample> history;
    public static final class Values<T> {
        private final Object[] items;
        private Values(Object[] items) { this.items = items; }
        public int size() { return items.length; }
        @SuppressWarnings("unchecked") public T get(int index) { return (T) items[index]; }
    }
    public record ProcessView(
            String description, 
            int id, 
            String name, 
            com.avilaos.core.model.ProcessType type, 
            int remainingTime, 
            int totalInstructions,
            int memoryAddressRegister, 
            int programCounter,
            int priority, 
            int deadline,
            int remainingDeadline,
            int assignedMemory,
            com.avilaos.core.model.BlockReason blockReason,
            String targetBufferId,
            int ioInterval, int ioDuration, int cyclesPerOperation, int requiredElements,
            int readyWaitTicks, int blockedWaitTicks, int semaphoreWaitTicks
    ) {
        public ProcessView(String description, int id, String name, com.avilaos.core.model.ProcessType type, int remainingTime, int memoryAddressRegister, int priority, int remainingDeadline) {
            this(description, id, name, type, remainingTime, remainingTime, memoryAddressRegister, 0, priority, remainingDeadline, remainingDeadline, 0, com.avilaos.core.model.BlockReason.NONE, null, 0, 0, 0, 0, 0, 0, 0);
        }
        public String getDescription() { return description; }
        public int getId() { return id; }
        public String getName() { return name; }
        public com.avilaos.core.model.ProcessType getType() { return type; }
        public int getRemainingTime() { return remainingTime; }
        public int getTotalInstructions() { return totalInstructions; }
        public int getMemoryAddressRegister() { return memoryAddressRegister; }
        public int getProgramCounter() { return programCounter; }
        public int getPriority() { return priority; }
        public int getDeadline() { return deadline; }
        public int getRemainingDeadline() { return remainingDeadline; }
        public int getAssignedMemory() { return assignedMemory; }
        public com.avilaos.core.model.BlockReason getBlockReason() { return blockReason; }
        public String getTargetBufferId() { return targetBufferId; }
        @Override public String toString() { return description; }
    }
    public record CpuView(ProcessView PCB, ExecutionMode mode, int PC) {
        public ProcessView getPCB() { return PCB; }
        public ExecutionMode getMode() { return mode; }
        public int getPC() { return PC; }
    }
    public record MemoryView(int totalMemory, int usedMemory) {
        public int getTotalMemory() { return totalMemory; }
        public int getUsedMemory() { return usedMemory; }
    }
    public record RamView(int totalSize) {
        public int getTotalSize() { return totalSize; }
    }
    public record QueueView(int count, String text, Values<ProcessView> processes) {
        public QueueView(int count, String text) {
            this(count, text, new Values<>(new Object[0]));
        }
        public int getCount() { return count; }
        public String getText() { return text; }
        public int size() { return count; }
        public Values<ProcessView> getProcesses() { return processes; }
        @Override public String toString() { return text; }
    }
    public record KernelView(QueueView newQueue, QueueView readyQueue, QueueView blockedQueue, QueueView endedQueue) {
        public QueueView getNewQueue() { return newQueue; }
        public QueueView getReadyQueue() { return readyQueue; }
        public QueueView getBlockedQueue() { return blockedQueue; }
        public QueueView getEndedQueue() { return endedQueue; }
    }
    public record Node(int id, String name, SchedulingPolicyType politica, int quantum, CpuView cpu, MemoryView memoryManager, RamView ram, KernelView kernel, int ciclosActivos, int ciclosObservados, int procesosCompletados, int procesosFinalizados, int deadlinesCumplidos, int totalTiempoRespuesta, int totalTiempoEspera) {
        public Node(int id, String name, SchedulingPolicyType politica, int quantum, CpuView cpu, MemoryView memoryManager, RamView ram, KernelView kernel, int ciclosActivos, int ciclosObservados, int procesosCompletados, int procesosFinalizados, int deadlinesCumplidos, int totalTiempoRespuesta) {
            this(id, name, politica, quantum, cpu, memoryManager, ram, kernel, ciclosActivos, ciclosObservados, procesosCompletados, procesosFinalizados, deadlinesCumplidos, totalTiempoRespuesta, 0);
        }
        public int getTotalTiempoEspera() { return totalTiempoEspera; }
        public int getId() { return id; }
        public String getName() { return name; }
        public SchedulingPolicyType getPolitica() { return politica; }
        public int getQuantum() { return quantum; }
        public CpuView getCpu() { return cpu; }
        public MemoryView getMemoryManager() { return memoryManager; }
        public RamView getRam() { return ram; }
        public KernelView getKernel() { return kernel; }
        public int getCiclosActivos() { return ciclosActivos; }
        public int getCiclosObservados() { return ciclosObservados; }
        public int getProcesosCompletados() { return procesosCompletados; }
        public int getProcesosFinalizados() { return procesosFinalizados; }
        public int getDeadlinesCumplidos() { return deadlinesCumplidos; }
        public int getTotalTiempoRespuesta() { return totalTiempoRespuesta; }
    }
    public record PermitView(int count) {
        public int getCount() { return count; }
        public int availablePermits() { return count; }
    }
    public record BufferView(int id, int idComp, int storage, int sizeInMemory, int produced, int comsumed, QueueView datos, PermitView mutex, PermitView llenos, PermitView vacios, String blockedProcessesAsString, int latency) {
        public BufferView(int id, int idComp, int storage, int sizeInMemory, int produced, int comsumed, QueueView datos, PermitView mutex, PermitView llenos, PermitView vacios, String blockedProcessesAsString) {
            this(id, idComp, storage, sizeInMemory, produced, comsumed, datos, mutex, llenos, vacios, blockedProcessesAsString, 0);
        }
        public int getId() { return id; }
        public int getIdComp() { return idComp; }
        public int getStorage() { return storage; }
        public int getSizeInMemory() { return sizeInMemory; }
        public int getProduced() { return produced; }
        public int getComsumed() { return comsumed; }
        public QueueView getDatos() { return datos; }
        public PermitView getMutex() { return mutex; }
        public PermitView getLlenos() { return llenos; }
        public PermitView getVacios() { return vacios; }
        public String getBlockedProcessesAsString() { return blockedProcessesAsString; }
        public int getLatency() { return latency; }
    }

    private static ProcessView toProcessView(ProcessControlBlock p) {
        if (p == null) return null;
        int mem = p.getAssignedMemory() > 0 ? p.getAssignedMemory() : p.getRequiredMemory();
        return new ProcessView(
                p.toString(),
                p.getId(),
                p.getName(),
                p.getType(),
                p.getRemainingTime(),
                p.getTotalInstructions(),
                p.getMemoryAddressRegister(),
                p.getProgramCounter(),
                p.getPriority(),
                p.getDeadline(),
                p.getRemainingDeadline(),
                mem,
                p.getBlockReason() != null ? p.getBlockReason() : BlockReason.NONE,
                p.getTargetBufferId(),
                p.getIoInterval(), p.getIoDuration(), p.getCyclesPerOperation(), p.getRequiredElements(),
                p.getReadyWaitTicks(), p.getBlockedWaitTicks(), p.getSemaphoreWaitTicks()
        );
    }

    private static QueueView toQueueView(com.avilaos.core.structures.MyQueue<ProcessControlBlock> q) {
        if (q == null) return new QueueView(0, "[]", new Values<>(new Object[0]));
        Object[] raw = q.toArray();
        Object[] views = new Object[raw.length];
        for (int i = 0; i < raw.length; i++) {
            views[i] = toProcessView((ProcessControlBlock) raw[i]);
        }
        return new QueueView(q.size(), q.toString(), new Values<>(views));
    }

    public SimulationSnapshot(long session, int cycle, boolean running, boolean paused, int interval,
            MyLinkedList<Computer> nodes, MyLinkedList<Buffer> sources, SimulationSnapshot previous) {
        this.session=session; this.cycle=cycle; this.running=running; this.paused=paused; this.interval=interval;
        Object[] copiedNodes = new Object[nodes.size()];
        for (int i=0; i<nodes.size(); i++) {
            var c=nodes.get(i); var p=c.getCpu().getPCB(); var k=c.getKernel();
            var cpu=new CpuView(toProcessView(p), c.getCpu().getMode(), c.getCpu().getPC());
            var kernel=new KernelView(
                    toQueueView(k.getNewQueue()),
                    toQueueView(k.getReadyQueue()),
                    toQueueView(k.getBlockedQueue()),
                    toQueueView(k.getEndedQueue()));
            copiedNodes[i]=new Node(c.getId(),c.getName(),c.getPolitica(),c.getQuantum(),cpu,
                    new MemoryView(c.getMemoryManager().getTotalMemory(),c.getMemoryManager().getUsedMemory()),
                    new RamView(c.getRam().getTotalSize()),kernel,c.getCiclosActivos(),c.getCiclosObservados(),
                    c.getProcesosCompletados(),c.getProcesosFinalizados(),c.getDeadlinesCumplidos(),c.getTotalTiempoRespuesta(),c.getTotalTiempoEspera());
        }
        Object[] copiedBuffers=new Object[sources.size()];
        for(int i=0;i<sources.size();i++) {
            var b=sources.get(i);
            int m = b.getTheoreticalValue(Buffer.SemaphoreKind.MUTEX);
            int l = b.getTheoreticalValue(Buffer.SemaphoreKind.LLENOS);
            int v = b.getTheoreticalValue(Buffer.SemaphoreKind.VACIOS);
            copiedBuffers[i]=new BufferView(b.getId(),b.getIdComp(),b.getStorage(),b.getSizeInMemory(),b.getProduced(),b.getComsumed(),
                    new QueueView(b.getDatos().size(),b.getDatos().toString()),new PermitView(m),
                    new PermitView(l),new PermitView(v),b.getBlockedProcessesAsString(), b.getLatency());
        }
        int processCount = 0;
        for (int i=0; i<nodes.size(); i++) processCount += nodes.get(i).getKernel().getProcessList().size();
        Object[] copiedProcesses = new Object[processCount];
        int processIndex = 0;
        for (int i=0; i<nodes.size(); i++) {
            var computer = nodes.get(i);
            var list = computer.getKernel().getProcessList();
            for (int j=0; j<list.size(); j++) {
                var process = list.get(j);
                copiedProcesses[processIndex++] = new ProcessRow(computer.getId(), computer.getName(), process.getState(), toProcessView(process));
            }
        }
        computers=new Values<>(copiedNodes); buffers=new Values<>(copiedBuffers); processes=new Values<>(copiedProcesses);
        // Mantener muestras aunque el EDT omita refrescos. Ventana acotada a 2000 ciclos.
        Object[] usages=new Object[computers.size()];
        for(int i=0;i<usages.length;i++) {
            Node node=computers.get(i);
            usages[i]=new Usage(node.id(),node.ciclosObservados()==0 ? 0.0 : 100.0*node.ciclosActivos()/node.ciclosObservados(),
                    node.ciclosObservados()==0 ? 0.0 : (double)node.procesosCompletados()/node.ciclosObservados(),
                    node.procesosFinalizados()==0 ? 0.0 : 100.0*node.deadlinesCumplidos()/node.procesosFinalizados(),
                    node.procesosCompletados()==0 ? 0.0 : (double)node.totalTiempoEspera()/node.procesosCompletados());
        }
        int retained=previous!=null && previous.session==session && previous.cycle<=cycle ? previous.history.size() : 0;
        if(retained>0 && previous.cycle==cycle) retained--;
        int count=Math.min(1999,retained);
        Object[] samples=new Object[count+1];
        for(int i=0;i<count;i++) samples[i]=previous.history.get(retained-count+i);
        samples[count]=new Sample(cycle,new Values<>(usages));
        history=new Values<>(samples);
    }
}
