package net.kikin.nubecita.core.auth

/**
 * Sign-in was refused because this copy of the app, or the device it runs on,
 * could not be verified (the token-mediating backend requires a valid
 * attestation token for sign-in once the client is confidential).
 *
 * Distinct from a network or credentials failure so the sign-in screen can
 * say what actually happened. Never thrown for a refresh: an attestation
 * problem must not end, or appear to end, an existing session.
 */
class DeviceVerificationRequiredException : RuntimeException("Sign-in refused: the app or device could not be verified")
