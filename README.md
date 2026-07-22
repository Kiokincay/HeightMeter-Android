# Height Meter Android APK

A native Android application for Arduino ultrasonic height measurement through HC-05 or HC-06 Bluetooth Classic.

## Included

- Landscape Android application
- Manual HC-05/HC-06 pairing and selection
- No random Bluetooth device connection
- `C` calibration command
- `M` measurement command
- Real serial height parsing, including `HEIGHT:171.5`, `HEIGHT=171.5`, or `171.5`
- Centimeter and meter display
- Animated ruler and measurement history
- First-run country selection and automatic localized date/time
- Language and System/Dark/Light appearance settings
- Guest mode, sign-in, sign-up, account panel, and 8-digit verification interface
- Developer Options under the upper-right Guest/Account control
- Developer passcode: `9`
- Supabase fields for real cross-device accounts and email verification

## Build the APK entirely in Chrome with GitHub Actions

1. Sign in to GitHub in Chrome.
2. Create a new empty repository.
3. Upload every file and folder from this project, including the hidden `.github` folder.
4. Commit the files to the `main` branch.
5. Open the repository's **Actions** tab.
6. Select **Build Height Meter APK**.
7. Press **Run workflow**.
8. When the workflow finishes, open it and download the artifact named **HeightMeter-Android-APK**.
9. Extract the ZIP and install `HeightMeter-v1.1.0.apk` on Android.

The generated debug APK is already signed by the Android build system and can be installed directly for testing.

## Android use

1. Open the app.
2. Press **Connect Bluetooth**.
3. Android Bluetooth settings open.
4. Pair HC-05 or HC-06, usually with PIN `1234` or `0000`.
5. Return to the app.
6. Select the paired HC-05/HC-06 explicitly.
7. Calibrate with the measurement area empty.
8. Measure the person.

## Arduino serial protocol

App to Arduino:

- `C\n` — calibrate
- `M\n` — measure

Arduino to app examples:

- `CALIBRATION_OK`
- `HEIGHT:171.5`
- `HEIGHT=171.5`
- `171.5`
- `NOT_ENOUGH_VALID_READINGS`

All height values are interpreted as centimeters.

## Real email verification

The interface is ready for Supabase authentication, but real emails require:

- Supabase project URL
- Supabase publishable/anon key
- SMTP configuration
- Email OTP length set to 8 digits

Enter the project URL and key inside **Developer Options**. The app never asks for the user's Gmail or Outlook mailbox password; it uses a Height Meter account password and verifies mailbox ownership with the emailed code.


## Version 1.1.0
See `CHANGELOG-v1.1.0.md` and `UPDATE-INSTRUCTIONS.txt`.
