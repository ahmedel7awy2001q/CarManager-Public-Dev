# CarManager v0.9.0 — UX Intelligence & Parts Platform

## Release intent
v0.9.0 is an in-place upgrade over v0.8.0. It keeps the same applicationId, permanent signing certificate, Room v4 database and all existing user data. No destructive migration is used.

## UX / navigation
- Real page back-stack: Back returns to the actual previous screen step-by-step; Home alone prompts to exit.
- Light/dark system-bar icon contrast correction.
- Home vehicle hero replaces the low-value garage/change control with real recent fuel-consumption insight; insufficient data is stated explicitly.
- Maintenance is grouped into Now / Soon / Later and supports a multi-item service visit.
- Vehicle Health aligns score label/color, explains key causes and makes priority/fault counters actionable.
- Reports avoid implying a trend from one month and explain when distance or full-to-full fuel data is insufficient.
- Control Center Tune action is functional: up to six persistent favorite shortcuts and collapsible sections.
- Currency display standardized to EGP (ج.م).

## Parts & Maintenance Intelligence
- Vehicle-specific maintenance guide built from MaintenanceGuidanceCatalog; manufacturer guidance remains separate from user plan and actual service history.
- “What do I need now?” and next-10,000-km service-preparation view.
- Vehicle guide search by part/service/category; each item shows service kind, guidance, matching user plan, latest actual service and related fault signals when available.
- Provider-agnostic store/source manager with: Official API, CarManager Connector, Search Template, External Link and Manual Source concepts.
- Connection states: Connected, Search Available, Partial, External, Stale, Error and Paused.
- Auto Spare is included as a search provider only. v0.9.0 does not claim live price/stock because no official API/connector is configured.
- Users can add their own store/source URL and optional search template, enable/disable it and test reachability.
- Service shopping list: Needed → Purchased → Installed. Installation proposes/creates maintenance history only on explicit user confirmation.
- Local price-watch list with optional target price. Automated threshold notifications require a future provider API/connector; no fake background live monitoring is claimed.
- Existing installed-parts history remains available and can still be added.

## Cross-fit / compatibility intelligence
- Compatibility records are vehicle-specific and can document source, source reference, original OEM, alternative vehicle/part/number, confidence, fitment mode, measurements, a persisted photo URI, notes and verification time.
- Confidence labels: Verified / High / Medium / Low / Unverified.
- Fitment labels: Direct / Minor modification / Major modification / Unverified.
- Safety notice: brakes, steering, suspension and other safety-critical parts need stronger evidence than visual similarity or a single community report.
- Alternative parts are never silently represented as OEM or manufacturer-approved.

## Data model / sync note
To protect the production Room database during this release, new marketplace/source/watchlist/compatibility metadata is stored locally in app preferences in v0.9.0. Existing core vehicle/maintenance/part/fault data stays in Room v4 and existing backup/cloud mechanisms. A later schema migration can promote marketplace metadata to synced Room/cloud entities after field usage stabilizes.

## Existing capability preserved
Multi-vehicle data, maintenance plans/history, inspections, Vehicle Health, faults, ThinkDiag workflow, GPS/PHONE/HEAD_UNIT, trusted Bluetooth, Android Auto, trip tracking, fuel records, documents, expenses/TCO, Timeline, reports/export, Firebase auth/cloud snapshot, appearance and reminders remain intact.

## Explicit limitations preserved
- PHONE auto-trip start is conservative and is not guaranteed from a killed process.
- ThinkDiag PDF parsing is not represented as a complete real parser without a supported real sample/parser implementation.
- DTC codes do not prove that a component must be replaced.
- Apple Find My / Xiaomi Tag is not represented as continuous in-app GPS telemetry.
- Store prices/stock are only live when a real provider API or supported connector supplies them.
