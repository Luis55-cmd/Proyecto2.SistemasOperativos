# Backlog operativo — Proyecto 2 ÁvilaOS

Corte: 14 de noviembre de 2026, `develop` en `edde1ec`. Los estados son una fotografía: **pendiente** significa que todavía no se ha verificado o completado aquí; no implica que no exista código. Los issues cerrados no certifican integración. NetBeans compiló y abrió la interfaz; el usuario confirmó que la simulación arranca después de corregir el requisito de dos computadores. Se ejecutaron directamente pruebas existentes el 14-11-2026; ver resultados y límites al final.

**Cómo usarlo:** atender una tarea por vez en el orden indicado. El orden es sugerido; las dependencias entre áreas se indican debajo. Al terminar, registrar evidencia y actualizar estado. Antes de programar, elaborar y explicar el mapa de la función según la regla pedagógica del proyecto.

Estados: ⬜ pendiente · 🟨 en curso · ✅ comprobado · 🟥 bloqueado. Ninguna tarea está marcada como comprobada sin evidencia.

**Punto de partida:** `main` solo contiene README; `develop` contiene la base heredada, disco, políticas, árbol y pruebas. Issues #1–#3 y #5–#9 cerrados; #10–#17 abiertos. La integración completa aún no se ha verificado.

## Base y disco — #1, #2, #5–#9

**Corrección comprobada por el usuario:** la vista exigía dos computadores; ahora permite iniciar con uno y dirige a creación cuando no hay ninguno.

| ID | Estado | Acción y comprobación de terminado |
|---|---|---|
| T01 | ✅ | Compilar y abrir en NetBeans; comprobar arranque de la simulación con un computador. |
| T02 | ✅ | Ejecutar las pruebas existentes de disco y registrar resultados y fallos. |
| T03 | ✅ | Verificar límites de configuración: bloques totales, swap, cabezal y velocidad. |
| T04 | ✅ | Comprobar que las zonas de archivos y swap no se solapen. |
| T05 | ✅ | Asignar un archivo de varios bloques y recorrer la cadena hasta su fin. |
| T06 | ✅ | Eliminarlo y comprobar que todos sus bloques vuelven a quedar libres. |
| T07 | ✅ | Intentar asignar más bloques de los disponibles y verificar que no quede una asignación parcial. |
| T08 | ✅ | Comprobar que solo se atiende una solicitud a la vez y el disco avanza con el reloj. |
| T09 | ✅ | Comprobar tiempos de desplazamiento y lectura/escritura por ciclo. |
| T10 | ✅ | Ejecutar FIFO y SSTF con los casos del enunciado; registrar orden y desplazamiento. |
| T11 | ✅ | Ejecutar SCAN y C-SCAN en dirección creciente; registrar extremos, retorno y convención del salto. |
| T12 | ✅ | Verificar cambio de política con solicitudes pendientes y pausa/reanudación. |
| T13 | ✅ | Comprobar que CPU, memoria y métricas anteriores siguen funcionando con un computador. |

## Árbol y operaciones — #3, #10, #11

| ID | Estado | Acción y comprobación de terminado |
|---|---|---|
| T14 | ✅ | Verificar búsqueda por ruta existente e inexistente y estructura jerárquica propia. |
| T15 | ✅ | Verificar inserción de directorio y rechazo de nombres duplicados o rutas inválidas. |
| T16 | ✅ | Verificar inserción de archivo y conservación de dueño, permiso, tamaño y primer bloque. |
| T17 | ✅ | Crear archivo con espacio suficiente y comprobar árbol y bloques coherentes. |
| T18 | ✅ | Rechazar creación sin espacio sin alterar árbol ni disco. |
| T19 | ✅ | Leer archivo existente y manejar ruta inexistente. |
| T20 | ✅ | Renombrar archivo y directorio, incluyendo validación de nombres duplicados. |
| T21 | ✅ | Eliminar archivo y verificar liberación de sus bloques. |
| T22 | ✅ | Eliminar directorio anidado y comprobar eliminación y liberación recursivas. |
| T23 | ✅ | Comprobar permisos de administrador para cada operación. |
| T24 | ✅ | Comprobar lectura de archivos propios y públicos y rechazo de privados ajenos. |
| T25 | ✅ | Comprobar creación de usuario solo en directorio propio y rechazo de modificaciones no autorizadas. |
| T26 | ✅ | Mostrar el motivo de un rechazo sin interrumpir la simulación. |

## Procesos y disco — #13, #14, #15

