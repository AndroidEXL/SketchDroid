# دليل مشروع SketchDroid (Sketchware Pro) — الشرح الكامل بالعربي

> مرجع تقني شامل: المعمارية، نظام البلوكات، طريقة البناء، طريقة التشغيل، وشرح كل شاشة.
> الفحوصات المذكورة هنا مأخوذة مباشرة من الكود في الفرع `arena/21c63592-sketchdroid`.

---

## 1) نظرة عامة سريعة

**SketchDroid** هو نسخة متفرّعة (fork) من مشروع **Sketchware Pro** — تطبيق أندرويد يعمل كـ *استوديو تطوير داخل الهاتف*:
تصمّم فيه واجهات التطبيق بالسحب والإفلات، وتبرمج منطقه بالبلوكات المرئية، ثم تضغط زرّ Run فيبني التطبيق **على الجهاز نفسه** وينتج ملف APK قابلاً للتثبيت — بدون كمبيوتر وبدون Android Studio.

| البند | القيمة (من `app/build.gradle`) |
| --- | --- |
| اسم الحزمة | `pro.sketchware` |
| الإصدار | `v7.1.0` — versionCode `150` |
| minSdk | `26` (أندرويد 8.0+) |
| targetSdk | `28` |
| compileSdk | `37` |
| Java | 17 (مع core library desugaring) |
| Gradle / AGP / Kotlin | `9.6.0` / `9.4.1` / `2.3.21` |
| حجم الكود | 583 ملف Java + 9 Kotlin ≈ **114,669 سطر** |
| الموارد | 277 ملف layout، 21 مجلد موارد، 879 نص واجهة + **523 نص بلوك** |
| الشاشات | **75 Activity** مصرّح بها في `AndroidManifest.xml` |
| حجم APK الناتج | ≈ **133 MB** (لأنه يحمل داخله أدوات البناء و`android.jar`) |

---

## 2) معمارية المشروع (طبقات الكود)

```
app/src/main/java/
├── a/a/a/            ← قلب التطبيق (مولّدات الكود + المترجم + إدارة المسارات)
├── com/besome/sketch/ ← شاشات Sketchware الأصلية (التصميم، المنطق، الخصائص، الإدارة، البناء، التصدير)
├── pro/sketchware/    ← الطبقة الحديثة (الشاشة الرئيسية + المتجر، الإعدادات، محرّرات الكود، محرّر الموارد)
├── mod/               ← تعديلات المساهمين (أكبر مصدر للميزات الجديدة)
├── dev/aldi/sayuti/   ← لوحة البلوكات الإضافية
├── kellinwood/        ← مكتبة zipsigner مدمجة في المصدر
└── com/bumptech/glide/← امتداد توقيع Glide مدمج
```

### جدول الطبقات

| الحزمة | الدور |
| --- | --- |
| `a.a.a` | **القلب**: توليد الكود (`Fx`/`Jx`/`Lx`/`Ix`/`Ox`)، مترجم المشروع (`ProjectBuilder`)، منظّم مسارات المشروع (`yq`)، خرائط الأنواع (`mq`)، ألوان البلوكات (`kq`)، مديرو البيانات (`jC` + `eC`/`hC`/`iC`/`kC`) |
| `com.besome.sketch` | شاشات المنتج الأصلية: `DesignActivity`، `LogicEditorActivity`، `PropertyActivity`، `ManageCollectionActivity`، `ExportProjectActivity`، المجموعات (صور/أصوات/خطوط/ودجات)، المساعدة |
| `pro.sketchware` | `MainActivity` + `ProjectsFragment` + متجر Sketchub، `SettingsActivity`، محرّر الموارد، صانع الأيقونات، محرّرات الكود، معاينة التصميم، أدوات |
| `mod.hey.studios` | محرّر كود، إعدادات المشروع (`ProjectSettings`: min/target SDK، `enable_viewbinding`، `app_class`، تعطيل الطرق القديمة)، مكتبات محلية، ProGuard، إدارة Assets/Java/مكتبات Native، النسخ الاحتياطي |
| `mod.hilal.saif` | مكتبة البلوكات الجاهزة (`BlocksHandler` = 2837 سطر)، مدير البلوكات، حقن المانيفست، الإعدادات المتقدمة |
| `mod.agus.jcoderz` | الأذونات، الموارد، مكتبات محلية، لوحة بلوكات بديلة |
| `mod.jbk` | حِزم البناء (`BuildProgressReceiver`، `BuiltInLibraries`، مُصرِّف الموارد/dex)، أدوات المنطق، تصدير ProGuard |
| `mod.pranav` | `R8Compiler`، `JarTask`، محلّل تبعيات Gradle (`DependencyResolver`)، مولّد ViewBinding (`ViewBindingBuilder`) |
| `mod.khaled` / `mod.tyron` / `mod.alucard` / `mod.remaker` | قارئ Logcat / النسخ الاحتياطي / adb library / مزخرفات الواجهة |

### ⚠️ أهم ملاحظة معمارية: المشروع **مفتوح جزئيًا**

المشروع **لا يحتوي على كل أصنافه** في المصدر. أصناف النواة الخاصة بـ Sketchware تأتي **مُسبقَة التصريف داخل ملفات JAR** مخزّنة في Git LFS:

| الملف | الحجم | الدور |
| --- | --- | --- |
| `app/libs/a.a.a-important-classes.jar` | 37 KB | أصناف `a.a.a` المهمة غير الموجودة في المصدر |
| `app/libs/a.a.a-notimportant-classes.jar` | 78 KB | بقية أصناف النواة |
| `app/libs/com.besome.sketch-classes.jar` | 9 KB | أصناف `com.besome.sketch` الأساسية |
| `app/libs/proguard-base-7.2.2.jar` | 903 KB | ProGuard المستخدم في بناء مشاريع المستخدم |

بالفحص: **45 صنفًا** من `a.a.a.*` مُستدعى من الكود وغير موجود في المصدر، أهمها:
`eC` و `hC` و `iC` و `kC` (مديرو البيانات: بيانات المشروع، الملفات، المكتبات، الموارد)، و`oB`/`KB` (أدوات ملفات)، و`By`/`zy` (استثناءات البناء)، و`Vs` (واجهة اختيار فئة البلوك)، و`MB`/`NB`/`Rs`…

