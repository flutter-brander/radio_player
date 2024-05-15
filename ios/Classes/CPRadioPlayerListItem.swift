//
//  CPRadioPlayerListItem.swift
//  radio_player
//
//  Created by Dima Kutko on 29.05.2024.
//

import Foundation
import CarPlay

class CPRadioPlayerListItem{
    var item: CPListItem
    var stationId: Int
    
    init(item: CPListItem, stationId: Int) {
        self.item = item
        self.stationId = stationId
    }
}
