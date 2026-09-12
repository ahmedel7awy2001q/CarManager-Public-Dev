package com.ahmed.carmanager.data.maintenance

import com.ahmed.carmanager.data.local.model.FuelType
import com.ahmed.carmanager.data.local.model.TransmissionType
import com.ahmed.carmanager.data.local.model.VehicleEntity

/**
 * Read-only maintenance guidance. It never invents service history or prices.
 * Exact manufacturer profiles are used when we have a sufficiently specific source;
 * otherwise CarManager exposes a conservative reference schedule and labels it as such.
 */
object MaintenanceGuidanceCatalog {

    private const val GENERAL_SOURCE = "مرجع CarManager العام المحافظ — ليس دليل مصنع:"
    private const val OEM_SOURCE = "دليل المصنع الموثق:"
    private const val MARKET_SOURCE = "مرجع سوق/صيانة محلي — ليس دليل مصنع موثق:"

    fun templatesFor(vehicle: VehicleEntity, preset: MaintenancePreset): List<MaintenanceTemplate> {
        val identity = "${vehicle.brand} ${vehicle.model} ${vehicle.displayName.orEmpty()}".lowercase()
        return when {
            vehicle.fuelType == FuelType.ELECTRIC ->
                electricOwnerGuideReference(vehicle).withSource(GENERAL_SOURCE)

            vehicle.year in 2020..2024 && "fiat" in identity && "tipo" in identity ->
                fiatTipo2022Family().withSource("$OEM_SOURCE Fiat Tipo Owner Handbook 2022.")

            ("soueast" in identity || "sou east" in identity || "ساوايست" in identity || "سو ايست" in identity || "سو إيست" in identity) && "dx3" in identity ->
                soueastDx3Reference().withSource(MARKET_SOURCE)

            preset == MaintenancePreset.CERATO_2021_EGYPT ->
                cerato2021Egypt().withSource("$OEM_SOURCE Kia — جدول الصيانة الموثق للملف المعروف، مع توضيح أي بند محلي داخل ملاحظته.")

            else -> universalOwnerGuideReference(vehicle).withSource(GENERAL_SOURCE)
        }
    }

    private fun List<MaintenanceTemplate>.withSource(source: String): List<MaintenanceTemplate> = map { template ->
        val existing = template.notes?.trim().orEmpty()
        template.copy(notes = if (existing.isBlank()) source else "$source $existing")
    }

