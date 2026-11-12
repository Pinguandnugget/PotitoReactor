# PotitoReactor v1.0.0 (prototipo funcional)

Plugin Java 21 para Minecraft 1.21.1. Usa exclusivamente la API Bukkit/Paper, sin dependencias en tiempo de ejecución. **Compatibilidad con Arclight/Fabric por verificar en tu servidor real.**

## Compilar en GitHub (sin instalar Gradle)
1. Crea un repositorio nuevo en GitHub.
2. Sube **el contenido** de esta carpeta a la raíz (incluye `.github/workflows/build.yml`).
3. Ve a **Actions → Build PotitoReactor → Run workflow**; también corre tras subir cambios.
4. Al terminar, abre la ejecución y descarga el artifact `PotitoReactor-1.0.0`.
5. Extrae `PotitoReactor-1.0.0.jar` y cópialo en `plugins/`. Reinicia el servidor.

Alternativa local: Java 21 + Gradle 8.14, ejecutar `gradle build` y tomar `build/libs/PotitoReactor-1.0.0.jar`.

## Primer test en el servidor
1. **Haz backup de mundo y plugins antes.** En un mundo de pruebas, elige un bloque de la estructura y míralo a <=6 bloques.
2. `/preactor crear` (registra ese bloque exacto).
3. Opcional: mira a Tremorzilla y usa `/preactor boss` para asociar un ejemplar. Esta asociación dura hasta reiniciar el plugin.
4. Mira al bloque registrado y usa `/preactor iniciar` estando a <=10 bloques.
5. Haz clic derecho en el bloque. Abre un inventario de 54 casillas: selecciona cable **izquierdo** y luego terminal **derecho** del mismo color.
6. Prueba un cable incorrecto: anuncio con el nombre del jugador. A los tres fallos explota. Prueba daño acumulado de 4 HP durante el puzzle: cancela y se debe empezar de cero; **el cronómetro global sigue**.
7. `/preactor detener` detiene el evento; `/preactor quitar` desregistra el bloque que miras; `/preactor lista` imprime bloques registrados.

## Comportamiento actual
- Solo un reactor/evento activo a la vez; puedes registrar varios puntos físicos y elegir el activo al iniciar.
- Un solo operador a la vez; el menú no admite arrastrar ítems o sacarlos.
- El límite es 30 s **global**, no se reinicia cuando un jugador es evacuado o abandona el menú.
- Al agotarse el tiempo, falla con mutación; 3 errores en un intento producen cortocircuito. Las fallas detienen el evento (no son fases de una pelea continua todavía).
- Sidebar derecho para jugadores cercanos; al finalizar se restaura el scoreboard anterior. Si otro plugin modifica el scoreboard durante el evento, puede haber conflicto.
- Con un tótem equipado en la mano principal o secundaria, intenta consumirlo mediante daño letal del juego. **No está garantizado que el mod/Arclight lo procese igual ni que consuma solo uno en todos los casos; probar con inventario de test.** Sin tótem, hace 14 HP de daño (potencialmente mortal, según armadura/efectos).
- El castigo aplica a jugadores dentro del radio configurable; no destruye bloques. La destrucción de bloques de ataques propios de Tremorzilla se controla por separado.
- Con boss vinculado, un fallo por errores aplica Fuerza I 45 segundos; un fallo por tiempo aplica velocidad y curación. Un éxito aplica Debilidad I 15 segundos; **todavía no inhibe la regeneración nativa de Tremorzilla**, que exigiría integración adicional.
- El estado de mutaciones está reservado para la futura lógica por fases: esta versión termina el protocolo después de un fallo, y `/preactor iniciar` vuelve a iniciar desde cero.

## Cambiar estética
Usa tu resource pack para diseñar glifos bitmap y modelos visuales. **Esta primera entrega todavía usa bloques y cristales vanilla**: no contiene un resource pack propio ni un modelo 3D de reactor. Los nombres de los cables en la GUI son solo texto; no hay arrastre visual tipo Among Us.

## Precauciones
- Diseñado para compilar contra Paper API 1.21.1; Arclight puede diferir en inventarios, scoreboard, daños, entidades mod y efectos.
- El evento debe probarse con 1–2 jugadores y backup antes de usarlo con todos.
- Un plugin de scoreboard/chat puede competir por la misma visualización.
- No es todavía una integración completa con varias fases de combate de Tremorzilla.
