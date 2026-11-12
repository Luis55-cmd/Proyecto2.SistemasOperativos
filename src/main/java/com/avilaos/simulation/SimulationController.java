package com.avilaos.simulation;

import com.avilaos.core.kernel.ComputerKernel;
import com.avilaos.core.kernel.GlobalClock;
import com.avilaos.core.kernel.IClockTickable;
import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ExecutionMode;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.ProcessSizing;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.core.structures.MyQueue;
import com.avilaos.hardware.CPU;
import com.avilaos.hardware.Computer;
import com.avilaos.hardware.DMA;
import com.avilaos.hardware.RAM;
import com.avilaos.sync.Buffer;
import com.avilaos.sync.ProducerConsumerDriver;

import javax.swing.SwingUtilities;
import com.avilaos.concurrency.StateAccess;

/**
 * Controlador y Fachada Central de la Simulación (Capa 3: Control y Simulación).
 * Coordina la simulación del clúster ÁvilaOS, orquesta el avance temporal
 * sincronizado con GlobalClock, e integra el registro/notificación de eventos (logs).
 */
public class SimulationController implements IClockTickable {

    /** Interfaz funcional propia para escuchar eventos de log sin depender de java.util.Consumer */
    public interface ILogListener {
        void onLogAdded(String message);
    }

    private static volatile SimulationController instance;

    private final MyLinkedList<Computer> computers;
    private final MyLinkedList<Buffer> buffers;
    private final GlobalClock clock;
    private final ProducerConsumerDriver syncDriver;

    // Lista de observadores de la interfaz gráfica y de logs
    private final MyLinkedList<Runnable> guiListeners;
    private final MyLinkedList<ILogListener> logListeners;

    private record LogEntry(long session, int cycle, String message) { }
    private final com.avilaos.core.structures.MyQueue<LogEntry> pendingLogs = new com.avilaos.core.structures.MyQueue<>();
    private boolean logPending;
    private volatile SimulationSnapshot snapshot;
    private volatile long session;
    private boolean refreshPending;
    public SimulationSnapshot snapshot() { return snapshot; }
    public long session() { return session; }
    public void requireSession(long expected) {
        if (session != expected) throw new IllegalStateException("La sesión cambió; vuelve a enviar la solicitud");
    }
    private void publishSnapshot() {
        snapshot = new SimulationSnapshot(session, clock.getCycleCounter(), clock.isRunning(), clock.isPaused(),
                clock.getCycleDurationMs(), computers, buffers, snapshot);
        synchronized (guiListeners) {
            if (refreshPending) return;
            refreshPending = true;
        }
        SwingUtilities.invokeLater(() -> {
            Runnable[] listeners;
            synchronized (guiListeners) {
                refreshPending = false;
                listeners = new Runnable[guiListeners.size()];
                for (int i=0;i<listeners.length;i++) listeners[i]=guiListeners.get(i);
            }
            for (Runnable listener : listeners) listener.run();
        });
    }
    public void setComputerPolicy(int id, SchedulingPolicyType policy) {
        StateAccess.enter();
        try {
            Computer c=getComputerById(id);
            if(c==null) throw new IllegalArgumentException("Computador inexistente");
            if(policy==null) throw new IllegalArgumentException("Política inválida");
            SchedulingPolicyType anterior = c.getPolitica();
            if (anterior == policy) return;
            c.setPolitica(policy);
            logEvent("Política de " + c.getName() + " (ID " + id + ") cambiada de "
                    + anterior + " a " + policy);
        } finally { StateAccess.exit(); }
    }
    private int nextComputerId = 1;
    private int nextBufferId = 1;
    // 0: hubo CPU ocupada o no se ha informado; 1: inactivas con pendientes; 2: procesos terminados.
    private int ultimoEstadoInactividad;

