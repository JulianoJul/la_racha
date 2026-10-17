# La Racha — Auditoría actual y plan unificado

**Fecha:** 2026-10-17 · **Revisor:** Astra · **Base Git:** `16c5eef` + cambios locales en curso.

**Nota de seguimiento:** este informe conserva los diagnósticos de su revisión; varias secciones describen estados anteriores. La compilación y las comprobaciones posteriores de UI/widgets están registradas en `README.md`; los riesgos sin prueba no se consideran cerrados por compilar.

Sustituye íntegramente `AUDITORIA-PLAN-LUNA.md` y `GOOGLE-CALENDAR-PLAN-LUNA.md`. Integra los pendientes vigentes de ambos y elimina sus diagnósticos desactualizados. Los originales quedan en el historial Git; este es el único informe operativo de auditoría.

## 1. Dictamen y alcance
**El árbol local no compila actualmente.** La implementación de la nueva iteración quedó pausada a petición del usuario para hacer esta auditoría. No se debe instalar el APK anterior como si incluyera esos cambios.

La base ha mejorado desde la auditoría antigua: observación reactiva en Glance, rutas al detalle, configuración con insets/tema, respaldo de refresco, control de fechas en Hoy y política explícita de backup. Calendar ya tiene código, esquema Room v2 y navegación: su antiguo estado «plan, no implementación» dejó de ser cierto.

Persisten brechas de integración y aparecen defectos concretos en recordatorios, recuperación de operaciones Calendar, historial anterior y acciones de widgets al cruzar medianoche. **Compilar, pasar tests locales y publicar un evento local son comprobaciones distintas de validar el producto y la sincronización remota.**

### Evidencia y prioridades
- **E:** resultado de herramienta observado en esta sesión; se indica qué comando y alcance.
- **C:** conclusión demostrable por lectura de código; no implica reproducción en teléfono.
- **R:** riesgo o cobertura ausente; requiere prueba antes de atribuir un fallo concreto.
- **H:** evidencia histórica registrada, no repetida en esta revisión.
- **P0:** bloquea construir la entrega. **P1:** resolver antes de aceptar el flujo afectado. **P2:** corrección/cobertura funcional pendiente. **P3:** documentación o acabado.

Los estados «implementado parcialmente», «corregido en código» y «verificado en dispositivo» no son intercambiables. Un hallazgo heredado con arreglo de código puede conservar una prueba pendiente.

### Estado que se revisó
| Capa | Evidencia |
|---|---|
| Base publicada | Commit `16c5eef`, proyecto inicial en GitHub; `chat.txt` excluido |
| Trabajo local | Cambios de historial, frecuencias, widgets, Hoy y Calendar más esta corrección de dependencias de prueba; no hay commit |
| Compilación actual | **E:** `./gradlew test lint assembleDebug --rerun-tasks` pasó el 2026-10-17. 72 tests unitarios, 0 fallos; lint 0 errores/10 advertencias/1 hint; APK debug compilado |
| Migración Room | **E:** `./gradlew :app:connectedDebugAndroidTest` pasó 1 prueba v2→v3 en Samsung SM-A156M, Android 16; conserva hábito/marca y crea baseline diario. Se alineó Serialization 1.8.1 con Room 2.8.5 |
| Instalación | **E:** APK debug recién generado instalado con `adb -s R5CX3463Q6H install -r ...`; la pantalla estaba bloqueada al intentar abrir y tomar captura. No se revisó la UI/interacción en launcher |
| Validación previa | **H:** 47 tests y lint previo a la iteración; conservado solo como antecedente, no como estado actual |
| SQLite aislado | **E:** ejecutadas las cuatro sentencias de `MIGRATION_1_2` en SQLite 3.54.0 de Python: preservaron un hábito/una marca; columnas, afinidad y nullabilidad coinciden con schema v2; tablas Calendar vacías y `foreign_key_check` sin errores |
| Instrumentación | **E/C:** hay un test instrumentado de migración; no cubre coordinador/gateway Calendar, proveedor ni launcher |

La comprobación SQLite aislada anterior **no ejecuta Room ni Android**; la prueba instrumentada posterior sí valida la migración v2→v3 con `MigrationTestHelper` en dispositivo, pero no cubre concurrencia ni migración Calendar v1→v2. Resultado SQL aislado: `/tmp/opencode/la-racha-audit-sqlite.txt`. El primer intento del script no contemplaba campos nullable sin `notNull`; se corrigió y la ejecución final pasó.

Revisión dirigida de dominio/repositorio, UI afectada, widgets/configuración, Calendar (gateway, coordinador, DAO, Worker, preferencias, migración), recursos, pruebas y CI. Los cambios de implementación se conservaron y posteriormente se verificaron según la tabla anterior; no se accedió a eventos del teléfono. No es una nueva matriz visual completa ni una auditoría de seguridad exhaustiva.

## 2. Bloqueos y cambios locales incompletos

### B01 — Resuelto en código y compilación · E
**Ubicación:** `app/src/main/java/com/laracha/app/ui/TodayScreen.kt`; icono local `app/src/main/res/drawable/ic_calendar_today.xml`.

