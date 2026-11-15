# Backlog operativo — Proyecto 2 ÁvilaOS

Corte: 14 de noviembre de 2026, `develop` en `edde1ec`. Los estados son una fotografía: **pendiente** significa que todavía no se ha verificado o completado aquí; no implica que no exista código. Los issues cerrados no certifican integración. NetBeans compiló y abrió la interfaz; el usuario confirmó que la simulación arranca después de corregir el requisito de dos computadores. Las pruebas automáticas siguen pendientes.

**Cómo usarlo:** atender una tarea por vez en el orden indicado. El orden es sugerido; las dependencias entre áreas se indican debajo. Al terminar, registrar evidencia y actualizar estado. Antes de programar, elaborar y explicar el mapa de la función según la regla pedagógica del proyecto.

Estados: ⬜ pendiente · 🟨 en curso · ✅ comprobado · 🟥 bloqueado. Ninguna tarea está marcada como comprobada sin evidencia.

**Punto de partida:** `main` solo contiene README; `develop` contiene la base heredada, disco, políticas, árbol y pruebas. Issues #1–#3 y #5–#9 cerrados; #10–#17 abiertos. El código se revisó superficialmente y no se ejecutó.

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
| T23 | ⬜ | Comprobar permisos de administrador para cada operación. |
| T24 | ⬜ | Comprobar lectura de archivos propios y públicos y rechazo de privados ajenos. |
| T25 | ⬜ | Comprobar creación de usuario solo en directorio propio y rechazo de modificaciones no autorizadas. |
| T26 | ⬜ | Mostrar el motivo de un rechazo sin interrumpir la simulación. |

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
| T39 | ⬜ | Validar configuración y jerarquía del JSON del sistema de archivos antes de aplicarla. |
| T40 | ⬜ | Restaurar bloques, cabezal, dueño y permisos; probar archivo de varios bloques. |
| T41 | ⬜ | Validar JSON de procesos, políticas y rutas, con mensajes de error comprensibles. |
| T42 | ⬜ | Impedir el inicio del reloj hasta cargar ambos JSON válidos. |
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

## Referencias y entrega

- Issues: [#1–#3](https://github.com/Luis55-cmd/Proyecto2.SistemasOperativos/issues?q=is%3Aissue), [#5–#9](https://github.com/Luis55-cmd/Proyecto2.SistemasOperativos/issues?q=is%3Aissue), [#10–#17](https://github.com/Luis55-cmd/Proyecto2.SistemasOperativos/issues?q=is%3Aissue). Las secciones indican sus issues; verificar el estado en GitHub antes de actualizar.
- Cumplir restricciones: Java posterior a 21, NetBeans, estructuras propias, hilos y semáforos; revisar librerías y ausencia de colecciones prohibidas.
- Preparar informe PDF, dos UML y conclusiones; entregar enlace y PDF a los preparadores y añadirlos al spreadsheet antes del viernes de semana 11 a las 7:00 a. m. Confirmar la fecha en el calendario del curso.
- Mantener ramas por funcionalidad, PR comentados e issues para errores; integrar a `main` por el flujo acordado.
- Aclarar con el equipo el formato persistente de cadenas multibloque, identidad/permisos y convención de desplazamiento C-SCAN. El anexo excluye el salto de retorno.

## Registro de evidencia

| Tarea | Fecha |
|---|---|---|
| T01 | 14-11-2026 | NetBeans compiló 64 archivos con destino Java 25 y abrió la interfaz; responde y permite configurar. El usuario confirmó que la simulación arranca tras corregir la condición de dos computadores. |

| T02–T13 | 15-11-2026 | Ejecutadas y aprobadas `SimulatedDiskTest` y `DiskSchedulingPoliciesTest` (`COMPILE 0`): límites, partición datos/swap sin solapamiento, cadenas multibloque, liberación, Casos P1–P4 y Anexo (518, 208, 332, 192 bloques) y cambio de política en caliente en 0 ticks. |
| T14–T22 | 15-11-2026 | Ejecutadas y aprobadas `FileSystemTreeTest` y `FileSystemManagerTest` (`COMPILE 0`): CRUD en `FileSystemManager` protegido con `Semaphore`, vinculación con asignación encadenada en `SimulatedDisk`, rechazo atómico cuando no hay espacio libre y eliminación recursiva de directorios liberando sus bloques en el SD. |