> لذلك: أي تعديل على هذه الأصناف يتطلّب تعديل ملفات الـ JAR، وهو السبب الأساسي في أن كل مساهمات المجتمع تتركّز في `mod/` و`pro.sketchware/`.

### الأصول الثنائية (كلها LFS) وتُستخرَج وقت التشغيل

| الأصل | الحجم | يُستخدم في |
| --- | --- | --- |
| `assets/aapt/aapt2-{arm64-v8a,armeabi-v7a,x86,x86_64}` | 4–5 MB لكل معمارية | تصريف موارد تطبيق المستخدم (AAPT2) |
| `assets/libs/android.jar.zip` | 23.6 MB | `android.jar` الخاص بـ compileSdk لتصريف Java |
| `assets/libs/libs.zip` | 26.6 MB | موارد + JAR المكتبات المدمجة (AppCompat, Material, Firebase…) |
| `assets/libs/dexs.zip` | 8.2 MB | نسخ dex جاهزة للمكتبات المدمجة (تُدمج بالتطبيق النهائي) |
| `assets/libs/testkey.zip` | 2.4 KB | مفتاح التوقيع الافتراضي (debug) |
| `assets/libs/core-lambda-stubs.jar` | 17 KB | دعم لامبدا Java 8 |
| `assets/icons/icon_pack.zip` | 5.2 MB | مكتبة الأيقونات لصانع الأيقونات |
| `assets/template.zip` | 915 B | قالب المشروع الفارغ |
| `gradle/wrapper/gradle-wrapper.jar` | 59 KB | غلاف Gradle (مطلوب لبناء التطبيق) |

> **مهم:** لأن هذه الملفات في LFS فلابد من `git lfs pull` قبل البناء المحلي، و`lfs: true` في `actions/checkout` في CI (موجودة بالفعل في الـ workflow).

---

## 3) تخزين البيانات: خريطة مجلد `.sketchware/`

كل شيء يعيش في ذاكرة الهاتف الداخلية تحت `.sketchware/` (مصدر المسارات: `a.a.a.wq` و`pro.sketchware.utility.FilePathUtil`):

| المسار | المحتوى |
| --- | --- |
| `mysc/<project>/` | **مشروع المستخدم**: بنية مصدر كاملة (java, res, assets, libs, …) تُولَّد عند البناء |
| `mysc/list/` | فهرس المشاريع (بطاقة كل مشروع: الاسم، الحزمة، الأيقونة…) |
| `data/<sc_id>/` | بيانات المشروع المنطقية + إعداداته. المجلدات المؤكَّدة من الكود: `permission`, `compile_log`, `proguard-rules.pro`, `local_library`, `converted-vectors/`, `Injection/androidmanifest/{attributes,activities_components,app_components}/`, و`files/` ويحتوي: `java/`, `resource/{layout,drawable,values/{strings,colors,styles}}/`, `assets/`, `service/`, `broadcast/`, `native_libs/`, `kt_plugins/`, `library/{jar,dex,res}/`, `classpath/` |
| `resources/` | **مجموعات المستخدم**: `images/`, `sounds/`, `fonts/`, `icons/`, و`block/My Block/{block.json,menu.json,palette.json}` (البلوكات المخصّصة)، و`export/` |
| `libs/local_libs/` | المكتبات المحلية (`.jar` / `.aar`) التي يضيفها المستخدم |
| `temp/{images,sounds,fonts,proj,data}/` | ملفات مؤقتة أثناء الاستيراد/البناء |
| `bak/` , `backups/` | النسخ الاحتياطية (ملفات `.swb`) |
| `keystore/release_key.jks` | مفتاح التوقيع الخاص بالمستخدم (يُنشأ من شاشة إنشاء الشهادة) |
| `signed_apk/` | الـ APK النهائي الموقّع عند التصدير |
| `data/settings.json`, `data/system/{events.json,listeners.json,component.json,export/events/}` | إعدادات ومخصّصات عامة (الأحداث والمستمعين والمكوّنات المخصّصة والقوالب المُصدَّرة) |
| `debug.txt` | سجل الأعطال والتشخيص |

ملفات المشروع تُفتح/تُشارك بصيغة **`.swb`**، و`MainActivity` مسجّل لها `intent-filter` لفتحها بالنقر من مدير الملفات.

---

## 4) نظام البلوكات — الشرح التفصيلي

### 4.1 نموذج بيانات البلوك: `BlockBean`

البلوك الواحد داخل المشروع عبارة عن كائن `com.besome.sketch.beans.BlockBean` (يرث `SelectableBean`، و`Parcelable`):

| الحقل | المعنى |
| --- | --- |
| `id` | معرّف البلوك داخل الملف المنطقي |
| `spec` | **نص البلوك** مع فراغات الإدخال، مثل `add source directly %s.inputOnly` |
| `type` | نوع البلوك: `" "` (جملة/statement)، `s` (نص)، `d` (رقم)، `b` (منطقي)، `c` (مكوّن)، `l` (قائمة)… |
| `typeName` | الاسم الفرعي للنوع عند الحاجة |
| `opCode` | **اسم العملية** الذي يحدّد الكود المولَّد (`setText`, `ifElse`, `mathSqrt`…) |
| `parameters` | قيم الإدخال التي أدخلها المستخدم (نصوص/أرقام/أسماء متغيرات/بلوكات فرعية) |
| `nextBlock`, `subStack1`, `subStack2` | البلوك التالي + فرعا if/else والحلقات (شكل الشجرة) |
| `color` | لون البلوك على الشاشة |
| `classInfo` / `paramClassInfo` | `Gx`: سلسلة الوراثة للتحقّق من نوع الإدخال |

مثال حقيقي على شكل الـ spec: `"add source directly %s.inputOnly"` حيث `.inputOnly` تعني *هذا الفراغ يقبل كتابة نص فقط ولا يقبل سحب بلوك فيه*.

