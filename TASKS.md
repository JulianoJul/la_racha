# TASKS.md — Entregas y definición de terminado

## Hito 0 — Inicio verificable
- [x] Crear proyecto Kotlin Android reproducible y Gradle Wrapper.
- [x] Elegir y documentar versiones de Compose, Glance, Room, DataStore y Android SDK en `README.md`.
- [x] Configurar tests de dominio.
- [x] Configurar CI básico (build + tests).

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
- [ ] Comprobaciones de consistencia de la DB y recuperaciones por eliminación de hábito.
- [ ] Manual corto: compilar `assembleDebug`, instalar en dispositivo y añadir widgets.
- [x] APK `debug` generado localmente; no se declara instalación ni prueba en dispositivo.

## Ampliación — Google Calendar opt-in (código implementado, validación pendiente)
- [x] Android Calendar Provider con selector de calendario Google existente, permiso runtime al activar y destino predeterminado para nuevas publicaciones.
- [x] Opt-in por hábito; series `FREQ=DAILY`, duración RFC 5545, zona horaria y recordatorio opcional.
- [x] Room v1→v2 no destructiva y operaciones durables con vínculo/propiedad, reconciliación por marcador y WorkManager best-effort para reanudar pendientes.
- [ ] Exportar/commitear snapshot Room schema v2 mediante KSP durante la validación global (no generado aquí porque no se ejecutó build).
- [x] Pantalla separada `CalendarIntegrationScreen`; la navegación y el editor de hábitos existente aún deben conectarse.
- [ ] Ejecutar migración Room real y pruebas de fechas/DST, permisos, duplicados, crash entre provider y Room y revocación.
- [ ] Validar recurrencia, marcadores y recordatorios en provider de dispositivo; comprobar sincronización efectiva en Google Calendar web/u otro dispositivo (no afirmar éxito antes de esta comprobación).
- [ ] Integrar pantalla y planificación por hábito en la navegación/editor principal; probar conservar/eliminar en archive/delete de hábitos.
- [ ] `./gradlew test lint assembleDebug` y prueba manual con calendario/cuenta de QA, después del cierre global coordinado.

La exportación publica solo horario/nombre de hábitos opt-in; completados e historial permanecen en Room. El estado «publicado en calendario del dispositivo» no equivale a «sincronizado con Google».

## Regla para los agentes
Implementar y comprobar solo un hito por iteración. El revisor no aprueba por capturas bonitas: necesita test real o declaración explícita de lo no probado.