Se sustituyó `CalendarToday` no disponible por vector propio. El build actual pasa; instalar no demuestra todavía la navegación visual Calendar.

**Pendiente:** comprobar acceso Calendar en la pantalla Hoy tras desbloquear el teléfono.

### B02 — Resuelto · E
**Ubicación:** `app/src/test/java/com/laracha/app/domain/StreakCalculatorTest.kt`, `tracksFromEarliestCompletionAndIgnoresDaysAfterToday` (líneas 29–32).

Con creación 10 de octubre, hoy 11 y marcas 9/10/12, las fechas válidas 9 y 10 forman racha actual y mejor racha de 2. La expectativa se corrigió y la suite completa actual pasó: 72/72.

Se mantuvo el algoritmo de rachas; no se cambió para acomodar la expectativa errónea.

### H01 — P1 · C · Historial anterior a creación habilitado solo parcialmente
**Ubicaciones:** `ui/HabitDetailScreen.kt` (`canGoBack`, `CalendarDay`), `data/HabitRepository.kt` (`setCompleted`), calculadores en `domain/`.

Repositorio y celda permiten fechas anteriores; score/rachas/semana derivan un inicio anterior. Sin embargo, «Mes anterior» se detiene en el mes de la menor entre creación y primera marca existente. Sin historia antigua no puede registrarse la primera marca de un mes anterior: el usuario no puede llegar hasta ella. Dentro del mismo mes sí hay fechas habilitadas.

También falta mostrar el inicio efectivo al ampliar historia, y no hay regresión integral que borre la marca más antigua, conserve creación y compruebe las métricas de app/widgets. El cálculo de inicio está repetido en tres clases. Los guardas de creación futura siguen devolviendo cero: revisar el caso de viaje a una fecha local anterior sin borrar claves existentes.

**Aceptación:** crear hoy → navegar al mes/año anterior → marcar → recalcular → desmarcar → recuperar inicio; futuros rechazados; creación intacta. Probar con fixtures, no alterando historia personal.

### W06 — P1 · C · Acción de hoy sin fecha de render
**Ubicaciones:** `widgets/SetHabitCompletionAction.kt:17–23`, parámetros de `SingleHabitWidget.kt` y `HabitListWidget.kt`.

El callback recibe ID/valor pero escribe el `today()` del momento de ejecución. Un widget renderizado antes de medianoche puede marcar/desmarcar una fecha diferente después. La fuente reactiva y el Worker reducen antigüedad visual, pero no validan la intención de una acción retrasada.

**Aceptación:** parametrizar/verificar la fecha presentada; si dejó de ser hoy, refrescar sin escribir otra fecha. Tests con Clock controlable, pendiente/completo y callbacks repetidos/retrasados en ambos widgets. Preservar `setCompleted(value)` idempotente.

### W07 — P2 · C/R · Nueva fila semanal: target pequeño, semántica y preview pendientes
**Ubicaciones:** `widgets/SingleHabitWidget.kt` (`SingleHabitWeek`), `res/layout/widget_single_preview.xml`, `res/drawable/widget_single_preview.xml`.

El check grande ya se eliminó en el código local y la métrica positiva está implementada. Hoy es clicable en una columna de peso 1/7: a 180dp de ancho y 8dp de padding queda aproximadamente **23dp**, lejos del objetivo de toque de 48dp. La descripción del indicador informa fecha/estado, pero no nombre del hábito ni acción Completar/Desmarcar. La preview aún muestra el check grande anterior.

**Aceptación:** único check en hoy, área de toque suficiente sin invadir nombre/otros días, pasado completo gris, futuro sin acción, métrica racha/score/oculta; actualizar previews. Probar tamaños mínimos, nombre largo y fuente grande en launcher. El aspecto de esta versión aún no se ha comprobado.

### W08 — P2 · R · Solapamiento de scroll del widget aún sin demostrar corregido
**Ubicación:** `widgets/HabitListWidget.kt`, padding `end = 14.dp` en el `LazyColumn` local.

Se añadió padding al contenedor desplazable. No se midió si Glance/RemoteViews lo sitúa dentro del scroll o desplaza también la barra; no garantiza por sí mismo separación del checkbox. El checkbox pendiente conserva texto vacío y no añade nombre accesible del hábito.

**Aceptación:** 15+ hábitos, barra visible mientras se arrastra, checks fuera de su canal, última fila alcanzable, tap sin activar otro hábito y TalkBack con nombre/estado. Ajustar el margen interior de las filas si la medición lo exige.

### T04 — P1 · C/R · Tema de arranque no cubre preferencia forzada
**Ubicaciones:** recursos locales `res/values*/app_window.xml`, `ui/theme/Theme.kt`, `MainActivity.kt`, `data/AppPreferences.kt`.

El cambio local sustituye el tema claro fijo por recursos día/noche, pero la ventana inicial sigue al **sistema**. `HabitAppTheme` empieza con `ThemeMode.SYSTEM` hasta recibir DataStore. Oscuro forzado con sistema claro puede presentar todavía un frame claro. No se ha verificado splash/ventana en arranque frío ni las Activities de configuración con preferencia opuesta al sistema.

