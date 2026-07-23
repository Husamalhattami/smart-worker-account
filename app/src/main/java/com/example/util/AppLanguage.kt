package com.example.util

enum class AppLanguage(val code: String, val displayName: String, val isRtl: Boolean) {
    ARABIC("ar", "العربية", true),
    ENGLISH("en", "English", false),
    FRENCH("fr", "Français", false),
    URDU("ur", "اردو", true);

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.find { it.code.lowercase() == code.lowercase() } ?: ARABIC
        }
    }
}

class LanguageStrings(val lang: AppLanguage) {
    // App Header & Branding
    val appTitle: String = when (lang) {
        AppLanguage.ARABIC -> "حساب العامل الذكي (Smart Worker)"
        AppLanguage.ENGLISH -> "Smart Worker Account Calculator"
        AppLanguage.FRENCH -> "Calculateur Compte Travailleur"
        AppLanguage.URDU -> "اسمارٹ وركر حساب كھاتہ"
    }

    val developerName: String = when (lang) {
        AppLanguage.ARABIC -> "حسام محمد الحطامي"
        AppLanguage.ENGLISH -> "Husam Mohammad Al-Hattami"
        AppLanguage.FRENCH -> "Husam Mohammad Al-Hattami"
        AppLanguage.URDU -> "حسام محمد الحطامی"
    }

    val developerTitle: String = when (lang) {
        AppLanguage.ARABIC -> "👨‍💻 تطوير المهندس: حسام الحطامي"
        AppLanguage.ENGLISH -> "👨‍💻 Developed by Eng. Husam Al-Hattami"
        AppLanguage.FRENCH -> "👨‍💻 Développé par Ing. Husam Al-Hattami"
        AppLanguage.URDU -> "👨‍💻 ڈویلپر انجینیئر: حسام الحطامی"
    }

    val welcomeGreeting: String = when (lang) {
        AppLanguage.ARABIC -> "مرحباً يا"
        AppLanguage.ENGLISH -> "Welcome,"
        AppLanguage.FRENCH -> "Bienvenue,"
        AppLanguage.URDU -> "خوش آمدید،"
    }

    // Metrics
    val netBalanceTitle: String = when (lang) {
        AppLanguage.ARABIC -> "الصافي المستحق لك"
        AppLanguage.ENGLISH -> "Net Due Balance"
        AppLanguage.FRENCH -> "Solde Net Dû"
        AppLanguage.URDU -> "خالص قابل ادا رقم"
    }

    val earnedSalaryTitle: String = when (lang) {
        AppLanguage.ARABIC -> "الراتب المكتسب"
        AppLanguage.ENGLISH -> "Earned Salary"
        AppLanguage.FRENCH -> "Salaire Gagné"
        AppLanguage.URDU -> "کمایا ہوا باقاعدہ تنخواہ"
    }

    val totalSpendingsTitle: String = when (lang) {
        AppLanguage.ARABIC -> "إجمالي الصرفيات"
        AppLanguage.ENGLISH -> "Total Expenses"
        AppLanguage.FRENCH -> "Total Dépenses"
        AppLanguage.URDU -> "کل روزانہ اخراجات"
    }

    val totalDrawsTitle: String = when (lang) {
        AppLanguage.ARABIC -> "إجمالي السحبيات"
        AppLanguage.ENGLISH -> "Total Advances & Draws"
        AppLanguage.FRENCH -> "Total Avances"
        AppLanguage.URDU -> "کل پیشگی رقم"
    }

    val workDaysTitle: String = when (lang) {
        AppLanguage.ARABIC -> "أيام العمل الفعلي"
        AppLanguage.ENGLISH -> "Actual Work Days"
        AppLanguage.FRENCH -> "Jours Travaillés"
        AppLanguage.URDU -> "کام کے دن"
    }

    val absenceDaysTitle: String = when (lang) {
        AppLanguage.ARABIC -> "أيام الغياب"
        AppLanguage.ENGLISH -> "Absence Days"
        AppLanguage.FRENCH -> "Jours d'absence"
        AppLanguage.URDU -> "غير حاضری کے دن"
    }

