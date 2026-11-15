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

Tras una carga válida, cargar otra configuración del mismo tipo pide confirmación si existen datos que serán reemplazados. Cancelar conserva el estado. La carga se rechaza si el reloj ya está iniciado, aunque esté pausado. El aviso de éxito desaparece tras 2,5 segundos. Las pruebas de rechazo deben comprobar que el estado previo se conserva; el log por sí solo no demuestra esa conservación.

Estos archivos son casos para pruebas manuales, no pruebas automatizadas. `08` es intencionalmente inválido.