**Aceptación:** tema efectivo coherente desde ventana/splash hasta primer contenido, sin espera artificial; seis combinaciones de sistema/preferencia, entrada desde icono/widgets, frío/caliente y barras legibles. La corrección visual anterior de Calendar no demuestra resuelto este arranque.

### A03 — P2 · C/R · Interacciones de Hoy en curso
**Ubicaciones:** `ui/TodayScreen.kt` (`ScrollHint`, cabecera, `HabitEditorDialog`), `ui/LaRachaApp.kt`, `ui/SettingsScreen.kt`.

- El indicador «Desliza» se superpone en `BottomCenter`; el padding inferior no impide que una fila intermedia pase por debajo. Verificar que no cubra texto/controles y que no aparezca solo por espacio vacío de reserva.
- La fecha sin `weight` comparte fila con dos botones; posible desbordamiento en fuente grande/ancho estrecho.
- Solicitud de foco/teclado implementada en un `LaunchedEffect` exterior al contenido del diálogo; verificar disponibilidad real del campo/ventana y apertura una sola vez, incluso tras recreación.
- Calendar se añadió a Hoy, pero sigue también en Ajustes. Confirmar si «mover» implica retirar el acceso anterior; no tratar duplicación como fallo de datos.
- `HabitDetailScreen` usa `null` tanto para carga inicial como para hábito ausente y muestra «ya no está disponible» mientras carga.

**Aceptación:** indicador solo ante contenido desplazable y sin tapar filas; cabecera accesible; Nuevo hábito abre teclado, Cancelar no crea nada; detalle distingue carga/ausencia. El APK se instaló, pero falta desbloquear el dispositivo para la prueba visual/interactiva.

## 3. Calendar: auditoría de la implementación existente

### G01 — P1 · C · Republicar el mismo recordatorio puede eliminarlo
**Ubicación:** `calendar/CalendarGateway.kt`, `replaceReminder` (líneas 146–155).

Lee recordatorios en `existing`, borra los considerados gestionados y calcula `alreadyPresent` sobre la lista **anterior al borrado**. Si el recordatorio anterior era de 10 minutos y se publica de nuevo con 10, lo borra, cree que sigue presente y no lo inserta. Renombrar un hábito exportado puede recorrer este camino.

La propiedad del recordatorio se infiere por minutos/método, no por identidad individual; debe definirse cómo preservar recordatorios externos coincidentes. `HAS_ALARM=0` al retirar el propio tampoco debe ignorar otros que permanezcan.

**Aceptación:** republicar sin cambios conserva exactamente el recordatorio gestionado; cambiarlo o quitarlo no duplica ni elimina recordatorios ajenos; capacidad/máximo del calendario respetados. Probar gateway con provider controlado y después destino de QA.

### G02 — P1 · C · Recuperación IN_FLIGHT confunde existencia con aplicación
**Ubicaciones:** `calendar/CalendarExportCoordinator.kt:93–126`, `CalendarGateway.kt:115–124`.

Antes de llamar al provider se persiste `IN_FLIGHT`. En recuperación, encontrar el evento por token basta para guardar `publishedRevision = operation.revision` y terminar. Si se interrumpe una **actualización antes de ejecutarla**, el evento antiguo ya existe: se da por aplicada una revisión que no llegó al provider. Si se interrumpe entre evento y recordatorio, también puede quedar incompleta la operación.

**Aceptación:** recuperar el ID no equivale a finalizar. Reaplicar o comprobar idempotentemente todos los campos gestionados y recordatorios de la revisión vigente, sin duplicar el evento. Inyectar caídas antes de provider, después del evento, durante recordatorio y antes del commit Room; cubrir inserción y actualización. Room/provider siguen sin compartir transacción.

### G03 — P1 · C/R · Revisiones y sustitución no serializan la intención completa
**Ubicaciones:** `CalendarExportCoordinator.kt` (`requestPublication`, `requestDisable`, `drainMutex`); `data/CalendarDao.kt` (`saveDesired`, `removeSupersededOperations`, `finish`).

El mutex protege `drain`, pero las lecturas de revisión/vínculo y `saveDesired` ocurren antes. Dos solicitudes pueden leer la misma revisión y, en primera publicación, generar tokens distintos. `saveDesired` borra operaciones anteriores, incluso una `IN_FLIGHT`; si esa operación acaba de crear un evento y `finish` ya no encuentra la fila, no guarda su vínculo. `finish` sí rechaza revisiones obsoletas: es una protección útil, pero no resuelve el efecto externo que ya ocurrió.

**Aceptación:** serializar/asignar revisiones en la frontera durable de intención y conservar información para reconciliar efectos en vuelo. Tests con barreras: publicar/publicar, editar/desactivar, publicar/archivar y publicar/borrar; ninguna serie tardía queda sin seguimiento. El interleaving real debe reproducirse antes de dar por ocurrido en dispositivo.

