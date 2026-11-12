# PotitoReactor v2.0 — Protocolo Extinción

Plugin Bukkit/Paper API 1.21.1 (Java 21), pensado para pruebas en **Arclight + Fabric 1.21.1**.

## Compilación
Sube el contenido del ZIP a la raíz del repo GitHub. Actions -> Build PotitoReactor -> Run workflow. Descarga el artifact y extrae `PotitoReactor-2.0.0.jar`.

## Instalación
Sustituye el JAR anterior en `plugins/`, nunca mantengas varias versiones a la vez. Reinicia el servidor.

## Texturas
**Usa el mismo resource pack de PotitoReactor v1.3**. No han cambiado los IDs de modelos (`72100..`, `72200..`). Se incluye copia en el paquete de distribución. Este resource pack NO debe reemplazar directamente tu resource pack principal: fusiónalo antes si corresponde.

## Comandos
- `/preactor crear` registra el bloque que estás mirando, como terminal físico de control.
- `/preactor quitar` desregistra el bloque.
- `/preactor lista` enumera los registrados.
- `/preactor boss` vincula la entidad viva que estés mirando (comprueba que sea Tremorzilla).
- `/preactor iniciar` inicia una ronda en el reactor registrado cercano.
- `/preactor detener` detiene la ronda.
- `/preactor estado` consulta tiempo, estado y mutaciones.

## Mecánicas
- Una ronda: 30 s, cinco cables, hasta tres conexiones incorrectas.
- Las combinaciones varían y evita las 12 últimas; una evacuación no permite cambiar los pares de la ronda.
- Daño acumulado de 4 HP efectivos al operador = puzzle cancelado y pérdida del progreso, SIN resetear el reloj del reactor.
- Tres errores = cortocircuito; a participantes dentro de 65 bloques en modo supervivencia se les consume un tótem equipado o reciben 14 puntos base de daño. Tremorzilla obtiene Fuerza I por 45 s si fue vinculado.
- Tiempo agotado = misma detonación + mutación persistente durante el combate (Velocidad I -> Fuerza I -> Velocidad II) + 10% recuperación de vida del jefe.
- Las mutaciones permanecen entre rondas si el jefe vinculado sigue siendo el mismo. `/preactor boss` sobre otro jefe selecciona su propio contador de mutaciones.
- Éxito = cancela la detonación y aplica Debilidad I por 15 s al jefe vinculado.
- Sin World#createExplosion: la explosión del plugin no rompe bloques.

## Advertencias críticas
- **Probar primero en un mundo de pruebas y con inventarios vacíos.** La rama sin tótem puede matar jugadores.
- El tótem se consume de la mano secundaria en primer lugar, luego de la principal. Los tótems en el inventario NO cuentan.
- La detonación simula el consumo de un tótem y su animación de rescate, pero **no dispara el mismo flujo de eventos que un totem vanilla**. Compatibilidad con plugins que intercepten resurrecciones requiere pruebas.
- PotitoReactor no cancela la destrucción de terreno causada por los ataques NATIVOS de Tremorzilla.
- Las pociones de Fuerza/Velocidad/Debilidad pueden no afectar ataques personalizados del mod (comprobar en vivo).
- El bloque físico registrado sigue siendo un bloque normal con partículas. El **modelo 3D personalizado para ese bloque todavía no está implementado**.
- El plugin se basa en Paper API 1.21.1 y NO se ha validado mediante arranque real en Arclight; compilar exitosamente no garantiza compatibilidad de todos los eventos.
