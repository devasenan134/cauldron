# Google sign-in (Credential Manager) looks its Play Services provider up by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
