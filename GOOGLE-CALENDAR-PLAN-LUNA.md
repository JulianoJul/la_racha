# Google Calendar — ampliación del plan para Luna

Fecha: 2026-10-17. Petición del usuario: «quiero integración a Google Calendar».

**Estado: plan, no implementación.** Se mantiene el encargo de que Sol documenta y Luna ejecuta. No se han añadido permisos, accedido a calendarios del teléfono ni creado eventos. Esta ampliación complementa `AUDITORIA-PLAN-LUNA.md`; no sustituye las reparaciones de widgets, temas e insets.

## 1. Alcance de la primera entrega

Integración opcional y unidireccional:

**Room → Calendar Provider de Android → sincronización de la cuenta Google → Google Calendar.**

- La app publica planificación, no historial de completados.
- Un hábito exportado tiene una serie recurrente diaria gestionada por la app, no cientos de eventos individuales.
- Configuración por hábito: activar exportación, fecha inicial, hora, duración y recordatorio opcional.
- Destino: calendario de Google existente, sincronizado y con permiso de escritura, elegido expresamente por el usuario.
- Nombre y horario editados en la app actualizan la serie vinculada. Completar/desmarcar o corregir historia **no escribe en Calendar**.
- Cambios externos en Calendar nunca crean/modifican hábitos, completados, score o rachas. Leer la identidad/existencia de una serie para actualizarla con seguridad no es sincronización inversa del dominio.
- Los hábitos siguen siendo diarios sí/no. El horario de agenda no cambia cuándo se cierra un día ni añade objetivos semanales.
- Sin backend, OAuth propio, SDK de Google ni permiso de Internet en nuestra app. La cuenta y su sincronización son responsabilidad del sistema/Google Calendar.

### Límites reales de esta vía

1. Escribir correctamente en el provider local **no demuestra** que el evento ya haya llegado a Google. La sincronización depende de cuenta, conexión, configuración y restricciones del sistema; no prometer inmediatez.
2. No basta con tener instalada la aplicación Google Calendar. Debe existir un calendario de cuenta Google disponible en el provider y con sincronización habilitada.
3. No crear un calendario local y presentarlo como Google Calendar: los calendarios locales no se suben a Google.
4. La creación de calendarios corresponde principalmente a sync adapters. La primera entrega **no crea automáticamente un calendario remoto «Hábitos»**. El usuario puede crearlo previamente en Google Calendar y seleccionarlo cuando aparezca en Android.
5. Un simple Intent de insertar un evento puede funcionar sin permisos, pero no cubre la gestión automática posterior y fiable de la serie. No ofrecerlo como equivalente a esta integración.

## 2. Cambios explícitos respecto del alcance original

`PRODUCT.md` excluía sync/notificaciones y prometía datos locales. Esta petición autoriza planificar **exportación opcional de agenda**, no sincronización de la base de datos ni un sistema propio de notificaciones.

Antes de implementar, Luna debe actualizar coherentemente `PRODUCT.md`, `README.md` y `TASKS.md`:

- Explicar que el núcleo permanece local/offline, pero los nombres y horarios de hábitos seleccionados pueden enviarse a Google al activar la integración.
- Añadir los dos permisos de calendario a la allowlist de P02 de la auditoría, solicitados únicamente al activar la función.
- No considerar autorizados contactos, cuentas del dispositivo mediante `GET_ACCOUNTS`, alarmas exactas, notificaciones propias, backend ni Internet.
- Mantener la política de backup P01 separada: permitir exportación explícita no autoriza copiar toda la DB en nube.

No cambia el stack Kotlin/Compose/Room/Glance. No hace falta un ADR de cambio de stack.

## 3. Experiencia de usuario

### Ajustes → Google Calendar

