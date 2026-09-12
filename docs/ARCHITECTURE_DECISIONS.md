# Architecture decisions

## Identity
Firebase Authentication UID is the canonical user identity. Email addresses are display/login attributes only and are never used as authorization keys.

## Data ownership
Every cloud document lives below `/users/{uid}`. Firestore and Storage rules require `request.auth.uid == uid`.

## Offline-first
Room remains the local source of truth. Cloud sync mirrors owned data and must never require connectivity for normal local operations.

## Legacy data
Pre-account data is treated as unclaimed local data. The first authenticated user must explicitly claim it before upload. Claiming is a one-time local transaction.

## Updates
Schema changes require explicit Room migrations. `fallbackToDestructiveMigration()` is forbidden for production databases.
