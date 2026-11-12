package com.avilaos.core.model;

import java.util.concurrent.Semaphore;

/**
 * Bloque de Control de Proceso (PCB) de ÁvilaOS.
 * 
 * Contiene toda la metadata, métricas temporales, registros simulados
 * y parámetros de ejecución de un proceso en el clúster.
 * 
 * Requisitos: RF §1, RF §2, PCB-01 a PCB-11, D-04, D-06, D-07.
 */
public class ProcessControlBlock {

    private static int nextId = 1;
    private static final Semaphore ID_MUTEX = new Semaphore(1, true);

    /** Un único hilo asigna e incrementa el contador. No modifica el reloj ni PCBs existentes. */
    private static int allocateId() {
        ID_MUTEX.acquireUninterruptibly();
        try {
            if (nextId == Integer.MAX_VALUE)
                throw new IllegalStateException("Se agotaron los IDs de procesos");
            return nextId++;
        } finally {
            ID_MUTEX.release();
        }
    }

    // Identificación y ubicación
    private int id;
    private String name;
    private int computerId;
    private int idFather;

    // Estados y métricas
    private ProcessState state;
    private ProcessType type;
    private ExecutionMode mode;
    private BlockReason blockReason;
    private int priority;
    private int assignedMemory;
    /** Memoria solicitada al crear el proceso, incluso después de liberarla. */
    private int requiredMemory;

    // Tiempos e instrucciones
    private int totalInstructions;
    private int remainingTime;
    private int deadline;
    private int remainingDeadline;
    private int arrivalCycle;
    private int startCycle;
    private int completionCycle;
    /** Ciclos acumulados sin ejecutar (listo o bloqueado) desde su admisión. */
    private int waitingTime;
    private int readyWaitTicks;
    private int blockedWaitTicks;
    private int semaphoreWaitTicks;
    private int lastBlockedSampleCycle = -1;

    // Registros hardware simulados
    private int programCounter;
    private int memoryAddressRegister;

    // Atributos para Productor / Consumidor
    private String targetBufferId;
    private int cyclesPerOperation;
    private int requiredElements;
    private int processedElements;

    private int memoryPointer;
    public enum SyncPhase { WAIT_SLOT, WAIT_MUTEX, ACCESS, SIGNAL_MUTEX, SIGNAL_SLOT, WORK }
    private SyncPhase syncPhase = SyncPhase.WORK;
    private int operationWorkDone;
    public SyncPhase getSyncPhase() { return syncPhase; }
    public void setSyncPhase(SyncPhase phase) { syncPhase = phase; }
    public int getOperationWorkDone() { return operationWorkDone; }
    public void setOperationWorkDone(int value) { operationWorkDone = value; }
    /** Solo la limpieza de recursos debe vaciar este registro, tras devolverlos. */
    public void clearBufferPermits() {
        holdsBufferMutex = false;
        reservedBufferSlot = null;
        pendingBufferSignal = null;
        permitBufferId = -1;
    }

    private int networkDelayRemaining = 0;   
    private boolean networkDelayDone = false;
    private boolean terminated = false;

    public enum BufferPermit { MUTEX, VACIOS, LLENOS }

    // Una operación a la vez, contra un único buffer; no son permisos del hilo Java.
    private int permitBufferId = -1;
    private boolean holdsBufferMutex;
    private BufferPermit reservedBufferSlot;
    private BufferPermit pendingBufferSignal;

    public int getPermitBufferId() { return permitBufferId; }
    public boolean holdsBufferMutex() { return holdsBufferMutex; }
    public BufferPermit getReservedBufferSlot() { return reservedBufferSlot; }
    public BufferPermit getPendingBufferSignal() { return pendingBufferSignal; }

