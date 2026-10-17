# Auditoría de La Racha y plan de ejecución para Luna

Fecha: 2026-10-17. Revisor: Sol. Implementación posterior: Luna.

**Esta entrega es únicamente una auditoría y un plan. No se ha modificado código, recursos, dependencias ni especificaciones existentes.**

**Ampliación solicitada después de la auditoría:** el usuario pidió integración con Google Calendar. Su plan separado está en [`GOOGLE-CALENDAR-PLAN-LUNA.md`](GOOGLE-CALENDAR-PLAN-LUNA.md). Es una exportación opcional de agenda mediante Android, no sincronización inversa del historial. Sus permisos de calendario y salida de datos seleccionados son una excepción explícita al alcance original; Luna debe ajustar las especificaciones al implementarla. Los hallazgos y prioridades de reparación de este documento permanecen vigentes.

## 1. Dictamen y alcance

El MVP tiene una base de dominio razonable, pero **no está listo para darse por validado**. El problema prioritario del widget individual no es solo visual: su contenido y su configuración se capturan una vez y pueden quedar obsoletos durante una sesión activa de Glance. El mismo patrón existe en el widget de lista.

Las pantallas de configuración mezclan el tema nativo claro de la ventana con componentes Compose oscuros. Además, no aplican insets seguros: el título queda pegado al notch. Se confirmó también un problema de contraste de los iconos de la barra de estado al forzar el tema claro dentro de la app.

Se revisaron todos los archivos de producción Kotlin, recursos propios, manifiestos fuente/fusionado, configuración de compilación, CI y los siete archivos de tests. La revisión en teléfono fue dirigida a widgets, configuración, navegación y temas; **no equivale a ejecutar toda la matriz de aceptación**.

### Grados de evidencia

- **V — Verificado en teléfono:** síntoma observado durante esta auditoría.
- **C — Confirmado en código:** comportamiento o carencia demostrable por la implementación; puede faltar reproducción aislada en dispositivo.
- **R — Riesgo:** necesita reproducción/medición antes de atribuirle un fallo concreto.
- Prioridad **P1:** bloquear el cierre del MVP hasta resolver/verificar. **P2:** corregir antes de la aceptación funcional. **P3:** acabado o documentación.

### Entorno y verificación efectuada

- Samsung SM-A156M, Android 16 / API 36, launcher `com.sec.android.app.launcher`, sistema en modo oscuro.
- App instalada: `com.laracha.app`, versión `0.1.0`, `versionCode=1`.
- Instancias observadas: lista `47`, individual `49`. Son IDs del teléfono de prueba, **no constantes que deban incorporarse al código**.
- `./gradlew test lint assembleDebug`: **BUILD SUCCESSFUL**. Parte de las tareas estaba en caché.
- `./gradlew :app:testDebugUnitTest --rerun-tasks`: **BUILD SUCCESSFUL**, **29 tests, 0 fallos, 0 errores, 0 omitidos**.
- Lint: **0 errores y 6 advertencias**, no «lint sin observaciones». Informe: `app/build/reports/lint-results-debug.txt`.
- No se observaron excepciones fatales de la app en el buffer de `AndroidRuntime` examinado; esto no garantiza ausencia de errores en otros recorridos.
- Se volvió a guardar la misma selección del widget individual. Se cambió temporalmente el tema de la app a Claro y se restauró Sistema; se comprobó que el archivo de preferencias volvió a ser idéntico al inicial.
- Durante esta auditoría no se crearon/borraron hábitos ni se modificaron sus marcas. No se cambiaron ajustes globales de pantalla, fecha, zona ni launcher.

### Evidencias temporales

Capturas en `/tmp/opencode/`, fuera del repositorio. Pueden desaparecer; los hallazgos se describen abajo sin depender de conservarlas. No publicar capturas completas del launcher personal sin revisar privacidad.

| Archivo | Observación |
|---|---|
| `audit-current.png` | Widget individual vacío mientras la lista muestra un hábito activo. |
| `audit-single-config-dark.png` | Ese hábito ya está seleccionado; fondo blanco, tarjeta oscura y título junto al notch. |
| `audit-home-single.png` | Tras guardar nuevamente, el individual sí muestra el hábito. |
| `audit-list-config-dark.png` | La configuración de lista reproduce la mezcla de temas y la falta de insets. |
| `audit-settings-light.png` | Tema Claro aplicado a Compose, pero iconos de estado blancos sobre fondo claro. |
| `audit-single-name-opens-today.png` | Pulsar el nombre del individual abre Hoy, no su detalle. |

La lectura de las preferencias del widget confirmó que `single_habit_49` ya contenía un ID antes de volver a guardar. No se midió de forma aislada la duración exacta de la sesión responsable del estado vacío.

## 2. Primero: widget individual y base compartida

### W01 — P1 · V/C · Snapshot obsoleto en sesiones Glance

**Archivos:** `app/src/main/java/com/laracha/app/widgets/SingleHabitWidget.kt:41-53`, `HabitListWidget.kt:42-57`, `WidgetConfigStore.kt:26-44`.

