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
import com.avilaos.core.model.UserMode;

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
    private UserMode currentMode = UserMode.ADMIN;
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
        kernel.setFileSystemManager(this.fileSystemManager);
        kernel.setAccessControl(this.accessControl);

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
            int instructions = operation == IOOperationType.READ
                    ? com.avilaos.core.model.ProcessSizing.fileReadInstructions()
                    : Math.max(1, fileSizeBlocks > 0 ? fileSizeBlocks : 1);
            ProcessControlBlock pcb = createProcess(name, computerId, ProcessType.FILE_IO,
                    instructions, instructions, priority, deadline);
            if (pcb == null) return null;
            pcb.setIoOperation(operation);
            pcb.setTargetPath(targetPath.trim());
            pcb.setFileSizeBlocks(fileSizeBlocks);
            pcb.setOwner(owner != null && !owner.trim().isEmpty() ? owner.trim() : "admin");
            pcb.setMode(mode != null ? mode : ExecutionMode.USER);
            pcb.setNewFileName(newFileName != null ? newFileName.trim() : "");
            pcb.setRequesterMode(currentMode);
            pcb.setRequesterName(accessControl.getActiveUsername());
            if (currentMode == UserMode.USER) pcb.setOwner(accessControl.getActiveUsername());
            logEvent("Proceso FILE_IO creado: " + pcb.getName() + " (ID " + pcb.getId() + ") op=" + operation
                    + " ruta=" + targetPath + (operation == IOOperationType.CREATE ? " bloques=" + fileSizeBlocks : "")
                    + " dueño=" + pcb.getOwner() + " modo=" + mode);
            notifyGUI();
            return pcb;
        } finally { StateAccess.exit(); }
    }

    /** Solicita leer un archivo usando la identidad activa al crear el proceso. */
    public ProcessControlBlock requestRead(String path) {
        StateAccess.enter();
        try {
            if (computers.isEmpty()) throw new IllegalStateException("Crea primero un computador.");
            var node = fileSystemManager.findNode(path);
            if (node == null || !node.isFile()) throw new IllegalArgumentException("Selecciona un archivo existente.");
            UserMode mode = currentMode;
            String actor = mode == UserMode.ADMIN ? "admin" : accessControl.getActiveUsername();
            var decision = new com.avilaos.filesystem.AccessControlManager(mode, actor).checkOperation(
                    fileSystemManager.getTree(), IOOperationType.READ, path, node.getType());
            if (!decision.allowed()) throw new IllegalArgumentException(decision.message());
            ProcessControlBlock p = createProcess("Leer " + path, computers.get(0).getId(), ProcessType.FILE_IO, 2, 2, 5, 10000);
            if (p == null) throw new IllegalStateException("No se pudo crear el proceso; revisa RAM y log.");
            p.setIoOperation(IOOperationType.READ);
            p.setTargetPath(path);
            p.setRequesterMode(mode);
            p.setRequesterName(actor);
            return p;
        } finally { StateAccess.exit(); }
    }

    /** Solicita eliminar un archivo o directorio cuando el disco atienda la E/S. */
    public ProcessControlBlock requestDelete(String path) {
        StateAccess.enter();
        try {
            if (computers.isEmpty()) throw new IllegalStateException("Crea primero un computador.");
            var node = fileSystemManager.findNode(path);
            if (node == null || "/".equals(path)) throw new IllegalArgumentException("Selecciona un archivo o directorio distinto de la raíz.");
            UserMode mode = currentMode;
            String actor = mode == UserMode.ADMIN ? "admin" : accessControl.getActiveUsername();
            var decision = new com.avilaos.filesystem.AccessControlManager(mode, actor).checkOperation(
                    fileSystemManager.getTree(), IOOperationType.DELETE, path, node.getType());
            if (!decision.allowed()) throw new IllegalArgumentException(decision.message());
            ProcessControlBlock p = createProcess("Eliminar " + path, computers.get(0).getId(), ProcessType.FILE_IO, 2, 2, 5, 10000);
            if (p == null) throw new IllegalStateException("No se pudo crear el proceso; revisa RAM y log.");
            p.setIoOperation(IOOperationType.DELETE);
            p.setTargetPath(path);
            p.setRequesterMode(mode);
            p.setRequesterName(actor);
            return p;
        } finally { StateAccess.exit(); }
    }

    public ProcessControlBlock requestRename(String path, String newName) {
        StateAccess.enter();
        try {
            if (computers.isEmpty()) throw new IllegalStateException("Crea primero un computador.");
            if (currentMode != UserMode.ADMIN) throw new IllegalArgumentException("Solo el administrador puede renombrar archivos y directorios.");
            var node = fileSystemManager.findNode(path);
            if (node == null || "/".equals(path)) throw new IllegalArgumentException("Selecciona un archivo o directorio distinto de la raíz.");
            String name = newName == null ? "" : newName.trim();
            if (name.isEmpty() || name.contains("/") || name.equals(".") || name.equals(".."))
                throw new IllegalArgumentException("Indica un nombre válido.");
            var sibling = node.getParent().findChild(name);
            if (sibling != null && sibling != node) throw new IllegalArgumentException("Ya existe un elemento con ese nombre.");
            ProcessControlBlock p = createProcess("Renombrar " + path, computers.get(0).getId(), ProcessType.FILE_IO, 2, 2, 5, 10000);
            if (p == null) throw new IllegalStateException("No se pudo crear el proceso; revisa RAM y log.");
            p.setIoOperation(IOOperationType.UPDATE);
            p.setTargetPath(path);
            p.setNewFileName(name);
            p.setRequesterMode(currentMode);
            p.setRequesterName("admin");
            return p;
        } finally { StateAccess.exit(); }
    }

    public ProcessControlBlock requestCreation(String path, com.avilaos.filesystem.FsNodeType type,
            int blocks, String owner, com.avilaos.filesystem.FilePermission permission) {
        StateAccess.enter();
        try {
            if (computers.isEmpty()) throw new IllegalStateException("Crea primero un computador.");
            if (path == null || !path.startsWith("/") || path.length() < 2 || path.endsWith("/"))
                throw new IllegalArgumentException("Indica una ruta absoluta y un nombre válido.");
            if (type == null || permission == null || blocks < 1 || (type == com.avilaos.filesystem.FsNodeType.DIRECTORY && blocks != 1))
                throw new IllegalArgumentException("Tipo, permiso o tamaño inválido.");
            UserMode mode = currentMode;
            String actor = mode == UserMode.ADMIN ? "admin" : accessControl.getActiveUsername();
            String effectiveOwner = mode == UserMode.ADMIN ? owner : actor;
            if (effectiveOwner == null || effectiveOwner.trim().isEmpty()) throw new IllegalArgumentException("Indica el dueño.");
            var decision = new com.avilaos.filesystem.AccessControlManager(mode, actor).checkOperation(
                    fileSystemManager.getTree(), IOOperationType.CREATE, path, type);
            if (!decision.allowed()) throw new IllegalArgumentException(decision.message());
            ProcessControlBlock p = createProcess("Crear " + path, computers.get(0).getId(), ProcessType.FILE_IO, 2, 2, 5, 10000);
            if (p == null) throw new IllegalStateException("No se pudo crear el proceso; revisa RAM y log.");
            p.setIoOperation(IOOperationType.CREATE);
            p.setTargetPath(path);
            p.setFileSizeBlocks(blocks);
            p.setOwner(effectiveOwner.trim());
            p.setCreationType(type);
            p.setCreationPermission(permission);
            p.setRequesterMode(mode);
            p.setRequesterName(actor);
            return p;
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

    public void startSimulation() {
        StateAccess.enter();
        try {
            if (dualJsonGateActive && !isReadyToStartClock()) {
                String msg = "Error: Prepara un computador con procesos o carga el JSON de procesos y configuración.";
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
                String msg = "Error: Prepara un computador con procesos o carga el JSON de procesos y configuración.";
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
    public MyLinkedList<Computer> getComputers() {
        return computers;
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
        this.currentMode = UserMode.ADMIN;
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
        if (computers.isEmpty()) return false;
        if (processesJsonLoaded) return true;
        for (int i = 0; i < computers.size(); i++) {
            Computer computer = computers.get(i);
            if (computer != null && computer.getKernel() != null && !computer.getKernel().getProcessList().isEmpty()) return true;
        }
        return false;
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
                    pcb.setRequesterMode(currentMode);
                    pcb.setRequesterName(accessControl.getActiveUsername());
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
                c.getKernel().setFileSystemManager(this.fileSystemManager);
                c.getKernel().setAccessControl(this.accessControl);
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
        throw new IllegalStateException("Cambio de capacidad deshabilitado hasta proteger archivos, E/S y swap.");
    }
    
    public UserMode getMode() {
        return currentMode;
    }

    /** Cambia conjuntamente el modo y la identidad de las próximas solicitudes. */
    public void setUserSession(UserMode mode, String username) {
        if (mode == null) throw new IllegalArgumentException("Selecciona un modo de usuario.");
        if (mode == UserMode.USER && (username == null || username.trim().isEmpty())) {
            throw new IllegalArgumentException("Indica un nombre de usuario.");
        }
        String actor = mode == UserMode.ADMIN ? "admin" : username.trim();
        StateAccess.enter();
        try {
            accessControl.setSession(mode, actor);
            currentMode = mode;
            logEvent("Cambio de sesión: " + mode.name() + " · " + actor);
            notifyGUI();
        } finally { StateAccess.exit(); }
    }

    /**
     * Cambia el modo de usuario del sistema.
     * @param mode El nuevo modo (ADMIN o USER).
     */
    public void setMode(UserMode mode) {
        if (mode == null) return;
        this.currentMode = mode;
        this.accessControl.setActiveMode(mode);
        logEvent("Cambio de sesión: Ahora operando en modo " + mode.name());
    }
}