    /** Valida antes de descontar el semáforo. No modifica el PCB. */
    public void validateBufferPermitAcquisition(int bufferId, BufferPermit permit) {
        if (bufferId < 0 || permit == null) throw new IllegalArgumentException("Permiso inválido");
        if (permitBufferId != -1 && permitBufferId != bufferId)
            throw new IllegalStateException("El PCB conserva recursos de otro buffer");
        if (permit == BufferPermit.MUTEX ? holdsBufferMutex
                : reservedBufferSlot != null || pendingBufferSignal != null)
            throw new IllegalStateException("El PCB ya tiene una reserva o señal pendiente");
    }

    /** Se invoca solo tras adquirir o recibir por transferencia un permiso. */
    public void grantBufferPermit(int bufferId, BufferPermit permit) {
        validateBufferPermitAcquisition(bufferId, permit);
        permitBufferId = bufferId;
        if (permit == BufferPermit.MUTEX) holdsBufferMutex = true;
        else reservedBufferSlot = permit;
    }

    /** Tras insertar/extraer: la reserva se transforma en una notificación pendiente.
     * No modifica PC ni memoria; el ejecutor debe llamarla junto con la operación real.
     */
    public void recordBufferOperation(int bufferId) {
        if (permitBufferId != bufferId || !holdsBufferMutex || reservedBufferSlot == null
                || pendingBufferSignal != null)
            throw new IllegalStateException("Operación sin mutex y reserva del buffer");
        pendingBufferSignal = reservedBufferSlot == BufferPermit.VACIOS
                ? BufferPermit.LLENOS : BufferPermit.VACIOS;
        reservedBufferSlot = null;
    }

    /** No modifica estado: permite rechazar un signal inválido antes de ejecutarlo. */
    public void validateBufferSignal(int bufferId, BufferPermit permit) {
        if (permit == null) throw new IllegalArgumentException("Permiso inválido");
        if (permitBufferId != bufferId || (permit == BufferPermit.MUTEX
                ? !holdsBufferMutex : pendingBufferSignal != permit))
            throw new IllegalStateException("El PCB no puede emitir este signal");
    }

    /** Consume la titularidad o notificación una vez, al emitir signal. */
    public void recordBufferSignal(int bufferId, BufferPermit permit) {
        validateBufferSignal(bufferId, permit);
        if (permit == BufferPermit.MUTEX) holdsBufferMutex = false;
        else pendingBufferSignal = null;
        if (!holdsBufferMutex && reservedBufferSlot == null && pendingBufferSignal == null)
            permitBufferId = -1;
    }

    // Atributos para procesos IO-bound
    /** Ciclos de CPU que el proceso ejecuta antes de lanzar una solicitud de IO. 0 = no aplica. */
    private int ioInterval = 0;
    /** Ciclos que el proceso permanece bloqueado esperando IO. 0 = no aplica. */
    private int ioDuration = 0;
    /** Contador interno: ciclos restantes hasta el próximo evento IO (ejecución o bloqueo). */
    private int ioCycleCountdown = 0;
    private boolean ioRequestInstruction;

    public boolean isIoRequestInstruction() { return ioRequestInstruction; }
    public void setIoRequestInstruction(boolean enabled) { ioRequestInstruction = enabled; }

    /**
     * Constructor por defecto: asigna ID único generado automáticamente.
     */
    public ProcessControlBlock() {
        this.id = allocateId();
        this.name = "Process-" + this.id;
        this.computerId = 0;
        this.state = ProcessState.NUEVO;
        this.type = ProcessType.CPU_BOUND;
        this.blockReason = BlockReason.NONE;
        this.priority = 1;
        this.assignedMemory = 0;
        this.totalInstructions = 10;
        this.remainingTime = 10;
        this.deadline = 100;
        this.remainingDeadline = 100;
        this.arrivalCycle = 0;
        this.startCycle = -1;
        this.completionCycle = -1;
        this.programCounter = 0;
        this.memoryAddressRegister = 0;
        this.targetBufferId = null;
        this.cyclesPerOperation = 1;
        this.requiredElements = 0;
        this.processedElements = 0;
        this.mode = ExecutionMode.USER;
    }