**Causa:** configuración e historial se leen mediante llamadas suspendidas/`first()` antes de `provideContent`; la composición solo recibe valores capturados. `updateAll()` no reinicia `provideGlance` si ya hay una sesión activa. La documentación oficial describe expresamente esta limitación.

**Impacto:** widget recién configurado que sigue vacío; checkbox/score/racha que no reflejan una escritura; renombrados, archivados, cambios de selección o nuevos hábitos en Todos que tardan en aparecer. El síntoma de widget vacío se vio; no se reprodujo cada variante de esta lista.

**Plan para Luna:**
1. Escribir una regresión de selección vacía → selección válida durante una sesión activa.
2. Exponer `Flow` de configuración por `appWidgetId` y observarlo junto con los datos Room **dentro de `provideContent`**, usando estado de Compose compatible con Glance.
3. Una lectura inicial antes de `provideContent` puede conservarse, pero no puede ser la única fuente de actualizaciones.
4. Mantener `update`/`updateAll` después de las escrituras para iniciar el trabajo cuando no hay sesión activa. No sustituir el fallo por retrasos, reinicios forzados o múltiples `updateAll` consecutivos.
5. Aplicar el mismo arreglo al widget de lista; no persistir score/racha como solución alternativa.

**Aceptación:** configurar y reconfigurar actualiza la instancia sin esperar a que expire la sesión; cambiar una marca, nombre, archivo o selección se refleja en todas las instancias relacionadas, tanto con sesión activa como con arranque en frío. Verificar también que una instancia no recoge la selección de otra.

### W02 — P2 · V/C · El nombre no abre el detalle del hábito

**Archivos:** `SingleHabitWidget.kt:60-70`, `app/src/main/java/com/laracha/app/MainActivity.kt:23-35`, `ui/LaRachaApp.kt:17-39`, `ui/TodayScreen.kt:83-99`.

El widget inicia `MainActivity` sin el ID del hábito y la app no interpreta una ruta de detalle. Incumple `WIDGETS.md:10`.

**Plan:** definir una entrada explícita de navegación con ID estable; procesarla tanto al crear la Activity como al recibir un nuevo Intent si se reutiliza. Evitar abrir el hábito que hubiera quedado seleccionado en otra navegación. No es obligatorio introducir una biblioteca de navegación nueva.

**Aceptación:** dos widgets individuales de hábitos diferentes abren sus respectivos detalles con app fría o ya abierta; un ID eliminado muestra un estado recuperable. Checkbox y nombre siguen siendo zonas de acción distintas.

### W03 — P2 · C/R · Redimensionable declarado, sin adaptación real del contenido

**Archivos:** `SingleHabitWidget.kt:62-104`, `app/src/main/res/xml/single_habit_widget_info.xml:3-10`; revisar también `HabitListWidget.kt:71-125` y su XML.

El individual declara un mínimo de `110×110dp`, pero siempre presenta padding de 16dp, título de hasta dos líneas, score, racha, separadores y checkbox. No hay selección de layout por espacio disponible. **El recorte exacto a 2×2 y con fuente grande queda pendiente de medir**, no se da por reproducido por haber visto un widget mayor.

**Plan:** usar tamaño disponible y una estrategia `SizeMode` adecuada a Glance; diseñar versión compacta y ampliada. En compacto priorizar nombre, score y control, ocultando la racha si no cabe. Ajustar mínimos/celdas y límites de texto de acuerdo con tamaños reales del launcher, sin asumir que «2×2» son los mismos dp en todos los teléfonos.

**Aceptación:** control siempre visible y operable en 2×2; nombres largos no lo desplazan; versión ampliada aprovecha espacio. Comprobar 2×2, 4×2 y fuente grande en Samsung y Pixel Launcher.

### W04 — P2 · C · No hay ruta explícita para reconfigurar el individual válido

**Archivos:** `SingleHabitWidget.kt:59-124`, `single_habit_widget_info.xml`.

Solo el estado vacío ofrece configuración. El XML tampoco declara `widgetFeatures="reconfigurable"`. No hay gestión de widgets desde la app. No se ha comprobado si el launcher OEM ofrece alguna vía adicional por su cuenta.

**Plan:** habilitar reconfiguración del sistema donde se admita y ofrecer una vía accesible explícita si es necesaria para los launchers soportados. Preservar la selección anterior al cancelar.

**Aceptación:** se puede cambiar de hábito sin eliminar/recrear el widget, sin alterar ninguna otra instancia. No usar `configuration_optional` para omitir la elección obligatoria de un hábito individual.

### W05 — P2 · V/C · Selector del launcher: previews de carga y presentación ambigua

**Archivos:** `app/src/main/res/layout/widget_loading.xml`, ambos XML de provider, `app/src/main/res/values/widget_strings.xml`, `AndroidManifest.xml:26-44`.

Ambos providers usan la misma preview de «Cargando hábito…», con texto oscuro fijo, sin fondo propio ni variante nocturna. En el selector Samsung se vio esa preview, no una representación de cada widget. Falta un nombre de provider explícito que distinga individual y lista.

