import 'dart:convert';

import 'package:flutter/foundation.dart';

@immutable
class Station {
  Station({
    required this.id,
    required this.title,
    required this.coverUrl,
    required this.streamUrl,
  });

  final int id;
  final String title;
  final String coverUrl;
  final String streamUrl;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Station && other.id == id && other.title == title && other.coverUrl == coverUrl;

  @override
  int get hashCode => id.hashCode ^ title.hashCode ^ coverUrl.hashCode;

  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'id': id,
      'title': title,
      'coverUrl': coverUrl,
      'streamUrl': streamUrl,
    };
  }

  String toJson() => json.encode(toMap());
}