    /**
     * Universal conservative reference used for combustion and hybrid vehicles when an exact
     * verified manufacturer schedule is not embedded. These are baseline intervals, not an OEM claim.
     */
    private fun universalOwnerGuideReference(vehicle: VehicleEntity): List<MaintenanceTemplate> {
        val core = listOf(
            MaintenanceTemplate(
                "زيت المحرك + فلتر الزيت",
                "المحرك",
                10_000.0,
                12,
                priority = 10,
                notes = "10,000 كم أو 12 شهرًا أيهما أقرب. في الاستخدام الشاق أو الزحام/الحرارة/الأتربة قد يلزم تقصير الفترة إلى نحو 5,000–7,500 كم أو 6 أشهر."
            ),
            MaintenanceTemplate(
                "فلتر التكييف / المقصورة",
                "الفلاتر",
                10_000.0,
                12,
                notes = "استبدال مرجعي كل 10,000 كم أو 12 شهرًا، وأبكر عند ضعف تدفق الهواء أو كثرة الأتربة."
            ),
            MaintenanceTemplate(
                "فلتر هواء المحرك",
                "الفلاتر",
                20_000.0,
                12,
                notes = "استبدال مرجعي كل 20,000 كم أو 12 شهرًا، مع فحصه كل 10,000 كم وخدمة أبكر في الأجواء المغبرة."
            ),
            MaintenanceTemplate(
                "فلتر الوقود — إن كان قابلاً للخدمة",
                "الوقود",
                40_000.0,
                24,
                notes = "مرجع عام للفلتر القابل للاستبدال. بعض السيارات تستخدم فلترًا داخل الخزان بلا فترة دورية؛ تصميم السيارة ودليل المصنع يتفوقان على هذا المرجع."
            ),
            MaintenanceTemplate(
                "فحص شمعات الإشعال (البوجيهات) وتحديد النوع",
                "المحرك",
                30_000.0,
                24,
                notes = "افحص النوع والحالة. البوجيهات العادية غالبًا 30–40 ألف كم، والإيريديوم/البلاتينيوم قد تمتد غالبًا إلى 80–100 ألف كم؛ لا يُعتمد رقم استبدال واحد قبل معرفة النوع والمحرك."
            )
        ) + transmissionMaintenanceReference(vehicle) + commonChassisAndSafetyReference() + listOf(
            MaintenanceTemplate(
                "سائل تبريد المحرك — فحص",
                "السوائل",
                10_000.0,
                12,
                notes = "فحص المستوى والتركيز والتسريب كل 10,000 كم أو 12 شهرًا."
            ),
            MaintenanceTemplate(
                "سائل تبريد المحرك — مراجعة موعد الاستبدال",
                "السوائل",
                60_000.0,
                36,
                notes = "هذه نقطة مراجعة محافظة وليست موعد تغيير موحدًا؛ بعض سوائل Long Life لها أول استبدال أطول بكثير، لذلك نوع السائل ودليل المصنع هما المرجع النهائي للتغيير."
            ),
            MaintenanceTemplate(
                "فحص البطارية 12V ونظام الشحن",
                "الكهرباء",
                10_000.0,
                6,
                notes = "الاختبار الفعلي وحالة البطارية أهم من عمر استبدال ثابت."
            ),
            MaintenanceTemplate(
                "فحص السيور الخارجية والشداد والبكرات",
                "المحرك",
                20_000.0,
                12,
                notes = "فحص دوري؛ موعد الاستبدال يختلف حسب المحرك والمصنع."
            ),
            MaintenanceTemplate(
                "فحص نظام التوقيت (سير/سلسلة)",
                "المحرك",
                40_000.0,
                24,
                notes = "حدد أولًا هل المحرك يستخدم سيرًا أم سلسلة. لا تستبدل على رقم عام بدون مرجع المحرك."
            ),
            MaintenanceTemplate("فحص دورة التبريد والخراطيم", "فحص دوري", 10_000.0, 12),
            MaintenanceTemplate(
                "فحص قواعد المحرك وناقل الحركة",
                "فحص دوري",
                20_000.0,
                12,
                notes = "فحص الاهتزاز والتشققات؛ لا يوجد عمر استبدال ثابت."
            ),
            MaintenanceTemplate("فحص PCV وخراطيم الفاكيوم", "فحص دوري", 20_000.0, 12),
            MaintenanceTemplate("فحص منظومة الوقود والتسريب", "فحص دوري", 20_000.0, 12)
        )

        val hybrid = if (vehicle.fuelType == FuelType.HYBRID) listOf(
            MaintenanceTemplate(
                "فحص بطارية ومنظومة الجهد العالي للهايبرد",
                "الجهد العالي",
                20_000.0,
                12,
                notes = "فحص رسائل النظام وحالة البطارية ومسارات/سوائل التبريد خارجيًا فقط. أي خدمة جهد عالٍ لفني مؤهل."
            ),
            MaintenanceTemplate(
                "مراجعة تبريد بطارية/إنفرتر الهايبرد",
                "الجهد العالي",
                20_000.0,
                12,
                notes = "افحص مسار الهواء أو السائل حسب تصميم السيارة، وراجع دليل المصنع قبل أي تغيير سائل."
            )
        ) else emptyList()

        return core + hybrid
    }

