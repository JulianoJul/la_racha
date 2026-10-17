# FLUTTER.md — Alternativa (no elegida para MVP Android-only)

Flutter está instalado y se podría usar para la interfaz principal. Es una elección válida si el objetivo futuro es Android + iOS. Sin embargo, un widget real en homescreen Android no es simplemente un Widget de Flutter: debe renderizarse mediante código Android, por ejemplo Jetpack Glance, o con una biblioteca que abstraiga esa integración.

## Si cambiamos el stack conscientemente
- App: Flutter/Dart + Material 3.
- Widgets Android: Jetpack Glance/Kotlin + `home_widget` para la coordinación, o evaluación de `glance_widget` si ofrece realmente todo lo exigido.
- Caso exigente: un widget de **todos con scroll** + selecciones por **instancia** + checkbox nativo que funciona con la app cerrada; comprobar en una PoC antes de migrar el proyecto.
- Una única fuente de verdad compartida sin contradicciones entre SQLite, callbacks y estado de widget. Preferir una capa repositorio Android estable si la interacción nativa debe funcionar autónomamente; definir frontera `MethodChannel` con contratos versionados y pruebas. No dar por hecho que snapshots en preferencias bastan para garantizar consistencia.
- No introducir Flutter y Kotlin UI a la vez dentro del mismo MVP sin una necesidad demostrada.

## Paquetes candidatos, por evaluar con PoC
- https://pub.dev/packages/home_widget — plugin de intercambio/actualización; código de la interfaz del widget es nativo.
- https://pub.dev/packages/glance_widget — plantillas y lista desplazable, pero verificar límites en config. por instancia, diseño propio y callbacks.

## Criterio objetivo para decidir Flutter
Cambiar si la app se ampliará a iOS, o si la PoC demuestra que los dos widgets ofrecen toda la interacción/scroll/configuración/sincronización requerida y el trabajo de puente merece la pena. Si Android es el único objetivo, Kotlin reduce las piezas móviles.
