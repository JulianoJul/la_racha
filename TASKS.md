# TASKS.md — Entregas y definición de terminado

## Hito 0 — Inicio verificable
- [x] Crear proyecto Kotlin Android reproducible y Gradle Wrapper.
- [x] Elegir y documentar versiones de Compose, Glance, Room, DataStore y Android SDK en `README.md`.
- [x] Configurar tests de dominio.
- [x] Build automático de GitHub Actions retirado por petición del usuario; mantener las comprobaciones locales con Gradle.

## Hito 1 — Dominio probado
- [x] Habit, Completion, repositorio y Clock inyectable.
- [x] Score diario con media exponencial, no penalizar hoy pendiente.
- [x] Racha actual y récord independientes.
- [x] Tests de 7/13/30/60 días, falta, remarcado, cambios de fecha/hora, editar pasado y dos taps.
- [x] Esquema Room v1 y operaciones atómicas; falta validar el motor SQLite real en dispositivo/emulador.

## Hito 2 — App funcional
- [x] Pantalla Hoy y crear/editar/archivar/borrar/reordenar.
- [x] Marcar/desmarcar hoy con persistencia inmediata.
- [x] Detalle con score, racha e historial corregible (UI compilada; pendiente prueba visual en dispositivo).
- [x] Diseño Material 3 claro/oscuro/sistema y semántica accesible básica (validación visual pendiente en dispositivo).

## Hito 3 — Widget individual
- [x] Receiver + configuración por `appWidgetId` en Preferences DataStore.
- [x] Widget 2×2 redimensionable, puntuación y acción Room sin iniciar la Activity.
- [ ] Validar dos instancias diferentes, acción con app cerrada y limpieza real en launcher/emulador.

## Hito 4 — Widget lista
- [x] Receiver + modos `selected` (1–5) / `all`, configuración por `appWidgetId` y orden de selección.
- [x] Glance `LazyColumn` y completar/desmarcar cada fila conectado a Room.
- [ ] Validar dos instancias con modos distintos, scroll de 15+ hábitos y acciones en launcher real/emulador.

## Hito 5 — Calidad
- [ ] Widget en launcher real/emulador con pantallas pequeña/grande, modo oscuro y fuente grande.
- [ ] Reinicio, ahorro batería, sin conexión, cambio local de fecha.
- [ ] Ampliar pruebas instrumentadas de Room: hoy solo migración v2→v3 ejecutada; faltan operaciones, FK y recuperación al borrar hábitos.
- [ ] Manual corto: compilar `assembleDebug`, instalar en dispositivo y añadir widgets.
- [x] APK `debug` generado e instalado en SM-A156M; UI quedó sin validar porque el teléfono estaba bloqueado.

## Ampliación — Google Calendar opt-in (código implementado, validación pendiente)
- [x] Android Calendar Provider con selector de calendario Google existente, permiso runtime al activar y destino predeterminado para nuevas publicaciones.
- [x] Opt-in por hábito; recurrencia diaria, días fijos, intervalo anclado y cuota con N días elegidos para Calendar; duración RFC 5545, zona horaria y recordatorio opcional. Nuevas recurrencias compiladas, sin QA de provider.
- [x] Room v1→v2 no destructiva y operaciones durables con vínculo/propiedad, reconciliación por marcador y WorkManager best-effort para reanudar pendientes.
- [x] Snapshots Room v1, v2, v3 y v4 exportados mediante KSP.
- [x] Pantalla `CalendarIntegrationScreen` conectada a Hoy/Ajustes; cambios de nombre/frecuencia enlazados al editor.
- [x] Migración Room v1→v2 y v2→v3 ejecutada en SQL aislado y migración v2→v3 validada con `MigrationTestHelper` en SM-A156M Android 16.
- [ ] Probar permisos, proveedor, duplicados, caídas entre provider y Room y revocación; la prueba instrumentada no cubre Calendar.
- [ ] Validar recurrencia, marcadores y recordatorios en provider de dispositivo; comprobar sincronización efectiva en Google Calendar web/u otro dispositivo (no afirmar éxito antes de esta comprobación).
- [x] Integrar pantalla en navegación y conectar conservación/eliminación de series con archive/delete.
- [x] Snapshot durable de recurrencia autorizada y confirmación al modificar una frecuencia exportada. Renombrar/reintentar no aplica reglas nuevas sin consentimiento.
- [x] Migración v3→v4 comprobada en SQLite aislado; pruebas de migración/reapertura Room y cadena v2→v4 añadidas y compiladas.
- [ ] Ejecutar esas nuevas pruebas instrumentadas y probar flujo visual/launcher, Calendar real y cuenta de QA; ver `FREQUENCY-CALENDAR-FINDING.md`.
- [x] `./gradlew test lint assembleDebug --rerun-tasks` pasó el 2026-10-17: 72 unit tests; lint sin errores.

La exportación publica solo horario/nombre de hábitos opt-in; completados e historial permanecen en Room. El estado «publicado en calendario del dispositivo» no equivale a «sincronizado con Google».

## Regla para los agentes
Implementar y comprobar solo un hito por iteración. El revisor no aprueba por capturas bonitas: necesita test real o declaración explícita de lo no probado.
