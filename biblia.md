# 📖 BIBLIA DE ÁVILAOS — SISTEMA OPERATIVO DISTRIBUIDO SIMULADO
> **DOCUMENTO MAESTRO Y CANÓNICO DE REGLAS, ARQUITECTURA Y REQUERIMIENTOS**  
> *Este documento es de lectura OBLIGATORIA para cualquier desarrollador (humano o agente de IA) antes de escribir, modificar o sugerir una sola línea de código en este repositorio.*  
> **Versión:** 1.0 (Unificada con Checklist Atómico y Resoluciones de Diseño)  
> **Materia:** Sistemas Operativos — Proyecto 1  
> **Entorno objetivo:** Java 21+ | Apache NetBeans | Git Flow en GitHub  

---

## 🛑 1. MANDAMIENTOS ABSOLUTOS: LO QUE SE PUEDE Y NO SE PUEDE HACER

Cualquier violación a las reglas marcadas con **[NOTA 0]** causará la reprobación automática del proyecto según el enunciado oficial.

### 🚫 LO QUE ESTÁ TERMINANTEMENTE PROHIBIDO (Zero-Tolerance Rules)

1. **[NOTA 0] PROHIBIDO USAR COLECCIONES DE JAVA:**
   - Queda estrictamente vetado el uso de `java.util.ArrayList`, `java.util.LinkedList`, `java.util.List`, `java.util.Queue`, `java.util.Deque`, `java.util.Stack`, `java.util.Vector`, `java.util.PriorityQueue`, `java.util.HashMap`, `java.util.HashSet`, `java.util.TreeMap`, o cualquier clase del Collections Framework de Java para gestionar procesos, PCBs, colas o buffers.
   - **Solución obligatoria:** Todas las estructuras de datos deben ser programadas desde cero (`MyLinkedList<T>`, `MyQueue<T>`, `MyStack<T>`, etc.).
2. **[NOTA 0] PROHIBIDO ENTREGAR SIN GUI O SOLO POR CONSOLA:**
   - La aplicación debe poseer una Interfaz Gráfica interactiva completa en tiempo real. Proyectos de consola reciben 0.
3. **[NOTA 0] PROHIBIDO EL CÓDIGO QUE NO COMPILE O NO EJECUTE EN NETBEANS:**
   - El código debe funcionar y compilar limpiamente en **Apache NetBeans** bajo **Java 21 o superior**. No usar dependencias o plugins exóticos que rompan la ejecución estándar de NetBeans.
4. **[NOTA 0] PROHIBIDO LENGUAJES DISTINTOS A JAVA:**
   - Todo el simulador debe estar programado en Java estándar.
5. **[NOTA 0] PROHIBIDO TRABAJAR SIN GITHUB O SOLO EN `main`:**
   - Sin repositorio en GitHub la nota es 0. Prohibido hacer commits directos a `main`. Se exige flujo de trabajo en equipo.
6. **PROHIBIDO DUPLICAR CÓDIGO PARA CADA COMPUTADOR:**
   - Todos los computadores del clúster deben ser instancias reutilizables de la misma clase `Computer` / `MachineKernel`. Si hay 4 computadores, son 4 instancias de la misma clase.
7. **PROHIBIDO MEZCLAR LA LÓGICA DEL SISTEMA OPERATIVO CON LA GUI:**
   - La GUI es un **observador desacoplado** (patrón Observer / Listener). El núcleo de ÁvilaOS corre en su propio motor/hilos y notifica o expone datos a la GUI. La lógica de simulación nunca vive dentro de eventos de botones ni en las ventanas.
8. **PROHIBIDO MIGRAR PROCESOS:**
   - Una vez que un proceso es asignado a un computador (manual o automáticamente), permanece en ese computador hasta su terminación.
9. **PROHIBIDO INVENTAR COMPILADORES O MEMORIA VIRTUAL EN ESTE PROYECTO:**
   - En el Proyecto 1 **no hay disco ni memoria virtual ni paginación**. El control de memoria RAM es contable (memoria usada vs. memoria libre). Todas las instrucciones se ejecutan de manera lineal (PC y MAR incrementan en 1 por ciclo).
