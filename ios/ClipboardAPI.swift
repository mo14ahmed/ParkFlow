import Foundation

// Add this file to your iOS project. Pass the key from API-KEY.txt when creating
// the client; for a personal app, store the entered key in the iOS Keychain.
struct ClipboardItem: Decodable, Identifiable {
    let id: String
    let type: String
    let text: String?
    let filename: String?
    let size: Int
    let createdAt: String
    let expiresAt: String
}
struct ClipboardHistory: Decodable {
    let date: String
    let timezone: String
    let expiresAt: String
    let usedBytes: Int
    let maxBytes: Int
    let maxItems: Int
    let items: [ClipboardItem]
}
struct ClipboardAPI {
    // Include a trailing slash, e.g. https://www.parkflow.tech/
    // For a servlet context: https://example.com/ParkFlow/
    let baseURL: URL
    let apiKey: String

    struct APIError: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }
    private func send(_ path: String = "", method: String = "GET",
                      contentType: String? = nil, body: Data? = nil) async throws -> Data {
        let url = baseURL.appendingPathComponent("api/clipboard" + path)
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue(apiKey, forHTTPHeaderField: "X-API-Key")
        if let contentType { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        request.httpBody = body
        request.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        request.httpShouldHandleCookies = false
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError(message: "Invalid server response") }
        guard (200...299).contains(http.statusCode) else {
            let error = try? JSONDecoder().decode([String: String].self, from: data)
            throw APIError(message: error?["error"] ?? "Request failed (\(http.statusCode))")
        }
        return data
    }
    func history() async throws -> ClipboardHistory {
        let data = try await send()
        return try JSONDecoder().decode(ClipboardHistory.self, from: data)
    }
    func sendText(_ text: String) async throws -> ClipboardItem {
        let body = try JSONSerialization.data(withJSONObject: ["text": text])
        let data = try await send("/text", method: "POST", contentType: "application/json", body: body)
        return try JSONDecoder().decode(ClipboardItem.self, from: data)
    }
    func upload(data: Data, filename: String) async throws -> ClipboardItem {
        guard !data.isEmpty, data.count <= 10 * 1024 * 1024 else {
            throw APIError(message: "Choose a non-empty file of 10 MB or less.")
        }
        let boundary = "ParkFlow-" + UUID().uuidString
        let safeName = filename.replacingOccurrences(of: "\r", with: "_")
            .replacingOccurrences(of: "\n", with: "_")
            .replacingOccurrences(of: "\"", with: "_")
            .replacingOccurrences(of: "\\", with: "_")
        var body = Data()
        body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"\(safeName)\"\r\nContent-Type: application/octet-stream\r\n\r\n".utf8))
        body.append(data)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        let response = try await send("/files", method: "POST", contentType: "multipart/form-data; boundary=\(boundary)", body: body)
        return try JSONDecoder().decode(ClipboardItem.self, from: response)
    }
    func download(id: String) async throws -> Data {
        guard UUID(uuidString: id) != nil else { throw APIError(message: "Invalid item ID") }
        return try await send("/\(id)/download")
    }
    func delete(id: String) async throws {
        guard UUID(uuidString: id) != nil else { throw APIError(message: "Invalid item ID") }
        _ = try await send("/\(id)", method: "DELETE")
    }
}
