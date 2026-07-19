import Foundation

final class SettingsManager {
  static let shared = SettingsManager()

  private let defaults = UserDefaults.standard

  private enum Key: String {
    case geminiAPIKey
    case openClawHost
    case openClawPort
    case openClawHookToken
    case openClawGatewayToken
    case geminiSystemPrompt
    case webrtcSignalingURL
    case speakerOutputEnabled
    case videoStreamingEnabled
    case proactiveNotificationsEnabled
  }

  private init() {}

  // Reads a secret from the Keychain, transparently migrating a value left over from before
  // this field moved out of UserDefaults (and removing it from there once migrated) so an
  // existing install doesn't appear to have lost its configured key/token after an update.
  private func keychainValue(_ key: Key, fallback: String) -> String {
    if let value = KeychainStore.get(for: key.rawValue) {
      return value
    }
    if let legacy = defaults.string(forKey: key.rawValue) {
      KeychainStore.set(legacy, for: key.rawValue)
      defaults.removeObject(forKey: key.rawValue)
      return legacy
    }
    return fallback
  }

  // MARK: - Gemini

  var geminiAPIKey: String {
    get { keychainValue(.geminiAPIKey, fallback: Secrets.geminiAPIKey) }
    set { KeychainStore.set(newValue, for: Key.geminiAPIKey.rawValue) }
  }

  var geminiSystemPrompt: String {
    get { defaults.string(forKey: Key.geminiSystemPrompt.rawValue) ?? GeminiConfig.defaultSystemInstruction }
    set { defaults.set(newValue, forKey: Key.geminiSystemPrompt.rawValue) }
  }

  // MARK: - OpenClaw

  var openClawHost: String {
    get { defaults.string(forKey: Key.openClawHost.rawValue) ?? Secrets.openClawHost }
    set { defaults.set(newValue, forKey: Key.openClawHost.rawValue) }
  }

  var openClawPort: Int {
    get {
      let stored = defaults.integer(forKey: Key.openClawPort.rawValue)
      return stored != 0 ? stored : Secrets.openClawPort
    }
    set { defaults.set(newValue, forKey: Key.openClawPort.rawValue) }
  }

  var openClawHookToken: String {
    get { keychainValue(.openClawHookToken, fallback: Secrets.openClawHookToken) }
    set { KeychainStore.set(newValue, for: Key.openClawHookToken.rawValue) }
  }

  var openClawGatewayToken: String {
    get { keychainValue(.openClawGatewayToken, fallback: Secrets.openClawGatewayToken) }
    set { KeychainStore.set(newValue, for: Key.openClawGatewayToken.rawValue) }
  }

  // MARK: - WebRTC

  var webrtcSignalingURL: String {
    get { defaults.string(forKey: Key.webrtcSignalingURL.rawValue) ?? Secrets.webrtcSignalingURL }
    set { defaults.set(newValue, forKey: Key.webrtcSignalingURL.rawValue) }
  }

  // MARK: - Audio

  var speakerOutputEnabled: Bool {
    get { defaults.bool(forKey: Key.speakerOutputEnabled.rawValue) }
    set { defaults.set(newValue, forKey: Key.speakerOutputEnabled.rawValue) }
  }

  // MARK: - Video

  var videoStreamingEnabled: Bool {
    get { defaults.object(forKey: Key.videoStreamingEnabled.rawValue) as? Bool ?? true }
    set { defaults.set(newValue, forKey: Key.videoStreamingEnabled.rawValue) }
  }

  // MARK: - Notifications

  var proactiveNotificationsEnabled: Bool {
    get { defaults.object(forKey: Key.proactiveNotificationsEnabled.rawValue) as? Bool ?? true }
    set { defaults.set(newValue, forKey: Key.proactiveNotificationsEnabled.rawValue) }
  }

  // MARK: - Reset

  func resetAll() {
    for key in [Key.geminiSystemPrompt, .openClawHost, .openClawPort, .webrtcSignalingURL,
                .speakerOutputEnabled, .videoStreamingEnabled,
                .proactiveNotificationsEnabled] {
      defaults.removeObject(forKey: key.rawValue)
    }
    for key in [Key.geminiAPIKey, .openClawHookToken, .openClawGatewayToken] {
      KeychainStore.remove(for: key.rawValue)
    }
  }
}