### G04 — P1 · C/R · Habilitación global no gobierna al coordinador/Worker
**Ubicaciones:** `CalendarPreferences.kt`, `CalendarIntegrationScreen.kt:222–244`, `CalendarExportWorker.kt`, construcción en `HabitApplication.kt`.

La preferencia global solo controla la UI. Coordinador y Worker no la consultan. Desactivar recorre vínculos y llama a `requestDisable` uno por uno; cada llamada drena **toda** la cola, pudiendo publicar otros hábitos antes de invalidarlos. La preferencia se apaga al final y el proceso depende del scope de pantalla.

**Aceptación:** bloquear nuevas publicaciones al comenzar la desactivación, persistir de forma durable la intención global y por serie, recuperar si se cancela/muere la UI. Permitir las limpiezas explícitamente autorizadas aunque la publicación global esté apagada. Conservar/Eliminar/Cancelar no deben confundirse con revocar permisos ni borrar historial.

### G05 — P1 · C · Mensaje de éxito local aunque la publicación esté bloqueada
**Ubicaciones:** `CalendarIntegrationScreen.kt:186–191`, `CalendarExportCoordinator.kt:137–168`.

`requestPublication` devuelve sin resultado explícito. `drain` convierte errores de permiso/provider/identidad en operaciones `BLOCKED` sin lanzarlos, y la UI muestra «La serie se publicó…». La tarjeta puede mostrar a la vez que requiere atención. La frase evita afirmar sincronización remota, pero tampoco está justificado afirmar publicación **local** en este caso.

**Aceptación:** devolver/observar resultados durables: solicitado, publicado local, bloqueado o incierto. Un error de provider no debe producir mensaje de éxito. La subida a Google siempre requiere evidencia externa independiente.

### G06 — P1 · C/R · Lectura nula no distinguida de evento ausente
**Ubicación:** `CalendarGateway.kt`, consultas `listWritableGoogleCalendars`, `findOwnedEvent` y `verifyOwned`.

Un cursor nulo se transforma en lista vacía/false. En reconciliación, eso puede significar `Missing` sin haber obtenido una lectura válida. Una operación sin ID conocido puede entonces intentar insertar. El comportamiento de un provider concreto ante fallo necesita reproducción; el colapso de estados está en código.

**Aceptación:** distinguir resultado vacío confirmado de error de lectura; nunca insertar/recrear a partir de una lectura fallida. Mantener pausa ante identidad ambigua y ofrecer recuperación explícita para bloqueos genéricos, no solo evento faltante.

### G07 — P2 · C · Reintentos/cancelación incompletos
**Ubicaciones:** `CalendarExportCoordinator.kt:161–171`, `CalendarExportWorker.kt:15–21`, `CalendarDao.kt:68–78`.

Errores genéricos pasan a `BLOCKED`; `markRetry` no tiene llamadas. El botón de reintento solo desbloquea errores cuyo texto coincide con dos prefijos. Fallos transitorios pueden quedar inmóviles, mientras el Worker informa éxito al drenar sin excepciones. Además su `catch (Exception)` captura `CancellationException`, a diferencia del coordinador.

**Aceptación:** propagar cancelación, clasificar fallos estructuradamente y separar reintento seguro de reconciliación incierta. Backoff para errores transitorios conocidos; permiso/destino requieren intervención. No convertir toda incertidumbre en reintento ciego ni en bloqueo irreversible. Revisar acumulación de trabajos con `APPEND_OR_REPLACE`.

### G08 — P2 · C/R · Editor Calendar: validación y estado frágiles
**Ubicación:** `ui/CalendarIntegrationScreen.kt`, `CalendarScheduleCard` y recorrido `habits.forEach`.

- La validación ejecuta `duration.toInt() in 1..1440` pero descarta su booleano y termina con `true`: duración 0/1441 puede habilitar Publicar. El coordinador sí rechaza ese rango; no es aceptación de duración inválida por el provider.
- Campos usan `remember(habitName, initial...)`, no ID estable/estado guardable; una recreación pierde edición. El recorrido no usa claves por hábito: reordenación/eliminación y nombres iguales requieren prueba para evitar estado desplazado.
- No hay estado Guardando que impida doble envío; refuerza la necesidad de G03 aunque la UI se proteja.
- Recordatorios y acciones en filas fijas pueden desbordar con fuente grande. Falta resumen inequívoco del destino de cada serie existente antes de publicar; el selector global es solo para nuevas exportaciones.
- El plan anterior proponía entrada desde editor/detalle; hoy la planificación está centralizada en Calendar. Resolver la ubicación deseada antes de duplicar formularios.

**Aceptación:** validación real, edición por ID persistente tras recrear, guardado visible/reintentable, calendario/zona/serie completa explícitos y controles alcanzables con IME/fuente grande. No pedir permisos al completar hábitos.

### G09 — P1 para aceptación · R · Provider real y migración Room sin cobertura suficiente
**Ubicaciones:** `data/CalendarMigration.kt`, `app/schemas/`, `app/src/test/`, `app/build.gradle.kts`, CI.

