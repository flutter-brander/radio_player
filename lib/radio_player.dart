import 'dart:async';

import 'package:flutter/services.dart';
import 'package:radio_player/constants.dart';
import 'package:radio_player/station.dart';
import 'package:radio_player/system_player_event.dart';
import 'package:radio_player/system_player_event_type.dart';

export 'package:radio_player/station.dart';
export 'package:radio_player/system_player_event.dart';
export 'package:radio_player/system_player_event_type.dart';

class RadioPlayer {
  static const _methodChannel = MethodChannel(kMainChannel);
  static const _metadataEvents = EventChannel(kMetadataChannel);
  static const _playerEvents = EventChannel(kPlayerChannel);

  Stream<List<String>>? _metadataStream;
  Stream<SystemPlayerEvent>? _playerEventsStream;

  Future<void> setStations(List<Station> stations) async {
    final mapList = stations.map((item) => item.toJson()).toList();
    await _methodChannel.invokeMethod(kSetStationsMethod, mapList);
  }

  Future<void> selectStation(int stationId) async {
    await _methodChannel.invokeMethod(kSelectStationMethod, stationId);
  }

  Future<void> play() async {
    await _methodChannel.invokeMethod(kPlayMethod);
  }

  Future<void> stop() async {
    await _methodChannel.invokeMethod(kStopMethod);
  }

  Future<void> pause() async {
    await _methodChannel.invokeMethod(kPauseMethod);
  }

  /// Added media info to player in control center and enable buttons on it
  Future<void> addToControlCenter() async {
    await _methodChannel.invokeMethod(kAddToControlCenterMethod);
  }

  /// Remove media info from player in control center and disable any interaction with it
  Future<void> removeFromControlCenter() async {
    await _methodChannel.invokeMethod(kRemoveFromControlCenterMethod);
  }

  /// Stop playing after specified number of seconds.
  Future<void> setupTimer(Duration duration) async {
    return await _methodChannel.invokeMethod(kStartTimerMethod, duration.inSeconds);
  }

  /// Cancel scheduled timer
  Future<void> removeTimer() async {
    return await _methodChannel.invokeMethod(kRemoveTimerMethod);
  }

  /// Returns true if player is playing sound otherwise false.
  Future<bool> isPlaying() async {
    final result = await _methodChannel.invokeMethod(kIsPlayingMethod);
    return result as bool;
  }

  /// Get the playback state stream.
  Stream<SystemPlayerEvent> get playerEventsStream {
    _playerEventsStream ??= _playerEvents.receiveBroadcastStream().map<SystemPlayerEvent>((data) {
      try {
        final list = data as List<dynamic>;
        final type = SystemPlayerEventType.values.byName(list[0]);
        if (type.isChangeStation) {
          return SystemPlayerEvent(type: type, stationId: list[1] as int);
        } else {
          return SystemPlayerEvent(type: type);
        }
      } catch (_) {
        return SystemPlayerEvent.other();
      }
    });
    return _playerEventsStream!;
  }

  /// Get the metadata stream.
  Stream<List<String>> get metadataStream {
    _metadataStream ??= _metadataEvents.receiveBroadcastStream().map((metadata) {
      return metadata.map<String>((value) => value as String).toList();
    });
    return _metadataStream!;
  }

  void resetStreams() {
    _metadataStream = null;
    _playerEventsStream = null;
  }
}