    // Actions
    val addSpending: String = when (lang) {
        AppLanguage.ARABIC -> "صرفة يومية 💵"
        AppLanguage.ENGLISH -> "Daily Expense 💵"
        AppLanguage.FRENCH -> "Dépense Quotidienne 💵"
        AppLanguage.URDU -> "روزانہ خرچہ 💵"
    }

    val addDraw: String = when (lang) {
        AppLanguage.ARABIC -> "سحبية سلفة 🏧"
        AppLanguage.ENGLISH -> "Advance / Draw 🏧"
        AppLanguage.FRENCH -> "Avance / Acompte 🏧"
        AppLanguage.URDU -> "پیشگی رقم 🏧"
    }

    val addAbsence: String = when (lang) {
        AppLanguage.ARABIC -> "تسجيل غياب ❌"
        AppLanguage.ENGLISH -> "Record Absence ❌"
        AppLanguage.FRENCH -> "Marquer Absence ❌"
        AppLanguage.URDU -> "غير حاضری درج کریں ❌"
    }

    val fullStatement: String = when (lang) {
        AppLanguage.ARABIC -> "كشف الحساب الشامل 📑"
        AppLanguage.ENGLISH -> "Full Statement 📑"
        AppLanguage.FRENCH -> "Relevé de Compte 📑"
        AppLanguage.URDU -> "مکمل کھاتہ بيان 📑"
    }

    val exportPdf: String = when (lang) {
        AppLanguage.ARABIC -> "طباعة / تصدير PDF 📄"
        AppLanguage.ENGLISH -> "Print / Export PDF 📄"
        AppLanguage.FRENCH -> "Imprimer / Exporter PDF 📄"
        AppLanguage.URDU -> "پی ڈی ایف ایکسپورٹ 📄"
    }

    val checkUpdates: String = when (lang) {
        AppLanguage.ARABIC -> "فحص التحديثات المباشرة 🔄"
        AppLanguage.ENGLISH -> "Check Live Updates 🔄"
        AppLanguage.FRENCH -> "Vérifier Mises à jour 🔄"
        AppLanguage.URDU -> "لائیو اپڈیٹ چیک کریں 🔄"
    }

    val settingsAndControl: String = when (lang) {
        AppLanguage.ARABIC -> "الإعدادات والتحكم ⚙️"
        AppLanguage.ENGLISH -> "Settings & Control ⚙️"
        AppLanguage.FRENCH -> "Paramètres & Contrôle ⚙️"
        AppLanguage.URDU -> "سیٹنگز اور کنٹرول ⚙️"
    }

    val devInfoTitle: String = when (lang) {
        AppLanguage.ARABIC -> "معلومات المطور والتحديثات ℹ️"
        AppLanguage.ENGLISH -> "Developer & Live Updates ℹ️"
        AppLanguage.FRENCH -> "Développeur & Mises à jour ℹ️"
        AppLanguage.URDU -> "ڈیولپر اور اپڈیٹس معلومات ℹ️"
    }

    // Settings Dialog
    val userTab: String = when (lang) {
        AppLanguage.ARABIC -> "معلومات المستخدم"
        AppLanguage.ENGLISH -> "User Profile"
        AppLanguage.FRENCH -> "Profil Utilisateur"
        AppLanguage.URDU -> "صارف کی معلومات"
    }

    val systemTab: String = when (lang) {
        AppLanguage.ARABIC -> "إعدادات النظام"
        AppLanguage.ENGLISH -> "System Settings"
        AppLanguage.FRENCH -> "Paramètres Système"
        AppLanguage.URDU -> "سسٹم سیٹنگز"
    }

    val employeeNameLabel: String = when (lang) {
        AppLanguage.ARABIC -> "اسم الموظف الكريم:"
        AppLanguage.ENGLISH -> "Employee Name:"
        AppLanguage.FRENCH -> "Nom de l'employé:"
        AppLanguage.URDU -> "ملازم کا نام:"
    }

    val monthlySalaryLabel: String = when (lang) {
        AppLanguage.ARABIC -> "الراتب الشهري الأساسي:"
        AppLanguage.ENGLISH -> "Basic Monthly Salary:"
        AppLanguage.FRENCH -> "Salaire Mensuel de Base:"
        AppLanguage.URDU -> "بنیادی ماہانہ تنخواہ:"
    }