    /**
     * Compact active-plan fallback for pure EVs. It intentionally excludes engine oil, fuel,
     * spark plugs and conventional AT/CVT service. The comprehensive OwnerGuideCatalog still shows
     * the larger read-only EV guide, while this list contains only useful recurring reminders.
     */
    private fun electricOwnerGuideReference(vehicle: VehicleEntity): List<MaintenanceTemplate> =
        listOf(
            MaintenanceTemplate(
                "فلتر التكييف / المقصورة",
                "الفلاتر",
                10_000.0,
                12,
                priority = 8,
                notes = "استبدال مرجعي كل 10,000 كم أو 12 شهرًا، وأبكر مع الأتربة أو ضعف تدفق الهواء."
            ),
            MaintenanceTemplate(
                "فحص بطارية ومنظومة الجهد العالي",
                "الجهد العالي",
                20_000.0,
                12,
                priority = 9,
                notes = "فحص الحالة ورسائل النظام والتلف الخارجي فقط. لا تُفتح أو تُخدم مكونات الجهد العالي إلا بواسطة فني مؤهل."
            ),
            MaintenanceTemplate(
                "فحص تبريد البطارية والإنفرتر",
                "الجهد العالي",
                20_000.0,
                12,
                notes = "راجع سائل أو مسارات هواء التبريد حسب تصميم السيارة. مواصفة السائل وفترة تغييره من دليل المصنع."
            ),
            MaintenanceTemplate(
                "مراجعة موعد تغيير سائل دائرة الجهد العالي",
                "الجهد العالي",
                40_000.0,
                24,
                notes = "نقطة مراجعة فقط وليست موعد تغيير موحدًا؛ الفترات تختلف بشدة بين الشركات."
            ),
            MaintenanceTemplate(
                "مراجعة زيت علبة التخفيض / Reduction Gear",
                "نظام الدفع الكهربائي",
                40_000.0,
                36,
                notes = "نقطة فحص ومراجعة؛ لا تفترض ATF أو CVT لسيارة كهربائية. اتبع مواصفة ووثيقة المصنع."
            ),
            MaintenanceTemplate(
                "فحص منفذ وكابل الشحن",
                "الشحن الكهربائي",
                10_000.0,
                12,
                notes = "فحص التلف والرطوبة أو السخونة غير الطبيعية. أوقف الاستخدام عند وجود تلف."
            ),
            MaintenanceTemplate(
                "فحص البطارية 12V",
                "الكهرباء",
                10_000.0,
                6,
                notes = "السيارات الكهربائية ما زالت تستخدم بطارية 12V للأنظمة المساعدة؛ اختبر حالتها ولا تعتمد عمرًا ثابتًا للتغيير."
            )
        ) + commonChassisAndSafetyReference()

    private fun commonChassisAndSafetyReference(): List<MaintenanceTemplate> = listOf(
        MaintenanceTemplate(
            "سائل الفرامل",
            "الفرامل",
            intervalMonths = 24,
            notes = "تغيير مرجعي كل 24 شهرًا مع فحص المستوى والحالة في كل صيانة دورية. إذا حدد المصنع فترة أقصر فتُتبع."
        ),
        MaintenanceTemplate(
            "فحص تيل الفرامل الأمامي",
            "الفرامل",
            10_000.0,
            6,
            notes = "فحص السمك والتآكل؛ الاستبدال حسب القياس والحالة وليس عند مسافة ثابتة."
        ),
        MaintenanceTemplate(
            "فحص الفرامل الخلفية",
            "الفرامل",
            10_000.0,
            6,
            notes = "فحص التيل/الأحذية/الهوبات حسب تصميم السيارة وحالة التآكل؛ الاستبدال حسب الحالة."
        ),
        MaintenanceTemplate(
            "فحص أقراص / طنابير الفرامل والخراطيم",
            "الفرامل",
            10_000.0,
            12,
            notes = "الاستبدال بعد القياس والفحص وليس عند رقم ثابت."
        ),
        MaintenanceTemplate(
            "فحص الإطارات والضغط والنقشة",
            "الإطارات",
            10_000.0,
            6,
            notes = "فحص الضغط والتآكل والتشققات والعمر الزمني دوريًا."
        ),
        MaintenanceTemplate(
            "تدوير الإطارات",
            "الإطارات",
            10_000.0,
            6,
            notes = "مرجع عام لتحسين انتظام التآكل؛ اتبع نمط التدوير المسموح لسيارتك وإطاراتك."
        ),
        MaintenanceTemplate(
            "زوايا وترصيص",
            "الإطارات",
            10_000.0,
            12,
            notes = "فحص مرجعي، وينفذ فورًا عند السحب أو الاهتزاز أو التآكل غير المنتظم."
        ),
        MaintenanceTemplate(
            "فحص العفشة والمساعدين والمقصات والجلب",
            "فحص دوري",
            10_000.0,
            12,
            notes = "الاستبدال حسب الفحص والأعراض وحالة الطرق."
        ),
        MaintenanceTemplate("فحص الكبالن/أنصاف المحاور والجلد والمفاصل", "فحص دوري", 10_000.0, 12),
        MaintenanceTemplate("فحص نظام التوجيه", "فحص دوري", 10_000.0, 12),
        MaintenanceTemplate(
            "فحص التكييف وكفاءة التبريد",
            "التكييف",
            20_000.0,
            12,
            notes = "فحص الكفاءة والتسريب؛ شحن الفريون ليس صيانة دورية ثابتة إذا كانت المنظومة سليمة."
        ),
        MaintenanceTemplate("فحص المساحات ورشاشات الزجاج", "فحص دوري", 10_000.0, 6),
        MaintenanceTemplate("فحص الأنوار واللمبات", "فحص دوري", 10_000.0, 6),
        MaintenanceTemplate("فحص أحزمة الأمان ومثبتاتها", "فحص دوري", 20_000.0, 12),
        MaintenanceTemplate("فحص أسفل السيارة والتسريبات", "فحص دوري", 10_000.0, 12)
    )

