# Borrador de pantallas — Proyecto 2

Estado: propuesta para revisión visual, de flujo, funcionamiento, log y cumplimiento. No implica que las pantallas o capacidades ya existan. Conservar la apariencia de ÁvilaOS y la navegación lateral de la interfaz actual. La decisión más reciente reemplaza el Inicio anterior y modifica la vista existente de Computador; estos cambios están en el PR #31, pendiente de integración.

## Navegación propuesta

1. **Inicio:** resumen y accesos para cargar o guardar los dos conjuntos de datos.
2. **Computador:** preparar el único computador, su RAM, política de CPU y duración del ciclo.
3. **Disco:** capacidad, área de intercambio, cabezal, velocidad y política inicial de disco.
4. **Archivos:** preparar y visualizar la jerarquía inicial; árbol, propiedades y asignación de bloques.
5. **Procesos:** configurar procesos, incluidos datos dependientes de la operación de E/S.
6. **Simulación:** reloj, controles, computador, colas, disco, solicitudes y log. Las operaciones CRUD durante la simulación pasan por procesos y disco.
7. **Métricas:** vistas de CPU, memoria, disco y suspensión; distribución detallada pendiente.

Las secciones son navegables desde la barra lateral; mostrar los datos disponibles y lo que falta antes de iniciar. La vista existente de Computador se está modificando en el PR #31. El orden final puede cambiar al aprobar la propuesta.

## Carga, creación y guardado

