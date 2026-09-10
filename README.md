# AutoKeeper – Car Maintenance Tracker

AutoKeeper is a complete Android app built with Kotlin + Jetpack Compose for managing car maintenance.

## Implemented features

- Firebase Authentication (sign up, sign in, sign out)
- Profile screen with display-name updates
- Vehicle management for multiple vehicles
- Service record creation and history for oil changes, tire rotations, brake service, and custom service types
- Record fields: date, mileage, cost, notes, shop name
- Search and type filtering for maintenance records
- Lifetime maintenance expense calculation
- CameraX receipt capture
- Firebase Storage upload for captured receipt images
- Cloud Firestore persistence for users, vehicles, and service records
- Fused Location Services support for auto-capturing shop location coordinates
- AlarmManager + local notification reminders for future maintenance
- Bottom-tab navigation (Vehicles, Services, Profile)

## Firestore collections

- `users`
- `vehicles`
- `serviceRecords`

## Firebase setup

1. Create a Firebase Android app for package `com.autokeeper.carmaintenancetracker`.
2. Enable Firebase Authentication (Email/Password).
3. Enable Cloud Firestore.
4. Enable Firebase Storage.
5. Download `google-services.json` and place it in `/home/runner/work/CarMaintainanceTracker/CarMaintainanceTracker/app/google-services.json`.

## Build and test

From `/home/runner/work/CarMaintainanceTracker/CarMaintainanceTracker`:

```bash
gradle :app:testDebugUnitTest
```

If your environment has the Gradle wrapper configured, use `./gradlew` instead of `gradle`.