Existen migración v1→v2, snapshots y tablas durables sin FK para conservar limpieza después de borrar hábitos. La prueba SQL aislada de esta auditoría es favorable, pero falta `MigrationTestHelper`/Room real y gateway/coordinador con fallos inyectados. Los dos tests Calendar existentes cubren solo la primera hora resuelta con DST, no expansión de recurrencias ni sincronización Google.

**Aceptación:** migración y reapertura de DB real, tombstones, idempotencia y matrices G01–G08; después calendario de QA: marcador conservado tras sync, recurrencia y recordatorios, offline→reconexión, cambios externos y evidencia en web/u otro dispositivo. No crear eventos personales como fixture.

### Seguimiento de fixes Calendar — 2026-10-17
- **G01, parcial:** la decisión de recordatorios ya calcula primero qué filas borrar/insertar; republicar la misma alarma administrada no la elimina. Las filas externas coincidentes no se duplican. El provider no da identidad por recordatorio: si una fila externa coincide exactamente con el valor previamente administrado, el código no puede distinguirlas con seguridad; sigue pendiente verificar la política con provider de QA.
- **G02, parcial:** recuperación `IN_FLIGHT` de `UPSERT` vuelve a aplicar la planificación completa mediante `publish` al marcador encontrado antes de confirmar revisión. Tests fake cubren orden reconcile→publish. No hay prueba de corte real entre operaciones Calendar/Room.
- **G03, parcial:** asignación y drenaje de intenciones se serializan dentro de una instancia del coordinador; `saveDesired` conserva operaciones `IN_FLIGHT` para reconciliación. No cubre dos instancias/procesos coordinadores ni hay prueba de barreras contra Room real.
- **G04, pendiente:** no se cambió `CalendarPreferences` ni el flujo de preferencia del usuario. La desactivación global sigue sin intención durable/bloqueo en la frontera del coordinador; requiere diseño y prueba dedicada antes de cerrarse.
- **G05, parcial:** publicar devuelve `PUBLISHED_LOCAL`, `PENDING` o `BLOCKED`; la pantalla ya no afirma éxito local ante bloqueo. Sigue sin acreditar sincronización remota.
- **G06, corregido en código:** cursor nulo en lecturas de calendario/evento/recordatorios/verificación ahora es error de lectura, no lista vacía ni `Missing`. Sin provider controlado, falta verificación Android.
- **G07, parcial:** Worker preserva `CancellationException`; errores de proveedor inciertos continúan bloqueados para evitar repetición ciega. La clasificación estructurada y backoff selectivo de errores transitorios siguen pendientes.
- **G08, parcial:** validación de duración se propaga realmente; campos quedan asociados al ID y son guardables, y el botón publica muestra/impone estado Guardando. No se ha probado recreación/fuente grande en dispositivo.

Pruebas locales añadidas para reglas puras/fake: recordatorio sin cambio, sustitución conservando externos, duración y recuperación `IN_FLIGHT`. No validan consultas/cursor Android, SQLite Room, concurrencia interproceso, provider ni launcher.

## 4. Seguimiento de todos los hallazgos anteriores
Los IDs antiguos se conservan aquí para no perder pendientes; no se conservan sus conclusiones obsoletas como fallos activos.

