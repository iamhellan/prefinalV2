package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_MOBI_id_authorization_fastgames {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(List.of("--start-maximized"))
        );
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(null)
        );
        page = context.newPage();
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    // ===== УТИЛИТЫ ============================================================

    private Frame findFrameWithSelector(Page p, String selector, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Page pg : p.context().pages()) {
                for (Frame f : pg.frames()) {
                    try {
                        if (f.locator(selector).count() > 0) {
                            System.out.println("[DEBUG] Нашли селектор в фрейме: " + f.url());
                            return f;
                        }
                    } catch (Throwable ignore) {}
                }
            }
            p.waitForTimeout(300);
        }
        return null;
    }

    private Locator smartLocator(Page p, String selector, int timeoutMs) {
        Locator direct = p.locator(selector);
        if (direct.count() > 0) return direct;
        Frame f = findFrameWithSelector(p, selector, timeoutMs);
        if (f != null) return f.locator(selector);
        throw new RuntimeException("Элемент не найден: " + selector);
    }

    private void robustClick(Page p, Locator loc, int timeoutMs, String debugName) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        RuntimeException lastErr = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                loc.first().scrollIntoViewIfNeeded();
                loc.first().click(new Locator.ClickOptions().setTimeout(3000));
                return;
            } catch (RuntimeException e1) {
                lastErr = e1;
                try {
                    loc.first().click(new Locator.ClickOptions().setTimeout(2500).setForce(true));
                    return;
                } catch (RuntimeException e2) {
                    lastErr = e2;
                    try {
                        loc.first().evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true}))");
                        return;
                    } catch (RuntimeException e3) { lastErr = e3; }
                }
            }
            p.waitForTimeout(200);
        }
        if (lastErr != null) throw lastErr;
    }

    private void clickFirstEnabled(Page p, String selector, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Locator group;
            try {
                group = smartLocator(p, selector, 1500);
            } catch (RuntimeException e) {
                p.waitForTimeout(200);
                continue;
            }
            int count = group.count();
            for (int i = 0; i < count; i++) {
                Locator candidate = group.nth(i);
                boolean visible;
                try { visible = candidate.isVisible(); } catch (Throwable t) { visible = false; }
                if (!visible) continue;
                boolean enabled;
                try { enabled = (Boolean) candidate.evaluate("e => !(e.classList && e.classList.contains('pointer-events-none'))"); } catch (Throwable t) { enabled = true; }
                if (enabled) {
                    robustClick(p, candidate, 8000, selector + " [nth=" + i + "]");
                    return;
                }
            }
            p.waitForTimeout(200);
        }
        throw new RuntimeException("Не дождались активного элемента: " + selector);
    }

    private void clickFirstEnabledAny(Page p, String[] selectors, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (String sel : selectors) {
                try {
                    clickFirstEnabled(p, sel, 1200);
                    return;
                } catch (RuntimeException ignore) {}
            }
            p.waitForTimeout(150);
        }
        throw new RuntimeException("Не нашли активный элемент ни по одному из селекторов");
    }

    private Page clickCardMaybeOpensNewTab(Locator card) {
        int before = context.pages().size();
        robustClick(page, card, 30000, "game-card");
        page.waitForTimeout(600);
        int after = context.pages().size();
        if (after > before) {
            Page newPage = context.pages().get(after - 1);
            newPage.bringToFront();
            return newPage;
        }
        return page;
    }

    private void passTutorialIfPresent(Page gamePage) {
        for (int i = 1; i <= 5; i++) {
            try {
                Locator nextBtn = smartLocator(gamePage, "div[role='button']:has-text('Далее')", 600);
                if (nextBtn.count() == 0 || !nextBtn.first().isVisible()) break;
                robustClick(gamePage, nextBtn.first(), 2000, "Далее");
                gamePage.waitForTimeout(150);
            } catch (RuntimeException ignore) { break; }
        }
        try {
            Locator understood = smartLocator(gamePage, "div[role='button']:has-text('Я всё понял')", 600);
            if (understood.count() > 0 && understood.first().isVisible()) {
                robustClick(gamePage, understood.first(), 2000, "Я всё понял");
            }
        } catch (RuntimeException ignore) {}
    }

    private void setStake50ViaChip(Page gamePage) {
        Locator chip50 = smartLocator(gamePage, "div.chip-text:has-text('50')", 2000);
        robustClick(gamePage, chip50.first(), 12000, "chip-50");
    }

    private void waitRoundToSettle(Page gamePage, int maxMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxMs) {
            Locator anyBet = gamePage.locator("div[role='button'][data-market][data-outcome]:has-text('Сделать ставку')");
            try {
                if (anyBet.count() > 0 && anyBet.first().isVisible()) {
                    boolean enabled = (Boolean) anyBet.first().evaluate("e => !(e.classList && e.classList.contains('pointer-events-none'))");
                    if (enabled) return;
                }
            } catch (Throwable ignore) {}
            gamePage.waitForTimeout(150);
        }
    }

    private Page openGameByHrefContains(Page originPage, String hrefContains, String fallbackMenuText) {
        Frame f = findFrameWithSelector(originPage, "a[href*='" + hrefContains + "']", 5000);
        if (f == null && fallbackMenuText != null) {
            f = findFrameWithSelector(originPage, "span.text-hub-header-game-title:has-text('" + fallbackMenuText + "')", 5000);
        }
        if (f == null) throw new RuntimeException("Не нашли игру: " + hrefContains);
        Locator link = f.locator("a[href*='" + hrefContains + "']");
        link.first().scrollIntoViewIfNeeded();
        return clickCardMaybeOpensNewTab(link.first());
    }

    private Page openUniqueBoxingFromHub(Page originPage) {
        String innerSpan = "a.menu-sports-item-inner[href*='productId=boxing'] span.text-hub-header-game-title:has-text('Бокс')";
        Frame f = findFrameWithSelector(originPage, innerSpan, 8000);
        if (f == null) throw new RuntimeException("Не нашли уникальную кнопку 'Бокс'");
        Locator link = f.locator(innerSpan).first().locator("xpath=ancestor::a");
        return clickCardMaybeOpensNewTab(link.first());
    }

    // ===== ТЕСТ ===============================================================

    @Test
    void loginAndPlayFastGames() {
        // === Авторизация ===
        page.navigate("https://1xbet.kz/?platform_type=mobile");
        page.click("button#curLoginForm >> text=Войти");

        page.fill("input#auth_id_email", "168715375");
        page.fill("input#auth-form-password", "Aezakmi11+");
        page.click("button.auth-button:has(span.auth-button__text:has-text('Войти'))");

        // ждём «Выслать код»
        page.waitForSelector("button:has-text('Выслать код')",
                new Page.WaitForSelectorOptions().setTimeout(300000).setState(WaitForSelectorState.VISIBLE));
        page.click("button:has-text('Выслать код')");

        // ждём поле SMS-кода
        page.waitForSelector("input.phone-sms-modal-code__input",
                new Page.WaitForSelectorOptions().setTimeout(300000).setState(WaitForSelectorState.VISIBLE));

        // Google Messages
        Page messagesPage = context.newPage();
        messagesPage.navigate("https://messages.google.com/web/conversations");
        Locator qrBtn = messagesPage.locator("button:has(span.qr-text:has-text('Подключить, отсканировав QR-код'))");
        if (qrBtn.count() > 0 && qrBtn.first().isVisible()) {
            qrBtn.first().click();
        }
        Locator lastMessage = messagesPage.locator("mws-conversation-list-item").first();
        lastMessage.waitFor();
        String smsText = lastMessage.innerText();
        Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9]{6,8}\\b").matcher(smsText);
        String code = matcher.find() ? matcher.group() : null;
        page.bringToFront();
        page.fill("input.phone-sms-modal-code__input", code);
        page.click("button.phone-sms-modal-content__send:has-text('Подтвердить')");

        // === Быстрые игры ===
        page.click("button.header__hamburger.hamburger");
        page.click("a.drop-menu-list__link[href*='fast-games']");

        // Crash Boxing (особые кнопки)
        Locator crashTile = page.locator("div.tile__cell img[alt='Crash boxing']").first();
        Page crashPage = clickCardMaybeOpensNewTab(crashTile);
        passTutorialIfPresent(crashPage);
        clickFirstEnabled(crashPage, "div[role='button'][data-market='hit_met_condition'][data-outcome='yes']", 30000);
        clickFirstEnabled(crashPage, "div[role='button'][data-market='hit_met_condition'][data-outcome='yes_2']", 30000);
        waitRoundToSettle(crashPage, 25000);

        // Нарды
        Page nardsPage = openGameByHrefContains(crashPage, "nard", "Нарды");
        passTutorialIfPresent(nardsPage);
        setStake50ViaChip(nardsPage);
        clickFirstEnabled(nardsPage, "span[role='button'][data-market='dice'][data-outcome='blue']", 20000);
        waitRoundToSettle(nardsPage, 25000);

        // Дартс
        Page dartsPage = openGameByHrefContains(nardsPage, "darts?cid", "Дартс");
        passTutorialIfPresent(dartsPage);
        setStake50ViaChip(dartsPage);
        clickFirstEnabled(dartsPage, "span[role='button'][data-market='1-4-5-6-9-11-15-16-17-19']", 20000);
        waitRoundToSettle(dartsPage, 25000);

        // Дартс - Фортуна
        Page dartsFortunePage = openGameByHrefContains(dartsPage, "darts-fortune", "Дартс - Фортуна");
        passTutorialIfPresent(dartsFortunePage);
        setStake50ViaChip(dartsFortunePage);
        clickFirstEnabled(dartsFortunePage, "div[data-outcome='ONE_TO_EIGHT']", 20000);
        waitRoundToSettle(dartsFortunePage, 25000);

        // Больше/Меньше
        Page hiloPage = openGameByHrefContains(dartsFortunePage, "darts-hilo", "Больше/Меньше");
        passTutorialIfPresent(hiloPage);
        setStake50ViaChip(hiloPage);
        clickFirstEnabledAny(hiloPage, new String[]{
                "div[role='button'][data-market='THROW_RESULT'][data-outcome='gte-16']",
                "div.board-market-hi-eq:has-text('Больше или равно')"
        }, 45000);
        waitRoundToSettle(hiloPage, 30000);

        // Буллиты NHL21
        Page shootoutPage = openGameByHrefContains(hiloPage, "shootout", "Буллиты NHL21");
        passTutorialIfPresent(shootoutPage);
        setStake50ViaChip(shootoutPage);
        clickFirstEnabled(shootoutPage, "div[role='button'].market-button:has-text('Да')", 45000);
        waitRoundToSettle(shootoutPage, 35000);

        // Бокс
        Page boxingPage = openUniqueBoxingFromHub(shootoutPage);
        passTutorialIfPresent(boxingPage);
        setStake50ViaChip(boxingPage);
        clickFirstEnabled(boxingPage, "div[role='button'].contest-panel-outcome-button", 20000);
        waitRoundToSettle(boxingPage, 20000);

        System.out.println("Готово ✅");
    }
}
