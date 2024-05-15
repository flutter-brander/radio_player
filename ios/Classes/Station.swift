//
//  Station.swift
//  radio_player
//
//  Created by Dima Kutko on 28.05.2024.
//

import Foundation

class Station: Codable {
    var id: Int
    var title: String
    var coverUrl: String
    var streamUrl: String

    enum CodingKeys: String, CodingKey {
        case id, title, coverUrl, streamUrl
    }

    init(id: Int, title: String, coverUrl: String, streamUrl: String) {
        self.id = id
        self.title = title
        self.coverUrl = coverUrl
        self.streamUrl = streamUrl
    }

    static func fromJson(_ jsonString: String) -> Station? {
        guard let data = jsonString.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(Station.self, from: data)
    }

    static func fromJsonList(_ items: [String]) -> [Station] {
        return items.compactMap { fromJson($0) }
    }

    func toJson() -> String? {
        guard let data = try? JSONEncoder().encode(self) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func toMap() -> [String: Any]? {
        guard let data = try? JSONEncoder().encode(self) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data, options: .allowFragments)) as? [String: Any]
    }
}