    private SimulationController() {
        this.computers = new MyLinkedList<>();
        this.buffers = new MyLinkedList<>();
        this.guiListeners = new MyLinkedList<>();
        this.logListeners = new MyLinkedList<>();
        this.clock = GlobalClock.getInstance();
        this.syncDriver = new ProducerConsumerDriver(2); // Latencia por defecto: 2 ciclos
        this.syncDriver.setEventListener(this::logEvent);
        this.clock.addSubscriber(this);
        StateAccess.setPublisher(this::publishSnapshot);
    }

    public static SimulationController getInstance() {
        if (instance == null) {
            synchronized (SimulationController.class) {
                if (instance == null) {
                    instance = new SimulationController();
                }
            }
        }
        return instance;
    }

    // =========================================================================
    // CONEXIÓN CON LA INTERFAZ GRÁFICA Y LOGS (Patrón Observador)
    // =========================================================================

    /** Registra un panel de la GUI para que se actualice cuando cambie la simulación. */
    public void addGUIListener(Runnable listener) {
        if (listener != null) {
            synchronized (guiListeners) { this.guiListeners.append(listener); }
        }
    }

    /** Registra un escuchador de logs (por ejemplo, el panel de consola en la GUI). */
    public void addLogListener(ILogListener listener) {
        if (listener != null) {
            synchronized (logListeners) { this.logListeners.append(listener); }
        }
    }

    /** Emite un mensaje de evento al registro de logs y notifica a la GUI en el EDT. */
    public void logEvent(String message) {
        if (message == null || message.trim().isEmpty()) return;

        LogEntry event=new LogEntry(session,clock.getCycleCounter(),message);
        synchronized(logListeners) {
            pendingLogs.enqueue(event);
            if(logPending) return;
            logPending=true;
        }
        SwingUtilities.invokeLater(this::flushLogs);
    }

    /** Lotes acotados para no inundar la cola del EDT. Los callbacks no retienen el monitor. */
    private void flushLogs() {
        LogEntry[] batch;
        ILogListener[] listeners;
        synchronized(logListeners) {
            batch=new LogEntry[Math.min(100,pendingLogs.size())];
            for(int i=0;i<batch.length;i++) batch[i]=pendingLogs.dequeue();
            listeners=new ILogListener[logListeners.size()];
            for(int i=0;i<listeners.length;i++) listeners[i]=logListeners.get(i);
        }
        try {
            for(ILogListener listener:listeners) {
                for(LogEntry event:batch) if(event.session()==session)
                    listener.onLogAdded("[Ciclo "+event.cycle()+"] "+event.message());
            }
        } finally {
            synchronized(logListeners) {
                if(pendingLogs.isEmpty()) logPending=false;
                else SwingUtilities.invokeLater(this::flushLogs);
            }
        }
    }

    /** Dispara la actualización en todos los paneles registrados de forma segura en Swing. */
    private void notifyGUI() {
        // La salida exterior del permiso publica una sola vista consistente.
        if (!StateAccess.isOwner()) { StateAccess.enter(); StateAccess.exit(); }
    }

    // =========================================================================
    // MÉTODOS PARA BOTONES DE LA GUI (Gestión de Clúster, Procesos y Buffers)
    // =========================================================================

    public Computer createComputer(int ramSize, SchedulingPolicyType policy) {
        StateAccess.enter();
        try {
        return createComputer(ramSize, policy, 3);

        } finally { StateAccess.exit(); }
    }

    public Computer createComputer(int ramSize, SchedulingPolicyType policy, int quantum) {
        StateAccess.enter();
        try {
        int id = nextComputerId++;
        RAM ram = new RAM(ramSize, 0, new int[ramSize]);
        CPU cpu = new CPU(0, 0, 0, null, ExecutionMode.USER);
        DMA dma = new DMA();

        ComputerKernel kernel = new ComputerKernel(
                new MyLinkedList<>(),
                new MyQueue<>(),
                new MyQueue<>(),
                new MyQueue<>(),
                new MyQueue<>(),
                this.clock
        );
        kernel.setEventListener(this::logEvent);

        Computer comp = new Computer(id, cpu, ram, dma, kernel, policy != null ? policy : SchedulingPolicyType.FCFS, quantum);
        this.computers.append(comp);

        String quantumInfo = (comp.getPolitica() == SchedulingPolicyType.ROUND_ROBIN) ? " con quantum " + comp.getQuantum() : "";
        logEvent("Computador creado: " + comp.getName() + " (ID " + id + ") con política " + comp.getPolitica() + quantumInfo);
        notifyGUI();
        return comp;

        } finally { StateAccess.exit(); }
    }

