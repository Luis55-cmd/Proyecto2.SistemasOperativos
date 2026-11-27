<img src="media/image1.png" style="width:2.24077in;height:1.0342in" />

Departamento de Gestión de Proyectos y Sistemas

Asignatura: Sistemas Operativos

Trimestre: 2627-1

Preparadores: Gabriela Costa, Ares Ramírez

# Proyecto 2: ÁvilaOS con sistema de archivos.

# Planificación de disco y suspensión de procesos

Noviembre 2026

# Planteamiento del problema

En este segundo proyecto, el equipo de diseño debe extender ÁvilaOS, tomando como base el código entregado en el Proyecto 1, con tres funciones nuevas del sistema operativo: un disco simulado (SD) local con un sistema de archivos jerárquico, la planificación de las solicitudes de E/S a este disco, y la suspensión de procesos al disco cuando la memoria principal no alcanza. El computador tendrá un único disco local.

El sistema de archivos se representará como una estructura jerárquica de directorios y archivos, visible en la interfaz mediante un JTree. Cada archivo tendrá un tamaño en bloques, y sus bloques se asignan en el SD con el método de asignación encadenada: cada archivo se guarda como una lista enlazada de bloques, y cada bloque indica cuál es el siguiente.

Las operaciones sobre archivos no se harán directamente desde la interfaz. Cada vez que el usuario quiera crear, leer, actualizar o eliminar un archivo (operaciones CRUD), ÁvilaOS creará un proceso de E/S en el computador.. Ese proceso pasa por las mismas colas del Proyecto 1 que cualquier otro proceso. Cuando obtiene la CPU, genera una solicitud al disco y queda Bloqueado por E/S hasta que el disco la atienda. Las solicitudes llegan a una única cola de E/S del disco, y el planificador de disco decide en qué orden se atienden según la política activa (FIFO, SSTF, SCAN, C-SCAN, entre otras).

Además, ÁvilaOS pasará al modelo de siete estados. Cuando un proceso nuevo no pueda ser admitido inicialmente en la memoria principal por falta de espacio, su PCB pasará a la cola a largo plazo. Para hacer espacio, el planificador a mediano plazo podrá suspender procesos: los saca de la RAM, los escribe en un área de intercambio reservada en el disco y libera su memoria. Los procesos suspendidos regresarán a la RAM cuando haya espacio. Tanto la suspensión como la reactivación son solicitudes de E/S que pasan por la cola del disco.

El sistema operará en dos modos de usuario. En el modo administrador se permiten todas las operaciones: crear, modificar y eliminar archivos y directorios, gestionar los procesos, cambiar las políticas de planificación y ver toda la información del disco y las métricas. En el modo usuario solo se pueden leer los archivos propios o públicos y crear procesos de E/S sobre los archivos propios; no se pueden modificar archivos del sistema ni de otros usuarios.

La interfaz gráfica debe mostrar en tiempo real el estado del sistema: la estructura de directorios y archivos en el JTree, el disco con sus bloques libres y ocupados (indicando qué archivo o proceso suspendido ocupa cada bloque) y la posición del cabezal, la tabla de asignación de archivos, la cola de E/S del disco y, como en el Proyecto 1, el estado del computador con sus colas, ahora incluyendo las de procesos suspendidos.

Por último, igual que en el Proyecto 1, el diseño es una parte fundamental de la evaluación. El disco debe ser un componente más del sistema, las políticas de planificación de disco deben definirse mediante una interfaz, y los nuevos conjuntos fijos de valores (estados, tipos de operación, modos de usuario) deben representarse con enumeraciones.

# Requerimientos funcionales

## Continuidad con el Proyecto 1

- Todo lo implementado en el Proyecto 1 para el computador debe seguir funcionando: las políticas de planificación de CPU, la memoria principal y las métricas.

- En este Proyecto 2 vamos a trabajar siempre con **una** sola instancia del computador para incorporarlo con la memoria.

- No será necesario mantener los buffers de productor consumidor ni los procesos relacionados a estos.