1. Explicación previa: «Exporta los horarios que elijas a tu calendario. Los completados, rachas y puntuación no se exportan. Los cambios de Calendar no se importan».
2. Botón **Activar integración**, desactivada inicialmente. No solicitar permisos al iniciar la app ni desde un widget de completar.
3. Solicitar `READ_CALENDAR` y `WRITE_CALENDAR` con el flujo de permisos de Android. Leer es necesario para elegir destino/verificar las series; escribir, para gestionarlas. Denegar o revocar no bloquea hábitos ni widgets.
4. Selector real de calendarios: nombre, cuenta y color; no elegir silenciosamente el primero. Filtrar cuentas Google, acceso de escritura suficiente (`CAL_ACCESS_CONTRIBUTOR` o superior) y `SYNC_EVENTS`; revisar visibilidad y capacidades de recordatorios. La presencia de la bandera de sync no garantiza una subida remota completada.
5. Sin destinos válidos: explicar cómo configurar/sincronizar Google en Android y ofrecer **Volver a comprobar**. No crear cuentas ni modificar ajustes de sincronización del usuario.
6. Destino predeterminado para **nuevas exportaciones**. Cambiarlo no mueve ni duplica series ya publicadas: cada vínculo conserva su calendario explícito. El movimiento masivo entre calendarios queda fuera de esta primera entrega.
7. Estado por hábito: Desactivado, Pendiente, Publicado en calendario del dispositivo, Sin permiso, Calendario no disponible, Evento no encontrado o Error con reintento. No mostrar «Sincronizado con Google» a partir de un insert local.
8. Botones **Reintentar pendientes** y **Desactivar integración**, sin polling constante ni lecturas de toda la agenda.

### Crear/editar hábito → Planificar en Calendar

- Switch por hábito, inicialmente apagado; ningún hábito se exporta solo por conceder el permiso.
- Fecha inicial y hora local, duración positiva (propuesta: 30 minutos por defecto, editable), zona horaria visible y recordatorio: Ninguno / A la hora / 10 min antes / 30 min antes.
- Por defecto, proponer la siguiente ocurrencia: hoy si todavía no pasó la hora elegida, mañana si ya pasó. El usuario confirma el resumen antes de publicar.
- Una hora diaria; sin selector semanal, asistentes, invitados, videollamadas, ubicaciones ni múltiples franjas por hábito en esta fase.
- Mostrar calendario de destino, zona, repetición diaria y qué datos saldrán del dispositivo antes de confirmar.
- Usar los arreglos de tema/insets de T01–T03; pantalla desplazable y accesible con fuente grande/IME. No repetir la mezcla de fondos claros y oscuros.

### Ediciones externas y acciones destructivas

- Avisar: «Edita la planificación desde La Racha; los cambios hechos en Calendar no se importan y los campos gestionados pueden sobrescribirse al publicar un cambio desde la app».
- Si la serie desaparece, mostrar **Evento no encontrado** y ofrecer recrearlo con confirmación. No resucitarlo automáticamente en un bucle de reintentos.
- Desactivar exportación, archivar o eliminar un hábito con vínculo debe ofrecer explícitamente **Conservar la serie sin gestionarla** o **Eliminar la serie creada por La Racha**, con opción de cancelar. No borrar eventos ajenos.
- En esta primera fase se opera la serie completa: editarla puede afectar apariciones anteriores; eliminarla borra también sus apariciones pasadas en Calendar. Explicarlo antes de confirmar. El historial de la app no cambia.
- Mantener una referencia de serie desvinculada si se conserva, para no duplicarla al reactivar: ofrecer reutilizarla si su identidad puede verificarse. No asumir que un nuevo export puede insertar a ciegas.
- La conservación de apariciones pasadas mediante división «desde esta fecha en adelante» es una ampliación distinta; no simularla con una actualización de toda la serie.
- Al desactivar globalmente, detener nuevos trabajos de publicación y preguntar si se conservan o eliminan las series gestionadas. Si falta permiso para limpiar, informar y conservar el registro pendiente; no afirmar que desaparecieron de Google.
- Desinstalar la app no ejecuta limpieza remota garantizada: advertir que los eventos pueden permanecer.

## 4. Datos y arquitectura propuesta

Verificar el esquema vigente al empezar. Actualmente `HabitDatabase` es v1 y `HabitEntity` no tiene horario. Añadir migración explícita a la siguiente versión real, sin migración destructiva.

Separar tres conceptos en Room (nombres orientativos):

