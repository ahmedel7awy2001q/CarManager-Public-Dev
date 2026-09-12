# CarManager v0.5.0 — Batch Development Plan

> Working branch: `v0.5.0-batch-dev-no-ci`
> 
> Rule: collect and implement the full batch here without GitHub Actions/Release. Build only when the user explicitly says: **قم بالبناء الآن**.

## 1. Design system / UI overhaul
- Replace the current gray/pink-heavy look with a professional automotive visual system.
- Light theme: clean warm-white / off-white background, restrained surfaces, subtle borders/shadows.
- Dark theme: deep graphite automotive palette, not flat black.
- Compact cards, smaller radii/padding, denser information hierarchy, consistent RTL typography.
- Apply consistently to Dashboard, Garage, Maintenance, Fuel, Expenses, Documents, GPS, Account/Sync, Backup, Reports, sheets and dialogs.
- Keyboard-safe forms: scrolling + IME padding + focused-field visibility.

## 2. Multi-vehicle types
- Add vehicle type: Car / Motorcycle / Other.
- Other type exposes a custom vehicle type/name field.
- Vehicle type drives icons, inspection templates, relevant fields, dashboard content, fuel/energy options and reminders.

## 3. Dashboard / Garage
- Professional compact dashboard inspired by modern fleet/garage apps but with original CarManager identity.
- Compact summary metrics: vehicles, alerts, maintenance, monthly cost.
- Needs Attention section only when relevant.
- Compact garage vehicle cards with image/type/name/year/odometer/health state.
- Unified vehicle health score with explainable causes.

## 4. Maintenance intelligence
- Compact maintenance cards with expandable details.
- Searchable preset/service-item selector + custom service item.
- Safe delete/soft-delete of maintenance plans without deleting historical maintenance records.
- Smart upcoming maintenance bundle with grouped service/inspection items.
- Priority per item: Critical / Important / Can defer.
- Show concise visible priority reason (safety, overdue, linked open fault, due soon, etc.).
- Sort items and cost summaries by priority.
- Budget-aware maintenance suggestion: critical first, then important.
- Suggest merging nearby services when reasonable.
- Missing-price quick editor for upcoming bundle.
- Maintenance session workflow: select items, record actual cost, workshop, odometer, notes, invoice; update only completed items.

## 5. Vehicle inspection / health
- Templates by vehicle type: car, motorcycle, other.
- Modes: Quick / Comprehensive / Pre-trip / Custom.
- Template customization: add, hide/delete, reorder, edit name/description, severity/importance.
- Result states: Good / Needs follow-up / Critical.
- Link inspection findings to new/existing fault, maintenance plan, reminder, note and photo.
- Preserve historical inspection snapshots when templates later change.
- Compact inspection UI and historical comparison.
- Vehicle Health Score uses maintenance, faults, latest inspection, documents and selected wear items.

## 6. Fuel / energy
- Redesign into compact summary + transaction history + filters + trends.
- Support Egyptian fuel/energy types relevant to cars/motorcycles/other vehicles: Gasoline 80/92/95, Diesel, CNG, Electricity, Hybrid/multi-energy, Custom where needed.
- Auto-bind default fuel/energy type to vehicle; manual override per transaction.
- Periodic Egyptian market price refresh target: every 7 days using a trusted/official source when available.
- Cache last trusted price locally; transaction remains usable offline.
- Preserve historical price per fueling; market updates never rewrite old transactions.
- Manual price override with source marker (automatic/manual).
- Flexible calculation: amount + price -> liters; liters + price -> amount.
- Full-tank flag for accurate full-to-full consumption calculations.
- Optional station, receipt and note; detect implausible odometer/price values.
- Charging mode for EVs using kWh; hybrid supports fuel + charging.
- Consumption anomaly alert and cost-per-km analytics.

## 7. GPS / trip tracking
- Keep iTrack/eTrack external tracker support.
- Xiaomi Tag / Google Find Hub treated as a tag shortcut/reference, not as continuous GPS telemetry unless an official integration exists.
- Add Phone Tracking mode: manual Start/Stop trip using phone GPS.
- Automatic trip start/stop option using vehicle Bluetooth / Android Auto connection-disconnection.
- Save route, distance, duration, speed metrics and stops to the selected vehicle.
- Optional suggestion to update odometer from recorded trip distance after user approval.
- Use trip distance in fuel/cost/maintenance analytics.

## 8. Documents vault
- Compact document cards and summary strip.
- Types: vehicle license, driver license, insurance, inspection, contracts, authorization, receipts, custom.
- Per-type default expiry reminders: 90 / 30 / 7 days.
- Per-document override: inherit type / custom reminders / disabled.
- Archive previous document version on renewal; keep history.
- Needs Attention integration for upcoming/expired documents without notification spam.
- Optional OCR-assisted data suggestion later, always requiring user confirmation before save.
- Inspection documents show compact summary, not a long embedded checklist.

## 9. Expenses / ownership cost
- Compact expense summary and rows.
- Filters by period/category/vehicle.
- Recurring expenses/reminders (license, insurance, subscriptions, garage, etc.).
- Prevent double-counting: fuel and maintenance remain linked source costs, not duplicated manual expenses.
- TCO: purchase + fuel + maintenance + other expenses - sale price.
- Cost per km / month / year.

## 10. Unified vehicle timeline + search
- Unified chronological timeline for maintenance, fuel, trip, expense, inspection, document, fault, battery/tire/part and odometer events.
- Global search across vehicle history and records.

## 11. Data quality and safety
- Validate odometer monotonicity and suspicious jumps before save.
- Keep account/vehicle ownership isolation by Vehicle ID.
- Soft-delete where historical integrity matters.
- Automatic local/cloud backup checkpoint before destructive operations such as restore, delete vehicle, archive/sale where feasible.
- Local-first behavior remains mandatory.

## 12. Account / sync / backup UX
- Compact Account & Sync UI.
- Clear cloud state, last sync, vehicle count and backup status.
- Backup/restore UI reduced in size and wording while preserving safety warnings.

## 13. Build strategy / GitHub Actions budget
- Do not push batch work to CI-triggered branches during development.
- Do not open a PR to a CI-triggered base until the batch is ready.
- Perform static review and repository consistency checks while editing.
- One final CI/build only after explicit user instruction: **قم بالبناء الآن**.
