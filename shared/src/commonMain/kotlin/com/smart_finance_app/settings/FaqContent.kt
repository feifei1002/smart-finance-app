package com.smart_finance_app.settings

internal data class FaqItem(
    val question: String,
    val answer: String
)

/**
 * FAQ questions and answers per language.
 *
 * Wrap screen, button and plan names in **double asterisks** to show them in bold
 * (FaqScreen converts them). Use the exact label from AppStrings.kt for that language,
 * so users see the same words on screen.
 *
 * Any language not listed in get() falls back to English.
 */
internal object FaqContent {

    fun get(lang: String): List<FaqItem> = when (lang) {
        "es"    -> es
        "fr"    -> fr
        "nl"    -> nl
        "de"    -> de
        "it"    -> it
        "pl"    -> pl
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
            answer = "Go to **Accounts**, tap **Connect your bank** and choose your bank. You'll be taken to your bank's own secure page to approve read-only access. We never see or store your online banking password."
        ),
        FaqItem(
            question = "How many bank accounts can I connect?",
            answer = "The **Free** plan lets you connect up to 2 bank accounts. The **Basic** plan, coming soon, will allow up to 6."
        ),
        FaqItem(
            question = "Why is a transaction in the wrong category?",
            answer = "Categories are assigned automatically and can occasionally be wrong. Open **Transactions**, tap **Edit** on the transaction and choose your preferred category."
        ),
        FaqItem(
            question = "How are amounts in other currencies converted?",
            answer = "Amounts are converted into your chosen currency using the latest available exchange rates. If rates can't be loaded, amounts are shown in their original currency. You can change your currency in **Settings**."
        ),
        FaqItem(
            question = "Can I customise my dashboard?",
            answer = "Yes. Tap **Customise** on the **Dashboard** to add or remove charts and switch each chart between full and half width."
        ),
        FaqItem(
            question = "How do I add more charts to the Dashboard?",
            answer = "On the **Dashboard**, tap **+ Charts** to see the charts you can add, then tap **+ Add** next to the one you want."
        ),
        FaqItem(
            question = "How do I delete my account?",
            answer = "Contact us through **Support** and we'll delete your account and all associated data."
        )
    )

    private val es = listOf(
        FaqItem(
            question = "¿Están seguros mis datos financieros?",
            answer = "Sí. Solo solicitamos acceso de solo lectura a tus cuentas bancarias, lo que significa que podemos ver saldos y transacciones, pero nunca podemos mover dinero ni realizar pagos. Tus datos se almacenan de forma segura y cifrada."
        ),
        FaqItem(
            question = "¿Cómo funciona la conexión con mi banco?",
            answer = "Ve a **Cuentas**, toca **Conectar tu banco** y elige tu banco. Se te dirigirá a la página segura de tu propio banco para aprobar el acceso de solo lectura. Nunca vemos ni guardamos la contraseña de tu banca online."
        ),
        FaqItem(
            question = "¿Cuántas cuentas bancarias puedo conectar?",
            answer = "El plan **Gratis** te permite conectar hasta 2 cuentas bancarias. El plan **Básico**, que llegará próximamente, permitirá hasta 6."
        ),
        FaqItem(
            question = "¿Por qué una transacción está en la categoría incorrecta?",
            answer = "Las categorías se asignan automáticamente y a veces pueden ser incorrectas. Abre **Transacciones**, toca **Editar** en la transacción y elige la categoría que prefieras."
        ),
        FaqItem(
            question = "¿Cómo se convierten los importes en otras monedas?",
            answer = "Los importes se convierten a la moneda que elijas usando los tipos de cambio más recientes disponibles. Si no se pueden cargar los tipos, los importes se muestran en su moneda original. Puedes cambiar tu moneda en **Ajustes**."
        ),
        FaqItem(
            question = "¿Puedo personalizar mi panel?",
            answer = "Sí. Toca **Personalizar** en **Inicio** para añadir o quitar gráficos y cambiar cada gráfico entre ancho completo y medio ancho."
        ),
        FaqItem(
            question = "¿Cómo añado más gráficos al panel?",
            answer = "En **Inicio**, toca **+ Gráficos** para ver los gráficos que puedes añadir y luego toca **+ Añadir** junto al que quieras."
        ),
        FaqItem(
            question = "¿Cómo elimino mi cuenta?",
            answer = "Contáctanos a través de **Soporte** y eliminaremos tu cuenta y todos los datos asociados."
        )
    )

    private val fr = listOf(
        FaqItem(
            question = "Mes données financières sont-elles en sécurité ?",
            answer = "Oui. Nous demandons uniquement un accès en lecture seule à vos comptes bancaires : nous pouvons voir vos soldes et vos transactions, mais nous ne pouvons jamais déplacer d'argent ni effectuer de paiements. Vos données sont stockées de manière sécurisée et chiffrées."
        ),
        FaqItem(
            question = "Comment fonctionne la connexion à ma banque ?",
            answer = "Allez dans **Comptes**, appuyez sur **Connecter votre banque** et choisissez votre banque. Vous serez redirigé vers la page sécurisée de votre banque pour autoriser l'accès en lecture seule. Nous ne voyons ni ne stockons jamais le mot de passe de votre banque en ligne."
        ),
        FaqItem(
            question = "Combien de comptes bancaires puis-je connecter ?",
            answer = "Le plan **Gratuit** permet de connecter jusqu'à 2 comptes bancaires. Le plan **Basique**, bientôt disponible, en permettra jusqu'à 6."
        ),
        FaqItem(
            question = "Pourquoi une transaction est-elle dans la mauvaise catégorie ?",
            answer = "Les catégories sont attribuées automatiquement et peuvent parfois être erronées. Ouvrez **Transactions**, appuyez sur **Modifier** sur la transaction et choisissez la catégorie de votre choix."
        ),
        FaqItem(
            question = "Comment les montants dans d'autres devises sont-ils convertis ?",
            answer = "Les montants sont convertis dans la devise de votre choix selon les derniers taux de change disponibles. Si les taux ne peuvent pas être chargés, les montants sont affichés dans leur devise d'origine. Vous pouvez changer de devise dans **Paramètres**."
        ),
        FaqItem(
            question = "Puis-je personnaliser mon tableau de bord ?",
            answer = "Oui. Appuyez sur **Personnaliser** dans **Accueil** pour ajouter ou retirer des graphiques et passer chaque graphique en pleine largeur ou en demi-largeur."
        ),
        FaqItem(
            question = "Comment ajouter d'autres graphiques au tableau de bord ?",
            answer = "Dans **Accueil**, appuyez sur **+ Graphiques** pour voir les graphiques disponibles, puis appuyez sur **+ Ajouter** à côté de celui que vous voulez."
        ),
        FaqItem(
            question = "Comment supprimer mon compte ?",
            answer = "Contactez-nous via **Assistance** et nous supprimerons votre compte ainsi que toutes les données associées."
        )
    )

    private val nl = listOf(
        FaqItem(
            question = "Zijn mijn financiële gegevens veilig?",
            answer = "Ja. We vragen alleen leestoegang tot je bankrekeningen. Dat betekent dat we saldi en transacties kunnen zien, maar nooit geld kunnen verplaatsen of betalingen kunnen doen. Je gegevens worden veilig en versleuteld opgeslagen."
        ),
        FaqItem(
            question = "Hoe werkt het koppelen van mijn bank?",
            answer = "Ga naar **Rekeningen**, tik op **Jouw bank koppelen** en kies je bank. Je wordt doorgestuurd naar de beveiligde pagina van je eigen bank om leestoegang goed te keuren. We zien of bewaren nooit het wachtwoord van je internetbankieren."
        ),
        FaqItem(
            question = "Hoeveel bankrekeningen kan ik koppelen?",
            answer = "Met het **Gratis**-abonnement kun je tot 2 bankrekeningen koppelen. Het **Basis**-abonnement, dat binnenkort beschikbaar is, maakt tot 6 rekeningen mogelijk."
        ),
        FaqItem(
            question = "Waarom staat een transactie in de verkeerde categorie?",
            answer = "Categorieën worden automatisch toegewezen en kunnen soms onjuist zijn. Open **Transacties**, tik op **Bewerken** bij de transactie en kies de categorie die je wilt."
        ),
        FaqItem(
            question = "Hoe worden bedragen in andere valuta omgerekend?",
            answer = "Bedragen worden omgerekend naar je gekozen valuta met de meest recente beschikbare wisselkoersen. Als de koersen niet geladen kunnen worden, worden bedragen in hun oorspronkelijke valuta getoond. Je kunt je valuta wijzigen in **Instellingen**."
        ),
        FaqItem(
            question = "Kan ik mijn dashboard aanpassen?",
            answer = "Ja. Tik op **Aanpassen** op het **Dashboard** om grafieken toe te voegen of te verwijderen en elke grafiek op volledige of halve breedte te zetten."
        ),
        FaqItem(
            question = "Hoe voeg ik meer grafieken toe aan het dashboard?",
            answer = "Tik op het **Dashboard** op **+ Grafieken** om de grafieken te zien die je kunt toevoegen en tik daarna op **+ Toevoegen** naast de grafiek die je wilt."
        ),
        FaqItem(
            question = "Hoe verwijder ik mijn account?",
            answer = "Neem contact met ons op via **Support** en we verwijderen je account en alle bijbehorende gegevens."
        )
    )

    private val de = listOf(
        FaqItem(
            question = "Sind meine Finanzdaten sicher?",
            answer = "Ja. Wir fordern nur Lesezugriff auf deine Bankkonten an. Das heißt, wir können Kontostände und Transaktionen sehen, aber niemals Geld bewegen oder Zahlungen ausführen. Deine Daten werden sicher und verschlüsselt gespeichert."
        ),
        FaqItem(
            question = "Wie funktioniert das Verknüpfen meiner Bank?",
            answer = "Gehe zu **Konten**, tippe auf **Bank verknüpfen** und wähle deine Bank. Du wirst zur sicheren Seite deiner Bank weitergeleitet, um den Lesezugriff zu bestätigen. Wir sehen oder speichern niemals dein Onlinebanking-Passwort."
        ),
        FaqItem(
            question = "Wie viele Bankkonten kann ich verknüpfen?",
            answer = "Mit dem Plan **Kostenlos** kannst du bis zu 2 Bankkonten verknüpfen. Der Plan **Basic**, der bald verfügbar ist, ermöglicht bis zu 6."
        ),
        FaqItem(
            question = "Warum ist eine Transaktion in der falschen Kategorie?",
            answer = "Kategorien werden automatisch zugewiesen und können gelegentlich falsch sein. Öffne **Transaktionen**, tippe bei der Transaktion auf **Bearbeiten** und wähle die gewünschte Kategorie."
        ),
        FaqItem(
            question = "Wie werden Beträge in anderen Währungen umgerechnet?",
            answer = "Beträge werden mit den neuesten verfügbaren Wechselkursen in deine gewählte Währung umgerechnet. Wenn die Kurse nicht geladen werden können, werden Beträge in ihrer Originalwährung angezeigt. Du kannst deine Währung in den **Einstellungen** ändern."
        ),
        FaqItem(
            question = "Kann ich mein Dashboard anpassen?",
            answer = "Ja. Tippe auf dem **Dashboard** auf **Anpassen**, um Diagramme hinzuzufügen oder zu entfernen und jedes Diagramm zwischen voller und halber Breite umzuschalten."
        ),
        FaqItem(
            question = "Wie füge ich dem Dashboard weitere Diagramme hinzu?",
            answer = "Tippe auf dem **Dashboard** auf **+ Diagramme**, um die verfügbaren Diagramme zu sehen, und tippe dann neben dem gewünschten Diagramm auf **+ Hinzufügen**."
        ),
        FaqItem(
            question = "Wie lösche ich mein Konto?",
            answer = "Kontaktiere uns über **Support** und wir löschen dein Konto und alle zugehörigen Daten."
        )
    )

    private val it = listOf(
        FaqItem(
            question = "I miei dati finanziari sono al sicuro?",
            answer = "Sì. Richiediamo solo l'accesso in sola lettura ai tuoi conti bancari: possiamo vedere saldi e transazioni, ma non possiamo mai spostare denaro né effettuare pagamenti. I tuoi dati sono archiviati in modo sicuro e crittografati."
        ),
        FaqItem(
            question = "Come funziona il collegamento della mia banca?",
            answer = "Vai su **Conti**, tocca **Collega la tua banca** e scegli la tua banca. Verrai indirizzato alla pagina sicura della tua banca per approvare l'accesso in sola lettura. Non vediamo né memorizziamo mai la password del tuo home banking."
        ),
        FaqItem(
            question = "Quanti conti bancari posso collegare?",
            answer = "Il piano **Gratuito** ti consente di collegare fino a 2 conti bancari. Il piano **Base**, in arrivo, ne consentirà fino a 6."
        ),
        FaqItem(
            question = "Perché una transazione è nella categoria sbagliata?",
            answer = "Le categorie vengono assegnate automaticamente e a volte possono essere errate. Apri **Transazioni**, tocca **Modifica** sulla transazione e scegli la categoria che preferisci."
        ),
        FaqItem(
            question = "Come vengono convertiti gli importi in altre valute?",
            answer = "Gli importi vengono convertiti nella valuta scelta usando i tassi di cambio più recenti disponibili. Se non è possibile caricare i tassi, gli importi vengono mostrati nella valuta originale. Puoi cambiare valuta nelle **Impostazioni**."
        ),
        FaqItem(
            question = "Posso personalizzare la mia dashboard?",
            answer = "Sì. Tocca **Personalizza** nella **Dashboard** per aggiungere o rimuovere grafici e impostare ogni grafico a larghezza intera o a metà."
        ),
        FaqItem(
            question = "Come aggiungo altri grafici alla Dashboard?",
            answer = "Nella **Dashboard**, tocca **+ Grafici** per vedere i grafici che puoi aggiungere, poi tocca **+ Aggiungi** accanto a quello che desideri."
        ),
        FaqItem(
            question = "Come elimino il mio account?",
            answer = "Contattaci tramite **Assistenza** ed elimineremo il tuo account e tutti i dati associati."
        )
    )

    private val pl = listOf(
        FaqItem(
            question = "Czy moje dane finansowe są bezpieczne?",
            answer = "Tak. Prosimy wyłącznie o dostęp tylko do odczytu do Twoich kont bankowych, co oznacza, że widzimy salda i transakcje, ale nigdy nie możemy przenosić pieniędzy ani dokonywać płatności. Twoje dane są bezpiecznie przechowywane i szyfrowane."
        ),
        FaqItem(
            question = "Jak działa łączenie z moim bankiem?",
            answer = "Przejdź do **Konta**, dotknij **Połącz swój bank** i wybierz swój bank. Zostaniesz przekierowany na bezpieczną stronę Twojego banku, aby zatwierdzić dostęp tylko do odczytu. Nigdy nie widzimy ani nie przechowujemy Twojego hasła do bankowości internetowej."
        ),
        FaqItem(
            question = "Ile kont bankowych mogę połączyć?",
            answer = "Plan **Bezpłatny** pozwala połączyć do 2 kont bankowych. Plan **Podstawowy**, dostępny wkrótce, pozwoli połączyć do 6."
        ),
        FaqItem(
            question = "Dlaczego transakcja ma niewłaściwą kategorię?",
            answer = "Kategorie są przypisywane automatycznie i czasami mogą być błędne. Otwórz **Transakcje**, dotknij **Edytuj** przy transakcji i wybierz preferowaną kategorię."
        ),
        FaqItem(
            question = "Jak przeliczane są kwoty w innych walutach?",
            answer = "Kwoty są przeliczane na wybraną przez Ciebie walutę według najnowszych dostępnych kursów wymiany. Jeśli nie można załadować kursów, kwoty są wyświetlane w oryginalnej walucie. Walutę możesz zmienić w sekcji **Ustawienia**."
        ),
        FaqItem(
            question = "Czy mogę dostosować panel główny?",
            answer = "Tak. W widoku **Panel główny** dotknij **Dostosuj**, aby dodawać lub usuwać wykresy i przełączać każdy wykres między pełną a połową szerokości."
        ),
        FaqItem(
            question = "Jak dodać więcej wykresów do panelu głównego?",
            answer = "W widoku **Panel główny** dotknij **+ Wykresy**, aby zobaczyć wykresy, które możesz dodać, a następnie dotknij **+ Dodaj** obok wybranego wykresu."
        ),
        FaqItem(
            question = "Jak usunąć moje konto?",
            answer = "Skontaktuj się z nami przez **Pomoc**, a usuniemy Twoje konto i wszystkie powiązane dane."
        )
    )

    private val zhTW = listOf(
        FaqItem(
            question = "我的財務資料安全嗎？",
            answer = "安全。我們只要求對您銀行帳戶的唯讀存取權限，也就是說我們可以查看餘額和交易記錄，但永遠無法移動資金或進行付款。您的資料會以加密方式安全儲存。"
        ),
        FaqItem(
            question = "如何連結我的銀行？",
            answer = "前往「**帳戶**」，點擊「**連結您的銀行**」並選擇您的銀行。系統會將您導向銀行本身的安全頁面以授權唯讀存取。我們不會看到或儲存您的網路銀行密碼。"
        ),
        FaqItem(
            question = "我可以連結多少個銀行帳戶？",
            answer = "**免費**方案最多可連結 2 個銀行帳戶。即將推出的**基本**方案最多可連結 6 個。"
        ),
        FaqItem(
            question = "為什麼交易被歸到錯誤的類別？",
            answer = "類別是自動分配的，偶爾可能會出錯。請開啟「**交易記錄**」，點擊該筆交易的「**編輯**」，然後選擇您偏好的類別。"
        ),
        FaqItem(
            question = "其他貨幣的金額如何換算？",
            answer = "金額會依最新可取得的匯率換算成您選擇的貨幣。若無法取得匯率，金額將以原始貨幣顯示。您可以在「**設定**」中更改貨幣。"
        ),
        FaqItem(
            question = "我可以自訂儀表板嗎？",
            answer = "可以。在「**主頁**」點擊「**自訂**」即可新增或移除圖表，並將每個圖表切換為全寬或半寬。"
        ),
        FaqItem(
            question = "如何在主頁新增更多圖表？",
            answer = "在「**主頁**」點擊「**+ 圖表**」查看可新增的圖表，然後點擊想要的圖表旁的「**+ 新增**」。"
        ),
        FaqItem(
            question = "如何刪除我的帳戶？",
            answer = "請透過「**客服支援**」與我們聯絡，我們將刪除您的帳戶及所有相關資料。"
        )
    )
}