- Inicio ofrece «Cargar sistema de archivos» y «Cargar procesos y configuración», con indicación del resultado. El primero abastece disco y archivos; el segundo, computador, ajustes de disco y procesos. Las secciones también muestran de dónde viene cada dato y permiten revisarlo. Debe advertirse antes de reemplazar cambios existentes; política de combinación y alcance de sustitución pendientes de diseño.
- Crear manualmente ambos conjuntos y mezclarlos con datos cargados son flujos propuestos. Antes de iniciar se validan juntos; qué constituye un escenario vacío válido está pendiente. La lectura literal del enunciado exige cargar ambos archivos: la alternativa manual fue enviada al equipo y no está aprobada por los preparadores.
- Inicio ofrece «Guardar sistema de archivos» y «Guardar procesos y configuración»; se podría ofrecer guardar ambos como comodidad. Las páginas de archivos o simulación pueden añadir accesos directos coherentes, sin duplicar su significado. Guardar ocurre a petición, no en cada ciclo. La carga y el guardado del segundo conjunto aún requieren comprobar alcance y soporte real; no prometer reanudación exacta de CPU, procesos o disco a mitad de ejecución.
- [Enunciado fuente](enunciado-proyecto-2.md#formato-de-los-json-a-utilizar). La persistencia de la jerarquía modificada está exigida; la propuesta manual es una divergencia de su condición literal de carga.

## Propuesta de vista nueva de computador

Mantener la paleta, tipografía y tarjetas de la pantalla existente. Una tarjeta de configuración, un resumen del único computador y mensajes claros sustituyen conceptualmente la tabla de múltiples computadores, sin modificar la vista existente.

| Campo | Propósito | Propuesta inicial |
|---|---|---|
| Nombre | Identificar el computador en la interfaz y el log. | Editable; comprobar si el formato de carga puede conservarlo: el ejemplo JSON no lo incluye. |
| RAM | Capacidad disponible para procesos. | Selector con unidad de memoria; validar valor permitido. |
| Política de CPU | Seleccionar FCFS, Round Robin, EDF o prioridades según capacidades reales. | Opciones visibles; confirmar disponibilidad en código. |
| Quantum | Número de ciclos de turno para Round Robin. | Habilitado y validado solo cuando corresponda. |
| Duración del ciclo | Velocidad del reloj global en milisegundos. | Mostrar como ajuste global en esta pantalla; confirmar rangos y control actual. |

Mostrar estado sin crear/configurado, origen manual o cargado cuando sea fiable, resumen y mensajes de validación. Un botón aplicaría los datos; sus consecuencias si ya existe un computador o hay procesos quedan por definir antes de conectarlo. Evitar crear un segundo computador. Un cambio aplicado o rechazado debe dejar evidencia comprensible en el log sin registrar cada edición de campo. El diseño visual inicial no supone que estos comportamientos ya estén implementados.

## Verificaciones futuras

- Visual: jerarquía, legibilidad, tamaño de ventana, accesibilidad y consistencia con la GUI actual.
- Flujo: creación manual, carga, combinación y prevención de reemplazos accidentales.
- Funcionamiento: validación, una sola instancia y coherencia entre formularios y estado.
- Log: éxito y rechazo de acciones relevantes con motivos claros.
- Enunciado: dos JSON, persistencia del disco, operaciones de E/S y divergencia explícita del arranque manual.

## Primer mapa propuesto para acordar

- **Función o módulo:** construir el contenido visual de la nueva vista de computador.
- **Responsabilidad:** presentar los campos y etiquetas del borrador, sin aplicar ni modificar la simulación.
- **Entradas:** ninguna para una primera maqueta estática; los valores mostrados serían ejemplos identificables como tales.
- **Salidas:** un panel Swing para mostrar en una entrada lateral independiente.
- **Estado o efectos laterales:** crea componentes visuales; no crea computador ni modifica estado del simulador.
- **Conexiones:** el contenedor de navegación mostrará el panel al seleccionar su entrada; la vista actual permanece separada.
- **Condiciones:** ninguna de negocio en la maqueta inicial; decidir si mostrar el quantum deshabilitado como representación visual.
- **Iteraciones y terminación:** no requiere ciclos propios para su lógica; Swing gestiona sus eventos.
- **Caso normal y error:** se muestra al seleccionarla; si no se puede agregar al contenedor, se detecta al compilar o abrir la aplicación. La validación de datos pertenece a un paso posterior.

Antes de implementar, el estudiante debe poder explicar el mapa con sus palabras; después anticipará el resultado de la prueba visual.

## Borrador: creación y gestión del disco

Propuesta del estudiante: iniciar sin disco configurado; Disco muestra una invitación a crear o cargar uno y, cuando existe, presenta la vista de consulta y gestión. El cabezal empieza en 0 por defecto; una carga JSON conserva la posición declarada. El enunciado pide configurar la posición inicial, por lo que se propone además ofrecer un ajuste avanzado y no restringirla solo a JSON. El modelo actual crea un disco predeterminado, por lo que este comportamiento todavía no está implementado.

- Capacidad y swap: definir cuándo pueden modificarse y cómo prevenir pérdida de archivos o procesos suspendidos; no se ha acordado un algoritmo de redimensionamiento.
- Velocidad: evaluar cambio durante la ejecución utilizando el mecanismo del disco y validación de permisos; la política ya tiene controles en la vista.
- Eliminar: confirmar explícitamente la pérdida del disco y archivos. El estudiante prefiere conservar los procesos y registrar los fallos al intentar acceder a lo perdido, sin eliminarlos preventivamente. Debe permitirse eliminar incluso con procesos en swap; todavía falta diseñar el resultado para esos procesos, la solicitud activa y la cola de E/S para evitar bloqueos o referencias huérfanas.
- Antes de iniciar se requiere un disco válido. La alternativa de creación manual al requisito literal de carga de ambos JSON sigue pendiente de validación con el equipo.

## Borrador: pestaña Archivos — flujo guiado

Propuesta de nivel 2, pendiente de revisión con el estudiante y de comprobación contra la versión vigente de `develop` y el PR #37. Ningún control o flujo descrito aquí se considera implementado por estar documentado. La entrada **Archivos** iría en la navegación lateral, entre **Disco** y **Procesos**, respetando la apariencia existente de ÁvilaOS.

### Distribución propuesta

```text
Archivos                                    Modo: ADMIN / usuario activo
Consulta y gestión del sistema de archivos

Jerarquía                         Detalles de la selección
[árbol: /, directorios, archivos]  Nombre · Ruta · Tipo
                                   Dueño · Permisos
                                   Tamaño en bloques · Primer bloque

Acciones: [Crear archivo] [Crear directorio] [Leer] [Renombrar] [Eliminar]
Solicitud: pendiente / completada / fallida y motivo
```

El modo activo se muestra como contexto; su control global definitivo se diseñará aparte, sin duplicar una sesión independiente en esta pantalla. Sin selección, detalles y acciones dependientes muestran una indicación. Sin sistema de archivos disponible, se muestra estado vacío y acceso a la carga desde Inicio. Un árbol vacío con raíz válida se distingue de la ausencia de sistema. La tabla de asignación y el mapa detallado del disco se ubicarán según se definan #39 y #40; no se presupone su implementación aquí.

### Flujos

- **Explorar:** expandir directorios y seleccionar un elemento; mostrar nombre, ruta, tipo, dueño, permisos, tamaño y primer bloque cuando corresponda. El enunciado permite a todos ver la estructura; eso no implica permiso para leer cualquier archivo.
- **Crear:** seleccionar directorio, elegir archivo o directorio, introducir nombre y tamaño en bloques cuando corresponda, revisar y enviar. Un usuario solo puede crear archivos en sus propios directorios; la creación de directorios corresponde al administrador. Validar y mostrar el motivo del rechazo.
- **Leer:** seleccionar archivo, solicitar lectura y mostrar el resultado o error una vez atendida. El contenido concreto de la lectura depende de lo que almacena el modelo; no prometer un editor de texto.
- **Renombrar:** seleccionar archivo o directorio, introducir nuevo nombre, revisar y solicitar. El enunciado asigna esta operación al administrador; no renombrar la raíz.
- **Eliminar:** seleccionar archivo o directorio, advertir que un directorio elimina su contenido recursivamente y pedir confirmación; enviar solicitud y reflejar el resultado al terminar. No eliminar la raíz.
- **Seguimiento:** distinguir solicitud enviada o pendiente, completada y fallida cuando el sistema permita conocerlo; presentar el motivo de fallo. Consultar etapas detalladas en Computador, Disco y log. No mostrar como completada una operación solo por crear el proceso.

La interfaz no ejecuta directamente el CRUD de archivos: crea un proceso de E/S, que pasa por CPU y disco. Los permisos y condiciones deben comprobarse en backend además de las validaciones de interfaz. Precisar con el equipo el recorrido de operaciones de directorio, que el enunciado menciona junto a archivos pero formula explícitamente la obligación de proceso para operaciones sobre archivos. Definir qué ocurre si la selección cambia o desaparece mientras una solicitud está pendiente y cómo se notifican los resultados.

### Base técnica observada y límites

El enunciado exige `JTree` para visualización y estructuras propias para el modelo; prohíbe colecciones del framework de Java. En la copia aislada de `develop` revisada el 19-11, `FileSystemTree` y `FSNode` representan la jerarquía y cada nodo guarda hijos en `MyLinkedList` propia. `FileSystemManager` protege operaciones con un semáforo, pero devuelve referencias a nodos: la lectura posterior no queda automáticamente protegida por ese semáforo. La vista debe obtener datos coherentes sin bloquear el hilo de interfaz indefinidamente y actualizar Swing en su hilo de eventos. Se evaluará un `TreeModel` adaptador o una instantánea con estructuras propias; no se da por resuelta la concurrencia ni se garantiza ausencia de estructuras internas de Swing por elegir un adaptador.

La copia examinada tiene navegación lateral con `CardLayout` y entradas Inicio, Computador, Disco, Procesos, Simulación y Métricas; aún no aparece una página Archivos. La rama local activa es más antigua. Antes de implementar, contrastar la versión remota vigente y el PR #37 sin sobrescribir cambios locales. El control de cambio de capacidad del disco tiene riesgos de pérdida y de referencias pendientes; se trata por separado.

### Primera pieza para acordar

Comenzar con la construcción visual de la página y su estado sin selección, sin conectar acciones CRUD. Antes de escribir código se completará y explicará el mapa obligatorio de la función concreta: responsabilidad, entradas, salidas, efectos, conexiones, condiciones, iteraciones y casos normal y de error. Luego se anticipará el resultado de la prueba visual. El árbol real y su actualización segura serán piezas posteriores con sus propios mapas.

### Propuesta visual detallada — 20 de noviembre

Actualización: se incorporó una primera maqueta navegable en la rama local, con panel de jerarquía pendiente de conexión, detalles vacíos, botones deshabilitados y tarjeta filosófica reutilizada. Compila; falta revisión visual en NetBeans. El árbol real y las operaciones siguen pendientes.

Conservar el fondo crema, tarjetas claras de borde fino, títulos verde oscuro, acentos verdes y texto secundario de la vista de Disco. Reutilizar el lenguaje de las funciones visuales existentes. El menú lateral quedaría: 01 Inicio, 02 Computador, 03 Disco, 04 Archivos, 05 Procesos, 06 Simulación y 07 Métricas. Revisar también el acceso de Archivos en el recorrido de Inicio. La numeración y los destinos deben mantenerse alineados; la navegación actual depende de ordinales y conserva un destino de buffer oculto.

Encabezado: etiqueta «ARCHIVOS», título «Tus archivos» y bajada «Explora la estructura y solicita operaciones sobre tus archivos.» En la zona principal, dos tarjetas: a la izquierda «JERARQUÍA» con árbol desplazable; a la derecha «DETALLES» con nombre, ruta, tipo, dueño, permisos, tamaño y primer bloque. Debajo de los detalles, «ACCIONES» con Crear archivo, Crear directorio, Leer, Renombrar y Eliminar. Seguir con «ESTADO DE LA SOLICITUD» y un mensaje de espera o resultado; en la maqueta inicial indicar claramente «Operaciones disponibles próximamente» y deshabilitar botones. Con poco ancho, colocar tarjetas una debajo de otra y permitir desplazamiento vertical; no truncar datos importantes.

Al pie de la zona principal, una tarjeta filosófica como las de Inicio y Disco: título «SOBRE EL ORDEN», espacio para retrato y cita con autor y referencia. La selección de cita y retrato concretos queda pendiente de verificar; existen recursos locales de Marco Aurelio, Lao-Tsé y Aristóteles. Mantener esa tarjeta subordinada a la gestión y sin fingir una cita o atribución verificada.

**Estados visibles:** sin sistema disponible, invitación a cargar desde Inicio; con sistema vacío, raíz «/» y explicación de que no hay elementos; sin selección, «Selecciona un archivo o directorio para ver sus detalles»; al seleccionar, datos correspondientes. Una maqueta sin conexión debe identificarse como tal: no presentar un árbol de ejemplo como datos cargados ni simular que una solicitud se envió.

**Mapa propuesto — función individual para construir la maqueta visual:**

- **Función o módulo:** función que construye el panel inicial de Archivos.
- **Responsabilidad:** reunir título, áreas de jerarquía y detalles, acciones deshabilitadas, estado y espacio filosófico.
- **Entradas:** ninguna para la maqueta; textos y estilo existentes definidos en la propia interfaz.
- **Salidas:** un panel Swing que el contenedor pueda mostrar.
- **Estado o efectos laterales:** crea componentes en memoria; no consulta ni modifica archivos, disco, procesos o reloj.
- **Funciones con las que se conecta:** helpers visuales existentes y, posteriormente, el contenedor de navegación. La conexión del árbol real se diseñará por separado.
- **Condiciones relevantes:** mostrar controles deshabilitados y un mensaje que identifique la maqueta; si falta un retrato, mostrar la tarjeta sin imagen, sin atribución inventada.
- **Iteraciones y condición de terminación:** ninguna iteración de negocio; si se recorre una lista fija de etiquetas o botones, termina al llegar al último elemento. Swing gestiona eventos y pintura.
- **Caso normal y caso de error:** devuelve el panel con las zonas visibles; ausencia de imagen opcional no impide mostrarlo. Un fallo al incorporar el panel se detectaría al compilar o abrir la vista.

**Predicción para comprobar después de explicarlo:** al seleccionar Archivos se verá la maqueta y los controles estarán deshabilitados; no cambiarán el árbol ni el estado de la simulación. La prueba será visual en NetBeans, incluyendo una ventana estrecha. Este mapa es una propuesta de la IA, pendiente de que el estudiante lo explique y lo ajuste antes de implementar.