**Plan:** separar preview de estado inicial de carga; proporcionar previews representativas y legibles, etiquetas diferentes y fallback para API 26–30. Revisar tamaños/celdas declarados. El selector externo pertenece al launcher: la app puede corregir sus recursos, no controlar todo su tema.

**Aceptación:** identificar ambos tipos antes de añadirlos y leer las previews con launcher claro/oscuro; ningún texto oscuro queda transparente sobre un fondo oscuro.

## 3. Configuración de widgets: tema, notch y estado

### T01 — P1 · V/C · Fondo nativo claro y componentes Compose oscuros

**Archivos:** `app/src/main/res/values/widget_styles.xml:2-5`, `AndroidManifest.xml:7,16-25`, `SingleHabitWidgetConfigurationActivity.kt:60-68,106-139`, `HabitListWidgetConfigurationActivity.kt:56-64,101-181`, `ui/theme/Theme.kt:43-59`.

El estilo de ventana hereda de `Theme.Material.Light.NoActionBar`. Las pantallas son `Column` sin `Surface`/`Scaffold` de fondo. `MaterialTheme` por sí solo no pinta toda la ventana ni establece un color de contenido raíz para todos los textos. Resultado observado: blanco/negro de la ventana y textos raíz, tarjetas oscuras y acciones verde claro de la paleta oscura.

**Plan:** proporcionar una raíz Material que pinte `background` y use su color de contenido; alinear los recursos de ventana diurnos/nocturnos con Compose. Revisar arranque y lectura asíncrona de la preferencia para evitar destellos del tema incorrecto. No arreglarlo fijando colores blancos/negros por componente ni cambiando el modo global de Android.

**Aceptación:** configuración individual y lista coherentes con Claro/Oscuro/Sistema, tanto en arranque frío desde launcher como al reabrir. «Cancelar», ayudas, filas y radio/check deben tener contraste legible en todos los casos.

### T02 — P1 · V/C · Título pegado al notch: faltan insets seguros

**Archivos:** `SingleHabitWidgetConfigurationActivity.kt:106-109`, `HabitListWidgetConfigurationActivity.kt:101-104`.

Solo hay `padding(24.dp)`; no se consume el inset de barra de estado/cutout ni de navegación. Es un problema distinto al de paleta y fue señalado también por el usuario. En Android 16 con target 36 no debe intentarse resolver deshabilitando edge-to-edge.

**Plan:** incorporar un contenedor con insets seguros y consumir su padding exactamente una vez. Respetar borde superior, cutout lateral, navegación e IME cuando corresponda. El padding de diseño se añade dentro del área segura; no usar una constante extra como supuesto tamaño del notch.

**Aceptación:** título y controles totalmente fuera de notch/barras en vertical, horizontal y ventana pequeña; Guardar/Cancelar accesibles sobre navegación/teclado; sin doble padding respecto de Hoy/Detalle/Ajustes.

### T03 — P1 · V/C · Iconos de estado ilegibles al cambiar de tema

**Archivos:** `MainActivity.kt`, ambas Activities de configuración, `ui/theme/Theme.kt`, estilos de ventana.

Además de las configuraciones, se reprodujo en Ajustes al elegir Claro con Android en oscuro: los iconos de estado permanecen blancos sobre el fondo claro.

**Plan:** sincronizar el aspecto de las barras del sistema con el **tema efectivo de cada Activity**, no únicamente con el modo de Android. Considerar contraste de la barra de navegación y transición entre Activities.

**Aceptación:** hora, batería e iconos legibles para las seis combinaciones de sistema claro/oscuro × preferencia Sistema/Claro/Oscuro, en app y en ambas configuraciones.

### C01 — P2 · C/R · Configuración sin estado durable de edición ni manejo de guardado

**Archivos:** `SingleHabitWidgetConfigurationActivity.kt:71-104`, `HabitListWidgetConfigurationActivity.kt:67-99`, `WidgetConfigStore.kt:29-54`.

- Selección/modo/orden usan `remember`; se pierden los cambios no guardados al recrear la Activity.
- Los hábitos se cargan una sola vez; no se distinguen carga, lista realmente vacía y error.
- No hay estado Guardando ni bloqueo del doble envío. Las excepciones de escritura/refresco no tienen representación recuperable.
- Las Activities exportadas solo rechazan el ID `-1`; no comprueban pertenencia y tipo del provider. Es endurecimiento de entrada, no una vulnerabilidad explotada en esta auditoría.

**Plan:** estado guardable o ViewModel/SavedStateHandle, observación de hábitos activos, estados explícitos de carga/error/guardado y validación final antes de persistir. Validar el ID frente al provider propio sin bloquear las invocaciones legítimas del launcher. Actualizar la instancia configurada, devolver resultado coherente y permitir reintentar errores; no propagar la escritura a una selección global.

**Aceptación:** rotar no borra la selección/orden; cancelar no cambia configuración persistida; doble Guardar no inicia operaciones en paralelo; error no crashea ni aparenta éxito. ID inválido/ajeno/tipo equivocado se rechaza sin crear preferencias huérfanas.