| Concepto | Información |
|---|---|
| `HabitCalendarSchedule` | `habitId`, exportación deseada, fecha inicial local, hora, duración, `zoneId`, recordatorio y revisión del estado deseado. |
| `CalendarEventBinding` | ID local del calendario, identidad de cuenta/tipo, ID/URI de serie, token de propiedad, revisión publicada y estado gestionado/desvinculado. |
| `CalendarExportOperation` | Operación durable, token único, revisión, tipo, destino/serie y estado/error. Debe poder conservar información de limpieza aunque se elimine el hábito. |

La preferencia global de habilitación/destino puede vivir en DataStore; la intención de publicación y sus operaciones deben tener una fuente durable coherente. Evitar dos flags contradictorios de habilitación sin una precedencia definida.

Componentes sugeridos, no existentes todavía:

- `calendar/CalendarGateway.kt`: interfaz de lectura de destinos y gestión de series propias, testeable.
- `calendar/AndroidCalendarGateway.kt`: `ContentResolver`/`CalendarContract`, I/O fuera del hilo principal, proyecciones mínimas y cursores cerrados.
- `calendar/CalendarExportCoordinator.kt`: serialización, revisión vigente, reconciliación, identidad y permisos.
- `calendar/CalendarExportWorker.kt`: drenar operaciones persistidas y reintentar fallos transitorios con backoff; trabajo único y sin dependencia de un scope de pantalla.
- `ui/CalendarSettingsScreen.kt` y sección del editor/detalle para horario y estado.
- Nuevos DAO/entities/migración y tests en `data/`, `domain/`, `calendar/` y `androidTest/` según el tipo.

### Consistencia: Room y Calendar no comparten transacción

1. Guardar horario/intención y operación pendiente en una transacción Room.
2. Ejecutar una operación por hábito/serie con la revisión más reciente; coalescer ediciones rápidas. Una desactivación o borrado invalida una creación antigua pendiente.
3. Verificar permisos, destino y propiedad antes de cada acceso externo, incluyendo la posibilidad de `SecurityException` si se revocan entre comprobación y operación.
4. Aplicar evento y recordatorio mediante un batch del provider cuando proceda. El batch no vuelve atómica la escritura posterior del vínculo en Room.
5. Persistir el ID y la revisión publicada al completar. Si cambia la revisión mientras se publica, dejar pendiente la nueva; no marcarla aplicada usando un resultado viejo.
6. Probar el fallo crítico **insert externo exitoso → proceso muere antes de guardar el ID**. El reintento necesita localizar el evento por un marcador estable de operación/propiedad, no por título/hora. Evaluar campos normales admitidos por el provider, como descripción con marcador y URI de app donde corresponda, y comprobar qué conserva la sincronización Google.
7. No usar `_SYNC_ID`, `SYNC_DATA*` o `ExtendedProperties` reservados para sync adapters, ni hacerse pasar por uno. No prometer exactly-once entre dos almacenes sin demostrar recuperación.
8. Si falta identidad fiable o aparecen varios candidatos, parar en estado de reconciliación y pedir confirmación; no insertar otro evento ni borrar todos los candidatos. Nunca actualizar/eliminar solo porque un ID numérico coincide tras restaurar datos.
9. Preservar tombstones/operaciones de limpieza al borrar un hábito; un FK cascade no puede borrar la única referencia al evento antes de retirarlo. Si el usuario elige conservarlo, registrar la desvinculación de forma deliberada.
10. Reconciliar pendientes al guardar, al reactivar y mediante trabajo de respaldo permitido. No vigilar toda la agenda ni importar eventos externos. El flujo de completar un hábito no depende de que Calendar responda.

### Contenido publicado

- Título del hábito; descripción mínima que identifique La Racha y la planificación, sin score, rachas, historial ni notas sensibles adicionales.
- `CALENDAR_ID`, `DTSTART`, `EVENT_TIMEZONE`, `ALL_DAY=0`, `RRULE=FREQ=DAILY` y `DURATION` válida RFC 5545, por ejemplo `PT30M`.
- Para inserción directa de un recurrente, usar duración conforme al contrato; no trasladar sin más un ejemplo de evento único con `DTEND`.
- Recordatorio, si se solicita, con `EVENT_ID`, minutos y método permitido por el calendario; respetar `ALLOWED_REMINDERS` y `MAX_REMINDERS`. Reemplazar el recordatorio propio sin acumular duplicados y mantener coherencia de `HAS_ALARM` conforme al provider.
- Sin asistentes ni invitaciones. Actualizar/eliminar únicamente eventos y recordatorios cuya identidad como propios esté verificada.