    public Computer createComputer(String name, int ramSize, SchedulingPolicyType policy) {
        StateAccess.enter();
        try {
        return createComputer(name, ramSize, policy, 3);

        } finally { StateAccess.exit(); }
    }

    public Computer createComputer(String name, int ramSize, SchedulingPolicyType policy, int quantum) {
        StateAccess.enter();
        try {
        if (name == null || name.trim().isEmpty() || ramSize <= 0 || policy == null) {
            throw new IllegalArgumentException("Indica nombre, RAM positiva y política.");
        }
        String normalizedName = name.trim();
        for (int i = 0; i < computers.size(); i++) {
            if (computers.get(i).getName().equalsIgnoreCase(normalizedName)) {
                throw new IllegalArgumentException("Asegúrate de usar un nombre único");
            }
        }
        Computer computer = createComputer(ramSize, policy, quantum);
        computer.setName(name);

        notifyGUI();
        return computer;

        } finally { StateAccess.exit(); }
    }

    public void setComputerQuantum(int computerId, int quantum) {
        StateAccess.enter();
        try {
        Computer comp = getComputerById(computerId);
        if (comp != null) {
            comp.setQuantum(quantum);
            logEvent("Quantum de " + comp.getName() + " actualizado a " + comp.getQuantum());
            notifyGUI();
        }

        } finally { StateAccess.exit(); }
    }

    /** Crea un proceso del formulario usando el mismo cálculo para instrucciones y RAM. */
    public ProcessControlBlock createConfiguredProcess(String name, int computerId, ProcessType type,
            int cpuWork, int priority, int deadline, int ioInterval, int ioDuration,
            String buffer, int cyclesPerOperation, int elements) {
        StateAccess.enter();
        try {
            if (type == ProcessType.IO_BOUND && ioDuration < 1)
                throw new IllegalArgumentException("La duración de E/S debe ser positiva");
            int total = ProcessSizing.instructions(type, cpuWork, ioInterval, cyclesPerOperation, elements);
            ProcessControlBlock pcb = (type == ProcessType.PRODUCER || type == ProcessType.CONSUMER)
                    ? createProcess(name, computerId, type, total, total, priority, deadline, buffer, cyclesPerOperation, elements)
                    : createProcess(name, computerId, type, total, total, priority, deadline, ioInterval, ioDuration);
            if (pcb != null && type == ProcessType.IO_BOUND) {
                pcb.setIoRequestInstruction(true);
                notifyGUI();
            }
            return pcb;
        } finally { StateAccess.exit(); }
    }

    public ProcessControlBlock createProcess(String name, int computerId, ProcessType type,
                                                          int instructions, int memory, int priority, int deadline) {
        StateAccess.enter();
        try {
        return createProcess(name, computerId, type, instructions, memory, priority, deadline, 0, 0);

        } finally { StateAccess.exit(); }
    }

    public ProcessControlBlock createProcess(String name, int computerId, ProcessType type,
                                                          int instructions, int memory, int priority, int deadline,
                                                          int ioInterval, int ioDuration) {
        StateAccess.enter();
        try {
        ProcessControlBlock pcb = createProcess(name, computerId, type, instructions, memory, priority, deadline, null, 1, 0);
        if (pcb != null && type == ProcessType.IO_BOUND && ioInterval > 0 && ioDuration > 0) {
            pcb.setIoInterval(ioInterval);
            pcb.setIoDuration(ioDuration);
            pcb.setIoCycleCountdown(ioInterval); // primer bloqueo ocurre luego de ioInterval ciclos de CPU
        }
        return pcb;

        } finally { StateAccess.exit(); }
    }

