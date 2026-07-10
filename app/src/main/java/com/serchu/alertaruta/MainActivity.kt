package com.serchu.alertaruta

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var textEstado: TextView
    private lateinit var box1: TextView
    private lateinit var box2: TextView
    private lateinit var box3: TextView
    private lateinit var prefs: SharedPreferences
    private var isOn = false
    private var intervaloMin = 2

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startAlertService()
        } else {
            setButtonState(false)
            textEstado.text = "Necesitás dar permiso de notificaciones para que funcione"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        intervaloMin = prefs.getInt(KEY_INTERVALO, 2)

        btnToggle = findViewById(R.id.btnToggle)
        textEstado = findViewById(R.id.textEstado)
        box1 = findViewById(R.id.box1min)
        box2 = findViewById(R.id.box2min)
        box3 = findViewById(R.id.box3min)

        actualizarCajasIntervalo()

        box1.setOnClickListener { seleccionarIntervalo(1) }
        box2.setOnClickListener { seleccionarIntervalo(2) }
        box3.setOnClickListener { seleccionarIntervalo(3) }

        btnToggle.setOnClickListener {
            val nuevoEstado = !isOn
            if (nuevoEstado) {
                requestIgnoreBatteryOptimizations()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startAlertService()
                }
            } else {
                stopAlertService()
            }
        }
    }

    private fun seleccionarIntervalo(minutos: Int) {
        intervaloMin = minutos
        prefs.edit().putInt(KEY_INTERVALO, minutos).apply()
        actualizarCajasIntervalo()
        // Si ya está corriendo, reiniciamos el servicio para que tome el nuevo intervalo
        if (isOn) {
            startAlertService()
        }
    }

    private fun actualizarCajasIntervalo() {
        box1.setBackgroundResource(if (intervaloMin == 1) R.drawable.box_selected else R.drawable.box_unselected)
        box2.setBackgroundResource(if (intervaloMin == 2) R.drawable.box_selected else R.drawable.box_unselected)
        box3.setBackgroundResource(if (intervaloMin == 3) R.drawable.box_selected else R.drawable.box_unselected)
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                // Algunos dispositivos bloquean este intent directo, no pasa nada
            }
        }
    }

    private fun startAlertService() {
        val intent = Intent(this, AlertService::class.java)
        intent.putExtra(AlertService.EXTRA_INTERVALO_MIN, intervaloMin)
        ContextCompat.startForegroundService(this, intent)
        setButtonState(true)
        textEstado.text = "Alerta activa: cada $intervaloMin min"
    }

    private fun stopAlertService() {
        val intent = Intent(this, AlertService::class.java)
        stopService(intent)
        setButtonState(false)
        textEstado.text = "Alerta detenida"
    }

    private fun setButtonState(on: Boolean) {
        isOn = on
        btnToggle.backgroundTintList = null
        if (on) {
            btnToggle.setBackgroundResource(R.drawable.btn_circle_on)
            btnToggle.text = "ENCENDIDO"
        } else {
            btnToggle.setBackgroundResource(R.drawable.btn_circle_off)
            btnToggle.text = "APAGADO"
        }
    }

    override fun onResume() {
        super.onResume()
        setButtonState(AlertService.isRunning)
        textEstado.text = if (AlertService.isRunning)
            "Alerta activa: cada $intervaloMin min"
        else
            "Alerta detenida"
    }

    companion object {
        const val PREFS_NAME = "alerta_ruta_prefs"
        const val KEY_INTERVALO = "intervalo_min"
    }
}
