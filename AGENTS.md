# AGENTS.md — Guía de trabajo para agentes

## Antes de cambiar algo
1. Leer `README.md`, `PRODUCT.md`, `SCORE.md`, `WIDGETS.md` y `DESIGN.md` antes de programar. Consultar después los archivos afectados y sus pruebas.
2. Respetar el alcance del encargo. Una revisión documental no autoriza implementar las funciones descritas.
3. Distinguir **implementado**, **verificado**, **acordado pendiente** y **propuesta**. No convertir una propuesta en requisito aprobado ni un test unitario en prueba de dispositivo.
4. Cada documento tiene una responsabilidad definida en `README.md`. Ante una contradicción, atender la decisión explícita más reciente del usuario y actualizar el documento responsable; si falta una decisión de producto, señalarla antes de codificar esa parte.
5. Preservar trabajo existente, datos y configuraciones. No asumir que un archivo sin seguimiento de Git es desechable. Evitar cambios ajenos al encargo.

## Arquitectura y datos
- Stack: Kotlin nativo, Jetpack Compose + Material 3, Room, Glance y coroutines/Flow. DataStore guarda preferencias/configuración; Room guarda hábitos e historial. Flutter es una alternativa histórica documentada, no otro framework que instalar.
- Mantener la estructura existente bajo `app/src/main/java/com/laracha/app/`: `domain/`, `data/`, `ui/`, `widgets/` y `calendar/`. No crear clases por seguir un árbol sugerido que no corresponda al código real.
- El dominio calcula fechas, score y rachas sin depender de Android. App y widgets consumen el mismo repositorio; no duplicar reglas en composables o callbacks.
- Room es la fuente de verdad del historial. Score, racha, semana visible y progreso se derivan de él; no guardar contadores paralelos como autoridad.
- Escribir mediante operaciones transaccionales e idempotentes, con clave única hábito/fecha. Usar `setCompleted(id, date, value)`, no un toggle basado en estado desactualizado.
- Cualquier cambio de esquema requiere migración que preserve datos, esquema Room exportado y comprobación de migración. No usar migraciones destructivas como solución.
- Usar `Clock` inyectable y fecha de calendario en la zona local del dispositivo. No obtener el día dividiendo milisegundos UTC; no desplazar marcas históricas al cambiar de zona.
- Mantener compatibilidad de IDs de hábitos y de configuración por `appWidgetId`.

## Contratos de producto
- Aplicar las fórmulas y reglas de `SCORE.md`: score por hábito entre 0 y 100 %, hoy pendiente sin penalización anticipada y rachas de días naturales consecutivos separadas del score.
- La ampliación acordada incluye historial anterior a la creación y hábitos no diarios; aún no está implementada. Las decisiones abiertas de `SCORE.md` deben resolverse antes de implementar su cálculo.
- Sin protectores, skips, logros, monedas, anuncios, analíticas, backend ni cuentas propias.
- Calendar es exportación opcional de planificación mediante el proveedor de Android, no sincronización del historial. Completar un hábito no modifica eventos.
- No añadir permisos ajenos a las funciones solicitadas. Conservar el consentimiento y alcance de la integración Calendar existente.
- No copiar código ni componentes de Loop Habit Tracker (GPLv3). La implementación matemática y visual debe ser propia.

## Widgets y presentación
- Cada instancia mantiene su selección y modo independientes. El modo lista `all` usa `LazyColumn` real.
- Completar desde un widget funciona sin abrir una Activity y con el proceso de la app previamente terminado. Abrir detalle es una acción distinta.
- Refrescar las instancias afectadas después del commit; la persistencia no depende de que el launcher acepte el refresco. No prometer actualización exacta a medianoche.
- Validar tema desde el arranque de la Activity, no solo tras componer la pantalla. Probar Claro/Oscuro/Sistema, fuentes grandes y tamaños reales de widget.
- No declarar listo un widget sin probar su interacción en un launcher compatible. Registrar launcher, dispositivo y casos comprobados.

## Entrega y verificación
1. Trabajar por incrementos verificables. Para cambios de reglas: primero tests de dominio/fechas; después persistencia y migración; luego UI y widgets.
2. Añadir pruebas de comportamiento para nuevas reglas o regresiones, evitando tests que solo repitan la implementación. Una edición documental no exige recompilar la app.
3. Para cambios de código, ejecutar cuando el entorno lo permita:
   ```bash
   ./gradlew test lint assembleDebug
   ```
   Añadir pruebas instrumentadas/manuales cuando el cambio requiera Room real, provider o launcher. Un DAO falso no valida SQLite ni una migración.
4. Informar archivos relevantes, comandos exactos, resultados reales y límites de verificación. Si no se ejecutó una comprobación, decirlo; no reciclar resultados antiguos como nuevos.
5. Antes de cambiar dependencias, contrastar compatibilidad en fuentes oficiales y actualizar la toolchain documentada. No afirmar «última versión» sin una consulta fechada.
6. Documentar en un ADR breve los cambios excepcionales de stack o arquitectura. No hace falta un ADR para cada ajuste visual.
7. Si el usuario solicita colaboración entre agentes, separar Implementador y Revisor: uno implementa el hito y el otro comprueba aceptación y casos extremos. Estas etiquetas no atribuyen capacidades especiales ni exigen delegar por defecto.
