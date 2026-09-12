package com.ahmed.carmanager.data.maintenance

import com.ahmed.carmanager.data.local.model.FuelType
import com.ahmed.carmanager.data.local.model.TransmissionType
import com.ahmed.carmanager.data.local.model.VehicleEntity

/**
 * A read-only owner-guide knowledge layer. It is intentionally independent from MaintenancePlanEntity:
 * showing a comprehensive guide must never create dozens of active maintenance reminders.
 */
enum class OwnerGuideSourceType {
    OEM_VERIFIED,
    MARKET_REFERENCE,
    CARMANAGER_GENERAL
}

enum class OwnerGuideAction {
    INSPECT,
    REPLACE,
    CHANGE_FLUID,
    ROTATE,
    CLEAN_OR_SERVICE,
    REVIEW_OEM
}

data class OwnerGuideItem(
    val id: String,
    val titleAr: String,
    val categoryAr: String,
    val action: OwnerGuideAction,
    val intervalKm: Double? = null,
    val intervalMonths: Int? = null,
    val sourceType: OwnerGuideSourceType = OwnerGuideSourceType.CARMANAGER_GENERAL,
    val sourceLabelAr: String = "مرجع CarManager العام المحافظ",
    val noteAr: String,
    val safetyCritical: Boolean = false
)

object OwnerGuideCatalog {

    /**
     * Complete general guide for a passenger vehicle. Exact OEM data can later overlay an item by id.
     * The general intervals are conservative reference points, never a claim that every manufacturer
     * specifies the same replacement period.
     */
    fun itemsFor(vehicle: VehicleEntity): List<OwnerGuideItem> {
        val electric = vehicle.fuelType == FuelType.ELECTRIC
        val combustion = !electric
        val base = buildList {
            if (combustion) addAll(engineAndIntake())
            if (electric) addAll(electricDrivetrain()) else addAll(transmission(vehicle))
            addAll(brakes())
            if (combustion) addAll(coolingAndFuel())
            addAll(suspensionSteeringAndDrive())
            addAll(tiresAndWheels())
            addAll(electrical(vehicle))
            addAll(hvac())
            addAll(safetyBodyAndVisibility())
            if (combustion) addAll(exhaustAndEmissions())
        }
        return base.distinctBy { it.id }
    }

    private fun item(
        id: String,
        title: String,
        category: String,
        action: OwnerGuideAction,
        km: Double? = null,
        months: Int? = null,
        note: String,
        safety: Boolean = false
    ) = OwnerGuideItem(
        id = id,
        titleAr = title,
        categoryAr = category,
        action = action,
        intervalKm = km,
        intervalMonths = months,
        noteAr = note,
        safetyCritical = safety
    )