| ID | Estado | Acción y comprobación de terminado |
|---|---|---|
| T27 | ⬜ | Registrar operación y ruta en el PCB de E/S y validar sus datos de entrada. |
| T28 | ⬜ | Crear proceso desde interfaz sin ejecutar directamente el CRUD. |
| T29 | ⬜ | Recorrer admisión, listo y ejecución hasta generar solicitud de disco. |
| T30 | ⬜ | Comprobar bloqueo por E/S, liberación de CPU y entrada a cola única. |
| T31 | ⬜ | Completar solicitud y verificar que el proceso vuelve a listo. |
| T32 | ⬜ | Registrar la operación como instrucción en modo sistema operativo. |
| T33 | ⬜ | Verificar las siete transiciones y colas, incluyendo bloqueado/suspendido a listo/suspendido al ocurrir su evento. |
| T34 | ⬜ | Probar falta de RAM: proceso nuevo queda en cola de largo plazo. |
| T35 | ⬜ | Acordar y justificar criterio para seleccionar un proceso bloqueado a suspender. |
| T36 | ⬜ | Solicitar suspensión por cola de disco y verificar ocupación de swap y liberación de RAM al completarse. |
| T37 | ⬜ | Manejar falta de espacio en swap sin perder proceso ni memoria. |
| T38 | ⬜ | Solicitar reactivación cuando haya RAM y comprobar liberación de swap y retorno a listo. |

## Persistencia y configuración — #12

| ID | Estado | Acción y comprobación de terminado |
|---|---|---|
| T39 | 🟨 | Validar configuración y jerarquía del JSON del sistema de archivos antes de aplicarla. |
| T40 | ⬜ | Restaurar bloques, cabezal, dueño y permisos; probar archivo de varios bloques. |
| T41 | 🟨 | Validar JSON de procesos, políticas y rutas, con mensajes de error comprensibles. |
| T42 | ⬜ | Validar los datos de disco y procesos antes de iniciar; permitir creación manual y carga JSON. El enunciado exige literalmente cargar ambos archivos: ver decisión y diferencia debajo. |
| T43 | ⬜ | Guardar el estado modificado del sistema de archivos y volver a cargarlo. |
| T44 | ⬜ | Reiniciar con nuevos procesos conservando el disco guardado. |

## Interfaz, métricas y cierre — #16, #17

| ID | Estado | Acción y comprobación de terminado |
|---|---|---|
| T45 | ⬜ | Mostrar JTree y detalle del elemento seleccionado. |
| T46 | ⬜ | Mostrar bloques libres, ocupados y de swap con propietario; mostrar cabezal y dirección. |
| T47 | ⬜ | Mostrar tabla de asignación y actualizarla tras CRUD. |
| T48 | ⬜ | Mostrar cola de E/S con operación y colas suspendidas con motivo de bloqueo. |
| T49 | ⬜ | Mostrar modo activo y permitir cambiar política de disco durante la ejecución. |
| T50 | ⬜ | Validar tipo y rango en cada entrada sin detener la simulación. |
| T51 | ⬜ | Registrar decisiones del disco y del planificador a mediano plazo en el log. |
| T52 | ⬜ | Medir desplazamiento acumulado, espera media y solicitudes atendidas por política. |
| T53 | ⬜ | Contar suspensiones y reactivaciones y mostrar gráfico de desplazamiento por tiempo. |
| T54 | ⬜ | Ejecutar un escenario integrado de CRUD mediante proceso de E/S. |
| T55 | ⬜ | Ejecutar un escenario integrado de suspensión y reactivación. |
| T56 | ⬜ | Documentar clases, métodos, enums e interfaces principales en el informe. |
| T57 | ⬜ | Actualizar el primer diagrama UML. |
| T58 | ⬜ | Actualizar el segundo diagrama UML. |
| T59 | ⬜ | Comparar políticas de disco con las métricas obtenidas y escribir conclusiones. |
| T60 | ⬜ | Evaluar y documentar el efecto de la suspensión. |
| T61 | ⬜ | Preparar y practicar la defensa individual de cada integrante. |

## Dependencias entre áreas

El árbol y disco sustentan el CRUD; el CRUD y las solicitudes de disco sustentan el recorrido de procesos de E/S. Los estados y el disco son necesarios para suspensión. La persistencia necesita modelos de archivos y procesos; la interfaz y métricas dependen de los datos que muestran. Las comprobaciones de cada área pueden adelantarse cuando su componente exista.

## Propuesta de diseño: creación manual y JSON

