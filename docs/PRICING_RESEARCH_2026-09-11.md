# CarManager Parts Pricing Research — 2026-09-11

Working notes for the Parts & Maintenance Intelligence update. No release build should be triggered until the complete change set is reviewed and explicitly authorized.

## Verified / targeted Egyptian-market sources

- Auto Spare — https://autospare.com.eg/ — vehicle-specific spare-parts catalog with published prices.
- Tawfiqia — https://www.tawfiqia.com/ar/shop — Egyptian automotive marketplace with product search.
- Zait & Filters — https://zaitandfilters.com/store — oils/filters and vehicle-aware store filters.
- Fit & Fix — https://www.fitandfix.com/ar — Egypt retail/service source, especially tires, batteries, oils and related products.
- Esteraad & Gdeed — https://www.esteraadwegdeed.com/ — Egyptian spare-parts source.
- Feteha Bros — https://fetehabross.com/ — Egyptian automotive parts source.
- Dawaasa — https://www.dawaasa.com/ — Egyptian parts storefront/search.
- Ajyad Auto — https://ajyadauto.com/ar-eg/shop — Egyptian parts catalog.
- Spare Zone — https://sparezone-eg.com/ — Egyptian spare-parts source.
- Kia Egypt / EIT — https://www.kia.com/eg/ — official local brand/service reference; no price is fabricated when not published.
- AutoCycle Egypt — https://www.autocycle.com.eg/ — Egyptian automotive marketplace/source.
- Dawar — https://www.dawar-app.com/ — Egyptian parts/service marketplace that supports saved-car filtering.
- Al Mohandes Auto Parts — https://almohandesautoparts.com/ — Egyptian store with vehicle-fitment filtering.
- SAK Spare Parts — https://sakspareparts.com/ — Egyptian spare-parts storefront.
- El Ebiary — https://el-ebiary.com/ — Egyptian importer/store with published products and prices.
- PUPPO Auto Parts — https://www.puppo.store/ — Egyptian parts source.
- Jumia Egypt — https://www.jumia.com.eg/ar/automobile-replacement-parts/ — Egypt marketplace; shopping/reference source, not fitment authority.
- Dynamu — https://dynamu.co/ar/home/ — Egyptian parts marketplace/aggregator with car-based discovery.

## Product rules

1. Never label a price Live unless it came from an actually connected API/connector with a successful current refresh.
2. Search/open-link-only providers show external-search behavior and no fabricated price or stock.
3. Every displayed price includes source/store, availability state where detectable, checked time, and source URL.
4. Fitment is independent from price. A price result does not prove compatibility; OEM/part number remains final verification.
5. Search is locked to the saved vehicle identity before any result is displayed.
6. Known multi-generation models require generation/year/market-name evidence unless the provider itself is a vehicle-specific catalog.
7. Queries use local market aliases and generation years; unrelated vehicle results are discarded.
8. Provider-specific direct search is preferred where a stable search URL is available.
9. Market aliases may be learned conservatively from already filtered Egyptian listings and stored per vehicle.