### Fechas, zona y DST

- Guardar hora de pared y `ZoneId` explícito. Propuesta inicial: capturar la zona del dispositivo al configurar y mantenerla fija para la serie, mostrándola en UI. Viajar no desplaza silenciosamente la serie; cambiar la zona se hace desde el editor con confirmación.
- No guardar «20:00» como un offset UTC fijo: con DST debe seguir siendo 20:00 en la zona seleccionada.
- Resolver de manera definida la primera ocurrencia en horas inexistentes/ambiguas y probar expansión de la recurrencia en el provider, no solo `ZonedDateTime`. Política propuesta: desplazar una hora inexistente por el salto de DST y elegir la primera ocurrencia de una hora repetida; verificar que el calendario de destino puede respetarlo antes de prometerlo. Si difiere, explicar la limitación o revisar el alcance.
- `DTSTART` requiere milisegundos de instante UTC por contrato. Esto **no** permite convertir las claves de completado a epoch: el historial sigue usando fechas locales ISO y las reglas de `SCORE.md`.
- Clock inyectable para «siguiente ocurrencia», cambios de fecha y recuperación tras semanas sin abrir.

## 5. Privacidad, permisos y fallos

- La función está apagada inicialmente y el consentimiento es por hábito. No acceder al provider antes de que el usuario active y autorice la función.
- Aunque Android otorgue acceso amplio de calendario, limitar las consultas a metadatos necesarios del destino y a eventos propios; no almacenar/copiar ni registrar la agenda personal.
- Tratar denegación parcial, denegación permanente, revocación posterior, permisos revocados automáticamente, provider inexistente, cursor nulo, cuenta eliminada y calendario que pasa a solo lectura.
- Los fallos de permiso/destino necesitan acción del usuario; no reintentar indefinidamente. Reservar backoff para errores transitorios.
- Sin `GET_ACCOUNTS`, `INTERNET`, `POST_NOTIFICATIONS` ni `SCHEDULE_EXACT_ALARM` por esta función. Los recordatorios los publica la aplicación de calendario y dependen de sus permisos/configuración; nuestra app no garantiza que suene una alarma.
- IDs del provider son locales al dispositivo y pueden quedar obsoletos tras restauración/reinstalación. Excluirlos del backup o invalidarlos/revalidarlos antes de usarlos; nunca borrar por ID restaurado sin propiedad verificada.
- Distinguir núcleo offline y exportación opt-in en Ajustes y documentación; no seguir afirmando «ningún dato sale del dispositivo» cuando está activa.

## 6. Hitos verificables para Luna

Mantener separados los hitos de auditoría y esta funcionalidad. Resolver al menos W01, T01–T03 y la coherencia temporal antes de declarar la integración lista; reutilizar su arquitectura cuando corresponda.

### GC1 — Dominio y persistencia antes del provider

Tests de validación de horario/duración, siguiente ocurrencia, zona/DST, estado de exportación y coalescencia. Migración no destructiva con Room real: hábitos/historia/selecciones existentes intactos, exportación apagada para todos y ningún evento creado durante migración. Añadir gateway falso y pruebas de revisión/operaciones durables.

### GC2 — Permisos y elección real de destino

Implementar flujo de consentimiento, permisos bajo demanda y selector de calendarios de Google válidos, vacío/error/reintento y estado de cuenta. Pruebas Compose con tema e insets, denegación/revocación y regreso de Ajustes de Android. No acceder a cuentas/calendarios del usuario para «probar» sin consentimiento.

### GC3 — Serie recurrente, identidad y recuperación

Implementar el adaptador Android y coordinador/Worker. Crear/actualizar una serie y su recordatorio sin duplicados; cubrir cierres de proceso y escritura parcial entre almacenes. En el provider real, verificar identidad conservada al subir/bajar desde Google. No dar por resuelto el riesgo de inserción duplicada solo con un mock.

### GC4 — Integración con hábitos y ciclo de vida