10. **REGLA CANÓNICA DE MEMORIA RAM (1 INSTRUCCIÓN == 1 POSICIÓN RAM):**
   - En ÁvilaOS la memoria RAM requerida por un proceso **no es un campo manual que el usuario invente en la GUI**, sino que se **deriva directamente del conjunto de instrucciones** del programa (conforme al ítem 104 del enunciado y acuerdo unificado del equipo).
   - Cada instrucción ocupa 1 posición en la RAM física simulada ($1 \text{ inst} = 1 \text{ posición RAM}$).
   - **Procesos CPU-bound / I/O-bound:** $\text{RAM} = \sum \text{duraciones de bloques de instrucciones}$.
   - **Procesos Productor / Consumidor:** $\text{RAM} = 5 \times \text{requiredElements}$ (5 instrucciones de sincronización y manipulación de buffer del SO por cada elemento).
   - **Admisión y Saturación:** Si un proceso pide más memoria que la RAM física total del computador, se rechaza de inmediato en creación. Si cabe en la RAM total pero supera la RAM libre actual, se admite en estado `NEW` (Cola de Nuevos) y espera a que los procesos en ejecución terminen y liberen RAM.

---

### ✅ LO QUE SE PUEDE Y DEBE HACER (Reglas Permitidas y Obligatorias)

1. **Estructuras de Datos Propias:**
   - Crear clases genéricas con nodos enlazados propios: `Node<T>`, `CustomList<T>`, `CustomQueue<T>`, `CustomStack<T>`.
   - Implementar ordenamientos manuales propios para las colas de prioridad (EDF y Prioridades Apropiativas).
2. **Hilos y Semáforos Nativos de Java:**
   - **Hilos (`Thread`, `Runnable`, `ScheduledExecutorService`):** Permitidos y obligatorios para el Reloj Global (`GlobalClock`) y la ejecución concurrente sincronizada de los computadores.
   - **Semáforos de Java (`java.util.concurrent.Semaphore`):** Permitidos para proteger las estructuras de datos compartidas entre hilos Java reales (memoria compartida, buffers entre hilos).
3. **Librerías Externas Estrictamente Autorizadas:**
   - **Visualización de Gráficas:** `JFreeChart` (u otra librería estándar de gráficos de Java).
   - **Persistencia JSON/CSV:** `Gson` (o `org.json` o parser propio) y utilidades estándar de lectura/escritura de archivos (`java.io.*`, `java.nio.*`).
4. **Arquitectura Orientada a Objetos:**
   - **Enums obligatorios:** `ProcessState` (NEW, READY, RUNNING, BLOCKED, TERMINATED), `ProcessType` (CPU_BOUND, IO_BOUND, PRODUCER, CONSUMER), `ExecutionMode` (USER, SYSTEM), `SchedulingPolicyType` (FCFS, EDF, ROUND_ROBIN, PRIORITY_PREEMPTIVE), `BlockReason` (WAITING_MUTEX, WAITING_BUFFER_EMPTY, WAITING_BUFFER_FULL, NETWORK_IO).
   - **Interfaces obligatorias:** `IScheduler` (para conectar cualquier política intercambiable en caliente), `IClockSubscriber` / `ClockTickable` (para que CPU, núcleos y dispositivos avancen al unísono por cada pulso del reloj).
5. **Tecnología de GUI Recomendada:**
   - **Java Swing:** 100% nativo de NetBeans (GUI Form Builder / Palette), cero dolores de cabeza de configuración de módulos JDK, compilación limpia en cualquier sistema operativo.
6. **Manejo de Errores y Validaciones:**
   - Todos los campos de entrada de la GUI (tiempos, memorias, IDs, tamaños) deben tener validación estricta de tipo y rango sin lanzar excepciones no controladas ni colgar el simulador.

---

## 🏛️ 2. ARQUITECTURA DE ÁVILAOS Y MODELO POR CAPAS

ÁvilaOS se organiza en 4 capas estrictas con bajo acoplamiento y alta cohesión:

```
┌────────────────────────────────────────────────────────────────────────┐
│  CAPA 3: INTERFAZ GRÁFICA, MÉTRICAS Y PERSISTENCIA (GUI & Visualizer)  │
│  - Swing UI (Vista 1: Config/Creación, Vista 2: Monitoreo tiempo real) │
│  - Gráfico JFreeChart (Uso de CPU vs Tiempo por computador)            │
│  - Log de eventos en tiempo real & Métricas de rendimiento             │
│  - Importador / Exportador JSON o CSV                                  │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ Observa / Envía órdenes
┌────────────────────────────────────▼───────────────────────────────────┐
│  CAPA 2: MEMORIA RAM, DISTRIBUCIÓN Y SINCRONIZACIÓN (Buffer & Network) │
│  - RAM Manager (Memoria usada / libre, admisión a largo plazo)         │
│  - Buffers acotados (Algoritmo de Stallings: semáforos s, n, e)        │
│  - SimulatedSemaphores (Colas de procesos bloqueados de ÁvilaOS)       │
│  - NetworkSimulator (Latencia de red configurable para acceso remoto)  │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ Gestiona recursos
┌────────────────────────────────────▼───────────────────────────────────┐
│  CAPA 1: NÚCLEO, ESTRUCTURAS PROPIAS Y PLANIFICACIÓN (CPU & Schedulers)│
│  - Custom Data Structures (MyLinkedList, MyQueue, MyStack)             │
│  - ProcessControlBlock (PCB) & Contadores lineales (PC, MAR)           │
│  - GlobalClock (Hilo orquestador de ciclos / tics sincronizados)       │
│  - ComputerKernel (Instancia reutilizable: CPU, colas, OS/User mode)   │
│  - IScheduler & 4 Políticas (FCFS, EDF, Round Robin, Prioridades)      │
└────────────────────────────────────────────────────────────────────────┘
```

---

## ⚖️ 3. RESOLUCIONES DE DISEÑO DEFINITIVAS (Aclaración de Ambigüedades)

Con base en el enunciado y la planificación del equipo, se establecen las siguientes decisiones canónicas (resuelven los puntos D-01 a D-16 del checklist):

| ID Duda | Decisión Oficial del Proyecto ÁvilaOS | Justificación y Regla Canónica |
|---|---|---|
| **D-01 (Políticas)** | **Se implementan las 4 políticas:** FCFS, EDF, Round Robin y Prioridades Apropiativas. | El enunciado menciona "mínimo 3" y luego detalla 4. Para máxima calificación e idoneidad de métricas, se implementan las 4 con la interfaz `IScheduler`. |
| **D-02 (Asignación)** | **Manual obligatoria + Asignación automática básica opcional.** | En Vista 1 el usuario siempre puede elegir a qué computador enviar el proceso. Se ofrece la opción de "Automático (Menor carga de RAM/procesos)" como valor agregado. |
| **D-03 (Bloqueos)** | **Enum `BlockReason` con 4 causas concretas:** `WAITING_MUTEX`, `WAITING_EMPTY_BUFFER`, `WAITING_FULL_BUFFER`, `NETWORK_LATENCY`. | Permite mostrar en la GUI exactamente por qué está bloqueado cada proceso y calcular el tiempo en semáforo vs tiempo en red. |
| **D-04 (PC y MAR)** | **Incremento lineal de PC y MAR en 1 por ciclo solo para el proceso en RUNNING.** | Los procesos en READY o BLOCKED congelan sus contadores. En cada ciclo, si la CPU ejecuta una instrucción, `PC++` y `MAR++`. |
| **D-05 (Instrucciones)** | **Simulación basada en ciclos lineales y fases de trabajo.** | Para CPU-bound: instrucciones de usuario. Para I/O-bound: ciclos de trabajo. Para Prod/Cons: ciclos de producción/consumo seguidos de instrucciones de sistema (`semWait`, `add/extract`, `semSignal`). |
| **D-06 (Fin Prod/Cons)** | **Terminan cuando completan su cuota de elementos a producir/consumir, o por deadline.** | El usuario define `requiredElements`. Al llegar a la cuota o si el deadline llega a 0, pasa a TERMINATED y libera memoria. |
| **D-07 (Deadline)** | **Se decrementa 1 ciclo por cada tick del reloj global para todos los procesos activos (READY, RUNNING, BLOCKED).** | El deadline representa una fecha límite de tiempo real de reloj. Si llega a 0 en cualquier estado, el SO aborta el proceso y lo pasa a TERMINATED. |
| **D-08 (Tamaño Buffer)** | **Cada buffer ocupa en la RAM de su anfitrión `capacidad * 1 slot de memoria` (configurable o 1 unidad por slot).** | Al crearse el buffer en Vista 1, se reserva dicha memoria de la RAM libre del computador anfitrión. |
| **D-09 (Métricas)** | **Throughput = Procesos terminados / Ciclo actual; CPU Utilization = Ciclos de CPU ocupada / Ciclos totales; Deadline Success Rate = Procesos completados a tiempo / Procesos totales terminados.** | Se computan tanto por computador como consolidadas globalmente. |
| **D-10 (Cambio en caliente)** | **El cambio de planificador o quantum entra en vigencia de inmediato en el siguiente ciclo.** | Si se cambia a Round Robin, el proceso en CPU inicia con el nuevo quantum. La cola de listos se reordena según la nueva política. |
| **D-11 (Latencia Remota)** | **Si el buffer está en otro computador, la solicitud añade $L$ ciclos de red al proceso antes de acceder al semáforo o tras salir de él.** | Durante esos $L$ ciclos el proceso está en `BLOCKED` con motivo `NETWORK_LATENCY` liberando la CPU. |
| **D-12 (Admisión RAM)** | **Cola de nuevos funciona FIFO estricto; si el primer proceso cabe en RAM, se admite; si no cabe, se puede evaluar si el siguiente cabe o esperar a liberación.** | Política definida: Evaluar en orden de cola de nuevos al terminar cualquier proceso. |
| **D-13 (Aborto Deadline)** | **Si un proceso vence su deadline mientras retenía un semáforo (ej. mutex), el SO debe forzar la liberación del mutex para no congelar a los demás.** | El núcleo invoca un `cleanupResources(pcb)` que evita deadlocks causados por procesos abortados. |
| **D-14 (UMLs del Informe)**| **1 Diagrama de Clases general + 1 Diagrama de Secuencia (Interacción Productor-Consumidor Remoto con Semáforos y Red).** | Cumple con creces los 2 diagramas requeridos. |
| **D-15 (Memoria Derivada: 1 Inst = 1 RAM)** | **La memoria RAM del proceso no es un campo manual en la GUI, sino un atributo derivado del conjunto de instrucciones.** | Conforme al ítem 104 del enunciado y acuerdo del equipo: $1 \text{ instrucción} = 1 \text{ posición RAM}$. Para CPU/IO-bound: $\text{RAM} = \sum \text{duraciones de bloques}$. Para Prod/Cons: $\text{RAM} = 5 \times \text{requiredElements}$ (instrucciones de SO para sincronización). Si $\text{RAM} > \text{RAM total}$, se rechaza en creación; si $\text{RAM} > \text{RAM libre}$, espera en cola `NEW` hasta que otros procesos liberen memoria. |