    /**
     * Constructor para procesos estándar (CPU-bound o I/O-bound).
     */
    public ProcessControlBlock(String name, int computerId, ProcessType type,
                               int totalInstructions, int assignedMemory,
                               int priority, int deadline) {
        this(name, computerId, type, totalInstructions, assignedMemory, priority, deadline, 0);
    }

    public ProcessControlBlock(String name, int computerId, ProcessType type,
                               int totalInstructions, int assignedMemory,
                               int priority, int deadline, int memoryPointer) {
        this();
        this.name = (name != null && !name.trim().isEmpty()) ? name : "Process-" + this.id;
        this.computerId = computerId;
        this.type = (type != null) ? type : ProcessType.CPU_BOUND;
        this.totalInstructions = totalInstructions;
        this.remainingTime = totalInstructions;
        this.assignedMemory = assignedMemory;
        this.requiredMemory = assignedMemory;
        this.priority = priority;
        this.deadline = deadline;
        this.remainingDeadline = deadline;
        this.memoryPointer = memoryPointer;
    }

    public ProcessControlBlock(String name, int computerId, ProcessType type,
                               int totalInstructions, int assignedMemory,
                               int priority, int deadline,
                               String targetBufferId, int cyclesPerOperation,
                               int requiredElements) {
        this(name, computerId, type, totalInstructions, assignedMemory, priority, deadline,
                targetBufferId, cyclesPerOperation, requiredElements, 0);
    }

    /**
     * Constructor para procesos de sincronización (Productor / Consumidor).
     * Recibe los datos comunes y los del buffer; crea un PCB con identidad propia.
     * Delega al constructor estándar, que genera el ID e inicializa el contexto,
     * y después completa los parámetros de sincronización. No reserva RAM ni encola.
     * Sin ciclos; conserva CONSUMER si type es null y las reglas del nombre común.
     * La validación adicional de rangos no forma parte de este constructor.
     */
    public ProcessControlBlock(String name, int computerId, ProcessType type,
                               int totalInstructions, int assignedMemory,
                               int priority, int deadline,
                               String targetBufferId, int cyclesPerOperation,
                               int requiredElements, int memoryPointer) {
        this(name, computerId, type != null ? type : ProcessType.CONSUMER,
                totalInstructions, assignedMemory, priority, deadline, memoryPointer);
        this.targetBufferId = targetBufferId;
        this.cyclesPerOperation = cyclesPerOperation;
        this.requiredElements = requiredElements;
        this.processedElements = 0;
    }

    /**
     * Actualiza el deadline del proceso ante un tick del reloj global.
     * En cada ciclo, si remainingDeadline <= 0, el PCB transiciona a TERMINATED inmediatamente.
     *
     * @param currentCycle Ciclo actual del reloj global.
     */
    public void updateDeadlineOnTick(int currentCycle) {
        if (this.state != ProcessState.SALIENTE) {
            this.remainingDeadline--;
            if (this.remainingDeadline <= 0) {
                this.state = ProcessState.SALIENTE;
                this.blockReason = BlockReason.NONE;
                if (this.completionCycle == -1) {
                    this.completionCycle = currentCycle;
                }
            }
        }
    }

    /**
     * Ejecuta un ciclo de procesamiento sobre la CPU simulada.
     * Si está en RUNNING, se registra la dirección actual y avanza programCounter,
     * se decrementa remainingTime y se verifica si finalizó.
     *
     * @param currentCycle Ciclo actual del reloj global.
     */
    public void executeOneCycle(int currentCycle) {
        if (this.state == ProcessState.EJECUTANDO) {
            if (this.startCycle == -1) {
                this.startCycle = currentCycle;
            }
            advanceInstruction();
            this.remainingTime--;

            if (this.remainingTime <= 0) {
                this.state = ProcessState.SALIENTE;
                this.blockReason = BlockReason.NONE;
                this.completionCycle = currentCycle;
            }
        }
    }

    /** MAR guarda la dirección de la instrucción ejecutada; PC señala la siguiente. */
    public void advanceInstruction() {
        this.memoryAddressRegister = this.programCounter;
        this.programCounter++;
    }

