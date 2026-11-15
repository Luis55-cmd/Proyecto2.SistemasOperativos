# Escenarios para probar la carga de JSON

En «Inicio nuevo», usa el botón de carga de cada tarjeta. Para el escenario del enunciado, carga `01` como sistema de archivos y `05` como procesos y configuración. Revisa también los mensajes del log de la simulación.

| Archivo | Tarjeta | Resultado esperado |
|---|---|---|
| `01-sistema-enunciado.json` | Sistema de archivos | Carga el disco y la jerarquía del ejemplo del enunciado. |
| `02-disco-vacio.json` | Sistema de archivos | Carga un disco sin entradas de archivos. |
| `03-cabezal-invalido.json` | Sistema de archivos | Rechaza el cabezal fuera del rango. |
| `04-sin-sistema-archivos.json` | Sistema de archivos | Rechaza el JSON porque falta `fileSystem`. |
| `05-procesos-enunciado.json` | Procesos y configuración | Carga la configuración y los ocho procesos del ejemplo. Sus rutas corresponden al escenario `01`. |
| `06-procesos-vacios.json` | Procesos y configuración | Actualmente se acepta con cero procesos; si debe permitirse arrancar así está pendiente de decidir. |
| `07-sin-computador.json` | Procesos y configuración | Rechaza el JSON porque falta `computer`. |
| `08-json-malformado.json` | Procesos y configuración | Rechaza el JSON malformado. |
| `09-cambio-fcfs-prioridades.json` | Procesos y configuración | Con `01`, avanzar 3 ciclos en FCFS y cambiar a PRIORITY_PREEMPTIVE; orden de finalización esperado: Urgente (6), Medio (10), Lento (15). Prueba CPU; E/S no está integrada. |
| `10-disco-ocupacion-agrupada.json` | Sistema de archivos | 110 de 200 bloques de datos ocupados en rangos. |
| `11-disco-lleno.json` | Sistema de archivos | 200 de 200 bloques de datos ocupados. |
| `12-disco-disperso.json` | Sistema de archivos | 20 bloques de datos ocupados separados. |

Tras una carga válida, cargar otra configuración del mismo tipo pide confirmación si existen datos que serán reemplazados. Cancelar conserva el estado. La carga se rechaza si el reloj ya está iniciado, aunque esté pausado. El aviso de éxito desaparece tras 2,5 segundos. Las pruebas de rechazo deben comprobar que el estado previo se conserva; el log por sí solo no demuestra esa conservación.

Estos archivos son casos para pruebas manuales, no pruebas automatizadas. `08` es intencionalmente inválido.