### C02 — P2 · V/C/R · Selección confusa en Todos y riesgo de desbordamiento

**Archivo:** `HabitListWidgetConfigurationActivity.kt:101-179`.

Se muestran casillas de selección incluso cuando Todos está activo; cambiarlas no cambia el modo. El orden de hasta cinco elegidos se dibuja fuera de la zona desplazable, junto con encabezados y botones fijos. Con fuente grande/ventana baja puede dejar sin espacio la lista o el guardado; esa combinación todavía no se probó.

**Plan:** ocultar/deshabilitar selección en Todos o activar explícitamente Elegidos al elegir, con comportamiento inequívoco. Rediseñar el scroll para que todos los controles puedan alcanzarse incluso con cinco filas ordenables. Dar nombres accesibles a ↑/↓.

**Aceptación:** modo seleccionado evidente, límite 1–5 explicado, orden persistente, todos los controles alcanzables en paisaje/fuente 200 %, y estado sin hábitos utilizable.

## 4. Fechas, refrescos y operaciones

### R01 — P1 · C · La fecha de Hoy queda congelada mientras la app sigue abierta

**Archivos:** `MainActivity.kt:20,25,38-42`, `HabitRepository.kt:39-50,74-80,114-115`, `TodayScreen.kt:77,164-168`.

La fecha visible se renueva únicamente en `onCreate`/`onResume`; los Flows reciben un `forDate` fijo. Si pasa medianoche sin salir, la pantalla sigue calculando ayer, pero `setCompleted` sin fecha resuelve el nuevo hoy. Por tanto, la acción puede escribir un día distinto del presentado. Es deducible del código; no se cambió la hora del teléfono para reproducirlo.

**Plan:** introducir una fuente observable del día local, con Clock inyectable y zona actual, compartida con las consultas. Reevaluar al interactuar y en foreground al pasar de día. Definir qué ocurre si cambia el día entre render y pulsación: refrescar y operar el día actual coherentemente, sin escribir silenciosamente otro día que el mostrado.

**Aceptación:** pruebas de 23:59:59 → 00:00 con la app abierta; encabezado, completados, score, racha, calendario y acción coinciden. Probar DST de primavera/otoño, fin de año y cambio de zona manteniendo las claves históricas originales.

### R02 — P1 · C · Falta un mecanismo de respaldo para refrescar widgets

**Archivos:** `AndroidManifest.xml`, ambos providers XML, `WidgetUpdater.kt`, `HabitApplication.kt`.

No hay Worker propio periódico ni gestión de eventos de fecha/zona para actualizar datos de widgets; tampoco `updatePeriodMillis` explícito. El WorkManager transitivo de Glance y su receptor de reinicio **no equivalen** a haber programado un refresco de la fecha del producto.

**Plan:** programar trabajo único de respaldo permitido por Android y gestionar los eventos temporales pertinentes respetando sus restricciones. Recalcular siempre desde Room y fecha local. Evitar trabajos duplicados por cada widget, polling intensivo, alarmas exactas y permisos nuevos innecesarios; cancelar trabajo propio si ya no es necesario.

**Aceptación:** sin abrir la app durante días, un evento/trabajo admitido refresca marcas y métricas; tras reinicio los widgets recuperan datos. Documentar retrasos de Doze/ahorro y no prometer actualización exacta a medianoche.

### R03 — P2 · C/R · Errores de refresco silenciados y efectos ligados a pantallas

**Archivos:** `WidgetUpdater.kt:7-9`, `TodayScreen.kt:164-168,183-192,229-234,247-249`, `HabitDetailScreen.kt:111-115`, `MainActivity.kt:42-46`.

`runCatching` oculta todos los fallos, incluida cancelación de coroutines. Cada pantalla encadena escribir y refrescar en su propio scope; si sale de composición entre ambas operaciones, una escritura persistida puede quedarse sin invalidación de widgets. No se ha reproducido pérdida de datos: el riesgo es falta de notificación/visibilidad.

**Plan:** preservar `CancellationException`, registrar fallos técnicos sin datos personales, permitir que un provider falle sin impedir intentar el otro y exponer/reintentar lo necesario. Llevar la invalidación posterior al commit a una capa/ciclo de vida apropiado; no alargar indefinidamente el scope de una pantalla ni convertir un error de refresco en rollback ficticio de una marca ya guardada.

**Aceptación:** prueba de fallo de un provider, cancelación de UI después del commit y reintento; la escritura no se duplica y el resto de instancias termina consistente.

### R04 — P2 · C/R · Limpieza al borrar un widget sin tiempo de vida garantizado

**Archivos:** `SingleHabitWidgetReceiver.kt:12-16`, `HabitListWidgetReceiver.kt:12-16`.

Se inicia una coroutine suelta después del callback del receiver; el sistema puede terminar el proceso al devolver el broadcast. No se ha demostrado una preferencia huérfana en este teléfono.