أنواع الفراغات المتكررة في مكتبة البلوكات (إحصائية فعلية من `BlocksHandler`):

| العنصر | العدد | معناه |
| --- | --- | --- |
| `%d` | 70 | إدخال رقمي (double) |
| `%s` | 64 | إدخال نصي |
| `%b` | 23 | إدخال منطقي (صح/خطأ) |
| `%m.<نوع>` | 292 | منفذ يقبل **بلوك** أو مكوّنًا من نوع محدّد: `%m.view`, `%m.color`, `%m.listStr`, `%m.listMap`, `%m.progressdialog`, `%m.listview`, `%m.edittext`, `%m.videoview`, `%m.patternview`, `%m.viewpager`, `%m.textinputlayout`, `%m.varMap`… |

`Gx` (في `a.a.a.Gx`) يبني *شجرة أنواع* لتحديد أي بلوك يقبل أي إدخال، مثل:
`Var.boolean` / `Var.double` / `Var.String` / `List.ListString` / `View.Clickable.TextView.Button` / `View.AdapterView.AbsListView.ListView`… أي أن نظام الأنواع في البلوكات مبني على سلسلة وراثة نصية.

### 4.2 فئات لوحة البلوكات (10 فئات)

من `com.besome.sketch.editor.logic.PaletteSelector` — الأسماء والألوان والمعرّفات كما في الكود:

| المعرّف | الفئة | اللون |
| --- | --- | --- |
| `0` | المتغيرات (Var) | `#FFEE7D16` |
| `1` | القوائم (List) | `#FFCC5B22` |
| `2` | التحكّم (Control) | `#FFE1A92A` |
| `3` | العمليات (Operator) | `#FF5CB722` |
| `4` | الرياضيات (Math) | `#FF23B9A9` |
| `5` | الملفات (File) | `#FFA1887F` |
| `6` | دوال الواجهة (View func) | `#FF4A6CD4` |
| `7` | دوال المكوّنات (Component func) | `#FF2CA5E2` |
| `-1` | نصوص XML (من محرّر الموارد) | `#FF7C83DB` |
| `8` | بلوكات المزيد (MoreBlock) | `#FF8A55D7` |

بالإضافة إلى فئات ديناميكية تُضاف من `mod.agus.jcoderz...palette.PaletteSelector` (لوحات يوفّرها المستخدم)، وتُدمج في نفس القائمة.

### 4.3 من أين تأتي البلوكات فعليًا؟ (4 مصادر)

1. **اللبنات الأصلية** — معرّفة داخل النواة (ملفات الـ JAR) وهي البلوكات الأساسية للغة Sketchware.
2. **`dev.aldi.sayuti.block.ExtraPaletteBlock`** (1266 سطر) — يملأ كل فئة عند الضغط عليها، عبر `setBlock(paletteId, color)` ودالة `logicEditor.a(type, opcode)`، و`logicEditor.b(label, tag, listener)` للأزرار الرمادية (مثل "Add variable"). أمثلة من الكود:
   * فئة 4 (رياضيات): `mathGetDip, mathPow, mathSqrt, mathRound, mathSin…` (24 بلوكًا)
   * فئة 5 (ملفات): `fileutilread, fileutilwrite, fileutilcopy, fileutildelete, resizeBitmapFile*, getExternalStorageDir, getPackageDataDir…`
   * فئة 6 (واجهة): `setEnable, setVisible, setElevation, setRotate, setAlpha, setTranslationX/Y, setScaleX…`
   * فئة -1: يقرأ `strings.xml` الخاصة بالمشروع ويولّد بلوكًا لكل نص (`getResStr`) + زر "Open Resources editor"
3. **`mod.hilal.saif.blocks.BlocksHandler`** (2837 سطر، **258 بلوكًا جاهزًا**) — بلوكات "إضافية" جاهزة بأكواد Java كاملة، منها:
   `viewOnClick` (يولّد `setOnClickListener` كاملاً)، `AsyncTaskExecute/PublishProgress`، `datePickerDialogShow`، `timePickerDialogShow`، `customToast` و`customToastWithIcon`، `LightStatusBar`، `hideKeyboard`/`showKeyboard`، `EditTextShowError`، `progressdialog*`، `imageCrop`، `isConnected`، `List View` helpers… وتُبنى كـ `HashMap` بالمفاتيح: `name`, `spec`, `type`, `typeName`, `code` (مثال حقيقي: `AsyncTaskExecute` → الكود `new %s().execute(%s);`).
   كما يحتوي `primaryBlocksC` و`primaryBlocksD` = بلوكات **التحكّم** و**العمليات** الرسمية.
4. **بلوكات المستخدم** — من ملفات JSON خارجية: `~/.sketchware/resources/block/My Block/block.json` (البلوكات) و`palette.json` (اللوحة) و`menu.json`، تُدار عبر `ExtraBlockFile`, `pro.sketchware.blocks.ExtraBlocks`, `BlockLoader`, وشاشات **مدير البلوكات** في الإعدادات.

وفوق ذلك يوجد **"بلوكات المزيد" (More blocks)** التي ينشئها المستخدم بنفسه (اسم + بارامترات + كود) عبر `MakeBlockActivity`، وتُخزَّن كمجموعات في `ManageCollectionActivity`.

### 4.4 كيف يُعرض البلوك على الشاشة؟

* `PaletteSelector` (RecyclerView مخصّص) = الفئات + البحث + التنقّل، وله مستمع `Vs onBlockCategorySelectListener`.
* `PaletteBlock` (view مخصّص) = لوحة العرض نفسها، و`paletteAdapter` يعرض البلوكات، و`logicEditor.m.a()` تُفرّغ اللوحة قبل عرض فئة جديدة.
* نص البلوك على الشاشة يُقرأ من `res/values/block_strings.xml` (523 نصًا) بالاتفاق: `block_<opcode_بالـsnake_case>_head` / `_body_N` / `_tail`.
  مثال للتحقّق: `mathSqrt` → `block_math_sqrt_head`، و`setText` → `block_set_text_body_1`، و`addSourceDirectly` → `block_add_source_directly_head`.
