import Foundation
import Security

enum AccountStore {
    private static var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: "Madus.Bilibili", kSecAttrAccount as String: "session"]
    }

    static func read() -> String {
        var request = query
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var value: CFTypeRef?
        guard SecItemCopyMatching(request as CFDictionary, &value) == errSecSuccess, let data = value as? Data else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }

    static func save(_ cookie: String) throws {
        let data = Data(cookie.utf8)
        let updates: [String: Any] = [kSecValueData as String: data]
        var status = SecItemUpdate(query as CFDictionary, updates as CFDictionary)
        if status == errSecItemNotFound {
            var item = query
            item[kSecValueData as String] = data
            item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            status = SecItemAdd(item as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw MadusError.message("登录凭据无法保存（\(status)）。") }
    }

    static func clear() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw MadusError.message("登录凭据无法移除（\(status)）。")
        }
    }
}
