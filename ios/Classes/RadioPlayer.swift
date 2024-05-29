/*
 *  RadioPlayer.swift
 *
 *  Created by Ilia Chirkunov <xc@yar.net> on 10.01.2021.
 */

import MediaPlayer
import AVKit

class RadioPlayer: NSObject , AVPlayerItemMetadataOutputPushDelegate {
    static let shared = RadioPlayer()
    
    private override init() {}
    
    private var player: AVPlayer!
    private var playerItem: AVPlayerItem!
    
    private var stations: [Station] = []
    public var getStations: [Station] {
        return stations
    }
    
    private var selectedStation: Station? = nil
    public var getSelectedStation: Station? {
        return selectedStation
    }
    
    private var interruptionObserverAdded: Bool = false
    private var mediaButtonsIsListened: Bool = false
    
    var isPlaying: Bool {
        guard let player = player else {
            return false
        }
        return player.rate != 0 && player.error == nil
    }
    
    private(set) var isAvailableInControlCenter = false
    private var playButtonIsEnabled = false
    
    private var stationImage: UIImage? = nil
    private var selectedStreamUrl: String? = nil
    
    private var playerStopDate: Date?
    private var timeObserver: Any?
    
    deinit {
        removeTimeObserver()
    }
    
    public func setStations(_ stations: [Station], notifyCarPlay: Bool = true) {
        print("stations: \(stations)")
        self.stations = stations
        StationRepository.saveStations(stations)
        if(notifyCarPlay){RadioPlayerCarPlayDelegate.updateStations(stations)}
    }
    
    public func selectStation(_ stationId: Int){
        print("selectStation: \(stationId)")
        print(stations)
        selectedStation = stations.first(where: { $0.id == stationId })
        if(selectedStation == nil) {
            guard !stations.isEmpty else { return }
            selectedStation = stations.first
        }
        setStream(streamUrl: selectedStation!.streamUrl, title: selectedStation!.title, streamImageUrl: selectedStation!.coverUrl)
        RadioPlayerCarPlayDelegate.onChangeSelectedStation(stationId)
        NotificationCenter.default.post(name: NSNotification.Name(rawValue: "playerEvent"), object: nil, userInfo: ["playerEvent": ["changeStation", stationId]])
    }
    
