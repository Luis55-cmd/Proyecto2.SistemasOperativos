# Borrador de pantallas — Proyecto 2

Estado: propuesta para revisión visual, de flujo, funcionamiento, log y cumplimiento. No implica que las pantallas o capacidades ya existan. Conservar la apariencia de ÁvilaOS y la navegación lateral de la interfaz actual. La vista existente de computador debe permanecer intacta mientras se desarrolla una vista nueva, accesible mediante una entrada lateral independiente y situada al final durante la revisión.

## Navegación propuesta

1. **Inicio:** resumen y accesos para cargar o guardar los dos conjuntos de datos.
2. **Computador:** preparar el único computador, su RAM, política de CPU y duración del ciclo.
3. **Disco:** capacidad, área de intercambio, cabezal, velocidad y política inicial de disco.
4. **Archivos:** preparar y visualizar la jerarquía inicial; árbol, propiedades y asignación de bloques.
5. **Procesos:** configurar procesos, incluidos datos dependientes de la operación de E/S.
6. **Simulación:** reloj, controles, computador, colas, disco, solicitudes y log. Las operaciones CRUD durante la simulación pasan por procesos y disco.
7. **Métricas:** vistas de CPU, memoria, disco y suspensión; distribución detallada pendiente.

Las secciones son navegables desde la barra lateral; mostrar los datos disponibles y lo que falta antes de iniciar. En desarrollo, la nueva vista de computador se agrega al final, sin sustituir la actual. El orden final puede cambiar al aprobar la propuesta.

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