El [enunciado original convertido a Markdown](instrucciones/enunciado-proyecto-2.md#formato-de-los-json-a-utilizar) exige literalmente cargar ambos archivos JSON antes de iniciar el reloj. La propuesta del estudiante, enviada al equipo para validación, es permitir también crear desde cero ambos conjuntos de datos en la interfaz e iniciar cuando sean válidos, sin obligar a crear archivos físicos. Esta propuesta se aparta de la redacción literal y no equivale a una aclaratoria de los preparadores; mantiene disponible la carga de ambos JSON. El guardado será a petición, no en cada ciclo. Queda por definir y verificar el alcance exacto de lo que se guarda y si permite reanudar una ejecución o solo preparar otra.

## Referencias y entrega

- Fuente: [enunciado Proyecto 2 en Markdown](instrucciones/enunciado-proyecto-2.md) y [DOCX original](instrucciones/enunciado-proyecto-2-original.docx).
- Issues: [#1–#3](https://github.com/Luis55-cmd/Proyecto2.SistemasOperativos/issues?q=is%3Aissue), [#5–#9](https://github.com/Luis55-cmd/Proyecto2.SistemasOperativos/issues?q=is%3Aissue), [#10–#17](https://github.com/Luis55-cmd/Proyecto2.SistemasOperativos/issues?q=is%3Aissue). Las secciones indican sus issues; verificar el estado en GitHub antes de actualizar.
- Cumplir restricciones: Java posterior a 21, NetBeans, estructuras propias, hilos y semáforos; revisar librerías y ausencia de colecciones prohibidas.
- Preparar informe PDF, dos UML y conclusiones; entregar enlace y PDF a los preparadores y añadirlos al spreadsheet antes del viernes de semana 11 a las 7:00 a. m. Confirmar la fecha en el calendario del curso.
- Mantener ramas por funcionalidad, PR comentados e issues para errores; integrar a `main` por el flujo acordado.
- Aclarar con el equipo el formato persistente de cadenas multibloque, identidad/permisos y convención de desplazamiento C-SCAN. El anexo excluye el salto de retorno.

## Registro de evidencia

| Tarea | Fecha | Resultado, prueba o PR |
|---|---|---|
| T01 | 14-11-2026 | NetBeans compiló 64 archivos con destino Java 25 y abrió la interfaz; responde y permite configurar. El usuario confirmó que la simulación arranca tras corregir la condición de dos computadores. |

| T02–T13 | 15-11-2026 | Ejecutadas y aprobadas `SimulatedDiskTest` y `DiskSchedulingPoliciesTest` (`COMPILE 0`): límites, partición datos/swap sin solapamiento, cadenas multibloque, liberación, Casos P1–P4 y Anexo (518, 208, 332, 192 bloques) y cambio de política en caliente en 0 ticks. |
| T14–T22 | 15-11-2026 | Ejecutadas y aprobadas `FileSystemTreeTest` y `FileSystemManagerTest` (`COMPILE 0`): CRUD en `FileSystemManager` protegido con `Semaphore`, vinculación con asignación encadenada en `SimulatedDisk`, rechazo atómico cuando no hay espacio libre y eliminación recursiva de directorios liberando sus bloques en el SD. |
| T23–T26 | 15-11-2026 | Ejecutada y aprobada `AccessControlManagerTest` (`COMPILE 0`): permisos completos en modo `ADMIN`, restricciones en modo `USER` (lectura de estructura y propios/públicos, creación solo en directorio propio, prohibición de modificar/eliminar recursos del sistema o ajenos) y retorno de `AccessDecision` con motivo claro sin interrumpir la simulación. |

## Avance de Inicio y carga JSON — 15 de noviembre de 2026

PR [#29](https://github.com/Luis55-cmd/Proyecto2.SistemasOperativos/pull/29), pendiente de integración. La vista «Inicio nuevo» conserva el Inicio anterior y permite seleccionar los dos JSON por separado. La carga registra intentos, resultados y errores; pide confirmación al reemplazar datos existentes, muestra un aviso temporal de éxito y rechaza cargas con la simulación iniciada o pausada. Guardar sigue pendiente. Véanse el [borrador de pantallas](instrucciones/borrador-pantallas-proyecto-2.md) y los [escenarios manuales](escenarios/carga-json/README.md).

- **T39 y T41 en curso:** el estudiante probó desde la interfaz los ejemplos del enunciado, el disco vacío, la lista de procesos vacía y los casos de cabezal inválido, sección faltante y JSON malformado. El log reportó las cargas y rechazos esperados. No se han cubierto todas las validaciones de jerarquía, políticas y rutas, ni se ha demostrado desde la interfaz que el estado anterior permanece intacto después de todos los rechazos.
- La prueba existente `Module2FileSystemAndPersistenceTest` pasó ejecutada directamente; no se agregaron pruebas automatizadas de integración para los botones. La carga de cero procesos actualmente se acepta; falta decidir si debe permitir arrancar.
- **T40, T42–T44 y T45–T61 siguen pendientes:** no se ha completado su comprobación integral, la creación manual ni el guardado desde el nuevo Inicio. La propuesta de arranque manual sigue pendiente de validación por el equipo y difiere de la redacción literal del enunciado.
- **Siguiente propuesta:** diseñar una nueva pantalla de Computador que muestre y configure el único computador y permita contrastar los datos cargados, sin alterar la pantalla anterior durante su revisión. Revisar la diferencia del log entre «Computador 1» y «AvilaOS-PC».

## Vista de Computador — 15 de noviembre de 2026

En revisión: Inicio pasa a ser la vista nueva única; Computador muestra creación o consulta según exista un equipo. La vista permite cambiar política y quantum mediante los métodos existentes; RAM y nombre quedan solo de lectura tras crear. La eliminación permanece deshabilitada y depende de la integración del disco con el reloj ([#30](https://github.com/Luis55-cmd/Proyecto2.SistemasOperativos/issues/30)) y de definir e implementar la limpieza de procesos, cola y swap.

El estudiante comprobó desde la interfaz el escenario `09-cambio-fcfs-prioridades.json`: FCFS durante 3 ciclos; cambio a prioridades apropiativas; Urgente termina en ciclo 6, Medio en 10 y Lento en 15. Coincide con la ejecución de referencia del controlador. Falta comprobar Round Robin y cambios de quantum desde esta vista; el escenario prueba CPU, no la integración de E/S. La aplicación y los archivos de prueba compilan; no se agregaron pruebas automáticas para la nueva vista.

## Resultados de pruebas — 14 de noviembre de 2026

Estos resultados corresponden a una revisión anterior; los estados actualizados por el equipo y sus evidencias aparecen arriba.

- `mvn test` compiló el proyecto y los archivos de prueba, pero ejecutó **cero pruebas**: las pruebas existentes son programas con `main`, no pruebas reconocidas por Surefire. No interpretar BUILD SUCCESS como validación de la suite.
- Ejecutadas directamente con Java 25: pasaron 19 programas sin argumentos de disco, árbol, reloj, PCB, planificadores, estructuras y algunos componentes Swing en modo sin pantalla. Incluyen `SimulatedDiskTest`, `DiskSchedulingPoliciesTest` y `FileSystemTreeTest`; FIFO, SSTF, SCAN y C-SCAN coinciden con los órdenes P1–P4 y los desplazamientos del anexo según las pruebas.
- El cambio de política con solicitudes en cola pasó en la prueba de disco; falta comprobar pausa/reanudación integrada con el disco para cerrar T12. El rechazo por espacio insuficiente pasó, pero la prueba no verifica explícitamente ausencia de cambios parciales (T07). T03 requiere revisar límites y T16 todos los metadatos.
- `CreacionComputadoresGuiTest` falló al esperar varias instancias; es una expectativa heredada incompatible con el requisito de un computador. `MonitorSimulacionTest` falló en entradas del menú y requiere revisión frente a la interfaz actual. `CargaDuplicadosTest` y `EventosLogTest` requieren un archivo de entrada: la ejecución sin argumento no los valida.
- Riesgo de interpretación: el disco implementa `totalBlocks` como bloques de datos y agrega `swapSizeBlocks` aparte; por ejemplo 200 + 100 produce 300 físicos. Contrastar con el enunciado, que describe swap como área reservada del disco. Las pruebas actuales esperan el comportamiento implementado y no resuelven esa duda.
- El test concurrente de disco comprueba resultados finales, pero las aserciones dentro de hilos no se propagan al hilo principal; no demuestra por sí solo ausencia de errores de concurrencia.

## Revisión de regresión — 15 de noviembre de 2026

Base: `develop` 110536e. Compilar con Maven pasó, pero Surefire sigue ejecutando cero pruebas; se ejecutaron los programas de prueba directamente con Java 25 y modo sin pantalla. Pasaron 20 programas, incluidos los tres nuevos de CRUD, permisos y persistencia. Dos de los programas que habían pasado ahora fallan: `ControlesRelojGuiTest` (aserción Paso) y `LogCompletoYRelojTest` (se exige cargar ambos JSON antes de iniciar). La nueva condición de JSON está en el enunciado; hay que revisar y adaptar los escenarios antes de determinar si existe una regresión funcional. También siguen fallando las pruebas heredadas `CreacionComputadoresGuiTest` y `MonitorSimulacionTest`. `CargaDuplicadosTest` y `EventosLogTest` requieren archivos de entrada y no se ejecutaron en esta revisión.

Las pruebas nuevas verifican operaciones CRUD, permisos y carga/guardado de ambos JSON a nivel de componentes y controlador. Esto no demuestra integración completa con la interfaz ni que CRUD se ejecute mediante procesos de E/S. La marca T26 del remoto se refiere a la devolución de un motivo en el control de permisos; mostrarlo correctamente en la interfaz sigue pendiente de validación. Mantener abierta la duda sobre si swap debe formar parte de `totalBlocks`.
