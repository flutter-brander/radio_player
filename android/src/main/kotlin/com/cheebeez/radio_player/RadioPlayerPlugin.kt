/*
 *  RadioPlayerPlugin.kt
 *
 *  Created by Ilia Chirkunov <xc@yar.net> on 28.12.2020.
 */

package com.cheebeez.radio_player

import androidx.annotation.NonNull
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.EventChannel.StreamHandler
import android.content.Intent
import android.content.IntentFilter
import android.content.Context
import android.content.ServiceConnection
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.os.IBinder
import android.util.Log
import com.cheebeez.radio_player.models.Station

/** RadioPlayerPlugin */
class RadioPlayerPlugin : FlutterPlugin, MethodCallHandler {

    companion object {
        var TAG: String = "RadioPlayerPluginLog"
    }

    private lateinit var context: Context
    private lateinit var channel: MethodChannel
    private lateinit var playerEventsChannel: EventChannel
    private lateinit var metadataChannel: EventChannel
    private lateinit var intent: Intent
    private lateinit var service: RadioPlayerService


    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, K.FLUTTER_CHANEL_NAME)
        channel.setMethodCallHandler(this)

        playerEventsChannel =
            EventChannel(flutterPluginBinding.binaryMessenger, "radio_player/playerEvents")
        playerEventsChannel.setStreamHandler(playerEventsStreamHandler)
        metadataChannel =
            EventChannel(flutterPluginBinding.binaryMessenger, "radio_player/metadataEvents")
        metadataChannel.setStreamHandler(metadataStreamHandler)

        // Start service
        intent = Intent(context, RadioPlayerService::class.java)
//        intent.action = "other"
        context.bindService(
            intent, serviceConnection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        )
        context.startService(intent)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        playerEventsChannel.setStreamHandler(null)
        metadataChannel.setStreamHandler(null)
        context.unbindService(serviceConnection)
        context.stopService(intent)
    }


    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "selectStation" -> {
                val stationId = call.arguments<Int>()!!
                service.selectStation(stationId = stationId);
            }

            "setStations" -> {
                val args = call.arguments<ArrayList<String>>()!!
                service.setStations(Station.fromJsonList(args))
            }

            "play" -> {
                service.play()
            }

            "stop" -> {
                service.stop()
            }

            "pause" -> {
                service.pause()
            }

            "addToControlCenter" -> {
                service.addToControlCenter()
            }

            "removeFromControlCenter" -> {
                service.removeFromControlCenter()
            }

            "startTimer" -> {
                val timerInterval = call.arguments<Double>()!!
                service.startTimer(timerInterval)
            }

            "cancelTimer" -> {
                service.cancelTimer()
            }

            "isPlaying" -> {
                result.success(service.isPlaying())
            }

            else -> {
                result.notImplemented()
            }
        }

        result.success(1)
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            val binder = iBinder as RadioPlayerService.LocalBinder
            service = binder.getService()
        }

        // Called when the connection with the service disconnects unexpectedly.
        // The service should be running in a different process.
        override fun onServiceDisconnected(componentName: ComponentName) {
        }
    }

    /** Handler for playback state changes, passed to setStreamHandler() */
    private var playerEventsStreamHandler = object : StreamHandler {
        private var eventSink: EventSink? = null

        override fun onListen(arguments: Any?, events: EventSink?) {
            eventSink = events
            LocalBroadcastManager.getInstance(context).registerReceiver(
                broadcastReceiver, IntentFilter(K.ACTION_STATE_PLAYER_EVENT)
            )
        }

        override fun onCancel(arguments: Any?) {
            eventSink = null
            LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver)
        }

        // Broadcast receiver for playback state changes, passed to registerReceiver()
        private var broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    val list = intent.getStringArrayListExtra(K.ACTION_STATE_PLAYER_EVENT_EXTRA)
                    if (list != null) {
                        eventSink?.success(list)
                    }
                }
            }
        }
    }

    /** Handler for new metadata, passed to setStreamHandler() */
    private var metadataStreamHandler = object : StreamHandler {
        private var eventSink: EventSink? = null

        override fun onListen(arguments: Any?, events: EventSink?) {
            eventSink = events
            LocalBroadcastManager.getInstance(context).registerReceiver(
                broadcastReceiver, IntentFilter(K.ACTION_NEW_METADATA)
            )
        }

        override fun onCancel(arguments: Any?) {
            eventSink = null
            LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver)
        }

        // Broadcast receiver for new metadata, passed to registerReceiver()
        private var broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    val received = intent.getStringArrayListExtra(K.ACTION_NEW_METADATA_EXTRA)
                    eventSink?.success(received)
                }
            }
        }
    }
}
