package com.remotecontrol.client

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class RemoteService : Service() {
    private lateinit var socket: Socket
    private lateinit var locationManager: LocationManager
    override fun onCreate() {
        super.onCreate()
        startForeground()
        connectSocket()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    private fun startForeground() {
        val channelId = "remote_control"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Remote Control", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Service actif")
            .setContentText("En attente de commandes")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .build()
        startForeground(1, notification)
    }
    private fun connectSocket() {
        try {
            val options = IO.Options()
            options.reconnection = true
            socket = IO.socket(Config.SERVER_URL, options)
            socket.on(Socket.EVENT_CONNECT) {
                socket.emit("register_device", JSONObject().apply {
                    put("token", Config.DEVICE_TOKEN)
                    put("name", Build.MODEL)
                    put("type", "android")
                    put("model", Build.MODEL)
                })
            }
            socket.on("command") { args ->
                val data = args[0] as JSONObject
                executeCommand(data)
            }
            socket.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun executeCommand(data: JSONObject) {
        val commandId = data.getInt("id")
        val type = data.getString("type")
        val params = data.optJSONObject("params") ?: JSONObject()
        when (type) {
            "gps" -> getLocation(commandId)
            "apps_list" -> getAppsList(commandId)
            "launch_app" -> launchApp(params.getString("package"), commandId)
            "lock" -> lockDevice(commandId)
            else -> {
                socket.emit("command_result", JSONObject().apply {
                    put("command_id", commandId)
                    put("result", JSONObject().apply { put("status", "ok") })
                })
            }
        }
    }
    private fun getLocation(commandId: Int) {
        try {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            socket.emit("command_result", JSONObject().apply {
                put("command_id", commandId)
                put("result", JSONObject().apply {
                    put("latitude", location?.latitude ?: 0.0)
                    put("longitude", location?.longitude ?: 0.0)
                    put("accuracy", location?.accuracy ?: 0.0)
                })
            })
        } catch (e: Exception) {
            socket.emit("command_result", JSONObject().apply {
                put("command_id", commandId)
                put("result", JSONObject().apply { put("error", e.message) })
            })
        }
    }
    private fun getAppsList(commandId: Int) {
        val packages = packageManager.getInstalledApplications(0)
        val appsList = packages.map { app ->
            JSONObject().apply {
                put("name", packageManager.getApplicationLabel(app).toString())
                put("package", app.packageName)
            }
        }
        socket.emit("command_result", JSONObject().apply {
            put("command_id", commandId)
            put("result", JSONObject().apply { put("apps", appsList) })
        })
    }
    private fun launchApp(packageName: String, commandId: Int) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
                socket.emit("command_result", JSONObject().apply {
                    put("command_id", commandId)
                    put("result", JSONObject().apply { put("status", "launched") })
                })
            } else {
                socket.emit("command_result", JSONObject().apply {
                    put("command_id", commandId)
                    put("result", JSONObject().apply { put("error", "App not found") })
                })
            }
        } catch (e: Exception) {
            socket.emit("command_result", JSONObject().apply {
                put("command_id", commandId)
                put("result", JSONObject().apply { put("error", e.message) })
            })
        }
    }
    private fun lockDevice(commandId: Int) {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {})
        }
        socket.emit("command_result", JSONObject().apply {
            put("command_id", commandId)
            put("result", JSONObject().apply { put("status", "locked") })
        })
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