    /**
     * Registra la producción o consumo de un elemento.
     * Si alcanza los elementos requeridos, pasa a TERMINATED.
     *
     * @param currentCycle Ciclo actual del reloj global.
     */
    public void registerElementProcessed(int currentCycle) {
        this.processedElements++;
        if (this.requiredElements > 0 && this.processedElements >= this.requiredElements) {
            this.state = ProcessState.SALIENTE;
            this.blockReason = BlockReason.NONE;
            this.completionCycle = currentCycle;
        }
    }

    /**
     * Finaliza forzadamente el proceso (por ejemplo, por aborto de deadline o cancelación).
     *
     * @param currentCycle Ciclo actual del reloj global.
     */
    public void terminate(int currentCycle) {
        this.state = ProcessState.SALIENTE;
        this.blockReason = BlockReason.NONE;
        if (this.completionCycle == -1) {
            this.completionCycle = currentCycle;
        }
    }

    public static void resetIdGenerator(int initialValue) {
        if (initialValue < 1) throw new IllegalArgumentException("El siguiente ID debe ser positivo");
        ID_MUTEX.acquireUninterruptibly();
        try {
            nextId = initialValue;
        } finally {
            ID_MUTEX.release();
        }
    }
    
    public void incrementProcessedElements() {
        this.processedElements++;
    }

    public void decrementNetworkDelay() {
        if (this.networkDelayRemaining > 0) {
            this.networkDelayRemaining--;
        }
    }
    
    public void decrementRemainingTime() {
        if (this.remainingTime > 0) {
            this.remainingTime--;
            this.programCounter++;
        }
    }

    public int getIoInterval() {
        return ioInterval;
    }

    public void setIoInterval(int ioInterval) {
        this.ioInterval = ioInterval;
    }

    public int getIoDuration() {
        return ioDuration;
    }

    public void setIoDuration(int ioDuration) {
        this.ioDuration = ioDuration;
    }

    public int getIoCycleCountdown() {
        return ioCycleCountdown;
    }

    public void setIoCycleCountdown(int ioCycleCountdown) {
        this.ioCycleCountdown = ioCycleCountdown;
    }
    
    public int getSyncStage() {
        return syncPhase.ordinal();
    }

    public void setSyncStage(int syncStage) {
        this.syncPhase = SyncPhase.values()[syncStage];
    }

    public int getNetworkDelayRemaining() {
        return networkDelayRemaining;
    }

    public void setNetworkDelayRemaining(int networkDelayRemaining) {
        this.networkDelayRemaining = networkDelayRemaining;
    }

    public boolean isNetworkDelayDone() {
        return networkDelayDone;
    }

