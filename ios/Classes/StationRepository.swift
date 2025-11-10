//
//  StationRepository.swift
//  radio_player
//
//  Created by Dima Kutko on 29.05.2024.
//

import Foundation

class StationRepository {
    
    static private let stationKey = "cached_stations"
    static private let defaults = UserDefaults.standard
    
    static public func loadStations() -> [Station]{
        if let jsonStrings = defaults.object(forKey: stationKey) as? [String] {
            return jsonStrings.compactMap { Station.fromJson($0) }
        }
        return []
    }
    
    static public func saveStations( _ stations: [Station]){
        let jsonStrings = stations.map { $0.toJson() }
        defaults.set(jsonStrings, forKey: stationKey)
    }
}
