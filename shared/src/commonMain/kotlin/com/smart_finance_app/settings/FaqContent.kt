package com.smart_finance_app.settings

internal data class FaqItem(
    val question: String,
    val answer: String
)

/**
 * FAQ questions and answers per language.
 * Languages without their own list fall back to English.
 * To add a language later, add a list below and a line in get().
 */
internal object FaqContent {

    fun get(lang: String): List<FaqItem> = when (lang) {
        "zh-TW" -> zhTW
        else    -> en
    }

    private val en = listOf(
        FaqItem(
            question = "Is my financial data safe?",
            answer = "Yes. We only request read-only access to your bank accounts, which means we can see balances and transactions but can never move money or make payments. Your data is stored securely and encrypted."
        ),
        FaqItem(
            question = "How does connecting my bank work?",
            answer = "Go to Accounts, tap Connect your bank and choose your bank. You'll be taken to your bank's own secure page to approve read-only access. We never see or store your online banking password."
        ),
        FaqItem(
            question = "How many bank accounts can I connect?",
            answer = "The Free plan lets you connect up to 2 bank accounts. The Basic plan, coming soon, will allow up to 6."
        ),
        FaqItem(
            question = "Why is a transaction in the wrong category?",
            answer = "Categories are assigned automatically and can occasionally be wrong. Open Transactions, tap Edit on the transaction and choose the correct category."
        ),
        FaqItem(
            question = "How are amounts in other currencies converted?",
            answer = "Amounts are converted into your chosen currency using the latest available exchange rates. If rates can't be loaded, amounts are shown in their original currency. You can change your currency in Settings."
        ),
        FaqItem(
            question = "Can I customise my dashboard?",
            answer = "Yes. Tap Customise on the Dashboard to add or remove charts and switch each chart between full and half width."
        ),
        FaqItem(
            question = "How are upcoming bills predicted?",
            answer = "We look for payments that repeat weekly or monthly in your transaction history and estimate when they'll next be due. These are predictions, so actual dates and amounts may differ."
        ),
        FaqItem(
            question = "I forgot my password. What should I do?",
            answer = "On the sign-in screen, tap Forgot password? and enter your email. We'll send you a reset link, which can be used once and expires after 15 minutes."
        ),
        FaqItem(
            question = "When will the Basic plan be available?",
            answer = "The Basic plan (£5/month) is coming soon and will add more linked accounts, budget alerts, monthly email reports and more. You can see the full list in Settings under Manage Subscription."
        ),
        FaqItem(
            question = "How do I delete my account?",
            answer = "Contact us through Support and we'll delete your account and all associated data."
        )
    )

    private val zhTW = listOf(
        FaqItem(
            question = "我的財務資料安全嗎？",
            answer = "安全。我們只要求對您銀行帳戶的唯讀存取權限，也就是說我們可以查看餘額和交易記錄，但永遠無法移動資金或進行付款。您的資料會以加密方式安全儲存。"
        ),
        FaqItem(
            question = "如何連結我的銀行？",
            answer = "前往「帳戶」，點擊「連結您的銀行」並選擇您的銀行。系統會將您導向銀行本身的安全頁面以授權唯讀存取。我們不會看到或儲存您的網路銀行密碼。"
        ),
        FaqItem(
            question = "我可以連結多少個銀行帳戶？",
            answer = "免費方案最多可連結 2 個銀行帳戶。即將推出的基本方案最多可連結 6 個。"
        ),
        FaqItem(
            question = "為什麼交易被歸到錯誤的類別？",
            answer = "類別是自動分配的，偶爾可能會出錯。請開啟「交易記錄」，點擊該筆交易的「編輯」，然後選擇正確的類別。"
        ),
        FaqItem(
            question = "其他貨幣的金額如何換算？",
            answer = "金額會依最新可取得的匯率換算成您選擇的貨幣。若無法取得匯率，金額將以原始貨幣顯示。您可以在「設定」中更改貨幣。"
        ),
        FaqItem(
            question = "我可以自訂儀表板嗎？",
            answer = "可以。在主頁點擊「自訂」即可新增或移除圖表，並將每個圖表切換為全寬或半寬。"
        ),
        FaqItem(
            question = "即將到來的帳單是如何預測的？",
            answer = "我們會從您的交易記錄中找出每週或每月重複的付款，並估算下次到期的時間。這些只是預測，實際日期和金額可能會有所不同。"
        ),
        FaqItem(
            question = "我忘記密碼了，該怎麼辦？",
            answer = "在登入畫面點擊「忘記密碼？」並輸入您的電子郵件。我們會寄送重設連結給您，該連結僅能使用一次，並於 15 分鐘後失效。"
        ),
        FaqItem(
            question = "基本方案什麼時候推出？",
            answer = "基本方案（每月 £5）即將推出，將提供更多可連結帳戶、預算提醒、每月電子郵件報告等功能。您可以在「設定」的「管理訂閱」中查看完整清單。"
        ),
        FaqItem(
            question = "如何刪除我的帳戶？",
            answer = "請透過「客服支援」與我們聯絡，我們將刪除您的帳戶及所有相關資料。"
        )
    )
}