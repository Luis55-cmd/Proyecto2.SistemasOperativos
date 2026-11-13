# Prueba manual de prioridad corregida

Reiniciar la aplicación desde proyecto1-gui. Cada carga reemplaza la sesión.
Usar +1 tick para comprobar pasos exactos; después puede Iniciar y Pausar.

## 05-prioridad-primer-despacho.json

1. Cargar. Baja está antes que Alta en el archivo, ambas listas.
2. +1 tick: CPU debe mostrar **Alta, PC1**. Listos contiene solo Baja, una vez.
3. Ejecutar hasta ciclo12 y pausar. Ambas terminadas, RAM0.

Log esperado en Actividad CPUs:
- ciclos1–5: Alta PC1–5.
- ciclo6: Inactiva (Alta acaba de terminar en ese ciclo).
- ciclos7–11: Baja PC1–5.
- ciclo12: Inactiva (Baja termina).

## 06-prioridad-apropiacion.json

Estado inicial: Media ejecutando con PC2; Listos contiene Baja1 seguida de Alta10.
1. +1 tick: **Alta PC1**. Listos debe quedar Media, Baja, cada una una sola vez.
2. +1 tick: Alta termina; CPU Inactiva al final del ciclo2.
3. +1 tick: **Media PC3**: conserva el contexto; no vuelve a PC1.
4. Continuar hasta ciclo8: las tres terminadas y RAM0.

Log esperado:
- ciclo1 Alta PC1
- ciclo2 Inactiva
- ciclo3 Media PC3
- ciclo4 Media PC4
- ciclo5 Media PC5
- ciclo6 Inactiva
- ciclo7 Baja PC1
- ciclo8 Inactiva

Exportar cada log con nombres 05-prioridad-corregida.log y 06-apropiacion-corregida.log.
El texto Inactiva refleja el estado al final del tick; no significa que el ciclo de terminación no consumió CPU.

## Verificación automática

PriorityDispatchTest cubre cola externa desordenada, orden restante, desalojo con candidato detrás de otro, contexto sin duplicación, empate sin desalojo, cola vacía y convenio inverso.
scripts/verificar-prioridades.sh compila copias con mutaciones: quitar ordenamiento inicial y desactivar apropiación; ambas deben fallar.
EscenariosJsonRunner carga los JSON mediante la misma función del botón y comprueba ejecución completa y RAM final.