    val currencyLabel: String = when (lang) {
        AppLanguage.ARABIC -> "العملة الرئيسية للراتب والحسابات 💱:"
        AppLanguage.ENGLISH -> "Main Currency for Calculations 💱:"
        AppLanguage.FRENCH -> "Devise Principale 💱:"
        AppLanguage.URDU -> "بنیادی کرنسی 💱:"
    }

    val quickCurrencySelect: String = when (lang) {
        AppLanguage.ARABIC -> "اختر العملة بسرعة (جاهز للنشر على Google Play):"
        AppLanguage.ENGLISH -> "Quick Currency Selection (Google Play Ready):"
        AppLanguage.FRENCH -> "Sélection Rapide de Devise:"
        AppLanguage.URDU -> "فوری کرنسی منتخب کریں:"
    }

    val appLanguageLabel: String = when (lang) {
        AppLanguage.ARABIC -> "🌐 لغة التطبيق (App Language):"
        AppLanguage.ENGLISH -> "🌐 Application Language:"
        AppLanguage.FRENCH -> "🌐 Langue de l'application:"
        AppLanguage.URDU -> "🌐 ایپ کی زبان:"
    }

    val saveBtn: String = when (lang) {
        AppLanguage.ARABIC -> "حفظ الحساب"
        AppLanguage.ENGLISH -> "Save Settings"
        AppLanguage.FRENCH -> "Enregistrer"
        AppLanguage.URDU -> "سیٹنگز محفوظ کریں"
    }

    val cancelBtn: String = when (lang) {
        AppLanguage.ARABIC -> "إلغاء"
        AppLanguage.ENGLISH -> "Cancel"
        AppLanguage.FRENCH -> "Annuler"
        AppLanguage.URDU -> "منسوخ کریں"
    }

    val recentTransactionsHeader: String = when (lang) {
        AppLanguage.ARABIC -> "سجل العمليات الأخيرة"
        AppLanguage.ENGLISH -> "Recent Transactions Log"
        AppLanguage.FRENCH -> "Historique des Transactions"
        AppLanguage.URDU -> "حالیہ لین دین کا ریکارڈ"
    }

    val noTransactionsYet: String = when (lang) {
        AppLanguage.ARABIC -> "لا توجد عمليات مسجلة حتى الآن"
        AppLanguage.ENGLISH -> "No transactions recorded yet"
        AppLanguage.FRENCH -> "Aucune transaction enregistrée"
        AppLanguage.URDU -> "ابھی تک کوئی لین دین ریکارڈ نہیں ہوا"
    }

    val systemSettingsTitle: String = when (lang) {
        AppLanguage.ARABIC -> "إعدادات النظام"
        AppLanguage.ENGLISH -> "System Settings"
        AppLanguage.FRENCH -> "Paramètres Système"
        AppLanguage.URDU -> "سسٹم سیٹنگز"
    }

    val systemSettingsSubtitle: String = when (lang) {
        AppLanguage.ARABIC -> "الراتب الشهري الأساسي، العملة، الاستقطاعات"
        AppLanguage.ENGLISH -> "Base salary, currency, deductions"
        AppLanguage.FRENCH -> "Salaire de base, devise, déductions"
        AppLanguage.URDU -> "بنیادی تنخواہ، کرنسی اور کٹوتیاں"
    }

    val userSettingsTitle: String = when (lang) {
        AppLanguage.ARABIC -> "معلومات المستخدم"
        AppLanguage.ENGLISH -> "User Information"
        AppLanguage.FRENCH -> "Informations Utilisateur"
        AppLanguage.URDU -> "صارف کی معلومات"
    }

    val userSettingsSubtitle: String = when (lang) {
        AppLanguage.ARABIC -> "اسم الموظف الكريم، الرصيد والحماية"
        AppLanguage.ENGLISH -> "Employee name, opening balance & security"
        AppLanguage.FRENCH -> "Nom de l'employé, solde et sécurité"
        AppLanguage.URDU -> "ملازم کا نام، بیلنس اور سیکیورٹی"
    }

