//
//  RadioPlayerCarPlayDelegate.swift
//  radio_player
//
//  Created by Dima Kutko on 29.05.2024.
//

import Foundation
import CarPlay

@available(iOS 14.0, *)
class RadioPlayerCarPlayDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
    static private var interfaceController: CPInterfaceController?
    static private let player = RadioPlayer.shared
    static private var currentPlayingItem: CPListItem? = nil
    static private var items: [CPRadioPlayerListItem] = []
    static private var rootTemplate: CPListTemplate? = nil
    
    static private let emptyMessageTitle: String = "Connection..."
    static private let emptyMessageSubtitle: String = "Confirm login in the your mobile app"
    
    // Create connection
    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene,
                                  didConnect interfaceController: CPInterfaceController) {
        RadioPlayerCarPlayDelegate.interfaceController = interfaceController
        RadioPlayerCarPlayDelegate.creteRootTemplate()
    }
    
    // Init root tamplate
    static private func creteRootTemplate() {
        var stations = player.getStations;
        if(stations.isEmpty){
            stations = StationRepository.loadStations()
            player.addToControlCenter()
        }
        var section = stationsToCPListSection(stations)
        rootTemplate = CPListTemplate(title: "Chanson America", sections: [section])
        rootTemplate!.emptyViewTitleVariants = [emptyMessageTitle]
        rootTemplate!.emptyViewSubtitleVariants = [emptyMessageSubtitle]
        self.interfaceController?.setRootTemplate(rootTemplate!, animated: true)
    }
    
    
    // Updaet stations
    static public func updateStations(_ stations: [Station]){
        guard rootTemplate != nil else {
            creteRootTemplate()
            return
        }
        var section = stationsToCPListSection(stations)
        rootTemplate?.updateSections([section])
        if(stations.isEmpty){
            if(interfaceController?.topTemplate is CPNowPlayingTemplate){
                interfaceController?.popToRootTemplate(animated: true)
            }
        }
    }
    
    
    // Generate list
    static private func stationsToCPListSection(_ stations: [Station]) -> CPListSection {
        let nowPlayingStationId = player.getSelectedStation?.id
        items = stations.map { station -> CPRadioPlayerListItem in
            var item =  CPListItem(text: station.title, detailText: "")
            item.setImage(ImageHallper.downloadImage(station.coverUrl))
            item.handler = { selectedItem, complete in
                onPressedItem(item: item, stationId: station.id)
                complete()
            }
            if(nowPlayingStationId == station.id){
                currentPlayingItem?.isPlaying = false
                currentPlayingItem = item
                currentPlayingItem?.isPlaying = true
            }
            return CPRadioPlayerListItem(item: item, stationId: station.id)
        }
        return CPListSection(items: items.map {cpItem -> CPListItem in return cpItem.item})
    }
    
    // Tap on list item heander
    static private func onPressedItem(item: CPListItem, stationId: Int){
        currentPlayingItem?.isPlaying = false
        // Load stations from cacahe when radio player is empty
        if(player.getStations.isEmpty){
            var stations = StationRepository.loadStations()
            if(stations.isEmpty) {return}
            player.setStations(stations, notifyCarPlay: false)
        }
        player.selectStation(stationId)
        player.play()
        let nowPlayingTemplate = CPNowPlayingTemplate.shared
        interfaceController?.pushTemplate(nowPlayingTemplate, animated: true)
        currentPlayingItem = item
        currentPlayingItem?.isPlaying = true
    }
    
    // On change station by other interfaces
    static public func onChangeSelectedStation(_ stationId: Int){
        currentPlayingItem?.isPlaying = false
        currentPlayingItem = items.first(where: {$0.stationId == stationId})?.item
        currentPlayingItem?.isPlaying = true
    }
}