| ID anterior | Revisión actual y pendiente heredado |
|---|---|
| W01 · snapshots Glance | Corregido el patrón en código: ambos widgets observan configuración/historial dentro de `provideContent`. Faltan regresiones de sesión activa/fría y cuatro instancias |
| W02 · nombre sin detalle | ID/Intent, `onNewIntent` y navegación existen; **H** revisión de una fila en Samsung. Pendientes dos IDs, frío/caliente e ID borrado |
| W03 · tamaño fijo | `SizeMode.Responsive` implementado; diseño vuelve a cambiar localmente. Continúa en W07: tamaños mínimos/fuente grande/Pixel |
| W04 · reconfiguración | Providers declaran `reconfigurable`; vacío ofrece configuración. Probar cambiar/cancelar por instancia en launchers soportados |
| W05 · previews genéricas | Previews/etiquetas separadas implementadas. Individual desactualizado con el diseño local: W07; fallback API 26–30 pendiente |
| T01 · fondo mixto configuración | `Surface` raíz y recursos nocturnos presentes en ambas configuraciones. Matriz completa de tema aún pendiente; arranque forzado en T04 |
| T02 · notch/insets | `safeDrawing` + IME en configuración y Scaffold en Calendar. Falta paisaje, navegación por botones, fuente grande y ventana pequeña |
| T03 · barras ilegibles | `HabitTheme` actualiza apariencia de barras según tema efectivo. Falta validar seis combinaciones/transiciones, T04 |
| C01 · edición/guardado/ID | `rememberSaveable`, Flow, estados de carga/error/guardado y validación de provider ya existen. Probar recreación durante guardado: write puede persistir antes de cancelarse update/resultado de Activity |
| C02 · Todos confuso/scroll | Selección solo en Elegidos, modos separados y orden dentro de LazyColumn con semántica. Fuente 200 %, cinco elegidos y botones inferiores pendientes |
| R01 · Hoy congelado | MainActivity revisa fecha cada 15s y Hoy verifica antes de escribir. Widgets emiten al siguiente día local. Cambio de zona/hora mientras se espera ese instante y acción vieja siguen pendientes, W06 |
| R02 · sin respaldo | Worker único periódico de 12h programado. Faltan eventos temporales específicos, política al no quedar widgets y pruebas reinicio/Doze; no prometer medianoche exacta |
| R03 · errores/refresco en UI | Repositorio invalida tras commit en `NonCancellable`; updater conserva cancelación y separa errores por tipo. Falta inyección de fallo/cancelación; Worker widget devuelve éxito aun si updater registró fallos |
| R04 · limpieza receiver | Se añadió `goAsync`/`finally finish`; verificar cooperación con lifecycle Glance, excepciones y eliminación inmediata con proceso terminado. No afirmar limpieza durable probada |
| A01 · Atrás/edición | BackHandler e ID de edición guardable implementados. Falta recreación, Atrás predictivo y conservación de ruta al salir/volver de Ajustes |
| A02 · accesibilidad | Selectores app ahora 48dp y métricas detalle apiladas. Siguen controles widget sin contexto/target suficiente: W07/W08/A03; TalkBack completo pendiente |
| D01 · fechas | La regla antigua «rechazar antes de creación» fue sustituida por petición del usuario. Rechazo de futuro existe; historia anterior en curso, H01/B02/W06 |
| D02 · orden restaurado | DAO restaura al final y valida reordenación completa en transacción; test con DAO falso existe. Falta SQLite/concurrencia y comprobación de listas seleccionadas independientes |
| D03 · CPU en UI | Cálculos Flow usan `Dispatchers.Default`. Persisten consultas de todo el historial y recorridos por día; medir volumen multianual, especialmente con historia ampliada |
| P01 · backup implícito | `allowBackup=false` y reglas nube/transferencia excluyen DB, archivos y preferencias. Falta comprobar comportamiento OEM/restore; no se detectó subida de backup |
| P02 · permisos transitivos | Documentados; Calendar READ/WRITE ahora son opt-in solicitado. Verificar manifiestos debug/release; sin Internet/cuentas/notificaciones/alarma exacta propias |
| Q01 · integración sin tests | Parcial: existe una prueba instrumentada de migración v2→v3 ejecutada en SM-A156M. Sigue pendiente validar más Room/SQLite, DataStore real, sesiones Glance y concurrencia. CI solo ejecuta test/lint/build |
| Q02 · acabado/documentación | Icono/paleta y seis documentos centrales actualizados; B01 bloquea el árbol local. `TASKS.md` conserva afirmaciones antiguas sobre esquema v1, navegación Calendar y ausencia de pruebas en teléfono; checksum wrapper pendiente de valorar |

### Hitos Calendar heredados
| Hito anterior | Estado real y trabajo restante |
|---|---|
| GC1 · dominio/persistencia | Entidades/DAO/migración v2 existen y SQL aislado comprobado; faltan Room real y pruebas de estado/revisión: G03/G09 |
| GC2 · permisos/destino | Flujo opt-in y selector Google implementados; faltan denegación parcial/permanente, revocación, retorno desde Ajustes Android y editor: G04/G08/G09 |
| GC3 · series/recuperación | Gateway y Worker existen; defectos G01/G02/G03/G06/G07 impiden considerarlo cerrado |
| GC4 · ciclo de vida | Renombrar y conservar/eliminar al archivar/borrar están conectados; cubrir carreras, restauración sin republicación automática y huérfanos: G03/G04/G09 |
| GC5 · extremo a extremo | Pendiente: cuenta de QA, provider, sincronización web/otro dispositivo y aceptación completa. Tema de pantalla con integración apagada no lo sustituye |

