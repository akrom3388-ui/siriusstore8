# SIRIUS STORE — APK (GitHub Actions orqali yig'iladi)

## v2.7
- Diagnostika: o'ng pastdagi yozuvda versiya, WebView, APK/web va plagin holati ko'rinadi. Tugma bosilganda javob bo'lmasa, qizil xabarda qaysi element bosishni to'sayotgani chiqadi.

## v2.2
- Eski Android WebView (Chrome 80 dan past) da ishlamaydigan yangi sintaksis (`?.`, `??`) olib tashlandi: bunday telefonlarda tugmalar bosilganda animatsiya bor, lekin hech narsa ochilmasdi.
- Ekranning o'ng pastida kichik `v2.2 • WebView NN` yozuvi turadi. Ilova to'liq yuklanmasa, qizil xabar chiqadi.

## v2.1 — tuzatish (MUHIM)
- **"Ilovaga kirsam hech qaysi tugma ishlamaydi" xatosi tuzatildi.** Sabab: kod Android ichida mavjud bo'lmagan `Capacitor.registerPlugin()` ni chaqirardi va shu joyda to'xtab, qulf, orqaga tugmasi, sinxronlash va boshqa hamma narsa yuklanmay qolardi. Endi `Capacitor.Plugins.Sirius` orqali ulanadi.
- Kutilmagan xato bo'lsa, ekran tepasida qizil xabar chiqadi (jim qotib qolmaydi). Shu xabarni menga yuborsangiz, sababini topaman.

