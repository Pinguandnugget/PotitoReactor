# PotitoReactor v1.1 (Minecraft 1.21.1)

## Correcciones
- Lectura de daño al final del evento (`MONITOR`), con verificación en el siguiente tick para Arclight.
- Acumula **4 HP efectivos (2 corazones)** mientras el puzzle está abierto. Incluye daño que consume absorción.
- Envía avance del daño al operador en el chat de diagnóstico (`puzzle.show-damage-debug`).
- Cancela la sesión, cierra el inventario y deja el reactor libre sin reiniciar el temporizador.
- Iconos visuales con CustomModelData 72100-72118 sobre PAPER y pack independiente.

## Compilación
Sube esta carpeta a un repo de GitHub y ejecuta `Build PotitoReactor` en GitHub Actions.
Java 21, Gradle 8.14, Paper API 1.21.1 (ejecución en Arclight sujeta a pruebas).
Coloca el JAR generado en `plugins/` y reinicia el servidor.

## Instalación del resource pack
El ZIP de texturas es **independiente**, no reemplaza tu pack actual. Para incorporarlo al pack de DEDsafío, copia la carpeta `assets/potitoreactor`, el archivo `assets/minecraft/models/item/paper.json` y fusiona los overrides de PAPER si tu pack ya tiene ese archivo. No reemplaces ese JSON sin fusionarlo: se perderían otros modelos de papel.

## Cómo probar la corrección del daño
1. `/preactor crear` mirando a un bloque; `/preactor iniciar`.
2. Abre el puzzle con clic derecho.
3. Recibe golpes de daño REAL sin armadura, o prueba varios golpes hasta que el acumulado indicado en el chat llegue a 4.0/4.0.
4. Debe cerrarse el GUI y anunciar evacuación, sin reiniciar los 30 segundos.
5. Si tienes armadura o Resistencia, un golpe de **4 de daño bruto** puede causar menos de 4 HP efectivos: no debe cancelar hasta alcanzar el umbral real.
6. Si no hay daño o no sale el diagnóstico, pasa `logs/latest.log`, tipo de daño, armadura y efectos; podremos depurarlo.

**Cuidado:** el código de detonación de v1 aún usa daño letal para activar tótems y puede matar a jugadores sin tótems. Prueba las detonaciones con inventarios respaldados y en arena de pruebas; no ejecutes con jugadores reales hasta endurecer ese código.

## Compatibilidad
Los iconos usan `CustomModelData` en PAPER y el formato de overrides anterior a 1.21.4, adecuado para 1.21.1. El pack usa `pack_format: 34`.
