package com.avilaos.simulation;

import com.avilaos.core.kernel.ComputerKernel;
import com.avilaos.core.kernel.GlobalClock;
import com.avilaos.core.kernel.IClockTickable;
import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ExecutionMode;
import com.avilaos.core.model.IOOperationType;
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
// [P2-LEGACY]     private final MyLinkedList<Buffer> buffers;
    private final GlobalClock clock; // [P2] restaurado (no es de buffers)
// [P2-LEGACY]     private final ProducerConsumerDriver syncDriver;

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
                // [P2-LEGACY] clock.getCycleDurationMs(), computers, buffers, snapshot);
                clock.getCycleDurationMs(), computers, snapshot); // [P2] sin buffers
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

    // --- Componentes Proyecto 2: Disco, Sistema de Archivos, Permisos y Carga Dual JSON ---
    private com.avilaos.disk.SimulatedDisk disk = new com.avilaos.disk.SimulatedDisk(200, 100, 50, 10);
    private com.avilaos.filesystem.FileSystemManager fileSystemManager = new com.avilaos.filesystem.FileSystemManager(disk);
    private final com.avilaos.filesystem.AccessControlManager accessControl = new com.avilaos.filesystem.AccessControlManager();
    private int configuredSwapSizeBlocks = 100;
    private boolean fileSystemJsonLoaded = false;
    private boolean processesJsonLoaded = false;
    private boolean dualJsonGateActive = false;

    private SimulationController() {
        this.computers = new MyLinkedList<>();
// [P2-LEGACY]         this.buffers = new MyLinkedList<>();
        this.guiListeners = new MyLinkedList<>();
        this.logListeners = new MyLinkedList<>();
        this.clock = GlobalClock.getInstance();
// [P2-LEGACY]         this.syncDriver = new ProducerConsumerDriver(2); // Latencia por defecto: 2 ciclos
// [P2-LEGACY]         this.syncDriver.setEventListener(this::logEvent);
        this.clock.addSubscriber(this);
        this.disk.setEventListener(this::logEvent);
        this.disk.setCompletionListener(this::onDiskRequestCompleted);
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
        // [P2] Una sola instancia de computador.
        if (computers.size() >= 1) {
            throw new IllegalArgumentException("Proyecto 2: solo se admite un computador");
        }
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
                new MyQueue<>(),
                new MyQueue<>(),
                this.clock
        );
        kernel.setEventListener(this::logEvent);
        kernel.setDisk(this.disk);

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

    /** Crea un proceso FILE_IO desde la GUI con operación CRUD, ruta, tamaño en bloques, dueño y modo. */
    public ProcessControlBlock createConfigFileIOProcess(String name, int computerId,
            IOOperationType operation, String targetPath, int fileSizeBlocks,
            String owner, ExecutionMode mode, String newFileName,
            int priority, int deadline) {
        StateAccess.enter();
        try {
            if (name == null || name.trim().isEmpty()) {
                logEvent("Error: El nombre del proceso FILE_IO no puede estar vacío");
                return null;
            }
            if (operation == null) {
                logEvent("Error: Debes especificar una operación CRUD para el proceso FILE_IO");
                return null;
            }
            if (targetPath == null || !targetPath.startsWith("/")) {
                logEvent("Error: La ruta del archivo debe comenzar con '/' (recibido: " + targetPath + ")");
                return null;
            }
            if (operation == IOOperationType.CREATE && fileSizeBlocks < 1) {
                logEvent("Error: El tamaño del archivo debe ser al menos 1 bloque para CREATE");
                return null;
            }
            if (operation == IOOperationType.UPDATE && (newFileName == null || newFileName.trim().isEmpty())) {
                logEvent("Error: Debes especificar un nuevo nombre para la operación UPDATE");
                return null;
            }
            // 1 instruction = 1 unit of RAM for FILE_IO
            int instructions = Math.max(1, fileSizeBlocks > 0 ? fileSizeBlocks : 1);
            ProcessControlBlock pcb = createProcess(name, computerId, ProcessType.FILE_IO,
                    instructions, instructions, priority, deadline);
            if (pcb == null) return null;
            pcb.setIoOperation(operation);
            pcb.setTargetPath(targetPath.trim());
            pcb.setFileSizeBlocks(fileSizeBlocks);
            pcb.setOwner(owner != null && !owner.trim().isEmpty() ? owner.trim() : "admin");
            pcb.setMode(mode != null ? mode : ExecutionMode.USER);
            pcb.setNewFileName(newFileName != null ? newFileName.trim() : "");
            logEvent("Proceso FILE_IO creado: " + pcb.getName() + " (ID " + pcb.getId() + ") op=" + operation
                    + " ruta=" + targetPath + (operation == IOOperationType.CREATE ? " bloques=" + fileSizeBlocks : "")
                    + " dueño=" + pcb.getOwner() + " modo=" + mode);
            notifyGUI();
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
        // [P2] Los tipos PRODUCER/CONSUMER dejan de existir en la práctica.
        if (type == ProcessType.PRODUCER || type == ProcessType.CONSUMER) {
            logEvent("Error: tipo de proceso no soportado en Proyecto 2 (" + type + ")");
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

// [P2-LEGACY]     public Buffer createBuffer(int hostComputerId, int storage) {
// [P2-LEGACY]         StateAccess.enter();
// [P2-LEGACY]         try {
// [P2-LEGACY]         return createBuffer(hostComputerId, storage, storage, getNetworkLatency());
// [P2-LEGACY] 
// [P2-LEGACY]         } finally { StateAccess.exit(); }
// [P2-LEGACY]     }
// [P2-LEGACY] 
// [P2-LEGACY]     /** Compatibilidad con cargas anteriores: la RAM se deriva de capacidad, no del campo legacy memory. */
// [P2-LEGACY]     public Buffer createBuffer(int hostComputerId, int storage, int latency) {
// [P2-LEGACY]         StateAccess.enter();
// [P2-LEGACY]         try {
// [P2-LEGACY]         return createBuffer(hostComputerId, storage, storage, latency);
// [P2-LEGACY] 
// [P2-LEGACY]         } finally { StateAccess.exit(); }
// [P2-LEGACY]     }
// [P2-LEGACY] 
// [P2-LEGACY] 
// [P2-LEGACY]     public Buffer createBuffer(int hostComputerId, int storage, int memory, int latency) {
// [P2-LEGACY]         StateAccess.enter();
// [P2-LEGACY]         try {
// [P2-LEGACY]         if (storage <= 0) {
// [P2-LEGACY]             return null;
// [P2-LEGACY]         }
// [P2-LEGACY]         Computer host = getComputerById(hostComputerId);
// [P2-LEGACY]         if (host == null || host.getKernel() == null) {
// [P2-LEGACY]             logEvent("Error: Computador anfitrión inválido para crear Buffer");
// [P2-LEGACY]             return null;
// [P2-LEGACY]         }
// [P2-LEGACY] 
// [P2-LEGACY]         int id = nextBufferId;
// [P2-LEGACY]         Buffer buffer = new Buffer(null, null, null, hostComputerId, storage, id, new MyQueue<>(), storage, latency);
// [P2-LEGACY]         boolean success = host.getKernel().placeBufferRAM(buffer, host);
// [P2-LEGACY]         if (!success) {
// [P2-LEGACY]             logEvent("Error: Memoria RAM insuficiente en " + host.getName() + " para crear el Buffer " + id);
// [P2-LEGACY]             return null;
// [P2-LEGACY]         }
// [P2-LEGACY] 
// [P2-LEGACY]         nextBufferId++;
// [P2-LEGACY]         this.buffers.append(buffer);
// [P2-LEGACY] 
// [P2-LEGACY]         logEvent("Buffer " + id + " creado en " + host.getName() + " con capacidad " + storage + " y latencia " + latency + " ticks");
// [P2-LEGACY]         notifyGUI();
// [P2-LEGACY]         return buffer;
// [P2-LEGACY] 
// [P2-LEGACY]         } finally { StateAccess.exit(); }
// [P2-LEGACY]     }

    // =========================================================================
    // CONTROL DEL RELOJ GLOBAL (Play, Pausa, Step, Velocidad)
    // =========================================================================

    public void startSimulation() {
        StateAccess.enter();
        try {
            if (dualJsonGateActive && !isReadyToStartClock()) {
                String msg = "Error: Debes cargar ambos archivos JSON (Sistema de Archivos y Procesos/Configuración) antes de iniciar el reloj global.";
                logEvent(msg);
                throw new IllegalStateException(msg);
            }
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
        StateAccess.enter();
        try {
            if (dualJsonGateActive && !isReadyToStartClock()) {
                String msg = "Error: Debes cargar ambos archivos JSON (Sistema de Archivos y Procesos/Configuración) antes de avanzar el reloj global.";
                logEvent(msg);
                throw new IllegalStateException(msg);
            }
            this.clock.step();
            notifyGUI();
        } finally { StateAccess.exit(); }
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

// [P2-LEGACY]     public void setNetworkLatency(int latencyCycles) {
// [P2-LEGACY]         StateAccess.enter();
// [P2-LEGACY]         try { 
// [P2-LEGACY]             this.syncDriver.setLatencia(latencyCycles);
// [P2-LEGACY]             for (int i = 0; i < this.buffers.size(); i++) {
// [P2-LEGACY]                 Buffer b = this.buffers.get(i);
// [P2-LEGACY]                 if (b != null) {
// [P2-LEGACY]                     b.setLatency(latencyCycles);
// [P2-LEGACY]                 }
// [P2-LEGACY]             }
// [P2-LEGACY]             logEvent("Latencia de red actualizada a " + latencyCycles + " ciclos");
// [P2-LEGACY]         } finally { StateAccess.exit(); }
// [P2-LEGACY]     }
// [P2-LEGACY] 
// [P2-LEGACY]     public int getNetworkLatency() {
// [P2-LEGACY]         return this.syncDriver.getLatencia();
// [P2-LEGACY]     }
// [P2-LEGACY] 
// [P2-LEGACY]     public void setBufferLatency(int bufferId, int latency) {
// [P2-LEGACY]         StateAccess.enter();
// [P2-LEGACY]         try {
// [P2-LEGACY]             Buffer b = getBufferById(bufferId);
// [P2-LEGACY]             if (b != null) {
// [P2-LEGACY]                 b.setLatency(latency);
// [P2-LEGACY]                 int nuevaLat = b.getLatency();
// [P2-LEGACY]                 MyLinkedList<ProcessControlBlock> procs = getAllProcesses();
// [P2-LEGACY]                 for (int i = 0; i < procs.size(); i++) {
// [P2-LEGACY]                     ProcessControlBlock p = procs.get(i);
// [P2-LEGACY]                     if (p != null && p.getState() == ProcessState.BLOQUEADO && p.getBlockReason() == BlockReason.NETWORK_LATENCY) {
// [P2-LEGACY]                         int pBufId = p.getPermitBufferId();
// [P2-LEGACY]                         if (pBufId <= 0 && p.getTargetBufferId() != null) {
// [P2-LEGACY]                             try {
// [P2-LEGACY]                                 pBufId = Integer.parseInt(p.getTargetBufferId().replaceAll("[^0-9]", ""));
// [P2-LEGACY]                             } catch (Exception ignored) {}
// [P2-LEGACY]                         }
// [P2-LEGACY]                         if (pBufId == bufferId) {
// [P2-LEGACY]                             if (p.getNetworkDelayRemaining() > nuevaLat) {
// [P2-LEGACY]                                 p.setNetworkDelayRemaining(nuevaLat);
// [P2-LEGACY]                                 if (nuevaLat <= 0) {
// [P2-LEGACY]                                     p.setNetworkDelayDone(true);
// [P2-LEGACY]                                 }
// [P2-LEGACY]                             }
// [P2-LEGACY]                         }
// [P2-LEGACY]                     }
// [P2-LEGACY]                 }
// [P2-LEGACY]                 logEvent("Latencia del Buffer " + bufferId + " actualizada a " + nuevaLat + " ticks");
// [P2-LEGACY]                 notifyGUI();
// [P2-LEGACY]             } else {
// [P2-LEGACY]                 logEvent("Error: No se encontrÃ³ el Buffer con ID " + bufferId);
// [P2-LEGACY]             }
// [P2-LEGACY]         } finally { StateAccess.exit(); }
// [P2-LEGACY]     }

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

        // [P2] Avanzar el hardware del disco simulado exactamente un ciclo de reloj
        this.disk.tick(currentCycle);

        for (int i = 0; i < this.computers.size(); i++) {
            Computer comp = this.computers.get(i);
            if (comp != null && comp.getKernel() != null) {
                // [P2-LEGACY] comp.getKernel().executeCycle(comp, currentCycle, this.syncDriver, this.buffers);
                comp.getKernel().executeCycle(comp, currentCycle); // [P2] sin syncDriver/buffers

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

// [P2-LEGACY]     public MyLinkedList<Buffer> getBuffers() {
// [P2-LEGACY]         return buffers;
// [P2-LEGACY]     }

    public Computer getComputerById(int id) {
        for (int i = 0; i < this.computers.size(); i++) {
            Computer c = this.computers.get(i);
            if (c != null && c.getId() == id) {
                return c;
            }
        }
        return null;
    }

// [P2-LEGACY]     public Buffer getBufferById(int id) {
// [P2-LEGACY]         for (int i = 0; i < this.buffers.size(); i++) {
// [P2-LEGACY]             Buffer b = this.buffers.get(i);
// [P2-LEGACY]             if (b != null && b.getId() == id) {
// [P2-LEGACY]                 return b;
// [P2-LEGACY]             }
// [P2-LEGACY]         }
// [P2-LEGACY]         return null;
// [P2-LEGACY]     }

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
// [P2-LEGACY]         while (!this.buffers.isEmpty()) {
// [P2-LEGACY]             this.buffers.deletePos(0);
// [P2-LEGACY]         }
        this.nextComputerId = 1;
        this.nextBufferId = 1;
        this.ultimoEstadoInactividad = 0;
        this.fileSystemJsonLoaded = false;
        this.processesJsonLoaded = false;
        this.dualJsonGateActive = false;
        this.configuredSwapSizeBlocks = 100;
        this.disk = new com.avilaos.disk.SimulatedDisk(200, 100, 50, 10);
        this.disk.setEventListener(this::logEvent);
        this.disk.setCompletionListener(this::onDiskRequestCompleted);
        this.fileSystemManager = new com.avilaos.filesystem.FileSystemManager(this.disk);
        this.accessControl.setSession(com.avilaos.core.model.UserMode.ADMIN, "admin");
        ProcessControlBlock.resetIdGenerator(1);

        logEvent("Simulador reiniciado a estado inicial");
        notifyGUI();

        } finally { StateAccess.exit(); }
    }

    // =========================================================================
    // PROYECTO 2: DISCO, SISTEMA DE ARCHIVOS, PERMISOS Y PERSISTENCIA DUAL JSON
    // =========================================================================

    public com.avilaos.disk.SimulatedDisk getDisk() {
        return disk;
    }

    public com.avilaos.filesystem.FileSystemManager getFileSystemManager() {
        return fileSystemManager;
    }

    public com.avilaos.filesystem.AccessControlManager getAccessControl() {
        return accessControl;
    }

    public boolean isFileSystemJsonLoaded() {
        return fileSystemJsonLoaded;
    }

    public boolean isProcessesJsonLoaded() {
        return processesJsonLoaded;
    }

    public boolean isReadyToStartClock() {
        return fileSystemJsonLoaded && processesJsonLoaded;
    }

    public void setDualJsonGateActive(boolean active) {
        this.dualJsonGateActive = active;
    }

    /**
     * Carga y valida el JSON #1 (Estado del Sistema de Archivos: diskConfig + fileSystem).
     */
    public void loadFileSystemJson(String json) {
        StateAccess.enter();
        try {
            com.avilaos.io.FileSystemJsonSerializer.LoadedFileSystem loaded =
                    com.avilaos.io.FileSystemJsonSerializer.loadFromString(json, configuredSwapSizeBlocks);
            com.avilaos.disk.DiskSchedulingPolicy currentPolicy = this.disk != null ? this.disk.getSchedulingPolicy() : null;
            this.disk = loaded.disk();
            if (currentPolicy != null) {
                this.disk.setSchedulingPolicy(currentPolicy);
            }
            this.disk.setEventListener(this::logEvent);
            this.disk.setCompletionListener(this::onDiskRequestCompleted);
            this.fileSystemManager = loaded.fileSystemManager();
            this.fileSystemJsonLoaded = true;
            this.dualJsonGateActive = true;
            updateComputersDiskReference();
            logEvent("JSON del Sistema de Archivos cargado: " + loaded.totalBlocks()
                    + " bloques, cabezal inicial en " + loaded.initialHead());
            notifyGUI();
        } finally {
            StateAccess.exit();
        }
    }

    public void loadFileSystemJson(java.io.File file) throws java.io.IOException {
        StateAccess.enter();
        try {
            com.avilaos.io.FileSystemJsonSerializer.LoadedFileSystem loaded =
                    com.avilaos.io.FileSystemJsonSerializer.loadFromFile(file, configuredSwapSizeBlocks);
            com.avilaos.disk.DiskSchedulingPolicy currentPolicy = this.disk != null ? this.disk.getSchedulingPolicy() : null;
            this.disk = loaded.disk();
            if (currentPolicy != null) {
                this.disk.setSchedulingPolicy(currentPolicy);
            }
            this.disk.setEventListener(this::logEvent);
            this.disk.setCompletionListener(this::onDiskRequestCompleted);
            this.fileSystemManager = loaded.fileSystemManager();
            this.fileSystemJsonLoaded = true;
            this.dualJsonGateActive = true;
            updateComputersDiskReference();
            logEvent("JSON del Sistema de Archivos cargado desde archivo: " + file.getName());
            notifyGUI();
        } finally {
            StateAccess.exit();
        }
    }

    /**
     * Carga y valida el JSON #2 (Procesos y Configuración: globalConfig + computer + processes).
     * Si el disco y el sistema de archivos ya estaban cargados (o modificados por una corrida previa),
     * preserva intacto el estado de los bloques de archivos y el árbol del sistema de archivos (T44).
     */
    public void loadProcessesConfigJson(String json) {
        StateAccess.enter();
        try {
            com.avilaos.io.ProcessConfigJsonSerializer.LoadedProcessConfig cfg =
                    com.avilaos.io.ProcessConfigJsonSerializer.loadFromString(json);

            this.clock.stop();
            this.clock.setCycleCounter(0);
            this.clock.setCycleDurationMs(cfg.globalConfig().cycleDurationMs());
            this.configuredSwapSizeBlocks = cfg.globalConfig().swapSizeBlocks();

            // Si el disco ya tenía un tamaño de swap distinto, redimensionar la partición de swap
            // preservando todos los bloques de datos y el árbol de archivos actual.
            if (this.disk != null && this.disk.getSwapSizeBlocks() != this.configuredSwapSizeBlocks) {
                this.disk = rebuildDiskPreservingDataBlocks(this.disk, this.configuredSwapSizeBlocks);
                this.fileSystemManager.setDisk(this.disk);
            }
            if (this.disk != null) {
                this.disk.setSchedulingPolicy(cfg.globalConfig().initialDiskPolicy());
                this.disk.setEventListener(this::logEvent);
                this.disk.setCompletionListener(this::onDiskRequestCompleted);
            }

            // Reiniciar únicamente el computador y los procesos sin borrar el sistema de archivos
            while (!this.computers.isEmpty()) {
                this.computers.deletePos(0);
            }
            this.nextComputerId = 1;
            ProcessControlBlock.resetIdGenerator(1);

            Computer comp = createComputer("AvilaOS-PC",
                    cfg.computer().ramCapacity(),
                    cfg.computer().policy(),
                    cfg.computer().quantum());

            for (int i = 0; i < cfg.processes().size(); i++) {
                var p = cfg.processes().get(i);
                ProcessControlBlock pcb = createProcess(
                        p.name(), comp.getId(), ProcessType.FILE_IO,
                        p.instructions(), p.memory(), p.priority(), p.deadline());
                if (pcb != null) {
                    pcb.setIoOperation(p.operation());
                    pcb.setTargetPath(p.targetPath());
                    pcb.setFileSizeBlocks(p.fileSizeBlocks());
                    pcb.setOwner(p.owner());
                    pcb.setNewFileName(p.newFileName());
                }
            }

            this.processesJsonLoaded = true;
            this.dualJsonGateActive = true;
            logEvent("JSON de Procesos y Configuración cargado: " + cfg.processes().size()
                    + " procesos, política disco " + cfg.globalConfig().initialDiskPolicy()
                    + ", swap " + this.configuredSwapSizeBlocks + " bloques");
            notifyGUI();
        } finally {
            StateAccess.exit();
        }
    }

    public void loadProcessesConfigJson(java.io.File file) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        try (java.io.FileReader fr = new java.io.FileReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int n;
            while ((n = fr.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        loadProcessesConfigJson(sb.toString());
    }

    public String exportFileSystemJson() {
        StateAccess.enter();
        try {
            return com.avilaos.io.FileSystemJsonSerializer.toJsonString(this.disk, this.fileSystemManager);
        } finally {
            StateAccess.exit();
        }
    }

    public void saveFileSystemJson(java.io.File file) throws java.io.IOException {
        StateAccess.enter();
        try {
            com.avilaos.io.FileSystemJsonSerializer.saveToFile(file, this.disk, this.fileSystemManager);
            logEvent("Estado del Sistema de Archivos guardado en " + file.getName());
        } finally {
            StateAccess.exit();
        }
    }

    private static com.avilaos.disk.SimulatedDisk rebuildDiskPreservingDataBlocks(
            com.avilaos.disk.SimulatedDisk oldDisk, int newSwapSizeBlocks) {
        int totalData = oldDisk.getTotalBlocks();
        int head = oldDisk.getHeadPosition();
        com.avilaos.disk.SimulatedDisk rebuilt = new com.avilaos.disk.SimulatedDisk(
                totalData, newSwapSizeBlocks, head, oldDisk.getHeadSpeedPerTick());
        for (int i = 0; i < totalData; i++) {
            com.avilaos.disk.DiskBlock src = oldDisk.getBlock(i);
            if (src.isOccupied()) {
                rebuilt.allocateChainedBlocksFrom(i, src.getOccupiedBy(), 1, src.getOwnerProcessId());
                rebuilt.getBlock(i).setNextBlock(src.getNextBlock());
            }
        }
        rebuilt.setSchedulingPolicy(oldDisk.getSchedulingPolicy());
        return rebuilt;
    }

    private void updateComputersDiskReference() {
        for (int i = 0; i < computers.size(); i++) {
            Computer c = computers.get(i);
            if (c != null && c.getKernel() != null) {
                c.getKernel().setDisk(this.disk);
            }
        }
    }

    private void onDiskRequestCompleted(com.avilaos.disk.IORequest request, int cycle) {
        if (request == null) return;
        for (int i = 0; i < computers.size(); i++) {
            Computer c = computers.get(i);
            if (c != null && c.getKernel() != null) {
                c.getKernel().handleDiskCompletion(c, request, cycle);
            }
        }
        notifyGUI();
    }
    
    public void reconfigurarDisco(int totalBlocks, int swapSizeBlocks) {
        com.avilaos.concurrency.StateAccess.enter();
        try {
            int headInicial = 0; 
            com.avilaos.disk.DiskSchedulingPolicy currentPolicy = null;
            
            if (this.disk != null) {
                currentPolicy = this.disk.getSchedulingPolicy();
                // Mantener la posición del cabezal si cabe en el nuevo tamaño
                if (this.disk.getHeadPosition() < totalBlocks) {
                    headInicial = this.disk.getHeadPosition();
                }
            }
            this.disk = new com.avilaos.disk.SimulatedDisk(totalBlocks, swapSizeBlocks, headInicial, 10);
            this.configuredSwapSizeBlocks = swapSizeBlocks;
            if (currentPolicy != null) {
                this.disk.setSchedulingPolicy(currentPolicy);
            }
            this.disk.setEventListener(this::logEvent);
            this.disk.setCompletionListener(this::onDiskRequestCompleted);
            
            this.fileSystemManager = new com.avilaos.filesystem.FileSystemManager(this.disk);
            
            updateComputersDiskReference();
            
            logEvent("Disco reconfigurado manualmente: " + totalBlocks + " bloques de datos, " + swapSizeBlocks + " bloques de swap.");
            notifyGUI();
        } finally {
            com.avilaos.concurrency.StateAccess.exit();
        }
    }
}
