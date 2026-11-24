# 勿擾白名單

適用於 HyperOS 的 LSPosed 模組，讓選定 App 在勿擾模式下保留通知提醒，並可為每個 App 選擇自動亮屏或系統側邊光效。設定頁支援深色模式及系統主題色。

包名：`io.github.de0921188.dnd.whitelist`。0.2.4 起改用此包名以符合 LSPosed 倉庫規範。Android 會將舊包名版本視為另一個 App，設定不會自動繼承；請保留原設定並避免同時啟用兩個模組。

## 使用方式

1. 安裝支援 libxposed API 102 的 LSPosed，並安裝本模組。
2. 在 LSPosed 中啟用模組，勾選系統框架 `system` 及 `com.android.systemui` 作用域，然後重新開機。
3. 開啟模組設定頁，確認設定服務已連線，啟用勿擾例外並選擇需要的 App 和提醒方式。
4. 確認目標 App 的通知、聲音、震動及橫幅已在系統設定中開啟。側邊光效使用系統既有樣式，需先在 HyperOS 中啟用。

自動亮屏沿用系統的鎖屏與防誤觸條件。模組僅管理目前 Android 使用者；不同 HyperOS 版本的相容性可能有所差異。

## 建置

需要 Android SDK 37、JDK 21 與 Android SDK 設定。在專案的 `local.properties` 設定自己的 SDK 路徑後執行：

```powershell
.\build.ps1 -JavaHome 'JDK 21 路徑'
```

建置包含單元測試與 lint 檢查，產生的 APK 使用 debug 簽章。

## 技術資訊

模組使用 [libxposed API](https://github.com/libxposed/api) 102 和 [設定服務](https://github.com/libxposed/service)。設定服務不可用時，模組停止放行勿擾限制。模組沒有網路權限。