* لون البلوك يُحسب في `a.a.a.kq.a(opcode, blockType)` (يوجد منه جدول ضخم لكل الـ opcodes، مع تلوين متوافق مع لون ثيم التطبيق عبر `harmonizeWithPrimary`).

### 4.5 محرّر المنطق `LogicEditorActivity` (109 KB — أكبر ملف في المشروع)

يوفّر: اختيار فئة → سحب بلوك → إدخال القيم → ربط البلوكات (قوائم منسدلة لإضافة بلوكات لكل حدث/شاشة) + قوائم:
Add variable / Add custom variable / Remove variable، Add list / Add custom list / Remove list، Add component، Add event، Import (استيراد مكتبات/باكدج عبر بلوكات `customImport`)، **View Code** (عرض الكود المولَّد)، **Undo/Redo**، **Blocks collection** (مجموعات بلوكات جاهزة)، **المفضّلة** (Favorites)، **البحث** في البلوكات، و**استيراد بلوكات** (`MoreblockImporterDialog`).

### 4.6 من البلوكات إلى Java (مولّدات الكود)

هذه هي السلسلة الكاملة — كما هي موصوفة في `README` الرسمي مع تفاصيل الكود:

| الصنف | الدور |
| --- | --- |
| `a.a.a.Fx` (74 KB) | **توليد كود كل بلوك**: يحوّل `opCode` + البارامترات إلى نص Java. أمثلة من الكود: `viewOnClick` ← `setOnClickListener(new View.OnClickListener(){...})`، `Timer` ← `new TimerTask(){ runOnUiThread(...) }`، `progressdialog*` ← `setPositiveButton(...)`، Firebase `onDataChange/onCancelled`… |
| `a.a.a.Jx` (52 KB) | **توليد ملف الـ Activity الكامل**: الحزم، الاستيرادات، الحقول، `onCreate`، `initialize`، `initializeLogic`، `onActivityResult`، أحداث العرض/المكوّنات/الدرج/المصادقة، وأصناف الـ adapters، ودعم ViewBinding |
| `a.a.a.Lx` (167 KB) | **قوالب المستمعين والأحداث**: `onClick`, `onBackPressed`, `onStart/Stop/Resume/Pause/Destroy`, `onCheckedChanged`, `onItemSelected`, `onTextChanged`, `onPageStarted/Finished`, `onAnimationStart...`, `onDataChange`, `onChildAdded/Changed/Removed/Moved`… |
| `a.a.a.Ix` | **توليد `AndroidManifest.xml`**: الصلاحيات، `FileProvider`، ميتاداتا Firebase، الأنشطة… |
| `a.a.a.Ox` | **توليد XML للتخطيطات (layouts)** من محرّر التصميم |
| `a.a.a.mq` | **خرائط الأنواع**: أسماء أصناف المكوّنات/القوائم/المتغيرات (تُستدعى عند توليد التعريفات والاستيرادات) |
| `a.a.a.kq` | ألوان البلوكات (عرض فقط) |
| `mod.jbk.util.BlockUtil` / `pro.sketchware.blocks.ExtraBlocks` | أدوات مساعدة للبلوكات (تسمية، ترتيب، بلوكات إضافية) |
| StringFog (`assets/stringfog/stringfog.zip`) | تشفير النصوص داخل الكود المولَّد (اختياري من إعدادات البناء) |

---

## 5) طريقة البناء (Build) — ثلاثة مستويات

### 5.1 المستوى الأول: بناء **تطبيق المستخدم** داخل الهاتف (20 خطوة)

الدالة المسؤولة: `DesignActivity$BuildTask.doInBackground()` (حوالي السطر 1125 → 1287). الخطوات بالترتيب مع ما يحدث فعليًا:

| # (في شريط التقدّم من 20) | الخطوة | ما يحدث |
| --- | --- | --- |
| 1 | حذف الملفات المؤقتة | مسح مجلد بناء المشروع (`projectMyscPath`) |
| 2 | توليد ملفات المصدر | `yq.a()` / `yq.b(...)` / `yq.f()` / `yq.e()` تنشئ بنية المشروع كاملة (java, res, assets, libs) + نسخ الصور/الأصوات/الخطوط إلى `res` و`assets` |
| 3–6 | استخراج AAPT2 وأصول البناء | `maybeExtractAapt2()` ينسخ `aapt2-<abi>` حسب معمارية الجهاز، ثم `BuiltInLibraries.extractCompileAssets()`: `android.jar.zip` → `android.jar` (خطوة 3)، `dexs.zip` → `dexs/` (4)، `libs.zip` → `libs/` (5)، `testkey.zip` → `testkey/` (6) + `core-lambda-stubs.jar` |
| 8 | **AAPT2** | `ProjectBuilder.compileResources()` → `ResourceCompiler.Aapt2Compiler`: تجميع الموارد (`compile`) ثم الربط (`link`) مع `android.jar` وموارد المكتبات، وتوليد `R.java` |
| 11 | ViewBinding | `generateViewBinding()` لتوليد أصناف الربط إن كان مفعّلاً |
| — | Kotlin (اختياري) | `KotlinCompilerBridge.compileKotlinCodeIfPossible()` لمشاريع Kotlin |
| 13 | **تصريف Java** | `compileJavaCode()` عبر **ECJ** (`org.eclipse.jdt` 3.26.0) مع classpath يضم المكتبات المدمجة + `core-lambda-stubs.jar` |
| — | StringFog | `StringfogHandler.start()`: تشفير النصوص في الأصناف المصرّفة (إن كان مفعّلاً) |
| — | ProGuard / R8 | `ProguardHandler.start()` ثم `runProguard()` أو `runR8()` (تصغير الكود + قواعد `proguard-rules.pro`) |
| 17 | **Dexing** | `createDexFilesFromClasses()`: يستخدم **D8** افتراضيًا (`DexCompiler` مع minApiLevel من إعدادات المشروع)، أو `dx` القديم (`com.android.dx.command.dexer.Main` مع `--multi-dex`) إن أُغلق D8؛ ويُتخطّى كليًا إذا كان التصغير بـ R8 مفعّلاً لأن R8 يتولّى الـ dexing |
| 18 | دمج الـ DEX | `getDexFilesReady()`: دمج dex المشروع مع dex المكتبات المدمجة (`dexs/`) — **آخر تعديل على main (`f76133a`) استبدل `DexMerger` القديم بدمج D8** لدعم Multidex بشكل صحيح |
| 19 | بناء APK | `buildApk()` عبر `com.android.sdklib.build.ApkBuilder` → APK غير موقّع |
| 20 | التوقيع | `signDebugApk()` بمفتاح debug المستخرج من `testkey.zip` ثم `zipalign` (`zipalign-java`) — أو بمفتاح المستخدم عند التصدير الرسمي (`apksig`) |
| — | التثبيت | `installBuiltApk()`: إن كان الجهاز **root** ومُفعّل خيار التثبيت التلقائي → `pm install` مباشرة (مع إمكانية الفتح التلقائي)؛ وإلا يظهر مُثبِّت الحزم العادي |
 `installBuiltApk()`: إن كان الجهاز **root** ومُفعّل خيار التثبيت التلقائي → `pm install` مباشرة (مع إمكانية الفتح التلقائي)؛ وإلا يظهر مُثبِّت الحزم العادي |

