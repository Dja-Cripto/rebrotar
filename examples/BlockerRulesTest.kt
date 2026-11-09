package expo.modules.rebrotarblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Cada teste aqui garante uma proteção do bloqueio (ver PROTECOES.md). Se um deles quebrar, uma proteção foi desfeita.
class BlockerRulesTest {

  // P2: telas internas do navegador (histórico, configurações...) nunca são lidas nem bloqueadas
  @Test
  fun paginasInternasNaoSaoLidas() {
    assertTrue(BlockerRules.isInternalPage("chrome://history"))
    assertTrue(BlockerRules.isInternalPage("  Chrome://Settings "))
    assertTrue(BlockerRules.isInternalPage("about:blank"))
    assertTrue(BlockerRules.isInternalPage("edge://downloads"))
    assertFalse(BlockerRules.isInternalPage("xvideos.com"))
    assertFalse(BlockerRules.isInternalPage("google.com/search?q=chrome"))
    assertFalse(BlockerRules.isInternalPage(null))
  }

  @Test
  fun telasQueNaoSaoPaginaPeloNome() {
    assertTrue(BlockerRules.isNonPageScreen("org.chromium.chrome.browser.history.HistoryActivity"))
    assertTrue(BlockerRules.isNonPageScreen("org.chromium.chrome.browser.settings.SettingsActivity"))
    assertTrue(BlockerRules.isNonPageScreen("org.chromium.chrome.browser.download.home.DownloadActivity"))
    assertTrue(BlockerRules.isNonPageScreen("org.chromium.chrome.browser.bookmarks.BookmarkActivity"))
    assertFalse(BlockerRules.isNonPageScreen("org.chromium.chrome.browser.ChromeTabbedActivity"))
    assertFalse(BlockerRules.isNonPageScreen("org.chromium.chrome.browser.customtabs.CustomTabActivity"))
    assertFalse(BlockerRules.isNonPageScreen(null))
  }

  // P2: a visão geral das abas é reconhecida (cartões das abas ou vários botões de fechar aba)
  @Test
  fun visaoGeralDeAbasEReconhecida() {
    assertFalse(BlockerRules.looksLikeTabGrid(0, 0))
    assertFalse(BlockerRules.looksLikeTabGrid(0, 1))
    assertTrue(BlockerRules.looksLikeTabGrid(1, 0))
    assertTrue(BlockerRules.looksLikeTabGrid(0, 2))
    assertTrue(BlockerRules.looksLikeTabGrid(5, 5))
  }

  // P2 e P1: só lê a tela como página quando nenhum sinal diz que não é uma página
  @Test
  fun soLeAPaginaQuandoEPaginaDeVerdade() {
    assertTrue(BlockerRules.shouldScanPage(typing = false, internalAddress = false, blankAddressBar = false, tabGrid = false, nonPageScreen = false))
    assertFalse(BlockerRules.shouldScanPage(typing = true, internalAddress = false, blankAddressBar = false, tabGrid = false, nonPageScreen = false))
    assertFalse(BlockerRules.shouldScanPage(typing = false, internalAddress = true, blankAddressBar = false, tabGrid = false, nonPageScreen = false))
    assertFalse(BlockerRules.shouldScanPage(typing = false, internalAddress = false, blankAddressBar = true, tabGrid = false, nonPageScreen = false))
    assertFalse(BlockerRules.shouldScanPage(typing = false, internalAddress = false, blankAddressBar = false, tabGrid = true, nonPageScreen = false))
    assertFalse(BlockerRules.shouldScanPage(typing = false, internalAddress = false, blankAddressBar = false, tabGrid = false, nonPageScreen = true))
  }

  @Test
  fun contaAbasPeloTextoDoBotao() {
    assertEquals(5, BlockerRules.firstNumber("Alternar ou fechar guias. 5 guias abertas"))
    assertEquals(12, BlockerRules.firstNumber("Switch or close tabs. 12 tabs open"))
    assertEquals(1, BlockerRules.firstNumber("Alternar ou fechar guias. 1 guia aberta"))
    assertNull(BlockerRules.firstNumber("sem número"))
    assertNull(BlockerRules.firstNumber(null))
  }

