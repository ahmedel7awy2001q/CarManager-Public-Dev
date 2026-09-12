# CarManager v0.8.0 — Premium Automotive Revolution

## Scope
This release is a visual/UX reconstruction over the existing data and feature engine. It does not reset Room data and does not change the applicationId.

## Identity preserved
- applicationId: com.ahmed.carmanager
- Room schema: v4
- migrations/data preservation retained
- permanent signing identity must remain unchanged
- Android Auto Main-dispatcher hotfix retained

## Major UI/UX changes
- New cockpit-style home dashboard with a dark automotive vehicle hero, clearer odometer/maintenance information and far lower visual noise.
- New multi-colour semantic palette for fuel, maintenance, expenses, reports, health, warnings and tracking.
- New custom bottom navigation with a prominent quick-add control.
- More visual Garage vehicle cards and easier-to-scan metrics.
- Maintenance cockpit header and high-contrast primary maintenance action.
- Reports cockpit and coloured cost categories.
- Control Hub redesign replaces the old long Settings-style list with feature tiles.
- Colour-coded Quick Add sheet.
- Premium dark gradient headers propagated to screens using shared premium headers.
- Vehicle Health hero redesigned as a cockpit health gauge.
- Fuel, expenses, documents, timeline and GPS entry surfaces refreshed.
- New approved CarManager app icon and Android 12+ splash treatment.

## Tracking accessory centre
- Adds local association for Xiaomi Tag / Apple Find My accessories for key and in-vehicle slots.
- This is deliberately not represented as live GPS: Apple Find My does not expose a public Android live-location API to CarManager.
- Existing PHONE / HEAD_UNIT / GPS sources remain the live/telemetry sources supported by CarManager.

## Accuracy principles retained
- no mileage predictions are written as real odometer data
- estimates remain visibly separate from user-recorded facts
- ThinkDiag/DTC data is not presented as final mechanical diagnosis
- GPS movement alone does not identify a vehicle

## Professional polish pass
- Approved final CarManager icon integrated as the real launcher identity with adaptive icon resources and Android 12+ splash usage.
- Cockpit hero gauges upgraded from generic circular indicators to dedicated animated automotive gauge meters.
- Reports upgraded with a real multi-segment cost donut plus semantic legend while retaining exact numeric breakdowns below it.
- Shared visual primitives keep motion, colour meaning and status hierarchy consistent without turning every screen into the same card layout.

## Android launcher polish
- Android adaptive launcher icon now uses the approved CarManager artwork.
- Android 13+ themed icon support uses a dedicated monochrome automotive mark instead of reducing the full-colour artwork badly.
- The splash screen uses the same new identity for continuity from launcher to app.

## Repair experience
- Open faults now show a compact repair journey: reported → diagnosed → in repair → resolved.
- This reuses the existing FaultStatus truth and does not invent a separate repair state or database migration.

## Secondary-surface redesign
- Appearance selector rebuilt as a visual cockpit-style configurator with live identity preview and semantic colour explanation.
- Local backup/restore rebuilt as a premium safety surface with clear export/import actions and explicit restore safeguards.
- These surfaces now use the same Premium Automotive visual language instead of falling back to the older utility-card style.

## Account experience
- Sign-in screen now uses the approved CarManager identity and a premium automotive entry surface rather than a generic car glyph.
- Account sheet receives the same cockpit header/profile treatment and semantic cloud/vehicle metrics while preserving authentication and account-isolation behaviour.