    val liveUpdatesTitle: String = when (lang) {
        AppLanguage.ARABIC -> "تحديثات التطبيق"
        AppLanguage.ENGLISH -> "App Live Updates"
        AppLanguage.FRENCH -> "Mises à jour en direct"
        AppLanguage.URDU -> "ایپ کی جدید ترین اپ ڈیٹس"
    }

    val liveUpdatesSubtitle: String = when (lang) {
        AppLanguage.ARABIC -> "فحص وتحديث التطبيق لأحدث إصدار"
        AppLanguage.ENGLISH -> "Check and update to the latest version"
        AppLanguage.FRENCH -> "Vérifier et mettre à jour l'application"
        AppLanguage.URDU -> "ایپ کا نیا ورژن چیک کریں"
    }

    val currentVersionText: String = when (lang) {
        AppLanguage.ARABIC -> "🏷️ رقم الإصدار الحالي: v1.3.0 (مستقر)"
        AppLanguage.ENGLISH -> "🏷️ Current Version: v1.3.0 (Stable)"
        AppLanguage.FRENCH -> "🏷️ Version Actuelle: v1.3.0 (Stable)"
        AppLanguage.URDU -> "🏷️ موجودہ ورژں: v1.3.0 (مستحکم)"
    }

    val backupRestoreSection: String = when (lang) {
        AppLanguage.ARABIC -> "💾 النسخ الاحتياطي واستعادة البيانات"
        AppLanguage.ENGLISH -> "💾 Data Backup & Restore"
        AppLanguage.FRENCH -> "💾 Sauvegarde & Restauration"
        AppLanguage.URDU -> "💾 ڈیٹا بیک اپ اور بحالی"
    }

    val createBackupBtn: String = when (lang) {
        AppLanguage.ARABIC -> "إنشاء نسخة احتياطية (تصدير)"
        AppLanguage.ENGLISH -> "Create Backup (Export)"
        AppLanguage.FRENCH -> "Créer une sauvegarde (Exporter)"
        AppLanguage.URDU -> "بیک اپ بنائیں (ایکسپورٹ)"
    }

    val restoreBackupBtn: String = when (lang) {
        AppLanguage.ARABIC -> "استعادة نسخة احتياطية (استيراد)"
        AppLanguage.ENGLISH -> "Restore Backup (Import)"
        AppLanguage.FRENCH -> "Restaurer une sauvegarde (Importer)"
        AppLanguage.URDU -> "بیک اپ بحال کریں (امپورٹ)"
    }

    val backupDescription: String = when (lang) {
        AppLanguage.ARABIC -> "احفظ كافة سجلاتك والراتب والإعدادات في ملف آمن بالجوال لضمان عدم ضياع أي بيانات إذا تم حذف التطبيق."
        AppLanguage.ENGLISH -> "Save all your logs, salary details, and settings in a secure file on your phone so your data is never lost if the app is deleted."
        AppLanguage.FRENCH -> "Enregistrez tous vos enregistrements, salaires et paramètres dans un fichier sécurisé sur votre téléphone."
        AppLanguage.URDU -> "اپنے تمام ریکارڈز، تنخواہ اور سیٹنگز کو اپنے فون میں محفوظ کریں تاکہ ایپ ڈیلیٹ ہونے پر ڈیٹا ضائع نہ ہو۔"
    }

    val appObjective: String = when (lang) {
        AppLanguage.ARABIC -> "🎯 الهدف: نظام محاسبي وذكاء اصطناعي صوتي شامل لإدارة مستحقات وراتب العمال والموظفين بدقة تامة."
        AppLanguage.ENGLISH -> "🎯 Goal: Smart accounting system & voice AI to calculate workers' salary and dues with complete accuracy."
        AppLanguage.FRENCH -> "🎯 Objectif: Système comptable intelligent pour calculer les salaires des travailleurs avec précision."
        AppLanguage.URDU -> "🎯 مقصد: سمارٹ اکاؤنٹنگ اور وائس اے آئی سسٹم تاکہ ورکرز کی تنخواہ کا درست حساب رکھا جا سکے۔"
    }
}