    public ProcessControlBlock createProcess(String name, int computerId, ProcessType type,
                                                          int instructions, int memory, int priority, int deadline,
                                                          String targetBufferId, int cyclesPerOperation, int requiredElements) {
        StateAccess.enter();
        try {
        if (name == null || name.trim().isEmpty()) {
            logEvent("Error: El nombre del proceso no puede estar vacío");
            return null;
        }

        Computer comp;
        if (computerId == -1) {
            comp = selectSenderInitiatedComputer(memory);
            if (comp == null) {
                logEvent("Error: Ningún computador tiene RAM suficiente (" + memory + ") para " + name);
                return null;
            }
            computerId = comp.getId();
        } else {
            comp = getComputerById(computerId);
            if (comp == null) {
                logEvent("Error: No se encontró el computador ID " + computerId + " para crear el proceso " + name);
                return null;
            }
        }

        if (instructions <= 0) {
            logEvent("Error: Las instrucciones deben ser mayores a 0 para el proceso " + name);
            return null;
        }

        if (memory <= 0) {
            logEvent("Error: La memoria requerida debe ser mayor a 0 para el proceso " + name);
            return null;
        }

        if (comp.getMemoryManager() != null && memory > comp.getMemoryManager().getTotalMemory()) {
            logEvent("Error: Memoria solicitada (" + memory + ") excede la RAM total ("
                    + comp.getMemoryManager().getTotalMemory() + ") de " + comp.getName());
            return null;
        }

        if (priority < 1) {
            logEvent("Error: La prioridad debe ser al menos 1 para el proceso " + name);
            return null;
        }

        if (deadline <= 0) {
            logEvent("Error: El deadline debe ser mayor a 0 para el proceso " + name);
            return null;
        }

        if (type == ProcessType.PRODUCER || type == ProcessType.CONSUMER) {
            if (cyclesPerOperation <= 0) {
                logEvent("Error: Los ciclos por operación deben ser mayores a 0 para " + name);
                return null;
            }
            if (requiredElements <= 0) {
                logEvent("Error: La cantidad de elementos requeridos debe ser mayor a 0 para " + name);
                return null;
            }
        }

        ProcessControlBlock pcb = new ProcessControlBlock(
                name.trim(), computerId, type != null ? type : ProcessType.CPU_BOUND,
                instructions, memory, priority, deadline,
                targetBufferId, cyclesPerOperation, requiredElements
        );

        pcb.setArrivalCycle(getCurrentCycle());
        String resGestion = comp.getKernel().gestionCreateProcess(comp, pcb);
        if (resGestion != null && resGestion.startsWith("Error")) {
            logEvent(resGestion);
            return null;
        }

        logEvent("Proceso creado: " + pcb.getName() + " (ID " + pcb.getId() + ", Mem: " + memory + ") asignado a " + comp.getName());
        notifyGUI();
        return pcb;

        } finally { StateAccess.exit(); }
    }