## 5. Contrato Calendar que se conserva del plan integrado
1. **Room → Calendar Provider → sincronización Android/Google**. Exportación unidireccional, opcional, apagada inicialmente y por hábito. No exportar marcas/rachas/score ni importar cambios de dominio.
2. Calendario Google existente (`com.google`), visible, escribible (`CAL_ACCESS_CONTRIBUTOR` o superior), con sync habilitada. Sin elección silenciosa del primero, creación de cuentas/calendarios ni fallback a calendario local.
3. Permisos READ/WRITE solo al activar. Sin `GET_ACCOUNTS`, `INTERNET`, notificaciones propias, alarmas exactas, backend u OAuth propio. Denegar no bloquea hábitos/widgets.
4. Plan: fecha inicial, hora de pared, zona explícita, duración 1–1440 minutos y recordatorio opcional (-1/0/10/30). Actualmente serie diaria con `RRULE=FREQ=DAILY`, `DURATION` RFC 5545, `DTSTART` como instante y `ALL_DAY=0`. No cientos de eventos individuales ni campos reservados a sync adapters.
5. Zona fija de la serie al planificar; viajar no la cambia silenciosamente. Hora inexistente se desplaza por salto DST y hora ambigua elige primer offset en el resolvedor actual. Verificar la expansión real del provider y duración cruzando medianoche; el historial sigue en fechas locales ISO.
6. Destino predeterminado afecta nuevas exportaciones; cada vínculo conserva calendario/cuenta propios. Mostrar destino, recurrencia y zona antes de publicar. No mover series existentes en silencio.
7. Verificar propiedad por token/cuenta/destino, nunca por coincidencia de nombre/hora o solo ID numérico. Leer metadatos mínimos y eventos propios, no la agenda completa. Identidad ambigua detiene mutaciones.
8. Intención/operación durable antes del efecto externo, revisión vigente y recuperación entre almacenes. Conservar referencias de limpieza al borrar hábito y de series desvinculadas para reactivar sin duplicar.
9. Conservar/Eliminar/Cancelar al desactivar/archivar/borrar; explicar que se opera la **serie completa**, incluidas apariciones pasadas. No implementar una falsa edición «solo desde ahora» sobre toda la serie.
10. Serie eliminada externamente: estado recuperable y recreación confirmada; no resucitar automáticamente. Campos gestionados pueden sobrescribir ediciones externas al republicar, pero nunca alterar el historial de hábitos.
11. Estados distinguibles: desactivado, pendiente, publicado local, sin permiso/destino, faltante, ambiguo/error. Reintentos seguros con backoff; permiso/cuenta requieren intervención. Un insert local no significa sincronización remota ni garantiza que suene un recordatorio.
12. Backup excluye IDs locales; desinstalar puede dejar eventos externos. Probar cuenta eliminada, destino solo lectura, sincronización pausada, offline y reinstalación sin reutilizar ciegamente IDs.

**Ampliación nueva:** hábitos en días fijos, cada N días y cuotas semanales están acordados, no implementados. La regla diaria del plan antiguo no los prohíbe. Cerrar cálculo/ediciones en `SCORE.md`; después decidir traducción a Calendar. Una cuota sin días fijos no define automáticamente fechas exportables.

## 6. Plan de ejecución actualizado
| Orden | Entrega | Condición de salida |
|---|---|---|
| 1 | Build y tests | B01/B02 resueltos; `test lint assembleDebug --rerun-tasks` y migración v2→v3 instrumentada pasan |
| 2 | Completar historial y widget individual: H01/W06/W07 | Tests de fechas/inicio, callback fechado, preview y control único; prueba real de acción y navegación |
| 3 | Tema, scroll y Hoy: T04/W08/A03 | Frío/caliente, sistema/preferencia opuestos, IME, fuente grande, barra sobre 15+ filas; configuración y Atrás regresados |
| 4 | Fiabilidad Calendar: G01–G07 | Pruebas de recordatorios, interrupciones y carreras con gateway controlado; estados/resultados honestos |
| 5 | Persistencia/editor y QA Calendar: G08/G09 | Room migración/reapertura, permisos/provider real, limpieza durable y sincronización remota comprobada |
| 6 | Frecuencias | Decisiones de `SCORE.md` cerradas antes del algoritmo; tests primero, migración preservando diarios, UI/widgets y después recurrencias Calendar |
| 7 | Cierre heredado | Q01, matrices de launcher/accesibilidad/sistema/rendimiento; README/TASKS actualizados con lo realmente probado |

Los pasos 4–5 deben completarse antes de presentar Calendar como fiable. No esperar a las frecuencias para corregir defectos de exportación ya existentes. Implementar incrementos acotados, no una reescritura del stack. Esta auditoría no ejecuta esos arreglos ni asigna automáticamente trabajo a otro agente.

## 7. Matriz de aceptación pendiente
| Área | Escenarios que no deben perderse al ejecutar el plan |
|---|---|
| Dominio | Hitos diarios, hoy pendiente/desmarcado, 13 faltas reducen a la mitad, historial alterno/largo, antes de creación y eliminación de primera marca |
| Fechas | Medianoche abierta/callback retrasado, DST ambos sentidos, febrero bisiesto, fin de semana/año, cambio de hora/zona sin desplazar claves |
| Room | Migración v1→v2, FK/cascade, reapertura, true/true concurrente y true/false/true, restauración/orden y tombstones Calendar |
| Widgets | Dos individuales + dos listas independientes, sesiones activas/frías, configurar/reconfigurar/cancelar, renombrar/archivar/borrar/corregir; acción sin Activity con proceso previamente terminado |
| Lista/resize | 15+ hábitos, nuevo en Todos, límite 1–5 y orden de Elegidos, barra sin cubrir checks, 2×2/4×2/4×3/4×4 según host |
| UI/accesibilidad | Seis combinaciones tema, arranque sin destello, notch/IME/paisaje, fuente 100/150/200 %, nombres largos, TalkBack y targets sin solapamiento |
| Sistema | Reinicio, Doze/ahorro, sin conexión, eliminación de widget y limpieza por instancia; distinguir proceso terminado de Forzar detención |
| Calendar básico | No activar/denegar: cero consultas/eventos; destino vacío/solo lectura/cuenta retirada; selección explícita; dos hábitos de igual nombre independientes |
| Calendar consistencia | Doble publicar, cambios rápidos, desactivar durante creación, caídas entre provider/Room, recreación confirmada, marcador ambiguo y lecturas fallidas |
| Calendar edición | Mismo recordatorio/cambio/quitar, capacidad/límites, edición/borrado externo, conservar/eliminar/cancelar, archivo/restauración y hábito eliminado |
| Calendar remoto | Horario/DST y duración, marcador tras sync, offline→online, sync pausada, web/otro dispositivo; no inferir éxito remoto del local |
| Privacidad/build | Manifiestos debug/release y permisos transitivos, backup/transferencia OEM, CI y dependencia compatible; `chat.txt` sigue excluido |