- El disco debe implementar la misma interfaz que los demás componentes que reaccionan al reloj: en cada ciclo de reloj, el disco avanza exactamente un ciclo de trabajo.

- Deben seguir usándose Hilos/Threads y Semáforos/Semaphores de Java para proteger las estructuras compartidas de la simulación, ahora también la cola de E/S del disco, el mapa de bloques y el sistema de archivos.

- Uso obligatorio de enums. Como mínimo, además de los del Proyecto 1: los estados del proceso con suspensión, el tipo de operación de E/S (crear, leer, actualizar, eliminar, suspender, reactivar), la política de planificación de disco y el modo de usuario.

- Uso obligatorio de interfaces. Como mínimo, la política de planificación de disco debe definirse mediante una interfaz, de forma que agregar una política nueva no requiera modificar el código del planificador de disco.

## Simulación del disco (SD) y asignación de bloques

- Representar visualmente el SD como un conjunto de bloques numerados, indicando cuáles están libres y cuáles ocupados, y qué archivo o proceso suspendido ocupa cada uno.

- El número de bloques del disco es configurable y limitado (una cantidad razonable, por ejemplo entre 100 y 500 bloques). No se puede crear un archivo si no hay bloques libres suficientes.

- Implementar la asignación encadenada: cada archivo es una lista enlazada de bloques, y cada bloque guarda la referencia al siguiente.

- Liberar los bloques de un archivo cuando este se elimina.

- Reservar en el disco un área de intercambio (swap) de tamaño configurable para los procesos suspendidos.

- Mostrar en la interfaz la posición actual del cabezal del disco y su dirección de movimiento.

## Sistema de archivos y operaciones CRUD

- Implementar un JTree que represente la estructura jerárquica de directorios y archivos. Al seleccionar un elemento se debe mostrar su información: nombre, tamaño en bloques, dueño, permisos y primer bloque.

- Crear: el administrador puede crear directorios y archivos, indicando el tamaño del archivo en bloques. Los usuarios pueden crear archivos solo dentro de sus propios directorios.

- Leer: todos los usuarios pueden ver la estructura del sistema y leer sus archivos propios o los públicos.

- Actualizar: el administrador puede cambiar el nombre de archivos y directorios.

- Eliminar: al borrar un archivo se liberan sus bloques en el SD. Al eliminar un directorio se eliminan también todos sus archivos y subdirectorios.

- Cada operación sobre un archivo (crear, leer, actualizar o eliminar) la realiza un proceso de E/S, nunca la interfaz de forma directa. La interfaz solo crea el proceso.

## Procesos de E/S y planificación de disco

- Al crear un proceso de E/S, el usuario indica la operación y el archivo o directorio sobre el que trabaja. El PCB de estos procesos debe incluir, además de los elementos del Proyecto 1, la operación solicitada y la ruta del archivo.

- Cuando el proceso de E/S genera su solicitud al disco, pasa al estado Bloqueado (motivo E/S) y libera la CPU. La solicitud entra a la cola de E/S del disco local.

- Todas las solicitudes llegan a una única cola de E/S del disco. El disco atiende una solicitud a la vez.

- Implementar como mínimo cuatro políticas de planificación de disco: FIFO, SSTF, SCAN y C-SCAN. Pueden agregar otras (LOOK, C-LOOK, etc.).

- Debe ser posible pausar y reanudar la ejecución en cualquier momento, así como cambiar la política de planificación de disco mientras el sistema está ejecutando.

- Al terminar de atender una solicitud, el disco despierta al proceso correspondiente, que pasa a Listo en la cola del computador.

## Suspensión de procesos y planificador a mediano plazo

- Ampliar el modelo de estados del Proyecto 1 al modelo de siete estados: Nuevo, Listo, Ejecución, Bloqueado, Listo/Suspendido, Bloqueado/Suspendido y Terminado.