    private fun transmissionMaintenanceReference(vehicle: VehicleEntity): MaintenanceTemplate {
        if (vehicle.fuelType == FuelType.ELECTRIC) {
            return MaintenanceTemplate(
                "مراجعة زيت علبة التخفيض / Reduction Gear",
                "نظام الدفع الكهربائي",
                40_000.0,
                36,
                priority = 8,
                notes = "لا تفترض زيت ناقل حركة AT/CVT للسيارة الكهربائية؛ راجع دليل ووصف وحدة الدفع الفعلية."
            )
        }
        val transmissionIdentity = "${vehicle.transmissionName.orEmpty()} ${vehicle.transmissionCode.orEmpty()}".lowercase()
        return when {
            vehicle.transmissionType == TransmissionType.CVT || "cvt" in transmissionIdentity || "ivt" in transmissionIdentity ->
                MaintenanceTemplate(
                    "زيت ناقل الحركة CVT / IVT",
                    "ناقل الحركة",
                    40_000.0,
                    36,
                    priority = 8,
                    notes = "مرجع محافظ: 40,000 كم أو 3 سنوات أيهما أقرب. استخدم فقط مواصفة زيت CVT/IVT الصحيحة للفتيس، وإذا حدد المصنع فترة مختلفة فتتغلب على هذا المرجع."
                )

            vehicle.transmissionType == TransmissionType.DCT || "dct" in transmissionIdentity ->
                MaintenanceTemplate(
                    "زيت ناقل الحركة DCT",
                    "ناقل الحركة",
                    60_000.0,
                    36,
                    priority = 8,
                    notes = "مرجع محافظ: 60,000 كم أو 3 سنوات. نوع الزيت وإجراء الخدمة يختلفان بين DCT الجاف والرطب؛ دليل المصنع يتفوق على المرجع العام."
                )

            vehicle.transmissionType == TransmissionType.MANUAL ->
                MaintenanceTemplate(
                    "زيت ناقل الحركة اليدوي",
                    "ناقل الحركة",
                    60_000.0,
                    48,
                    priority = 8,
                    notes = "مرجع محافظ: 60,000 كم أو 4 سنوات. مواصفة الزيت ودليل المصنع هما المرجع النهائي."
                )

            vehicle.transmissionType == TransmissionType.AUTOMATIC ->
                MaintenanceTemplate(
                    "زيت ناقل الحركة الأوتوماتيك AT",
                    "ناقل الحركة",
                    60_000.0,
                    36,
                    priority = 8,
                    notes = "مرجع محافظ: 60,000 كم أو 3 سنوات أيهما أقرب. لا تستخدم زيتًا عامًا؛ يجب مطابقة مواصفة ATF وكود الفتيس."
                )

            else ->
                MaintenanceTemplate(
                    "فحص زيت ناقل الحركة وتحديد نوع الفتيس",
                    "ناقل الحركة",
                    40_000.0,
                    36,
                    priority = 8,
                    notes = "نوع ناقل الحركة غير محدد بما يكفي. هذه نقطة فحص ومراجعة فقط؛ حدّد CVT/IVT أو AT أو DCT أو Manual قبل اعتماد موعد تغيير الزيت."
                )
        }
    }

