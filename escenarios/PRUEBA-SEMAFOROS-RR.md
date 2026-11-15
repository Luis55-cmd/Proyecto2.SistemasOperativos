# Prueba de wait/signal con Round Robin

Cargar `07-semaforos-rr.json` en Simulación, con el reloj pausado. Es un escenario inicial, no un checkpoint a mitad del protocolo.

- Dos computadores: productores locales y consumidor remoto.
- Quantum 1 en ambos: puede haber desalojo entre cualquier par de instrucciones.
- Buffer de capacidad 2; ocupa 2 unidades de RAM en Productores-RR.
- Productor-A y Productor-B: 2 elementos cada uno, 2 ticks de trabajo por elemento.
- Consumidor-C: 4 elementos, 1 tick de trabajo por elemento.
- Latencia remota: 2 ticks.

## Prueba manual

1. Cargar el JSON. No añadir otros procesos durante esta prueba.
2. Avanzar algunos ticks manuales: ver alternar Productor-A/B; observar bloqueos y colas.
3. Reanudar a 500 ms (o 100 ms para acabar antes) y pausar al terminar todos, antes del ciclo 80.
4. Comprobar 4 producidos, 4 consumidos, buffer vacío y semáforos M=1, L=0, V=2.
5. Los tres procesos deben estar terminados. PC/MAR finales: A=14, B=14, C=24. Si el inspector no los muestra, el log automático del escenario contiene esos registros.
6. RAM final: 2 unidades en Productores-RR (el buffer), 0 en Consumidor-remoto.
7. Exportar el log desde la interfaz y compartirlo para contrastar el recorrido.

Cada elemento ejecuta N instrucciones de trabajo + 5 de protocolo. El tiempo bloqueado o esperando CPU no avanza PC. Un wait que bloquea sí avanza una vez; despertar no vuelve a ejecutar wait.

## Verificación automática

`EscenariosJsonRunner` usa la misma función Cargar Estado que la GUI. Para el escenario 07 verifica por tick conservación de permisos y del mutex, observa espera por mutex y desalojo conservándolo, y exige los registros y contadores finales indicados.

`ProtocolCleanupTest` cancela productores y consumidores en 12 puntos del protocolo, comprueba permisos contra el contenido real del buffer y ejecuta dos veces la limpieza para verificar idempotencia.

La persistencia de una sesión a mitad de protocolo sigue pendiente: el formato existente no guarda fases, permisos ni contenido del buffer. Este JSON parte siempre del ciclo 0.