- Implementar un planificador a mediano plazo. Cuando un proceso nuevo no pueda ser admitido por falta de RAM, debe elegir un proceso Bloqueado para suspenderlo, escribiéndolo en el área de intercambio y liberando su memoria. El criterio de selección queda a su elección (por ejemplo, el de menor prioridad o el que lleva más tiempo bloqueado) y deberá justificarse el día de la defensa.

- Un proceso Bloqueado/Suspendido cuyo evento ocurre pasa a Listo/Suspendido.

- Cuando se libera memoria, el planificador a mediano plazo debe revisar si puede reactivar un proceso Listo/Suspendido, trayéndolo de vuelta a la RAM.

- Suspender y reactivar son operaciones de E/S: generan solicitudes que pasan por la cola del disco y ocupan o liberan bloques del área de intercambio.

- Las colas de procesos suspendidos deben verse en la interfaz de cada computador.

## Modo administrador y modo usuario

- Implementar dos modos de uso, seleccionables desde la interfaz: administrador (todas las operaciones) y usuario (acceso restringido).

- Cada archivo tiene un dueño y un permiso que indica si es privado o público.

- En modo usuario, la interfaz debe impedir las operaciones no permitidas y explicar por qué fueron rechazadas, sin interrumpir la simulación.

## Tabla de asignación de archivos

- Implementar un JTable que muestre, para cada archivo: nombre, cantidad de bloques asignados, dirección del primer bloque, dueño y el proceso que lo creó. Si usan colores para representar los archivos en el disco, incluyan el color correspondiente en la tabla.

- La tabla debe actualizarse en tiempo real con cada operación CRUD.

## Interfaz gráfica

- Para el computador, además de lo pedido en el Proyecto 1: las colas de Listo/Suspendido y Bloqueado/Suspendido, y el motivo de cada bloqueo (semáforo, E/S, etc.).

- De forma global: el JTree, el mapa de bloques del disco y el cabezal, la tabla de asignación de archivos, la cola de E/S del disco con la operación de cada solicitud, la política de disco en uso con un selector para cambiarla, y el modo de usuario activo.

- El log de eventos debe registrar también las decisiones del disco y del planificador a mediano plazo (ejemplos: "El disco atiende la solicitud de P4 en el bloque 87 (SSTF)", "Se suspende al proceso P7 para admitir a P9").

- La interfaz debe observar al sistema, no ser el sistema: la lógica de ÁvilaOS no debe vivir en las ventanas.

## Persistencia y configuración

- El estado del sistema de archivos (directorios, archivos, bloques asignados, dueños y permisos) debe guardarse en un archivo JSON para poder cargarse en futuras ejecuciones.

- Debe ser posible cargar los procesos en un json según el formato indicado más abajo en este documento. Así mismo, también debe ser posible cargar el estado del sistema de archivos con otro JSON.

- Agregar a la configuración del Proyecto 1: el número de bloques del disco, el tamaño del área de intercambio, la política de disco inicial y la posición inicial del cabezal.

## Métricas de rendimiento

- Del disco: desplazamiento total del cabezal, tiempo promedio de espera de las solicitudes en la cola de E/S y cantidad de solicitudes atendidas por cada política.

- De la suspensión: cantidad de suspensiones y reactivaciones.

- Mostrar en un gráfico el desplazamiento acumulado del cabezal con respecto al tiempo, para poder comparar las políticas de disco.

- Se mantienen las métricas del Proyecto 1.

## Nota

Con el fin de minimizar la complejidad del proyecto y estandarizar, se debe asumir que:

- Todos los bloques del disco tienen el mismo tamaño, y una unidad de memoria de la RAM equivale a un bloque. Un proceso suspendido ocupa en el área de intercambio tantos bloques como unidades de memoria tiene asignadas.

- El número de un bloque representa su posición en el disco. El cabezal recorre una cantidad configurable de posiciones por ciclo de reloj, y cada bloque leído o escrito cuesta un ciclo.

- El disco atiende una sola solicitud a la vez.

- Cada operación sobre el sistema de archivos o el disco que ejecuta un proceso cuenta como una instrucción ejecutada en modo sistema operativo.