    /** Fiat Tipo family, model-year 2022 handbook service structure. */
    private fun fiatTipo2022Family(): List<MaintenanceTemplate> = listOf(
        MaintenanceTemplate("فحص الصيانة الدورية — Fiat Tipo", "فحص دوري", 15_000.0, 12, priority = 10,
            notes = "جدول الخدمة مبني على خطوات 15,000 كم، مع اختلاف بعض البنود حسب نسخة المحرك والسوق."),
        MaintenanceTemplate("زيت المحرك + فلتر الزيت", "المحرك", 15_000.0, 12, priority = 10,
            notes = "موعد الزيت يعتمد على ظروف الاستخدام/مؤشر السيارة، وفي جميع الأحوال لا يتجاوز سنة للبنزين في هذا الدليل. 15,000 كم هي نقطة الخدمة الدورية؛ قد يلزم أبكر."),
        MaintenanceTemplate("فلتر هواء المحرك", "الفلاتر", 15_000.0, 12,
            notes = "في المناطق كثيرة الأتربة يُستبدل كل 15,000 كم؛ في الظروف الأخرى اتبع صف المحرك بجدول الخدمة."),
        MaintenanceTemplate("فلتر التكييف / المقصورة", "الفلاتر", 15_000.0, 12,
            notes = "فحص/استبدال مع الخدمة الدورية، وأبكر في الغبار أو ضعف تدفق الهواء."),
        MaintenanceTemplate("سائل الفرامل", "الفرامل", intervalMonths = 24,
            notes = "تغيير سائل الفرامل كل سنتين بغض النظر عن المسافة."),
        MaintenanceTemplate("فحص تيل وأقراص الفرامل", "الفرامل", 15_000.0, 12,
            notes = "فحص دوري؛ الاستبدال حسب السمك والحالة."),
        MaintenanceTemplate("فحص مستوى سائل التبريد", "السوائل", 15_000.0, 12),
        MaintenanceTemplate("فحص السيور الخارجية", "المحرك", 15_000.0, 12),
        MaintenanceTemplate("فحص شمعات الإشعال / تحديد فترة النسخة", "المحرك", 30_000.0, 24,
            notes = "فترة استبدال البوجيهات تعتمد على نسخة محرك Tipo. أدخل المحرك/الكود للحصول على تطابق أدق ولا تعتمد على رقم عام للاستبدال."),
        MaintenanceTemplate("زيت ناقل الحركة — 1.5 Mild Hybrid فقط", "ناقل الحركة", 60_000.0, 72,
            notes = "بند تغيير زيت ناقل الحركة المذكور لنسخة 1.5 Mild Hybrid كل 60,000 كم أو 6 سنوات. تجاهله إن لم تكن سيارتك هذه النسخة."),
        MaintenanceTemplate("فحص نظام التوقيت / تحديد نسخة المحرك", "المحرك", 30_000.0, 24,
            notes = "نوع وفترة سير/سلسلة التوقيت تختلف حسب المحرك؛ يتطلب تحديد نسخة المحرك قبل إعطاء موعد استبدال مؤكد."),
        MaintenanceTemplate("فحص الإطارات والضغط والنقشة", "الإطارات", 15_000.0, 12),
        MaintenanceTemplate("فحص البطارية ونظام الشحن", "الكهرباء", 15_000.0, 12),
        MaintenanceTemplate("فحص العفشة والمفاصل والجلب", "فحص دوري", 15_000.0, 12),
        MaintenanceTemplate("فحص الخراطيم والتسريبات", "فحص دوري", 15_000.0, 12),
        MaintenanceTemplate("فحص المساحات والأنوار", "فحص دوري", 15_000.0, 12)
    )

