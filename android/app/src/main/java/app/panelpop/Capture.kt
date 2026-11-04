package app.panelpop

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/** Encender el traductor desde cualquier pantalla (pantalla principal o lector). */
object Capture {
    const val REQ = 42

    /** Devuelve true si ya estaba encendido. Si no, pide los permisos necesarios. */
    fun request(a: Activity): Boolean {
        if (TranslatorService.running) return true
        if (!Settings.canDrawOverlays(a)) {
            Toast.makeText(a, "Activa «Mostrar sobre otras apps» para PanelPop", Toast.LENGTH_LONG).show()
            a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}")))
            return false
        }
        val mpm = a.getSystemService(Activity.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // Android 14+: pantalla completa (no una sola app) para que las posiciones coincidan
        val intent = if (Build.VERSION.SDK_INT >= 34)
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        else mpm.createScreenCaptureIntent()
        @Suppress("DEPRECATION") a.startActivityForResult(intent, REQ)
        return false
    }

    /** Llamar desde onActivityResult. Devuelve true si el traductor arrancó. */
    fun onResult(a: Activity, req: Int, res: Int, data: Intent?): Boolean {
        if (req != REQ) return false
        if (res != Activity.RESULT_OK || data == null) {
            Toast.makeText(a, "Sin permiso de captura no se puede traducir", Toast.LENGTH_SHORT).show(); return false
        }
        a.startForegroundService(Intent(a, TranslatorService::class.java)
            .putExtra(TranslatorService.EXTRA_CODE, res).putExtra(TranslatorService.EXTRA_DATA, data))
        return true
    }

    fun stop(a: Activity) {
        a.startService(Intent(a, TranslatorService::class.java).setAction(TranslatorService.ACTION_STOP))
    }
}