- No es necesario simular el contenido de los archivos, solo su estructura y sus bloques.

# ¿Por dónde empezar?

Empiecen por el disco como un componente aislado: un mapa de bloques, la asignación encadenada y un planificador que ordene una cola de solicitudes. Prueben las cuatro políticas con el ejemplo del anexo antes de conectarlas a ÁvilaOS. Después agreguen el sistema de archivos sobre el disco y, solo cuando ese conjunto funcione, conecten los procesos de E/S a las colas del Proyecto 1. Dejen la suspensión para el final: es la parte que más toca lo que ya construyeron.

Si en el Proyecto 1 separaron bien las capas, el disco será un componente más del hardware que obedece al reloj, el sistema de archivos y el planificador de disco serán parte del sistema operativo, y la interfaz solo los observará. Si no lo hicieron, este es el momento de corregirlo.

# Consideraciones

## Conformación de equipos

- El proyecto puede ser elaborado máximo por 2 personas, en el caso de que su equipo del Proyecto 1 sea de 3, pueden mantenerlo.

- Se recomienda mantener los mismos equipos del Proyecto 1, ya que el Proyecto 2 se construye sobre su código.

## Tecnología y entorno

- Se requiere que hagan el proyecto en versiones posteriores a Java 21.

- IDE: estrictamente NetBeans. Los programas que no se ejecuten adecuadamente en este entorno serán calificados con 0 (cero).

- Restricción de librerías: solo se permite el uso de librerías externas para la visualización de gráficas (por ejemplo, JFreeChart), el manejo de archivos JSON/CSV y el uso de Hilos y Semáforos.

- Estructuras de datos: queda terminantemente prohibido el uso de java.util.ArrayList, Queue, Stack, Vector o cualquier colección del framework de Java. Los estudiantes deben programar sus propias estructuras (listas enlazadas, colas, árboles, etc.).

## Estándares de desarrollo en GitHub (obligatorio)

- El uso de un repositorio en GitHub es obligatorio. Pueden continuar en el repositorio del Proyecto 1. Proyecto sin repositorio será calificado con 0 (cero).

- Uso de ramas: no se permite trabajar únicamente en la rama main. Se debe evidenciar el uso de ramas por funcionalidad (por ejemplo: feat/disk, feat/filesystem, feat/swap) y se debe contar con una rama develop.

- Gestión de tareas: el equipo debe registrar las tareas pendientes y los errores encontrados mediante el sistema de Issues de GitHub.

- Integración: la fusión de código entre ramas debe realizarse mediante Pull Requests comentados.

- Contribución equitativa: el historial de commits debe reflejar una participación equilibrada de todos los integrantes. Un desbalance significativo en los aportes afectará la nota individual.

- Commits: los mensajes deben ser descriptivos (por ejemplo: "feat: agrega política C-SCAN al planificador de disco" en lugar de "update code") y de tamaño limitado.

## Interfaz gráfica

- Es un requisito indispensable. Proyectos sin interfaz gráfica o que solo funcionen por consola serán calificados con 0 (cero).

- La interfaz debe ser intuitiva y permitir ver en tiempo real el cambio de estado de los procesos, del disco y del sistema de archivos.

- Es obligatorio implementar validaciones de tipo de dato y de rango en todos los campos de entrada. El sistema debe manejar las entradas inválidas sin interrumpir el simulador.

## Documentación e informe

- Junto al código se debe entregar un informe que detalle la funcionalidad de las clases y métodos más importantes del proyecto, y los enums e interfaces utilizados.

- Dos diagramas UML que describan el sistema, actualizados con los componentes nuevos.

- Las conclusiones sobre el comportamiento del sistema con cada política de planificación de disco (comparando el desplazamiento del cabezal y los tiempos de espera) y sobre el efecto de la suspensión de procesos.

- No hace falta documentar todo el código.

## Entrega y evaluación

- Fecha límite: viernes de Semana 11 antes de las 7:00 AM.

- Canal: enviar el informe en PDF y el link del repositorio de GitHub a Gabriela Costa y Ares Ramírez, y agregarlos al spreadsheet antes de la hora indicada.