Sección de horario/estado; renombrar y cambiar horario; desactivar, conservar/eliminar, archivar/restaurar, borrar y reactivar; limpieza durable. Restaurar un hábito archivado no recrea silenciosamente series previamente conservadas. Confirmaciones de serie completa, evento externo eliminado/editado y ausencia de efecto en score/rachas.

### GC5 — Aceptación extremo a extremo y documentación

Cuenta/calendario de QA con consentimiento y registros mínimos. Comprobar la serie en Android y **en Google Calendar web u otro dispositivo** después de la sincronización del sistema. Probar offline → reconexión y sincronización pausada sin mentir sobre el estado. Actualizar especificaciones, manual, allowlist de permisos y resultados reales.

En cada hito: tests primero, después implementación incremental, `./gradlew test lint assembleDebug` y, cuando existan las pruebas instrumentadas, `./gradlew :app:connectedDebugAndroidTest`. Verificar versiones oficiales antes de añadir dependencias. No se ejecutaron nuevas compilaciones para este cambio documental ni se atribuyen los 29 tests anteriores a Calendar.

## 7. Criterios de aceptación obligatorios

| Caso | Resultado esperado |
|---|---|
| Usuario no activa Calendar / deniega permisos | App y widgets funcionan sin consultas de calendario ni eventos nuevos. |
| No hay cuenta/destino Google escribible | Explicación y reintento; no fallback silencioso a calendario local. |
| Publicar Leer cada día 20:00, 30 min | Una serie diaria en destino confirmado y una duración correcta, sin 365 inserciones. |
| Reintentar / doble Guardar / reiniciar proceso | Una serie gestionada por hábito; en estado ambiguo, reconciliación visible, no duplicación a ciegas. |
| Dos hábitos con igual nombre/hora | Dos vínculos independientes por ID/token, sin cruzarse. |
| Renombrar/cambiar horario/duración/recordatorio | Se actualiza la serie propia; no acumula recordatorios ni toca eventos ajenos. |
| Completar/desmarcar/corregir historia | Calendar no se modifica; métricas dependen exclusivamente de Room. |
| Editar o borrar en Calendar | Ningún cambio de hábitos/score/rachas; aviso de evento faltante y recreación solo confirmada. |
| Desactivar, archivar o borrar | Se respeta Conservar/Eliminar/Cancelar, con advertencia de serie completa y limpieza durable. |
| Desactivar mientras hay creación pendiente | No aparece una serie tardía por una revisión antigua. |
| Caída después del insert antes del vínculo Room | Recuperación por identidad probada; sin evento extra al reintentar. |
| Permiso revocado / calendario eliminado o solo lectura | Pausa recuperable sin crash, loop de reintentos ni daño al historial. |
| Viaje/DST/hora ambigua y duración que cruza medianoche | Comportamiento temporal documentado, probado con Clock y provider; ninguna marca de hábito cambia de clave. |
| Cambiar destino predeterminado | Solo afecta futuras exportaciones nuevas; vínculos actuales no se mueven silenciosamente. |
| Reinstalar/restaurar datos | No usar IDs antiguos para modificar eventos ajenos ni recrear sin verificar identidad/consentimiento. |
| Sin conexión y sincronización Google desactivada | Publicación local/pending bien indicada; no éxito remoto inventado. |
| Reconciliar con eventos personales similares presentes | Sin lecturas masivas ni modificación/borrado de eventos ajenos por coincidencia de nombre/hora. |

## 8. Fuentes oficiales consultadas

- Calendar Provider, permisos, inserción recurrente, calendarios y recordatorios: https://developer.android.com/identity/providers/calendar-provider
- Contrato de eventos y columnas: https://developer.android.com/reference/android/provider/CalendarContract.Events
- Calendarios y acceso: https://developer.android.com/reference/android/provider/CalendarContract.Calendars
- Permisos bajo demanda y revocación: https://developer.android.com/training/permissions/requesting

La fuente principal confirma que crear calendarios corresponde a sync adapters, que los recurrentes requieren duración y que los calendarios locales no se sincronizan a un servidor. Verificar comportamiento real del adaptador de Google/OEM durante GC3–GC5; una API disponible no equivale a integración remota validada.
