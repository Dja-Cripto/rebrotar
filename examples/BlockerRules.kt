package expo.modules.rebrotarblocker

// As regras de decisão do bloqueio, sem nada do Android. Ficam separadas para poderem ser TESTADAS no computador
// (src/test/.../BlockerRulesTest.kt): cada proteção descrita em PROTECOES.md tem um teste que a garante.
object BlockerRules {

  // Endereços internos do navegador (histórico, configurações, downloads...). Não são páginas da internet:
  // nunca são lidos nem bloqueados, senão a pessoa não conseguiria nem limpar o histórico.
  private val internalSchemes = listOf(
    "chrome://", "chrome-native://", "chrome-search://", "chrome-untrusted://", "about:",
    "edge://", "brave://", "vivaldi://", "opera://", "kiwi://", "view-source:",
  )

  fun isInternalPage(address: String?): Boolean {
    val t = address?.trim()?.lowercase() ?: return false
    return internalSchemes.any { t.startsWith(it) }
  }

  // Telas do navegador que não são uma página da internet, pelo nome da tela (histórico, configurações...)
  private val nonPageScreens = listOf("History", "Settings", "Preference", "Download", "Bookmark")

  fun isNonPageScreen(screenName: String?): Boolean {
    val n = screenName ?: return false
    return nonPageScreens.any { n.contains(it, ignoreCase = true) }
  }

  // A visão geral das abas (a grade com todas as abas): tem os cartões das abas (nomes internos conhecidos) ou
  // vários botões "Fechar guia". Nessa tela aparecem os títulos das páginas abertas, que NÃO são uma página.
  fun looksLikeTabGrid(cardIdHits: Int, closeButtonHits: Int): Boolean = cardIdHits > 0 || closeButtonHits >= 2

  // O robô só lê o conteúdo da tela como "página" quando há motivo para crer que é uma página de verdade.
  // Qualquer sinal de que não é (digitando, tela interna, página inicial vazia, visão geral de abas, histórico...)
  // e ele não lê nada.
  fun shouldScanPage(
    typing: Boolean,
    internalAddress: Boolean,
    blankAddressBar: Boolean,
    tabGrid: Boolean,
    nonPageScreen: Boolean,
  ): Boolean = !typing && !internalAddress && !blankAddressBar && !tabGrid && !nonPageScreen

  // Primeiro número de um texto: "Alternar ou fechar guias. 5 guias abertas" vira 5
  fun firstNumber(text: String?): Int? = Regex("\\d+").find(text ?: "")?.value?.toIntOrNull()

  // O item "Fechar guia" do menu que abre ao segurar o botão de abas (vários idiomas)
  private val closeTabItems = setOf(
    "fechar guia", "close tab", "cerrar pestaña", "cerrar la pestaña", "fermer l'onglet",
    "tab schließen", "chiudi scheda",
  )

  fun isCloseTabItem(label: String?): Boolean {
    val t = label?.trim()?.lowercase() ?: return false
    return t in closeTabItems
  }

  // O botão de fechar de um cartão da grade de abas: "Fechar guia Xvideos", "Close Xvideos tab"
  private val cardCloseWords = Regex("^(fechar|close|cerrar|fermer|schlie|chiudi)\\b.*\\b(guia|tab|pesta|onglet|scheda|regist)", RegexOption.IGNORE_CASE)

  fun isCardCloseLabel(label: String?): Boolean = label != null && cardCloseWords.containsMatchIn(label.trim())

  // O botão de abas da barra do navegador: "Alternar ou fechar guias. 5 guias abertas" / "Switch or close tabs"
  private val tabSwitcherWords = Regex("(alternar ou fechar guias|switch or close tabs|cambiar o cerrar pesta|changer ou fermer les onglets|tab ?switcher)", RegexOption.IGNORE_CASE)

  fun isTabSwitcherLabel(label: String?): Boolean = label != null && tabSwitcherWords.containsMatchIn(label)

  // Fechou mesmo? Pela contagem de abas (diminuiu) ou, sem contagem, porque a tela não está mais bloqueada
  fun tabWasClosed(before: Int?, after: Int?, stillBlocked: Boolean): Boolean =
    if (before != null && after != null) after < before || !stillBlocked else !stillBlocked

  // Limite de ações com efeito colateral (como abrir abas): nunca mais que `max` dentro de `windowMs`.
  // Garantia por construção: o robô não consegue inundar o navegador de abas, aconteça o que acontecer.
  class Budget(private val max: Int, private val windowMs: Long) {
    private val times = ArrayList<Long>()

    @Synchronized
    fun tryTake(now: Long): Boolean {
      times.removeAll { now - it > windowMs }
      if (times.size >= max) return false
      times.add(now)
      return true
    }
  }
}