- Defensa: el viernes de Semana 11 los estudiantes deberán realizar la defensa, en la cual cada uno demostrará sus conocimientos sobre el proyecto. La presencia de todos los integrantes del equipo es obligatoria. Si un estudiante no asiste, su nota en el proyecto será 0 (cero). Si un estudiante reprueba la defensa, su nota máxima será de 10 (diez) puntos, independientemente de la calidad del código.

- Es importante que cada uno de los miembros del equipo posea un buen conocimiento general del funcionamiento de cada módulo de la solución.

- No se corregirá el código para validar su funcionamiento, pero sí para verificar que los miembros del equipo lo comprendan.

- Los proyectos sin repositorio en GitHub, que no se ejecuten adecuadamente o que no sean realizados en Java serán calificados con 0 (cero).

# 

# 

# Formato de los JSON a utilizar:

Para garantizar la consistencia de la simulación, la interfaz gráfica debe exigir que se carguen ambos archivos JSON obligatoriamente antes de iniciar el reloj global de ÁvilaOS. El estado del sistema de archivos debe cargarse con un JSON para inicializar el disco (mapa de bloques, tamaño, archivos existentes y posición del cabezal), y los procesos deben cargarse mediante un segundo JSON que define las políticas y los procesos. Si la simulación arranca sin el sistema de archivos, los procesos de E/S fallarán al intentar acceder a rutas que no están en el JTree; si arranca sin los procesos, el disco y la RAM quedarán vacíos. Separar esta carga permite reiniciar la simulación con nuevos procesos preservando el estado modificado del disco de una ejecución anterior.

**Archivo del Sistema de Archivos**

```json
{

"diskConfig": {

"totalBlocks": 200,

"initialHead": 50

},

"fileSystem": [

{

"path": "/sistema",

"type": "DIRECTORY",

"owner": "admin",

"permissions": "public"

},

{

"path": "/usuarios",

"type": "DIRECTORY",

"owner": "admin",

"permissions": "public"

},

{

"path": "/sistema/boot.bin",

"type": "FILE",

"startBlock": 11,

"blocks": 1,

"owner": "admin",

"permissions": "public"

},

{

"path": "/usuarios/notas.txt",

"type": "FILE",

"startBlock": 34,

"blocks": 1,

"owner": "user1",

"permissions": "private"

},

{

"path": "/sistema/script.py",

"type": "FILE",

"startBlock": 62,

"blocks": 1,

"owner": "admin",

"permissions": "public"

},

{

"path": "/sistema/core.dll",

"type": "FILE",

"startBlock": 64,

"blocks": 1,

"owner": "admin",

"permissions": "public"

},

{

"path": "/sistema/config.sys",

"type": "FILE",

"startBlock": 95,

"blocks": 1,

"owner": "admin",

"permissions": "public"

},

{

"path": "/usuarios/img1.png",

"type": "FILE",

"startBlock": 119,

"blocks": 1,

"owner": "user1",

"permissions": "private"

},

{

"path": "/usuarios/data.csv",

"type": "FILE",

"startBlock": 123,

"blocks": 1,

"owner": "user2",

"permissions": "public"

},

{

"path": "/usuarios/video.mp4",

"type": "FILE",

"startBlock": 180,

"blocks": 1,

"owner": "user1",

"permissions": "public"

}

]

}


```
**Archivo de Procesos y Configuración**