    // --- GETTERS Y SETTERS ---
    public void setNetworkDelayDone(boolean networkDelayDone) {    
        this.networkDelayDone = networkDelayDone;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getComputerId() {
        return computerId;
    }

    public void setComputerId(int computerId) {
        this.computerId = computerId;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    public ProcessType getType() {
        return type;
    }

    public void setType(ProcessType type) {
        this.type = type;
    }

    public BlockReason getBlockReason() {
        return blockReason;
    }

    public void setBlockReason(BlockReason blockReason) {
        this.blockReason = blockReason;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getAssignedMemory() {
        return assignedMemory;
    }

    public int getRequiredMemory() {
        return requiredMemory;
    }

    public void setAssignedMemory(int assignedMemory) {
        this.assignedMemory = assignedMemory;
    }

    public int getTotalInstructions() {
        return totalInstructions;
    }

    public void setTotalInstructions(int totalInstructions) {
        this.totalInstructions = totalInstructions;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public void setRemainingTime(int remainingTime) {
        this.remainingTime = remainingTime;
    }

    public int getDeadline() {
        return deadline;
    }

    public void setDeadline(int deadline) {
        this.deadline = deadline;
    }

    public int getRemainingDeadline() {
        return remainingDeadline;
    }

    public void setRemainingDeadline(int remainingDeadline) {
        this.remainingDeadline = remainingDeadline;
    }

    public int getWaitingTime() {
        return waitingTime;
    }

    /** Suma un ciclo de espera (listo o bloqueado sin ejecutar). */
    public void incrementWaitingTime() {
        this.waitingTime++;
    }

    /** Registra el estado bloqueado al inicio del ciclo global, antes de señales entre computadores. */
    public void sampleBlockedWait(int cycle) {
        if (state != ProcessState.BLOQUEADO || lastBlockedSampleCycle == cycle) return;
        lastBlockedSampleCycle = cycle;
        blockedWaitTicks++;
        if (blockReason == BlockReason.WAITING_MUTEX || blockReason == BlockReason.WAITING_EMPTY_BUFFER
                || blockReason == BlockReason.WAITING_FULL_BUFFER) semaphoreWaitTicks++;
    }

    /** El proceso espera listo si no ejecutó ni se contó bloqueado en este ciclo. */
    public void sampleReadyWait(int cycle) {
        if (state == ProcessState.LISTO && lastBlockedSampleCycle != cycle) readyWaitTicks++;
    }

    public int getReadyWaitTicks() { return readyWaitTicks; }
    public int getBlockedWaitTicks() { return blockedWaitTicks; }
    public int getSemaphoreWaitTicks() { return semaphoreWaitTicks; }

    public int getArrivalCycle() {
        return arrivalCycle;
    }

    public void setArrivalCycle(int arrivalCycle) {
        this.arrivalCycle = arrivalCycle;
    }

    public int getStartCycle() {
        return startCycle;
    }

    public void setStartCycle(int startCycle) {
        this.startCycle = startCycle;
    }

    public int getCompletionCycle() {
        return completionCycle;
    }

    public void setCompletionCycle(int completionCycle) {
        this.completionCycle = completionCycle;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public void setTerminated(boolean terminated) {
        this.terminated = terminated;
    }

    public int getProgramCounter() {
        return programCounter;
    }

    public void setProgramCounter(int programCounter) {
        this.programCounter = programCounter;
    }

    public int getMemoryAddressRegister() {
        return memoryAddressRegister;
    }

    public void setMemoryAddressRegister(int memoryAddressRegister) {
        this.memoryAddressRegister = memoryAddressRegister;
    }

    public String getTargetBufferId() {
        return targetBufferId;
    }

    public void setTargetBufferId(String targetBufferId) {
        this.targetBufferId = targetBufferId;
    }

    public int getCyclesPerOperation() {
        return cyclesPerOperation;
    }

    public void setCyclesPerOperation(int cyclesPerOperation) {
        this.cyclesPerOperation = cyclesPerOperation;
    }

    public int getRequiredElements() {
        return requiredElements;
    }

    public void setRequiredElements(int requiredElements) {
        this.requiredElements = requiredElements;
    }

    public int getProcessedElements() {
        return processedElements;
    }

    public void setProcessedElements(int processedElements) {
        this.processedElements = processedElements;
    }
    
    public int getIdFather() {
        return idFather;
    }

    public void setIdFather(int idFather) {
        this.idFather = idFather;
    }

    public int getMemoryPointer() {
        return memoryPointer;
    }

    public void setMemoryPointer(int memoryPointer) {
        this.memoryPointer = memoryPointer;
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public void setMode(ExecutionMode mode) {
        this.mode = mode;
    }

    @Override
public String toString() {
    String textoNombre = name;
    if (blockReason != null && blockReason != BlockReason.NONE) {
        textoNombre += " [" + blockReason + "]";
    }

    return "PCB{" +
            "id=" + id +
            ", name='" + textoNombre + '\'' +
            ", pc=" + computerId +
            ", state=" + state +
            ", type=" + type +
            ", remTime=" + remainingTime +
            ", remDeadline=" + remainingDeadline +
            ", PC=" + programCounter +
            ", MAR=" + memoryAddressRegister +
            '}';
}
}