شريط التقدّم يعرض هذه الخطوات من 20 خطوة (`onProgress(progress, step)` + إشعار في شريط الحالة)، ويمكن إلغاء البناء (`cancelBuild`)، ويمكن تفعيل **البناء في الخلفية** من الإعدادات.

**مكتبات الأدوات المستخدمة:** `aapt2` (4 معماريات)، `org.eclipse.jdt:ecj`، `com.android.tools:r8`، `proguard-core`، `com.android.tools.build:apksig`، `sdklib` (`ApkBuilder`)، `dalvik-dx`، `zipalign-java`، `bundletool` (لـ AAB)، `Kotlin compiler 2.1.21/2.3.21`.

### 5.2 المستوى الثاني: بناء **التطبيق نفسه** (هذا المستودع)

**المتطلبات:** JDK 17، Android SDK (compileSdk 37)، **Git LFS** (بدونه لن تعمل الأصول والـ JARs).

```bash
git lfs install && git lfs pull      # ضروري: الأصول و JARs في LFS
./gradlew assembleRelease            # أو assembleDebug
```
النتيجة: `app/build/outputs/apk/release/app-release.apk` (موقّع بمفتاح `testkey.keystore`).

إضافات على مستوى Gradle:
* `google-services` + `crashlytics` (Firebase) — يحتاجان `app/google-services.json` أو السرّ `GOOGLE_SERVICES_JSON`، و`gradle/custom-tasks.gradle` ينشئ نسخة وهمية عند الغياب.
* `SKETCHUB_API_KEY` يُمرَّر كـ `buildConfigField` (يستخدمه المتجر).
* `--stable-ids public-stable-ids.txt` لموارد `public.xml`، وإقصاءات `xmlpull`/`kxml2`/kotlin-scripting، وdesugaring.

**عبر GitHub Actions:** الـ workflow `.github/workflows/android.yml` يبني على `ubuntu-latest` بـ `actions/checkout` مع `lfs: true` وJDK 17 و`gradle assembleRelease`، ويرفع الـ artifact باسم `apk-release-<short_sha>`، مع خطوة `Inspect APK` التي تنشر الحجم وبصمة SHA-256 والتوقيع في ملخّص التشغيل.

### 5.3 المستوى الثالث: التصدير كـ **مشروع Android Studio / AAB**

`ExportProjectActivity` (423 سطرًا للـ AsyncTask) يولّد مشروع Gradle كامل قابلًا للفتح في Android Studio، ويستطيع:
* تصدير المصادر كملف ZIP وإرسالها بالبريد.
* **بناء AAB** عبر `bundletool` + `AppBundleCompiler`.
* **توقيع APK** بمفتاح المستخدم (يُنشأ من `NewKeyStoreActivity`، ويُحفظ في `.sketchware/keystore/release_key.jks`).

### 5.4 تشخيص أخطاء البناء

| الأداة | الاستخدام |
| --- | --- |
| `CompileLogActivity` | عرض آخر سجل تصريف (`data/<sc_id>/compile_log`) ومشاركته |
| `CollectErrorActivity` | شاشة الأعطال (Crash) — يسجّلها `SketchApplication` تلقائيًا |
| `.sketchware/debug.txt` | سجل عام للتشخيص (رسائل البناء والتثبيت) |
| `DesignActivity.indicateCompileErrorOccurred()` | نافذة الخطأ مع زر عرض السجل عند فشل البناء |

---

## 6) طريقة التشغيل (Runtime)

### 6.1 إقلاع التطبيق

1. `SketchApplication.onCreate()` → يحفظ `Context` عامًا، يثبّت `UncaughtExceptionHandler` (أي انهيار → `CollectErrorActivity` + إنهاء العملية)، ثم `ThemeManager.applyTheme()` لتطبيق الثيم المختار.
2. `MainActivity` (نقطة الدخول) يرث `BasePermissionAppCompatActivity` — يطلب صلاحيات التخزين (بما فيها `MANAGE_EXTERNAL_STORAGE` المطلوبة للوصول إلى `.sketchware/`).
3. الشاشة الرئيسية تحتوي تبويبين (Navigation): **المشاريع** و**متجر Sketchub**، ودرج جانبي بـ: About the team، Changelog، معلومات التطبيق، إنشاء Keystore، الإعدادات، SW Assist، وروابط Discord/Telegram/GitHub. وزر عائم (FAB) لإنشاء مشروع جديد أو استيراد `.swb`.

### 6.2 دورة العمل الكاملة لمستخدم التطبيق