---

## 📋 4. CHECKLIST UNIFICADO DE REQUISITOS (Matriz de Control)

Esta matriz integra y audita cada uno de los puntos exigidos en el enunciado oficial.

### Bloque ARC: Arquitectura, Diseño y Reloj
- [ ] **ARC-01:** Construir un simulador de software de ÁvilaOS (no un kernel real).
- [ ] **ARC-02:** Soportar un clúster de mínimo 2 computadores (ampliable a N computadores).
- [ ] **ARC-03:** Configurar la cantidad de computadores desde la interfaz gráfica.
- [ ] **ARC-04:** Cada computador cuenta con su propia CPU simulada.
- [ ] **ARC-05:** Cada computador cuenta con su propia RAM simulada.
- [ ] **ARC-06:** Cada computador cuenta con su propio núcleo de SO independiente.
- [ ] **ARC-07:** Cada computador cuenta con su propio planificador independiente.
- [ ] **ARC-08:** Colas de procesos de cada computador totalmente independientes.
- [ ] **ARC-09:** Todos los computadores son instancias de una misma clase (`ComputerKernel`).
- [ ] **ARC-10:** Se pueden instanciar nuevos computadores sin duplicar código.
- [ ] **ARC-11:** Uso de Threads de Java (`Thread` / `Runnable`) para la orquestación temporal del reloj.
- [ ] **ARC-12:** Uso de semáforos de Java (`Semaphore`) para exclusión mutua de estructuras compartidas concurrentes.
- [ ] **ARC-13:** Reloj global (`GlobalClock`) centralizado que marca el avance temporal.
- [ ] **ARC-14:** Cada computador avanza exactamente un ciclo por cada tick del reloj global.
- [ ] **ARC-15:** Diseño orientado a objetos estricto y desacoplado por capas.
- [ ] **ARC-16:** Enum obligatorio `ProcessState` (NEW, READY, RUNNING, BLOCKED, TERMINATED).
- [ ] **ARC-17:** Enum obligatorio `ProcessType` (CPU_BOUND, IO_BOUND, PRODUCER, CONSUMER).
- [ ] **ARC-18:** Enum obligatorio `SchedulingPolicyType` (FCFS, EDF, ROUND_ROBIN, PRIORITY_PREEMPTIVE).
- [ ] **ARC-19:** Enum obligatorio `ExecutionMode` (USER, SYSTEM).
- [ ] **ARC-20:** Interfaz obligatoria `IScheduler` para definir el comportamiento de planificación.
- [ ] **ARC-21:** Extensibilidad: agregar una nueva política no modifica el núcleo del planificador.
- [ ] **ARC-22:** Interfaz común `IClockSubscriber` para componentes reactivos al reloj.
- [ ] **ARC-23:** La GUI solo observa y controla; la lógica del SO reside en las capas de núcleo y memoria.