  // P3: o item "Fechar guia" do menu do botão de abas é reconhecido em vários idiomas, e só ele
  @Test
  fun reconheceOItemFecharGuia() {
    assertTrue(BlockerRules.isCloseTabItem("Fechar guia"))
    assertTrue(BlockerRules.isCloseTabItem("  Close tab "))
    assertTrue(BlockerRules.isCloseTabItem("Cerrar pestaña"))
    assertTrue(BlockerRules.isCloseTabItem("Fermer l'onglet"))
    assertFalse(BlockerRules.isCloseTabItem("Nova guia"))
    assertFalse(BlockerRules.isCloseTabItem("Nova guia anônima"))
    assertFalse(BlockerRules.isCloseTabItem("Fechar guia Xvideos"))
    assertFalse(BlockerRules.isCloseTabItem(null))
  }

  @Test
  fun reconheceOBotaoDeFecharDeUmCartaoDaGrade() {
    assertTrue(BlockerRules.isCardCloseLabel("Fechar guia Xvideos"))
    assertTrue(BlockerRules.isCardCloseLabel("Close Xvideos tab"))
    assertTrue(BlockerRules.isCardCloseLabel("Cerrar pestaña Xvideos"))
    assertFalse(BlockerRules.isCardCloseLabel("Nova guia"))
    assertFalse(BlockerRules.isCardCloseLabel("Fechar"))
    assertFalse(BlockerRules.isCardCloseLabel(null))
  }

  @Test
  fun reconheceOBotaoDeAbasDaBarra() {
    assertTrue(BlockerRules.isTabSwitcherLabel("Alternar ou fechar guias. 5 guias abertas"))
    assertTrue(BlockerRules.isTabSwitcherLabel("Switch or close tabs. 3 tabs open"))
    assertFalse(BlockerRules.isTabSwitcherLabel("Nova guia"))
    assertFalse(BlockerRules.isTabSwitcherLabel(null))
  }

  // P3: só considera "fechou" quando há prova (a contagem de abas diminuiu ou a tela deixou de estar bloqueada)
  @Test
  fun sabeSeAAbaFoiFechada() {
    assertTrue(BlockerRules.tabWasClosed(before = 5, after = 4, stillBlocked = true))
    assertFalse(BlockerRules.tabWasClosed(before = 5, after = 5, stillBlocked = true))
    assertTrue(BlockerRules.tabWasClosed(before = 5, after = 5, stillBlocked = false))
    assertTrue(BlockerRules.tabWasClosed(before = 1, after = 1, stillBlocked = false))
    assertTrue(BlockerRules.tabWasClosed(before = null, after = null, stillBlocked = false))
    assertFalse(BlockerRules.tabWasClosed(before = null, after = null, stillBlocked = true))
    assertFalse(BlockerRules.tabWasClosed(before = 5, after = null, stillBlocked = true))
  }

  // P4: o robô nunca abre mais que o limite de abas na janela de tempo (não inunda o navegador)
  @Test
  fun limiteDeAbasNuncaEUltrapassado() {
    val tenMin = 10 * 60_000L
    val budget = BlockerRules.Budget(max = 3, windowMs = tenMin)
    assertTrue(budget.tryTake(0))
    assertTrue(budget.tryTake(1_000))
    assertTrue(budget.tryTake(2_000))
    // quarta tentativa dentro da janela: negada, quantas vezes forem pedidas
    assertFalse(budget.tryTake(3_000))
    assertFalse(budget.tryTake(60_000))
    assertFalse(budget.tryTake(tenMin))
    // passada a janela, volta a poder
    assertTrue(budget.tryTake(tenMin + 5_000))
  }
}
