import Foundation
import Security

// Minimal Keychain wrapper for the handful of real secrets (API keys, gateway tokens) that
// SettingsManager used to keep in UserDefaults -- readable in plaintext by anything with
// filesystem access to the app's container (a jailbroken device, an unencrypted backup). The
// Keychain encrypts at rest and ties access to the device passcode/biometry.
enum KeychainStore {
  private static let service = Bundle.main.bundleIdentifier ?? "com.xiaoanliu.VisionClaw"

  static func set(_ value: String, for key: String) {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
    ]
    // Delete-then-add avoids SecItemUpdate's separate query/attributes-to-update split for a
    // single-value item -- simpler, and this isn't a hot path.
    SecItemDelete(query as CFDictionary)

    var attributes = query
    attributes[kSecValueData as String] = Data(value.utf8)
    // AfterFirstUnlock (not WhenUnlocked) so background WebSocket/network calls that need
    // these tokens can still read them if the app is woken while the device is locked.
    attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock

    let status = SecItemAdd(attributes as CFDictionary, nil)
    if status != errSecSuccess {
      NSLog("[KeychainStore] Failed to store value for %@: OSStatus %d", key, status)
    }
  }

  static func get(for key: String) -> String? {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
      kSecReturnData as String: true,
      kSecMatchLimit as String: kSecMatchLimitOne,
    ]
    var result: AnyObject?
    let status = SecItemCopyMatching(query as CFDictionary, &result)
    guard status == errSecSuccess, let data = result as? Data else { return nil }
    return String(data: data, encoding: .utf8)
  }

  static func remove(for key: String) {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
    ]
    SecItemDelete(query as CFDictionary)
  }
}
