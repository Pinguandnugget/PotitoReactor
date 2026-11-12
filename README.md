# PotitoReactor v1.3.0 — Cableado visual

Plugin Java 21 / Paper API 1.21.1 diseñado para Arclight Fabric 1.21.1.

## Cambios frente a 1.2
- Al conectar un circuito, se ilumina una línea de ese color entre los terminales.
- El recorrido usa cinco columnas centrales con curvas y tramos verticales.
- Si hay varios cables conectados, los recorridos se alternan cada 12 ticks (no se superponen).
- El temporizador, cancelación por daño, mensajes, historial de combinaciones y demás lógica se conserva.
- Indicadores de error/progreso movidos a la fila inferior y selección a esquina superior.

## Instalación
1. Sube este proyecto a GitHub y ejecuta Actions para compilar `PotitoReactor-1.3.0.jar`.
2. Sustituye el JAR anterior en plugins y reinicia el servidor.
3. Activa el resource pack v1.3 suministrado **en lugar del pack de texturas PotitoReactor anterior**.
4. Si usas un pack unificado, fusiona los archivos assets/potitoreactor y los overrides de paper.json.
5. Prueba una conexión correcta; debe iluminarse una ruta sobre las casillas centrales.

Las líneas se representan como objetos de inventario, NO como gráficos libres encima de la GUI. Los recorridos de varios cables se alternan deliberadamente para que permanezcan distinguibles. No modifica las consecuencias de fallar un reactor.

El código fuente no se ha compilado en este entorno; valida el resultado mediante GitHub Actions y en un mundo de pruebas.