    /**
     * Algoritmo de distribución de carga iniciado por el emisor (Sender-Initiated Load Balancing).
     * Selecciona automáticamente el computador de destino para un proceso entrante:
     * 1. Descarta computadores cuya RAM total no pueda albergar la memoria del proceso.
     * 2. Evalúa la carga activa de cada nodo (CPU + Listos + Bloqueados) contra el umbral T (3).
     *    Si encuentra un nodo con carga < T, lo selecciona de inmediato (iniciativa del emisor).
     * 3. Si todos los nodos superan el umbral T, selecciona el nodo con menor carga activa (contingencia).
     *
     * @param requiredMemory Memoria solicitada por el proceso.
     * @return El computador seleccionado, o null si ninguno tiene capacidad de RAM suficiente.
     */
    public Computer selectSenderInitiatedComputer(int requiredMemory) {
        StateAccess.enter();
        try {
        if (this.computers.isEmpty()) {
            return null;
        }

        final int THRESHOLD_LOAD = 3;
        Computer bestCandidate = null;
        int minLoad = Integer.MAX_VALUE;

        for (int i = 0; i < this.computers.size(); i++) {
            Computer c = this.computers.get(i);
            if (c == null) continue;

            // Filtro de viabilidad física de memoria
            if (c.getMemoryManager() != null && requiredMemory > c.getMemoryManager().getTotalMemory()) {
                continue;
            }

            int running = (c.getCpu() != null && c.getCpu().getPCB() != null) ? 1 : 0;
            int ready = (c.getKernel() != null && c.getKernel().getReadyQueue() != null) ? c.getKernel().getReadyQueue().size() : 0;
            int blocked = (c.getKernel() != null && c.getKernel().getBlockedQueue() != null) ? c.getKernel().getBlockedQueue().size() : 0;
            int load = running + ready + blocked;

            // Iniciativa del emisor: primer nodo receptor que esté por debajo del umbral de carga
            if (load < THRESHOLD_LOAD) {
                logEvent("[Sender-Initiated] Asignando proceso a " + c.getName()
                        + " (Carga: " + load + " < Umbral T=" + THRESHOLD_LOAD + ")");
                return c;
            }

            if (load < minLoad) {
                minLoad = load;
                bestCandidate = c;
            }
        }

        if (bestCandidate != null) {
            logEvent("[Sender-Initiated] Todos los nodos superaron umbral T=" + THRESHOLD_LOAD
                    + ". Asignando a " + bestCandidate.getName() + " por menor carga (" + minLoad + ")");
        }
        return bestCandidate;

        } finally { StateAccess.exit(); }
    }

    public Buffer createBuffer(int hostComputerId, int storage) {
        StateAccess.enter();
        try {
        return createBuffer(hostComputerId, storage, storage, getNetworkLatency());

        } finally { StateAccess.exit(); }
    }

    /** Compatibilidad con cargas anteriores: la RAM se deriva de capacidad, no del campo legacy memory. */
    public Buffer createBuffer(int hostComputerId, int storage, int latency) {
        StateAccess.enter();
        try {
        return createBuffer(hostComputerId, storage, storage, latency);

        } finally { StateAccess.exit(); }
    }


    public Buffer createBuffer(int hostComputerId, int storage, int memory, int latency) {
        StateAccess.enter();
        try {
        if (storage <= 0) {
            return null;
        }
        Computer host = getComputerById(hostComputerId);
        if (host == null || host.getKernel() == null) {
            logEvent("Error: Computador anfitrión inválido para crear Buffer");
            return null;
        }

        int id = nextBufferId;
        Buffer buffer = new Buffer(null, null, null, hostComputerId, storage, id, new MyQueue<>(), storage, latency);
        boolean success = host.getKernel().placeBufferRAM(buffer, host);
        if (!success) {
            logEvent("Error: Memoria RAM insuficiente en " + host.getName() + " para crear el Buffer " + id);
            return null;
        }

        nextBufferId++;
        this.buffers.append(buffer);

        logEvent("Buffer " + id + " creado en " + host.getName() + " con capacidad " + storage + " y latencia " + latency + " ticks");
        notifyGUI();
        return buffer;

        } finally { StateAccess.exit(); }
    }

    // =========================================================================
    // CONTROL DEL RELOJ GLOBAL (Play, Pausa, Step, Velocidad)
    // =========================================================================

    public void startSimulation() {
        StateAccess.enter();
        try {
            this.clock.start();
            logEvent("Simulación iniciada");
            notifyGUI();
        } finally { StateAccess.exit(); }
    }

    public void pauseSimulation() {
        this.clock.pause();
        logEvent("Simulación pausada");
        notifyGUI();
    }