```
إنشاء مشروع (FAB)
   └→ MyProjectSettingActivity: الاسم، اسم الحزمة، اسم التطبيق، الإصدار، الأيقونة، ألوان الثيم، التحكم بالإصدار
        └→ DesignActivity  ← واجهة التصميم الرئيسية (3 تبويبات: View / Event / Component)
             ├→ سحب الودجات من اللوحة، تعديل التخطيطات، ViewCodeEditor/CodeViewer للـ XML
             ├→ PropertyActivity: خصائص الودجت المحدّد (نص، لون، حجم، هوامش، أحداث…)
             ├→ LogicEditorActivity: البلوكات لكل حدث/شاشة (Add variable/list/component/event)
             ├→ ManageCollectionActivity: الصور/الأصوات/الخطوط/الودجات/مجموعات البلوكات
             ├→ إدارة المتقدّمين: المكتبات، الأذونات، الموارد، Assets، Java، Native libs، ProGuard، المانيفست
             └→ زر Run ▶ → البناء (20 خطوة في 5.1) → تثبيت تلقائي → تشغيل التطبيق
   
بعد نجاح الفكرة:
   ExportProjectActivity → APK موقّع بمفتاحك / مشروع Android Studio / AAB
```

### 6.3 تشغيل نسخة جاهزة (طريقة عملية)

1. نزّل الـ APK (من CI/الإصدارات) وثبّته (يحتاج السماح بتثبيت التطبيقات من مصادر خارجية).
2. عند أول تشغيل: اسمح بصلاحية **كل الملفات** (إجباري — بدونها لا يستطيع التطبيق إنشاء `.sketchware/`).
3. أنشئ مشروعًا → صمّم شاشة بسيطة → أضف بلوك `Toast` مثلًا → علّم على زر Run.
4. أول بناء أبطأ (استخراج `android.jar` و`libs.zip` و`dexs.zip` ≈ 60 MB لأول مرة فقط)، واللاحق سريع (يُخزّن في `files/libs`).

### 6.4 قيود معروفة في هذه النسخة (fork)

| القيد | الأثر |
| --- | --- |
| لا `google-services.json` حقيقي | Firebase/Crashlytics/Messaging معطّلة (ملف وهمي في CI فقط)، وقد تظهر تحذيرات |
| `SKETCHUB_API_KEY` فارغ | تبويب متجر Sketchub لا يعمل (`sketchub.in/api/v3`) |
| توقيع `testkey` | لا يُثبَّت فوق نسخة رسمية موقّعة بمفتاح آخر، وغير صالح للنشر |
| `targetSdk 28` | سلوك قديم لصلاحيات التخزين (مطلوب لعمل المحرّر) |
| لا تقسيم ABI | APK واحد بحجم ~133 MB لكل الأجهزة |

---

## 7) كل صفحة ووظيفتها (75 شاشة)

### 7.1 الشاشة الرئيسية والمتجر

| الشاشة | الوظيفة |
| --- | --- |
| `MainActivity` | الصفحة الرئيسية: تبويب المشاريع + تبويب المتجر، الدرج الجانبي، FAB إنشاء/استيراد مشروع، فتح ملفات `.swb`، النسخ الاحتياطي/الاسترجاع (`BackupRestoreManager`) |
| `ProjectsFragment` | قائمة مشاريعك المحلية: إنشاء، بحث، ترتيب، نسخ، حذف، استرجاع، فتح المشروع في المصمّم |
| `ProjectsStoreFragment` | متجر المشاريع المفتوحة (Sketchub API) بفئات وبحث وتحميل |
| `ProjectPreviewActivity` | معاينة مشروع من المتجر: لقطات شاشة، تقييمات، تعليقات، تحميل |
| `CommentsBottomSheet` | نافذة تعليقات سفليّة في المتجر |

### 7.2 محرّر التصميم والمنطق

| الشاشة | الوظيفة |
| --- | --- |
| `DesignActivity` | **قلب المشروع**: تصفّح ملفات المشروع (Activities/Fragments/Files) في الدرج، 3 تبويبات (View / Event / Component)، سحب الودجات، Undo/Redo، حفظ، بحث، **زر Run** (بناء+تثبيت)، إعدادات البناء، عرض الكود المولّد، تحذير المساحة |
| `LogicEditorActivity` | **محرّر البلوكات**: اللوحة بالفئات، سحب/ربط البلوكات، إضافة متغيرات/قوائم/مكوّنات/أحداث، View Code، Undo/Redo، المفضّلة، البحث، استيراد بلوكات |
| `PropertyActivity` | خصائص الودجت المحدّد (النص، اللون، الأبعاد، الهوامش، الأحداث…) |
| `LogicEditorDrawer` / `logic_editor_top_menu` | مكوّنات مساعدة لمحرّر المنطق (الدرج وشريط الأدوات العلوي) |
| `MakeBlockActivity` | إنشاء/تعديل **بلوك "المزيد"** مخصّص: الاسم + البارامترات + كود Java داخله |
| `AddEventActivity` | إضافة حدث جديد لملف/ودجت |
| `ViewSelectorActivity` | شاشة اختيار الودجت الذي تريد إضافته |
| `AddViewActivity` | إضافة ودجت قياسي إلى التخطيط |
| `AddCustomViewActivity` | إضافة ودجت مخصّص (RecyclerView, Lottie, CircleImageView, OTPView, CardView…) |
| `PresetSettingActivity` | إعدادات جاهزة (Preset) للتخطيط/الودجت |
| `ShowBlockCollectionActivity` | عرض محتوى مجموعة بلوكات جاهزة |
| `ShowMoreBlockCollectionActivity` | عرض محتوى مجموعة "بلوكات المزيد" |
| `ShowWidgetCollectionActivity` | عرض الودجات المحفوظة في مجموعة المستخدم |
| `ManageViewActivity` | إدارة شاشات/تخطيطات المشروع (View manager) |
| `ViewCodeEditorActivity` / `CodeViewerActivity` | تحرير/عرض كود XML أو Java لما تم توليده (مع تلوين Sora Editor + textmate) |
| `LayoutPreviewActivity` | معاينة ملف تخطيط XML مباشرة |
| `SrcViewerActivity` | عارض كود المصدر للمشروع/الشاشة الحالية (توليد في الذاكرة) |

