# La Racha — Hábitos para Android

App local de hábitos con **constancia acumulada (0–100 %)**, **rachas de días consecutivos** y widgets interactivos. Implementada en Kotlin nativo: Compose + Material 3, Room, Glance, DataStore y coroutines/Flow.

## Mapa de documentación
| Documento | Responsabilidad |
|---|---|
| [AGENTS.md](AGENTS.md) | Cómo trabajar, preservar datos y verificar entregas |
| [PRODUCT.md](PRODUCT.md) | Alcance, comportamiento y aceptación del producto |
| [SCORE.md](SCORE.md) | Fórmulas, fechas, rachas y decisiones de frecuencias |
| [WIDGETS.md](WIDGETS.md) | Diseño funcional, acciones, configuración y pruebas en launcher |
| [DESIGN.md](DESIGN.md) | Tema, jerarquía visual, interacción y accesibilidad |

Antes de programar, leer estos documentos y el código afectado. Mantener cada regla en su documento responsable y enlazarla desde los demás.

Vocabulario de estado: **implementado** = existe código; **verificado** = se ejecutó una comprobación concreta; **acordado pendiente** = pedido aceptado sin completar; **propuesta/decisión abierta** = no asumir una regla definitiva. Los criterios de aceptación no son evidencia de pruebas.

Auditoría vigente: [AUDITORIA-Y-PLAN.md](AUDITORIA-Y-PLAN.md), revisión unificada de app, widgets y Calendar contra `16c5eef` y el trabajo local posterior. Sustituye los dos planes antiguos, conserva pendientes y distingue evidencia de código, pruebas históricas y validación aún ausente.

Material complementario: [TASKS.md](TASKS.md), [PROMPT-IA.md](PROMPT-IA.md) y [FLUTTER.md](FLUTTER.md). Son planes/referencias de otras etapas y pueden contener alcance anterior; contrastarlos con estos seis documentos y la auditoría vigente antes de ejecutarlos. Flutter no forma parte del stack activo.

## Estado actual — revisión documental 2026-10-17
### Base implementada
- Dominio de score/rachas/fecha local, historial anterior a creación, tres frecuencias y períodos históricos en Room/repositorio transaccional.
- Pantallas Hoy, editor, detalle/historial y ajustes de tema.
- Widget individual con check grande y semana informativa; widget lista con selección por instancia y navegación al detalle.
- Exportación opt-in de planes diarios mediante Android Calendar Provider. Su compilación no acredita integración con un proveedor/cuenta real.

### Próxima iteración acordada, aún sin implementar
- La semana del widget individual sustituye al check grande: tocar hoy completa/desmarca.
- Mostrar racha 🔥 positiva; si es cero, porcentaje de constancia positivo; ocultar la métrica si ambos son cero.
- Corregir destello blanco al abrir desde widget en oscuro y solapamiento de la barra de scroll con checks del widget lista.
- Historial anterior a creación, score/rachas desde la primera marca y recálculo al quitarla: implementado localmente.
- Días fijos, intervalos de N días y cuotas semanales con historia de reglas Room: implementado localmente; migración v2→v3 verificada en Samsung SM-A156M Android 16.
- Indicar desbordamiento/scroll en Hoy, abrir teclado al crear y situar Calendar junto a Ajustes.

**Estado de la auditoría previa:** los resultados documentados abajo preceden a los cambios locales compartidos; el fallo anterior por `CalendarToday` ya no describe el árbol actual.

### Verificación local de frecuencias — 2026-10-17
- `./gradlew test lint assembleDebug --rerun-tasks`: **BUILD SUCCESSFUL**, 72 tests unitarios sin fallos; lint 0 errores, 10 advertencias y 1 hint; APK debug compilado.
- `./gradlew :app:connectedDebugAndroidTest`: **BUILD SUCCESSFUL**, migración v2→v3 comprobada con `MigrationTestHelper` en Samsung SM-A156M Android 16. Se alineó Serialization 1.8.1 requerida por Room 2.8.5 frente al BOM transitivo 1.7.3 de DataStore.
- APK debug instalado. El teléfono estaba bloqueado al abrirlo y capturar pantalla; la UI/widgets aún no tienen validación visual/interactiva. Calendar sigue sin soporte para frecuencias no diarias; ver [FREQUENCY-CALENDAR-FINDING.md](FREQUENCY-CALENDAR-FINDING.md).