    public void stepSimulation() {
        this.clock.step();
        notifyGUI();
    }

    public void setCycleDurationMs(int ms) {
        this.clock.setCycleDurationMs(ms);
        logEvent("Duración de ciclo ajustada a " + ms + " ms");
        notifyGUI();
    }

    public int getCycleDurationMs() {
        return this.clock.getCycleDurationMs();
    }

    public int getCurrentCycle() {
        return this.clock.getCycleCounter();
    }

    public void setCurrentCycle(int cycle) {
        this.clock.setCycleCounter(cycle);
    }

    public boolean isRunning() {
        return this.clock.isRunning();
    }

    public boolean isPaused() {
        return this.clock.isPaused();
    }

    public void setNetworkLatency(int latencyCycles) {
        StateAccess.enter();
        try { 
            this.syncDriver.setLatencia(latencyCycles);
            for (int i = 0; i < this.buffers.size(); i++) {
                Buffer b = this.buffers.get(i);
                if (b != null) {
                    b.setLatency(latencyCycles);
                }
            }
            logEvent("Latencia de red actualizada a " + latencyCycles + " ciclos");
        } finally { StateAccess.exit(); }
    }

    public int getNetworkLatency() {
        return this.syncDriver.getLatencia();
    }

    public void setBufferLatency(int bufferId, int latency) {
        StateAccess.enter();
        try {
            Buffer b = getBufferById(bufferId);
            if (b != null) {
                b.setLatency(latency);
                int nuevaLat = b.getLatency();
                MyLinkedList<ProcessControlBlock> procs = getAllProcesses();
                for (int i = 0; i < procs.size(); i++) {
                    ProcessControlBlock p = procs.get(i);
                    if (p != null && p.getState() == ProcessState.BLOQUEADO && p.getBlockReason() == BlockReason.NETWORK_LATENCY) {
                        int pBufId = p.getPermitBufferId();
                        if (pBufId <= 0 && p.getTargetBufferId() != null) {
                            try {
                                pBufId = Integer.parseInt(p.getTargetBufferId().replaceAll("[^0-9]", ""));
                            } catch (Exception ignored) {}
                        }
                        if (pBufId == bufferId) {
                            if (p.getNetworkDelayRemaining() > nuevaLat) {
                                p.setNetworkDelayRemaining(nuevaLat);
                                if (nuevaLat <= 0) {
                                    p.setNetworkDelayDone(true);
                                }
                            }
                        }
                    }
                }
                logEvent("Latencia del Buffer " + bufferId + " actualizada a " + nuevaLat + " ticks");
                notifyGUI();
            } else {
                logEvent("Error: No se encontrÃ³ el Buffer con ID " + bufferId);
            }
        } finally { StateAccess.exit(); }
    }

    // =========================================================================
    // AVANCE SINCRONIZADO POR CICLO (IClockTickable) Y REPORTE DE LOGS
    // =========================================================================

