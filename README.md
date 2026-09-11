# Contador de Pasos - Compilación con GitHub Actions

Este proyecto está configurado para compilarse automáticamente en GitHub mediante **GitHub Actions**, generando el instalador `.apk` listo para descargar e instalar en tu teléfono Android.

---

## 🚀 Cómo compilar y descargar el APK en GitHub

### Opción 1: Compilación Automática (por cada cambio)
Cada vez que hagas un `push` a las ramas `main` o `master`, o crees un `Pull Request`, GitHub Actions compilará la aplicación y verificará que las pruebas pasen.

### Opción 2: Compilación Manual (desde la web de GitHub)
1. Ve a tu repositorio en GitHub.
2. Haz clic en la pestaña **Actions** en la parte superior.
3. En la barra lateral izquierda, selecciona el flujo **"Compilar APK Android"**.
4. Haz clic en el botón desplegable **"Run workflow"** a la derecha.
5. *(Opcional)* Puedes marcar si deseas compilar también un paquete App Bundle (`.aab`) o desactivar las pruebas unitarias.
6. Pulsa en **"Run workflow"** para iniciar la compilación.

---

## 📦 Dónde descargar el APK compilado

1. En la pestaña **Actions**, entra en la ejecución en curso o terminada (con el icono verde de verificación ✅).
2. Desplázate hacia abajo hasta la sección **Artifacts** (Artefactos) al final de la página del resumen.
3. Haz clic en **`app-debug-apk`** para descargar el archivo ZIP que contiene el APK (`app-debug.apk`).
4. Descomprime el ZIP y transfiérelo o ábrelo en tu dispositivo Android para instalar la aplicación.

---

## 🛠️ Archivos incluidos para GitHub Actions

- `.github/workflows/build.yml`: Flujo de trabajo de GitHub Actions con JDK 21, Gradle 9.3.1 y exportación de artefactos APK y reportes.
- `gradlew` y `gradlew.bat`: Scripts de arranque de Gradle Wrapper para entornos Linux y Windows.
- `gradle/wrapper/gradle-wrapper.properties`: Configuración de versión de Gradle compatible (9.3.1).
- `gradle/wrapper/gradle-wrapper.jar`: Binario de Gradle Wrapper requerido para la ejecución portátil sin dependencias externas previas.
