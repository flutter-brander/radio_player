//
//  ImageHallper.swift
//  radio_player
//
//  Created by Dima Kutko on 29.05.2024.
//

import Foundation

class ImageHallper {
    static public func downloadImage(_ value: String) -> UIImage? {
        guard let url = URL(string: value) else { return nil }
        var result: UIImage?
        let semaphore = DispatchSemaphore(value: 0)
        let task = URLSession.shared.dataTask(with: url) { (data, response, error) in
            if let data = data, error == nil {
                result = UIImage(data: data)
            }
            semaphore.signal()
        }
        task.resume()
        let _ = semaphore.wait(timeout: .distantFuture)
        return result
    }
}