### Ajustes posteriores y nombres de frecuencia — 2026-10-17
- Selector en una fila con scroll: **Diario**, **Días fijos**, **Cada X días**, **Días por semana**. La cuota se resume como «3 días por semana», no «Por intervalo». No cambian reglas, datos ni esquema.
- `./gradlew test lint assembleDebug`: **BUILD SUCCESSFUL**, 83 tests sin fallos; lint 0 errores, 11 advertencias y 1 hint.
- APK actualizado conservando datos con `adb -s R5CX3463Q6H install -r app/build/outputs/apk/debug/app-debug.apk`. Esta última modificación de nombres no tuvo una nueva comprobación visual en dispositivo; la instalación no la sustituye.

### Cierre de UI/widgets y navegación — 2026-10-17
- Hoy sin «Un día cada vez» y con nombre, constancia y racha en líneas separadas. Widget individual con cinco fechas centradas en hoy; lista sin texto de estado redundante y con 24dp de margen dentro de cada fila para la barra de scroll.
- El fondo y los espacios vacíos del widget lista abren la app. Las listas cortas ajustan su altura para no interceptar taps debajo de la última fila. Información del hábito, checks y «Editar» mantienen acciones independientes.
- `./gradlew test lint assembleDebug :app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL**, 83 tests unitarios sin fallos; lint 0 errores, 11 advertencias y 1 hint.
- Tras instalar ambos APK con `adb -s R5CX3463Q6H install -r`, se ejecutó `adb -s R5CX3463Q6H shell am instrument -w -e class com.laracha.app.widgets.HabitListRowLayoutTest,com.laracha.app.widgets.HabitListBackgroundTest com.laracha.app.test/androidx.test.runner.AndroidJUnitRunner`: **4 tests correctos** en Samsung SM-A156M Android 16. Comprueban RemoteViews, margen, altura y controles separados; no sustituyen interacción real en launcher. La comprobación manual del último cambio queda a cargo del usuario.
- APK final instalado; no se modificaron hábitos ni marcas durante estas pruebas. La ejecución anterior de `connectedDebugAndroidTest` desinstaló la app al finalizar y se reinstaló después: no afirmar que esa ejecución conservó los datos. Para teléfonos personales, evitar esa tarea y su limpieza automática; usar un dispositivo desechable o instalación `-r` e instrumentación directa sin desinstalación. Se guardó un respaldo local fuera del repositorio antes de esta última actualización.

## Arquitectura
```text
app/src/main/java/com/laracha/app/
  domain/    # Cálculos y reglas de fecha, sin Android
  data/      # Room, repositorio y preferencias
  ui/        # Compose, navegación y tema
  widgets/   # Glance, configuración y acciones nativas
  calendar/  # Exportación de planificación al proveedor Android