    private func setStream(streamUrl: String, title: String, streamImageUrl: String) {
        if(self.selectedStreamUrl == streamUrl) {return}
        self.selectedStreamUrl = streamUrl
        
        self.stationImage = ImageHallper.downloadImage(streamImageUrl);
        setPlayerMetadata(title: title, coverUrl: streamImageUrl)
        
        playerItem = AVPlayerItem(url: URL(string: streamUrl)!)
        if (player == nil) {
            player = AVPlayer(playerItem: playerItem)
            player.automaticallyWaitsToMinimizeStalling = true
            player.addObserver(self, forKeyPath: #keyPath(AVPlayer.timeControlStatus), options: [.new], context: nil)
            runInBackground()
        } else {
            player.replaceCurrentItem(with: playerItem)
        }
        
        addInterruptionObserverIfNeed()
        
        let metaOutput = AVPlayerItemMetadataOutput(identifiers: nil)
        metaOutput.setDelegate(self, queue: DispatchQueue.main)
        playerItem.add(metaOutput)
    }
    
    private func resetStream() {
        playerItem = AVPlayerItem(url: URL(string: selectedStreamUrl!)!)
        player.replaceCurrentItem(with: playerItem)
        addInterruptionObserverIfNeed()
        let metaOutput = AVPlayerItemMetadataOutput(identifiers: nil)
        metaOutput.setDelegate(self, queue: DispatchQueue.main)
        playerItem.add(metaOutput)
    }
    
    private func addInterruptionObserverIfNeed(){
        if (!interruptionObserverAdded) {
            NotificationCenter.default.addObserver(self, selector: #selector(playerItemFailedToPlay), name: NSNotification.Name.AVPlayerItemFailedToPlayToEndTime, object: nil)
            NotificationCenter.default.addObserver(self, selector: #selector(handleInterruption), name: AVAudioSession.interruptionNotification, object: AVAudioSession.sharedInstance())
            interruptionObserverAdded = true
        }
    }
    
    private func setPlayerMetadata(title: String? = nil, subtitle: String = "", coverUrl: String? = nil){
        guard isAvailableInControlCenter else {return}
        
        print("setPlayerMetadata \(String(describing: title)), \(subtitle), \(String(describing: coverUrl))")
        
        var metadataTitle = title ?? selectedStation?.title ?? "";
        var metadataSubtitle = subtitle;
        
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [MPMediaItemPropertyTitle: metadataTitle, MPMediaItemPropertyArtist: metadataSubtitle]
        
        var metadataImage: UIImage? = nil
        if(coverUrl?.isEmpty ?? true || coverUrl == selectedStation?.coverUrl){
            metadataImage = stationImage ?? ImageHallper.downloadImage(selectedStation?.coverUrl)
        } else {
            metadataImage = ImageHallper.downloadImage(coverUrl)
        }
        
        guard metadataImage != nil else { return }
        let artwork = MPMediaItemArtwork(boundsSize: metadataImage!.size) { (size) -> UIImage in metadataImage! }
        MPNowPlayingInfoCenter.default().nowPlayingInfo?.updateValue(artwork, forKey: MPMediaItemPropertyArtwork)
    }
    
    /// Resume playback after phone call.
    @objc func handleInterruption(_ notification: Notification) {
        guard let info = notification.userInfo,
              let typeValue = info[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else {
            return
        }
        if type == .began {
            
        } else if type == .ended {
            guard let optionsValue = info[AVAudioSessionInterruptionOptionKey] as? UInt else {
                return
            }
            let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue)
            if options.contains(.shouldResume) {
                play()
            }
        }
    }
    
    /// TODO: Attempt to reconnect when disconnecting.
    @objc func playerItemFailedToPlay(_ notification: Notification) {
        
    }
    
    func play() {
        guard player != nil else {return}
        resetStream()
        player.play()
    }
    
    func stop() {
        guard player != nil else {return}
        player.pause()
        player.replaceCurrentItem(with: nil)
    }
    
    func pause() {
        guard player != nil else {return}
        player.pause()
    }
    
    private func onTapNext() {
        guard !stations.isEmpty else { return }
        if let currentIndex = stations.firstIndex(where: {$0.id == selectedStation?.id}){
            let nextIndex = (currentIndex + 1) % stations.count
            selectStation(stations[nextIndex].id)
        }
    }
    
    private func onTapPrevios() {
        guard !stations.isEmpty else { return  }
        if let currentIndex = stations.firstIndex(where: {$0.id == selectedStation?.id}){
            let stationsCount = stations.count
            let previosIndex = (currentIndex - 1 + stationsCount) % stationsCount
            selectStation(stations[previosIndex].id)
        }
    }
    
    
    func removeFromBackground() {
        UIApplication.shared.endReceivingRemoteControlEvents()
        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.removeTarget(self)
        commandCenter.pauseCommand.removeTarget(self)
        commandCenter.playCommand.isEnabled = false
        commandCenter.pauseCommand.isEnabled = false
        
        
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        try? AVAudioSession.sharedInstance().setActive(false)
    }
    
    func runInBackground() {
        isAvailableInControlCenter = true
        try? AVAudioSession.sharedInstance().setActive(true)
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        
        // Control buttons on the lock screen.
        UIApplication.shared.beginReceivingRemoteControlEvents()
        let commandCenter = MPRemoteCommandCenter.shared()
        
        // Play button.
        commandCenter.playCommand.isEnabled = true
        commandCenter.playCommand.addTarget { [weak self] (event) -> MPRemoteCommandHandlerStatus in
            self?.play()
            return .success
        }
        
        // Pause button.
        commandCenter.pauseCommand.isEnabled = true
        commandCenter.pauseCommand.addTarget { [weak self] (event) -> MPRemoteCommandHandlerStatus in
            self?.stop()
            return .success
        }
    }
    
    func jsonToMap(_ jsonString: String) -> [String: Any] {
        guard let jsonData = jsonString.data(using: .utf8) else {
            return [:]
        }
        do {
            if let jsonMap = try JSONSerialization.jsonObject(with: jsonData, options: []) as? [String: Any] {
                return jsonMap
            }
        } catch {
            print("Ошибка парсинга JSON: \(error)")
        }
        return [:]
    }
    
    func strToJson(_ oldStr: String) -> String{
        var str = oldStr;
        str = str.replacingOccurrences(of: "\"", with: "'")
        str = str.replacingOccurrences(of: "{'", with: "{\"")
        str = str.replacingOccurrences(of: "''}", with: "\"}")
        str = str.replacingOccurrences(of: "None", with: "null")
        str = str.replacingOccurrences(of: "', '", with: "\", \"")
        str = str.replacingOccurrences(of: "': '", with: "\": \"")
        str = str.replacingOccurrences(of: "':", with: "\":")
        return str;
    }
    
    func metadataOutput(_ output: AVPlayerItemMetadataOutput, didOutputTimedMetadataGroups groups: [AVTimedMetadataGroup],
                        from track: AVPlayerItemTrack?) {
        let metaDataItems = groups.first.map({ $0.items })
        let first = metaDataItems?.first
        guard let json = metaDataItems?.first?.stringValue else { return }
        
        print("trackData: \(strToJson(json))")
        let trackData = jsonToMap(strToJson(json));
        
        let title = trackData["title"] as? String ?? ""
        let artistTitle = trackData["artist"] as? String ?? ""
        var cover = trackData["cover"] as? String ?? ""
        
        lazy var trackTitle: String = {
            if !title.isEmpty, title != "unknown", !artistTitle.isEmpty, artistTitle != "unknown" {
                return "\(title) - \(artistTitle)"
            }
            return ""
        }()
        
        var trackImageUrl: String? = selectedStation?.coverUrl
        if (!cover.isEmpty && !cover.contains("defaultSongImage")) {
            trackImageUrl = cover
        }
        
        if(trackTitle.isEmpty){
            setPlayerMetadata(title: selectedStation?.title ?? "", coverUrl: trackImageUrl)
        }else{
            setPlayerMetadata(title: trackTitle, subtitle: selectedStation?.title ?? "", coverUrl: trackImageUrl)
        }
        
        
        // NOTIFY Flutter about meta
        NotificationCenter.default.post(name: NSNotification.Name(rawValue: "metadata"), object: nil, userInfo: ["metadata": [title, artistTitle, cover]])
    }
    
    func addToControlCenter() {
        playButtonIsEnabled = true
        UIApplication.shared.beginReceivingRemoteControlEvents()
        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.isEnabled = true
        commandCenter.pauseCommand.isEnabled = true
        
        setPlayerMetadata(title: selectedStation?.title, coverUrl: selectedStation?.coverUrl)
        
        commandCenter.nextTrackCommand.isEnabled = true
        
        if(!mediaButtonsIsListened){
            commandCenter.nextTrackCommand.addTarget { [weak self] event in
                self?.onTapNext()
                return .success
            }
            commandCenter.previousTrackCommand.addTarget { [weak self] event in
                self?.onTapPrevios()
                return .success
            }
            mediaButtonsIsListened = true
        }
        commandCenter.previousTrackCommand.isEnabled = true
    }
    
    func removeFromControlCenter() {
        playButtonIsEnabled = false
        UIApplication.shared.endReceivingRemoteControlEvents()
        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.isEnabled = false
        commandCenter.pauseCommand.isEnabled = false
        
        commandCenter.nextTrackCommand.isEnabled = false
        commandCenter.nextTrackCommand.removeTarget(nil)
        
        commandCenter.previousTrackCommand.isEnabled = false
        commandCenter.previousTrackCommand.removeTarget(nil)
        
        mediaButtonsIsListened = false
    }
    
    func stopPlayer(after seconds: TimeInterval) {
        removeTimeObserver()
        let interval = CMTimeMakeWithSeconds(seconds, preferredTimescale: 1)
        playerStopDate = Date(timeIntervalSinceNow: seconds)
        timeObserver = player.addPeriodicTimeObserver(forInterval: interval,
                                                      queue: .main) { [weak self] _ in
            guard let self = self else { return }
            guard let playerStopDate = self.playerStopDate else {
                self.removeTimeObserver()
                return
            }
            let secondsLeft = Date().timeIntervalSince(playerStopDate)
            if secondsLeft >= -1 {
                self.removeTimeObserver()
                self.stop()
            } else if abs(secondsLeft) < seconds / 2 {
                self.stopPlayer(after: abs(secondsLeft))
            }
        }
    }
    
    func cancelTimer() {
        removeTimeObserver()
    }
    
    override func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey: Any]?, context: UnsafeMutableRawPointer?) {
        guard let observedKeyPath = keyPath, object is AVPlayer, observedKeyPath == #keyPath(AVPlayer.timeControlStatus) else {
            return
        }
        
        if let statusAsNumber = change?[NSKeyValueChangeKey.newKey] as? NSNumber {
            let status = AVPlayer.TimeControlStatus(rawValue: statusAsNumber.intValue)
            
            if status == .paused {
                NotificationCenter.default.post(name: NSNotification.Name(rawValue: "playerEvent"), object: nil, userInfo: ["playerEvent": ["pause"]])
                setPlayerMetadata(title: selectedStation?.title, coverUrl: selectedStation?.coverUrl)
            } else if status == .waitingToPlayAtSpecifiedRate {
                NotificationCenter.default.post(name: NSNotification.Name(rawValue: "playerEvent"), object: nil, userInfo: ["playerEvent": ["play"]])
            }
        }
    }
    
    private func removeTimeObserver() {
        playerStopDate = nil
        if let timeObserver = timeObserver {
            player.removeTimeObserver(timeObserver)
        }
        timeObserver = nil
    }
}