### 7.3 المجموعات والموارد

| الشاشة | الوظيفة |
| --- | --- |
| `ManageCollectionActivity` | **مركز المجموعات**: تبويبات الصور، الأصوات، الخطوط، الودجات، البلوكات، بلوكات المزيد (إضافة/تعديل/حذف/تصدير) |
| `ManageImageActivity` + `AddImageActivity` + `AddImageCollectionActivity` + `ManageImageImportActivity` | إدارة الصور: قص، ضغط، دعم SVG/vector، استيراد من الاستوديو |
| `ManageSoundActivity` + `AddSoundActivity` + `AddSoundCollectionActivity` + `ManageSoundImportActivity` | إدارة الأصوات (تشغيل/تجربة/استيراد) |
| `ManageFontActivity` + `AddFontActivity` + `AddFontCollectionActivity` + `ManageFontImportActivity` | إدارة الخطوط (TTF/OTF) |
| `ResourcesEditorActivity` | محرّر موارد مرئي: `strings.xml`, `colors.xml`, `arrays.xml`, `attrs.xml`, `dimens`… بدعم المتغيّرات (variants) — وبلوكات نصوص XML تأخذ منه |
| `ManageResourceActivity` | إدارة ملفات الموارد في المشروع (mod.agus) |
| `ManageAssetsActivity` | إدارة مجلد assets (نسخ/حذف/تعديل الملفات) |
| `ManagePermissionActivity` | إدارة أذونات التطبيق المستهدف (بلوكات/مانيفست) |
| `ManageAppCompatActivity` | تفعيل/تعديل دعم AppCompat (الحزم والأصناف الأساسية)
| `ManageCustomComponentActivity` / `AddCustomComponentActivity` | مكوّنات مخصّصة (تصدير/استيراد) |
| `ManageXMLCommandActivity` | أوامر XML المخصّصة (حقن في المانيفست/التخطيطات) |
| `AndroidManifestInjection` + `...Details` | حقن أكواد في `AndroidManifest.xml` عبر واجهة |
| `IconCreatorActivity` | صانع أيقونات التطبيق (قص، أشكال، خلفية، طبقات) |
| `ImportIconActivity` | استيراد أيقونة جاهزة للمشروع |

### 7.4 المكتبات (Libraries)

| الشاشة | الوظيفة |
| --- | --- |
| `ManageLibraryActivity` | تفعيل/تعطيل المكتبات المدمجة (AppCompat, Material3, Firebase, AdMob, Google Maps, Play Billing…) — تسجّلها `a.a.a.qq` |
| `Material3LibraryActivity` | إعدادات مكتبة Material 3 |
| `FirebaseActivity` / `ManageFirebaseActivity` | إعداد Firebase (Google Services JSON، قواعد البيانات، المصادقة، الرسائل) وربطها بالبلوكات |
| `AdmobActivity` / `ManageAdmobActivity` | إعداد إعلانات AdMob (مفاتيح الوحدات) |
| `ManageGoogleMapActivity` | إعداد خرائط Google (مفتاح API) |
| `ManageLocalLibraryActivity` (`dev.aldi.sayuti`) | إضافة مكتبات محلية `.jar`/`.aar` وربطها بالمشروع |
| `ExcludeBuiltInLibrariesActivity` | استثناء مكتبات مدمجة من البناء |
| `ManageProguardActivity` | تحرير قواعد ProGuard/R8 للمشروع |
| `ManageJavaActivity` | إدارة ملفات Java الإضافية في المشروع |
| `ManageNativelibsActivity` | إدارة مكتبات `.so` الأصلية |
| `ManageLocalLibraryActivity`/`ManageCollection*` | تكملة منظومة الإدارة |

### 7.5 محرّرات ومديرو الإعدادات (الطبقة الحديثة)

| الشاشة | الوظيفة |
| --- | --- |
| `SettingsActivity` | حاوٍ للإعدادات؛ يفتح: **Appearance** (`SettingsAppearanceFragment`: الثيم فاتح/غامق/النظام)، **Events Manager** (`EventsManagerFragment` + Creator + Details: إنشاء أحداث ومستمعين مخصّصين)، **Block Selector** (`BlockSelectorManagerFragment` + Details: تخصيص أسماء/أشكال البلوكات ومجموعاتها) |
| `AppSettings` (`mod.hilal.saif`) | صفحة إعدادات المود العامّة: المظهر، قارئ Logcat، إعدادات النظام، مدير البلوكات |
| `ConfigActivity` | مفاتيح سلوك التطبيق: `always-show-blocks`, `built-in-blocks`, `show-every-single-block`, `backup-dir`, `backup-filename`, `root-auto-install-projects`, `root-auto-open-after-installing`, `use-new-version-control`, `use-asd-highlighter`, `background-building`, `palletteDir`, `blockDir`… |
| `BlocksManager` / `BlocksManagerCreatorActivity` / `BlocksManagerDetailsActivity` | **مدير البلوكات المخصّصة**: إنشاء بلوك جديد (اسم، spec، كود)، تفاصيله، تصديره/استيراده (JSON)، مجلداته من `ConfigActivity` |
| `SystemSettingActivity` | إعدادات النظام (mod.hilal) |
| `SystemInfoActivity` | معلومات الجهاز (الموديل، DPI، الدقة…): مهمّة عند تصميم التخطيطات |
| `ProgramInfoActivity` | معلومات التطبيق + فحص التحديثات + روابط المجتمع |
| `LogReaderActivity` (`mod.khaled`) | قارئ Logcat مدمج لتصحيح أخطاء تطبيق المستخدم |
| `AboutActivity` | عن التطبيق: الفريق، سجل التغييرات، تغييرات النسخ التجريبية |

### 7.6 البناء والتصدير والأدوات