    /**
     * DX3 Egypt reference. Exact VIN/engine schedule is not published in our verified OEM data yet,
     * so this is deliberately labelled as a market reference rather than an OEM claim.
     */
    private fun soueastDx3Reference(): List<MaintenanceTemplate> = listOf(
        MaintenanceTemplate("صيانة دورية SouEast DX3", "فحص دوري", 10_000.0, 6, priority = 10,
            notes = "دورة الصيانة الشائعة لـ SouEast كل 10,000 كم أو 6 أشهر. الجدول الدقيق للـVIN/المحرك من الوكيل أو دليل السيارة يتفوق على هذا المرجع."),
        MaintenanceTemplate("زيت المحرك + فلتر الزيت", "المحرك", 10_000.0, 6, priority = 10,
            notes = "مرجع سوق مصر المحافظ لـ DX3؛ استخدم مواصفة ولزوجة الزيت المحددة للمحرك الفعلي."),
        MaintenanceTemplate("فلتر هواء المحرك", "الفلاتر", 10_000.0, 6,
            notes = "فحص مع كل صيانة دورية؛ الاستبدال حسب الاتساخ/تعليمات الوكيل."),
        MaintenanceTemplate("فلتر التكييف / المقصورة", "الفلاتر", 10_000.0, 6),
        MaintenanceTemplate("فحص شمعات الإشعال (البوجيهات)", "المحرك", 20_000.0, 12,
            notes = "موعد الاستبدال يتطلب معرفة المحرك ونوع البوجيه؛ لا يُفترض تلقائيًا."),
        MaintenanceTemplate("فحص زيت ناقل الحركة CVT/الفتيس", "ناقل الحركة", 20_000.0, 12,
            notes = "DX3 توجد منه نسخ CVT ونسخ أخرى. افحص النوع والمواصفة أولًا؛ لا تستخدم زيتًا عامًا ولا تعتمد موعد استبدال بلا كود ناقل الحركة."),
        MaintenanceTemplate("سائل الفرامل", "الفرامل", intervalMonths = 24,
            notes = "مرجع محافظ؛ تحقق من جدول الوكيل/دليل الـVIN."),
        MaintenanceTemplate("فحص سائل تبريد المحرك", "السوائل", 10_000.0, 6),
        MaintenanceTemplate("مراجعة موعد استبدال سائل التبريد", "السوائل", 40_000.0, 24,
            notes = "نقطة مراجعة وليست ادعاء مصنع؛ نوع السائل والمحرك يحددان الفترة النهائية."),
        MaintenanceTemplate("فحص تيل وأقراص الفرامل", "الفرامل", 10_000.0, 6),
        MaintenanceTemplate("فحص الإطارات والضغط والنقشة", "الإطارات", 10_000.0, 6),
        MaintenanceTemplate("تدوير الإطارات", "الإطارات", 10_000.0, 6),
        MaintenanceTemplate("فحص البطارية ونظام الشحن", "الكهرباء", 10_000.0, 6),
        MaintenanceTemplate("فحص السيور والشدادات", "المحرك", 20_000.0, 12),
        MaintenanceTemplate("فحص العفشة والمساعدين والمفاصل", "فحص دوري", 10_000.0, 6),
        MaintenanceTemplate("فحص نظام التوجيه", "فحص دوري", 10_000.0, 6),
        MaintenanceTemplate("فحص الخراطيم والتسريبات", "فحص دوري", 10_000.0, 6),
        MaintenanceTemplate("فحص التكييف والمساحات والأنوار", "فحص دوري", 10_000.0, 6)
    )

