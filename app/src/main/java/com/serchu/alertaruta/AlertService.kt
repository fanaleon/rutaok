package com.serchu.alertaruta

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * Servicio en foreground que alterna dos patrones de alerta cada N minutos
 * (configurable: 1, 2 o 3 min):
 *   - Patrón LARGO: un pulso simple pero largo (~900ms)
 *   - Patrón TRIPLE: tres pulsos cortos seguidos (~300ms cada uno)
 *
 * El celular vibra con el patrón exacto usando el Vibrator nativo.
 * El Redmi Watch 5 no tiene SDK abierto para mandarle un patrón custom, pero
 * espeja notificaciones del teléfono (vibra cuando llega una). Por eso además
 * mandamos 1 notificación para el pulso largo y 3 notificaciones espaciadas
 * para el triple, así el reloj también marca la diferencia por cantidad de
 * "buzz" aunque no pueda replicar la duración exacta.
 */
class AlertService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var esPulsoLargo = true
    private var notifIdCounter = 1000
    private var intervalMillis = 2 * 60 * 1000L

    private val tickRunnable = object : Runnable {
        override fun run() {
            try {
                if (esPulsoLargo) fireAlertLargo() else fireAlertTriple()
            } catch (e: Exception) {
                android.util.Log.e("AlertaRuta", "Error al disparar alerta", e)
            }
            esPulsoLargo = !esPulsoLargo
            handler.postDelayed(this, intervalMillis)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val minutos = intent?.getIntExtra(EXTRA_INTERVALO_MIN, 2) ?: 2
        intervalMillis = minutos.coerceIn(1, 3) * 60 * 1000L

        val foregroundNotif = NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setContentTitle("Alerta Ruta activa")
            .setContentText("Vibrando cada $minutos min para mantenerte alerta")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, foregroundNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, foregroundNotif)
        }

        isRunning = true
        handler.removeCallbacks(tickRunnable)
        esPulsoLargo = true
        handler.postDelayed(tickRunnable, intervalMillis)
        return START_STICKY
    }

    /** Un pulso largo: ~900ms seguidos, celular + 1 notificación al reloj */
    private fun fireAlertLargo() {
        vibrarCelularSimple(longArrayOf(0, 900))
        postearNotificacion("Pulso largo")
    }

    /** Triple pulso: 3 vibraciones cortas separadas, celular + 3 notificaciones al reloj */
    private fun fireAlertTriple() {
        vibrarCelularSimple(longArrayOf(0, 300, 180, 300, 180, 300))
        postearNotificacion("Pulso triple")
        handler.postDelayed({ postearNotificacion("Pulso triple") }, 480L)
        handler.postDelayed({ postearNotificacion("Pulso triple") }, 960L)
    }

    private fun vibrarCelularSimple(pattern: LongArray) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            android.util.Log.e("AlertaRuta", "No se pudo vibrar el celular", e)
        }
    }

    private fun postearNotificacion(etiqueta: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = notifIdCounter++
        val notif = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setContentTitle("¡Atento!")
            .setContentText(etiqueta)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 400))
            .build()
        nm.notify(id, notif)
        handler.postDelayed({ nm.cancel(id) }, 3000)
    }

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val statusChannel = NotificationChannel(
            CHANNEL_STATUS, "Estado del servicio", NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(statusChannel)

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT, "Alertas de vibración", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400)
        }
        nm.createNotificationChannel(alertChannel)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(tickRunnable)
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        const val CHANNEL_STATUS = "status_channel"
        const val CHANNEL_ALERT = "alert_channel"
        const val EXTRA_INTERVALO_MIN = "extra_intervalo_min"
        var isRunning = false
    }
}