## APK ni yig'ish (bir marta sozlanadi)
1. github.com da **YANGI PRIVATE repo** oching (Private bo'lishi shart — ichida imzo kaliti va Telegram token bor).
2. Shu zip ichidagi hamma narsani repoga yuklang. `.github` papkasi yashirin bo'lgani uchun web orqali yuklashda ko'rinmasligi mumkin.
   Shunda: repoda **Add file → Create new file** → nom maydoniga `.github/workflows/build-apk.yml` yozing va zipdagi `build-apk.yml` mazmunini qo'ying.
   Yoki kompyuterda:
   ```
   git init && git add -A && git commit -m "SIRIUS STORE"
   git branch -M main
   git remote add origin https://github.com/<login>/<repo>.git
   git push -u origin main
   ```
3. Repo → **Actions** → "Build APK" → bajarilishini kuting (5–10 daqiqa).
4. Tugagach shu yugurish sahifasi pastida **Artifacts → SIRIUS-STORE-apk** ni yuklab oling, zipdan `app-debug.apk` chiqadi.
5. Telefonga o'rnating ("noma'lum manbalardan o'rnatish"ga ruxsat bering).

Keyin kodni o'zgartirsangiz (`www/index.html`), repoga push qiling — APK qayta yig'iladi.
Imzo kaliti doimiy (`signing/debug.keystore`), shuning uchun yangi APK eskisi ustiga ma'lumot yo'qolmasdan o'rnatiladi.

## Birinchi ishga tushirish (telefonda)
1. Ilovani oching → SMS ruxsatini bering (so'rasa).
2. **Sozlamalar → "Fon rejimi"** kartasini bosing va chiqqan savollarga "Ha" deng: SMS ruxsati, "Signal va eslatmalar", batareya cheklovi.
3. **Android 13+ da SMS ruxsati o'chiq/kulrang bo'lsa** (APK Play Market'dan emas, shuning uchun): Telefon sozlamalari → Ilovalar → SIRIUS STORE → yuqori o'ngdagi **⋮ → "Cheklangan sozlamalarga ruxsat berish"**, so'ng Ruxsatlar → SMS ni yoqing. Ilova "Sozlamalarni ochaymi?" deb o'zi ham taklif qiladi.
4. **Sozlamalar → "Fon tekshiruvini hozir sinash"** — o'z raqamingiz bilan test qarzdor qo'shib sinab ko'ring.

Xiaomi / Redmi / Oppo / Vivo / Huawei / Samsung: ilovaga **Avtostart** ruxsatini bering va batareya tejashda **Cheklovsiz** qiling.

## v2.0 da nima o'zgardi
- **Qarz berishda muddat majburiy** (bo'sh qoldirib bo'lmaydi).
- **Barcha qarzdorlarga SMS** — telefon SIM-kartasidan, ilovadan chiqmasdan, har biriga natija (yuborildi/xato sababi) bilan. Mijoz ichidagi SMS tugmalari ham ilovadan chiqmaydi.
- **Orqaga tugmasi / imo-ishora** to'g'ri ishlaydi (oyna → sahifa → bosh sahifa → chiqish).
- **Qo'ng'iroq tugmasi** to'g'ridan-to'g'ri mijozga qo'ng'iroq qiladi (birinchi marta ruxsat so'raydi).
- **Telegram tugmasi** (bosh sahifa) — botingizni Telegramda ochadi. **Mijozdagi Telegram tugmasi** — shu mijoz raqami bilan Telegramdan izlaydi.
- **Bazani yuklash** tugmasi — botdagi yangi operatsiyalarni darhol yuklaydi (faylga saqlash: Sozlamalar → "Bazani faylga saqlash").
- **PIN-kod + barmoq izi** (Sozlamalar → Maxfiylik). Ilova 30 soniyadan ko'p fonda tursa qayta qulflanadi.
- **Til: O'zbekcha / Русский** (Sozlamalar → Ilova tili). SMS matni tili alohida (SMS sozlash sahifasida).
- **Avto-eslatma vaqti** sozlanadi (Sozlamalar → Avto-eslatma vaqti). Muddat — belgilangan vaqtdan keyingi 12 soat ichida (telefon o'chiq bo'lsa yoqilgach yuboriladi).
- **Murojaat telefoni**: +998 90 661 04 99.
- **Ovozli xabar**: mijoz sahifasida "Ovozli xabar (WhatsApp/Telegram)" — matn telefon ovoz dvigatelida audio faylga aylantirilib yuboriladi.
- **Logotip** (yulduz) — ikonka, splash va bildirishnoma belgisi.
- **Bildirishnomalar**: har kuni ertalab "Muddati o'tgan qarzdorlar" ro'yxati + SMS yuborilgach "kimlarga ketdi" ro'yxati.
- Boshqa tuzatishlar: muddat hisobi endi to'lovlarni eng eski qarzdan yopadi (FIFO); sana UTC emas, mahalliy vaqt bo'yicha; Telegram sozlamalari fon hisobotida ham ishlatiladi (avval kod ichidagi token olinardi); do'kon nomi/telefon saqlanadi; operatsiyani o'chirish (mijoz sahifasida 🗑); PDF/chop etish APK da ishlaydi; mijozlar korzinadan tiklash va import to'g'rilandi.

## Telefonlarni sinxronlash (2 ta telefonda bir xil baza)
1. **Ikkala telefonda ham**: Sozlamalar → *Telegram sozlamalari* — **bir xil bot tokeni va bir xil birinchi chat ID** kiriting (masalan o'zingizning shaxsiy chat ID yoki umumiy guruh).
2. **Ikkala telefonda**: Sozlamalar → *Telefonlarni sinxronlash* → yoqing, telefonga nom bering (Telefon-1, Telefon-2).
3. Har bir amal (qarz, to'lov, kassa, sotuv, mijoz...) bot orqali o'tadi: Telegramga qisqa xabar boradi, baza fayli yangilanadi va ikkinchi telefonga **bildirishnoma** keladi.
4. Ilova ochiq bo'lsa ~12 soniyada yangilanadi; yopiq bo'lsa fonda ~5–15 daqiqada tekshiriladi. "Bazani yuklash" tugmasi darhol yangilaydi.
Qanday ishlaydi: baza JSON fayl sifatida botga yuboriladi, oxirgi fayl ko'rsatkichi botning tavsifida (description) saqlanadi. Ikkala telefon bir vaqtda o'zgartirsa, ma'lumotlar yozuvlar bo'yicha birlashtiriladi (hech narsa yo'qolmaydi).

## Cheklovlar
- Qarz to'langanini ilova faqat siz "To'lov" yozganingizdan keyin biladi.
- "SMS yuborildi" = telefon SMS ni operatorga topshirdi; yetib borganini kafolatlamaydi (hisobda mablag' bo'lishi kerak).
- Android bir vaqtda ~30 tadan ortiq SMS ni cheklaydi — so'rov chiqsa "Ruxsat" bosing.
- O'zbekcha ovoz telefonda o'rnatilmagan bo'lsa, ovozli xabar ruscha ovozda yaratiladi (Google "Speech Services" ni yangilang). WhatsApp audio faylni "ovozli xabar" emas, audio fayl sifatida jo'natadi.
- Bot tavsifi (description) sinxron ko'rsatkichi uchun ishlatiladi — botni boshqa maqsadda tavsifini o'zgartirmang.
- Telegram token kod ichida (`www/index.html`) — repo faqat Private bo'lsin. Xohlasangiz @BotFather'da tokenni yangilab, ilovada Sozlamalar → Telegram sozlamalariga kiriting (ikkala telefonda).
- Ilovani "Force stop" qilsangiz yoki o'chirib qayta o'rnatsangiz, ilovani bir marta ochish kerak.

## Telegram sozlamalari (Sozlamalar → "Telegram sozlamalari")
- Bot tokenini almashtirish mumkin (@BotFather). Bir nechta chat ID vergul bilan: shaxsiy chat, guruh (`-100...`), kanal.
- Guruh: botni guruhga qo'shing. Kanal: botni kanalga **administrator** qiling.
- "ID topish" tugmasi guruh/kanal ID sini o'zi topadi (avval o'sha yerga xabar yozing).
- Sinxronlash fayli **birinchi** chat ID ga yuboriladi.


## v2.7 o'zgarishlari
1. Mijoz sahifasida (va ro'yxatda) qalamcha: ism va telefonni keyin kiritish/tahrirlash.
2. Bosh sahifadan "Sotuvlar" bo'limi olib tashlandi, Kassa qoldi.
3. Kassa yangi ko'rinishda: balans kartasi, davr filtri, Hammasi/Kirim/Chiqim, qidiruv, kategoriya, kunlar bo'yicha guruh. Kirim/Chiqim — pastdan chiqadigan forma.
4. SMS yuborishda ilovaning "Davom etasizmi?" savoli yo'q — tugma bosilishi bilan yuboriladi.
5. Do'kon qarzlari, yetkazib beruvchilar, kirimlar, mening qarzlarim, xarajatlar, kassa, qarz/to'lov operatsiyalarida tahrirlash (qalamcha).
6. Barcha sodda prompt/confirm oynalari o'rniga zamonaviy forma va dialog oynalari.
7. Hisobotlar: Qarzlar / Sotuvlar / Kassa / Xarajatlar / Top qarzdorlar kartalari, 7 kun / Oy / Yil.
8. Ro'yxatlarda xulosa kartalari, qidiruv, filtrlar, qisman to'lov (−) tugmasi, bo'sh holat ekranlari.
9. Ikonka: qulupnay (fonsiz). Ilova nomi launcherda ikki qatorda: SIRIUS / STORE.
10. Kirim/Chiqim, xarajat, sotuv, do'kon qarzi, mening qarzlarim, kirimlar va yetkazib beruvchi formalarida: ovozli yozish (mikrofonni bosib turing, 2 daqiqagacha, 5 tagacha) va rasm qo'shish (kamera/galereya, 10 tagacha). Fayllar telefon xotirasida (IndexedDB) saqlanadi, ro'yxatda 📎 belgisi bilan ochiladi. Ruxsatlar: RECORD_AUDIO, CAMERA.

## v2.8 yangiliklari
- **Savdo (POS):** mahsulotlar bazasi (Ombor), savat, chegirma (so'm/%), qisman to'lov, qolgani mijoz qarziga; shtrix-kod skaneri (kamera, qo'lda, Bluetooth/USB skaner); kam qolgan mahsulot ogohlantirishi; qaytarish; chek (rasm, PDF, WhatsApp, Telegram, SMS, RawBT orqali termoprinter).
- **Mijozlar:** qarz/to'lov formasi rasm va ovoz bilan, bo'lib to'lash (2-12 oy), mijoz reytingi, akt sverka (PDF/rasm), to'lov cheki (SMS/Telegram/WhatsApp, avtomatik SMS), muddati o'tganlar uchun bosqichli eslatma, kontaktlar (.vcf) va Excel/CSV import.
- **Hisobotlar 2.0:** oraliq sana, grafiklar, foyda va sof foyda, top mahsulotlar, Excel (.xlsx, 8 varaq) va PDF eksport, Telegram'ga yuborish.
- **Xavfsizlik:** egasi/sotuvchi rollari (alohida PIN), faoliyat jurnali, o'chirilgan yozuvlar savati (hammasini tiklash), rasm/ovozni Telegram orqali boshqa telefonlarga o'tkazish, zaxira nusxani ulashish.
- **Qulaylik:** kunduzgi/tungi mavzu, shrift o'lchami, umumiy qidiruv, bosh sahifa xulosasi.
- **Native o'zgarish:** SiriusPlugin.java ga `shareData` (fayl ulashish) va `saveFile` ga binar (b64) qo'llab-quvvatlash qo'shildi.
