# PotitoReactor v2.1 — Mutaciones y batalla

Basado en la v2.0 que pasó las seis pruebas del usuario. Se conservan la GUI, los cables animados, el manejo de tótems, la cancelación por daño, los mensajes y la explosión sin romper bloques.

## Novedades

- `/preactor boss` mirando directamente al Tremorzilla (verifica el tipo mostrado; el plugin permite vincular cualquier entidad viva para pruebas).
- `/preactor batalla iniciar`: activa un monitor de vida del jefe. Al llegar al **60 %** y luego al **25 %** se activa automáticamente una ronda con el reactor registrado más cercano en ese mundo.
- `/preactor batalla detener`: desactiva los activadores automáticos; no cancela una ronda ya activa.
- `/preactor batalla estado`: muestra estado y fases usadas.
- `/preactor detener`: detiene ronda y automatización.
- Los umbrales de fase se ejecutan **una vez por batalla**. Si se salta el 60% y llega al 25% de golpe, las rondas se procesan consecutivamente, no a la vez.
- Cortocircuito (3 errores): un tótem por participante en supervivencia afectado, buff de Fuerza I y **+20 % de daño de ataques directos detectables por Bukkit durante 45 s**.
- Tiempo agotado: un tótem por participante, curación del 10 % de vida máxima y una mutación acumulativa de hasta 3 niveles. +12 % de daño de ataques directos detectables por Bukkit por mutación; además, efectos de velocidad/fuerza originales.
- Éxito: 15 s de Debilidad I y **25 % menos daño en ataques directos detectables por Bukkit**.
- `battle.*` en config.yml permite ajustar umbrales, tiempos y multiplicadores.

**Limitaciones técnicas importantes:** Alex's Caves puede generar daño personalizado sin `EntityDamageByEntityEvent` del jefe, por lo que los multiplicadores no cubren necesariamente el rayo, pisotones o proyectiles especiales. Los efectos de velocidad de poción podrían no acelerar la IA del mob. No se modifica el sistema de invulnerabilidad/objeto vulnerable existente; el plugin simplemente dispara fases de reactores.

## Instalación

1. Sustituye los archivos de tu repositorio GitHub por los de este ZIP, conservando `.github/workflows/build.yml`.
2. Compila con Actions (Java 21, Gradle 8.14).
3. Apaga HolyHosting, elimina el JAR anterior, coloca `PotitoReactor-2.1.0.jar` y reinicia.
4. Mantén el resource pack de la v1.3: no se han modificado modelos.

## Prueba segura

1. En una zona separada, coloca/registra un reactor (`/preactor crear`) y crea un Tremorzilla de prueba. Mira el jefe y ejecuta `/preactor boss`. Comprueba que realmente hayas vinculado el mob correcto.
2. Ejecuta `/preactor batalla iniciar` y `/preactor batalla estado`.
3. Baja la vida del boss gradualmente hasta el 60 %; debería anunciar `FASE 1` y activar el reactor.
4. Resuelve el puzzle, o provoca tres errores. Comprueba éxito o buff de daño solo si los ataques lo permiten.
5. Baja el jefe al 25 %; debería activar `FASE 2` después del cooldown. Déjalo agotar tiempo y revisa `/preactor estado` (mutaciones) y su vida.
6. Ejecuta `/preactor detener` al terminar.

**Evita probar en la arena principal o con jugadores sin protección.** No se ha compilado ni probado en el Arclight del usuario todavía.
