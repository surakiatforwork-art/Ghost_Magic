# Ghost Magic Android — independent source implementation

โครงการนี้เริ่มสร้างแอปใหม่จากซอร์สของเราเอง โดยใช้แนวคิด Trigger → Condition → Action ของ Automagic และข้อมูลที่ยืนยันใน blueprint เป็นแหล่งอ้างอิง ไม่ใช้ DEX, layouts หรือ helper APK จากต้นฉบับใน APK ใหม่นี้

**สถานะ: 0.4.0 experimental / เพิ่ม OCR Text Reader** ยังไม่ใช่ตัวแทน Automagic ที่สมบูรณ์ ไฟล์ `.flows` นำเข้าเพื่อเก็บและตรวจสอบได้ แต่ยังไม่แปลงเป็น Flow ที่รันได้

ดาวน์โหลด APK และ SHA-256 ได้ที่ [GitHub Releases](https://github.com/surakiatforwork-art/Ghost_Magic/releases) รุ่นปัจจุบันเป็น experimental prerelease ลงนามด้วย debug key สำหรับการทดลอง

## เพิ่มใน 0.4

- Action `action.ocr.read` อ่านข้อความ Latin จากภาพที่เลือกผ่าน Android document picker หรือหน้าจอบน Android 11+ เมื่อเปิด screenshot-only Accessibility service
- กำหนดพื้นที่เป็นเปอร์เซ็นต์ หมุนภาพ และเก็บผลเป็นตัวแปร string พร้อม error route; การอ่านไม่พบข้อความให้ string ว่าง ไม่ใช่ error
- ใช้ bundled on-device ML Kit SDK และเอา network permission ออกจาก manifest; ภาษาไทยยังไม่รองรับ ผล OCR อาจคงอยู่ในตัวแปรที่บันทึกใน History
- อ่านวิธีใช้ สิทธิ์ ข้อจำกัด capture/timeout และข้อมูลที่จัดเก็บใน [OCR_ACTION.md](docs/OCR_ACTION.md); แยกหลักฐานอ้างอิงไว้ใน [MACRORIFY_OCR_STUDY.md](docs/MACRORIFY_OCR_STUDY.md)

## เพิ่มใน 0.3

- Import อ่าน native JSON หรือ XML `.flows` ผ่านตัวเลือกไฟล์ Android; งานอ่าน/วิเคราะห์อยู่บน worker thread
- Legacy library แยกจาก runnable FlowStore; เก็บต้นฉบับโดยไม่เปลี่ยนไบต์ พร้อม SHA-256, deduplication และตรวจ integrity ก่อนอ่าน/ส่งออก
- ตัวอ่าน shared Trigger/Condition/Action, container references, connection NORMAL/TRUE/FALSE/EXCEPTION/NOTE และ execution policy ในขอบเขต blueprint
- รายงานชื่อซ้ำ การอ้างอิงหาย เส้นทางเชื่อมที่ผิด fan-out และข้อมูลที่ยังไม่รู้จัก พร้อมตำแหน่งบรรทัด; XML tree inspector และ Flow inventory แบบอ่านอย่างเดียว
- เก็บ element/attribute/CDATA/comment ที่ยังแปลงไม่ได้ไว้ในไฟล์ต้นฉบับครบ โดยไม่สร้าง native Flow หรือเปิด schedule จากค่า enabled เดิม
- Export original `.flows` แบบไบต์ต่อไบต์ และ export รายงาน UTF-8 ที่ผูกกับ source SHA-256
- ปฏิเสธ DTD/external entities และ input ที่เกินขนาด/ความซับซ้อน; หน้าจอ preview จำกัดจำนวนรายการ แต่ไม่ตัดต้นฉบับ

**ยังไม่มี export จริงจาก Automagic ใน workspace ที่ใช้ยืนยันการแปลง** การทดสอบส่วนนี้ใช้ synthetic fixtures ที่ระบุที่มาไว้ชัดเจน อ่าน [LEGACY_IMPORT.md](docs/LEGACY_IMPORT.md) ก่อนตีความว่าเป็น migration ที่พร้อมรัน

## เพิ่มใน 0.2

- Periodic trigger ทุก 15–10080 นาที ผ่าน Android JobScheduler เป็นเวลาที่ยืดหยุ่นได้ ไม่ใช่ exact alarm
- หน้าจอ Schedule สำหรับเปิด ปิด และปรับรอบ; Android เก็บ job ข้ามการ reboot ปกติ; import/export ไม่พาสถานะ enabled ไปด้วย
- Journal ใน SQLite บันทึก revision/source/status/trace ของ 100 run ล่าสุด ดูได้จาก History รวม manual run
- Shared execution guard ระหว่างหน้าจอและ JobService; callback ที่ใช้ generation/revision เก่าไม่มีสิทธิ์รัน Flow ใหม่
- จอง occurrence และสร้าง RUNNING record ใน transaction เดียวก่อน side effects; กัน delivery ซ้ำภายใน interval เดิม; เมื่อ process หายจะเปลี่ยน RUNNING เดิมเป็น INTERRUPTED เมื่อเปิด runtime ครั้งต่อไป
- System stop และ Disable ขอให้หยุดแบบ cooperative; ไม่ replay รอบที่หยุดทันที และไม่อ้างว่าย้อน side effects ที่เกิดไปแล้วได้
- แยก Android adapters ให้ใช้ application context ได้; ไม่อนุญาต Wi-Fi panel ใน scheduled Flow

รายละเอียด timing, crash recovery และข้อจำกัดอยู่ใน [BACKGROUND_RUNTIME.md](docs/BACKGROUND_RUNTIME.md)

## สิ่งที่ทำงานแล้ว

- สร้าง/แก้ไข/ลบ Flow; แก้ตำแหน่ง node บนกราฟ; ตั้งเส้นทาง next/true/false/error; Fit และมุมมอง 1:1; รายการ Nodes สำหรับเข้าถึงการตั้งค่าโดยไม่ต้องแตะกราฟ
- Manual/periodic trigger, ตัวแปร string ระดับแต่ละ run, `${name}` interpolation, variable equals, battery above, log และ delay
- Show notification ผ่าน channel และ runtime permission; system brightness ผ่าน special access พร้อมตรวจ adaptive brightness; Wi-Fi settings panel ที่ระบุชัดว่าให้ผู้ใช้ควบคุมเอง
- บันทึกแบบ atomic ในพื้นที่ส่วนตัวของแอป; import/export `.ghostflow.json` ผ่านตัวเลือกไฟล์ Android; imported Flow ไม่รันเอง
- ตัวรันแยกจาก Android: typed errors, error branch, per-node trace, cancellation, 1,000 steps และ 120 วินาทีต่อ run
- package ใหม่ `com.ghostmagic.android` จึงแยกข้อมูลจาก Automagic เดิม; min API 26, compile/target API 36

## สิ่งที่ยังไม่รองรับ

Event triggers, exact alarms, resume จาก node หลัง process death, global typed variables, executable legacy `.flows` conversion, scripts, plugins, widgets, HTTP/file actions, Accessibility gestures/window automation, notification listener, direct Wi-Fi/Bluetooth/mobile-data toggles และ privileged bridge ยังไม่ได้ implement การรองรับ SDK ไม่ใช่หลักฐานว่า action ทำงานบนโทรศัพท์ทุกเครื่อง

Manual run อยู่ใน lifecycle หน้าจอแอป เมื่อแอปถูกซ่อนจะขอยกเลิก ส่วน periodic run อยู่ภายใต้ JobService และข้อจำกัดของ Android การ force-stop แอปหยุดการส่งงานจนกว่าจะเปิดแอปอีกครั้ง ผลข้างเคียงที่เกิดไปแล้วไม่ย้อนกลับ และไม่มีการ resume node หรือ replay รอบที่ค้างเอง

## Build และทดสอบ

ต้องมี JDK 17, Android SDK platform 36 และ build-tools; Gradle wrapper รวมไว้แล้ว SDK ค้นจาก `ANDROID_HOME` หรือ `local.properties` ของเครื่อง

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
# บน emulator/device ที่ระบุไว้สำหรับการทดสอบเท่านั้น
$env:ANDROID_SERIAL = 'emulator-5582'
.\gradlew.bat :app:connectedDebugAndroidTest
```

Android integration tests เปลี่ยน notification permission/AppOps และค่าความสว่างในอุปกรณ์ทดสอบของแอปนี้ แล้วคืนค่าความสว่างเดิม รวมทั้งสร้าง/ยกเลิก schedule ชั่วคราว ใช้ emulator แยก ไม่ควรรันโดยเลือกอุปกรณ์ส่วนตัวผิดเครื่อง `tools/background_smoke.py` ตรวจเพิ่มเติมโดยบังคับส่ง job เพื่อทดสอบ runtime, timeout, kill process และ reboot; ไม่ใช่ผลทดสอบความแม่นยำตามรอบจริง 15 นาที

Debug APK ใช้กุญแจ debug ของเครื่อง build; เป็นรุ่นสำหรับการทดลอง ยังไม่ใช่ release signing สำหรับเผยแพร่

## หลักฐานและทิศทางงาน

- [ผลการตรวจรุ่นนี้](docs/VALIDATION.md)
- [ข้อจำกัด Android และแนวทางย้าย action](docs/COMPATIBILITY.md)
- [แผนงานและเกณฑ์ยอมรับแต่ละระยะ](docs/ROADMAP.md)
- [รูปแบบ Flow และ execution semantics](docs/FLOW_FORMAT.md)
- [รายการติดตามความสามารถเดิม](docs/migration-ledger.json): ทุกแถวเป็น resource candidate; สถานะ partial analogue ไม่ใช่ยืนยัน semantic parity

งานต่อไปต้องใช้ Flow จริงที่ export จาก Automagic และผลที่ผู้ใช้ยืนยัน เพื่อทำ regression fixtures โดยเฉพาะการแตกกิ่ง ตัวแปร execution policy และ action ที่ Android เปลี่ยนข้อจำกัด
