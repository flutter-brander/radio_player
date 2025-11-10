import 'package:radio_player/system_player_event_type.dart';

class SystemPlayerEvent {
  const SystemPlayerEvent({
    required this.type,
    this.stationId,
  });

  const SystemPlayerEvent.other() : this(type: SystemPlayerEventType.other);

  final int? stationId;
  final SystemPlayerEventType type;
}