Usar fixtures y bases separadas para pruebas que escriben/borran datos, cambian fecha o necesitan numerosos hábitos. La conexión del teléfono personal no autoriza alterar sus marcas/eventos o su configuración global como parte de una auditoría documental.

## 8. Comandos y registro de cierre
Verificación final ejecutada el 2026-10-17: `./gradlew test lint assembleDebug --rerun-tasks` → **BUILD SUCCESSFUL** (72 tests unitarios; lint 0 errores, 10 advertencias, 1 hint). `./gradlew :app:connectedDebugAndroidTest` → **BUILD SUCCESSFUL**, 1 migración Room v2→v3 en Samsung SM-A156M Android 16. APK instalado, pero la UI no se pudo inspeccionar porque el teléfono estaba bloqueado. Calendar real, launcher y recorridos visuales siguen pendientes.

Al reparar y entregar código:
```bash
./gradlew test lint assembleDebug
adb devices -l
```

Después de añadir/configurar pruebas instrumentadas (actualmente ausentes):
```bash
./gradlew :app:connectedDebugAndroidTest
```

Solo con APK recién validado y actualización autorizada:
```bash
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Cada cierre registra ID, commit/archivos, prueba/comando, resultado, dispositivo/launcher si procede y límites. La auditoría se actualiza por evidencia, no marcando tareas como hechas solo por existir código. La consulta SQL de esta revisión puede repetirse con el bloque siguiente; sigue sin sustituir Room/Android:

```bash
python3 - <<'PY'
import json, re, sqlite3
from pathlib import Path
base = Path('app/schemas/com.laracha.app.data.HabitDatabase')
v1 = json.loads((base / '1.json').read_text())['database']
v2 = json.loads((base / '2.json').read_text())['database']
db = sqlite3.connect(':memory:')
db.execute('PRAGMA foreign_keys=ON')
for entity in v1['entities']:
    db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    for index in entity.get('indices', []):
        db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
db.execute("INSERT INTO habits VALUES ('audit','Prueba local','x','#437A54','2026-10-16',NULL,0)")
db.execute("INSERT INTO completions VALUES ('audit','2026-10-16')")
source = Path('app/src/main/java/com/laracha/app/data/CalendarMigration.kt').read_text()
statements = [a or b for a, b in re.findall(r'db\.execSQL\((?:"""(.*?)"""|"([^"\n]*)")\)', source, re.S)]
assert len(statements) == 4
for statement in statements:
    db.execute(statement)
for entity in v2['entities']:
    actual = {r[1]: (r[2].upper(), bool(r[3])) for r in db.execute('PRAGMA table_info(`' + entity['tableName'] + '`)')}
    expected = {f['columnName']: (f['affinity'], f.get('notNull', False)) for f in entity['fields']}
    assert actual == expected, entity['tableName']
assert db.execute('SELECT name FROM habits').fetchall() == [('Prueba local',)]
assert db.execute('SELECT habitId,localDate FROM completions').fetchall() == [('audit','2026-10-16')]
assert db.execute('PRAGMA foreign_key_check').fetchall() == []
for table in ('habit_calendar_schedules', 'calendar_event_bindings', 'calendar_export_operations'):
    assert db.execute(f'SELECT count(*) FROM {table}').fetchone()[0] == 0
print('PASS: SQL aislado; no prueba Room/Android')
PY
```

## Referencias
Contratos vigentes: [PRODUCT.md](PRODUCT.md), [SCORE.md](SCORE.md), [WIDGETS.md](WIDGETS.md), [DESIGN.md](DESIGN.md) y [AGENTS.md](AGENTS.md). La auditoría identifica brechas, no redefine silenciosamente el producto.

Fuentes oficiales para implementar/verificar (no reconsultadas en esta revisión): [Glance lifecycle](https://developer.android.com/reference/kotlin/androidx/glance/appwidget/GlanceAppWidget), [configuración](https://developer.android.com/develop/ui/compose/glance/configuration), [tamaños](https://developer.android.com/develop/ui/compose/glance/app-widget-size), [insets](https://developer.android.com/develop/ui/compose/system/insets), [Room testing](https://developer.android.com/training/data-storage/room/testing-db), [backup](https://developer.android.com/identity/data/autobackup), [Calendar Provider](https://developer.android.com/identity/providers/calendar-provider), [Events](https://developer.android.com/reference/android/provider/CalendarContract.Events), [Calendars](https://developer.android.com/reference/android/provider/CalendarContract.Calendars), [permisos](https://developer.android.com/training/permissions/requesting).
