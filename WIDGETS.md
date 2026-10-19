# WIDGETS.md — Contrato de widgets Android

## Estado y base técnica
Widgets nativos con Kotlin + Jetpack Glance, conectados al mismo repositorio Room que la app. Lista desplazable mediante `LazyColumn` real.

La evidencia histórica al final corresponde a versiones anteriores. **Diseño actual en código:** individual con hoy centrado y solo dos acciones (fondo y hoy); lista con tira de completados recientes y control azul de hoy. La verificación de esta revisión se registra en `README.md`; no confundir build/tests con prueba de launcher.

## A. Individual
### Composición objetivo
- Tamaño inicial 2×2, redimensionable, conjunto centrado: emoji/nombre, días alrededor de hoy y métrica opcional.
- Hoy es el círculo más grande en el centro y el único control de completado; no hay otro check duplicado. Mostrar exactamente cinco fechas: dos anteriores, hoy y dos siguientes. Adaptar los círculos vecinos al ancho disponible sin desplazar hoy del centro.
- Tocar hoy marca/desmarca sin abrir la app. El toque general del fondo abre el detalle del hábito. Nombre/emoji no tienen una acción propia duplicada: forman parte de esa superficie general.
- Los otros días muestran historial real o fechas futuras sin acción; la edición histórica sigue en el detalle. «Carrusel» describe la presentación centrada, no una navegación horizontal de historial ni siete acciones diminutas.
- Mostrar `🔥 N` si la racha actual es mayor que cero. Si es cero y el porcentaje mostrado de constancia es mayor que cero, mostrar ese porcentaje. Si ambos son cero, omitir la métrica y su espacio. No mostrar dos métricas simultáneas en este widget pequeño.
- Elegir exactamente un hábito al configurar. Hábito borrado/archivado o selección ausente: estado comprensible con acceso a reconfigurar.

### Días y estados
Ventana móvil de fechas de calendario alrededor de hoy, derivada del mismo historial del repositorio. Cruzar de semana, mes o año no cambia su orden ni oculta el día previo: hoy permanece en el centro. Solo hoy usa azul.

| Fecha/estado | Representación |
|---|---|
| Hoy pendiente | Círculo de borde azul |
| Hoy completado | Círculo azul relleno con check transparente |
| Otro día completado | Círculo gris relleno con check transparente |
| Otro día sin completar | Círculo de borde gris |
| Fecha futura | Indicador gris, sin marca ni acción de completado |

El check es un recorte transparente, no un trazo pintado con un fondo supuesto. Las marcas anteriores a creación también deberán representarse al habilitar ese historial. Un círculo vacío no significa necesariamente falta: puede ser pendiente, futuro o día no programado.

Al incorporar frecuencias, el estado accesible debe distinguir día libre y pendiente exigible. El comportamiento de marcar fuera de programación depende de la decisión de `SCORE.md`; no deducirlo del color.

### Tamaño y accesibilidad
- Mantener nombre legible, sin colisión entre semana y métrica. Permitir elipsis para nombres largos y conservar el nombre completo en accesibilidad.
- Priorizar un área de toque utilizable para hoy: objetivo 48dp, aunque el círculo sea menor. No hacer clicable toda la fila para marcar hoy ni invadir el acceso al detalle.
- Probar la distribución real en 2×2 y al redimensionar; adaptar espacios/composición antes de reducir texto a tamaños ilegibles.
- Descripción de hoy: nombre del hábito, fecha completa, estado y acción «Completar»/«Desmarcar». Resto de días: fecha y estado; no depender solo del azul/gris.

## B. Lista
- Tamaño inicial 4×3 o mayor, redimensionable.
- `selected`: entre 1 y 5 hábitos activos, elegidos y ordenados para esa instancia.
- `all`: todos los hábitos activos, sin límite artificial, con scroll vertical auténtico. Los nuevos aparecen automáticamente.
- Encabezado «Mis hábitos», acceso a configuración y resumen del día cuando haya espacio. La semántica del resumen con frecuencias se define antes de incorporarlas; no equiparar «todos» a «solo los que tocan hoy» silenciosamente.
- Cada fila: emoji, nombre, constancia o racha y una tira de días con inicial sobre cada círculo. Hoy es el último, a la derecha: borde azul si está pendiente y check azul si está completo; es el único control de completado. Los anteriores son checks grises informativos. No hay otro checkbox duplicado.
- Máximo siete fechas en total, siempre dentro de hoy y sus seis días naturales anteriores, en orden cronológico y sin futuros. Hoy permanece visible aunque no toque según la frecuencia, para poder marcar/desmarcar también un día extra.
- **Diario:** recorrer los seis días anteriores desde ayer hacia atrás, tolerando una única fecha sin marcar; mostrar solo sus completados y detenerse al encontrar una segunda falta. Ejemplo: marcas `L M M J`, viernes sin marcar y hoy sábado → `L M M J S`. Nunca dibujar un check en el hueco. Esta tolerancia es solo visual: no protege ni cambia la racha real, el score o el historial.
- **Días fijos, Cada X días y Días por semana:** mostrar todos los anteriores completados dentro de esa ventana, incluidos extras fuera de programación. Omitir las fechas sin marcar, sin que una falta corte esta tira visual. Ejemplo: hoy sábado y marcas martes/miércoles/viernes → `M M V S`; los tres anteriores son grises y hoy azul. No reinterpretar esta tira como racha ni como cumplimiento del objetivo semanal.
- Con una a tres fechas la tira comparte línea con la información. Con cuatro a siete ocupa una línea propia para conservar el nombre legible y el área de hoy de 48×52dp.
- El control no repite nombre/estado como texto visible; la descripción accesible mantiene nombre, estado y acción.
- Tocar la zona de información abre el detalle por **ID estable**, incluso si la app estaba en Ajustes/Calendar o mostraba otro hábito.
- Tocar el fondo o un espacio vacío abre la app, también debajo de una lista corta. Los checks mantienen su acción independiente sin abrir la app; «Editar» sigue abriendo la configuración de esa instancia.
- Reservar un canal lateral para la barra de desplazamiento del launcher, fuera del contenido y del área de toque de los checks. Comprobar con la barra visible mientras se arrastra, no solo en reposo.
- Scroll y taps no deben activar accidentalmente el check de otra fila. La última fila debe poder desplazarse completamente hasta una zona accesible.