    private fun engineAndIntake() = listOf(
        item("engine_oil", "زيت المحرك", "المحرك والزيوت", OwnerGuideAction.CHANGE_FLUID, 10_000.0, 12, "تغيير محافظ كل 10,000 كم أو 12 شهرًا أيهما أقرب. يُقصّر مع الاستخدام الشاق إذا أوصى المصنع بذلك."),
        item("engine_oil_filter", "فلتر زيت المحرك", "المحرك والزيوت", OwnerGuideAction.REPLACE, 10_000.0, 12, "يُغيّر مع زيت المحرك."),
        item("engine_oil_level", "مستوى زيت المحرك والتسريب", "المحرك والزيوت", OwnerGuideAction.INSPECT, 5_000.0, 3, "فحص المستوى وأي تسريب أو استهلاك غير طبيعي؛ الفحص أهم من الانتظار حتى موعد الزيت."),
        item("air_filter_inspect", "فلتر هواء المحرك — فحص", "الفلاتر والسحب", OwnerGuideAction.INSPECT, 10_000.0, 6, "يُفحص أبكر في الأتربة الشديدة."),
        item("air_filter_replace", "فلتر هواء المحرك — تغيير", "الفلاتر والسحب", OwnerGuideAction.REPLACE, 20_000.0, 12, "مرجع عام محافظ؛ الحالة ودليل المصنع لهما الأولوية."),
        item("cabin_filter", "فلتر التكييف / المقصورة", "الفلاتر والسحب", OwnerGuideAction.REPLACE, 10_000.0, 12, "يُستبدل أبكر عند ضعف الهواء أو الروائح أو كثرة الأتربة."),
        item("spark_plugs_inspect", "البوجيهات — فحص النوع والحالة", "الإشعال", OwnerGuideAction.INSPECT, 30_000.0, 24, "العادي يختلف عن الإيريديوم/البلاتينيوم؛ لا تعتمد رقم تغيير واحد قبل معرفة النوع."),
        item("spark_plugs_review", "البوجيهات — موعد التغيير", "الإشعال", OwnerGuideAction.REVIEW_OEM, 40_000.0, 36, "نقطة مراجعة محافظة. العادي قد يحتاج تغييرًا أبكر، والإيريديوم قد يمتد كثيرًا؛ مواصفة المحرك هي المرجع."),
        item("ignition_coils", "كويلات الإشعال والتوصيلات", "الإشعال", OwnerGuideAction.INSPECT, 40_000.0, 24, "لا تُغيّر دوريًا دون عطل؛ تُفحص عند تقطيع/ضعف إشعال أو ضمن فحص شامل."),
        item("pcv", "بلف PCV وخراطيم الفاكيوم", "المحرك والزيوت", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص الانسداد والتسريب والتشققات. التغيير حسب الحالة أو جدول المصنع."),
        item("throttle_body", "بوابة الهواء / الثروتل", "الفلاتر والسحب", OwnerGuideAction.CLEAN_OR_SERVICE, 40_000.0, 24, "تنظيف عند الاتساخ أو أعراض الخمول/الاستجابة؛ ليس تنظيفًا إلزاميًا لكل سيارة في موعد ثابت."),
        item("engine_mounts", "قواعد المحرك والفتيس", "المحرك والزيوت", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص التشققات والهبوط والاهتزاز؛ لا يوجد عمر تغيير ثابت."),
        item("accessory_belt", "سير المجموعة / السيور الخارجية", "السيور والتوقيت", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص التشقق واللمعان والشد والتلوث بالزيت."),
        item("belt_tensioner", "شداد وبكرات سير المجموعة", "السيور والتوقيت", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص الصوت واللعب والحركة غير الطبيعية."),
        item("timing_system", "سير/سلسلة التوقيت", "السيور والتوقيت", OwnerGuideAction.REVIEW_OEM, 40_000.0, 24, "حدد أولًا هل المحرك يستخدم سيرًا أم سلسلة؛ موعد الاستبدال مؤكد فقط من دليل المحرك/المصنع."),
        item("engine_leaks", "تسريبات المحرك والجوانات", "المحرك والزيوت", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص زيت/مياه حول المحرك وأسفل السيارة."),
        item("engine_air_hoses", "خراطيم هواء المحرك والانتيك", "الفلاتر والسحب", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص التشقق والتثبيت وتسريب الهواء.")
    )

    private fun transmission(vehicle: VehicleEntity): List<OwnerGuideItem> {
        val identity = "${vehicle.transmissionName.orEmpty()} ${vehicle.transmissionCode.orEmpty()}".lowercase()
        val primary = when {
            vehicle.transmissionType == TransmissionType.CVT || "cvt" in identity || "ivt" in identity ->
                item("transmission_fluid", "زيت ناقل الحركة CVT / IVT", "ناقل الحركة", OwnerGuideAction.CHANGE_FLUID, 40_000.0, 36, "مرجع محافظ: 40,000 كم أو 3 سنوات. استخدم مواصفة CVT/IVT الصحيحة فقط.")
            vehicle.transmissionType == TransmissionType.AUTOMATIC ->
                item("transmission_fluid", "زيت ناقل الحركة الأوتوماتيك AT", "ناقل الحركة", OwnerGuideAction.CHANGE_FLUID, 60_000.0, 36, "مرجع محافظ: 60,000 كم أو 3 سنوات. مواصفة ATF وكود الفتيس لهما الأولوية.")
            vehicle.transmissionType == TransmissionType.DCT || "dct" in identity ->
                item("transmission_fluid", "زيت ناقل الحركة DCT", "ناقل الحركة", OwnerGuideAction.REVIEW_OEM, 60_000.0, 36, "الفترة والإجراء يختلفان بين DCT الجاف والرطب؛ راجع دليل الفتيس قبل التغيير.")
            vehicle.transmissionType == TransmissionType.MANUAL ->
                item("transmission_fluid", "زيت ناقل الحركة اليدوي", "ناقل الحركة", OwnerGuideAction.CHANGE_FLUID, 60_000.0, 48, "مرجع محافظ: 60,000 كم أو 4 سنوات، ما لم يحدد المصنع غير ذلك.")
            else ->
                item("transmission_fluid", "زيت ناقل الحركة — تحديد النوع أولًا", "ناقل الحركة", OwnerGuideAction.REVIEW_OEM, 40_000.0, 36, "حدد AT/CVT/IVT/DCT/Manual وكود الفتيس قبل اعتماد موعد تغيير الزيت.")
        }
        return listOf(
            primary,
            item("transmission_leaks", "تسريب زيت الفتيس والجوانات", "ناقل الحركة", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص أي تسريب أو بلل حول جسم الفتيس وأنابيب مبرد الزيت إن وجدت."),
            item("transmission_mount", "قاعدة ناقل الحركة", "ناقل الحركة", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص الاهتزاز والقطع والهبوط."),
            item("clutch_system", "الدبرياج ومنظومة التشغيل — إن وجدت", "ناقل الحركة", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص الانزلاق والصوت والمشوار والتسريب؛ الاستبدال حسب الحالة."),
            item("differential_fluid", "زيت الدفرنس — إن كان منفصلًا", "نقل الحركة", OwnerGuideAction.REVIEW_OEM, 60_000.0, 48, "يطبق فقط إذا كان للسيارة دفرنس بزيت مستقل؛ راجع مواصفة المصنع."),
            item("transfer_case_fluid", "زيت علبة التحويل AWD/4WD — إن وجدت", "نقل الحركة", OwnerGuideAction.REVIEW_OEM, 40_000.0, 36, "يطبق على أنظمة الدفع الرباعي فقط؛ مواصفة الزيت ودليل المصنع مرجعان أساسيان.")
        )
    }

    private fun electricDrivetrain() = listOf(
        item("ev_reduction_gear_fluid", "زيت علبة التخفيض / Reduction Gear", "نظام الدفع الكهربائي", OwnerGuideAction.REVIEW_OEM, 40_000.0, 36, "هذه نقطة فحص ومراجعة فقط؛ بعض السيارات الكهربائية لها زيت تروس بفترة محددة وأخرى لا تطلب استبدالًا دوريًا. اتبع دليل المصنع."),
        item("ev_drive_unit", "وحدة الدفع/الموتور الكهربائي", "نظام الدفع الكهربائي", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص التسريب والأصوات والإنذارات؛ لا توجد خدمة تغيير دورية عامة للموتور نفسه."),
        item("ev_drive_mounts", "قواعد وحدة الدفع الكهربائية", "نظام الدفع الكهربائي", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص الاهتزاز والتشققات والهبوط حسب الحالة.")
    )

    private fun highVoltageSystem(isElectric: Boolean): List<OwnerGuideItem> = listOf(
        item("hv_battery", "بطارية الجهد العالي وحالتها", "الجهد العالي", OwnerGuideAction.INSPECT, 20_000.0, 12, "راجع حالة البطارية ورسائل النظام وأي تدهور غير طبيعي. لا تُفتح أو تُخدم إلا بواسطة فني مؤهل للجهد العالي.", true),
        item("hv_cables_connectors", "كابلات ووصلات الجهد العالي", "الجهد العالي", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص بصري خارجي فقط للتلف/الصدمات/التسريب حول المكونات؛ لا تلمس موصلات الجهد العالي.", true),
        item("inverter_dc_dc", "الإنفرتر / محول DC-DC", "الجهد العالي", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص رسائل الأعطال والتبريد والتسريب وفق تصميم السيارة."),
        item("hv_cooling", "تبريد البطارية/الإنفرتر", "الجهد العالي", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص مستوى/حالة سائل أو مسارات هواء التبريد حسب تصميم السيارة. مواصفة السائل وموعد تغييره من دليل المصنع."),
        item("hv_coolant_change", "موعد تغيير سائل دائرة الجهد العالي", "الجهد العالي", OwnerGuideAction.REVIEW_OEM, 40_000.0, 24, "نقطة مراجعة وليست موعد تغيير موحدًا؛ تختلف سوائل وفترات تبريد البطارية والإنفرتر بشدة بين الشركات."),
        item("regen_braking", "نظام الكبح التجديدي", "الجهد العالي", OwnerGuideAction.INSPECT, 20_000.0, 12, "راقب التحذيرات وأداء الاسترجاع؛ الفرامل الاحتكاكية التقليدية تظل بحاجة للفحص الدوري.", true)
    ) + if (isElectric) listOf(
        item("charge_port", "منفذ الشحن والغطاء", "الشحن الكهربائي", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص النظافة والتلف والحرارة غير الطبيعية وعدم وجود رطوبة أو احتراق." , true),
        item("charging_cable", "كابل/موصل الشحن المحمول — إن وجد", "الشحن الكهربائي", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص العازل والفيشة وعدم وجود سخونة أو تشقق؛ أوقف الاستخدام عند أي تلف." , true)
    ) else listOf(
        item("hybrid_battery_air_path", "فلتر/مسار تبريد بطارية الهايبرد — إن وجد", "الجهد العالي", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص وتنظيف مسار الهواء أو الفلتر إذا كان تصميم السيارة يستخدم تبريدًا هوائيًا؛ اتبع دليل المصنع.")
    )

    private fun brakes() = listOf(
        item("front_pads", "تيل الفرامل الأمامي", "الفرامل", OwnerGuideAction.INSPECT, 10_000.0, 6, "فحص السمك والتآكل والصوت؛ التغيير حسب القياس والحالة وليس مسافة ثابتة.", true),
        item("rear_pads_shoes", "تيل/أحذية الفرامل الخلفية", "الفرامل", OwnerGuideAction.INSPECT, 10_000.0, 6, "فحص حسب تصميم السيارة: تيل، أحذية، هوبات/حلل أو أقراص.", true),
        item("front_discs", "أقراص / طنابير الفرامل الأمامية", "الفرامل", OwnerGuideAction.INSPECT, 10_000.0, 12, "قياس السمك والاعوجاج والحواف؛ التغيير بعد القياس وليس بموعد عام ثابت.", true),
        item("rear_discs_drums", "أقراص/هوبات الفرامل الخلفية", "الفرامل", OwnerGuideAction.INSPECT, 10_000.0, 12, "الفحص حسب التصميم وقياسات المصنع.", true),
        item("brake_fluid", "سائل الفرامل", "الفرامل", OwnerGuideAction.CHANGE_FLUID, null, 24, "تغيير محافظ كل سنتين ما لم يحدد المصنع فترة أقصر.", true),
        item("brake_hoses", "خراطيم ومواسير الفرامل", "الفرامل", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص التشقق والانتفاخ والصدأ والتسريب.", true),
        item("parking_brake", "فرامل اليد / التثبيت", "الفرامل", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص الكفاءة والضبط والكابلات/الموتور الإلكتروني حسب النظام.", true),
        item("abs_sensors", "حساسات ABS وحلقات السرعة", "الفرامل", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص عند ظهور لمبة ABS أو أثناء فحص العجل؛ لا تستبدل دوريًا دون عطل.", true)
    )

    private fun coolingAndFuel() = listOf(
        item("coolant_level", "مستوى وتركيز سائل التبريد", "التبريد", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص المستوى والتركيز واللون وأي تسريب."),
        item("coolant_replace_review", "سائل التبريد — موعد الاستبدال", "التبريد", OwnerGuideAction.REVIEW_OEM, 60_000.0, 36, "نقطة مراجعة محافظة وليست موعدًا موحدًا؛ سوائل Long Life قد يكون لها جدول أطول بكثير."),
        item("radiator", "الردياتير والغطاء", "التبريد", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص الزعانف والتسريب والغطاء والانسداد الخارجي."),
        item("cooling_hoses", "خراطيم دورة التبريد والوصلات", "التبريد", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص الانتفاخ والتصلب والتشققات والتسريب."),
        item("thermostat", "الثرموستات", "التبريد", OwnerGuideAction.INSPECT, 40_000.0, 24, "لا يستبدل دوريًا بلا سبب؛ يُراجع عند حرارة غير طبيعية أو ضمن فحص شامل."),
        item("water_pump", "طرمبة المياه", "التبريد", OwnerGuideAction.INSPECT, 40_000.0, 24, "فحص التسريب والصوت واللعب؛ بعض المحركات تربط خدمتها بسير التوقيت."),
        item("cooling_fans", "مراوح التبريد وحساساتها", "التبريد", OwnerGuideAction.INSPECT, 20_000.0, 12, "اختبار التشغيل والسرعات والفيش عند الحاجة."),
        item("fuel_filter", "فلتر الوقود — إذا كان قابلاً للخدمة", "الوقود", OwnerGuideAction.REVIEW_OEM, 40_000.0, 24, "بعض السيارات تستخدم فلترًا داخل الخزان بلا فترة تغيير دورية؛ تحقق من تصميم السيارة."),
        item("fuel_lines", "خراطيم ومواسير الوقود", "الوقود", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص التسريب والروائح والتشققات."),
        item("injectors", "الرشاشات", "الوقود", OwnerGuideAction.INSPECT, 40_000.0, 24, "التنظيف ليس واجبًا في موعد ثابت إذا كانت المنظومة تعمل جيدًا؛ افحص عند أعراض أو جودة وقود سيئة."),
        item("fuel_pump", "طرمبة الوقود وضغط المنظومة", "الوقود", OwnerGuideAction.INSPECT, 40_000.0, 24, "اختبار عند ضعف الأداء/صعوبة التشغيل؛ لا تغيير دوري بلا عطل.")
    )

    private fun suspensionSteeringAndDrive() = listOf(
        item("shock_absorbers", "المساعدين / ممتصات الصدمات", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص التسريب والارتداد والثبات؛ التغيير حسب الحالة، ويفضل على المحور كزوج."),
        item("strut_mounts", "قواعد ورولمان بلي المساعدين", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص الخبط والصوت والخلوص."),
        item("control_arms", "المقصات", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص الاعوجاج والجلب والمفاصل."),
        item("control_arm_bushings", "جلب المقصات والعفشة", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص التشقق والخلوص؛ التغيير حسب الحالة."),
        item("stabilizer_links", "تيش الميزان", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص الخلوص والخبط والجلد."),
        item("stabilizer_bushes", "جلب الميزان", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص التآكل والخلوص والصوت."),
        item("ball_joints", "بيض المقص / المفاصل الكروية", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص الخلوص والجلد؛ بند سلامة مهم." , true),
        item("tie_rod_ends", "بيض الدركسيون / أطراف التيش", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص الخلوص والجلد والزوايا.", true),
        item("inner_tie_rods", "تيش الدركسيون الداخلية", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص الخلوص والصوت."),
        item("steering_rack", "علبة الدركسيون والجلد", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص الخلوص والتسريب والجلد."),
        item("power_steering_fluid", "زيت الباور الهيدروليك — إن وجد", "العفشة والتوجيه", OwnerGuideAction.REVIEW_OEM, 40_000.0, 24, "غير مطبق على الباور الكهربائي. افحص المستوى/الحالة واتبع مواصفة المصنع."),
        item("cv_joints_outer", "رؤوس الكبالن الخارجية", "نقل الحركة", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص الطقطقة والخلوص والجلد."),
        item("cv_joints_inner", "رؤوس الكبالن الداخلية", "نقل الحركة", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص الرعشة والخلوص والجلد."),
        item("cv_boots", "جلد الكبالن", "نقل الحركة", OwnerGuideAction.INSPECT, 10_000.0, 6, "استبدال الجلد مبكرًا عند القطع قد يمنع تلف الكوبلن."),
        item("wheel_bearings", "بلي العجل / الهوبات", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص الصوت والخلوص والسخونة؛ لا عمر تغيير ثابت."),
        item("subframe_mounts", "جلب وقواعد القنطرة / الساب فريم", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص التشقق والخلوص والتثبيت."),
        item("alignment", "ضبط الزوايا", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 10_000.0, 12, "ينفذ فورًا عند سحب السيارة أو تآكل غير منتظم أو بعد أعمال عفشة."),
        item("wheel_balance", "ترصيص العجل", "العفشة والتوجيه", OwnerGuideAction.INSPECT, 10_000.0, 12, "ينفذ عند اهتزاز أو بعد فك/تركيب الإطارات حسب الحاجة.")
    )

    private fun tiresAndWheels() = listOf(
        item("tire_pressure", "ضغط الإطارات", "الإطارات والعجل", OwnerGuideAction.INSPECT, null, 1, "فحص شهريًا وقبل السفر والحمولة الكبيرة، بالقيم الموصى بها على ملصق السيارة." , true),
        item("tire_tread", "نقشة الإطارات والتآكل", "الإطارات والعجل", OwnerGuideAction.INSPECT, 5_000.0, 3, "فحص العمق والتآكل غير المتساوي والقطع والانتفاخ." , true),
        item("tire_rotation", "تدوير الإطارات", "الإطارات والعجل", OwnerGuideAction.ROTATE, 10_000.0, 6, "مرجع عام لتحسين انتظام التآكل إذا كان مقاس/اتجاه الإطارات يسمح بالتدوير."),
        item("tire_age", "عمر الإطارات وتاريخ الإنتاج", "الإطارات والعجل", OwnerGuideAction.INSPECT, 10_000.0, 12, "تزداد أهمية الفحص بعد عدة سنوات حتى لو كانت النقشة جيدة؛ الاستبدال النهائي يعتمد على العمر والحالة وتوصية المصنع." , true),
        item("spare_tire", "الإطار الاحتياطي وضغطه", "الإطارات والعجل", OwnerGuideAction.INSPECT, 10_000.0, 6, "فحص الضغط والعمر وأدوات الرفع."),
        item("wheel_nuts", "مسامير وصواميل العجل", "الإطارات والعجل", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص الحالة وربطها بعزم المصنع بعد أعمال العجل."),
        item("wheel_rims", "الجنوط", "الإطارات والعجل", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص الاعوجاج والشروخ والتآكل.")
    )

    private fun electrical(vehicle: VehicleEntity): List<OwnerGuideItem> {
        val electricOrHybrid = vehicle.fuelType == FuelType.ELECTRIC || vehicle.fuelType == FuelType.HYBRID
        val base = mutableListOf(
            item("battery_test", "البطارية 12V", "الكهرباء", OwnerGuideAction.INSPECT, 10_000.0, 6, "اختبار الجهد وحالة التشغيل؛ التغيير حسب الاختبار والعمر وليس تاريخًا ثابتًا."),
            item("battery_terminals", "أقطاب البطارية والكابلات", "الكهرباء", OwnerGuideAction.INSPECT, 10_000.0, 6, "فحص الصدأ والارتخاء وسلامة العزل."),
            item("fuses_relays", "الفيوزات والريلايات", "الكهرباء", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص عند أعطال الدوائر؛ لا تستبدل فيوزًا بقيمة أعلى."),
            item("lights", "الأنوار والإشارات ولمبات الفرامل", "الكهرباء", OwnerGuideAction.INSPECT, 10_000.0, 6, "اختبار التشغيل والعدسات والتوصيلات." , true),
            item("horn", "الكلاكس", "الكهرباء", OwnerGuideAction.INSPECT, 20_000.0, 12, "اختبار التشغيل."),
            item("sensors_warning_lamps", "لمبات التحذير والحساسات الأساسية", "الكهرباء", OwnerGuideAction.INSPECT, 10_000.0, 12, "أي لمبة تحذير مستمرة تحتاج تشخيصًا؛ لا يتم تغيير الحساسات دوريًا بلا عطل.")
        )
        if (electricOrHybrid) {
            base += highVoltageSystem(isElectric = vehicle.fuelType == FuelType.ELECTRIC)
        } else {
            base += item("charging_system", "الدينامو ونظام الشحن", "الكهرباء", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص الجهد والحزام إن كان ميكانيكيًا وأي لمبات تحذير.")
            base += item("starter", "المارش / بادئ الحركة", "الكهرباء", OwnerGuideAction.INSPECT, 40_000.0, 24, "لا تغيير دوري؛ فحص عند بطء التشغيل أو أصوات غير طبيعية.")
        }
        return base
    }

    private fun hvac() = listOf(
        item("cabin_filter", "فلتر التكييف / المقصورة", "الفلاتر والتكييف", OwnerGuideAction.REPLACE, 10_000.0, 12, "يُستبدل أبكر عند ضعف تدفق الهواء أو الروائح أو كثرة الأتربة."),
        item("ac_performance", "كفاءة التكييف", "التكييف", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص التبريد والضوضاء والتسريب. شحن الفريون ليس خدمة دورية إذا لم يوجد تسريب."),
        item("ac_condenser", "سربنتينة التكييف / المكثف", "التكييف", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص النظافة والزعانف والتسريب."),
        item("ac_evaporator_drain", "صرف مياه التكييف والمبخر", "التكييف", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص انسداد الصرف والروائح؛ تنظيف حسب الحاجة."),
        item("blower", "مروحة الصالون ومقاومتها", "التكييف", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص السرعات والصوت وتدفق الهواء."),
        item("ac_compressor", "كمبروسر التكييف والبكرة", "التكييف", OwnerGuideAction.INSPECT, 40_000.0, 24, "فحص الصوت والتسريب وأداء الضاغط عند الحاجة.")
    )

    private fun safetyBodyAndVisibility() = listOf(
        item("wipers", "ريش المساحات", "الرؤية والسلامة", OwnerGuideAction.INSPECT, 10_000.0, 6, "تغيير عند التقطيع أو ضعف المسح؛ لا تنتظر رقمًا ثابتًا." , true),
        item("washer", "مياه ورشاشات الزجاج", "الرؤية والسلامة", OwnerGuideAction.INSPECT, 10_000.0, 6, "فحص المستوى والرشاشات والخراطيم."),
        item("windshield", "الزجاج الأمامي والمرايات", "الرؤية والسلامة", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص الشروخ والرؤية والمرايات." , true),
        item("seat_belts", "أحزمة الأمان ومثبتاتها", "الرؤية والسلامة", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص القفل والرجوع والقطع؛ بند سلامة." , true),
        item("airbag_warning", "نظام الوسائد الهوائية ولمبة SRS", "الرؤية والسلامة", OwnerGuideAction.INSPECT, 20_000.0, 12, "أي لمبة SRS تحتاج تشخيصًا فورًا؛ لا توجد خدمة استبدال دورية عامة." , true),
        item("doors_locks", "الأبواب والأقفال والمفصلات", "الهيكل", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص الغلق والتشحيم والحالة."),
        item("underbody", "أسفل السيارة والحماية والتسريبات", "الهيكل", OwnerGuideAction.INSPECT, 10_000.0, 12, "فحص بعد الطرق السيئة أو الخبطات وأثناء الصيانة الدورية."),
        item("rust", "الصدأ ونقاط الهيكل", "الهيكل", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص المناطق المكشوفة وأسفل السيارة خصوصًا بعد إصلاحات الهيكل."),
        item("hood_trunk_latches", "كوالين الكبوت والشنطة", "الهيكل", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص الغلق والتشحيم."),
        item("sunroof_drains", "مصارف فتحة السقف — إن وجدت", "الهيكل", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص وتنظيف عند وجود فتحة سقف لتجنب تسرب المياه.")
    )

    private fun exhaustAndEmissions() = listOf(
        item("exhaust_system", "منظومة العادم والشكمان", "العادم والانبعاثات", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص التسريب والصدأ والتثبيت والصوت."),
        item("exhaust_hangers", "قواعد وجلد الشكمان", "العادم والانبعاثات", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص القطع والارتخاء."),
        item("catalytic_converter", "علبة البيئة / المحول الحفاز", "العادم والانبعاثات", OwnerGuideAction.INSPECT, 40_000.0, 24, "لا تنظيف أو تغيير دوري عام؛ افحص عند أكواد كفاءة أو انسداد/ضعف أداء."),
        item("oxygen_sensors", "حساسات الأكسجين", "العادم والانبعاثات", OwnerGuideAction.INSPECT, 40_000.0, 24, "لا تغيير دوري بلا عطل؛ تُفحص بالبيانات الحية والأكواد."),
        item("evap_system", "منظومة EVAP وغطاء خزان الوقود", "العادم والانبعاثات", OwnerGuideAction.INSPECT, 20_000.0, 12, "فحص التسريب والغطاء عند ظهور أكواد أو رائحة وقود."),
        item("egr_if_equipped", "EGR — إن وجد", "العادم والانبعاثات", OwnerGuideAction.INSPECT, 40_000.0, 24, "يُفحص/ينظف حسب تصميم المحرك والأعراض، وليس كخدمة موحدة لكل السيارات.")
    )
}
