# 勿擾白名單 · HyperOS DND Allowlist

讓指定 App 在 HyperOS 勿擾期間保留通知提醒，獨立設定聲音、震動及鎖屏效果。

目前版本 **0.2.7**，套件名稱為 `io.github.de0921188.dnd.whitelist`。

[下載正式 APK](https://github.com/de0921188/HyperOSDndWhitelist/releases/latest) · [LSPosed 官方倉庫](https://github.com/Xposed-Modules-Repo/io.github.de0921188.dnd.whitelist) · [問題回報](https://github.com/de0921188/HyperOSDndWhitelist/issues)

## 功能

- 按 App 選擇勿擾例外，並可按通知類別名稱勾選放行範圍。
- 每個 App 獨立選擇自動亮屏、系統側邊光效、跟隨系統鎖屏設定，或不亮屏且無側邊光效。
- 同一個設定視窗可分別勾選允許聲音與允許震動。例如只震動，或保留聲音與震動但讓螢幕保持暗著。
- 太陽／月亮圖示單擊切換系統勿擾。勿擾開啟顯示太陽，關閉顯示月亮；開啟時使用優先通知模式。
- 快速重啟選單可選系統介面、桌面、設定與安全服務，支援多選及全選。
- 本機 JSON 匯出與合併匯入，確認前顯示新增、衝突及未安裝項目。
- 相容性診斷、延遲測試通知、App 搜尋及已選取篩選。
- 繁體中文、簡體中文與英文，跟隨系統深色模式及動態主題色。

## 安裝與啟用

1. 使用支援 libxposed API 102 的 LSPosed。主要適配 HyperOS 3 / Android 16，其他 ROM 需驗證。
2. 安裝正式 APK，在 LSPosed 啟用模組，作用域選取系統框架 `system` 與系統介面 `com.android.systemui`。
3. 重新啟動手機，再打開設定頁，確認設定服務連線並開啟「啟用勿擾例外」。
4. 點選 App，選擇鎖屏效果與聲音、震動選項，按「儲存」。
5. 如需限制類別，點選該 App 的「通知類別」，勾選類別後儲存。全部取消表示允許該 App 的所有類別。
6. 開啟手機勿擾，使用真實來訊測試。一般規則修改約一秒後對新通知生效。更新涉及系統框架的版本時，重新啟動手機以載入新版。

0.2.4 起採用目前套件名稱。不同套件名稱或不同簽章的安裝有不同的資料範圍，升級前請備份設定。不同 Android 使用者、分身及工作設定檔需各自設定。

## 通知類別與鎖屏效果

類別清單沿用 App 建立的名稱，重複名稱會附頻道 ID 以便辨識。系統框架尚未回傳清單時，可點「用 Root 讀取類別」；手動輸入 ID 供進階設定使用。App 尚未建立類別時，收到通知後再查看。

側邊光效使用 HyperOS 現有樣式，需先在系統設定啟用。自動亮屏沿用鎖屏隱私與防誤觸條件。未勾選的類別保留勿擾限制，通知仍可留在通知中心。

允許聲音與震動沿用原通知頻道、系統音量及靜音設定。請在系統中開啟目標 App 的通知、鈴聲、震動與橫幅。網路來電與媒體播放另有處理路徑。

## 快捷操作與 Root

首頁勿擾按鈕及快速重啟需要 Root 授權。勿擾按鈕會核對實際系統狀態，關閉時暫停當前生效的自動規則並保留排程設定。快速重啟在選取並確認後執行，系統介面重啟可能短暫黑屏。桌面、設定及安全服務會結束既有程序，之後由系統或使用者再次開啟；桌面會另外啟動首頁。載入系統框架 Hook 仍需完整重新開機。

## 備份與診斷

在「更多設定」匯出目前 Android 使用者的設定。備份包含選取的套件、頻道與提醒偏好，請自行保管。匯入會套用備份總開關及衝突項目的規則，保留其他既有 App，並保留未安裝 App 的規則供後續使用。0.2.6 匯出格式為版本 3，可讀取舊格式。

相容性診斷顯示 Hook 安裝結果、錯誤次數及取樣耗時，實際效果需使用通知確認。延遲測試通知由本模組發出，若要驗證白名單例外，需將本模組加入白名單；測試其他 App 時使用該 App 的真實來訊。

## 0.2.7 更新

修正自動勿擾規則生效時快捷關閉失敗，補上指令結果診斷與三語錯誤提示。已在實機確認點一下開啟、再點一下關閉，太陽與月亮顯示正確。

## 驗證範圍

0.2.6 已驗證真實訊息的自動亮屏、聲音與震動，以及不亮屏且無側邊光效時保留聲音與震動。通知類別取消與恢復勾選可在無重開機下生效。SystemUI 快速重啟及月亮按鈕切換勿擾已通過實機測試。

ROM 私有實作具有版本差異。其他快速重啟項目、所有 ROM 與所有 App 的通知組合尚待個別驗證。

## 隱私

App 未宣告網路權限。設定儲存在本機 Xposed 設定服務；診斷回報使用簽章權限。公開專案文件使用一般化測試結果，裝置設定、私訊內容、實機截圖、備份與簽章材料保存在本機。

## 建置

需要 JDK 21、Android SDK 37，及專案使用的 Gradle 工具鏈。在 `local.properties` 指定自己的 SDK 路徑後執行。

```powershell
.\build.ps1 -JavaHome 'JDK 21 路徑'
```

腳本建置 Debug APK、單元測試與 lint。正式版可執行 `assembleRelease testDebugUnitTest lintDebug`，再用自己的簽章工具簽署產物。

原始碼位於 [HyperOSDndWhitelist](https://github.com/de0921188/HyperOSDndWhitelist)。模組使用 [libxposed API](https://github.com/libxposed/api) 102 與[設定服務](https://github.com/libxposed/service)。

## English

A HyperOS LSPosed module for per-app Do Not Disturb exceptions. Choose notification categories, sound, vibration, and lock screen behavior independently. Available screen modes include wake, native edge lighting, system default, and no wake or edge lighting.

Enable the module for `system` and `com.android.systemui`, then reboot. Notification permissions, channel settings, volume and lock screen privacy still apply. Root is required for the optional DND toggle, quick restart and fallback category lookup. Interface languages include Traditional Chinese, Simplified Chinese and English.

In 0.2.7, the DND shortcut also handles active automatic rules. The sun indicates DND is on, and the moon indicates DND is off. Tap to toggle.