    private fun cerato2021Egypt(): List<MaintenanceTemplate> = listOf(
        MaintenanceTemplate("زيت المحرك + فلتر الزيت", "المحرك", 10_000.0, 12, priority = 10,
            notes = "دليل Kia لمصر/الشرق الأوسط: استخدام عادي 10,000 كم أو 12 شهر. في الاستخدام الشاق 5,000 كم أو 6 أشهر."),
        MaintenanceTemplate("زيت المحرك + فلتر الزيت — استخدام شاق", "المحرك", 5_000.0, 6,
            notes = "فعّل هذا الجدول بدل العادي إذا كانت ظروف الاستخدام الشاق تنطبق على سيارتك."),
        MaintenanceTemplate("فلتر هواء المحرك", "الفلاتر", 15_000.0, 12, notes = "قد يحتاج خدمة أبكر مع الغبار."),
        MaintenanceTemplate("فلتر التكييف / المقصورة", "الفلاتر", 15_000.0, 12),
        MaintenanceTemplate("شمعات الإشعال (البوجيهات) 1.6 MPI", "المحرك", 60_000.0, notes = "فترة مرجعية لمحرك 1.6 MPI."),
        MaintenanceTemplate("سائل الفرامل", "الفرامل", 30_000.0, 24),
        MaintenanceTemplate("زيت ناقل الحركة الأوتوماتيك — استخدام شاق", "ناقل الحركة", 90_000.0,
            notes = "في الاستخدام العادي لا يفرض الدليل خدمة دورية مماثلة؛ في الشاق يذكر 90,000 كم."),
        MaintenanceTemplate("سائل تبريد المحرك — أول استبدال", "السوائل", 210_000.0, 120),
        MaintenanceTemplate("سائل تبريد المحرك — بعد أول استبدال", "السوائل", 30_000.0, 24,
            notes = "يستخدم بعد تنفيذ أول استبدال فقط."),
        MaintenanceTemplate("فحص سير المجموعة / السيور الخارجية", "المحرك", 10_000.0, 12),
        MaintenanceTemplate("استبدال سير المجموعة / السيور الخارجية", "المحرك", 50_000.0, 24),
        MaintenanceTemplate("فحص تيل الفرامل الأمامي", "الفرامل", 10_000.0, 12),
        MaintenanceTemplate("فحص الفرامل الخلفية", "الفرامل", 10_000.0, 12),
        MaintenanceTemplate("فحص الأقراص/الطنابير والخراطيم", "الفرامل", 10_000.0, 12),
        MaintenanceTemplate("فحص البطارية 12V ونظام الشحن", "الكهرباء", 10_000.0, 6),
        MaintenanceTemplate("فلتر الوقود", "الوقود", 40_000.0, 24,
            notes = "خطة محافظة للسوق المحلي؛ راجع الحالة وجودة الوقود."),
        MaintenanceTemplate("فحص الإطارات والضغط والنقشة", "الإطارات", 10_000.0, 6),
        MaintenanceTemplate("تدوير الإطارات", "الإطارات", 10_000.0, 6),
        MaintenanceTemplate("زوايا وترصيص", "الإطارات", 10_000.0, 12),
        MaintenanceTemplate("فحص العفشة والمساعدين والمقصات والجلب", "فحص دوري", 10_000.0, 12),
        MaintenanceTemplate("فحص الكبالن والجلد والمفاصل", "فحص دوري", 10_000.0, 12),
        MaintenanceTemplate("فحص قواعد المحرك وناقل الحركة", "فحص دوري", 20_000.0, 12),
        MaintenanceTemplate("فحص دورة التبريد والخراطيم", "فحص دوري", 10_000.0, 12),
        MaintenanceTemplate("فحص PCV وخراطيم الفاكيوم", "فحص دوري", 20_000.0, 12),
        MaintenanceTemplate("فحص التكييف وكفاءة التبريد", "التكييف", 20_000.0, 12),
        MaintenanceTemplate("فحص المساحات ورشاشات الزجاج", "فحص دوري", 10_000.0, 6),
        MaintenanceTemplate("فحص الأنوار واللمبات", "فحص دوري", 10_000.0, 6)
    )
}