**Plan:** usar el callback suspendido de eliminación que soporte Glance, o un mecanismo de receiver/trabajo con duración acotada y correctamente vinculada. Borrar únicamente las claves de los IDs eliminados. Revisar la política de restauración de IDs junto con P01.

**Aceptación:** quitar una instancia y finalizar el proceso inmediatamente no deja su configuración ni elimina la de las otras. Probar cancelación al añadir y restauración si se decide soportarla.

## 5. App, datos, accesibilidad y privacidad

### A01 — P2 · C · Atrás del sistema no representa la navegación interna

**Archivos:** `ui/LaRachaApp.kt:24-37`, `ui/TodayScreen.kt:82-99`, `ui/HabitDetailScreen.kt:84-88`.

Detalle/Ajustes se seleccionan mediante estado local, sin manejo de Atrás ni pila de navegación. La flecha cambia el estado; Atrás del sistema puede finalizar la Activity o volver a otra Activity en vez de regresar a Hoy. La prueba aislada en una tarea limpia sigue pendiente.

Además, `showEditor` se guarda pero `editing` no: después de recreación puede reabrirse un editor como creación en lugar de edición. `TodayScreen` desaparece al entrar en Ajustes, por lo que su estado local no constituye una navegación durable.

**Plan:** unificar estado de ruta/ID y comportamiento de Atrás (incluido predictivo), preservando el ID del hábito en edición. No exigir una migración amplia de arquitectura si se resuelve correctamente con el stack actual.

**Aceptación:** Detalle → Atrás → Hoy; Ajustes → Atrás → Hoy; diálogos se cierran antes de salir; rotar al editar no crea otro hábito ni pierde el objetivo de edición.

### A02 — P2 · C/R · Accesibilidad y layouts extremos sin cubrir

**Archivos:** `TodayScreen.kt:299-348,419-447`, `HabitDetailScreen.kt:146-150,185-223,250-255`, ambos widgets y configuraciones.

- Selector de colores de 36dp e iconos de 44dp, por debajo del objetivo de 48dp del diseño; selección semántica insuficiente.
- Checkbox de lista de widget sin nombre cuando está pendiente, ni contexto explícito del hábito en su etiqueta de accesibilidad.
- «Editar» del widget de lista es un texto pequeño clicable sin área generosa; flechas de orden carecen de contexto.
- Nombres largos, tres métricas en una sola fila y calendario de siete columnas necesitan revisión con fuente grande/ancho pequeño.

**Plan:** semántica de nombre/estado/acción, rol y selección; targets amplios sin solapar acciones; etiquetas humanas para colores. No resolver un calendario estrecho con siete targets de 48dp que se superpongan: adaptar espacio o proporcionar una alternativa accesible para editar el día. Mantener el control de completar distinguible sin depender solo del color.

**Aceptación:** TalkBack anuncia hábito + constancia + estado + acción correcta; controles accesibles por exploración y foco; fuente 100/150/200 %, nombre largo y ventana de ancho pequeño sin acciones inaccesibles. Las medidas de todos los casos quedan registradas, no solo una captura visual.

### D01 — P2 · C · La capa de escritura no valida el intervalo histórico

**Archivos:** `HabitRepository.kt:114-115`, `HabitDao.kt:59-67`, `HabitDetailScreen.kt:242-254`.

La UI limita el calendario, pero la API permite marcas anteriores a creación o futuras; el esquema acepta cualquier cadena de fecha mediante el DAO. Los calculadores ignoran algunas fechas fuera de rango, de modo que podrían existir filas persistidas sin reflejo en las métricas. No se inyectaron filas inválidas en el teléfono.

**Plan:** validar fecha e intervalo en la frontera de dominio/repositorio y conservar chequeo de activo dentro de la transacción. Resolver una única fecha local por operación, probar viaje hacia una zona cuyo día sea anterior a la creación y definir cómo se rechaza sin corromper historia.

**Aceptación:** fechas inválidas/futuras/anteriores a creación no se insertan; el error es recuperable; fechas pasadas válidas recomputan métricas. No borrar datos existentes sin una política de reparación explícita.

### D02 — P2 · C/R · Restaurar archivados puede introducir órdenes empatados

**Archivos:** `HabitDao.kt:49-55,70-81`, `HabitRepository.kt:117-122`.

Reordenar normaliza solo los activos; restaurar conserva el `sortOrder` antiguo. Pueden existir dos activos con la misma posición y desempatar por UUID, de manera poco predecible para el usuario. No se probó el flujo completo en el teléfono.

**Plan:** definir y probar política de restauración (por ejemplo, al final) y normalización transaccional. Validar IDs duplicados/ajenos y listas parciales de reordenación en la API.

**Aceptación:** crear → archivar → reordenar → restaurar mantiene un orden deliberado y persistente; Todos refleja ese orden, Elegidos conserva su propio orden por instancia.

### D03 — P2 · R · Recálculo de todo el historial en contexto de la UI

**Archivo:** `HabitRepository.kt:39-64,74-89`; consumo en `TodayScreen.kt:77-79`.