```json
{

"globalConfig": {

"cycleDurationMs": 300,

"swapSizeBlocks": 100,

"initialDiskPolicy": "FIFO"

},

"computer": {

"ramCapacity": 1024,

"policy": "FCFS",

"quantum": 0

},

"processes": [

{

"name": "Proc_95",

"memory": 16,

"deadline": 500,

"instructions": 2,

"targetPath": "/sistema/config.sys",

"operation": "READ",

"fileSizeBlocks": 0

},

{

"name": "Proc_180",

"memory": 16,

"deadline": 500,

"instructions": 2,

"targetPath": "/usuarios/video.mp4",

"operation": "READ",

"fileSizeBlocks": 0

},

{

"name": "Proc_34",

"memory": 16,

"deadline": 500,

"instructions": 2,

"targetPath": "/usuarios/notas.txt",

"operation": "UPDATE",

"fileSizeBlocks": 0

},

{

"name": "Proc_119",

"memory": 16,

"deadline": 500,

"instructions": 2,

"targetPath": "/usuarios/img1.png",

"operation": "DELETE",

"fileSizeBlocks": 0

},

{

"name": "Proc_11",

"memory": 16,

"deadline": 500,

"instructions": 2,

"targetPath": "/sistema/boot.bin",

"operation": "READ",

"fileSizeBlocks": 0

},

{

"name": "Proc_123",

"memory": 16,

"deadline": 500,

"instructions": 2,

"targetPath": "/usuarios/data.csv",

"operation": "UPDATE",

"fileSizeBlocks": 0

},

{

"name": "Proc_62",

"memory": 16,

"deadline": 500,

"instructions": 2,

"targetPath": "/sistema/script.py",

"operation": "READ",

"fileSizeBlocks": 0

},

{

"name": "Proc_64",

"memory": 16,

"deadline": 500,

"instructions": 2,

"targetPath": "/sistema/core.dll",

"operation": "READ",

"fileSizeBlocks": 0

}

]

}


```


**Casos de prueba:**

### Caso P1 – FIFO

| Elemento | Valor |
|----|----|
| Cabezal inicial | 50 |
| Solicitudes generadas (en orden de llegada) | 95, 180, 34, 119, 11, 123, 62, 64 |
| Política | FIFO |
| Orden esperado | 95 → 180 → 34 → 119 → 11 → 123 → 62 → 64 |

### 

### Caso P2 – SSTF (Shortest Seek Time First)

| Elemento            | Valor                                    |
|---------------------|------------------------------------------|
| Cabezal inicial     | 50                                       |
| Solicitudes en cola | 95, 180, 34, 119, 11, 123, 62, 64        |
| Política            | SSTF                                     |
| Orden esperado      | 62 → 64 → 34 → 11 → 95 → 119 → 123 → 180 |

### 

### Caso P3 – SCAN

| Elemento | Valor |
|----|----|
| Cabezal inicial | 50 |
| Solicitudes en cola | 95, 180, 34, 119, 11, 123, 62, 64 |
| Dirección inicial | Hacia los bloques mayores (↑) |
| Política | SCAN |
| Orden esperado | 62 → 64 → 95 → 119 → 123 → 180 → (llega al 199 y regresa) → 34 → 11 |

### 

### Caso P4 – C-SCAN

| Elemento            |
|---------------------|
| Cabezal inicial     |
| Solicitudes en cola |
| Dirección inicial   |
| Política            |
| Orden esperado      |

\
=

# Anexo: ejemplo de planificación de disco

Disco de 200 bloques (0 a 199). El cabezal está en el bloque 50 y se mueve hacia los bloques mayores. La cola de E/S tiene solicitudes para los bloques 82, 17, 43, 140, 24, 16 y 190, en ese orden de llegada. Pueden usar este ejemplo para verificar sus políticas.

| **Política** | **Orden de atención** | **Desplazamiento total** |
|----|----|----|
| FIFO | 82, 17, 43, 140, 24, 16, 190 | 518 bloques |
| SSTF | 43, 24, 17, 16, 82, 140, 190 | 208 bloques |
| SCAN | 82, 140, 190, (llega al 199 y regresa), 43, 24, 17, 16 | 332 bloques |
| C-SCAN | 82, 140, 190, (llega al 199 y salta al 0), 16, 17, 24, 43 | 192 bloques, sin contar el salto de regreso |

Para leer más: W. Stallings, Sistemas Operativos, 5.ª ed., capítulo 11 (planificación del disco) y capítulo 12 (gestión de ficheros); capítulo 3 (procesos suspendidos y modelo de siete estados).