### Bloque PRO & PCB: Procesos y Bloque de Control
- [ ] **PRO-01:** Creación de procesos desde la interfaz de usuario (Vista 1).
- [ ] **PRO-02:** Creación dinámica de procesos durante la simulación en tiempo real.
- [ ] **PRO-03:** Parámetro: Nombre del proceso.
- [ ] **PRO-04:** Parámetro: Cantidad total de instrucciones/ciclos.
- [ ] **PRO-05:** Memoria requerida en RAM (derivada automáticamente del conjunto de instrucciones: $1 \text{ instrucción} = 1 \text{ posición RAM}$, sin input manual en GUI).
- [ ] **PRO-06:** Parámetro: Prioridad del proceso (valor entero).
- [ ] **PRO-07:** Selector del tipo de proceso (`ProcessType`).
- [ ] **PRO-08:** Soporte de procesos CPU-bound (consumo continuo de ciclos de CPU).
- [ ] **PRO-09:** Soporte de procesos I/O-bound (simulación de ráfagas de E/S).
- [ ] **PRO-10:** Soporte de procesos Productores.
- [ ] **PRO-11:** Soporte de procesos Consumidores.
- [ ] **PRO-12:** Configuración de ciclos requeridos para procesos CPU-bound.
- [ ] **PRO-13:** Configuración de ciclos para procesos I/O-bound.
- [ ] **PRO-14:** Asociación de productor a un buffer específico.
- [ ] **PRO-15:** Asociación de consumidor a un buffer específico.
- [ ] **PRO-16:** Productores: ciclos requeridos para producir un elemento.
- [ ] **PRO-17:** Consumidores: ciclos requeridos para consumir un elemento.
- [ ] **PRO-18:** Productores: cantidad de elementos requeridos para terminar.
- [ ] **PRO-19:** Consumidores: cantidad de elementos requeridos para terminar.
- [ ] **PRO-20:** Asignación de computador (manual obligatoria + balanceo automático opcional).
- [ ] **PRO-21:** Afinidad fija: el proceso nunca migra de computador una vez asignado.
- [ ] **PRO-22:** Modelo monohilo por proceso simulado (asociación 1:1 con su flujo).
- [ ] **PRO-23:** Program Counter (PC) individual por proceso.
- [ ] **PRO-24:** Memory Address Register (MAR) individual por proceso.
- [ ] **PCB-01 a PCB-11:** Clase `ProcessControlBlock` con todos sus atributos: ID dinámico único en el clúster, nombre, ID de computador asignado, estado actual, tipo, prioridad, memoria asignada, deadline, tiempo restante, PC y MAR.
- [ ] **EST-08:** Detección de deadline expirado (deadline == 0): forzar cambio inmediato a TERMINATED y liberar recursos.

### Bloque PLA: Planificación del Procesador
- [ ] **PLA-01:** Implementar política FCFS (First-Come, First-Served).
- [ ] **PLA-02:** Implementar política EDF (Earliest Deadline First, ordenado por menor deadline).
- [ ] **PLA-03:** Implementar política Round Robin con quantum configurable.
- [ ] **PLA-04:** Control dinámico del quantum en tiempo de ejecución.
- [ ] **PLA-05:** Implementar política de Prioridades Apropiativas (interrupción de CPU si entra un proceso con mayor prioridad).
- [ ] **PLA-06:** Algoritmos propios de ordenamiento de cola tras cada inserción o cambio.
- [ ] **PLA-07:** Política independiente para cada computador del clúster.
- [ ] **PLA-08:** Selector en caliente para cambiar la política de cualquier computador en ejecución.
- [ ] **PLA-09:** El cambio de política en un computador no afecta a los demás.
- [ ] **PLA-10:** Cola única de listos para el procesador de cada computador.