| الشاشة | الوظيفة |
| --- | --- |
| `ExportProjectActivity` | تصدير المشروع: مشروع Android Studio (ZIP/بريد)، **AAB** عبر Bundletool، **APK موقّع** بمفتاحك |
| `NewKeyStoreActivity` | إنشاء شهادة/مفتاح توقيع جديد (`release_key.jks`) |
| `CompileLogActivity` | سجل التصريف الأخير (عرض/مشاركة/حذف) |
| `CollectErrorActivity` | شاشة الانهيار + عرض التتبّع الكامل وإرساله |
| `LicenseActivity` | رخص المكتبات مفتوحة المصدر (Maven/OSS) |
| شاشات أساسية (`BaseAppCompatActivity`, `BaseDialogActivity`, `BaseBottomSheetDialogActivity`, `BasePermissionAppCompatActivity`) | أصناف أساس مشتركة (الثيم، الصلاحيات، الحوارات) تُستخدم من كل الشاشات |

---

## 8) خريطة ملفات سريعة (Cheat Sheet)

| أريد تعديل… | الملف |
| --- | --- |
| نص/شكل بلوك على الشاشة | `app/src/main/res/values/block_strings.xml` |
| الكود الذي يولّده بلوك موجود | `app/src/main/java/a/a/a/Fx.java` (أو `BlocksHandler` للبلوكات المضافة) |
| الكود الكامل للأكتيفيتي المولّد | `a/a/a/Jx.java` |
| قوالب الأحداث/المستمعين | `a/a/a/Lx.java` |
| شكل المانيفست المولَّد | `a/a/a/Ix.java` |
| توليد التخطيطات XML | `a/a/a/Ox.java` |
| اللوحة والفئات | `com/besome/sketch/editor/logic/PaletteSelector.java` + `dev/aldi/sayuti/block/ExtraPaletteBlock.java` |
| مكتبة البلوكات الجاهزة | `mod/hilal/saif/blocks/BlocksHandler.java` |
| ألوان البلوكات | `a/a/a/kq.java` |
| محرّر البلوكات | `com/besome/sketch/editor/LogicEditorActivity.java` |
| المترجم و20 خطوة البناء | `a/a/a/ProjectBuilder.java` + `com/besome/sketch/design/DesignActivity.java` (BuildTask) |
| تصريف الموارد / dex | `mod/jbk/build/compiler/resource/ResourceCompiler.java` و`.../dex/DexCompiler.java` |
| R8 / ProGuard للمستخدم | `mod/pranav/build/R8Compiler.kt`، `mod/hey/studios/project/proguard/ProguardHandler.java` |
| مسارات المشروع المولَّد | `a/a/a/yq.java` |
| مسارات `.sketchware` | `a/a/a/wq.java` و`pro/sketchware/utility/FilePathUtil.java` |
| المكتبات المدمجة وأدوات البناء | `mod/jbk/build/BuiltInLibraries.java` |
| إعدادات السلوك | `mod/hilal/saif/activities/tools/ConfigActivity.java` |
| واجهة الإعدادات | `pro/sketchware/activities/settings/SettingsActivity.java` + `pro/sketchware/fragments/settings/**` |

---

## 9) كيف تضيف بلوكًا جديدًا (وصفة عملية)

بلوك "جملة" بسيط مثل بلوك يطبع رسالة في اللوج (المثال التالي **توضيحي** بالكامل: اسم البلوك `myLogDemo` غير موجود في المشروع، وهو مجرّد نموذج للخطوات):

1. **النص على الشاشة** — أضف في `res/values/block_strings.xml`:
   ```xml
   <string name="block_myLogDemo_head" translatable="false">log demo</string>
   <string name="block_myLogDemo_body_1" translatable="false">message</string>
   ```
2. **الكود المولَّد** — أضف حالة في `a/a/a/Fx.java` (خريطة `opcode → Java`)، مثل:
   ```java
   case "myLogDemo":
       opcode = "android.util.Log.d(\"Demo\", " + params.get(0) + ");";
       break;
   ```
   (أو أضف بلوكًا جاهزًا في `BlocksHandler.builtInBlocks` بالمفاتيح `name/spec/type/code`.)
3. **إظهاره في اللوحة** — في `dev/aldi/sayuti/block/ExtraPaletteBlock.setBlock(...)` أضف:
   ```java
   logicEditor.a(" ", "myLogDemo");   // " " = بلوك جملة
   ```
   (النوع `"s"` نص، `"d"` رقم، `"b"` منطقي.)
4. **اللون** (اختياري) — أضف الـ opcode في `a/a/a/kq` بالفئة المناسبة.
5. **الأنواع** — إن كان للمنفذ نوع محدّد استخدم `%m.view` / `%m.listStr`… داخل الـ `spec`.
6. **الاختبار**: افتح مشروعًا → المنطق → الفئة → اسحب البلوك → View Code → Run.

---

## 10) خلاصة في 10 أسطر

1. التطبيق = IDE داخل الهاتف: تصميم + بلوكات + بناء APK محليًا.
2. الكود طبقات: `a.a.a` (قلب النواة، بعضه في JARs/LFS)، `com.besome.sketch` (شاشات المنتج)، `pro.sketchware` و`mod/*` (ميزات المجتمع).
3. البلوك = `BlockBean` فيه `spec` + `opCode` + `parameters` + فروع (`subStack1/2`).
4. اللوحة = 10 فئات + لوحات ديناميكية، تُملأ من النواة و`ExtraPaletteBlock` و`BlocksHandler` وملفات JSON الخاصة بالمستخدم.
5. النص واللون من `block_strings.xml` و`kq`.
6. التحويل إلى Java: `Fx` (بلوك ← كود) ثم `Jx` (أكتيفيتي كامل) مع `Lx` للأحداث و`Ix`/`Ox` للمانيفست والتخطيطات.
7. البناء على الجهاز: AAPT2 → ECJ/Kotlin → ProGuard/R8 → D8 → دمج dex → ApkBuilder → zipalign → apksig → تثبيت (20 خطوة).
8. البناء للتطبيق نفسه: Gradle 9.6 + JDK 17 + LFS (أو GitHub Actions كما في هذا الفرع).
9. التشغيل: صلاحية "كل الملفات" → مشروع جديد → تصميم → بلوكات → Run.
10. أول بناء بطيء (استخراج ~60 MB أدوات)، والتالي سريع.