## Configuración y ciclo de vida
- Receivers separados: `SingleHabitWidgetReceiver` y `HabitListWidgetReceiver`.
- `appWidgetId` es la clave de configuración, con tipo, modo e IDs seleccionados en orden. No guardar selecciones globales ni usar posiciones como identidad.
- La Activity de configuración maneja `EXTRA_APPWIDGET_ID`, cancelación y `RESULT_OK`. Cancelar conserva la configuración previa de una instancia existente.
- Eliminar una instancia limpia solo su configuración, no sus hábitos ni otras instancias.
- «Todos» y «Elegidos» son opciones visualmente separadas por 12dp. El launcher puede ofrecer reconfiguración al mantener pulsado; el estado vacío también debe permitirla.
- App y widgets pueden tener contextos de tema distintos: la app sigue su preferencia; el widget debe respetar el modo que le proporciona el host. Ver colores en `DESIGN.md`.

## Acción de completar y refrescos
1. Enviar ID del hábito y valor destino explícito a un `ActionCallback` nativo.
2. Resolver la fecha según el contrato de la acción. Para el control visual de **hoy**, detectar un render de ayer y refrescar en vez de modificar silenciosamente otra fecha; cubrir el límite de medianoche al implementar.
3. Verificar existencia/estado del hábito y persistir transaccionalmente con `setCompleted`.
4. Recalcular o invalidar valores derivados y solicitar actualización de todas las instancias afectadas.
5. La app observa el mismo Room mediante Flow; no necesita una escritura paralela para ponerse al día.

La acción no inicia una Activity y debe funcionar si el proceso estaba terminado. Esto no equivale a prometer funcionamiento tras un «Forzar detención» de Android. Acciones repetidas no duplican marcas; una acción sobre un hábito borrado no debe causar un crash.

Refrescar tras escritura, al retomar la app y mediante eventos de fecha/hora y trabajo de respaldo permitido. Android puede retrasar tareas: no prometer actualización exacta a medianoche. Tras reinicio, reconstruir desde Room/configuración persistida, no desde el aspecto previo del widget.

## Matriz de validación en launcher
| Caso | Comprobar |
|---|---|
| Instancias | Dos individuales distintos y dos listas `selected`/`all`, sin interferencia |
| Acciones | Marcar/desmarcar con proceso previamente terminado; Room, app y widgets coinciden; no se abre Activity |
| Semana | Hoy pendiente/completo; ayer completo gris; futura inactiva; marca anterior a creación |
| Métrica | Racha positiva, racha cero con score positivo y ambos cero |
| Navegación | Abrir dos hábitos distintos desde lista, arranque frío/caliente, app previamente en Ajustes/Calendar |
| Scroll | 15+ hábitos; barra visible sin superponer checks; primera/última fila y gestos rápidos |
| Presentación | Claro/oscuro, fuente grande, nombres largos, 2×2 y tamaños de lista 4×2/4×3/4×4 si el host los admite |
| Ciclo de vida | Renombrar, archivar, borrar, reordenar, reconfigurar y cancelar |
| Tiempo/sistema | Cambio de fecha/semana/zona, reinicio y ahorro de batería |

Usar al menos un launcher compatible para aceptar la entrega. Ampliar a Pixel Launcher y un OEM cuando haya dispositivos disponibles; declarar la cobertura real.

## Evidencia histórica — 2026-10-17
Estos resultados pertenecen a la versión anterior al nuevo diseño objetivo.

- `./gradlew test lint assembleDebug`: 47 tests sin fallos/omitidos; lint con 0 errores, 4 advertencias y 1 sugerencia. El rediseño previo tenía 39 tests.
- Instalación conservando datos: `adb -s R5CX3463Q6H install -r app/build/outputs/apk/debug/app-debug.apk`.
- Samsung SM-A156M, Android 16 / One UI: individual centrado 2×2 (187,73×226,13dp reportados por el launcher), siete iniciales, grises y hoy completado azul con check transparente. Se usó la marca que ya tenía el usuario.
- Comprobadas separación Todos/Elegidos de 12dp y navegación al detalle desde la fila, también con la app previamente en Ajustes. Se abrió/canceló configuración sin guardarla.
- Calendar desactivado revisado en Claro y Sistema oscuro, restaurando Sistema después. No se concedieron permisos ni se modificaron marcas/eventos.
- Capturas temporales, fuera del repositorio: `/tmp/opencode/single-widget-week-dark.png`, `/tmp/opencode/calendar-theme-dark-fixed.png` y `/tmp/opencode/calendar-theme-light-fixed.png`.
- Pendientes de esa revisión: interacción check/desmarcar, ayer completo gris en dispositivo, medianoche real, dos hábitos con navegación independiente, fuente grande/nombres largos, tamaños mínimos y Pixel Launcher. Los tests de datos no sustituyen esos casos.
- El check semanal único, la métrica condicional, el arreglo de solapamiento y las nuevas frecuencias aún no tienen verificación de launcher. Los tests antiguos que bloquean fechas anteriores a creación deberán cambiar al implementar la nueva regla.

Referencia técnica: [UI de Glance](https://developer.android.com/develop/ui/compose/glance/build-ui).