    @Override
    public void tick(int currentCycle) {
        StateAccess.enter();
        try {
        StringBuilder tickReport = new StringBuilder("CPU al finalizar ciclo -> ");
        boolean hayComputadores = false, hayCpuOcupada = false, hayProcesos = false, hayPendientes = false;

        // Muestrear todos los bloqueos antes de ejecutar cualquier computador: un signal remoto
        // no debe borrar la espera de este ciclo según el orden de los computadores.
        for (int i = 0; i < computers.size(); i++) {
            Computer computer = computers.get(i);
            if (computer == null || computer.getKernel() == null) continue;
            var processes = computer.getKernel().getProcessList();
            for (int j = 0; j < processes.size(); j++) {
                ProcessControlBlock process = processes.get(j);
                if (process != null) process.sampleBlockedWait(currentCycle);
            }
        }

        for (int i = 0; i < this.computers.size(); i++) {
            Computer comp = this.computers.get(i);
            if (comp != null && comp.getKernel() != null) {
                comp.getKernel().executeCycle(comp, currentCycle, this.syncDriver, this.buffers);

                // Construcción del reporte de log por cada CPU
                ProcessControlBlock runningPcb = comp.getCpu().getPCB();
                if (runningPcb != null) hayCpuOcupada = true;
                var procesos = comp.getKernel().getProcessList();
                for (int j = 0; j < procesos.size(); j++) {
                    ProcessControlBlock p = procesos.get(j);
                    if (p != null) { hayProcesos = true; if (!p.isTerminated()) hayPendientes = true; }
                }
                String estadoCpu = (runningPcb != null) ? runningPcb.getName() + " (PC:" + comp.getCpu().getPC() + ")" : "Inactiva";

                tickReport.append("[").append(comp.getName()).append(": ").append(estadoCpu).append("] ");
                hayComputadores = true;
            }
        }

        if (hayCpuOcupada) {
            ultimoEstadoInactividad = 0;
            logEvent(tickReport.toString().trim());
        } else if (hayComputadores) {
            int estado = hayProcesos && !hayPendientes ? 2 : 1;
            if (estado != ultimoEstadoInactividad) {
                logEvent(estado == 2
                        ? "Todos los procesos terminaron; las CPUs están inactivas. El reloj continúa."
                        : "Todas las CPUs están inactivas; puede haber procesos pendientes. El reloj continúa.");
                ultimoEstadoInactividad = estado;
            }
        }

        notifyGUI(); // Actualiza visualmente la GUI

        } finally { StateAccess.exit(); }
    }

    // =========================================================================
    // GETTERS PARA CONSULTA DE ESTADO DESDE LA GUI (Observabilidad)
    // =========================================================================

    public MyLinkedList<Computer> getComputers() {
        return computers;
    }

    public MyLinkedList<Buffer> getBuffers() {
        return buffers;
    }

    public Computer getComputerById(int id) {
        for (int i = 0; i < this.computers.size(); i++) {
            Computer c = this.computers.get(i);
            if (c != null && c.getId() == id) {
                return c;
            }
        }
        return null;
    }

    public Buffer getBufferById(int id) {
        for (int i = 0; i < this.buffers.size(); i++) {
            Buffer b = this.buffers.get(i);
            if (b != null && b.getId() == id) {
                return b;
            }
        }
        return null;
    }

    public MyLinkedList<ProcessControlBlock> getAllProcesses() {
        MyLinkedList<ProcessControlBlock> all = new MyLinkedList<>();
        for (int i = 0; i < this.computers.size(); i++) {
            Computer comp = this.computers.get(i);
            if (comp != null && comp.getKernel() != null) {
                MyLinkedList<ProcessControlBlock> list = comp.getKernel().getProcessList();
                if (list != null) {
                    for (int j = 0; j < list.size(); j++) {
                        all.append(list.get(j));
                    }
                }
            }
        }
        return all;
    }

    public void restoreProcess(ProcessControlBlock pcb) {
        StateAccess.enter();
        try {
        if (pcb == null) return;
        Computer comp = getComputerById(pcb.getComputerId());
        if (comp != null && comp.getKernel() != null) {
            comp.getKernel().restoreProcess(comp, pcb);
            notifyGUI();
        }

        } finally { StateAccess.exit(); }
    }

    public void reset() {
        StateAccess.enter();
        try {
        session++;
        this.clock.reset();
        this.clock.addSubscriber(this);
        while (!this.computers.isEmpty()) {
            this.computers.deletePos(0);
        }
        while (!this.buffers.isEmpty()) {
            this.buffers.deletePos(0);
        }
        this.nextComputerId = 1;
        this.nextBufferId = 1;
        this.ultimoEstadoInactividad = 0;
        ProcessControlBlock.resetIdGenerator(1);

        logEvent("Simulador reiniciado a estado inicial");
        notifyGUI();

        } finally { StateAccess.exit(); }
    }
}
