/*
 *  SwiftRadioPlayerPlugin.swift
 *
 *  Created by Ilia Chirkunov <xc@yar.net> on 10.01.2021.
 */

import Flutter
import UIKit

public class SwiftRadioPlayerPlugin: NSObject, FlutterPlugin {
    static let instance = SwiftRadioPlayerPlugin()
    private let player = RadioPlayer.shared
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "radio_player", binaryMessenger: registrar.messenger())
        registrar.addMethodCallDelegate(instance, channel: channel)
        
        let playerEventsChannel = FlutterEventChannel(name: "radio_player/playerEvents", binaryMessenger: registrar.messenger())
        playerEventsChannel.setStreamHandler(PlayerEventStreamHandler())
        let metadataChannel = FlutterEventChannel(name: "radio_player/metadataEvents", binaryMessenger: registrar.messenger())
        metadataChannel.setStreamHandler(MetadataStreamHandler())
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "setStations":
            print(call.arguments)
            let stations = Station.fromJsonList(call.arguments as! [String])
            player.setStations( stations)
        case "selectStation":
            let stationId = call.arguments as! Int
            player.selectStation(stationId)
            print(call.arguments)
        case "play":
            player.play()
        case "stop":
            player.stop()
        case "pause":
            player.pause()
        case "addToControlCenter":
            player.addToControlCenter()
        case "removeFromControlCenter":
            player.removeFromControlCenter()
        case "startTimer":
            let seconds = call.arguments as! Double
            player.stopPlayer(after: seconds)
        case "cancelTimer":
            player.cancelTimer()
        case "isPlaying":
            let isPlaying = player.isPlaying
            result(isPlaying)
        default:
            result(FlutterMethodNotImplemented)
        }
        
        result(1)
    }
}

/** Handler for playback state changes, passed to setStreamHandler() */
class PlayerEventStreamHandler: NSObject, FlutterStreamHandler {
    private var eventSink: FlutterEventSink?
    
    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        eventSink = events
        NotificationCenter.default.addObserver(self, selector: #selector(onRecieve(_:)), name: NSNotification.Name(rawValue: "playerEvent"), object: nil)
        return nil
    }
    
    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        eventSink = nil
        return nil
    }
    
    // Notification receiver for playback state changes, passed to addObserver()
    @objc private func onRecieve(_ notification: Notification) {
        if let state = notification.userInfo!["playerEvent"] {
            eventSink?(state)
        }
    }
}

/** Handler for new metadata, passed to setStreamHandler() */
class MetadataStreamHandler: NSObject, FlutterStreamHandler {
    private var eventSink: FlutterEventSink?
    
    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        eventSink = events
        NotificationCenter.default.addObserver(self, selector: #selector(onRecieve(_:)), name: NSNotification.Name(rawValue: "metadata"), object: nil)
        return nil
    }
    
    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        eventSink = nil
        return nil
    }
    
    // Notification receiver for new metadata, passed to addObserver()
    @objc private func onRecieve(_ notification: Notification) {
        if let metadata = notification.userInfo!["metadata"] {
            eventSink?(metadata)
        }
    }
}