### Bloque BUF & SEM: Memoria, Buffers, Red y Semáforos
- [ ] **BUF-01:** Creación de uno o más buffers con nombre y capacidad definida.
- [ ] **BUF-02:** Creación de buffers en tiempo de ejecución.
- [ ] **BUF-03:** Capacidad acotada estricta por buffer.
- [ ] **BUF-04:** Imposibilidad de almacenar más elementos que la capacidad máxima.
- [ ] **BUF-05:** Asignación de computador anfitrión para cada buffer.
- [ ] **BUF-06:** El buffer reside en la memoria RAM de su computador anfitrión.
- [ ] **BUF-07:** La memoria ocupada por el buffer se descuenta de la RAM libre del anfitrión.
- [ ] **BUF-08:** Múltiples productores concurrentes sobre el mismo buffer.
- [ ] **BUF-09:** Múltiples consumidores concurrentes sobre el mismo buffer.
- [ ] **BUF-10 / BUF-11:** Soporte de acceso local (proceso y buffer en el mismo computador, latencia 0).
- [ ] **BUF-12 / BUF-13:** Soporte de acceso remoto (proceso y buffer en computadores distintos).
- [ ] **BUF-14:** Parámetro configurable de latencia de red (en ciclos).
- [ ] **BUF-15:** Bloqueo del proceso durante los ciclos de latencia de red en cada acceso remoto.
- [ ] **SEM-01:** Implementación de semáforos simulados de ÁvilaOS con cola interna de procesos bloqueados.
- [ ] **SEM-02:** Algoritmo clásico de Stallings (Capítulo 5) con 3 semáforos por buffer:
  - Mutex binario `s` (inicializado en 1).
  - Semáforo de elementos `n` (inicializado en 0).
  - Semáforo de espacios vacíos `e` (inicializado en capacidad).
- [ ] **SEM-03 a SEM-09:** Productor se bloquea si el buffer está lleno (`semWait(e)`); consumidor se bloquea si está vacío (`semWait(n)`); cualquier proceso se bloquea si la región crítica está tomada (`semWait(s)`). Al bloquearse, sale de CPU y pasa a la cola del semáforo.
- [ ] **SEM-13 a SEM-20:** Secuencia estricta de llamadas semafóricas para evitar deadlock:
  - Productor: `semWait(e)` ➔ `semWait(s)` ➔ `añadir()` ➔ `semSignal(s)` ➔ `semSignal(n)`.
  - Consumidor: `semWait(n)` ➔ `semWait(s)` ➔ `extraer()` ➔ `semSignal(s)` ➔ `semSignal(e)`.
- [ ] **CIC-05 a CIC-12:** Las operaciones de `semWait`, `semSignal`, inserción y extracción se contabilizan como 1 instrucción y se ejecutan en **modo sistema operativo** (`SYSTEM`).

### Bloque MEM: Admisión y Memoria RAM
- [ ] **MEM-01:** RAM de tamaño configurable para cada computador.
- [ ] **MEM-02:** Capacidad limitada y contabilidad estricta de memoria libre / usada.
- [ ] **MEM-03:** Admisión a largo plazo: un proceso solo pasa de `NEW` a `READY` si cabe en la RAM libre.
- [ ] **MEM-05:** Si no cabe en RAM, permanece esperando en la cola de nuevos (`NEW`).
- [ ] **MEM-06:** Liberación automática de RAM cuando un proceso pasa a `TERMINATED`.
- [ ] **MEM-07:** Disparo automático de revisión de la cola de nuevos tras liberar RAM para admitir procesos en espera.