app/src/test/  # Tests locales
app/schemas/   # Esquemas Room exportados
```

Room es la fuente de verdad de hábitos e historial. Score/rachas se derivan; DataStore mantiene preferencias/configuración. App y widgets usan el mismo repositorio. Una acción de check en widget no debe abrir una Activity.

## Compilar y comprobar
Desde la raíz, con JDK compatible con AGP 9 y Android SDK Platform 36 configurado mediante el entorno o `local.properties`:

```bash
./gradlew test lint assembleDebug
```

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Tests: `app/build/test-results/testDebugUnitTest/`.
- Lint: `app/build/reports/lint-results-debug.txt`.
- Para actualizar un dispositivo conservando datos y con firma compatible:
  ```bash
  adb devices -l
  adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
  ```

Tests de dominio no validan launcher, migración Room real ni provider Calendar. Consultar las matrices de `SCORE.md` y `WIDGETS.md` según el cambio.

### Última comprobación registrada — 2026-10-17
`./gradlew test lint assembleDebug`: **47 tests sin fallos; lint con 0 errores, 4 advertencias y 1 sugerencia; compilación correcta**. Es evidencia de la iteración anterior, no una ejecución nueva durante esta revisión documental.

APK actualizado sin borrar datos en Samsung SM-A156M / One UI. Se revisaron el widget individual con semana y hoy azul, la navegación desde filas de lista y Calendar desactivado en claro/oscuro. No se modificaron marcas ni eventos. Cobertura y pendientes detallados en `WIDGETS.md`.

Pendientes: migraciones/provider real, sincronización remota Calendar, matriz completa de launcher/temas/accesibilidad y nuevas funciones acordadas. No afirmar que los cambios documentados ya pasan esos tests.

## Toolchain fijada
Versiones contrastadas con los archivos Gradle del repositorio en esta revisión; no se afirma que sean las últimas publicadas.

| Componente | Versión/configuración |
|---|---|
| Android Gradle Plugin | `9.0.1` |
| Gradle Wrapper | `9.1.0` |
| Kotlin | Integrado en AGP 9; sin segundo plugin Kotlin Android |
| Compose BOM / Compiler plugin | `2026.06.00` / `2.2.10` |
| Activity Compose | `1.13.0` |
| Room / KSP | `2.8.5` / `2.3.12` |
| Glance AppWidget | `1.2.0` |
| Preferences DataStore | `1.2.1` |
| WorkManager | `2.11.2` |
| JUnit local | `4.13.2` |
| compileSdk / targetSdk / minSdk | `36` / `36` / `26` |

La combinación tiene la validación local histórica indicada arriba. Antes de actualizar, comprobar Kotlin/AGP/KSP/Compose y requisitos SDK en fuentes oficiales; no actualizar una BOM de forma aislada suponiendo compatibilidad.

## Privacidad y Calendar
Núcleo offline, sin backend, cuentas propias, anuncios, analíticas ni permiso `INTERNET`. Calendar solicita `READ_CALENDAR` y `WRITE_CALENDAR` al activar la exportación. Publica planificación de hábitos elegidos en calendarios Google elegibles del proveedor local; no exporta marcas/rachas/score ni importa cambios externos.

La sincronización del proveedor con Google depende de Android. Un insert local no demuestra llegada a Calendar web. Las nuevas frecuencias del hábito todavía no están traducidas a recurrencias de exportación.

WorkManager puede aportar permisos/receptores de infraestructura al manifiesto fusionado (`WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`). Revisar el manifiesto de release; no retirarlos sin comprobar reintentos y recuperación tras reinicio.

Base, preferencias e IDs locales se excluyen de backup/transferencia mediante `allowBackup=false`, `fullBackupContent` y `dataExtractionRules`. Eventos ya exportados pueden permanecer tras desinstalar.

## Originalidad y referencias
Implementación propia. Loop Habit Tracker es GPLv3: no copiar sus fuentes ni componentes. Usar el concepto matemático como referencia no implica reproducir su implementación ni asigna una licencia a este proyecto.

Fuentes oficiales para contrastar cambios (no consultadas de nuevo en esta edición documental):
- [AGP 9](https://developer.android.com/build/releases/past-releases/agp-9-0-0-release-notes), [Gradle 9.1](https://docs.gradle.org/9.1.0/release-notes.html) y [SDK Platforms](https://developer.android.com/tools/releases/platforms).
- [Compose y compilador](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler), [Activity](https://developer.android.com/jetpack/androidx/releases/activity).
- [Room](https://developer.android.com/jetpack/androidx/releases/room), [KSP](https://github.com/google/ksp/releases), [DataStore](https://developer.android.com/jetpack/androidx/releases/datastore).
- [Glance](https://developer.android.com/jetpack/androidx/releases/glance), [WorkManager](https://developer.android.com/jetpack/androidx/releases/work).
- [Calendar Provider](https://developer.android.com/identity/providers/calendar-provider), [permisos en runtime](https://developer.android.com/training/permissions/requesting).
- Referencia conceptual: [Loop Habit Tracker](https://github.com/iSoron/uhabits) y [FAQ de puntuación](https://github.com/iSoron/uhabits/discussions/689).
