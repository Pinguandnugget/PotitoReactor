# PotitoReactor v1.2.0 — variaciones de cables

Actualización basada en la versión 1.1.0 del plugin.

- Cada `/preactor iniciar` genera un **nuevo diseño** con 5 terminales izquierdos y 5 derechos, mezclados independientemente.
- No reutiliza ninguno de los **12 diseños anteriores** (historial guardado en `config.yml`).
- Evita rondas demasiado fáciles: como máximo 2 cables quedan alineados horizontalmente.
- Si el operador cierra el inventario, es evacuado o lo reemplaza otra persona **dentro de la misma ronda**, los cables mantienen el orden de esa ronda. Se pierde el progreso, no se puede 'rerollear' para buscar uno fácil.
- El resto del comportamiento (scoreboard, daño, chat, ítems CustomModelData) se conserva.

## Compilación
Subir el contenido de esta carpeta al repositorio, ejecutar Actions / Build o `gradle build --no-daemon` con JDK 21. El artefacto será `PotitoReactor-1.2.0.jar`.

## Pruebas
1. `/preactor iniciar`, apuntar a reactor y abrir GUI; recordar posiciones de los cinco colores en cada lado.
2. Cerrar, reabrir: mismo orden, progreso 0/5.
3. `/preactor detener`, luego `/preactor iniciar`: distinto orden.
4. Probar más de 12 rondas: no deberían repetirse los últimos 12 patrones.

No incluye todavía el efecto gráfico de dibujar segmentos entre conectores: eso requiere una disposición adicional de las ranuras y nuevos modelos del resource pack.