Cada emisión agrupa todas las marcas y recorre días desde creación por cada hábito. En la app estos operadores se ejecutan en el contexto del colector salvo que se cambie, y no se deriva el cálculo a un dispatcher de CPU. Cada widget individual consulta además la lista completa para buscar su hábito. **No se midió jank ni ANR**; es un riesgo con muchos hábitos/años.

**Plan:** medir primero con fixtures; mover CPU fuera del hilo principal y evitar recalcular/cargar hábitos no afectados cuando sea relevante. Conservar Room como verdad y cálculo correcto; no introducir acumuladores persistidos para el score.

**Aceptación:** prueba de rendimiento reproducible con decenas/centenares de hábitos e historial multianual, registro de tiempos y ausencia de bloqueo del input. No limitar el número de hábitos para ocultar el problema.

### P01 — P2 · C/R · Copia de seguridad automática sin política explícita

**Archivo:** `AndroidManifest.xml:3`; no hay reglas propias de backup/extracción ni manejo propio de remapeo de IDs.

`allowBackup="true"` hace elegibles datos de la app para las copias del sistema, según configuración/transporte del dispositivo. Esto requiere aclaración frente a «100 % local». Las preferencias de widgets vinculadas a IDs tampoco son portables por sí mismas. **No se verificó que se haya producido una subida ni restauración.**

**Plan:** definir explícitamente si se permite backup en nube/transferencia entre dispositivos. Con la promesa actual de local-only, impedir copia en nube del historial hasta que exista autorización/política distinta. Aplicar reglas compatibles con las APIs soportadas; excluir configuraciones efímeras de widget o remapear IDs al restaurar si esa ruta se soporta. Documentar diferencias OEM de transferencia.

**Aceptación:** el manifiesto/reglas y el texto de privacidad dicen lo mismo; restaurar no enlaza una instancia con otra por IDs antiguos. No añadir backend ni sincronización para resolverlo.

### P02 — P2 · C · El APK sí incorpora permisos transitivos

**Evidencia:** `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml:11-20`, informe `app/build/outputs/logs/manifest-merger-debug-report.txt` y `adb shell dumpsys package com.laracha.app`.

El manifiesto fuente no contiene permisos, pero el APK instalado solicita `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` y el permiso interno de receiver. Proceden de dependencias, principalmente WorkManager usado por Glance. **No incluye `INTERNET`; no hay evidencia de backend o analíticas.** La afirmación previa «no declara permisos adicionales» no describe el APK fusionado.

**Plan:** inventariar origen/necesidad y documentar los permisos de infraestructura. Retirar solo los realmente innecesarios tras validar Glance/WorkManager; no borrarlos todos a ciegas ni añadir permisos para alarmas exactas, notificaciones o acceso al almacenamiento. Revisar también el manifiesto release.

**Aceptación:** allowlist de permisos verificada sobre manifiesto fusionado/APK; sin `INTERNET`, permisos peligrosos nuevos ni fallos en widgets/trabajo de respaldo.

## 6. Calidad, puntos correctos y límites de la auditoría

### Q01 — P1 para aceptación · C · La suite no prueba los fallos de integración

**Archivos:** `app/src/test/`, `app/build.gradle.kts:21,44`, `.github/workflows/android.yml`.

- Los tests de repositorio usan `FakeHabitDao`; no ejecutan SQLite, anotaciones `@Transaction`, FK ni concurrencia real.
- «Dos taps» cubre dos llamadas secuenciales con `true`; no acciones paralelas ni secuencias contradictorias.
- Los tests de widgets prueban nombres de claves y serialización, no DataStore real ni independencia persistida entre cuatro instancias.
- No existe `app/src/androidTest`, ni dependencias de AndroidX Test/Compose Test; declarar el runner no crea esas pruebas.
- CI compila y ejecuta tests/lint, pero no prueba Room en Android ni un launcher.

**Plan:** añadir primero regresiones relevantes a cada hito; después cerrar con Room real, DataStore, Compose y acciones/host de widget. Comprobar versiones oficiales compatibles de las dependencias de test antes de fijarlas. Usar bases de datos de prueba separadas y fixtures propios, nunca limpiar los datos del usuario para ejecutar tests.

**Aceptación:** validar exclusión/duplicados, cascade, concurrencia, reapertura, fecha/zona, cuatro instancias, sesiones Glance activas/frías, resultado de configuración y navegación. CI debe ejecutar las nuevas pruebas automatizables o declarar explícitamente dónde se ejecutan y dónde no.

### Q02 — P3 · V/C · Acabado y documentación desalineados

**Archivos:** `ui/theme/Theme.kt:14-39`, `TodayScreen.kt:264,426`, `AndroidManifest.xml:2-7`, `README.md:31-55`, `TASKS.md:13-37`, `gradle/wrapper/gradle-wrapper.properties`.