### Bloque GUI: Interfaz Gráfica y Observabilidad
- [ ] **GUI-01 / GUI-02:** Interfaz gráfica completa en Swing, intuitiva y fluida.
- [ ] **GUI-03:** Actualización dinámica de estados y colas en tiempo real.
- [ ] **GUI-04 a GUI-09:** Vista por computador: proceso en CPU, PC, MAR, prioridad, deadline, modo (USER/SYSTEM).
- [ ] **GUI-10 a GUI-14:** Visualización de las 4 colas por computador: Nuevos, Listos, Bloqueados (con motivo explícito) y Terminados.
- [ ] **GUI-15:** Reflejo visual inmediato cuando una cola se reordena por cambio de política.
- [ ] **GUI-16 / GUI-17:** Inspección de los atributos del PCB al seleccionar procesos.
- [ ] **GUI-18 / GUI-19:** Barra o indicador de memoria RAM usada y libre por computador.
- [ ] **GUI-20 / GUI-21:** Selector en pantalla para alternar la política de cada máquina en caliente.
- [ ] **GUI-22:** Contador visible del ciclo global del reloj.
- [ ] **GUI-23 a GUI-26:** Monitor de buffers: elementos actuales, capacidad, valores de `s`, `n`, `e` y lista de procesos en espera.
- [ ] **GUI-27 / GUI-28:** Log de eventos en tiempo real con scroll automático, registrando transiciones, bloqueos, despachos y accesos de red.
- [ ] **GUI-29:** Gráfico en tiempo real con `JFreeChart` mostrando en un solo lienzo la utilización de CPU de todos los computadores vs tiempo.
- [ ] **GUI-30 a GUI-32:** Validaciones a prueba de balas en todos los formularios de entrada (numéricos, rangos positivos, campos requeridos).

### Bloque CFG & MET: Configuración, Persistencia y Métricas
- [ ] **CFG-01 / CFG-02:** Control de velocidad de simulación (slider o spinner de ms por ciclo) y quantum.
- [ ] **CFG-03 a CFG-11:** Formulario de parámetros iniciales: N° computadores, RAMs, políticas iniciales, latencia de red, procesos y buffers.
- [x] **CFG-12 / CFG-13:** Exportar e Importar configuración inicial en formato JSON (o CSV) para guardar y recargar simulaciones.
- [ ] **MET-01 a MET-10:** Cálculo y visualización de Throughput, CPU Utilization, Tiempo de respuesta promedio, Tasa de cumplimiento de deadlines y Equidad (por máquina y clúster).
- [ ] **MET-11 a MET-13:** Métricas del productor-consumidor: total producidos/consumidos por buffer y tiempo promedio en bloqueo.

### Bloque TEC & GIT: Tecnología y Repositorio
- [ ] **TEC-01 a TEC-05:** Equipo de hasta 3 integrantes, código 100% Java 21+, proyecto compatible con Apache NetBeans.
- [ ] **TEC-07 / TEC-08:** Cero uso de `java.util.*` collections; 100% estructuras enlazadas hechas en casa.
- [ ] **GIT-01 a GIT-08:** Repositorio en GitHub, ramas `main` y `develop`, ramas de características `feat/...`, issues para tareas/bugs y Pull Requests comentados para cada merge.
- [ ] **GIT-09 a GIT-11:** Commits atómicos, frecuentes, con mensajes claros y contribución equilibrada de todos los miembros.
- [ ] **DOC-01 a DOC-09:** Informe técnico final en PDF con detalle de clases, enums, interfaces, 2 diagramas UML y conclusiones comparativas.

---

## 🤖 5. INSTRUCCIONES ESTRICTAS PARA ASISTENTES DE INTELIGENCIA ARTIFICIAL

Cualquier IA que participe en el desarrollo de este código debe cumplir taxativamente:
1. **Verificación de importaciones:** NUNCA sugerir ni autocompletar con `import java.util.ArrayList`, `java.util.LinkedList`, `java.util.Queue`, etc. Si necesitas una lista o cola, usa las clases del paquete `com.avilaos.core.structures`.
2. **Compatibilidad con NetBeans:** Mantener la estructura estándar de proyecto Java (`src/` o estándar Maven con `pom.xml` compatible sin configuraciones raras).
3. **Pureza de capas:** No colocar lógica de semáforos, procesos o planificadores dentro de componentes de Swing (como `ActionListener` o `JFrame`).
4. **Tratamiento de Deadlines:** Garantizar que todo proceso cuyo `remainingDeadline <= 0` sea interceptado y finalizado limpiamente liberando memoria y candados.
5. **Comentarios de Código:** Mantener comentarios breves, limpios y significativos en español o inglés neutro.
6. **Componentes que reaccionan al reloj (ARC-22):** `CPU` y `DMA` deben implementar obligatoriamente `IClockTickable` (`tick(int currentCycle)`). El ciclo del computador delega la actualización de registros o transferencias a través de esta interfaz común.

