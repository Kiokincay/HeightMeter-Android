# Height Meter v1.1.0

## Added and fixed
- Android Follow phone theme now receives live system dark/light changes.
- Removed forced landscape orientation; Android now follows the user's auto-rotate and orientation lock.
- Added gender and date of birth during account creation.
- Added editable gender and date of birth in Settings and Account.
- Moved minimum and maximum accepted height into Settings.
- Added measurement-to-measurement growth comparison: taller, shorter, or unchanged.
- Added overall comparison from the first saved measurement.
- History date and time now use the selected country's time zone.
- Added an Open Gmail button to the email verification screen.
- Replaced the weak numeric developer code with a hashed private owner passphrase and lockout after repeated failures.
- Added developer diagnostics, raw Bluetooth log, custom Bluetooth command, history export, calibration reset, diagnostics copy, local-data clear, and configuration restore tools.
- Preserved HC-05/HC-06 connection, C calibration command, M measurement command, ruler, units, history, countries, languages, account and guest modes.

## Email note
A verification code is read from Gmail/email and entered inside Height Meter. Real delivery requires a configured Supabase project and email/SMTP settings. Preview mode remains available when cloud settings are absent.