- Se personalizan algunos roles Material, pero quedan `primaryContainer`, `secondaryContainer` y superficies de contenedor por defecto: aparecen morados en la UI verde. Completar pares fondo/contenido de los roles utilizados y comprobar contraste, no recolorear arbitrariamente todos los componentes.
- Falta icono propio de app, advertido por lint; el selector usa un icono Android genérico.
- README aún dice que no se probó en dispositivo; TASKS marca casos de fechas/taps más amplios de lo que realmente cubre la suite.
- Lint tiene seis avisos: target no último, dos de `previewLayout` API 31+, Gradle más nuevo, icono ausente y sugerencia KTX. No elevar SDK/Gradle solo por el aviso: preservar compatibilidad y justificar decisiones.
- Valorar checksum de la distribución del wrapper como endurecimiento de reproducibilidad, no como arreglo del widget.

**Aceptación:** documentación distingue implementado/probado/pendiente; previews/icono identificables; paleta consistente. Comandos y versiones usados realmente quedan registrados.

### Qué está bien y no debe romperse

- Score y rachas están separados; score usa la fórmula independiente del producto, no acumuladores por taps. Los hitos 7/13/30/60 y hoy pendiente pasan tests.
- Las claves de completado son fechas ISO locales y el Clock es inyectable. `Clock.systemUTC()` en `HabitApplication` **no es un bug por sí solo**: `LocalDay` aplica la zona local al resolver el día.
- La clave compuesta y FK con cascade son apropiadas; falta comprobarlas contra Room real.
- La acción usa `ToggleableStateKey` y `setCompleted(valor)`, no un toggle ciego. Preservar esa idempotencia. Los eventos rápidos/retrasados aún requieren pruebas de orden y política temporal, no un debounce que descarte intención sin definirlo.
- Configuración por IDs estables y por instancia; Todos emplea `androidx.glance.appwidget.lazy.LazyColumn` auténtico.
- No se detectaron componentes de backend, publicidad o analíticas ni permiso de Internet en el APK examinado.

### No validado en esta auditoría

Cuatro instancias simultáneas, scroll con 15+ hábitos, toggle con proceso muerto, eliminación/archivo durante interacción, fuente grande, paisaje, Pixel Launcher, API 26–30, reinicio, Doze/ahorro, arrastre de resize mínimo, restauración de backup, concurrencia SQLite y rendimiento multianual. No convertir estas ausencias en «fallos reproducidos» ni en pruebas aprobadas.

## 7. Plan de ejecución incremental para Luna

No hacer una reescritura global ni actualizar el stack como parte de esta reparación. Leer las cinco especificaciones y `AGENTS.md`; comprobar que los archivos no hayan cambiado desde la auditoría. Hay trabajo sin seguimiento en Git: no resetear, borrar, mover ni sobreescribir cambios ajenos.

### Hito 1 — Individual fiable y base Glance reactiva

1. Reproducir W01 con fixtures y escribir regresiones de selección/escritura durante sesión activa.
2. Resolver W01 en ambos widgets y asegurar observación por instancia.
3. Resolver W02–W05: detalle por ID, tamaños adaptativos, reconfiguración y previews/etiquetas.
4. Probar primero el individual en launcher Samsung; añadir segundo individual de otro hábito en un entorno de QA, sin alterar widgets ajenos.
5. Entregar cambios pequeños, tests y evidencias. No cerrar W03 por una captura de tamaño grande.

### Hito 2 — Configuraciones legibles y fuera del notch

1. Regresión visual/Compose para T01–T03 en ambas configuraciones y barras del sistema de la app.
2. Raíz Material, recursos de ventana, insets y barras con tema efectivo.
3. Resolver C01–C02: selección guardable, carga/error/guardando, validación de IDs y scroll.
4. Comprobar las seis combinaciones de tema, rotación, cero hábitos, cinco elegidos, título/nombre largos y fuente 200 %.

### Hito 3 — Día local y refresco duradero

1. Tests con Clock mutable para R01 antes de modificar la gestión temporal.
2. Fuente observable del día y coherencia de lectura/escritura.
3. Respaldo/eventos de R02 y manejo de errores/cancelación de R03–R04.
4. Verificar callbacks con app cerrada/proceso terminado normalmente, sin confundirlo con `force-stop` y sus restricciones del sistema.
5. Política explícita para acciones retrasadas al cruzar medianoche: no modificar silenciosamente una fecha distinta de la intención presentada.

### Hito 4 — App y datos

1. Regresiones para Atrás, edición tras recreación, fechas no válidas y restauración de orden.
2. Resolver A01–A02 y D01–D02.
3. Perfilar D03 y optimizar solo los costes medidos, sin cambiar la fórmula.
4. Acordar/aplicar P01 y revisar P02 sobre manifiestos debug/release. No implementar nube ni permisos de conveniencia.

### Hito 5 — Cierre comprobado

1. Completar Q01 con Room/DataStore reales y pruebas UI/Glance; integrar su ejecución en CI donde sea viable.
2. Matriz manual en Samsung y Pixel Launcher. Para fixtures de 15+ hábitos, cambios de fecha, reinicios y datos destructivos, usar emulador o variante de QA separada; no tocar historia personal ni el launcher predeterminado sin permiso.
3. Cerrar Q02 y actualizar README/TASKS con resultados exactos, avisos y pendientes.
4. No declarar listo ningún widget mientras falte su aceptación en launcher real.

