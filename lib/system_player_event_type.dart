enum SystemPlayerEventType {
  play,
  pause,
  changeStation,
  other;

  bool get isPlay => play == this;
  bool get isPause => pause == this;
  bool get isChangeStation => changeStation == this;
}