## 8. Matriz mínima de aceptación

| Área | Casos obligatorios |
|---|---|
| Individual | Dos IDs/selecciones diferentes; configurar/reconfigurar/cancelar; nombre abre detalle; completado/desmarcado no abre UI. |
| Actualización | Selección durante sesión activa, frío, renombrar, archivar, borrar, corregir historia; app y widgets coinciden. |
| Lista | Dos instancias Elegidos/Todos; orden independiente; 1/5 elegidos y sexto rechazado; nuevo hábito en Todos; scroll real 15+. |
| Temas | Android claro/oscuro × Sistema/Claro/Oscuro; app/configuraciones/barras/previews. Definir por separado si el widget sigue sistema/Material You o preferencia de app; hoy no usa `AppPreferences`. |
| Insets/layout | Notch, gestos y navegación por botones, vertical/horizontal, ventana pequeña, IME, nombres largos, fuente 100/150/200 %. |
| Tamaños | Individual 2×2 y 4×2; lista 4×3 y 4×4; botón nunca recortado, scroll no intercepta completar. |
| Fechas | Medianoche con app abierta, fecha/zona modificada, DST primavera/otoño, bisiesto y cambio de año; conservar claves históricas. |
| Persistencia | Reabrir DB/proceso; FK/cascade; concurrentes `true/true` y secuencias `true/false/true`; corrección histórica; sin duplicados. |
| Ciclo de vida | Rotar/recrear configurando/editando/guardando, cancelar alta, borrar solo una instancia, restaurar si se soporta. |
| Sistema | App fuera de primer plano/proceso muerto, reinicio, ahorro/Doze, sin conexión, trabajo de respaldo sin duplicación. |
| Accesibilidad | TalkBack con contexto y estado; targets no superpuestos; orden de foco y acciones identificables sin color. |
| Privacidad/build | Permisos reales del APK, política de backup explícita, no Internet, versiones verificadas, lint explicado y CI. |

Los tests de dominio adicionales deben incluir desmarcar hoy y recuperar exactamente el score de ayer, historial alterno, ausencia larga después de un score no cero y reducción a la mitad tras 13 días cerrados, además de los casos temporales de integración. Son brechas de cobertura, no errores matemáticos demostrados.

## 9. Comandos y evidencia de cada entrega

Desde la raíz, comprobaciones disponibles hoy:

```bash
./gradlew test lint assembleDebug
./gradlew :app:testDebugUnitTest --rerun-tasks
adb devices -l
adb shell dumpsys appwidget
adb shell dumpsys package com.laracha.app
```

Después de añadir y configurar realmente las pruebas instrumentadas (actualmente no existen):

```bash
./gradlew :app:connectedDebugAndroidTest
```

Para instalar una entrega posterior en el teléfono, con autorización y conservando datos:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No usar desinstalación, `pm clear`, `git reset` ni cambiar fecha/zona/launcher del teléfono personal para obtener una prueba verde. Para pruebas aisladas, elegir explícitamente emulador o aplicación de QA; los tests deben crear su propia DB, no reutilizar `la_racha.db` del usuario.

Cada hito debe entregar: IDs de hallazgos resueltos, archivos, tests nuevos, comandos/resultados reales, evidencia de launcher cuando aplique y lista de lo que sigue sin verificarse. No sustituir pruebas por capturas estáticas ni por «compila».

## 10. Fuentes oficiales útiles

- Ciclo de vida y observación en Glance: https://developer.android.com/reference/kotlin/androidx/glance/appwidget/GlanceAppWidget (apartado `provideGlance`; `updateAll` no reinicia una sesión activa).
- Configuración y reconfiguración: https://developer.android.com/develop/ui/compose/glance/configuration
- Tamaños Glance: https://developer.android.com/develop/ui/compose/glance/app-widget-size
- Insets en Compose: https://developer.android.com/develop/ui/compose/system/insets
- Barras del sistema: https://developer.android.com/develop/ui/views/layout/edge-to-edge
- Copia de seguridad: https://developer.android.com/identity/data/autobackup
- Pruebas Room: https://developer.android.com/training/data-storage/room/testing-db

### Encargo resumido para Luna

Implementar este plan por hitos, empezando por W01 y el individual; luego corregir conjuntamente T01–T03 en las configuraciones. Mantener Kotlin/Compose/Room/Glance, fórmula de score y configuración por instancia. Empezar cada cambio por su regresión, no tocar datos personales y no declarar el MVP validado hasta completar la matriz correspondiente. Los hallazgos R necesitan primero reproducción o medición; si se descartan, documentar por qué en vez de fabricar un arreglo. Incorporar la ampliación solicitada de Calendar siguiendo `GOOGLE-CALENDAR-PLAN-LUNA.md`, sin confundir sus nuevos permisos opt-in con los permisos del APK auditado ni convertirla en sync bidireccional.
